package com.hieuld.cowatch.media

data class RawVideo(
    val resId: Int,
    val resourceName: String,
    val title: String
) {
    fun toVideoSource(): VideoSource.Raw {
        return VideoSource.Raw(
            resId = resId,
            resourceName = resourceName,
            title = title
        )
    }
}
