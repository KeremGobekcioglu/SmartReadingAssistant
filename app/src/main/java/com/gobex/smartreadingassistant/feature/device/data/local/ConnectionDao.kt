package com.gobex.smartreadingassistant.feature.device.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceConnectionDao {

    // Get the current active connection
    @Query("SELECT * FROM device_connections WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveConnection(): DeviceConnectionEntity?

    @Query("SELECT * FROM device_connections WHERE is_active = 1 LIMIT 1")
    fun getActiveConnectionFlow(): Flow<DeviceConnectionEntity?>

    // Save new connection (deactivate old ones first)
    @Transaction
    suspend fun saveNewConnection(connection: DeviceConnectionEntity) : Long {
        deactivateAll()
        return insert(connection)
    }

    @Insert
    suspend fun insert(connection: DeviceConnectionEntity): Long

    @Query("UPDATE device_connections SET is_active = 0, disconnected_at = :timestamp")
    suspend fun deactivateAll(timestamp: Long = System.currentTimeMillis())

    @Update
    suspend fun update(connection: DeviceConnectionEntity)

    // Update just the health check timestamp
    @Query("UPDATE device_connections SET last_health_check = :timestamp WHERE id = :id")
    suspend fun updateHealthCheck(id: Long, timestamp: Long = System.currentTimeMillis())

    // Get connection history (useful for debugging)
    @Query("SELECT * FROM device_connections ORDER BY connected_at DESC LIMIT 10")
    suspend fun getConnectionHistory(): List<DeviceConnectionEntity>

    // Clean up old connections (keep last 20)
    @Query("""
        DELETE FROM device_connections 
        WHERE id NOT IN (
            SELECT id FROM device_connections 
            ORDER BY connected_at DESC 
            LIMIT 20
        )
    """)
    suspend fun cleanupOldConnections()
}