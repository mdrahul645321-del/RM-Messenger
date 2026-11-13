package com.example.core.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val liveAmplitudes: List<Int> = emptyList(),
)

data class RecordedVoiceResult(
    val base64Data: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int,
    val waveform: List<Int>,
)

data class PlaybackState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val progressFraction: Float = 0f,
    val currentPositionSeconds: Int = 0,
    val durationSeconds: Int = 0,
)

class VoiceRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var meterJob: Job? = null
    private var startedAtMs: Long = 0L
    private val collectedSamples = mutableListOf<Int>()

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun startRecording(): Result<Unit> {
        if (_state.value.isRecording) return Result.success(Unit)
        return try {
            val dir = File(context.cacheDir, "rm_media").apply { mkdirs() }
            val file = File(dir, "rm_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(24_000)
                setAudioSamplingRate(16_000)
                setMaxDuration(180_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            collectedSamples.clear()
            _state.value = RecordingState(isRecording = true, elapsedSeconds = 0, liveAmplitudes = emptyList())

            meterJob?.cancel()
            meterJob = scope.launch(Dispatchers.Main) {
                while (isActive && _state.value.isRecording) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = ((amp / 32767f) * 100f).toInt().coerceIn(8, 100)
                    collectedSamples.add(normalized)
                    val elapsed = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            elapsedSeconds = elapsed,
                            liveAmplitudes = collectedSamples.takeLast(28),
                        )
                    }
                    delay(120L)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            cleanupRecorder()
            Result.failure(e)
        }
    }

    fun stopAndReadRecording(): Result<RecordedVoiceResult> {
        meterJob?.cancel()
        val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(1)
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _state.value = RecordingState()

            if (file == null || !file.exists() || file.length() == 0L) {
                return Result.failure(IllegalStateException("Recording file was empty."))
            }
            val bytes = file.readBytes()
            file.delete()
            outputFile = null

            if (bytes.size > 680_000) {
                return Result.failure(IllegalArgumentException("Voice note exceeded max 680 KB size."))
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val waveform = downsampleWaveform(collectedSamples, targetBars = 28)
            Result.success(
                RecordedVoiceResult(
                    base64Data = base64,
                    fileSizeBytes = bytes.size.toLong(),
                    durationSeconds = elapsedSec,
                    waveform = waveform,
                ),
            )
        } catch (e: Exception) {
            cleanupRecorder()
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        cleanupRecorder()
    }

    private fun cleanupRecorder() {
        meterJob?.cancel()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
        _state.value = RecordingState()
    }

    private fun downsampleWaveform(samples: List<Int>, targetBars: Int): List<Int> {
        if (samples.isEmpty()) return List(targetBars) { 25 }
        if (samples.size <= targetBars) return samples
        val step = samples.size.toFloat() / targetBars.toFloat()
        return List(targetBars) { index ->
            val pos = (index * step).toInt().coerceIn(0, samples.lastIndex)
            samples[pos].coerceIn(10, 100)
        }
    }
}

class VoicePlayerManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null
    private var tempFile: File? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun togglePlay(
        messageId: String,
        base64Data: String,
        fallbackDurationSeconds: Int,
        fileExtension: String = "m4a",
    ): Result<Unit> {
        val current = _state.value
        val activePlayer = player
        if (current.playingMessageId == messageId && activePlayer != null) {
            return try {
                if (activePlayer.isPlaying) {
                    activePlayer.pause()
                    progressJob?.cancel()
                    _state.update { it.copy(isPlaying = false) }
                } else {
                    activePlayer.start()
                    _state.update { it.copy(isPlaying = true) }
                    startProgressLoop()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                release()
                Result.failure(e)
            }
        }

        release()
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val dir = File(context.cacheDir, "rm_media").apply { mkdirs() }
            val file = File(dir, "play_${messageId.take(16)}.$fileExtension")
            file.writeBytes(bytes)
            tempFile = file

            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    progressJob?.cancel()
                    _state.value = PlaybackState()
                }
                start()
            }
            player = mp
            val totalSec = (mp.duration / 1000).coerceAtLeast(fallbackDurationSeconds.coerceAtLeast(1))
            _state.value = PlaybackState(
                playingMessageId = messageId,
                isPlaying = true,
                progressFraction = 0f,
                currentPositionSeconds = 0,
                durationSeconds = totalSec,
            )
            startProgressLoop()
            Result.success(Unit)
        } catch (e: Exception) {
            release()
            Result.failure(e)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mp = player ?: break
                if (!mp.isPlaying) break
                val dur = mp.duration.coerceAtLeast(1)
                val pos = mp.currentPosition.coerceIn(0, dur)
                _state.update {
                    it.copy(
                        progressFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                        currentPositionSeconds = pos / 1000,
                        durationSeconds = (dur / 1000).coerceAtLeast(1),
                    )
                }
                delay(100L)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        tempFile?.delete()
        tempFile = null
        _state.value = PlaybackState()
    }
}
