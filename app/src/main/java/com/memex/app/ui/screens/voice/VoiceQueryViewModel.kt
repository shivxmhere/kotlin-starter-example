package com.memex.app.ui.screens.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.ai.STTService
import com.memex.app.ai.VoiceAgentService
import com.memex.app.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

/**
 * Unified UI state for [VoiceQueryScreen].
 *
 * @param stage          Current pipeline stage (mirrors [VoiceAgentService.PipelineStage]).
 * @param transcript     Live or final STT transcript.
 * @param answer         Accumulated LLM answer tokens.
 * @param language       Active query language: "en" or "hi".
 * @param audioAmplitude Normalised microphone amplitude [0..1] for the waveform.
 * @param memoryCount    Total memories indexed — displayed in the cost footer.
 * @param errorMessage   Non-null if something went wrong.
 */
data class VoiceQueryUiState(
    val stage         : VoiceAgentService.PipelineStage = VoiceAgentService.PipelineStage.IDLE,
    val transcript    : String  = "",
    val answer        : String  = "",
    val language      : String  = "en",
    val audioAmplitude: Float   = 0f,
    val memoryCount   : Int     = 0,
    val errorMessage  : String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class VoiceQueryViewModel @Inject constructor(
    private val voiceAgent : VoiceAgentService,
    private val sttService : STTService,
    private val repository : MemoryRepository
) : ViewModel() {

    private val _language = MutableStateFlow("en")

    // Total memory count for the footer badge
    private val _memoryCount = MutableStateFlow(0)

    init {
        // Keep memory count up-to-date
        viewModelScope.launch {
            repository.getAllMemories().collect { list ->
                _memoryCount.value = list.size
            }
        }
    }

    // ── Public UI state ───────────────────────────────────────────────────────

    val uiState: StateFlow<VoiceQueryUiState> = combine(
        voiceAgent.stage,
        voiceAgent.transcript,
        voiceAgent.answer,
        voiceAgent.audioLevel,
        _language,
        _memoryCount
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val stage      = values[0] as VoiceAgentService.PipelineStage
        val transcript = values[1] as String
        val answer     = values[2] as String
        val amplitude  = values[3] as Float
        val language   = values[4] as String
        val count      = values[5] as Int
        VoiceQueryUiState(
            stage          = stage,
            transcript     = transcript,
            answer         = answer,
            language       = language,
            audioAmplitude = amplitude,
            memoryCount    = count
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceQueryUiState()
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Start a full voice query session (STT → RAG+LLM → TTS).
     * If already listening, stops the current session instead.
     */
    fun startQuery() {
        if (uiState.value.stage != VoiceAgentService.PipelineStage.IDLE) {
            stopQuery()
            return
        }
        voiceAgent.startVoiceSession(language = _language.value)
    }

    /** Explicitly stop an ongoing session. */
    fun stopQuery() {
        voiceAgent.stopVoiceSession()
    }

    /**
     * Toggle between English and Hindi.
     * Stops any running session before switching.
     */
    fun toggleLanguage() {
        stopQuery()
        _language.value = if (_language.value == "en") "hi" else "en"
    }

    override fun onCleared() {
        super.onCleared()
        voiceAgent.stopVoiceSession()
    }
}
