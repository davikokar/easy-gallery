# Walkthrough - Video Thumbnails and Enhanced Decoding

I have enabled video frame decoding, ensuring that all your videos and video-only folders now feature high-quality thumbnails.

## Changes Made

### Multimedia Support
- **Video Thumbnails**: Integrated the `coil-video` extension. The app now automatically extracts the first frame of every video to use as its thumbnail. This fixes the issue where videos and certain folders appeared empty.
- **Unified Decoding**: Created a custom `Application` class ([EasyGalleryApp.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/EasyGalleryApp.kt)) to configure a global `ImageLoader`. This shared loader handles:
    - **Video frames** for thumbnails.
    - **GIF animations** for a dynamic grid experience.

### UI Improvements
- **Optimized Loading**: Refactored [MediaGridItem.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaGridItem.kt) to use the global singleton `ImageLoader`, reducing memory overhead and ensuring consistent decoding across the app.
- **Format Labeling**: Videos in the grid continue to show their duration and a play icon, now overlayed on a clear video-frame thumbnail.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Video Folders**: Confirmed that folders containing only videos now correctly display a video frame as their cover image.
- **Video Items**: Verified that every video in the grid now shows its first frame instead of a blank box.
- **GIFs**: Confirmed that animated GIFs still play correctly in the grid and full-screen view.

> [!TIP]
> The app now uses a centralized image loading system, which makes it much faster and more efficient when scrolling through large collections of mixed media!
