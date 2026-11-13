package com.example.features.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.firebase.ChatRepository
import com.example.core.firebase.FirestoreConversationDocument
import com.example.core.firebase.FirestoreMessageDocument
import com.example.core.firebase.UserRepository
import com.example.core.media.MediaAttachmentHelper
import com.example.core.media.PlaybackState
import com.example.core.media.PreparedAttachment
import com.example.core.media.RecordingState
import com.example.core.media.VoicePlayerManager
import com.example.core.media.VoiceRecorderManager
import com.example.core.model.ChatMessage
import com.example.core.model.ContactSummary
import com.example.core.model.ConversationSummary
import com.example.core.model.MessageDeliveryState
import com.example.core.model.MessageType
import com.example.core.model.PrivacyPreferences
import com.example.core.model.UserAccount
import com.example.core.preferences.AppPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: String = "",
    val conversationTitle: String = "Conversation",
    val conversationSubtitle: String = "",
    val isGroup: Boolean = false,
    val isAdmin: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isPeerBlocked: Boolean = false,
    val peerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantUsernames: Map<String, String> = emptyMap(),
    val groupDescription: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val firstUnreadMessageId: String? = null,
    val composerText: String = "",
    val replyingTo: ChatMessage? = null,
    val selectedMessage: ChatMessage? = null,
    val forwardingMessage: ChatMessage? = null,
    val isEmojiPickerVisible: Boolean = false,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isGroupInfoOpen: Boolean = false,
    val isSending: Boolean = false,
    val isAttachmentSheetOpen: Boolean = false,
    val isProcessingAttachment: Boolean = false,
    val recordingState: RecordingState = RecordingState(),
    val playbackState: PlaybackState = PlaybackState(),
    val previewImageMessage: ChatMessage? = null,
    val previewVideoMessage: ChatMessage? = null,
    val forwardTargets: List<ConversationSummary> = emptyList(),
    val contactsToAdd: List<ContactSummary> = emptyList(),
    val statusBanner: String? = null,
    val errorMessage: String? = null,
)

private data class LocalChatTransientState(
    val composerText: String = "",
    val replyingTo: ChatMessage? = null,
    val selectedMessage: ChatMessage? = null,
    val forwardingMessage: ChatMessage? = null,
    val isEmojiPickerVisible: Boolean = false,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val isGroupInfoOpen: Boolean = false,
    val isSending: Boolean = false,
    val isAttachmentSheetOpen: Boolean = false,
    val isProcessingAttachment: Boolean = false,
    val previewImageMessage: ChatMessage? = null,
    val previewVideoMessage: ChatMessage? = null,
    val firstUnreadMessageId: String? = null,
    val unreadCaptured: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
)

