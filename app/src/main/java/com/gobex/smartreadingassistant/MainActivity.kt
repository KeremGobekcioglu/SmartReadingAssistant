package com.gobex.smartreadingassistant

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gobex.smartreadingassistant.core.navigation.Route
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConnectScreen
import com.gobex.smartreadingassistant.feature.conversation.presentation.ConversationViewModel
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.AccessibleUserScreen
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.ChatTestScreen
import com.gobex.smartreadingassistant.feature.conversation.presentation.screens.HistoryScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    var onHardwareKeyEvent: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 1. Check if a Composable (like HardwareKeyHandler) wants this key
        val consumedByCompose = onHardwareKeyEvent?.invoke(event) ?: false

        // 2. If it was a Volume key and our app handled it, return true to block the System Volume UI
        if (consumedByCompose) return true

        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Route.Connect
                    ) {
                        // 1. Main Chat Screen
                        composable<Route.Connect> { backStackEntry ->
                            // This gets the ViewModel scoped to this specific backstack entry
                            val viewModel = hiltViewModel<ConversationViewModel>()

                            ConnectScreen(
                                viewModel = viewModel,
                                onNavigateToChat = { navController.navigate(Route.Chat) },
                                onNavigateToAccessibleChat = { navController.navigate(Route.AccessibleChat)}
                            )
                        }

//                        composable<Route.Chat> { backStackEntry ->
//                            // To share the SAME instance, we scope it to the "Connect" route
//                            // OR a common parent navigation graph.
//                            val parentEntry = remember(backStackEntry) {
//                                navController.getBackStackEntry(Route.Connect)
//                            }
//                            val viewModel = hiltViewModel<ConversationViewModel>(parentEntry)
//
//                            ChatScreen(viewModel = viewModel)
//                        }

                        composable<Route.AccessibleChat> { backStackEntry ->
                            // Get the ViewModel from the 'Connect' route so data is SHARED
                            val parentEntry = remember(backStackEntry) {
                                navController.getBackStackEntry(Route.Connect)
                            }
                            val viewModel = hiltViewModel<ConversationViewModel>(parentEntry)
                            viewModel.enableAccessibilityMode()
                            AccessibleUserScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable<Route.Chat> {
                            ChatTestScreen(
                                onNavigateToHistory = {
                                    navController.navigate(Route.History)
                                }
                            )
                        }

                        // 2. History / Debug Screen
                        composable<Route.History> {
                            HistoryScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}