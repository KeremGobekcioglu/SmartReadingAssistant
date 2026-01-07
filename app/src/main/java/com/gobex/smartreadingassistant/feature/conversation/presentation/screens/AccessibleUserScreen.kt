package com.gobex.smartreadingassistant.feature.conversation.presentation.screens

import android.app.Activity
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccessibleUserScreen(
    viewModel: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    Log.d("AccessibleUserScreen" , "Accessibility mode  : ${viewModel.state.value.isAccessibilityMode}")
    val uiState by viewModel.state.collectAsState()
    val sttState by viewModel.sttState.collectAsStateWithLifecycle(initialValue = SttState.Idle)
    val connectionState by viewModel.isDeviceConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    HardwareKeyHandler(
        onVolumeUp = { isLongPress ->
            vibrate(context, if(isLongPress) VibrationPattern.LONG_PRESS else VibrationPattern.SHORT_PRESS)
            if (isLongPress) {
                viewModel.captureAndAnalyze()
            } else {
                // Toggle Mic: If listening, stop. If idle, start.
                if (sttState is SttState.Listening) viewModel.stopListening()
                else viewModel.startListening()
            }
        },
        onVolumeDown = { isLongPress ->
            vibrate(context, if(isLongPress) VibrationPattern.SHORT_PRESS else VibrationPattern.LONG_PRESS)
            if (isLongPress) {
                Log.d("AccessibleUserScreen" , "FLASH IS ${uiState.isFlashOn}")
                viewModel.toggleFlash(!uiState.isFlashOn)
            } else {
                viewModel.stopSpeaking()
            }
        }
    )
    // Setup volume button interception
//    VolumeButtonHandler(
//        activity = activity,
//        enabled = uiState.isAccessibilityMode,
//        onVolumeUp = { isLongPress ->
//            if (isLongPress) {
//                // Long press: Capture photo
//                vibrate(context, VibrationPattern.LONG_PRESS)
//                viewModel.captureAndAnalyze()
//            } else {
//                // Short press: Toggle microphone
//                vibrate(context, VibrationPattern.SHORT_PRESS)
//                when (sttState) {
//                    is SttState.Listening -> viewModel.stopListening()
//                    else -> viewModel.startListening()
//                }
//            }
//        },
//        onVolumeDown = { isLongPress ->
//            if (isLongPress) {
//                // Long press: Toggle flash
//                vibrate(context, VibrationPattern.LONG_PRESS)
//                viewModel.toggleFlash(!uiState.isFlashOn)
//            } else {
//                // Short press: Stop speaking
//                vibrate(context, VibrationPattern.SHORT_PRESS)
//                viewModel.stopSpeaking()
//            }
//        }
//    )

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is ConversationEffect.ShowError -> {
                    // Errors are already announced via TTS in ViewModel
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

    // Back button handler with confirmation
    BackHandler {
        viewModel.announceAction("Press back again to exit")
        // Simple implementation - you might want a proper confirmation dialog
        onNavigateBack()
    }

    // Announce instructions on first launch
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        viewModel.announceAction(
            "Voice mode active. " +
                    "Press volume up to talk, volume down to stop speaking. " +
                    "Long press volume up to take a picture. " +
                    "Say commands like: take a picture, flash on, or ask me anything."
        )
    }

    // 2. UI EFFECTS (Haptics)
    // Your ViewModel already sends these effects
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            if (effect is ConversationEffect.AnnounceAction && effect.withHaptic) {
                vibrate(context, VibrationPattern.ACTION_CONFIRM)
            }
        }
    }

    // 3. NAVIGATION
    BackHandler {
        viewModel.announceAction("Exiting voice mode")
        onNavigateBack()
    }
    CapturedImageDialog(
        imageBytes = uiState.capturedImageBytes,
        onDismiss = { viewModel.clearImagePreview() }
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black // Dark screen to save battery and reduce glare
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Connection Status Indicator
            ConnectionStatusBadge(isConnected = connectionState)

            Spacer(modifier = Modifier.height(32.dp))

            // Main Status Display
            StatusDisplay(
                appState = uiState.currentAppState,
                sttState = sttState,
                isStreaming = uiState.isStreaming
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Visual Indicators (for sighted helpers)
            VisualIndicators(
                isListening = sttState is SttState.Listening,
                isProcessing = uiState.isStreaming,
                isFlashOn = uiState.isFlashOn
            )

            Spacer(modifier = Modifier.weight(1f))

            // Button Guide (small text for helpers)
            ButtonGuide()
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
    // Determine what to show
    val statusText = when {
        sttState is SttState.Listening -> "Listening..."
        isStreaming -> "Processing..."
        appState is AppState.Capturing -> appState.step
        appState is AppState.Speaking -> "Speaking..."
        else -> "Ready"
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
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            textAlign = TextAlign.Center
        )

        // Animated indicator
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
        // Microphone indicator
        Icon(
            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
            contentDescription = "Microphone",
            tint = if (isListening) Color(0xFFFF5252) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(32.dp))

        // Processing indicator
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Processing",
            tint = if (isProcessing) Color(0xFF2196F3) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(32.dp))

        // Flash indicator
        Icon(
            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = "Flash",
            tint = if (isFlashOn) Color(0xFFFFEB3B) else Color.Gray,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun ButtonGuide() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Button Guide",
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Vol↑: Talk  |  Long: Photo", fontSize = 12.sp, color = Color.Gray)
        Text("Vol↓: Stop  |  Long: Flash", fontSize = 12.sp, color = Color.Gray)
    }
}

// ==================== VOLUME BUTTON HANDLER ====================

//@Composable
//private fun VolumeButtonHandler(
//    activity: ComponentActivity?,
//    enabled: Boolean,
//    onVolumeUp: (isLongPress: Boolean) -> Unit,
//    onVolumeDown: (isLongPress: Boolean) -> Unit
//) {
//    DisposableEffect(enabled) {
//        if (activity == null || !enabled) return@DisposableEffect onDispose {}
//
//        val callback = object : ComponentActivity.OnKeyEventListener {
//            override fun onKeyEvent(event: KeyEvent): Boolean {
//                if (!enabled) return false
//
//                // Only handle key down events
//                if (event.action != KeyEvent.ACTION_DOWN) return false
//
//                return when (event.keyCode) {
//                    KeyEvent.KEYCODE_VOLUME_UP -> {
//                        onVolumeUp(event.isLongPress)
//                        true // Consume event to prevent volume change
//                    }
//                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
//                        onVolumeDown(event.isLongPress)
//                        true // Consume event
//                    }
//                    else -> false
//                }
//            }
//        }
//
//        activity.addOnKeyEventListener(callback)
//
//        onDispose {
//            activity.removeOnKeyEventListener(callback)
//        }
//    }
//}

// ==================== VIBRATION PATTERNS ====================

private enum class VibrationPattern(val timings: LongArray, val amplitudes: IntArray) {
    SHORT_PRESS(
        timings = longArrayOf(0, 50),
        amplitudes = intArrayOf(0, 100)
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

// ==================== ALTERNATIVE: If ComponentActivity approach doesn't work ====================
// You can also handle volume buttons in your Activity's dispatchKeyEvent:

/*
// In your MainActivity.kt:

override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Check if we're in accessibility mode
    val isAccessibilityMode = // Get from ViewModel or shared state

    if (isAccessibilityMode && event.action == KeyEvent.ACTION_DOWN) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // Handle volume up
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // Handle volume down
                return true
            }
        }
    }

    return super.dispatchKeyEvent(event)
}
*/