package com.davide.seddio.easygallery.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreDataSource(private val context: Context) : MediaRepository {

    override suspend fun getFolders(): List<Folder> = withContext(Dispatchers.IO) {
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

    override suspend fun getMediaInFolder(bucketName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val media = mutableListOf<MediaItem>()
        val selection = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(bucketName)

        queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, selection, selectionArgs) { media.add(it) }
        queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, selection, selectionArgs) { media.add(it) }

        media.sortedByDescending { it.dateAdded }
    }

    override suspend fun getAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val media = mutableListOf<MediaItem>()
        queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) { media.add(it) }
        queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true) { media.add(it) }
        media.sortedByDescending { it.dateAdded }
    }

    override suspend fun copyFolderContents(sourcePath: String, targetParentPath: String): Unit = withContext(Dispatchers.IO) {
        val sourceDir = File(sourcePath)
        val targetDir = File(targetParentPath, sourceDir.name)
        if (!targetDir.exists()) targetDir.mkdirs()

        sourceDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val destFile = File(targetDir, file.name)
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            } else if (file.isDirectory) {
                copyFolderContents(file.absolutePath, targetDir.absolutePath)
            }
        }
    }

    override suspend fun copyFile(sourceFolderPath: String, fileName: String, targetFolderPath: String) = withContext(Dispatchers.IO) {
        val sourceFile = File(sourceFolderPath, fileName)
        val targetFile = File(targetFolderPath, fileName)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
    }

    override suspend fun deleteMediaItems(uris: List<Uri>) = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            context.contentResolver.delete(uri, null, null)
        }
    }

    override suspend fun updateMediaRelativePath(uris: List<Uri>, targetRelativePath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
        }
        uris.forEach { uri ->
            context.contentResolver.update(uri, values, null, null)
        }
    }

    override fun getSubdirectories(path: String): List<Folder> {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return emptyList()
        
        return root.listFiles { file -> file.isDirectory && !file.isHidden }
            ?.map { file ->
                Folder(
                    name = file.name,
                    imageCount = 0, // Not needed for browsing
                    thumbnailUri = Uri.EMPTY, // Will use default folder icon in UI
                    path = file.absolutePath
                )
            }?.sortedBy { it.name } ?: emptyList()
    }

    override suspend fun createFolder(parentPath: String, folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val folder = File(parentPath, folderName)
            if (folder.exists()) {
                Result.failure(Exception("Folder already exists"))
            } else {
                if (folder.mkdirs()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to create folder"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun folderExists(path: String): Boolean {
        return File(path).exists()
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
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        ).apply {
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val durCol = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val bucket = cursor.getString(bucketCol) ?: "Unknown"
                val added = cursor.getLong(addedCol)
                val modified = cursor.getLong(modCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val path = cursor.getString(dataCol) ?: ""
                val folderPath = path.substringBeforeLast("/")
                val size = cursor.getLong(sizeCol)
                val duration = if (isVideo) cursor.getLong(durCol) else null
                val itemUri = ContentUris.withAppendedId(uri, id)

                val type = when {
                    isVideo -> MediaType.VIDEO
                    mime.contains("gif", ignoreCase = true) -> MediaType.GIF
                    else -> MediaType.IMAGE
                }

                onItem(MediaItem(itemUri, name, added, modified, size, type, bucket, folderPath, duration))
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
