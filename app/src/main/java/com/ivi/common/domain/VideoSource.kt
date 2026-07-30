package com.ivi.common.domain

sealed interface VideoSource {
    val title: String

    data class Asset(
        val assetPath: String,
        override val title: String
    ) : VideoSource
}
