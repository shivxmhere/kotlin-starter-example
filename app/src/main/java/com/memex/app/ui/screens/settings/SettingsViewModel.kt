package com.memex.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memex.app.ai.RunAnywhereManager
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val memoryCount       : Int     = 0,
    val vaultSizeKb       : Long    = 0L,
    val biometricEnabled  : Boolean = false,
    val biometricAvailable: Boolean = false,
    val llmLoaded         : Boolean = false,
    val sttLoaded         : Boolean = false,
    val ttsLoaded         : Boolean = false,
    val vlmLoaded         : Boolean = false,
    val aiLoadingStatus   : String  = "Checking…",
    val showDeleteDialog  : Boolean = false,
    val showEncryptionInfo: Boolean = false,
    val hashExported      : Boolean = false,
    val isDeleting        : Boolean = false
) {
    val allModelsReady: Boolean get() = llmLoaded && sttLoaded && ttsLoaded
    val vaultSizeMb: String
        get() = if (vaultSizeKb < 1024) "${vaultSizeKb} KB"
                else "${"%.1f".format(vaultSizeKb / 1024.0)} MB"
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MemoryRepository,
    private val manager   : RunAnywhereManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("memex_prefs", Context.MODE_PRIVATE)

    private val _biometricEnabled   = MutableStateFlow(prefs.getBoolean("biometric_lock", false))
    private val _showDeleteDialog   = MutableStateFlow(false)
    private val _showEncryptionInfo = MutableStateFlow(false)
    private val _hashExported       = MutableStateFlow(false)
    private val _isDeleting         = MutableStateFlow(false)
    private val _allMemories        = MutableStateFlow<List<Memory>>(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        _allMemories,
        _biometricEnabled,
        _showDeleteDialog,
        _showEncryptionInfo,
        _hashExported,
        _isDeleting,
        manager.loadingProgress
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val memories         = values[0] as List<Memory>
        val bioEnabled       = values[1] as Boolean
        val showDelete       = values[2] as Boolean
        val showEncryption   = values[3] as Boolean
        val hashExported     = values[4] as Boolean
        val isDeleting       = values[5] as Boolean
        val loadingMsg       = values[6] as String

        // Approximate vault size from content lengths
        val approxKb = memories.sumOf { it.rawContent.length + it.summary.length } / 1024L

        SettingsUiState(
            memoryCount        = memories.size,
            vaultSizeKb        = approxKb,
            biometricEnabled   = bioEnabled,
            biometricAvailable = true, // real check done in composable via MemexBiometricManager.isAvailable()
            llmLoaded          = loadingMsg.contains("Ready", ignoreCase = true),
            sttLoaded          = !loadingMsg.contains("STT not loaded"),
            ttsLoaded          = !loadingMsg.contains("TTS not loaded"),
            vlmLoaded          = !loadingMsg.contains("VLM not loaded"),
            aiLoadingStatus    = loadingMsg,
            showDeleteDialog   = showDelete,
            showEncryptionInfo = showEncryption,
            hashExported       = hashExported,
            isDeleting         = isDeleting
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            repository.getAllMemories().collect { _allMemories.value = it }
        }
    }

    // ── Biometric toggle ──────────────────────────────────────────────────────

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        _biometricEnabled.value = enabled
    }

    // ── Dialog controls ───────────────────────────────────────────────────────

    fun showDeleteDialog()    { _showDeleteDialog.value = true }
    fun dismissDeleteDialog() { _showDeleteDialog.value = false }
    fun showEncryptionInfo()  { _showEncryptionInfo.value = true }
    fun dismissEncryptionInfo() { _showEncryptionInfo.value = false }

    // ── Nuclear delete ────────────────────────────────────────────────────────

    /**
     * Permanently wipes all memories from the encrypted vault.
     * [onComplete] is invoked on success so NavGraph can navigate to splash.
     */
    fun deleteAllMemories(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isDeleting.value = true
            _showDeleteDialog.value = false
            try {
                repository.deleteAllMemories()
                onComplete()
            } finally {
                _isDeleting.value = false
            }
        }
    }

    // ── Export hashes ─────────────────────────────────────────────────────────

    fun exportProofHashes() {
        val memories = _allMemories.value
        if (memories.isEmpty()) return

        val text = buildString {
            appendLine("MEMEX Integrity Proof Hashes")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("──────────────────────────────────────────")
            memories.forEachIndexed { i, m ->
                appendLine("[${i + 1}] ${m.type} | ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.createdAt))}")
                appendLine("    SHA-256: ${m.sha256Hash}")
                appendLine()
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MEMEX hashes", text))
        _hashExported.value = true
    }
}
