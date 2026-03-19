package com.memex.app.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps [BiometricPrompt] in a clean callback-based API for MEMEX.
 *
 * Authenticators used:
 *   - [BiometricManager.Authenticators.BIOMETRIC_STRONG] — fingerprint / face / iris
 *   - [BiometricManager.Authenticators.DEVICE_CREDENTIAL]  — PIN / pattern / password fallback
 *
 * When no biometric hardware is enrolled ([BiometricPrompt.ERROR_NO_BIOMETRICS] or
 * [BiometricPrompt.ERROR_HW_NOT_PRESENT]) the [onFallback] lambda is invoked so the
 * caller can bypass authentication gracefully (first-run / no hardware).
 */
class MemexBiometricManager(private val activity: FragmentActivity) {

    /**
     * Show the system biometric prompt.
     *
     * @param onSuccess  Called when the user authenticates successfully.
     * @param onError    Called with a human-readable message when auth fails or is
     *                   cancelled (excludes the "no biometrics enrolled" case).
     * @param onFallback Called when no biometrics are enrolled/available — the app
     *                   should proceed without locking in this case.
     */
    fun authenticate(
        onSuccess : () -> Unit,
        onError   : (String) -> Unit,
        onFallback: () -> Unit
    ) {
        // Pre-check availability so we can fast-path without showing the prompt
        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        when (canAuth) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onFallback()
                return
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE -> onFallback()

                    // User pressed Cancel — treat as error so caller can decide
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onError("Authentication cancelled")

                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // Biometric read failed (not matched) — system shows its own retry UI
                // Do NOT call onError here; the prompt stays open automatically.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MEMEX")
            .setSubtitle("Your private AI memory vault")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    companion object {
        /**
         * Returns `true` if biometric / device-credential authentication is
         * available on this device.  Use to decide whether to show the lock toggle
         * in Settings.
         */
        fun isAvailable(activity: FragmentActivity): Boolean {
            val mgr = BiometricManager.from(activity)
            return mgr.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }
}
