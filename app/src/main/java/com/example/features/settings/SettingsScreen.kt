package com.example.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.firebase.GoogleAuthHelper
import com.example.core.firebase.UserRepository
import com.example.core.model.AppLanguage
import com.example.core.model.ContactSummary
import com.example.core.model.FutureFeatureRegistry
import com.example.core.model.ThemeMode
import com.example.core.model.VisibilityOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userRepository: UserRepository,
    blockedContacts: List<ContactSummary>,
    onUnblockContact: (String) -> Unit,
    onNavigateBack: () -> Unit,
    currentUserRole: String = "user",
    onOpenAdminPanel: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showModulesDialog by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (currentUserRole == "admin" || currentUserRole == "super_admin") {
                    SettingsSectionHeader(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = "Platform Governance",
                    )
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (currentUserRole == "super_admin") "Super Admin Panel" else "Moderator Panel",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            supportingContent = {
                                Text("Manage users, moderation reports, announcements & platform controls")
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier
                                .clickable(onClick = onOpenAdminPanel)
                                .testTag("settings_admin_panel_entry"),
                        )
                    }
                }

                SettingsSectionHeader(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(id = R.string.appearance_section),
                )
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    modifier = Modifier.testTag("theme_option_${mode.name.lowercase()}"),
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }

                SettingsSectionHeader(
                    icon = Icons.Rounded.PrivacyTip,
                    title = stringResource(id = R.string.privacy_section),
                )
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column {
                        VisibilitySettingRow(
                            title = "Last seen visibility",
                            current = state.privacy.lastSeenVisibility,
                            onSelect = viewModel::updateLastSeenVisibility,
                            testTag = "privacy_last_seen",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        VisibilitySettingRow(
                            title = "Online status visibility",
                            current = state.privacy.onlineVisibility,
                            onSelect = viewModel::updateOnlineVisibility,
                            testTag = "privacy_online",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        VisibilitySettingRow(
                            title = "Profile photo visibility",
                            current = state.privacy.profilePhotoVisibility,
                            onSelect = viewModel::updatePhotoVisibility,
                            testTag = "privacy_photo",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        VisibilitySettingRow(
                            title = "About / Bio visibility",
                            current = state.privacy.aboutVisibility,
                            onSelect = viewModel::updateAboutVisibility,
                            testTag = "privacy_about",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Read receipts") },
                            supportingContent = { Text("Let contacts see when you have read messages") },
                            trailingContent = {
                                Switch(
                                    checked = state.privacy.readReceiptsEnabled,
                                    onCheckedChange = viewModel::toggleReadReceipts,
                                    modifier = Modifier.testTag("privacy_read_receipts_switch"),
                                )
                            },
                        )
                    }
                }

                SettingsSectionHeader(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(id = R.string.notifications_section),
                )
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column {
                        NotificationToggleRow(
                            title = "Message notifications",
                            checked = state.notifications.messageAlerts,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications { it.copy(messageAlerts = enabled) }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NotificationToggleRow(
                            title = "Incoming call alerts",
                            checked = state.notifications.callAlerts,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications { it.copy(callAlerts = enabled) }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NotificationToggleRow(
                            title = "Group notifications",
                            checked = state.notifications.groupAlerts,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications { it.copy(groupAlerts = enabled) }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NotificationToggleRow(
                            title = "In-app sounds",
                            checked = state.notifications.inAppSounds,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications { it.copy(inAppSounds = enabled) }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NotificationToggleRow(
                            title = "Vibration",
                            checked = state.notifications.vibrationEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications { it.copy(vibrationEnabled = enabled) }
                            },
                        )
                    }
                }

                SettingsSectionHeader(
                    icon = Icons.Rounded.Language,
                    title = stringResource(id = R.string.language_section),
                )
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box {
                        ListItem(
                            headlineContent = { Text("App Language") },
                            supportingContent = {
                                Text("${state.language.displayName} (${state.language.nativeName})")
                            },
                            modifier = Modifier
                                .clickable { showLanguageMenu = true }
                                .testTag("settings_language_row"),
                        )
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.displayName} • ${lang.nativeName}") },
                                    onClick = {
                                        viewModel.setLanguage(lang)
                                        showLanguageMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSectionHeader(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(id = R.string.account_section),
                )
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Blocked Users") },
                            supportingContent = { Text("${blockedContacts.size} blocked account(s)") },
                            leadingContent = { Icon(Icons.Rounded.Block, contentDescription = null) },
                            modifier = Modifier
                                .clickable { showBlockedDialog = true }
                                .testTag("settings_blocked_users"),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Future Feature Modules") },
                            supportingContent = { Text("Inspect Phase 3–6 & Remote Config architecture") },
                            leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                            modifier = Modifier
                                .clickable { showModulesDialog = true }
                                .testTag("settings_modules_row"),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Request Account Deletion",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            supportingContent = { Text("Server-side cleanup activates with Cloud Functions") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier
                                .clickable { showDeleteAccountDialog = true }
                                .testTag("settings_delete_account"),
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val credentialManager = CredentialManager.create(context)
                        GoogleAuthHelper.signOut(
                            credentialManager = credentialManager,
                            userRepository = userRepository,
                            scope = scope,
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Users") },
            text = {
                if (blockedContacts.isEmpty()) {
                    Text("You have no blocked contacts.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedContacts.forEach { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${contact.displayName} (@${contact.username})")
                                TextButton(onClick = { onUnblockContact(contact.uid) }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Close") }
            },
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Account Deletion") },
            text = {
                Text("Full account deletion removes your Firebase Auth record and triggers Cloud Functions cleanup across conversations and media.")
            },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Understood") }
            },
        )
    }

    if (showModulesDialog) {
        AlertDialog(
            onDismissRequest = { showModulesDialog = false },
            title = { Text("Modular Feature Architecture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutureFeatureRegistry.modules.forEach { module ->
                        Text(
                            text = "• ${module.phaseLabel}: ${module.title}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModulesDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VisibilitySettingRow(
    title: String,
    current: VisibilityOption,
    onSelect: (VisibilityOption) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(current.label) },
            modifier = Modifier
                .clickable { expanded = true }
                .testTag(testTag),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            VisibilityOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
