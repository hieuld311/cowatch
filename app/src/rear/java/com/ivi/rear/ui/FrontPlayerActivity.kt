package com.ivi.rear.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.ivi.common.domain.VideoSource
import com.ivi.common.ipc.SharedSessionSnapshot
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.common.playback.LocalPlaybackDestination
import com.ivi.common.playback.VideoCatalogNavigator
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.enterImmersiveFullscreen
import com.ivi.common.ui.player.FanoutVideoSurface
import com.ivi.common.ui.player.Media3PlayerSurface
import com.ivi.common.ui.player.PlaybackControlBar
import com.ivi.common.ui.player.PlayerScreenFrame
import com.ivi.common.ui.player.ReadOnlyPlaybackControlBar
import com.ivi.rear.sharing.RearShareClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FrontPlayerActivity : ComponentActivity() {
    @Inject lateinit var playbackController: Media3PlaybackController
    @Inject lateinit var seekFrameProvider: SeekFrameProvider
    @Inject lateinit var shareClient: RearShareClient
    @Inject lateinit var videoCatalogNavigator: VideoCatalogNavigator
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closePlayer()
        })
        showLocalSourceIfNeeded(intent)
        window.enterImmersiveFullscreen()
        lifecycleScope.launch {
            shareClient.shareExit.collect { destination ->
                if (destination != LocalPlaybackDestination.FULLSCREEN && !isFinishing) {
                    startActivity(RearNavigation.libraryIntent(this@FrontPlayerActivity))
                    finish()
                }
            }
        }
        setContent {
            CoWatchTheme {
                val shared by shareClient.sharedSession.collectAsStateWithLifecycle()
                val pendingRequest by shareClient.pendingShareRequest.collectAsStateWithLifecycle()
                var controlsVisible by remember(shared?.sessionId) { mutableStateOf(false) }
                var interactionVersion by remember(shared?.sessionId) { mutableIntStateOf(0) }
                var nowMs by remember(shared?.sessionId) {
                    mutableLongStateOf(SystemClock.elapsedRealtime())
                }

                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    display?.displayId?.let(shareClient::markReceiverUiReady)
                }
                LaunchedEffect(shared?.sessionId, shared?.sequence) {
                    while (shared != null) {
                        nowMs = SystemClock.elapsedRealtime()
                        delay(REMOTE_POSITION_UPDATE_DELAY_MS)
                    }
                }
                LaunchedEffect(shared?.sessionId, interactionVersion) {
                    if (shared == null || interactionVersion == 0) return@LaunchedEffect
                    controlsVisible = true
                    delay(CONTROLS_AUTO_HIDE_DELAY_MS)
                    controlsVisible = false
                }

                PlayerScreenFrame(
                    modifier = Modifier.pointerInput(shared?.sessionId) {
                        if (shared == null) return@pointerInput
                        detectTapGestures { interactionVersion += 1 }
                    },
                    onCloseClick = ::closePlayer,
                    videoSurface = {
                        if (shared != null) {
                            FanoutVideoSurface(
                                modifier = Modifier.fillMaxSize(),
                                onSurfaceAvailable = shareClient::registerRenderSurface,
                                onSurfaceDestroyed = shareClient::unregisterRenderSurface
                            )
                        } else {
                            Media3PlayerSurface(
                                playerSurfaceController = playbackController,
                                modifier = Modifier.fillMaxSize(),
                                onPlayerViewReady = { playerView = it }
                            )
                        }
                    },
                    playbackControls = {
                        val snapshot = shared
                        if (snapshot != null) {
                            ReadOnlyPlaybackControlBar(
                                visible = controlsVisible,
                                positionMs = snapshot.currentPositionAt(nowMs),
                                durationMs = snapshot.durationMs,
                                title = snapshot.title,
                                isPlaying = snapshot.isPlaying,
                                playbackSpeed = snapshot.playbackSpeed,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PlaybackControlBar(
                                player = playbackController.exoPlayer,
                                activeAssetPath = playbackController.currentSource?.assetPath,
                                seekFrameProvider = seekFrameProvider,
                                onPictureInPictureClick = ::enterInAppPip,
                                onPreviousVideo = ::showPreviousVideo,
                                onNextVideo = ::showNextVideo,
                                showVideoTitle = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    overlays = {
                        pendingRequest?.let { request ->
                            ReceiverBroadcastDialog(
                                request = request,
                                onShown = shareClient::onPendingRequestDialogShown,
                                onDismiss = shareClient::dismissPendingRequest,
                                onAccept = shareClient::acceptPendingRequest
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        display?.displayId?.let(shareClient::updateDisplay)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showLocalSourceIfNeeded(intent)
    }

    override fun onResume() {
        super.onResume()
        if (shareClient.sharedSession.value == null) {
            playbackController.exitInAppPip()
            playerView?.let(playbackController::attach)
        }
        window.enterImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enterImmersiveFullscreen()
    }

    private fun showLocalSourceIfNeeded(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SHARED_MODE, false) || shareClient.sharedSession.value != null) return
        val source = RearNavigation.readSource(intent) as? VideoSource.Asset
        if (source == null) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        showLocalVideo(source)
    }

    private fun enterInAppPip() {
        if (shareClient.sharedSession.value != null) return
        playbackController.enterInAppPip()
        startActivity(RearNavigation.libraryIntent(this))
    }

    private fun showPreviousVideo() {
        videoCatalogNavigator.previous(playbackController.currentSource)?.let(::showLocalVideo)
    }

    private fun showNextVideo() {
        videoCatalogNavigator.next(playbackController.currentSource)?.let(::showLocalVideo)
    }

    private fun showLocalVideo(source: VideoSource.Asset) {
        playbackController.setSharedMode(false)
        playbackController.showFullscreen(source)
    }

    private fun closePlayer() {
        if (shareClient.sharedSession.value != null) {
            shareClient.requestLeaveSharing()
        } else {
            playbackController.stop()
            finish()
        }
    }

    companion object {
        const val EXTRA_SHARED_MODE = "com.ivi.rear.extra.SHARED_MODE"
        private const val REMOTE_POSITION_UPDATE_DELAY_MS = 100L
        private const val CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L
    }
}

private fun SharedSessionSnapshot.currentPositionAt(nowMs: Long): Long {
    val safeDuration = durationMs.coerceAtLeast(0L)
    if (!isPlaying) return positionMs.coerceIn(0L, safeDuration)
    val playbackStartMs = maxOf(anchorElapsedRealtimeMs, scheduledStartElapsedRealtimeMs)
    val elapsedMs = (nowMs - playbackStartMs).coerceAtLeast(0L)
    val projectedPosition = positionMs + (elapsedMs * playbackSpeed).toLong()
    return projectedPosition.coerceIn(0L, safeDuration)
}
