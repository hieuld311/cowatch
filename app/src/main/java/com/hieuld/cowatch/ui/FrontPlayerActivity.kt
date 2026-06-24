package com.hieuld.cowatch.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hieuld.cowatch.R
import com.hieuld.cowatch.display.DisplayInfo
import com.hieuld.cowatch.display.DisplayRepository
import com.hieuld.cowatch.player.SharedPlaybackState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class FrontPlayerActivity : AppCompatActivity(), ShareDialogFragment.Callback {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository
    private lateinit var switchBroadcast: SwitchCompat

    private val mainHandler = Handler(Looper.getMainLooper())

    private var ignoreBroadcastToggleChange = false
    private var isApplyingSharedState = false
    private var scheduledStartRunnable: Runnable? = null
    private var lastScheduledStartAt: Long? = null

    private val demoVideoUrl by lazy {
        "android.resource://${packageName}/${R.raw.demo_video}"
    }

    private val positionTicker = object : Runnable {
        override fun run() {
            if (::player.isInitialized && !isApplyingSharedState) {
                viewModel.updatePositionFromHost(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L
                )
            }

            mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_front_player)

        viewModel = ViewModelProvider(this)[FrontPlayerViewModel::class.java]
        displayRepository = DisplayRepository(this)

        viewModel.setMediaIfNeeded(demoVideoUrl)

        setupPlayer()
        setupBroadcastToggle()
        observePlaybackState()
        observeShareSession()

        mainHandler.post(positionTicker)
    }

    // Builds the host ExoPlayer and forwards user-originated player events to the ViewModel.
    private fun setupPlayer() {
        val playerView = findViewById<PlayerView>(R.id.playerView)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val state = viewModel.playbackState.value
        val mediaUri = state.mediaUrl.ifBlank { demoVideoUrl }

        player.setMediaItem(MediaItem.fromUri(mediaUri))
        player.prepare()
        player.seekTo(state.positionMs)
        player.playWhenReady = state.isPlaying && !state.hasSharedTimeline

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isApplyingSharedState) return

                val positionMs = player.currentPosition
                val durationMs = player.duration.takeIf { it > 0L } ?: 0L

                if (isPlaying) {
                    viewModel.playAt(positionMs)
                } else {
                    viewModel.pauseAt(positionMs, durationMs)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (isApplyingSharedState) return

                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    viewModel.seekTo(newPosition.positionMs)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (isApplyingSharedState) return

                viewModel.updateSpeedFromHost(
                    speed = playbackParameters.speed,
                    positionMs = player.currentPosition
                )
            }
        })
    }

    // Broadcast switch owns only UI flow; session mutations go through the ViewModel.
    private fun setupBroadcastToggle() {
        switchBroadcast = findViewById(R.id.switchBroadcast)

        switchBroadcast.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreBroadcastToggleChange) return@setOnCheckedChangeListener

            if (isChecked) {
                val targets = getShareTargets()

                if (targets.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No secondary display available",
                        Toast.LENGTH_SHORT
                    ).show()

                    setBroadcastToggleChecked(false)
                    return@setOnCheckedChangeListener
                }

                ShareDialogFragment().show(
                    supportFragmentManager,
                    "ShareDialogFragment"
                )
            } else {
                viewModel.stopSharing()
            }
        }
    }

    // Shared playback state drives host player changes without letting listener loops echo back.
    private fun observePlaybackState() {
        lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                applySharedStateToHost(state)
            }
        }
    }

    // Session state is reflected in the Broadcast switch.
    private fun observeShareSession() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                setBroadcastToggleChecked(session != null)
            }
        }
    }

    private fun applySharedStateToHost(state: SharedPlaybackState) {
        if (!::player.isInitialized || state.mediaUrl.isBlank()) return

        isApplyingSharedState = true

        try {
            val scheduledStartAt = state.startAtElapsedRealtimeMs

            if (scheduledStartAt != null && state.isPlaying) {
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

    // Scheduled start is local so the host and receivers can start from the same future time.
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

    override fun onStartSharing(displayIds: Set<Int>) {
        if (displayIds.isEmpty() || !::player.isInitialized) {
            setBroadcastToggleChecked(false)
            return
        }

        val anchorPositionMs = player.currentPosition
        val durationMs = player.duration.takeIf { it > 0L } ?: 0L

        isApplyingSharedState = true
        try {
            player.pause()
        } finally {
            isApplyingSharedState = false
        }

        viewModel.pauseAt(anchorPositionMs, durationMs)
        viewModel.startSharing(
            hostDisplayId = getCurrentDisplayId(),
            targetDisplayIds = displayIds,
            anchorPositionMs = anchorPositionMs
        )
    }

    override fun onShareDialogDismissedWithoutSharing() {
        if (viewModel.session.value == null) {
            setBroadcastToggleChecked(false)
        }
    }

    fun getShareTargets(): List<DisplayInfo> {
        return displayRepository.getShareTargets(getCurrentDisplayId())
    }

    private fun seekIfNeeded(positionMs: Long) {
        val positionDiff = abs(player.currentPosition - positionMs)

        if (positionDiff > SYNC_SEEK_TOLERANCE_MS) {
            player.seekTo(positionMs)
        }
    }

    private fun setBroadcastToggleChecked(checked: Boolean) {
        if (!::switchBroadcast.isInitialized) return

        ignoreBroadcastToggleChange = true
        switchBroadcast.isChecked = checked
        ignoreBroadcastToggleChange = false
    }

    @Suppress("DEPRECATION")
    private fun getCurrentDisplayId(): Int {
        return windowManager.defaultDisplay.displayId
    }

    private fun clearScheduledStart() {
        lastScheduledStartAt = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
    }

    override fun onStop() {
        if (::player.isInitialized) {
            viewModel.updatePositionFromHost(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L
            )
        }

        super.onStop()
    }

    override fun onDestroy() {
        clearScheduledStart()
        mainHandler.removeCallbacks(positionTicker)

        if (::player.isInitialized) {
            player.release()
        }

        super.onDestroy()
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val SYNC_SEEK_TOLERANCE_MS = 250L
    }
}
