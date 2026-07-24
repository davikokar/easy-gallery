# Implementation Plan - Change View Type (Grid/List)

This plan outlines the steps to add a "View Type" selection, allowing users to switch between the existing square grid and a new informative list view for their folders.

## User Review Required

> [!IMPORTANT]
> - **View Type Persistence**: For now, the view type will be stored in the ViewModel (memory).
> - **List Layout**:
>   - Left side: A square thumbnail (tile).
>   - Right side (Top line): Folder Name and Image Count.
>   - Right side (Bottom line): Full folder path.
> - This feature specifically applies to the **Folder List** view.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add `ViewType` enum: `GRID`, `LIST`.
- Add `viewType: StateFlow<ViewType>` state.
- Add `setViewType(viewType: ViewType)` function.

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- Add `onViewTypeClick: (() -> Unit)?` callback.
- Connect the "Change view type" menu item to this callback.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add `showViewTypeDialog` state.
- Implement `ViewTypeDialog` composable to let users pick between Grid and List.
- Create `FolderList` composable (using `LazyColumn`).
- Create `FolderListItem` composable:
    - `Row` with `AsyncImage` (fixed size square) on the left.
    - `Column` on the right containing:
        - `Row` with Folder Name (Bold) and Count.
        - Folder Path (smaller, secondary text).
- Update the main `FolderListScreen` logic to switch between `FolderGrid` and `FolderList` based on the ViewModel state.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Menu Integration**:
    - Tap the 3 dots -> "Change view type".
    - Verify the dialog appears.
2.  **Switch to List**:
    - Select "List" -> The folder display should change from squares to a vertical list.
    - Verify thumbnail on the left, details on the right.
3.  **Details in List**:
    - Verify the name, image count, and path are correctly displayed.
4.  **Switch to Grid**:
    - Select "Grid" -> Return to the original square grid layout.
5.  **Functionality**:
    - Verify search and sorting still work correctly in List mode.
    - Verify clicking a list item opens the folder detail.
    - Verify long-pressing enters selection mode.
