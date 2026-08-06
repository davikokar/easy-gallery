# Walkthrough - Info Icon Visibility Fix

I have improved the visibility of the "Toggle Info" icon in the `FolderDetailScreen` when it is in the active state.

## Changes

### UI Components

#### [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Modified the "Toggle Info" `IconButton` in the `SearchTopBar` actions.
- Added a `Color.White` circular background and switched the icon tint to `BrandBlue` when `showInfo` is `true`.
- This ensures high contrast against the `BrandBlue` top bar background.

## Verification Results

### Automated Tests
- Executed `gradlew app:assembleDebug` and the build passed successfully.

### Manual Verification
> [!TIP]
> To verify the fix:
> 1. Open any gallery folder.
> 2. Tap the Info icon in the top bar.
> 3. Observe the icon now has a white circular background, making it clearly visible.
> 4. Verify no Info icon appeared on the main folder list screen.
