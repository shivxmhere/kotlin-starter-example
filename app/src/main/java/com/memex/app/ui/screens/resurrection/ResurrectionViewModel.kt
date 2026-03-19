package com.memex.app.ui.screens.resurrection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.ai.LLMService
import com.memex.app.ai.TTSService
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import com.memex.app.util.CryptoUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ── Stage enum ────────────────────────────────────────────────────────────────

enum class ResurrectionStage {
    SELECTING,    // user picking memories to weave together
    ANIMATING,    // cinematic convergence animation playing
    SYNTHESIZING, // LLM generating narrative (can overlap with animation)
    DONE          // narrative ready to display
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class ResurrectionUiState(
    val stage            : ResurrectionStage = ResurrectionStage.SELECTING,
    val allMemories      : List<Memory>      = emptyList(),
    val selectedIds      : Set<String>       = emptySet(),
    val narrativeText    : String            = "",
    val typedChars       : Int               = 0,    // for typewriter effect
    val isSpeaking       : Boolean           = false,
    val isSaved          : Boolean           = false,
    val errorMessage     : String?           = null
) {
    val selectedMemories: List<Memory>
        get() = allMemories.filter { it.id in selectedIds }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ResurrectionViewModel @Inject constructor(
    private val repository : MemoryRepository,
    private val llmService : LLMService,
    private val ttsService : TTSService
) : ViewModel() {

    private val _stage         = MutableStateFlow(ResurrectionStage.SELECTING)
    private val _allMemories   = MutableStateFlow<List<Memory>>(emptyList())
    private val _selectedIds   = MutableStateFlow<Set<String>>(emptySet())
    private val _narrativeText = MutableStateFlow("")
    private val _typedChars    = MutableStateFlow(0)
    private val _isSaved       = MutableStateFlow(false)
    private val _errorMessage  = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ResurrectionUiState> = combine(
        _stage, _allMemories, _selectedIds, _narrativeText,
        _typedChars, ttsService.isSpeaking, _isSaved, _errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ResurrectionUiState(
            stage         = values[0] as ResurrectionStage,
            allMemories   = values[1] as List<Memory>,
            selectedIds   = values[2] as Set<String>,
            narrativeText = values[3] as String,
            typedChars    = values[4] as Int,
            isSpeaking    = values[5] as Boolean,
            isSaved       = values[6] as Boolean,
            errorMessage  = values[7] as String?
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResurrectionUiState()
    )

    init {
        viewModelScope.launch {
            repository.getAllMemories().collect { list ->
                _allMemories.value = list
            }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun toggleMemorySelection(id: String) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) {
            current - id
        } else {
            if (current.size >= 10) current   // max 10
            else current + id
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ── Resurrection pipeline ─────────────────────────────────────────────────

    /**
     * Kick off the cinematic animation then run the LLM narrative synthesis.
     * The animation and LLM call run concurrently; the narrative is revealed
     * only after [onAnimationComplete] signals the animation is done.
     */
    fun resurrect() {
        val memories = _selectedIds.value
            .mapNotNull { id -> _allMemories.value.find { it.id == id } }
        if (memories.size < 2) return

        _stage.value = ResurrectionStage.ANIMATING
        _narrativeText.value = ""
        _typedChars.value = 0

        // Fire LLM synthesis concurrently (result cached, revealed after animation)
        viewModelScope.launch {
            try {
                _stage.value = ResurrectionStage.SYNTHESIZING
                val narrative = llmService.resurrectContext(memories)
                _narrativeText.value = narrative
                // If animation already done, start typewriter now
                if (_stage.value == ResurrectionStage.DONE) {
                    startTypewriter(narrative)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Synthesis failed: ${e.message}"
                _stage.value = ResurrectionStage.SELECTING
            }
        }
    }

    /** Called by [ResurrectionAnimation] when the cinematic sequence finishes. */
    fun onAnimationComplete() {
        _stage.value = ResurrectionStage.DONE
        val narrative = _narrativeText.value
        if (narrative.isNotEmpty()) {
            viewModelScope.launch { startTypewriter(narrative) }
        }
    }

    /** Simulates typewriter by incrementing [_typedChars] every 40ms. */
    private suspend fun startTypewriter(text: String) {
        _typedChars.value = 0
        for (i in 1..text.length) {
            _typedChars.value = i
            kotlinx.coroutines.delay(40)
        }
    }

    // ── Speak ─────────────────────────────────────────────────────────────────

    fun speakNarrative() {
        val text = _narrativeText.value
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                ttsService.speak(text)
            } catch (e: Exception) {
                _errorMessage.value = "TTS error: ${e.message}"
            }
        }
    }

    fun stopSpeaking() {
        ttsService.stop()
    }

    // ── Save as memory ────────────────────────────────────────────────────────

    fun saveNarrativeAsMemory() {
        val text = _narrativeText.value
        if (text.isBlank() || _isSaved.value) return
        viewModelScope.launch {
            val memory = Memory(
                id         = UUID.randomUUID().toString(),
                type       = MemoryType.TEXT,
                rawContent = text,
                summary    = text.take(150),
                tags       = listOf("resurrection", "synthesis", "narrative"),
                language   = "en",
                sha256Hash = CryptoUtil.sha256(text),
                createdAt  = System.currentTimeMillis(),
                thumbnailPath = null,
                audioPath    = null
            )
            repository.saveMemory(memory)
            _isSaved.value = true
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        ttsService.stop()
        _stage.value = ResurrectionStage.SELECTING
        _selectedIds.value = emptySet()
        _narrativeText.value = ""
        _typedChars.value = 0
        _isSaved.value = false
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
    }
}
