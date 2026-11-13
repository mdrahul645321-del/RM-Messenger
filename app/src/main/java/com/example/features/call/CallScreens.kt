package com.example.features.call

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.firebase.FirestoreCallSessionDocument
import com.example.core.model.CallType
import com.example.ui.components.AvatarCircle
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavyPrimaryContainer
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.SkyBlueBright
import com.example.ui.theme.SkyBlueSecondary
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun IncomingCallScreen(
    incomingCall: FirestoreCallSessionDocument,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val isVideo = incomingCall.callType == "video"
    val pulseTransition = rememberInfiniteTransition(label = "incoming_pulse")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_scale",
    )
    val ringAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_alpha",
    )

    BackHandler {
        // Prevent accidental dismiss; caller must Accept or Decline
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyDeep, NavyPrimary, NavyPrimaryContainer),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("incoming_call_screen"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = SkyBlueSecondary.copy(alpha = 0.22f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = SkyBlueBright,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isVideo) "Incoming RM Video Call" else "Incoming RM Audio Call",
                        style = MaterialTheme.typography.labelLarge,
                        color = SkyBlueBright,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .clip(CircleShape)
                        .background(SkyBlueBright),
                )
                AvatarCircle(
                    initials = incomingCall.callerName.take(2),
                    size = 112.dp,
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = incomingCall.callerName.ifBlank { "RM User" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (incomingCall.callerUsername.isNotBlank()) {
                Text(
                    text = "@${incomingCall.callerUsername}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyBlueBright,
                )
            }
            Text(
                text = "Ringing…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onReject)
                        .testTag("incoming_reject_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Decline call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Text("Decline", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = OnlineEmerald,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAccept)
                        .testTag("incoming_accept_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = "Accept call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Text("Accept", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    callState: ActiveCallUiState,
    eglContext: EglBase.Context?,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
) {
    val isVideo = callState.callType == CallType.VIDEO
    val statusText = when (callState.phase) {
        CallUiPhase.CONNECTED -> formatCallDuration(callState.elapsedSeconds)
        else -> callState.statusLabel.ifBlank { "Calling…" }
    }

    BackHandler {
        onEndCall()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyDeep, NavyPrimary, NavyPrimaryContainer),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("active_call_screen"),
    ) {
        if (isVideo && eglContext != null && remoteVideoTrack != null) {
            WebRtcVideoSurface(
                eglContext = eglContext,
                videoTrack = remoteVideoTrack,
                mirror = false,
                isOverlay = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarCircle(
                    initials = callState.peerName.take(2),
                    size = 118.dp,
                )
                Text(
                    text = callState.peerName.ifBlank { "RM User" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (callState.peerUsername.isNotBlank()) {
                    Text(
                        text = "@${callState.peerUsername}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyBlueBright,
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("active_call_timer"),
                )
            }
        }

        // Top HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$statusText • ${callState.iceConnectionLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SkyBlueBright,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.35f),
            ) {
                Text(
                    text = if (isVideo) "Video" else "Audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // Local camera PiP preview
        if (isVideo && callState.isCameraEnabled && eglContext != null && localVideoTrack != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 74.dp, end = 16.dp)
                    .width(108.dp)
                    .height(152.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                WebRtcVideoSurface(
                    eglContext = eglContext,
                    videoTrack = localVideoTrack,
                    mirror = callState.isFrontCamera,
                    isOverlay = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Bottom Controls Deck
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallControlButton(
                    icon = if (callState.isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (callState.isMicMuted) "Unmute" else "Mute",
                    active = callState.isMicMuted,
                    onClick = onToggleMute,
                    testTag = "call_mute_button",
                )

                if (isVideo) {
                    CallControlButton(
                        icon = if (callState.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = if (callState.isCameraEnabled) "Cam On" else "Cam Off",
                        active = !callState.isCameraEnabled,
                        onClick = onToggleCamera,
                        testTag = "call_camera_button",
                    )
                    CallControlButton(
                        icon = Icons.Rounded.FlipCameraAndroid,
                        label = "Flip",
                        active = false,
                        onClick = onSwitchCamera,
                        testTag = "call_flip_camera_button",
                    )
                }

                CallControlButton(
                    icon = if (callState.isSpeakerOn) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.Rounded.PhoneInTalk
                    },
                    label = if (callState.isSpeakerOn) "Speaker" else "Earpiece",
                    active = callState.isSpeakerOn,
                    onClick = onToggleSpeaker,
                    testTag = "call_speaker_button",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEndCall)
                            .testTag("call_end_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "End call",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.18f),
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag(testTag),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) NavyPrimary else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun WebRtcVideoSurface(
    eglContext: EglBase.Context,
    videoTrack: VideoTrack,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val rendererHolder = androidx.compose.runtime.remember { mutableListOf<SurfaceViewRenderer>() }

    DisposableEffect(videoTrack) {
        onDispose {
            rendererHolder.forEach { renderer ->
                runCatching { videoTrack.removeSink(renderer) }
                runCatching { renderer.release() }
            }
            rendererHolder.clear()
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(isOverlay)
                videoTrack.addSink(this)
                rendererHolder.add(this)
            }
        },
        update = { view ->
            view.setMirror(mirror)
        },
        modifier = modifier,
    )
}

private fun formatCallDuration(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return "%02d:%02d".format(mins, secs)
}
