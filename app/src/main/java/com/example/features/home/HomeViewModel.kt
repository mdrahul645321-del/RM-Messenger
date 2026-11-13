package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.firebase.AdminRepository
import com.example.core.firebase.CallRepository
import com.example.core.firebase.ChatRepository
import com.example.core.firebase.FirestoreAnnouncementDocument
import com.example.core.firebase.FirestoreCallSessionDocument
import com.example.core.firebase.FirestoreContactDocument
import com.example.core.firebase.FirestoreConversationDocument
import com.example.core.firebase.FirestorePlatformConfigDocument
import com.example.core.firebase.FirestoreUserDocument
import com.example.core.firebase.UserRepository
import com.example.core.firebase.UsernameLookupDocument
import com.example.core.model.CallDirection
import com.example.core.model.CallRecord
import com.example.core.model.CallType
import com.example.core.model.ContactSummary
import com.example.core.model.ConversationSummary
import com.example.core.model.GroupSummary
import com.example.core.model.MessageDeliveryState
import com.example.core.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab(val title: String) {
    CHATS("Chats"),
    CALLS("Calls"),
    CONTACTS("Contacts"),
    GROUPS("Groups"),
}

enum class HomeSheetType {
    NEW_CHAT,
    NEW_CALL,
    ADD_CONTACT,
    CREATE_GROUP,
    PHASE_ROADMAP,
}

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.CHATS,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val activeSheet: HomeSheetType? = null,
    val selectedContact: ContactSummary? = null,
    val currentUserAccount: UserAccount = UserAccount(),
    val conversations: List<ConversationSummary> = emptyList(),
    val calls: List<CallRecord> = emptyList(),
    val contacts: List<ContactSummary> = emptyList(),
    val blockedContacts: List<ContactSummary> = emptyList(),
    val groups: List<GroupSummary> = emptyList(),
    val lastCheckedNote: String = "Phase 3 Connected • Real-Time Messaging",
    val firestoreError: String? = null,
    // Contact lookup state
    val usernameSearchQuery: String = "",
    val isSearchingUsername: Boolean = false,
    val lookupResult: UsernameLookupDocument? = null,
    val lookupMessage: String? = null,
    // Group creation & action state
    val newGroupTitle: String = "",
    val newGroupDescription: String = "",
    val selectedGroupMemberUids: Set<String> = emptySet(),
    val isCreatingChatOrGroup: Boolean = false,
    val groupCreationError: String? = null,
    val announcements: List<FirestoreAnnouncementDocument> = emptyList(),
    val platformConfig: FirestorePlatformConfigDocument = FirestorePlatformConfigDocument(),
    val statusBanner: String? = null,
)

private data class LocalHomeTransientState(
    val activeTab: HomeTab = HomeTab.CHATS,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val activeSheet: HomeSheetType? = null,
    val selectedContact: ContactSummary? = null,
    val lastCheckedNote: String = "Phase 6 Live • RM Messenger",
    val usernameSearchQuery: String = "",
    val isSearchingUsername: Boolean = false,
    val lookupResult: UsernameLookupDocument? = null,
    val lookupMessage: String? = null,
    val newGroupTitle: String = "",
    val newGroupDescription: String = "",
    val selectedGroupMemberUids: Set<String> = emptySet(),
    val isCreatingChatOrGroup: Boolean = false,
    val groupCreationError: String? = null,
    val dismissedAnnouncementIds: Set<String> = emptySet(),
    val statusBanner: String? = null,
)

class HomeViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val currentUserId: String,
    private val callRepository: CallRepository? = null,
    private val adminRepository: AdminRepository? = null,
) : ViewModel() {

    private val transientState = MutableStateFlow(LocalHomeTransientState())
    private val streamErrorState = MutableStateFlow<String?>(null)

    private val remoteUserFlow = userRepository.observeCurrentUser()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            streamErrorState.value = root.localizedMessage ?: "Failed to sync user profile."
            emit(null)
        }

    private val remoteContactsFlow = userRepository.observeContacts()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            streamErrorState.value = root.localizedMessage ?: "Failed to sync contacts."
            emit(emptyList())
        }

    private val remoteConversationsFlow = chatRepository.observeConversations()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            streamErrorState.value = root.localizedMessage ?: "Failed to sync conversations."
            emit(emptyList())
        }

    private val remoteCallsFlow = (callRepository?.observeCallHistory() ?: flowOf(emptyList()))
        .catch { emit(emptyList()) }

    private val remoteAnnouncementsFlow = (adminRepository?.observeAnnouncements(activeOnly = true) ?: flowOf(emptyList()))
        .catch { emit(emptyList()) }

    private val remotePlatformConfigFlow = (adminRepository?.observePlatformConfig() ?: flowOf(FirestorePlatformConfigDocument()))
        .catch { emit(FirestorePlatformConfigDocument()) }

    val uiState: StateFlow<HomeUiState> = combine(
        transientState,
        remoteUserFlow,
        remoteContactsFlow,
        remoteConversationsFlow,
        remoteCallsFlow,
        remoteAnnouncementsFlow,
        remotePlatformConfigFlow,
        streamErrorState,
    ) { values ->
        val local = values[0] as LocalHomeTransientState
        val userDoc = values[1] as FirestoreUserDocument?
        @Suppress("UNCHECKED_CAST")
        val contactDocs = values[2] as List<FirestoreContactDocument>
        @Suppress("UNCHECKED_CAST")
        val convDocs = values[3] as List<FirestoreConversationDocument>
        @Suppress("UNCHECKED_CAST")
        val callDocs = values[4] as List<FirestoreCallSessionDocument>
        @Suppress("UNCHECKED_CAST")
        val allAnnouncements = values[5] as List<FirestoreAnnouncementDocument>
        val platformConfig = values[6] as FirestorePlatformConfigDocument
        val err = values[7] as String?

        val account = userDoc.toUserAccount(currentUserId)
        val allContacts = contactDocs.map { it.toContactSummary() }
        val conversations = convDocs.map { it.toConversationSummary(currentUserId) }
        val calls = callDocs.map { it.toCallRecord(currentUserId) }
        val groups = convDocs
            .filter { it.type == "group" }
            .map { it.toGroupSummary(currentUserId) }

        HomeUiState(
            activeTab = local.activeTab,
            isSearchVisible = local.isSearchVisible,
            searchQuery = local.searchQuery,
            isRefreshing = local.isRefreshing,
            activeSheet = local.activeSheet,
            selectedContact = local.selectedContact,
            currentUserAccount = account,
            conversations = conversations,
            calls = calls,
            contacts = allContacts.filter { !it.isBlocked },
            blockedContacts = allContacts.filter { it.isBlocked },
            groups = groups,
            lastCheckedNote = if (account.username.isNotBlank()) {
                "@${account.username} • Online"
            } else {
                local.lastCheckedNote
            },
            firestoreError = err,
            usernameSearchQuery = local.usernameSearchQuery,
            isSearchingUsername = local.isSearchingUsername,
            lookupResult = local.lookupResult,
            lookupMessage = local.lookupMessage,
            newGroupTitle = local.newGroupTitle,
            newGroupDescription = local.newGroupDescription,
            selectedGroupMemberUids = local.selectedGroupMemberUids,
            isCreatingChatOrGroup = local.isCreatingChatOrGroup,
            groupCreationError = local.groupCreationError,
            announcements = allAnnouncements.filterNot { local.dismissedAnnouncementIds.contains(it.id) },
            platformConfig = platformConfig,
            statusBanner = local.statusBanner,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState(),
    )

    fun selectTab(tab: HomeTab) {
        transientState.update { it.copy(activeTab = tab) }
    }

    fun toggleSearch() {
        transientState.update {
            val nextVisible = !it.isSearchVisible
            it.copy(
                isSearchVisible = nextVisible,
                searchQuery = if (nextVisible) it.searchQuery else "",
            )
        }
    }

    fun updateSearchQuery(query: String) {
        transientState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        if (transientState.value.isRefreshing) return
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true) }
            delay(600L)
            transientState.update {
                it.copy(
                    isRefreshing = false,
                    lastCheckedNote = "Cloud Firestore synced",
                )
            }
        }
    }

    fun openSheet(sheet: HomeSheetType) {
        transientState.update {
            it.copy(
                activeSheet = sheet,
                selectedContact = null,
                lookupResult = null,
                lookupMessage = null,
                usernameSearchQuery = "",
                newGroupTitle = "",
                newGroupDescription = "",
                selectedGroupMemberUids = emptySet(),
                groupCreationError = null,
            )
        }
    }

    fun dismissSheet() {
        transientState.update {
            it.copy(
                activeSheet = null,
                selectedContact = null,
                lookupResult = null,
                lookupMessage = null,
                groupCreationError = null,
            )
        }
    }

    fun openContactDetail(contact: ContactSummary) {
        transientState.update { it.copy(selectedContact = contact) }
    }

    fun updateUsernameSearch(query: String) {
        transientState.update {
            it.copy(
                usernameSearchQuery = query,
                lookupMessage = null,
            )
        }
    }

    fun searchUserByUsername() {
        val query = transientState.value.usernameSearchQuery
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    isSearchingUsername = true,
                    lookupResult = null,
                    lookupMessage = null,
                )
            }
            val result = userRepository.lookupByUsername(query)
            result.fold(
                onSuccess = { doc ->
                    transientState.update {
                        it.copy(
                            isSearchingUsername = false,
                            lookupResult = doc,
                            lookupMessage = if (doc == null) "No RM Messenger user found for @$query." else null,
                        )
                    }
                },
                onFailure = { error ->
                    transientState.update {
                        it.copy(
                            isSearchingUsername = false,
                            lookupMessage = error.localizedMessage ?: "Username search failed.",
                        )
                    }
                },
            )
        }
    }

    fun addLookedUpContact(target: UsernameLookupDocument) {
        viewModelScope.launch {
            val res = userRepository.addContact(target)
            res.fold(
                onSuccess = {
                    transientState.update {
                        it.copy(
                            lookupMessage = "${target.displayName} added to your Contacts.",
                            lookupResult = null,
                            usernameSearchQuery = "",
                        )
                    }
                },
                onFailure = { err ->
                    transientState.update {
                        it.copy(lookupMessage = err.localizedMessage ?: "Could not add contact.")
                    }
                },
            )
        }
    }

    fun startDirectChat(
        peerUid: String,
        peerDisplayName: String,
        peerUsername: String,
        onOpened: (String) -> Unit,
    ) {
        if (transientState.value.isCreatingChatOrGroup) return
        val account = uiState.value.currentUserAccount
        viewModelScope.launch {
            transientState.update { it.copy(isCreatingChatOrGroup = true, groupCreationError = null) }
            val result = chatRepository.getOrCreateDirectConversation(
                currentUser = account,
                peerUid = peerUid,
                peerDisplayName = peerDisplayName,
                peerUsername = peerUsername,
            )
            result.fold(
                onSuccess = { convId ->
                    transientState.update {
                        it.copy(
                            isCreatingChatOrGroup = false,
                            activeSheet = null,
                            selectedContact = null,
                        )
                    }
                    onOpened(convId)
                },
                onFailure = { err ->
                    transientState.update {
                        it.copy(
                            isCreatingChatOrGroup = false,
                            lookupMessage = err.localizedMessage ?: "Failed to open conversation.",
                        )
                    }
                },
            )
        }
    }

    fun updateNewGroupTitle(title: String) {
        transientState.update { it.copy(newGroupTitle = title.take(80), groupCreationError = null) }
    }

    fun updateNewGroupDescription(desc: String) {
        transientState.update { it.copy(newGroupDescription = desc.take(200)) }
    }

    fun toggleGroupMemberSelection(uid: String) {
        transientState.update { state ->
            val next = state.selectedGroupMemberUids.toMutableSet()
            if (next.contains(uid)) next.remove(uid) else next.add(uid)
            state.copy(selectedGroupMemberUids = next, groupCreationError = null)
        }
    }

    fun createGroup(onCreated: (String) -> Unit) {
        val local = transientState.value
        val state = uiState.value
        if (local.isCreatingChatOrGroup) return
        if (local.newGroupTitle.trim().length < 2) {
            transientState.update { it.copy(groupCreationError = "Group name must be at least 2 characters.") }
            return
        }
        val selectedMembers = state.contacts.filter { local.selectedGroupMemberUids.contains(it.uid) }

        viewModelScope.launch {
            transientState.update { it.copy(isCreatingChatOrGroup = true, groupCreationError = null) }
            val result = chatRepository.createGroupConversation(
                currentUser = state.currentUserAccount,
                title = local.newGroupTitle,
                description = local.newGroupDescription,
                selectedMembers = selectedMembers,
            )
            result.fold(
                onSuccess = { convId ->
                    transientState.update {
                        it.copy(
                            isCreatingChatOrGroup = false,
                            activeSheet = null,
                            newGroupTitle = "",
                            newGroupDescription = "",
                            selectedGroupMemberUids = emptySet(),
                        )
                    }
                    onCreated(convId)
                },
                onFailure = { err ->
                    transientState.update {
                        it.copy(
                            isCreatingChatOrGroup = false,
                            groupCreationError = err.localizedMessage ?: "Failed to create group.",
                        )
                    }
                },
            )
        }
    }

    fun togglePinConversation(conversationId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            chatRepository.togglePinConversation(conversationId, currentlyPinned)
        }
    }

    fun toggleMuteConversation(conversationId: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleMuteConversation(conversationId, currentlyMuted)
        }
    }

    fun setContactBlocked(contactUid: String, blocked: Boolean) {
        viewModelScope.launch {
            userRepository.toggleBlockContact(contactUid, blocked)
            transientState.update { it.copy(selectedContact = null) }
        }
    }

    suspend fun saveProfile(
        displayName: String,
        username: String,
        about: String,
        email: String?,
    ): Result<Unit> {
        return userRepository.updateProfile(
            displayName = displayName,
            newUsername = username,
            about = about,
            email = email,
        )
    }

    fun dismissAnnouncement(announcementId: String) {
        transientState.update {
            it.copy(dismissedAnnouncementIds = it.dismissedAnnouncementIds + announcementId)
        }
    }

    fun reportContact(contact: ContactSummary, reason: String, details: String) {
        val repo = adminRepository ?: return
        val me = uiState.value.currentUserAccount
        viewModelScope.launch {
            val result = repo.submitReport(
                reporterName = me.displayName.ifBlank { "RM User" },
                targetUid = contact.uid,
                targetName = contact.displayName,
                targetType = "user",
                targetId = contact.uid,
                reason = reason,
                details = details,
            )
            transientState.update {
                it.copy(
                    selectedContact = null,
                    statusBanner = result.fold(
                        onSuccess = { "Report submitted for moderator review." },
                        onFailure = { err -> err.localizedMessage ?: "Failed to submit report." },
                    ),
                )
            }
        }
    }

    fun clearStatusBanner() {
        transientState.update { it.copy(statusBanner = null) }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository,
        private val currentUserId: String,
        private val callRepository: CallRepository? = null,
        private val adminRepository: AdminRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                userRepository = userRepository,
                chatRepository = chatRepository,
                currentUserId = currentUserId,
                callRepository = callRepository,
                adminRepository = adminRepository,
            ) as T
        }
    }
}

