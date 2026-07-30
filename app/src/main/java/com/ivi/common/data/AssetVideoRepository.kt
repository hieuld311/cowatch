package com.ivi.common.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository

@Singleton
class AssetVideoRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VideoCatalogRepository {

    // Exhibition videos live under assets/fileVideoSample; assetPath keeps the folder for Media3 asset:/// playback.
    override suspend fun listVideos(): List<AssetVideo> {
        return scanAssetMediaItems(context)
            .filter { item -> item.type == AssetMediaType.Video }
            .map { item -> item.toAssetVideo() }
            .sortedBy { it.title.lowercase() }
    }

    // Resolve PiP/current playback back to a catalog item without relying on generated resource IDs.
    private fun scanAssetMediaItems(context: Context): List<ScannedAssetMediaItem> {
        return context.assets
            .listAssetPaths(VIDEO_ASSET_ROOT)
            .mapNotNull { assetPath -> assetPath.toScannedMediaItem() }
    }

    // Recursively scan only the video catalog folder so unrelated assets never appear in the library.
    private fun android.content.res.AssetManager.listAssetPaths(rootDirectory: String): List<String> {
        val result = mutableListOf<String>()
        collectAssetPaths(
            directory = rootDirectory,
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

    private companion object {
        const val VIDEO_ASSET_ROOT = "fileVideoSample"
    }
}
