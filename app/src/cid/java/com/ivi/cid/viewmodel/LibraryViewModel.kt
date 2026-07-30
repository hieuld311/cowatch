package com.ivi.cid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.PlaybackController
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.cid.ui.LibraryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = videoCatalogRepository.listVideos()
            videos.value = catalog
        }
    }

    fun togglePlayback() = playbackController.togglePlayback()

    // CIDVideo owns one MediaSession/player pair; closing PiP releases both and clears PiP state.
    fun closePipPlayback() = playbackController.stop()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
