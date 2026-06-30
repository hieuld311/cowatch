package com.hieuld.cowatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hieuld.cowatch.media.RawVideo
import com.hieuld.cowatch.media.RawVideoRepository

class VideoLibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videos = RawVideoRepository.listVideos()

        setContent {
            CoWatchTheme {
                VideoLibraryScreen(
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
private fun VideoLibraryScreen(
    videos: List<RawVideo>,
    onVideoSelected: (RawVideo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredVideos = remember(query, videos) {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank()) {
            videos
        } else {
            videos.filter { video ->
                video.title.contains(normalizedQuery, ignoreCase = true) ||
                    video.resourceName.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF142231)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VideoSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.fillMaxWidth(0.68f)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 124.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                items(
                    items = filteredVideos,
                    key = { it.resId }
                ) { video ->
                    VideoLibraryItem(
                        video = video,
                        onClick = { onVideoSelected(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(54.dp),
        singleLine = true,
        placeholder = {
            Text(
                text = "Search for Videos",
                color = Color(0xFFB7C6D7),
                fontFamily = FontFamily.Monospace
            )
        },
        leadingIcon = {
            SearchGlyph()
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = Color.White,
            fontFamily = FontFamily.Monospace
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF496277),
            unfocusedBorderColor = Color(0xFF496277),
            cursorColor = Color(0xFF8ABDE8),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun VideoLibraryItem(
    video: RawVideo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Color(0xFF0E4C69))
        )

        Text(
            text = video.title,
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Raw video",
            modifier = Modifier.padding(top = 4.dp),
            color = Color(0xFF75A9CC),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val color = Color(0xFFB7C6D7)
        val strokeWidth = 2.dp.toPx()
        val center = Offset(size.width * 0.43f, size.height * 0.43f)
        val radius = size.minDimension * 0.27f

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
            end = Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
