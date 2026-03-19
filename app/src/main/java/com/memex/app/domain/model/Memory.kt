package com.memex.app.domain.model

/**
 * Pure domain model for a MEMEX memory.
 * This class is decoupled from Room — it contains no annotations.
 * Conversion to/from [com.memex.app.data.db.MemoryEntity] happens in
 * [com.memex.app.data.repository.MemoryRepository].
 */
data class Memory(
    /** UUID string, matches MemoryEntity.id */
    val id: String,

    /** Capture origin (CAMERA / VOICE / TEXT) */
    val type: MemoryType,

    /** Raw extracted text (OCR output, transcript, or typed input) */
    val rawContent: String,

    /** LLM-generated one-sentence summary */
    val summary: String,

    /** Semantic tags decoded from JSON array */
    val tags: List<String>,

    /** ISO 639-1 language code ("en" | "hi") */
    val language: String,

    /** SHA-256 hex digest of rawContent */
    val sha256Hash: String,

    /** Unix epoch milliseconds of original capture */
    val createdAt: Long,

    /** Absolute path to thumbnail JPEG (null for non-camera memories) */
    val thumbnailPath: String?,

    /** Absolute path to audio file (null for non-voice memories) */
    val audioPath: String?
)
