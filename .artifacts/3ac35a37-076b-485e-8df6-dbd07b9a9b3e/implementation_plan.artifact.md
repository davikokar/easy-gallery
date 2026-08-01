# Implementation Plan - Functional Media and Folder Move

This plan outlines the steps to implement actual file movement for media items and folders using the MediaStore API, ensuring compatibility with Android 10+ (Scoped Storage) and maintaining UI state consistency.

## User Review Required

> [!IMPORTANT]
> - **Security Dialogs**: Moving files between top-level directories (e.g., from `Pictures` to `Download`) may trigger a system dialog asking for permission. This is a standard Android security feature.
> - **Relative Paths**: MediaStore uses relative paths (e.g., `Pictures/Vacation/`) to organize files. I will implement a reliable absolute-to-relative path converter.
> - **Retry Logic**: Since requesting permission is an asynchronous process involving a system activity, the app will save the "Pending Move" operation and automatically retry it once you grant permission in the system dialog.

## Proposed Changes

### Data Layer

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- **Implement `updateMediaRelativePath(uris: List<Uri>, targetRelativePath: String)`**:
    - Use `ContentResolver.update()` to set the `MediaStore.MediaColumns.RELATIVE_PATH` for each URI.
    - Catch `SecurityException` and propagate it to the ViewModel for permission handling.
- **Implement `getPathFromUri(uri: Uri)`**: Helper to resolve a physical path from a MediaStore URI if needed for legacy logic.

### Logic Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - Add `pendingMoveOperation: MutableStateFlow<MoveOperation?>`.
- **Refactor `performOperationWithPath`**:
    - Convert the target absolute path to a MediaStore-compatible `RELATIVE_PATH`.
    - Collect all affected URIs (either from `selectedMediaItems` or by querying all items in `selectedFolders`).
    - Attempt the move. If a `SecurityException` occurs, store the intent in `pendingWriteRequest` and the details in `pendingMoveOperation`.
- **Implement `onWriteRequestResult(granted: Boolean)`**:
    - If `granted`, automatically re-trigger the saved `pendingMoveOperation`.
    - Clear state after completion.

### UI Layer

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Update `intentSenderLauncher` to call `viewModel.onWriteRequestResult(true)` when `RESULT_OK` is received.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Individual Move**: Select 2 photos -> Move to -> Choose destination -> Tap OK.
    - Verify system dialog (if needed) -> Confirm -> Verify files are moved in storage and UI.
2.  **Folder Move**: Select a gallery folder -> Move to -> Choose destination -> OK.
    - Verify all media within that folder is physically relocated to the new parent directory.
3.  **Cross-Partition**: Attempt moving between internal storage and SD card (if available) to verify fallback logic.
4.  **Cancellation**: Deny system permission -> Verify app clears pending state and items remain in original location.
