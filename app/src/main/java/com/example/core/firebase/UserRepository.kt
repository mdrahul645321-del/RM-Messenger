package com.example.core.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun observeCurrentUser(): Flow<FirestoreUserDocument?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        FirestoreUserDocument::class.java,
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

    fun observeContacts(): Flow<List<FirestoreContactDocument>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("contacts")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreContactDocument::class.java,
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
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                },
        )
    }

    suspend fun ensureUserProfileForAuth(
        firebaseUser: FirebaseUser,
        preferredDisplayName: String? = null,
        preferredUsername: String? = null,
        preferredAbout: String? = null,
        preferredPhone: String? = null,
    ): Result<FirestoreUserDocument> {
        val uid = firebaseUser.uid
        val userDocRef = db.collection("users").document(uid)
        return try {
            val existingSnapshot = userDocRef.get().await()
            if (existingSnapshot.exists()) {
                val existing = existingSnapshot.toObject(
                    FirestoreUserDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                if (existing != null) {
                    runCatching {
                        userDocRef.update(
                            mapOf(
                                "onlineStatus" to "online",
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ),
                        ).await()
                    }
                    return Result.success(existing)
                }
            }

            val rawDisplayName = (preferredDisplayName?.takeIf { it.isNotBlank() }
                ?: firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: "RM User")
                .trim()
                .take(60)
                .let { if (it.length < 2) "RM User" else it }

            val candidateBase = sanitizeUsername(
                preferredUsername?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: rawDisplayName,
            )
            val resolvedUsername = claimAvailableUsername(uid, candidateBase, rawDisplayName, preferredAbout)

            val resolvedEmail = firebaseUser.email?.takeIf { it.isNotBlank() }?.take(160)
            val resolvedPhone = (preferredPhone?.takeIf { it.isNotBlank() } ?: firebaseUser.phoneNumber)
                ?.takeIf { it.isNotBlank() }
                ?.take(32)
            val resolvedPhoto = firebaseUser.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)
            val resolvedAbout = (preferredAbout?.takeIf { it.isNotBlank() } ?: "Available on RM Messenger")
                .trim()
                .take(140)

            val payload = mapOf(
                "uid" to uid,
                "displayName" to rawDisplayName,
                "username" to resolvedUsername,
                "email" to resolvedEmail,
                "phoneNumber" to resolvedPhone,
                "photoUrl" to resolvedPhoto,
                "about" to resolvedAbout,
                "onlineStatus" to "online",
                "role" to "user",
                "accountStatus" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            userDocRef.set(payload).await()
            val created = userDocRef.get().await().toObject(
                FirestoreUserDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: FirestoreUserDocument(
                uid = uid,
                displayName = rawDisplayName,
                username = resolvedUsername,
                email = resolvedEmail,
                phoneNumber = resolvedPhone,
                photoUrl = resolvedPhoto,
                about = resolvedAbout,
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userDocRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        newUsername: String,
        about: String,
        email: String?,
    ): Result<Unit> {
        val uid = requireUserId()
        val userDocRef = db.collection("users").document(uid)
        val sanitizedDisplay = displayName.trim().take(60)
        if (sanitizedDisplay.length < 2) {
            return Result.failure(IllegalArgumentException("Display name must be at least 2 characters."))
        }
        val cleanUsername = sanitizeUsername(newUsername)
        val cleanAbout = about.trim().take(140)

        return try {
            val currentSnap = userDocRef.get().await()
            val currentDoc = currentSnap.toObject(FirestoreUserDocument::class.java)
                ?: return Result.failure(IllegalStateException("User profile not initialized."))

            if (cleanUsername != currentDoc.username) {
                val targetIndexRef = db.collection("usernames").document(cleanUsername)
                val existingTarget = targetIndexRef.get().await()
                if (existingTarget.exists() && existingTarget.getString("userId") != uid) {
                    return Result.failure(IllegalArgumentException("Username @$cleanUsername is already taken."))
                }
                val indexPayload = mapOf(
                    "userId" to uid,
                    "username" to cleanUsername,
                    "displayName" to sanitizedDisplay,
                    "about" to cleanAbout,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                targetIndexRef.set(indexPayload).await()
                if (currentDoc.username.isNotBlank()) {
                    runCatching { db.collection("usernames").document(currentDoc.username).delete().await() }
                }
            } else {
                db.collection("usernames").document(cleanUsername).update(
                    mapOf(
                        "displayName" to sanitizedDisplay,
                        "about" to cleanAbout,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }

            val updates = mutableMapOf<String, Any>(
                "displayName" to sanitizedDisplay,
                "username" to cleanUsername,
                "about" to cleanAbout,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!email.isNullOrBlank()) {
                updates["email"] = email.trim().take(160)
            }
            userDocRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDocRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun lookupByUsername(rawQuery: String): Result<UsernameLookupDocument?> {
        requireUserId()
        val handle = rawQuery.trim().lowercase().removePrefix("@")
        if (!USERNAME_REGEX.matches(handle)) {
            return Result.failure(IllegalArgumentException("Enter a valid username (3–24 lowercase letters, numbers, or _)."))
        }
        val docRef = db.collection("usernames").document(handle)
        return try {
            val snap = docRef.get().await()
            val item = snap.toObject(
                UsernameLookupDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addContact(target: UsernameLookupDocument): Result<Unit> {
        val uid = requireUserId()
        if (target.userId == uid) {
            return Result.failure(IllegalArgumentException("You cannot add yourself as a contact."))
        }
        val contactRef = db.collection("users").document(uid)
            .collection("contacts").document(target.userId)
        val payload = mapOf(
            "contactUid" to target.userId,
            "displayName" to target.displayName.take(60),
            "username" to target.username.take(24),
            "about" to target.about.take(140),
            "isBlocked" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            contactRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, contactRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockContact(contactUid: String, blocked: Boolean): Result<Unit> {
        val uid = requireUserId()
        val contactRef = db.collection("users").document(uid)
            .collection("contacts").document(contactUid)
        return try {
            contactRef.update(
                mapOf(
                    "isBlocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, contactRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "onlineStatus" to if (online) "online" else "offline",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        }
    }

    private suspend fun claimAvailableUsername(
        uid: String,
        base: String,
        displayName: String,
        about: String?,
    ): String {
        val suffix = uid.lowercase().filter { it.isLetterOrDigit() }.take(4).ifBlank { "rm" }
        val candidates = listOf(base, "${base.take(19)}_$suffix", "rm_${uid.lowercase().filter { it.isLetterOrDigit() }.take(8)}")
        for (candidate in candidates) {
            val valid = sanitizeUsername(candidate)
            val ref = db.collection("usernames").document(valid)
            val snap = ref.get().await()
            if (!snap.exists() || snap.getString("userId") == uid) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "username" to valid,
                        "displayName" to displayName,
                        "about" to (about?.take(140) ?: "Available on RM Messenger"),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                return valid
            }
        }
        val fallback = sanitizeUsername("user_${uid.lowercase().filter { it.isLetterOrDigit() }.take(10)}")
        db.collection("usernames").document(fallback).set(
            mapOf(
                "userId" to uid,
                "username" to fallback,
                "displayName" to displayName,
                "about" to (about?.take(140) ?: "Available on RM Messenger"),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return fallback
    }

    private fun sanitizeUsername(raw: String): String {
        val filtered = raw.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(24)
        return when {
            filtered.length >= 3 -> filtered
            filtered.isNotEmpty() -> "${filtered}_rm".take(24)
            else -> "rm_user"
        }
    }

    private companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9_]{3,24}$")
    }
}
