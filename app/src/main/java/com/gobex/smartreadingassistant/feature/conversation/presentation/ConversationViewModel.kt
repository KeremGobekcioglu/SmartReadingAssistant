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
import android.util.Base64
import android.util.Log

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
    val connectionState: StateFlow<DeviceConnectionManager.ConnectionState> =
        connectionManager.state
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
    val isBluetoothEnabled: StateFlow<Boolean> = connectionManager.isBluetoothEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Assume true until the first check
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
        observeSttWithCommands()
    }

    // ====================================================================================
    //                                  IOT CONNECTION LOGIC
    // ====================================================================================
    // 1. Observe STT: When user finishes speaking, send to Gemini
    private fun observeSttWithCommands() = viewModelScope.launch {
        sttManager.state.collect { sttState ->
            when (sttState) {
                is SttState.Listening -> {
                    announceState(AppState.Listening)
                }

                is SttState.Result -> {
                    stopListening()
                    announceState(AppState.Processing)

                    // Parse for voice commands if in accessibility mode
                    if (_state.value.isAccessibilityMode) {
                        handleVoiceCommand(sttState.text)
                    } else {
                        // Regular mode - send directly to AI
                        val imageBase64 = _state.value.capturedImageBytes?.let {
                            Base64.encodeToString(it, Base64.NO_WRAP)
                        }
                        sendPrompt(sttState.text, imageBase64)
                    }
                }

                is SttState.Error -> {
                    announceState(AppState.Error(sttState.error))
                    _uiEffect.send(ConversationEffect.ShowError(sttState.error))
                }

                else -> {}
            }
        }
    }
    // Add a function to just hide the dialog
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
    fun captureImageOnly() = viewModelScope.launch {
        announceState(AppState.Capturing("Capturing photo"))
        _state.update {
            it.copy(
                isStreaming = true,
                currentText = "Capturing photo...",
                currentImageUri = null  // ✅ Clear old URI when taking new photo
            )
        }

        val result = deviceRepository.captureImage()

        result.onSuccess { imageBytes ->
            _state.update {
                it.copy(
                    capturedImageBytes = imageBytes,
                    isStreaming = false,
                    isImageDialogVisible = true,
                    currentText = "",
                    currentImageUri = null  // ✅ Reset URI for new image
                )
            }
            announceAction("Photo captured. What would you like to know about it?")
            delay(2500)
        }

        result.onFailure {
            announceState(AppState.Error("Capture failed: ${it.message}"))
            _state.update { it.copy(isStreaming = false, currentText = "") }
            _uiEffect.send(ConversationEffect.ShowError("Capture Failed: ${it.message}"))
        }
    }

    // Update clearCapturedImage
    fun clearCapturedImage() {
        _state.update {
            it.copy(
                capturedImageBytes = null,
                currentImageUri = null  // ✅ Clear URI too
            )
        }
        if (_state.value.isVoiceAnnouncementsEnabled) {
            announceAction("Image cleared", withHaptic = false)
        }
    }
    fun captureAndAnalyze() = viewModelScope.launch {
//            if (!_isDeviceConnected.value && _assignedIp.value == null) {
//                _uiEffect.send(ConversationEffect.ShowError("Glasses not connected"))
//                return@launch
//            }
        announceState(AppState.Capturing("Capturing photo"))
        _state.update { it.copy(isStreaming = true, currentText = "Capturing photo...") }

        // 1. Capture from ESP32
        val result = deviceRepository.captureImage()

        result.onSuccess { imageBytes ->
            announceState(AppState.Capturing("Photo captured, analyzing"))
            _state.update { it.copy(capturedImageBytes = imageBytes , isImageDialogVisible = true,) }

            delay(2500)  // Wait for dialog to close first

            _state.update { it.copy(isImageDialogVisible = false) }  // Hide dialog
            // 2. Convert to Base64
            val base64 =
                android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

            // 3. Send to Gemini
            _state.update { it.copy(currentText = "Analyzing text...") }
            sendPrompt("Explain this image to me. What objects you detected, what is the context. If it contains text, extract it.", base64)
        }

        result.onFailure {
            announceState(AppState.Error("Capture failed: ${it.message}"))
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

    fun resetSttState() {
        // This stops the LaunchedEffect from seeing "Result" a second time
        sttManager.resetState()
    }

    private suspend fun processStream(
        text: String,
        imageBase64: String? = null,
        messages: List<Message>
    ) {
        val speechBuffer = StringBuilder()
        try {
            repository.streamMessage(
                message = text,
                conversationHistory = messages,
                imageBase64 = imageBase64,
            ).collect { result ->
                when (result) {
                    is StreamResult.Complete -> {
                        if (speechBuffer.isNotEmpty()) {
                            ttsManager.speak(speechBuffer.toString())
                            speechBuffer.clear()
                        }

                        // ✅ Save the URI if image was uploaded
                        if (result.uploadedFileUri != null) {
                            Log.d("VIEWMODEL", "💾 Received URI from repository: ${result.uploadedFileUri}")
                            _state.update {
                                Log.d("VIEWMODEL", "💾 Saving URI to state...")
                                it.copy(currentImageUri = result.uploadedFileUri)
                            }
                            Log.d("VIEWMODEL", "💾 URI saved. Current state URI: ${_state.value.currentImageUri}")
                        } else {
                            Log.d("VIEWMODEL", "⚠️ No URI received from repository")
                        }

                        _state.update { state ->
                            val updatedList = state.messages.toMutableList()
                            updatedList.add(result.fullMessage)
                            state.copy(
                                messages = updatedList,
                                isStreaming = false,
                                currentText = ""
                            )
                        }
                    }

                    is StreamResult.Chunk -> {
                        _state.update { state ->
                            state.copy(currentText = state.currentText + result.text)
                        }
                        speechBuffer.append(result.text)
                        if (result.text.contains(" ") || result.text.contains("\n")) {
                            val wordToSpeak = speechBuffer.toString()
                            if (wordToSpeak.isNotBlank()) {
                                ttsManager.speak(wordToSpeak)
                                speechBuffer.clear()
                            }
                        }
                    }

                    is StreamResult.Error -> {
                        _state.update { it.copy(isStreaming = false) }
                        _uiEffect.send(
                            ConversationEffect.ShowError(
                                result.exception.message ?: "An error occurred"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(isStreaming = false) }
            _uiEffect.send(ConversationEffect.ShowError(e.localizedMessage ?: "Connection failed"))
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
    // ===== Voice Announcement System =====

    fun enableAccessibilityMode() {
        _state.update { it.copy(isAccessibilityMode = true, isVoiceAnnouncementsEnabled = true) }
        announceState(AppState.Idle)
    }

    fun disableAccessibilityMode() {
        _state.update { it.copy(isAccessibilityMode = false, isVoiceAnnouncementsEnabled = false) }
        ttsManager.stop()
    }

    fun enableVoiceAnnouncements() {
        _state.update { it.copy(isVoiceAnnouncementsEnabled = true) }
    }

    fun disableVoiceAnnouncements() {
        _state.update { it.copy(isVoiceAnnouncementsEnabled = false) }
    }

    /**
     * Announces app state changes via TTS
     * Only announces if voice announcements are enabled and state has changed
     */
    private fun announceState(newState: AppState) {
        val currentState = _state.value

        // Don't announce if disabled
        if (!currentState.isVoiceAnnouncementsEnabled) return

        // Don't announce same state twice
        if (currentState.lastAnnouncedState == newState) return

        // Update state
        _state.update { it.copy(currentAppState = newState, lastAnnouncedState = newState) }

        // Get announcement text
        val announcement = newState.toAnnouncement()
        if (announcement.isBlank()) return

        // Interrupt current speech for high priority states
        if (newState.priority() == AppState.Priority.HIGH) {
            ttsManager.stop()
        }

        // Speak announcement
        viewModelScope.launch {
            _uiEffect.send(ConversationEffect.AnnounceState(newState, interrupt = newState.priority() == AppState.Priority.HIGH))
        }

        ttsManager.speak(announcement)
    }

    /**
     * Announces a custom action message
     */
    fun announceAction(message: String, withHaptic: Boolean = true) {
        if (!_state.value.isVoiceAnnouncementsEnabled) return

        viewModelScope.launch {
            _uiEffect.send(ConversationEffect.AnnounceAction(message, withHaptic))
        }

        ttsManager.speak(message)
    }

    /**
     * Start monitoring connection state and announce changes
     */
    fun startConnectionAnnouncements() = viewModelScope.launch {
        connectionManager.state.collect { state ->
            val appState = when (state) {
                is DeviceConnectionManager.ConnectionState.Disconnected -> AppState.Disconnected
                is DeviceConnectionManager.ConnectionState.Connecting -> AppState.Connecting
                is DeviceConnectionManager.ConnectionState.Connected -> AppState.Connected(state.ip)
                is DeviceConnectionManager.ConnectionState.Error -> AppState.Error(state.message)
            }
            announceState(appState)
        }
    }


    /**
     * Handles voice commands or sends to AI
     */
    private fun handleVoiceCommand(text: String) {
        val command = VoiceCommandParser.parse(text)
//        _state.update { it.copy(isImageDialogVisible = false) }
        Log.d("VIEWMODAL HANDLE VOICE COMMAND" , "STT TEXT IS THIS : $text")
        when (command) {
            is VoiceCommand.CapturePhoto -> {
                announceAction("Capturing photo")
//                captureAndAnalyze()
                captureImageOnly()
            }

            is VoiceCommand.ToggleFlash -> {
                val newState = !_state.value.isFlashOn
                toggleFlash(newState)
                announceAction("Flash turned ${if (newState) "on" else "off"}")
            }

            is VoiceCommand.SetFlash -> {
                toggleFlash(command.on)
                announceAction("Flash turned ${if (command.on) "on" else "off"}")
            }

            is VoiceCommand.StopSpeaking -> {
                stopSpeaking()
                announceAction("Stopped", withHaptic = false)
            }

            is VoiceCommand.RepeatLast -> {
                repeatLastResponse()
            }

            is VoiceCommand.ClearConversation -> {
                clearConversation()
                clearCapturedImage()
                announceAction("Conversation cleared")
            }

            is VoiceCommand.SendToAI -> {
                _state.update { it.copy(isImageDialogVisible = false) }

                // ✅ Smart image handling: Reuse URI if available
                val currentUri = _state.value.currentImageUri
                val imageBytes = _state.value.capturedImageBytes

                Log.d("VoiceCommand", "=== IMAGE STATE CHECK ===")
                Log.d("VoiceCommand", "currentUri: $currentUri")
                Log.d("VoiceCommand", "imageBytes: ${imageBytes?.size ?: "null"}")
                Log.d("VoiceCommand", "========================")

                when {
                    // Case 1: We have a URI from previous upload - DON'T send Base64
                    currentUri != null -> {
                        Log.d("VoiceCommand", "♻️ Reusing existing URI: $currentUri")
                        announceAction("Processing your question")
                        // Send text only, URI will be found in conversation history
                        sendPrompt(command.text, null)
                    }
                    // Case 2: We have image bytes but no URI yet - first question
                    imageBytes != null -> {
                        Log.d("VoiceCommand", "📤 First question with image, will upload")
                        announceAction("Processing your question with the captured image")
                        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                        sendPrompt(command.text, imageBase64)
                    }
                    // Case 3: No image at all
                    else -> {
                        Log.d("VoiceCommand", "💬 Text-only question")
                        sendPrompt(command.text, null)
                    }
                }
            }
        }
    }

    /**
     * Repeats the last AI response
     */
    private fun repeatLastResponse() {
        val lastAssistantMessage = _state.value.messages
            .lastOrNull { it.role == MessageRole.ASSISTANT }

        if (lastAssistantMessage != null) {
            announceAction("Repeating")
            ttsManager.speak(lastAssistantMessage.text)
        } else {
            announceAction("No previous response to repeat")
        }
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
    fun dismissImageDialog() {
        val before = _state.value.isImageDialogVisible
        Log.d("VIEWMODEL_DISMISS", "BEFORE: $before")

        val newState = _state.value.copy(isImageDialogVisible = false)
        Log.d("VIEWMODEL_DISMISS", "NEW STATE CREATED: ${newState.isImageDialogVisible}")

        _state.value = newState

        val after = _state.value.isImageDialogVisible
        Log.d("VIEWMODEL_DISMISS", "AFTER: $after")
    }
    fun setImageBytes(bytes: ByteArray?) {
        _state.update { it.copy(capturedImageBytes = bytes) }
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