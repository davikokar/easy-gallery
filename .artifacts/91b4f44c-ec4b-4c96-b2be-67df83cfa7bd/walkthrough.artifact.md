# Walkthrough - Folder Selection Scroll Reset Fix

I have fixed the regression where the scroll position would reset to the top when entering selection mode in the folder gallery.

## Changes Made

### MainActivity Navigation Refactor
I refactored the navigation logic in `MainActivity.kt` to stabilize the composition branch for the gallery and folder detail screens.
- **Before**: `MainActivity` switched between different `if-else` branches depending on `isSelectionMode` or `isMediaSelectionMode`. This caused the entire screen to be disposed and recreated, losing the internal scroll state of the lists/grids.
- **After**: `MainActivity` now uses a stable navigation structure. It decides which screen to show based on `selectedMedia`, `isSettingsMode`, and `selectedFolder`, but it **no longer** branches on selection modes. Selection modes are now handled internally by `FolderListScreen` and `FolderDetailScreen`, allowing the scroll state to be preserved.

### UI Improvements
- **Stable Item Keys**: Added stable keys (`key = { it.path }`) to the `LazyColumn` in `FolderListScreen.kt`. This ensures that items are correctly tracked by Compose even if the list contents change slightly, contributing to better scroll stability.

## Verification Results

### Automated Tests
I updated the regression tests in `FolderListContentTest.kt` to match the new stable navigation pattern and verified that they now pass:
- `scrollPositionIsMaintainedWhenEnteringSelectionMode`: **PASSED**
- `scrollPositionIsMaintainedInListViewWhenEnteringSelectionMode`: **PASSED**
- All other 5 tests in the suite also **PASSED**.

### Acceptance Criteria Check
- [x] Scroll down in folder grid view.
- [x] Long-press a folder tile -> Tile becomes selected.
- [x] Screen **does not** jump to the top.
- [x] Selected tile remains visible.
- [x] Exit selection mode -> Scroll position is preserved.
- [x] Verified for both Grid and List views.
