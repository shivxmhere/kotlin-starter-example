package com.memex.app.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.TTS.TTSOptions
import com.runanywhere.sdk.public.extensions.synthesize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-Speech service using the RunAnywhere SDK.
 *
 * SDK API (from TextToSpeechScreen.kt):
 *   `RunAnywhere.synthesize(text: String, options: TTSOptions): TtsOutput`
 *   where `TtsOutput.audioData: ByteArray` is a WAV file.
 *
 * Architecture:
 *   - [speak] synthesises the WAV via the SDK, then plays it via [AudioTrack].
 *   - [stop] cancels any active playback immediately.
 *   - [isSpeaking] lets the UI display a "speaking" state.
 *
 * Design note:
 *   We parse the WAV header ourselves to match the exact sample rate / bit-depth
 *   the Piper TTS model produces (typically 22050 Hz mono PCM-16).  This mirrors
 *   the approach in the starter's TextToSpeechScreen.kt.
 */
@Singleton
class TTSService @Inject constructor(
    private val manager: RunAnywhereManager
) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    @Volatile private var activeAudioTrack: AudioTrack? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Synthesise [text] to audio and play it through the device speaker.
     * Suspends until playback is complete (or [stop] is called).
     */
    suspend fun speak(text: String) {
        if (text.isBlank()) return

        _isSpeaking.value = true
        try {
            val output = withContext(Dispatchers.IO) {
                RunAnywhere.synthesize(text, TTSOptions())
            }
            playWav(output.audioData)
        } catch (e: Exception) {
            // Swallow — caller may check isSpeaking for completion
        } finally {
            _isSpeaking.value = false
        }
    }

    /**
     * Immediately stop any ongoing TTS playback.
     */
    fun stop() {
        activeAudioTrack?.stop()
        activeAudioTrack?.release()
        activeAudioTrack = null
        _isSpeaking.value = false
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Parse a WAV byte array and play via [AudioTrack].
     * Identical logic to the starter's TextToSpeechScreen.kt.
     */
    private suspend fun playWav(wavData: ByteArray) = withContext(Dispatchers.IO) {
        if (wavData.size < 44) return@withContext

        val buffer = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN)

        // Read fmt chunk (offset 20)
        buffer.position(20)
        /* audioFormat   = */ buffer.short  // 1 = PCM
        val numChannels  = buffer.short.toInt()
        val sampleRate   = buffer.int
        /* byteRate       = */ buffer.int
        /* blockAlign     = */ buffer.short
        val bitsPerSample = buffer.short.toInt()

        // Find the "data" chunk
        var dataOffset = 36
        while (dataOffset < wavData.size - 8) {
            if (wavData[dataOffset]     == 'd'.code.toByte() &&
                wavData[dataOffset + 1] == 'a'.code.toByte() &&
                wavData[dataOffset + 2] == 't'.code.toByte() &&
                wavData[dataOffset + 3] == 'a'.code.toByte()
            ) break
            dataOffset++
        }
        dataOffset += 8
        if (dataOffset >= wavData.size) return@withContext

        val pcmData = wavData.copyOfRange(dataOffset, wavData.size)

        val channelConfig = if (numChannels == 1)
            AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val audioFormatConst = if (bitsPerSample == 16)
            AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormatConst)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormatConst)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcmData.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeAudioTrack = track
        track.write(pcmData, 0, pcmData.size)
        track.play()

        // Wait for approximate playback duration
        val bytesPerSecond = sampleRate * numChannels * (bitsPerSample / 8)
        val durationMs = (pcmData.size.toLong() * 1000L) / bytesPerSecond
        Thread.sleep(durationMs + 150)

        track.stop()
        track.release()
        if (activeAudioTrack === track) activeAudioTrack = null
    }
}
