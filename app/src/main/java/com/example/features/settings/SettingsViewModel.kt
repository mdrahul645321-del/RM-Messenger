package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.AppLanguage
import com.example.core.model.NotificationPreferences
import com.example.core.model.PrivacyPreferences
import com.example.core.model.ThemeMode
import com.example.core.model.UserAccount
import com.example.core.model.VisibilityOption
import com.example.core.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val privacy: PrivacyPreferences = PrivacyPreferences(),
    val notifications: NotificationPreferences = NotificationPreferences(),
    val profile: UserAccount = UserAccount(),
)

class SettingsViewModel(
    private val repository: AppPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.themeMode,
        repository.language,
        repository.privacyPreferences,
        repository.notificationPreferences,
        repository.draftProfile,
    ) { theme, language, privacy, notifications, profile ->
        SettingsUiState(
            themeMode = theme,
            language = language,
            privacy = privacy,
            notifications = notifications,
            profile = profile,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun updateLastSeenVisibility(option: VisibilityOption) {
        viewModelScope.launch {
            repository.updatePrivacy(uiState.value.privacy.copy(lastSeenVisibility = option))
        }
    }

    fun updateOnlineVisibility(option: VisibilityOption) {
        viewModelScope.launch {
            repository.updatePrivacy(uiState.value.privacy.copy(onlineVisibility = option))
        }
    }

    fun updatePhotoVisibility(option: VisibilityOption) {
        viewModelScope.launch {
            repository.updatePrivacy(uiState.value.privacy.copy(profilePhotoVisibility = option))
        }
    }

    fun updateAboutVisibility(option: VisibilityOption) {
        viewModelScope.launch {
            repository.updatePrivacy(uiState.value.privacy.copy(aboutVisibility = option))
        }
    }

    fun toggleReadReceipts(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePrivacy(uiState.value.privacy.copy(readReceiptsEnabled = enabled))
        }
    }

    fun updateNotifications(transform: (NotificationPreferences) -> NotificationPreferences) {
        viewModelScope.launch {
            repository.updateNotifications(transform(uiState.value.notifications))
        }
    }

    fun saveDraftProfile(
        displayName: String,
        username: String,
        about: String,
        email: String,
        photoUri: String?,
    ) {
        viewModelScope.launch {
            repository.updateDraftProfile(
                displayName = displayName,
                username = username,
                about = about,
                email = email,
                photoUri = photoUri,
            )
        }
    }

    class Factory(private val repository: AppPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
