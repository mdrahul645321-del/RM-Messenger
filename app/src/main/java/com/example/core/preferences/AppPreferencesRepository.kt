package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppLanguage
import com.example.core.model.NotificationPreferences
import com.example.core.model.PrivacyPreferences
import com.example.core.model.ThemeMode
import com.example.core.model.UserAccount
import com.example.core.model.VisibilityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rmDataStore: DataStore<Preferences> by preferencesDataStore(name = "rm_messenger_prefs")

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val DISPLAY_NAME = stringPreferencesKey("draft_display_name")
        val USERNAME = stringPreferencesKey("draft_username")
        val ABOUT = stringPreferencesKey("draft_about")
        val EMAIL = stringPreferencesKey("draft_email")
        val PHONE = stringPreferencesKey("draft_phone")
        val PHOTO_URI = stringPreferencesKey("draft_photo_uri")

        val LAST_SEEN_VIS = stringPreferencesKey("privacy_last_seen")
        val ONLINE_VIS = stringPreferencesKey("privacy_online")
        val PHOTO_VIS = stringPreferencesKey("privacy_photo")
        val ABOUT_VIS = stringPreferencesKey("privacy_about")
        val READ_RECEIPTS = booleanPreferencesKey("privacy_read_receipts")

        val MSG_ALERTS = booleanPreferencesKey("notif_messages")
        val CALL_ALERTS = booleanPreferencesKey("notif_calls")
        val GROUP_ALERTS = booleanPreferencesKey("notif_groups")
        val IN_APP_SOUNDS = booleanPreferencesKey("notif_sounds")
        val VIBRATION = booleanPreferencesKey("notif_vibration")
    }

    val themeMode: Flow<ThemeMode> = context.rmDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val language: Flow<AppLanguage> = context.rmDataStore.data.map { prefs ->
        runCatching { AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.name) }
            .getOrDefault(AppLanguage.ENGLISH)
    }

    val privacyPreferences: Flow<PrivacyPreferences> = context.rmDataStore.data.map { prefs ->
        PrivacyPreferences(
            lastSeenVisibility = parseVisibility(prefs[Keys.LAST_SEEN_VIS], VisibilityOption.CONTACTS),
            onlineVisibility = parseVisibility(prefs[Keys.ONLINE_VIS], VisibilityOption.EVERYONE),
            profilePhotoVisibility = parseVisibility(prefs[Keys.PHOTO_VIS], VisibilityOption.EVERYONE),
            aboutVisibility = parseVisibility(prefs[Keys.ABOUT_VIS], VisibilityOption.CONTACTS),
            readReceiptsEnabled = prefs[Keys.READ_RECEIPTS] ?: true,
        )
    }

    val notificationPreferences: Flow<NotificationPreferences> = context.rmDataStore.data.map { prefs ->
        NotificationPreferences(
            messageAlerts = prefs[Keys.MSG_ALERTS] ?: true,
            callAlerts = prefs[Keys.CALL_ALERTS] ?: true,
            groupAlerts = prefs[Keys.GROUP_ALERTS] ?: true,
            inAppSounds = prefs[Keys.IN_APP_SOUNDS] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
        )
    }

    val draftProfile: Flow<UserAccount> = context.rmDataStore.data.map { prefs ->
        UserAccount(
            uid = "Pending Phase 2 Firebase Auth",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "RM Explorer",
            username = prefs[Keys.USERNAME] ?: "rm_preview",
            phoneNumber = prefs[Keys.PHONE] ?: "Not verified yet",
            email = prefs[Keys.EMAIL] ?: "",
            photoUri = prefs[Keys.PHOTO_URI],
            about = prefs[Keys.ABOUT] ?: "Connect. Chat. Call. Share.",
            createdAtLabel = "Phase 1 Local Preview",
            isOnline = false,
            lastSeenLabel = "Connect Firebase in Phase 2",
            isAuthenticated = false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.rmDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.rmDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updateDraftProfile(
        displayName: String,
        username: String,
        about: String,
        email: String,
        phone: String? = null,
        photoUri: String? = null,
    ) {
        context.rmDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = displayName.trim()
            prefs[Keys.USERNAME] = username.trim().lowercase().removePrefix("@")
            prefs[Keys.ABOUT] = about.trim()
            prefs[Keys.EMAIL] = email.trim()
            if (phone != null) prefs[Keys.PHONE] = phone
            if (photoUri != null) prefs[Keys.PHOTO_URI] = photoUri
        }
    }

    suspend fun updatePrivacy(preferences: PrivacyPreferences) {
        context.rmDataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_VIS] = preferences.lastSeenVisibility.name
            prefs[Keys.ONLINE_VIS] = preferences.onlineVisibility.name
            prefs[Keys.PHOTO_VIS] = preferences.profilePhotoVisibility.name
            prefs[Keys.ABOUT_VIS] = preferences.aboutVisibility.name
            prefs[Keys.READ_RECEIPTS] = preferences.readReceiptsEnabled
        }
    }

    suspend fun updateNotifications(preferences: NotificationPreferences) {
        context.rmDataStore.edit { prefs ->
            prefs[Keys.MSG_ALERTS] = preferences.messageAlerts
            prefs[Keys.CALL_ALERTS] = preferences.callAlerts
            prefs[Keys.GROUP_ALERTS] = preferences.groupAlerts
            prefs[Keys.IN_APP_SOUNDS] = preferences.inAppSounds
            prefs[Keys.VIBRATION] = preferences.vibrationEnabled
        }
    }

    private fun parseVisibility(raw: String?, fallback: VisibilityOption): VisibilityOption {
        return runCatching { VisibilityOption.valueOf(raw ?: fallback.name) }.getOrDefault(fallback)
    }
}
