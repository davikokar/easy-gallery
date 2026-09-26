# ADR-0008: Implicit camera-folder sort default resolved beneath explicit preferences

- Status: Active
- Date: 2026-09-26
- Decision makers: Easy Gallery project maintainer

## Context

The app-wide default for Folder Detail View is `SortType.NAME` / `SortOrder.ASCENDING`
(`ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL)`). That is a reasonable default for a
folder of documents, downloads or scans, and a poor one for the camera roll, where the useful
ordering is newest first. A user who opens the camera folder on a fresh install sees
`IMG_20190103_...` above a photo taken this morning, and must open the sort dialog before the view
is usable.

ADR-0006 already established the resolution model for this view: a persisted global
`ViewPreferences` for `PreferenceScope.FOLDER_DETAIL`, with a sparse, persisted
`FolderViewOverrides` per `Folder.path` merged over it on read via
`FolderViewOverrides.applyTo(base)`. That model has exactly two layers, and both of them are
persisted. There is no place in it to express "this folder starts out differently, until the user
says otherwise" — the only tool available is a real override, which is a *user decision*, not a
default.

Forces shaping the response:

- **A camera default must not behave like a user decision.** ADR-0006 gives the user a single
  destructive escape hatch — unchecking "apply only to this folder", confirming, and having the
  chosen value written globally while that group's overrides are deleted from *every* folder. Any
  camera behaviour that survives that action makes the confirmation dialog a lie.
- **Android does not identify camera folders.** `android.os.Environment` documents
  `DIRECTORY_DCIM` as "The traditional location for pictures and videos when mounting the device
  as a camera. Note that this is primarily a convention for the top-level public directory". There
  is no `DIRECTORY_CAMERA` constant, and `MediaStore` exposes no column, flag or bucket attribute
  that marks a directory as camera output. Whatever the app does here is a heuristic, and the
  record should say so rather than imply platform support exists.
- **Per-file metadata reads are forbidden.** ADR-0001 rules out eager EXIF reads during media
  queries because `getAllMedia()` returns every image and video on the device, turning a cursor
  walk into O(n) file opens. Identifying a camera folder by inspecting EXIF `Make`/`Model` of its
  contents is therefore not available.
- **`PreferenceScope` is closed.** ADR-0006 already rejected widening it; it is an `enum` consumed
  exhaustively by `ViewPreferences.defaultFor` and by `PreferenceScope.entries.associateWith { }`
  maps in `DisplayPreferencesState`.
- **Existing installs must not be disturbed.** Users already running on a non-default folder-detail
  sort must not have the camera folder silently re-sort itself after an app update.
- **Resolution logic must stay Android-free.** Per ADR-0004, ADR-0005 and ADR-0006, merge logic is
  pure Kotlin so it unit-tests on a plain JVM without Robolectric or an emulator.

## Decision

Folder Detail View resolves its effective preferences from **three** layers, lowest to highest
precedence:

1. **Global `ViewPreferences` for `PreferenceScope.FOLDER_DETAIL`** — ADR-0006 layer 1, persisted
   in `display_preferences`.
2. **Implicit camera default** — `sortType = SortType.DATE_TAKEN`,
   `sortOrder = SortOrder.DESCENDING`. **New in this ADR.** Applied only when both conditions hold:
   the folder is identified as a camera folder by the path heuristic below, **and** the user has
   not explicitly retargeted the global folder-detail sort at `PreferenceApplyTarget.ALL_FOLDERS`.
   It is **computed on read and never written to any store.**
3. **Explicit `FolderViewOverrides` for the folder's path** — ADR-0006 layer 2, persisted in
   `folder_display_preferences`.

Expressed as:

```
explicit.applyTo(implicit.applyTo(global))
```

The implicit layer is an ordinary `FolderViewOverrides` instance — empty (identity) for every
folder that is not a camera folder, or for every user who has taken the global-sort branch. No new
merge machinery, no new data type and no new store is introduced. `applyTo` is already
null-coalescing per field, so an empty implicit layer is a no-op by construction.

