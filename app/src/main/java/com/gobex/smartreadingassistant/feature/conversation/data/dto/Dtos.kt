package com.gobex.smartreadingassistant.feature.conversation.data.dto
import com.google.gson.annotations.SerializedName

// The top-level Request
data class GeminiRequestDto(
    @SerializedName("contents") val contents: List<ContentDto>
)

// The top-level Response (Chunk)
data class GeminiResponseDto(
    @SerializedName("candidates") val candidates: List<CandidateDto>?,
    @SerializedName("usageMetadata") val usageMetadata: UsageMetadataDto?
)

data class CandidateDto(
    @SerializedName("content") val content: ContentDto?,
    @SerializedName("finishReason") val finishReason: String?
)

data class ContentDto(
    @SerializedName("role") val role: String, // "user" or "model"
    @SerializedName("parts") val parts: List<PartDto>
)

data class InlineDataDto(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String // Raw Base64 string
)

data class UsageMetadataDto(
    @SerializedName("totalTokenCount") val totalTokenCount: Int
)

data class FileUploadResponse(
    @SerializedName("file") val file: UploadedFileResponseDto
)

data class PartDto(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: InlineDataDto? = null,
    // Add the correct DTO for referencing an uploaded file
    @SerializedName("fileData") val fileData: FileDataRequestDto? = null
)
data class UploadedFileResponseDto(
    @SerializedName("uri") val uri: String,
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String,
    // (There might be other fields like displayName, expirationTime, etc.)
)
data class FileDataRequestDto(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("fileUri") val fileUri: String // Use fileUri for the request
)