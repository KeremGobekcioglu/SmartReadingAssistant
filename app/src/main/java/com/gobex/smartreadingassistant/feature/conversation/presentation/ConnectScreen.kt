package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.HighContrastBlack
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.HighContrastGreen
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.HighContrastRed
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.HighContrastWhite
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.HighContrastYellow
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.LargeAccessibilityButton
import com.gobex.smartreadingassistant.feature.conversation.presentation.onlyforpreview.StatusIndicator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.ui.platform.LocalHapticFeedback
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectScreen(
    viewModel: ConversationViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToAccessibleChat: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
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

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    if (permissionsState.allPermissionsGranted) {
// 1. LIFECYCLE: Start monitoring and STOP when leaving
        val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
        val isConnected by viewModel.isDeviceConnected.collectAsState()
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val assignedIp by viewModel.assignedIp.collectAsStateWithLifecycle()
        val uiState by viewModel.state.collectAsStateWithLifecycle()
        // 2. AUTO-CONNECT TRIGGER
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = HighContrastBlack // Dark mode default is better for many VI users (reduces glare)
        ) { padding->
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
                    } else if (uiState.currentAppState is AppState.Connecting ||
                        connectionStatus.contains("Connecting") ||
                        connectionStatus.contains("Sending") ||
                        connectionStatus.contains("restarting")) {
                        StatusIndicator(
                            icon = Icons.Default.Radar, // Radar implies scanning better than circle
                            color = HighContrastYellow,
                            mainText = if(connectionStatus.contains("restarting")) "Syncing..." else "Scanning...",
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
                                viewModel.connectToSmartGlasses()
                            },
                            isBluetoothEnabled
                        )
                    }
                }
            }
        }
    }
    else {
        // Show permission rationale
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Location and Bluetooth permissions are required")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun BluetoothDisabledWarning() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Bluetooth is Off",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Please turn on Bluetooth to find your glasses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}