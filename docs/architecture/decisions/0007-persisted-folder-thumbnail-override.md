# ADR-0007: Persisted per-folder thumbnail override resolved against live media

- Status: Active
- Date: 2026-09-25
- Decision makers: Easy Gallery maintainers

## Context

In the Folders View (`FolderListScreen` with `DisplayMode.GALLERY`) every folder tile renders
`Folder.thumbnailUri`. That value is not chosen by the user: `GalleryTransformations.filterAndSortFolders`
builds each `Folder` from the **first** `MediaItem` it encounters for a given `folderPath` while
iterating `allMedia`, and `allMedia` is `MediaStoreDataSource.getAllMedia()` sorted by `dateAdded`
descending. The tile therefore shows whichever item MediaStore happened to return first — usually the
newest item, which is often a screenshot, a thumbnail-unfriendly frame, or an unrelated download.

Users want to nominate a specific item in a folder as that folder's cover image, and that choice must
survive process death and app restarts.

Constraints that shape the decision:

- The app has no DI framework, no Room, and no DataStore. Every persisted user preference so far uses
  a hand-written `SharedPreferences` store behind an interface, with an in-memory fake for tests
  (`FolderPreferencesStore`, `DisplayPreferencesStore`, `FolderViewPreferencesStore`).
- `MediaStoreDataSource.getFolders()` is **not** the source of the rendered folder list.
  `GalleryViewModel.filteredFolders` derives folders from `_allMedia` via
  `GalleryTransformations.filterAndSortFolders`; `getFolders()` only seeds `_uiState` as a
  loading/error sentinel. Any change made in the data source would not reach the grid.
- The nominated item can be deleted, renamed, or moved to another folder at any moment by this app,
  by another app, or over USB. MediaStore ids are also not guaranteed stable across a media rescan.
- ADR-0006 already established that per-folder state is keyed by `Folder.path`, stored sparsely, and
  merged **on read** in `GalleryViewModel` so that no screen read path has to change.

## Decision

Persist a sparse map of `Folder.path -> MediaItem.uri.toString()` in a dedicated
`SharedPreferences` file (`folder_thumbnails`) behind a new `FolderThumbnailStore` interface with a
`SharedPreferences` implementation and an in-memory implementation for tests.

Resolve the override **on read**, in the pure function `GalleryTransformations.filterAndSortFolders`,
which gains an optional `thumbnailOverrides: Map<String, String>` parameter (default `emptyMap()`).
`GalleryViewModel` feeds the persisted map into the existing `filteredFolders` `combine`. The data
source and `Folder` itself are unchanged.

Resolution is **validating by construction**: while iterating `allMedia`, an override is honoured only
if some item in that folder actually has the stored URI. If no such item exists — deleted, moved out,
or re-indexed under a new id — the folder silently falls back to the current automatic thumbnail. The
stale entry is not garbage-collected.

Override resolution is deliberately performed **before** the media-type filter is applied, so a folder
filtered to videos still shows a user-chosen still image as its cover.

Any `MediaType` may be chosen — `IMAGE`, `GIF`, and `VIDEO`. The automatic thumbnail can already be a
video frame or an animated GIF today, so restricting the explicit choice would be less capable than
the behaviour it replaces and would introduce an "this item cannot be used" error state for no gain.

The choice is made through a dedicated, ViewModel-owned picking mode in the Folder Detail View: while
`isThumbnailPickerActive` is true, a tap on a media item assigns the thumbnail and exits the mode
instead of opening the full-screen viewer. The mode flag lives in `GalleryViewModel`, alongside
`isMediaSelectionMode`, not in screen-local `remember` state.

A reset affordance is required: without it the user can replace the cover but can never return the
folder to automatic selection.

## Alternatives Considered

**Compute the thumbnail in `MediaStoreDataSource.getFolders()`.** Rejected: the rendered folder list
does not come from `getFolders()`, so the override would never appear. It would also push
`SharedPreferences` access into the data source, which currently owns no user preferences, and would
force every `MediaRepository` fake in the unit tests to grow a new concern.

**Store the override inside `FolderViewOverrides` / `FolderViewPreferencesStore` (ADR-0006).** Rejected:
that store models *display preferences of the Folder Detail View*, has an `OverridablePreference`
group concept with an "apply to all folders" reset semantic, and its `clear(group)` wipes a field for
every folder. A cover image is per-folder by definition and has no global counterpart, so it would
never participate in that mechanism and would only dilute the store's meaning.

**Store the chosen item's file name instead of its URI.** Rejected: it survives a MediaStore id
renumbering but breaks on a file rename, needs an extra lookup before Coil can load it, and is
ambiguous when two folders in different buckets share a name. The validating resolution already
degrades gracefully in the renumbering case.

**Actively garbage-collect the stored entry when the chosen item is deleted.** Rejected for now: it
would couple `deleteSelectedMedia`, the viewer's delete path, and the move/copy paths to the thumbnail
store, and it cannot catch deletions performed outside the app anyway. Orphaned per-folder entries are
accepted precedent from ADR-0006.

**Pick the thumbnail from a grid inside an `AlertDialog`.** Rejected: it would duplicate the grid,
its column count, its sorting/grouping and its scroll state, and a dense media grid inside a dialog is
cramped. The picking mode reuses the Folder Detail grid exactly as the user has already configured it.

**Reuse the existing media-selection mode plus an overflow action** (the ADR-0005 pattern used by
"Use as background"). Rejected as the primary entry point: the feature is specified to live in the
Folder Detail overflow menu, and a single tap is a clearer affordance than "long-press, then open a
second menu, then choose". Multi-select is also meaningless for a single cover image.

## Consequences

### Positive

- No screen read path changes. `FolderGrid` and `FolderList` keep reading `folder.thumbnailUri`, so
  both the Folders View grid **and** the Folders View list get the feature for free.
- Resolution stays in a pure, Android-free function, so it is unit-testable on the plain JVM next to
  the existing `GalleryTransformationsTest`.
- A deleted, renamed-away, or moved-out cover can never produce a broken tile; the fallback is the
  pre-existing behaviour.
- The store follows the established interface + `SharedPreferences` impl + in-memory impl shape, so
  `GalleryViewModel` tests can inject a fake through the existing `@JvmOverloads` constructor.

### Negative

- Stale entries accumulate for folders that are renamed or deleted; there is no compaction. The size
  is one short string per folder the user has customised.
- Resolution costs one extra map lookup per item in `allMedia` on every rebuild of `filteredFolders`,
  and one map/copy pass over the folder list.
- Because the folder key is `Folder.path` and `filterAndSortFolders` buckets by `folderPath`, the
  pre-existing bucket-name collision behaviour of `getFolders()` is inherited unchanged.
- The user gets no explicit confirmation at the moment of choosing; the effect is only visible after
  navigating back to the Folders View.
- `GalleryViewModel` grows two more flows and four more methods, worsening the known god-class problem.

## References

- ADR-0005: Selection-scoped media actions mirror the full-screen viewer (single-item action gating
  precedent).
- ADR-0006: Per-folder display preference overrides layered over global defaults (path-keyed sparse
  overrides merged on read; orphan-entry precedent). Not superseded by this record.
- `app/src/main/java/com/davide/seddio/easygallery/data/GalleryTransformations.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderThumbnailStore.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt`
