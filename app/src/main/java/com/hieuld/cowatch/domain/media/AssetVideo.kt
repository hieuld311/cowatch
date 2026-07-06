package com.hieuld.cowatch.domain.media

data class AssetVideo(
    val assetPath: String,
    val fileName: String,
    val title: String
) {
    fun toVideoSource(): VideoSource.Asset {
        return VideoSource.Asset(
            assetPath = assetPath,
            title = title
        )
    }
}
