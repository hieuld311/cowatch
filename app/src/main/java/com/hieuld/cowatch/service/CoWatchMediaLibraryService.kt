package com.hieuld.cowatch.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hieuld.cowatch.R
import com.hieuld.cowatch.ui.FrontPlayerActivity

class CoWatchMediaLibraryService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var librarySession: MediaLibrarySession

    private val rootItem: MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Co-watch Demo")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private val demoVideoItem: MediaItem by lazy {
        MediaItem.Builder()
            .setMediaId(DEMO_VIDEO_ID)
            .setUri(getDemoVideoUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Demo Video")
                    .setArtist("CoWatch Demo")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, FrontPlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        librarySession = MediaLibrarySession.Builder(
            this,
            player,
            LibraryCallback()
        )
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession {
        return librarySession
    }

    override fun onDestroy() {
        librarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun getDemoVideoUri(): String {
        return "android.resource://${packageName}/${R.raw.demo_video}"
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = if (parentId == ROOT_ID) {
                ImmutableList.of(demoVideoItem)
            } else {
                ImmutableList.of()
            }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(children, params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT_ID -> rootItem
                DEMO_VIDEO_ID -> demoVideoItem
                else -> null
            }

            return if (item != null) {
                Futures.immediateFuture(
                    LibraryResult.ofItem(item, null)
                )
            } else {
                Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            }
        }
    }

    companion object {
        const val ROOT_ID = "root"
        const val DEMO_VIDEO_ID = "demo_video"
    }
}