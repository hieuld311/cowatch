package com.hieuld.cowatch.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hieuld.cowatch.R
import com.hieuld.cowatch.player.SharedPlaybackState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class ReceiverActivity : AppCompatActivity() {

    private lateinit var viewModel: ReceiverPlayerViewModel
    private lateinit var player: ExoPlayer

    private val mainHandler = Handler(Looper.getMainLooper())

    private var expectedSessionId: String? = null
    private var expectedDisplayId: Int = -1
    private var anchorPositionMs: Long = 0L

    private var currentMediaUrl: String = ""
    private var hasReportedReady = false
    private var isApplyingSharedState = false

    private var scheduledStartRunnable: Runnable? = null
    private var lastScheduledStartAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_receiver)

        viewModel = ViewModelProvider(this)[ReceiverPlayerViewModel::class.java]
        expectedSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        expectedDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        anchorPositionMs = intent.getLongExtra(EXTRA_ANCHOR_POSITION_MS, 0L)

        findViewById<TextView>(R.id.tvReceiverInfo).text =
            "Receiver displayId=$expectedDisplayId"

        setupPlayer()
        observePlaybackState()
        observeSessionState()
    }

    // Receiver owns a local ExoPlayer instance but never writes playback commands back.
    private fun setupPlayer() {
        val playerView = findViewById<PlayerView>(R.id.playerView)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !hasReportedReady) {
                    hasReportedReady = true

                    isApplyingSharedState = true
                    try {
                        player.seekTo(anchorPositionMs)
                        player.pause()
                    } finally {
                        isApplyingSharedState = false
                    }

                    viewModel.markReceiverReady(expectedDisplayId)
                }
            }
        })
    }

    // Playback state tells the passive receiver what to render.
    private fun observePlaybackState() {
        lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                applySharedState(state)
            }
        }
    }

    // Session state decides whether this receiver still belongs on this display.
    private fun observeSessionState() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                if (viewModel.shouldFinish(session, expectedSessionId, expectedDisplayId)) {
                    finish()
                }
            }
        }
    }

    private fun applySharedState(state: SharedPlaybackState) {
        if (!::player.isInitialized || state.mediaUrl.isBlank()) return

        isApplyingSharedState = true

        try {
            if (currentMediaUrl != state.mediaUrl) {
                prepareMediaAtAnchor(state.mediaUrl)
                return
            }

            val scheduledStartAt = state.startAtElapsedRealtimeMs

            if (scheduledStartAt != null && state.isPlaying) {
                seekIfNeeded(state.positionMs)
                scheduleLocalStartIfNeeded(scheduledStartAt)
                return
            }

            clearScheduledStart()
            seekIfNeeded(state.positionMs)

            if (player.playbackParameters.speed != state.playbackSpeed) {
                player.setPlaybackSpeed(state.playbackSpeed)
            }

            if (state.isPlaying && !player.isPlaying) {
                player.play()
            } else if (!state.isPlaying && player.isPlaying) {
                player.pause()
            }
        } finally {
            isApplyingSharedState = false
        }
    }

    // Initial receiver preparation must land on the same anchor as the host.
    private fun prepareMediaAtAnchor(mediaUrl: String) {
        currentMediaUrl = mediaUrl
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.prepare()
        player.seekTo(anchorPositionMs)
        player.pause()
    }

    // Scheduled start is the only continuous-sync mechanism in the lightweight branch.
    private fun scheduleLocalStartIfNeeded(startAtElapsedRealtimeMs: Long) {
        if (lastScheduledStartAt == startAtElapsedRealtimeMs) return

        lastScheduledStartAt = startAtElapsedRealtimeMs
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)

        val delayMs = (startAtElapsedRealtimeMs - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)

        val runnable = Runnable {
            if (::player.isInitialized) {
                isApplyingSharedState = true
                try {
                    player.play()
                } finally {
                    isApplyingSharedState = false
                }
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

    private fun clearScheduledStart() {
        lastScheduledStartAt = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
    }

    override fun onDestroy() {
        clearScheduledStart()

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
