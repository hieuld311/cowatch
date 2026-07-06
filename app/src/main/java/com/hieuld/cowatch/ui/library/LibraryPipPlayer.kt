package com.hieuld.cowatch.ui.library

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.hieuld.cowatch.session.AppPlaybackSession
import com.hieuld.cowatch.render.FrameFanoutRenderEngine
import com.hieuld.cowatch.render.VideoRenderEngine

@Composable
internal fun LibraryPipPlayer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(context) { AppPlaybackSession.getPlayer(context) }
    // PiP reuses the shared render engine so active shared displays keep receiving frames.
    val renderEngine = remember(player) { AppPlaybackSession.getRenderEngine() }

    DisposableEffect(player, renderEngine) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                applyVideoSize(renderEngine, videoSize)
            }
        }

        player.addListener(listener)
        AppPlaybackSession.attachRenderEngine(renderEngine)
        applyVideoSize(renderEngine, player.videoSize)

        onDispose {
            player.removeListener(listener)
            renderEngine.removeOutputAsync(VideoRenderEngine.LIBRARY_PIP_OUTPUT_ID)
        }
    }

    Box(modifier = modifier.clickable(onClick = onClick)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                SurfaceView(viewContext).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            renderEngine.addOutputAsync(
                                // Use a dedicated PiP output id so fullscreen host surface cleanup cannot remove it.
                                outputId = VideoRenderEngine.LIBRARY_PIP_OUTPUT_ID,
                                surface = holder.surface,
                                width = width,
                                height = height
                            ) { }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderEngine.removeOutputAsync(VideoRenderEngine.LIBRARY_PIP_OUTPUT_ID)
                        }
                    })
                }
            }
        )
    }
}

private fun applyVideoSize(
    renderEngine: FrameFanoutRenderEngine,
    videoSize: VideoSize
) {
    if (videoSize.width <= 0 || videoSize.height <= 0) return

    renderEngine.setVideoSize(
        width = videoSize.width,
        height = videoSize.height,
        pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
    )
}
