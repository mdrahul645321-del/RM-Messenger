package com.example.features.chat

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.media.MediaAttachmentHelper
import com.example.core.media.PlaybackState
import com.example.core.media.RecordingState
import com.example.core.model.ChatMessage
import com.example.core.model.MessageType
import com.example.ui.theme.SkyBlueBright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VoiceRecordingBar(
    recordingState: RecordingState,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec_dot_alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("voice_cancel_button"),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Cancel voice recording",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(dotAlpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Text(
                    text = MediaAttachmentHelper.formatDuration(recordingState.elapsedSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WaveformBarsCanvas(
                    amplitudes = recordingState.liveAmplitudes.ifEmpty { List(18) { 18 } },
                    progressFraction = 1f,
                    activeColor = MaterialTheme.colorScheme.error,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onSend)
                .testTag("voice_send_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send voice message",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun AttachmentPickerSheetContent(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickDocument: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Share Media & Files",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AttachmentOptionCard(
                label = "Photo",
                subtitle = "Gallery picker",
                icon = Icons.Rounded.Image,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onPickImage,
                modifier = Modifier
                    .weight(1f)
                    .testTag("attach_photo_option"),
            )
            AttachmentOptionCard(
                label = "Video",
                subtitle = "Compact clip",
                icon = Icons.Rounded.Videocam,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onPickVideo,
                modifier = Modifier
                    .weight(1f)
                    .testTag("attach_video_option"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AttachmentOptionCard(
                label = "Audio File",
                subtitle = "MP3 / M4A / WAV",
                icon = Icons.Rounded.AudioFile,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onPickAudio,
                modifier = Modifier
                    .weight(1f)
                    .testTag("attach_audio_option"),
            )
            AttachmentOptionCard(
                label = "Document",
                subtitle = "PDF / TXT / File",
                icon = Icons.Rounded.Description,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onPickDocument,
                modifier = Modifier
                    .weight(1f)
                    .testTag("attach_document_option"),
            )
        }
        Text(
            text = "Photos compress automatically. Videos, audio files, and documents up to 650 KB sync directly via Cloud Firestore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AttachmentOptionCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VoiceAudioMessageContent(
    message: ChatMessage,
    playbackState: PlaybackState,
    textColor: Color,
    onTogglePlay: () -> Unit,
    onSave: () -> Unit,
) {
    val isThisPlaying = playbackState.playingMessageId == message.id && playbackState.isPlaying
    val isThisLoaded = playbackState.playingMessageId == message.id
    val progress = if (isThisLoaded) playbackState.progressFraction else 0f
    val activeSec = if (isThisLoaded) playbackState.currentPositionSeconds else message.durationSeconds
    val bars = message.waveform.ifEmpty { List(24) { idx -> 24 + ((idx * 17) % 62) } }

    Column(
        modifier = Modifier.widthIn(min = 215.dp, max = 270.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.messageType == MessageType.AUDIO && !message.fileName.isNullOrBlank()) {
            Text(
                text = message.fileName,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (message.isFromMe) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !message.mediaData.isNullOrBlank(), onClick = onTogglePlay)
                    .testTag("audio_play_${message.id}"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isThisPlaying) "Pause audio" else "Play audio",
                        tint = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WaveformBarsCanvas(
                    amplitudes = bars,
                    progressFraction = progress,
                    activeColor = if (message.isFromMe) SkyBlueBright else MaterialTheme.colorScheme.secondary,
                    inactiveColor = textColor.copy(alpha = 0.30f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = MediaAttachmentHelper.formatDuration(activeSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.85f),
                    )
                    if (message.fileSizeBytes > 0L) {
                        Text(
                            text = MediaAttachmentHelper.formatFileSize(message.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            IconButton(
                onClick = onSave,
                enabled = !message.mediaData.isNullOrBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("media_save_${message.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Save audio",
                    tint = textColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun ImageMessageContent(
    message: ChatMessage,
    textColor: Color,
    onOpenPreview: () -> Unit,
) {
    val bitmap by rememberBase64ImageBitmap(message.id, message.mediaData)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 260.dp)
                .height(175.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(textColor.copy(alpha = 0.1f))
                .clickable(enabled = bitmap != null, onClick = onOpenPreview)
                .testTag("image_preview_thumb_${message.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = message.fileName ?: "Photo attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "Loading photo",
                    tint = textColor.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        if (message.text.isNotBlank() && message.text != "Photo") {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}

@Composable
fun VideoMessageContent(
    message: ChatMessage,
    textColor: Color,
    onOpenVideo: () -> Unit,
    onSave: () -> Unit,
) {
    val thumb by rememberBase64ImageBitmap("${message.id}_thumb", message.thumbnailData)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .widthIn(min = 190.dp, max = 260.dp)
                .height(160.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFF0A192F))
                .clickable(enabled = !message.mediaData.isNullOrBlank(), onClick = onOpenVideo)
                .testTag("video_preview_thumb_${message.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!,
                    contentDescription = message.fileName ?: "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier.size(50.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play video",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                ) {
                    Text(
                        text = MediaAttachmentHelper.formatDuration(message.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${message.fileName ?: "Video"} • ${MediaAttachmentHelper.formatFileSize(message.fileSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Save video",
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun DocumentMessageContent(
    message: ChatMessage,
    textColor: Color,
    onOpenDocument: () -> Unit,
    onSaveDocument: () -> Unit,
) {
    val isPdf = message.mimeType?.contains("pdf", ignoreCase = true) == true ||
        message.fileName?.endsWith(".pdf", ignoreCase = true) == true
    val cardBg = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = cardBg,
            modifier = Modifier
                .widthIn(min = 210.dp, max = 270.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = !message.mediaData.isNullOrBlank(), onClick = onOpenDocument),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                            contentDescription = "Document",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.fileName ?: message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = MediaAttachmentHelper.formatFileSize(message.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onOpenDocument,
                enabled = !message.mediaData.isNullOrBlank(),
                modifier = Modifier.testTag("doc_open_${message.id}"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Open", color = textColor)
            }
            TextButton(
                onClick = onSaveDocument,
                enabled = !message.mediaData.isNullOrBlank(),
                modifier = Modifier.testTag("doc_save_${message.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Save", color = textColor)
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val bitmap by rememberBase64ImageBitmap("full_${message.id}", message.mediaData)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = message.fileName ?: "Full photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("preview_close_button"),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close preview", tint = Color.White)
                }
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("preview_save_button"),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun VideoPreviewDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val tempFile = remember(message.id, message.mediaData) {
        val data = message.mediaData
        if (data.isNullOrBlank()) null else MediaAttachmentHelper.writeTempVideoFile(context, message.id, data)
    }

    DisposableEffect(tempFile) {
        onDispose {
            tempFile?.delete()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (tempFile != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(tempFile))
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("video_preview_close"),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close video", tint = Color.White)
                }
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("video_preview_save"),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun WaveformBarsCanvas(
    amplitudes: List<Int>,
    progressFraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val count = amplitudes.size.coerceAtLeast(1)
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(2.5f)
        val maxHeight = size.height
        amplitudes.forEachIndexed { index, raw ->
            val ratio = (raw.coerceIn(10, 100)) / 100f
            val barHeight = (maxHeight * ratio).coerceAtLeast(4f)
            val top = (maxHeight - barHeight) / 2f
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            val barProgress = (index + 1).toFloat() / count.toFloat()
            val color = if (barProgress <= progressFraction && progressFraction > 0f) activeColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun rememberBase64ImageBitmap(key: String, base64: String?): androidx.compose.runtime.State<ImageBitmap?> {
    return produceState<ImageBitmap?>(initialValue = null, key1 = key, key2 = base64) {
        value = if (base64.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}
