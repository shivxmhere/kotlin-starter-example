package com.memex.app.ai

import android.content.Context
import android.util.Log
import com.runanywhere.sdk.public.ModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "ModelDownloadManager"
    private val prefs = context.getSharedPreferences("memex_prefs", Context.MODE_PRIVATE)

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _statusMessage = MutableStateFlow("Checking AI models…")
    val statusMessage: StateFlow<String> = _statusMessage

    private val models = listOf(
        "smollm2-1.7b-instruct-q4_k_m.gguf",
        "moondream2-q4_k_m.gguf",
        "whisper-tiny.onnx",
        "all-minilm-l6-v2.onnx"
    )

    fun areModelsDownloaded(): Boolean {
        return prefs.getBoolean("models_downloaded", false)
    }

    suspend fun checkAndDownload() {
        if (areModelsDownloaded()) {
            _statusMessage.value = "AI models found."
            return
        }

        _statusMessage.value = "Preparing to download AI models…"
        
        try {
            var completedCount = 0
            for (model in models) {
                _statusMessage.value = "Downloading $model…"
                
                // Note: The actual progress tracking would depend on RunAnywhere SDK's flow.
                // Here we assume a simple downloadIfNeeded call that might block or provide a flow.
                // Using a placeholder for now as per user instruction.
                ModelManager.downloadIfNeeded(model) { progress ->
                    // Average progress across all 4 models
                    val totalProgress = (completedCount + progress) / models.size
                    _downloadProgress.value = totalProgress
                }
                
                completedCount++
            }

            prefs.edit().putBoolean("models_downloaded", true).apply()
            _statusMessage.value = "All models downloaded."
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _statusMessage.value = "Download failed: ${e.message}"
            throw e
        }
    }
}
