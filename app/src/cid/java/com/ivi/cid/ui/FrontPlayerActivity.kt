package com.ivi.cid.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ivi.cid.app.CIDVideoNavigation
import com.ivi.common.domain.VideoSource
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.common.playback.VideoCatalogNavigator
import com.ivi.cid.ui.player.FrontPlayerScreen
import com.ivi.common.ui.player.hasAudioTrackInitializationFailure
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.enterImmersiveFullscreen
import com.ivi.cid.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FrontPlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    @Inject
    lateinit var playbackController: Media3PlaybackController

    @Inject
    lateinit var seekFrameProvider: SeekFrameProvider

    @Inject
    lateinit var videoCatalogNavigator: VideoCatalogNavigator

    private val viewModel: PlayerViewModel by viewModels()
    private var playerView: PlayerView? = null
    private var audioFallbackApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = CIDVideoNavigation.readVideo(intent) as? VideoSource.Asset
        if (source == null) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = closePlayer()
            }
        )
        setupPlayer(source)
        observePlaybackCompletion()
        window.enterImmersiveFullscreen()

        setContent {
            CoWatchTheme {
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                FrontPlayerScreen(
                    player = player,
                    playerSurfaceController = playbackController,
                    seekFrameProvider = seekFrameProvider,
                    activeAssetPath = playbackState.activeSource?.assetPath,
                    onPlayerViewReady = { playerView = it },
                    onCloseClick = ::closePlayer,
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
        (CIDVideoNavigation.readVideo(intent) as? VideoSource.Asset)?.let(::showFullscreenVideo)
    }

    override fun onResume() {
        super.onResume()
        viewModel.exitInAppPip()
        playerView?.let(playbackController::attach)
        window.enterImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enterImmersiveFullscreen()
    }

    private fun setupPlayer(source: VideoSource.Asset) {
        player = playbackController.exoPlayer
        player.addListener(playerListener)
        viewModel.show(source)
    }

    private fun showFullscreenVideo(source: VideoSource.Asset) {
        if (!::player.isInitialized) return
        audioFallbackApplied = false
        viewModel.show(source)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (audioFallbackApplied || !error.hasAudioTrackInitializationFailure()) return
            Log.w(TAG, "AudioTrack failed; disabling audio track once.", error)
            audioFallbackApplied = true
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val shouldResume = player.playWhenReady
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            player.seekTo(positionMs)
            player.prepare()
            player.playWhenReady = shouldResume
        }
    }

    private fun enterInAppPip() {
        viewModel.enterInAppPip()
        startActivity(CIDVideoNavigation.createLibraryIntent(this))
    }

    private fun showPreviousVideo() {
        videoCatalogNavigator.previous(playbackController.currentSource)?.let(::continuePlayback)
    }

    private fun showNextVideo() {
        videoCatalogNavigator.next(playbackController.currentSource)?.let(::continuePlayback)
    }

    private fun continuePlayback(source: VideoSource.Asset) {
        audioFallbackApplied = false
        viewModel.continuePlayback(source)
    }

    private fun closePlayer() {
        viewModel.close()
        finish()
    }

    private fun observePlaybackCompletion() {
        lifecycleScope.launch {
            viewModel.playbackCompleted.collect {
                if (!isFinishing && !isDestroyed) {
                    showNextVideo()
                }
            }
        }
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.removeListener(playerListener)
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CIDVideoFrontPlayer"
    }
}
