# Implementation Plan - Fix Scroll Position Bug

This plan outlines the steps to fix the issue where the folder/media list scrolls back to the top when entering or exiting selection mode.

## User Review Required

> [!IMPORTANT]
> - **State Hoisting**: I will explicitly hoist the `LazyGridState` and `LazyListState` to the top level of each screen. This ensures that the scroll position is preserved even when the UI structure (like the TopBar) changes significantly.
> - **Unified Solution**: This fix will be applied to the Folder List, Folder Detail, and Timeline views to ensure a consistent and smooth experience throughout the app.

## Proposed Changes

### UI Layer

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Hoist `gridState` and `listState` using `rememberLazyGridState()` and `rememberLazyListState()` at the top of `FolderListScreen`.
- Pass these states down to `FolderGrid` and `FolderList`.
- Ensure `LazyVerticalGrid` and `LazyColumn` use these explicit states.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Similar to above, hoist and pass scroll states to `MediaGrid`, `MediaList`, and `GroupedMediaContent`.
- This prevents jumping when selecting photos within a folder.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Hoist and apply scroll states for the grouped Timeline view.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Folder Selection**:
    - Scroll down to the middle of the folder grid.
    - Long-press a folder.
    - Verify the grid stays at the same scroll position.
    - Exit selection mode and verify the position is still maintained.
2.  **Photo Selection**:
    - Open a folder with many photos.
    - Scroll down and long-press a photo.
    - Verify the photo remains visible and the grid doesn't jump.
3.  **Timeline Selection**:
    - Scroll down in the Timeline.
    - Long-press a photo.
    - Verify scroll position is preserved.
