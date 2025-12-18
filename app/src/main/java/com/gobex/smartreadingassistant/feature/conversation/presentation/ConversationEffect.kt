package com.gobex.smartreadingassistant.feature.conversation.presentation

// This effect class is for one time actions for ui to observe. With this , i dont need to update state manually each time.
sealed class ConversationEffect
{
    data class ShowError(val message: String?) : ConversationEffect()
    data class SpeakText(val text: String) : ConversationEffect()
}