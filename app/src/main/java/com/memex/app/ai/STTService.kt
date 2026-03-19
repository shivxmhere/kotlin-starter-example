package com.memex.app.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.transcribe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech-to-Text service using the RunAnywhere SDK.
 *
 * SDK API (from SpeechToTextScreen.kt):
 *   `RunAnywhere.transcribe(audioData: ByteArray): String`
 *   Audio must be 16 kHz mono PCM-16 raw bytes.
 *
 * Architecture:
 *   - This service owns an [AudioRecord] internally and manages its lifecycle.
 *   - Callers call [startListening] to begin buffering, then [stopAndGetTranscript]
 *     to stop recording, ship the raw PCM to the SDK, and return the transcript.
 *   - [amplitudeFlow] gives the UI a 0-1 normalised amplitude for waveform display.
 *
 * Permission:
 *   The caller must ensure RECORD_AUDIO permission before calling [startListening].
 *   [hasPermission] is a convenience check.
 */
@Singleton
class STTService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: RunAnywhereManager
) {

    // ── State flows ───────────────────────────────────────────────────────────

    private val _isListening  = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _liveTranscript = MutableStateFlow("")
    /** Partial transcript updated while recording (empty until stopAndGetTranscript). */
    val transcriptFlow: StateFlow<String> = _liveTranscript

    private val _amplitudeFlow  = MutableStateFlow(0f)
    /** Normalised amplitude [0f, 1f] — update every ~50 ms while recording. */
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow

    // ── Internal recording state ──────────────────────────────────────────────

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()

    companion object {
        const val SAMPLE_RATE    = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if RECORD_AUDIO permission is granted. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Start buffering microphone audio.
     * Launches a background thread that continuously reads 50 ms chunks and:
     *  - appends them to [audioBuffer]
     *  - computes RMS amplitude and pushes to [_amplitudeFlow]
     *
     * Call [stopAndGetTranscript] to end recording and transcribe.
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (_isListening.value) return@withContext

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ).coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 4
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return@withContext
        }

        audioBuffer.reset()
        _liveTranscript.value = ""
        _amplitudeFlow.value  = 0f
        audioRecord?.startRecording()
        isRecording        = true
        _isListening.value = true

        // Read audio chunks in a dedicated thread
        Thread {
            val chunk = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(chunk, 0, chunk.size) ?: 0
                if (read > 0) {
                    synchronized(audioBuffer) {
                        audioBuffer.write(chunk, 0, read)
                    }
                    // Compute RMS for amplitude visualisation
                    val rms = computeRms(chunk, read)
                    _amplitudeFlow.value = rms
                }
            }
        }.start()
    }

    /**
     * Stop the microphone, ship the recorded PCM to the SDK, and return the
     * transcribed text.
     *
     * @return Transcript string (may be empty if no speech detected).
     */
    suspend fun stopAndGetTranscript(): String = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext ""

        isRecording        = false
        _isListening.value = false
        _amplitudeFlow.value = 0f

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmBytes: ByteArray
        synchronized(audioBuffer) {
            pcmBytes = audioBuffer.toByteArray()
            audioBuffer.reset()
        }

        if (pcmBytes.isEmpty()) return@withContext ""

        val transcript = runCatching {
            RunAnywhere.transcribe(pcmBytes)
        }.getOrElse { "" }

        _liveTranscript.value = transcript
        transcript
    }

    /** Force-stop without transcribing (e.g. on screen dismiss). */
    fun cancel() {
        isRecording        = false
        _isListening.value = false
        _amplitudeFlow.value = 0f
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioBuffer.reset()
        _liveTranscript.value = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Compute normalised RMS amplitude from a PCM-16 byte array chunk. */
    private fun computeRms(bytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = (bytes[i].toInt() or (bytes[i + 1].toInt() shl 8)).toShort().toFloat()
            sumSquares += sample * sample
            i += 2
        }
        val samples = length / 2
        val rms = Math.sqrt(sumSquares / samples).toFloat()
        // Normalise: 16-bit max = 32768
        return (rms / 32768f).coerceIn(0f, 1f)
    }
}
