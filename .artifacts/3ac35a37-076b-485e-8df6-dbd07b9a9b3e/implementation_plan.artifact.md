# Implementation Plan - Exclude Folders and Selection Menu Cleanup

This plan outlines the steps to implement the "Exclude" functionality for gallery folders and cleanup the multiselection menu as requested.

## User Review Required

> [!IMPORTANT]
> - **Exclusion**: Excluded folders will be hidden from all gallery views (Folder List, Timeline).
> - **Management**: Users can restore excluded folders via a new "Manage Excluded" screen (to be added in a later step, for now they are hidden permanently for the session).
> - **Confirmation**: A specific dialog will explain how to re-include folders before the user confirms the exclusion.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - Add `excludedFolders: StateFlow<Set<String>>` to track hidden folder names.
- **Logic**:
    - Update `filteredFolders` to exclude any folder whose name is in the `excludedFolders` set.
    - Update `groupedPhotosByDate` (filteredAllMedia) to exclude photos belonging to excluded folders (requires passing folder name/bucket to `MediaItem`).
    - Add `excludeSelected()` function to add selected folders to the excluded set.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- **Selection Mode**:
    - Pass `onExclude` callback to `SelectionTopBar`.
- **Confirmation Dialog**:
    - Add `showExcludeDialog` state.
    - Implement `ExcludeConfirmationDialog`:
        - Explain that folders will be hidden.
        - Mention "Settings > Manage Excluded" for restoration.

#### [MODIFY] [SelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt)
- Add "Info" icon to the left of the Pin icon.
- Ensure the 3-dotted menu is on the far right.
- Fully wire the menu items: Rename, Copy to, Move to, Exclude, Select all.

#### [MODIFY] [MediaItem.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaItem.kt)
- Add `bucketName: String` to the data class so we can filter them out in Timeline mode if their parent folder is excluded.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Exclusion**:
    - Select folders -> Tap 3-dots -> Exclude.
    - Verify the confirmation dialog explains how to restore.
    - Confirm -> Verify folders disappear from the main list.
2.  **Timeline Sync**:
    - Verify that after excluding a folder, its photos also disappear from the chronological Timeline view.
3.  **Menu Position**:
    - Verify Selection Bar has: Back, Counter, Info, Pin, Delete, 3-dots (in that order or similar).
