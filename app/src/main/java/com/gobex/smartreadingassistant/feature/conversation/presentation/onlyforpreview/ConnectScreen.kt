package com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationViewModel
import com.gobex.smartreadingassistant.ui.theme.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*

// Define High Contrast Colors for VI Accessibility
val HighContrastBlack = Color(0xFF121212)
val HighContrastWhite = Color(0xFFFFFFFF)
val HighContrastYellow = Color(0xFFFFD600) // excellent against black
val HighContrastGreen = Color(0xFF00E676)
val HighContrastRed = Color(0xFFFF1744)

@Composable
private fun ConnectContent(
    isBluetoothEnabled: Boolean,
    isConnected: Boolean,
    connectionStatus: String,
    assignedIp: String?,
    allPermissionsGranted: Boolean,
    onConnectClick: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAccessibleChat: () -> Unit,
    onGrantPermissionsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    if (!allPermissionsGranted) {
        PermissionsScreen(onGrantPermissionsClick)
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HighContrastBlack // Dark mode default is better for many VI users (reduces glare)
    ) { padding -> 12.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .paddingFromBaseline(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // --- HEADER SECTION ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Semantics: Merge these so TalkBack reads "Smart Reader, AI Assistant" in one go
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        text = "Smart Reader",
                        style = MaterialTheme.typography.displaySmall, // Larger
                        fontWeight = FontWeight.ExtraBold,
                        color = HighContrastWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        color = HighContrastYellow,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // --- STATUS SECTION (CENTER) ---
            // This acts as the visual anchor.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!isBluetoothEnabled) {
                    StatusIndicator(
                        icon = Icons.Default.BluetoothDisabled,
                        color = HighContrastRed,
                        mainText = "Bluetooth Off",
                        subText = "Enable in settings",
                        isError = true
                    )
                } else if (isConnected && assignedIp != null) {
                    StatusIndicator(
                        icon = Icons.Default.CheckCircle,
                        color = HighContrastGreen,
                        mainText = "Connected",
                        subText = "Ready to read"
                    )
                } else if (connectionStatus.contains("Scanning") || connectionStatus.contains("Starting")) {
                    StatusIndicator(
                        icon = Icons.Default.Radar, // Radar implies scanning better than circle
                        color = HighContrastYellow,
                        mainText = "Scanning...",
                        subText = "Looking for glasses",
                        isLoading = true
                    )
                } else {
                    StatusIndicator(
                        icon = Icons.Default.BluetoothSearching,
                        color = HighContrastWhite,
                        mainText = "Disconnected",
                        subText = "Ready to pair"
                    )
                }
            }

            // --- CONTROLS SECTION (BOTTOM) ---
            // Big buttons, easy to hit.
            Column(
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isConnected && assignedIp != null) {
                    // Primary Task: Accessible Chat
                    // We prioritize the Accessible Chat for this specific user group
                    LargeAccessibilityButton(
                        text = "Start Reading Mode",
                        icon = Icons.Default.Visibility,
                        backgroundColor = HighContrastYellow,
                        textColor = Color.Black,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onNavigateToAccessibleChat()
                        }
                    )

                    // Secondary Task
//                    LargeAccessibilityButton(
//                        text = "Standard Chat",
//                        icon = Icons.Default.Chat,
//                        backgroundColor = Color.DarkGray,
//                        textColor = Color.White,
//                        onClick = {
//                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
//                            onNavigateToChat()
//                        }
//                    )
                } else {
                    // Connect Button
                    LargeAccessibilityButton(
                        text = if (isBluetoothEnabled) "Connect to Glasses" else "Enable Bluetooth From Settings",
                        icon = if (isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.SettingsBluetooth,
                        backgroundColor = if (isBluetoothEnabled) HighContrastWhite else HighContrastRed,
                        textColor = Color.Black,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onConnectClick()
                        }
                    )
                }
            }
        }
    }
}

// --- REUSABLE COMPONENTS FOR CONSISTENCY ---

@Composable
fun StatusIndicator(
    icon: ImageVector,
    color: Color,
    mainText: String,
    subText: String,
    isError: Boolean = false,
    isLoading: Boolean = false
) {
    // Merge descendants so TalkBack reads the status as a single update
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            stateDescription = "$mainText, $subText"
            if (isError) error(mainText)
        }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = color,
                strokeWidth = 6.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null, // Handled by parent semantics
                modifier = Modifier.size(100.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = mainText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = subText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray
        )
    }
}

