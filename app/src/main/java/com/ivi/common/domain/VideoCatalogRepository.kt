package com.ivi.common.domain

import kotlinx.coroutines.flow.Flow

interface VideoCatalogRepository {
    suspend fun listVideos(): List<AssetVideo>

    /** Emits a fresh catalog when removable storage is mounted or ejected. */
    fun observeVideos(): Flow<List<AssetVideo>>
}
