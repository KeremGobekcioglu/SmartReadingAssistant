package com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gobex.smartreadingassistant.core.audio.SttState
import com.gobex.smartreadingassistant.feature.conversation.presentation.AppState
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components.CapturedImageDialog
import com.gobex.smartreadingassistant.ui.theme.Typography
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayOutputStream

// ==================== STATELESS VERSION (Preview-friendly) ====================

@Composable
private fun AccessibleUserContent(
    isConnected: Boolean,
    isFlashOn: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    sttState: SttState,
    appState: AppState,
    isStreaming: Boolean,
    imageBytes: ByteArray?,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onCameraTap: () -> Unit
) {
    val testImageBytes = remember { createTestImageBytes() }

    CapturedImageDialog(
        imageBytes = testImageBytes,
        showDialog = true,
        onDismiss = {}
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TOP BAR - Connection Status + Indicators
        TopStatusBar(
            isConnected = isConnected,
            isFlashOn = isFlashOn,
            isListening = isListening,
            isProcessing = isProcessing
        )

        // MIDDLE - Big Status Text + Transcription
        StatusAndTranscriptionArea(
            sttState = sttState,
            appState = appState,
            isStreaming = isStreaming,
            imageBytes = imageBytes
        )

        // BOTTOM HALF - Two Giant Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // MIC BUTTON (Left side - 50%)
            MicButton(
                isListening = isListening,
                isProcessing = isProcessing,
                onPress = onMicPress,
                onRelease = onMicRelease,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // CAMERA BUTTON (Right side - 50%)
            CameraButton(
                isCapturing = appState is AppState.Capturing,
                onTap = onCameraTap,
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
                text = if (isCapturing) "CAPTURING\nPHOTO..." else "HOLD TO\nCAPTURE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (isCapturing) Color.Black else Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                lineHeight = 32.sp
            )
        }
    }
}

// ==================== PREVIEW STATES ====================

@Preview(showBackground = true, name = "Ready State - Connected")
@Composable
fun PreviewAccessibleReadyConnected() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Idle,
            appState = AppState.Idle,
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Ready State - Disconnected")
@Composable
fun PreviewAccessibleReadyDisconnected() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = false,
            isFlashOn = false,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Idle,
            appState = AppState.Idle,
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Listening State")
@Composable
fun PreviewAccessibleListening() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = true,
            isProcessing = false,
            sttState = SttState.Listening,
            appState = AppState.Listening,
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Processing State")
@Composable
fun PreviewAccessibleProcessing() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = false,
            isProcessing = true,
            sttState = SttState.Idle,
            appState = AppState.Processing,
            isStreaming = true,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "With Transcription Result")
@Composable
fun PreviewAccessibleWithTranscription() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Result("Take a picture of this document"),
            appState = AppState.Idle,
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Capturing Photo")
@Composable
fun PreviewAccessibleCapturing() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = true,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Idle,
            appState = AppState.Capturing("Capturing\nphoto"),
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Image Captured")
@Composable
fun PreviewAccessibleImageCaptured() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        // Simulate image bytes with a non-null value
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Idle,
            appState = AppState.Idle,
            isStreaming = false,
            imageBytes = byteArrayOf(0x1, 0x2, 0x3), // Dummy bytes for preview
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "All Features Active")
@Composable
fun PreviewAccessibleAllActive() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = true,
            isListening = false,
            isProcessing = true,
            sttState = SttState.Result("What can you see in this image?"),
            appState = AppState.Processing,
            isStreaming = true,
            imageBytes = byteArrayOf(0x1, 0x2, 0x3),
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

@Preview(showBackground = true, name = "Error State")
@Composable
fun PreviewAccessibleError() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        AccessibleUserContent(
            isConnected = true,
            isFlashOn = false,
            isListening = false,
            isProcessing = false,
            sttState = SttState.Error("Failed to recognize speech"),
            appState = AppState.Error("Connection lost"),
            isStreaming = false,
            imageBytes = null,
            onMicPress = {},
            onMicRelease = {},
            onCameraTap = {}
        )
    }
}

// ==================== CAPTURED IMAGE DIALOG PREVIEW ====================

/**
 * Creates a simple test image for preview purposes
 */
private fun createTestImageBytes(): ByteArray {
    // Create a simple 400x300 bitmap with a gradient-like pattern
    val width = 400
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
    }
    
    // Draw a gradient-like pattern
    for (x in 0 until width) {
        for (y in 0 until height) {
            val r = (x * 255 / width).toInt()
            val g = (y * 255 / height).toInt()
            val b = 128
            val color = android.graphics.Color.rgb(r, g, b)
            bitmap.setPixel(x, y, color)
        }
    }
    
    // Add some text-like pattern in the center
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 60f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText("TEST IMAGE", width / 2f, height / 2f, paint)
    canvas.drawText("Preview", width / 2f, height / 2f + 80f, paint)
    
    // Convert to JPEG bytes
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return outputStream.toByteArray()
}

@Preview(showBackground = true, name = "Captured Image Dialog")
@Composable
fun PreviewCapturedImageDialog() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        // Create test image bytes
        val testImageBytes = remember { createTestImageBytes() }
        
        CapturedImageDialog(
            imageBytes = testImageBytes,
            showDialog = true,
            onDismiss = {}
        )
    }
}
