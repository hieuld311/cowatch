package com.ivi.common.data

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class AssetVideoRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VideoCatalogRepository {

    override suspend fun listVideos(): List<AssetVideo> = withContext(Dispatchers.IO) {
        loadCatalog()
    }

    override fun observeVideos(): Flow<List<AssetVideo>> = callbackFlow {
        fun refreshCatalog() {
            launch(Dispatchers.IO) { trySend(loadCatalog()) }
        }

        refreshCatalog()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            awaitClose()
            return@callbackFlow
        }

        val storageManager = context.getSystemService(StorageManager::class.java)
        val callback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                if (volume.isRemovable && !volume.isPrimary) refreshCatalog()
            }
        }
        storageManager.registerStorageVolumeCallback(context.mainExecutor, callback)
        awaitClose { storageManager.unregisterStorageVolumeCallback(callback) }
    }

    private fun loadCatalog(): List<AssetVideo> {
        return (scanAssetVideos() + scanUsbVideos())
            .distinctBy { it.assetPath }
            .sortedBy { it.title.lowercase() }
    }

    // Exhibition videos live under assets/fileVideoSample; assetPath keeps the folder for Media3 asset:/// playback.
    private fun scanAssetVideos(): List<AssetVideo> {
        return context.assets
            .listAssetPaths(VIDEO_ASSET_ROOT)
            .mapNotNull { assetPath -> assetPath.toAssetVideo(isPackagedAsset = true) }
    }

    /** USB is read in place; no file is copied into app storage or the APK. */
    private fun scanUsbVideos(): List<AssetVideo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val storageManager = context.getSystemService(StorageManager::class.java)
        return storageManager.storageVolumes
            .asSequence()
            .filter { it.isRemovable && !it.isPrimary }
            .mapNotNull { it.directory }
            .flatMap { root ->
                runCatching {
                    root.walkTopDown()
                        .filter { file -> file.isFile && file.canRead() && MediaFileTypes.isSupportedVideoFileName(file.name) }
                        .map { file -> file.toUsbVideo() }
                        .asSequence()
                }.getOrElse { emptySequence() }
            }
            .toList()
    }

    private fun File.toUsbVideo(): AssetVideo {
        return AssetVideo(
            assetPath = toURI().toString(),
            fileName = name,
            title = name.substringBeforeLast('.').toVideoTitle(),
            isPackagedAsset = false
        )
    }

    // Recursively scan only the video catalog folder so unrelated assets never appear in the library.
    private fun android.content.res.AssetManager.listAssetPaths(rootDirectory: String): List<String> {
        val result = mutableListOf<String>()
        collectAssetPaths(directory = rootDirectory, result = result)
        return result
    }

    private fun android.content.res.AssetManager.collectAssetPaths(directory: String, result: MutableList<String>) {
        val children = runCatching { list(directory).orEmpty() }.getOrDefault(emptyArray())
        children.forEach { child ->
            val path = if (directory.isBlank()) child else "$directory/$child"
            val nestedChildren = runCatching { list(path).orEmpty() }.getOrDefault(emptyArray())
            if (nestedChildren.isEmpty()) result += path else collectAssetPaths(path, result)
        }
    }

    private fun String.toAssetVideo(isPackagedAsset: Boolean): AssetVideo? {
        val fileName = substringAfterLast('/')
        if (!MediaFileTypes.isSupportedVideoFileName(fileName)) return null
        return AssetVideo(
            assetPath = this,
            fileName = fileName,
            title = fileName.substringBeforeLast('.').toVideoTitle(),
            isPackagedAsset = isPackagedAsset
        )
    }

    private fun String.toVideoTitle(): String {
        return split('_').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
    }

    private companion object {
        const val VIDEO_ASSET_ROOT = "fileVideoSample"
    }
}
