package com.gobex.smartreadingassistant.feature.conversation.domain

data class Message(
    val role: MessageRole,
    val text: String,
    val imageBase64: String? = null,
    val fileUri: String? = null, // Image will be uplaoded to gemini file api so that we wont resend.
    val timestamp: Long = System.currentTimeMillis(),
    val totalTokenCount: Int? = null
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ConversationState(
    val messages: List<Message>,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class Content(
    val role: String,  // "user" or "model"
    val parts: List<Part>
)