private fun FirestoreUserDocument?.toUserAccount(fallbackUid: String): UserAccount {
    if (this == null) {
        return UserAccount(
            uid = fallbackUid,
            displayName = "RM User",
            username = "",
            isOnline = true,
            isAuthenticated = true,
        )
    }
    return UserAccount(
        uid = uid.ifBlank { fallbackUid },
        displayName = displayName.ifBlank { "RM User" },
        username = username,
        phoneNumber = phoneNumber ?: "Not linked",
        email = email.orEmpty(),
        photoUri = photoUrl,
        about = about,
        createdAtLabel = "Cloud Firestore Synced",
        isOnline = onlineStatus == "online",
        lastSeenLabel = if (onlineStatus == "online") "Online now" else "Offline",
        isAuthenticated = true,
        role = role,
        accountStatus = accountStatus,
    )
}

private fun FirestoreContactDocument.toContactSummary(): ContactSummary {
    return ContactSummary(
        uid = contactUid,
        displayName = displayName,
        username = username,
        phoneNumber = "",
        about = about,
        isOnline = false,
        isBlocked = isBlocked,
    )
}

private fun FirestoreConversationDocument.toConversationSummary(currentUserId: String): ConversationSummary {
    val isGroup = type == "group"
    val otherUid = if (!isGroup) memberUids.firstOrNull { it != currentUserId }.orEmpty() else ""
    val resolvedTitle = if (isGroup) {
        title.ifBlank { "Group Chat" }
    } else {
        participantNames[otherUid] ?: "Direct Chat"
    }
    val typingNames = typingUids
        .filter { it != currentUserId }
        .mapNotNull { uid -> participantNames[uid]?.split(" ")?.firstOrNull() }
    val typingLabel = if (typingNames.isNotEmpty()) {
        if (isGroup) "${typingNames.joinToString(", ")} typing…" else "typing…"
    } else {
        null
    }
    val preview = when {
        typingLabel != null -> typingLabel
        lastMessageText.isBlank() -> "Tap to start chatting"
        isGroup && lastMessageSenderName.isNotBlank() -> "${lastMessageSenderName.split(" ").first()}: $lastMessageText"
        else -> lastMessageText
    }
    val timeLabel = formatShortTime((lastMessageAt ?: updatedAt)?.toDate())
    return ConversationSummary(
        conversationId = id,
        title = resolvedTitle,
        lastMessagePreview = preview,
        lastMessageTime = timeLabel,
        unreadCount = (unreadCounts[currentUserId] ?: 0L).toInt(),
        isOnline = false,
        isPinned = pinnedBy.contains(currentUserId),
        isMuted = mutedBy.contains(currentUserId),
        deliveryState = if (lastMessageSenderId == currentUserId && lastMessageText.isNotBlank()) {
            MessageDeliveryState.DELIVERED
        } else {
            MessageDeliveryState.SENT
        },
        isGroup = isGroup,
        otherUid = otherUid,
        memberCount = memberUids.size,
        typingLabel = typingLabel,
    )
}

