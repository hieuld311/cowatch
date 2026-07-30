package com.ivi.pid.sharing

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Surface
import com.ivi.common.ipc.IPidPlaybackCoordinator
import com.ivi.common.ipc.IRearPlaybackReceiver
import com.ivi.common.ipc.ReceiverRegistration
import com.ivi.common.ipc.ReceiverStatus
import com.ivi.common.ipc.ShareProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PidPlaybackShareService : Service() {
    @Inject lateinit var coordinator: PidShareCoordinator

    private val binder = object : IPidPlaybackCoordinator.Stub() {
        override fun getProtocolVersion(): Int = ShareProtocol.VERSION

        override fun registerReceiver(
            registration: ReceiverRegistration,
            receiver: IRearPlaybackReceiver
        ) = secured { coordinator.registerReceiver(registration, receiver) }

        override fun unregisterReceiver(role: String, receiver: IRearPlaybackReceiver) =
            secured { coordinator.unregisterReceiver(role, receiver) }

        override fun updateReceiverStatus(status: ReceiverStatus) =
            secured { coordinator.updateReceiverStatus(status) }

        override fun registerRenderSurface(
            role: String,
            sessionId: String,
            surfaceGeneration: Long,
            surface: Surface,
            width: Int,
            height: Int
        ) = secured {
            coordinator.registerRenderSurface(
                role,
                sessionId,
                surfaceGeneration,
                surface,
                width,
                height
            )
        }

        override fun unregisterRenderSurface(
            role: String,
            sessionId: String,
            surfaceGeneration: Long
        ) = secured {
            coordinator.unregisterRenderSurface(role, sessionId, surfaceGeneration)
        }

        override fun requestLeaveSharing(role: String, sessionId: String) =
            secured { coordinator.stopTarget(role, "Rear target requested leave") }

        private inline fun secured(block: () -> Unit) {
            enforceCallingOrSelfPermission(
                ShareProtocol.SIGNATURE_PERMISSION,
                "Caller is not an approved CoWatch application"
            )
            block()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
