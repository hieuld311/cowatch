package com.hieuld.cowatch.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.session.AppPlaybackSession
import com.hieuld.cowatch.session.HostSharePlaybackController
import com.hieuld.cowatch.ui.player.FrontPlayerScreen
import com.hieuld.cowatch.util.hasAudioTrackInitializationFailure
import com.hieuld.cowatch.ui.theme.CoWatchTheme
import com.hieuld.cowatch.viewmodel.FrontPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FrontPlayerActivity : ComponentActivity() {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository
    private lateinit var renderEngine: FrameFanoutRenderEngine
    private lateinit var sharePlaybackController: HostSharePlaybackController

    private var audioFallbackApplied = false
    private var endSessionOnDestroy = false

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBackToLibrary()
                }
            }
        )

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
            showFullscreenVideo(video)
        }
    }

    override fun onResume() {
        super.onResume()
        AppPlaybackSession.exitInAppPip()
        applyFullscreenMode(true)
    }

    private fun setupPlayer(source: VideoSource.Asset) {
        player = AppPlaybackSession.getPlayer(this)
        sharePlaybackController = HostSharePlaybackController(
            player = player,
            coroutineScope = lifecycleScope,
            sessionProvider = { viewModel.session.value },
            setPlaybackState = viewModel::setPlaybackState
        )
        AppPlaybackSession.attachRenderEngine(renderEngine)

        player.addListener(playerListener)
        applyVideoSize(player.videoSize)
        AppPlaybackSession.showFullscreen(this, source)
        sharePlaybackController.updatePlaybackStateFromPlayer()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    viewModel.setPlaybackState(CoWatchPlaybackState.Preparing)
                }
                Player.STATE_READY,
                Player.STATE_ENDED -> sharePlaybackController.updatePlaybackStateFromPlayer()
                else -> viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            sharePlaybackController.updatePlaybackStateFromPlayer()
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

    private fun showFullscreenVideo(source: VideoSource.Asset) {
        sharePlaybackController.cancelPendingSharedStart()
        viewModel.setPlaybackState(CoWatchPlaybackState.Preparing)
        audioFallbackApplied = false
        if (AppPlaybackSession.currentSource?.assetPath != source.assetPath) {
            viewModel.stopSharing(notifyEnded = false)
        }
        AppPlaybackSession.showFullscreen(this, source)
        sharePlaybackController.updatePlaybackStateFromPlayer()
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
            sharePlaybackController.pauseForShareDialog()

            if (targets.isEmpty()) {
                Toast.makeText(
                    this,
                    "No secondary display available",
                    Toast.LENGTH_SHORT
                ).show()

                broadcastEnabledState.value = false
                clearShareDialog()
                sharePlaybackController.resumeAfterShareDialogCancel()
                return
            }

            shareTargetsState.value = targets
            shareDialogVisibleState.value = true
        } else {
            sharePlaybackController.cancelPendingSharedStart()
            sharePlaybackController.clearPendingShareDialogResume()
            clearShareDialog()
            viewModel.stopSharing(notifyEnded = true)
            sharePlaybackController.updatePlaybackStateFromPlayer()
        }
    }

    private fun observeShareSession() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                broadcastEnabledState.value = session != null
                if (session == null && ::player.isInitialized) {
                    sharePlaybackController.cancelPendingSharedStart()
                    sharePlaybackController.updatePlaybackStateFromPlayer()
                }
            }
        }
    }

    private fun onStartSharing(displayIds: Set<Int>) {
        clearShareDialog()
        sharePlaybackController.clearPendingShareDialogResume()

        if (displayIds.isEmpty() || !::player.isInitialized) {
            broadcastEnabledState.value = false
            return
        }

        val shareAnchor = sharePlaybackController.prepareShareAtCurrentPosition()

        val sharingStarted = viewModel.startSharing(
            context = this,
            videoTitle = AppPlaybackSession.currentSource?.title ?: "Video Title",
            hostDisplayId = getCurrentDisplayId(),
            targetDisplayIds = displayIds,
            anchorPositionMs = shareAnchor.positionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = { startPositionMs ->
                sharePlaybackController.startSharedPlaybackAfterDisplaysReady(startPositionMs)
            }
        )

        if (!sharingStarted) {
            sharePlaybackController.cancelPendingSharedStart()
            broadcastEnabledState.value = false
            sharePlaybackController.restorePlaybackAfterShareLaunchFailure(
                shareAnchor.wasPlayingBeforeShare
            )
        }
    }

    private fun onShareDialogDismissedWithoutSharing() {
        clearShareDialog()

        if (viewModel.session.value == null) {
            broadcastEnabledState.value = false
            sharePlaybackController.resumeAfterShareDialogCancel()
            sharePlaybackController.updatePlaybackStateFromPlayer()
        }
    }

    private fun onBackToLibrary() {
        sharePlaybackController.cancelPendingSharedStart()
        viewModel.stopSharing(notifyEnded = false)
        viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
        endSessionOnDestroy = true
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
        sharePlaybackController.cancelPendingSharedStart()
        sharePlaybackController.clearPendingShareDialogResume()
        clearShareDialog()
        AppPlaybackSession.enterInAppPip()
        applyFullscreenMode(false)
        startActivity(
            Intent(this, VideoLibraryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun clearShareDialog() {
        shareDialogVisibleState.value = false
        shareTargetsState.value = emptyList()
    }

    private fun observeHostNotifications() {
        lifecycleScope.launch {
            viewModel.hostNotifications.collectLatest { notification ->
                showHostNotification(notification.message)

                if (sharePlaybackController.handleHostNotification(notification)) {
                    broadcastEnabledState.value = false
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
            if (endSessionOnDestroy) {
                viewModel.stopSharing(notifyEnded = false)
                viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            }
        }

        if (::player.isInitialized) {
            sharePlaybackController.release()
            hostNotificationJob?.cancel()
            player.removeListener(playerListener)
            if (endSessionOnDestroy) {
                AppPlaybackSession.detachRenderEngine(renderEngine)
            }
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "FrontPlayerActivity"
        private const val HOST_NOTIFICATION_DURATION_MS = 2_000L
    }
}
