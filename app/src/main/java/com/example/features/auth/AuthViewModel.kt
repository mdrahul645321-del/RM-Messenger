package com.example.features.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.firebase.GoogleAuthHelper
import com.example.core.firebase.PhoneAuthHelper
import com.example.core.firebase.UserRepository
import com.example.core.model.CountryCode
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val selectedCountry: CountryCode = CountryCode.KUWAIT,
    val phoneDigits: String = "",
    val otpCode: String = "",
    val verificationId: String? = null,
    val isOtpStep: Boolean = false,
    val isSendingOtp: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    val isLoadingGoogle: Boolean = false,
    val resendSecondsRemaining: Int = 0,
    val phoneError: String? = null,
    val otpError: String? = null,
    val authStatusMessage: String? = null,
    val authErrorMessage: String? = null,
    // Registration fields applied after auth succeeds
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val about: String = "Hey there! I am using RM Messenger.",
    val registrationError: String? = null,
)

class AuthViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun selectCountry(country: CountryCode) {
        _uiState.update {
            it.copy(
                selectedCountry = country,
                phoneError = null,
                authErrorMessage = null,
            )
        }
    }

    fun updatePhoneDigits(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(12)
        _uiState.update {
            it.copy(
                phoneDigits = digitsOnly,
                phoneError = null,
                authErrorMessage = null,
            )
        }
    }

    fun updateOtpCode(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(6)
        _uiState.update {
            it.copy(
                otpCode = digitsOnly,
                otpError = null,
                authErrorMessage = null,
            )
        }
    }

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value, registrationError = null) }
    }

    fun updateUsername(value: String) {
        val sanitized = value.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(24)
        _uiState.update { it.copy(username = sanitized, registrationError = null) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, registrationError = null) }
    }

    fun updateAbout(value: String) {
        _uiState.update { it.copy(about = value.take(140)) }
    }

    fun sendPhoneOtp(activity: Activity?, fromRegistration: Boolean = false) {
        val state = _uiState.value
        if (fromRegistration) {
            val regErr = validateRegistrationFields(state)
            if (regErr != null) {
                _uiState.update { it.copy(registrationError = regErr) }
                return
            }
        } else if (state.phoneDigits.length != state.selectedCountry.expectedDigits) {
            _uiState.update {
                it.copy(
                    phoneError = "Enter a valid ${state.selectedCountry.expectedDigits}-digit ${state.selectedCountry.countryName} number.",
                )
            }
            return
        }

        if (activity == null) {
            _uiState.update { it.copy(authErrorMessage = "Activity unavailable to start phone verification.") }
            return
        }

        val e164Phone = "${state.selectedCountry.dialCode}${state.phoneDigits}"
        _uiState.update {
            it.copy(
                isSendingOtp = true,
                phoneError = null,
                authErrorMessage = null,
                authStatusMessage = null,
            )
        }

        PhoneAuthHelper.sendOtp(
            activity = activity,
            e164Phone = e164Phone,
            resendToken = resendToken,
            onCodeSent = { verId, token ->
                resendToken = token
                _uiState.update {
                    it.copy(
                        isSendingOtp = false,
                        isOtpStep = true,
                        verificationId = verId,
                        authStatusMessage = "Verification code sent to $e164Phone.",
                    )
                }
                startResendTimer()
            },
            onAutoVerified = { credential ->
                signInWithPhoneCredential(credential)
            },
            onFailed = { exception ->
                val raw = exception.localizedMessage ?: "Phone verification could not start."
                val friendly = if (raw.contains("operation-not-allowed", ignoreCase = true) ||
                    raw.contains("MISSING_CLIENT_IDENTIFIER", ignoreCase = true)
                ) {
                    "Phone OTP provider is not enabled yet in Firebase Console. Enable Phone Sign-In under Authentication -> Sign-in method, or sign in with Google below."
                } else {
                    raw
                }
                _uiState.update {
                    it.copy(
                        isSendingOtp = false,
                        authErrorMessage = friendly,
                    )
                }
            },
        )
    }

    fun resendPhoneOtp(activity: Activity?) {
        if (_uiState.value.resendSecondsRemaining > 0) return
        sendPhoneOtp(activity, fromRegistration = false)
    }

    fun verifyOtpCode() {
        val state = _uiState.value
        if (state.otpCode.length < 6) {
            _uiState.update { it.copy(otpError = "Enter the 6-digit verification code.") }
            return
        }
        val verId = state.verificationId
        if (verId.isNullOrBlank()) {
            _uiState.update { it.copy(otpError = "Verification session expired. Request a new code.") }
            return
        }
        val credential = PhoneAuthProvider.getCredential(verId, state.otpCode)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        val state = _uiState.value
        _uiState.update { it.copy(isVerifyingOtp = true, otpError = null, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                val authResult = Firebase.auth.signInWithCredential(credential).await()
                val user = authResult.user
                    ?: throw IllegalStateException("Firebase user was null after OTP verification.")
                val formattedPhone = if (state.phoneDigits.isNotBlank()) {
                    "${state.selectedCountry.dialCode}${state.phoneDigits}"
                } else {
                    null
                }
                userRepository.ensureUserProfileForAuth(
                    firebaseUser = user,
                    preferredDisplayName = state.displayName.takeIf { it.isNotBlank() },
                    preferredUsername = state.username.takeIf { it.isNotBlank() },
                    preferredAbout = state.about.takeIf { it.isNotBlank() },
                    preferredPhone = formattedPhone,
                )
                _uiState.update { it.copy(isVerifyingOtp = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isVerifyingOtp = false,
                        otpError = e.localizedMessage ?: "Invalid or expired OTP code.",
                    )
                }
            }
        }
    }

    fun backToPhoneInput() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isOtpStep = false,
                otpCode = "",
                otpError = null,
                verificationId = null,
                resendSecondsRemaining = 0,
            )
        }
    }

    fun signInWithGoogle(context: Context, fromRegistration: Boolean = false) {
        val state = _uiState.value
        if (fromRegistration && state.displayName.isNotBlank()) {
            val regErr = validateOptionalRegistrationFields(state)
            if (regErr != null) {
                _uiState.update { it.copy(registrationError = regErr) }
                return
            }
        }
        val credentialManager = CredentialManager.create(context)
        _uiState.update {
            it.copy(
                isLoadingGoogle = true,
                authErrorMessage = null,
                authStatusMessage = null,
            )
        }
        val formattedPhone = if (state.phoneDigits.length == state.selectedCountry.expectedDigits) {
            "${state.selectedCountry.dialCode}${state.phoneDigits}"
        } else {
            null
        }
        GoogleAuthHelper.signInWithGoogle(
            context = context,
            credentialManager = credentialManager,
            userRepository = userRepository,
            preferredDisplayName = state.displayName.takeIf { it.isNotBlank() },
            preferredUsername = state.username.takeIf { it.isNotBlank() },
            preferredAbout = state.about.takeIf { it.isNotBlank() },
            preferredPhone = formattedPhone,
            scope = viewModelScope,
            onAuthSuccess = {
                _uiState.update { it.copy(isLoadingGoogle = false) }
            },
            onAuthCancelled = {
                _uiState.update {
                    it.copy(
                        isLoadingGoogle = false,
                        authStatusMessage = "Google Sign-In cancelled. Choose an account to continue.",
                    )
                }
            },
            onAuthError = { errorMsg ->
                _uiState.update {
                    it.copy(
                        isLoadingGoogle = false,
                        authErrorMessage = errorMsg,
                    )
                }
            },
        )
    }

    private fun validateRegistrationFields(state: AuthUiState): String? {
        return when {
            state.displayName.trim().length < 2 -> "Display name must be at least 2 characters."
            state.username.length < 3 -> "Username must be at least 3 characters (letters, numbers, _)."
            state.phoneDigits.length != state.selectedCountry.expectedDigits ->
                "Enter a valid ${state.selectedCountry.expectedDigits}-digit ${state.selectedCountry.countryName} phone number."
            state.email.isNotBlank() && !state.email.contains("@") ->
                "Enter a valid email address or leave it blank."
            else -> null
        }
    }

    private fun validateOptionalRegistrationFields(state: AuthUiState): String? {
        return when {
            state.displayName.trim().length < 2 -> "Display name must be at least 2 characters."
            state.username.isNotBlank() && state.username.length < 3 ->
                "Username must be at least 3 characters (letters, numbers, _)."
            else -> null
        }
    }

    private fun startResendTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 30 downTo 0) {
                _uiState.update { it.copy(resendSecondsRemaining = sec) }
                if (sec > 0) delay(1000L)
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    class Factory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(userRepository) as T
        }
    }
}
