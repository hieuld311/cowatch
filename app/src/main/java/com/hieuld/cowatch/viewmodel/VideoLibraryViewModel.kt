package com.hieuld.cowatch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hieuld.cowatch.data.media.repository.AssetVideoRepository
import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.session.AppPlaybackSession
import com.hieuld.cowatch.ui.VideoLibraryUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    // Asset catalog is static for the installed APK, so load once for the ViewModel lifetime.
    private val videos = AssetVideoRepository.listVideos(appContext)

    val uiState: StateFlow<VideoLibraryUiState> =
        AppPlaybackSession.pipState
            .map { pipState ->
                VideoLibraryUiState(
                    videos = videos,
                    pipState = pipState
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Companion.Eagerly,
                initialValue = VideoLibraryUiState(videos = videos)
            )

    fun currentPipVideo(): AssetVideo? {
        // Resolve current player source back to library item so PiP click can reopen fullscreen playback.
        return AppPlaybackSession.currentSource
            ?.let { source -> AssetVideoRepository.findByAssetPath(appContext, source.assetPath) }
    }
}
