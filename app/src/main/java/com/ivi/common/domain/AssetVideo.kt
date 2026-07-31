package com.ivi.common.domain

data class AssetVideo(
    val assetPath: String,
    val fileName: String,
    val title: String,
    /** False when [assetPath] is an externally mounted USB file URI. */
    val isPackagedAsset: Boolean = true
) {
    fun toVideoSource(): VideoSource.Asset {
        return VideoSource.Asset(
            assetPath = assetPath,
            title = title,
            isPackagedAsset = isPackagedAsset
        )
    }
}
