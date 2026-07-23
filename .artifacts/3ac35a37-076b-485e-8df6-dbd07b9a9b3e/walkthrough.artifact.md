# Walkthrough - Folder-based Gallery View

I have implemented the folder-based gallery view as requested. The app now displays photos available on the device organized by their containing folders.

## Changes Made

### Configuration & Dependencies
- **Permissions**: Added `READ_MEDIA_IMAGES` (for Android 13+) and `READ_EXTERNAL_STORAGE` (for older versions) to the `AndroidManifest.xml`.
- **Image Loading**: Integrated **Coil** for efficient image loading in Compose.
- **Kotlin Versioning**: Resolved a Kotlin version mismatch by forcing the stdlib to `2.1.0`.

### Data Layer
- [Folder.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/Folder.kt): Data class to hold folder metadata (name, image count, thumbnail Uri).
- [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt): Logic to query `MediaStore.Images` and group results by "bucket" (folder).

### UI Layer
- [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt): Manages loading state and provides the list of folders.
- [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt): A responsive grid (`LazyVerticalGrid`) displaying folders as squares. Each square shows:
    - A thumbnail image from the folder.
    - The folder name.
    - The number of images in that folder.
- [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt): Handles runtime permissions and hosts the gallery UI.

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug` and the project builds successfully.

### Manual Verification
- The app successfully requests permissions on startup.
- Once granted, it scans the device and displays folders in a beautiful grid.

> [!TIP]
> Each folder "square" has a subtle gradient overlay to ensure the folder name and image count are always legible against any thumbnail.
