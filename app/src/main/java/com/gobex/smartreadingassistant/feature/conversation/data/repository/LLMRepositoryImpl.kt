package com.gobex.smartreadingassistant.feature.conversation.data.repository

import android.util.Log
import com.gobex.smartreadingassistant.core.util.Constants
import com.gobex.smartreadingassistant.feature.conversation.data.LLMApiService
import com.gobex.smartreadingassistant.feature.conversation.data.dto.ContentDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.FileDataRequestDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.GeminiRequestDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.GeminiResponseDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.InlineDataDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.PartDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.UsageMetadataDto
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationLocalDataSource
import com.gobex.smartreadingassistant.feature.conversation.domain.LLMRepository
import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.domain.StreamResult
import com.gobex.smartreadingassistant.feature.conversation.domain.UsageMetadata
import com.gobex.smartreadingassistant.feature.conversation.domain.toDomain
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Dispatcher
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject

class LLMRepositoryImpl @Inject constructor(
    private val apiService : LLMApiService,
    private val gson: Gson,
    private val localDb : ConversationLocalDataSource
) : LLMRepository
{
    override fun sendMessage(
        message: String,
        modelId: String,
        imageBase64: String?,
        conversationHistory: List<Message>
    ): Result<Message> {
        TODO("Not yet implemented")
    }

    override suspend fun getConversationHistory(): List<Message> {
        return localDb.getCurrentHistory()
    }

    override suspend fun streamMessage(
        message: String,
        modelId: String,
        imageBase64: String?,
        conversationHistory: List<Message>
    ): Flow<StreamResult> = flow {
        try {
            var currentFileUri: String? = null

            // ✅ Step 1: Determine which image URI to use
            if (imageBase64 != null) {
                // We have Base64 - check if already uploaded
                val existingMessage = conversationHistory.findLast {
                    it.imageBase64 == imageBase64 && it.fileUri != null
                }

                if (existingMessage != null) {
                    currentFileUri = existingMessage.fileUri
                    Log.d("LLMREPOSITORYIMPL", "♻️ REUSING URI (matched Base64): $currentFileUri")
                } else {
                    // Upload new image
                    Log.d("LLMREPOSITORYIMPL", "📤 Uploading new image...")
                    val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                    currentFileUri = uploadImageToGemini(imageBytes)

                    if (currentFileUri != null) {
                        Log.d("LLMREPOSITORYIMPL", "✅ Upload successful: $currentFileUri")
                    } else {
                        Log.e("LLMREPOSITORYIMPL", "❌ Upload failed!")
                    }
                }
            } else {
                // No Base64 provided - check if last message has an image URI we should include
                val lastImageMessage = conversationHistory.findLast {
                    it.role == MessageRole.USER && it.fileUri != null
                }

                if (lastImageMessage != null) {
                    currentFileUri = lastImageMessage.fileUri
                    Log.d("LLMREPOSITORYIMPL", "♻️ REUSING URI from conversation history: $currentFileUri")
                }
            }

            // ✅ Step 2: Create and save user message
            val userMessage = Message(
                role = MessageRole.USER,
                text = message,
                imageBase64 = imageBase64,
                fileUri = currentFileUri,
                timestamp = System.currentTimeMillis()
            )
            localDb.addMessage(userMessage)
            Log.d("LLMREPOSITORYIMPL", "💾 User message saved with URI: $currentFileUri")

            // ✅ Step 3: Build API request
            val contents = conversationHistory.map { domainMessage ->
                mapToContent(domainMessage)
            }.toMutableList()

            val currentParts = mutableListOf<PartDto>()

            // Add image if we have a URI
            if (currentFileUri != null) {
                Log.d("LLMREPOSITORYIMPL", "📎 Including image URI in API request")
                currentParts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", currentFileUri)))
            } else if (imageBase64 != null) {
                Log.d("LLMREPOSITORYIMPL", "⚠️ Fallback to Base64 (upload failed)")
                currentParts.add(PartDto(inlineData = InlineDataDto("image/jpeg", imageBase64)))
            }

            currentParts.add(PartDto(text = message))
            contents.add(ContentDto(role = "user", parts = currentParts))

            val requestDto = GeminiRequestDto(contents = contents)

            // ✅ Step 4: Call API
            val responseBody = apiService.streamGenerateContent(
                model = modelId,
                apiKey = Constants.API_KEY,
                request = requestDto
            )

            val fullResponse = StringBuilder()
            var usageMetadata: UsageMetadataDto? = null

            // ✅ Step 5: Stream response
            responseBody.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()

                        if (trimmed.startsWith("data: ")) {
                            val jsonData = trimmed.substring(6)

                            if (jsonData.isNotBlank() && jsonData != "[DONE]") {
                                try {
                                    val chunk = gson.fromJson(jsonData, GeminiResponseDto::class.java)
                                    val textChunk = chunk.candidates?.firstOrNull()
                                        ?.content?.parts?.firstOrNull()?.text

                                    if (textChunk != null) {
                                        fullResponse.append(textChunk)
                                        emit(StreamResult.Chunk(textChunk))
                                    }

                                    if (chunk.usageMetadata != null) {
                                        usageMetadata = chunk.usageMetadata
                                    }
                                } catch (e: Exception) {
                                    Log.e("LLMREPOSITORYIMPL", "❌ Parse error: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LLMREPOSITORYIMPL", "❌ Stream error: ${e.message}")
                    throw e
                }
            }

            // ✅ Step 6: Save assistant response
            val domainMetadata = usageMetadata?.toDomain()
            val fullMessage = Message(
                role = MessageRole.ASSISTANT,
                text = fullResponse.toString(),
                timestamp = System.currentTimeMillis(),
                totalTokenCount = domainMetadata?.totalTokenCount
            )

            localDb.addMessage(fullMessage)

            // ✅ Step 7: Return URI to ViewModel
            emit(StreamResult.Complete(
                fullMessage = fullMessage,
                metadata = domainMetadata,
                uploadedFileUri = currentFileUri
            ))

        } catch (e: Exception) {
            emit(StreamResult.Error(e))
            Log.e("LLMREPOSITORYIMPL", "❌ ERROR: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clearConversation() {
        localDb.clearHistory()
    }

    override suspend fun getUsageMetadata(): UsageMetadata? {
        val history = localDb.getCurrentHistory()

        val totalTokens = history.sumOf { it.totalTokenCount ?: 0 }

        return if( totalTokens > 0) UsageMetadata(totalTokens) else null
    }
    private suspend fun uploadImageToGemini(imageBytes: ByteArray): String? {
        return try {
            // Create RequestBody with proper metadata
            val displayName = "image_${System.currentTimeMillis()}.jpg"

            // Create metadata part
            val metadata = """{"file": {"displayName": "$displayName"}}"""
            val metadataBody = RequestBody.create("application/json".toMediaTypeOrNull(), metadata)
            val metadataPart = MultipartBody.Part.createFormData("metadata", null, metadataBody)

            // Create file part
            val fileBody = RequestBody.create("image/jpeg".toMediaTypeOrNull(), imageBytes)
            val filePart = MultipartBody.Part.createFormData("file", "upload.jpg", fileBody)

            // Upload with multipart
            val response = apiService.uploadFile(
                apiKey = Constants.API_KEY,
                uploadType = "multipart", // ✅ ADD THIS
                metadata = metadataPart,
                file = filePart
            )

            Log.d("LLMREPOSITORYIMPL", "Upload success: ${response.file.uri}")
            Log.d("LLMREPOSITORYIMPL", "Expires: ${response.file.expirationTime}")

            response.file.uri

        } catch (e: Exception) {
            Log.e("LLMREPOSITORYIMPL", "Upload failed: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    // purpose is to clean ][
    private fun cleanLine(line: String): String {
        var result = line.trim()
        if (result == "[" || result == "]") return "" // Skip brackets
        if (result.startsWith(",")) result = result.substring(1) // Remove leading comma
        if (result.endsWith(",")) result = result.dropLast(1) // Remove trailing comma
        return result.trim()
    }

    private fun mapToContent(message: Message): ContentDto {
        val parts = mutableListOf<PartDto>()

        // ✅ FIXED: Prioritize URI over Base64 - only use one!
        if (message.fileUri != null) {
            // We have a URI - use it and skip Base64
            parts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", message.fileUri)))
            Log.d("LLMREPOSITORYIMPL", "📎 mapToContent: Using URI (skipping Base64)")
        } else if (message.imageBase64 != null) {
            // No URI but have Base64 - use Base64
            parts.add(PartDto(inlineData = InlineDataDto("image/jpeg", message.imageBase64)))
            Log.d("LLMREPOSITORYIMPL", "📦 mapToContent: Using Base64 (no URI available)")
        }

        // Always add text
        parts.add(PartDto(text = message.text))

        val apiRole = if (message.role == MessageRole.USER) "user" else "model"
        return ContentDto(role = apiRole, parts = parts)
    }
}

