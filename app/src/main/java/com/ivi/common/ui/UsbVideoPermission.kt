package com.ivi.common.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity

public const val USB_VIDEO_PERMISSION_REQUEST_CODE: Int = 402

/** Requests only the media-read permission required for mounted USB video files. */
public fun ComponentActivity.requestUsbVideoPermissionIfNeeded() {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        Log.i(TAG, "Requesting $permission for USB video scan")
        requestPermissions(arrayOf(permission), USB_VIDEO_PERMISSION_REQUEST_CODE)
    } else {
        Log.i(TAG, "$permission already granted")
    }
}

public fun logUsbVideoPermissionResult(granted: Boolean) {
    Log.i(TAG, "USB video read permission granted=$granted")
}

private const val TAG = "CoWatchUsbPermission"
