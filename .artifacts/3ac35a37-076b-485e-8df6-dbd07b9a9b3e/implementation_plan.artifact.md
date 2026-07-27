# Implementation Plan - Correct Scoped Storage Deletion Flow

This plan outlines the steps to implement a robust deletion mechanism for media items and folders on Android 10+ (Scoped Storage), including the necessary system confirmation dialogs.

## User Review Required

> [!IMPORTANT]
> - **Security Dialogs**: You will see a system dialog (e.g., "Allow Easy Gallery to delete this photo?") when deleting items the app doesn't "own". This is a mandatory Android security feature.
> - **Folder Uniqueness**: I will transition folder selection from using names (`bucketName`) to using full physical paths. This prevents issues where folders with the same name in different locations are treated as one.
> - **Permanent Action**: Deletion will physically remove files from your device.

## Proposed Changes

### Logic Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **Selection State**:
    - Change `_selectedFolders` from `Set<String>` (names) to `Set<String>` (paths).
- **Deletion Logic**:
    - Refactor `deleteMedia(item: MediaItem)` to call `performDeletion(listOf(item.uri))`.
    - Update `deleteSelected()` (folders) to collect all `MediaItem` URIs where `folderPath` matches any of the selected folder paths.
    - Update `performDeletion` to ensure `exitMediaSelectionMode()` (or closing the full image) is only done *after* a successful operation or after the system dialog is launched.
- **Cleanup**: Ensure `pendingWriteRequest` is cleared after being handled.

### UI Layer

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- **Result Launcher**: Add `rememberLauncherForActivityResult` using `ActivityResultContracts.StartIntentSenderForResult()`.
- **Observer**: Add a `LaunchedEffect` to observe `viewModel.pendingWriteRequest`.
- **Launch logic**: When a request appears, launch the `IntentSender`. On `RESULT_OK`, trigger `viewModel.loadFolders()` to refresh the UI and clear selection states.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Update selection logic to pass `folder.path` instead of `folder.name`.

### Data Layer

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Ensure `deleteMediaItems` simply executes the delete command, relying on the ViewModel to catch and handle the resulting `SecurityException`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Single File Deletion**: Delete a photo from `FullImageScreen`. Verify the system dialog appears, and the photo is gone from storage and UI after "Allow".
2.  **Batch Deletion**: Select 3 photos -> Delete. Verify one system prompt for 3 items.
3.  **Folder Deletion**: Select a folder -> Delete. Verify all contents are physically removed.
4.  **Unique Folders**: Create two folders named "Vacation" in different directories. Select one -> Delete. Verify only the targeted one is deleted.
