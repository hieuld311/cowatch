package com.hieuld.cowatch.cowatch

import android.content.Context
import android.util.Log
import com.hieuld.cowatch.display.PresentationDisplayManager
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object CoWatchSessionManager {

    private val _session = MutableStateFlow<CoWatchSession?>(null)
    val session: StateFlow<CoWatchSession?> = _session.asStateFlow()

    private var presentationDisplayManager: PresentationDisplayManager? = null
    private var onAllDisplaysReady: ((Long) -> Unit)? = null

    fun startSharing(
        context: Context,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long,
        renderEngine: VideoRenderEngine,
        onAllDisplaysReady: (Long) -> Unit
    ): Boolean {
        if (targetDisplayIds.isEmpty()) return false

        val sessionId = UUID.randomUUID().toString()
        Log.i(
            TAG,
            "Starting share session $sessionId host=$hostDisplayId targets=$targetDisplayIds anchor=$anchorPositionMs"
        )

        val newSession = CoWatchSession(
            sessionId = sessionId,
            hostDisplayId = hostDisplayId,
            participantDisplayIds = targetDisplayIds,
            readyDisplayIds = emptySet(),
            anchorPositionMs = anchorPositionMs,
            status = CoWatchSessionStatus.PREPARING_SHARE
        )

        _session.value = newSession
        this.onAllDisplaysReady = onAllDisplaysReady

        val displayManager = PresentationDisplayManager(
            context = context,
            renderEngine = renderEngine,
            onDisplayReady = ::markDisplayReady,
            onDisplayRemoved = ::handlePresentationDisplayRemoved
        )
        presentationDisplayManager = displayManager

        val launchedDisplayIds = displayManager.show(targetDisplayIds)

        if (launchedDisplayIds.isEmpty()) {
            Log.w(TAG, "Share session $sessionId failed because no presentation launched.")
            _session.value = null
            presentationDisplayManager?.dismissAll()
            presentationDisplayManager = null
            this.onAllDisplaysReady = null
            return false
        }

        if (launchedDisplayIds != targetDisplayIds) {
            Log.w(
                TAG,
                "Share session $sessionId launched partial displays=$launchedDisplayIds requested=$targetDisplayIds"
            )
            val current = _session.value ?: newSession
            _session.value = current.copy(
                participantDisplayIds = launchedDisplayIds,
                readyDisplayIds = current.readyDisplayIds.intersect(launchedDisplayIds)
            )
        }

        return true
    }

    fun markDisplayReady(displayId: Int) {
        val current = _session.value ?: return

        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (displayId !in current.participantDisplayIds) return
        if (displayId in current.readyDisplayIds) return

        Log.i(TAG, "Display $displayId ready for session ${current.sessionId}.")
        val updatedSession = current.copy(
            readyDisplayIds = current.readyDisplayIds + displayId
        )

        _session.value = updatedSession

        if (updatedSession.allDisplaysReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    fun stopSharing() {
        _session.value?.let { session ->
            Log.i(TAG, "Stopping share session ${session.sessionId}.")
        }
        val displayManager = presentationDisplayManager
        presentationDisplayManager = null
        onAllDisplaysReady = null
        _session.value = null
        displayManager?.dismissAll()
    }

    fun isSharing(): Boolean {
        return _session.value != null
    }

    fun getCurrentSession(): CoWatchSession? {
        return _session.value
    }

    private fun handlePresentationDisplayRemoved(displayId: Int) {
        val current = _session.value ?: return
        if (displayId !in current.participantDisplayIds) return

        Log.w(TAG, "Display $displayId removed from session ${current.sessionId}.")
        val remainingDisplayIds = current.participantDisplayIds - displayId

        if (remainingDisplayIds.isEmpty()) {
            Log.w(TAG, "Ending session ${current.sessionId} because no participant displays remain.")
            val displayManager = presentationDisplayManager
            presentationDisplayManager = null
            onAllDisplaysReady = null
            _session.value = null
            displayManager?.dismissAll()
            return
        }

        val updatedSession = current.copy(
            participantDisplayIds = remainingDisplayIds,
            readyDisplayIds = current.readyDisplayIds - displayId
        )
        _session.value = updatedSession

        if (updatedSession.allDisplaysReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    private fun startSynchronizedPlayback(session: CoWatchSession) {
        if (session.status != CoWatchSessionStatus.PREPARING_SHARE) return

        Log.i(
            TAG,
            "All displays ready for session ${session.sessionId}; resume at ${session.anchorPositionMs}."
        )
        _session.value = session.copy(
            status = CoWatchSessionStatus.PLAYING_SHARED
        )
        onAllDisplaysReady?.invoke(session.anchorPositionMs)
    }

    private const val TAG = "CoWatchSession"

}
