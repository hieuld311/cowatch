package com.hieuld.cowatch.ui

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.hieuld.cowatch.R
import com.hieuld.cowatch.display.DisplayInfo
import com.hieuld.cowatch.display.DisplayRepository
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.render.VideoRenderEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class FrontPlayerActivity : ComponentActivity() {

    private lateinit var viewModel: FrontPlayerViewModel
    private lateinit var player: ExoPlayer
    private lateinit var displayRepository: DisplayRepository
    private lateinit var renderEngine: FrameFanoutRenderEngine

    private var audioFallbackApplied = false

    private val broadcastEnabledState = mutableStateOf(false)
    private val shareDialogVisibleState = mutableStateOf(false)

    private val demoVideoUrl by lazy {
        "android.resource://${packageName}/${R.raw.demo_video}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    displays = getShareTargets(),
                    onBroadcastCheckedChange = ::onBroadcastCheckedChange,
                    onShareDialogDismiss = ::onShareDialogDismissedWithoutSharing,
                    onStartSharing = ::onStartSharing
                )
            }
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
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(demoVideoUrl))
        player.prepare()
        player.play()

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                renderEngine.setVideoSize(
                    width = videoSize.width,
                    height = videoSize.height,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                if (audioFallbackApplied || !error.hasAudioTrackInitializationFailure()) return

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
            }
        )

        if (!sharingStarted) {
            broadcastEnabledState.value = false

            if (wasPlayingBeforeShare) {
                player.play()
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

    @Suppress("DEPRECATION")
    private fun getCurrentDisplayId(): Int {
        return windowManager.defaultDisplay.displayId
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.clearVideoSurface()
            player.release()
        }

        if (::renderEngine.isInitialized) {
            renderEngine.release()
        }

        super.onDestroy()
    }
}

private fun Throwable.hasAudioTrackInitializationFailure(): Boolean {
    var current: Throwable? = this

    while (current != null) {
        if (
            current.javaClass.name.contains("AudioSink") ||
            current.message?.contains("Cannot create AudioTrack") == true
        ) {
            return true
        }
        current = current.cause
    }

    return false
}

private const val HOST_RENDER_OUTPUT_ID = Int.MIN_VALUE

@Composable
private fun FrontPlayerScreen(
    player: Player,
    renderEngine: VideoRenderEngine,
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
            Column(modifier = Modifier.fillMaxSize()) {
                BroadcastTopBar(
                    checked = broadcastChecked,
                    onCheckedChange = onBroadcastCheckedChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    HostVideoSurface(
                        modifier = Modifier.fillMaxSize(),
                        renderEngine = renderEngine
                    )

                    HostPlaybackControls(
                        modifier = Modifier
                            .matchParentSize(),
                        player = player
                    )
                }
            }

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
private fun HostVideoSurface(
    renderEngine: VideoRenderEngine,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        renderEngine.addOutput(
                            outputId = HOST_RENDER_OUTPUT_ID,
                            surface = holder.surface,
                            width = width,
                            height = height
                        )
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        renderEngine.removeOutput(HOST_RENDER_OUTPUT_ID)
                    }
                })
            }
        }
    )
}

@Composable
private fun HostPlaybackControls(
    player: Player,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var durationMs by remember {
        mutableStateOf(player.duration.takeIf { it > 0L } ?: 0L)
    }
    var positionMs by remember { mutableStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var sliderPositionMs by remember { mutableStateOf(positionMs) }
    var isDragging by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableStateOf(0) }

    fun showControls() {
        controlsVisible = true
        interactionVersion += 1
    }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            durationMs = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            positionMs = player.currentPosition.coerceAtLeast(0L)

            if (!isDragging) {
                sliderPositionMs = positionMs
            }

            delay(250L)
        }
    }

    LaunchedEffect(interactionVersion, controlsVisible, isDragging) {
        if (controlsVisible && !isDragging) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            }
        ) {
            showControls()
        }
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    showControls()
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
            ) {
                Icon(
                    painter = mediaControlPainter(isPlaying),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                VideoSeekBar(
                    positionMs = sliderPositionMs,
                    durationMs = durationMs,
                    onSeekPreview = { position ->
                        showControls()
                        isDragging = true
                        sliderPositionMs = position
                    },
                    onSeekFinished = {
                        player.seekTo(sliderPositionMs)
                        isDragging = false
                        showControls()
                    },
                    enabled = durationMs > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(1) }

    fun positionFromX(x: Float): Long {
        if (durationMs <= 0L || widthPx <= 0) return 0L

        val fraction = (x / widthPx)
            .coerceIn(0f, 1f)
        return (durationMs * fraction).toLong()
    }

    Canvas(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { size -> widthPx = size.width.coerceAtLeast(1) }
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput

                detectTapGestures { offset ->
                    onSeekPreview(positionFromX(offset.x))
                    onSeekFinished()
                }
            }
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekPreview(positionFromX(offset.x))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onSeekPreview(positionFromX(change.position.x))
                    },
                    onDragEnd = onSeekFinished,
                    onDragCancel = onSeekFinished
                )
            }
    ) {
        val trackY = size.height / 2f
        val progress = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val progressX = size.width * progress
        val strokeWidth = 6.dp.toPx()
        val thumbRadius = 12.dp.toPx()

        drawLine(
            color = Color(0xFF777777),
            start = androidx.compose.ui.geometry.Offset(0f, trackY),
            end = androidx.compose.ui.geometry.Offset(size.width, trackY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF111111),
            start = androidx.compose.ui.geometry.Offset(0f, trackY),
            end = androidx.compose.ui.geometry.Offset(progressX, trackY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(progressX, trackY)
        )
    }
}

@Composable
private fun mediaControlPainter(isPlaying: Boolean): Painter {
    return painterResource(
        id = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
    )
}

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L

@Composable
private fun BroadcastTopBar(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0D1117),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (checked) "Sharing" else "Share",
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
