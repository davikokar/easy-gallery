# Implementation Plan - Full-Screen Image Viewer

This plan outlines the steps to implement a full-screen image viewer with interactive controls (delete, share, rotate) and an immersive mode.

## User Review Required

> [!IMPORTANT]
> - **Immersive Mode**: Toggled by tapping the image while in full-screen view. It hides all UI elements (buttons and top bars).
> - **Rotation**: The rotation will be visual-only in this implementation (not saved to the file).
> - **Deletion**: Following the pattern used for folders, deletion will be simulated by removing the image from the current UI state.
> - **Sharing**: Will use the standard Android Share Sheet.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State**:
    - `selectedPhoto: StateFlow<Photo?>`
    - `isImmersiveMode: StateFlow<Boolean>`
    - `currentRotation: StateFlow<Float>`
- **Functions**:
    - `selectPhoto(photo: Photo)`: Sets the photo and resets immersive mode/rotation.
    - `closePhoto()`: Clears the selected photo.
    - `toggleImmersiveMode()`: Toggles UI visibility.
    - `rotatePhoto()`: Increments rotation by 90 degrees.
    - `deletePhoto(photo: Photo)`: Removes the photo from the current folder/all-photos list.
    - `sharePhoto(photo: Photo)`: (Triggers an event or handled in UI).

#### [NEW] [FullImageScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt)
- Displays the image using `AsyncImage` with dynamic rotation.
- Toggles immersive mode on tap.
- Shows a bottom action bar when not immersive:
    - **Bin Icon**: Triggers delete (with confirmation).
    - **Share Icon**: Triggers share.
    - **Rotate Icon**: Triggers 90° rotation.

#### [MODIFY] [PhotoItem.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/PhotoItem.kt)
- Add an `onClick: () -> Unit` parameter to the `PhotoItem` composable.
- Wrap the content in a `clickable` modifier.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt) & [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Pass `viewModel::selectPhoto` to the `PhotoItem` click listener.

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Observe `selectedPhoto`.
- Display `FullImageScreen` if a photo is selected.
- Update `BackHandler` to call `viewModel.closePhoto()` when viewing an image.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Open Image**: Tap any photo thumbnail in folder detail or timeline -> Image opens full-screen.
2.  **Controls**: Verify Bin, Share, and Rotate icons are visible at the bottom.
3.  **Rotation**: Tap rotate -> Image rotates by 90°.
4.  **Immersive Mode**: Tap the image -> Buttons disappear. Tap again -> Buttons reappear.
5.  **Back Navigation**: Press system back or top-left back (if added) -> Returns to the previous grid view.
6.  **Deletion**: Tap Bin -> Confirm -> Returns to grid and the photo is gone.
