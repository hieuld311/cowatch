package com.ivi.common.ipc

import android.os.Parcelable
import android.os.Parcel

object ShareProtocol {
    const val VERSION = 4
    const val PID_PACKAGE = "com.hieuld.cowatch.pid"
    const val REAR_LEFT_PACKAGE = "com.hieuld.cowatch.rear.left"
    const val REAR_RIGHT_PACKAGE = "com.hieuld.cowatch.rear.right"
    const val HOST_SERVICE_CLASS = "com.ivi.pid.sharing.PidPlaybackShareService"
    const val SIGNATURE_PERMISSION = "com.hieuld.cowatch.permission.SHARE_PLAYBACK"
    const val EXTRA_CONSENT_BOOTSTRAP = "com.ivi.common.extra.CONSENT_BOOTSTRAP"
}

object ScreenRole {
    const val REAR_LEFT = "REAR_LEFT"
    const val REAR_RIGHT = "REAR_RIGHT"
    val all = listOf(REAR_LEFT, REAR_RIGHT)

    fun packageName(role: String): String = when (role) {
        REAR_LEFT -> ShareProtocol.REAR_LEFT_PACKAGE
        REAR_RIGHT -> ShareProtocol.REAR_RIGHT_PACKAGE
        else -> error("Unsupported rear role: $role")
    }

    fun label(role: String): String = when (role) {
        REAR_LEFT -> "Rear Left Screen"
        REAR_RIGHT -> "Rear Right Screen"
        else -> role
    }
}

object ReceiverState {
    const val NOT_RUNNING = "NOT_RUNNING"
    const val STARTING = "STARTING"
    const val READY = "READY"
    const val AWAITING_RESPONSE = "AWAITING_RESPONSE"
    const val ACCEPTED = "ACCEPTED"
    const val SURFACE_READY = "SURFACE_READY"
    const val DENIED = "DENIED"
    const val SHARING = "SHARING"
    const val DISCONNECTED = "DISCONNECTED"
    const val FAILED = "FAILED"
}

data class ReceiverRegistration(
    val role: String,
    val displayId: Int,
    val protocolVersion: Int,
    val ready: Boolean
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readInt(), parcel.readInt(), parcel.readInt() != 0
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(role); parcel.writeInt(displayId); parcel.writeInt(protocolVersion)
        parcel.writeInt(if (ready) 1 else 0)
    }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<ReceiverRegistration> {
        override fun createFromParcel(parcel: Parcel) = ReceiverRegistration(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ReceiverRegistration>(size)
    }
}

data class ReceiverStatus(
    val role: String,
    val displayId: Int,
    val state: String,
    val sessionId: String? = null,
    val errorMessage: String? = null
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readInt(), parcel.readString().orEmpty(),
        parcel.readString(), parcel.readString()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(role); parcel.writeInt(displayId); parcel.writeString(state)
        parcel.writeString(sessionId); parcel.writeString(errorMessage)
    }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<ReceiverStatus> {
        override fun createFromParcel(parcel: Parcel) = ReceiverStatus(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ReceiverStatus>(size)
    }
}

data class SharedSessionSnapshot(
    val protocolVersion: Int = ShareProtocol.VERSION,
    val sessionId: String,
    val mediaId: String,
    val assetPath: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val anchorElapsedRealtimeMs: Long,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val playbackSpeed: Float,
    val scheduledStartElapsedRealtimeMs: Long,
    val seekEventId: Long,
    val sequence: Long
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readInt(), parcel.readString().orEmpty(), parcel.readString().orEmpty(),
        parcel.readString().orEmpty(), parcel.readString().orEmpty(), parcel.readLong(),
        parcel.readLong(), parcel.readLong(), parcel.readInt() != 0, parcel.readInt() != 0,
        parcel.readFloat(),
        parcel.readLong(), parcel.readLong(), parcel.readLong()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(protocolVersion); parcel.writeString(sessionId); parcel.writeString(mediaId)
        parcel.writeString(assetPath); parcel.writeString(title); parcel.writeLong(positionMs)
        parcel.writeLong(durationMs); parcel.writeLong(anchorElapsedRealtimeMs)
        parcel.writeInt(if (playWhenReady) 1 else 0)
        parcel.writeInt(if (isPlaying) 1 else 0); parcel.writeFloat(playbackSpeed)
        parcel.writeLong(scheduledStartElapsedRealtimeMs)
        parcel.writeLong(seekEventId); parcel.writeLong(sequence)
    }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<SharedSessionSnapshot> {
        override fun createFromParcel(parcel: Parcel) = SharedSessionSnapshot(parcel)
        override fun newArray(size: Int) = arrayOfNulls<SharedSessionSnapshot>(size)
    }
}
