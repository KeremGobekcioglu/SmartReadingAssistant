package com.gobex.smartreadingassistant.core.connectivity

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gobex.smartreadingassistant.feature.device.data.local.DeviceConnectionDao
import com.gobex.smartreadingassistant.feature.device.data.local.DeviceConnectionEntity
import com.gobex.smartreadingassistant.feature.device.data.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceConnectionManager @Inject constructor(
    private val hotspotManager: HotspotManager,
    private val bleManager: BleConnectionManager,
    private val networkScanner: NetworkScanner,
    private val deviceRepository: DeviceRepository,
    private val connectionDao: DeviceConnectionDao,  // Inject Room DAO
    @ApplicationContext private val context: Context
) {
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        data class Connecting(val step: String) : ConnectionState()
        data class Connected(val ip: String, val connectionId: Long) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state = _state.asStateFlow()

    // Observe active connection from database
    val activeConnection: Flow<DeviceConnectionEntity?> =
        connectionDao.getActiveConnectionFlow()
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    }

    private val bluetoothAdapter: android.bluetooth.BluetoothAdapter?
        get() = bluetoothManager.adapter

    // 1. Initialize with current hardware state
    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    // 2. Define the Receiver
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    android.bluetooth.BluetoothAdapter.EXTRA_STATE,
                    android.bluetooth.BluetoothAdapter.ERROR
                )
                val isEnabled = (state == android.bluetooth.BluetoothAdapter.STATE_ON)

                // Only update if value actually changed
                if (_isBluetoothEnabled.value != isEnabled) {
                    Log.d("CONN_MGR", "Bluetooth State Changed: $isEnabled")
                    _isBluetoothEnabled.value = isEnabled
                }
            }
        }
    }
    init {
        val filter = android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
        refreshBluetoothState()
        // Try to reconnect to last active connection on app start
        CoroutineScope(Dispatchers.IO).launch {
            attemptReconnectToSavedConnection()
        }
    }
    fun refreshBluetoothState() {
        val current = bluetoothAdapter?.isEnabled == true
        if (_isBluetoothEnabled.value != current) {
            _isBluetoothEnabled.value = current
            Log.d("CONN_MGR", "Bluetooth State Refreshed: $current")
        }
    }
    private suspend fun attemptReconnectToSavedConnection() {
        val lastConnection = connectionDao.getActiveConnection()

        if (lastConnection != null) {
            _state.value = ConnectionState.Connecting("Reconnecting to glasses...")
            val isAlive = deviceRepository.pingDevice(lastConnection.deviceIp)

            if (isAlive) {
                deviceRepository.connectToDeviceServer(lastConnection.deviceIp)
                connectionDao.updateHealthCheck(lastConnection.id)
                _state.value = ConnectionState.Connected(lastConnection.deviceIp, lastConnection.id)
            } else {
                // IF THE PING FAILS ON STARTUP:
                // Don't just give up; the device is likely waiting for BLE!
                Log.d("DEVICE", "Saved device not on WiFi. Starting BLE flow.")
                connectionDao.deactivateAll()
                connect() // Trigger the automated BLE handshake
            }
        }
    }

    suspend fun connect() {
        if (_state.value is ConnectionState.Connecting) return
        if (!_isBluetoothEnabled.value) {
            _state.value = ConnectionState.Error("Bluetooth is disabled. Please enable it to connect.")
            return
        }
        val serviceIntent = Intent(context, HotspotService::class.java)
        context.startService(serviceIntent)

        _state.value = ConnectionState.Connecting("Starting Hotspot...")

        try {
            hotspotManager.startHotspot().collect { creds ->
                _state.value = ConnectionState.Connecting("Sending Credentials via BLE...")

                bleManager.startConnectionSequence(creds.ssid, creds.pass)

                val ipViaBle = withTimeoutOrNull(15_000) {
                    bleManager.deviceIpFlow.first { it.isNotEmpty() }
                }

                if (ipViaBle != null) {
                    finalizeConnection(ipViaBle, creds.ssid, "BLE", serviceIntent)
                } else {
                    _state.value = ConnectionState.Connecting("BLE Timeout. Scanning Network...")

                    val scannedIp = scanForDeviceWithRetry(15_000)

                    if (scannedIp != null) {
                        finalizeConnection(scannedIp, creds.ssid, "NETWORK_SCAN", serviceIntent)
                    } else {
                        handleConnectionFailure(serviceIntent, "Could not find glasses")
                    }
                }
            }
        } catch (e: Exception) {
            handleConnectionFailure(serviceIntent, "Connection Error: ${e.message}")
        }
    }

    private suspend fun finalizeConnection(
        ip: String,
        ssid: String,
        method: String,
        serviceIntent: Intent
    ) {
        // Save to database
        val connectionEntity = DeviceConnectionEntity(
            deviceIp = ip,
            ssid = ssid,
            connectionMethod = method
        )

        val connectionId = connectionDao.saveNewConnection(connectionEntity)

        deviceRepository.connectToDeviceServer(ip)
        context.stopService(serviceIntent)

        _state.value = ConnectionState.Connected(ip, connectionId)
    }

    private suspend fun handleConnectionFailure(serviceIntent: Intent, errorMsg: String) {
        _state.value = ConnectionState.Error(errorMsg)
        context.stopService(serviceIntent)
        hotspotManager.stopHotspot()
    }

    private suspend fun scanForDeviceWithRetry(maxDurationMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            val ip = networkScanner.findEsp32Ip()
            if (ip != null) return ip
            delay(2000)
        }
        return null
    }

    suspend fun disconnect() {
        connectionDao.deactivateAll()
        _state.value = ConnectionState.Disconnected
    }

    // Periodic health check (call from a background worker or service)
    suspend fun performHealthCheck() {
        val connection = connectionDao.getActiveConnection() ?: return

        // Try to ping 3 times with a delay before giving up
        var isAlive = false
        repeat(3) { attempt ->
            isAlive = deviceRepository.pingDevice(connection.deviceIp)
            if (isAlive) return@repeat // Found it!

            Log.d("DEVICE", "Ping attempt ${attempt + 1} failed. Waiting...")
            delay(2000) // Wait 2 seconds between pings
        }

        if (isAlive) {
            connectionDao.updateHealthCheck(connection.id)
        } else {
            Log.d("DEVICE CONNECTION MANAGER", "Ping failed after retries. Starting BLE recovery.")

            // 1. Move to connecting state
            _state.value = ConnectionState.Connecting("Connection lost. Reconnecting...")

            // 2. Clean up old session
            connectionDao.deactivateAll()

            // 3. Trigger BLE handshake
            connect()
        }
    }
    // Call this if the app is shutting down or if this wasn't a Singleton
    fun teardown() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}