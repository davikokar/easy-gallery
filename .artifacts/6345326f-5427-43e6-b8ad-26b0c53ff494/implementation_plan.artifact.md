# Fix Info Icon Visibility in Folder Detail Screen

The "Toggle Info" icon in the `FolderDetailScreen` is currently nearly invisible when active because its tint (`MaterialTheme.colorScheme.primary`, which is `BrandBlue`) matches the `SearchTopBar`'s background color (`BrandBlue`).

## Proposed Changes

### [Component Name] UI

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Update the `IconButton` for the "Toggle Info" action.
- When `showInfo` is `true`, apply a `Color.White` background with `CircleShape` and set the icon tint to `BrandBlue`.
- Add necessary imports for `CircleShape` and `BrandBlue`.

## Verification Plan

### Manual Verification
1. Launch the app.
2. Open any folder to see the picture thumbnails (`FolderDetailScreen`).
3. Verify the "Info" icon is visible in the top bar.
4. Tap the "Info" icon to toggle filename overlays.
5. Verify the "Info" icon is clearly visible in its active state (white circular background with blue icon).
6. Verify filenames appear on thumbnails.
7. Return to the main folder list view.
8. Verify NO "Info" button is present in the main folder list view (GALLERY mode).
9. Toggle Timeline (CALENDAR mode) and verify NO "Info" button is present there (as per "no new buttons" rule).
10. Ensure filenames in Timeline still respect the `showInfo` state if it was toggled in a folder view.
