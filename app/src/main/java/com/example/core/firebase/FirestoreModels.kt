package com.example.core.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserDocument(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val about: String = "Available on RM Messenger",
    val onlineStatus: String = "online",
    val role: String = "user",
    val accountStatus: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UsernameLookupDocument(
    @DocumentId val usernameKey: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val about: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreContactDocument(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    @get:PropertyName("isBlocked")
    @set:PropertyName("isBlocked")
    var isBlocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreConversationDocument(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val title: String = "",
    val description: String = "",
    val participantNames: Map<String, String> = emptyMap(),
    val participantUsernames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val pinnedBy: List<String> = emptyList(),
    val mutedBy: List<String> = emptyList(),
    val typingUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreMessageDocument(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val durationSeconds: Long? = null,
    val waveform: List<Long>? = null,
    val mediaData: String? = null,
    val thumbnailData: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val forwardedFrom: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val readByUids: List<String> = emptyList(),
    val deliveredToUids: List<String> = emptyList(),
    val deletedForUids: List<String> = emptyList(),
    @get:PropertyName("isDeletedForEveryone")
    @set:PropertyName("isDeletedForEveryone")
    var isDeletedForEveryone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreCallSessionDocument(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerUsername: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeUsername: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val endedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreIceCandidateDocument(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreAnnouncementDocument(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = "info",
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreModerationReportDocument(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val reporterName: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val targetType: String = "user",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    val resolutionNote: String? = null,
    val reviewedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestorePlatformConfigDocument(
    @DocumentId val id: String = "platform",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "RM Messenger is undergoing scheduled maintenance. Please check back shortly.",
    val allowNewRegistrations: Boolean = true,
    val callingEnabled: Boolean = true,
    val voiceMessagesEnabled: Boolean = true,
    val mediaSharingEnabled: Boolean = true,
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)


