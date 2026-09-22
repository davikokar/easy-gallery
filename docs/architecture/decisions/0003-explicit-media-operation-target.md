# ADR-0003: Explicit operation target for copy and move

- Status: Active
- Date: 2026-09-22
- Decision makers: Easy Gallery project maintainer

## Context

Copy and move both funnel through `GalleryViewModel.performOperationWithPath(path)`. That method
decides **what** to operate on implicitly, by branching on selection mode:

- if `_isMediaSelectionMode` is true, the operands are `_selectedMediaItems`;
- otherwise the operands are every media item whose `folderPath` is in `_selectedFolders`.

Two entry points exist today and both satisfy that assumption. `MediaSelectionTopBar` is only
visible while media selection mode is active, and `SelectionTopBar` is only visible while folders
are selected. The destination picker itself is rendered by `FolderDetailScreen` and
`FolderListScreen`, gated on `isDestinationPickerActive`.

The full-screen viewer adds a third entry point. `FullImageScreen` is gaining "Copy to" / "Move to"
in an overflow menu acting on the **single currently-viewed item**. The viewer is in neither
selection mode: `selectMedia` only opens the viewer when `_isMediaSelectionMode` is false, so at
that moment `_selectedMediaItems` is empty and `_selectedFolders` is typically empty too. With the
current implicit resolution, a viewer-initiated operation collects an empty operand list, hits the
`urisToMove.isEmpty()` guard, calls `cancelOperation()` and silently does nothing — no error, no
feedback.

Three further constraints shaped the response:

- The `SecurityException` recovery path for moves is non-trivial and lives in one place.
  `tryMoveMedia` stores a `MoveOperation`, raises a `createWriteRequest` IntentSender through
  `_pendingWriteRequest`, and `onWriteRequestResult(granted = true)` replays it. Any second
  implementation of move would have to reproduce all of it.
- Selection mode is **app-level** state, not screen-level. `enterMediaSelectionMode` flips a
  ViewModel flag that `FolderDetailScreen` and `CalendarGrid` both render against.
- Per ADR-0002, transient viewer state that must survive Activity recreation is ViewModel-owned.
  `MainActivity` renders `FullImageScreen` in its own `if (selectedMedia != null)` branch, so the
  screen is torn down and rebuilt on rotation and on the in-app language switcher's
  `activity.recreate()`.

## Decision

`GalleryViewModel` gains an **explicit operation target**: a private, ViewModel-owned list of
`MediaItem` describing exactly what the pending operation applies to. An empty list means "no
explicit target".

`performOperationWithPath` resolves its operands in a fixed precedence order:

1. the explicit operation target, when non-empty;
2. otherwise `_selectedMediaItems`, when media selection mode is active;
3. otherwise the media belonging to `_selectedFolders`.

Viewer-initiated operations set the target explicitly through a new
`startOperationForMedia(item, type)`, which records the single item as the target and then opens
the destination picker exactly as `startOperation(type)` does. Existing selection-mode call sites
are unchanged and continue to fall through to steps 2 and 3.

The target is transient and must be cleared in `cancelOperation()` and after a completed
operation. It is never exposed as a public `StateFlow` and never persisted.

Completion behaviour differs by operation type for viewer-initiated work:

- **MOVE closes the viewer on success.** `currentMediaList` is the paged snapshot the viewer was
  opened with; after a move the item no longer exists at its old location and the snapshot is
  stale.
- **COPY leaves the viewer open.** The source is untouched, so the current page stays valid.

## Alternatives Considered

**Have the viewer call `enterMediaSelectionMode(item)` and reuse the existing media-selection
path.** Rejected. Selection mode is app-level state, not viewer-local. Entering it from the viewer
leaves `FolderDetailScreen` — which is not composed while the viewer is open — in selection mode
when the user backs out, complete with `MediaSelectionTopBar` and a checked item the user never
selected there. Exiting on the viewer's behalf then means the viewer has to guess whether the user
was *already* in selection mode before, which the current flags cannot distinguish. It trades a
silent no-op for a state-leak between two screens.

**Add a separate viewer-only copy/move method on the ViewModel.** Rejected. Move is not a single
repository call: it is `updateMediaRelativePath` plus `SecurityException` capture, `MoveOperation`
stashing, IntentSender surfacing through `_pendingWriteRequest`, and replay via
`onWriteRequestResult`. A second implementation duplicates that recovery logic and guarantees the
two copies drift — the scoped-storage path is precisely the one that is hardest to exercise in
tests and easiest to get subtly wrong.

