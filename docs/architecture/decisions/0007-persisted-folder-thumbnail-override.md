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

> **Amended 2026-09-25 — see Amendment 1 below.** The picking mode, its ViewModel ownership and its
> interception of the tap before the viewer all still govern. What no longer holds is that a tap
> *assigns and exits*: a tap now sets a draft, and a separate confirm action commits it.

A reset affordance is required: without it the user can replace the cover but can never return the
folder to automatic selection.

> **Withdrawn 2026-09-25 — see Amendment 1 below.** This requirement no longer holds. The reset
> menu item has been removed from the product, and with it `clearCurrentFolderThumbnail` and
> `hasSelectedFolderCustomThumbnail`. The original text is kept here deliberately so the record
> shows that the requirement existed and was reversed.

## Amendment 1 — 2026-09-25 — Draft-and-commit picking, and withdrawal of the reset affordance

This amendment narrows the **UI interaction** paragraph of the Decision and **withdraws** the reset
paragraph. Everything else in the record is unchanged and continues to govern: the `folder_thumbnails`
`SharedPreferences` file, the `FolderThumbnailStore` interface plus in-memory fake, the
`Folder.path -> uri` key shape, merge-on-read inside `GalleryTransformations.filterAndSortFolders`,
resolution **before** the media-type filter, validating-by-construction fallback, no garbage
collection of orphans, and all three `MediaType`s being selectable.

This is recorded as an amendment rather than a superseding ADR because the persistence and
resolution architecture — the part of ADR-0007 with lasting structural impact — has not stopped
applying. Disabling ADR-0007 and writing ADR-0008 would retire a decision that still governs, and
restating it in a new record would duplicate it. What changed is confined to one screen
(`FolderDetailScreen`), one top bar (`ThumbnailPickerTopBar`), one dropdown item (`SearchTopBar`)
and the mode-state members of `GalleryViewModel`. Both changes are visible in this record rather
than concealed: the two superseded paragraphs above are annotated in place and left intact.

### What changed

**1. Picking is draft-and-commit, not commit-on-tap.**

- Entering the mode selects nothing. The top bar shows a close (X) icon and the
  `thumbnail_picker_title` title.
- A tap on a media item sets a **draft** and does nothing else. It does not persist, does not exit
  the mode, and does not open the full-screen viewer. Tapping a different item moves the draft.
  Tapping the drafted item again is a no-op, not a deselect — there is no state in which the user
  can be left with an empty draft after having made one.
- Once a draft exists the top bar's close icon becomes a back arrow. That arrow is the **commit**:
  it persists the draft and exits the mode.
- The close icon, shown only while there is no draft, exits without writing anything.

**2. The draft lives in `GalleryViewModel`, not in the screen.** `folderThumbnailDraftUri:
StateFlow<Uri?>` sits next to `isThumbnailPickerMode`. This is ADR-0002's rule applied to a second
surface: `MainActivity` is a plain `if`/`else if` chain with no back stack, so a screen leaving the
composition is destroyed and screen-local `remember` — including `rememberSaveable` outside the
`SaveableStateHolder` — is discarded. A draft held in `FolderDetailContent` would be silently lost
on any navigation or configuration change while the picker is open, and would additionally
desynchronise from `isThumbnailPickerMode`, which is already ViewModel-owned. The draft is cleared
wherever the mode flag is cleared: entering the mode, cancelling, committing, `selectFolder`,
`backToFolders`, and both selection-mode entry points.

**3. System back does what the top-bar button does.** With a draft, system back commits; without
one, it cancels. The navigation icon in that state *is* a back arrow, so having the arrow and the
back gesture disagree would give one affordance two meanings. The usual "back cancels" convention
protects the user from losing work; there is nothing to lose here, because the committed value is
a single reversible preference that the same two taps can change again, and the automatic
thumbnail it replaces is recomputed, not stored.

**4. There is no pre-selection of the existing custom thumbnail.** Seeding the draft from the
persisted override would mean a draft exists the instant the mode opens, so the bar would show the
commit arrow immediately and the cancel affordance would be unreachable — directly contradicting
point 1. It would also make "open the picker and press back" silently rewrite the stored value
with itself. The cost is that the current cover is not highlighted when the picker opens; that is
accepted, and it is the same information the user just saw on the folder tile.

