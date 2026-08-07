package com.ivi.cid.ui

import android.graphics.Color
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.cid.app.CIDVideoNavigation
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.playback.PlayerSurfaceController
import com.ivi.cid.ui.library.VideoLibraryScreen
import com.ivi.cid.viewmodel.LibraryViewModel
import com.ivi.common.ui.CoWatchTheme
import com.ivi.common.ui.USB_VIDEO_PERMISSION_REQUEST_CODE
import com.ivi.common.ui.requestUsbVideoPermissionIfNeeded
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoLibraryActivity : ComponentActivity() {
    @Inject
    lateinit var thumbnailLoader: ThumbnailLoader

    @Inject
    lateinit var playerSurfaceController: PlayerSurfaceController

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLibrarySystemBars()
        requestUsbVideoPermissionIfNeeded()

        setContent {
            CoWatchTheme {
                // Activity stays as navigation shell; media catalog and PiP state live in the ViewModel.
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                VideoLibraryScreen(
                    videos = uiState.videos,
                    thumbnailLoader = thumbnailLoader,
                    playerSurfaceController = playerSurfaceController,
                    pipState = uiState.pipState,
                    activeSessionAssetPath = uiState.playbackState.activeSource?.assetPath,
                    isSessionPlaying = uiState.playbackState.isPlaying,
                    onPipSelected = {
                        uiState.pipState?.source?.let { source ->
                            startActivity(CIDVideoNavigation.createIntent(this, source))
                        }
                    },
                    onPipPlaybackToggle = viewModel::togglePlayback,
                    onFocusedPlaybackToggle = viewModel::togglePlayback,
                    onPipClose = viewModel::closePipPlayback,
                    onFocusedVideoPlay = { video ->
                        startActivity(CIDVideoNavigation.createIntent(this, video))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        configureLibrarySystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureLibrarySystemBars()
    }

    @Suppress("DEPRECATION")
    private fun configureLibrarySystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = false
        }
    }

    @Deprecated("Uses the framework permission callback for the USB read permission.")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == USB_VIDEO_PERMISSION_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            recreate()
        }
    }

}
