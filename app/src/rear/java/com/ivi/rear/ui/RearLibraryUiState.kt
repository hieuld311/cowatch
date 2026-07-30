package com.ivi.rear.ui

import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.InAppPipState
import com.ivi.common.domain.SessionPlaybackState

data class RearLibraryUiState(
    val videos: List<AssetVideo> = emptyList(),
    val pipState: InAppPipState? = null,
    val playbackState: SessionPlaybackState = SessionPlaybackState(),
    val sharedMode: Boolean = false
)
