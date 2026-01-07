package com.gobex.smartreadingassistant.feature.conversation.domain

sealed class StreamResult {
    data class Chunk(val text: String) : StreamResult()
    data class Complete(val fullMessage: Message, val metadata: UsageMetadata? , val uploadedFileUri: String? = null) : StreamResult()
    data class Error(val exception: Exception) : StreamResult()
}