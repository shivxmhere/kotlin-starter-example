package com.memex.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.ai.RAGService
import com.memex.app.ai.RunAnywhereManager
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI contract ───────────────────────────────────────────────────────────────

/**
 * Complete UI state for [HomeScreen].
 *
 * @param memories         Filtered (or full) memory list to display.
 * @param isLoading        True while the initial DB query is in-flight.
 * @param searchQuery      Current text in the search bar.
 * @param aiLoadingMessage Non-null while the RunAnywhere SDK is still loading
 *                         models; null once all models are ready.
 * @param totalCount       Total number of memories in the vault (for header).
 */
data class HomeUiState(
    val memories        : List<Memory> = emptyList(),
    val isLoading       : Boolean      = true,
    val searchQuery     : String       = "",
    val aiLoadingMessage: String?      = "Initializing AI…",
    val totalCount      : Int          = 0
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
@OptIn(FlowPreview::class)
class HomeViewModel @Inject constructor(
    private val repository : MemoryRepository,
    private val ragService : RAGService,
    private val aiManager  : RunAnywhereManager
) : ViewModel() {

    // ── Internal mutable state ────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading   = MutableStateFlow(true)

    // ── Derived: all memories from DB ─────────────────────────────────────────

    /**
     * All memories from the DB, filtered reactively by [_searchQuery].
     * - Empty query → full list from [MemoryRepository.getAllMemories] (Flow).
     * - Non-empty   → snapshot search via [RAGService.searchMemories], re-run
     *                 on every debounced keystroke.
     */
    private val filteredMemories: StateFlow<List<Memory>> =
        _searchQuery
            .debounce(250)                    // avoid search on every keystroke
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    repository.getAllMemories()
                } else {
                    flow { emit(ragService.searchMemories(query)) }
                }
            }
            .stateIn(
                scope         = viewModelScope,
                started       = SharingStarted.WhileSubscribed(5_000),
                initialValue  = emptyList()
            )

    // ── Derived: AI loading message ───────────────────────────────────────────

    /** Forwards the SDK loading progress string; null once isReady = true. */
    private val aiMessage: StateFlow<String?> =
        combine(aiManager.isReady, aiManager.loadingProgress) { ready, msg ->
            if (ready) null else msg
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = "Initializing AI…"
        )

    // ── Exposed UI state ──────────────────────────────────────────────────────

    val uiState: StateFlow<HomeUiState> =
        combine(
            filteredMemories,
            _isLoading,
            _searchQuery,
            aiMessage
        ) { memories, loading, query, aiMsg ->
            HomeUiState(
                memories         = memories,
                isLoading        = loading && memories.isEmpty() && query.isEmpty(),
                searchQuery      = query,
                aiLoadingMessage = aiMsg,
                totalCount       = memories.size
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    // Backwards-compat for any existing callers
    val memories: StateFlow<List<Memory>> = filteredMemories

    init {
        // Mark loading done once the first DB emission arrives
        viewModelScope.launch {
            filteredMemories.collect {
                if (_isLoading.value) _isLoading.value = false
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Update the search query; triggers reactive filtering above. */
    fun searchMemories(query: String) {
        _searchQuery.value = query
    }

    /** Delete a single memory by its UUID. */
    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    /** Wipe the entire vault — used from the "Panic Wipe" settings action. */
    fun deleteAllMemories() {
        viewModelScope.launch { repository.deleteAllMemories() }
    }
}
