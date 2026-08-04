# Implementation Plan - Fix Move-Refresh Bug

The goal is to fix the bug where the folder view does not immediately refresh after moving or deleting media items.

## Proposed Changes

### [Gallery UI] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Update `loadFolders()` to also refresh `_mediaInFolder` if a folder is currently selected (`_selectedFolder.value != null`).
- This ensures that any operation triggering `loadFolders()` (move, delete, rotate, copy) will also sync the currently open folder's content.
- Optionally, update `_selectedFolder` itself if the corresponding folder data in the repository has changed (e.g., `imageCount`).

## Verification Plan

### Automated Tests
- Run the unit regression test:
    - `com.davide.seddio.easygallery.ui.GalleryViewModelTest > moved media disappears from source folder immediately`
- **Expected Result**: The test should now PASS.

### Manual Verification
- Open a folder in the app.
- Move a picture to another folder.
- Verify the picture disappears from the current view immediately.
- Delete a picture and verify it disappears immediately.
