package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.navigation.RmRootApp
import com.example.core.preferences.AppPreferencesRepository
import com.example.features.settings.SettingsViewModel
import com.example.ui.theme.RmMessengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencesRepository = remember { AppPreferencesRepository(applicationContext) }
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(preferencesRepository),
            )
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            RmMessengerTheme(themeMode = settingsState.themeMode) {
                RmRootApp(settingsViewModel = settingsViewModel)
            }
        }
    }
}
