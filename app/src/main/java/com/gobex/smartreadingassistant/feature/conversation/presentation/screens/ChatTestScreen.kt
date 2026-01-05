package com.gobex.smartreadingassistant.feature.conversation.presentation.screens

import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationEffect
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationViewModel

import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.MessageBubble
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.StreamingBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gobex.smartreadingassistant.core.audio.SttState
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.CapturedImageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTestScreen(
    onNavigateToHistory: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sttState by viewModel.sttState.collectAsStateWithLifecycle(
        initialValue = SttState.Idle
    )
    // Side Effects
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is ConversationEffect.ShowError -> Toast.makeText(context, "Error: ${effect.message}", Toast.LENGTH_LONG).show()
                is ConversationEffect.SpeakText -> Toast.makeText(context, "TTS: ${effect.text.take(50)}...", Toast.LENGTH_SHORT).show()
                is ConversationEffect.NavigateToChat -> Toast.makeText(context, "NP" , Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-scroll
    LaunchedEffect(uiState.messages.size, uiState.currentText) {
        if (uiState.messages.isNotEmpty()) {
            val index = if (uiState.isStreaming) uiState.messages.size else uiState.messages.lastIndex
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    // Image Preview Dialog
    CapturedImageDialog(
        imageBytes = uiState.capturedImageBytes,
        onDismiss = { viewModel.clearImagePreview() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Test Console") },
                actions = {
                    val isFlashOn = uiState.isFlashOn
                    IconButton(onClick = { viewModel.toggleFlash(!isFlashOn) }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (isFlashOn) Color.Yellow else LocalContentColor.current
                        )
                    }

                    IconButton(onClick = { viewModel.clearConversation() }) {
                        Icon(Icons.Default.Delete, "Clear Chat")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "Debug DB")
                    }
                }
            )
        },
        bottomBar = {
            DeveloperInputBar(
                isStreaming = uiState.isStreaming,
                onSend = { text, uri ->
                    scope.launch(Dispatchers.IO) {
                        val base64 = uri?.let {
                            context.contentResolver.openInputStream(it)?.use { stream ->
                                val bytes = stream.readBytes()
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            viewModel.sendPrompt(text, base64)
                        }
                    }
                },
                onCapturePhoto = { viewModel.captureAndAnalyze() },
                sttState = sttState, // <--- Pass State
                onStartListening = { viewModel.startListening() }, // <--- Pass Action
                onStopListening = { viewModel.stopListening() },   // <--- Pass Action
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(uiState.messages) { msg ->
                MessageBubble(
                    message = msg,
                    isUser = msg.role == MessageRole.USER
                )
            }
            if (uiState.isStreaming) {
                item {
                    StreamingBubble(text = uiState.currentText)
                }
            }
        }
    }
}

// --- Developer Input Bar ---
@Composable
fun DeveloperInputBar(
    isStreaming: Boolean,
    sttState: SttState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSend: (String, Uri?) -> Unit,
    onCapturePhoto: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selectedUri = it
    }

    Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Image Preview
        if (selectedUri != null) {
            Row(
                Modifier.padding(8.dp).fillMaxWidth().background(Color.Black.copy(alpha=0.1f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📷 Image Selected", modifier = Modifier.padding(8.dp))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selectedUri = null }) { Text("Clear") }
            }
        }

        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isListening = sttState is SttState.Listening
            IconButton(
                onClick = { if (isListening) onStopListening() else onStartListening() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = "Voice Input"
                )
            }
            IconButton(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.AddPhotoAlternate, "Upload")
            }
            // --- CAPTURE PHOTO BUTTON (ESP32 TRIGGER) ---
            IconButton(
                onClick = onCapturePhoto,
                enabled = !isStreaming
            ) {
                Icon(Icons.Default.CameraAlt, "Capture from Glasses", tint = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Simulate STT input...") },
                maxLines = 3
            )

            IconButton(
                onClick = {
                    onSend(text, selectedUri)
                    text = ""
                    selectedUri = null
                },
                enabled = !isStreaming && text.isNotBlank()
            ) {
                if(isStreaming) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.Send, "Send")
            }
        }
    }
}