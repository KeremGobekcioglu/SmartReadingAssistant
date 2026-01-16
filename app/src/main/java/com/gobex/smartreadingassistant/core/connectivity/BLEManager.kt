package com.gobex.smartreadingassistant.core.connectivity

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.ble.ktx.suspend
import java.util.UUID
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
    private var handshakeJob: Job? = null // To track the connection process
    private var currentScanCallback: ScanCallback? = null // To track the active scan
    @SuppressLint("MissingPermission")
    fun startConnectionSequence(ssid: String, pass: String) {
        val scanner = adapter?.bluetoothLeScanner ?: return

        // --- NEW: THE CLEANUP (This prevents the "stacking" problem) ---
        // If a scan is already running, stop it first
        currentScanCallback?.let { scanner.stopScan(it) }
        // If we are mid-handshake, cancel it
        handshakeJob?.cancel()
        // ---------------------------------------------------------------

        val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        // Save this callback to our variable so we can stop it later
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                Log.d("BLE", "ESP32 Found: ${device.address}")

                scanner.stopScan(this)
                currentScanCallback = null // Clear it since it's stopped

                // Start the handshake and save it to our job variable
                handshakeJob = performHandshake(device, ssid, pass)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed: $errorCode")
                currentScanCallback = null
            }
        }

        currentScanCallback = scanCallback // Store the current scan
        scanner.startScan(listOf(filter), settings, scanCallback)
    }
    private fun performHandshake(device: BluetoothDevice, ssid: String, pass: String) : Job {
        return CoroutineScope(Dispatchers.IO).launch {
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