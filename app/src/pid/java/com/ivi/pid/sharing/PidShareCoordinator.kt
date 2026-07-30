package com.ivi.pid.sharing

import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.ivi.common.domain.VideoSource
import com.ivi.common.ipc.IRearPlaybackReceiver
import com.ivi.common.ipc.ReceiverRegistration
import com.ivi.common.ipc.ReceiverState
import com.ivi.common.ipc.ReceiverStatus
import com.ivi.common.ipc.ScreenRole
import com.ivi.common.ipc.ShareProtocol
import com.ivi.common.ipc.SharedSessionSnapshot
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.pid.rendering.PidRenderFanout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class PidShareCoordinator @Inject constructor(
    private val playbackController: Media3PlaybackController,
    private val renderFanout: PidRenderFanout,
    private val rearDisplayResolver: RearDisplayResolver,
    private val rearAppLauncher: RearAppLauncher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Every access to these structures is serialized through scope/Main.
    private val receivers = linkedMapOf<String, ReceiverRecord>()
    private val _targets = MutableStateFlow(ScreenRole.all.map(::RearTargetState))
    val targets: StateFlow<List<RearTargetState>> = _targets.asStateFlow()
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()
    private val _hostNotifications = MutableSharedFlow<HostShareNotification>(extraBufferCapacity = 4)
    val hostNotifications: SharedFlow<HostShareNotification> = _hostNotifications.asSharedFlow()

    private var activeSession: ActiveSession? = null
    private var startJob: Job? = null
    private var reconciliationJob: Job? = null
    private var sequence = 0L
    private var seekEventId = 0L
    private var suppressPlayerEvents = false

    init {
        playbackController.exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publishPlayerTruth()

            override fun onPlaybackStateChanged(playbackState: Int) = publishPlayerTruth()

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
                publishPlayerTruth()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
                publishPlayerTruth()

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) seekEventId += 1
                publishPlayerTruth()
            }
        })
        renderFanout.setRearOutputLostListener { role, reason ->
            scope.launch { handleRearOutputLost(role, reason) }
        }
    }

    fun refreshTargets(hostDisplayId: Int) {
        val session = activeSession
        _targets.value = ScreenRole.all.map { role ->
            val mappedDisplayId = rearDisplayResolver.resolveAvailableDisplayId(role)
            val registration = receivers[role]?.registration
            val target = RearTargetState(
                role = role,
                displayId = mappedDisplayId
            )
            when {
                mappedDisplayId == null -> target.copy(
                    state = ReceiverState.NOT_RUNNING,
                    errorMessage = "Rear display is unavailable or off"
                )
                mappedDisplayId == hostDisplayId -> target.copy(
                    state = ReceiverState.FAILED,
                    errorMessage = "Rear display is the PID host display"
                )
                registration != null && registration.displayId != mappedDisplayId -> target.copy(
                    state = ReceiverState.NOT_RUNNING,
                    errorMessage = "Rear app is registered on a different display"
                )
                session?.selectedRoles?.contains(role) == true -> target.copy(
                    state = when (session.phase) {
                        SharePhase.SHARING -> ReceiverState.SHARING
                        SharePhase.WAITING_SURFACES -> ReceiverState.ACCEPTED
                        SharePhase.WAITING_RESPONSES -> ReceiverState.AWAITING_RESPONSE
                        SharePhase.WAITING_RECEIVERS -> if (registration?.ready == true) {
                            ReceiverState.READY
                        } else {
                            ReceiverState.STARTING
                        }
                    },
                    errorMessage = null
                )
                registration == null -> target.copy(
                    state = ReceiverState.NOT_RUNNING,
                    errorMessage = null
                )
                else -> target.copy(
                    state = if (registration.ready) {
                        ReceiverState.READY
                    } else {
                        ReceiverState.STARTING
                    },
                    errorMessage = null
                )
            }
        }
    }

    fun registerReceiver(registration: ReceiverRegistration, receiver: IRearPlaybackReceiver) {
        scope.launch { registerReceiverInternal(registration, receiver) }
    }

    fun unregisterReceiver(role: String, receiver: IRearPlaybackReceiver) {
        scope.launch { unregisterReceiverInternal(role, receiver.asBinder()) }
    }

    fun updateReceiverStatus(status: ReceiverStatus) {
        scope.launch { updateReceiverStatusInternal(status) }
    }

    fun registerRenderSurface(
        role: String,
        sessionId: String,
        surfaceGeneration: Long,
        surface: Surface,
        width: Int,
        height: Int
    ) {
        scope.launch {
            registerRenderSurfaceInternal(
                role,
                sessionId,
                surfaceGeneration,
                surface,
                width,
                height
            )
        }
    }

    fun unregisterRenderSurface(role: String, sessionId: String, surfaceGeneration: Long) {
        scope.launch {
            unregisterRenderSurfaceInternal(role, sessionId, surfaceGeneration)
        }
    }

    fun startSharing(
        selectedRoles: Set<String>,
        source: VideoSource.Asset,
        hostDisplayId: Int,
        wasPlayingBeforeDialog: Boolean = playbackController.exoPlayer.isPlaying
    ): Boolean {
        finishSharingSession(
            reason = "Replaced by a new share session",
            notifyReceivers = true,
            notification = null
        )
        refreshTargets(hostDisplayId)
        val currentTargets = _targets.value.associateBy { it.role }
        val validRoles = selectedRoles.filterTo(linkedSetOf()) {
            currentTargets[it]?.selectable == true
        }
        if (validRoles.isEmpty()) return false

        val player = playbackController.exoPlayer
        val session = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            source = source,
            selectedRoles = validRoles,
            anchorPositionMs = player.currentPosition.coerceAtLeast(0L),
            wasPlaying = wasPlayingBeforeDialog
        )
        activeSession = session
        _sessionActive.value = true
        suppressPlayerEvents = true
        player.pause()
        suppressPlayerEvents = false

        validRoles.toList().forEach { role ->
            val target = currentTargets.getValue(role)
            val receiverReady = receivers[role]?.registration?.let { registration ->
                registration.ready && registration.displayId == target.displayId
            } == true
            if (receiverReady) {
                updateTarget(role, ReceiverState.READY)
            } else {
                updateTarget(role, ReceiverState.STARTING)
                val launchError = rearAppLauncher
                    .launch(role, target.displayId!!)
                    .exceptionOrNull()
                if (launchError != null) {
                    session.selectedRoles.remove(role)
                    updateTarget(
                        role,
                        ReceiverState.FAILED,
                        "Unable to start Rear app: ${launchError.message.orEmpty()}"
                    )
                    Log.e(
                        TAG,
                        "Unable to launch Rear app role=$role displayId=${target.displayId}",
                        launchError
                    )
                }
            }
        }
        if (session.selectedRoles.isEmpty()) {
            finishSharingSession(
                reason = "No Rear app could be started",
                notifyReceivers = false,
                notification = HostShareNotification.DENIED
            )
            return false
        }

        startJob = scope.launch { awaitReceiversAndStart(session) }
        return true
    }

    fun stopTarget(role: String, reason: String = "Target left sharing") {
        scope.launch { stopTargetInternal(role, reason, notifyReceiver = true) }
    }

    fun stopSharingAll(reason: String = "PID stopped sharing") {
        finishSharingSession(
            reason = reason,
            notifyReceivers = true,
            notification = activeSession?.takeIf { it.phase == SharePhase.SHARING }
                ?.let { HostShareNotification.ENDED }
        )
    }

    private fun registerReceiverInternal(
        registration: ReceiverRegistration,
        receiver: IRearPlaybackReceiver
    ) {
        val expectedDisplayId = RearDisplayConfig.displayId(registration.role)
        if (expectedDisplayId == null || registration.displayId != expectedDisplayId) {
            updateTarget(
                registration.role,
                ReceiverState.FAILED,
                "Rear app registered on unexpected display ${registration.displayId}"
            )
            runCatching {
                receiver.onStopSharing(
                    "",
                    "Expected display $expectedDisplayId for role ${registration.role}"
                )
            }
            return
        }
        if (registration.protocolVersion != ShareProtocol.VERSION) {
            updateTarget(registration.role, ReceiverState.FAILED, "Protocol version mismatch")
            runCatching { receiver.onStopSharing("", "Protocol version mismatch") }
            return
        }

        receivers.remove(registration.role)?.let(::unlinkDeathRecipient)
        val binder = receiver.asBinder()
        val deathRecipient = IBinder.DeathRecipient {
            scope.launch { handleReceiverDisconnected(registration.role, binder) }
        }
        val record = ReceiverRecord(registration, receiver, deathRecipient)
        receivers[registration.role] = record
        runCatching { binder.linkToDeath(deathRecipient, 0) }.onFailure { error ->
            Log.w(TAG, "Unable to monitor Rear binder ${registration.role}", error)
        }
        updateTarget(
            registration.role,
            if (registration.ready) ReceiverState.READY else ReceiverState.STARTING,
            displayId = registration.displayId
        )

        activeSession?.takeIf { registration.role in it.selectedRoles }?.let { session ->
            when {
                session.phase == SharePhase.SHARING && registration.role in session.acceptedRoles -> {
                    sendSnapshot(registration.role, session)
                    updateTarget(registration.role, ReceiverState.ACCEPTED)
                }
                session.requestSent && registration.role !in session.respondedRoles -> {
                    sendShareRequest(registration.role, session)
                    updateTarget(registration.role, ReceiverState.AWAITING_RESPONSE)
                }
            }
        }
        Log.i(TAG, "Receiver registered role=${registration.role} displayId=${registration.displayId}")
    }

    private fun unregisterReceiverInternal(role: String, binder: IBinder) {
        val current = receivers[role] ?: return
        if (current.receiver.asBinder() != binder) return
        receivers.remove(role)
        unlinkDeathRecipient(current)
        handleCurrentReceiverRemoved(role)
    }

    private fun updateReceiverStatusInternal(status: ReceiverStatus) {
        if (receivers[status.role]?.registration?.displayId != status.displayId) return
        updateTarget(status.role, status.state, status.errorMessage, status.displayId)
        val session = activeSession ?: return
        if (status.sessionId != session.sessionId || status.role !in session.selectedRoles) return

        when (status.state) {
            ReceiverState.ACCEPTED -> {
                session.respondedRoles += status.role
                session.acceptedRoles += status.role
            }
            ReceiverState.DENIED -> session.respondedRoles += status.role
            ReceiverState.FAILED -> {
                if (session.phase == SharePhase.SHARING) {
                    stopTargetInternal(status.role, status.errorMessage ?: "Rear failed", false)
                } else {
                    session.respondedRoles += status.role
                    session.acceptedRoles -= status.role
                    session.surfaceReadyRoles -= status.role
                }
            }
        }
    }

    private fun registerRenderSurfaceInternal(
        role: String,
        sessionId: String,
        surfaceGeneration: Long,
        surface: Surface,
        width: Int,
        height: Int
    ) {
        val session = activeSession
        if (session == null || session.sessionId != sessionId || role !in session.acceptedRoles) {
            surface.release()
            return
        }
        val currentGeneration = session.surfaceGenerations[role] ?: Long.MIN_VALUE
        if (surfaceGeneration < currentGeneration) {
            surface.release()
            return
        }
        session.surfaceRecoveryJobs.remove(role)?.cancel()
        session.surfaceGenerations[role] = surfaceGeneration
        session.surfaceReadyRoles -= role

        renderFanout.addRearOutput(role, surface, width, height) { added ->
            scope.launch {
                val current = activeSession
                if (current !== session || current.surfaceGenerations[role] != surfaceGeneration) {
                    return@launch
                }
                if (added) {
                    current.surfaceReadyRoles += role
                    updateTarget(role, ReceiverState.SURFACE_READY)
                    Log.i(TAG, "Render surface ready role=$role generation=$surfaceGeneration")
                } else {
                    current.surfaceGenerations.remove(role)
                    updateTarget(role, ReceiverState.FAILED, "Unable to attach render surface")
                    if (current.phase == SharePhase.SHARING) {
                        stopTargetInternal(role, "Unable to attach render surface", true)
                    }
                }
            }
        }
    }

    private fun unregisterRenderSurfaceInternal(
        role: String,
        sessionId: String,
        surfaceGeneration: Long
    ) {
        val session = activeSession ?: return
        if (session.sessionId != sessionId || session.surfaceGenerations[role] != surfaceGeneration) {
            return
        }
        session.surfaceGenerations.remove(role)
        session.surfaceReadyRoles.remove(role)
        renderFanout.removeRearOutput(role)
        if (session.phase == SharePhase.SHARING && role in session.selectedRoles) {
            updateTarget(role, ReceiverState.ACCEPTED)
            session.surfaceRecoveryJobs[role] = scope.launch {
                delay(SURFACE_RECOVERY_TIMEOUT_MS)
                if (activeSession === session && role !in session.surfaceReadyRoles) {
                    stopTargetInternal(role, "Rear render surface was lost", true)
                }
            }
        }
    }

    private suspend fun awaitReceiversAndStart(session: ActiveSession) {
        val readyDeadline = SystemClock.elapsedRealtime() + RECEIVER_READY_TIMEOUT_MS
        while (scope.isActive && activeSession === session && SystemClock.elapsedRealtime() < readyDeadline) {
            if (session.selectedRoles.all { receivers[it]?.registration?.ready == true }) break
            delay(READY_POLL_MS)
        }
        if (activeSession !== session) return
        val readyRoles = session.selectedRoles.filterTo(linkedSetOf()) {
            receivers[it]?.registration?.ready == true
        }
        (session.selectedRoles - readyRoles).forEach { role ->
            updateTarget(role, ReceiverState.FAILED, "Rear app did not become ready")
        }
        session.selectedRoles.retainAll(readyRoles)
        if (readyRoles.isEmpty()) {
            finishSharingSession("No Rear app became ready", false, HostShareNotification.DENIED)
            return
        }

        session.phase = SharePhase.WAITING_RESPONSES
        session.requestSent = true
        readyRoles.forEach { role ->
            sendShareRequest(role, session)
            updateTarget(role, ReceiverState.AWAITING_RESPONSE)
        }
        val responseDeadline = SystemClock.elapsedRealtime() + RECEIVER_RESPONSE_TIMEOUT_MS
        while (scope.isActive && activeSession === session && SystemClock.elapsedRealtime() < responseDeadline) {
            if (session.selectedRoles.all { it in session.respondedRoles }) break
            delay(READY_POLL_MS)
        }
        if (activeSession !== session) return

        val acceptedRoles = session.selectedRoles.filterTo(linkedSetOf()) {
            it in session.acceptedRoles
        }
        (session.selectedRoles - acceptedRoles).forEach { role ->
            if (role !in session.respondedRoles) {
                updateTarget(role, ReceiverState.FAILED, "Broadcast response timed out")
                sendTo(role) {
                    it.onStopSharing(session.sessionId, "Broadcast response timed out")
                }
            }
        }
        session.selectedRoles.retainAll(acceptedRoles)
        if (acceptedRoles.isEmpty()) {
            finishSharingSession("Broadcast was not accepted", true, HostShareNotification.DENIED)
            return
        }

        session.phase = SharePhase.WAITING_SURFACES
        val surfaceDeadline = SystemClock.elapsedRealtime() + SURFACE_READY_TIMEOUT_MS
        while (scope.isActive && activeSession === session && SystemClock.elapsedRealtime() < surfaceDeadline) {
            if (session.selectedRoles.all { it in session.surfaceReadyRoles }) break
            delay(READY_POLL_MS)
        }
        if (activeSession !== session) return

        val surfaceReadyRoles = session.selectedRoles.filterTo(linkedSetOf()) {
            it in session.surfaceReadyRoles
        }
        (session.selectedRoles - surfaceReadyRoles).forEach { role ->
            updateTarget(role, ReceiverState.FAILED, "Rear render surface did not become ready")
            renderFanout.removeRearOutput(role)
            sendTo(role) {
                it.onStopSharing(session.sessionId, "Rear render surface did not become ready")
            }
        }
        session.selectedRoles.retainAll(surfaceReadyRoles)
        if (surfaceReadyRoles.isEmpty()) {
            finishSharingSession("No Rear render surface became ready", false, HostShareNotification.DENIED)
            return
        }

        _hostNotifications.tryEmit(HostShareNotification.ACCEPTED)
        session.scheduledStartMs = SystemClock.elapsedRealtime() + START_LEAD_TIME_MS
        surfaceReadyRoles.forEach { role ->
            sendSnapshot(role, session, initialStart = true)
            updateTarget(role, ReceiverState.SHARING)
        }
        delay((session.scheduledStartMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        if (activeSession !== session) return

        session.phase = SharePhase.SHARING
        suppressPlayerEvents = true
        val player = playbackController.exoPlayer
        player.seekTo(session.anchorPositionMs)
        if (session.wasPlaying) player.play() else player.pause()
        suppressPlayerEvents = false
        sendSnapshotToAll(session)
        startReconciliation(session)
    }

    private fun stopTargetInternal(role: String, reason: String, notifyReceiver: Boolean) {
        val session = activeSession ?: return
        if (role !in session.selectedRoles && role !in session.acceptedRoles) return
        session.selectedRoles.remove(role)
        session.acceptedRoles.remove(role)
        session.surfaceReadyRoles.remove(role)
        session.surfaceGenerations.remove(role)
        session.surfaceRecoveryJobs.remove(role)?.cancel()
        renderFanout.removeRearOutput(role)
        if (notifyReceiver) sendTo(role) { it.onStopSharing(session.sessionId, reason) }
        updateTarget(
            role,
            idleState(role)
        )
        if (session.selectedRoles.isEmpty()) {
            finishSharingSession(
                reason = reason,
                notifyReceivers = false,
                notification = if (session.phase == SharePhase.SHARING) {
                    HostShareNotification.ENDED
                } else {
                    HostShareNotification.DENIED
                }
            )
        }
    }

    private fun finishSharingSession(
        reason: String,
        notifyReceivers: Boolean,
        notification: HostShareNotification?
    ) {
        val session = activeSession ?: return
        activeSession = null
        startJob?.cancel()
        reconciliationJob?.cancel()
        startJob = null
        reconciliationJob = null
        session.surfaceRecoveryJobs.values.forEach(Job::cancel)
        session.surfaceRecoveryJobs.clear()

        val roles = (session.selectedRoles + session.acceptedRoles + session.surfaceGenerations.keys)
            .toSet()
        roles.forEach { role ->
            renderFanout.removeRearOutput(role)
            if (notifyReceivers) {
                sendTo(role) { it.onStopSharing(session.sessionId, reason) }
            }
            updateTarget(
                role,
                idleState(role)
            )
        }
        _sessionActive.value = false
        if (session.phase != SharePhase.SHARING && session.wasPlaying) {
            playbackController.exoPlayer.play()
        }
        notification?.let(_hostNotifications::tryEmit)
        Log.i(TAG, "Share session finished phase=${session.phase} reason=$reason")
    }

    private fun handleReceiverDisconnected(role: String, binder: IBinder) {
        val current = receivers[role] ?: return
        if (current.receiver.asBinder() != binder) return
        receivers.remove(role)
        unlinkDeathRecipient(current)
        handleCurrentReceiverRemoved(role)
    }

    private fun handleCurrentReceiverRemoved(role: String) {
        _targets.value = _targets.value.map { target ->
            if (target.role == role) {
                target.copy(
                    state = ReceiverState.NOT_RUNNING,
                    errorMessage = "Rear app disconnected"
                )
            } else {
                target
            }
        }
        renderFanout.removeRearOutput(role)
        val session = activeSession ?: return
        session.surfaceRecoveryJobs.remove(role)?.cancel()
        session.surfaceGenerations.remove(role)
        session.surfaceReadyRoles.remove(role)
        session.acceptedRoles.remove(role)
        session.selectedRoles.remove(role)
        session.respondedRoles.add(role)
        if (session.selectedRoles.isEmpty()) {
            finishSharingSession(
                reason = "Rear app disconnected",
                notifyReceivers = false,
                notification = if (session.phase == SharePhase.SHARING) {
                    HostShareNotification.ENDED
                } else {
                    HostShareNotification.DENIED
                }
            )
        }
    }

    private fun handleRearOutputLost(role: String, reason: String) {
        val session = activeSession ?: return
        if (role !in session.selectedRoles) return
        session.surfaceGenerations.remove(role)
        session.surfaceReadyRoles.remove(role)
        stopTargetInternal(role, "Renderer output lost: $reason", true)
    }

    private fun unlinkDeathRecipient(record: ReceiverRecord) {
        runCatching {
            record.receiver.asBinder().unlinkToDeath(record.deathRecipient, 0)
        }
    }

    private fun publishPlayerTruth() {
        if (suppressPlayerEvents) return
        val session = activeSession ?: return
        if (session.phase == SharePhase.SHARING) sendSnapshotToAll(session)
    }

    private fun sendShareRequest(role: String, session: ActiveSession) {
        val player = playbackController.exoPlayer
        val request = SharedSessionSnapshot(
            sessionId = session.sessionId,
            mediaId = session.source.assetPath,
            assetPath = session.source.assetPath,
            title = session.source.title,
            positionMs = session.anchorPositionMs,
            durationMs = player.duration.coerceAtLeast(0L),
            anchorElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            playWhenReady = false,
            isPlaying = false,
            playbackSpeed = player.playbackParameters.speed,
            scheduledStartElapsedRealtimeMs = 0L,
            seekEventId = seekEventId,
            sequence = ++sequence
        )
        sendTo(role) { it.onShareRequest(request) }
    }

    private fun sendSnapshot(role: String, session: ActiveSession, initialStart: Boolean = false) {
        val player = playbackController.exoPlayer
        val snapshot = SharedSessionSnapshot(
            sessionId = session.sessionId,
            mediaId = session.source.assetPath,
            assetPath = session.source.assetPath,
            title = session.source.title,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            anchorElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            playWhenReady = if (initialStart) session.wasPlaying else player.playWhenReady,
            isPlaying = if (initialStart) false else player.isPlaying,
            playbackSpeed = player.playbackParameters.speed,
            scheduledStartElapsedRealtimeMs = session.scheduledStartMs,
            seekEventId = seekEventId,
            sequence = ++sequence
        )
        sendTo(role) { it.onSharedSession(snapshot) }
    }

    private fun sendSnapshotToAll(session: ActiveSession) {
        session.selectedRoles.toList().forEach { sendSnapshot(it, session) }
    }

    private fun startReconciliation(session: ActiveSession) {
        reconciliationJob?.cancel()
        reconciliationJob = scope.launch {
            while (isActive && activeSession === session) {
                delay(RECONCILIATION_INTERVAL_MS)
                sendSnapshotToAll(session)
            }
        }
    }

    private inline fun sendTo(role: String, block: (IRearPlaybackReceiver) -> Unit) {
        val receiver = receivers[role]?.receiver ?: return
        try {
            block(receiver)
        } catch (error: RemoteException) {
            Log.w(TAG, "Receiver disconnected role=$role", error)
            scope.launch { handleReceiverDisconnected(role, receiver.asBinder()) }
        }
    }

    private fun idleState(role: String): String =
        if (receivers[role]?.registration?.ready == true) {
            ReceiverState.READY
        } else {
            ReceiverState.NOT_RUNNING
        }

    private fun updateTarget(
        role: String,
        state: String,
        error: String? = null,
        displayId: Int? = null
    ) {
        _targets.value = _targets.value.map { target ->
            if (target.role == role) {
                target.copy(
                    displayId = displayId ?: target.displayId,
                    state = state,
                    errorMessage = error
                )
            } else {
                target
            }
        }
    }

    private data class ReceiverRecord(
        val registration: ReceiverRegistration,
        val receiver: IRearPlaybackReceiver,
        val deathRecipient: IBinder.DeathRecipient
    )

    private data class ActiveSession(
        val sessionId: String,
        val source: VideoSource.Asset,
        val selectedRoles: MutableSet<String>,
        val anchorPositionMs: Long,
        val wasPlaying: Boolean,
        val respondedRoles: MutableSet<String> = linkedSetOf(),
        val acceptedRoles: MutableSet<String> = linkedSetOf(),
        val surfaceReadyRoles: MutableSet<String> = linkedSetOf(),
        val surfaceGenerations: MutableMap<String, Long> = linkedMapOf(),
        val surfaceRecoveryJobs: MutableMap<String, Job> = linkedMapOf(),
        var requestSent: Boolean = false,
        var scheduledStartMs: Long = 0L,
        var phase: SharePhase = SharePhase.WAITING_RECEIVERS
    )

    private enum class SharePhase {
        WAITING_RECEIVERS,
        WAITING_RESPONSES,
        WAITING_SURFACES,
        SHARING
    }

    private companion object {
        const val TAG = "PidShareCoordinator"
        const val RECEIVER_READY_TIMEOUT_MS = 8_000L
        const val RECEIVER_RESPONSE_TIMEOUT_MS = 12_000L
        const val SURFACE_READY_TIMEOUT_MS = 5_000L
        const val SURFACE_RECOVERY_TIMEOUT_MS = 2_000L
        const val READY_POLL_MS = 100L
        const val START_LEAD_TIME_MS = 700L
        const val RECONCILIATION_INTERVAL_MS = 2_000L
    }
}

enum class HostShareNotification(val message: String) {
    ACCEPTED("Video broadcast accepted"),
    DENIED("Video broadcast denied"),
    ENDED("Video broadcast ended")
}
