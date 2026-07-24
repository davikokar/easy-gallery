# Implementation Plan - Calendar View Mode

This plan outlines the steps to add a "Calendar Mode" to the main gallery view, allowing users to see all their photos grouped by date (e.g., Today, Yesterday, July 24, etc.).

## User Review Required

> [!IMPORTANT]
> - A new toggle icon will be added to the TopBar.
> - The calendar view will display all photos in chronological order, grouped by date.
> - Zooming (pinch-to-zoom) will still be available in the calendar grid.
> - Search will still work in calendar mode, filtering photos by filename across all dates.

## Proposed Changes

### Data Layer

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Add `getAllPhotos(): List<Photo>` to fetch every image on the device.

---

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add `DisplayMode` enum: `GALLERY` (Folder-based) and `CALENDAR` (Date-based).
- Add `displayMode` StateFlow.
- Add `allPhotos` StateFlow.
- Add `groupedPhotosByDate` StateFlow:
    - This will compute the chronological grouping (Latest first).
    - It will also respect the search query.
- Add `toggleDisplayMode()` function.

#### [NEW] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Implement a screen/component showing a scrollable list of date-grouped photo grids.
- Use `LazyVerticalGrid` or a nested `LazyColumn` + `LazyVerticalGrid` (or just `LazyColumn` with multiple rows) to display the grouped photos.

#### [MODIFY] [SearchTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt)
- Add an optional `extraActions` slot or specifically add the toggle button logic.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Add the toggle icon to the `SearchTopBar`.
- Conditionally display either the `FolderGrid` or the new `CalendarGrid` based on the current `displayMode`.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Toggle Mode**:
    - Tap the calendar icon in Gallery mode -> View switches to chronological grouping.
    - Tap the gallery icon in Calendar mode -> View switches back to folder-based squares.
2.  **Calendar View**:
    - Verify photos are grouped by date (Today, Yesterday, etc.).
    - Verify most recent photos are at the top.
    - Verify pinch-to-zoom works in calendar mode.
3.  **Search**:
    - Enter search mode while in calendar view -> Photos should be filtered across all dates.
