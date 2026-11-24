package com.davide.seddio.easygallery.data

import java.text.SimpleDateFormat
import java.util.*

/** What the media index knows about one live item's date taken. Shared by sorting and album rules. */
sealed interface DateTakenSortState {
    data class IndexedWithDate(val epochMillis: Long) : DateTakenSortState
    data object IndexedWithoutDate : DateTakenSortState
    data object NotIndexed : DateTakenSortState
}

/** A date taken for display, sort and grouping; [isCaptureDate] is false when it is the date-added fallback. */
data class ResolvedDateTaken(val epochMillis: Long, val isCaptureDate: Boolean)

object GalleryTransformations {

    fun filterAndSortFolders(
        allMedia: List<MediaItem>,
        query: String,
        pinned: Set<String>,
        sort: SortType,
        order: SortOrder,
        excluded: Set<String>,
        showExcluded: Boolean,
        types: Set<MediaType>,
        thumbnailOverrides: Map<String, String> = emptyMap(),
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>
    ): List<Folder> {
        val foldersMap = mutableMapOf<String, Folder>()
        val resolvedOverrides = mutableMapOf<String, android.net.Uri>()
        allMedia.forEach { item ->
            if (thumbnailOverrides[item.folderPath] == item.uri.toString()) {
                resolvedOverrides[item.folderPath] = item.uri
            }

            val isExcluded = excluded.contains(item.folderPath)
            if ((showExcluded || !isExcluded) && types.contains(item.type)) {
                val dateTakenMillis = resolveDateTaken(item, indexedDateTakenByCanonicalMediaUri).epochMillis
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
                        dateTaken = dateTakenMillis
                    )
                } else {
                    foldersMap[item.folderPath] = existing.copy(
                        imageCount = existing.imageCount + 1,
                        size = existing.size + item.size,
                        dateModified = maxOf(existing.dateModified, item.dateModified),
                        dateTaken = maxOf(existing.dateTaken, dateTakenMillis)
                    )
                }
            }
        }

        val foldersList = foldersMap.values.map { folder ->
            val overrideUri = resolvedOverrides[folder.path]
            if (overrideUri != null) {
                folder.copy(thumbnailUri = overrideUri)
            } else {
                folder
            }
        }

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

    fun sortMedia(
        media: List<MediaItem>,
        sort: SortType,
        order: SortOrder,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>
    ): List<MediaItem> {
        val baseSorted = when (sort) {
            SortType.PATH, SortType.SIZE -> return media.sortedWith(pathOrSizeComparator(sort, order))
            SortType.NAME -> media.sortedBy { it.name }
            SortType.LAST_MODIFIED -> media.sortedBy { it.dateModified }
            SortType.DATE_TAKEN -> media.sortedBy { item ->
                dateTakenEpochMillis(item, indexedDateTakenByCanonicalMediaUri)
            }
            SortType.RANDOM -> media.shuffled()
        }

        return if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
            baseSorted.reversed()
        } else {
            baseSorted
        }
    }

    /**
     * The date taken outside album rules (ADR-0026): the indexed capture date, else MediaStore's, else
     * `dateAdded` with [ResolvedDateTaken.isCaptureDate] false.
     */
    fun resolveDateTaken(
        item: MediaItem,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>
    ): ResolvedDateTaken {
        val state = dateTakenSortState(item, indexedDateTakenByCanonicalMediaUri)
        if (state is DateTakenSortState.IndexedWithDate) return ResolvedDateTaken(state.epochMillis, true)
        val mediaStoreDateTaken = item.mediaStoreDateTakenMillis
        if (mediaStoreDateTaken != null) return ResolvedDateTaken(mediaStoreDateTaken, true)
        return ResolvedDateTaken(item.dateAdded * 1_000L, false)
    }

    /** The instant the Date taken sort uses for one item. */
    fun dateTakenEpochMillis(
        item: MediaItem,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>
    ): Long = resolveDateTaken(item, indexedDateTakenByCanonicalMediaUri).epochMillis

    // Path is folderPath/name, case-sensitive like the folders' Path sort. Only the primary key
    // follows the order; ties always fall back to path then URI so equal keys never swap places.
    private fun pathOrSizeComparator(sort: SortType, order: SortOrder): Comparator<MediaItem> {
        val byPath = compareBy<MediaItem> { "${it.folderPath}/${it.name}" }
        val primary: Comparator<MediaItem> =
            if (sort == SortType.SIZE) compareBy<MediaItem> { it.size } else byPath
        val directed = if (order == SortOrder.DESCENDING) primary.reversed() else primary
        return directed.then(byPath).thenBy { it.uri.toString() }
    }

    private fun dateTakenSortState(
        item: MediaItem,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>
    ): DateTakenSortState {
        // The index projection is keyed by canonical MediaStore URI aliases.
        val mediaUri = canonicalMediaStoreUriForJoin(item.uri.toString())
        if (!indexedDateTakenByCanonicalMediaUri.containsKey(mediaUri)) {
            return DateTakenSortState.NotIndexed
        }

        val indexedDateTaken = indexedDateTakenByCanonicalMediaUri[mediaUri]
        return if (indexedDateTaken != null) {
            DateTakenSortState.IndexedWithDate(indexedDateTaken)
        } else {
            DateTakenSortState.IndexedWithoutDate
        }
    }

    fun groupMedia(
        items: List<MediaItem>,
        type: GroupByType,
        order: SortOrder,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>,
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
        fileTypeLabels: Map<MediaType, String> = emptyMap()
    ): Map<String, List<MediaItem>> {
        if (type == GroupByType.NONE) return mapOf("" to items)

        fun dateTakenMillis(item: MediaItem) =
            resolveDateTaken(item, indexedDateTakenByCanonicalMediaUri).epochMillis

        val sortedItems = when (type) {
            GroupByType.DATE_TAKEN_DAILY, GroupByType.DATE_TAKEN_MONTHLY -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { dateTakenMillis(it) }
                else items.sortedBy { dateTakenMillis(it) }
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
                val longDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault())
                
                sortedItems.groupBy { item ->
                    val dateMillis = if (type == GroupByType.DATE_TAKEN_DAILY) dateTakenMillis(item) else item.dateModified * 1000
                    val dateStr = sdf.format(Date(dateMillis))
                    when (dateStr) {
                        today -> todayLabel
                        yesterday -> yesterdayLabel
                        else -> longDateFormat.format(Date(dateMillis))
                    }
                }
            }
            GroupByType.DATE_TAKEN_MONTHLY, GroupByType.LAST_MODIFIED_MONTHLY -> {
                // Using a localized pattern for month and year
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMM yyyy")
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sortedItems.groupBy { item ->
                    val dateMillis = if (type == GroupByType.DATE_TAKEN_MONTHLY) dateTakenMillis(item) else item.dateModified * 1000
                    sdf.format(Date(dateMillis))
                }
            }
            GroupByType.FILE_TYPE -> {
                sortedItems.groupBy { item ->
                    fileTypeLabels[item.type] ?: (item.type.name.lowercase().replaceFirstChar { char -> char.uppercase() } + "s")
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
