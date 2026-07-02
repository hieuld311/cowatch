package com.hieuld.cowatch.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.hieuld.cowatch.cowatch.CoWatchSessionManager
import com.hieuld.cowatch.playback.CoWatchPlaybackState
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrontPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val session = CoWatchSessionManager.session

    private val _playbackState =
        MutableStateFlow<CoWatchPlaybackState>(CoWatchPlaybackState.Idle)
    val playbackState: StateFlow<CoWatchPlaybackState> = _playbackState.asStateFlow()

    fun setPlaybackState(state: CoWatchPlaybackState) {
        _playbackState.value = state
    }

    fun startSharing(
        context: Context,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long,
        renderEngine: VideoRenderEngine,
        onAllDisplaysReady: (Long) -> Unit
    ): Boolean {
        return CoWatchSessionManager.startSharing(
            context = context,
            hostDisplayId = hostDisplayId,
            targetDisplayIds = targetDisplayIds,
            anchorPositionMs = anchorPositionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = onAllDisplaysReady
        )
    }

    fun stopSharing() {
        CoWatchSessionManager.stopSharing()
    }

}
