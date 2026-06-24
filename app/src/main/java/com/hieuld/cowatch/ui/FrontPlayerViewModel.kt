package com.hieuld.cowatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hieuld.cowatch.cowatch.CoWatchSessionManager
import com.hieuld.cowatch.player.PlaybackManager

class FrontPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playbackState = PlaybackManager.state
    val session = CoWatchSessionManager.session

    // Keep media ownership in the playback state, not in the Activity view code.
    fun setMediaIfNeeded(mediaUrl: String) {
        PlaybackManager.setMediaIfNeeded(mediaUrl)
    }

    // Host is the only writer for normal play intent.
    fun playAt(positionMs: Long) {
        PlaybackManager.playAt(positionMs)
    }

    // Host pause publishes the current media-clock anchor for receivers.
    fun pauseAt(
        positionMs: Long,
        durationMs: Long
    ) {
        PlaybackManager.pauseAt(positionMs, durationMs)
    }

    // Host seek is a shared playback event.
    fun seekTo(positionMs: Long) {
        PlaybackManager.seekTo(positionMs)
    }

    // Host speed changes are shared, but receiver correction speed changes are local only.
    fun updateSpeedFromHost(
        speed: Float,
        positionMs: Long
    ) {
        PlaybackManager.updateSpeedFromHost(speed, positionMs)
    }

    // During shared playback this keeps the moving host media clock fresh.
    fun updatePositionFromHost(
        positionMs: Long,
        durationMs: Long
    ) {
        PlaybackManager.updatePositionFromHost(positionMs, durationMs)
    }

    // Starts one receiver Activity per selected display through the session manager.
    fun startSharing(
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long
    ) {
        CoWatchSessionManager.startSharing(
            context = getApplication<Application>().applicationContext,
            hostDisplayId = hostDisplayId,
            targetDisplayIds = targetDisplayIds,
            anchorPositionMs = anchorPositionMs
        )
    }

    // Turning Broadcast off tears down receiver screens through shared session state.
    fun stopSharing() {
        CoWatchSessionManager.stopSharing()
    }
}
