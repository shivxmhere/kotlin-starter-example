package com.memex.app.ai

import com.memex.app.domain.model.Memory
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM operations for MEMEX.
 *
 * The RunAnywhere SDK exposes the LLM via the static extension:
 *   `RunAnywhere.chat(prompt: String): String`
 *
 * All calls are dispatched to [Dispatchers.IO] so callers can stay on the
 * main thread without worrying about blocking.
 *
 * Design decisions:
 *  - [summarize] uses a tightly constrained prompt (temperature 0.3) to keep
 *    summaries factual and concise.
 *  - [generateTags] forces JSON-array output and falls back gracefully if the
 *    model returns something unparseable.
 *  - [answerWithContext] returns a cold [Flow<String>] that chunks the response
 *    word-by-word for a streaming-text effect in the UI, using the non-streaming
 *    `chat` API (the SDK's streaming API is via [streamVoiceSession] only).
 *  - [resurrectContext] is the "Context Resurrection" ✨ feature — it synthesises
 *    multiple memory fragments into one coherent narrative.
 */
@Singleton
class LLMService @Inject constructor(
    private val manager: RunAnywhereManager
) {

    // ── System prompt ─────────────────────────────────────────────────────────

    private val systemPrompt = """
        You are MEMEX, a private AI memory assistant running entirely on-device.
        You help users recall and understand their captured memories.
        Be concise, accurate, and privacy-focused.
        When the user asks in Hindi, always respond fully in Hindi.
    """.trimIndent()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Summarise captured content into 2-3 sentences.
     * Used immediately after a capture event to create the stored [Memory.summary].
     *
     * @param content Raw text extracted (OCR, transcript, or typed note).
     * @param language "en" or "hi" — controls response language instruction.
     */
    suspend fun summarize(content: String, language: String = "en"): String =
        withContext(Dispatchers.IO) {
            val langInstruction =
                if (language == "hi") "Respond in Hindi." else "Respond in English."

            val prompt = """$systemPrompt

$langInstruction
Summarize the following captured content in 2-3 sentences, preserving the key information:

"$content"

Summary:"""

            runCatching {
                RunAnywhere.chat(prompt)
            }.getOrElse { e ->
                "Error generating summary: ${e.message}"
            }
        }

    /**
     * Extract 3-5 topic tags and return them as a [List<String>].
     * The prompt forces JSON-array output; falls back to ["general"] on parse error.
     */
    suspend fun generateTags(content: String): List<String> =
        withContext(Dispatchers.IO) {
            val prompt = """Extract 3-5 short topic tags from the following content.
Return ONLY a JSON array of lowercase strings.
Example format: ["work","meeting","deadline"]
Do not include any explanation or text outside the JSON array.

Content:
"$content"

Tags:"""

            val response = runCatching {
                RunAnywhere.chat(prompt)
            }.getOrElse { return@withContext listOf("general") }

            // Parse JSON — extract the first [...] block found in the response
            val jsonArray = Regex("""\[.*?]""", RegexOption.DOT_MATCHES_ALL)
                .find(response)
                ?.value
                ?: return@withContext listOf("general")

            runCatching {
                Json.decodeFromString<List<String>>(jsonArray)
                    .map { it.lowercase().trim() }
                    .filter { it.isNotEmpty() }
                    .take(5)
                    .ifEmpty { listOf("general") }
            }.getOrElse { listOf("general") }
        }

    /**
     * Context Resurrection — synthesise multiple memory fragments into a
     * coherent narrative paragraph.  Powers the "resurface" feature on HomeScreen.
     *
     * @param memories  The memory fragments to weave together.
     * @param query     Optional focus query (e.g. "project timeline").
     */
    suspend fun resurrectContext(memories: List<Memory>, query: String = ""): String =
        withContext(Dispatchers.IO) {
            if (memories.isEmpty()) return@withContext ""

            val memoriesText = memories.mapIndexed { i, m ->
                "Fragment ${i + 1} (${formatDate(m.createdAt)}): ${m.summary}"
            }.joinToString("\n")

            val focusLine = if (query.isNotEmpty())
                "Focus especially on details related to: \"$query\""
            else
                "Create a coherent, chronological narrative."

            val prompt = """$systemPrompt

You are reconstructing a narrative from these memory fragments.
$focusLine

Memory Fragments:
$memoriesText

Synthesize these into one coherent paragraph, connecting the dots and filling context:"""

            runCatching {
                RunAnywhere.chat(prompt)
            }.getOrElse { e -> "Error resurrecting context: ${e.message}" }
        }

    /**
     * Answer a voice or text query using RAG context (memories retrieved by [RAGService]).
     *
     * Returns a [Flow<String>] that emits words progressively for a streaming
     * UI effect.  Uses the standard `chat` API internally — true token streaming
     * is only available via [VoiceAgentService.streamVoiceSession].
     *
     * @param query     The user's question.
     * @param context   Relevant memory snippets retrieved by RAG.
     * @param language  "en" or "hi".
     */
    fun answerWithContext(
        query: String,
        context: String,
        language: String = "en"
    ): Flow<String> = flow {
        val langInstruction = if (language == "hi")
            "The user asked in Hindi. Answer fully in Hindi."
        else
            "Answer concisely in English."

        val prompt = """$systemPrompt

$langInstruction
Based ONLY on the following memories from the user's private vault:

$context

Answer this question: $query

Be direct and brief. Only use information from the provided memories."""

        val fullResponse = withContext(Dispatchers.IO) {
            runCatching {
                RunAnywhere.chat(prompt)
            }.getOrElse { e -> "Error answering query: ${e.message}" }
        }

        // Emit word-by-word for streaming UI effect
        fullResponse.split(" ").forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
        }
    }.flowOn(Dispatchers.Default)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
}
