package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gobex.smartreadingassistant.core.connectivity.BleConnectionManager
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
        private val hotspotManager: HotspotManager,
        private val bleManager: BleConnectionManager,
        private val networkScanner: NetworkScanner,
        private val deviceRepository: DeviceRepository,
    @ApplicationContext private val context: Context
    ) : ViewModel() {

        // 1. UI State
        private val _state = MutableStateFlow(ConversionState())
        val state: StateFlow<ConversionState> = _state.asStateFlow()

        // 2. One-shot Effects
        private val _uiEffect = Channel<ConversationEffect>()
        public val uiEffect = _uiEffect.receiveAsFlow()

        // 3. Connection Specific State
        private val _isDeviceConnected = MutableStateFlow(false)
        public val isDeviceConnected = _isDeviceConnected.asStateFlow()

        private val _connectionStatus = MutableStateFlow("Ready to Connect")
        val connectionStatus = _connectionStatus.asStateFlow()
    private val _assignedIp = MutableStateFlow<String?>(null)
    val assignedIp: StateFlow<String?> = _assignedIp.asStateFlow()
        init {
            loadHistory()
        }

        // ====================================================================================
        //                                  IOT CONNECTION LOGIC
        // ====================================================================================

    fun connectToSmartGlasses() = viewModelScope.launch {
        // Start the Foreground Service for visual notification
        val serviceIntent = Intent(context, HotspotService::class.java)
        context.startService(serviceIntent)

        _connectionStatus.value = "Starting Hotspot..."

        try {
            // Collect the Hotspot credentials (SSID/PASS)
            hotspotManager.startHotspot().collect { creds ->
                _connectionStatus.value = "Sending Credentials (BLE)..."

                // 1. Send credentials to ESP32 via BLE
                bleManager.startConnectionSequence(creds.ssid, creds.pass)

                // 2. WAIT for the ESP32 to send its IP back via BLE (15s timeout)
                val ipViaBle = withTimeoutOrNull(15_000) {
                    bleManager.deviceIpFlow.first { it.isNotEmpty() }
                }

                if (ipViaBle != null) {
                    finalizeConnection(ipViaBle, serviceIntent)
                } else {
                    // 3. FALLBACK: If BLE fails/times out, start polling the network
                    _connectionStatus.value = "BLE Timeout. Scanning Network..."

                    val scannedIp = scanForDeviceWithRetry(maxDurationMs = 15_000)

                    if (scannedIp != null) {
                        finalizeConnection(scannedIp, serviceIntent)
                    } else {
                        handleConnectionFailure(serviceIntent, "Could not find glasses on network.")
                    }
                }
            }
        } catch (e: Exception) {
            handleConnectionFailure(serviceIntent, "Hotspot Error: ${e.message}")
        }
    }

    /**
     * Retries scanning the subnet for the ESP32.
     * This is needed because the ESP32 might still be connecting to WiFi when the first scan runs.
     */
    private suspend fun scanForDeviceWithRetry(maxDurationMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            val ip = networkScanner.findEsp32Ip()
            if (ip != null) return ip
            delay(2000) // Wait 2 seconds before trying again
        }
        return null
    }

    private fun finalizeConnection(ip: String, serviceIntent: Intent) {
        // Save the IP so the UI can show it
        _assignedIp.value = ip

        deviceRepository.connectToDeviceServer(ip)
        _isDeviceConnected.value = true
        _connectionStatus.value = "Connected to $ip" // Show IP in status

        // Stop the notification service
        context.stopService(serviceIntent)

        // REMOVED: The automatic navigation effect so user can see the IP first
        // viewModelScope.launch {
        //    _uiEffect.send(ConversationEffect.NavigateToChat)
        // }
    }

    private suspend fun handleConnectionFailure(serviceIntent: Intent, errorMsg: String) {
        _connectionStatus.value = "Connection Failed"
        context.stopService(serviceIntent)
        hotspotManager.stopHotspot()
        _uiEffect.send(ConversationEffect.ShowError(errorMsg))
    }
        // ====================================================================================
        //                                  HARDWARE CAPTURE
        // ====================================================================================

        fun captureAndAnalyze() = viewModelScope.launch {
            if (!_isDeviceConnected.value && _assignedIp.value == null) {
                _uiEffect.send(ConversationEffect.ShowError("Glasses not connected"))
                return@launch
            }

            _state.update { it.copy(isStreaming = true, currentText = "Capturing photo...") }

            // 1. Capture from ESP32
            val result = deviceRepository.captureImage()

            result.onSuccess { imageBytes ->
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