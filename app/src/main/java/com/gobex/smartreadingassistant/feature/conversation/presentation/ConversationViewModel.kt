package com.gobex.smartreadingassistant.feature.conversation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gobex.smartreadingassistant.feature.conversation.domain.LLMRepository
import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.domain.StreamResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repository : LLMRepository
) : ViewModel() {

    // 1. The "Billboard": Persistent data the UI simply displays.
    private val _state = MutableStateFlow(ConversionState())
    val state : StateFlow<ConversionState> = _state.asStateFlow()

    // 2. The "Walkie-Talkie": One-shot commands.
    private val _uiEffect =  Channel<ConversationEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        loadHistory()
    }

    fun sendPrompt(text : String, imageBase64: String? = null) = viewModelScope.launch {
        if(text.isBlank()) return@launch

        // creating user message
        val userMessage = Message(
            role = MessageRole.USER,
            text = text,
            imageBase64 = imageBase64,
        )
        val currentMessages = _state.value.messages.toMutableList()
        currentMessages.add(userMessage)

        _state.update { it.copy(
            messages = currentMessages,
            isStreaming = true,
            currentText = ""
        ) }

        processStream(text, imageBase64, currentMessages)
    }

    private suspend fun processStream(text : String , imageBase64 : String? = null , messages : List<Message>)
    {
        try {
            // This opens a pipe to llm api.
            // We use collect since it is a flow and flow keeps returning data over and over.
            repository.streamMessage(
                message = text,
                conversationHistory = messages,
                imageBase64 = imageBase64,
            ).collect { result ->
                when (result){
                is StreamResult.Error -> {
                    _state.update {it.copy(isStreaming = false)}
                    _uiEffect.send(ConversationEffect.ShowError(result.exception.message ?: "An error occured"))
                }
                is StreamResult.Complete -> {
                    // in here we copy the old list and add the final message to it.
                    _state.update {
                        state ->
                        val updatedList = state.messages.toMutableList()
                        updatedList.add(result.fullMessage)
                        state.copy(
                            messages = updatedList, // we save the new list
                            isStreaming = false,
                            currentText = "" // we clear the buffer
                        )
                    }
                }
                is StreamResult.Chunk ->{
                    _state.update {
                        state -> state.copy(currentText = state.currentText + result.text)
                    }
                }
            }
            }
        }
        catch (e : Exception){
            _state.update { it.copy(isStreaming = false) }
            _uiEffect.send(ConversationEffect.ShowError(e.localizedMessage ?: "Connection failed"))
        }
    }
    private fun loadHistory() = viewModelScope.launch {
        _state.update { it.copy(isLoadingHistory = true) }

        try {
            val messageHistory = repository.getConversationHistory()
            _state.update { it.copy(
                messages = messageHistory,
                isLoadingHistory = false
            ) }
        }
        catch (e : Exception)
        {
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