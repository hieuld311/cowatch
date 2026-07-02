package com.hieuld.cowatch.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache

private const val TAG_VIDEO_LIBRARY = "CoWatchVideoLibrary"
private const val THUMBNAIL_FRAME_US = 3_000_000L

enum class RawVideoThumbnailProfile(
    val width: Int,
    val height: Int
) {
    Rail(width = 640, height = 360),
    Background(width = 1280, height = 720)
}

private data class CacheKey(
    val resId: Int,
    val profile: RawVideoThumbnailProfile
)

object RawVideoThumbnailCache {
    private val cache = object : LruCache<CacheKey, Bitmap>(32 * 1024) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount / 1024
    }

    @Synchronized
    fun getCached(
        resId: Int,
        profile: RawVideoThumbnailProfile = RawVideoThumbnailProfile.Rail
    ): Bitmap? {
        return cache.get(CacheKey(resId = resId, profile = profile))
    }

    @Synchronized
    fun getOrLoad(
        context: Context,
        video: RawVideo,
        profile: RawVideoThumbnailProfile = RawVideoThumbnailProfile.Rail
    ): Bitmap? {
        val cacheKey = CacheKey(resId = video.resId, profile = profile)
        cache.get(cacheKey)?.let { return it }

        val bitmap = decodeThumbnail(context, video, profile) ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun decodeThumbnail(
        context: Context,
        video: RawVideo,
        profile: RawVideoThumbnailProfile
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
                    profile.width,
                    profile.height
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
