package com.memex.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.cancelVLMGeneration
import com.runanywhere.sdk.public.extensions.processImageStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vision Language Model service for analysing camera captures.
 *
 * SDK API (from VisionScreen.kt):
 *   `VLMImage.fromFilePath(path: String)`
 *   `RunAnywhere.processImageStream(image, prompt, options): Flow<String>`
 *   `RunAnywhere.cancelVLMGeneration()`
 *
 * The VLM model (SmolVLM-256M) must be loaded before calling [analyzeImage].
 * Loading is triggered on-demand via [RunAnywhereManager.isVLMReady].
 */
@Singleton
class VLMService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: RunAnywhereManager
) {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Analyse an image from [imagePath] (must be an absolute file path).
     *
     * Returns a [Flow<String>] that streams tokens as the VLM generates its
     * description.  Collect all tokens and concatenate to get the full result.
     *
     * The prompt is optimised for MEMEX: it asks for comprehensive text
     * extraction first, then scene description, to maximise memory utility.
     */
    fun analyzeImage(imagePath: String): Flow<String> {
        val vlmImage = VLMImage.fromFilePath(imagePath)
        val options  = VLMGenerationOptions(maxTokens = 500)

        val prompt = """Analyze this image thoroughly for memory indexing.
1. If there is ANY text (printed, handwritten, on-screen), extract it ALL verbatim.
2. Identify the type of content: document, whiteboard, receipt, label, scene, person, object, food, etc.
3. Describe the key visual information likely to be useful for future recall.
4. If it contains a face, describe only the context (not identity).
Be comprehensive. Start with text extraction, then visual description."""

        return RunAnywhere.processImageStream(vlmImage, prompt, options)
            .catch { e -> emit("Error analysing image: ${e.message}") }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Convenience overload: collect the full [Flow<String>] and return a
     * [VLMResult] data class.  Blocks the coroutine until generation completes.
     */
    suspend fun analyzeImageFull(imagePath: String): VLMResult =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            analyzeImage(imagePath).collect { token -> sb.append(token) }
            val text = sb.toString().trim()
            VLMResult(
                extractedText = text,
                hasText       = text.length > 20,
                confidence    = if (text.isNotEmpty()) 0.85f else 0f
            )
        }

    /**
     * Analyse a [Bitmap] directly (e.g. from CameraX ImageCapture callback).
     * Saves the bitmap to a temp file, then delegates to [analyzeImageFull].
     */
    suspend fun analyzeBitmap(bitmap: Bitmap): VLMResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "memex_vlm_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        analyzeImageFull(tempFile.absolutePath)
    }

    /**
     * Analyse an image from an absolute file path. Convenience wrapper.
     */
    suspend fun analyzeFromPath(path: String): VLMResult = analyzeImageFull(path)

    /** Cancel any in-progress VLM generation (e.g. user navigates away). */
    fun cancelAnalysis() = RunAnywhere.cancelVLMGeneration()

    /**
     * Load a Bitmap from [imagePath] for thumbnail preview.
     * Returns null if decoding fails.
     */
    suspend fun loadBitmap(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
    }
}

/**
 * Result of a VLM image analysis.
 *
 * @param extractedText  Full text / description returned by the model.
 * @param hasText        True when the model found substantial text (>20 chars).
 * @param confidence     Heuristic confidence score [0, 1].
 */
data class VLMResult(
    val extractedText: String,
    val hasText: Boolean,
    val confidence: Float
)
