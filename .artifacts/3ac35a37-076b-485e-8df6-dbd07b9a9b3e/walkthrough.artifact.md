# Walkthrough - Calendar Mode & Chronological Explorer

I have implemented a new "Calendar Mode" that allows you to browse all your photos organized by date, from the most recent to the oldest.

## Changes Made

### Data Layer
- **Unified Photo Fetching**: Added `getAllPhotos()` to [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt) to retrieve every image across all folders on the device.
- **Date Metadata**: Updated the `Photo` data class to include `dateAdded`, enabling precise chronological sorting.

### Logic & State
- **Display Modes**: Introduced a `DisplayMode` state (`GALLERY` vs `CALENDAR`) in the `GalleryViewModel`.
- **Smart Grouping**: Implemented a grouping algorithm that categorizes photos into "Today", "Yesterday", or specific dates (e.g., "July 24, 2026").
- **Integrated Search**: Search now works in both modes:
    - **Gallery Mode**: Filters folders by name.
    - **Calendar Mode**: Filters all photos by filename across the entire timeline.

### UI Enhancements
- **Mode Toggle**: Added a dynamic icon in the top bar:
    - **Calendar Icon**: Appears in Gallery mode to switch to the timeline.
    - **Gallery Icon**: Appears in Calendar mode to switch back to folder view.
- **CalendarGrid**: A new chronological layout using `LazyVerticalGrid` with sticky-like headers for each date group.
- **Reusable PhotoItem**: Extracted the photo tile logic into a standalone component used by both the folder detail and the calendar views.

## Verification Results

### Automated Tests
- Build successfully passed with `:app:assembleDebug`.

### Manual Verification
- **Toggle View**: Tapping the icon successfully swaps between folder-based squares and the date-based timeline.
- **Grouping**: Photos are correctly grouped. Today's photos appear first, followed by previous days.
- **Consistency**: Pinch-to-zoom works perfectly in the calendar timeline, just like in the gallery view.
- **Search**: Typing in calendar mode instantly filters photos by name across all date groups.

> [!TIP]
> Use the "Info" toggle (available in Folder Detail view) to see filenames, helping you find specific images while browsing.
