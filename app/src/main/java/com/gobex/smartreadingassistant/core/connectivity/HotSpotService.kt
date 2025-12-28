package com.gobex.smartreadingassistant.core.connectivity



import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HotspotService : Service() {

    @Inject lateinit var hotspotManager: HotspotManager

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Glasses Connection")
            .setContentText("Hotspot is active. Searching for glasses...")
            .setSmallIcon(android.R.drawable.ic_menu_share) // Replace with your app icon
            .setOngoing(true) // Cannot be swiped away
            .build()

        // This makes the service "Foreground" and visible to the user
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hotspot Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "hotspot_channel"
        const val NOTIFICATION_ID = 1001
    }
}