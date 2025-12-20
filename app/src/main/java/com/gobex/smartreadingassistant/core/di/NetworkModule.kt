package com.gobex.smartreadingassistant.core.di

import android.util.Log
import com.gobex.smartreadingassistant.core.util.Constants
import com.gobex.smartreadingassistant.feature.conversation.data.LLMApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule{


    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
//        val loggingInterceptor = HttpLoggingInterceptor { message ->
//            Log.d("OkHttp", message)
//        }.apply {
//            level = HttpLoggingInterceptor.Level.BODY
//        }

        return OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Disable read timeout for streaming
            .connectTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES) // Max time for entire request
            .writeTimeout(30, TimeUnit.SECONDS)
//            .addInterceptor(loggingInterceptor)
//            .addInterceptor { chain ->
//                val request = chain.request()
//                Log.d("NetworkRequest", "URL: ${request.url}")
//                Log.d("NetworkRequest", "Method: ${request.method}")
//                Log.d("NetworkRequest", "Headers: ${request.headers}")
//                val response = chain.proceed(request)
//                Log.d("NetworkResponse", "Code: ${response.code}")
//                Log.d("NetworkResponse", "Message: ${response.message}")
//                response
//            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS // ✅ Don't log body for streaming
            })
            .cache(null)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(httpClient: OkHttpClient) : Retrofit
    {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLLMApiService(retrofit : Retrofit) : LLMApiService
    {
        return retrofit.create(LLMApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient() // Optional: Helps with loose JSON from some APIs
            .create()
    }
}
