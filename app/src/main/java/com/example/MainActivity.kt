package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (::viewModel.isInitialized) {
                val currentScreen = viewModel.currentScreen.value
                val isSidebarVisible = viewModel.isSidebarVisible.value
                
                if (keyCode == android.view.KeyEvent.KEYCODE_SETTINGS || keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    if (currentScreen != "settings") {
                        viewModel.navigateTo("settings")
                        return true
                    }
                }

                if (currentScreen == "player") {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_PAGE_UP,
                        android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.playNextChannel()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_PAGE_DOWN,
                        android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.playPreviousChannel()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!isSidebarVisible) {
                                viewModel.playNextChannel()
                                return true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isSidebarVisible) {
                                viewModel.playPreviousChannel()
                                return true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val dpadSidebarEnabled = viewModel.dpadSidebarControlEnabled.value
                            if (dpadSidebarEnabled && !isSidebarVisible) {
                                viewModel.setSidebarVisible(true)
                                return true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val dpadSidebarEnabled = viewModel.dpadSidebarControlEnabled.value
                            if (dpadSidebarEnabled && isSidebarVisible) {
                                viewModel.setSidebarVisible(false)
                                return true
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full Edge-to-Edge display support
        enableEdgeToEdge()
        
        setContent {
            viewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                "light" -> false
                else -> true // default is "dark" mode for elegant cinema TV design
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == "settings") {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut()
                                )
                            }
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            "player" -> {
                                PlayerScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { viewModel.navigateTo("settings") },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateTo("player") },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
