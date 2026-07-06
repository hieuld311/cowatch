package com.hieuld.cowatch.ui

import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.session.InAppPipState

data class VideoLibraryUiState(
    val videos: List<AssetVideo> = emptyList(),
    val pipState: InAppPipState? = null
)