> **Reversed 2026-09-25 — see Amendment 2 below.** The existing custom thumbnail **is** now
> pre-selected. The two objections raised here were both real, and both were consequences of
> *seeding the draft*, not of pre-selection itself; Amendment 2 keeps the draft meaning exactly
> what it means here and adds a separate display-only flow. The paragraph is kept intact so the
> record shows the position that was held and why it no longer applies.

**5. The draft is rendered with the existing selection checkmark, not a new affordance.**
`MediaGridItem` already draws `Icons.Default.CheckCircle` at `Alignment.TopEnd`, tinted
`MaterialTheme.colorScheme.primary` — which this app's theme pins to `BrandBlue` (`0xFF017DDF`) for
both light and dark — over a white circle, plus a 30% black scrim, tagged `selected_checkmark`.
That is already "a blue check in the top-right corner"; adding a picker-specific badge would put a
second, near-identical glyph in the codebase. The draft is therefore passed into the existing
`selectedItems: Set<Uri>` parameter as `setOfNotNull(draftUri)`, which also means grid, list and
grouped rendering all get the affordance without touching any of them.

**6. List view keeps its own existing placement.** `MediaListItem` draws the same icon, the same
tint and the same `selected_checkmark` tag, but centred on its 64 dp thumbnail rather than in the
tile's top-right corner, and it additionally tints the row `primaryContainer`. It is **not**
changed to match the grid: that component is shared with media multi-selection in Folder Detail
View and Timeline View, so re-aligning it for the picker would silently restyle multi-selection
everywhere, and a corner badge on a 64 dp thumbnail is cramped. "Top-right" is therefore a
statement about the grid tile, which is the configuration the request describes.

### Alternatives considered in this amendment

**Keep commit-on-tap and add an undo snackbar.** Rejected: a snackbar is a timed affordance layered
over a grid the user is still scrolling, it needs its own host in a screen that has none, and it
still writes the preference first and repairs it afterwards. Not writing until the user confirms is
simpler and has no timeout.

**Hold the draft in `remember`/`rememberSaveable` inside `FolderDetailContent`.** Rejected on
existing precedent, not on taste. ADR-0002 records that `MainActivity`'s `if`/`else if` navigation
destroys the outgoing screen, and the `SaveableStateHolder` added for scroll retention keys on
`"folder_detail:$path"` — it would not survive `backToFolders`, and it would let the draft and
`isThumbnailPickerMode` disagree, since the flag is ViewModel-owned.

**Add a confirm action separate from the navigation icon** (a checkmark in the bar's `actions`
slot, or a floating button). Rejected: it leaves two controls visible at once — cancel and confirm —
and the request specifies that the single navigation icon changes meaning. A separate confirm also
has to define what the still-present X does after a draft exists, which reintroduces the ambiguity
the icon swap removes.

**Introduce a picker-specific badge composable rather than reusing `selectedItems`.** Rejected: the
existing checkmark is already a blue `CheckCircle` in the grid tile's top-right corner over a white
circle, and reusing the parameter means `MediaGrid`, `MediaList` and `GroupedMediaContent` need no
changes at all. A parallel badge would be a second near-identical glyph to keep in sync.

**Keep the reset item but disable it instead of hiding it when no override exists.** Moot once the
item is removed, and there is no disabled-`DropdownMenuItem` precedent anywhere in this repo.

**Make tapping the drafted item clear the draft (toggle).** Rejected: it would flip the bar back
from the commit arrow to the X, so a single repeated gesture would silently change what the
navigation icon does. Selection-mode toggling exists because a *set* can shrink; a single cover
image cannot be "none".

### Constraints preserved

- **The tap interception stays the first branch of `selectMedia`**, so the picker can never open
  the viewer or mutate the media selection.
- **Mode exclusivity stays bidirectional.** Entering the picker exits both selection modes, and
  entering either selection mode clears the picker *and now also the draft*.
- **The long-press handler stays inert while picking**, so a long press cannot swap in
  `MediaSelectionTopBar` and leave the picker flag set.
- **`SearchTopBar`'s thumbnail parameters remain nullable with `null` defaults**, which is what
  keeps the `FolderListScreen` call site and `FolderListContentTest` untouched.
