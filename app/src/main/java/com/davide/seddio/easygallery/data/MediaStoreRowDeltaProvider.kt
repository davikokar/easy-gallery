package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

private const val COLUMN_ID = "_id"
private const val COLUMN_DATE_MODIFIED = "date_modified"
private const val COLUMN_GENERATION_ADDED = "generation_added"
private const val COLUMN_GENERATION_MODIFIED = "generation_modified"
private val LEGACY_IMAGE_COLLECTION_URI: Uri = Uri.parse("content://media/external/images/media")
private val LEGACY_VIDEO_COLLECTION_URI: Uri = Uri.parse("content://media/external/video/media")

interface MediaStoreRowDeltaProvider {
    fun queryObservedRows(
        volumeName: String,
        apiBranch: MediaStoreDeltaApiBranch
    ): List<MediaStoreObservedRowDelta>
}

class AndroidMediaStoreRowDeltaProvider(
    context: Context,
    private val contentResolver: ContentResolver = context.contentResolver
) : MediaStoreRowDeltaProvider {

    override fun queryObservedRows(
        volumeName: String,
        apiBranch: MediaStoreDeltaApiBranch
    ): List<MediaStoreObservedRowDelta> {
        if (apiBranch == MediaStoreDeltaApiBranch.API_28 && volumeName != MediaStore.VOLUME_EXTERNAL_PRIMARY) {
            return emptyList()
        }

        val includeGeneration = apiBranch == MediaStoreDeltaApiBranch.API_30_PLUS
        val imageUri = collectionUri(
            volumeName = volumeName,
            apiBranch = apiBranch,
            mediaPathSegment = "images",
            legacyUri = LEGACY_IMAGE_COLLECTION_URI
        )
        val videoUri = collectionUri(
            volumeName = volumeName,
            apiBranch = apiBranch,
            mediaPathSegment = "video",
            legacyUri = LEGACY_VIDEO_COLLECTION_URI
        )

        return buildList {
            addAll(queryCollection(imageUri, volumeName, includeGeneration))
            addAll(queryCollection(videoUri, volumeName, includeGeneration))
        }
    }

    private fun collectionUri(
        volumeName: String,
        apiBranch: MediaStoreDeltaApiBranch,
        mediaPathSegment: String,
        legacyUri: Uri
    ): Uri {
        return when (apiBranch) {
            MediaStoreDeltaApiBranch.API_28 -> legacyUri
            MediaStoreDeltaApiBranch.API_29,
            MediaStoreDeltaApiBranch.API_30_PLUS -> Uri.parse("content://media/$volumeName/$mediaPathSegment/media")
        }
    }

    private fun queryCollection(
        collectionUri: Uri,
        volumeName: String,
        includeGeneration: Boolean
    ): List<MediaStoreObservedRowDelta> {
        val projection = buildList {
            add(COLUMN_ID)
            add(COLUMN_DATE_MODIFIED)
            if (includeGeneration) {
                add(COLUMN_GENERATION_ADDED)
                add(COLUMN_GENERATION_MODIFIED)
            }
        }.toTypedArray()

        val rows = mutableListOf<MediaStoreObservedRowDelta>()
        contentResolver.query(collectionUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE_MODIFIED)
            val generationAddedIndex = cursor.getColumnIndex(COLUMN_GENERATION_ADDED)
            val generationModifiedIndex = cursor.getColumnIndex(COLUMN_GENERATION_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val observedUri = ContentUris.withAppendedId(collectionUri, id)
                rows += MediaStoreObservedRowDelta(
                    mediaUri = canonicalMediaStoreUriForJoin(observedUri.toString()),
                    volumeName = volumeName,
                    generationAdded = cursor.longOrNull(generationAddedIndex),
                    generationModified = cursor.longOrNull(generationModifiedIndex),
                    dateModifiedSeconds = cursor.getLong(dateModifiedIndex)
                )
            }
        }

        return rows
    }
}

private fun android.database.Cursor.longOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0) return null
    if (isNull(columnIndex)) return null
    return getLong(columnIndex)
}
