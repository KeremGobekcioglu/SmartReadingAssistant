package com.gobex.smartreadingassistant.core.audio



import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechToTextManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextManager {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()
    private var lastResult: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)

    // SpeechRecognizer must run on Main Thread
    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)
            } else {
                _state.update { SttState.Error("Speech recognition not available") }
            }
        }
    }
    override fun resetState() {
        lastResult = null  // Clear the cache
        _state.update { SttState.Idle }
    }
    override fun startListening() {
        mainScope.launch {
            ensureRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

                // Set the primary language (e.g., English)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")

                // Allow the recognizer to also consider Turkish
                // Note: Support for multiple simultaneous languages varies by Android version/Google App version
                val preferredLanguages = arrayListOf("en-US", "tr-TR")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, preferredLanguages)

                // Hint for the recognizer to expect these languages
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-US")

                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            _state.update { SttState.Listening }
        }
    }

    override fun stopListening() {
        mainScope.launch {
            speechRecognizer?.stopListening()
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {} // Wait for results

        override fun onError(error: Int) {
            val message = when(error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                5 -> "Client Error" // Error 5
                else -> "Error: $error"
            }
            // Ignore trivial errors
            when (error) {
                // 1. Handle Error 5: Update state but tell UI to be silent
                5 -> {
                    _state.update { SttState.Error(message, shouldSpeak = false) }
                }

                // 2. Handle Trivial Errors: Just go back to Idle
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    _state.update { SttState.Idle }
                }

                // 3. Handle All Other Errors: Update state and allow speaking
                else -> {
                    _state.update { SttState.Error(message, shouldSpeak = true) }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val result = matches[0]
                // Only emit if it's a new result
                if (result != lastResult) {
                    lastResult = result
                    _state.update { SttState.Result(result) }
                }
            } else {
                _state.update { SttState.Idle }
            }
        }


        override fun onPartialResults(partialResults: Bundle?) {
            // Optional: Update UI with partial text here if desired
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}