- **No new string resources.** `thumbnail_picker_title` and `cd_exit_selection` are already used by
  this bar; the commit arrow reuses `cd_back`, which exists in all 10 locales. Only
  `menu_reset_folder_thumbnail` is deleted, from all 10.

### Why the reset affordance was withdrawn

The original argument was that without a reset the user can never return a folder to automatic
selection. That is now judged to overstate the problem: the automatic thumbnail is not a stored
value that can be lost, it is recomputed from `allMedia` on every rebuild of `filteredFolders`, and
the override is one entry the user can overwrite at any time from the same menu. The reset item
also occupied a permanent slot in an already long overflow menu while being useful at most once per
folder, and it was the only conditionally-*hidden* item in that menu, so the menu's contents changed
shape depending on hidden state.

## Amendment 2 — 2026-09-25 — The existing custom thumbnail is pre-selected, via a display-only flow

This amendment **reverses point 4 of Amendment 1** ("There is no pre-selection of the existing
custom thumbnail"). That paragraph is annotated in place above and left intact. Nothing else in
this record changes: the `folder_thumbnails` `SharedPreferences` file, the `FolderThumbnailStore`
interface and in-memory fake, the `Folder.path -> uri` key shape, merge-on-read inside
`GalleryTransformations.filterAndSortFolders`, resolution before the media-type filter,
validating-by-construction fallback, no garbage collection of orphans, all three `MediaType`s being
selectable, draft-and-commit interaction, the icon swap, system back mirroring the navigation icon,
and reuse of the existing `selected_checkmark` all continue to govern verbatim.

This is recorded as a second in-place amendment for the same reason as the first: the persistence
and resolution architecture — the structural part of ADR-0007 — has not stopped applying. What
changes is one initialisation in `GalleryViewModel.enterThumbnailPickerMode()` and one expression in
`FolderDetailContent`.

### What changed

**1. Opening the picker pre-selects the folder's persisted custom thumbnail.** If
`folderThumbnailOverrides[selectedFolder.path]` holds a URI, the item with that URI renders the
existing blue `CheckCircle` the moment the picker opens. If the folder has no override, nothing is
pre-selected and the screen is exactly as it is today.

**2. Only the persisted override is pre-selected — never the automatic thumbnail.** When a folder
has no override, the tile shows whichever item `filterAndSortFolders` encountered first while
walking `allMedia`. That is a derived display value, not a choice the user made, and the picker has
exactly one visual vocabulary — the selection checkmark — which asserts "this is selected". Marking
a system-derived item as selected would misattribute it, and there is no second affordance
available to distinguish "your cover" from "what is currently displayed". The narrower rule also
removes the need to re-derive the effective thumbnail anywhere outside `filterAndSortFolders`; see
"Why the effective thumbnail is not the seed" below.

**3. The pre-selection is a separate, display-only flow; the draft is untouched.**
`folderThumbnailPreselectedUri: StateFlow<Uri?>` is added next to `folderThumbnailDraftUri`. It is
seeded in `enterThumbnailPickerMode()`, never written by `selectMedia`, never read by
`commitFolderThumbnailDraft()`, and cleared by the same `clearThumbnailPickerState()` that clears
the mode flag and the draft. `folderThumbnailDraftUri` keeps its existing meaning exactly: non-null
if and only if the user has tapped an item in this picker session.

This state shape is what dissolves both objections recorded in Amendment 1 point 4, rather than
accepting them as costs:

- The navigation icon still keys off `folderThumbnailDraftUri != null`, so it is still a close (X)
  when the picker opens over a folder that already has a custom cover. The cancel affordance is not
  lost.
- "Open the picker and press back" still takes the no-draft branch and writes nothing. The stored
  value is not rewritten with itself, and — the sharper version of the same hazard — a folder whose
  thumbnail is currently automatic cannot be frozen into an explicit persisted override merely by
  opening the picker and leaving.

The grid renders `setOfNotNull(draftThumbnailUri ?: preselectedThumbnailUri)`: the pre-selection is
shown until the user taps, after which the draft wins permanently for that session.

### Why the effective thumbnail is not the seed

`Folder.thumbnailUri` on `selectedFolder` does carry the effective (override-or-automatic) URI at
the instant the folder is opened — `FolderListScreen` collects `viewModel.filteredFolders`, which
applies the overrides, and passes those `Folder` instances straight into `selectFolder`. It is
nonetheless the wrong source, because `_selectedFolder` is a frozen snapshot: it is assigned only in
`selectFolder` and nulled only in `backToFolders`. `commitFolderThumbnailDraft()` updates
`_folderThumbnailOverrides` but not `_selectedFolder`, so the second time the picker is opened
within one Folder Detail session it would pre-select the *previous* cover rather than the one just
committed. Deleting media from inside Folder Detail can invalidate it the same way.
`_folderThumbnailOverrides` is authoritative and is updated at commit, so it is read directly.

### Alternatives considered in this amendment

**Seed the draft itself from the override and add a boolean "the user has picked something" flow to
drive the top bar.** Behaviourally identical to what was chosen, and rejected on blast radius:
`folderThumbnailDraftUri` would stop meaning "the user picked this", so every existing reader of it
would have to be re-audited — the top bar's `hasDraft` parameter would become a misnomer and need
renaming, `FolderDetailContent`'s `BackHandler` condition would have to be repointed, and
`commitFolderThumbnailDraft()` would become a method that writes whenever a draft exists while
relying entirely on its call sites to be gated. The chosen shape adds one flow and changes one
expression, and leaves every existing member's meaning intact.

**Derive "the user has picked something" by comparing the draft to the seeded value instead of
storing it.** Rejected: tapping item A and then tapping back to the original cover would compare
equal and flip the navigation icon from the commit arrow back to the X. That is the same defect
recorded in Amendment 1 against toggle-to-deselect — a repeated gesture silently changing what the
navigation icon does. It also saves no state, since the seed has to be retained to compare against.

**Pre-select the effective thumbnail (override if present, otherwise the automatic one), so the
picker always opens with exactly one item checked.** Rejected for the misattribution in point 2
above, and because the implementation would have to reproduce `filterAndSortFolders`' resolution
rules — including the FOLDERS-scope media-type filter that selects the automatic item — inside
`GalleryViewModel`, where it would be free to drift from the single pure function that ADR-0007
made the sole owner of that logic. Reading the effective value out of `filteredFolders.value`
instead was also rejected: that flow applies the Folders View search query and the
`showExcludedTemporarily` flag, which `selectFolder` resets, so the current folder can legitimately
be absent from it, and it is `SharingStarted.Lazily`, so its `.value` is the `Loading` sentinel
until something collects it.

**Scroll the grid to the pre-selected item so it is always visible.** Rejected: it would fight the
grid scroll position that `MainActivity`'s `SaveableStateHolder` deliberately retains per folder,
and it would move the viewport without the user asking.

### Consequences

Positive:

- The picker answers "which one is the cover?" without the user having to leave the screen and read
  the folder tile.
- Re-picking becomes a comparison rather than a recall task, and the state after opening the picker
  now matches the state after committing.
- Nothing is written on open, on cancel, or on back-without-a-tap, so the picker remains free to
  enter and leave.

Negative:

- The pre-selected item may not be rendered at all. The picker grid is `filteredMedia`, which
  applies the FOLDER_DETAIL media-type filter and search query, while the override is resolved
  against `allMedia`; a folder filtered to videos with a still image as its cover shows no
  checkmark. A stale override whose item has been deleted behaves the same way. This is **accepted
  and deliberately unhandled**: the alternatives are to override the user's own active filter or to
  scroll the viewport unbidden, both more surprising than a missing checkmark, and the degraded
  behaviour is exactly the pre-amendment behaviour — no checkmark, no error state, cancel still
  writes nothing.
- A folder with no override still opens with nothing checked, so the picker is not uniformly
  "always one item selected". That asymmetry is the point: it is the only signal distinguishing a
  chosen cover from an automatic one.
- One more flow must be cleared in `clearThumbnailPickerState()`. The six sites that clear picker
  state all route through it, so the risk is contained to that one function, but forgetting it
  leaves a stale checkmark in the next folder's picker.


The cost is stated plainly below rather than argued away: **a user who has set a custom cover can no
longer restore the automatic one.** This is a deliberate capability removal, not an oversight.

### Consequences of this amendment

#### Positive

- A mis-tap no longer writes a preference. Under commit-on-tap, the first tap was final and also
  closed the mode, so correcting it meant reopening the picker.
- The bar's icon carries the state: X means "nothing chosen", back-arrow means "choice made, go
  back". The user is told whether a selection has registered, which commit-on-tap never did — the
  old flow's only feedback was the mode closing.
- The draft survives rotation, process-level configuration changes and any navigation, because it
  is ViewModel-owned.
- `GalleryViewModel` gets **smaller**: `selectedFolderThumbnailOverrideUri`,
  `hasSelectedFolderCustomThumbnail`, `setCurrentFolderThumbnail` and `clearCurrentFolderThumbnail`
  are removed and replaced by one draft flow and one commit method, against a record that already
  lists the god-class growth as a negative.
- `FolderDetailContent` loses a parameter on balance, and the `selectedItems` reuse means no
  rendering component changes at all.

#### Negative

- **Automatic thumbnail selection is no longer recoverable through the UI** once a folder has a
  custom cover. Clearing app data or choosing a different item are the only routes.
- **Committing costs one more tap than before.** Choosing a cover is now tap-then-confirm.
- **System back commits rather than cancels**, which inverts the platform's usual reading of the
  gesture. Mitigated by the icon matching the behaviour, and by the action being trivially
  reversible, but a user who expects back to abandon will instead save.
- **The mode has two exits with different outcomes**, distinguished only by an icon. A user who
  taps an image and then wants to abandon has no cancel affordance left — they must commit and
  re-pick.
- **A third piece of ViewModel state is now coupled to picker mode** (`isThumbnailPickerMode`,
  `folderThumbnailDraftUri`, `_folderThumbnailOverrides`), and the draft must be cleared at every
  site that clears the mode flag. Missing one leaves a stale draft that pre-selects an item — the
  very pre-selection point 4 rules out — the next time the picker opens.
- **The draft is not itself persisted across process death.** `onSaveInstanceState` is not involved;
  a ViewModel survives configuration changes, not a process kill. An in-progress, uncommitted choice
  is lost if the process is killed while backgrounded. Accepted: persisting a draft would mean
  writing the very value the draft exists to avoid writing.

### Test impact of this amendment

- `FolderDetailContentTest`: the two `menu_reset_folder_thumbnail` assertions are deleted with the
  feature; the picker-mode test gains draft/no-draft cases asserting the icon swap and the
  `selected_checkmark`.
- `GalleryViewModelTest`: the commit-on-tap case becomes a draft case (mode stays active, store
  untouched), a commit case and a cancel-discards-draft case are added, and the
  `clearCurrentFolderThumbnail` case is deleted.
- `GalleryTransformationsTest` and `FolderThumbnailStoreTest` are untouched — nothing below the
  ViewModel changed.

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

- ADR-0002: ViewModel-owned transient viewer state across configuration changes. Still `Active`.
  Amendment 1 applies its rule to the picker draft: `MainActivity` has no back stack, so screen-local
  `remember` does not survive navigation and transient mode state belongs in `GalleryViewModel`.
- ADR-0005: Selection-scoped media actions mirror the full-screen viewer (single-item action gating
  precedent, and the precedent for amending an `Active` record in place rather than superseding it).
- ADR-0006: Per-folder display preference overrides layered over global defaults (path-keyed sparse
  overrides merged on read; orphan-entry precedent). Not superseded by this record.
- `app/src/main/java/com/davide/seddio/easygallery/data/GalleryTransformations.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderThumbnailStore.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/ThumbnailPickerTopBar.kt` — the
  picker bar whose navigation icon swaps between cancel and commit (Amendment 1)
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt` — the overflow
  menu the reset item was removed from (Amendment 1)
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaGridItem.kt`,
  `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaListItem.kt` — the existing
  `selected_checkmark` affordance reused to render the draft (Amendment 1)
- `app/src/main/java/com/davide/seddio/easygallery/ui/theme/Color.kt`,
  `app/src/main/java/com/davide/seddio/easygallery/ui/theme/Theme.kt` — `BrandBlue` is bound to
  `colorScheme.primary`, which is what makes the existing checkmark the brand blue (Amendment 1)
