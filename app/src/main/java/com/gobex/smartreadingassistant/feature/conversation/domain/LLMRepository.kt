package com.gobex.smartreadingassistant.feature.conversation.domain

import kotlinx.coroutines.flow.Flow
import kotlin.io.encoding.Base64

interface LLMRepository {

    fun sendMessage(message : String , modelId: String = "gemini-1.5-flash", imageBase64: String? , conversationHistory: List<Message>) : Result<Message>

    // Get conversation history
    suspend fun getConversationHistory(): List<Message>
    suspend fun streamMessage(
        message: String,
        modelId: String = "gemini-1.5-flash", // Default
        imageBase64: String? = null,
        conversationHistory: List<Message>
    ): Flow<StreamResult>
    // Clear conversation
    suspend fun clearConversation()

    // This is used to get usage details.
    suspend fun getUsageMetadata(): UsageMetadata?
}
