# Implementation Plan - Dynamic Grid Animations

This plan outlines the steps to implement smooth, dynamic displacement animations when changing the grid column count (pinching or using the dialog) for both folders and photos.

## Proposed Changes

### UI Layer

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- **FolderGridItem**:
    - Apply `Modifier.animateItem()` (or `animateItemPlacement()` depending on exact Compose version availability) to the `Card` container.
- **FolderGrid**:
    - Ensure `items` in `LazyVerticalGrid` use a **stable key** (e.g., `folder.path`) to allow the animation system to track items across layout changes.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- **MediaGridItem**:
    - Apply `Modifier.animateItem()` to the `Card` container.
- **MediaGrid** & **GroupedMediaContent**:
    - Ensure `items` use a **stable key** (e.g., `item.uri.toString()`).
    - This will allow thumbnails to smoothly glide into their new positions when the column count increases or decreases.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Add stable keys to `items` in both Grid and List modes.
- Apply `Modifier.animateItem()` to items and headers.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Pinch Animation**:
    - Open the main gallery -> Pinch to change columns.
    - Verify that folder tiles smoothly glide to their new positions instead of snapping.
2.  **Photo Grid Animation**:
    - Open a folder -> Pinch to change columns.
    - Verify that thumbnails rearrange with a visible "displacement" movement.
3.  **Dialog Animation**:
    - Change columns via the "Column count" dialog.
    - Verify that the layout transition is animated.
