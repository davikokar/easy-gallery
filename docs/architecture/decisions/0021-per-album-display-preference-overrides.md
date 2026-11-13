# ADR-0021: Per-album display preference overrides reuse the per-folder override model

- Status: Active
- Date: 2026-11-13
- Decision makers: Easy Gallery project maintainer

## Context

[ADR-0006](0006-per-folder-display-preference-overrides.md) lets the user apply a display
preference to one folder only: the five draft-and-commit dialogs carry an "Apply only to this
folder" checkbox, and the choice is stored as a sparse `FolderViewOverrides` keyed by `Folder.path`
and merged on read over the global `FOLDER_DETAIL` bundle. Its Decision point 2 confined that to
Folder Detail View, because at the time the only other scopes were Folders View and Timeline View,
where "this folder" has no referent.

[ADR-0013](0013-albums-as-third-display-mode.md) later added `PreferenceScope.ALBUM_DETAIL`. Album
Detail View — which also renders Favourites, through `AlbumDetailScreen` with
`isFavouritesAlbum = true` — exposes the same five dialogs, and pinch-to-zoom, but every change
writes the one global `ALBUM_DETAIL` bundle. Albums are no more interchangeable than folders: a
trip album wants date-ascending and daily grouping, a screenshots album a dense grid, a favourites
collection a list. The request is the same behaviour as Folder Detail View: change a preference
inside one album and apply it to that album only.

Forces shaping the response:

- **The model already exists and is pure.** `FolderViewOverrides`, its `applyTo` / `cleared` /
  `with*` operations, `OverridablePreference`, `PreferenceApplyTarget` and `PreferenceScopeSelector`
  are container-agnostic in everything but their names. `FolderViewPreferencesStore` and
  `FolderViewPreferencesState` are keyed by an opaque `String`. A parallel album implementation
  would duplicate all of it.
- **The folder store cannot be shared.** `FolderViewPreferencesStore.clear(group)` deletes a field
  from **every key in its file** — that is how "apply to all folders" works. Album keys in
  `folder_display_preferences` would be wiped by a folder-wide reset, and the reverse.
- **Favourites has no album row.** It is `MediaStore`-backed
  ([ADR-0011](0011-favourites-backed-by-mediastore.md)), has no `AlbumEntity` and no id, yet is
  shown by the same screen and reads the same scope.
- **Album ids are never reused.** `AlbumEntity.id` is `@PrimaryKey(autoGenerate = true)`, which
  Room emits as `INTEGER PRIMARY KEY AUTOINCREMENT` (see `app/schemas/.../EasyGalleryDatabase/1.json`),
  so SQLite never hands a deleted album's id to a new one. The database has no destructive
  migration fallback.
- **Album deletion is an in-app event with an exact id.** Unlike a folder renamed or deleted
  outside the app — the reason ADR-0006 accepted orphaned folder overrides — every album deletion
  goes through `AlbumsViewModel.deleteAlbum(albumId)`.
- **`GalleryViewModel` must not grow** (`AGENTS.md`, *State ownership*). The global `ALBUM_DETAIL`
  bundle lives in its `DisplayPreferencesState`; the open album lives in `AlbumsViewModel`, and the
  Favourites flag in `MainActivity`.

## Decision

Album Detail View gains a **per-album override layer that reuses ADR-0006's model unchanged**, in a
store of its own, owned by `AlbumsViewModel`.

**1. Two layers, no implicit default.** The effective preferences for an open album, or for
Favourites, are its sparse `FolderViewOverrides` applied over the global `ALBUM_DETAIL`
`ViewPreferences`. There is no middle layer: [ADR-0008](0008-camera-folder-default-sort-order.md)'s
camera default is a property of a folder path and has no album equivalent. The composition is a
pure, Android-free function in `data/FolderViewOverrides.kt`, beside the Folder Detail resolver, so
it is unit-tested on a plain JVM and resolved in one place.

**2. Keys.** An album's overrides are keyed by the decimal string of `AlbumEntity.id`; Favourites'
by the reserved literal `favourites`, which can never equal a decimal id. Neither contains the
store's `/` key separator. Album names are not used: they are renamable.

**3. The same five groups.** Sort, columns, group by, filter media and view type, exactly as
ADR-0006 point 3. `showInfo` and search stay global per scope.

