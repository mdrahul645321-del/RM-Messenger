package com.example.core.firebase

import android.content.Context
import com.example.R
import com.example.core.media.MediaAttachmentHelper
import com.example.core.media.PreparedAttachment
import com.example.core.model.ChatMessage
import com.example.core.model.ContactSummary
import com.example.core.model.MessageType
import com.example.core.model.UserAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing chats.")
    }

    fun observeConversations(): Flow<List<FirestoreConversationDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("conversations")
        val query = colRef.whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreConversationDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
                        .sortedWith(
                            compareByDescending<FirestoreConversationDocument> { it.pinnedBy.contains(uid) }
                                .thenByDescending { (it.lastMessageAt ?: it.updatedAt)?.toDate()?.time ?: 0L },
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                },
        )
    }

    fun observeConversation(conversationId: String): Flow<FirestoreConversationDocument?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document(conversationId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestoreConversationDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                },
        )
    }

    fun observeMessages(conversationId: String): Flow<List<FirestoreMessageDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("conversations").document(conversationId).collection("messages")
        val query = colRef.orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(200)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreMessageDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
                        .filter { !it.deletedForUids.contains(uid) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                },
        )
    }

    suspend fun getOrCreateDirectConversation(
        currentUser: UserAccount,
        peerUid: String,
        peerDisplayName: String,
        peerUsername: String,
    ): Result<String> {
        val uid = requireUserId()
        if (peerUid == uid) {
            return Result.failure(IllegalArgumentException("Cannot start a direct chat with yourself."))
        }
        val convId = directConversationId(uid, peerUid)
        val docRef = db.collection("conversations").document(convId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val sortedMembers = listOf(uid, peerUid).sorted()
                val myName = currentUser.displayName.ifBlank { "RM User" }.take(60)
                val peerName = peerDisplayName.ifBlank { "RM User" }.take(60)
                val myUsername = currentUser.username.ifBlank { "rm_user" }.take(24)
                val cleanPeerUsername = peerUsername.ifBlank { "rm_user" }.take(24)
                val payload = mapOf(
                    "type" to "direct",
                    "memberUids" to sortedMembers,
                    "adminUids" to sortedMembers,
                    "title" to "",
                    "description" to "",
                    "participantNames" to mapOf(
                        uid to myName,
                        peerUid to peerName,
                    ),
                    "participantUsernames" to mapOf(
                        uid to myUsername,
                        peerUid to cleanPeerUsername,
                    ),
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "lastMessageSenderName" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to mapOf(uid to 0L, peerUid to 0L),
                    "pinnedBy" to emptyList<String>(),
                    "mutedBy" to emptyList<String>(),
                    "typingUids" to emptyList<String>(),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        currentUser: UserAccount,
        title: String,
        description: String,
        selectedMembers: List<ContactSummary>,
    ): Result<String> {
        val uid = requireUserId()
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Group name must be at least 2 characters."))
        }
        val cleanDesc = description.trim().take(200)
        val convId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("conversations").document(convId)

        val uniqueMembers = (listOf(uid) + selectedMembers.map { it.uid }).distinct().take(100)
        val participantNames = buildMap {
            put(uid, currentUser.displayName.ifBlank { "RM User" }.take(60))
            selectedMembers.forEach { member ->
                put(member.uid, member.displayName.ifBlank { "RM User" }.take(60))
            }
        }
        val participantUsernames = buildMap {
            put(uid, currentUser.username.ifBlank { "rm_user" }.take(24))
            selectedMembers.forEach { member ->
                put(member.uid, member.username.ifBlank { "rm_user" }.take(24))
            }
        }
        val unreadCounts = uniqueMembers.associateWith { 0L }

        val payload = mapOf(
            "type" to "group",
            "memberUids" to uniqueMembers,
            "adminUids" to listOf(uid),
            "title" to cleanTitle,
            "description" to cleanDesc,
            "participantNames" to participantNames,
            "participantUsernames" to participantUsernames,
            "lastMessageText" to "Group created",
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to currentUser.displayName.ifBlank { "RM User" }.take(60),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to unreadCounts,
            "pinnedBy" to emptyList<String>(),
            "mutedBy" to emptyList<String>(),
            "typingUids" to emptyList<String>(),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        return try {
            docRef.set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: FirestoreConversationDocument,
        senderName: String,
        rawText: String,
        replyTo: ChatMessage? = null,
        isForwarded: Boolean = false,
    ): Result<String> {
        val uid = requireUserId()
        val cleanText = rawText.trim().take(4000)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        val cleanSenderName = senderName.trim().ifBlank { "RM User" }.take(60)
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()

        val msgPayload = buildMap<String, Any> {
            put("senderId", uid)
            put("senderName", cleanSenderName)
            put("text", cleanText)
            put("messageType", "text")
            if (replyTo != null) {
                put("replyToMessageId", replyTo.id)
                put("replyToSenderName", replyTo.senderName.take(60))
                put("replyToPreview", replyTo.text.take(200))
            }
            put("forwardedFrom", isForwarded)
            put("reactions", emptyMap<String, String>())
            put("readByUids", listOf(uid))
            put("deliveredToUids", listOf(uid))
            put("deletedForUids", emptyList<String>())
            put("isDeletedForEveryone", false)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val convUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to cleanText.take(500),
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to cleanSenderName,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "typingUids" to FieldValue.arrayRemove(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        conversation.memberUids
            .filter { it != uid }
            .forEach { peerUid ->
                convUpdates["unreadCounts.$peerUid"] = FieldValue.increment(1L)
            }
        convUpdates["unreadCounts.$uid"] = 0L

        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(convRef, convUpdates)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        conversation: FirestoreConversationDocument,
        senderName: String,
        attachment: PreparedAttachment,
        caption: String = "",
        replyTo: ChatMessage? = null,
        isForwarded: Boolean = false,
    ): Result<String> {
        val uid = requireUserId()
        if (attachment.base64Data.isBlank() || attachment.base64Data.length > 950_000) {
            return Result.failure(IllegalArgumentException("Attachment exceeds Firestore inline payload limit."))
        }
        val cleanSenderName = senderName.trim().ifBlank { "RM User" }.take(60)
        val cleanFileName = attachment.fileName.trim().take(160).ifBlank { "attachment" }
        val cleanCaption = caption.trim().take(1000)
        val displayText = cleanCaption.ifBlank {
            when (attachment.messageType) {
                MessageType.VOICE -> "Voice message (${MediaAttachmentHelper.formatDuration(attachment.durationSeconds)})"
                MessageType.IMAGE -> "Photo"
                MessageType.VIDEO -> "Video (${MediaAttachmentHelper.formatDuration(attachment.durationSeconds)})"
                MessageType.AUDIO -> "Audio: $cleanFileName"
                MessageType.DOCUMENT -> cleanFileName
                MessageType.TEXT -> "Message"
            }
        }
        val conversationPreview = when (attachment.messageType) {
            MessageType.VOICE -> "Voice message (${MediaAttachmentHelper.formatDuration(attachment.durationSeconds)})"
            MessageType.IMAGE -> if (cleanCaption.isNotBlank()) "Photo: $cleanCaption" else "Photo"
            MessageType.VIDEO -> if (cleanCaption.isNotBlank()) "Video: $cleanCaption" else "Video"
            MessageType.AUDIO -> "Audio: $cleanFileName"
            MessageType.DOCUMENT -> "File: $cleanFileName"
            MessageType.TEXT -> displayText
        }.take(500)

        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()

        val msgPayload = buildMap<String, Any> {
            put("senderId", uid)
            put("senderName", cleanSenderName)
            put("text", displayText)
            put("messageType", attachment.messageType.wireValue)
            put("fileName", cleanFileName)
            put("mimeType", attachment.mimeType.take(100))
            put("fileSizeBytes", attachment.fileSizeBytes)
            put("durationSeconds", attachment.durationSeconds.toLong())
            if (attachment.waveform.isNotEmpty()) {
                put("waveform", attachment.waveform.take(64).map { it.toLong() })
            }
            put("mediaData", attachment.base64Data)
            if (!attachment.thumbnailBase64.isNullOrBlank() && attachment.thumbnailBase64.length <= 120_000) {
                put("thumbnailData", attachment.thumbnailBase64)
            }
            if (replyTo != null) {
                put("replyToMessageId", replyTo.id)
                put("replyToSenderName", replyTo.senderName.take(60))
                put("replyToPreview", replyTo.text.take(200))
            }
            put("forwardedFrom", isForwarded)
            put("reactions", emptyMap<String, String>())
            put("readByUids", listOf(uid))
            put("deliveredToUids", listOf(uid))
            put("deletedForUids", emptyList<String>())
            put("isDeletedForEveryone", false)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val convUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to conversationPreview,
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to cleanSenderName,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "typingUids" to FieldValue.arrayRemove(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        conversation.memberUids
            .filter { it != uid }
            .forEach { peerUid ->
                convUpdates["unreadCounts.$peerUid"] = FieldValue.increment(1L)
            }
        convUpdates["unreadCounts.$uid"] = 0L

        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(convRef, convUpdates)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun markConversationAsRead(
        conversationId: String,
        unreadMessages: List<FirestoreMessageDocument>,
        sendReadReceipt: Boolean,
    ) {
        val uid = auth.currentUser?.uid ?: return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val batch = db.batch()
            var hasChanges = false

            val toUpdate = unreadMessages
                .filter { msg ->
                    msg.senderId != uid &&
                        (!msg.deliveredToUids.contains(uid) || (sendReadReceipt && !msg.readByUids.contains(uid)))
                }
                .take(40)

            toUpdate.forEach { msg ->
                val msgRef = convRef.collection("messages").document(msg.id)
                val updates = mutableMapOf<String, Any>(
                    "deliveredToUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (sendReadReceipt) {
                    updates["readByUids"] = FieldValue.arrayUnion(uid)
                }
                batch.update(msgRef, updates)
                hasChanges = true
            }

            batch.update(
                convRef,
                mapOf(
                    "unreadCounts.$uid" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            hasChanges = true

            if (hasChanges) {
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
        }
    }

    suspend fun setTyping(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "typingUids" to if (isTyping) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
        }
    }

    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        currentReactions: Map<String, String>,
    ): Result<Unit> {
        val uid = requireUserId()
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        val existingEmoji = currentReactions[uid]
        val value: Any = if (existingEmoji == emoji) FieldValue.delete() else emoji
        return try {
            msgRef.update(
                mapOf(
                    "reactions.$uid" to value,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(conversationId: String, messageId: String): Result<Unit> {
        val uid = requireUserId()
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "deletedForUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> {
        requireUserId()
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "isDeletedForEveryone" to true,
                    "text" to "This message was deleted",
                    "reactions" to emptyMap<String, String>(),
                    "mediaData" to FieldValue.delete(),
                    "thumbnailData" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun togglePinConversation(conversationId: String, currentlyPinned: Boolean): Result<Unit> {
        val uid = requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "pinnedBy" to if (currentlyPinned) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun toggleMuteConversation(conversationId: String, currentlyMuted: Boolean): Result<Unit> {
        val uid = requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "mutedBy" to if (currentlyMuted) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateGroupInfo(
        conversationId: String,
        newTitle: String,
        newDescription: String,
    ): Result<Unit> {
        requireUserId()
        val cleanTitle = newTitle.trim().take(80)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Group name must be at least 2 characters."))
        }
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "description" to newDescription.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addGroupMember(conversationId: String, contact: ContactSummary): Result<Unit> {
        requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(contact.uid),
                    "participantNames.${contact.uid}" to contact.displayName.ifBlank { "RM User" }.take(60),
                    "participantUsernames.${contact.uid}" to contact.username.ifBlank { "rm_user" }.take(24),
                    "unreadCounts.${contact.uid}" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(conversationId: String, targetUid: String): Result<Unit> {
        val uid = requireUserId()
        if (uid == targetUid) {
            return Result.failure(IllegalArgumentException("Use Leave Group to remove yourself."))
        }
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(targetUid),
                    "adminUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun toggleGroupAdmin(
        conversationId: String,
        targetUid: String,
        makeAdmin: Boolean,
    ): Result<Unit> {
        requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "adminUids" to if (makeAdmin) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(conversation: FirestoreConversationDocument): Result<Unit> {
        val uid = requireUserId()
        if (conversation.memberUids.size <= 1) {
            return Result.failure(IllegalArgumentException("You are the last member in this group."))
        }
        val convRef = db.collection("conversations").document(conversation.id)
        val remainingMembers = conversation.memberUids.filter { it != uid }
        val remainingAdmins = conversation.adminUids.filter { it != uid }
        val nextAdmins = if (remainingAdmins.isEmpty()) listOf(remainingMembers.first()) else remainingAdmins
        return try {
            convRef.update(
                mapOf(
                    "memberUids" to remainingMembers,
                    "adminUids" to nextAdmins,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    companion object {
        fun directConversationId(uidA: String, uidB: String): String {
            return if (uidA <= uidB) "dm_${uidA}_${uidB}" else "dm_${uidB}_${uidA}"
        }
    }
}
