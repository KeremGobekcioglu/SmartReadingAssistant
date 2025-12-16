package com.gobex.smartreadingassistant.feature.conversation.domain

import com.gobex.smartreadingassistant.feature.conversation.data.dto.*

/* ---------------------------------------------------
   Role Mappers
--------------------------------------------------- */

fun MessageRole.toDtoRole(): String =
    when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "model"
    }

fun String.toDomainRole(): MessageRole =
    when (this) {
        "user" -> MessageRole.USER
        "model" -> MessageRole.ASSISTANT
        else -> MessageRole.ASSISTANT
    }

/* ---------------------------------------------------
   Part Mappers
--------------------------------------------------- */

fun Part.toDto(): PartDto =
    PartDto(
        text = text,
        inlineData = inlineData?.toDto()
    )

fun InlineData.toDto(): InlineDataDto =
    InlineDataDto(
        mimeType = mimeType,
        data = data
    )

fun PartDto.toDomain(): Part =
    Part(
        text = text,
        inlineData = inlineData?.toDomain()
    )

fun InlineDataDto.toDomain(): InlineData =
    InlineData(
        mimeType = mimeType,
        data = data
    )

/* ---------------------------------------------------
   Content Mappers
--------------------------------------------------- */

fun Content.toDto(): ContentDto =
    ContentDto(
        role = role,
        parts = parts.map { it.toDto() }
    )

fun ContentDto.toDomain(): Content =
    Content(
        role = role,
        parts = parts.map { it.toDomain() }
    )

/* ---------------------------------------------------
   Gemini Request Mapper
--------------------------------------------------- */

fun GeminiRequest.toDto(): GeminiRequestDto =
    GeminiRequestDto(
        contents = contents.map { it.toDto() }
    )
fun GeminiRequestDto.toDomain(): GeminiRequest =
    GeminiRequest(
        contents = contents.map { it.toDomain() }
    )

/* ---------------------------------------------------
   Gemini Response Mappers
--------------------------------------------------- */

fun GeminiResponseDto.toDomain(): GeminiResponse =
    GeminiResponse(
        candidates = candidates?.mapNotNull { it.toDomain() }.orEmpty(),
        usageMetadata = usageMetadata?.toDomain()
    )

fun CandidateDto.toDomain(): Candidate? {
    val content = content?.toDomain() ?: return null
    return Candidate(
        content = content,
        finishReason = finishReason
    )
}

fun UsageMetadataDto.toDomain(): UsageMetadata =
    UsageMetadata(
        totalTokenCount = totalTokenCount
    )

/* ---------------------------------------------------
   Message → Content (Conversation History)
--------------------------------------------------- */

fun Message.toContent(): Content =
    Content(
        role = role.toDtoRole(),
        parts = buildList {
            add(Part(text = text))

            imageBase64?.let {
                add(
                    Part(
                        inlineData = InlineData(
                            mimeType = "image/jpeg",
                            data = it
                        )
                    )
                )
            }
        }
    )

/* ---------------------------------------------------
   Conversation → GeminiRequest
--------------------------------------------------- */

fun List<Message>.toGeminiRequest(): GeminiRequest =
    GeminiRequest(
        contents = map { it.toContent() }
    )
