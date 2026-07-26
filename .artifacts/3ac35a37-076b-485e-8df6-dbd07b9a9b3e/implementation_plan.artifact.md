# Implementation Plan - Independent View and Sort Settings

This plan decouples the "View Type" (Grid/List) and "Sort By" settings for the main gallery and the picture grids, allowing for independent customization of each level.

## Proposed Changes

### UI Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **State Separation**:
    - Split `_sortType` into `_folderSortType` and `_pictureSortType`.
    - Split `_viewType` into `_folderViewType` and `_pictureViewType`.
- **Logic Updates**:
    - Update `filteredFolders` to use `folderSortType`.
    - Update `filteredMedia` and `filteredAllMedia` to use `pictureSortType`.
- **Function Updates**:
    - Update `setSortType` and `setViewType` to accept a `forPictures: Boolean` parameter.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Use `folderSortType` and `folderViewType` for the main gallery view.
- Update dialog callbacks to pass `forPictures = false`.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Use `pictureSortType` and `pictureViewType`.
- **New Feature**: Implement "List" view for media items.
    - Create `MediaList` and `MediaListItem` components.
    - Media List Layout: Thumbnail on the left, filename and size on the right.
- Add `showSortDialog` and `showViewTypeDialog` states.
- Connect `SearchTopBar` callbacks to show these dialogs.

#### [MODIFY] [CalendarGrid.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CalendarGrid.kt)
- Respect `pictureViewType`. (If List is selected, the timeline will show items in a single-column list under date headers).

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Independent View Type**:
    - Set Gallery to **List**.
    - Open a folder -> Set View Type to **Grid**.
    - Go back -> Gallery should still be a **List**.
2.  **Independent Sorting**:
    - Sort Gallery by **Size**.
    - Open a folder -> Sort Pictures by **Name**.
    - Go back -> Gallery should still be sorted by **Size**.
3.  **Cross-Folder Consistency**:
    - Change sort/view settings in one folder -> Verify they apply to other folders and the Timeline, but not the main gallery list.
