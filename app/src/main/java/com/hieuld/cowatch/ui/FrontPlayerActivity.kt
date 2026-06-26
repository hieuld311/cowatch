package com.hieuld.cowatch.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

class FrontPlayerActivity : ComponentActivity() {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isApplyingSharedState = false
    private var scheduledStartRunnable: Runnable? = null
    private var lastScheduledStartAt: Long? = null

    private val broadcastEnabledState = mutableStateOf(false)
    private val shareDialogVisibleState = mutableStateOf(false)

    private val demoVideoUrl by lazy {
        "android.resource://${packageName}/${R.raw.demo_video}"
    }

    private val positionTicker = object : Runnable {
        override fun run() {
            val hasScheduledStart = if (::viewModel.isInitialized) {
                viewModel.playbackState.value.hasScheduledStart
            } else {
                false
            }

            if (
                ::player.isInitialized &&
                !isApplyingSharedState &&
                !hasScheduledStart
            ) {
                viewModel.publishHostSnapshot()
            }

            mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[FrontPlayerViewModel::class.java]
        displayRepository = DisplayRepository(this)

        setupPlayer()
        observePlaybackState()
        observeShareSession()

        setContent {
            CoWatchTheme {
                FrontPlayerScreen(
                    player = player,
                    broadcastChecked = broadcastEnabledState.value || shareDialogVisibleState.value,
                    showShareDialog = shareDialogVisibleState.value,
                    displays = getShareTargets(),
                    onBroadcastCheckedChange = ::onBroadcastCheckedChange,
                    onShareDialogDismiss = ::onShareDialogDismissedWithoutSharing,
                    onStartSharing = ::onStartSharing
                )
            }
        }

        mainHandler.post(positionTicker)
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()

        viewModel.attachPlayer(player)
        viewModel.setMedia(
            mediaUri = demoVideoUrl,
            title = "CoWatch Demo"
        )

        viewModel.play()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isApplyingSharedState) return
                if (viewModel.playbackState.value.hasScheduledStart) return
                viewModel.publishHostSnapshot()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (isApplyingSharedState) return
                if (viewModel.playbackState.value.hasScheduledStart) return

                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    viewModel.publishHostSnapshot()
                }
            }

            override fun onPlaybackParametersChanged(
                playbackParameters: PlaybackParameters
            ) {
                if (isApplyingSharedState) return
                if (viewModel.playbackState.value.hasScheduledStart) return

                viewModel.publishHostSnapshot()
            }
        })
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
            viewModel.publishHostSnapshot()
            viewModel.stopSharing()
        }
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                applySharedStateToHost(state)
            }
        }
    }

    private fun observeShareSession() {
        lifecycleScope.launch {
            viewModel.session.collectLatest { session ->
                broadcastEnabledState.value = session != null
            }
        }
    }

    private fun applySharedStateToHost(state: SharedPlaybackState) {
        if (!::player.isInitialized) return
        if (state.mediaUri.isBlank()) return

        isApplyingSharedState = true

        try {
            val scheduledStartAt = state.startAtElapsedRealtimeMs

            if (scheduledStartAt != null) {
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
        } finally {
            isApplyingSharedState = false
        }
    }

    private fun scheduleLocalStartIfNeeded(startAtElapsedRealtimeMs: Long) {
        if (lastScheduledStartAt == startAtElapsedRealtimeMs) return

        lastScheduledStartAt = startAtElapsedRealtimeMs
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)

        val delayMs = (startAtElapsedRealtimeMs - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)

        val runnable = Runnable {
            if (!::player.isInitialized) return@Runnable

            isApplyingSharedState = true

            try {
                viewModel.play()

                mainHandler.postDelayed(
                    {
                        viewModel.clearScheduledStart()
                        viewModel.publishHostSnapshot()
                    },
                    SCHEDULED_START_CLEAR_DELAY_MS
                )
            } finally {
                isApplyingSharedState = false
            }
        }

        scheduledStartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun onStartSharing(displayIds: Set<Int>) {
        shareDialogVisibleState.value = false

        if (displayIds.isEmpty() || !::player.isInitialized) {
            broadcastEnabledState.value = false
            return
        }

        val anchorPositionMs = player.currentPosition
        val wasPlayingBeforeShare = player.isPlaying

        isApplyingSharedState = true

        try {
            player.pause()
        } finally {
            isApplyingSharedState = false
        }

        viewModel.pause()
        viewModel.publishHostSnapshot()

        val sharingStarted = viewModel.startSharing(
            hostDisplayId = getCurrentDisplayId(),
            targetDisplayIds = displayIds,
            anchorPositionMs = anchorPositionMs
        )

        if (!sharingStarted) {
            broadcastEnabledState.value = false

            if (wasPlayingBeforeShare) {
                viewModel.play()
            }
        }
    }

    private fun onShareDialogDismissedWithoutSharing() {
        shareDialogVisibleState.value = false

        if (viewModel.session.value == null) {
            broadcastEnabledState.value = false
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

    private fun clearLocalScheduledStart() {
        lastScheduledStartAt = null
        scheduledStartRunnable?.let(mainHandler::removeCallbacks)
        scheduledStartRunnable = null
    }

    @Suppress("DEPRECATION")
    private fun getCurrentDisplayId(): Int {
        return windowManager.defaultDisplay.displayId
    }

    override fun onStop() {
        if (::player.isInitialized) {
            viewModel.publishHostSnapshot()
        }

        super.onStop()
    }

    override fun onDestroy() {
        clearLocalScheduledStart()
        mainHandler.removeCallbacks(positionTicker)

        if (::player.isInitialized) {
            viewModel.releasePlayer()
            player.release()
        }

        super.onDestroy()
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val SYNC_SEEK_TOLERANCE_MS = 250L
        private const val SCHEDULED_START_CLEAR_DELAY_MS = 200L
    }
}

@Composable
private fun FrontPlayerScreen(
    player: ExoPlayer,
    broadcastChecked: Boolean,
    showShareDialog: Boolean,
    displays: List<DisplayInfo>,
    onBroadcastCheckedChange: (Boolean) -> Unit,
    onShareDialogDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit
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
                        useController = true
                        this.player = player
                    }
                },
                update = { playerView ->
                    playerView.useController = true
                    if (playerView.player !== player) {
                        playerView.player = player
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xEE05070A),
                                Color(0x9905070A),
                                Color.Transparent
                            )
                        )
                    )
            )

            BroadcastTopBar(
                checked = broadcastChecked,
                onCheckedChange = onBroadcastCheckedChange,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )

            if (showShareDialog) {
                ShareDisplaysDialog(
                    displays = displays,
                    onDismiss = onShareDialogDismiss,
                    onStartSharing = onStartSharing
                )
            }
        }
    }
}

