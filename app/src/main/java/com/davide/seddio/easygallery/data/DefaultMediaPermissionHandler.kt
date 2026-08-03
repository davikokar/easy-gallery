package com.davide.seddio.easygallery.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class DefaultMediaPermissionHandler : MediaPermissionHandler {
    override fun createDeleteRequest(contentResolver: ContentResolver, uris: List<Uri>): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        } else null
    }

    override fun createWriteRequest(contentResolver: ContentResolver, uris: List<Uri>): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(contentResolver, uris).intentSender
        } else null
    }

    override fun getIntentSenderFromException(e: SecurityException): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        } else null
    }
}
