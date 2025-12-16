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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            // if we have an media to upload , i have to check it. also we need its uri so that we dont resend.
            if(imageBase64 != null)
            {
                val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                currentFileUri = uploadImageToGemini(imageBytes)
            }
            // B: Now our coversation will be turn to our dtos to upload.
            val contents = conversationHistory.map {
                domainMessage -> mapToContent(domainMessage)
            }.toMutableList()

            val currentParts = mutableListOf<PartDto>()
            // if we uplaod image ..
            if (currentFileUri != null) {
                currentParts.add(PartDto(fileData = FileDataRequestDto("image/jpeg", currentFileUri)))
            }
            // If upload fails , we get the iamge base 64.
            else if(imageBase64 != null)
            {
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
            responseBody.source().use {
                source ->
                source.inputStream().bufferedReader().use {
                    reader ->
                    var line = reader.readLine()
                    while(line != null)
                    {
                        // Phase 3 : parsing

                        val cleanedLine = cleanLine(line)
                        if(cleanedLine.isNotBlank())
                        {
                            try {
                                val chunk = gson.fromJson(cleanedLine, GeminiResponseDto::class.java)
                                val textChunk = chunk.candidates?.firstOrNull()
                                    ?.content?.parts?.firstOrNull()?.text

                                // Text being received is sent to UI immediately
                                if(textChunk != null)
                                {
                                    fullResponse.append(textChunk)
                                    emit(StreamResult.Chunk(textChunk))
                                }
                                // If there is a ısage meta data , it is saved.
                                if (chunk.usageMetadata != null) {
                                    usageMetadata = chunk.usageMetadata
                                }
                            }
                            catch (e : Exception)
                            {
                                Log.d("LLMREPOSITORYIMPLGENERATESTREAM" , "${e.message}}")
                            }
                        }

                        line = reader.readLine()
                    }
                    // PHASE 4 : Finish
                }
            }
            val domainMetadata = usageMetadata?.toDomain()
            val fullMessage = Message(
                role = MessageRole.ASSISTANT,
                text = fullResponse.toString(),
                timestamp = System.currentTimeMillis(),
                totalTokenCount = domainMetadata?.totalTokenCount
            )
            localDb.addMessage(fullMessage)

            emit(StreamResult.Complete(fullMessage = fullMessage, metadata = domainMetadata))
            }

        catch(e : Exception){
            emit(StreamResult.Error(e))
            Log.d("LLMREPOSITORYIMPL" , "STREAM MESSAGE CATCH BLOCK")
        }
    }

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
            // Create RequestBody
            val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), imageBytes)
            val body = MultipartBody.Part.createFormData("file", "upload.jpg", requestFile)

            val response = apiService.uploadFile(Constants.API_KEY, body)
            response.file.uri
        } catch (e: Exception) {
            Log.d("LLMREPOSITORYIMPL", "{${e.message}}")
            e.printStackTrace()
            null // Fallback to Base64 if upload fails
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

