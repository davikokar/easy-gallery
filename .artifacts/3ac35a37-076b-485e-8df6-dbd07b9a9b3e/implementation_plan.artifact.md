# Implementation Plan - Search Functionality

This plan outlines the steps to add search functionality to the Easy Gallery app. Users will be able to search for folders in the main view and photos within a folder detail view.

## User Review Required

> [!IMPORTANT]
> - The TopBar title "Easy Gallery" will be left-aligned.
> - Search is case-insensitive and matches if the name contains the search string.
> - When search is active, the title is replaced by a back arrow and a text field.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add state for `searchQuery` (String) and `isSearchActive` (Boolean).
- Update `uiState` to provide filtered folders based on `searchQuery`.
- Update `photosInFolder` to be filtered based on `searchQuery`.
- Add functions:
    - `setSearchQuery(query: String)`
    - `setSearchActive(active: Boolean)` - which also clears the query when deactivated.

#### [NEW] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- Create a reusable Composable for the TopBar that handles:
    - Default state: Left-aligned title + Search icon on the right.
    - Search state: Back arrow + `TextField` for entering search query.
    - Info icon (for detail screen) should still be visible or handled appropriately.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Use `TopAppBar` instead of `CenterAlignedTopAppBar` for left alignment.
- Implement the search UI logic in the TopBar.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Use `TopAppBar` instead of `CenterAlignedTopAppBar`.
- Implement the search UI logic in the TopBar, preserving the "Info" and "Back" functionality.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Folder List**:
    - Verify "Easy Gallery" is left-aligned.
    - Click search icon -> Title disappears, TextField + Back arrow appear.
    - Type text -> Folders are filtered by name (case-insensitive).
    - Click back arrow -> Search is cancelled, query cleared, normal view restored.
2.  **Folder Detail**:
    - Click search icon -> Title (folder name) disappears, TextField + Back arrow appear.
    - Type text -> Photos are filtered by filename.
    - Click back arrow -> Search cancelled.
    - Verify "Info" toggle still works in both normal and search modes (if applicable/desired).
