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
    ): Flow<StreamResult>  = flow { // This flow will start the coroutine stream
        try {
            // Adding user message to local db so that even if api fails , we can see it.
            val userMessage = Message   (
                role = MessageRole.USER,
                text = message,
                imageBase64 = imageBase64,
                timestamp = System.currentTimeMillis()
            )
            localDb.addMessage(userMessage)
            var currentFileUri : String? = null

            val existingMessage = conversationHistory.lastOrNull {
                it.imageBase64 == imageBase64 && it.fileUri != null
            }
            // if we have an media to upload , i have to check it. also we need its uri so that we dont resend.
            if (existingMessage != null) {
                // Reuse existing URI
                currentFileUri = existingMessage.fileUri
                Log.d("LLMREPOSITORYIMPL", "Reusing existing URI: $currentFileUri")
            } else if (imageBase64 != null) {
                // Upload new image
                val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                currentFileUri = uploadImageToGemini(imageBytes)

                // ✅ Update the message with the URI
                if (currentFileUri != null) {
                    val updatedMessage = userMessage.copy(fileUri = currentFileUri)
//                    localDb.updateMessage(updatedMessage)
                }
                Log.d("LLMREPOSITORYIMPL", "Image uploaded. URI: $currentFileUri")
            }
            // B: Now our coversation will be turn to our dtos to upload.
            val contents = conversationHistory.map {
                domainMessage -> mapToContent(domainMessage)
            }.toMutableList()

            val currentParts = mutableListOf<PartDto>()
            // if we uplaod image ..
            // Add image ONLY if upload succeeded
            if (currentFileUri != null) {
                Log.d("LLMREPOSITORYIMPL", "Using uploaded file URI: $currentFileUri")
                currentParts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", currentFileUri)))
            }
            // Fallback to Base64 only if upload failed AND Base64 is available
            else if(imageBase64 != null) {
                Log.d("LLMREPOSITORYIMPL", "Upload failed, using Base64 fallback")
                currentParts.add(PartDto(inlineData = InlineDataDto("image/jpeg", imageBase64)))
            }
            currentParts.add(PartDto(text = message))
            contents.add(ContentDto(role = "user" , parts = currentParts))

            val requestDto = GeminiRequestDto(contents = contents)

            // PHASE 2: Call or request
                val responseBody = apiService.streamGenerateContent(
                    model = modelId,
                    apiKey = Constants.API_KEY,
                    request = requestDto
                )
            val fullResponse = StringBuilder()
            var usageMetadata : UsageMetadataDto? = null
            // Im opening the stream here
// ✅ FIXED: Unbuffered streaming
            responseBody.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                try {
                    while (true) {
                        val line = reader.readLine() ?: break // null = end of stream
                        val trimmed = line.trim()

                        Log.d("LLMREPOSITORYIMPL", "🔍 Raw line: $trimmed") // Debug log

                        if (trimmed.startsWith("data: ")) {
                            val jsonData = trimmed.substring(6)

                            if (jsonData.isNotBlank() && jsonData != "[DONE]") {
                                try {
                                    Log.d("LLMREPOSITORYIMPL", "⏱️ Parsing at ${System.currentTimeMillis()}")

                                    val chunk = gson.fromJson(jsonData, GeminiResponseDto::class.java)
                                    val textChunk = chunk.candidates?.firstOrNull()
                                        ?.content?.parts?.firstOrNull()?.text

                                    if (textChunk != null) {
                                        Log.d("LLMREPOSITORYIMPL", "📤 Emitting: '$textChunk'")
                                        fullResponse.append(textChunk)

                                        // 🔥 Emit immediately - don't wait!
                                        emit(StreamResult.Chunk(textChunk))

                                        Log.d("LLMREPOSITORYIMPL", "✅ Emitted at ${System.currentTimeMillis()}")
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
            val domainMetadata = usageMetadata?.toDomain()
            Log.d("LLMREPOSITORYIMPL", "✅ Domain metadata: $domainMetadata")
            val fullMessage = Message(
                role = MessageRole.ASSISTANT,
                text = fullResponse.toString(),
                timestamp = System.currentTimeMillis(),
                totalTokenCount = domainMetadata?.totalTokenCount
            )
            Log.d("LLMREPOSITORYIMPL", "✅ Full message created with tokens: ${fullMessage.totalTokenCount}")
            localDb.addMessage(fullMessage)

            emit(StreamResult.Complete(fullMessage = fullMessage, metadata = domainMetadata))
            }

        catch(e : Exception){
            emit(StreamResult.Error(e))
            Log.d("LLMREPOSITORYIMPL" , "STREAM MESSAGE CATCH BLOCK")
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

    private fun mapToContent(message : Message) : ContentDto
    {
        // we ll get
        val parts = mutableListOf<PartDto>()
        if (message.fileUri != null) {
            parts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", message.fileUri)))
        }
        else if(message.imageBase64 != null)
        {
            parts.add(PartDto(inlineData = InlineDataDto("image/jpeg" , message.imageBase64)))
        }
        parts.add(PartDto(text = message.text))

        val apiRole = if(message.role == MessageRole.USER) "user" else "model"
        return ContentDto(role = apiRole, parts = parts)
    }
}

