# Walkthrough - Independent View and Sort Settings

I have fully decoupled the visualization and organization settings for your folders and your photos. This allows you to tailor the experience for browsing collections versus individual media items.

## Changes Made

### Visualization Independence
- **Dual Display Settings**: You can now set independent **View Types** (Grid/List) for the main gallery and your photos.
    - *Example*: You can keep your Folders in a detailed **List** (to see paths) while viewing Photos in a dense **Grid**.
- **Dual Sort Settings**: Sorting criteria are now independent.
    - *Example*: Sort your **Folders by Size** to find space-wasters, while sorting your **Photos by Date Taken** for chronological browsing.
- **Synced Photo Preferences**: Any changes made to view type or sorting inside a folder are automatically applied to the Timeline and all other folders, but do not affect the main gallery list.

### New Photo List View
- **Media List Mode**: Implemented a dedicated "List" view for photos and videos.
    - **Thumbnails**: High-quality previews on the left.
    - **Details**: Filename and formatted Date/Time (when Info is toggled) on the right.
- **Unified Components**: Extracted the `MediaListItem` component to ensure a consistent look between the folder content list and the chronological timeline list.

### Dynamic Interaction
- **Context-Aware Dialogs**: The "Sort by" and "Change view type" menus intelligently detect which screen you are on and update only the relevant level (Gallery or Pictures).
- **Responsive Timeline**: The chronological timeline now supports both Grid and List modes, respecting your global photo visualization preferences.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Independence**:
    - Set Gallery to **List**.
    - Opened a folder -> Set to **Grid**.
    - Returned to Gallery -> Verified it remained in **List** mode.
- **Sorting Consistency**:
    - Sorted Gallery by **Name**.
    - Sorted Photos by **Random**.
    - Verified that navigating between folders preserves the **Random** sort, while the main list stays **Alphabetical**.
- **Timeline Integration**: Verified that switching to List view inside a folder also changes the Timeline to a clean vertical list with date headers.

> [!TIP]
> Try combining the **List view** with the **Sort by Size** option in the main gallery to quickly identify which folders are taking up the most space on your device!
