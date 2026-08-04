# Walkthrough - Fix Move-Refresh Bug

I have fixed the bug where the folder view would not immediately refresh after moving or deleting media items.

## Changes Made

### GalleryViewModel
- Updated `loadFolders()` in [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt) to also refresh the `_mediaInFolder` state flow if a folder is currently selected.
- This ensures that when `loadFolders()` is called (which happens after operations like Move, Delete, or Rotate), the currently displayed folder's content is also re-queried from the repository and updated in the UI.

### Test Fix
- Fixed the regression test in [GalleryViewModelTest.kt](file:///C:/git/easy-gallery/app/src/test/java/com/davide/seddio/easygallery/ui/GalleryViewModelTest.kt) by using `backgroundScope.launch` for background flow collection. This prevents `UncompletedCoroutinesError` in the test.

## Verification Results

### Automated Tests
I ran the full unit test suite, including the new regression test, and all tests passed:

> [!TIP]
> **Test Result**: `25 passed, 0 skipped, 0 failed`
> The test `moved media disappears from source folder immediately` now passes, confirming that the UI state correctly reflects the moved item's removal from the source folder without requiring a manual refresh.
