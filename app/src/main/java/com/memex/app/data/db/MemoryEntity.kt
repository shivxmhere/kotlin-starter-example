package com.memex.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a single encrypted memory record.
 * Stored in the SQLCipher-encrypted "memex_vault.db" database.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** Capture origin: "CAMERA", "VOICE", or "TEXT" */
    val type: String,

    /** Raw OCR / transcript / user-typed content */
    val rawContent: String,

    /** LLM-generated one-sentence summary */
    val summary: String,

    /** JSON array of semantic tags, e.g. ["work","meeting","important"] */
    val tags: String,

    /** ISO 639-1 language code of the content ("en" | "hi") */
    val language: String = "en",

    /** SHA-256 hex digest of rawContent — cryptographic integrity proof */
    val sha256Hash: String,

    /** Unix epoch milliseconds of capture time */
    val createdAt: Long = System.currentTimeMillis(),

    /** Absolute path to JPEG thumbnail on internal storage (camera captures only) */
    val thumbnailPath: String? = null,

    /** Absolute path to AAC/OGG audio file on internal storage (voice captures only) */
    val audioPath: String? = null
)
