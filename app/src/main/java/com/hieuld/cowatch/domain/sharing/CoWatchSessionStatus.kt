package com.hieuld.cowatch.domain.sharing

enum class CoWatchSessionStatus {
    PREPARING_SHARE,
    PLAYING_SHARED
}

enum class ShareHostNotification(
    val message: String
) {
    ACCEPTED("Video broadcast accepted"),
    DENIED("Video broadcast denied"),
    ENDED("Video broadcast ended")
}
