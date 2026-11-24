package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumUnevaluatableCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumStatusNoticesTest {

    private fun kind(status: AlbumEvaluationStatus, cause: AlbumUnevaluatableCause?) =
        albumStatusNoticeKind(status, cause)

    @Test
    fun `a rule not understood is the newer app notice`() {
        assertEquals(
            AlbumStatusNoticeKind.NEWER_APP,
            kind(AlbumEvaluationStatus.UNEVALUATABLE, AlbumUnevaluatableCause.RULE_NOT_UNDERSTOOD)
        )
    }

    @Test
    fun `face data off is the face data off notice`() {
        assertEquals(
            AlbumStatusNoticeKind.FACE_DATA_OFF,
            kind(AlbumEvaluationStatus.UNEVALUATABLE, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF)
        )
    }

    @Test
    fun `an incapable device is the device incapable notice`() {
        assertEquals(
            AlbumStatusNoticeKind.DEVICE_INCAPABLE,
            kind(AlbumEvaluationStatus.UNEVALUATABLE, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE)
        )
    }

    @Test
    fun `an anchor not found is the person not found notice`() {
        assertEquals(
            AlbumStatusNoticeKind.ANCHOR_NOT_FOUND,
            kind(AlbumEvaluationStatus.UNEVALUATABLE, AlbumUnevaluatableCause.ANCHOR_NOT_FOUND)
        )
    }

    @Test
    fun `an unevaluatable status with no cause falls back to the newer app notice`() {
        assertEquals(AlbumStatusNoticeKind.NEWER_APP, kind(AlbumEvaluationStatus.UNEVALUATABLE, null))
    }

    @Test
    fun `a waiting album shows nothing whatever the cause`() {
        assertEquals(AlbumStatusNoticeKind.NONE, kind(AlbumEvaluationStatus.WAITING_ON_INDEX, null))
        AlbumUnevaluatableCause.entries.forEach { cause ->
            assertEquals(cause.name, AlbumStatusNoticeKind.NONE, kind(AlbumEvaluationStatus.WAITING_ON_INDEX, cause))
        }
    }

    @Test
    fun `a complete and a manual album show nothing`() {
        assertEquals(AlbumStatusNoticeKind.NONE, kind(AlbumEvaluationStatus.COMPLETE, null))
        assertEquals(AlbumStatusNoticeKind.NONE, kind(AlbumEvaluationStatus.MANUAL, null))
    }

    @Test
    fun `every cause under an unevaluatable status gets a notice of its own`() {
        val kinds = AlbumUnevaluatableCause.entries.map { kind(AlbumEvaluationStatus.UNEVALUATABLE, it) }

        assertEquals(AlbumUnevaluatableCause.entries.size, kinds.toSet().size)
        assertFalse(kinds.contains(AlbumStatusNoticeKind.NONE))
    }

    @Test
    fun `none has no spec and every other kind has its own tag and wording`() {
        assertNull(AlbumStatusNoticeKind.NONE.spec())

        val specs = AlbumStatusNoticeKind.entries.filter { it != AlbumStatusNoticeKind.NONE }.map { it.spec()!! }
        assertEquals(specs.size, specs.map { it.testTag }.toSet().size)
        assertEquals(specs.size, specs.map { it.shortTextRes }.toSet().size)
        assertEquals(specs.size, specs.map { it.fullTextRes }.toSet().size)
        specs.forEach { assertNotEquals(it.shortTextRes, it.fullTextRes) }
    }

    @Test
    fun `each kind shows its own short and full wording`() {
        val wording = AlbumStatusNoticeKind.entries.filter { it != AlbumStatusNoticeKind.NONE }
            .associateWith { it.spec()!!.shortTextRes to it.spec()!!.fullTextRes }

        assertEquals(
            mapOf(
                AlbumStatusNoticeKind.NEWER_APP to
                    (R.string.album_rule_unevaluatable_notice_short to R.string.album_rule_unevaluatable_notice),
                AlbumStatusNoticeKind.FACE_DATA_OFF to
                    (R.string.album_people_face_data_off_notice_short to R.string.album_people_face_data_off_notice),
                AlbumStatusNoticeKind.DEVICE_INCAPABLE to
                    (R.string.album_people_device_incapable_notice_short to R.string.album_people_device_incapable_notice),
                AlbumStatusNoticeKind.ANCHOR_NOT_FOUND to
                    (R.string.album_people_anchor_not_found_notice_short to R.string.album_people_anchor_not_found_notice)
            ),
            wording
        )
    }

    @Test
    fun `the tags and icons are the plan's`() {
        val tags = AlbumStatusNoticeKind.entries.filter { it != AlbumStatusNoticeKind.NONE }
            .associateWith { it.spec()!!.testTag }
        val icons = AlbumStatusNoticeKind.entries.filter { it != AlbumStatusNoticeKind.NONE }
            .associateWith { it.spec()!!.icon.name }

        assertEquals(
            mapOf(
                AlbumStatusNoticeKind.NEWER_APP to "album_rule_unevaluatable_notice",
                AlbumStatusNoticeKind.FACE_DATA_OFF to "album_people_face_data_off_notice",
                AlbumStatusNoticeKind.DEVICE_INCAPABLE to "album_people_device_incapable_notice",
                AlbumStatusNoticeKind.ANCHOR_NOT_FOUND to "album_people_anchor_not_found_notice"
            ),
            tags
        )
        assertEquals(
            mapOf(
                AlbumStatusNoticeKind.NEWER_APP to "Filled.SystemUpdate",
                AlbumStatusNoticeKind.FACE_DATA_OFF to "Filled.FaceRetouchingOff",
                AlbumStatusNoticeKind.DEVICE_INCAPABLE to "Filled.FaceRetouchingOff",
                AlbumStatusNoticeKind.ANCHOR_NOT_FOUND to "Filled.PersonOff"
            ),
            icons
        )
    }
}
