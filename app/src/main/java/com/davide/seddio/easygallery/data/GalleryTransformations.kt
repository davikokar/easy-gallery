package com.davide.seddio.easygallery.data

import java.text.SimpleDateFormat
import java.util.*

object GalleryTransformations {

    fun filterAndSortFolders(
        allMedia: List<MediaItem>,
        query: String,
        pinned: Set<String>,
        sort: SortType,
        order: SortOrder,
        excluded: Set<String>,
        showExcluded: Boolean,
        types: Set<MediaType>
    ): List<Folder> {
        val foldersMap = mutableMapOf<String, Folder>()
        allMedia.forEach { item ->
            val isExcluded = excluded.contains(item.folderPath)
            if ((showExcluded || !isExcluded) && types.contains(item.type)) {
                val existing = foldersMap[item.folderPath]
                if (existing == null) {
                    foldersMap[item.folderPath] = Folder(
                        name = item.bucketName,
                        imageCount = 1,
                        thumbnailUri = item.uri,
                        isPinned = pinned.contains(item.folderPath),
                        path = item.folderPath,
                        size = item.size,
                        dateModified = item.dateModified,
                        dateTaken = item.dateAdded
                    )
                } else {
                    foldersMap[item.folderPath] = existing.copy(
                        imageCount = existing.imageCount + 1,
                        size = existing.size + item.size,
                        dateModified = maxOf(existing.dateModified, item.dateModified),
                        dateTaken = maxOf(existing.dateTaken, item.dateAdded)
                    )
                }
            }
        }

        val foldersList = foldersMap.values.toList()

        val sorted = when (sort) {
            SortType.NAME -> foldersList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortType.PATH -> foldersList.sortedBy { it.path }
            SortType.SIZE -> foldersList.sortedByDescending { it.size }
            SortType.LAST_MODIFIED -> foldersList.sortedByDescending { it.dateModified }
            SortType.DATE_TAKEN -> foldersList.sortedByDescending { it.dateTaken }
            SortType.RANDOM -> foldersList.shuffled()
        }

        val ordered = if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
            sorted.reversed()
        } else {
            sorted
        }

        val finalSorted = ordered.sortedByDescending { it.isPinned }

        return if (query.isNotEmpty()) {
            finalSorted.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            finalSorted
        }
    }

    fun filterMedia(
        media: List<MediaItem>,
        query: String,
        types: Set<MediaType>,
        excluded: Set<String>? = null
    ): List<MediaItem> {
        val nonExcluded = if (excluded != null) {
            media.filter { !excluded.contains(it.folderPath) }
        } else {
            media
        }
        val typeFiltered = nonExcluded.filter { types.contains(it.type) }
        return if (query.isNotEmpty()) {
            typeFiltered.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            typeFiltered
        }
    }

    fun sortMedia(media: List<MediaItem>, sort: SortType, order: SortOrder): List<MediaItem> {
        val baseSorted = when (sort) {
            SortType.NAME -> media.sortedBy { it.name }
            SortType.LAST_MODIFIED -> media.sortedBy { it.dateModified }
            SortType.DATE_TAKEN -> media.sortedBy { it.dateAdded }
            SortType.RANDOM -> media.shuffled()
            else -> media.sortedBy { it.dateAdded }
        }

        return if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
            baseSorted.reversed()
        } else {
            baseSorted
        }
    }

    fun groupMedia(items: List<MediaItem>, type: GroupByType, order: SortOrder): Map<String, List<MediaItem>> {
        if (type == GroupByType.NONE) return mapOf("" to items)

        val sortedItems = when (type) {
            GroupByType.DATE_TAKEN_DAILY, GroupByType.DATE_TAKEN_MONTHLY -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.dateAdded }
                else items.sortedBy { it.dateAdded }
            }
            GroupByType.LAST_MODIFIED_DAILY, GroupByType.LAST_MODIFIED_MONTHLY -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.dateModified }
                else items.sortedBy { it.dateModified }
            }
            GroupByType.FILE_TYPE -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.type.name }
                else items.sortedBy { it.type.name }
            }
            GroupByType.NONE -> items
        }

        return when (type) {
            GroupByType.DATE_TAKEN_DAILY, GroupByType.LAST_MODIFIED_DAILY -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = sdf.format(Date())
                val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
                
                sortedItems.groupBy { item ->
                    val date = if (type == GroupByType.DATE_TAKEN_DAILY) item.dateAdded else item.dateModified
                    val dateStr = sdf.format(Date(date * 1000))
                    when (dateStr) {
                        today -> "Today"
                        yesterday -> "Yesterday"
                        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(date * 1000))
                    }
                }
            }
            GroupByType.DATE_TAKEN_MONTHLY, GroupByType.LAST_MODIFIED_MONTHLY -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sortedItems.groupBy { item ->
                    val date = if (type == GroupByType.DATE_TAKEN_MONTHLY) item.dateAdded else item.dateModified
                    sdf.format(Date(date * 1000))
                }
            }
            GroupByType.FILE_TYPE -> {
                sortedItems.groupBy {
                    it.type.name.lowercase().replaceFirstChar { char -> char.uppercase() } + "s"
                }
            }
            else -> mapOf("" to sortedItems)
        }
    }

    fun absoluteToRelativePath(absolutePath: String, rootPath: String): String? {
        if (!absolutePath.startsWith(rootPath)) return null
        
        var relative = absolutePath.removePrefix(rootPath).trimStart('/')
        if (relative.isNotEmpty() && !relative.endsWith('/')) {
            relative += '/'
        }
        return relative
    }
}
