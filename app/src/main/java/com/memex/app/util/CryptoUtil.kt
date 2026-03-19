package com.memex.app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Cryptographic utilities for MEMEX.
 *
 * ## Passphrase derivation
 *   A 256-bit AES key is generated once and stored in the Android Keystore under
 *   [KEY_ALIAS]. On subsequent calls, the same key is retrieved — it never leaves
 *   the secure hardware enclave (or software-backed TEE on devices without HSM).
 *
 *   The raw key bytes are used directly as the SQLCipher database passphrase via
 *   [SupportOpenHelperFactory], so no additional PBKDF2 stretching is required.
 *
 * ## Content integrity
 *   [sha256] produces a hex-encoded SHA-256 digest that is stored alongside every
 *   memory record, enabling tamper detection at retrieval time.
 */
object CryptoUtil {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "memex_vault_key"
    private const val KEY_SIZE_BITS = 256 // 32 bytes

    // ── Passphrase ────────────────────────────────────────────────────────────

    /**
     * Returns the 32-byte AES key material stored in Android Keystore.
     * Creates and permanently stores the key on the first call.
     *
     * The [context] parameter is accepted for API symmetry with older patterns;
     * Android Keystore itself does not require a context.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getOrCreatePassphrase(context: Context): ByteArray {
        return getOrCreateKey().encoded
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        // Return existing key if already generated
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        // Generate a new AES-256 key in the Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Key is non-exportable from the Keystore; only raw bytes are exposed
            // via SecretKey.encoded for the SQLCipher factory
            .setUserAuthenticationRequired(false) // no biometric gate on DB open
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // ── Hashing ───────────────────────────────────────────────────────────────

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of [input].
     * Used to generate the [com.memex.app.data.db.MemoryEntity.sha256Hash] field
     * from a memory's raw content before persisting it.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