private fun FirestoreConversationDocument.toGroupSummary(currentUserId: String): GroupSummary {
    val preview = when {
        lastMessageText.isBlank() -> "No messages yet"
        lastMessageSenderName.isNotBlank() -> "${lastMessageSenderName.split(" ").first()}: $lastMessageText"
        else -> lastMessageText
    }
    return GroupSummary(
        groupId = id,
        groupName = title.ifBlank { "Group Chat" },
        memberCount = memberUids.size,
        lastMessagePreview = preview,
        updatedAtLabel = formatShortTime((lastMessageAt ?: updatedAt)?.toDate()),
        unreadCount = (unreadCounts[currentUserId] ?: 0L).toInt(),
        isMuted = mutedBy.contains(currentUserId),
        isPinned = pinnedBy.contains(currentUserId),
        memberUids = memberUids,
        adminUids = adminUids,
    )
}

private fun FirestoreCallSessionDocument.toCallRecord(currentUserId: String): CallRecord {
    val isOutgoing = callerId == currentUserId
    val peerUid = if (isOutgoing) calleeId else callerId
    val peerName = (if (isOutgoing) calleeName else callerName).ifBlank { "RM User" }
    val peerUsername = if (isOutgoing) calleeUsername else callerUsername
    val type = if (callType == "video") CallType.VIDEO else CallType.AUDIO
    val direction = when {
        (status == "missed" || status == "rejected") && !isOutgoing -> CallDirection.MISSED
        isOutgoing -> CallDirection.OUTGOING
        else -> CallDirection.INCOMING
    }
    val durationLabel = when (status) {
        "missed" -> "Missed"
        "rejected" -> if (isOutgoing) "Declined" else "Missed"
        "ringing" -> "Ringing"
        else -> if (durationSeconds > 0L) formatDurationLabel(durationSeconds) else "Connected"
    }
    return CallRecord(
        callId = id,
        participantName = peerName,
        callType = type,
        direction = direction,
        timestampLabel = formatShortTime((endedAt ?: createdAt)?.toDate()),
        durationLabel = durationLabel,
        peerUid = peerUid,
        peerUsername = peerUsername,
    )
}

private fun formatDurationLabel(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
}

private fun formatShortTime(date: Date?): String {
    if (date == null) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}
