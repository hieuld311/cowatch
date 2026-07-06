package com.hieuld.cowatch.data.media.repository

import android.content.Context
import com.hieuld.cowatch.domain.media.AssetVideo
import com.hieuld.cowatch.util.MediaFileTypes

object AssetVideoRepository {

    // Exhibition media is bundled in APK assets; assetPath is the stable id used by UI, PiP, and player routing.
    fun listVideos(context: Context): List<AssetVideo> {
        return scanAssetMediaItems(context)
            .filter { item -> item.type == AssetMediaType.Video }
            .map { item -> item.toAssetVideo() }
            .sortedBy { it.title.lowercase() }
    }

    // Resolve PiP/current playback back to a catalog item without relying on generated resource IDs.
    fun findByAssetPath(context: Context, assetPath: String): AssetVideo? {
        return listVideos(context).firstOrNull { it.assetPath == assetPath }
    }

    private fun scanAssetMediaItems(context: Context): List<ScannedAssetMediaItem> {
        return context.assets
            .listAssetPaths()
            .mapNotNull { assetPath -> assetPath.toScannedMediaItem() }
    }

    // Recursively scan assets so production media can be grouped in folders without changing app code.
    private fun android.content.res.AssetManager.listAssetPaths(): List<String> {
        val result = mutableListOf<String>()
        collectAssetPaths(
            directory = "",
            result = result
        )
        return result
    }

    private fun android.content.res.AssetManager.collectAssetPaths(
        directory: String,
        result: MutableList<String>
    ) {
        val children = runCatching { list(directory).orEmpty() }.getOrDefault(emptyArray())
        children.forEach { child ->
            val path = if (directory.isBlank()) child else "$directory/$child"
            val nestedChildren = runCatching { list(path).orEmpty() }.getOrDefault(emptyArray())
            if (nestedChildren.isEmpty()) {
                result += path
            } else {
                collectAssetPaths(path, result)
            }
        }
    }

    private fun String.toScannedMediaItem(): ScannedAssetMediaItem? {
        val fileName = substringAfterLast('/')
        val type = when {
            MediaFileTypes.isSupportedVideoFileName(fileName) -> AssetMediaType.Video
            else -> return null
        }

        return ScannedAssetMediaItem(
            assetPath = this,
            fileName = fileName,
            title = fileName.substringBeforeLast('.').toVideoTitle(),
            type = type
        )
    }

    private fun String.toVideoTitle(): String {
        return split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }

    private fun ScannedAssetMediaItem.toAssetVideo(): AssetVideo {
        return AssetVideo(
            assetPath = assetPath,
            fileName = fileName,
            title = title
        )
    }

    private data class ScannedAssetMediaItem(
        val assetPath: String,
        val fileName: String,
        val title: String,
        val type: AssetMediaType
    )

    private enum class AssetMediaType {
        Video
    }
}
