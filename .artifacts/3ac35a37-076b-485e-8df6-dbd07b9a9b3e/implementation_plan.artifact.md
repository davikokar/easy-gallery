# Implementation Plan - Folder-based Gallery View

This plan outlines the steps to implement a gallery view that organizes photos by their containing folders. The app will display a grid of folders, each showing a thumbnail, the folder name, and the count of images it contains.

## User Review Required

> [!IMPORTANT]
> - The app will require storage permissions (`READ_MEDIA_IMAGES` for Android 13+ and `READ_EXTERNAL_STORAGE` for older versions). The user will be prompted to grant these permissions on startup.
> - I will add **Coil** for efficient image loading in Jetpack Compose.

## Proposed Changes

### Configuration & Dependencies

#### [MODIFY] [AndroidManifest.xml](file:///C:/git/easy-gallery/app/src/main/AndroidManifest.xml)
- Add required storage permissions.

#### [MODIFY] [libs.versions.toml](file:///C:/git/easy-gallery/gradle/libs.versions.toml)
- Add Coil dependency.

#### [MODIFY] [build.gradle.kts](file:///C:/git/easy-gallery/app/build.gradle.kts)
- Include Coil implementation.

---

### Data Layer

#### [NEW] [Folder.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/Folder.kt)
- Define a data class to represent a photo folder:
    - `name: String`
    - `imageCount: Int`
    - `thumbnailUri: Uri`

#### [NEW] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Implement logic to query `MediaStore.Images` and group results by bucket (folder).

---

### UI Layer

#### [NEW] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Manage the list of folders and handle data loading state.

#### [NEW] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Implement the grid UI using `LazyVerticalGrid`.
- Create a `FolderItem` composable for the square folder representation.

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Handle runtime permissions.
- Initialize the `GalleryViewModel` and display `FolderListScreen`.

## Verification Plan

### Automated Tests
- I will verify the build after adding dependencies.
- (Optional) I can add a unit test for the grouping logic if time permits.

### Manual Verification
- Deploy the app to a device/emulator with photos.
- Verify that permissions are requested.
- Verify that folders are displayed correctly in a grid with thumbnails, names, and counts.
