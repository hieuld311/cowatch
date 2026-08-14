package com.ivi.cid.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.PlaybackController
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.cid.ui.LibraryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val videoCatalogRepository: VideoCatalogRepository,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val videos = MutableStateFlow(emptyList<AssetVideo>())

    val uiState: StateFlow<LibraryUiState> = combine(
        videos,
        playbackController.pipState,
        playbackController.playbackState
    ) { catalog, pipState, playbackState ->
        // TEMP DIAGNOSTIC: confirms whether combine() re-runs when `videos` changes.
        Log.i(TAG, "uiState combine: videos=${catalog.size}")
        LibraryUiState(
            videos = catalog,
            pipState = pipState,
            playbackState = playbackState
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LibraryUiState()
        )

    init {
        viewModelScope.launch {
            videoCatalogRepository.observeVideos().collect { catalog ->
                // TEMP DIAGNOSTIC: confirms whether this collector is still alive and how big each
                // emitted catalog is, so a stalled collector is distinguishable from a stalled UI.
                Log.i(TAG, "observeVideos collected: videos=${catalog.size}")
                val source = playbackController.playbackState.value.activeSource
                if (source != null && !source.isPackagedAsset && catalog.none { it.assetPath == source.assetPath }) {
                    playbackController.stop()
                }
                videos.value = catalog
                Log.i(TAG, "videos.value set: videos=${videos.value.size}")
            }
        }
    }

    fun togglePlayback() = playbackController.togglePlayback()

    // CIDVideo owns one MediaSession/player pair; closing PiP releases both and clears PiP state.
    fun closePipPlayback() = playbackController.stop()

    private companion object {
        const val TAG = "CoWatchLibraryVM"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
