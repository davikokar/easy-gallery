package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumUnevaluatableCause

/** The one status notice an album can show; [NONE] is every status but an unevaluatable one. */
enum class AlbumStatusNoticeKind { NONE, NEWER_APP, FACE_DATA_OFF, DEVICE_INCAPABLE, ANCHOR_NOT_FOUND }

/** How a notice is shown: [shortTextRes] on a tile or row, [fullTextRes] in the Album Detail View band. */
class AlbumStatusNoticeSpec(
    val testTag: String,
    @param:StringRes val shortTextRes: Int,
    @param:StringRes val fullTextRes: Int,
    val icon: ImageVector
)

/**
 * The single place that decides which status notice an album shows, so the tile, the row and the
 * Album Detail View band cannot drift. A waiting, complete or manual album shows none (ADR-0017 Amendment 3).
 */
fun albumStatusNoticeKind(
    status: AlbumEvaluationStatus,
    cause: AlbumUnevaluatableCause?
): AlbumStatusNoticeKind {
    if (status != AlbumEvaluationStatus.UNEVALUATABLE) return AlbumStatusNoticeKind.NONE
    return when (cause) {
        AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF -> AlbumStatusNoticeKind.FACE_DATA_OFF
        AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE -> AlbumStatusNoticeKind.DEVICE_INCAPABLE
        AlbumUnevaluatableCause.ANCHOR_NOT_FOUND -> AlbumStatusNoticeKind.ANCHOR_NOT_FOUND
        // An unevaluatable status always has a cause; a missing one gets the older, general notice.
        AlbumUnevaluatableCause.RULE_NOT_UNDERSTOOD, null -> AlbumStatusNoticeKind.NEWER_APP
    }
}

/** Null for [AlbumStatusNoticeKind.NONE]. */
fun AlbumStatusNoticeKind.spec(): AlbumStatusNoticeSpec? = when (this) {
    AlbumStatusNoticeKind.NONE -> null
    AlbumStatusNoticeKind.NEWER_APP -> AlbumStatusNoticeSpec(
        testTag = "album_rule_unevaluatable_notice",
        shortTextRes = R.string.album_rule_unevaluatable_notice_short,
        fullTextRes = R.string.album_rule_unevaluatable_notice,
        icon = Icons.Default.SystemUpdate
    )
    AlbumStatusNoticeKind.FACE_DATA_OFF -> AlbumStatusNoticeSpec(
        testTag = "album_people_face_data_off_notice",
        shortTextRes = R.string.album_people_face_data_off_notice_short,
        fullTextRes = R.string.album_people_face_data_off_notice,
        icon = Icons.Default.FaceRetouchingOff
    )
    AlbumStatusNoticeKind.DEVICE_INCAPABLE -> AlbumStatusNoticeSpec(
        testTag = "album_people_device_incapable_notice",
        shortTextRes = R.string.album_people_device_incapable_notice_short,
        fullTextRes = R.string.album_people_device_incapable_notice,
        icon = Icons.Default.FaceRetouchingOff
    )
    AlbumStatusNoticeKind.ANCHOR_NOT_FOUND -> AlbumStatusNoticeSpec(
        testTag = "album_people_anchor_not_found_notice",
        shortTextRes = R.string.album_people_anchor_not_found_notice_short,
        fullTextRes = R.string.album_people_anchor_not_found_notice,
        icon = Icons.Default.PersonOff
    )
}
