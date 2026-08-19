package com.ivi.pid.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.ivi.common.domain.VideoSource
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.common.playback.VideoCatalogNavigator
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.enterImmersiveFullscreen
import com.ivi.common.ui.player.hasAudioTrackInitializationFailure
import com.ivi.pid.ui.player.FrontPlayerScreen
import com.ivi.pid.rendering.PidRenderFanout
import com.ivi.pid.viewmodel.FrontPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FrontPlayerActivity : ComponentActivity() {
    @Inject lateinit var playbackController: Media3PlaybackController
    @Inject lateinit var seekFrameProvider: SeekFrameProvider
    @Inject lateinit var renderFanout: PidRenderFanout
    @Inject lateinit var videoCatalogNavigator: VideoCatalogNavigator

    private val viewModel: FrontPlayerViewModel by viewModels()
    private val showShareDialog = mutableStateOf(false)
    private val activeAssetPath = mutableStateOf<String?>(null)
    private val hostNotificationText = mutableStateOf<String?>(null)
    private var resumeAfterShareDialog = false
    private var audioFallbackApplied = false
    private lateinit var observedPlayer: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = FrontPlayerContract.readVideo(intent) as? VideoSource.Asset
        if (source == null) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closePlayer()
        })
        observedPlayer = playbackController.exoPlayer
        observedPlayer.addListener(playerListener)
        show(source)
        observeHostNotifications()
        observePlaybackCompletion()
        window.enterImmersiveFullscreen()

        setContent {
            CoWatchTheme {
                val targets by viewModel.targets.collectAsStateWithLifecycle()
                val sharing by viewModel.sessionActive.collectAsStateWithLifecycle()
                FrontPlayerScreen(
                    player = playbackController.exoPlayer,
                    renderFanout = renderFanout,
                    activeAssetPath = activeAssetPath.value,
                    seekFrameProvider = seekFrameProvider,
                    broadcastChecked = sharing || showShareDialog.value,
                    showShareDialog = showShareDialog.value,
                    targets = targets,
                    hostNotificationText = hostNotificationText.value,
                    onBroadcastCheckedChange = ::onBroadcastCheckedChange,
                    onShareDialogDismiss = ::dismissShareDialog,
                    onStartSharing = ::startSharing,
                    onBackClick = ::closePlayer,
                    onPictureInPictureClick = ::enterInAppPip,
                    onPreviousVideo = ::showPreviousVideo,
                    onNextVideo = ::showNextVideo
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (FrontPlayerContract.readVideo(intent) as? VideoSource.Asset)?.let(::show)
    }

    override fun onResume() {
        super.onResume()
        playbackController.exitInAppPip()
        window.enterImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enterImmersiveFullscreen()
    }

    private fun show(source: VideoSource.Asset) {
        playSource(source, continueCurrentDestination = false)
    }

    private fun continuePlayback(source: VideoSource.Asset) {
        playSource(source, continueCurrentDestination = true)
    }

    private fun playSource(source: VideoSource.Asset, continueCurrentDestination: Boolean) {
        val sourceChanged = playbackController.currentSource?.assetPath != source.assetPath
        if (sourceChanged) {
            viewModel.shareCoordinator.prepareMediaChange(source)
        }
        renderFanout.prepareForPlayback(sourceChanged)
        activeAssetPath.value = source.assetPath
        audioFallbackApplied = false
        if (continueCurrentDestination) {
            playbackController.continuePlayback(source)
        } else {
            playbackController.showFullscreen(source)
        }
    }

    private fun onBroadcastCheckedChange(checked: Boolean) {
        if (!checked) {
            if (!viewModel.sessionActive.value) dismissShareDialog()
            return
        }
        viewModel.shareCoordinator.refreshTargets(currentDisplayId())
        if (viewModel.targets.value.none { it.available }) {
            Toast.makeText(this, "No rear screen available", Toast.LENGTH_SHORT).show()
            return
        }
        resumeAfterShareDialog = playbackController.exoPlayer.isPlaying
        playbackController.exoPlayer.pause()
        showShareDialog.value = true
    }

    private fun startSharing(roles: Set<String>) {
        showShareDialog.value = false
        val source = playbackController.currentSource ?: return
        val started = viewModel.shareCoordinator.startSharing(
            selectedRoles = roles,
            source = source,
            hostDisplayId = currentDisplayId(),
            wasPlayingBeforeDialog = resumeAfterShareDialog
        )
        if (!started && resumeAfterShareDialog) playbackController.exoPlayer.play()
        resumeAfterShareDialog = false
    }

    private fun dismissShareDialog() {
        showShareDialog.value = false
        if (resumeAfterShareDialog) playbackController.exoPlayer.play()
        resumeAfterShareDialog = false
    }

    private fun observeHostNotifications() {
        lifecycleScope.launch {
            viewModel.hostNotifications.collect { notification ->
                hostNotificationText.value = notification.message
                delay(HOST_NOTIFICATION_DURATION_MS)
                if (hostNotificationText.value == notification.message) {
                    hostNotificationText.value = null
                }
            }
        }
    }

    private fun observePlaybackCompletion() {
        lifecycleScope.launch {
            playbackController.playbackCompleted.collect {
                if (!isFinishing && !isDestroyed) showNextVideo()
            }
        }
    }

    private fun enterInAppPip() {
        playbackController.enterInAppPip()
        startActivity(
            Intent(this, VideoLibraryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun showPreviousVideo() {
        playbackController.handlePreviousButtonPress(videoCatalogNavigator)?.let(::continuePlayback)
    }

    private fun showNextVideo() {
        videoCatalogNavigator.next(playbackController.currentSource)?.let(::continuePlayback)
    }

    private fun closePlayer() {
        viewModel.shareCoordinator.stopSharingAll("PID player closed")
        playbackController.stop()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayId(): Int = windowManager.defaultDisplay.displayId

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (audioFallbackApplied || !error.hasAudioTrackInitializationFailure()) return
            Log.w(TAG, "AudioTrack failed; disabling audio once", error)
            audioFallbackApplied = true
            val player = playbackController.exoPlayer
            val position = player.currentPosition.coerceAtLeast(0L)
            val playWhenReady = player.playWhenReady
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            player.seekTo(position)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    override fun onDestroy() {
        if (::observedPlayer.isInitialized) observedPlayer.removeListener(playerListener)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PIDFrontPlayer"
        const val HOST_NOTIFICATION_DURATION_MS = 2_000L
    }
}
