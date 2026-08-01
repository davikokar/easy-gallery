# Implementation Plan - Consistent Non-Bold Typography

This plan outlines the steps to remove all instances of bold font weight across the entire application to ensure a consistent, clean, and modern look.

## Proposed Changes

### UI Layer

Replace `FontWeight.Bold` with `FontWeight.Normal` in all components.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Update `DateHeader` and `DateHeaderList` to use `FontWeight.Normal`.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Update `GroupHeader` and `GroupHeaderList` to use `FontWeight.Normal`.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- In `PropertiesDialog`, change the folder name weight to `Normal`.
- In `FolderGridItem`, change the folder name weight to `Normal`.

#### [MODIFY] [ColumnCountDialog.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/ColumnCountDialog.kt)
- Change the selected number weight from `Bold` to `Normal`. (It will still be distinguished by the background color).

#### [MODIFY] [MediaPropertiesDialog.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaPropertiesDialog.kt)
- Change the media name weight to `Normal`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
- **Visual Audit**: Navigate through all screens (Gallery, Folder Detail, Timeline, Properties Dialogs, Settings) and verify that no text appears bold.
- **Dialogs**: Open "Sort by", "Column count", and "Move to" to ensure consistency in sub-menus as well.
