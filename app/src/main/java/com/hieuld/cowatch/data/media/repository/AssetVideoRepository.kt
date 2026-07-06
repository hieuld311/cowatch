package com.hieuld.cowatch.data.media.repository

import android.content.Context
import com.hieuld.cowatch.domain.media.AssetVideo
import java.util.Locale

object AssetVideoRepository {

    // Exhibition media is bundled in APK assets; assetPath is the stable id used by UI, PiP, and player routing.
    fun listVideos(context: Context): List<AssetVideo> {
        return context.assets
            .listVideoAssetPaths()
            .map { assetPath ->
                val fileName = assetPath.substringAfterLast('/')
                AssetVideo(
                    assetPath = assetPath,
                    fileName = fileName,
                    title = fileName.substringBeforeLast('.').toVideoTitle()
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    // Resolve PiP/current playback back to a catalog item without relying on generated resource IDs.
    fun findByAssetPath(context: Context, assetPath: String): AssetVideo? {
        return listVideos(context).firstOrNull { it.assetPath == assetPath }
    }

    // Recursively scan assets so production media can be grouped in folders without changing app code.
    private fun android.content.res.AssetManager.listVideoAssetPaths(): List<String> {
        val result = mutableListOf<String>()
        collectVideoAssetPaths(
            directory = "",
            result = result
        )
        return result
    }

    private fun android.content.res.AssetManager.collectVideoAssetPaths(
        directory: String,
        result: MutableList<String>
    ) {
        val children = runCatching { list(directory).orEmpty() }.getOrDefault(emptyArray())
        children.forEach { child ->
            val path = if (directory.isBlank()) child else "$directory/$child"
            val nestedChildren = runCatching { list(path).orEmpty() }.getOrDefault(emptyArray())
            if (nestedChildren.isEmpty()) {
                if (path.isSupportedVideoAsset()) {
                    result += path
                }
            } else {
                collectVideoAssetPaths(path, result)
            }
        }
    }

    private fun String.isSupportedVideoAsset(): Boolean {
        val lowerName = lowercase(Locale.ROOT)
        return lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".m4v") ||
                lowerName.endsWith(".webm") ||
                lowerName.endsWith(".mkv")
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
}
