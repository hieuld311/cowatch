package com.hieuld.cowatch.ui

import androidx.lifecycle.ViewModel
import com.hieuld.cowatch.cowatch.CoWatchMediaSessionAdapter
import com.hieuld.cowatch.cowatch.CoWatchSession
import com.hieuld.cowatch.cowatch.CoWatchSessionManager

class ReceiverPlayerViewModel : ViewModel() {

    val playbackState = CoWatchMediaSessionAdapter.state
    val session = CoWatchSessionManager.session

    fun markReceiverReady(displayId: Int) {
        CoWatchSessionManager.markReceiverReady(displayId)
    }

    fun shouldFinish(
        session: CoWatchSession?,
        expectedSessionId: String?,
        expectedDisplayId: Int
    ): Boolean {
        if (expectedDisplayId == -1) return true
        if (session == null) return true

        if (expectedSessionId != null && session.sessionId != expectedSessionId) {
            return true
        }

        return expectedDisplayId !in session.participantDisplayIds
    }
}