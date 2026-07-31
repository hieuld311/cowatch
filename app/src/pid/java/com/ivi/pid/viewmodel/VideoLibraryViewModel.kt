package com.ivi.pid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.media.ThumbnailProfile
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.pid.sharing.PidShareCoordinator
import com.ivi.pid.ui.VideoLibraryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoLibraryViewModel @Inject constructor(
    private val videoCatalogRepository: VideoCatalogRepository,
    private val thumbnailLoader: ThumbnailLoader,
    private val playbackSession: Media3PlaybackController,
    private val shareCoordinator: PidShareCoordinator
) : ViewModel() {
    private val videos = MutableStateFlow(emptyList<AssetVideo>())

    val uiState: StateFlow<VideoLibraryUiState> = combine(
        videos,
        playbackSession.pipState,
        playbackSession.playbackState
    ) { catalog, pipState, playbackState ->
        VideoLibraryUiState(
            videos = catalog,
            pipState = pipState,
            playbackState = playbackState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VideoLibraryUiState()
    )

    init {
        viewModelScope.launch {
            videoCatalogRepository.observeVideos().collect { catalog ->
                stopRemovedUsbPlayback(catalog)
                videos.value = catalog
                delay(BACKGROUND_CACHE_WARMUP_DELAY_MS)
                thumbnailLoader.warmPersistentCache(catalog, ThumbnailProfile.Background)
            }
        }
    }

    fun togglePipPlayback() = playbackSession.togglePlayback()

    fun closePipPlayback() {
        shareCoordinator.stopSharingAll()
        playbackSession.stop()
    }

    private fun stopRemovedUsbPlayback(catalog: List<AssetVideo>) {
        val source = playbackSession.currentSource ?: return
        if (!source.isPackagedAsset && catalog.none { it.assetPath == source.assetPath }) {
            shareCoordinator.stopSharingAll("USB media was removed")
            playbackSession.stop()
        }
    }

    private companion object {
        const val BACKGROUND_CACHE_WARMUP_DELAY_MS = 750L
    }
}
