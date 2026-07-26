# Implementation Plan - Sort Order (Ascending/Descending)

This plan outlines the steps to add "Ascending" and "Descending" order options to the "Sort by" dialog, with context-aware disabling for the "Random" sort type.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - Add `_folderSortOrder: MutableStateFlow<SortOrder>` (default: `DESCENDING`).
    - Add `_pictureSortOrder: MutableStateFlow<SortOrder>` (default: `DESCENDING`).
- **Logic**:
    - Update `filteredFolders` to apply the `folderSortOrder` to the sorted list.
    - Update `sortMedia` to apply the `pictureSortOrder` to the sorted media list.
- **Functions**:
    - `setSortOrder(order: SortOrder, forPictures: Boolean)`.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- **SortDialog Component**:
    - Add `SortOrder` parameter and `onOrderSelected` callback.
    - Add a `HorizontalDivider` after the primary sort criteria.
    - Add "Ascending" and "Descending" radio buttons.
    - **Logic**: Disable/Dim the order options if the current sort type is `SortType.RANDOM`.
- **Integration**:
    - Observe `folderSortOrder` and `pictureSortOrder`.
    - Pass the correct context-aware order and updates to the `SortDialog`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Contextual Order**:
    - Sort Folders by **Name (Ascending)**.
    - Open a folder -> Sort Pictures by **Date Taken (Descending)**.
    - Verify that returning to the gallery preserves the folder's ascending alphabetical order.
2.  **Random Disable**:
    - Select "Random" in the sort dialog.
    - Verify that "Ascending" and "Descending" options are disabled or unclickable.
3.  **Visuals**:
    - Verify the horizontal line correctly separates criteria from order.
    - Verify the radio buttons are white/themed.