**4. Its own `SharedPreferences` file, through the existing store.** Overrides persist in a new
file, `album_display_preferences`, through the existing `FolderViewPreferencesStore` interface and
its existing `SharedPreferences*` and `InMemory*` implementations, the production one taking its
file name as a parameter. The folder file keeps its name and contents. This is the seventh
preferences file.

**5. Owned by `AlbumsViewModel`.** It holds a second `FolderViewPreferencesState` over that store
and exposes the override map. `AlbumDetailScreen` resolves the effective preferences from the global
bundle (read from `GalleryViewModel`, as today) and that map. `GalleryViewModel` gains nothing.

**6. The same interaction.** The five dialogs show "Apply only to this album" in Album Detail View,
including in Favourites, checked by default on every opening. Unchecking it and confirming writes
the global `ALBUM_DETAIL` value and then clears that group's override from **every** album and from
Favourites, leaving other groups intact — ADR-0006 points 5 and 6 with "album" for "folder".
Pinch-to-zoom in Album Detail View writes an album override computed from the effective column
count, as ADR-0006 point 7 does for folders; otherwise pinching an album with a column override
would appear to do nothing.

**7. Deleting an album deletes its overrides.** `AlbumsViewModel.deleteAlbum` removes that key
after the album row is deleted. Nothing prunes overrides against the album list on load: the list
starts empty, so a load-time prune would erase every override on a cold start. Favourites' entry is
never deleted.

**8. Backup is unchanged.** The new file falls under the existing rules and is backed up, like
every preferences file except `media_index_run_state`
([ADR-0014](0014-album-database-included-in-auto-backup.md) and its Amendment 1). Its keys are
album ids, and the album database is backed up and restored with it, so restored overrides find the
albums they were saved for.

**9. Names are not changed.** `FolderViewOverrides`, `FolderViewPreferencesStore`,
`FolderViewPreferencesState` and `PreferenceApplyTarget.CURRENT_FOLDER` / `ALL_FOLDERS` keep their
names; for albums, "folder" reads as "the open container" and "all folders" as "every container of
that kind". A rename is mechanical and may be done separately; it is not part of this decision.

## Alternatives Considered

**Store album overrides in `folder_display_preferences` under a prefixed key.** Rejected:
`clear(group)` is file-wide, so "apply to all folders" would erase every album's override of that
group, and "apply to all albums" every folder's. Keeping the two in step would need key filtering
inside a store whose whole contract is "one file, one key space".

**A table, or columns, in the album database.** Rejected. Favourites has no row, so a foreign key
cannot cascade for it and it would need a second home anyway. It needs a schema migration and a
second persistence implementation of a model that already has one. And the album database holds
what the user built — references and intent ([ADR-0009](0009-room-as-first-owned-datastore.md));
every display preference in the app lives in `SharedPreferences`, which ADR-0009 deliberately left
in place. The one thing Room would add, cascade on delete, point 7 gives explicitly.

**A parallel `AlbumViewOverrides` model, store and state holder.** Rejected: a copy of three
classes whose behaviour must stay identical to the originals, which is exactly how the folder and
picker resolution once drifted before ADR-0016 Amendment 1 shared it.

**Hold the overrides in `GalleryViewModel`, beside the folder ones.** Rejected: it does not know
which album is open, and `AGENTS.md` directs new state away from it. Albums added no album state to
it, and this keeps that true.

**Accept orphans, as ADR-0006 does for folders.** Rejected as unnecessary: a folder disappears
outside the app with no event to hook, while an album is deleted through one method that knows its
id. Because ids are never reused an orphan would be harmless, so point 7 is hygiene rather than a
correctness guarantee — an override left behind by an interrupted deletion is inert.

**Rename the shared types to container-neutral names in the same change.** Deferred, not rejected:
it widens the diff into `GalleryViewModel`, `FolderDetailScreen` and their tests for no behaviour,
and the instrumented tests pass these values by name.

## Consequences

### Positive

- Each album, and Favourites, can be configured independently while unconfigured ones track one
  editable global default — the same behaviour, wording and confirmation the user already knows
  from folders.
- No new model, store implementation, or state-holder class. The override semantics cannot drift
  between folders and albums because there is one implementation of them.
- `GalleryViewModel` is untouched.