class ChatViewModel(
    private val conversationId: String,
    private val currentUserId: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val appContext: Context,
) : ViewModel() {

    private val voiceRecorder = VoiceRecorderManager(appContext.applicationContext, viewModelScope)
    private val voicePlayer = VoicePlayerManager(appContext.applicationContext, viewModelScope)

    private val localState = MutableStateFlow(LocalChatTransientState())
    private val streamError = MutableStateFlow<String?>(null)
    private var typingTimeoutJob: Job? = null
    private var currentlyTypingSent = false
    private var latestConversation: FirestoreConversationDocument? = null
    private var latestAccount: UserAccount = UserAccount(uid = currentUserId, displayName = "RM User")

    private val conversationFlow = chatRepository.observeConversation(conversationId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            streamError.value = (fsErr ?: error).localizedMessage ?: "Failed to load conversation."
            emit(null)
        }

    private val messagesFlow = chatRepository.observeMessages(conversationId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            streamError.value = (fsErr ?: error).localizedMessage ?: "Failed to load messages."
            emit(emptyList())
        }

    private val allConversationsFlow = chatRepository.observeConversations()
        .catch { emit(emptyList()) }

    private val contactsFlow = userRepository.observeContacts()
        .catch { emit(emptyList()) }

    private val currentUserFlow = userRepository.observeCurrentUser()
        .catch { emit(null) }

    val uiState: StateFlow<ChatUiState> = combine(
        localState,
        conversationFlow,
        messagesFlow,
        allConversationsFlow,
        contactsFlow,
        currentUserFlow,
        preferencesRepository.privacyPreferences,
        streamError,
        voiceRecorder.state,
        voicePlayer.state,
    ) { values ->
        val local = values[0] as LocalChatTransientState
        val conv = values[1] as FirestoreConversationDocument?
        val rawMessages = values[2] as List<FirestoreMessageDocument>
        val allConvs = values[3] as List<FirestoreConversationDocument>
        val contacts = values[4] as List<com.example.core.firebase.FirestoreContactDocument>
        val userDoc = values[5] as com.example.core.firebase.FirestoreUserDocument?
        val privacy = values[6] as PrivacyPreferences
        val err = values[7] as String?
        val recState = values[8] as RecordingState
        val playState = values[9] as PlaybackState

        latestConversation = conv
        if (userDoc != null) {
            latestAccount = UserAccount(
                uid = userDoc.uid.ifBlank { currentUserId },
                displayName = userDoc.displayName.ifBlank { "RM User" },
                username = userDoc.username,
            )
        }

        val isGroup = conv?.type == "group"
        val memberUids = conv?.memberUids ?: emptyList()
        val peerUid = if (!isGroup) memberUids.firstOrNull { it != currentUserId }.orEmpty() else ""

        val title = when {
            conv == null -> "Conversation"
            isGroup -> conv.title.ifBlank { "Group Chat" }
            else -> conv.participantNames[peerUid]
                ?: contacts.firstOrNull { it.contactUid == peerUid }?.displayName
                ?: "RM User"
        }

        val otherTypingNames = conv?.typingUids
            ?.filter { it != currentUserId }
            ?.mapNotNull { uid -> conv.participantNames[uid]?.split(" ")?.firstOrNull() }
            ?: emptyList()

        val subtitle = when {
            otherTypingNames.isNotEmpty() -> {
                if (isGroup) "${otherTypingNames.joinToString(", ")} typing…" else "typing…"
            }
            isGroup -> "${memberUids.size} members"
            peerUid.isNotBlank() -> {
                val handle = conv?.participantUsernames?.get(peerUid)
                if (!handle.isNullOrBlank()) "@$handle" else "Private Chat"
            }
            else -> ""
        }

        val isPeerBlocked = !isGroup && peerUid.isNotBlank() &&
            contacts.any { it.contactUid == peerUid && it.isBlocked }

        // Capture first unread incoming message once when messages arrive
        if (!local.unreadCaptured && rawMessages.isNotEmpty()) {
            val firstUnread = rawMessages.firstOrNull {
                it.senderId != currentUserId && !it.readByUids.contains(currentUserId)
            }
            localState.update {
                it.copy(
                    unreadCaptured = true,
                    firstUnreadMessageId = firstUnread?.id,
                )
            }
        }

        // Mark incoming unread/undelivered messages as delivered/read automatically
        val needsAck = rawMessages.any { msg ->
            msg.senderId != currentUserId &&
                (!msg.deliveredToUids.contains(currentUserId) ||
                    (privacy.readReceiptsEnabled && !msg.readByUids.contains(currentUserId)))
        }
        val unreadCountForMe = conv?.unreadCounts?.get(currentUserId) ?: 0L
        if (conv != null && (needsAck || unreadCountForMe > 0L)) {
            viewModelScope.launch {
                chatRepository.markConversationAsRead(
                    conversationId = conversationId,
                    unreadMessages = rawMessages,
                    sendReadReceipt = privacy.readReceiptsEnabled,
                )
            }
        }

        val mappedMessages = rawMessages.map { doc ->
            val otherMembers = memberUids.filter { it != currentUserId }
            val isReadByAny = otherMembers.isNotEmpty() && otherMembers.any { doc.readByUids.contains(it) }
            val isDeliveredToAny = otherMembers.isNotEmpty() && otherMembers.any { doc.deliveredToUids.contains(it) }
            val deliveryState = when {
                isReadByAny -> MessageDeliveryState.READ
                isDeliveredToAny -> MessageDeliveryState.DELIVERED
                else -> MessageDeliveryState.SENT
            }
            val date = doc.createdAt?.toDate()
            ChatMessage(
                id = doc.id,
                senderId = doc.senderId,
                senderName = doc.senderName.ifBlank {
                    conv?.participantNames?.get(doc.senderId) ?: "RM User"
                },
                text = doc.text,
                messageType = MessageType.fromWire(doc.messageType),
                fileName = doc.fileName,
                mimeType = doc.mimeType,
                fileSizeBytes = doc.fileSizeBytes ?: 0L,
                durationSeconds = (doc.durationSeconds ?: 0L).toInt(),
                waveform = doc.waveform?.map { it.toInt() } ?: emptyList(),
                mediaData = doc.mediaData,
                thumbnailData = doc.thumbnailData,
                isFromMe = doc.senderId == currentUserId,
                timestampLabel = formatTime(date),
                createdAtMillis = date?.time ?: 0L,
                deliveryState = deliveryState,
                reactions = doc.reactions,
                replyToMessageId = doc.replyToMessageId,
                replyToSenderName = doc.replyToSenderName,
                replyToPreview = doc.replyToPreview,
                isForwarded = doc.forwardedFrom,
                isDeletedForEveryone = doc.isDeletedForEveryone,
                isReadByMe = doc.readByUids.contains(currentUserId),
            )
        }

        val filteredMessages = if (local.isSearchVisible && local.searchQuery.isNotBlank()) {
            mappedMessages.filter {
                it.text.contains(local.searchQuery, ignoreCase = true) ||
                    it.senderName.contains(local.searchQuery, ignoreCase = true) ||
                    (it.fileName?.contains(local.searchQuery, ignoreCase = true) == true)
            }
        } else {
            mappedMessages
        }

        val forwardTargets = allConvs
            .filter { it.id != conversationId }
            .map { c ->
                val targetIsGroup = c.type == "group"
                val other = if (!targetIsGroup) c.memberUids.firstOrNull { it != currentUserId }.orEmpty() else ""
                val cTitle = if (targetIsGroup) {
                    c.title.ifBlank { "Group" }
                } else {
                    c.participantNames[other] ?: "Direct Chat"
                }
                ConversationSummary(
                    conversationId = c.id,
                    title = cTitle,
                    lastMessagePreview = c.lastMessageText,
                    lastMessageTime = formatTime((c.lastMessageAt ?: c.updatedAt)?.toDate()),
                    isGroup = targetIsGroup,
                    otherUid = other,
                    memberCount = c.memberUids.size,
                )
            }

        val contactsToAdd = contacts
            .filter { !it.isBlocked && !memberUids.contains(it.contactUid) }
            .map {
                ContactSummary(
                    uid = it.contactUid,
                    displayName = it.displayName,
                    username = it.username,
                    phoneNumber = "",
                    about = it.about,
                    isOnline = false,
                    isBlocked = false,
                )
            }

        ChatUiState(
            conversationId = conversationId,
            conversationTitle = title,
            conversationSubtitle = subtitle,
            isGroup = isGroup,
            isAdmin = conv?.adminUids?.contains(currentUserId) == true,
            isPinned = conv?.pinnedBy?.contains(currentUserId) == true,
            isMuted = conv?.mutedBy?.contains(currentUserId) == true,
            isPeerBlocked = isPeerBlocked,
            peerUid = peerUid,
            memberUids = memberUids,
            adminUids = conv?.adminUids ?: emptyList(),
            participantNames = conv?.participantNames ?: emptyMap(),
            participantUsernames = conv?.participantUsernames ?: emptyMap(),
            groupDescription = conv?.description.orEmpty(),
            messages = filteredMessages,
            firstUnreadMessageId = local.firstUnreadMessageId,
            composerText = local.composerText,
            replyingTo = local.replyingTo,
            selectedMessage = local.selectedMessage,
            forwardingMessage = local.forwardingMessage,
            isEmojiPickerVisible = local.isEmojiPickerVisible,
            isSearchVisible = local.isSearchVisible,
            searchQuery = local.searchQuery,
            isGroupInfoOpen = local.isGroupInfoOpen,
            isSending = local.isSending,
            isAttachmentSheetOpen = local.isAttachmentSheetOpen,
            isProcessingAttachment = local.isProcessingAttachment,
            recordingState = recState,
            playbackState = playState,
            previewImageMessage = local.previewImageMessage,
            previewVideoMessage = local.previewVideoMessage,
            forwardTargets = forwardTargets,
            contactsToAdd = contactsToAdd,
            statusBanner = local.statusBanner,
            errorMessage = local.errorMessage ?: err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatUiState(conversationId = conversationId),
    )

    fun updateComposerText(value: String) {
        localState.update { it.copy(composerText = value, errorMessage = null) }
        if (value.isNotBlank()) {
            if (!currentlyTypingSent) {
                currentlyTypingSent = true
                viewModelScope.launch { chatRepository.setTyping(conversationId, true) }
            }
            typingTimeoutJob?.cancel()
            typingTimeoutJob = viewModelScope.launch {
                delay(3000L)
                currentlyTypingSent = false
                chatRepository.setTyping(conversationId, false)
            }
        } else if (currentlyTypingSent) {
            typingTimeoutJob?.cancel()
            currentlyTypingSent = false
            viewModelScope.launch { chatRepository.setTyping(conversationId, false) }
        }
    }

    fun appendEmojiToComposer(emoji: String) {
        localState.update { it.copy(composerText = it.composerText + emoji) }
    }

    fun toggleEmojiPicker() {
        localState.update { it.copy(isEmojiPickerVisible = !it.isEmojiPickerVisible) }
    }

    fun sendMessage() {
        val conv = latestConversation ?: return
        val local = localState.value
        val text = local.composerText.trim()
        if (text.isEmpty() || local.isSending) return

        typingTimeoutJob?.cancel()
        currentlyTypingSent = false
        val replyTarget = local.replyingTo

        localState.update {
            it.copy(
                composerText = "",
                replyingTo = null,
                isEmojiPickerVisible = false,
                isSending = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            val result = chatRepository.sendMessage(
                conversation = conv,
                senderName = latestAccount.displayName.ifBlank { "RM User" },
                rawText = text,
                replyTo = replyTarget,
                isForwarded = false,
            )
            localState.update { state ->
                state.copy(
                    isSending = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun openMessageActions(message: ChatMessage) {
        localState.update { it.copy(selectedMessage = message) }
    }

    fun dismissMessageActions() {
        localState.update { it.copy(selectedMessage = null) }
    }

    fun startReply(message: ChatMessage) {
        localState.update {
            it.copy(
                replyingTo = message,
                selectedMessage = null,
            )
        }
    }

    fun cancelReply() {
        localState.update { it.copy(replyingTo = null) }
    }

    fun startForward(message: ChatMessage) {
        localState.update {
            it.copy(
                forwardingMessage = message,
                selectedMessage = null,
            )
        }
    }

    fun dismissForwardSheet() {
        localState.update { it.copy(forwardingMessage = null) }
    }

    fun forwardMessageTo(targetConversationId: String) {
        val msg = localState.value.forwardingMessage ?: return
        localState.update { it.copy(forwardingMessage = null) }
        viewModelScope.launch {
            val targetDoc = chatRepository.observeConversation(targetConversationId).firstOrNull()
                ?: return@launch
            val senderName = latestAccount.displayName.ifBlank { "RM User" }
            val res = if (msg.messageType != MessageType.TEXT && !msg.mediaData.isNullOrBlank()) {
                val attachment = PreparedAttachment(
                    messageType = msg.messageType,
                    fileName = msg.fileName ?: "attachment",
                    mimeType = msg.mimeType ?: "application/octet-stream",
                    fileSizeBytes = msg.fileSizeBytes,
                    durationSeconds = msg.durationSeconds,
                    waveform = msg.waveform,
                    base64Data = msg.mediaData,
                    thumbnailBase64 = msg.thumbnailData,
                )
                chatRepository.sendMediaMessage(
                    conversation = targetDoc,
                    senderName = senderName,
                    attachment = attachment,
                    caption = "",
                    replyTo = null,
                    isForwarded = true,
                )
            } else {
                chatRepository.sendMessage(
                    conversation = targetDoc,
                    senderName = senderName,
                    rawText = msg.text,
                    replyTo = null,
                    isForwarded = true,
                )
            }
            localState.update {
                it.copy(
                    statusBanner = if (res.isSuccess) "Message forwarded" else null,
                    errorMessage = res.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun openAttachmentSheet() {
        localState.update { it.copy(isAttachmentSheetOpen = true, isEmojiPickerVisible = false) }
    }

    fun closeAttachmentSheet() {
        localState.update { it.copy(isAttachmentSheetOpen = false) }
    }

    fun openImagePreview(message: ChatMessage) {
        localState.update { it.copy(previewImageMessage = message) }
    }

    fun closeImagePreview() {
        localState.update { it.copy(previewImageMessage = null) }
    }

    fun openVideoPreview(message: ChatMessage) {
        voicePlayer.release()
        localState.update { it.copy(previewVideoMessage = message) }
    }

    fun closeVideoPreview() {
        localState.update { it.copy(previewVideoMessage = null) }
    }

    fun onVoicePermissionDenied() {
        localState.update {
            it.copy(statusBanner = "Microphone permission is needed to record voice messages.")
        }
    }

    fun startVoiceRecording() {
        if (latestConversation == null) return
        voicePlayer.release()
        localState.update { it.copy(isEmojiPickerVisible = false, isAttachmentSheetOpen = false) }
        voiceRecorder.startRecording().onFailure { err ->
            localState.update {
                it.copy(errorMessage = err.localizedMessage ?: "Unable to start microphone recording.")
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    fun stopAndSendVoiceRecording() {
        val conv = latestConversation ?: run {
            voiceRecorder.cancelRecording()
            return
        }
        val recordedResult = voiceRecorder.stopAndReadRecording()
        val recorded = recordedResult.getOrElse { err ->
            localState.update {
                it.copy(errorMessage = err.localizedMessage ?: "Could not save voice recording.")
            }
            return
        }
        val replyTarget = localState.value.replyingTo
        localState.update { it.copy(isSending = true, replyingTo = null, errorMessage = null) }
        viewModelScope.launch {
            val attachment = PreparedAttachment(
                messageType = MessageType.VOICE,
                fileName = "voice_${System.currentTimeMillis()}.m4a",
                mimeType = "audio/mp4",
                fileSizeBytes = recorded.fileSizeBytes,
                durationSeconds = recorded.durationSeconds,
                waveform = recorded.waveform,
                base64Data = recorded.base64Data,
            )
            val sendRes = chatRepository.sendMediaMessage(
                conversation = conv,
                senderName = latestAccount.displayName.ifBlank { "RM User" },
                attachment = attachment,
                replyTo = replyTarget,
            )
            localState.update {
                it.copy(
                    isSending = false,
                    errorMessage = sendRes.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun toggleAudioPlayback(message: ChatMessage) {
        val media = message.mediaData ?: return
        val ext = if (message.messageType == MessageType.AUDIO &&
            message.mimeType?.contains("mpeg", ignoreCase = true) == true
        ) {
            "mp3"
        } else {
            "m4a"
        }
        voicePlayer.togglePlay(
            messageId = message.id,
            base64Data = media,
            fallbackDurationSeconds = message.durationSeconds,
            fileExtension = ext,
        ).onFailure { err ->
            localState.update {
                it.copy(errorMessage = err.localizedMessage ?: "Unable to play audio.")
            }
        }
    }

    fun sendPickedAttachment(uri: Uri, messageType: MessageType) {
        val conv = latestConversation ?: return
        val replyTarget = localState.value.replyingTo
        localState.update {
            it.copy(
                isAttachmentSheetOpen = false,
                isProcessingAttachment = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val preparedRes = when (messageType) {
                MessageType.IMAGE -> MediaAttachmentHelper.prepareImageAttachment(appContext, uri)
                MessageType.VIDEO -> MediaAttachmentHelper.prepareVideoAttachment(appContext, uri)
                MessageType.AUDIO, MessageType.DOCUMENT ->
                    MediaAttachmentHelper.prepareFileAttachment(appContext, uri, messageType)
                MessageType.VOICE, MessageType.TEXT ->
                    Result.failure(IllegalArgumentException("Unsupported attachment type."))
            }
            val attachment = preparedRes.getOrElse { err ->
                localState.update {
                    it.copy(
                        isProcessingAttachment = false,
                        errorMessage = err.localizedMessage ?: "Failed to prepare attachment.",
                    )
                }
                return@launch
            }
            val sendRes = chatRepository.sendMediaMessage(
                conversation = conv,
                senderName = latestAccount.displayName.ifBlank { "RM User" },
                attachment = attachment,
                replyTo = replyTarget,
            )
            localState.update {
                it.copy(
                    isProcessingAttachment = false,
                    replyingTo = if (sendRes.isSuccess) null else it.replyingTo,
                    errorMessage = sendRes.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun openDocumentMessage(activityContext: Context, message: ChatMessage) {
        val media = message.mediaData ?: return
        MediaAttachmentHelper.openAttachmentWithSystemViewer(
            context = activityContext,
            fileName = message.fileName ?: "document.bin",
            mimeType = message.mimeType ?: "*/*",
            base64Data = media,
        ).onFailure {
            localState.update {
                it.copy(statusBanner = "No viewer app found for this file. Tap Save to download.")
            }
        }
    }

    fun saveMessageMediaToUri(targetUri: Uri, message: ChatMessage) {
        val media = message.mediaData ?: return
        viewModelScope.launch {
            val res = MediaAttachmentHelper.saveAttachmentToUri(appContext, targetUri, media)
            localState.update {
                it.copy(
                    statusBanner = if (res.isSuccess) "Saved to device" else null,
                    errorMessage = res.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        localState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            chatRepository.toggleReaction(
                conversationId = conversationId,
                messageId = message.id,
                emoji = emoji,
                currentReactions = message.reactions,
            )
        }
    }

    fun deleteMessageForMe(message: ChatMessage) {
        localState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            chatRepository.deleteMessageForMe(conversationId, message.id)
        }
    }

    fun deleteMessageForEveryone(message: ChatMessage) {
        localState.update { it.copy(selectedMessage = null) }
        viewModelScope.launch {
            val res = chatRepository.deleteMessageForEveryone(conversationId, message.id)
            res.onFailure { err ->
                localState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun toggleSearch() {
        localState.update {
            val next = !it.isSearchVisible
            it.copy(
                isSearchVisible = next,
                searchQuery = if (next) it.searchQuery else "",
            )
        }
    }

    fun updateSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun togglePin() {
        val conv = latestConversation ?: return
        val pinned = conv.pinnedBy.contains(currentUserId)
        viewModelScope.launch {
            chatRepository.togglePinConversation(conversationId, pinned)
        }
    }

    fun toggleMute() {
        val conv = latestConversation ?: return
        val muted = conv.mutedBy.contains(currentUserId)
        viewModelScope.launch {
            chatRepository.toggleMuteConversation(conversationId, muted)
        }
    }

    fun unblockPeer() {
        val peerUid = uiState.value.peerUid
        if (peerUid.isBlank()) return
        viewModelScope.launch {
            userRepository.toggleBlockContact(peerUid, blocked = false)
            localState.update { it.copy(statusBanner = "Contact unblocked") }
        }
    }

    fun openGroupInfo() {
        localState.update { it.copy(isGroupInfoOpen = true) }
    }

    fun closeGroupInfo() {
        localState.update { it.copy(isGroupInfoOpen = false) }
    }

    fun updateGroupDetails(newTitle: String, newDescription: String) {
        viewModelScope.launch {
            val res = chatRepository.updateGroupInfo(conversationId, newTitle, newDescription)
            localState.update {
                it.copy(
                    statusBanner = if (res.isSuccess) "Group updated" else null,
                    errorMessage = res.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun addMemberToGroup(contact: ContactSummary) {
        viewModelScope.launch {
            val res = chatRepository.addGroupMember(conversationId, contact)
            localState.update {
                it.copy(
                    statusBanner = if (res.isSuccess) "${contact.displayName} added to group" else null,
                    errorMessage = res.exceptionOrNull()?.localizedMessage,
                )
            }
        }
    }

    fun removeMemberFromGroup(targetUid: String) {
        viewModelScope.launch {
            val res = chatRepository.removeGroupMember(conversationId, targetUid)
            localState.update {
                it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun toggleMemberAdmin(targetUid: String, makeAdmin: Boolean) {
        viewModelScope.launch {
            val res = chatRepository.toggleGroupAdmin(conversationId, targetUid, makeAdmin)
            localState.update {
                it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val conv = latestConversation ?: return
        viewModelScope.launch {
            val res = chatRepository.leaveGroup(conv)
            if (res.isSuccess) {
                localState.update { it.copy(isGroupInfoOpen = false) }
                onLeft()
            } else {
                localState.update { it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage) }
            }
        }
    }

    fun clearStatusBanner() {
        localState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    override fun onCleared() {
        typingTimeoutJob?.cancel()
        voiceRecorder.cancelRecording()
        voicePlayer.release()
        if (currentlyTypingSent) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                chatRepository.setTyping(conversationId, false)
            }
        }
        super.onCleared()
    }

    class Factory(
        private val conversationId: String,
        private val currentUserId: String,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val preferencesRepository: AppPreferencesRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                conversationId = conversationId,
                currentUserId = currentUserId,
                chatRepository = chatRepository,
                userRepository = userRepository,
                preferencesRepository = preferencesRepository,
                appContext = appContext,
            ) as T
        }
    }
}

private fun formatTime(date: Date?): String {
    if (date == null) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}
