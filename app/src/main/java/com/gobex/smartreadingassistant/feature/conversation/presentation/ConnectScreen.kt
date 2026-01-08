package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectScreen(
    viewModel: ConversationViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToAccessibleChat: () -> Unit
) {
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

        val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
        val isConnected by viewModel.isDeviceConnected.collectAsState()
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val assignedIp by viewModel.assignedIp.collectAsStateWithLifecycle()
        // 1. LIFECYCLE OBSERVER: Force refresh when user comes back from settings/shade
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    Log.d("UI", "App Resumed - Refreshing Bluetooth State")
                    // You'll need to expose this in ViewModel, or just rely on the flow
                    // Ideally, add refreshBluetoothState() to your ViewModel and call it here:
                    viewModel.refreshBluetoothState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

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
        // REMOVED: The LaunchedEffects that auto-navigate

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Smart Reader",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(48.dp))
                if (!isBluetoothEnabled) {
                    BluetoothDisabledWarning()
                }
                // 1. Success State: Show IP and Navigation Button
                if (isConnected && assignedIp != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Glasses Found!", fontWeight = FontWeight.Bold)
                    Text("IP Address: $assignedIp", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onNavigateToChat,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Start Chatting", fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToAccessibleChat,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                    ) {
                        Text("Start Accessible Chatting.", fontSize = 18.sp)
                    }
                }
                // 2. Loading State
                else if (connectionStatus.contains("Scanning") || connectionStatus.contains("Starting") || connectionStatus.contains("Sending")) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = connectionStatus)
                }
                // 3. Initial / Error State
                else {
                    Button(
                        onClick = { viewModel.connectToSmartGlasses() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        isBluetoothEnabled
                    ) {
                        Text("Connect Glasses", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = connectionStatus)
                }

                Spacer(modifier = Modifier.height(48.dp))

                if (!isConnected) {
                    TextButton(onClick = onNavigateToChat) {
                        Text("Skip (Test Mode)")
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