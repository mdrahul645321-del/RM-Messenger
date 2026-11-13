package com.example.features.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.media.PlaybackState
import com.example.core.model.ChatMessage
import com.example.core.model.MessageDeliveryState
import com.example.core.model.MessageType
import com.example.ui.components.AvatarCircle
import com.example.ui.theme.SkyBlueBright

private val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "🙏", "👍")
private val QUICK_EMOJIS = listOf("😀", "😂", "🥰", "😎", "🤝", "🙏", "👍", "❤️", "🔥", "🎉", "✅", "📞")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onStartAudioCall: (peerUid: String, peerName: String, peerUsername: String) -> Unit = { _, _, _ -> },
    onStartVideoCall: (peerUid: String, peerName: String, peerUsername: String) -> Unit = { _, _, _ -> },
    onReportChat: (targetUid: String, targetName: String, targetType: String, targetId: String, reason: String, details: String) -> Unit = { _, _, _, _, _, _ -> },
    callingEnabled: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Spam or Harassment") }
    var reportDetails by remember { mutableStateOf("") }
    var pendingSaveMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.sendPickedAttachment(uri, MessageType.IMAGE)
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.sendPickedAttachment(uri, MessageType.VIDEO)
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.sendPickedAttachment(uri, MessageType.AUDIO)
    }

    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.sendPickedAttachment(uri, MessageType.DOCUMENT)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            viewModel.onVoicePermissionDenied()
        }
    }

    val saveMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { targetUri ->
        val msg = pendingSaveMessage
        pendingSaveMessage = null
        if (targetUri != null && msg != null) {
            viewModel.saveMessageMediaToUri(targetUri, msg)
        }
    }

    val requestSaveMessage: (ChatMessage) -> Unit = { msg ->
        pendingSaveMessage = msg
        saveMediaLauncher.launch(msg.fileName ?: "rm_attachment")
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && !state.isSearchVisible) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.statusBanner, state.errorMessage) {
        val text = state.errorMessage ?: state.statusBanner
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearStatusBanner()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("chat_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = state.isGroup) { viewModel.openGroupInfo() },
                        ) {
                            if (state.isGroup) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Groups,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            } else {
                                AvatarCircle(
                                    initials = state.conversationTitle.take(2),
                                    size = 38.dp,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.conversationTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (state.conversationSubtitle.isNotBlank()) {
                                    Text(
                                        text = state.conversationSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (state.conversationSubtitle.contains("typing")) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (callingEnabled && !state.isGroup && state.peerUid.isNotBlank() && !state.isPeerBlocked) {
                            val peerUsername = state.participantUsernames[state.peerUid].orEmpty()
                            IconButton(
                                onClick = {
                                    onStartAudioCall(state.peerUid, state.conversationTitle, peerUsername)
                                },
                                modifier = Modifier.testTag("chat_audio_call_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Audio call",
                                )
                            }
                            IconButton(
                                onClick = {
                                    onStartVideoCall(state.peerUid, state.conversationTitle, peerUsername)
                                },
                                modifier = Modifier.testTag("chat_video_call_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = "Video call",
                                )
                            }
                        }
                        IconButton(
                            onClick = viewModel::toggleSearch,
                            modifier = Modifier.testTag("chat_search_button"),
                        ) {
                            Icon(
                                imageVector = if (state.isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search messages",
                            )
                        }
                        if (state.isGroup) {
                            IconButton(
                                onClick = viewModel::openGroupInfo,
                                modifier = Modifier.testTag("chat_group_info_button"),
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = "Group info")
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("chat_more_menu"),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (state.isPinned) "Unpin chat" else "Pin chat") },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.togglePin()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.isMuted) "Unmute notifications" else "Mute notifications") },
                                    leadingIcon = {
                                        Icon(
                                            if (state.isMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.toggleMute()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Report ${if (state.isGroup) "group" else "user"}") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showReportDialog = true
                                    },
                                )
                            }
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
                        placeholder = { Text("Search messages in this chat…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("chat_search_input"),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding(),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (state.isPeerBlocked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = "You blocked this contact",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            TextButton(onClick = viewModel::unblockPeer) {
                                Text("Unblock")
                            }
                        }
                    }
                } else {
                    AnimatedVisibility(visible = state.replyingTo != null) {
                        val reply = state.replyingTo
                        if (reply != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Replying to ${reply.senderName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = reply.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = viewModel::cancelReply) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Cancel reply")
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.isEmojiPickerVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            QUICK_EMOJIS.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.appendEmojiToComposer(emoji) }
                                        .padding(6.dp),
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.isProcessingAttachment) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (state.recordingState.isRecording) {
                        VoiceRecordingBar(
                            recordingState = state.recordingState,
                            onCancel = viewModel::cancelVoiceRecording,
                            onSend = viewModel::stopAndSendVoiceRecording,
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = viewModel::toggleEmojiPicker,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_emoji_toggle"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEmotions,
                                    contentDescription = "Emojis",
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            IconButton(
                                onClick = viewModel::openAttachmentSheet,
                                enabled = !state.isProcessingAttachment && !state.isSending,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_attach_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachFile,
                                    contentDescription = "Attach media or file",
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            OutlinedTextField(
                                value = state.composerText,
                                onValueChange = viewModel::updateComposerText,
                                placeholder = { Text("Message…") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input"),
                            )
                            Spacer(Modifier.width(4.dp))
                            if (state.composerText.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !state.isSending) {
                                            viewModel.sendMessage()
                                        }
                                        .testTag("chat_send_button"),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Send message",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !state.isSending && !state.isProcessingAttachment) {
                                            val hasMic = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO,
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                viewModel.startVoiceRecording()
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        .testTag("chat_mic_button"),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Record voice message",
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (state.isSearchVisible && state.searchQuery.isNotBlank()) {
                                    "No matching messages"
                                } else {
                                    "Start the conversation"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (state.isGroup) {
                                    "Messages sent here sync in real time with all group members."
                                } else {
                                    "Say hello! Messages are synced in real time via Cloud Firestore."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        if (msg.id == state.firstUnreadMessageId) {
                            UnreadSeparatorPill()
                        }
                        MessageBubbleRow(
                            message = msg,
                            isGroup = state.isGroup,
                            playbackState = state.playbackState,
                            onAction = { viewModel.openMessageActions(msg) },
                            onReactionTap = { emoji -> viewModel.toggleReaction(msg, emoji) },
                            onTogglePlayAudio = { viewModel.toggleAudioPlayback(msg) },
                            onOpenImage = { viewModel.openImagePreview(msg) },
                            onOpenVideo = { viewModel.openVideoPreview(msg) },
                            onOpenDocument = { viewModel.openDocumentMessage(context, msg) },
                            onSaveMedia = { requestSaveMessage(msg) },
                        )
                    }
                }
            }
        }
    }

    val selectedMsg = state.selectedMessage
    if (selectedMsg != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissMessageActions,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MessageActionsSheetContent(
                message = selectedMsg,
                canDeleteForEveryone = (selectedMsg.isFromMe || state.isAdmin) && !selectedMsg.isDeletedForEveryone,
                onReact = { emoji -> viewModel.toggleReaction(selectedMsg, emoji) },
                onReply = { viewModel.startReply(selectedMsg) },
                onForward = { viewModel.startForward(selectedMsg) },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(selectedMsg.text))
                    viewModel.dismissMessageActions()
                },
                onDeleteForMe = { viewModel.deleteMessageForMe(selectedMsg) },
                onDeleteForEveryone = { viewModel.deleteMessageForEveryone(selectedMsg) },
            )
        }
    }

    val forwardMsg = state.forwardingMessage
    if (forwardMsg != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissForwardSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Forward Message To", style = MaterialTheme.typography.titleLarge)
                if (state.forwardTargets.isEmpty()) {
                    Text(
                        text = "No other chats or groups available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.forwardTargets.forEach { target ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.forwardMessageTo(target.conversationId) },
                        ) {
                            ListItem(
                                headlineContent = { Text(target.title) },
                                supportingContent = {
                                    Text(if (target.isGroup) "${target.memberCount} members" else "Direct chat")
                                },
                                leadingContent = {
                                    AvatarCircle(initials = target.title.take(2), size = 40.dp)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isGroupInfoOpen && state.isGroup) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeGroupInfo,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            GroupInfoSheetContent(
                state = state,
                currentUserId = currentUserId,
                onSaveGroupDetails = viewModel::updateGroupDetails,
                onToggleMute = viewModel::toggleMute,
                onTogglePin = viewModel::togglePin,
                onAddMember = viewModel::addMemberToGroup,
                onRemoveMember = viewModel::removeMemberFromGroup,
                onToggleAdmin = viewModel::toggleMemberAdmin,
                onLeaveGroup = { viewModel.leaveGroup(onNavigateBack) },
                onClose = viewModel::closeGroupInfo,
            )
        }
    }

    if (state.isAttachmentSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeAttachmentSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AttachmentPickerSheetContent(
                onPickImage = {
                    viewModel.closeAttachmentSheet()
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPickVideo = {
                    viewModel.closeAttachmentSheet()
                    pickVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                onPickAudio = {
                    viewModel.closeAttachmentSheet()
                    pickAudioLauncher.launch(arrayOf("audio/*"))
                },
                onPickDocument = {
                    viewModel.closeAttachmentSheet()
                    pickDocumentLauncher.launch(arrayOf("application/pdf", "text/plain", "*/*"))
                },
            )
        }
    }

    val previewImage = state.previewImageMessage
    if (previewImage != null) {
        ImagePreviewDialog(
            message = previewImage,
            onDismiss = viewModel::closeImagePreview,
            onSave = { requestSaveMessage(previewImage) },
        )
    }

    val previewVideo = state.previewVideoMessage
    if (previewVideo != null) {
        VideoPreviewDialog(
            message = previewVideo,
            onDismiss = viewModel::closeVideoPreview,
            onSave = { requestSaveMessage(previewVideo) },
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${if (state.isGroup) "Group" else "User"}") },
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
                        label = { Text("Details (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetType = if (state.isGroup) "group" else "user"
                        val targetUid = if (state.isGroup) state.conversationId else state.peerUid.ifBlank { state.conversationId }
                        val targetId = state.conversationId
                        onReportChat(
                            targetUid,
                            state.conversationTitle,
                            targetType,
                            targetId,
                            reportReason.ifBlank { "Abuse / Spam" },
                            reportDetails,
                        )
                        reportDetails = ""
                        showReportDialog = false
                    },
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun UnreadSeparatorPill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "Unread messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleRow(
    message: ChatMessage,
    isGroup: Boolean,
    playbackState: PlaybackState,
    onAction: () -> Unit,
    onReactionTap: (String) -> Unit,
    onTogglePlayAudio: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenDocument: () -> Unit,
    onSaveMedia: () -> Unit,
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val bubbleShape = if (message.isFromMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val bubbleColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = if (message.isFromMe) 2.dp else 1.dp,
            modifier = Modifier
                .widthIn(min = 84.dp, max = 310.dp)
                .combinedClickable(
                    onClick = onAction,
                    onLongClick = onAction,
                )
                .testTag("message_bubble_${message.id}"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isGroup && !message.isFromMe) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (message.isForwarded && !message.isDeletedForEveryone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.72f),
                        )
                    }
                }

                if (!message.replyToPreview.isNullOrBlank() && !message.isDeletedForEveryone) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (message.isFromMe) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = message.replyToSenderName.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.isFromMe) SkyBlueBright else MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = message.replyToPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (message.isDeletedForEveryone) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.65f),
                    )
                } else {
                    when (message.messageType) {
                        MessageType.VOICE, MessageType.AUDIO -> {
                            VoiceAudioMessageContent(
                                message = message,
                                playbackState = playbackState,
                                textColor = textColor,
                                onTogglePlay = onTogglePlayAudio,
                                onSave = onSaveMedia,
                            )
                        }
                        MessageType.IMAGE -> {
                            ImageMessageContent(
                                message = message,
                                textColor = textColor,
                                onOpenPreview = onOpenImage,
                            )
                        }
                        MessageType.VIDEO -> {
                            VideoMessageContent(
                                message = message,
                                textColor = textColor,
                                onOpenVideo = onOpenVideo,
                                onSave = onSaveMedia,
                            )
                        }
                        MessageType.DOCUMENT -> {
                            DocumentMessageContent(
                                message = message,
                                textColor = textColor,
                                onOpenDocument = onOpenDocument,
                                onSaveDocument = onSaveMedia,
                            )
                        }
                        MessageType.TEXT -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = message.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.72f),
                    )
                    if (message.isFromMe && !message.isDeletedForEveryone) {
                        val (tickIcon, tickTint) = when (message.deliveryState) {
                            MessageDeliveryState.SENT -> Icons.Rounded.Check to textColor.copy(alpha = 0.72f)
                            MessageDeliveryState.DELIVERED -> Icons.Rounded.DoneAll to textColor.copy(alpha = 0.72f)
                            MessageDeliveryState.READ -> Icons.Rounded.DoneAll to SkyBlueBright
                        }
                        Icon(
                            imageVector = tickIcon,
                            contentDescription = message.deliveryState.name,
                            tint = tickTint,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            val grouped = message.reactions.values.groupingBy { it }.eachCount()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onReactionTap(emoji) },
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionsSheetContent(
    message: ChatMessage,
    canDeleteForEveryone: Boolean,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!message.isDeletedForEveryone) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onReact(emoji) }
                            .testTag("react_$emoji"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text("Reply") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onReply)
                    .testTag("action_reply"),
            )
            ListItem(
                headlineContent = { Text("Forward") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Shortcut, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onForward)
                    .testTag("action_forward"),
            )
            ListItem(
                headlineContent = { Text("Copy message") },
                leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onCopy)
                    .testTag("action_copy"),
            )
        }
        ListItem(
            headlineContent = { Text("Delete for me") },
            leadingContent = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            modifier = Modifier
                .clickable(onClick = onDeleteForMe)
                .testTag("action_delete_me"),
        )
        if (canDeleteForEveryone) {
            ListItem(
                headlineContent = {
                    Text("Delete for everyone", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier
                    .clickable(onClick = onDeleteForEveryone)
                    .testTag("action_delete_everyone"),
            )
        }
    }
}
