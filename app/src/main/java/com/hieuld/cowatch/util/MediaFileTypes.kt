package com.hieuld.cowatch.util

import java.util.Locale

object MediaFileTypes {
    val supportedVideoExtensions: Set<String> = setOf("mp4", "m4v", "webm", "mkv")

    fun isSupportedVideoFileName(fileName: String): Boolean {
        val extension = fileName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return extension in supportedVideoExtensions
    }
}
