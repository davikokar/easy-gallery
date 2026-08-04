# Fix Bug 1: Inconsistent excluded folder handling (Path vs Name)

The app currently mixes folder names and folder paths when handling excluded galleries. This leads to issues where folders cannot be reliably un-excluded from the "Manage Excluded" screen.

## User Review Required

> [!IMPORTANT]
> - All `GalleryViewModel` methods related to exclusion will now consistently use `folderPath` instead of `folderName`.
> - The UI in "Manage Excluded" will show both the folder name and its path for better clarity.

## Proposed Changes

### [GalleryViewModel](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Rename `unexcludeFolder(folderName: String)` to `unexcludeFolder(folderPath: String)`.
- Rename `excludeFolder(folderName: String)` to `excludeFolder(folderPath: String)`.
- Ensure internal logic uses paths consistently.

### [ManageExcludedScreen](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/ManageExcludedScreen.kt)

#### [MODIFY] [ManageExcludedScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/ManageExcludedScreen.kt)
- Update `AddExcludedFolderDialog` to call `viewModel.excludeFolder(it.path)` instead of `it.name`.
- Update `ManageExcludedScreen` to display both name and path if possible, or at least use the path for removal logic.
- Since `excludedFolders` only stores paths (strings), we might need to map them back to names for display or just show the path. I will update `ExcludedFolderItem` to show the full path or name derived from path.

## Verification Plan

### Automated Tests
- Create a new test case in `GalleryViewModelTest.kt` (or a new test file) that:
    1. Excludes a folder by path.
    2. Verifies it's in `excludedFolders`.
    3. Verifies `filteredFolders` excludes it.
    4. Un-excludes it by path.
    5. Verifies it's back in `filteredFolders`.
- Add a test for adding via name (simulating the bug) and verify it fails or is fixed.

### Manual Verification
1. Open the app.
2. Long-press a folder and select "Exclude".
3. Verify it disappears.
4. Go to Settings > Manage Excluded.
5. Verify the folder is listed.
6. Remove it.
7. Verify it reappears in the gallery.
8. Try adding an excluded folder via the "Add" button in Manage Excluded screen and repeat verification.
