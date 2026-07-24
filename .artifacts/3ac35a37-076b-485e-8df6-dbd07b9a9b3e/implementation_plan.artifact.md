# Implementation Plan - Multiselection, Deletion, and Pinning

This plan outlines the steps to add multiselection capabilities to the gallery, allowing users to select multiple folders to delete or pin them to the top of the list.

## User Review Required

> [!IMPORTANT]
> - **Deletion**: Deleting folders from the device is a destructive action. I will implement a confirmation dialog. Note that on Android 10+, deleting files through `MediaStore` may trigger an additional system-level confirmation dialog.
> - **Pinning**: Pinned folders will always appear at the top of the list, regardless of the alphabetical sorting.
> - **Multiselection Mode**: Triggered by a long-press. While active, the normal search and title are replaced by a selection toolbar.

## Proposed Changes

### Data Layer

#### [MODIFY] [Folder.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/Folder.kt)
- Add `isPinned: Boolean = false` to the data class.

---

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - `isSelectionMode: StateFlow<Boolean>`
    - `selectedFolders: StateFlow<Set<String>>` (storing folder names)
    - `pinnedFolders: StateFlow<Set<String>>` (for persistent pinning logic)
- **Logic**:
    - Update `loadFolders` to apply pinning status.
    - Update `filteredFolders` to sort: `isPinned` DESC, then `name` ASC.
    - Add `toggleSelection(folderName: String)`.
    - Add `enterSelectionMode(initialFolder: String)`.
    - Add `exitSelectionMode()`.
    - Add `deleteSelected()` and `pinSelected()`.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- **TopBar**:
    - Add a conditional TopBar for selection mode.
    - Display: Back arrow (exit), "X / Y selected" counter, Bin icon, Pin icon.
- **Grid Interaction**:
    - Update `FolderItem` to support `onLongClick`.
    - Show a tick icon in the top-right corner of selected items.
- **Confirmation Dialog**:
    - Implement a `DeleteConfirmationDialog` that appears when the bin icon is clicked.

#### [NEW] [SelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt)
- Create a reusable component for the multiselection toolbar.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Selection Mode**:
    - Long-press a folder -> Selection mode starts, UI updates.
    - Tap other folders -> Selection count updates, ticks appear/disappear.
    - Tap back arrow -> Mode exits, selection cleared.
2.  **Pinning**:
    - Select folders -> Tap Pin -> Folders move to the top and stay there.
    - Select pinned folders -> Tap Pin -> Folders are unpinned.
3.  **Deletion**:
    - Select folders -> Tap Bin -> Confirmation dialog appears.
    - Confirm -> Folders (and their content) are removed from the list.
