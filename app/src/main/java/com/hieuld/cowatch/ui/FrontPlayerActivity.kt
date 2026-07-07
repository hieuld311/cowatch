package com.hieuld.cowatch.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.data.display.repository.DisplayRepository
import com.hieuld.cowatch.domain.display.DisplayInfo
import com.hieuld.cowatch.domain.media.VideoSource
import com.hieuld.cowatch.domain.playback.CoWatchPlaybackState
import com.hieuld.cowatch.domain.sharing.CoWatchSessionStatus
import com.hieuld.cowatch.domain.sharing.ShareHostNotification
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.session.AppPlaybackSession
import com.hieuld.cowatch.ui.VideoLibraryActivity
import com.hieuld.cowatch.ui.FrontPlayerContract
import com.hieuld.cowatch.ui.player.FrontPlayerScreen
import com.hieuld.cowatch.ui.player.hasAudioTrackInitializationFailure
import com.hieuld.cowatch.ui.theme.CoWatchTheme
import com.hieuld.cowatch.viewmodel.FrontPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class FrontPlayerActivity : ComponentActivity() {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository
    private lateinit var renderEngine: FrameFanoutRenderEngine

    private var audioFallbackApplied = false
    private var pendingSharedStartJob: Job? = null
    private var sharedStartGeneration = 0
    private var resumeAfterShareDialogCancel = false
    private var resumeAfterShareRequestDenied = false
    private var keepPlaybackForInAppPip = false

    private val broadcastEnabledState = mutableStateOf(false)
    private val shareDialogVisibleState = mutableStateOf(false)
    private val shareTargetsState = mutableStateOf(emptyList<DisplayInfo>())
    private val hostNotificationState = mutableStateOf<String?>(null)
    private var hostNotificationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = FrontPlayerContract.readVideo(intent) as? VideoSource.Asset

        if (source == null) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[FrontPlayerViewModel::class.java]
        displayRepository = DisplayRepository(this)
        renderEngine = AppPlaybackSession.getRenderEngine()

        setupPlayer(source)
        observeShareSession()
        observeHostNotifications()
        applyFullscreenMode(true)

        setContent {
            CoWatchTheme {
                FrontPlayerScreen(
                    player = player,
                    renderEngine = renderEngine,
                    broadcastChecked = broadcastEnabledState.value,
                    showShareDialog = shareDialogVisibleState.value,
                    displays = shareTargetsState.value,
                    hostNotificationText = hostNotificationState.value,
                    onBroadcastCheckedChange = ::onBroadcastCheckedChange,
                    onShareDialogDismiss = ::onShareDialogDismissedWithoutSharing,
                    onStartSharing = ::onStartSharing,
                    onBackClick = ::onBackToLibrary,
                    onPictureInPictureClick = ::onInAppPipClick
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val video = FrontPlayerContract.readVideo(intent) as? VideoSource.Asset ?: return

        if (::player.isInitialized) {
            playVideo(video)
        }
    }

    private fun setupPlayer(source: VideoSource.Asset) {
        player = AppPlaybackSession.getPlayer(this)
        AppPlaybackSession.attachRenderEngine(renderEngine)

        player.addListener(playerListener)
        applyVideoSize(player.videoSize)
        AppPlaybackSession.showFullscreen(this, source)
        updatePlaybackStateFromPlayer()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    viewModel.setPlaybackState(CoWatchPlaybackState.Preparing)
                }
                Player.STATE_READY,
                Player.STATE_ENDED -> updatePlaybackStateFromPlayer()
                else -> viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackStateFromPlayer()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            applyVideoSize(videoSize)
        }

        override fun onPlayerError(error: PlaybackException) {
            viewModel.setPlaybackState(
                CoWatchPlaybackState.Error(error.message ?: error.errorCodeName)
            )

            if (audioFallbackApplied || !error.hasAudioTrackInitializationFailure()) return

            Log.w(TAG, "AudioTrack failed; disabling audio track once.", error)
            audioFallbackApplied = true
            val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            val resumePlayback = player.playWhenReady
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            player.seekTo(resumePositionMs)
            player.prepare()
            player.playWhenReady = resumePlayback
        }
    }

    private fun playVideo(source: VideoSource.Asset) {
        cancelPendingSharedStart()
        viewModel.setPlaybackState(CoWatchPlaybackState.Preparing)
        audioFallbackApplied = false
        AppPlaybackSession.play(this, source)
    }

    private fun applyVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return

        renderEngine.setVideoSize(
            width = videoSize.width,
            height = videoSize.height,
            pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
        )
    }

    private fun onBroadcastCheckedChange(checked: Boolean) {
        if (checked) {
            val targets = getShareTargets()
            pauseForShareDialog()

            if (targets.isEmpty()) {
                Toast.makeText(
                    this,
                    "No secondary display available",
                    Toast.LENGTH_SHORT
                ).show()

                broadcastEnabledState.value = false
                clearShareDialog()
                resumeAfterShareDialogCancel()
                return
            }

            shareTargetsState.value = targets
            shareDialogVisibleState.value = true
        } else {
            cancelPendingSharedStart()
            clearPendingShareDialogResume()
            clearShareDialog()
            viewModel.stopSharing(notifyEnded = true)
            updatePlaybackStateFromPlayer()
        }
    }

    private fun observeShareSession() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                broadcastEnabledState.value = session != null
                if (session == null && ::player.isInitialized) {
                    cancelPendingSharedStart()
                    updatePlaybackStateFromPlayer()
                }
            }
        }
    }

    private fun onStartSharing(displayIds: Set<Int>) {
        clearShareDialog()
        clearPendingShareDialogResume()

        if (displayIds.isEmpty() || !::player.isInitialized) {
            broadcastEnabledState.value = false
            return
        }

        val anchorPositionMs = player.currentPosition
        val wasPlayingBeforeShare = player.isPlaying
        resumeAfterShareRequestDenied = wasPlayingBeforeShare
        // Freeze the single decoder until all selected Presentation surfaces are attached.
        pauseAtShareAnchor(anchorPositionMs)
        viewModel.setPlaybackState(CoWatchPlaybackState.SharingPreparing)
        Log.i(
            TAG,
            "Preparing share at $anchorPositionMs; player paused until all displays are ready."
        )

        val sharingStarted = viewModel.startSharing(
            context = this,
            videoTitle = AppPlaybackSession.currentSource?.title ?: "Video Title",
            hostDisplayId = getCurrentDisplayId(),
            targetDisplayIds = displayIds,
            anchorPositionMs = anchorPositionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = { startPositionMs ->
                startSharedPlaybackAfterDisplaysReady(startPositionMs)
            }
        )

        if (!sharingStarted) {
            cancelPendingSharedStart()
            broadcastEnabledState.value = false
            resumeAfterShareRequestDenied = false
            restorePlaybackAfterShareStartFailure(wasPlayingBeforeShare)
        }
    }

    private fun onShareDialogDismissedWithoutSharing() {
        clearShareDialog()

        if (viewModel.session.value == null) {
            broadcastEnabledState.value = false
            resumeAfterShareDialogCancel()
            updatePlaybackStateFromPlayer()
        }
    }

    private fun onBackToLibrary() {
        cancelPendingSharedStart()
        viewModel.stopSharing(notifyEnded = false)
        viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
        AppPlaybackSession.stop()
        finish()
    }

    private fun onInAppPipClick() {
        if (viewModel.session.value?.status == CoWatchSessionStatus.PREPARING_SHARE) {
            Toast.makeText(
                this,
                "Wait until shared playback starts",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // PiP is app-scoped: keep player/render session alive while returning to the library.
        keepPlaybackForInAppPip = true
        cancelPendingSharedStart()
        clearPendingShareDialogResume()
        clearShareDialog()
        AppPlaybackSession.enterInAppPip()
        applyFullscreenMode(false)
        startActivity(Intent(this, VideoLibraryActivity::class.java))
        finish()
    }

    private fun pauseAtShareAnchor(anchorPositionMs: Long) {
        cancelPendingSharedStart()
        player.playWhenReady = false
        player.pause()
        player.seekTo(anchorPositionMs)
    }

    private fun restorePlaybackAfterShareStartFailure(wasPlayingBeforeShare: Boolean) {
        clearPendingShareDialogResume()
        viewModel.setPlaybackState(
            if (wasPlayingBeforeShare) {
                CoWatchPlaybackState.Playing
            } else {
                CoWatchPlaybackState.Paused
            }
        )

        if (wasPlayingBeforeShare) {
            player.play()
        }
    }

    private fun startSharedPlaybackAfterDisplaysReady(startPositionMs: Long) {
        val generation = ++sharedStartGeneration
        pendingSharedStartJob?.cancel()
        pendingSharedStartJob = lifecycleScope.launch {
            // All outputs are registered; seek once to the share anchor before releasing playback.
            Log.i(TAG, "All share displays ready; priming player at $startPositionMs.")
            player.playWhenReady = false
            player.pause()

            if (abs(player.currentPosition - startPositionMs) > SHARED_START_SEEK_TOLERANCE_MS) {
                player.seekTo(startPositionMs)
            }

            var waitedMs = 0L
            while (
                isActive &&
                generation == sharedStartGeneration &&
                player.playbackState == Player.STATE_BUFFERING &&
                waitedMs < SHARED_START_READY_TIMEOUT_MS
            ) {
                delay(SHARED_START_READY_POLL_MS)
                waitedMs += SHARED_START_READY_POLL_MS
            }

            delay(SHARED_START_PREROLL_MS)

            if (
                !isActive ||
                generation != sharedStartGeneration ||
                viewModel.session.value == null
            ) {
                return@launch
            }

            pendingSharedStartJob = null
            resumeAfterShareRequestDenied = false
            Log.i(TAG, "Starting shared playback at ${player.currentPosition}.")
            player.play()
            viewModel.setPlaybackState(CoWatchPlaybackState.Sharing)
        }
    }

    private fun cancelPendingSharedStart() {
        sharedStartGeneration += 1
        pendingSharedStartJob?.cancel()
        pendingSharedStartJob = null
    }

    private fun clearShareDialog() {
        shareDialogVisibleState.value = false
        shareTargetsState.value = emptyList()
    }

    private fun pauseForShareDialog() {
        if (!::player.isInitialized) return

        resumeAfterShareDialogCancel = player.isPlaying
        if (resumeAfterShareDialogCancel) {
            player.pause()
            updatePlaybackStateFromPlayer()
        }
    }

    private fun resumeAfterShareDialogCancel() {
        if (resumeAfterShareDialogCancel && ::player.isInitialized) {
            player.play()
        }
        clearPendingShareDialogResume()
    }

    private fun clearPendingShareDialogResume() {
        resumeAfterShareDialogCancel = false
    }

    private fun observeHostNotifications() {
        lifecycleScope.launch {
            viewModel.hostNotifications.collectLatest { notification ->
                showHostNotification(notification.message)

                when (notification) {
                    ShareHostNotification.ACCEPTED -> Unit
                    ShareHostNotification.DENIED -> {
                        cancelPendingSharedStart()
                        broadcastEnabledState.value = false
                        restorePlaybackAfterShareStartFailure(resumeAfterShareRequestDenied)
                        resumeAfterShareRequestDenied = false
                    }
                    ShareHostNotification.ENDED -> {
                        if (resumeAfterShareRequestDenied && !player.isPlaying) {
                            restorePlaybackAfterShareStartFailure(resumeAfterShareRequestDenied)
                        }
                        resumeAfterShareRequestDenied = false
                        updatePlaybackStateFromPlayer()
                    }
                }
            }
        }
    }

    private fun showHostNotification(message: String) {
        hostNotificationJob?.cancel()
        hostNotificationState.value = message
        hostNotificationJob = lifecycleScope.launch {
            delay(HOST_NOTIFICATION_DURATION_MS)
            if (hostNotificationState.value == message) {
                hostNotificationState.value = null
            }
        }
    }

    private fun updatePlaybackStateFromPlayer() {
        if (!::player.isInitialized) {
            viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            return
        }

        viewModel.setPlaybackState(
            when {
                viewModel.session.value != null && player.isPlaying -> CoWatchPlaybackState.Sharing
                player.isPlaying -> CoWatchPlaybackState.Playing
                else -> CoWatchPlaybackState.Paused
            }
        )
    }

    private fun applyFullscreenMode(fullscreen: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun getShareTargets(): List<DisplayInfo> {
        return displayRepository.getShareTargets(getCurrentDisplayId())
    }

    @Suppress("DEPRECATION")
    private fun getCurrentDisplayId(): Int {
        return windowManager.defaultDisplay.displayId
    }

    override fun onDestroy() {
        applyFullscreenMode(false)

        if (::viewModel.isInitialized) {
            if (!keepPlaybackForInAppPip) {
                viewModel.stopSharing(notifyEnded = false)
                viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            }
        }

        if (::player.isInitialized) {
            cancelPendingSharedStart()
            hostNotificationJob?.cancel()
            player.removeListener(playerListener)
            if (!keepPlaybackForInAppPip && !isChangingConfigurations) {
                AppPlaybackSession.detachRenderEngine(renderEngine)
                AppPlaybackSession.stop()
            }
        }

        if (
            ::renderEngine.isInitialized &&
            !keepPlaybackForInAppPip &&
            !isChangingConfigurations &&
            !::player.isInitialized
        ) {
            renderEngine.release()
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "FrontPlayerActivity"
        private const val SHARED_START_SEEK_TOLERANCE_MS = 100L
        private const val SHARED_START_READY_TIMEOUT_MS = 1_000L
        private const val SHARED_START_READY_POLL_MS = 25L
        private const val SHARED_START_PREROLL_MS = 100L
        private const val HOST_NOTIFICATION_DURATION_MS = 1_600L
    }
}
