# Implementation Plan - Squared Tiles and 1-Pixel Grid

This plan outlines the steps to change the visual appearance of folder tiles and image thumbnails to be squared (no rounded corners) and separated by a minimal 1-pixel grid.

## Proposed Changes

### UI Components

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- **FolderGrid**:
    - Set `horizontalArrangement = Arrangement.spacedBy(1.dp)`.
    - Set `verticalArrangement = Arrangement.spacedBy(1.dp)`.
    - Remove `contentPadding` (or set to `0.dp`).
- **FolderGridItem**:
    - Remove `padding(8.dp)`.
    - Remove `clip(RoundedCornerShape(8.dp))`.
    - Use `RectangleShape` for the card.

#### [MODIFY] [PhotoItem.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/PhotoItem.kt)
- **PhotoItem**:
    - Remove `padding(4.dp)`.
    - Remove `clip(RoundedCornerShape(4.dp))`.
    - Use `RectangleShape` for the card.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- **LazyVerticalGrid**:
    - Set `horizontalArrangement = Arrangement.spacedBy(1.dp)`.
    - Set `verticalArrangement = Arrangement.spacedBy(1.dp)`.
    - Remove `contentPadding`.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- **LazyVerticalGrid**:
    - Set `horizontalArrangement = Arrangement.spacedBy(1.dp)`.
    - Set `verticalArrangement = Arrangement.spacedBy(1.dp)`.
    - Remove `contentPadding`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Gallery Tiles**:
    - Verify they are perfectly squared (no rounded corners).
    - Verify they touch each other with only a 1-pixel gap.
2.  **Image Thumbnails**:
    - Verify they are perfectly squared.
    - Verify they are separated by a 1-pixel gap.
3.  **Background**:
    - Verify the background (and gap color) is the dark blue-grey (RGB 5, 1, 31).
