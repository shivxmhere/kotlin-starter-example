package com.memex.app.ai

import android.content.Context
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.domain.model.Memory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrieval-Augmented Generation service.
 *
 * MEMEX does NOT use a separate embedding model / vector store.  Instead it
 * implements a simple but effective keyword-ranked retrieval layer directly
 * on top of the SQLite FTS search in [MemoryRepository].  This avoids the
 * ~200 MB embedding model download and keeps everything device-friendly for
 * a hackathon demo, while still delivering accurate recall for well-tagged
 * memories.
 *
 * Architecture:
 *   1. [search] queries [MemoryRepository.searchMemories] for keyword matches.
 *   2. Results are ranked by a simple relevance score (tag overlap + recency).
 *   3. Top-K memories are formatted into a context string consumed by [LLMService].
 *
 * When the RunAnywhere SDK ships a stable RAGPipeline API, the [search] method
 * can be upgraded to use semantic embeddings without changing any call sites.
 */
@Singleton
class RAGService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MemoryRepository
) {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Search memories relevant to [query] and return a formatted context string
     * suitable for injection into an LLM prompt.
     *
     * @param query  Natural-language query (e.g. the user's voice question).
     * @param topK   Maximum number of memories to include in the context.
     * @return       Formatted context string, or empty string if nothing found.
     */
    suspend fun search(query: String, topK: Int = 5): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ""

        // 1. Keyword search in SQLite (rawContent + summary columns)
        val candidates = runCatching {
            repository.searchMemories(query)
        }.getOrElse { emptyList() }

        if (candidates.isEmpty()) return@withContext ""

        // 2. Rank by relevance score
        val queryTokens = tokenise(query)
        val ranked = candidates
            .map { memory -> memory to scoreMemory(memory, queryTokens) }
            .sortedByDescending { (_, score) -> score }
            .take(topK)
            .map { (memory, _) -> memory }

        // 3. Format for LLM context
        formatContext(ranked)
    }

    /**
     * Search and return raw [Memory] objects (used by HomeScreen search UI).
     */
    suspend fun searchMemories(query: String, topK: Int = 20): List<Memory> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            runCatching {
                val queryTokens = tokenise(query)
                repository.searchMemories(query)
                    .map { it to scoreMemory(it, queryTokens) }
                    .sortedByDescending { (_, s) -> s }
                    .take(topK)
                    .map { (m, _) -> m }
            }.getOrElse { emptyList() }
        }

    /**
     * Build RAG context from an explicit list of memories (e.g. "resurrect context"
     * feature where the caller has already selected the memories).
     */
    fun buildContext(memories: List<Memory>): String = formatContext(memories)

    // ── Ranking helpers ───────────────────────────────────────────────────────

    /**
     * Simple TF-inspired relevance score:
     *  +3 per query token that appears in a tag
     *  +1 per query token that appears in the summary
     *  +0.5 per query token that appears in rawContent
     *  +recency bonus (higher for recent memories)
     */
    private fun scoreMemory(memory: Memory, queryTokens: Set<String>): Float {
        var score = 0f
        val tagsLower    = memory.tags.joinToString(" ").lowercase()
        val summaryLower = memory.summary.lowercase()
        val rawLower     = memory.rawContent.lowercase()

        for (token in queryTokens) {
            if (tagsLower.contains(token))    score += 3f
            if (summaryLower.contains(token)) score += 1f
            if (rawLower.contains(token))     score += 0.5f
        }

        // Recency bonus: +0-2 points, decaying over 30 days
        val ageMs = System.currentTimeMillis() - memory.createdAt
        val ageDays = ageMs / (1_000L * 60 * 60 * 24)
        score += (2f * (1f - (ageDays / 30f).coerceIn(0f, 1f)))

        return score
    }

    /** Tokenise a query string into a set of normalised lowercase tokens. */
    private fun tokenise(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[\\s,;.!?]+"))
            .filter { it.length >= 3 }   // ignore very short words
            .toSet()

    // ── Formatting ────────────────────────────────────────────────────────────

    private fun formatContext(memories: List<Memory>): String {
        if (memories.isEmpty()) return ""
        return memories.mapIndexed { i, m ->
            val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(m.createdAt))
            val typeLabel = m.type.name.lowercase().replaceFirstChar { it.uppercase() }
            "[$typeLabel memory — $date]\n${m.summary}\n${
                if (m.rawContent.length > 300) m.rawContent.take(300) + "…"
                else m.rawContent
            }"
        }.joinToString("\n\n---\n\n")
    }
}
