package com.ivi.common.domain

interface VideoCatalogRepository {
    suspend fun listVideos(): List<AssetVideo>
}
