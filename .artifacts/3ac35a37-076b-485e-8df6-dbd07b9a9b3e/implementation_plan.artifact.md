# Implementation Plan - Independent Grid Densities

This plan outlines the steps to decouple the column counts for the folder grid and the picture grids (Folder Detail and Timeline), allowing users to set independent densities for each.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - Rename `_columnsCount` to `_folderColumns` (default: 2).
    - Add `_pictureColumns` (default: 3 or 2).
- **Functions**:
    - Update `increaseColumns()`, `decreaseColumns()`, and `setColumnsCount(count)` to accept a `forPictures: Boolean` parameter.
    - Logic: Update the corresponding state based on the flag.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Observe both `folderColumns` and `pictureColumns`.
- Main Grid logic:
    - If in `GALLERY` mode: Use `folderColumns` and update `folderColumns` on pinch/dialog.
    - If in `CALENDAR` mode: Use `pictureColumns` and update `pictureColumns` on pinch/dialog.
- Pass appropriate state to `CalendarGrid` and `FolderGrid`.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Observe `pictureColumns`.
- Update the grid and TopBar actions to use/update `pictureColumns`.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Update pinch-to-zoom callbacks to specifically target the `pictureColumns` state in the ViewModel.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Independent Adjustment**:
    - Set Folder Grid to **2 columns**.
    - Open a folder -> Set Picture Grid to **4 columns**.
    - Go back to Folder Grid -> Verify it is still **2 columns**.
    - Enter Timeline mode -> Verify it uses **4 columns** (same as folder content).
2.  **Pinch-to-Zoom**:
    - Verify pinching in the main gallery only affects folder tiles.
    - Verify pinching inside a folder only affects thumbnails.
3.  **Dialog Selection**:
    - Use the "Column count" menu in both views and verify they only update the current context.
