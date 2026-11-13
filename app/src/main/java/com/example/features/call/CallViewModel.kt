package com.example.features.call

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.firebase.CallRepository
import com.example.core.firebase.FirestoreCallSessionDocument
import com.example.core.model.CallType
import com.example.core.model.UserAccount
import com.example.core.webrtc.RmWebRtcManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

enum class CallUiPhase {
    IDLE,
    OUTGOING_RINGING,
    CONNECTED,
}

data class ActiveCallUiState(
    val phase: CallUiPhase = CallUiPhase.IDLE,
    val callId: String = "",
    val callType: CallType = CallType.AUDIO,
    val isCaller: Boolean = true,
    val peerUid: String = "",
    val peerName: String = "",
    val peerUsername: String = "",
    val statusLabel: String = "",
    val elapsedSeconds: Long = 0L,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val iceConnectionLabel: String = "Connecting",
)

data class CallCoordinatorState(
    val incomingCall: FirestoreCallSessionDocument? = null,
    val activeCall: ActiveCallUiState = ActiveCallUiState(),
    val statusBanner: String? = null,
)

class CallCoordinatorViewModel(
    private val currentUserId: String,
    private val callRepository: CallRepository,
    appContext: Context,
) : ViewModel() {

    private val context = appContext.applicationContext
    private val webRtcManager = RmWebRtcManager(context)

    val localVideoTrack: StateFlow<VideoTrack?> = webRtcManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrackFlow
    val eglContext: StateFlow<EglBase.Context?> = webRtcManager.eglContextFlow

    private val _uiState = MutableStateFlow(CallCoordinatorState())
    val uiState: StateFlow<CallCoordinatorState> = _uiState.asStateFlow()

    private var activeCallObserverJob: Job? = null
    private var iceObserverJob: Job? = null
    private var durationTimerJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var ringtone: Ringtone? = null
    private val processedCandidateIds = ConcurrentHashMap.newKeySet<String>()

    init {
        observeIncomingCalls()
    }

    private fun observeIncomingCalls() {
        viewModelScope.launch {
            callRepository.observeIncomingRingingCalls()
                .catch { emit(null) }
                .collect { incoming ->
                    val currentActiveId = _uiState.value.activeCall.callId
                    if (currentActiveId.isNotBlank()) {
                        stopRingtone()
                        _uiState.update { it.copy(incomingCall = null) }
                    } else {
                        if (incoming != null) {
                            startRingtone()
                        } else {
                            stopRingtone()
                        }
                        _uiState.update { it.copy(incomingCall = incoming) }
                    }
                }
        }
    }

    fun onPermissionsDenied(isVideo: Boolean) {
        val needed = if (isVideo) "Microphone and Camera" else "Microphone"
        _uiState.update {
            it.copy(statusBanner = "$needed permissions are required for calling.")
        }
    }

    fun startOutgoingCall(
        caller: UserAccount,
        calleeUid: String,
        calleeName: String,
        calleeUsername: String,
        callType: CallType,
    ) {
        if (_uiState.value.activeCall.phase != CallUiPhase.IDLE) return
        stopRingtone()
        processedCandidateIds.clear()

        viewModelScope.launch {
            val createResult = callRepository.createOutgoingCall(
                caller = caller,
                calleeUid = calleeUid,
                calleeName = calleeName,
                calleeUsername = calleeUsername,
                callType = callType,
            )
            val callId = createResult.getOrElse { err ->
                _uiState.update {
                    it.copy(statusBanner = err.localizedMessage ?: "Failed to start call.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    incomingCall = null,
                    activeCall = ActiveCallUiState(
                        phase = CallUiPhase.OUTGOING_RINGING,
                        callId = callId,
                        callType = callType,
                        isCaller = true,
                        peerUid = calleeUid,
                        peerName = calleeName.ifBlank { "RM User" },
                        peerUsername = calleeUsername,
                        statusLabel = "Ringing…",
                        elapsedSeconds = 0L,
                        isMicMuted = false,
                        isSpeakerOn = callType == CallType.VIDEO,
                        isCameraEnabled = callType == CallType.VIDEO,
                        isFrontCamera = true,
                        iceConnectionLabel = "Initializing WebRTC…",
                    ),
                )
            }

            startWebRtcSession(
                callId = callId,
                isVideo = callType == CallType.VIDEO,
                isCaller = true,
                remoteOfferSdp = null,
            )
            attachCallObservers(callId = callId, isCaller = true)
            startRingTimeout(callId)
        }
    }

    fun acceptIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        stopRingtone()
        processedCandidateIds.clear()
        val callType = if (incoming.callType == "video") CallType.VIDEO else CallType.AUDIO

        _uiState.update {
            it.copy(
                incomingCall = null,
                activeCall = ActiveCallUiState(
                    phase = CallUiPhase.CONNECTED,
                    callId = incoming.id,
                    callType = callType,
                    isCaller = false,
                    peerUid = incoming.callerId,
                    peerName = incoming.callerName.ifBlank { "RM User" },
                    peerUsername = incoming.callerUsername,
                    statusLabel = "Connecting…",
                    elapsedSeconds = 0L,
                    isMicMuted = false,
                    isSpeakerOn = callType == CallType.VIDEO,
                    isCameraEnabled = callType == CallType.VIDEO,
                    isFrontCamera = true,
                    iceConnectionLabel = "Negotiating WebRTC…",
                ),
            )
        }

        val webrtcRes = startWebRtcSession(
            callId = incoming.id,
            isVideo = callType == CallType.VIDEO,
            isCaller = false,
            remoteOfferSdp = incoming.offerSdp,
        )
        if (webrtcRes.isFailure || incoming.offerSdp.isNullOrBlank()) {
            viewModelScope.launch {
                callRepository.acceptCall(incoming.id, answerSdp = null)
            }
        }
        attachCallObservers(callId = incoming.id, isCaller = false)
        startDurationTimer()
    }

    fun rejectIncomingCall() {
        val incoming = _uiState.value.incomingCall ?: return
        stopRingtone()
        _uiState.update { it.copy(incomingCall = null) }
        viewModelScope.launch {
            callRepository.rejectCall(incoming.id)
        }
    }

    fun toggleMute() {
        val next = !_uiState.value.activeCall.isMicMuted
        webRtcManager.setMicMuted(next)
        _uiState.update {
            it.copy(activeCall = it.activeCall.copy(isMicMuted = next))
        }
    }

    fun toggleSpeaker() {
        val next = !_uiState.value.activeCall.isSpeakerOn
        webRtcManager.setSpeakerphone(next)
        _uiState.update {
            it.copy(activeCall = it.activeCall.copy(isSpeakerOn = next))
        }
    }

    fun toggleCamera() {
        val next = !_uiState.value.activeCall.isCameraEnabled
        webRtcManager.setCameraEnabled(next)
        _uiState.update {
            it.copy(activeCall = it.activeCall.copy(isCameraEnabled = next))
        }
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
        val next = !_uiState.value.activeCall.isFrontCamera
        _uiState.update {
            it.copy(activeCall = it.activeCall.copy(isFrontCamera = next))
        }
    }

    fun endCurrentCall() {
        val current = _uiState.value.activeCall
        if (current.callId.isBlank()) {
            cleanupSessionState()
            return
        }
        val wasRinging = current.phase == CallUiPhase.OUTGOING_RINGING && current.isCaller
        val duration = current.elapsedSeconds
        val callId = current.callId
        cleanupSessionState(banner = "Call ended")

        viewModelScope.launch {
            callRepository.endCall(
                callId = callId,
                durationSeconds = duration,
                markAsMissed = wasRinging,
            )
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    private fun startWebRtcSession(
        callId: String,
        isVideo: Boolean,
        isCaller: Boolean,
        remoteOfferSdp: String?,
    ): Result<Unit> {
        return webRtcManager.startSession(
            isVideo = isVideo,
            isCaller = isCaller,
            remoteOfferSdp = remoteOfferSdp,
            onLocalSdpReady = { localSdp ->
                viewModelScope.launch {
                    if (isCaller) {
                        callRepository.updateOfferSdp(callId, localSdp)
                    } else {
                        callRepository.acceptCall(callId, answerSdp = localSdp)
                    }
                }
            },
            onIceCandidate = { sdpMid, sdpMLineIndex, sdp ->
                viewModelScope.launch {
                    callRepository.addIceCandidate(callId, sdpMid, sdpMLineIndex, sdp)
                }
            },
            onPeerStateChanged = { peerState ->
                val label = when (peerState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> "HD Encrypted"
                    PeerConnection.PeerConnectionState.CONNECTING -> "Connecting…"
                    PeerConnection.PeerConnectionState.DISCONNECTED -> "Reconnecting…"
                    PeerConnection.PeerConnectionState.FAILED -> "Connection lost"
                    PeerConnection.PeerConnectionState.CLOSED -> "Closed"
                    else -> "Signaling"
                }
                _uiState.update {
                    it.copy(activeCall = it.activeCall.copy(iceConnectionLabel = label))
                }
            },
        ).onFailure { err ->
            _uiState.update {
                it.copy(
                    activeCall = it.activeCall.copy(
                        iceConnectionLabel = "Audio/Signaling fallback",
                    ),
                    statusBanner = err.localizedMessage,
                )
            }
        }
    }

    private fun attachCallObservers(callId: String, isCaller: Boolean) {
        activeCallObserverJob?.cancel()
        activeCallObserverJob = viewModelScope.launch {
            callRepository.observeCall(callId)
                .catch { emit(null) }
                .collect { doc ->
                    if (doc == null) return@collect
                    when (doc.status) {
                        "accepted" -> {
                            ringTimeoutJob?.cancel()
                            if (isCaller && !doc.answerSdp.isNullOrBlank()) {
                                webRtcManager.applyRemoteAnswer(doc.answerSdp)
                            }
                            if (_uiState.value.activeCall.phase != CallUiPhase.CONNECTED) {
                                _uiState.update {
                                    it.copy(
                                        activeCall = it.activeCall.copy(
                                            phase = CallUiPhase.CONNECTED,
                                            statusLabel = "Connected",
                                        ),
                                    )
                                }
                                startDurationTimer()
                            }
                        }
                        "rejected" -> {
                            cleanupSessionState(banner = "${_uiState.value.activeCall.peerName} declined the call")
                        }
                        "missed" -> {
                            cleanupSessionState(banner = "No answer")
                        }
                        "ended" -> {
                            cleanupSessionState(banner = "Call ended")
                        }
                    }
                }
        }

        iceObserverJob?.cancel()
        iceObserverJob = viewModelScope.launch {
            callRepository.observeIceCandidates(callId)
                .catch { emit(emptyList()) }
                .collect { candidates ->
                    candidates.forEach { candidate ->
                        if (candidate.senderId != currentUserId && processedCandidateIds.add(candidate.id)) {
                            webRtcManager.addRemoteIceCandidate(
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                sdp = candidate.sdp,
                            )
                        }
                    }
                }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = viewModelScope.launch {
            delay(45_000L)
            if (_uiState.value.activeCall.callId == callId &&
                _uiState.value.activeCall.phase == CallUiPhase.OUTGOING_RINGING
            ) {
                cleanupSessionState(banner = "No answer")
                callRepository.endCall(callId, durationSeconds = 0L, markAsMissed = true)
            }
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.activeCall.phase == CallUiPhase.CONNECTED) {
                delay(1_000L)
                _uiState.update {
                    val next = it.activeCall.elapsedSeconds + 1L
                    it.copy(activeCall = it.activeCall.copy(elapsedSeconds = next))
                }
            }
        }
    }

    private fun cleanupSessionState(banner: String? = null) {
        activeCallObserverJob?.cancel()
        iceObserverJob?.cancel()
        durationTimerJob?.cancel()
        ringTimeoutJob?.cancel()
        stopRingtone()
        webRtcManager.release()
        processedCandidateIds.clear()
        _uiState.update {
            it.copy(
                activeCall = ActiveCallUiState(),
                statusBanner = banner ?: it.statusBanner,
            )
        }
    }

    private fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply { play() }
        }
    }

    private fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    override fun onCleared() {
        cleanupSessionState()
        super.onCleared()
    }

    class Factory(
        private val currentUserId: String,
        private val callRepository: CallRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CallCoordinatorViewModel(
                currentUserId = currentUserId,
                callRepository = callRepository,
                appContext = appContext,
            ) as T
        }
    }
}
