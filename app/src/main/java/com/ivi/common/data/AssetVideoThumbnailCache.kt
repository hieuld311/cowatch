package com.ivi.common.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.ivi.common.domain.AssetVideo
import com.ivi.common.media.ThumbnailLoader
import com.ivi.common.media.ThumbnailProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG_VIDEO_LIBRARY = "CoWatchVideoLibrary"
private const val THUMBNAIL_FRAME_US = 3_000_000L
private const val BACKGROUND_CACHE_DIR = "media_background_cache"
private const val BACKGROUND_CACHE_QUALITY = 88

private data class CacheKey(
    val assetPath: String,
    val width: Int,
    val height: Int
)

@Singleton
class AssetVideoThumbnailCache @Inject constructor(
    @ApplicationContext context: Context
) : ThumbnailLoader {
    private val appContext = context.applicationContext
    private val cacheLock = Any()
    // Serialize thumbnail decode.
    private val decodeSemaphore = Semaphore(permits = 1)

    // Cache size is in KB; 32 MB holds the small exhibition thumbnail set without keeping full video frames.
    private val cache = object : LruCache<CacheKey, Bitmap>(32 * 1024) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun getCached(
        assetPath: String,
        profile: ThumbnailProfile
    ): Bitmap? {
        val cacheKey = profile.cacheKey(assetPath)
        return synchronized(cacheLock) {
            cache.get(cacheKey)
        }
    }

    override suspend fun getOrLoad(
        video: AssetVideo,
        profile: ThumbnailProfile
    ): Bitmap? {
        val cacheKey = profile.cacheKey(video.assetPath)
        synchronized(cacheLock) {
            cache.get(cacheKey)
        }?.let { return it }

        if (video.usesPersistentCache(profile)) {
            loadDiskCachedBitmap(video, profile)?.let { bitmap ->
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

            if (video.usesPersistentCache(profile)) {
                loadDiskCachedBitmap(video, profile)?.let { bitmap ->
                    return@withPermit synchronized(cacheLock) {
                        cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
                    }
                }
            }

            val bitmap = decodeThumbnail(video, profile) ?: return@withPermit null
            if (video.usesPersistentCache(profile)) {
                writeDiskCachedBitmap(video, profile, bitmap)
            }
            synchronized(cacheLock) {
                cache.get(cacheKey) ?: bitmap.also { cache.put(cacheKey, it) }
            }
        }
    }

    override suspend fun warmPersistentCache(
        videos: List<AssetVideo>,
        profile: ThumbnailProfile
    ) {
        videos.forEach { video ->
            if (!video.usesPersistentCache(profile)) return@forEach
            if (diskCacheFile(video, profile).isFile) return@forEach

            decodeSemaphore.withPermit {
                if (diskCacheFile(video, profile).isFile) return@withPermit
                val bitmap = decodeThumbnail(video, profile) ?: return@withPermit
                writeDiskCachedBitmap(video, profile, bitmap)
                bitmap.recycle()
            }
        }
    }

    private fun ThumbnailProfile.cacheKey(assetPath: String): CacheKey {
        return CacheKey(
            assetPath = assetPath,
            width = width,
            height = height
        )
    }

    private fun AssetVideo.usesPersistentCache(profile: ThumbnailProfile): Boolean {
        return isPackagedAsset && profile.persistentDiskCache
    }

    private fun decodeThumbnail(
        video: AssetVideo,
        profile: ThumbnailProfile
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (video.isPackagedAsset) {
                // Assets are packaged in the APK; openFd gives retriever an offset/length into that packaged file.
                appContext.assets.openFd(video.assetPath).use { afd ->
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    retriever.decodeScaledFrame(profile)
                }
            } else {
                retriever.setDataSource(appContext, Uri.parse(video.assetPath))
                retriever.decodeScaledFrame(profile)
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG_VIDEO_LIBRARY,
                "Thumbnail load failed title=${video.title}, source=${video.assetPath}",
                throwable
            )
            null
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.decodeScaledFrame(profile: ThumbnailProfile): Bitmap? {
        return getScaledFrameAtTime(
            THUMBNAIL_FRAME_US,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            profile.width,
            profile.height
        ) ?: getFrameAtTime(THUMBNAIL_FRAME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun loadDiskCachedBitmap(
        video: AssetVideo,
        profile: ThumbnailProfile
    ): Bitmap? {
        val file = diskCacheFile(video, profile)
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
        video: AssetVideo,
        profile: ThumbnailProfile,
        bitmap: Bitmap
    ) {
        val file = diskCacheFile(video, profile)
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
        video: AssetVideo,
        profile: ThumbnailProfile
    ): File {
        val cacheName = "${video.assetPath}_${profile.width}x${profile.height}".sha256()
        return File(
            File(appContext.cacheDir, BACKGROUND_CACHE_DIR),
            "$cacheName.jpg"
        )
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
