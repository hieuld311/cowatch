package com.hieuld.cowatch.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.hieuld.cowatch.domain.playback.CoWatchPlaybackState
import com.hieuld.cowatch.render.VideoRenderEngine
import com.hieuld.cowatch.session.ShareSessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrontPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val session = ShareSessionController.session
    val hostNotifications = ShareSessionController.hostNotifications

    private val _playbackState =
        MutableStateFlow<CoWatchPlaybackState>(CoWatchPlaybackState.Idle)
    val playbackState: StateFlow<CoWatchPlaybackState> = _playbackState.asStateFlow()

    fun setPlaybackState(state: CoWatchPlaybackState) {
        _playbackState.value = state
    }

    fun startSharing(
        context: Context,
        videoTitle: String,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long,
        renderEngine: VideoRenderEngine,
        onAllDisplaysReady: (Long) -> Unit
    ): Boolean {
        return ShareSessionController.startSharing(
            context = context,
            videoTitle = videoTitle,
            hostDisplayId = hostDisplayId,
            targetDisplayIds = targetDisplayIds,
            anchorPositionMs = anchorPositionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = onAllDisplaysReady
        )
    }

    fun stopSharing(
        notifyEnded: Boolean = true
    ) {
        ShareSessionController.stopSharing(notifyEnded = notifyEnded)
    }

}
