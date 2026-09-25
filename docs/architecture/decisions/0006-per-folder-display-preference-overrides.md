# ADR-0006: Per-folder display preference overrides layered over global defaults

- Status: Active
- Date: 2026-09-25
- Decision makers: Easy Gallery project maintainer

## Context

ADR-era refactoring (2026-09-21, no ADR) split display preferences into three independent
`PreferenceScope`s — `FOLDERS`, `TIMELINE`, `FOLDER_DETAIL` — each owning one `ViewPreferences`
bundle persisted by `SharedPreferencesDisplayStore` under the `display_preferences` file. That
fixed cross-view bleed, but it left a single `FOLDER_DETAIL` bundle shared by **every** folder:
sorting `Camera` by date and switching it to a 5-column grid also re-sorts and re-columns
`Screenshots`, `WhatsApp Images` and every other folder the user opens.

Folders are not interchangeable. A camera roll wants date-descending, dense grid, daily grouping;
a scanned-documents folder wants name-ascending list view; a folder of videos wants a media-type
filter that would hide everything in a photo folder. The request is therefore that `sort by`,
`column count`, `group by`, `filter media` and `change view type` become per-folder, while still
having a usable global default for the (many) folders the user never configures individually.

Forces shaping the response:

- **Folder count is unbounded and user-driven.** Persisting a full `ViewPreferences` bundle for
  every folder the user merely *visits* would write 8 keys × N folders to SharedPreferences and
  would permanently freeze each folder at whatever the global default happened to be on the day
  it was first opened — later changes to the global default would never reach it.
- **`showInfo` is not in scope.** The request names five preferences. `showInfo` is toggled from
  a top-bar icon with no dialog, has no natural "apply to all" affordance, and stays global.
- **Existing per-folder persistence precedent.** `FolderPreferencesStore` already persists pinned
  and excluded folder *paths* in a separate `folder_preferences` file, keyed by `Folder.path`.
  Folder identity in this app is `Folder.path` everywhere it matters (selection, pinning,
  exclusion), so a new store must use the same key.
- **The dialogs currently write on selection, not on OK.** `SortDialog`, `GroupByDialog`,
  `ColumnCountDialog` and `ViewTypeDialog` invoke their setter directly from the radio/cell click;
  `ColumnCountDialog` and `ViewTypeDialog` have no confirm button at all. Only `FilterMediaDialog`
  holds a draft and commits on OK. A scope choice that is only known at OK-time cannot gate a
  write that has already happened.
- **The module does not set `testOptions.unitTests.isReturnDefaultValues`.** Per ADR-0004 and
  ADR-0005, resolution logic must stay Android-free to be unit-testable on a plain JVM.
- **`GalleryViewModel.preferences(scope)` is the single read path** used by both screens and by
  every derived flow. Anything that changes what a folder *effectively* sees can be introduced
  behind that one accessor without touching screen signatures.

## Decision

Per-folder display preferences are modelled as a **sparse override layer resolved on read over the
global `FOLDER_DETAIL` bundle** — not as a per-folder copy of `ViewPreferences`.

**1. Two-layer model.** The persisted state for Folder Detail View is:

- the existing global `ViewPreferences` for `PreferenceScope.FOLDER_DETAIL` (the default), plus
- a sparse `FolderViewOverrides` per folder path, with every field nullable and absent by default.

The effective preferences for a folder are `overrides.applyTo(globalFolderDetailPreferences)`,
computed in a `combine` in the ViewModel. A folder with no override is not stored at all and
therefore continues to track the global default as it changes. `FolderViewOverrides` and
`applyTo` are pure Kotlin, Android-free, and unit-tested on a plain JVM.

**2. Override scope is Folder Detail View only.** `FOLDERS` and `TIMELINE` are not folder-scoped —
the Folders View shows all folders and the Timeline shows all media across folders, so "this
folder" is undefined there. Those two scopes keep today's behaviour exactly, and the "Apply only
to this folder" checkbox is **not rendered** for them.

**3. Only five preference groups are overridable**, matching the five dialogs:

| Group | Fields | Dialog |
|-------|--------|--------|
| Sort | `sortType`, `sortOrder` | `SortDialog` |
| Columns | `columns` | `ColumnCountDialog` |
| Group by | `groupBy`, `groupOrder` | `GroupByDialog` |
| Filter media | `mediaTypes` | `FilterMediaDialog` |
| View type | `viewType` | `ViewTypeDialog` |

`showInfo` and search state are explicitly excluded and stay global per scope. Sort type and sort
order are one group because one dialog owns both; the same holds for group-by and group order.
Overriding a group writes all of that group's fields, so a group is never half-overridden.

**4. The five dialogs become draft-and-commit.** Every one of them holds its selection in local
state and writes only when OK is pressed; Cancel discards. This is required — the target scope is
not known until OK — and it is applied uniformly to all three views rather than only to Folder
Detail View, so one dialog never has two write semantics depending on where it was opened from.

**5. "Apply only to this folder" is checked by default, per dialog opening.** The checkbox state
is UI-local, reset to checked every time a dialog opens. It is deliberately not persisted: the
destructive branch must be re-chosen explicitly each time, never inherited from a past session.

