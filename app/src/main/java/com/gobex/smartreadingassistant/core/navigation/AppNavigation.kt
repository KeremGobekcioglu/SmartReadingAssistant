package com.gobex.smartreadingassistant.core.navigation
import kotlinx.serialization.Serializable

sealed interface Route {
    // The Main Chat Interface (Test Playground)
    @Serializable
    data object Chat : Route

    // The Admin/History Dashboard
    @Serializable
    data object History : Route

    // Optional: If you support specific session IDs later
    @Serializable
    data class ConversationDetail(val conversationId: String) : Route
}