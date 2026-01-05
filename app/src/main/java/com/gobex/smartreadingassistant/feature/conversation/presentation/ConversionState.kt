package com.gobex.smartreadingassistant.feature.conversation.presentation

import com.gobex.smartreadingassistant.feature.conversation.domain.Message

data class ConversionState(
    val messages : List<Message> = emptyList(),
    val isStreaming : Boolean = false,
    val isLoadingHistory : Boolean = false,
    val currentText : String = "", // tempt text for ai s response.
    val isFlashOn: Boolean = false,
    val capturedImageBytes: ByteArray? = null
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
        if (!capturedImageBytes.contentEquals(other.capturedImageBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isStreaming.hashCode()
        result = 31 * result + isLoadingHistory.hashCode()
        result = 31 * result + isFlashOn.hashCode()
        result = 31 * result + messages.hashCode()
        result = 31 * result + currentText.hashCode()
        result = 31 * result + (capturedImageBytes?.contentHashCode() ?: 0)
        return result
    }
}
