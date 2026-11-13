package com.example.core.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.core.model.MessageType
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedAttachment(
    val messageType: MessageType,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int = 0,
    val waveform: List<Int> = emptyList(),
    val base64Data: String,
    val thumbnailBase64: String? = null,
)

object MediaAttachmentHelper {
    private const val MAX_INLINE_FIRESTORE_BYTES = 650_000L
    private const val MAX_IMAGE_DIMENSION = 1280

    suspend fun prepareImageAttachment(context: Context, uri: Uri): Result<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val fileName = queryDisplayName(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                    boundsOpts.outHeight / sampleSize > MAX_IMAGE_DIMENSION
                ) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalArgumentException("Could not decode selected image."))

                val scaled = scaleBitmapDown(bitmap, MAX_IMAGE_DIMENSION)
                var quality = 85
                var compressed: ByteArray
                do {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    compressed = baos.toByteArray()
                    quality -= 12
                } while (compressed.size > 440_000 && quality >= 30)

                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()

                if (compressed.size > MAX_INLINE_FIRESTORE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Image is too large after compression (${compressed.size / 1024} KB)."),
                    )
                }

                Result.success(
                    PreparedAttachment(
                        messageType = MessageType.IMAGE,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        fileSizeBytes = compressed.size.toLong(),
                        base64Data = Base64.encodeToString(compressed, Base64.NO_WRAP),
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun prepareVideoAttachment(context: Context, uri: Uri): Result<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val fileName = queryDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}.mp4"
                val mimeType = resolver.getType(uri) ?: "video/mp4"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected video."))

                if (bytes.size > MAX_INLINE_FIRESTORE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Video is ${bytes.size / 1024} KB. Inline Cloud Firestore limit is 650 KB (larger videos require Firebase Storage in Firebase Console).",
                        ),
                    )
                }

                var durationSec = 0
                var thumbBase64: String? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    durationSec = (durMs / 1000L).toInt().coerceAtLeast(1)
                    val frame = retriever.getFrameAtTime(0L)
                    if (frame != null) {
                        val thumb = scaleBitmapDown(frame, 480)
                        val baos = ByteArrayOutputStream()
                        thumb.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        thumbBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        if (thumb !== frame) thumb.recycle()
                        frame.recycle()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }

                Result.success(
                    PreparedAttachment(
                        messageType = MessageType.VIDEO,
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSizeBytes = bytes.size.toLong(),
                        durationSeconds = durationSec,
                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        thumbnailBase64 = thumbBase64,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun prepareFileAttachment(
        context: Context,
        uri: Uri,
        messageType: MessageType,
    ): Result<PreparedAttachment> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val defaultName = if (messageType == MessageType.AUDIO) {
                "audio_${System.currentTimeMillis()}.mp3"
            } else {
                "document_${System.currentTimeMillis()}.pdf"
            }
            val fileName = queryDisplayName(context, uri) ?: defaultName
            val mimeType = resolver.getType(uri)
                ?: if (messageType == MessageType.AUDIO) "audio/mpeg" else "application/octet-stream"

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected file."))

            if (bytes.size > MAX_INLINE_FIRESTORE_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "File '$fileName' is ${bytes.size / 1024} KB. Inline Cloud Firestore limit is 650 KB (larger files require Firebase Storage in Firebase Console).",
                    ),
                )
            }

            var durationSec = 0
            if (messageType == MessageType.AUDIO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    durationSec = (durMs / 1000L).toInt().coerceAtLeast(1)
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }
            }

            val waveform = if (messageType == MessageType.AUDIO) {
                List(24) { idx -> 24 + ((idx * 17) % 66) }
            } else {
                emptyList()
            }

            Result.success(
                PreparedAttachment(
                    messageType = messageType,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = bytes.size.toLong(),
                    durationSeconds = durationSec,
                    waveform = waveform,
                    base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openAttachmentWithSystemViewer(
        context: Context,
        fileName: String,
        mimeType: String,
        base64Data: String,
    ): Result<Unit> {
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val dir = File(context.cacheDir, "rm_media").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment.bin" }
            val file = File(dir, safeName)
            file.writeBytes(bytes)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open $safeName"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAttachmentToUri(
        context: Context,
        targetUri: Uri,
        base64Data: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(bytes)
            } ?: return@withContext Result.failure(IllegalStateException("Could not open destination file."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeTempVideoFile(context: Context, messageId: String, base64Data: String): File? {
        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val dir = File(context.cacheDir, "rm_media").apply { mkdirs() }
            val file = File(dir, "video_${messageId.take(16)}.mp4")
            file.writeBytes(bytes)
            file
        }.getOrNull()
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
    }

    private fun scaleBitmapDown(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}
