package com.gobex.smartreadingassistant.core.connectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class HotspotCredentials(val ssid: String, val pass: String)

@Singleton
class HotspotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var currentReservation: LocalOnlyHotspotReservation? = null

    @RequiresApi(Build.VERSION_CODES.O)
    fun startHotspot(): Flow<HotspotCredentials> = callbackFlow {

        // We need NEARBY_WIFI_DEVICES or FINE_LOCATION depending on Android version
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            close(Exception("Missing location permission for Hotspot"))
            return@callbackFlow
        }

        val callback = object : LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: LocalOnlyHotspotReservation?) {
                super.onStarted(reservation)
                currentReservation = reservation

                val credentials = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Modern Way (Android 11+)
                    val softApConfig = reservation?.softApConfiguration
                    if (softApConfig != null) {
                        HotspotCredentials(softApConfig.ssid ?: "", softApConfig.passphrase ?: "")
                    } else null
                } else {
                    // Legacy Way (Android 8.0 to 10)
                    @Suppress("DEPRECATION")
                    val config = reservation?.wifiConfiguration
                    if (config != null) {
                        HotspotCredentials(
                            config.SSID?.replace("\"", "") ?: "",
                            config.preSharedKey?.replace("\"", "") ?: ""
                        )
                    } else null
                }

                if (credentials != null && credentials.ssid.isNotEmpty()) {
                    trySend(credentials)
                } else {
                    close(Exception("Could not retrieve Hotspot credentials"))
                }
            }

            override fun onFailed(reason: Int) {
                close(Exception("Hotspot failed: Code $reason"))
            }
        }

        try {
            wifiManager.startLocalOnlyHotspot(callback, null)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose { stopHotspot() }
    }

    fun stopHotspot() {
        currentReservation?.close()
        currentReservation = null
    }
}