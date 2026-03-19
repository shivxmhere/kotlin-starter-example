package com.memex.app.data.repository

import com.memex.app.data.db.MemoryDao
import com.memex.app.data.db.MemoryEntity
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all memory persistence.
 *
 * Responsibilities:
 *  - Map between [MemoryEntity] (Room/SQLCipher storage) and [Memory] (domain).
 *  - Expose coroutine-friendly APIs to the ViewModel layer.
 *  - Wrap write operations in [Result] so callers can handle DB errors gracefully.
 *
 * Injected as a [Singleton] by Hilt — one instance per process lifetime.
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {

    // ── Reactive reads ──────────────────────────────────────────────────────

    /**
     * Returns a [Flow] that emits the full memory list whenever the DB changes.
     * Newest memories are first (ORDER BY createdAt DESC).
     */
    fun getAllMemories(): Flow<List<Memory>> =
        memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }

    // ── One-shot reads ──────────────────────────────────────────────────────

    /**
     * Full-text search. Matches [query] against rawContent, summary, and tags.
     * Returns an empty list when nothing matches — never throws.
     */
    suspend fun searchMemories(query: String): List<Memory> =
        memoryDao.searchMemories(query).map { it.toDomain() }

    /**
     * Fetches the [limit] most-recent memories (default 10).
     * Used to build the AI context window for RAG queries.
     */
    suspend fun getRecentMemories(limit: Int = 10): List<Memory> =
        memoryDao.getRecentMemories(limit).map { it.toDomain() }

    /** Fetches a single memory by its UUID. Returns null if not found. */
    suspend fun getMemoryById(id: String): Memory? =
        memoryDao.getMemoryById(id)?.toDomain()

    // ── Writes ──────────────────────────────────────────────────────────────

    /**
     * Persist a [Memory] domain object.
     * Returns [Result.success] on success, [Result.failure] if the DB throws.
     */
    suspend fun saveMemory(memory: Memory): Result<Unit> = runCatching {
        memoryDao.insertMemory(memory.toEntity())
    }

    /**
     * Delete a single memory by its UUID.
     * No-ops silently if the id does not exist in the DB.
     */
    suspend fun deleteMemory(id: String) {
        val entity = memoryDao.getMemoryById(id) ?: return
        memoryDao.deleteMemory(entity)
    }

    /**
     * Nuclear delete — permanently removes ALL memories from the vault.
     * Intended for "panic wipe" and full GDPR erasure scenarios.
     */
    suspend fun deleteAllMemories() {
        memoryDao.deleteAllMemories()
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    /** [MemoryEntity] → [Memory] domain conversion. */
    private fun MemoryEntity.toDomain(): Memory = Memory(
        id = id,
        type = MemoryType.valueOf(type),
        rawContent = rawContent,
        summary = summary,
        tags = runCatching { Json.decodeFromString<List<String>>(tags) }.getOrDefault(emptyList()),
        language = language,
        sha256Hash = sha256Hash,
        createdAt = createdAt,
        thumbnailPath = thumbnailPath,
        audioPath = audioPath
    )

    /** [Memory] → [MemoryEntity] storage conversion. */
    private fun Memory.toEntity(): MemoryEntity = MemoryEntity(
        id = id,
        type = type.name,
        rawContent = rawContent,
        summary = summary,
        tags = Json.encodeToString(tags),
        language = language,
        sha256Hash = sha256Hash,
        createdAt = createdAt,
        thumbnailPath = thumbnailPath,
        audioPath = audioPath
    )
}
