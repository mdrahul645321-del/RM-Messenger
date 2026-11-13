package com.example.features.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.firebase.FirestoreAnnouncementDocument
import com.example.core.firebase.FirestoreModerationReportDocument
import com.example.core.firebase.FirestoreUserDocument
import com.example.ui.components.AvatarCircle
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var reportActionTarget by remember { mutableStateOf<FirestoreModerationReportDocument?>(null) }
    var reportResolutionNote by remember { mutableStateOf("") }

    LaunchedEffect(state.statusBanner) {
        val banner = state.statusBanner
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Column {
                                Text(
                                    text = if (state.isSuperAdmin) "Super Admin Console" else "Moderator Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (state.isSuperAdmin) "Owner RBAC • Full Platform Control" else "Admin Moderation Access",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("admin_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
                if (state.isAuthorized) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = AdminTab.entries.indexOf(state.activeTab),
                        edgePadding = 12.dp,
                    ) {
                        AdminTab.entries.forEach { tab ->
                            val badge = when (tab) {
                                AdminTab.REPORTS -> if (state.openReportsCount > 0) " (${state.openReportsCount})" else ""
                                AdminTab.USERS -> if (state.users.isNotEmpty()) " (${state.users.size})" else ""
                                else -> ""
                            }
                            Tab(
                                selected = state.activeTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = { Text("${tab.label}$badge") },
                                modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!state.isAuthorized) {
                UnauthorizedAdminView(onNavigateBack = onNavigateBack)
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                ) {
                    when (state.activeTab) {
                        AdminTab.OVERVIEW -> AdminOverviewTab(
                            state = state,
                            onSelectTab = viewModel::selectTab,
                        )
                        AdminTab.USERS -> AdminUsersTab(
                            state = state,
                            onSearchChange = viewModel::updateUserSearchQuery,
                            onToggleSuspend = viewModel::toggleUserSuspension,
                            onToggleAdminRole = viewModel::toggleUserAdminRole,
                        )
                        AdminTab.REPORTS -> AdminReportsTab(
                            reports = state.reports,
                            onReviewReport = { report ->
                                reportResolutionNote = ""
                                reportActionTarget = report
                            },
                        )
                        AdminTab.BROADCAST -> AdminBroadcastTab(
                            state = state,
                            onUpdateDraft = viewModel::updateAnnouncementDraft,
                            onPublish = viewModel::publishAnnouncement,
                            onToggleActive = viewModel::toggleAnnouncementActive,
                            onDelete = viewModel::deleteAnnouncement,
                        )
                        AdminTab.PLATFORM -> AdminPlatformTab(
                            state = state,
                            onUpdateDraft = viewModel::updatePlatformDraft,
                            onSave = viewModel::savePlatformConfig,
                        )
                    }
                }
            }
        }
    }

    val pendingReport = reportActionTarget
    if (pendingReport != null) {
        AlertDialog(
            onDismissRequest = { reportActionTarget = null },
            title = { Text("Moderate Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Target: ${pendingReport.targetName} (${pendingReport.targetType})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Reason: ${pendingReport.reason}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (pendingReport.details.isNotBlank()) {
                        Text(
                            text = pendingReport.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = reportResolutionNote,
                        onValueChange = { reportResolutionNote = it.take(400) },
                        label = { Text("Resolution note (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.resolveReport(pendingReport.id, "dismissed", reportResolutionNote)
                            reportActionTarget = null
                        },
                    ) {
                        Text("Dismiss")
                    }
                    Button(
                        onClick = {
                            viewModel.resolveReport(pendingReport.id, "resolved", reportResolutionNote)
                            reportActionTarget = null
                        },
                    ) {
                        Text("Resolve")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { reportActionTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun UnauthorizedAdminView(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Super Admin Access Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Only backend-authorized Admin or Super Admin accounts can access RM Messenger moderation controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNavigateBack) {
            Text("Return to Settings")
        }
    }
}

@Composable
private fun AdminOverviewTab(
    state: AdminPanelUiState,
    onSelectTab: (AdminTab) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminMetricCard(
                    title = "Total Users",
                    value = "${state.users.size}",
                    subtitle = "${state.suspendedUsersCount} suspended",
                    icon = Icons.Rounded.Group,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(AdminTab.USERS) },
                )
                AdminMetricCard(
                    title = "Open Reports",
                    value = "${state.openReportsCount}",
                    subtitle = "${state.reports.size} total reports",
                    icon = Icons.Rounded.Flag,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(AdminTab.REPORTS) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AdminMetricCard(
                    title = "Announcements",
                    value = "${state.activeAnnouncementsCount}",
                    subtitle = "${state.announcements.size} published",
                    icon = Icons.Rounded.Campaign,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(AdminTab.BROADCAST) },
                )
                AdminMetricCard(
                    title = "Maintenance",
                    value = if (state.platformConfig.maintenanceMode) "ON" else "OFF",
                    subtitle = if (state.platformConfig.callingEnabled) "Calling live" else "Calling paused",
                    icon = Icons.Rounded.Tune,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(AdminTab.PLATFORM) },
                )
            }
        }
        item {
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "Zero-Trust RBAC Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = if (state.isSuperAdmin) {
                            "Signed in as Super Admin (Owner). You can suspend/reactivate accounts, promote moderators, resolve reports, broadcast announcements, and toggle platform capabilities."
                        } else {
                            "Signed in as Moderator Admin. You can review safety reports and publish platform announcements. Account suspension and system flags require Super Admin."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdminUsersTab(
    state: AdminPanelUiState,
    onSearchChange: (String) -> Unit,
    onToggleSuspend: (FirestoreUserDocument) -> Unit,
    onToggleAdminRole: (FirestoreUserDocument) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.userSearchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search users by name, @username, or email…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("admin_user_search_input"),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.filteredUsers, key = { it.uid }) { user ->
                val isSelf = user.uid == state.currentUserId
                val isTargetSuperAdmin = user.role == "super_admin"
                val isSuspended = user.accountStatus == "suspended"

                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AvatarCircle(initials = user.displayName.take(2), size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${user.username} • ${user.email ?: user.phoneNumber ?: user.uid.take(8)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = when (user.role) {
                                    "super_admin" -> MaterialTheme.colorScheme.primaryContainer
                                    "admin" -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Text(
                                    text = user.role.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSuspended) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    OnlineEmerald.copy(alpha = 0.16f)
                                },
                            ) {
                                Text(
                                    text = user.accountStatus.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuspended) MaterialTheme.colorScheme.error else OnlineEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        if (state.isSuperAdmin && !isSelf && !isTargetSuperAdmin) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleAdminRole(user) },
                                    enabled = !state.isSubmitting,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (user.role == "admin") "Demote User" else "Make Admin")
                                }
                                Button(
                                    onClick = { onToggleSuspend(user) },
                                    enabled = !state.isSubmitting,
                                    colors = if (isSuspended) {
                                        ButtonDefaults.buttonColors(containerColor = OnlineEmerald)
                                    } else {
                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = if (isSuspended) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isSuspended) "Activate" else "Suspend")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminReportsTab(
    reports: List<FirestoreModerationReportDocument>,
    onReviewReport: (FirestoreModerationReportDocument) -> Unit,
) {
    if (reports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No moderation reports submitted yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(reports, key = { it.id }) { report ->
            val isOpen = report.status == "open"
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${report.targetName} (${report.targetType})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isOpen) {
                                WarningAmber.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Text(
                                text = report.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "Reason: ${report.reason} • Reported by ${report.reporterName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (report.details.isNotBlank()) {
                        Text(
                            text = report.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!report.resolutionNote.isNullOrBlank()) {
                        Text(
                            text = "Note: ${report.resolutionNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (isOpen) {
                        Button(
                            onClick = { onReviewReport(report) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Review & Resolve")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBroadcastTab(
    state: AdminPanelUiState,
    onUpdateDraft: (title: String?, body: String?, priority: String?) -> Unit,
    onPublish: () -> Unit,
    onToggleActive: (FirestoreAnnouncementDocument) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Publish Global Announcement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = state.announcementTitleDraft,
                        onValueChange = { onUpdateDraft(it.take(100), null, null) },
                        label = { Text("Announcement title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_announcement_title"),
                    )
                    OutlinedTextField(
                        value = state.announcementBodyDraft,
                        onValueChange = { onUpdateDraft(null, it.take(1000), null) },
                        label = { Text("Message to all RM Messenger users") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_announcement_body"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("info" to "Info", "important" to "Important", "maintenance" to "Notice").forEach { (key, label) ->
                            FilterChip(
                                selected = state.announcementPriorityDraft == key,
                                onClick = { onUpdateDraft(null, null, key) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Button(
                        onClick = onPublish,
                        enabled = !state.isSubmitting &&
                            state.announcementTitleDraft.trim().length >= 2 &&
                            state.announcementBodyDraft.trim().length >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_publish_announcement_button"),
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Broadcast Announcement")
                    }
                }
            }
        }

        items(state.announcements, key = { it.id }) { ann ->
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = ann.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = ann.isActive,
                            onCheckedChange = { onToggleActive(ann) },
                        )
                        IconButton(onClick = { onDelete(ann.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete announcement",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        text = ann.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${ann.priority.uppercase()} • By ${ann.authorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPlatformTab(
    state: AdminPanelUiState,
    onUpdateDraft: ((com.example.core.firebase.FirestorePlatformConfigDocument) -> com.example.core.firebase.FirestorePlatformConfigDocument) -> Unit,
    onSave: () -> Unit,
) {
    val cfg = state.draftConfig
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column {
                    ListItem(
                        headlineContent = { Text("Maintenance Mode") },
                        supportingContent = { Text("Temporarily pause app access for regular users") },
                        trailingContent = {
                            Switch(
                                checked = cfg.maintenanceMode,
                                enabled = state.isSuperAdmin,
                                onCheckedChange = { enabled ->
                                    onUpdateDraft { it.copy(maintenanceMode = enabled) }
                                },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Audio & Video Calling") },
                        supportingContent = { Text("Enable WebRTC voice and video calls globally") },
                        trailingContent = {
                            Switch(
                                checked = cfg.callingEnabled,
                                enabled = state.isSuperAdmin,
                                onCheckedChange = { enabled ->
                                    onUpdateDraft { it.copy(callingEnabled = enabled) }
                                },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Voice Messages") },
                        supportingContent = { Text("Allow recording and sending voice notes") },
                        trailingContent = {
                            Switch(
                                checked = cfg.voiceMessagesEnabled,
                                enabled = state.isSuperAdmin,
                                onCheckedChange = { enabled ->
                                    onUpdateDraft { it.copy(voiceMessagesEnabled = enabled) }
                                },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Media & File Sharing") },
                        supportingContent = { Text("Allow sending images, videos, audio & documents") },
                        trailingContent = {
                            Switch(
                                checked = cfg.mediaSharingEnabled,
                                enabled = state.isSuperAdmin,
                                onCheckedChange = { enabled ->
                                    onUpdateDraft { it.copy(mediaSharingEnabled = enabled) }
                                },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Allow New Registrations") },
                        supportingContent = { Text("Control new account onboarding") },
                        trailingContent = {
                            Switch(
                                checked = cfg.allowNewRegistrations,
                                enabled = state.isSuperAdmin,
                                onCheckedChange = { enabled ->
                                    onUpdateDraft { it.copy(allowNewRegistrations = enabled) }
                                },
                            )
                        },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = cfg.maintenanceMessage,
                onValueChange = { msg -> onUpdateDraft { it.copy(maintenanceMessage = msg.take(300)) } },
                label = { Text("Maintenance screen message") },
                enabled = state.isSuperAdmin,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isSuperAdmin) {
            item {
                Button(
                    onClick = onSave,
                    enabled = !state.isSubmitting && state.hasPendingConfigEdits,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_save_platform_button"),
                ) {
                    Text(if (state.hasPendingConfigEdits) "Save Platform Configuration" else "Configuration Up To Date")
                }
            }
        }
    }
}
