package com.hieuld.cowatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.cowatch.CoWatchMediaSessionAdapter
import com.hieuld.cowatch.cowatch.CoWatchSessionManager

class FrontPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playbackState = CoWatchMediaSessionAdapter.state
    val session = CoWatchSessionManager.session

    fun attachPlayer(player: ExoPlayer) {
        CoWatchMediaSessionAdapter.attach(
            context = getApplication<Application>().applicationContext,
            exoPlayer = player
        )
    }

    fun setMedia(
        mediaUri: String,
        title: String = "CoWatch Media"
    ) {
        CoWatchMediaSessionAdapter.setMedia(
            mediaUri = mediaUri,
            title = title
        )
    }

    fun play() {
        CoWatchMediaSessionAdapter.play()
    }

    fun pause() {
        CoWatchMediaSessionAdapter.pause()
    }

    fun seekTo(positionMs: Long) {
        CoWatchMediaSessionAdapter.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        CoWatchMediaSessionAdapter.setPlaybackSpeed(speed)
    }

    fun publishHostSnapshot() {
        CoWatchMediaSessionAdapter.publishHostSnapshot()
    }

    fun clearScheduledStart() {
        CoWatchMediaSessionAdapter.clearScheduledStart()
    }

    fun startSharing(
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long
    ): Boolean {
        return CoWatchSessionManager.startSharing(
            context = getApplication<Application>().applicationContext,
            hostDisplayId = hostDisplayId,
            targetDisplayIds = targetDisplayIds,
            anchorPositionMs = anchorPositionMs
        )
    }

    fun stopSharing() {
        CoWatchSessionManager.stopSharing()
    }

    fun releasePlayer() {
        CoWatchMediaSessionAdapter.release()
    }

    override fun onCleared() {
        CoWatchMediaSessionAdapter.release()
        super.onCleared()
    }
}