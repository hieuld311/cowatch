package com.hieuld.cowatch.ui

import androidx.lifecycle.ViewModel
import com.hieuld.cowatch.cowatch.CoWatchSession
import com.hieuld.cowatch.cowatch.CoWatchSessionManager
import com.hieuld.cowatch.player.PlaybackManager

class ReceiverPlayerViewModel : ViewModel() {

    val playbackState = PlaybackManager.state
    val session = CoWatchSessionManager.session

    // Receiver readiness is session state, not playback-control authority.
    fun markReceiverReady(displayId: Int) {
        CoWatchSessionManager.markReceiverReady(displayId)
    }

    // A receiver should close when the session ends or no longer targets its display.
    fun shouldFinish(
        session: CoWatchSession?,
        expectedSessionId: String?,
        expectedDisplayId: Int
    ): Boolean {
        return session == null ||
                session.sessionId != expectedSessionId ||
                !session.participantDisplayIds.contains(expectedDisplayId)
    }

}
