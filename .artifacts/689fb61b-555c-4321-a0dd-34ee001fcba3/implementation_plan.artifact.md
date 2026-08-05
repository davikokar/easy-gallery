# Fix Full-Screen Image Panning Sensitivity

The goal is to fix the over-sensitive panning in `ZoomableImage.kt` when zoomed in. Currently, the translation is multiplied by the scale, which amplifies finger movements.

## Proposed Changes

### [Component] UI Components

#### [MODIFY] [ZoomableImage.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/ZoomableImage.kt)

- Extract the offset calculation into a top-level helper function for better testability and clarity:
  ```kotlin
  fun calculateNewOffset(currentOffset: Offset, pan: Offset, scale: Float): Offset {
      // Sensitivity fix: divide pan by scale to maintain 1:1 finger tracking
      // OR: we can keep accumulating pan and change the graphicsLayer.
      // The user suggested: offset += pan / scale
      return currentOffset + (pan / scale)
  }
  ```
- Update `pointerInput` to use this helper function.
- Update `graphicsLayer` to keep `translationX = offset.x * scale` (if we use the `pan / scale` approach) OR update both to a more stable approach.
- The user's suggestion `offset += pan / scale` is simple and effective. It keeps `offset` as a "normalized" offset.

Wait, if I use `offset += pan / scale`, then `translationX = offset.x * scale` will work correctly and maintain 1:1 tracking.

## Verification Plan

### Automated Tests
- Add a unit test to a new file `ZoomableImageMathTest.kt` (pure Kotlin test) to verify the panning logic.
- Verify that at different scales, the resulting on-screen movement is consistent with the finger movement.

### Manual Verification
- Deploy the app and open an image.
- Zoom in significantly (e.g., 20x-30x).
- Verify that panning feels natural and follows the finger accurately (1:1), not jumping around.
- Verify double-tap reset still works and centers the image.
- Verify rotation still works.
