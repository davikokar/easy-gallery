# Implementation Plan - Enhanced Multiselection Tools

This plan outlines the enhancements to the gallery's multiselection mode, including detailed folder properties and an expanded action menu.

## User Review Required

> [!IMPORTANT]
> - **Properties Calculation**: The app will calculate the total size and file count for all selected folders. For a single folder, it will also display its specific name and path.
> - **Action Menu**: New actions (Rename, Copy to, Move to, Exclude, Select all) will be added to the selection toolbar's overflow menu. Note that for this implementation, these will mostly be UI placeholders or simulate state changes.
> - **Select All**: This will select all folders currently visible in the list (including respect for the current search filter).

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add `getSelectedFoldersData()`: A helper to retrieve the full `Folder` objects for the currently selected names.
- Add `selectAll()`: Selects all folders in the current `filteredFolders` list.
- Add placeholders for `renameSelected()`, `copySelected()`, `moveSelected()`, and `excludeSelected()`.

#### [MODIFY] [SelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt)
- Add an "Info" icon button.
- Add a 3-dot overflow menu containing: Rename, Copy to, Move to, Exclude, and Select all.
- Add callbacks for these new actions.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add `showPropertiesDialog` state.
- Implement `PropertiesDialog` composable:
    - Displays "Items selected".
    - Displays "Content size" (sum of folder sizes in MB).
    - Displays "Total files" (sum of image counts).
    - If single selection: Displays "Name" and "Path".
- Connect `SelectionTopBar` to these new states and functions.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Selection Mode**:
    - Long-press to enter selection.
    - Verify new Info icon and 3-dot menu appear.
2.  **Properties Dialog**:
    - Select multiple folders -> Tap Info -> Verify correct aggregate totals.
    - Select one folder -> Tap Info -> Verify name and path are shown.
3.  **Action Menu**:
    - Tap 3-dots -> Select "Select all" -> Verify all items become selected.
    - Verify other menu items (Rename, etc.) appear in the list.
