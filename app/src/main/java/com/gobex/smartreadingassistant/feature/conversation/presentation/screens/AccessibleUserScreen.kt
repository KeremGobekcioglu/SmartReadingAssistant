package com.gobex.smartreadingassistant.feature.conversation.presentation.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val activity = context as? ComponentActivity
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(connectionStatus) {
        Log.d("ACCESSIBLE_SCREEN", "Connection status: $connectionStatus")
        // The ViewModel's observeConnectionStateChanges() will handle TTS announcements
    }
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
        delay(500)
        viewModel.announceAction(
            "Screen button mode active. " +
                    "Bottom Left: hold to speak. " +
                    "Bottom Right: tap to take picture."
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
    // ===== MAIN UI =====
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TOP BAR - Connection Status + Indicators
        TopStatusBar(
            isConnected = connectionState,
            isFlashOn = uiState.isFlashOn,
            isListening = sttState is SttState.Listening,
            isProcessing = uiState.isStreaming
        )

        // MIDDLE - Big Status Text + Transcription
        StatusAndTranscriptionArea(
            sttState = sttState,
            appState = uiState.currentAppState,
            isStreaming = uiState.isStreaming,
            imageBytes = uiState.capturedImageBytes
        )

        // BOTTOM HALF - Two Giant Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // MIC BUTTON (Left side - 50%)
            MicButton(
                isListening = sttState is SttState.Listening,
                isProcessing = uiState.isStreaming,
                onPress = {
                    vibrate(context, VibrationPattern.START_LISTENING)
                    viewModel.startListening()
                },
                onRelease = {
                    vibrate(context, VibrationPattern.STOP_LISTENING)
                    viewModel.stopListening()
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // CAMERA BUTTON (Right side - 50%)
            CameraButton(
                isCapturing = uiState.currentAppState is AppState.Capturing,
                onTap = {
                    vibrate(context, VibrationPattern.LONG_PRESS)
                    viewModel.captureImageOnly()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== TOP STATUS BAR ====================

@Composable
private fun TopStatusBar(
    isConnected: Boolean,
    isFlashOn: Boolean,
    isListening: Boolean,
    isProcessing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isConnected) "Connected" else "No Device",
                color = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                fontSize = 14.sp
            )
        }

        // Status indicators
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Mic indicator
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Mic",
                tint = if (isListening) Color(0xFFFF5252) else Color.Gray,
                modifier = Modifier.size(24.dp)
            )

            // Processing indicator
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF2196F3),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Processing",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Flash indicator
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (isFlashOn) Color(0xFFFFEB3B) else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== STATUS & TRANSCRIPTION AREA ====================

@Composable
private fun StatusAndTranscriptionArea(
    sttState: SttState,
    appState: AppState,
    isStreaming: Boolean,
    imageBytes: ByteArray?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // HUGE STATUS TEXT
        val statusColor = when {
            sttState is SttState.Listening -> Color(0xFFFF5252) // Red
            isStreaming -> Color(0xFF2196F3) // Blue
            appState is AppState.Capturing -> Color(0xFFFFEB3B) // Yellow
            else -> Color.White
        }

        if (appState is AppState.Capturing) {
            // Two separate Text components for "CAPTURING PHOTO..."
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CAPTURING",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "PHOTO...",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            val statusText = when {
                sttState is SttState.Listening -> "LISTENING..."
                isStreaming -> "PROCESSING..."
                else -> "READY"
            }
            Text(
                text = statusText,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TRANSCRIBED TEXT
        if (sttState is SttState.Result) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "You said:",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sttState.text,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676), // Bright green
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // IMAGE CAPTURE INDICATOR
        if (imageBytes != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IMAGE CAPTURED",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }
        }
    }
}

// ==================== MIC BUTTON ====================

@Composable
private fun MicButton(
    isListening: Boolean,
    isProcessing: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isListening -> Color(0xFFFF5252) // Red when listening
        isProcessing -> Color(0xFF2196F3) // Blue when processing
        isPressed -> Color(0xFF424242) // Dark gray when pressed but not listening yet
        else -> Color(0xFF2A2A2A) // Default dark
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isListening) "SPEAKING" else "HOLD\nTO TALK",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }
    }
}

// ==================== CAMERA BUTTON ====================

@Composable
private fun CameraButton(
    isCapturing: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isCapturing) Color(0xFFFFEB3B) else Color(0xFF2A2A2A)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera",
                tint = if (isCapturing) Color.Black else Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isCapturing) "CAPTURING..." else "TAP TO\nCAPTURE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (isCapturing) Color.Black else Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }
    }
}

// ==================== VIBRATION PATTERNS ====================

private enum class VibrationPattern(val timings: LongArray, val amplitudes: IntArray) {
    START_LISTENING(
        timings = longArrayOf(0, 50),
        amplitudes = intArrayOf(0, 120)
    ),
    STOP_LISTENING(
        timings = longArrayOf(0, 30),
        amplitudes = intArrayOf(0, 80)
    ),
    LONG_PRESS(
        timings = longArrayOf(0, 100, 50, 100),
        amplitudes = intArrayOf(0, 150, 0, 150)
    ),
    ACTION_CONFIRM(
        timings = longArrayOf(0, 30),
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