package com.hieuld.cowatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hieuld.cowatch.cowatch.CoWatchSession
import com.hieuld.cowatch.cowatch.CoWatchSessionManager
import com.hieuld.cowatch.player.PlaybackManager
import com.hieuld.cowatch.sync.PlaybackSyncConfig

class ReceiverPlayerViewModel(application: Application) : AndroidViewModel(application) {

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

    // Production calibration can seed this value; there is no debug UI in this phase.
    fun getAudioLatencyOffsetMs(displayId: Int): Long {
        return PlaybackSyncConfig.getAudioLatencyOffsetMs(
            context = getApplication<Application>().applicationContext,
            displayId = displayId
        )
    }
}
