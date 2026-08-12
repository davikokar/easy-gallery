package com.davide.seddio.easygallery.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentResolver
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric drives a real [Build.VERSION.SDK_INT] via [Config.sdk], so the SDK gating in
 * [DefaultMediaPermissionHandler] is exercised portably without reflective field hacks.
 *
 * [MediaStore.createDeleteRequest] / [MediaStore.createWriteRequest] are not implemented by the
 * built-in `ShadowMediaStore`, so a small local shadow supplies a real [PendingIntent] for the
 * SDK >= R branches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class DefaultMediaPermissionHandlerTest {

    private val handler = DefaultMediaPermissionHandler()

    private val resolver: ContentResolver
        get() = RuntimeEnvironment.getApplication().contentResolver

    private val uris = listOf(Uri.parse("content://media/external/images/media/1"))

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `createDeleteRequest returns null below Android R`() {
        assertNull(handler.createDeleteRequest(resolver, uris))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowMediaStoreRequests::class])
    fun `createDeleteRequest returns an intent sender on Android R and above`() {
        assertNotNull(handler.createDeleteRequest(resolver, uris))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `createWriteRequest returns null below Android R`() {
        assertNull(handler.createWriteRequest(resolver, uris))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowMediaStoreRequests::class])
    fun `createWriteRequest returns an intent sender on Android R and above`() {
        assertNotNull(handler.createWriteRequest(resolver, uris))
    }

    @Test
    fun `getIntentSenderFromException returns null for a plain SecurityException`() {
        assertNull(handler.getIntentSenderFromException(SecurityException("denied")))
    }

    @Test
    fun `getIntentSenderFromException extracts the intent sender from a recoverable exception`() {
        val context = RuntimeEnvironment.getApplication()
        val pendingIntent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_delete)
        val remoteAction = RemoteAction(icon, "title", "description", pendingIntent)
        val exception = RecoverableSecurityException(
            IllegalStateException("cause"),
            "user facing message",
            remoteAction
        )

        val result = handler.getIntentSenderFromException(exception)

        assertNotNull(result)
        assertEquals(pendingIntent.intentSender, result)
    }
}

/** Supplies the delete/write requests that the built-in `ShadowMediaStore` does not implement. */
@Implements(MediaStore::class)
class ShadowMediaStoreRequests {
    companion object {
        @JvmStatic
        @Implementation
        fun createDeleteRequest(resolver: ContentResolver, uris: Collection<Uri>): PendingIntent =
            newRequest()

        @JvmStatic
        @Implementation
        fun createWriteRequest(resolver: ContentResolver, uris: Collection<Uri>): PendingIntent =
            newRequest()

        private fun newRequest(): PendingIntent = PendingIntent.getActivity(
            RuntimeEnvironment.getApplication(),
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
