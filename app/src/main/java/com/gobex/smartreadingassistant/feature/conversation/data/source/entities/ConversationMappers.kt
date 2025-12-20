package com.gobex.smartreadingassistant.feature.conversation.data.source.entities

import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole

fun MessageEntity.toDomain(): Message {
    return Message(
        role = MessageRole.valueOf(this.role),
        text = this.text,
        imageBase64 = this.imageBase64,
        fileUri = this.fileUri,
        timestamp = this.timestamp,
        totalTokenCount = this.totalTokenCount
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        role = this.role.name,
        text = this.text,
        imageBase64 = this.imageBase64,
        fileUri = this.fileUri,
        timestamp = this.timestamp,
        totalTokenCount = this.totalTokenCount
    )
}