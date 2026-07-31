package com.ivi.rear.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.media.ThumbnailProfile
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.rear.sharing.RearShareClient
import com.ivi.rear.ui.RearLibraryUiState
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
    repository: VideoCatalogRepository,
    val thumbnailLoader: ThumbnailLoader,
    val playbackController: Media3PlaybackController,
    val shareClient: RearShareClient
) : ViewModel() {
    private val videos = MutableStateFlow(emptyList<AssetVideo>())
    val uiState: StateFlow<RearLibraryUiState> = combine(
        videos,
        playbackController.pipState,
        playbackController.playbackState,
        shareClient.sharedSession
    ) { catalog, pip, playback, shared ->
        RearLibraryUiState(catalog, pip, playback, shared != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RearLibraryUiState())

    init {
        viewModelScope.launch {
            repository.observeVideos().collect { catalog ->
                val source = playbackController.currentSource
                if (source != null && !source.isPackagedAsset && catalog.none { it.assetPath == source.assetPath }) {
                    playbackController.stop()
                }
                videos.value = catalog
                delay(750L)
                thumbnailLoader.warmPersistentCache(catalog, ThumbnailProfile.Background)
            }
        }
    }

    fun togglePlayback() {
        if (shareClient.sharedSession.value == null) playbackController.togglePlayback()
    }

    fun closePip() {
        if (shareClient.sharedSession.value == null) playbackController.stop()
    }
}
