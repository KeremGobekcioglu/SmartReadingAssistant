package com.gobex.smartreadingassistant.feature.conversation.domain

data class GeminiResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata? = null  // Track costs
)

data class Candidate(
    val content: Content,
    val finishReason: String? = null  // Debug cut-off responses
)

data class UsageMetadata(
    val totalTokenCount: Int  // Just track total, simplest
)