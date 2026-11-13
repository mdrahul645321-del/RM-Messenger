package com.example.core.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    suspend fun attemptSilentSignIn(
        context: Context,
        credentialManager: CredentialManager,
        userRepository: UserRepository,
    ): Boolean {
        if (Firebase.auth.currentUser != null) return true
        val activity = context.findActivity() ?: return false
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
            ?: return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return false
                userRepository.ensureUserProfileForAuth(user)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        userRepository: UserRepository,
        preferredDisplayName: String? = null,
        preferredUsername: String? = null,
        preferredAbout: String? = null,
        preferredPhone: String? = null,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthCancelled: () -> Unit,
        onAuthError: (String) -> Unit,
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Unable to access host Activity for Google Sign-In.")
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    val firebaseUser = authResult.user
                        ?: throw IllegalStateException("Firebase user was null after sign-in.")
                    val profileRes = userRepository.ensureUserProfileForAuth(
                        firebaseUser = firebaseUser,
                        preferredDisplayName = preferredDisplayName,
                        preferredUsername = preferredUsername,
                        preferredAbout = preferredAbout,
                        preferredPhone = preferredPhone,
                    )
                    if (profileRes.isFailure) {
                        Log.w(TAG, "Profile ensure warning: ${profileRes.exceptionOrNull()?.message}")
                    }
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        userRepository: UserRepository?,
        scope: CoroutineScope,
        onComplete: () -> Unit = {},
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    userRepository?.setOnlineStatus(false)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update offline status: ${e.message}")
                }
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onComplete()
                }
            }
        }
    }
}

object PhoneAuthHelper {
    fun sendOtp(
        activity: Activity,
        e164Phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailed: (FirebaseException) -> Unit,
    ) {
        val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(
                object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        onAutoVerified(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        onFailed(e)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                        onCodeSent(verificationId, token)
                    }
                },
            )
        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }
}
