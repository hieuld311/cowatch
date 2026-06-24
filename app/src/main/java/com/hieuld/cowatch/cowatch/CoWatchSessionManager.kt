package com.hieuld.cowatch.cowatch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.hieuld.cowatch.player.PlaybackManager
import com.hieuld.cowatch.ui.ReceiverActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object CoWatchSessionManager {

    private const val START_DELAY_MS = 1_500L

    private val _session = MutableStateFlow<CoWatchSession?>(null)
    val session: StateFlow<CoWatchSession?> = _session.asStateFlow()

    fun startSharing(
        context: Context,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long
    ) {
        val playbackState = PlaybackManager.state.value

        if (playbackState.mediaUrl.isBlank()) {
            return
        }

        if (targetDisplayIds.isEmpty()) {
            return
        }

        val sessionId = UUID.randomUUID().toString()
        val newSession = CoWatchSession(
            sessionId = sessionId,
            hostDisplayId = hostDisplayId,
            participantDisplayIds = targetDisplayIds,
            readyDisplayIds = emptySet(),
            mediaUrl = playbackState.mediaUrl,
            anchorPositionMs = anchorPositionMs,
            status = CoWatchSessionStatus.PREPARING_SHARE
        )

        _session.value = newSession

        targetDisplayIds.forEach { displayId ->
            launchReceiverOnDisplay(
                context = context,
                sessionId = sessionId,
                displayId = displayId,
                anchorPositionMs = anchorPositionMs
            )
        }

    }

    private fun launchReceiverOnDisplay(
        context: Context,
        sessionId: String,
        displayId: Int,
        anchorPositionMs: Long
    ) {
        try {
            val intent = Intent(context, ReceiverActivity::class.java).apply {
                putExtra(ReceiverActivity.EXTRA_SESSION_ID, sessionId)
                putExtra(ReceiverActivity.EXTRA_DISPLAY_ID, displayId)
                putExtra(ReceiverActivity.EXTRA_ANCHOR_POSITION_MS, anchorPositionMs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId)
                .toBundle()

            context.startActivity(intent, options)
        } catch (e: Exception) {
            // If launch fails, the session remains preparatory and can be cancelled by the host.
        }
    }

    fun markReceiverReady(displayId: Int) {
        val current = _session.value ?: return

        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) {
            return
        }

        if (!current.participantDisplayIds.contains(displayId)) {
            return
        }

        val updatedReadyDisplays = current.readyDisplayIds + displayId
        val updatedSession = current.copy(readyDisplayIds = updatedReadyDisplays)

        _session.value = updatedSession

        if (updatedSession.allReceiversReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    private fun startSynchronizedPlayback(session: CoWatchSession) {
        val startAtElapsedRealtimeMs = SystemClock.elapsedRealtime() + START_DELAY_MS

        PlaybackManager.synchronizedStart(
            positionMs = session.anchorPositionMs,
            startAtElapsedRealtimeMs = startAtElapsedRealtimeMs
        )

        _session.value = session.copy(status = CoWatchSessionStatus.PLAYING_SHARED)
    }

    fun stopSharing() {
        _session.value = null
    }
}
