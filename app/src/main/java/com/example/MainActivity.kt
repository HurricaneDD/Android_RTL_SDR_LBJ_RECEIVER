package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LbjViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lbjViewModel: LbjViewModel = viewModel()
            val state by lbjViewModel.receiverState.collectAsState()
            val isDarkTheme = when (state.themeMode) {
                "dark" -> true
                "system" -> isSystemInDarkTheme()
                else -> false
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainScreen(viewModel = lbjViewModel)
            }
        }
    }
}
