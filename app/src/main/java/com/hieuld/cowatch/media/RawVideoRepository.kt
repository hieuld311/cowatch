package com.hieuld.cowatch.media

import com.hieuld.cowatch.R

object RawVideoRepository {

    fun listVideos(): List<RawVideo> {
        return R.raw::class.java.fields
            .mapNotNull { field ->
                val resourceName = field.name
                val resId = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null

                RawVideo(
                    resId = resId,
                    resourceName = resourceName,
                    title = resourceName.toVideoTitle()
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun String.toVideoTitle(): String {
        return split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }
}
