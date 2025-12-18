package com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gobex.smartreadingassistant.feature.conversation.domain.Message

@Composable
fun MessageBubble(
    message: Message,
    isUser: Boolean // Passed from parent based on message.role == USER
) {
    // 1. Decide Alignment (Left or Right?)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    // 2. Decide Color (Blue or Gray?)
    val containerColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer // Blue-ish
    else
        MaterialTheme.colorScheme.surfaceVariant   // Gray-ish

    // The Container for the row
    Column(
        modifier = Modifier
            .fillMaxWidth() // Take full width so we can align left/right inside it
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment // <-- Applies the Left/Right logic
    ) {
        // 3. Show "Image Attached" tag if needed
        if (message.fileUri != null || message.imageBase64 != null) {
            Text(
                text = "📷 Image Attached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4. The Actual Bubble (Card)
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(16.dp), // Makes it round like a bubble
            modifier = Modifier.widthIn(max = 300.dp) // Don't let it stretch too wide
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp), // Space inside the bubble
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}