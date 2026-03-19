package com.memex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the "memories" table.
 * All write operations are suspend functions; reactive reads use Flow.
 */
@Dao
interface MemoryDao {

    // ── Reactive reads ─────────────────────────────────────────────────────

    /** Observe the full memory list, newest first. */
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    // ── One-shot reads ─────────────────────────────────────────────────────

    /**
     * Full-text search across rawContent, summary, and the tags JSON string.
     * Returns an empty list (never null) when nothing matches.
     */
    @Query(
        """
        SELECT * FROM memories
        WHERE rawContent LIKE '%' || :query || '%'
           OR summary    LIKE '%' || :query || '%'
           OR tags       LIKE '%' || :query || '%'
        """
    )
    suspend fun searchMemories(query: String): List<MemoryEntity>

    /** Fetch a single record by its UUID; returns null if not found. */
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoryEntity?

    /** Fetch the N most-recent memories (useful for AI context window). */
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>

    // ── Writes ─────────────────────────────────────────────────────────────

    /** Insert or replace a memory record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    /** Hard-delete a specific memory row. */
    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    /** Nuclear delete — wipes the entire table (GDPR / privacy nuke). */
    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}
