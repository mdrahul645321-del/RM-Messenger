package com.example.core.model

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    ARABIC("ar", "Arabic", "العربية"),
    BENGALI("bn", "Bengali", "বাংলা"),
}

enum class CountryCode(
    val isoCode: String,
    val countryName: String,
    val dialCode: String,
    val flagEmoji: String,
    val expectedDigits: Int,
    val sampleHint: String,
) {
    KUWAIT(
        isoCode = "KW",
        countryName = "Kuwait",
        dialCode = "+965",
        flagEmoji = "🇰🇼",
        expectedDigits = 8,
        sampleHint = "5001 2345",
    ),
    BANGLADESH(
        isoCode = "BD",
        countryName = "Bangladesh",
        dialCode = "+880",
        flagEmoji = "🇧🇩",
        expectedDigits = 10,
        sampleHint = "1712 345678",
    ),
}

enum class VisibilityOption(val label: String) {
    EVERYONE("Everyone"),
    CONTACTS("My Contacts"),
    NOBODY("Nobody"),
}

data class PrivacyPreferences(
    val lastSeenVisibility: VisibilityOption = VisibilityOption.CONTACTS,
    val onlineVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val profilePhotoVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val aboutVisibility: VisibilityOption = VisibilityOption.CONTACTS,
    val readReceiptsEnabled: Boolean = true,
)

data class NotificationPreferences(
    val messageAlerts: Boolean = true,
    val callAlerts: Boolean = true,
    val groupAlerts: Boolean = true,
    val inAppSounds: Boolean = true,
    val vibrationEnabled: Boolean = true,
)

data class UserAccount(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val photoUri: String? = null,
    val about: String = "Available on RM Messenger",
    val createdAtLabel: String = "Pending Cloud Sync",
    val isOnline: Boolean = false,
    val lastSeenLabel: String = "Offline",
    val isAuthenticated: Boolean = false,
    val role: String = "user",
    val accountStatus: String = "active",
) {
    val isAdmin: Boolean
        get() = role == "admin" || role == "super_admin"

    val isSuperAdmin: Boolean
        get() = role == "super_admin"

    val isSuspended: Boolean
        get() = accountStatus == "suspended"
}

enum class MessageDeliveryState {
    SENT,
    DELIVERED,
    READ,
}

enum class MessageType(val wireValue: String) {
    TEXT("text"),
    VOICE("voice"),
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    DOCUMENT("document");

    companion object {
        fun fromWire(value: String): MessageType {
            return entries.firstOrNull { it.wireValue == value } ?: TEXT
        }
    }
}

data class ConversationSummary(
    val conversationId: String,
    val title: String,
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
    val isGroup: Boolean = false,
    val otherUid: String = "",
    val memberCount: Int = 2,
    val typingLabel: String? = null,
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val messageType: MessageType = MessageType.TEXT,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long = 0L,
    val durationSeconds: Int = 0,
    val waveform: List<Int> = emptyList(),
    val mediaData: String? = null,
    val thumbnailData: String? = null,
    val isFromMe: Boolean,
    val timestampLabel: String,
    val createdAtMillis: Long,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val isForwarded: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val isReadByMe: Boolean = false,
)

enum class CallType {
    AUDIO,
    VIDEO,
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
}

data class CallRecord(
    val callId: String,
    val participantName: String,
    val callType: CallType,
    val direction: CallDirection,
    val timestampLabel: String,
    val durationLabel: String,
    val peerUid: String = "",
    val peerUsername: String = "",
)

data class ContactSummary(
    val uid: String,
    val displayName: String,
    val username: String,
    val phoneNumber: String,
    val about: String,
    val isOnline: Boolean,
    val isBlocked: Boolean = false,
)

data class GroupSummary(
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val lastMessagePreview: String,
    val updatedAtLabel: String,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
)

data class FeatureModule(
    val key: String,
    val title: String,
    val phaseLabel: String,
    val description: String,
    val remoteConfigControlled: Boolean = true,
    val isReadyInBuild: Boolean = false,
)

object FutureFeatureRegistry {
    val modules: List<FeatureModule> = listOf(
        FeatureModule("auth_phone_google", "Phone OTP & Google Sign-In", "Phase 2 (Live)", "Firebase Auth + Credential Manager + Firestore user profile", false, true),
        FeatureModule("private_messaging", "Real-Time Private Messaging", "Phase 3 (Live)", "Firestore real-time chats, typing, reactions, reply, forward, delete", false, true),
        FeatureModule("group_messaging", "Group Chats & Permissions", "Phase 3 (Live)", "Multi-member rooms, group admins, mute & member controls", false, true),
        FeatureModule("media_voice_notes", "Voice Notes & File Sharing", "Phase 4 (Live)", "Voice recording, waveform playback, photos, videos & documents", false, true),
        FeatureModule("webrtc_calling", "Audio & Video Calling", "Phase 5 (Live)", "WebRTC P2P audio/video, Firestore signaling, call controls & history", false, true),
        FeatureModule("super_admin_panel", "Role-Based Super Admin Panel", "Phase 6 (Live)", "Backend-authorized moderation, user suspension, announcements & platform controls", true, true),
        FeatureModule("remote_features", "Statuses, Channels & E2EE Prep", "Future", "Modular feature flags via Firebase Remote Config", true, false),
    )
}
