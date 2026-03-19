package com.memex.app.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State container for the Memory Detail view.
 */
data class MemoryDetailUiState(
    val memory: Memory? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    private val repository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryDetailUiState())
    val uiState: StateFlow<MemoryDetailUiState> = _uiState.asStateFlow()

    fun loadMemory(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val memory = repository.getMemoryById(id)
                if (memory != null) {
                    _uiState.value = MemoryDetailUiState(memory = memory, isLoading = false)
                } else {
                    _uiState.value = MemoryDetailUiState(isLoading = false, error = "Memory not found")
                }
            } catch (e: Exception) {
                _uiState.value = MemoryDetailUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteMemory(onComplete: () -> Unit) {
        val memory = _uiState.value.memory ?: return
        viewModelScope.launch {
            repository.deleteMemory(memory.id)
            onComplete()
        }
    }
}
