package com.memex.app.ai

import android.content.Context
import com.memex.app.services.ModelService
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.isSTTModelLoaded
import com.runanywhere.sdk.public.extensions.isTTSVoiceLoaded
import com.runanywhere.sdk.public.extensions.isVLMModelLoaded
import com.runanywhere.sdk.public.extensions.isVoiceAgentReady
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton gateway to the RunAnywhere SDK.
 */
@Singleton
class RunAnywhereManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ── Model Paths ───────────────────────────────────────────────────────────
    companion object {
        const val LLM_MODEL_PATH    = "models/smollm2-1.7b-instruct-q4_k_m.gguf"
        const val VLM_MODEL_PATH    = "models/moondream2-q4_k_m.gguf"
        const val STT_MODEL_PATH    = "models/whisper-tiny.onnx"
        const val EMBEDDINGS_PATH   = "models/all-minilm-l6-v2.onnx"
    }

    // ── Observable state ──────────────────────────────────────────────────────

    private val _loadingProgress = MutableStateFlow("Initializing AI…")
    val loadingProgress: StateFlow<String> = _loadingProgress

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Called from [MemexApplication] (or a coroutine triggered from Splash).
     * Registers all model descriptors with the SDK, then checks what is
     * already locally cached so subsequent launches skip downloads.
     *
     * Actual downloading/loading is triggered on-demand by each service.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        _loadingProgress.value = "Registering models…"

        // Register model descriptors (idempotent — safe to call multiple times)
        ModelService.registerDefaultModels()

        // Refresh state from SDK
        _loadingProgress.value = "Checking model cache…"
        refreshReadyState()
    }

    /**
     * Re-read model-loaded flags from the SDK and update [_isReady].
     * Suitable for polling after a load operation completes.
     */
    suspend fun refreshReadyState() = withContext(Dispatchers.IO) {
        val llm = RunAnywhere.isLLMModelLoaded()
        val stt = RunAnywhere.isSTTModelLoaded()
        val tts = RunAnywhere.isTTSVoiceLoaded()

        _loadingProgress.value = buildString {
            if (!llm) append("LLM not loaded  ")
            if (!stt) append("STT not loaded  ")
            if (!tts) append("TTS not loaded  ")
            if (llm && stt && tts) append("Ready")
        }.trim()

        _isReady.value = llm && stt && tts
    }

    /** Convenience: is LLM loaded? Checked before chat calls. */
    suspend fun isLLMReady(): Boolean =
        withContext(Dispatchers.IO) { RunAnywhere.isLLMModelLoaded() }

    /** Convenience: is STT loaded? Checked before transcription calls. */
    suspend fun isSTTReady(): Boolean =
        withContext(Dispatchers.IO) { RunAnywhere.isSTTModelLoaded() }

    /** Convenience: is TTS loaded? Checked before synthesis calls. */
    suspend fun isTTSReady(): Boolean =
        withContext(Dispatchers.IO) { RunAnywhere.isTTSVoiceLoaded() }

    /** Convenience: is VLM loaded? Checked before image analysis calls. */
    suspend fun isVLMReady(): Boolean =
        withContext(Dispatchers.IO) { RunAnywhere.isVLMModelLoaded }

    /** Full voice agent ready (LLM + STT + TTS, checked by the SDK itself). */
    suspend fun isVoiceAgentReady(): Boolean =
        withContext(Dispatchers.IO) { RunAnywhere.isVoiceAgentReady() }
}