### Negative

- A seventh `SharedPreferences` file, and every document that counts them goes stale.
- Album Detail View's preferences come from two ViewModels — the global bundle from
  `GalleryViewModel`, the overrides from `AlbumsViewModel` — and are composed in the screen. The
  "apply to all albums" branch writes to both, global first, so the screen has to get that order
  right; it is the one part of the flow not covered by a unit test of the ViewModel alone unless
  the commit entry point takes the global write as a parameter.

  > **Amended 2026-11-13 — see Amendment 1 below.** The entry point does take it, so the order is
  > the ViewModel's and is unit-tested.
- Type names say "folder" where they now also mean album, until someone renames them.
- ADR-0006's negatives carry over: no indicator that an album is overridden, and no "reset this
  album" action.

## References

- [ADR-0006](0006-per-folder-display-preference-overrides.md) — the model reused here; its point 2
  is extended, not superseded
- [ADR-0008](0008-camera-folder-default-sort-order.md) — the folder-only middle layer albums do not
  have
- [ADR-0009](0009-room-as-first-owned-datastore.md), [ADR-0011](0011-favourites-backed-by-mediastore.md),
  [ADR-0013](0013-albums-as-third-display-mode.md), [ADR-0014](0014-album-database-included-in-auto-backup.md)
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderViewOverrides.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/FolderViewPreferencesStore.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderViewPreferencesState.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/PreferenceScopeSelector.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/AlbumsViewModel.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/AlbumDetailScreen.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/AlbumEntities.kt` — `autoGenerate` ids

## Amendment 1 — 2026-11-13 — What landed, and a test this record should have cited

This amendment records two details of the implementation that the decision left open or did not
mention, and one argument it left out. The decision is unchanged and continues to govern; the
original text above is left intact.

**The global write is a parameter, so the ViewModel owns the order.** Consequences → Negative named
the one condition under which the "apply to all albums" order would be unit-testable in the
ViewModel alone. It was met. `AlbumsViewModel`'s five `commitAlbum*Preference` entry points take
the album's key, the value, a `PreferenceApplyTarget` and a `writeGlobal` callback, which
`AlbumDetailScreen` fills with the `GalleryViewModel` write to `ALBUM_DETAIL`. `CURRENT_FOLDER`
writes the album's override and never calls the callback; `ALL_FOLDERS` calls it first and then
clears the group from every key, Favourites' included. `AlbumsViewModelTest` checks that the
overrides are still intact when the callback runs. So the negative's risk is closed, and what the
screen is left to get right is only which write it passes. `PreferenceApplyTarget` keeps its folder
names, as point 9 decided. The pure resolver point 1 describes is
`resolveAlbumDetailPreferences(global, overrides)`.

**Before the open album is known, its key is null.** Point 6 says pinch-to-zoom writes an album
override. `AlbumDetailScreen` derives the key from `selectedAlbum`, which can still be null for a
moment after the album is opened; Favourites' key is a constant and is never null. While it is
null the screen shows the global bundle, a "this album" commit does nothing — an "all albums"
commit still writes the global value and clears the group everywhere, because it needs no key —
and pinch-to-zoom falls back to the global `ALBUM_DETAIL` column count, as it did before this
decision. So a pinch is never silently dropped, but in that window, and only for an Album, it
changes the default for every album rather than this one.

**A test this record should have cited.** [ADR-0018](0018-media-index-in-a-second-database.md)
Amendment 2 ends: "This record does not license a seventh preferences file. The same two tests
apply next time, in the same order: structured goes in a database; flat and disposable may stay."
Point 4 created the seventh without naming that sentence, and on its first test the new file is
structured — a key per album. It was weighed all the same, under *Alternatives Considered*, as the
album-database option, and rejected on grounds that test does not reach: Favourites has no row to
key a table by; the same structure has lived in `folder_display_preferences` since ADR-0006, which
[ADR-0009](0009-room-as-first-owned-datastore.md) left in `SharedPreferences` on purpose; and
nothing's correctness depends on the file — losing it costs an album its layout and nothing else,
the property ADR-0018 Amendment 2 itself treats as decisive. ADR-0018's sentence is not overridden:
a preferences file is still not licensed by precedent alone, and the next one must answer the same
two tests as this one now has.
