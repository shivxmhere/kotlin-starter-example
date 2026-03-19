package com.memex.app.util

/**
 * Retained for backwards compatibility.
 * SHA-256 functionality has been consolidated into [CryptoUtil.sha256].
 * Prefer calling [CryptoUtil.sha256] directly in new code.
 */
@Deprecated(
    message = "Use CryptoUtil.sha256(input) instead.",
    replaceWith = ReplaceWith("CryptoUtil.sha256(input)", "com.memex.app.util.CryptoUtil")
)
object HashUtil {
    fun sha256(input: String): String = CryptoUtil.sha256(input)
}
