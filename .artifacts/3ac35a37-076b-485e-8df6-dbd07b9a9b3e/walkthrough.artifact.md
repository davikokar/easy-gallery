# Walkthrough - Full-Screen Image Viewer

I have implemented a full-screen image viewer that allows you to view your photos in detail, perform basic edits, and toggle an immersive mode.

## Changes Made

### Logic & State
- **Photo Selection**: Added `selectedPhoto` state to `GalleryViewModel` to track which image is currently being viewed.
- **Immersive Mode**: Implemented `isImmersiveMode` to toggle UI visibility (top and bottom bars).
- **Rotation State**: Added `currentRotation` to allow 90° visual rotation of the displayed image.
- **Deletion Logic**: Added a `deletePhoto` function that removes the photo from the current UI state.

### UI Components
- **FullImageScreen**: A new immersive screen for viewing a single photo.
    - **Top Bar**: Displays the photo name and a back button.
    - **Bottom Action Bar**: Contains buttons for **Delete**, **Share**, and **Rotate**.
    - **Immersive Toggle**: Tapping the image hides or shows the bars for an unobstructed view.
    - **Smooth Transitions**: Used animations (`fadeIn`/`fadeOut` and `slideIn`/`slideOut`) for the top and bottom bars.
- **PhotoItem Integration**: Updated all photo thumbnails (in both folder and calendar views) to be clickable, launching the full-screen viewer.
- **Back Button Handling**: Integrated with the system back button to seamlessly return from full-screen to the previous grid view.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Launching Viewer**: Tapping a photo thumbnail in any grid successfully opens it full-screen.
- **Immersive Mode**: Tapping the image successfully toggles the UI bars.
- **Actions**:
    - **Rotate**: Tapping the rotate icon spins the image by 90°.
    - **Share**: Tapping share opens the standard Android share sheet.
    - **Delete**: Tapping the bin icon opens a confirmation dialog. Confirming removes the photo and returns to the grid.
- **Navigation**: The back arrow and system back button correctly close the viewer.

> [!TIP]
> Tapping the photo again when in full screen will hide all the buttons, letting you enjoy the photo without any distractions!
