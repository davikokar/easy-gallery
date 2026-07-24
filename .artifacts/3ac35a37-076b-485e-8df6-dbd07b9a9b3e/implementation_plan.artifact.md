# Implementation Plan - Sorting and Overflow Menu

This plan outlines the steps to add an overflow menu to the gallery top bar and implement folder sorting functionality.

## User Review Required

> [!IMPORTANT]
> - Pinned folders will always remain at the top of the list, regardless of the chosen sort order.
> - The "Random" sort will reshuffle the list each time it is selected or when the data refreshes.
> - The menu will include placeholders for: column count, temporarily show excluded, filter media, change view type, and settings. Only "Sort by" will be functional for now.

## Proposed Changes

### Data Layer

#### [MODIFY] [Folder.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/Folder.kt)
- Expand the `Folder` data class to include fields needed for sorting:
    - `path: String`
    - `totalSize: Long`
    - `lastModified: Long`
    - `dateTaken: Long`

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Update the projection in `getFolders()` to fetch:
    - `DATA` (for path)
    - `SIZE`
    - `DATE_MODIFIED`
    - `DATE_TAKEN`
- Aggregate these values when grouping images into folders (e.g., sum sizes, find max date).

---

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add `SortType` enum: `NAME`, `PATH`, `SIZE`, `LAST_MODIFIED`, `DATE_TAKEN`, `RANDOM`.
- Add `currentSortType` StateFlow.
- Update `filteredFolders` Flow to apply sorting based on `currentSortType`, keeping `isPinned` folders at the top.
- Add `setSortType(sortType: SortType)` function.

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- Add an overflow menu (3 dots) to the `actions` section.
- Implement the menu items: Sort by, Column count, etc.
- Only "Sort by" will trigger an action (callback to show the dialog).

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add state to show/hide the `SortDialog`.
- Implement `SortDialog` composable to let the user select a `SortType`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Overflow Menu**:
    - Tap the 3 dots in the top bar.
    - Verify the menu appears with all requested options.
2.  **Sorting**:
    - Tap "Sort by".
    - Select different options (Name, Size, Date, etc.).
    - Verify the folder grid reorders correctly.
    - Verify pinned folders stay at the top during any sort.
3.  **Persistence**:
    - Ensure sorting works even after searching or zooming.
