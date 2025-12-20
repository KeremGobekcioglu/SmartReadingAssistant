package com.gobex.smartreadingassistant.core.connectivity

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.ble.ktx.suspend
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bleManager = Esp32BleManager(context)
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _deviceIpFlow = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val deviceIpFlow = _deviceIpFlow.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startConnectionSequence(ssid: String, pass: String) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BLE", "Bluetooth unavailable")
            return
        }

        scanner.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                // Check name safely (name can be null)
                if (device.name == "SmartGlasses") {
                    Log.d("BLE", "Device Found: ${device.address}")
                    scanner.stopScan(this)
                    performHandshake(device, ssid, pass)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed: $errorCode")
            }
        })
    }

    private fun performHandshake(device: BluetoothDevice, ssid: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("BLE", "Connecting...")
                // 1. Connect using Nordic Library features
                bleManager.connect(device)
                    .retry(3, 100) // Retry 3 times, 100ms apart
                    .useAutoConnect(false)
                    .timeout(10000) // 10s timeout
                    .suspend()

                Log.d("BLE", "Connected. Setting up listener...")

                // 2. Start listening (Non-blocking)
                val job = launch {
                    bleManager.observeIpNotifications().collect { ipAddress ->
                        Log.d("BLE", "IP Received: $ipAddress")
                        _deviceIpFlow.emit(ipAddress)
                    }
                }

                // --- THE CRITICAL FIX ---
                // Give the flow above 500ms to register the "Enable Notification" descriptor
                // Otherwise, we might write credentials too fast and miss the reply.
                delay(500)

                // 3. Send Credentials & Check Result
                Log.d("BLE", "Sending Credentials...")
                val result = bleManager.sendWifiCredentials(ssid, pass)

                if (result.isSuccess) {
                    Log.d("BLE", "Credentials sent successfully")
                } else {
                    Log.e("BLE", "Failed to send credentials")
                    // If write fails, disconnect and stop listening
                    bleManager.disconnect().enqueue()
                    job.cancel()
                }

            } catch (e: Exception) {
                Log.e("BLE", "Handshake failed", e)
                bleManager.disconnect().enqueue()
            }
        }
    }
}