@Composable
private fun BroadcastTopBar(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val barAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0.92f,
        label = "barAlpha"
    )
    val statusColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF7EE787) else Color(0xFF8B949E),
        label = "statusColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(barAlpha),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC0D1117),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CoWatch",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = checked,
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Broadcasting",
                            color = Color(0xFFC9D1D9),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Text(
                text = if (checked) "On" else "Off",
                color = Color(0xFFC9D1D9),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 12.dp)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(1.08f)
            )
        }
    }
}

@Composable
private fun ShareDisplaysDialog(
    displays: List<DisplayInfo>,
    onDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit
) {
    var selectedDisplayIds by remember(displays) { mutableStateOf(emptySet<Int>()) }
    val allSelected = displays.isNotEmpty() && selectedDisplayIds.size == displays.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share media playback",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                SelectableDisplayRow(
                    title = "All secondary displays",
                    subtitle = "${displays.size} available",
                    checked = allSelected,
                    enabled = displays.isNotEmpty(),
                    onCheckedChange = { checked ->
                        selectedDisplayIds = if (checked) {
                            displays.map { it.displayId }.toSet()
                        } else {
                            emptySet()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = DividerDefaults.color.copy(alpha = 0.45f)
                )

                if (displays.isEmpty()) {
                    Text(
                        text = "No secondary display found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                    ) {
                        items(displays, key = { it.displayId }) { display ->
                            SelectableDisplayRow(
                                title = display.name,
                                subtitle = "Display id ${display.displayId}",
                                checked = selectedDisplayIds.contains(display.displayId),
                                enabled = true,
                                onCheckedChange = { checked ->
                                    selectedDisplayIds = if (checked) {
                                        selectedDisplayIds + display.displayId
                                    } else {
                                        selectedDisplayIds - display.displayId
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedDisplayIds.isNotEmpty(),
                onClick = { onStartSharing(selectedDisplayIds) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SelectableDisplayRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
