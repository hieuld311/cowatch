package com.hieuld.cowatch.cowatch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
    ): Boolean {
        if (targetDisplayIds.isEmpty()) return false

        val playbackState = CoWatchMediaSessionAdapter.state.value
        if (playbackState.mediaUri.isBlank()) return false

        val sessionId = UUID.randomUUID().toString()

        CoWatchMediaSessionAdapter.beginCoWatchSession(sessionId)

        val newSession = CoWatchSession(
            sessionId = sessionId,
            hostDisplayId = hostDisplayId,
            participantDisplayIds = targetDisplayIds,
            readyDisplayIds = emptySet(),
            mediaUri = playbackState.mediaUri,
            anchorPositionMs = anchorPositionMs,
            status = CoWatchSessionStatus.PREPARING_SHARE
        )

        _session.value = newSession

        val launchedDisplayIds = targetDisplayIds.filterTo(mutableSetOf()) { displayId ->
            launchReceiverOnDisplay(
                context = context.applicationContext,
                session = newSession,
                displayId = displayId
            )
        }

        if (launchedDisplayIds.isEmpty()) {
            _session.value = null
            CoWatchMediaSessionAdapter.endCoWatchSession()
            return false
        }

        if (launchedDisplayIds != targetDisplayIds) {
            _session.value = newSession.copy(
                participantDisplayIds = launchedDisplayIds
            )
        }

        return true
    }

    fun markReceiverReady(displayId: Int) {
        val current = _session.value ?: return

        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (displayId !in current.participantDisplayIds) return
        if (displayId in current.readyDisplayIds) return

        val updatedSession = current.copy(
            readyDisplayIds = current.readyDisplayIds + displayId
        )

        _session.value = updatedSession

        if (updatedSession.allReceiversReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    fun stopSharing() {
        _session.value = null
        CoWatchMediaSessionAdapter.endCoWatchSession()
    }

    fun isSharing(): Boolean {
        return _session.value != null
    }

    fun getCurrentSession(): CoWatchSession? {
        return _session.value
    }

    private fun startSynchronizedPlayback(session: CoWatchSession) {
        if (session.status != CoWatchSessionStatus.PREPARING_SHARE) return

        val startAtElapsedRealtimeMs =
            SystemClock.elapsedRealtime() + START_DELAY_MS

        CoWatchMediaSessionAdapter.publishSynchronizedStart(
            positionMs = session.anchorPositionMs,
            startAtElapsedRealtimeMs = startAtElapsedRealtimeMs
        )

        _session.value = session.copy(
            status = CoWatchSessionStatus.PLAYING_SHARED
        )
    }

    private fun launchReceiverOnDisplay(
        context: Context,
        session: CoWatchSession,
        displayId: Int
    ): Boolean {
        return try {
            val intent = Intent(context, ReceiverActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ReceiverActivity.EXTRA_SESSION_ID, session.sessionId)
                putExtra(ReceiverActivity.EXTRA_DISPLAY_ID, displayId)
                putExtra(
                    ReceiverActivity.EXTRA_ANCHOR_POSITION_MS,
                    session.anchorPositionMs
                )
            }

            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId)
                .toBundle()

            context.startActivity(intent, options)
            true
        } catch (_: Exception) {
            false
        }
    }
}
