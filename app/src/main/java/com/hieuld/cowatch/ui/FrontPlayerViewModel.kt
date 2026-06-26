package com.hieuld.cowatch.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.hieuld.cowatch.cowatch.CoWatchSessionManager
import com.hieuld.cowatch.render.VideoRenderEngine

class FrontPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val session = CoWatchSessionManager.session

    fun startSharing(
        context: Context,
        hostDisplayId: Int,
        targetDisplayIds: Set<Int>,
        anchorPositionMs: Long,
        renderEngine: VideoRenderEngine,
        onAllDisplaysReady: (Long) -> Unit
    ): Boolean {
        return CoWatchSessionManager.startSharing(
            context = context,
            hostDisplayId = hostDisplayId,
            targetDisplayIds = targetDisplayIds,
            anchorPositionMs = anchorPositionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = onAllDisplaysReady
        )
    }

    fun stopSharing() {
        CoWatchSessionManager.stopSharing()
    }

}
