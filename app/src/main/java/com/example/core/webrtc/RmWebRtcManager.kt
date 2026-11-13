package com.example.core.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class RmWebRtcManager(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: AudioOrVideoTrackHolder? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var remoteAnswerApplied = false
    private var previousAudioMode: Int? = null
    private var isFrontCameraActive = true

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _eglContext = MutableStateFlow<EglBase.Context?>(null)
    val eglContextFlow: StateFlow<EglBase.Context?> = _eglContext.asStateFlow()

    private class AudioOrVideoTrackHolder(val videoTrack: VideoTrack)

    fun startSession(
        isVideo: Boolean,
        isCaller: Boolean,
        remoteOfferSdp: String?,
        onLocalSdpReady: (String) -> Unit,
        onIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
        onPeerStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
    ): Result<Unit> {
        release()
        return runCatching {
            configureAudioForCall(speakerOn = isVideo)

            val egl = EglBase.create()
            eglBase = egl
            _eglContext.value = egl.eglBaseContext

            val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            val factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            peerConnectionFactory = factory

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val pc = factory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        if (candidate != null) {
                            onIceCandidate(
                                candidate.sdpMid ?: "0",
                                candidate.sdpMLineIndex,
                                candidate.sdp,
                            )
                        }
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                    override fun onAddStream(stream: MediaStream?) {
                        stream?.videoTracks?.firstOrNull()?.let { _remoteVideoTrack.value = it }
                    }

                    override fun onRemoveStream(stream: MediaStream?) = Unit
                    override fun onDataChannel(channel: DataChannel?) = Unit
                    override fun onRenegotiationNeeded() = Unit
                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            _remoteVideoTrack.value = track
                        }
                    }

                    override fun onTrack(transceiver: RtpTransceiver?) {
                        val track = transceiver?.receiver?.track()
                        if (track is VideoTrack) {
                            _remoteVideoTrack.value = track
                        }
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        if (newState != null) {
                            onPeerStateChanged(newState)
                        }
                    }
                },
            ) ?: error("Unable to initialize WebRTC PeerConnection.")
            peerConnection = pc

            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            val audioSource = factory.createAudioSource(audioConstraints)
            localAudioSource = audioSource
            val audioTrack = factory.createAudioTrack("RM_AUDIO_0", audioSource)
            audioTrack.setEnabled(true)
            localAudioTrack = audioTrack
            pc.addTrack(audioTrack, listOf("RM_MEDIA_STREAM"))

            if (isVideo) {
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    val texHelper = SurfaceTextureHelper.create("RmCaptureThread", egl.eglBaseContext)
                    surfaceTextureHelper = texHelper
                    val videoSource = factory.createVideoSource(capturer.isScreencast)
                    localVideoSource = videoSource
                    capturer.initialize(texHelper, appContext, videoSource.capturerObserver)
                    runCatching { capturer.startCapture(720, 1280, 24) }
                    val videoTrack = factory.createVideoTrack("RM_VIDEO_0", videoSource)
                    videoTrack.setEnabled(true)
                    localVideoTrack = AudioOrVideoTrackHolder(videoTrack)
                    _localVideoTrack.value = videoTrack
                    pc.addTrack(videoTrack, listOf("RM_MEDIA_STREAM"))
                }
            }

            val sdpConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(
                    MediaConstraints.KeyValuePair(
                        "OfferToReceiveVideo",
                        if (isVideo) "true" else "false",
                    ),
                )
            }

            if (isCaller) {
                pc.createOffer(
                    object : SimpleSdpObserver() {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc == null) return
                            pc.setLocalDescription(
                                object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        onLocalSdpReady(desc.description)
                                    }
                                },
                                desc,
                            )
                        }
                    },
                    sdpConstraints,
                )
            } else if (!remoteOfferSdp.isNullOrBlank()) {
                val offerDesc = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
                pc.setRemoteDescription(
                    object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            pc.createAnswer(
                                object : SimpleSdpObserver() {
                                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                                        if (answerDesc == null) return
                                        pc.setLocalDescription(
                                            object : SimpleSdpObserver() {
                                                override fun onSetSuccess() {
                                                    onLocalSdpReady(answerDesc.description)
                                                }
                                            },
                                            answerDesc,
                                        )
                                    }
                                },
                                sdpConstraints,
                            )
                        }
                    },
                    offerDesc,
                )
            }
        }
    }

    fun applyRemoteAnswer(answerSdp: String) {
        if (remoteAnswerApplied || answerSdp.isBlank()) return
        val pc = peerConnection ?: return
        remoteAnswerApplied = true
        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(SimpleSdpObserver(), answerDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val pc = peerConnection ?: return
        runCatching {
            pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
        }
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.videoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(
            object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    isFrontCameraActive = isFrontCamera
                }

                override fun onCameraSwitchError(errorDescription: String?) = Unit
            },
        )
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speaker = manager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        manager.setCommunicationDevice(speaker)
                    }
                } else {
                    val earpiece = manager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        manager.setCommunicationDevice(earpiece)
                    } else {
                        manager.clearCommunicationDevice()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = speakerOn
            }
        }
    }

    fun release() {
        remoteAnswerApplied = false
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _eglContext.value = null

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { localVideoTrack?.videoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localVideoSource?.dispose() }
        localVideoSource = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { localAudioSource?.dispose() }
        localAudioSource = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null

        runCatching { eglBase?.release() }
        eglBase = null

        restoreAudio()
    }

    private fun configureAudioForCall(speakerOn: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            if (previousAudioMode == null) {
                previousAudioMode = manager.mode
            }
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(speakerOn)
        }
    }

    private fun restoreAudio() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = false
            }
            previousAudioMode?.let { manager.mode = it }
            previousAudioMode = null
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val deviceNames = enumerator.deviceNames
        val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val backCamera = deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
        val chosen = frontCamera ?: backCamera ?: return null
        isFrontCameraActive = enumerator.isFrontFacing(chosen)
        return enumerator.createCapturer(chosen, null)
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
