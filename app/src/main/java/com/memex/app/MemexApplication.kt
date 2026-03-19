package com.memex.app

import android.app.Application
import com.memex.app.ai.RunAnywhereManager
import com.memex.app.services.ModelService
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.registerMultiFileModel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for MEMEX.
 *
 * Responsibilities:
 *  1. Initialize the RunAnywhere SDK with the API key.
 *  2. Register all AI model descriptors (so the SDK knows where to download them).
 *  3. Trigger [RunAnywhereManager.initialize] in a background coroutine.
 *     SplashScreen observes [RunAnywhereManager.isReady] and navigates to Home
 *     once initialization completes.
 */
@HiltAndroidApp
class MemexApplication : Application() {

    /** App-wide coroutine scope (supervisor, so one failure doesn't kill others). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Hilt injects the singleton [RunAnywhereManager] here.
     */
    @Inject
    lateinit var runAnywhereManager: RunAnywhereManager

    @Inject
    lateinit var modelDownloadManager: ModelDownloadManager

    override fun onCreate() {
        super.onCreate()

        // ── 0. Download models if needed ──────────────────────────────────────
        appScope.launch {
            try {
                modelDownloadManager.checkAndDownload()
                
                // ── 1. Initialize RunAnywhere SDK ─────────────────────────────────────
                RunAnywhere.initialize(
                    apiKey      = BuildConfig.RUNANYWHERE_API_KEY,
                    baseURL     = "https://api.runanywhere.ai",
                    environment = com.runanywhere.sdk.public.Environment.PRODUCTION
                )

                // ── 2. Register model descriptors ─────────────────────────────────────
                ModelService.registerDefaultModels()

                // ── 3. Warm-up ────────────────────────────────────────────────────────
                runAnywhereManager.initialize()
            } catch (e: Exception) {
                // Error handled in UI via ModelDownloadManager's state
            }
        }
    }
}
