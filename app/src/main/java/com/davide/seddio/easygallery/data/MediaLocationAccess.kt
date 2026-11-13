package com.davide.seddio.easygallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether `ACCESS_MEDIA_LOCATION` is held; without it extraction stores no location at all.
 */
fun interface MediaLocationAccessChecker {
    fun isGranted(): Boolean
}

class AndroidMediaLocationAccessChecker(private val context: Context) : MediaLocationAccessChecker {
    // The permission does not exist below API 29, where original-file location is never redacted.
    override fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
