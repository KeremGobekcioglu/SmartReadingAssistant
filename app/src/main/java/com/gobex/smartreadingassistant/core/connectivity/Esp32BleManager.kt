package com.gobex.smartreadingassistant.core.connectivity

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.ktx.suspend
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.*

class Esp32BleManager(context: Context) : BleManager(context) {

    // UUIDs from your ESP32 Code
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write
    private val TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify

    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    @Deprecated("Deprecated in Java")
    override fun getGattCallback(): BleManagerGattCallback = @Suppress("OVERRIDE_DEPRECATION")
    object : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(SERVICE_UUID)
            rxChar = service?.getCharacteristic(RX_CHAR_UUID)
            txChar = service?.getCharacteristic(TX_CHAR_UUID)
            return rxChar != null && txChar != null
        }

        override fun onServicesInvalidated() {
            rxChar = null
            txChar = null
        }
    }

    // Flow-based notification observer
    fun observeIpNotifications(): Flow<String> = callbackFlow {
        val characteristic = txChar
        if (characteristic == null) {
            close(Exception("TX characteristic not found"))
            return@callbackFlow
        }

        // Set the notification callback to parse incoming Data
        setNotificationCallback(characteristic).with(object : DataReceivedCallback {
            override fun onDataReceived(device: BluetoothDevice, data: Data) {
                val text = data.getStringValue(0)
                Log.d("BLE_IP", "Received: $text")
                if (text?.startsWith("IP:") == true) {
                    trySend(text.substring(3))
                }
            }
        })

        // Enable notifications
        try {
            enableNotifications(characteristic).suspend()
        } catch (e: Exception) {
            Log.e("ESP32_BLE", "Failed to enable notifications", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                disableNotifications(characteristic).enqueue()
            } catch (e: Exception) {
                Log.e("ESP32_BLE", "Failed to disable notifications", e)
            }
        }
    }

    // Helper to connect to device
    @Suppress("unused")
    suspend fun connectToDevice(device: BluetoothDevice) {
        connect(device)
            .useAutoConnect(false)
            .timeout(10000)
            .suspend()
    }

    // Helper function to send credentials with error handling
    suspend fun sendWifiCredentials(ssid: String, pass: String): Result<Unit> {
        return try {
            val char = rxChar ?: return Result.failure(
                IllegalStateException("Device not connected or characteristic not found")
            )

            val data = "$ssid,$pass".toByteArray()
            writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .suspend()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ESP32_BLE", "Failed to send credentials", e)
            Result.failure(e)
        }
    }
}
