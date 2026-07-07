package com.hieuld.cowatch.session

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.domain.playback.CoWatchPlaybackState
import com.hieuld.cowatch.domain.sharing.CoWatchSession
import com.hieuld.cowatch.domain.sharing.ShareHostNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class HostSharePlaybackController(
    private val player: ExoPlayer,
    private val coroutineScope: CoroutineScope,
    private val sessionProvider: () -> CoWatchSession?,
    private val setPlaybackState: (CoWatchPlaybackState) -> Unit
) {
    private var pendingSharedStartJob: Job? = null
    private var sharedStartGeneration = 0
    private var resumeAfterShareDialogCancel = false

    fun pauseForShareDialog() {
        resumeAfterShareDialogCancel = player.isPlaying
        if (resumeAfterShareDialogCancel) {
            player.pause()
            updatePlaybackStateFromPlayer()
        }
    }

    fun resumeAfterShareDialogCancel() {
        if (resumeAfterShareDialogCancel) {
            player.play()
        }
        clearPendingShareDialogResume()
    }

    fun clearPendingShareDialogResume() {
        resumeAfterShareDialogCancel = false
    }

    fun prepareShareAtCurrentPosition(): SharePlaybackAnchor {
        val anchorPositionMs = player.currentPosition
        val wasPlayingBeforeShare = player.isPlaying
        pauseAtShareAnchor(anchorPositionMs)
        setPlaybackState(CoWatchPlaybackState.SharingPreparing)
        Log.i(
            TAG,
            "Preparing share at $anchorPositionMs; player paused until accepted displays are ready."
        )
        return SharePlaybackAnchor(
            positionMs = anchorPositionMs,
            wasPlayingBeforeShare = wasPlayingBeforeShare
        )
    }

    fun restorePlaybackAfterShareLaunchFailure(wasPlayingBeforeShare: Boolean) {
        clearPendingShareDialogResume()
        setPlaybackState(
            if (wasPlayingBeforeShare) {
                CoWatchPlaybackState.Playing
            } else {
                CoWatchPlaybackState.Paused
            }
        )

        if (wasPlayingBeforeShare) {
            player.play()
        }
    }

    fun handleHostNotification(notification: ShareHostNotification): Boolean {
        return when (notification) {
            ShareHostNotification.ACCEPTED -> {
                resumeHostPlaybackAfterShareResponses()
                false
            }
            ShareHostNotification.DENIED -> {
                cancelPendingSharedStart()
                resumeHostPlaybackAfterShareResponses()
                true
            }
            ShareHostNotification.ENDED -> {
                updatePlaybackStateFromPlayer()
                false
            }
        }
    }

    fun startSharedPlaybackAfterDisplaysReady(startPositionMs: Long) {
        val generation = ++sharedStartGeneration
        pendingSharedStartJob?.cancel()
        pendingSharedStartJob = coroutineScope.launch {
            if (player.isPlaying) {
                pendingSharedStartJob = null
                setPlaybackState(CoWatchPlaybackState.Sharing)
                Log.i(TAG, "Share displays ready; host playback already resumed at ${player.currentPosition}.")
                return@launch
            }

            Log.i(TAG, "All share displays ready; priming player at $startPositionMs.")
            player.playWhenReady = false
            player.pause()

            if (abs(player.currentPosition - startPositionMs) > SHARED_START_SEEK_TOLERANCE_MS) {
                player.seekTo(startPositionMs)
            }

            var waitedMs = 0L
            while (
                isActive &&
                generation == sharedStartGeneration &&
                player.playbackState == Player.STATE_BUFFERING &&
                waitedMs < SHARED_START_READY_TIMEOUT_MS
            ) {
                delay(SHARED_START_READY_POLL_MS)
                waitedMs += SHARED_START_READY_POLL_MS
            }

            delay(SHARED_START_PREROLL_MS)

            if (
                !isActive ||
                generation != sharedStartGeneration ||
                sessionProvider() == null
            ) {
                return@launch
            }

            pendingSharedStartJob = null
            Log.i(TAG, "Starting shared playback at ${player.currentPosition}.")
            player.play()
            setPlaybackState(CoWatchPlaybackState.Sharing)
        }
    }

    fun cancelPendingSharedStart() {
        sharedStartGeneration += 1
        pendingSharedStartJob?.cancel()
        pendingSharedStartJob = null
    }

    fun updatePlaybackStateFromPlayer() {
        setPlaybackState(
            when {
                sessionProvider() != null && player.isPlaying -> CoWatchPlaybackState.Sharing
                player.isPlaying -> CoWatchPlaybackState.Playing
                else -> CoWatchPlaybackState.Paused
            }
        )
    }

    fun release() {
        cancelPendingSharedStart()
        clearPendingShareDialogResume()
    }

    private fun pauseAtShareAnchor(anchorPositionMs: Long) {
        cancelPendingSharedStart()
        player.playWhenReady = false
        player.pause()
        player.seekTo(anchorPositionMs)
    }

    private fun resumeHostPlaybackAfterShareResponses() {
        clearPendingShareDialogResume()
        player.play()
        updatePlaybackStateFromPlayer()
    }

    companion object {
        private const val TAG = "HostSharePlayback"
        private const val SHARED_START_SEEK_TOLERANCE_MS = 100L
        private const val SHARED_START_READY_TIMEOUT_MS = 1_000L
        private const val SHARED_START_READY_POLL_MS = 25L
        private const val SHARED_START_PREROLL_MS = 100L
    }
}

data class SharePlaybackAnchor(
    val positionMs: Long,
    val wasPlayingBeforeShare: Boolean
)
