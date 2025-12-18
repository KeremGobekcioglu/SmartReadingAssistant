package com.gobex.smartreadingassistant.feature.conversation.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gobex.smartreadingassistant.feature.conversation.domain.MessageRole
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    // Calculate Stats for Developer
    val totalMessages = uiState.messages.size
    val totalTokens = uiState.messages.sumOf { it.totalTokenCount ?: 0 }
    val userMessages = uiState.messages.count { it.role == MessageRole.USER }
    val assistantMessages = uiState.messages.count { it.role == MessageRole.ASSISTANT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DB & Token Debugger", fontSize = 18.sp)
                        Text("Total Usage: $totalTokens tokens", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 1. Statistics Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Msgs", totalMessages.toString())
                    StatItem("User", userMessages.toString())
                    StatItem("AI", assistantMessages.toString())
                    StatItem("Cost", "~$${String.format("%.4f", totalTokens * 0.0000005)}") // Approx Gemini Flash cost
                }
            }

            Text(
                "Raw Database Entries:",
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // 2. Raw DB List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages.reversed()) { msg -> // Show newest first for debugging
                    Card(elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(
                                    containerColor = if (msg.role == MessageRole.USER) Color.Blue else Color.Green
                                ) {
                                    Text(msg.role.name, color = Color.White, modifier = Modifier.padding(4.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.weight(1f))
                                Text("${msg.totalTokenCount ?: 0} toks", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, maxLines = 3, style = MaterialTheme.typography.bodyMedium)

                            if (msg.fileUri != null) {
                                Text("📁 File: ${msg.fileUri}", style = MaterialTheme.typography.labelSmall, color = Color.Magenta)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}