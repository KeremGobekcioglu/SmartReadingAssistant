package com.gobex.smartreadingassistant.feature.device.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_connections")
data class DeviceConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "device_ip")
    val deviceIp: String,

    @ColumnInfo(name = "ssid")
    val ssid: String,

    @ColumnInfo(name = "connected_at")
    val connectedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "disconnected_at")
    val disconnectedAt: Long? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true, // Only one active connection

    @ColumnInfo(name = "connection_method")
    val connectionMethod: String, // "BLE" or "CACHED_IP"

    @ColumnInfo(name = "last_health_check")
    val lastHealthCheck: Long = System.currentTimeMillis()
)
