package com.ivi.rear.sharing

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.ivi.common.ipc.IPidPlaybackCoordinator
import com.ivi.common.ipc.IRearPlaybackReceiver
import com.ivi.common.ipc.ReceiverRegistration
import com.ivi.common.ipc.ReceiverState
import com.ivi.common.ipc.ReceiverStatus
import com.ivi.common.ipc.ShareProtocol
import com.ivi.common.ipc.SharedSessionSnapshot
import com.ivi.common.playback.LocalPlaybackSnapshot
import com.ivi.common.playback.LocalPlaybackDestination
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.rear.app.RearRole
import com.ivi.rear.ui.FrontPlayerActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class RearShareClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackController: Media3PlaybackController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _sharedSession = MutableStateFlow<SharedSessionSnapshot?>(null)
    val sharedSession: StateFlow<SharedSessionSnapshot?> = _sharedSession.asStateFlow()
    private val _pendingShareRequest = MutableStateFlow<PendingShareRequest?>(null)
    val pendingShareRequest: StateFlow<PendingShareRequest?> = _pendingShareRequest.asStateFlow()
    private val _shareExit = MutableSharedFlow<LocalPlaybackDestination>(extraBufferCapacity = 1)
    val shareExit: SharedFlow<LocalPlaybackDestination> = _shareExit.asSharedFlow()
    private var coordinator: IPidPlaybackCoordinator? = null
    // ApplicationContext has no authoritative display. Registration waits for an Activity to
    // supply its display and confirm that the first UI frame is ready.
    private var displayId: Int? = null
    private var receiverUiReady = false
    private var lastSequence = -1L
    private var localPlaybackSnapshot: LocalPlaybackSnapshot? = null
    private var registeredSurfaceSessionId: String? = null
    private var registeredSurfaceGeneration = 0L
    private var returnToLibraryRequested = false
    private var autoAcceptJob: Job? = null
    private var bound = false

    private val receiver = object : IRearPlaybackReceiver.Stub() {
        override fun onShareRequest(snapshot: SharedSessionSnapshot) {
            scope.launch { receiveShareRequest(snapshot) }
        }

        override fun onSharedSession(snapshot: SharedSessionSnapshot) {
            scope.launch { applySnapshot(snapshot) }
        }

        override fun onStopSharing(sessionId: String, reason: String) {
            scope.launch {
                if (_pendingShareRequest.value?.snapshot?.sessionId == sessionId || sessionId.isBlank()) {
                    clearPendingRequest()
                }
                if (_sharedSession.value?.sessionId == sessionId || sessionId.isBlank()) {
                    leaveSharedMode(reason, notifyHost = false)
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            coordinator = IPidPlaybackCoordinator.Stub.asInterface(service)
            if (coordinator?.protocolVersion != ShareProtocol.VERSION) {
                Log.e(TAG, "PID share protocol version mismatch")
                return
            }
            registerReady()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            coordinator = null
            bound = false
            scope.launch {
                clearPendingRequest()
                if (_sharedSession.value != null) {
                    leaveSharedMode("PID disconnected", notifyHost = false)
                }
            }
        }

        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
    }

    fun connect() {
        if (bound) return
        val intent = Intent().setComponent(
            ComponentName(ShareProtocol.PID_PACKAGE, ShareProtocol.HOST_SERVICE_CLASS)
        )
        bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "PID service unavailable; standalone mode remains active", it) }
            .getOrDefault(false)
    }

    fun updateDisplay(displayId: Int) {
        if (this.displayId == displayId && coordinator != null) return
        this.displayId = displayId
        connect()
        registerReady()
    }

    fun markReceiverUiReady(displayId: Int) {
        this.displayId = displayId
        receiverUiReady = true
        connect()
        registerReady()
    }

    fun onPendingRequestDialogShown(sessionId: String) {
        scope.launch {
            val request = _pendingShareRequest.value ?: return@launch
            if (request.snapshot.sessionId != sessionId ||
                request.deadlineElapsedRealtimeMs != null
            ) {
                return@launch
            }

            val startedRequest = request.copy(
                deadlineElapsedRealtimeMs =
                    SystemClock.elapsedRealtime() + request.countdownDurationMs
            )
            _pendingShareRequest.value = startedRequest
            autoAcceptJob?.cancel()
            autoAcceptJob = scope.launch {
                delay(startedRequest.countdownDurationMs)
                if (_pendingShareRequest.value?.snapshot?.sessionId == sessionId) {
                    acceptPendingRequestInternal()
                }
            }
        }
    }

    fun requestLeaveSharing(returnToLibrary: Boolean = true) {
        val sessionId = _sharedSession.value?.sessionId ?: return
        returnToLibraryRequested = returnToLibrary
        runCatching { coordinator?.requestLeaveSharing(RearRole.current, sessionId) }
        scope.launch { leaveSharedMode("Rear target closed sharing", notifyHost = false) }
    }

    fun acceptPendingRequest() {
        scope.launch { acceptPendingRequestInternal() }
    }

    fun dismissPendingRequest() {
        scope.launch {
            val request = _pendingShareRequest.value ?: return@launch
            clearPendingRequest()
            reportState(ReceiverState.DENIED, request.snapshot.sessionId)
        }
    }

    fun dismissBootstrapRequest(onReleased: () -> Unit) {
        scope.launch {
            val request = _pendingShareRequest.value ?: return@launch
            clearPendingRequest()
            reportState(ReceiverState.DENIED, request.snapshot.sessionId)
            releaseBootstrapReceiver()
            onReleased()
        }
    }

    fun registerRenderSurface(generation: Long, surface: Surface, width: Int, height: Int) {
        val session = _sharedSession.value ?: return
        runCatching {
            coordinator?.registerRenderSurface(
                RearRole.current,
                session.sessionId,
                generation,
                surface,
                width,
                height
            )
            registeredSurfaceSessionId = session.sessionId
            registeredSurfaceGeneration = generation
        }.onFailure { error ->
            Log.e(TAG, "Unable to register Rear render surface", error)
            reportState(
                ReceiverState.FAILED,
                session.sessionId,
                "Unable to register render surface"
            )
        }
    }

    fun unregisterRenderSurface(generation: Long = registeredSurfaceGeneration) {
        val sessionId = registeredSurfaceSessionId ?: _sharedSession.value?.sessionId ?: return
        if (generation == 0L || generation != registeredSurfaceGeneration) return
        registeredSurfaceSessionId = null
        registeredSurfaceGeneration = 0L
        runCatching {
            coordinator?.unregisterRenderSurface(RearRole.current, sessionId, generation)
        }.onFailure { Log.w(TAG, "Unable to unregister Rear render surface", it) }
    }

    private fun registerReady() {
        if (!receiverUiReady) return
        val host = coordinator ?: return
        val actualDisplayId = displayId ?: return
        runCatching {
            host.registerReceiver(
                ReceiverRegistration(
                    role = RearRole.current,
                    displayId = actualDisplayId,
                    protocolVersion = ShareProtocol.VERSION,
                    ready = true
                ),
                receiver
            )
        }.onFailure { Log.e(TAG, "Unable to register rear receiver", it) }
    }

    private fun receiveShareRequest(snapshot: SharedSessionSnapshot) {
        if (snapshot.protocolVersion != ShareProtocol.VERSION) return
        if (_sharedSession.value?.sessionId == snapshot.sessionId) return
        if (_pendingShareRequest.value?.snapshot?.sessionId == snapshot.sessionId) return

        clearPendingRequest()
        _pendingShareRequest.value = PendingShareRequest(snapshot = snapshot)
        reportState(ReceiverState.AWAITING_RESPONSE, snapshot.sessionId)
    }

    private fun acceptPendingRequestInternal() {
        val request = _pendingShareRequest.value ?: return
        clearPendingRequest()
        localPlaybackSnapshot = playbackController.suspendForSharedRendering()
        playbackController.setSharedMode(true)
        _sharedSession.value = request.snapshot
        reportState(ReceiverState.ACCEPTED, request.snapshot.sessionId)
        launchSharedPlayer()
    }

    private fun applySnapshot(snapshot: SharedSessionSnapshot) {
        if (snapshot.protocolVersion != ShareProtocol.VERSION || snapshot.sequence < lastSequence) return
        if (_sharedSession.value?.sessionId != snapshot.sessionId) return
        lastSequence = snapshot.sequence
        _sharedSession.value = snapshot
        reportState(ReceiverState.SHARING, snapshot.sessionId)
    }

    private fun launchSharedPlayer() {
        val actualDisplayId = displayId ?: run {
            leaveSharedMode("Rear Activity has no display", notifyHost = true)
            return
        }
        val intent = Intent(context, FrontPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(FrontPlayerActivity.EXTRA_SHARED_MODE, true)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(actualDisplayId)
        context.startActivity(intent, options.toBundle())
    }

    private fun leaveSharedMode(reason: String, notifyHost: Boolean) {
        clearPendingRequest()
        val sessionId = _sharedSession.value?.sessionId
        unregisterRenderSurface()
        _sharedSession.value = null
        lastSequence = -1L
        val localSnapshot = localPlaybackSnapshot ?: playbackController.suspendForSharedRendering()
        localPlaybackSnapshot = null
        val restoreSnapshot = if (returnToLibraryRequested) {
            localSnapshot.copy(
                destination = if (localSnapshot.source != null) {
                    LocalPlaybackDestination.LIBRARY_PIP
                } else {
                    LocalPlaybackDestination.LIBRARY_IDLE
                }
            )
        } else {
            localSnapshot
        }
        returnToLibraryRequested = false
        val destination = playbackController.restoreAfterSharedRendering(restoreSnapshot)
        _shareExit.tryEmit(destination)
        if (notifyHost && sessionId != null) {
            runCatching { coordinator?.requestLeaveSharing(RearRole.current, sessionId) }
        }
        reportState(ReceiverState.READY, null)
        Log.i(TAG, "Returned to local idle: $reason")
    }

    private fun clearPendingRequest() {
        autoAcceptJob?.cancel()
        autoAcceptJob = null
        _pendingShareRequest.value = null
    }

    private fun releaseBootstrapReceiver() {
        receiverUiReady = false
        runCatching {
            coordinator?.unregisterReceiver(RearRole.current, receiver)
        }.onFailure { Log.w(TAG, "Unable to unregister bootstrap receiver", it) }
        coordinator = null
        displayId = null
        if (bound) {
            runCatching {
                context.unbindService(connection)
            }.onFailure { Log.w(TAG, "Unable to unbind bootstrap receiver", it) }
        }
        bound = false
    }

    private fun reportState(state: String, sessionId: String?, errorMessage: String? = null) {
        val actualDisplayId = displayId ?: return
        runCatching {
            coordinator?.updateReceiverStatus(
                ReceiverStatus(
                    role = RearRole.current,
                    displayId = actualDisplayId,
                    state = state,
                    sessionId = sessionId,
                    errorMessage = errorMessage
                )
            )
        }
    }

    private companion object {
        const val TAG = "RearShareClient"
    }
}

data class PendingShareRequest(
    val snapshot: SharedSessionSnapshot,
    val countdownDurationMs: Long = REQUEST_AUTO_ACCEPT_DELAY_MS,
    val deadlineElapsedRealtimeMs: Long? = null
)

private const val REQUEST_AUTO_ACCEPT_DELAY_MS = 10_000L
