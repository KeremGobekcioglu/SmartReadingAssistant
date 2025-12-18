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
fun StreamingBubble(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start // Always left-aligned (AI side)
    ) {
        // Optional: A little header to show activity
        Text(
            text = "⚡ Gemini is thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant // Gray-ish
            ),
            // We make the top-left corner sharp to look like a "typing" bubble
            shape = RoundedCornerShape(
                topStart = 2.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // If text is empty (connected but first chunk hasn't arrived), show "..."
            Text(
                text = text.ifEmpty { "..." },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}