# Implementation Plan - Video Thumbnails Support

This plan fixes the issue where folders with only videos and the videos themselves appear without thumbnails. I will enable video frame decoding in Coil to display the first frame of each video as a thumbnail.

## Proposed Changes

### Configuration & Dependencies

#### [MODIFY] [libs.versions.toml](file:///C:/git/easy-gallery/gradle/libs.versions.toml)
- Add `coil-video = { group = "io.coil-kt.coil3", name = "coil-video", version.ref = "coil" }`.

#### [MODIFY] [build.gradle.kts](file:///C:/git/easy-gallery/app/build.gradle.kts)
- Include `libs.coil.video`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/git/easy-gallery/app/src/main/AndroidManifest.xml)
- Add `android:name=".EasyGalleryApp"` to the `<application>` tag.

---

### Application Logic

#### [NEW] [EasyGalleryApp.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/EasyGalleryApp.kt)
- Implement `SingletonImageLoader.Factory`.
- Configure the `ImageLoader` to include:
    - `VideoFrameDecoder.Factory()` for video thumbnails.
    - `AnimatedImageDecoder.Factory()` for GIF animations.

---

### UI Layer

#### [MODIFY] [MediaGridItem.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaGridItem.kt)
- Remove the local `ImageLoader` creation.
- Rely on the singleton `ImageLoader` provided by Coil.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Ensure thumbnails in both Grid and List modes use the system `ImageLoader`. (By default, `AsyncImage` uses the singleton if one is configured).

## Verification Plan

### Automated Tests
- Verify build success after adding the new dependency.

### Manual Verification
1.  **Video Folders**: Verify that folders containing only videos now display a thumbnail from one of the videos.
2.  **Video Items**: Verify that individual video files in the gallery display their first frame as a thumbnail.
3.  **GIFs**: Verify that GIFs still animate correctly.
