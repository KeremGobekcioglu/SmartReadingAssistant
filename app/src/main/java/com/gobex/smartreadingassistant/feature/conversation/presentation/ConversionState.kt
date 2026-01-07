package com.gobex.smartreadingassistant.feature.conversation.presentation

import com.gobex.smartreadingassistant.feature.conversation.domain.Message

// ==================== ENHANCED STATE ====================
data class ConversionState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val currentText: String = "", // temp text for AI's response
    val isFlashOn: Boolean = false,
    val capturedImageBytes: ByteArray? = null,

    // ===== NEW: Accessibility & Voice Control =====
    val isAccessibilityMode: Boolean = false, // Voice-only mode enabled
    val isVoiceAnnouncementsEnabled: Boolean = false, // Auto-announce state changes
    val currentAppState: AppState = AppState.Idle, // For voice feedback
    val lastAnnouncedState: AppState? = null // Prevent duplicate announcements
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversionState

        if (isStreaming != other.isStreaming) return false
        if (isLoadingHistory != other.isLoadingHistory) return false
        if (isFlashOn != other.isFlashOn) return false
        if (messages != other.messages) return false
        if (currentText != other.currentText) return false
        if (isAccessibilityMode != other.isAccessibilityMode) return false
        if (isVoiceAnnouncementsEnabled != other.isVoiceAnnouncementsEnabled) return false
        if (currentAppState != other.currentAppState) return false
        if (!capturedImageBytes.contentEquals(other.capturedImageBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isStreaming.hashCode()
        result = 31 * result + isLoadingHistory.hashCode()
        result = 31 * result + isFlashOn.hashCode()
        result = 31 * result + messages.hashCode()
        result = 31 * result + currentText.hashCode()
        result = 31 * result + isAccessibilityMode.hashCode()
        result = 31 * result + isVoiceAnnouncementsEnabled.hashCode()
        result = 31 * result + currentAppState.hashCode()
        result = 31 * result + (capturedImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

// ==================== APP STATE FOR VOICE FEEDBACK ====================
sealed class AppState {
    object Idle : AppState()
    object Listening : AppState()
    object Processing : AppState()
    object Speaking : AppState()
    data class Capturing(val step: String = "Capturing photo") : AppState()
    data class Connected(val ip: String) : AppState()
    object Connecting : AppState()
    object Disconnected : AppState()
    data class Error(val message: String) : AppState()

    // Convert to user-friendly announcement
    fun toAnnouncement(): String = when (this) {
        is Idle -> "Ready"
        is Listening -> "Listening"
        is Processing -> "Processing your question"
        is Speaking -> "" // Don't announce, already speaking
        is Capturing -> step
        is Connected -> "Connected to glasses at IP $ip"
        is Connecting -> "Connecting to glasses"
        is Disconnected -> "Disconnected"
        is Error -> "Error: $message"
    }

    // Priority for interrupting current speech
    enum class Priority { LOW, MEDIUM, HIGH }

    fun priority(): Priority = when (this) {
        is Error, is Disconnected -> Priority.HIGH
        is Connected, is Connecting -> Priority.MEDIUM
        else -> Priority.LOW
    }
}

// ==================== ENHANCED EFFECTS ====================


// ==================== SOUND TYPES (Earcons) ====================
enum class SoundType {
    BUTTON_PRESS,      // Short beep
    PHOTO_CAPTURED,    // Camera shutter sound
    CONNECTION_SUCCESS, // Success chime
    CONNECTION_FAILED,  // Error tone
    LISTENING_START,    // Ascending tone
    LISTENING_STOP      // Descending tone
}

// ==================== VOICE COMMAND TYPES ====================
sealed class VoiceCommand {
    object CapturePhoto : VoiceCommand()
    object ToggleFlash : VoiceCommand()
    data class SetFlash(val on: Boolean) : VoiceCommand()
    object StopSpeaking : VoiceCommand()
    object RepeatLast : VoiceCommand()
    object ClearConversation : VoiceCommand()
    data class SendToAI(val text: String) : VoiceCommand() // Not a command, send to Gemini
}

// ==================== COMMAND PARSER ====================
object VoiceCommandParser {

    private val commandPatterns = mapOf(
        // Photo capture commands
        listOf("take a picture", "capture photo", "take photo", "capture", "snap")
                to VoiceCommand.CapturePhoto,

        // Flash control
        listOf("flash on", "turn on flash", "enable flash")
                to VoiceCommand.SetFlash(true),
        listOf("flash off", "turn off flash", "disable flash")
                to VoiceCommand.SetFlash(false),
        listOf("toggle flash", "switch flash")
                to VoiceCommand.ToggleFlash,

        // TTS control
        listOf("stop", "stop speaking", "be quiet", "silence", "shut up")
                to VoiceCommand.StopSpeaking,
        listOf("repeat", "say that again", "repeat that", "what did you say")
                to VoiceCommand.RepeatLast,

        // Conversation control
        listOf("clear chat", "clear conversation", "start over", "new conversation")
                to VoiceCommand.ClearConversation,
    )

    /**
     * Parses STT text and returns either a command or text to send to AI
     */
    fun parse(text: String): VoiceCommand {
        val normalized = text.trim().lowercase()

        // Check each command pattern
        for ((patterns, command) in commandPatterns) {
            if (patterns.any { pattern -> normalized.startsWith(pattern) || normalized == pattern }) {
                return command
            }
        }

        // No command matched - send to AI
        return VoiceCommand.SendToAI(text)
    }
}