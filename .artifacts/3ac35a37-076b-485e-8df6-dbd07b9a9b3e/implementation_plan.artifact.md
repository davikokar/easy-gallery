# Implementation Plan - UI Refinements and Gesture Fixes

This plan addresses the duplicate info icon, the grid background color requirements, and restores the broken pinch-to-zoom functionality in the grids.

## Proposed Changes

### UI Components

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- **Remove Duplicate**: Remove the second `actions?.invoke(this)` call that was causing the redundant info icon.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt), [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt), [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- **Background Color**:
    - Explicitly set `Modifier.background(BottomGrey)` on the `Box` containing the grids.
    - Set `colors = CardDefaults.cardColors(containerColor = BottomGrey)` for all folder and photo tiles to ensure they match the requested dark blue-grey color (rgb 5, 1, 31).
- **Restore Zoom**:
    - Re-implement the pinch-to-zoom logic using `detectTransformGestures` on the container `Box` instead of the `LazyVerticalGrid`.
    - To prevent scrolling interference, the gesture listener will only update the column count when a significant zoom change is detected.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Top Bar**: Verify only one "Info" icon appears when inside a gallery, and the 3-dotted menu is on the far right.
2.  **Background**: Verify the entire grid area and the tiles themselves have the dark blue-grey color (rgb 5, 1, 31).
3.  **Grid Zoom**: Verify pinch-to-zoom works in Folder List, Folder Detail, and Timeline views, while vertical scrolling remains functional.
