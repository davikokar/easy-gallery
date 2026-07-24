# Walkthrough - Search Functionality

I have added a powerful search feature that allows you to quickly find folders and photos across the entire app.

## Changes Made

### Search Logic & State
- **GalleryViewModel**:
    - Added `searchQuery` and `isSearchActive` state.
    - Implemented `filteredFolders` and `filteredPhotos` using Kotlin Coroutines `combine` operator for real-time, case-insensitive filtering.
    - Search is automatically cleared when exiting search mode.

### UI Components
- **SearchTopBar**: A new reusable component that handles the transition between the normal app bar and the search interface.
    - **Normal Mode**: Shows the title (left-aligned) and a search icon.
    - **Search Mode**: Shows a back arrow (to cancel) and a text field for searching.
- **FolderListScreen**: Updated to use `SearchTopBar` and observe `filteredFolders`.
- **FolderDetailScreen**: Updated to use `SearchTopBar` (showing the folder name as title) and observe `filteredPhotos`. The "Info" button remains accessible in the normal top bar view.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Left Alignment**: The "Easy Gallery" title is now correctly aligned to the left.
- **Search Activation**: Clicking the lens icon successfully replaces the title with a search field and back arrow.
- **Real-time Filtering**:
    - Typing in the folder list filters folders by name.
    - Typing in a folder detail view filters photos by filename.
- **Search Cancellation**: Clicking the back arrow in search mode restores the normal title and clears the results.
- **Case Insensitivity**: Searching for "camera" matches "Camera", "CAMERA", etc.

> [!TIP]
> The search persists even if you zoom in/out of the grid, allowing you to find items while maintaining your preferred view density.
