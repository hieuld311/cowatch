package com.hieuld.cowatch.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.hieuld.cowatch.media.RawVideo
import com.hieuld.cowatch.media.RawVideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG_VIDEO_LIBRARY = "CoWatchVideoLibrary"
private const val THUMBNAIL_FRAME_US = 3_000_000L
private const val THUMBNAIL_WIDTH = 640
private const val THUMBNAIL_HEIGHT = 360

class VideoLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videos = RawVideoRepository.listVideos()

        setContent {
            CoWatchTheme {
                MediaExplorerScreen(
                    videos = videos,
                    onVideoSelected = { video ->
                        startActivity(FrontPlayerContract.createIntent(this, video))
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaExplorerScreen(
    videos: List<RawVideo>,
    onVideoSelected: (RawVideo) -> Unit
) {
    var focusedIndex by remember(videos) { mutableIntStateOf(0) }
    val focusedVideo = videos.getOrNull(focusedIndex)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF142231)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ExplorerBackground(
                video = focusedVideo,
                modifier = Modifier.fillMaxSize()
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(1f))

                focusedVideo?.let { video ->
                    Text(
                        text = video.title,
                        modifier = Modifier.padding(start = 28.dp, bottom = 12.dp),
                        color = Color(0xFFD6E3EF),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                VideoRail(
                    videos = videos,
                    focusedIndex = focusedIndex,
                    onFocusChanged = { index -> focusedIndex = index },
                    onVideoSelected = onVideoSelected
                )
            }
        }
    }
}

@Composable
private fun ExplorerBackground(
    video: RawVideo?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFF70879B))
    ) {
        if (video != null) {
            VideoThumbnail(
                video = video,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            PlaceholderCross(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VideoRail(
    videos: List<RawVideo>,
    focusedIndex: Int,
    onFocusChanged: (Int) -> Unit,
    onVideoSelected: (RawVideo) -> Unit
) {
    var dragOffsetPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableIntStateOf(1) }

    fun wrappedIndex(index: Int): Int {
        if (videos.isEmpty()) return 0
        return ((index % videos.size) + videos.size) % videos.size
    }

    fun moveFocus(delta: Int) {
        if (videos.size <= 1 || delta == 0) {
            dragOffsetPx = 0f
            return
        }

        onFocusChanged(wrappedIndex(focusedIndex + delta))
        dragOffsetPx = 0f
    }

    val railHeight = 196.dp
    val focusedWidth = 286.dp
    val focusedHeight = 161.dp
    val sideWidth = 190.dp
    val sideHeight = 107.dp
    val horizontalPadding = 16.dp
    val topPadding = 12.dp
    val cardGap = 12.dp
    val density = LocalDensity.current
    val slotDistancePx = with(density) { sideWidth.toPx() + cardGap.toPx() }
    val focusedWidthPx = with(density) { focusedWidth.toPx() }
    val sideWidthPx = with(density) { sideWidth.toPx() }
    val focusedHeightPx = with(density) { focusedHeight.toPx() }
    val sideHeightPx = with(density) { sideHeight.toPx() }
    val startPaddingPx = with(density) { horizontalPadding.toPx() }
    val topPaddingPx = with(density) { topPadding.toPx() }
    val cardGapPx = with(density) { cardGap.toPx() }
    val visibleSlots = max(1, ((containerWidthPx - startPaddingPx) / max(slotDistancePx, 1f)).roundToInt() + 2)
    val dragSlots = dragOffsetPx / max(slotDistancePx, 1f)

    fun leadingSlot(index: Int): Float {
        if (videos.isEmpty()) return 0f

        val baseSlot = wrappedIndex(index - focusedIndex).toFloat()
        return if (dragSlots > 0f && baseSlot > videos.size / 2f) {
            baseSlot - videos.size + dragSlots
        } else {
            baseSlot + dragSlots
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(railHeight)
            .background(Color(0xAA2A4154))
            .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(1) }
            .pointerInput(videos.size, focusedIndex) {
                if (videos.size <= 1) return@pointerInput

                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = slotDistancePx * 0.32f
                        when {
                            dragOffsetPx < -threshold -> moveFocus(1)
                            dragOffsetPx > threshold -> moveFocus(-1)
                            else -> dragOffsetPx = 0f
                        }
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount
                    }
                )
            }
    ) {
        if (videos.isEmpty()) return@Box

        val cards = videos.indices.mapNotNull { index ->
            val slot = leadingSlot(index)
            if (slot < -1.05f || slot > visibleSlots + 0.35f) return@mapNotNull null
            index to slot
        }.sortedByDescending { (_, slot) -> abs(slot) }

        cards.forEach { (actualIndex, slot) ->
            val centerProgress = (1f - abs(slot)).coerceIn(0f, 1f)
            val widthPx = sideWidthPx + (focusedWidthPx - sideWidthPx) * centerProgress
            val heightPx = sideHeightPx + (focusedHeightPx - sideHeightPx) * centerProgress
            val xPx = startPaddingPx + if (slot >= 0f) {
                if (slot <= 1f) {
                    slot * (focusedWidthPx + cardGapPx)
                } else {
                    focusedWidthPx + cardGapPx + (slot - 1f) * slotDistancePx
                }
            } else {
                slot * slotDistancePx
            }
            val yPx = topPaddingPx
            val focused = centerProgress > 0.96f
            val cardScale = 0.96f + centerProgress * 0.04f

            VideoRailCard(
                video = videos[actualIndex],
                selected = focused,
                width = with(density) { widthPx.toDp() },
                thumbnailHeight = with(density) { heightPx.toDp() },
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .scale(cardScale)
                    .graphicsLayer { shadowElevation = if (focused) 14f else 0f },
                onClick = {
                    if (focused) {
                        onVideoSelected(videos[actualIndex])
                    } else {
                        onFocusChanged(actualIndex)
                    }
                }
            )
        }
    }
}

@Composable
private fun VideoRailCard(
    video: RawVideo,
    selected: Boolean,
    width: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Top
    ) {
        val thumbnailModifier = Modifier
            .fillMaxWidth()
            .height(thumbnailHeight)
            .background(Color(0xFF70879B))
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                } else {
                    Modifier
                }
            )

        Box(
            modifier = thumbnailModifier
        ) {
            VideoThumbnail(
                video = video,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    PlayTriangle(
                        color = Color(0xFF6E8295),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(12.dp)
                    )
                }
            }
        }

        if (!selected) {
            Text(
                text = video.title,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaceholderCross(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color(0x334A5E71)
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun VideoThumbnail(
    video: RawVideo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(
        initialValue = null,
        key1 = video.resId,
        key2 = context
    ) {
        value = withContext(Dispatchers.IO) {
            loadRawVideoThumbnail(
                context = context,
                video = video
            )
        }
    }

    val imageBitmap = remember(thumbnail) { thumbnail?.asImageBitmap() }

    Box(modifier = modifier.background(Color(0xFF70879B))) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun loadRawVideoThumbnail(
    context: android.content.Context,
    video: RawVideo
): Bitmap? {
    return RawVideoThumbnailCache.getOrLoad(context, video)
}

private object RawVideoThumbnailCache {
    private val cache = object : LruCache<Int, Bitmap>(24 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    @Synchronized
    fun getOrLoad(context: android.content.Context, video: RawVideo): Bitmap? {
        cache.get(video.resId)?.let { return it }

        val bitmap = decodeThumbnail(context, video) ?: return null
        cache.put(video.resId, bitmap)
        return bitmap
    }

    private fun decodeThumbnail(
        context: android.content.Context,
        video: RawVideo
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.resources.openRawResourceFd(video.resId).use { afd ->
                if (afd == null) {
                    Log.w(
                        TAG_VIDEO_LIBRARY,
                        "Raw resource has no file descriptor title=${video.title}, resId=${video.resId}"
                    )
                    return null
                }

                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                retriever.getScaledFrameAtTime(
                    THUMBNAIL_FRAME_US,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT
                ) ?: retriever.getFrameAtTime(
                    THUMBNAIL_FRAME_US,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG_VIDEO_LIBRARY,
                "Raw thumbnail load failed title=${video.title}, resId=${video.resId}",
                throwable
            )
            null
        } finally {
            retriever.release()
        }
    }
}

@Composable
private fun PlayTriangle(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.12f)
            lineTo(size.width * 0.25f, size.height * 0.88f)
            lineTo(size.width * 0.86f, size.height * 0.50f)
            close()
        }
        drawPath(path = path, color = color)
    }
}
