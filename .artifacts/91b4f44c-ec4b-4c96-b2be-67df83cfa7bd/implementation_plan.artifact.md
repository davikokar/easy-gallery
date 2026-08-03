# Fix Scroll Reset Regression in Folder Selection

The goal is to prevent the scroll position from resetting when entering or exiting selection mode in the folder list/grid. This is achieved by ensuring `FolderListScreen` is rendered from a stable composition call site in `MainActivity` and ensuring stable item keys are used in the lists.

## Proposed Changes

### [MainActivity](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Refactor the navigation logic in `setContent` to avoid branch switching when `isSelectionMode` or `isMediaSelectionMode` toggles while the user is on the folder list or folder detail screens.
- Consolidate the `FolderListScreen` and `FolderDetailScreen` calls so they stay in the same composition branch regardless of selection mode.

### [Folder UI](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add stable item keys to `LazyColumn` in `FolderList` using `key = { it.path }`.
- (Optional but good practice) Consider if `gridState` and `listState` need to be hoisted, but keeping them in `FolderListContent` is safe as long as it's not disposed.

## Verification Plan

### Automated Tests
- Run the regression tests in `FolderListContentTest.kt`:
    - `scrollPositionIsMaintainedWhenEnteringSelectionModeWithBranching`
    - `scrollPositionIsMaintainedInListViewWhenEnteringSelectionModeWithBranching`
- **Expected Result**: Tests should now PASS.

### Manual Verification
- Deploy the app to a device/emulator.
- Scroll down in the folder grid.
- Long-press a folder.
- Verify the scroll position remains the same and the folder is selected.
- Repeat for the list view.
