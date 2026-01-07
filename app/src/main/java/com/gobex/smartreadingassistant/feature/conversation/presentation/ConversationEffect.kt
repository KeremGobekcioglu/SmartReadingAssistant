package com.gobex.smartreadingassistant.feature.conversation.presentation

// This effect class is for one time actions for ui to observe. With this , i dont need to update state manually each time.
sealed class ConversationEffect {
    data class ShowError(val message: String?) : ConversationEffect()
    data class SpeakText(val text: String) : ConversationEffect()
    data object NavigateToChat : ConversationEffect()

    // ===== NEW: Voice Announcement Effects =====
    data class AnnounceState(
        val state: AppState,
        val interrupt: Boolean = false // Should interrupt current TTS
    ) : ConversationEffect()

    data class AnnounceAction(
        val message: String,
        val withHaptic: Boolean = true // Vibrate on announcement
    ) : ConversationEffect()

    data class PlaySound(
        val soundType: SoundType
    ) : ConversationEffect()
}