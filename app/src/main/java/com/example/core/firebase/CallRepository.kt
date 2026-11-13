package com.example.core.firebase

import android.content.Context
import com.example.R
import com.example.core.model.CallType
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

class CallRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing calls.")
    }

    fun observeCallHistory(): Flow<List<FirestoreCallSessionDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("calls")
        val query = colRef.whereArrayContains("participantUids", uid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                FirestoreCallSessionDocument::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                            )
                        }
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

    fun observeIncomingRingingCalls(): Flow<FirestoreCallSessionDocument?> =
        observeCallHistory().map { calls ->
            val uid = auth.currentUser?.uid ?: return@map null
            calls.firstOrNull { it.calleeId == uid && it.status == "ringing" }
        }

    fun observeCall(callId: String): Flow<FirestoreCallSessionDocument?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestoreCallSessionDocument::class.java,
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

    fun observeIceCandidates(callId: String): Flow<List<FirestoreIceCandidateDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("calls").document(callId).collection("candidates")
        val query = colRef.orderBy("createdAt", Query.Direction.ASCENDING)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            FirestoreIceCandidateDocument::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    }
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

    suspend fun createOutgoingCall(
        caller: UserAccount,
        calleeUid: String,
        calleeName: String,
        calleeUsername: String,
        callType: CallType,
        offerSdp: String? = null,
    ): Result<String> {
        val uid = requireUserId()
        if (calleeUid.isBlank() || calleeUid == uid) {
            return Result.failure(IllegalArgumentException("Cannot call this user."))
        }
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("calls").document(callId)
        val payload = buildMap<String, Any> {
            put("callerId", uid)
            put("callerName", caller.displayName.trim().ifBlank { "RM User" }.take(60))
            put("callerUsername", caller.username.trim().ifBlank { "rm_user" }.take(24))
            put("calleeId", calleeUid)
            put("calleeName", calleeName.trim().ifBlank { "RM User" }.take(60))
            put("calleeUsername", calleeUsername.trim().ifBlank { "rm_user" }.take(24))
            put("participantUids", listOf(uid, calleeUid))
            put("callType", if (callType == CallType.VIDEO) "video" else "audio")
            put("status", "ringing")
            if (!offerSdp.isNullOrBlank()) {
                put("offerSdp", offerSdp.take(30_000))
            }
            put("durationSeconds", 0L)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        return try {
            docRef.set(payload).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateOfferSdp(callId: String, offerSdp: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.update(
                mapOf(
                    "offerSdp" to offerSdp.take(30_000),
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

    suspend fun acceptCall(callId: String, answerSdp: String?): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        val updates = buildMap<String, Any> {
            put("status", "accepted")
            if (!answerSdp.isNullOrBlank()) {
                put("answerSdp", answerSdp.take(30_000))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun rejectCall(callId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "rejected",
                    "endedAt" to FieldValue.serverTimestamp(),
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

    suspend fun endCall(
        callId: String,
        durationSeconds: Long,
        markAsMissed: Boolean = false,
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        val finalStatus = if (markAsMissed) "missed" else "ended"
        return try {
            docRef.update(
                mapOf(
                    "status" to finalStatus,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
                    "endedAt" to FieldValue.serverTimestamp(),
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

    suspend fun addIceCandidate(
        callId: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String,
    ): Result<Unit> {
        val uid = requireUserId()
        val colRef = db.collection("calls").document(callId).collection("candidates")
        val docRef = colRef.document()
        val payload = mapOf(
            "senderId" to uid,
            "sdpMid" to sdpMid.take(64),
            "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
            "sdp" to sdp.take(4000),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
