package com.ivi.common.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.ivi.common.media.SeekFrameDecoder
import com.ivi.common.media.SeekFrameProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_SEEK_PREVIEW = "CoWatchSeekPreview"
private const val STORYBOARD_ROOT = "seekPreview"

/** Loads pre-generated storyboard images; no video decoder is used while scrubbing. */
public class AssetSeekFrameDecoder private constructor(
    private val assets: AssetManager,
    private val storyboardPath: String,
    private val intervalMs: Long,
    private val frameCount: Int
) : SeekFrameDecoder {

    override fun frameAt(positionMs: Long): Bitmap? {
        val frameIndex = storyboardFrameIndex(positionMs, intervalMs, frameCount)
        val key = SeekFrameKey(storyboardPath, frameIndex)
        SeekFrameCache.get(key)?.let { return it }

        val framePath = "$storyboardPath/frame_${frameIndex.toString().padStart(5, '0')}.jpg"
        return runCatching {
            assets.open(framePath).use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                )
            }
        }.onFailure { throwable ->
            Log.w(TAG_SEEK_PREVIEW, "Storyboard frame load failed path=$framePath", throwable)
        }.getOrNull()?.also { bitmap -> SeekFrameCache.put(key, bitmap) }
    }

    override fun close() = Unit

    companion object {
        fun open(assets: AssetManager, assetPath: String): AssetSeekFrameDecoder? {
            val videoKey = assetPath
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .takeIf { it.isNotBlank() }
                ?: return null
            val storyboardPath = "$STORYBOARD_ROOT/$videoKey"
            val indexPath = "$storyboardPath/index.txt"

            return runCatching {
                val values = assets.open(indexPath).bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        val separator = line.indexOf('=')
                        if (separator <= 0) null
                        else line.substring(0, separator) to line.substring(separator + 1)
                    }.toMap()
                }
                val intervalMs = values.getValue("intervalMs").toLong()
                val frameCount = values.getValue("frameCount").toInt()
                require(intervalMs > 0L && frameCount > 0)
                AssetSeekFrameDecoder(assets, storyboardPath, intervalMs, frameCount)
            }.onFailure { throwable ->
                Log.e(TAG_SEEK_PREVIEW, "Storyboard index unavailable for asset=$assetPath", throwable)
            }.getOrNull()
        }
    }
}

@Singleton
class AssetSeekFrameProvider @Inject constructor(
    @ApplicationContext context: Context
) : SeekFrameProvider {
    private val assets = context.applicationContext.assets

    override fun open(assetPath: String): SeekFrameDecoder? {
        return AssetSeekFrameDecoder.open(assets, assetPath)
    }
}

public fun storyboardFrameIndex(positionMs: Long, intervalMs: Long, frameCount: Int): Int {
    if (intervalMs <= 0L || frameCount <= 1) return 0
    val roundedIndex = (positionMs.coerceAtLeast(0L) + intervalMs / 2L) / intervalMs
    return roundedIndex.coerceIn(0L, frameCount.toLong() - 1L).toInt()
}

private data class SeekFrameKey(
    val storyboardPath: String,
    val frameIndex: Int
)

private object SeekFrameCache {
    private val cache = object : LruCache<SeekFrameKey, Bitmap>(8 * 1024) {
        override fun sizeOf(key: SeekFrameKey, value: Bitmap): Int = value.byteCount / 1024
    }

    @Synchronized
    fun get(key: SeekFrameKey): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: SeekFrameKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
