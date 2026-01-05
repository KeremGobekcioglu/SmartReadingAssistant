package com.gobex.smartreadingassistant.feature.conversation.presentation

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
    onNavigateToChat: () -> Unit
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
        val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
        val isConnected by viewModel.isDeviceConnected.collectAsStateWithLifecycle()
        val assignedIp by viewModel.assignedIp.collectAsStateWithLifecycle()

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
                        modifier = Modifier.fillMaxWidth().height(56.dp)
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