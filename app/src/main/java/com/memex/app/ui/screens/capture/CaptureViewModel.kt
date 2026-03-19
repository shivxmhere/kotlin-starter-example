package com.memex.app.ui.screens.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.ai.LLMService
import com.memex.app.ai.RAGService
import com.memex.app.ai.STTService
import com.memex.app.ai.VLMService
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import com.memex.app.util.CryptoUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

// ── UI state enums ────────────────────────────────────────────────────────────

enum class CaptureStatus {
    IDLE,        // camera/mic ready, waiting for user
    SCANNING,    // VLM / STT running
    PROCESSING,  // LLM summarize + tag generation
    SAVED,       // persisted to vault — triggers navigation
    ERROR        // error message available
}

/**
 * Full UI state for [CaptureScreen].
 *
 * @param status          Current pipeline stage.
 * @param extractedText   Raw text from VLM / STT / user input.
 * @param summary         LLM-generated summary (available during PROCESSING→SAVED).
 * @param tags            Suggested tags.
 * @param sha256Hash      Hash of rawContent.
 * @param errorMessage    Non-null when status == ERROR.
 * @param isListening     True while [STTService] is recording.
 * @param audioAmplitude  Live mic amplitude [0..1] for waveform display.
 */
data class CaptureUiState(
    val status         : CaptureStatus  = CaptureStatus.IDLE,
    val extractedText  : String         = "",
    val summary        : String         = "",
    val tags           : List<String>   = emptyList(),
    val sha256Hash     : String         = "",
    val errorMessage   : String?        = null,
    val isListening    : Boolean        = false,
    val audioAmplitude : Float          = 0f
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vlmService  : VLMService,
    private val sttService  : STTService,
    private val llmService  : LLMService,
    private val ragService  : RAGService,
    private val repository  : MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    // ── Camera capture ────────────────────────────────────────────────────────

    /**
     * Receive the photo path from CameraX ImageCapture, analyse via VLM,
     * then run LLM summarise + tag generation.
     *
     * @param imagePath Absolute path to the captured JPEG in cache.
     */
    fun captureFromCamera(imagePath: String) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = CaptureStatus.SCANNING)
            try {
                // Stage 1: VLM image analysis
                val vlmResult = vlmService.analyzeImageFull(imagePath)
                val rawText   = vlmResult.extractedText.ifBlank { "No text detected in image." }

                _uiState.value = _uiState.value.copy(
                    extractedText = rawText,
                    status        = CaptureStatus.PROCESSING
                )

                // Stage 2: LLM summarise + tags
                val summary  = llmService.summarize(rawText)
                val tags     = llmService.generateTags(rawText)
                val hash     = CryptoUtil.sha256(rawText)

                _uiState.value = _uiState.value.copy(
                    summary    = summary,
                    tags       = tags,
                    sha256Hash = hash,
                    status     = CaptureStatus.IDLE  // show result panel, not final SAVED yet
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status       = CaptureStatus.ERROR,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }

    // ── Voice capture ─────────────────────────────────────────────────────────

    /**
     * Start microphone recording. Call [stopVoiceCapture] to finalise.
     */
    fun startVoiceCapture() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = true, status = CaptureStatus.SCANNING)
            sttService.startListening()
            // Forward amplitude updates
            sttService.amplitudeFlow.collect { amp ->
                _uiState.value = _uiState.value.copy(audioAmplitude = amp)
            }
        }
    }

    /**
     * Stop recording, transcribe, then run LLM pipeline.
     */
    fun stopVoiceCapture() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = false, audioAmplitude = 0f)
            try {
                val transcript = sttService.stopAndGetTranscript()
                if (transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        status       = CaptureStatus.ERROR,
                        errorMessage = "No speech detected. Please try again."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    extractedText = transcript,
                    status        = CaptureStatus.PROCESSING
                )

                val summary = llmService.summarize(transcript, detectLanguage(transcript))
                val tags    = llmService.generateTags(transcript)
                val hash    = CryptoUtil.sha256(transcript)

                _uiState.value = _uiState.value.copy(
                    summary    = summary,
                    tags       = tags,
                    sha256Hash = hash,
                    status     = CaptureStatus.IDLE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status       = CaptureStatus.ERROR,
                    errorMessage = e.message ?: "Transcription failed"
                )
            }
        }
    }

    // ── Text capture ──────────────────────────────────────────────────────────

    /**
     * Process typed / pasted text through LLM pipeline.
     */
    fun captureFromText(text: String) {
        if (text.isBlank()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                extractedText = text,
                status        = CaptureStatus.PROCESSING
            )
            try {
                val lang    = detectLanguage(text)
                val summary = llmService.summarize(text, lang)
                val tags    = llmService.generateTags(text)
                val hash    = CryptoUtil.sha256(text)

                _uiState.value = _uiState.value.copy(
                    summary    = summary,
                    tags       = tags,
                    sha256Hash = hash,
                    status     = CaptureStatus.IDLE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status       = CaptureStatus.ERROR,
                    errorMessage = e.message ?: "Processing failed"
                )
            }
        }
    }

    // ── Save to vault ─────────────────────────────────────────────────────────

    /**
     * Persist the analysed memory to the encrypted Room DB and index it for RAG.
     *
     * @param type         Capture type (CAMERA / VOICE / TEXT).
     * @param thumbnailPath Optional path to thumbnail JPEG (CAMERA only).
     * @param audioPath     Optional path to audio recording (VOICE only).
     */
    fun saveToVault(
        type         : MemoryType,
        thumbnailPath: String? = null,
        audioPath    : String? = null
    ) {
        val state = _uiState.value
        if (state.extractedText.isBlank()) return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val memory = Memory(
                    id            = UUID.randomUUID().toString(),
                    type          = type,
                    rawContent    = state.extractedText,
                    summary       = state.summary.ifBlank { state.extractedText.take(120) },
                    tags          = state.tags,
                    language      = detectLanguage(state.extractedText),
                    sha256Hash    = state.sha256Hash.ifBlank { CryptoUtil.sha256(state.extractedText) },
                    createdAt     = System.currentTimeMillis(),
                    thumbnailPath = thumbnailPath,
                    audioPath     = audioPath
                )

                repository.saveMemory(memory).getOrThrow()

                _uiState.value = _uiState.value.copy(status = CaptureStatus.SAVED)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status       = CaptureStatus.ERROR,
                    errorMessage = "Vault save failed: ${e.message}"
                )
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        activeJob?.cancel()
        sttService.cancel()
        _uiState.value = CaptureUiState()
    }

    override fun onCleared() {
        super.onCleared()
        sttService.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Heuristic: if >25% chars are Devanagari, call it Hindi. */
    private fun detectLanguage(text: String): String {
        val devanagari = text.count { it in '\u0900'..'\u097F' }
        return if (text.isNotEmpty() && devanagari.toFloat() / text.length > 0.25f) "hi" else "en"
    }

    /** Write a Bitmap to the app's cache directory and return its path. */
    fun cacheImageFile(): File =
        File(context.cacheDir, "memex_cap_${System.currentTimeMillis()}.jpg")
}
