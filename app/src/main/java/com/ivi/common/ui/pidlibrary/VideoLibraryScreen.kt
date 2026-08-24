package com.ivi.common.ui.pidlibrary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivi.R
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.InAppPipState
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.playback.PlayerSurfaceController
import com.ivi.common.ui.coWatchColorScheme
import com.ivi.common.ui.library.ExplorerBackground
import com.ivi.common.ui.library.LibraryPipPlayer
import com.ivi.common.ui.player.Media3PlayerSurface

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
    onFocusedVideoPlay: (AssetVideo) -> Unit,
    useFocusedVideoSizeForPip: Boolean = false,
    pipVideoSurface: (@Composable BoxScope.() -> Unit)? = null
) {
    var focusedIndex by remember(videos) { mutableIntStateOf(0) }
    var previewFocusedIndex by remember(videos) { mutableStateOf<Int?>(null) }
    val displayFocusedIndex = previewFocusedIndex ?: focusedIndex
    val focusedVideo = videos.getOrNull(displayFocusedIndex)
    val density = LocalDensity.current
    val railHorizontalPadding = with(density) { VIDEO_RAIL_HORIZONTAL_PADDING_PX.toDp() }
    val pipWidthPx = if (useFocusedVideoSizeForPip) FOCUSED_VIDEO_WIDTH_PX else NORMAL_VIDEO_WIDTH_PX
    val pipHeightPx = if (useFocusedVideoSizeForPip) FOCUSED_VIDEO_HEIGHT_PX else NORMAL_VIDEO_HEIGHT_PX

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.coWatchColorScheme.librarySurface)
    ) {
        ExplorerBackground(
            video = focusedVideo,
            thumbnailLoader = thumbnailLoader,
            backgroundDrawable = R.drawable.img_passenger_launcher_background,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))

            focusedVideo?.let { video ->
                Text(
                    text = video.title,
                    modifier = Modifier.padding(
                        start = railHorizontalPadding,
                        end = railHorizontalPadding,
                        bottom = 12.dp
                    ),
                    color = MaterialTheme.coWatchColorScheme.libraryTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 44.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            VideoRail(
                videos = videos,
                thumbnailLoader = thumbnailLoader,
                focusedIndex = focusedIndex,
                activeSessionAssetPath = activeSessionAssetPath,
                isSessionPlaying = isSessionPlaying,
                onFocusChanged = { focusedIndex = it },
                onFocusPreviewChanged = { previewFocusedIndex = it },
                onFocusPreviewCleared = { previewFocusedIndex = null },
                onActiveSessionPlaybackToggle = onFocusedPlaybackToggle,
                onVideoPlayRequested = onFocusedVideoPlay
            )
        }

        VideoRailProgress(
            focusedIndex = displayFocusedIndex,
            videoCount = videos.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = railHorizontalPadding,
                    end = railHorizontalPadding
                )
        )

        if (pipState != null) {
            LibraryPipPlayer(
                isPlaying = isSessionPlaying,
                onExpand = onPipSelected,
                onPlaybackToggle = onPipPlaybackToggle,
                onClose = onPipClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // PiP keeps fixed library padding; each variant selects its required video size.
                    .padding(
                        top = 56.dp,
                        end = 28.dp
                    )
                    .width(with(density) { pipWidthPx.toDp() })
                    .height(with(density) { pipHeightPx.toDp() })
            ) {
                if (pipVideoSurface != null) {
                    pipVideoSurface()
                } else {
                    Media3PlayerSurface(
                        playerSurfaceController = playerSurfaceController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
