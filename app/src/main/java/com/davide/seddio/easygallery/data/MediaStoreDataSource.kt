package com.davide.seddio.easygallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val context: Context) {

    suspend fun getFolders(): List<Folder> = withContext(Dispatchers.IO) {
        val foldersMap = mutableMapOf<String, FolderData>()
        
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).forEach { uri ->
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val bucket = cursor.getString(bucketCol) ?: "Unknown"
                    val added = cursor.getLong(dateCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val modified = cursor.getLong(modCol)
                    val itemUri = ContentUris.withAppendedId(uri, id)
                    val folderPath = path.substringBeforeLast("/")

                    val existing = foldersMap[bucket]
                    if (existing == null) {
                        foldersMap[bucket] = FolderData(bucket, 1, itemUri, folderPath, size, modified, added)
                    } else {
                        foldersMap[bucket] = existing.copy(
                            count = existing.count + 1,
                            totalSize = existing.totalSize + size,
                            lastModified = maxOf(existing.lastModified, modified),
                            dateTaken = maxOf(existing.dateTaken, added)
                        )
                    }
                }
            }
        }

        foldersMap.values.map {
            Folder(it.name, it.count, it.thumbnailUri, false, it.path, it.totalSize, it.lastModified, it.dateTaken)
        }
    }

    suspend fun getMediaInFolder(bucketName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val media = mutableListOf<MediaItem>()
        val selection = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(bucketName)

        queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, selection, selectionArgs) { media.add(it) }
        queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, selection, selectionArgs) { media.add(it) }

        media.sortedByDescending { it.dateAdded }
    }

    suspend fun getAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val media = mutableListOf<MediaItem>()
        queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) { media.add(it) }
        queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true) { media.add(it) }
        media.sortedByDescending { it.dateAdded }
    }

    private fun queryMedia(
        uri: android.net.Uri,
        isVideo: Boolean,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        onItem: (MediaItem) -> Unit
    ) {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        ).apply {
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val durCol = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val added = cursor.getLong(addedCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val duration = if (isVideo) cursor.getLong(durCol) else null
                val itemUri = ContentUris.withAppendedId(uri, id)

                val type = when {
                    isVideo -> MediaType.VIDEO
                    mime.contains("gif", ignoreCase = true) -> MediaType.GIF
                    else -> MediaType.IMAGE
                }

                onItem(MediaItem(itemUri, name, added, type, duration))
            }
        }
    }

    private data class FolderData(
        val name: String,
        val count: Int,
        val thumbnailUri: android.net.Uri,
        val path: String,
        val totalSize: Long,
        val lastModified: Long,
        val dateTaken: Long
    )
}
