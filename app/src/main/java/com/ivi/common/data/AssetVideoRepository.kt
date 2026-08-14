package com.ivi.common.data

import android.content.Context
import android.content.ContentUris
import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.util.Log
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
                Log.i(TAG, "Storage changed; refreshing catalog")
                refreshCatalog()
            }
        }
        val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.i(TAG, "MediaStore changed: selfChange=$selfChange; refreshing catalog")
                refreshCatalog()
            }
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

    /** Reads indexed videos from primary storage and every mounted USB/SD media volume. */
    private fun scanExternalVideos(): List<AssetVideo> {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val volumeNames = runCatching { MediaStore.getExternalVolumeNames(context) }
            .onFailure { error -> Log.e(TAG, "Cannot enumerate MediaStore volumes", error) }
            .getOrDefault(emptySet())

        Log.i(
            TAG,
            "Scan: user=${Process.myUserHandle()}, permissionGranted=$permissionGranted, volumes=$volumeNames"
        )

        return volumeNames
            .asSequence()
            .flatMap { volumeName -> queryVolumeVideos(volumeName).asSequence() }
            .toList()
    }

    private fun queryVolumeVideos(volumeName: String): List<AssetVideo> {
        val collection = MediaStore.Video.Media.getContentUri(volumeName)
        repeat(VOLUME_QUERY_ATTEMPTS) { attempt ->
            val result = runCatching {
                context.contentResolver.query(
                    collection,
                    VIDEO_PROJECTION,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    Log.i(TAG, "MediaStore query: volume=$volumeName, uri=$collection, rows=${cursor.count}")
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    buildList {
                        while (cursor.moveToNext()) {
                            val fileName = cursor.getString(nameIndex) ?: continue
                            if (!MediaFileTypes.isSupportedVideoFileName(fileName)) continue
                            val contentUri = ContentUris.withAppendedId(
                                collection,
                                cursor.getLong(idIndex)
                            )
                            Log.i(TAG, "Accepted video: name=$fileName, uri=$contentUri")
                            add(
                                AssetVideo(
                                    assetPath = contentUri.toString(),
                                    fileName = fileName,
                                    title = fileName.substringBeforeLast('.').toVideoTitle(),
                                    isPackagedAsset = false
                                )
                            )
                        }
                    }
                } ?: emptyList<AssetVideo>().also {
                    Log.w(TAG, "MediaStore query returned null cursor: volume=$volumeName, uri=$collection")
                }
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "MediaStore query failed (attempt ${attempt + 1}/$VOLUME_QUERY_ATTEMPTS): " +
                        "volume=$volumeName, uri=$collection",
                    error
                )
            }
            if (result.isSuccess) return result.getOrThrow()

            // MediaStore.getExternalVolumeNames() can report a volume a moment before MediaProvider
            // finishes registering it, so the immediate query throws IllegalArgumentException("Volume
            // not found"). That registration race is the only case worth a short retry; any other
            // failure is returned as empty immediately.
            val isVolumeRegistrationRace = result.exceptionOrNull() is IllegalArgumentException
            val hasAttemptsLeft = attempt < VOLUME_QUERY_ATTEMPTS - 1
            if (!isVolumeRegistrationRace || !hasAttemptsLeft) return emptyList()
            Thread.sleep(VOLUME_QUERY_RETRY_DELAY_MS)
        }
        return emptyList()
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
        const val VOLUME_QUERY_ATTEMPTS = 2
        const val VOLUME_QUERY_RETRY_DELAY_MS = 400L
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
    }
}