**Pass the target as a parameter to `performOperationWithPath(path, items)`.** Rejected. The call
site is the destination picker's confirm button, which is wired as
`onPerformOperationWithPath(browsingPath)` in both `FolderListScreen` and `FolderDetailScreen` and
in both instrumented content tests. Threading operands through the picker would push knowledge of
*what* is being operated on into a component whose only job is choosing *where*, and would change
a signature already referenced from five call sites.

## Consequences

### Positive

- The viewer reaches copy and move through the same pipeline as the other two surfaces, so
  `SecurityException` recovery, the write-request replay and the post-operation `loadFolders()`
  refresh are inherited rather than reimplemented.
- The operand source becomes explicit and readable at the top of `performOperationWithPath`
  instead of being inferred from two unrelated selection flags.
- No selection-mode state is mutated by the viewer, so `FolderDetailScreen` is in exactly the
  state the user left it in when the viewer closes.
- The target is plain Kotlin data in the ViewModel, unit-testable on a plain JVM alongside the
  existing `GalleryViewModelTest` cases that already drive `performOperationWithPath`.
- Being ViewModel-owned, the pending target survives Activity recreation, consistent with ADR-0002
  (which remains `Active` and is **not** superseded by this record).

### Negative

- **Leak risk.** A target that is not cleared silently hijacks the *next* operation: a user copies
  one photo from the viewer, later selects ten items in a folder and taps Copy, and the ten are
  ignored in favour of the stale single item. Clearing must happen on **both** exit paths —
  `cancelOperation()` and normal completion — including the early-return branches inside
  `performOperationWithPath` and the `tryMoveMedia` success path. This failure mode is invisible
  within a single operation and only appears on the operation after it.
- Three surfaces now share one pipeline governed by a precedence order that must be respected.
  Anyone adding a fourth entry point, or reordering the resolution, can change behaviour for the
  other three at once.
- The two completion behaviours (MOVE closes the viewer, COPY does not) are an asymmetry that has
  to be re-derived by anyone reading the code. It exists because `currentMediaList` is a snapshot,
  not a live query.
- `MainActivity` renders `FullImageScreen` in a branch that excludes `FolderDetailScreen` and
  `FolderListScreen`, which are the only current hosts of the `isDestinationPickerActive` picker.
  The viewer must therefore render its own picker; otherwise `startOperationForMedia` sets state
  that nothing displays.
- `GalleryViewModel` gains more state and another public method. This is an accepted increment to
  an already-oversized class, justified by keeping a single copy of the move-recovery logic.

### Known Risks Made More Reachable

Routing a third surface into this pipeline does not create the following defects, but it makes
them materially easier for a user to hit. Both are in `MediaStoreDataSource.copyFile`, which is
what the COPY branch calls per item:

- **Copying into the item's own folder destroys the file.** `copyFile` resolves
  `File(targetFolderPath, fileName)` and opens it for writing, which truncates it to zero length,
  and only then reads from the source. When source and target are the same path they are the same
  file, so the read returns nothing and the original is left empty. From a selection-mode flow the
  user has at least navigated away from the folder to reach the picker; from the viewer, the
  item's own folder is the most obvious destination to pick.
- **`copyFile` has no error handling.** There is no try/catch and no `SecurityException` path
  equivalent to the one move has. A failure — missing write access to the destination, no free
  space, a name collision — propagates out of the `viewModelScope.launch` in
  `performOperationWithPath` with no user-visible message, after `cancelOperation()` has already
  been skipped.

Neither is fixed by this ADR. Both should be addressed before the viewer entry point ships.

## References

- `app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt` —
  `performOperationWithPath`, `startOperation`, `cancelOperation`, `tryMoveMedia`,
  `onWriteRequestResult`, `enterMediaSelectionMode` / `exitMediaSelectionMode`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/MediaViewerState.kt` — `currentMediaList`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt` and
  `FolderListScreen.kt` — the two existing `isDestinationPickerActive` hosts
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — `copyFile`
- `app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt` — the `if/else` navigation
  chain and its `selectedMedia != null` branch
- `app/src/test/java/com/davide/seddio/easygallery/ui/GalleryViewModelTest.kt` — existing
  `performOperationWithPath` coverage
- [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) — ViewModel-owned transient viewer
  state; still `Active`, not superseded
