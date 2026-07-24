# Walkthrough - Sorting and Overflow Menu

I have implemented an overflow menu in the gallery top bar and added powerful sorting capabilities for your folders.

## Changes Made

### Data Layer
- **Expanded Folder Metadata**: Updated the `Folder` data class and `MediaStoreDataSource` to fetch and store additional fields:
    - **Path**: The physical location of the folder.
    - **Size**: The total size of all images in the folder.
    - **Timestamps**: Last modified date and date taken (creation) for precise chronological sorting.

### Logic & State
- **Sort Logic**: Introduced a `SortType` enum and updated the `GalleryViewModel` to handle 6 different sorting methods.
- **Pinned Priority**: Enhanced the filtering logic to ensure **pinned folders always remain at the very top**, regardless of the selected sort order.
- **Random Shuffle**: Implemented a "Random" sort that re-scrambles your folders on demand.

### UI Enhancements
- **Overflow Menu**: Added a 3-dotted menu icon on the right side of the top bar.
- **Menu Options**: The menu includes placeholders for advanced features (Column count, Show excluded, etc.) and a functional "Sort by" action.
- **Sort Dialog**: A new dialog window allows you to select your preferred sorting criteria using a clean radio-button interface.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Menu Visibility**: The 3-dotted menu appears correctly in the top bar.
- **Sorting Accuracy**:
    - Selecting "Name" sorts folders alphabetically.
    - Selecting "Size" puts folders with the most data at the top.
    - Selecting "Date Taken" shows folders with the newest photos first.
- **Pinned Folders**: Pinned folders stay fixed at the top even when switching between different sort types.
- **Persistence**: Sorting persists while searching and zooming.

> [!TIP]
> Use the "Random" sort whenever you want a fresh perspective on your photo collection!
