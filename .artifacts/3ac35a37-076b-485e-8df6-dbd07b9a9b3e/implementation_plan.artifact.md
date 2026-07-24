# Implementation Plan - Folder Detail View

This plan outlines the steps to implement a detail view that shows images within a selected folder, complete with pinch-to-zoom, a back button, and an "Info" toggle to show filenames.

## User Review Required

> [!IMPORTANT]
> - I will use a simple state-based navigation within the `GalleryViewModel` to switch between the folder list and the folder detail view.
> - The "Info" button in the top bar will toggle the visibility of the filename for all photos in the current grid.
> - The pinch-to-zoom column count will be shared between the folder list and the folder detail view for consistency.

## Proposed Changes

### Data Layer

#### [NEW] [Photo.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/Photo.kt)
- Define a data class to represent a photo:
    - `uri: Uri`
    - `name: String`

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Add `getPhotosInFolder(bucketName: String): List<Photo>` to fetch images for a specific folder.

---

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add `selectedFolder` StateFlow.
- Add `photosInFolder` StateFlow.
- Add `showInfo` StateFlow (Boolean) to toggle filename overlays.
- Add `selectFolder(folder: Folder)` and `backToFolders()` functions.
- Add `toggleInfo()` function.

#### [NEW] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Implement a screen showing a grid of photos using `LazyVerticalGrid`.
- Support pinch-to-zoom (reusing logic or sharing `columnsCount`).
- Add TopBar with:
    - Back arrow button.
    - Folder name title.
    - Info (eye or info icon) button.
- Create `PhotoItem` composable that conditionally shows the filename overlay.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add click listener to `FolderItem` to call `viewModel.selectFolder(folder)`.

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Update the UI to observe the `selectedFolder` and switch between `FolderListScreen` and `FolderDetailScreen`.
- Handle the system back button to return to the folder list if a folder is selected.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
- Tap on a folder and verify it opens the detail view.
- Verify the top bar shows the folder name, back button, and info button.
- Tap "Info" and verify filenames appear/disappear on the photos.
- Pinch-to-zoom in the photo grid.
- Press the back button (top bar or system back) and verify it returns to the gallery view.
