package com.gobex.smartreadingassistant.feature.conversation.data

import com.gobex.smartreadingassistant.feature.conversation.data.dto.FileUploadResponse
import com.gobex.smartreadingassistant.feature.conversation.data.dto.GeminiRequestDto
import com.gobex.smartreadingassistant.feature.conversation.data.dto.GeminiResponseDto
import com.gobex.smartreadingassistant.feature.conversation.domain.GeminiRequest
import com.gobex.smartreadingassistant.feature.conversation.domain.GeminiResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface LLMApiService {

    // Non-streaming (simple, complete response)
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequestDto
    ): GeminiResponseDto

    // Streaming (real-time chunks)
    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequestDto
    ): ResponseBody

    // NEW: File Upload Endpoint
    @Multipart
    @POST("upload/v1beta/files")
    suspend fun uploadFile(
        @Query("key") apiKey: String,
        @Part file: MultipartBody.Part
    ): FileUploadResponse
}