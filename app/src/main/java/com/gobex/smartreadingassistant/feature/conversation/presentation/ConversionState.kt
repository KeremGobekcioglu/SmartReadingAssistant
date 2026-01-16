package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.speech.tts.Voice
import com.gobex.smartreadingassistant.feature.conversation.domain.Message

// ==================== ENHANCED STATE ====================
data class ConversionState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val currentText: String = "",
    val isLoadingHistory: Boolean = false,
    val capturedImageBytes: ByteArray? = null,
    val currentImageUri: String? = null,  // ✅ ADD THIS!
    val isImageDialogVisible: Boolean = false,
    val isFlashOn: Boolean = false,
    val isAccessibilityMode: Boolean = false,
    val isVoiceAnnouncementsEnabled: Boolean = false,
    val currentAppState: AppState = AppState.Idle,
    val lastAnnouncedState: AppState? = null,
    val isConnectScreen: Boolean = true,
) {
    // ByteArray equality override if needed


    override fun hashCode(): Int {
        var result = messages.hashCode()
        result = 31 * result + isStreaming.hashCode()
        result = 31 * result + currentText.hashCode()
        result = 31 * result + isLoadingHistory.hashCode()
        result = 31 * result + (capturedImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (currentImageUri?.hashCode() ?: 0)  // ✅ ADD THIS!
        result = 31 * result + isImageDialogVisible.hashCode()
        result = 31 * result + isFlashOn.hashCode()
        result = 31 * result + isAccessibilityMode.hashCode()
        result = 31 * result + isVoiceAnnouncementsEnabled.hashCode()
        result = 31 * result + currentAppState.hashCode()
        result = 31 * result + (lastAnnouncedState?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversionState

        if (isStreaming != other.isStreaming) return false
        if (isLoadingHistory != other.isLoadingHistory) return false
        if (isImageDialogVisible != other.isImageDialogVisible) return false
        if (isFlashOn != other.isFlashOn) return false
        if (isAccessibilityMode != other.isAccessibilityMode) return false
        if (isVoiceAnnouncementsEnabled != other.isVoiceAnnouncementsEnabled) return false
        if (isConnectScreen != other.isConnectScreen) return false
        if (messages != other.messages) return false
        if (currentText != other.currentText) return false
        if (!capturedImageBytes.contentEquals(other.capturedImageBytes)) return false
        if (currentImageUri != other.currentImageUri) return false
        if (currentAppState != other.currentAppState) return false
        if (lastAnnouncedState != other.lastAnnouncedState) return false

        return true
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
        is Connected -> "Success. Your glasses are connected and ready to use."
        is Connecting -> "Connecting to glasses. Please wait."
        is Disconnected -> "Glasses disconnected. Please ensure they are turned on."
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

    object Instructions: VoiceCommand()
    data class SetFlash(val on: Boolean) : VoiceCommand()
    object StopSpeaking : VoiceCommand()
    object RepeatLast : VoiceCommand()
    object ClearConversation : VoiceCommand()
    object ReadAll : VoiceCommand()
    object Stop : VoiceCommand()
    data class SendToAI(val text: String) : VoiceCommand() // Not a command, send to Gemini
}

// ==================== COMMAND PARSER ====================
object VoiceCommandParser {

    private val commandPatterns = mapOf(
        // Photo capture commands
        listOf("take a picture", "capture photo", "take photo", "capture", "snap" , "concon")
                to VoiceCommand.CapturePhoto,

        // Flash control
        listOf("flash on", "turn on flash", "enable flash" , "flush on" , "zenciboni")
                to VoiceCommand.SetFlash(true),
        listOf("flash off", "turn off flash", "disable flash" , "flush off" , "zencikoni")
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
        listOf("read all", "read everything", "read the whole thing", "read text")
                to VoiceCommand.ReadAll,

        listOf("stop reading", "stop" , "end conversation") to VoiceCommand.Stop,

        listOf("how to use this app" , "instructions" , "commands") to VoiceCommand.Instructions
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