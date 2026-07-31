package com.ivi.common.data

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
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
class

AssetVideoRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VideoCatalogRepository {

    override suspend fun listVideos(): List<AssetVideo> = withContext(Dispatchers.IO) {
        loadCatalog()
    }

    override fun observeVideos(): Flow<List<AssetVideo>> = callbackFlow {
        fun refreshCatalog(reason: String) {
            launch(Dispatchers.IO) {
                val catalog = loadCatalog()
                Log.i(
                    TAG,
                    "Catalog refresh reason=$reason total=${catalog.size} usb=${catalog.count { !it.isPackagedAsset }}"
                )
                trySend(catalog)
            }
        }

        refreshCatalog("initial")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "USB scan disabled: StorageVolume.directory requires API 30+, sdk=${Build.VERSION.SDK_INT}")
            awaitClose()
            return@callbackFlow
        }

        val storageManager = context.getSystemService(StorageManager::class.java)
        val callback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                if (volume.isRemovable && !volume.isPrimary) {
                    Log.i(TAG, "Volume state changed state=${volume.state} directory=${volume.directory}")
                    refreshCatalog("volume-state=${volume.state}")
                }
            }
        }
        storageManager.registerStorageVolumeCallback(context.mainExecutor, callback)
        awaitClose {
            Log.d(TAG, "Stop observing removable storage")
            storageManager.unregisterStorageVolumeCallback(callback)
        }
    }

    private fun loadCatalog(): List<AssetVideo> {
        val assetVideos = scanAssetVideos()
        val usbVideos = scanUsbVideos()
        Log.d(TAG, "Catalog scan complete assets=${assetVideos.size} usb=${usbVideos.size}")
        return (assetVideos + usbVideos)
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
        val removableVolumes = storageManager.storageVolumes
            .filter { it.isRemovable && !it.isPrimary }
        Log.i(
            TAG,
            "Removable volumes=${removableVolumes.size} ${removableVolumes.joinToString { "state=${it.state}, directory=${it.directory}" }}"
        )
        return removableVolumes.flatMap { volume ->
            val root = volume.directory
            if (root == null) {
                Log.w(TAG, "Skip removable volume without a mounted directory state=${volume.state}")
                emptyList()
            } else {
                runCatching {
                    root.walkTopDown()
                        .filter { file -> file.isFile && file.canRead() && MediaFileTypes.isSupportedVideoFileName(file.name) }
                        .map { file -> file.toUsbVideo() }
                        .toList()
                }.onSuccess { videos ->
                    Log.i(TAG, "USB root=${root.path} videos=${videos.size}")
                }.onFailure { error ->
                    Log.e(TAG, "USB scan failed root=${root.path}", error)
                }.getOrDefault(emptyList())
            }
        }
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
        const val TAG = "CoWatchUsbCatalog"
        const val VIDEO_ASSET_ROOT = "fileVideoSample"
    }
}
