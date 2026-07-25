# Implementation Plan - Image Viewer Reliability Fixes

This plan fixes the navigation issues (black screens) and restores the broken zoom functionality in the full-screen image viewer.

## User Review Required

> [!IMPORTANT]
> - **Navigation Fix**: The `HorizontalPager` will now correctly reset its state whenever you open a new collection of photos, preventing "black screen" or index out-of-sync issues.
> - **Zoom Fix**: The gesture detection logic will be decoupled from the scale state to ensure smooth, uninterrupted zooming and panning.
> - **State Reset**: When swiping between photos, the zoom level and position will automatically reset to normal for the next image.

## Proposed Changes

### UI Layer

#### [MODIFY] [ZoomableImage.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/ZoomableImage.kt)
- **Fix Zoom**: Move `scale` and `offset` logic into a single `pointerInput` block that does **not** use `scale` as a key. This prevents the gesture loop from being cancelled during a zoom operation.
- **Gesture Synchronization**: Maintain the logic to only consume horizontal swipes when zoomed in, allowing them to pass to the Pager when at 1x scale.
- **State Persistence**: Ensure the component resets its internal scale/offset if the `uri` changes (to prevent a new image from appearing zoomed in).

#### [MODIFY] [FullImageScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt)
- **Fix Navigation**:
    - Use `key(photosList) { ... }` around the pager or properly handle `pagerState` re-initialization.
    - Alternatively, use `LaunchedEffect(photo)` to call `pagerState.scrollToPage()` when the initial photo is set, ensuring the pager starts at the correct position.
- **Sync State**: Ensure the ViewModel is notified of the current photo as the user swipes.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Zooming**:
    - Open an image -> Pinch to zoom -> Verify it works smoothly and doesn't get "stuck".
    - Double-tap -> Verify it zooms in/out reliably.
2.  **Navigation**:
    - Swipe left/right -> Verify you can browse all photos without seeing black screens.
    - Delete a photo -> Verify the viewer moves to the next photo or closes correctly without error.
3.  **Rotation & Fit**:
    - Rotate image -> Verify it still fits the screen perfectly after the gesture fixes.
