package com.gobex.smartreadingassistant.feature.conversation.presentation

import com.gobex.smartreadingassistant.feature.conversation.domain.Message

data class ConversionState(
    val messages : List<Message> = emptyList(),
    val isStreaming : Boolean = false,
    val isLoadingHistory : Boolean = false,
    val currentText : String = "", // tempt text for ai s response.
    val isFlashOn: Boolean = false
)
