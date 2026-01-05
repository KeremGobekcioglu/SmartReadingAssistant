package com.gobex.smartreadingassistant.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationDAO
import com.gobex.smartreadingassistant.feature.conversation.data.source.entities.MessageEntity
import com.gobex.smartreadingassistant.feature.device.data.local.DeviceConnectionDao
import com.gobex.smartreadingassistant.feature.device.data.local.DeviceConnectionEntity

@Database(
    entities = [
        MessageEntity::class,
        DeviceConnectionEntity::class  // Add this
    ],
    version = 2,  // Increment version!
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO
    abstract fun deviceConnectionDao(): DeviceConnectionDao  // Add this
}