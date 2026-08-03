package com.davide.seddio.easygallery.data

import android.net.Uri

class FakeMediaRepository : MediaRepository {
    var folders = listOf<Folder>()
    var mediaItems = listOf<MediaItem>()
    val deletedUris = mutableListOf<Uri>()
    val movedUris = mutableListOf<Pair<List<Uri>, String>>()
    var shouldThrowSecurityException = false

    override suspend fun getFolders(): List<Folder> = folders

    override suspend fun getMediaInFolder(bucketName: String): List<MediaItem> =
        mediaItems.filter { it.bucketName == bucketName }

    override suspend fun getAllMedia(): List<MediaItem> = mediaItems

    override suspend fun copyFolderContents(sourcePath: String, targetParentPath: String) {}

    override suspend fun copyFile(sourceFolderPath: String, fileName: String, targetFolderPath: String) {}

    override suspend fun rotateImage(uri: Uri, degrees: Int) {}

    override suspend fun deleteMediaItems(uris: List<Uri>) {
        if (shouldThrowSecurityException) throw SecurityException("Mock security exception")
        deletedUris.addAll(uris)
    }

    override suspend fun updateMediaRelativePath(uris: List<Uri>, targetRelativePath: String) {
        if (shouldThrowSecurityException) throw SecurityException("Mock security exception")
        movedUris.add(uris to targetRelativePath)
    }

    override fun getSubdirectories(path: String): List<Folder> = emptyList()
}
