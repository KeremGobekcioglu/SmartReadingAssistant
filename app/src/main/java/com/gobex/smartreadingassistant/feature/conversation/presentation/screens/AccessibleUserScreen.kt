package com.gobex.smartreadingassistant.feature.conversation.presentation.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gobex.smartreadingassistant.core.audio.SttState
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationViewModel
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationEffect
import com.gobex.smartreadingassistant.feature.conversation.presentation.AppState
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.CapturedImageDialog
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.HardwareKeyHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccessibleUserScreen(
    viewModel: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.state.collectAsState()
    val sttState by viewModel.sttState.collectAsStateWithLifecycle(initialValue = SttState.Idle)
    val connectionState by viewModel.isDeviceConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ===== PUSH-TO-TALK: HARDWARE BUTTONS =====
    HardwareKeyHandler(
        onVolumeUp = { isLongPress ->
            // We only care about ACTION_DOWN (press) and ACTION_UP (release)
            // isLongPress here means "is currently being held"

            if (isLongPress) {
                // Button pressed down - START listening
                if (sttState !is SttState.Listening) {
                    vibrate(context, VibrationPattern.START_LISTENING)
                    viewModel.startListening()
                    Log.d("PushToTalk", "Volume Up PRESSED - Starting listening")
                }
            } else {
                // Button released - STOP listening
                if (sttState is SttState.Listening) {
                    vibrate(context, VibrationPattern.STOP_LISTENING)
                    viewModel.stopListening()
                    Log.d("PushToTalk", "Volume Up RELEASED - Stopping listening")
                }
            }
        },
        onVolumeDown = { isLongPress ->
            if (isLongPress) {
                // Long press: Capture photo
                vibrate(context, VibrationPattern.LONG_PRESS)
                viewModel.captureImageOnly()
                Log.d("PushToTalk", "Volume Down LONG PRESS - Capturing photo")
            }
            // Short press: Do nothing (allow volume control)
        }
    )

    // ===== HANDLE EFFECTS =====
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is ConversationEffect.ShowError -> {
                    // Errors announced via TTS
                }
                is ConversationEffect.AnnounceAction -> {
                    if (effect.withHaptic) {
                        vibrate(context, VibrationPattern.ACTION_CONFIRM)
                    }
                }
                else -> {}
            }
        }
    }

    // ===== BACK BUTTON =====
    BackHandler {
        viewModel.announceAction("Exiting voice mode")
        viewModel.stopListening()
        viewModel.disableAccessibilityMode()
        onNavigateBack()
    }

    // ===== WELCOME ANNOUNCEMENT =====
    LaunchedEffect(Unit) {
        delay(500)
        viewModel.announceAction(
            "Push to talk mode active. " +
                    "Hold volume up button to speak, release to send. " +
                    "Hold volume down button to take a picture. " +
                    "You can say commands like: take a picture, flash on, or ask me anything."
        )
    }
    Log.d("ACCESSIBLE USER SCREEN", "DIALOG VISIBLE: ${uiState.isImageDialogVisible}")

    Log.d("UI_RENDER", "Composing - isImageDialogVisible: ${uiState.isImageDialogVisible}")

    CapturedImageDialog(
        imageBytes = uiState.capturedImageBytes,
        showDialog = uiState.isImageDialogVisible,
        onDismiss = {
            Log.d("UI_RENDER", "onDismiss callback triggered")
            viewModel.dismissImageDialog()
        }
    )
    // ===== UI =====
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection Status
                ConnectionStatusBadge(isConnected = connectionState)

                Spacer(modifier = Modifier.height(32.dp))

                // Main Status Display
                StatusDisplay(
                    appState = uiState.currentAppState,
                    sttState = sttState,
                    isStreaming = uiState.isStreaming
                )
            }

            // Middle Section - BIG ASS TEXT
            TranscribedTextDisplay(sttState = sttState , imageBytes = uiState.capturedImageBytes)

            // Bottom Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual Indicators
                VisualIndicators(
                    isListening = sttState is SttState.Listening,
                    isProcessing = uiState.isStreaming,
                    isFlashOn = uiState.isFlashOn
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Button Guide
                ButtonGuide()
            }
        }
    }
}

@Composable
private fun ConnectionStatusBadge(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isConnected) "Glasses Connected" else "No Device",
            color = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun StatusDisplay(
    appState: AppState,
    sttState: SttState,
    isStreaming: Boolean
) {
    val statusText = when {
        sttState is SttState.Listening -> "Hold & Speak..."
        isStreaming -> "Processing..."
        appState is AppState.Capturing -> appState.step
        appState is AppState.Speaking -> "Speaking..."
        else -> "Ready\n(Press Vol↑ to talk)"
    }

    val statusColor = when {
        sttState is SttState.Listening -> Color(0xFFFF5252) // Red
        isStreaming -> Color(0xFF2196F3) // Blue
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            textAlign = TextAlign.Center,
            lineHeight = 50.sp
        )

        if (sttState is SttState.Listening || isStreaming) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = statusColor
            )
        }
    }
}

@Composable
private fun VisualIndicators(
    isListening: Boolean,
    isProcessing: Boolean,
    isFlashOn: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
            contentDescription = "Microphone",
            tint = if (isListening) Color(0xFFFF5252) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(32.dp))

        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Processing",
            tint = if (isProcessing) Color(0xFF2196F3) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(32.dp))

        Icon(
            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = "Flash",
            tint = if (isFlashOn) Color(0xFFFFEB3B) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )
    }
}
@Composable
private fun TranscribedTextDisplay(sttState: SttState, imageBytes: ByteArray?,) {
    // Get the transcribed text from STT state
    val displayText = when (sttState) {
        is SttState.Result -> sttState.text
        is SttState.Listening -> "..." // Show dots while listening
        else -> "" // Empty when idle or error
    }

    // Only show if there's text
    if (displayText.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You said:",
                fontSize = 16.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // BIG ASS TEXT - What you actually said
            Text(
                text = displayText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676), // Bright green
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if(imageBytes == null) "NO CAPTURE" else "IMAGE CAPTURED",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676), // Bright green
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Placeholder when nothing to show
        Box(modifier = Modifier.height(100.dp))
    }
}
@Composable
private fun ButtonGuide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Push-to-Talk Controls",
            fontSize = 14.sp,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Vol↑: Hold to Speak, Release to Send", fontSize = 12.sp, color = Color.Gray)
        Text("Vol↓: Hold 2s to Take Photo", fontSize = 12.sp, color = Color.Gray)
    }
}

// ==================== VIBRATION PATTERNS ====================

private enum class VibrationPattern(val timings: LongArray, val amplitudes: IntArray) {
    START_LISTENING(
        timings = longArrayOf(0, 50), // Short beep - listening started
        amplitudes = intArrayOf(0, 120)
    ),
    STOP_LISTENING(
        timings = longArrayOf(0, 30), // Even shorter - stopped listening
        amplitudes = intArrayOf(0, 80)
    ),
    LONG_PRESS(
        timings = longArrayOf(0, 100, 50, 100), // Double pulse - action triggered
        amplitudes = intArrayOf(0, 150, 0, 150)
    ),
    ACTION_CONFIRM(
        timings = longArrayOf(0, 30), // Tiny blip - confirmation
        amplitudes = intArrayOf(0, 80)
    )
}

private fun vibrate(context: Context, pattern: VibrationPattern) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern.timings, -1)
    }
}