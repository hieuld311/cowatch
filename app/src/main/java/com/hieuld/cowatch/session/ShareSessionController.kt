package com.hieuld.cowatch.session

import android.content.Context
import android.util.Log
import com.hieuld.cowatch.display.presentation.PresentationDisplayManager
import com.hieuld.cowatch.domain.sharing.CoWatchSession
import com.hieuld.cowatch.domain.sharing.CoWatchSessionStatus
import com.hieuld.cowatch.domain.sharing.ShareHostNotification
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ShareSessionController {

    private val _session = MutableStateFlow<CoWatchSession?>(null)
    val session: StateFlow<CoWatchSession?> = _session.asStateFlow()

    private val _hostNotifications = MutableSharedFlow<ShareHostNotification>(
        extraBufferCapacity = 4
    )
    val hostNotifications: SharedFlow<ShareHostNotification> = _hostNotifications

    private var presentationDisplayManager: PresentationDisplayManager? = null
    private var onAllDisplaysReady: ((Long) -> Unit)? = null
    private var acceptedNotificationSessionId: String? = null

    fun startSharing(
        context: Context,
        videoTitle: String,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long,
        renderEngine: VideoRenderEngine,
        onAllDisplaysReady: (Long) -> Unit
    ): Boolean {
        if (targetDisplayIds.isEmpty()) return false

        // Share startup anchors the single decoder before presentations attach their render outputs.
        val sessionId = UUID.randomUUID().toString()
        Log.i(
            TAG,
            "Starting share session $sessionId host=$hostDisplayId targets=$targetDisplayIds anchor=$anchorPositionMs"
        )

        val newSession = CoWatchSession(
            sessionId = sessionId,
            hostDisplayId = hostDisplayId,
            requestedDisplayIds = targetDisplayIds,
            participantDisplayIds = emptySet(),
            deniedDisplayIds = emptySet(),
            readyDisplayIds = emptySet(),
            anchorPositionMs = anchorPositionMs,
            status = CoWatchSessionStatus.PREPARING_SHARE
        )

        _session.value = newSession
        this.onAllDisplaysReady = onAllDisplaysReady

        val displayManager = PresentationDisplayManager(
            context = context,
            videoTitle = videoTitle,
            renderEngine = renderEngine,
            onDisplayAccepted = ::markDisplayAccepted,
            onDisplayDenied = ::markDisplayDenied,
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
                requestedDisplayIds = launchedDisplayIds,
                participantDisplayIds = current.participantDisplayIds.intersect(launchedDisplayIds),
                deniedDisplayIds = current.deniedDisplayIds.intersect(launchedDisplayIds),
                readyDisplayIds = current.readyDisplayIds.intersect(launchedDisplayIds)
            )
        }

        return true
    }

    fun markDisplayAccepted(displayId: Int) {
        val current = _session.value ?: return
        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (displayId !in current.requestedDisplayIds) return
        if (displayId in current.participantDisplayIds) return

        Log.i(TAG, "Display $displayId accepted share session ${current.sessionId}.")
        val updatedSession = current.copy(
            participantDisplayIds = current.participantDisplayIds + displayId,
            deniedDisplayIds = current.deniedDisplayIds - displayId
        )
        _session.value = updatedSession
        finalizeShareResponsesIfReady(updatedSession)
    }

    fun markDisplayDenied(displayId: Int) {
        val current = _session.value ?: return
        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (displayId !in current.requestedDisplayIds) return
        if (displayId in current.deniedDisplayIds) return

        Log.i(TAG, "Display $displayId denied share session ${current.sessionId}.")
        val updatedSession = current.copy(
            participantDisplayIds = current.participantDisplayIds - displayId,
            readyDisplayIds = current.readyDisplayIds - displayId,
            deniedDisplayIds = current.deniedDisplayIds + displayId
        )
        _session.value = updatedSession
        finalizeShareResponsesIfReady(updatedSession)
    }

    fun markDisplayReady(displayId: Int) {
        val current = _session.value ?: return

        // A display is ready only after its Presentation surface is accepted by the EGL fanout renderer.
        if (current.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (displayId !in current.participantDisplayIds) return
        if (displayId in current.readyDisplayIds) return

        Log.i(TAG, "Display $displayId ready for session ${current.sessionId}.")
        val updatedSession = current.copy(
            readyDisplayIds = current.readyDisplayIds + displayId
        )

        _session.value = updatedSession

        if (updatedSession.allResponsesReceived && updatedSession.allDisplaysReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    fun stopSharing(
        notifyEnded: Boolean = true
    ) {
        _session.value?.let { session ->
            Log.i(TAG, "Stopping share session ${session.sessionId}.")
            if (notifyEnded && session.status == CoWatchSessionStatus.PLAYING_SHARED) {
                _hostNotifications.tryEmit(ShareHostNotification.ENDED)
            }
        }
        acceptedNotificationSessionId = null
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
        if (displayId !in current.requestedDisplayIds) return

        Log.w(TAG, "Display $displayId removed from session ${current.sessionId}.")
        if (
            current.status == CoWatchSessionStatus.PREPARING_SHARE &&
            displayId !in current.participantDisplayIds &&
            displayId !in current.deniedDisplayIds
        ) {
            markDisplayDenied(displayId)
            return
        }

        val remainingDisplayIds = current.participantDisplayIds - displayId

        if (remainingDisplayIds.isEmpty()) {
            Log.w(TAG, "Ending session ${current.sessionId} because no participant displays remain.")
            if (current.participantDisplayIds.isNotEmpty()) {
                _hostNotifications.tryEmit(ShareHostNotification.ENDED)
            }
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

        if (updatedSession.allResponsesReceived && updatedSession.allDisplaysReady) {
            startSynchronizedPlayback(updatedSession)
        }
    }

    private fun finalizeShareResponsesIfReady(session: CoWatchSession) {
        if (!session.allResponsesReceived) return

        if (session.participantDisplayIds.isEmpty()) {
            Log.w(TAG, "Share session ${session.sessionId} denied by all displays.")
            acceptedNotificationSessionId = null
            _hostNotifications.tryEmit(ShareHostNotification.DENIED)
            val displayManager = presentationDisplayManager
            presentationDisplayManager = null
            onAllDisplaysReady = null
            _session.value = null
            displayManager?.dismissAll()
            return
        }

        if (acceptedNotificationSessionId != session.sessionId) {
            acceptedNotificationSessionId = session.sessionId
            _hostNotifications.tryEmit(ShareHostNotification.ACCEPTED)
        }

        if (session.allDisplaysReady) {
            startSynchronizedPlayback(session)
        }
    }

    private fun startSynchronizedPlayback(session: CoWatchSession) {
        if (session.status != CoWatchSessionStatus.PREPARING_SHARE) return
        if (!session.allResponsesReceived) return

        // All outputs are attached; now the host resumes the one player at the saved anchor position.
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
