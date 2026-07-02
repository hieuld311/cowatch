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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.display.DisplayInfo
import com.hieuld.cowatch.display.DisplayRepository
import com.hieuld.cowatch.media.VideoSource
import com.hieuld.cowatch.playback.CoWatchPlaybackState
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.ui.player.FrontPlayerScreen
import com.hieuld.cowatch.ui.player.hasAudioTrackInitializationFailure
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FrontPlayerActivity : ComponentActivity() {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository
    private lateinit var renderEngine: FrameFanoutRenderEngine

    private var audioFallbackApplied = false

    private val broadcastEnabledState = mutableStateOf(false)
    private val shareDialogVisibleState = mutableStateOf(false)
    private val fullscreenState = mutableStateOf(false)
    private var selectedSource: VideoSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedSource = FrontPlayerContract.readVideo(intent)

        if (selectedSource == null) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[FrontPlayerViewModel::class.java]
        displayRepository = DisplayRepository(this)
        renderEngine = FrameFanoutRenderEngine()

        setupPlayer()
        observeShareSession()

        setContent {
            CoWatchTheme {
                FrontPlayerScreen(
                    player = player,
                    renderEngine = renderEngine,
                    broadcastChecked = broadcastEnabledState.value || shareDialogVisibleState.value,
                    showShareDialog = shareDialogVisibleState.value,
                    isFullscreen = fullscreenState.value,
                    displays = getShareTargets(),
                    onBroadcastCheckedChange = ::onBroadcastCheckedChange,
                    onShareDialogDismiss = ::onShareDialogDismissedWithoutSharing,
                    onStartSharing = ::onStartSharing,
                    onFullscreenToggle = ::onFullscreenToggle,
                    onBackClick = ::onBackToLibrary
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val video = FrontPlayerContract.readVideo(intent) ?: return
        selectedSource = video

        if (::player.isInitialized) {
            playVideo(video)
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            volume = 1f
        }
        player.setVideoSurface(renderEngine.inputSurface)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.setPlaybackState(
                    when (playbackState) {
                        Player.STATE_BUFFERING -> CoWatchPlaybackState.Preparing
                        Player.STATE_READY -> {
                            if (viewModel.session.value != null && player.isPlaying) {
                                CoWatchPlaybackState.Sharing
                            } else if (player.isPlaying) {
                                CoWatchPlaybackState.Playing
                            } else {
                                CoWatchPlaybackState.Paused
                            }
                        }
                        Player.STATE_ENDED -> CoWatchPlaybackState.Paused
                        else -> CoWatchPlaybackState.Idle
                    }
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val state = if (viewModel.session.value != null && isPlaying) {
                    CoWatchPlaybackState.Sharing
                } else if (isPlaying) {
                    CoWatchPlaybackState.Playing
                } else {
                    CoWatchPlaybackState.Paused
                }
                viewModel.setPlaybackState(state)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                renderEngine.setVideoSize(
                    width = videoSize.width,
                    height = videoSize.height,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                )
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
        })

        selectedSource?.let(::playVideo)
    }

    private fun playVideo(source: VideoSource) {
        viewModel.setPlaybackState(CoWatchPlaybackState.Preparing)
        audioFallbackApplied = false
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        player.setMediaItem(source.toMediaItem(packageName))
        player.prepare()
        player.play()
    }

    private fun onBroadcastCheckedChange(checked: Boolean) {
        if (checked) {
            val targets = getShareTargets()

            if (targets.isEmpty()) {
                Toast.makeText(
                    this,
                    "No secondary display available",
                    Toast.LENGTH_SHORT
                ).show()

                broadcastEnabledState.value = false
                shareDialogVisibleState.value = false
                return
            }

            shareDialogVisibleState.value = true
        } else {
            shareDialogVisibleState.value = false
            viewModel.stopSharing()
            updatePlaybackStateFromPlayer()
        }
    }

    private fun observeShareSession() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                broadcastEnabledState.value = session != null
            }
        }
    }

    private fun onStartSharing(displayIds: Set<Int>) {
        shareDialogVisibleState.value = false

        if (displayIds.isEmpty() || !::player.isInitialized) {
            broadcastEnabledState.value = false
            return
        }

        val anchorPositionMs = player.currentPosition
        val wasPlayingBeforeShare = player.isPlaying
        viewModel.setPlaybackState(CoWatchPlaybackState.SharingPreparing)
        player.pause()

        val sharingStarted = viewModel.startSharing(
            context = this,
            hostDisplayId = getCurrentDisplayId(),
            targetDisplayIds = displayIds,
            anchorPositionMs = anchorPositionMs,
            renderEngine = renderEngine,
            onAllDisplaysReady = { startPositionMs ->
                player.seekTo(startPositionMs)
                if (wasPlayingBeforeShare) {
                    player.play()
                }
                viewModel.setPlaybackState(CoWatchPlaybackState.Sharing)
            }
        )

        if (!sharingStarted) {
            broadcastEnabledState.value = false
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
    }

    private fun onShareDialogDismissedWithoutSharing() {
        shareDialogVisibleState.value = false

        if (viewModel.session.value == null) {
            broadcastEnabledState.value = false
            updatePlaybackStateFromPlayer()
        }
    }

    private fun onFullscreenToggle() {
        val fullscreen = !fullscreenState.value
        fullscreenState.value = fullscreen
        applyFullscreenMode(fullscreen)
    }

    private fun onBackToLibrary() {
        viewModel.stopSharing()
        viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
        finish()
    }

    private fun updatePlaybackStateFromPlayer() {
        if (!::player.isInitialized) {
            viewModel.setPlaybackState(CoWatchPlaybackState.Idle)
            return
        }

        viewModel.setPlaybackState(
            if (player.isPlaying) {
                CoWatchPlaybackState.Playing
            } else {
                CoWatchPlaybackState.Paused
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
        if (fullscreenState.value) {
            applyFullscreenMode(false)
        }

        if (::player.isInitialized) {
            player.clearVideoSurface()
            player.release()
        }

        if (::renderEngine.isInitialized) {
            renderEngine.release()
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "FrontPlayerActivity"
    }
}
