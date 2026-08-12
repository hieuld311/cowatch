package com.ivi.common.playback

import com.ivi.common.domain.AssetVideo
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.common.domain.VideoSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Shared circular next/previous navigation for a flavor's current video catalog. */
@Singleton
class VideoCatalogNavigator @Inject constructor(
    repository: VideoCatalogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val videos: StateFlow<List<AssetVideo>> = repository.observeVideos().stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun previous(current: VideoSource.Asset?): VideoSource.Asset? =
        adjacent(current, -1)?.toVideoSource()

    fun next(current: VideoSource.Asset?): VideoSource.Asset? =
        adjacent(current, 1)?.toVideoSource()

    private fun adjacent(current: VideoSource.Asset?, direction: Int): AssetVideo? {
        val catalog = videos.value
        if (catalog.isEmpty()) return null
        val currentIndex = current?.let { source ->
            catalog.indexOfFirst { it.assetPath == source.assetPath }
        } ?: -1
        if (currentIndex < 0) return catalog.first()
        return catalog[(currentIndex + direction).floorMod(catalog.size)]
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
