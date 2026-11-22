package com.davide.seddio.easygallery.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDataDisclosurePropertiesTest {

    @Test
    fun `the disclosure is closed by neither Back nor a tap outside`() {
        val properties = faceDataDisclosureProperties(isInfoMode = false)

        assertFalse(properties.dismissOnBackPress)
        assertFalse(properties.dismissOnClickOutside)
    }

    @Test
    fun `the info mode is closed by Back and by a tap outside`() {
        val properties = faceDataDisclosureProperties(isInfoMode = true)

        assertTrue(properties.dismissOnBackPress)
        assertTrue(properties.dismissOnClickOutside)
    }
}
