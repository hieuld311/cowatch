package com.ivi.cid.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.InAppPipState
import com.ivi.common.playback.PlayerSurfaceController
import com.ivi.common.ui.library.ExplorerBackground
import com.ivi.common.ui.library.ExplorerTitleColor
import com.ivi.common.ui.library.LibraryPipPlayer
import com.ivi.cid.ui.surface.CIDPlayerSurface

@Composable
fun VideoLibraryScreen(
    videos: List<AssetVideo>,
    thumbnailLoader: ThumbnailLoader,
    playerSurfaceController: PlayerSurfaceController,
    pipState: InAppPipState?,
    activeSessionAssetPath: String?,
    isSessionPlaying: Boolean,
    onPipSelected: () -> Unit,
    onPipPlaybackToggle: () -> Unit,
    onFocusedPlaybackToggle: () -> Unit,
    onPipClose: () -> Unit,
    onFocusedVideoPlay: (AssetVideo) -> Unit
) {
    var focusedIndex by remember(videos) { mutableIntStateOf(0) }
    var previewFocusedIndex by remember(videos) { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val displayFocusedVideo = videos.getOrNull(previewFocusedIndex ?: focusedIndex)
    val backgroundVideo = videos.getOrNull(focusedIndex)
    // CIDVideo's design specifies fixed pixel geometry for the left-aligned video rail.
    val railWidthPx = FOCUSED_VIDEO_WIDTH_PX + VERTICAL_RAIL_LEFT_PADDING_PX * 2f

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ExplorerBackground(backgroundVideo, thumbnailLoader, Modifier.fillMaxSize())
        VerticalVideoRail(
            videos = videos,
            thumbnailLoader = thumbnailLoader,
            focusedIndex = focusedIndex,
            activeSessionAssetPath = activeSessionAssetPath,
            isSessionPlaying = isSessionPlaying,
            railWidthPx = railWidthPx,
            onFocusChanged = { focusedIndex = it },
            onFocusPreviewChanged = { previewFocusedIndex = it },
            onFocusPreviewCleared = { previewFocusedIndex = null },
            onActiveSessionPlaybackToggle = onFocusedPlaybackToggle,
            onVideoPlayRequested = onFocusedVideoPlay,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        displayFocusedVideo?.let { video ->
            Text(
                text = video.title,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 36.dp),
                color = ExplorerTitleColor,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (pipState != null) {
            LibraryPipPlayer(
                isPlaying = isSessionPlaying,
                onExpand = onPipSelected,
                onPlaybackToggle = onPipPlaybackToggle,
                onClose = onPipClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 28.dp)
                    .width(with(density) { NORMAL_VIDEO_WIDTH_PX.toDp() })
                    .height(with(density) { NORMAL_VIDEO_HEIGHT_PX.toDp() })
            ) {
                CIDPlayerSurface(
                    playerSurfaceController = playerSurfaceController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

internal const val FOCUSED_VIDEO_WIDTH_PX = 604f
internal const val FOCUSED_VIDEO_HEIGHT_PX = 304f
internal const val NORMAL_VIDEO_WIDTH_PX = 398f
internal const val NORMAL_VIDEO_HEIGHT_PX = 224f
internal const val VERTICAL_RAIL_LEFT_PADDING_PX = 24f
