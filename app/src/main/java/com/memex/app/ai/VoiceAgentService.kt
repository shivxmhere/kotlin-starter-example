package com.memex.app.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VoiceAgent.VoiceSessionConfig
import com.runanywhere.sdk.public.extensions.VoiceAgent.VoiceSessionEvent
import com.runanywhere.sdk.public.extensions.streamVoiceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full voice pipeline: STT → RAG + LLM → TTS.
 *
 * This service has two operating modes:
 *
 * **Mode 1 — SDK Voice Session (Recommended)**
 * Uses [RunAnywhere.streamVoiceSession] which orchestrates silence detection,
 * STT, LLM chat, and TTS synthesis internally.  The app only needs to provide
 * a `Flow<ByteArray>` of raw 16kHz PCM audio chunks.
 * SDK events: [VoiceSessionEvent.Transcribed], [VoiceSessionEvent.Responded],
 * [VoiceSessionEvent.TurnCompleted] (provides WAV audio for playback).
 *
 * **Mode 2 — Custom Pipeline (Fallback)**
 * Uses [STTService] + [RAGService] + [LLMService] + [TTSService] sequentially
 * with the 5-second VAD window approach.  Useful for text-only queries (no mic).
 *
 * UI integration: observe [stage], [transcript], and [answer] StateFlows.
 */
