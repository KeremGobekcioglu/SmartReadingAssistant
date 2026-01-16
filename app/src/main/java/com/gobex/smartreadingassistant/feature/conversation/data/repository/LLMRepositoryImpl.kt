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
import com.gobex.smartreadingassistant.feature.conversation.data.dto.SystemInstructionDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.UsageMetadataDto
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationLocalDataSource
import com.gobex.smartreadingassistant.feature.conversation.domain.LLMRepository
import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.domain.ResponseMode
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
import retrofit2.HttpException
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
        conversationHistory: List<Message>,
//        responseMode: ResponseMode = ResponseMode.NORMAL
    ): Flow<StreamResult> = flow {
        // Define fallback model chain
        val modelChain = listOf(
            modelId,
            "gemini-2.5-flash-lite",
            "gemini-3-flash"
        ).distinct() // Remove duplicates if modelId is already a fallback

        var lastError: Exception? = null
        var attemptedModels = mutableListOf<String>()

        for (currentModel in modelChain) {
            try {
                attemptedModels.add(currentModel)
                Log.d("LLMREPOSITORYIMPL", "🔄 Attempting model: $currentModel")

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

                // ✅ Step 2: Create and save user message (only on first attempt)
                if (attemptedModels.size == 1) {
                    val userMessage = Message(
                        role = MessageRole.USER,
                        text = message,
                        imageBase64 = imageBase64,
                        fileUri = currentFileUri,
                        timestamp = System.currentTimeMillis()
                    )
                    localDb.addMessage(userMessage)
                    Log.d("LLMREPOSITORYIMPL", "💾 User message saved with URI: $currentFileUri")
                }

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


//                val systemPrompt = SystemInstructionDto(
//                    parts = listOf(
//                        PartDto(
//                            text = """
//            You are a vision assistant for blind users. Your output is for Text-to-Speech.
//
//            CRITICAL LOGIC:
//            1. SCENE DESCRIPTION: If the image is a general scene, provide a 1-2 sentence overview.
//            2. TEXT DOMINANCE: If the image contains significant text (document, menu, sign, or labels), ignore the brevity rule and transcribe the text fully.
//            3. NO INTRODUCTIONS: Do not say "I see," "The image shows," or "The text says." Start immediately with the description or the first word of the text.
//            4. CLEAN OUTPUT: No markdown, no bullet points, no bolding (* or **). Use only plain text and natural punctuation for speech.
//
//            EXAMPLES:
//            - [Scene with a dog]: "A golden retriever is sitting on a sidewalk."
//            - [A Nutrition Label]: "Nutrition Facts. Serving size 1 cup. Calories 150. Total fat 2 grams..."
//            - [A Street Sign]: "Main Street. No parking from 2pm to 4pm."
//        """.trimIndent()
//                        )
//                    )
//                )

                val systemPrompt = SystemInstructionDto(
                    parts = listOf(
                        PartDto(
                            text = """
            PERSONA: Vision Assistant. Output is PLAIN TEXT ONLY. NO INTROS.
            
            LOGIC:
            1. ENVIRONMENT: Describe the setting in 1 short sentence (e.g., "You are in an office").
            2. SUMMARY: 
               - If text is short (sign/label): Read it.
               - If text is long (document/logs): Identify it and say "Say 'read all' to hear it fully."
            3. BREVITY: Keep general responses under 2 sentences. 
            
            EXCEPTION: If the user says "TRANSCRIPTION MODE", ignore Rule 3 and read everything.
            """.trimIndent()
                        )
                    )
                )

                val requestDto = GeminiRequestDto(
                    contents = contents,
                    systemInstruction = systemPrompt
                )

                // ✅ Step 4: Call API
                val responseBody = apiService.streamGenerateContent(
                    model = currentModel,
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
//                                            Log.d("LLMREPOSITORYIMPL", "❌ Parse error: ${e.message}")
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
                    totalTokenCount = domainMetadata?.totalTokenCount,
                )

                localDb.addMessage(fullMessage)

                // ✅ Step 7: Return URI to ViewModel
                Log.d("LLMREPOSITORYIMPL", "✅ SUCCESS with model: $currentModel")
                emit(StreamResult.Complete(
                    fullMessage = fullMessage,
                    metadata = domainMetadata,
                    uploadedFileUri = currentFileUri
                ))

                return@flow // Success - exit the flow

            } catch (e: Exception) {
                lastError = e
                val isRateLimit = (e as? HttpException)?.code() == 429
                val isServerError = (e as? HttpException)?.code()?.let { it in 500..599 } ?: false

                // Only fallback if it's a quota or server issue
                if ((isRateLimit || isServerError) && currentModel != modelChain.last()) {
                    Log.w("LLMREPOSITORYIMPL", "🔄 Quota hit or Server Error. Trying fallback...")
                    continue
                } else {
                    break // Stop immediately for safety/auth/input errors
                }
            }
        }

        // If we get here, all models failed
        val errorMessage = "All models failed. Attempted: ${attemptedModels.joinToString(", ")}"
        Log.e("LLMREPOSITORYIMPL", "❌ FINAL ERROR: $errorMessage")
        emit(StreamResult.Error(lastError ?: Exception(errorMessage)))
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

