package com.gobex.smartreadingassistant.feature.conversation.domain

data class GeminiRequest(
    val contents: List<Content>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String  // Base64
)