package com.memex.app.domain.model

/**
 * Discriminated union of memory capture origins.
 * Stored as a String in [com.memex.app.data.db.MemoryEntity.type].
 */
enum class MemoryType {
    /** Photo captured via device camera → OCR pipeline */
    CAMERA,

    /** Audio recorded via microphone → STT pipeline */
    VOICE,

    /** User-typed or clipboard-pasted text */
    TEXT
}
