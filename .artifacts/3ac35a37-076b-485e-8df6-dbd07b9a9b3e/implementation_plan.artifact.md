# Implementation Plan - Correct Chronological Grouping

This plan addresses the issue where date-based groups are sorted alphabetically by their display string (e.g., "September" before "August" if ascending) instead of chronologically.

## User Review Required

> [!IMPORTANT]
> - **Unified Sorting**: I will refactor the grouping logic to sort the underlying media items *before* grouping them. This ensures that group headers follow the actual date/time order rather than alphabetical order.
> - **Implicit Item Order**: The items within each group will follow the same ascending/descending direction as the group headers.

## Proposed Changes

### Logic Layer

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- **Refactor `groupMedia`**:
    1. First, sort the `items` list based on the `GroupByType` and `SortOrder`.
        - For `DATE_TAKEN`: Sort by `dateAdded`.
        - For `LAST_MODIFIED`: Sort by `dateModified`.
        - For `FILE_TYPE`: Sort by `type.name`.
    2. Then, use the `groupBy` operator on the sorted list.
    3. Since Kotlin's `groupBy` returns a `LinkedHashMap` for a list receiver, the order of the keys will perfectly match the sorted order of the items.
    4. Remove the `toSortedMap` call at the end, as it was the cause of the alphabetical sorting.

## Verification Plan

### Automated Tests
- Verify build success.

### Manual Verification
1.  **Monthly Chronological Sort**:
    - Group by "Date taken (monthly)" -> Set to **Ascending**.
    - Verify that older months (e.g., January 2024) appear at the top, and newer months (e.g., July 2026) appear at the bottom.
2.  **Daily Chronological Sort**:
    - Group by "Date taken (daily)" -> Set to **Descending**.
    - Verify "Today" is at the top, followed by "Yesterday", and then older dates in descending order.
3.  **File Type Sort**:
    - Group by "File type" -> Verify headers (Gifs, Images, Videos) follow the alphabetical order of the types.
