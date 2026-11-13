package com.example.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.firebase.UsernameLookupDocument
import com.example.core.model.CallType
import com.example.core.model.ContactSummary
import com.example.core.model.ConversationSummary
import com.example.core.model.FutureFeatureRegistry
import com.example.ui.components.AvatarCircle
import com.example.ui.components.CallHistoryItem
import com.example.ui.components.ContactListItem
import com.example.ui.components.ConversationListItem
import com.example.ui.components.EmptyFeatureState
import com.example.ui.components.GroupListItem
import com.example.ui.components.RmMessengerLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
    onStartCall: (peerUid: String, peerName: String, peerUsername: String, callType: CallType) -> Unit = { _, _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userAccount = state.currentUserAccount
    var conversationMenuTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var reportTargetContact by remember { mutableStateOf<ContactSummary?>(null) }
    var reportReason by remember { mutableStateOf("Spam or Abuse") }
    var reportDetails by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RmMessengerLogo(size = 34.dp)
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = state.lastCheckedNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = viewModel::toggleSearch,
                                modifier = Modifier.testTag("home_search_button"),
                            ) {
                                Icon(
                                    imageVector = if (state.isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = stringResource(
                                        id = if (state.isSearchVisible) R.string.cd_close_search else R.string.cd_search,
                                    ),
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("home_settings_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(id = R.string.cd_settings),
                                )
                            }
                            IconButton(
                                onClick = onOpenProfile,
                                modifier = Modifier.testTag("home_profile_button"),
                            ) {
                                val initials = userAccount.displayName.ifBlank { "RM" }
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().toString() }
                                    .ifBlank { "RM" }
                                AvatarCircle(
                                    initials = initials,
                                    size = 34.dp,
                                    showOnlineBadge = userAccount.isOnline,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )

                    AnimatedVisibility(visible = state.isSearchVisible) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(id = R.string.search_placeholder)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("home_search_input"),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        HomeTab.entries.forEach { tab ->
                            val selected = state.activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIcon(
                                        tab = tab,
                                        selected = selected,
                                        unreadChats = state.conversations.sumOf { it.unreadCount },
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                val (fabIcon, fabLabel, sheetType) = when (state.activeTab) {
                    HomeTab.CHATS -> Triple(Icons.AutoMirrored.Rounded.Chat, "New Chat", HomeSheetType.NEW_CHAT)
                    HomeTab.CALLS -> Triple(Icons.Rounded.Call, "New Call", HomeSheetType.NEW_CALL)
                    HomeTab.CONTACTS -> Triple(Icons.Rounded.PersonAdd, "Add Contact", HomeSheetType.ADD_CONTACT)
                    HomeTab.GROUPS -> Triple(Icons.Rounded.GroupAdd, "New Group", HomeSheetType.CREATE_GROUP)
                }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openSheet(sheetType) },
                    icon = { Icon(fabIcon, contentDescription = null) },
                    text = { Text(fabLabel) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("home_primary_fab"),
                )
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HomeTab.entries.forEach { tab ->
                            val selected = state.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    TabIcon(
                                        tab = tab,
                                        selected = selected,
                                        unreadChats = state.conversations.sumOf { it.unreadCount },
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    val activeAnnouncement = state.announcements.firstOrNull()
                    if (activeAnnouncement != null) {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("home_announcement_banner"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "${activeAnnouncement.title} • ${activeAnnouncement.priority.uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        text = activeAnnouncement.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissAnnouncement(activeAnnouncement.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss announcement",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
                        AnimatedContent(
                            targetState = state.activeTab,
                            label = "home_tab_transition",
                        ) { tab ->
                        when (tab) {
                            HomeTab.CHATS -> ChatsTabContent(
                                state = state,
                                onOpenConversation = onOpenConversation,
                                onConversationLongPress = { conversationMenuTarget = it },
                                onStartNewChat = { viewModel.openSheet(HomeSheetType.NEW_CHAT) },
                            )
                            HomeTab.CALLS -> CallsTabContent(
                                state = state,
                                onNewCallClick = { viewModel.openSheet(HomeSheetType.NEW_CALL) },
                                onCallBack = { record ->
                                    if (record.peerUid.isNotBlank()) {
                                        onStartCall(
                                            record.peerUid,
                                            record.participantName,
                                            record.peerUsername,
                                            record.callType,
                                        )
                                    } else {
                                        viewModel.openSheet(HomeSheetType.NEW_CALL)
                                    }
                                },
                            )
                            HomeTab.CONTACTS -> ContactsTabContent(
                                state = state,
                                onAddContactClick = { viewModel.openSheet(HomeSheetType.ADD_CONTACT) },
                                onContactClick = viewModel::openContactDetail,
                            )
                            HomeTab.GROUPS -> GroupsTabContent(
                                state = state,
                                onOpenGroup = onOpenConversation,
                                onCreateGroupClick = { viewModel.openSheet(HomeSheetType.CREATE_GROUP) },
                            )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentSheet = state.activeSheet
    if (currentSheet != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            HomeActionSheetContent(
                sheetType = currentSheet,
                state = state,
                onUsernameQueryChange = viewModel::updateUsernameSearch,
                onSearchUsername = viewModel::searchUserByUsername,
                onAddLookedUpContact = viewModel::addLookedUpContact,
                onOpenAddContact = { viewModel.openSheet(HomeSheetType.ADD_CONTACT) },
                onStartDirectChat = { contact ->
                    viewModel.startDirectChat(
                        peerUid = contact.uid,
                        peerDisplayName = contact.displayName,
                        peerUsername = contact.username,
                        onOpened = onOpenConversation,
                    )
                },
                onGroupTitleChange = viewModel::updateNewGroupTitle,
                onGroupDescChange = viewModel::updateNewGroupDescription,
                onToggleGroupMember = viewModel::toggleGroupMemberSelection,
                onCreateGroupSubmit = { viewModel.createGroup(onOpenConversation) },
                onStartCall = { peerUid, peerName, peerUsername, callType ->
                    viewModel.dismissSheet()
                    onStartCall(peerUid, peerName, peerUsername, callType)
                },
                onClose = viewModel::dismissSheet,
            )
        }
    }

    val selectedContact = state.selectedContact
    if (selectedContact != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            ContactDetailSheet(
                contact = selectedContact,
                isOpening = state.isCreatingChatOrGroup,
                onToggleBlock = {
                    viewModel.setContactBlocked(selectedContact.uid, !selectedContact.isBlocked)
                },
                onStartChat = {
                    viewModel.startDirectChat(
                        peerUid = selectedContact.uid,
                        peerDisplayName = selectedContact.displayName,
                        peerUsername = selectedContact.username,
                        onOpened = onOpenConversation,
                    )
                },
                onStartCall = { callType ->
                    viewModel.dismissSheet()
                    onStartCall(
                        selectedContact.uid,
                        selectedContact.displayName,
                        selectedContact.username,
                        callType,
                    )
                },
                onReport = {
                    viewModel.dismissSheet()
                    reportTargetContact = selectedContact
                },
                onClose = viewModel::dismissSheet,
            )
        }
    }

    val contactToReport = reportTargetContact
    if (contactToReport != null) {
        AlertDialog(
            onDismissRequest = { reportTargetContact = null },
            title = { Text("Report ${contactToReport.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it.take(100) },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(600) },
                        label = { Text("Additional details (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportContact(
                            contact = contactToReport,
                            reason = reportReason.ifBlank { "Spam or Abuse" },
                            details = reportDetails,
                        )
                        reportDetails = ""
                        reportTargetContact = null
                    },
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTargetContact = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val convTarget = conversationMenuTarget
    if (convTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { conversationMenuTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = convTarget.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(if (convTarget.isPinned) "Unpin conversation" else "Pin conversation") },
                    leadingContent = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.togglePinConversation(convTarget.conversationId, convTarget.isPinned)
                        conversationMenuTarget = null
                    },
                )
                ListItem(
                    headlineContent = { Text(if (convTarget.isMuted) "Unmute notifications" else "Mute notifications") },
                    leadingContent = {
                        Icon(
                            if (convTarget.isMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleMuteConversation(convTarget.conversationId, convTarget.isMuted)
                        conversationMenuTarget = null
                    },
                )
            }
        }
    }
}

@Composable
private fun TabIcon(
    tab: HomeTab,
    selected: Boolean,
    unreadChats: Int,
) {
    val icon = when (tab) {
        HomeTab.CHATS -> if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
        HomeTab.CALLS -> if (selected) Icons.Rounded.Call else Icons.Outlined.Call
        HomeTab.CONTACTS -> if (selected) Icons.Rounded.Contacts else Icons.Outlined.Contacts
        HomeTab.GROUPS -> if (selected) Icons.Rounded.Groups else Icons.Outlined.Groups
    }
    if (tab == HomeTab.CHATS && unreadChats > 0) {
        BadgedBox(badge = { Badge { Text(unreadChats.toString()) } }) {
            Icon(imageVector = icon, contentDescription = tab.title)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.title)
    }
}

@Composable
private fun ChatsTabContent(
    state: HomeUiState,
    onOpenConversation: (String) -> Unit,
    onConversationLongPress: (ConversationSummary) -> Unit,
    onStartNewChat: () -> Unit,
) {
    val filtered = state.conversations.filter {
        state.searchQuery.isBlank() ||
            it.title.contains(state.searchQuery, ignoreCase = true) ||
            it.lastMessagePreview.contains(state.searchQuery, ignoreCase = true)
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyFeatureState(
                title = stringResource(id = R.string.empty_chats_title),
                description = "Tap New Chat below to message any saved contact in real time via Cloud Firestore.",
                phaseLabel = "Phase 3 Real-Time Messaging",
                actionLabel = "Start New Chat",
                onAction = onStartNewChat,
                showHeroArtwork = true,
                actionTestTag = "empty_chats_cta",
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.conversationId }) { item ->
                ConversationListItem(
                    conversation = item,
                    onClick = { onOpenConversation(item.conversationId) },
                    onLongClick = { onConversationLongPress(item) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CallsTabContent(
    state: HomeUiState,
    onNewCallClick: () -> Unit,
    onCallBack: (com.example.core.model.CallRecord) -> Unit,
) {
    val filtered = state.calls.filter {
        state.searchQuery.isBlank() || it.participantName.contains(state.searchQuery, ignoreCase = true)
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyFeatureState(
                title = stringResource(id = R.string.empty_calls_title),
                description = "Start a real-time WebRTC audio or video call with any saved contact.",
                phaseLabel = "Phase 5 Audio & Video Calls",
                actionLabel = "Start Audio / Video Call",
                onAction = onNewCallClick,
                icon = Icons.Outlined.Call,
                actionTestTag = "empty_calls_cta",
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.callId }) { record ->
                CallHistoryItem(
                    record = record,
                    onCallBack = { onCallBack(record) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ContactsTabContent(
    state: HomeUiState,
    onAddContactClick: () -> Unit,
    onContactClick: (ContactSummary) -> Unit,
) {
    val filtered = state.contacts.filter {
        state.searchQuery.isBlank() ||
            it.displayName.contains(state.searchQuery, ignoreCase = true) ||
            it.username.contains(state.searchQuery, ignoreCase = true)
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyFeatureState(
                title = "No contacts saved yet",
                description = "Search RM Messenger users by their @username to add them directly to your Cloud Firestore contacts.",
                phaseLabel = "Live Firestore Contacts",
                actionLabel = "Find User by @username",
                onAction = onAddContactClick,
                icon = Icons.Outlined.Contacts,
                actionTestTag = "empty_contacts_cta",
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.uid }) { contact ->
                ContactListItem(
                    contact = contact,
                    onClick = { onContactClick(contact) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun GroupsTabContent(
    state: HomeUiState,
    onOpenGroup: (String) -> Unit,
    onCreateGroupClick: () -> Unit,
) {
    val filtered = state.groups.filter {
        state.searchQuery.isBlank() || it.groupName.contains(state.searchQuery, ignoreCase = true)
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyFeatureState(
                title = stringResource(id = R.string.empty_groups_title),
                description = "Create a real-time group room with your saved contacts, assign admins, and chat together.",
                phaseLabel = "Phase 3 Group Spaces",
                actionLabel = "Create Group",
                onAction = onCreateGroupClick,
                icon = Icons.Outlined.Groups,
                actionTestTag = "empty_groups_cta",
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.groupId }) { group ->
                GroupListItem(group = group, onClick = { onOpenGroup(group.groupId) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun HomeActionSheetContent(
    sheetType: HomeSheetType,
    state: HomeUiState,
    onUsernameQueryChange: (String) -> Unit,
    onSearchUsername: () -> Unit,
    onAddLookedUpContact: (UsernameLookupDocument) -> Unit,
    onOpenAddContact: () -> Unit,
    onStartDirectChat: (ContactSummary) -> Unit,
    onGroupTitleChange: (String) -> Unit,
    onGroupDescChange: (String) -> Unit,
    onToggleGroupMember: (String) -> Unit,
    onCreateGroupSubmit: () -> Unit,
    onStartCall: (String, String, String, CallType) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .heightIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (sheetType) {
            HomeSheetType.NEW_CHAT -> {
                Text("Start Private Chat", style = MaterialTheme.typography.titleLarge)
                if (state.contacts.isEmpty()) {
                    Text(
                        text = "You don't have any saved contacts yet. Find an RM Messenger user by @username to start chatting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Select a contact to open your real-time conversation:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.contacts.forEach { contact ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isCreatingChatOrGroup) {
                                    onStartDirectChat(contact)
                                },
                        ) {
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text("@${contact.username}") },
                                leadingContent = {
                                    AvatarCircle(initials = contact.displayName.take(2), size = 40.dp)
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = onOpenAddContact,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Find User by @username")
                }
            }
            HomeSheetType.NEW_CALL -> {
                Text("Start Audio or Video Call", style = MaterialTheme.typography.titleLarge)
                if (state.contacts.isEmpty()) {
                    Text(
                        text = "Add a contact by @username first to start a WebRTC voice or video call.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenAddContact,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Find User by @username")
                    }
                } else {
                    Text(
                        text = "Choose a contact and call mode:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.contacts.forEach { contact ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text("@${contact.username}") },
                                leadingContent = {
                                    AvatarCircle(initials = contact.displayName.take(2), size = 40.dp)
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                onStartCall(
                                                    contact.uid,
                                                    contact.displayName,
                                                    contact.username,
                                                    CallType.AUDIO,
                                                )
                                            },
                                            modifier = Modifier.testTag("sheet_audio_call_${contact.uid}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Call,
                                                contentDescription = "Audio call ${contact.displayName}",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onStartCall(
                                                    contact.uid,
                                                    contact.displayName,
                                                    contact.username,
                                                    CallType.VIDEO,
                                                )
                                            },
                                            modifier = Modifier.testTag("sheet_video_call_${contact.uid}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Videocam,
                                                contentDescription = "Video call ${contact.displayName}",
                                                tint = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            HomeSheetType.ADD_CONTACT -> {
                Text("Find User by @username", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Search any registered RM Messenger handle in Cloud Firestore without exposing private phone numbers or emails.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.usernameSearchQuery,
                    onValueChange = onUsernameQueryChange,
                    label = { Text("Exact username") },
                    placeholder = { Text("rahul_rm") },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_username_input"),
                )
                Button(
                    onClick = onSearchUsername,
                    enabled = !state.isSearchingUsername,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_username_button"),
                ) {
                    if (state.isSearchingUsername) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search User")
                    }
                }
                if (state.lookupMessage != null) {
                    Text(
                        text = state.lookupMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                val found = state.lookupResult
                if (found != null) {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        ListItem(
                            headlineContent = { Text(found.displayName) },
                            supportingContent = { Text("@${found.username} • ${found.about}") },
                            leadingContent = {
                                AvatarCircle(initials = found.displayName.take(2), size = 42.dp)
                            },
                            trailingContent = {
                                Button(
                                    onClick = { onAddLookedUpContact(found) },
                                    modifier = Modifier.testTag("confirm_add_contact_button"),
                                ) {
                                    Text("Add")
                                }
                            },
                        )
                    }
                }
            }
            HomeSheetType.CREATE_GROUP -> {
                Text("Create a Group Chat", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.newGroupTitle,
                    onValueChange = onGroupTitleChange,
                    label = { Text("Group Name") },
                    placeholder = { Text("Kuwait & BD Team") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_title_input"),
                )
                OutlinedTextField(
                    value = state.newGroupDescription,
                    onValueChange = onGroupDescChange,
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_desc_input"),
                )
                Text(
                    text = "Select Members (${state.selectedGroupMemberUids.size} chosen)",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.contacts.isEmpty()) {
                    Text(
                        text = "Add contacts first to invite them, or create a group and add members later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.contacts.forEach { contact ->
                        val checked = state.selectedGroupMemberUids.contains(contact.uid)
                        ListItem(
                            headlineContent = { Text(contact.displayName) },
                            supportingContent = { Text("@${contact.username}") },
                            leadingContent = {
                                AvatarCircle(initials = contact.displayName.take(2), size = 36.dp)
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleGroupMember(contact.uid) },
                                )
                            },
                            modifier = Modifier.clickable { onToggleGroupMember(contact.uid) },
                        )
                    }
                }
                if (state.groupCreationError != null) {
                    Text(
                        text = state.groupCreationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onCreateGroupSubmit,
                    enabled = !state.isCreatingChatOrGroup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_submit_button"),
                ) {
                    if (state.isCreatingChatOrGroup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Group")
                    }
                }
            }
            HomeSheetType.PHASE_ROADMAP -> {
                Text("RM Messenger Architecture Roadmap", style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FutureFeatureRegistry.modules.forEach { module ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            ListItem(
                                headlineContent = { Text(module.title) },
                                supportingContent = { Text(module.description) },
                                trailingContent = {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = module.phaseLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun ContactDetailSheet(
    contact: ContactSummary,
    isOpening: Boolean,
    onToggleBlock: () -> Unit,
    onStartChat: () -> Unit,
    onStartCall: (CallType) -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarCircle(initials = contact.displayName.take(2), size = 68.dp)
        Text(text = contact.displayName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = "@${contact.username}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = contact.about,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!contact.isBlocked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onStartCall(CallType.AUDIO) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contact_audio_call_button"),
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Call")
                }
                OutlinedButton(
                    onClick = { onStartCall(CallType.VIDEO) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contact_video_call_button"),
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video Call")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onStartChat,
                enabled = !isOpening,
                modifier = Modifier
                    .weight(1f)
                    .testTag("contact_message_button"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Message")
            }
            OutlinedButton(
                onClick = onToggleBlock,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (contact.isBlocked) "Unblock" else "Block")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onReport,
                modifier = Modifier
                    .weight(1f)
                    .testTag("contact_report_button"),
            ) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Report", color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
            ) {
                Text("Close")
            }
        }
    }
}
