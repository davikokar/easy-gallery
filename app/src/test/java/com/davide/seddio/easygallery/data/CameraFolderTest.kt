package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFolderTest {

    @Test
    fun `isCameraFolder returns true for camera directories on different volumes and DCF names`() {
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/Camera"))
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/0/dcim/camera"))
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/10/DCIM/Camera"))
        assertTrue(CameraFolder.isCameraFolder("/storage/1A2B-3C4D/DCIM/Camera"))
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/100ANDRO"))
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/100MEDIA"))
        assertTrue(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/Camera/"))
        assertTrue(CameraFolder.isCameraFolder("/storage//emulated///0//DCIM//Camera"))
    }

    @Test
    fun `isCameraFolder returns false for non-camera paths null and blank`() {
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM"))
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/Facebook"))
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/Screenshots"))
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/Pictures/Camera"))
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/DCIM/Camera/Sub"))
        assertFalse(CameraFolder.isCameraFolder("/storage/emulated/0/MyDCIM/Camera"))
        assertFalse(CameraFolder.isCameraFolder(""))
        assertFalse(CameraFolder.isCameraFolder(null))
    }

    @Test
    fun `implicitDefaultsForFolder returns empty overrides for non-camera folders`() {
        val overrides = CameraFolder.implicitDefaultsForFolder("/storage/emulated/0/Pictures/Camera")

        assertEquals(FolderViewOverrides(), overrides)
        assertTrue(overrides.isEmpty())
    }

    @Test
    fun `implicitDefaultsForFolder returns only date taken descending for camera folders`() {
        val overrides = CameraFolder.implicitDefaultsForFolder("/storage/emulated/0/DCIM/Camera")

        assertEquals(
            FolderViewOverrides(
                sortType = SortType.DATE_TAKEN,
                sortOrder = SortOrder.DESCENDING
            ),
            overrides
        )
    }
}
