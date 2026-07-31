package com.ivi.common.data

import android.content.Context
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val callback = object : StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: StorageVolume) {
                refreshCatalog()
            }
        }
        val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshCatalog()
        }
        storageManager.registerStorageVolumeCallback(context.mainExecutor, callback)
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            mediaObserver
        )
        awaitClose {
            storageManager.unregisterStorageVolumeCallback(callback)
            context.contentResolver.unregisterContentObserver(mediaObserver)
        }
    }

    private fun loadCatalog(): List<AssetVideo> {
        return (scanAssetVideos() + scanExternalVideos())
            .distinctBy { it.assetPath }
            .sortedBy { it.title.lowercase() }
    }

    // Exhibition videos live under assets/fileVideoSample; assetPath keeps the folder for Media3 asset:/// playback.
    private fun scanAssetVideos(): List<AssetVideo> {
        return context.assets
            .listAssetPaths(VIDEO_ASSET_ROOT)
            .mapNotNull { assetPath -> assetPath.toAssetVideo(isPackagedAsset = true) }
    }

    /** Reads indexed videos from primary storage and every mounted USB/SD media volume. */
    private fun scanExternalVideos(): List<AssetVideo> {
        return MediaStore.getExternalVolumeNames(context)
            .asSequence()
            .flatMap { volumeName -> queryVolumeVideos(volumeName).asSequence() }
            .toList()
    }

    private fun queryVolumeVideos(volumeName: String): List<AssetVideo> {
        val collection = MediaStore.Video.Media.getContentUri(volumeName)
        return runCatching {
            context.contentResolver.query(
                collection,
                VIDEO_PROJECTION,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val fileName = cursor.getString(nameIndex) ?: continue
                        if (!MediaFileTypes.isSupportedVideoFileName(fileName)) continue
                        add(
                            AssetVideo(
                                assetPath = ContentUris.withAppendedId(
                                    collection,
                                    cursor.getLong(idIndex)
                                ).toString(),
                                fileName = fileName,
                                title = fileName.substringBeforeLast('.').toVideoTitle(),
                                isPackagedAsset = false
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
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
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
    }
}
