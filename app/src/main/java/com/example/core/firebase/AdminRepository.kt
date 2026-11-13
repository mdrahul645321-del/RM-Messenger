package com.example.core.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdminRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing admin operations.")
    }

    fun observeAnnouncements(activeOnly: Boolean = true): Flow<List<FirestoreAnnouncementDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("announcements")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreAnnouncementDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
                        .filter { !activeOnly || it.isActive }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
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

    fun observePlatformConfig(): Flow<FirestorePlatformConfigDocument> = flow {
        val uid = requireUserId()
        val docRef = db.collection("app_config").document(PLATFORM_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestorePlatformConfigDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ) ?: FirestorePlatformConfigDocument()
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

    fun observeAllUsersForAdmin(): Flow<List<FirestoreUserDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreUserDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
                        .sortedWith(
                            compareBy<FirestoreUserDocument>(
                                { roleOrder(it.role) },
                                { it.displayName.lowercase() },
                            ),
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

    fun observeAllReportsForAdmin(): Flow<List<FirestoreModerationReportDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("moderation_reports")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreModerationReportDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
                        .sortedWith(
                            compareBy<FirestoreModerationReportDocument>(
                                { if (it.status == "open") 0 else 1 },
                                { -(it.createdAt?.toDate()?.time ?: 0L) },
                            ),
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

    suspend fun submitReport(
        reporterName: String,
        targetUid: String,
        targetName: String,
        targetType: String,
        targetId: String,
        reason: String,
        details: String,
    ): Result<String> {
        val uid = requireUserId()
        val cleanReason = reason.trim().take(100)
        if (cleanReason.length < 2) {
            return Result.failure(IllegalArgumentException("Please provide a valid report reason."))
        }
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("moderation_reports").document(reportId)
        val payload = mapOf(
            "reporterUid" to uid,
            "reporterName" to reporterName.trim().ifBlank { "RM User" }.take(60),
            "targetUid" to targetUid.trim().ifBlank { targetId }.take(128),
            "targetName" to targetName.trim().ifBlank { "RM User" }.take(60),
            "targetType" to targetType.takeIf { it in listOf("user", "chat", "group", "message") }.orEmpty().ifBlank { "user" },
            "targetId" to targetId.trim().ifBlank { targetUid }.take(128),
            "reason" to cleanReason,
            "details" to details.trim().take(600),
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun resolveReport(
        reportId: String,
        newStatus: String,
        resolutionNote: String,
    ): Result<Unit> {
        val uid = requireUserId()
        if (newStatus !in listOf("resolved", "dismissed")) {
            return Result.failure(IllegalArgumentException("Invalid report resolution status."))
        }
        val docRef = db.collection("moderation_reports").document(reportId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "resolutionNote" to resolutionNote.trim().take(400),
                    "reviewedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun setUserAccountStatus(targetUid: String, newStatus: String): Result<Unit> {
        val uid = requireUserId()
        if (targetUid == uid) {
            return Result.failure(IllegalArgumentException("Cannot suspend your own Super Admin account."))
        }
        if (newStatus !in listOf("active", "suspended")) {
            return Result.failure(IllegalArgumentException("Invalid account status."))
        }
        val docRef = db.collection("users").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "accountStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun setUserRole(targetUid: String, newRole: String): Result<Unit> {
        val uid = requireUserId()
        if (targetUid == uid) {
            return Result.failure(IllegalArgumentException("Cannot change your own role."))
        }
        if (newRole !in listOf("user", "admin")) {
            return Result.failure(IllegalArgumentException("Client moderation supports 'user' or 'admin' roles only."))
        }
        val docRef = db.collection("users").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "role" to newRole,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncement(
        authorName: String,
        title: String,
        body: String,
        priority: String,
    ): Result<String> {
        val uid = requireUserId()
        val cleanTitle = title.trim().take(100)
        val cleanBody = body.trim().take(1000)
        if (cleanTitle.length < 2 || cleanBody.length < 2) {
            return Result.failure(IllegalArgumentException("Title and announcement message must be at least 2 characters."))
        }
        val cleanPriority = if (priority in listOf("info", "important", "maintenance")) priority else "info"
        val annId = "ann_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("announcements").document(annId)
        val payload = mapOf(
            "title" to cleanTitle,
            "body" to cleanBody,
            "priority" to cleanPriority,
            "isActive" to true,
            "authorUid" to uid,
            "authorName" to authorName.trim().ifBlank { "RM Admin" }.take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(annId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun toggleAnnouncementActive(announcementId: String, currentlyActive: Boolean): Result<Unit> {
        requireUserId()
        val docRef = db.collection("announcements").document(announcementId)
        return try {
            docRef.update(
                mapOf(
                    "isActive" to !currentlyActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("announcements").document(announcementId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updatePlatformConfig(config: FirestorePlatformConfigDocument): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("app_config").document(PLATFORM_DOC_ID)
        val payload = mapOf(
            "maintenanceMode" to config.maintenanceMode,
            "maintenanceMessage" to config.maintenanceMessage.trim().take(300),
            "allowNewRegistrations" to config.allowNewRegistrations,
            "callingEnabled" to config.callingEnabled,
            "voiceMessagesEnabled" to config.voiceMessagesEnabled,
            "mediaSharingEnabled" to config.mediaSharingEnabled,
            "updatedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    private fun roleOrder(role: String): Int = when (role) {
        "super_admin" -> 0
        "admin" -> 1
        else -> 2
    }

    companion object {
        const val PLATFORM_DOC_ID = "platform"
    }
}