**6. Unchecking is a per-group global reset, confirmed before it happens.** When the box is
unchecked and OK is pressed, a confirmation dialog naming the affected setting is shown. On
confirm, and only then:

- the chosen value is written to the global `FOLDER_DETAIL` `ViewPreferences`, and
- **the overridden fields of that group are deleted from every folder's overrides**, including the
  current folder.

Overrides belonging to *other* groups are left intact — changing "sort by" for all folders does
not reset anyone's column count. A folder whose `FolderViewOverrides` becomes fully empty is
removed from the store entirely.

**7. Column changes made by pinch-zoom write a folder override.** Pinch-to-zoom in Folder Detail
View has no dialog and therefore no checkbox; it takes the default branch ("apply only to this
folder"). Pinch in Folders View and Timeline View is unchanged.

**8. Overrides live in their own SharedPreferences file, `folder_display_preferences`**, behind a
`FolderViewPreferencesStore` interface with an in-memory implementation for tests, mirroring
`DisplayPreferencesStore` and `FolderPreferencesStore`. They are not merged into
`display_preferences`: that file is keyed by `PreferenceScope` and has a fixed, bounded key set,
whereas this one has an unbounded, user-data-derived key set that must support bulk deletion of
one field across all keys.

## Alternatives Considered

**Store a full `ViewPreferences` per folder, no global layer.** Rejected: it materialises a record
for every folder merely visited, and a folder configured once is frozen forever — a later change
to the default can never reach it. It also makes "apply to all folders" mean "rewrite N complete
records", which is far more destructive than the user's request implies.

**Add a fourth `PreferenceScope` per folder (`FOLDER_DETAIL:<path>`).** Rejected: `PreferenceScope`
is a closed `enum` consumed exhaustively by `ViewPreferences.defaultFor` and by
`DisplayPreferencesState`'s `PreferenceScope.entries.associateWith { ... }` maps. Making it an
open/parameterised type would force every `when` and every eagerly-built per-scope map to become
dynamic, for a distinction that only exists in one of the three views.

**Make unchecking clear *all* overrides for *all* folders, not just the edited group.** Rejected as
disproportionate: the user is editing one setting in one dialog, and the confirmation text can
only honestly name that setting. Wiping a folder's carefully chosen column count because the user
standardised sort order would be a surprise deletion of unrelated data.

**Make unchecking set the global default but leave existing overrides alone.** Rejected: it does
not match the requested wording ("it would overwrite all folders previously set preferences"), and
it would be indistinguishable from the checked branch for every already-configured folder — the
user would press OK, confirm a scary warning, and observe no change in the folders they cared
about.

**Keep the dialogs writing live and put the scope checkbox in a menu or in Settings.** Rejected:
a live write cannot be retargeted after the fact, so the first radio tap would already have
mutated the global default — exactly the bleed this ADR exists to remove.

**Persist the checkbox state as a user preference.** Rejected: a sticky unchecked box turns an
opt-in destructive action into the silent default, and the confirmation dialog would fire on every
subsequent preference change.

**Key overrides by `Folder.name` (bucket name) instead of `Folder.path`.** Rejected: paths are what
`FolderPreferencesStore`, folder selection, pinning and exclusion already use, so bucket-name
keying would introduce a second, conflicting notion of folder identity.

## Consequences

### Positive

- Each folder can be configured independently while unconfigured folders keep tracking a single
  global default that remains editable.
- Storage cost is proportional to the number of folders the user actually customises, not to the
  number of folders on the device.
- Resolution is one pure function over two plain data classes, unit-testable without Robolectric,
  MockK or an emulator.
- Screens keep reading `viewModel.preferences(PreferenceScope.FOLDER_DETAIL)`; the override layer
  is applied behind that accessor, so no screen-level read path changes.
- The destructive branch is opt-in, named, and confirmed.

### Negative

- Folder Detail View now has two sources of truth for the same visible setting. A user who changes
  the global default will not see it in folders they have already customised, and the UI gives no
  indication that a folder is overridden. No "reset this folder" affordance is offered in this
  decision.
- Sort/group-by/column/view-type dialogs stop applying live in **all** views, including Folders
  and Timeline where nothing else changes. This is a deliberate UX regression traded for a single
  consistent write semantic.
- Overrides are keyed by `Folder.path`. `MediaStoreDataSource.getFolders()` aggregates by bucket
  name and records the first path seen for that bucket, so two distinct directories sharing a
  bucket name already collapse into one `Folder` and will therefore share one override. This is a
  pre-existing modelling limitation that this ADR inherits rather than introduces.
- Renaming or deleting a folder orphans its override entry. No garbage collection is performed;
  entries are small, but the store grows monotonically.
- `filteredMedia` and `groupedFolderMedia` gain inputs to their `combine`, widening the set of
  changes that trigger a re-sort of the current folder's media.

## References

- `app/src/main/java/com/davide/seddio/easygallery/data/ViewPreferences.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/DisplayPreferencesStore.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderPreferencesStore.kt` (per-folder
  persistence precedent)
- `app/src/main/java/com/davide/seddio/easygallery/ui/DisplayPreferencesState.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt`
- ADR-0004 and ADR-0005 — pure, Android-free logic kept separate so it unit-tests on a plain JVM
- ADR-0005 — gate and act on the same value
