package com.gobex.smartreadingassistant.core.audio


import kotlinx.coroutines.flow.Flow

// STT Interface
sealed class SttState {
    data object Idle : SttState()
    data object Listening : SttState()
    data class Result(val text: String) : SttState()
    data class Error(val error: String, val shouldSpeak: Boolean? = true) : SttState()
}

interface SpeechToTextManager {
    val state: Flow<SttState>
    fun startListening()
    fun stopListening()

    fun resetState()
}

// TTS Interface
interface TextToSpeechManager {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}