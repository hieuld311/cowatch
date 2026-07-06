package com.hieuld.cowatch.data.media.provider

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import com.hieuld.cowatch.domain.media.AssetVideo
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG_VIDEO_LIBRARY = "CoWatchVideoLibrary"
private const val THUMBNAIL_FRAME_US = 3_000_000L

enum class AssetVideoThumbnailProfile(
    val width: Int,
    val height: Int
) {
    // 640x360 keeps rail/background thumbnails at 16:9 while limiting decode and GPU upload cost.
    Rail(width = 640, height = 360),
    Background(width = 640, height = 360)
}

private data class CacheKey(
    val assetPath: String,
    val width: Int,
    val height: Int
)

object AssetVideoThumbnailCache {
    private val cacheLock = Any()
    // Serialize thumbnail decode to avoid startup spikes on automotive SoCs with limited media I/O bandwidth.
    private val decodeSemaphore = Semaphore(permits = 1)

    // Cache size is in KB; 32 MB holds the small exhibition thumbnail set without keeping full video frames.
    private val cache = object : LruCache<CacheKey, Bitmap>(32 * 1024) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount / 1024
    }

    fun getCached(
        assetPath: String,
        profile: AssetVideoThumbnailProfile = AssetVideoThumbnailProfile.Rail
    ): Bitmap? {
        val cacheKey = profile.cacheKey(assetPath)
        return synchronized(cacheLock) {
            cache.get(cacheKey)
        }
    }

    suspend fun getOrLoad(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile = AssetVideoThumbnailProfile.Rail
    ): Bitmap? {
        val cacheKey = profile.cacheKey(video.assetPath)
        synchronized(cacheLock) {
            cache.get(cacheKey)
        }?.let { return it }

        // MediaMetadataRetriever + asset file descriptors contend heavily when
        // several thumbnails start together, so keep decode serialized.
        return decodeSemaphore.withPermit {
            synchronized(cacheLock) {
                cache.get(cacheKey)
            }?.let { return@withPermit it }

            val bitmap = decodeThumbnail(context, video, profile) ?: return@withPermit null
            synchronized(cacheLock) {
                cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
            }
        }
    }

    private fun AssetVideoThumbnailProfile.cacheKey(assetPath: String): CacheKey {
        return CacheKey(
            assetPath = assetPath,
            width = width,
            height = height
        )
    }

    private fun decodeThumbnail(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            // Assets are packaged in the APK; openFd gives retriever an offset/length into that packaged file.
            context.assets.openFd(video.assetPath).use { afd ->
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
                "Asset thumbnail load failed title=${video.title}, assetPath=${video.assetPath}",
                throwable
            )
            null
        } finally {
            retriever.release()
        }
    }
}
