package com.davide.seddio.easygallery.ui

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSupportIntentTest {

    @Test
    fun createSupportEmailIntent_hasCorrectDetails() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        
        val intent = createSupportEmailIntent(context)
        
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        
        val uriString = intent.data?.toString() ?: ""
        assertTrue("Recipient missing", uriString.contains("davidemela.support@gmail.com"))
        assertTrue("Subject missing", uriString.contains("Easy%20Gallery%20support%20request"))
    }
}
