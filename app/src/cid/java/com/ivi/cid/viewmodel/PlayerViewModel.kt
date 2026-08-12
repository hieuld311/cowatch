package com.ivi.cid.viewmodel

import androidx.lifecycle.ViewModel
import com.ivi.common.domain.PlaybackController
import com.ivi.common.domain.VideoSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController
) : ViewModel() {
    val playbackState = playbackController.playbackState
    val playbackCompleted = playbackController.playbackCompleted

    fun show(source: VideoSource.Asset) = playbackController.showFullscreen(source)
    fun continuePlayback(source: VideoSource.Asset) = playbackController.continuePlayback(source)
    fun enterInAppPip() = playbackController.enterInAppPip()
    fun exitInAppPip() = playbackController.exitInAppPip()
    fun close() = playbackController.stop()

}
