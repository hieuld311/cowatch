package com.hieuld.cowatch.cowatch

import android.content.Context
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
            _session.value = null
            presentationDisplayManager?.dismissAll()
            presentationDisplayManager = null
            this.onAllDisplaysReady = null
            return false
        }

        if (launchedDisplayIds != targetDisplayIds) {
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

        val updatedSession = current.copy(
            readyDisplayIds = current.readyDisplayIds + displayId
        )

        _session.value = updatedSession

        if (updatedSession.allDisplaysReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    fun stopSharing() {
        presentationDisplayManager?.dismissAll()
        presentationDisplayManager = null
        onAllDisplaysReady = null
        _session.value = null
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

        val remainingDisplayIds = current.participantDisplayIds - displayId

        if (remainingDisplayIds.isEmpty()) {
            onAllDisplaysReady = null
            _session.value = null
            return
        }

        _session.value = current.copy(
            participantDisplayIds = remainingDisplayIds,
            readyDisplayIds = current.readyDisplayIds - displayId
        )
    }

    private fun startSynchronizedPlayback(session: CoWatchSession) {
        if (session.status != CoWatchSessionStatus.PREPARING_SHARE) return

        _session.value = session.copy(
            status = CoWatchSessionStatus.PLAYING_SHARED
        )
        onAllDisplaysReady?.invoke(session.anchorPositionMs)
    }

}
