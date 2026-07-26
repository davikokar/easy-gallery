# Implementation Plan - Functional Copy and Move Operations

This plan outlines the steps to implement the actual file system operations for "Copy to" and "Move to" folders, replacing the placeholders with functional code and updating the UI as requested.

## User Review Required

> [!IMPORTANT]
> - **File Permissions**: Moving and copying files on modern Android (11+) requires proper storage permissions. This implementation assumes the app has necessary access to the targeted directories (e.g., standard media folders).
> - **UI Change**: The "Select Current" button in the destination picker will be renamed to "OK" for clarity.
> - **Operation Logic**: Selecting "Folder A" and choosing "Destination B" will result in a new folder "Destination B/Folder A" containing the media items.

## Proposed Changes

### UI Layer

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Rename the `confirmButton` text in `DestinationFolderPickerDialog` from "Select Current" to "OK".

### Logic Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Update `performOperationWithPath(path: String)`:
    - Iterate through `getSelectedFoldersData()` to get each folder's **name** and **physical path**.
    - Call the corresponding `dataSource` method for each folder.
    - Trigger a full gallery refresh (`loadFolders()`) after the operation completes.

### Data Layer

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- **Implement `copyFolderContents(sourcePath: String, targetParentPath: String)`**:
    - Create the target subdirectory using the source folder's name.
    - Copy all files from source to target using `FileStreams`.
    - Use `MediaScannerConnection` to notify the system about new files.
- **Implement `moveFolderContents(sourcePath: String, targetParentPath: String)`**:
    - Similar to copy, but uses `File.renameTo()` or `copy + delete`.
    - Notify MediaStore to remove old entries and scan new ones.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Move Operation**:
    - Select a folder -> Choose "Move to" -> Pick a destination -> Tap "OK".
    - Verify the folder is physically moved on the device (using a file manager or the gallery refresh).
2.  **Copy Operation**:
    - Select a folder -> Choose "Copy to" -> Pick a destination -> Tap "OK".
    - Verify a duplicate folder appears in the new location.
3.  **UI Check**:
    - Confirm the button label is now "OK".
