# Walkthrough - Sort Order (Ascending & Descending)

I have enhanced the sorting system by adding independent "Ascending" and "Descending" order options for both the main gallery and your photo collections.

## Changes Made

### Directional Sorting
- **New Sort Order**: The "Sort by" dialog now includes a clear choice between **Ascending** and **Descending** order, separated from the primary criteria by a horizontal line.
- **Independent Levels**: Just like grid density and view type, the sort direction is independent for your Gallery and your Pictures.
    - *Example*: Sort your **Folders by Name (Ascending)** while sorting your **Photos by Size (Descending)**.
- **Smart Disabling**: When the "Random" sort type is selected, the Ascending and Descending options are automatically disabled and dimmed, as they are not applicable to a shuffled list.

### Enhanced Logic
- **GalleryViewModel**:
    - **Dual Order States**: Introduced `folderSortOrder` and `pictureSortOrder`.
    - **Refined Sorting Engine**: Updated the folder and media filtering logic to respect the selected direction while maintaining the "Pinned items first" rule for the folder list.
- **UI Consistency**: The sort dialog now features an "OK" button for explicit confirmation, matching the behavior of your media filters.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Contextual Behavior**:
    - Set Gallery to **Name Ascending**.
    - Opened a folder and set Photos to **Date Taken Descending**.
    - Returned to Gallery -> Verified alphabetical order was preserved.
- **Random Logic**: Verified that picking "Random" makes the direction options unclickable and visually disabled.
- **Visuals**: Confirmed that the separator line and radio buttons align perfectly with your dark theme.

> [!TIP]
> Combine **Descending Sort** with **Sort by Size** to instantly bring your largest folders or videos to the top of the list!
