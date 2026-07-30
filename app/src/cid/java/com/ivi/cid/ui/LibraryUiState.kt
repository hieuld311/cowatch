package com.ivi.cid.ui

import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.InAppPipState
import com.ivi.common.domain.SessionPlaybackState

data class LibraryUiState(
    val videos: List<AssetVideo> = emptyList(),
    val pipState: InAppPipState? = null,
    val playbackState: SessionPlaybackState = SessionPlaybackState()
)