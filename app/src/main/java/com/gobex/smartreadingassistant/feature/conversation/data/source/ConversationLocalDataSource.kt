package com.gobex.smartreadingassistant.feature.conversation.data.source

import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ConversationLocalDataSource {

    /**
     * Adds a new message (from user or model) to the conversation history.
     */
    suspend fun addMessage(message: Message)

    /**
     * Gets the current conversation history as a List.
     * This is used primarily when constructing the stateless Gemini API request.
     */
    suspend fun getCurrentHistory(): List<Message>

    /**
     * Optional: Exposes the list of messages as a StateFlow for real-time
     * observation by the ViewModel/UI.
     */
    fun getMessagesFlow(): Flow<List<Message>>

    /**
     * Clears all messages from the history.
     */
    suspend fun clearHistory()
}