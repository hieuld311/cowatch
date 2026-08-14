package com.ivi.common.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.core.content.ContextCompat
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
        val storageManager = context.getSystemService(StorageManager::class.java)
        val volumeCallback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                Log.i(TAG, "Storage changed: state=${volume.state}; refreshing catalog")
                refreshCatalog()
            }
        }
        storageManager.registerStorageVolumeCallback(context.mainExecutor, volumeCallback)

        // StorageVolumeCallback.onStateChanged does not fire for USB mount/eject on this hardware
        // (confirmed on-device: the initial scan runs, but nothing follows on attach/detach), so the
        // legacy media-mount broadcasts are registered as a second, independent trigger for the same
        // refresh rather than the only signal this depends on.
        val mediaMountReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                Log.i(TAG, "Media broadcast: action=${intent.action}, data=${intent.data}; refreshing catalog")
                refreshCatalog()
            }
        }
        val mediaMountFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(
            context,
            mediaMountReceiver,
            mediaMountFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        awaitClose {
            storageManager.unregisterStorageVolumeCallback(volumeCallback)
            context.unregisterReceiver(mediaMountReceiver)
        }
    }

    private fun loadCatalog(): List<AssetVideo> {
        val packagedVideos = scanAssetVideos()
        val externalVideos = scanExternalVideos()
        return (packagedVideos + externalVideos)
            .distinctBy { it.assetPath }
            .sortedBy { it.title.lowercase() }
            .also { catalog ->
                Log.i(
                    TAG,
                    "Catalog loaded: packaged=${packagedVideos.size}, external=${externalVideos.size}, " +
                        "total=${catalog.size}"
                )
            }
    }

    // Exhibition videos live under assets/fileVideoSample; assetPath keeps the folder for Media3 asset:/// playback.
    private fun scanAssetVideos(): List<AssetVideo> {
        return context.assets
            .listAssetPaths(VIDEO_ASSET_ROOT)
            .mapNotNull { assetPath -> assetPath.toAssetVideo(isPackagedAsset = true) }
    }

    /**
     * Reads every mounted USB/SD volume directly off the filesystem rather than through MediaStore.
     * On this hardware MediaStore never indexes a freshly mounted removable volume — even an
     * explicit MediaScannerConnection.scanFile() request left MediaStore's row count at zero for a
     * file that is genuinely present — so any MediaStore-backed query silently returns nothing. A
     * direct File walk has no such dependency on the platform's indexing state.
     */
    private fun scanExternalVideos(): List<AssetVideo> {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "Scan: permissionGranted=$permissionGranted")

        val storageManager = context.getSystemService(StorageManager::class.java)
        val removableVolumes = storageManager.storageVolumes.filter { it.isRemovable && !it.isPrimary }
        return removableVolumes.flatMap { volume ->
            val directory = volume.directory
            if (directory == null) {
                Log.w(TAG, "Skipping volume with no directory: name=${volume.mediaStoreVolumeName}")
                return@flatMap emptyList()
            }
            scanDirectoryForVideos(directory, volumeName = volume.mediaStoreVolumeName)
        }
    }

    private fun scanDirectoryForVideos(root: File, volumeName: String?): List<AssetVideo> {
        return runCatching {
            root.walk()
                .filter { file -> file.isFile && MediaFileTypes.isSupportedVideoFileName(file.name) }
                .onEach { file ->
                    Log.i(TAG, "Accepted video: volume=$volumeName, path=${file.absolutePath}, bytes=${file.length()}")
                }
                .map { file -> file.toAssetVideo() }
                .toList()
        }.onSuccess { videos ->
            Log.i(TAG, "Volume scan: volume=$volumeName, path=${root.path}, videos=${videos.size}")
        }.onFailure { error ->
            Log.e(TAG, "Volume scan failed: volume=$volumeName, path=${root.path}", error)
        }.getOrDefault(emptyList())
    }

    private fun File.toAssetVideo(): AssetVideo {
        return AssetVideo(
            assetPath = Uri.fromFile(this).toString(),
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
        const val TAG = "CoWatchVideoCatalog"
        const val VIDEO_ASSET_ROOT = "fileVideoSample"
    }
}
