package com.gobex.smartreadingassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gobex.smartreadingassistant.ui.MainScreen
import com.gobex.smartreadingassistant.ui.theme.SmartreadingassistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartreadingassistantTheme {
                MainScreen()
            }
        }
    }
}
