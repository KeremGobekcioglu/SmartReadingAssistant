package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gobex.smartreadingassistant.core.audio.SpeechToTextManager
import com.gobex.smartreadingassistant.core.audio.SttState
import com.gobex.smartreadingassistant.core.audio.TextToSpeechManager
import com.gobex.smartreadingassistant.core.connectivity.BleConnectionManager
import com.gobex.smartreadingassistant.core.connectivity.DeviceConnectionManager
import com.gobex.smartreadingassistant.core.connectivity.HotspotManager
import com.gobex.smartreadingassistant.core.connectivity.HotspotService
import com.gobex.smartreadingassistant.core.connectivity.NetworkScanner
import com.gobex.smartreadingassistant.feature.conversation.domain.LLMRepository
import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.domain.StreamResult
import com.gobex.smartreadingassistant.feature.device.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.jvm.java


// 1. The "Billboard": Persistent data the UI simply displays.
    @HiltViewModel
    class ConversationViewModel @Inject constructor(
    private val repository: LLMRepository,
        // IoT Dependencies
    private val connectionManager: DeviceConnectionManager,
    private val deviceRepository: DeviceRepository,
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TextToSpeechManager
    ) : ViewModel() {

    // ==================== UI State ====================
    private val _state = MutableStateFlow(ConversionState())
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    // One-shot Effects
    private val _uiEffect = Channel<ConversationEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    // ==================== Connection State (Observe from Manager) ====================
    val connectionState: StateFlow<DeviceConnectionManager.ConnectionState> = connectionManager.state
    val activeConnection = connectionManager.activeConnection
    val sttState = sttManager.state
    val connectionStatus: StateFlow<String> = connectionManager.state.map { state ->
        when (state) {
            is DeviceConnectionManager.ConnectionState.Disconnected -> "Ready to Connect"
            is DeviceConnectionManager.ConnectionState.Connecting -> state.step
            is DeviceConnectionManager.ConnectionState.Connected -> "Connected to ${state.ip}"
            is DeviceConnectionManager.ConnectionState.Error -> "Error: ${state.message}"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Ready to Connect"
    )

    val isDeviceConnected: StateFlow<Boolean> = connectionManager.state.map { state ->
        state is DeviceConnectionManager.ConnectionState.Connected
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val assignedIp: StateFlow<String?> = connectionManager.state.map { state ->
        (state as? DeviceConnectionManager.ConnectionState.Connected)?.ip
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
        init {
            loadHistory()
            observeStt()
        }

        // ====================================================================================
        //                                  IOT CONNECTION LOGIC
        // ====================================================================================
        // 1. Observe STT: When user finishes speaking, send to Gemini
        private fun observeStt() = viewModelScope.launch {
            sttManager.state.collect { sttState ->
                when (sttState) {
                    is SttState.Result -> {
                        stopListening() // Ensure UI updates
                        sendPrompt(sttState.text)
                    }
                    is SttState.Error -> {
                        _uiEffect.send(ConversationEffect.ShowError(sttState.error))
                    }
                    else -> {}
                }
            }
        }

    // 2. Audio Control Methods
    fun startListening() {
        ttsManager.stop() // Stop TTS if user wants to speak
        sttManager.startListening()
    }

    fun stopListening() {
        sttManager.stopListening()
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }
    fun connectToSmartGlasses() = viewModelScope.launch {
        try {
            connectionManager.connect()
        } catch (e: Exception) {
            _uiEffect.send(ConversationEffect.ShowError(e.message ?: "Connection failed"))
        }
    }

    fun disconnectFromSmartGlasses() = viewModelScope.launch {
        connectionManager.disconnect()
    }
        // ====================================================================================
        //                                  HARDWARE CAPTURE
        // ====================================================================================

        fun captureAndAnalyze() = viewModelScope.launch {
//            if (!_isDeviceConnected.value && _assignedIp.value == null) {
//                _uiEffect.send(ConversationEffect.ShowError("Glasses not connected"))
//                return@launch
//            }

            _state.update { it.copy(isStreaming = true, currentText = "Capturing photo...") }

            // 1. Capture from ESP32
            val result = deviceRepository.captureImage()

            result.onSuccess { imageBytes ->

                _state.update { it.copy(capturedImageBytes = imageBytes) }
                // 2. Convert to Base64
                val base64 =
                    android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

                // 3. Send to Gemini
                _state.update { it.copy(currentText = "Analyzing text...") }
                sendPrompt("Read this text for me.", base64)
            }

            result.onFailure {
                _state.update { it.copy(isStreaming = false, currentText = "") }
                _uiEffect.send(ConversationEffect.ShowError("Capture Failed: ${it.message}"))
            }
        }

        fun toggleFlash(isOn: Boolean) = viewModelScope.launch {
            deviceRepository.toggleFlash(isOn)
            _state.update { it.copy(isFlashOn = isOn) }
        }

        fun sendPrompt(text: String, imageBase64: String? = null) = viewModelScope.launch {
            if (text.isBlank()) return@launch

            // creating user message
            val userMessage = Message(
                role = MessageRole.USER,
                text = text,
                imageBase64 = imageBase64,
            )
            val currentMessages = _state.value.messages.toMutableList()
            currentMessages.add(userMessage)

            _state.update {
                it.copy(
                    messages = currentMessages,
                    isStreaming = true,
                    currentText = ""
                )
            }

            processStream(text, imageBase64, currentMessages)
        }

        private suspend fun processStream(
            text: String,
            imageBase64: String? = null,
            messages: List<Message>
        ) {
            val speechBuffer = StringBuilder()
            try {
                // This opens a pipe to llm api.
                // We use collect since it is a flow and flow keeps returning data over and over.
                repository.streamMessage(
                    message = text,
                    conversationHistory = messages,
                    imageBase64 = imageBase64,
                ).collect { result ->
                    when (result) {
                        is StreamResult.Error -> {
                            _state.update { it.copy(isStreaming = false) }
                            _uiEffect.send(
                                ConversationEffect.ShowError(
                                    result.exception.message ?: "An error occured"
                                )
                            )
                        }

                        is StreamResult.Complete -> {
                            // in here we copy the old list and add the final message to it.
                            if (speechBuffer.isNotEmpty()) {
                                ttsManager.speak(speechBuffer.toString())
                                speechBuffer.clear()
                            }
                            _state.update { state ->
                                val updatedList = state.messages.toMutableList()
                                updatedList.add(result.fullMessage)
                                state.copy(
                                    messages = updatedList, // we save the new list
                                    isStreaming = false,
                                    currentText = "" // we clear the buffer
                                )
                            }
                        }

                        is StreamResult.Chunk -> {
                            _state.update { state ->
                                state.copy(currentText = state.currentText + result.text)
                            }
                            // B. Buffer for Audio (Wait for sentence)
                            speechBuffer.append(result.text)
                            checkForSentenceAndSpeak(speechBuffer)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isStreaming = false) }
                _uiEffect.send(
                    ConversationEffect.ShowError(
                        e.localizedMessage ?: "Connection failed"
                    )
                )
            }
        }

        private fun loadHistory() = viewModelScope.launch {
            _state.update { it.copy(isLoadingHistory = true) }

            try {
                val messageHistory = repository.getConversationHistory()
                _state.update {
                    it.copy(
                        messages = messageHistory,
                        isLoadingHistory = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingHistory = false) }
                _uiEffect.send(ConversationEffect.ShowError(e.message))
            }
        }
    fun clearImagePreview() {
        _state.update { it.copy(capturedImageBytes = null) }
    }
        fun clearConversation() = viewModelScope.launch {
//        repository.clearConversation()
            _state.update { it.copy(messages = emptyList()) }
            _uiEffect.send(ConversationEffect.SpeakText("Conversation cleared"))
        }

        fun getUsageStats(): UsageStats {
            val messages = _state.value.messages

            // Count API requests (each USER message = 1 API call)
            val totalApiCalls = messages.count { it.role == MessageRole.USER }

            // Calculate today's requests (resets at midnight)
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayApiCalls = messages.count {
                it.role == MessageRole.USER && it.timestamp >= todayStart
            }

            // Calculate token usage
            val totalTokens = messages.sumOf { it.totalTokenCount ?: 0 }
            val todayTokens = messages
                .filter { it.timestamp >= todayStart }
                .sumOf { it.totalTokenCount ?: 0 }

            // Separate input vs output tokens (ASSISTANT messages have the full count)
            val inputTokens = messages
                .filter { it.role == MessageRole.USER }
                .sumOf { it.totalTokenCount ?: 0 }

            val outputTokens = messages
                .filter { it.role == MessageRole.ASSISTANT }
                .sumOf { it.totalTokenCount ?: 0 }

            return UsageStats(
                totalApiCalls = totalApiCalls,
                todayApiCalls = todayApiCalls,
                totalTokens = totalTokens,
                todayTokens = todayTokens,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                estimatedCost = calculateCost(inputTokens, outputTokens)
            )
        }

        private fun calculateCost(inputTokens: Int, outputTokens: Int): Double {
            // Gemini 2.5 Flash pricing
            val inputCostPer1M = 0.075 // $0.075 per 1M input tokens
            val outputCostPer1M = 0.30  // $0.30 per 1M output tokens

            val inputCost = (inputTokens / 1_000_000.0) * inputCostPer1M
            val outputCost = (outputTokens / 1_000_000.0) * outputCostPer1M

            return inputCost + outputCost
        }
    // Helper: Looks for punctuation, speaks the sentence, removes it from buffer
    private fun checkForSentenceAndSpeak(buffer: StringBuilder) {
        val text = buffer.toString()
        // Regex looks for [.!?] followed by a space or end of line
        val match = Regex("""([.!?])\s""").find(text)

        if (match != null) {
            val endParams = match.range.last // Index of the space after punctuation

            // Extract the full sentence
            val sentence = text.take(endParams)

            // Speak it!
            ttsManager.speak(sentence)

            // Remove it from buffer, keeping the rest for the next chunk
            buffer.delete(0, endParams + 1) // +1 to remove the split character
        }
    }
    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
    }


data class UsageStats(
    val totalApiCalls: Int,
    val todayApiCalls: Int,
    val totalTokens: Int,
    val todayTokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCost: Double
)