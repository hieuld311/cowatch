package com.hieuld.cowatch.data.media.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import com.hieuld.cowatch.domain.media.AssetVideo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG_VIDEO_LIBRARY = "CoWatchVideoLibrary"
private const val THUMBNAIL_FRAME_US = 3_000_000L
private const val BACKGROUND_CACHE_DIR = "media_background_cache"
private const val BACKGROUND_CACHE_QUALITY = 88

enum class AssetVideoThumbnailProfile(
    val width: Int,
    val height: Int,
    val persistentDiskCache: Boolean
) {
    // 640x360 keeps rail thumbnails cheap during carousel movement.
    Rail(width = 640, height = 360, persistentDiskCache = false),
    // 1280x720 backgrounds are generated once and read from internal storage after that.
    Background(width = 1280, height = 720, persistentDiskCache = true)
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

        if (profile.persistentDiskCache) {
            loadDiskCachedBitmap(context, video, profile)?.let { bitmap ->
                synchronized(cacheLock) {
                    cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
                }.let { return it }
            }
        }

        // MediaMetadataRetriever + asset file descriptors contend heavily when
        // several frames start together, so keep decode serialized.
        return decodeSemaphore.withPermit {
            synchronized(cacheLock) {
                cache.get(cacheKey)
            }?.let { return@withPermit it }

            if (profile.persistentDiskCache) {
                loadDiskCachedBitmap(context, video, profile)?.let { bitmap ->
                    return@withPermit synchronized(cacheLock) {
                        cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
                    }
                }
            }

            val bitmap = decodeThumbnail(context, video, profile) ?: return@withPermit null
            if (profile.persistentDiskCache) {
                writeDiskCachedBitmap(context, video, profile, bitmap)
            }
            synchronized(cacheLock) {
                cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
            }
        }
    }

    suspend fun warmPersistentCache(
        context: Context,
        videos: List<AssetVideo>,
        profile: AssetVideoThumbnailProfile = AssetVideoThumbnailProfile.Background
    ) {
        if (!profile.persistentDiskCache) return

        videos.forEach { video ->
            ensureDiskCached(context, video, profile)
        }
    }

    private suspend fun ensureDiskCached(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile
    ) {
        if (diskCacheFile(context, video, profile).isFile) return

        decodeSemaphore.withPermit {
            if (diskCacheFile(context, video, profile).isFile) return@withPermit

            val bitmap = decodeThumbnail(context, video, profile) ?: return@withPermit
            writeDiskCachedBitmap(context, video, profile, bitmap)
            bitmap.recycle()
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

    private fun loadDiskCachedBitmap(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile
    ): Bitmap? {
        val file = diskCacheFile(context, video, profile)
        if (!file.isFile) return null

        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.onFailure { throwable ->
            Log.w(
                TAG_VIDEO_LIBRARY,
                "Background cache read failed assetPath=${video.assetPath}",
                throwable
            )
            file.delete()
        }.getOrNull()
    }

    private fun writeDiskCachedBitmap(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile,
        bitmap: Bitmap
    ) {
        val file = diskCacheFile(context, video, profile)
        val tempFile = File(file.parentFile, "${file.name}.tmp")

        runCatching {
            file.parentFile?.mkdirs()
            tempFile.outputStream().use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    BACKGROUND_CACHE_QUALITY,
                    output
                )
            }
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        }.onFailure { throwable ->
            Log.w(
                TAG_VIDEO_LIBRARY,
                "Background cache write failed assetPath=${video.assetPath}",
                throwable
            )
            tempFile.delete()
        }
    }

    private fun diskCacheFile(
        context: Context,
        video: AssetVideo,
        profile: AssetVideoThumbnailProfile
    ): File {
        val cacheName = "${video.assetPath}_${profile.width}x${profile.height}".sha256()
        return File(
            File(context.filesDir, BACKGROUND_CACHE_DIR),
            "$cacheName.jpg"
        )
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
