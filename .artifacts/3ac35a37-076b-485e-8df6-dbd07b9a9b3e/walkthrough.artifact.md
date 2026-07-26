# Walkthrough - Temporarily Show Excluded Folders

I have implemented the "Temporarily show excluded" feature, which allows you to peek at your hidden folders without permanently changing your exclusion settings.

## Changes Made

### Temporary Visibility
- **Peek at Hidden Content**: Tapping **"Temporarily show excluded"** in the main menu will instantly reveal all folders that you've previously hidden.
- **Auto-Hide Logic**: To keep your gallery organized, the excluded folders are automatically hidden again as soon as you perform an "opening" operation. Specifically:
    - Opening any gallery folder will reset the view.
    - Toggling between **Gallery** and **Timeline** modes will reset the view.
- **Visual Consistency**: The menu item is now functional and available both in the main gallery list and while browsing inside a folder.

### Logic & State
- **GalleryViewModel**:
    - Added `showExcludedTemporarily` state to track the peeking mode.
    - Integrated this state into the folder filtering engine to bypass the exclusion list when active.
    - Implemented reset hooks in `selectFolder` and `toggleDisplayMode` to ensure the "temporary" behavior.
- **SearchTopBar**: Fully wired the callback for the temporary visibility action.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Activation**: Confirmed that tapping the menu item shows hidden folders.
- **Operation Reset**: Verified that opening a folder and then returning to the main list hides the excluded folders again.
- **Mode Reset**: Verified that switching to the Timeline view and back to Gallery view resets the peeking mode.
- **Menu Availability**: Confirmed the option works correctly from all top bars.

> [!TIP]
> Use this feature to quickly check a hidden folder or move items out of it without having to navigate into the "Manage Excluded" settings!
