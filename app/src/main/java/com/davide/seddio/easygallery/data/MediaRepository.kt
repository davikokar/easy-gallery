package com.davide.seddio.easygallery.data

import android.net.Uri

interface MediaRepository {
    suspend fun getFolders(): List<Folder>
    suspend fun getMediaInFolder(bucketName: String): List<MediaItem>
    suspend fun getAllMedia(): List<MediaItem>
    suspend fun copyFolderContents(sourcePath: String, targetParentPath: String)
    suspend fun copyFile(sourceFolderPath: String, fileName: String, targetFolderPath: String)
    suspend fun deleteMediaItems(uris: List<Uri>)
    suspend fun updateMediaRelativePath(uris: List<Uri>, targetRelativePath: String)
    fun getSubdirectories(path: String): List<Folder>
}
