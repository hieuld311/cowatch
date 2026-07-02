package com.hieuld.cowatch.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hieuld.cowatch.media.RawVideo

@Composable
fun VideoLibraryScreen(
    videos: List<RawVideo>,
    onVideoSelected: (RawVideo) -> Unit
) {
    var focusedIndex by remember(videos) { mutableIntStateOf(0) }
    var previewFocusedIndex by remember(videos) { mutableStateOf<Int?>(null) }
    val displayFocusedIndex = previewFocusedIndex ?: focusedIndex
    val focusedVideo = videos.getOrNull(displayFocusedIndex)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ExplorerSurfaceColor
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
                        color = ExplorerTitleColor,
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
                    onFocusChanged = { focusedIndex = it },
                    onFocusPreviewChanged = { previewFocusedIndex = it },
                    onFocusPreviewCleared = { previewFocusedIndex = null },
                    onVideoSelected = onVideoSelected
                )
            }
        }
    }
}