@Singleton
class VoiceAgentService @Inject constructor(
    private val sttService:  STTService,
    private val ttsService:  TTSService,
    private val llmService:  LLMService,
    private val ragService:  RAGService,
    private val manager:     RunAnywhereManager
) {

    // ── Pipeline stage ────────────────────────────────────────────────────────

    enum class PipelineStage { IDLE, LISTENING, PROCESSING, SPEAKING }

    private val _stage      = MutableStateFlow(PipelineStage.IDLE)
    val stage: StateFlow<PipelineStage> = _stage

    private val _transcript = MutableStateFlow("")
    /** Live transcript as STT emits it. */
    val transcript: StateFlow<String> = _transcript

    private val _answer     = MutableStateFlow("")
    /** Accumulates LLM response tokens. */
    val answer: StateFlow<String> = _answer

    private val _audioLevel = MutableStateFlow(0f)
    /** Normalised microphone amplitude [0..1] during listening phase. */
    val audioLevel: StateFlow<Float> = _audioLevel

    // ── Session management ────────────────────────────────────────────────────

    private var sessionJob: Job?           = null
    private var audioCaptureService: AudioCaptureService? = null

    // ── Mode 1: SDK Voice Session ─────────────────────────────────────────────

    /**
     * Start a full voice session using [RunAnywhere.streamVoiceSession].
     *
     * The session runs continuously until [stopVoiceSession] is called.
     * Silence is auto-detected by the SDK (1.5 s threshold).
     *
     * @param language  "en" for English, "hi" for Hindi (affects LLM system prompt,
     *                  but STT auto-detects language).
     * @param onTranscribed  Called when the user's speech is transcribed.
     * @param onAnswered     Called when the LLM finishes generating its response.
     */
    fun startVoiceSession(
        language: String = "en",
        onTranscribed: (String) -> Unit = {},
        onAnswered: (String) -> Unit = {}
    ) {
        if (_stage.value != PipelineStage.IDLE) return

        val capture = AudioCaptureService()
        audioCaptureService = capture

        val config = VoiceSessionConfig(
            silenceDuration = 1.5,
            speechThreshold = 0.1f,
            autoPlayTTS     = false,   // We handle playback via AudioTrack ourselves
            continuousMode  = true
        )

        sessionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _stage.value = PipelineStage.LISTENING
                _transcript.value = ""
                _answer.value     = ""

                RunAnywhere.streamVoiceSession(capture.startCapture(), config)
                    .collect { event ->
                        when (event) {
                            is VoiceSessionEvent.Started     -> _stage.value = PipelineStage.LISTENING
                            is VoiceSessionEvent.Listening   -> _audioLevel.value = event.audioLevel
                            is VoiceSessionEvent.SpeechStarted -> { /* no-op */ }
                            is VoiceSessionEvent.Processing  -> {
                                _stage.value = PipelineStage.PROCESSING
                                _audioLevel.value = 0f
                            }
                            is VoiceSessionEvent.Transcribed -> {
                                _transcript.value = event.text
                                onTranscribed(event.text)
                            }
                            is VoiceSessionEvent.Responded   -> {
                                _answer.value = event.text
                                onAnswered(event.text)
                            }
                            is VoiceSessionEvent.Speaking    -> _stage.value = PipelineStage.SPEAKING
                            is VoiceSessionEvent.TurnCompleted -> {
                                event.audio?.let { wav -> playWav(wav) }
                                _stage.value = PipelineStage.LISTENING
                                _audioLevel.value = 0f
                            }
                            is VoiceSessionEvent.Stopped     -> {
                                _stage.value = PipelineStage.IDLE
                                _audioLevel.value = 0f
                            }
                            is VoiceSessionEvent.Error       -> {
                                _stage.value = PipelineStage.IDLE
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Normal cancellation — no-op
            } finally {
                _stage.value = PipelineStage.IDLE
                _audioLevel.value = 0f
                capture.stopCapture()
            }
        }
    }

    /** Stop an active SDK voice session. */
    fun stopVoiceSession() {
        sessionJob?.cancel()
        sessionJob = null
        audioCaptureService?.stopCapture()
        audioCaptureService = null
        _stage.value = PipelineStage.IDLE
        _audioLevel.value = 0f
    }

    // ── Mode 2: Custom Pipeline (text query, no microphone) ──────────────────

    /**
     * Process a text query through the RAG + LLM pipeline (no STT, no TTS).
     * Useful for typed queries on [VoiceQueryScreen].
     *
     * @param query    The user's typed question.
     * @param language "en" or "hi".
     */
    suspend fun processTextQuery(query: String, language: String = "en") {
        if (query.isBlank()) return

        _stage.value  = PipelineStage.PROCESSING
        _answer.value = ""

        try {
            val context = ragService.search(query)
            llmService.answerWithContext(query, context, language).collect { token ->
                _answer.value += token
            }
        } finally {
            _stage.value = PipelineStage.IDLE
        }
    }

    /**
     * Record audio for up to [listenMs] ms (or until [STTService.stopAndGetTranscript]
     * is called), transcribe, run RAG+LLM, then speak the answer.
     *
     * This is the "Mode 2" manual pipeline; prefer [startVoiceSession] for
     * a better UX.
     *
     * @param listenMs  Maximum listen window (default 5 s).
     * @param language  "en" or "hi".
     */
    suspend fun processVoiceQuery(listenMs: Long = 5_000L, language: String = "en") {
        if (_stage.value != PipelineStage.IDLE) return

        // Stage 1: STT
        _stage.value = PipelineStage.LISTENING
        _transcript.value = ""
        _answer.value     = ""
        sttService.startListening()

        // Observe amplitude while listening
        val ampJob = CoroutineScope(Dispatchers.Default).launch {
            sttService.amplitudeFlow.collect { _audioLevel.value = it }
        }

        delay(listenMs)

        val transcript = sttService.stopAndGetTranscript()
        ampJob.cancel()
        _audioLevel.value = 0f
        _transcript.value = transcript

        if (transcript.isEmpty()) {
            _stage.value = PipelineStage.IDLE
            return
        }

        // Stage 2: RAG retrieval + LLM answer
        _stage.value = PipelineStage.PROCESSING
        val context = ragService.search(transcript)
        llmService.answerWithContext(transcript, context, language).collect { token ->
            _answer.value += token
        }

        // Stage 3: TTS playback
        _stage.value = PipelineStage.SPEAKING
        ttsService.speak(_answer.value)
        _stage.value = PipelineStage.IDLE
    }

    // ── Audio helpers ─────────────────────────────────────────────────────────

    /**
     * Play a WAV [ByteArray] via [AudioTrack].
     * Assumes 22050 Hz mono PCM-16 (Piper TTS default).
     */
    private suspend fun playWav(wavData: ByteArray) = withContext(Dispatchers.IO) {
        if (wavData.size < 44) return@withContext

        val headerSize = if (
            wavData[0] == 'R'.code.toByte() &&
            wavData[1] == 'I'.code.toByte()
        ) 44 else 0
        val pcmData  = wavData.copyOfRange(headerSize, wavData.size)
        val sampleRate = 22_050

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcmData.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmData, 0, pcmData.size)
        track.play()

        val durationMs = (pcmData.size.toLong() * 1000L) / (sampleRate * 2)
        delay(durationMs + 150)

        track.stop()
        track.release()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal: raw audio capture for SDK voice session
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Minimal audio capture that emits 100 ms PCM chunks as a [Flow<ByteArray>].
 * Required by [RunAnywhere.streamVoiceSession].
 */
private class AudioCaptureService {

    @Volatile private var isCapturing = false
    private var audioRecord: AudioRecord? = null

    companion object {
        const val SAMPLE_RATE   = 16_000
        const val CHUNK_MS      = 100
    }

    fun startCapture(): Flow<ByteArray> = callbackFlow {
        val bufferSize  = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val chunkSize = (SAMPLE_RATE * 2 * CHUNK_MS) / 1000

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(bufferSize, chunkSize * 2)
        ).also { if (it.state != AudioRecord.STATE_INITIALIZED) close(IllegalStateException("AudioRecord failed")) }

        audioRecord?.startRecording()
        isCapturing = true

        val readJob = launch(Dispatchers.IO) {
            val buf = ByteArray(chunkSize)
            while (isActive && isCapturing) {
                val read = audioRecord?.read(buf, 0, chunkSize) ?: -1
                if (read > 0) trySend(buf.copyOf(read))
            }
        }

        awaitClose {
            readJob.cancel()
            stopCapture()
        }
    }

    fun stopCapture() {
        isCapturing = false
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        audioRecord = null
    }
}
