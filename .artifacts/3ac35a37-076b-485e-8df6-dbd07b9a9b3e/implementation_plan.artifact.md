# Implementation Plan - Zoomable Gallery Grid

This plan outlines the steps to add pinch-to-zoom functionality to the gallery grid, allowing users to dynamically change the number of columns by zooming in and out.

## User Review Required

> [!IMPORTANT]
> - Zooming **in** (pinch apart) will decrease the column count (min 1), making items larger.
> - Zooming **out** (pinch together) will increase the column count (max 5-6), making items smaller.
> - The transition will be triggered when the zoom scale crosses a certain threshold to avoid jittery behavior.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add a `columnsCount` StateFlow (defaulting to 2).
- Add functions `zoomIn()` and `zoomOut()` to update the column count within bounds (e.g., 1 to 5).

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Observe `columnsCount` from the ViewModel.
- Wrap the `FolderGrid` (or the grid itself) with a `Modifier.pointerInput` that uses `detectTransformGestures`.
- Track the cumulative scale during a gesture.
- When the scale exceeds a threshold (e.g., > 1.2 or < 0.8), update the column count and reset the scale tracker for the next step.
- Pass the dynamic `columnsCount` to `LazyVerticalGrid`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
- Deploy to a device/emulator.
- Perform pinch-to-zoom gestures on the grid.
- Verify that zooming in reduces columns and zooming out increases them.
- Ensure the limits (min 1, max 5) are respected.