**The implicit layer affects the SORT group only.** It never sets `columns`, `groupBy`,
`groupOrder`, `viewType` or `mediaTypes`. Those fields stay `null` in the implicit overrides.

### Camera folder identification

There is no Android API for this. The following is a **path heuristic**, and is recorded as such.

Normalize `Folder.path`: replace `\` with `/`, collapse repeated separators, drop a trailing
separator. Split into segments. Compare case-insensitively.

A folder is a camera folder **iff** its parent segment is `DCIM` **and** its own segment is either:

- `Camera` — the de-facto convention across AOSP, Samsung, Xiaomi, OnePlus and Pixel builds; or
- a **JEITA DCF directory**, matching `^[0-9]{3}[0-9A-Za-z_]{5}$` — a three-digit directory number
  followed by five free characters (`100ANDRO`, `100MEDIA`, `100APPLE`, `100OPLUS`, …).

The rule is volume-agnostic: only the last two segments are examined, so it matches on
`/storage/emulated/0/DCIM/Camera`, `/storage/emulated/10/DCIM/Camera` (secondary user profiles) and
`/storage/XXXX-XXXX/DCIM/100ANDRO` (removable media) alike.

### "Explicitly set the global sort" is derived by comparison, not by key presence

Whether the user has retargeted the global folder-detail sort **cannot** be read from
SharedPreferences key presence: `SharedPreferencesDisplayStore.save()` writes its entire key bundle
— all eight keys — on every setter, so the sort keys exist on disk as soon as the user changes any
unrelated preference such as column count.

It is instead derived by comparing the stored `FOLDER_DETAIL` `sortType`/`sortOrder` against
`ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL)`. The implicit layer applies only while
the stored global sort still equals the app default. Consequently, an existing install already
sitting on a non-default folder-detail sort is treated as having chosen it, and never sees its
camera folder silently re-sort itself after an update.

### Relationship to ADR-0006

ADR-0006 remains **Active and unamended**. Every clause of it continues to govern verbatim: the
two-layer persisted model, the five overridable groups, the draft-and-commit dialogs, the
`folder_display_preferences` store, and the "apply to all folders" confirm-and-reset semantics.
This ADR is purely additive — it inserts a third layer *beneath* ADR-0006's explicit override layer
and changes nothing about how that layer is written, read or cleared.

## Alternatives Considered

**Persist a real `FolderViewOverrides` for the camera folder when the folder is first discovered.**
Rejected on three counts. It is a forced override, not a default — the user never chose it, yet it
is indistinguishable from a choice they did make. It would be re-created after ADR-0006's
`ALL_FOLDERS` reset, so "apply to all folders" would demonstrably not apply to the camera folder.
And it freezes the value at whatever was written on discovery day, so a later change to the app
default can never reach that folder — exactly the failure ADR-0006 rejected for per-folder full
bundles.

**Place the camera layer *above* the global layer unconditionally.** Rejected for the same
"apply to all folders" violation, in its most visible form: the user unchecks the box, reads a
confirmation naming the sort setting, confirms a destructive action — and then watches the camera
folder ignore it. The camera layer must yield to a deliberate global sort choice, which is why it
is gated on the global sort still equalling the app default.

**Match by bucket name `Camera` instead of path.** Rejected. It is not localization-safe; it also
matches `Pictures/Camera` and any user-created folder named "Camera" anywhere on the device; and it
contradicts ADR-0006's ruling that folder identity in this app is `Folder.path`, which would give
the codebase two competing notions of folder identity.

**Treat any `DCIM/*` child as a camera folder.** Rejected. `DCIM` is a dumping ground for
third-party apps — `DCIM/Facebook`, `DCIM/Instagram`, `DCIM/Screenshots`, `DCIM/Restored` — none of
which are camera output, and several of which the user is likely to want sorted differently.

**Add a fourth `PreferenceScope` for camera folders.** Rejected for the reason already recorded in
ADR-0006: `PreferenceScope` is a closed `enum` consumed exhaustively by `ViewPreferences.defaultFor`
and by eagerly-built per-scope maps, and opening it up for one folder-shaped distinction is
disproportionate.

**Also default `groupBy` to `DATE_TAKEN_DAILY` for camera folders.** Rejected as out of scope: the
request was about sort order only, and widening the implicit layer to further groups is a purely
additive change that can be made later without revisiting the resolution model.

## Consequences

### Positive

- A camera folder is newest-first on first open, with no user action and no stored state.
- The implicit layer costs zero bytes of persistence and is impossible to orphan, because nothing
  is written.
- A folder with an explicit sort override still wins — the user's explicit choice is never
  overridden by a heuristic.
- No new merge machinery: the implicit layer is a `FolderViewOverrides`, so it reuses `applyTo`,
  and the empty instance is the identity element.
- Resolution remains one pure function over plain data classes, unit-testable on a plain JVM
  alongside ADR-0006's existing tests. The path heuristic is string manipulation with no Android
  dependency.
- Changes to the app-wide default automatically reach camera folders, since nothing was frozen.

### Negative

- The heuristic deliberately **excludes the `DCIM` root itself and every other `DCIM/*` child** —
  `DCIM/Facebook`, `DCIM/Screenshots`, `DCIM/Restored`, `DCIM/Instagram`. These are third-party
  dumping grounds rather than camera output, and sorting them newest-first is not obviously
  correct.
- It **will not match** camera apps that write to `DCIM/OpenCamera`, to `Pictures/<Vendor>`, or to
  a localized folder name. Those users get the app-wide default and set the sort for that folder in
  one dialog. This is an accepted, self-correcting miss: the cost of a miss is one interaction,
  paid once.
- It **cannot be improved by reading EXIF `Make`/`Model`.** ADR-0001 forbids eager per-file
  metadata reads because they cost O(n) file opens across the whole device library.
- The implicit default is **invisible in the UI.** A camera folder that has never been configured
  looks identical to one that has; there is no badge, hint or "using camera default" indicator.
- **The camera exception is permanently off once the user commits a folder-detail sort with
  `ALL_FOLDERS`**, and there is no affordance to restore it. This is deliberate and consistent with
  ADR-0006, which likewise offers no "reset this folder".
- The default sorts on `MediaItem.dateAdded`, **not true EXIF capture time**. `SortType.DATE_TAKEN`
  already maps to `dateAdded` in `GalleryTransformations.sortMedia`; that mapping is pre-existing
  and is deliberately **not** fixed here. Re-imported, restored or copied camera files therefore
  sort by import time rather than capture time.
- **Bucket-name collision is inherited from ADR-0006, not introduced here.**
  `MediaStoreDataSource.getFolders()` aggregates by `BUCKET_DISPLAY_NAME` and keeps the first path
  it sees, so two distinct directories named `Camera` collapse into one `Folder` and the implicit
  default may be evaluated against the wrong path. Referenced for completeness; no attempt is made
  to fix it in this decision.

## References

- ADR-0006 — per-folder display preference overrides; **extended, not superseded**, by this record
- ADR-0001 — on-demand media location metadata; the prohibition on eager per-file metadata reads
- `app/src/main/java/com/davide/seddio/easygallery/data/ViewPreferences.kt` —
  `defaultFor(PreferenceScope.FOLDER_DETAIL)`
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderViewOverrides.kt` — `applyTo`,
  `PreferenceApplyTarget`, `OverridablePreference`
- `app/src/main/java/com/davide/seddio/easygallery/data/DisplayPreferencesStore.kt` —
  `SharedPreferencesDisplayStore.save()` writes the whole key bundle on every setter
- `app/src/main/java/com/davide/seddio/easygallery/data/GalleryTransformations.kt` —
  `sortMedia` maps `SortType.DATE_TAKEN` to `MediaItem.dateAdded`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — `getFolders()`
  bucket-name aggregation
- [`android.os.Environment.DIRECTORY_DCIM`](https://developer.android.com/reference/android/os/Environment#DIRECTORY_DCIM)
  — documented as "primarily a convention"; no `DIRECTORY_CAMERA` exists