@Composable
fun LargeAccessibilityButton(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    isBluetoothOpen : Boolean? = true
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp), // Large touch target (min 48dp, usually 60-80 for accessibility)
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        enabled = isBluetoothOpen == true
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun PermissionsScreen(onGrantClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = HighContrastBlack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, null, tint = HighContrastYellow, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Permissions Needed", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "To help you see, the app needs access to Bluetooth and Location.",
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.weight(1f))
            LargeAccessibilityButton(
                text = "Grant Permissions",
                icon = Icons.Default.Check,
                backgroundColor = HighContrastYellow,
                textColor = Color.Black,
                onClick = onGrantClick
            )
        }
    }
}


// 2. THE STATEFUL VERSION (Used in your App)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectScreen(
    viewModel: ConversationViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToAccessibleChat: () -> Unit
) {
    viewModel.isConnectScreen()
    val permissionsState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    )

    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val isConnected by viewModel.isDeviceConnected.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val assignedIp by viewModel.assignedIp.collectAsStateWithLifecycle()

    LaunchedEffect(isBluetoothEnabled, isConnected) {
        // Log to confirm the UI sees the change
        Log.d("AUTO_CONNECT", "State Check -> BT: $isBluetoothEnabled, Connected: $isConnected")

        if (isBluetoothEnabled && !isConnected) {
            if (connectionStatus == "Ready to Connect" || connectionStatus.contains("Error")) {
                // Small delay to let Bluetooth hardware stabilize
                kotlinx.coroutines.delay(500)
                Log.d("AUTO_CONNECT", "Triggering Connection...")
                viewModel.connectToSmartGlasses()
            }
        }
    }

    DisposableEffect(Unit) {
        val announcementJob = viewModel.startConnectionAnnouncements()

        // Check BT status immediately on entry
        viewModel.checkAndAnnounceBluetoothStatus()

        onDispose {
            announcementJob.cancel() // Kill the observer loop
            viewModel.stopSpeaking() // Silence TTS immediately
        }
    }

    ConnectContent(
        isBluetoothEnabled = isBluetoothEnabled,
        isConnected = isConnected,
        connectionStatus = connectionStatus,
        assignedIp = assignedIp,
        allPermissionsGranted = permissionsState.allPermissionsGranted,
        onConnectClick = { viewModel.connectToSmartGlasses() },
        onNavigateToChat = onNavigateToChat,
        onNavigateToAccessibleChat = onNavigateToAccessibleChat,
        onGrantPermissionsClick = { permissionsState.launchMultiplePermissionRequest() }
    )
}
@Preview(showBackground = true, name = "Initial State")
@Composable
fun PreviewConnectInitial() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = false,
            connectionStatus = "Ready to Connect",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Connected State")
@Composable
fun PreviewConnectSuccess() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = true,
            connectionStatus = "Connected",
            assignedIp = "192.168.1.45",
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Bluetooth Disabled")
@Composable
fun PreviewBluetoothOff() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = false,
            isConnected = false,
            connectionStatus = "Ready to Connect",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Permissions Missing")
@Composable
fun PreviewPermissions() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = false,
            connectionStatus = "Ready to Connect",
            assignedIp = null,
            allPermissionsGranted = false,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Scanning State")
@Composable
fun PreviewScanning() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = false,
            connectionStatus = "Scanning for devices...",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Starting Connection")
@Composable
fun PreviewStarting() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = false,
            connectionStatus = "Starting connection...",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Scanning with Bluetooth Off")
@Composable
fun PreviewScanningBluetoothOff() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = false,
            isConnected = false,
            connectionStatus = "Scanning for devices...",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Connection Error")
@Composable
fun PreviewConnectionError() {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = Typography
    ) {
        ConnectContent(
            isBluetoothEnabled = true,
            isConnected = false,
            connectionStatus = "Connection failed. Please try again.",
            assignedIp = null,
            allPermissionsGranted = true,
            onConnectClick = {},
            onNavigateToChat = {},
            onNavigateToAccessibleChat = {},
            onGrantPermissionsClick = {}
        )
    }
}