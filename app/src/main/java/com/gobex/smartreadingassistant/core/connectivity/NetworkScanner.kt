package com.gobex.smartreadingassistant.core.connectivity
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 1. Dynamically finds the phone's IP in the Hotspot network.
     * 2. Scans that specific subnet (e.g., 192.168.43.x).
     */
    suspend fun findEsp32Ip(): String? = withContext(Dispatchers.IO) {
        val gatewayPrefix = getHotspotSubnet() ?: return@withContext null
        Log.d("Scanner", "Scanning subnet: $gatewayPrefix.2 - .25")

        // Scan the first 25 addresses (Hotspots usually assign .2, .3, etc.)
        val range = 2..25
        val deferredResults = range.map { i ->
            async {
                val testIp = "$gatewayPrefix.$i"
                if (isPortOpen(testIp, 80)) testIp else null
            }
        }

        return@withContext deferredResults.awaitAll().filterNotNull().firstOrNull()
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            // Very fast timeout (50ms) is enough for local hotspot
            // THIS COULD  BE DANGEROUS
            socket.connect(InetSocketAddress(ip, port), 100)
            socket.close()
            Log.d("Scanner", "DEVICE FOUND AT $ip")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the subnet prefix (e.g., "192.168.43") of the Local Only Hotspot.
     */
    private fun getHotspotSubnet(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Find the network interface that is serving the hotspot
        // Note: In some Android versions, this might be tricky, so we fallback to a common trick
        // of checking all interfaces.

        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                // Hotspot interfaces often named "wlan0", "ap0", or "swlan0"
                if (iface.isUp && !iface.isLoopback) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.hostAddress.startsWith("127.")) {
                            // This is likely our IP. Return the first 3 segments.
                            // e.g., 192.168.43.1 -> 192.168.43
                            val ip = addr.hostAddress
                            return ip.substring(0, ip.lastIndexOf('.'))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Scanner", "Error finding subnet", e)
        }

        // Fallback for most devices if auto-detection fails
        return "192.168.43"
    }
}