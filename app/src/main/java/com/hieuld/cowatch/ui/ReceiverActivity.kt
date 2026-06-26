package com.hieuld.cowatch.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hieuld.cowatch.player.SharedPlaybackState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class ReceiverActivity : ComponentActivity() {

    private lateinit var viewModel: ReceiverPlayerViewModel
    private lateinit var player: ExoPlayer

    private val mainHandler = Handler(Looper.getMainLooper())

    private var expectedSessionId: String? = null
    private var expectedDisplayId: Int = -1
    private var anchorPositionMs: Long = 0L

    private var currentMediaUri: String = ""
    private var hasReportedReady = false

    private var scheduledStartRunnable: Runnable? = null
    private var lastScheduledStartAt: Long? = null
    private var lastAppliedVersion = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ReceiverPlayerViewModel::class.java]

        expectedSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        expectedDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        anchorPositionMs = intent.getLongExtra(EXTRA_ANCHOR_POSITION_MS, 0L)

        setupPlayer()
        observePlaybackState()
        observeSessionState()

        setContent {
            CoWatchTheme {
                ReceiverScreen(
                    player = player,
                    displayId = expectedDisplayId
                )
            }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !hasReportedReady) {
                    hasReportedReady = true

                    player.seekTo(anchorPositionMs)
                    player.pause()

                    viewModel.markReceiverReady(expectedDisplayId)
                }
            }
        })
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                applySharedState(state)
            }
        }
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                if (
                    viewModel.shouldFinish(
                        session = session,
                        expectedSessionId = expectedSessionId,
                        expectedDisplayId = expectedDisplayId
                    )
                ) {
                    finish()
                }
            }
        }
    }

    private fun applySharedState(state: SharedPlaybackState) {
        if (!::player.isInitialized) return
        if (state.mediaUri.isBlank()) return
        if (state.version <= lastAppliedVersion) return

        lastAppliedVersion = state.version

        if (currentMediaUri != state.mediaUri) {
            prepareMediaAtAnchor(state.mediaUri)
            return
        }

        val scheduledStartAt = state.startAtElapsedRealtimeMs

        if (scheduledStartAt != null) {
            player.pause()
            seekIfNeeded(state.positionMs)
            scheduleLocalStartIfNeeded(scheduledStartAt)
            return
        }

        clearLocalScheduledStart()

        seekIfNeeded(state.positionMs)

        if (player.playbackParameters.speed != state.playbackSpeed) {
            player.setPlaybackSpeed(state.playbackSpeed)
        }

        if (state.isPlaying && !player.isPlaying) {
            player.play()
        } else if (!state.isPlaying && player.isPlaying) {
            player.pause()
        }
    }

    private fun prepareMediaAtAnchor(mediaUri: String) {
        currentMediaUri = mediaUri
        hasReportedReady = false

        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.seekTo(anchorPositionMs)
        player.pause()
    }

    private fun scheduleLocalStartIfNeeded(startAtElapsedRealtimeMs: Long) {
        if (lastScheduledStartAt == startAtElapsedRealtimeMs) return

        lastScheduledStartAt = startAtElapsedRealtimeMs
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)

        val delayMs = (startAtElapsedRealtimeMs - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)

        val runnable = Runnable {
            if (::player.isInitialized) {
                player.play()
            }
        }

        scheduledStartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun seekIfNeeded(positionMs: Long) {
        val positionDiff = abs(player.currentPosition - positionMs)

        if (positionDiff > SYNC_SEEK_TOLERANCE_MS) {
            player.seekTo(positionMs)
        }
    }

    private fun clearLocalScheduledStart() {
        lastScheduledStartAt = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
    }

    override fun onDestroy() {
        clearLocalScheduledStart()

        if (::player.isInitialized) {
            player.release()
        }

        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_DISPLAY_ID = "extra_display_id"
        const val EXTRA_ANCHOR_POSITION_MS = "extra_anchor_position_ms"

        private const val SYNC_SEEK_TOLERANCE_MS = 250L
    }
}

@Composable
private fun ReceiverScreen(
    player: ExoPlayer,
    displayId: Int
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { playerView ->
                    playerView.useController = false
                    if (playerView.player !== player) {
                        playerView.player = player
                    }
                }
            )

            AnimatedVisibility(
                visible = displayId >= 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x990D1117)
                ) {
                    Text(
                        text = "Receiver display $displayId",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
