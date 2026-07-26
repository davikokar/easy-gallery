# Implementation Plan - Filter Media by Type

This plan outlines the steps to implement a "Filter media" functionality, allowing users to show or hide Images, Videos, and GIFs. The gallery will dynamically update folder counts and hide empty folders based on the selected filters.

## User Review Required

> [!IMPORTANT]
> - **Dynamic Folder Counts**: Folder counts will now be recalculated in real-time based on the active media filters.
> - **Automatic Hiding**: Folders that contain no media items of the selected types will be hidden from the gallery.
> - **Unified Filtering**: These filters will apply to all views: Folder List, Timeline, and Folder Details.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - Add `selectedMediaTypes: StateFlow<Set<MediaType>>` (default: all types).
- **Logic**:
    - Update `filteredAllMedia` to filter by `selectedMediaTypes`.
    - Update `filteredMedia` to filter by `selectedMediaTypes`.
    - **Refactor `filteredFolders`**: Instead of using the pre-fetched `_uiState.folders`, calculate the folder list dynamically from `_allMedia` and the active filters (Search, Pinned, Excluded, Media Type). This ensures folder counts are always accurate.
- **Functions**:
    - `toggleMediaType(type: MediaType)`.

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- Add `onFilterMediaClick: (() -> Unit)?` callback.
- Connect the "Filter media" menu item to this callback.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add `showFilterDialog` state.
- Implement `FilterMediaDialog` composable:
    - Lists "Images", "Videos", and "GIFs" with checkboxes.
    - Updates ViewModel state on change.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Filter Application**:
    - Open menu -> Filter media.
    - Uncheck "Videos" -> Verify all video thumbnails disappear from the timeline and folder details.
2.  **Folder Updates**:
    - Verify that folder counts update when a type is filtered out.
    - Verify that folders containing only "Videos" disappear if "Videos" is unchecked.
3.  **Search & Zoom**:
    - Verify that filtering works correctly in combination with search and pinch-to-zoom.
