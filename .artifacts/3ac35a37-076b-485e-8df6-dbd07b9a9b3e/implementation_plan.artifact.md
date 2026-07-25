# Implementation Plan - Grid Zoom and Pager Reliability Fixes

This plan addresses the broken pinch-to-zoom functionality in the gallery/photo grids and further improves the stability of the full-screen image navigation.

## User Review Required

> [!IMPORTANT]
> - **Grid Zoom**: I will move the gesture detection directly onto the grid components and use a more robust detection method that won't conflict with scrolling.
> - **Pager Stability**: I will ensure the `HorizontalPager` state is properly reset when the underlying photo collection changes (e.g., due to search or folder switching), preventing the "black screen" issue.

## Proposed Changes

### UI Layer

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt) & [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- **Gesture Refactor**:
    - Move the `detectTransformGestures` logic into a reusable `Modifier` extension or directly onto the `LazyVerticalGrid`.
    - Use `detectTransformGestures(passThrough = true)` logic (simulated via `awaitPointerEventScope`) if possible, or ensure it doesn't consume all events so scrolling remains smooth.
    - Specifically, only consume events when two or more pointers are active (multi-touch).

#### [MODIFY] [FullImageScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt)
- **Pager Fix**:
    - Use `key(photosList)` to force a complete re-initialization of the pager state when the photo collection changes. This is the most reliable way to prevent index mismatches that lead to black screens.
    - Double-check the `initialPage` logic to ensure it always lands on the correct photo even after a collection update.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Grid Zoom**:
    - Pinch-to-zoom in Folder Grid -> Columns should change (1-20).
    - Pinch-to-zoom in Photo Grid -> Columns should change.
    - Verify that vertical scrolling still works perfectly.
2.  **Pager Navigation**:
    - Open photo -> Swipe left/right multiple times.
    - Change search query or folder -> Open photo -> Verify it starts on the right image and swiping works without showing black screens.
