# Implementation Plan - Dark Background for Menus and Dialogs

This plan outlines the steps to ensure all menus and dialogs use the app's signature dark blue-grey background (`BottomGrey`), resolving the readability issue where white text was appearing on a white background.

## User Review Required

> [!IMPORTANT]
> - **Global Fix**: I will update the app's central `Theme.kt` to map all surface-related color tokens to `BottomGrey`. This will automatically fix all `AlertDialog` and `DropdownMenu` instances throughout the app without needing to edit each screen individually.
> - **Text Contrast**: I'll ensure that `onSurface` and `onSurfaceVariant` are consistently mapped to `White` and `White(70%)` respectively to guarantee high legibility on the dark background.

## Proposed Changes

### UI Layer

#### [MODIFY] [Theme.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/theme/Theme.kt)
- Update both `DarkColorScheme` and `LightColorScheme` to explicitly include:
    - `surface = BottomGrey`
    - `surfaceVariant = BottomGrey`
    - `onSurface = Color.White`
    - `onSurfaceVariant = Color.White.copy(alpha = 0.7f)`
- This ensures that components like Dialogs (which often use `surfaceVariant` or newer container tokens) always pick up the dark color.

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt) and [SelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt)
- Verify that `DropdownMenu` items use the default theme colors and don't have hardcoded text colors that might conflict with the new dark surface. (I previously standardized these to use `onSurface`).

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Dialog Backgrounds**:
    - Open "Sort by", "Change view type", "Filter media", and "Move to".
    - Verify that all windows now have the `BottomGrey` (dark blue-grey) background.
2.  **Menu Backgrounds**:
    - Open the 3-dotted overflow menu.
    - Verify the dropdown container is dark with white text.
3.  **Readability**:
    - Confirm that all text is clearly visible and high-contrast white.
