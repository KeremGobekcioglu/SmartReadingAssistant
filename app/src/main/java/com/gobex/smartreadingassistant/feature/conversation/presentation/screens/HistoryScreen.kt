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
import androidx.compose.runtime.remember
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

    // 🔥 Calculate REAL usage from DB
    val usageStats = remember(uiState.messages) {
        viewModel.getUsageStats()
    }

    val dailyLimit = 1500 // Gemini 2.5 Flash free tier
    val remainingToday = dailyLimit - usageStats.todayApiCalls
    val usagePercentage = (usageStats.todayApiCalls.toFloat() / dailyLimit * 100).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Usage Dashboard", fontSize = 18.sp)
                        Text(
                            "Today: ${usageStats.todayApiCalls}/$dailyLimit requests ($usagePercentage%)",
                            fontSize = 12.sp,
                            color = if (usagePercentage > 80) Color.Red else Color.Gray
                        )
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

            // 📊 Usage Summary Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        usagePercentage > 90 -> Color(0xFFFFEBEE) // Light red
                        usagePercentage > 70 -> Color(0xFFFFF3E0) // Light orange
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Daily Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { usageStats.todayApiCalls.toFloat() / dailyLimit },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = when {
                            usagePercentage > 90 -> Color.Red
                            usagePercentage > 70 -> Color(0xFFFF9800)
                            else -> Color.Green
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$remainingToday requests left today", fontSize = 12.sp)
                        Text("Resets at midnight PT", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            // 📈 Statistics Cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Today Requests", usageStats.todayApiCalls.toString(), Modifier.weight(1f))
                StatCard("Total Requests", usageStats.totalApiCalls.toString(), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Today Tokens", usageStats.todayTokens.toString(), Modifier.weight(1f))
                StatCard("Total Tokens", usageStats.totalTokens.toString(), Modifier.weight(1f))
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Input Tokens", fontSize = 12.sp, color = Color.Gray)
                        Text(usageStats.inputTokens.toString(), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Output Tokens", fontSize = 12.sp, color = Color.Gray)
                        Text(usageStats.outputTokens.toString(), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Est. Cost", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            if (usageStats.todayApiCalls <= 1500) "FREE"
                            else "$${String.format("%.6f", usageStats.estimatedCost)}",
                            fontWeight = FontWeight.Bold,
                            color = if (usageStats.todayApiCalls <= 1500) Color.Green else Color.Red
                        )
                    }
                }
            }

            // Warning if approaching limit
            if (usagePercentage > 80) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (usagePercentage > 90) Color(0xFFFFCDD2) else Color(0xFFFFE0B2)
                    )
                ) {
                    Text(
                        "⚠️ ${if (usagePercentage > 90) "Critical:" else "Warning:"} You've used $usagePercentage% of your daily quota!",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "Message History:",
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Message list (your existing code)
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages.reversed()) { msg ->
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
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}