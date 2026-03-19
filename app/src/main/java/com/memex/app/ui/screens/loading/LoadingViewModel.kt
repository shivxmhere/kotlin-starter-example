package com.memex.app.ui.screens.loading

import androidx.lifecycle.ViewModel
import com.memex.app.ai.RunAnywhereManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val aiManager: RunAnywhereManager,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    val isReady: StateFlow<Boolean> = aiManager.isReady
    val loadingStatus: StateFlow<String> = aiManager.loadingProgress
    
    val downloadStatus: StateFlow<String> = downloadManager.statusMessage
    val downloadProgress: StateFlow<Float> = downloadManager.downloadProgress
    val areModelsDownloaded: Boolean = downloadManager.areModelsDownloaded()
}
