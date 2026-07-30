package com.ivi.common.media

import android.graphics.Bitmap
import java.io.Closeable

interface SeekFrameProvider {
    fun open(assetPath: String): SeekFrameDecoder?
}

interface SeekFrameDecoder : Closeable {
    fun frameAt(positionMs: Long): Bitmap?
}
