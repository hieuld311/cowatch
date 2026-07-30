package com.ivi.pid.ui

import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.InAppPipState
import com.ivi.common.domain.SessionPlaybackState

data class VideoLibraryUiState(
    val videos: List<AssetVideo> = emptyList(),
    val pipState: InAppPipState? = null,
    val playbackState: SessionPlaybackState = SessionPlaybackState()
)
