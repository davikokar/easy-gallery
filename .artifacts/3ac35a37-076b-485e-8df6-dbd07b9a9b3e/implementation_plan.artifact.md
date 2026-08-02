# Implementation Plan - Accurate Filtered Folder Counts

This plan outlines the steps to ensure that folder/gallery tile counts accurately reflect the currently applied media type filters (Images, Videos, GIFs).

## User Review Required

> [!IMPORTANT]
> - **Reactive Counts**: Folder counts will now update in real-time as you toggle filters in the "Filter media" menu.
> - **Empty Folder Visibility**: Folders that contain no media of the selected types will be automatically hidden from the gallery view to keep your interface clean.
> - **Metadata Sync**: Folder size and date information will also be recomputed based only on the visible (filtered) items.

## Proposed Changes

### Logic Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **Refactor `filteredFolders`**:
    - Add `_allMedia` and `_selectedMediaTypes` to the `combine` dependencies.
    - For each folder in the current state:
        1. Filter `_allMedia` to find items where `item.folderPath == folder.path` AND `selectedMediaTypes.contains(item.type)`.
        2. If no items match, mark the folder for removal (unless special modes like "show excluded" are active).
        3. Update the folder's `imageCount`, `size`, `dateModified`, and `dateTaken` based on the matching items.
    - Re-apply sorting and pinning logic on the updated folder list.
- **Update `getSelectedFoldersData`**: Ensure it uses the filtered folders list to provide accurate "Properties" information.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Filter Toggle**:
    - Open the main gallery. Note the count on a folder (e.g., "Camera: 100").
    - Filter out "Videos".
    - Verify the count immediately updates (e.g., "Camera: 85").
    - Re-enable "Videos" and verify the count returns to 100.
2.  **Empty Folder Hiding**:
    - Filter for "Videos" only.
    - Verify that folders containing only photos are now hidden from the main view.
3.  **Properties Dialog**:
    - Select a folder while "Videos" are filtered out.
    - Open "Properties" and verify the "Total files count" matches the visible filtered count.
