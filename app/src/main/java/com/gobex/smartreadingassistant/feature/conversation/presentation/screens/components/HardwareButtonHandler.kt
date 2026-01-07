package com.gobex.smartreadingassistant.feature.conversation.presentation.screens.components

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.gobex.smartreadingassistant.MainActivity

@Composable
fun HardwareKeyHandler(
    onVolumeUp: (isLongPress: Boolean) -> Unit,
    onVolumeDown: (isLongPress: Boolean) -> Unit,
    longPressTimeout: Long = 600L
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    var isLongPressActive by remember { mutableStateOf(false) }
    var upRunnable: Runnable? by remember { mutableStateOf(null) }
    var downRunnable: Runnable? by remember { mutableStateOf(null) }

    DisposableEffect(activity) {
        activity?.onHardwareKeyEvent = { event ->
            val keyCode = event.keyCode

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            isLongPressActive = false // Reset state
                            val runnable = Runnable {
                                isLongPressActive = true
                                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) onVolumeUp(true)
                                else onVolumeDown(true)
                            }

                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                upRunnable = runnable
                                handler.postDelayed(runnable, longPressTimeout)
                            } else {
                                downRunnable = runnable
                                handler.postDelayed(runnable, longPressTimeout)
                            }
                        }
                        // IMPORTANT: Return false so the system can start its own
                        // volume repeat logic until our long-press Runnable triggers.
                        false
                    }
                    KeyEvent.ACTION_UP -> {
                        upRunnable?.let { handler.removeCallbacks(it) }
                        downRunnable?.let { handler.removeCallbacks(it) }

                        val wasLongPress = isLongPressActive
                        isLongPressActive = false

                        // If it WAS a long press, consume the 'UP' event so we don't
                        // trigger a tiny volume change at the end.
                        // If it WAS NOT a long press, return false to let the system
                        // finish the volume click.
                        wasLongPress
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        onDispose {
            activity?.onHardwareKeyEvent = null
            upRunnable?.let { handler.removeCallbacks(it) }
            downRunnable?.let { handler.removeCallbacks(it) }
        }
    }
}