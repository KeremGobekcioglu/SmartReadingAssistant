package com.gobex.smartreadingassistant.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechManager, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingSpeech = mutableListOf<String>()
    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localeTR = Locale("tr", "TR")
            val result = tts?.setLanguage(localeTR)

            // 1. Determine the language first
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Turkish not supported, falling back to US")
                tts?.language = Locale.US
            }

            // 2. Mark as ready BEFORE speaking the queue
            isInitialized = true

            // 3. Now speak whatever was waiting
            synchronized(pendingSpeech) {
                pendingSpeech.forEach { speak(it) }
                pendingSpeech.clear()
            }
        } else {
            Log.e("TTS", "Initialization failed with status: $status")
        }
    }

    override fun speak(text: String) {
        Log.d("TTS", "Speak requested. Initialized: $isInitialized, Text: $text")
        if (isInitialized) {
            val params = android.os.Bundle()
            // Use the Media stream (the one controlled by volume buttons)
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)

            tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "utteranceId")
        }
        else {
            // Keep the text in a list if the engine isn't ready yet
            synchronized(pendingSpeech) {
                pendingSpeech.add(text)
            }
        }
    }


    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
    }
}