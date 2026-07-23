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
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketName = cursor.getString(bucketColumn) ?: "Unknown"
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val existing = foldersMap[bucketName]
                if (existing == null) {
                    foldersMap[bucketName] = FolderData(bucketName, 1, uri)
                } else {
                    foldersMap[bucketName] = existing.copy(count = existing.count + 1)
                }
            }
        }

        foldersMap.values.map {
            Folder(it.name, it.count, it.thumbnailUri)
        }.sortedBy { it.name }
    }

    private data class FolderData(
        val name: String,
        val count: Int,
        val thumbnailUri: android.net.Uri
    )
}
