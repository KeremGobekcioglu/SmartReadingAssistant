package com.gobex.smartreadingassistant.feature.conversation.data.source.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_history")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // Stored as String, mapped from MessageRole
    val text: String,
    val imageBase64: String? = null,
    val fileUri: String? = null, // Crucial for File API optimization
    val timestamp: Long,
    val totalTokenCount: Int? = null
)