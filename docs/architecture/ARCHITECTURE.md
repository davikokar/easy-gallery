# Easy Gallery — Architecture

Easy Gallery is a native Android image and video gallery app built entirely with Jetpack Compose.
It browses the media already present on the device via the Android `MediaStore`, organised by
folder, and lets the user view, sort, group, filter, search, and manage that media. It keeps no
copy of the user's media. Since albums landed it owns a small Room database, but that database
holds references and user intent only — never media.

- **Package:** `com.davide.seddio.easygallery`
- **Language:** Kotlin · **UI toolkit:** Jetpack Compose (Material 3)
- **SDK levels and dependency versions:** [`app/build.gradle.kts`](../../app/build.gradle.kts) and
  [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) own them. Do not restate a
  version here; it will rot.
- **Data sources:** Android `MediaStore` for all media; a Room database for albums;
  `SharedPreferences` for the language override and user preferences. See *Persistence* below.
- **Network/account:** none, except Google Play Billing for the optional tip jar.
- **Dependency injection:** none — components are instantiated directly, so tests pass fakes.

> **This document describes structure, not decisions.** Choices with lasting impact are recorded
> as ADRs in [decisions/](decisions/) and indexed in [README.md](README.md). An active ADR is
> binding and outranks this file. Where a section below is governed by one, it says so — follow
> the link rather than inferring the rule from the summary here.
>
> **It is maintained, not archived.** The Planner owns it, and a change that adds a state holder,
> a preference store, a navigation branch, a main view, a `data/` file, or a tech-stack dependency
> must update it in the same change — *after* the work lands, so it describes what was actually
> built. The full trigger list is in
> [the architecture documentation instructions](../../.github/instructions/architecture-docs.instructions.md).
> Section numbering is cited by those triggers, so keep it stable.

---

## 1. The main views

The app has **five main views**, carried by **three screens**. These names are canonical; the full
vocabulary is in
[.github/instructions/ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).

| Canonical name | What it shows | Implementation |
|---|---|---|
| **Folders View** | Every device folder ("bucket") as a grid or list, with a thumbnail and item count. The view the app opens on. | `FolderListScreen` + `DisplayMode.FOLDERS` |
| **Timeline View** | Every image and video on the device, flat across folders, under date headers. | `FolderListScreen` + `DisplayMode.TIMELINE`, body drawn by `CalendarGrid` |
| **Albums View** | Every user-created album as a cover tile, preceded by the Favourites tile where the platform supports it. | `FolderListScreen` + `DisplayMode.ALBUMS`, body drawn by `AlbumsViewBody` |
| **Folder Detail View** | The media inside one folder. | `FolderDetailScreen` |
| **Album Detail View** | The media inside one album. Also renders Favourites, via an `isFavouritesAlbum` flag. | `AlbumDetailScreen` |

Folders View, Timeline View, and Albums View are three modes of one screen, switched by a
`DisplayMode` flag rather than by navigation, because they share the same search, sort, filter,
grouping and selection machinery. Albums View joined them rather than becoming a fourth screen —
**[ADR-0013](decisions/0013-albums-as-third-display-mode.md)**. Folder Detail View and Album
Detail View are separate screens.

The **view switcher** is a dropdown anchored on the top-bar title in `SearchTopBar`, listing
`DisplayMode.entries` with a check mark on the current one. It replaced a single toggle icon,
which could not express three states. Pressing back from Timeline or Albums View returns to
Folders View rather than leaving the app.

> **Naming debt:** `FolderListScreen` and `CalendarGrid` predate the canonical names, and
> `FolderListScreen` now hosts three views rather than two, which makes its name worse than it
> was. A rename to `FoldersScreen` and `TimelineGrid` is proposed but not done. `DisplayMode` has
> been renamed and is no longer part of this debt. Do not introduce a third set of names in the
> meantime.

Beyond the five views there are the **full-screen viewer** (`FullImageScreen`), the **Settings
screen**, the **Manage excluded screen**, and the **Permission denied screen**.

---

## 2. Data flow

```
MediaStore (ContentResolver queries)        Room (easy_gallery.db)
        │                                           │
MediaStoreDataSource ──▶ MediaRepository         AlbumDao
FavouritesDataSource                                │
        │                                    RoomAlbumStore ──▶ AlbumStore
        │                                           │
GalleryViewModel  ──delegates state to──▶  DisplayPreferencesState
   (central hub)                            FolderViewPreferencesState
        │                                   MediaViewerState
        │   (+ AlbumsViewModel, FavouritesViewModel, CreateFolderViewModel,
        │      BillingViewModel as feature ViewModels)
        │
*Screen composables   ← read the ViewModel, bind a PreferenceScope
        │
*Content composables  ← stateless; take plain values + callbacks; what androidTest drives
        │
Components (top bars, dialogs, grid/list items, ZoomableImage)
```

State is exposed as `StateFlow` and derived state is built with `combine`, so the UI recomposes
only when the data it reads changes. Room queries that feed the UI return `Flow`, so its
invalidation tracker drives recomposition through the same mechanism rather than a re-read
trigger.

Albums and media meet only in the ViewModel layer: `AlbumsViewModel` is handed the live
`allMedia` list and resolves stored memberships against it. The database never holds a media row.

**Kotlin property initialisers run top to bottom.** A derived `combine(...)` flow must be declared
below every property it reads, or the class does not compile. Do not work around this with
`lateinit` or a nullable backing field.

---

## 3. Layers

### `data/`

| File | Role |
|---|---|
| `MediaRepository.kt` | The interface abstracting all media access and file operations. |
| `MediaStoreDataSource.kt` | The `MediaStore`/`ContentResolver` implementation, plus copy/move/delete/create. |
| `GalleryTransformations.kt` | All pure filter, sort, and group logic, including date headers and path conversion. |
| `GalleryModels.kt` | `SortType`, `SortOrder`, `GroupByType`, `DisplayMode`, `ViewType`, `OperationType`, `GalleryUiState`, `MediaPermissionHandler`. |
| `MediaItem.kt`, `Folder.kt` | The two domain models. |
| `ViewPreferences.kt` | The per-view preference bundle and `PreferenceScope`. |
| `FolderViewOverrides.kt` | The sparse per-folder override bundle and its apply/merge logic. |
| `CameraFolder.kt` | The camera-folder path heuristic and the implicit sort default it produces. |
| `MediaLocation.kt` | GPS parsing, formatting, URI building, and the per-item `MediaLocationReader`. |
| `DefaultMediaPermissionHandler.kt` | Wraps the Android 11+ `createDeleteRequest`/`createWriteRequest` `IntentSender` APIs so the ViewModel stays free of `Activity` details. |
| `MediaStoreVersionStore.kt` | The `MediaStore.getVersion()` watermark store (`SharedPreferences*` and `InMemory*`) and the `MediaStoreVersionProvider` that reads the live value. |
| `EasyGalleryDatabase.kt` | The Room database: two entities, schema version 1, `exportSchema = true`. Built by `create(context)`; the singleton lives on `EasyGalleryApp`. |
| `AlbumEntities.kt` | `AlbumEntity`, `AlbumMembershipEntity`, `AlbumMembershipKind`, and its type converter. |
| `AlbumDao.kt` | The album DAO. UI-facing queries return `Flow`. |
| `AlbumStore.kt` | `AlbumStore` plus `RoomAlbumStore` and `InMemoryAlbumStore`, and the `StoredAlbum` model. |
| `AlbumMembership.kt` | Pure, Android-free membership resolution and healing — direct URI match, then healing key, then orphan. |
| `Favourites.kt` | Pure favourite-tier selection from SDK level, `MANAGE_MEDIA` grant, and R extension version. |
| `FavouritesDataSource.kt` | The `MediaStore` favourite read/write implementation behind the `FavouritesDataSource` interface. |
| `DisplayPreferencesStore.kt`, `FolderPreferencesStore.kt`, `FolderViewPreferencesStore.kt`, `FolderThumbnailStore.kt` | Persistence; see *Persistence*. |

There is **no `domain/` module**. Pure, Android-free logic lives in `data/`
(`GalleryTransformations`, `MediaLocation`, `CameraFolder`, `AlbumMembership`, `Favourites`) and
in `ui/components/` helper files (`MediaDuration`, `ShareIntents`, `WallpaperIntents`)
specifically so it can be unit tested on a plain JVM without Robolectric.

### `ui/`

- **`GalleryViewModel`** (`AndroidViewModel`) — the central state hub. It loads data,
  owns selection and file operations, and exposes the derived flows. It is the largest file in the
  app; prefer a new state holder or a feature ViewModel over adding to its body.
- **`DisplayPreferencesState`** — one `ViewPreferences` bundle per `PreferenceScope`, plus
  per-scope search state and the global `DisplayMode`.
- **`FolderViewPreferencesState`** — the sparse per-folder overrides, keyed by folder path.
- **`MediaViewerState`** — transient full-screen viewer state: current item, paging list,
  immersive mode, rotation, and the saved video position.
- **`AlbumsViewModel`** — albums and their members. It observes `AlbumStore`, resolves stored
  memberships against the live media list it is handed, writes healed URIs and orphan flags back,
  and owns `selectedAlbumId`. It also owns the `MediaStore.getVersion()` watermark that decides
  when a re-resolve is worth doing.
- **`FavouritesViewModel`** — the favourite URI set and the write path, including the
  `IntentSender` round trip and the `MANAGE_MEDIA` rationale.
- **`CreateFolderViewModel`** — the create-folder dialog's own state; emits a `folderCreated`
  event the host screen listens for. Precedent for a feature-scoped ViewModel.
- **`BillingViewModel`** — the Google Play Billing client and the tip-jar purchase flow.

`AlbumsViewModel` and `FavouritesViewModel` are feature-scoped on that precedent. `GalleryViewModel`
gained no album state; it still owns selection, media operations, and the viewer delegate, which
Album Detail View reuses.

Screens: `FolderListScreen` (with `FolderList`, `FolderGrid`, `FolderDialogs`),
`FolderDetailScreen`, `AlbumDetailScreen` (with `AlbumDialogs`), `FullImageScreen`, `CalendarGrid`,
`ManageExcludedScreen`, `SettingsScreen`.

Each main view is a pair: the `*Screen` composable reads the ViewModel and binds the scope; the
`*Content` composable is stateless and takes plain values plus callbacks. **The `*Content`
composable is what the instrumented tests drive, and they pass every parameter by name** — so a
signature change breaks them loudly, by design.

### `ui/components/`

Top bars (`SearchTopBar`, `SelectionTopBar`, `MediaSelectionTopBar`, `ThumbnailPickerTopBar`),
dialogs (`ColumnCountDialog`, `SortOptions`, `GroupByDialog`, `FilterMediaDialog`,
`CreateFolderDialog`, `MediaPropertiesDialog`, `FolderBrowser`), the `PreferenceScopeSelector`
state holder those dialogs share for their draft-and-commit "apply only to this folder" target,
media widgets (`MediaGridItem`, `MediaListItem`, `AlbumGridItem`, `ZoomableImage`), and the Android-free
helpers (`MediaDuration`, `ShareIntents`, `WallpaperIntents`, plus `MediaLocationUi` and
`VideoPlaybackState`, which are Compose-bound).

`SearchTopBar` also carries the view switcher described in §1.

### App entry points

- **`EasyGalleryApp`** (`Application`) — configures the Coil 3 `ImageLoader` with video-frame and
  animated-image decoders, applies the persisted locale in `attachBaseContext`, and holds the
  `EasyGalleryDatabase` singleton as a `lazy` property. There is no DI framework to own the
  database and a second instance would defeat Room's invalidation tracking —
  **[ADR-0009](decisions/0009-room-as-first-owned-datastore.md)**.
- **`MainActivity`** — hosts the Compose tree, checks media permissions, and registers the
  `IntentSender` launchers for scoped-storage delete/write requests and for favourite writes.
- **`LocaleHelper`** — persists the BCP-47 language tag and wraps a `Context` with it via
  `createConfigurationContext`.

---

## 4. Navigation

Navigation is a plain `if / else if` chain in `MainActivity` — **no Navigation library and no back
stack.** The order is:

```
selectedMedia != null        → FullImageScreen          (outside the state holder)
  else, inside SaveableStateProvider(screenKey):
    isManageExcludedMode     → ManageExcludedScreen
    isSettingsMode           → SettingsScreen
    selectedFolder != null   → FolderDetailScreen
    selectedAlbumId != null  → AlbumDetailScreen
    isFavouritesSelected     → AlbumDetailScreen        (isFavouritesAlbum = true)
    hasPermission            → FolderListScreen
    else                     → PermissionDeniedScreen
```

Albums added two branches rather than a navigation library; the chain stays —
**[ADR-0013](decisions/0013-albums-as-third-display-mode.md)**. They sit after `selectedFolder`
and before `hasPermission`, so a folder still wins over an album and the permission gate still
wins over nothing. `selectedAlbumId` lives in `AlbumsViewModel`; `isFavouritesSelected` is a
`rememberSaveable` flag in `MainActivity`, because Favourites has no row of its own to select.

Switching screens removes the previous one from composition, so a plain `remember` in a screen
does **not** survive navigation. A `rememberSaveableStateHolder` in `MainActivity` retains each
non-viewer screen's saveable state (lazy scroll position) under a `screenKey` that includes the
folder path or album id — `folder_detail:$path`, `album_detail:$albumId`, and the literal
`album_detail:favourites`.

`FullImageScreen` is deliberately rendered **outside** that provider, and `MainActivity` carries
no `android:configChanges`. Both are required by
**[ADR-0002](decisions/0002-viewmodel-owned-transient-viewer-state.md)** — read it before touching
either. Albums left both untouched.

State that must survive navigation therefore belongs either in a ViewModel or inside the
`SaveableStateProvider`.

---

## 5. Display preferences

`ViewPreferences` holds `sortType`, `sortOrder`, `viewType`, `columns`, `groupBy`, `groupOrder`,
`mediaTypes`, and `showInfo`. Each main view owns an independent set, keyed by `PreferenceScope`
(`FOLDERS`, `TIMELINE`, `FOLDER_DETAIL`, `ALBUMS`, `ALBUM_DETAIL`), so changing the grouping in
one view does not change another. `GalleryViewModel` exposes `preferences(scope)`,
`searchQuery(scope)`, and `isSearchActive(scope)` rather than one flow per field, and setters take
the scope: `setGroupBy(type, scope)`.

Derived flows in `GalleryViewModel` are bound to the owning scope: `filteredFolders` → `FOLDERS`,
`filteredAllMedia`/`groupedAllMedia` → `TIMELINE`, `filteredMedia`/`groupedFolderMedia` →
`FOLDER_DETAIL`. The two album scopes have **no ViewModel-level derived flow**: Albums View sorts
and filters the album list inside `AlbumsViewBody`, and Album Detail View runs
`GalleryTransformations` under `remember` inside `AlbumDetailScreen`, because the input is an
album's resolved member list rather than a `GalleryViewModel` flow.

| | Folders View | Timeline View | Albums View | Folder Detail View | Album Detail View |
|---|---|---|---|---|---|
| Sort | Name, ascending | Date taken, descending | Name, ascending | Name, ascending | Name, ascending |
| Group by | — | Date taken (daily) | — | None | None |
| Layout | Grid, 2 columns | Grid, 3 columns | Grid, 2 columns | Grid, 3 columns | Grid, 3 columns |

Albums View mirrors Folders View's defaults because it is structurally the same grid of
containers; Album Detail View mirrors Folder Detail View's for the same reason.

Search is scoped the same way but **deliberately not persisted** — restoring a stale query on
launch would silently hide media.

### Folder Detail resolves three layers

For Folder Detail View the effective preferences are composed, not read flat:

```
explicit per-folder override  ⟶ over ⟶  implicit camera default  ⟶ over ⟶  global FOLDER_DETAIL
```

- The **per-folder override** is sparse, keyed by `Folder.path`, and merged on read, so no screen
  read path changed when it was introduced —
  **[ADR-0006](decisions/0006-per-folder-display-preference-overrides.md)**.
- The **camera default** is computed from the folder path and **never persisted** —
  **[ADR-0008](decisions/0008-camera-folder-default-sort-order.md)**.

The nesting order matters and reversing it still compiles. Read both ADRs before changing this.

ADR-0006 also made the five display-preference dialogs **draft-and-commit**, with an "apply only
to this folder" target chosen at OK rather than on selection.

---

## 6. Persistence

The app persists in two places, and they sit **alongside** each other. Room did not replace the
preference stores: they work, they are tested, and migrating them would be churn with no
user-visible benefit — **[ADR-0009](decisions/0009-room-as-first-owned-datastore.md)**.

### Preference stores

Each store owns its own `SharedPreferences` file. Do not merge them — the per-folder override file
parses structured keys and would collide with flat ones.

| File | Holds | Interface |
|---|---|---|
| `app_settings` | language override; the `MediaStore.getVersion()` watermark | `LocaleHelper`, `MediaStoreVersionStore` |
| `display_preferences` | per-view `ViewPreferences` | `DisplayPreferencesStore` |
| `folder_preferences` | pinned and excluded folder paths | `FolderPreferencesStore` |
| `folder_display_preferences` | per-folder preference overrides | `FolderViewPreferencesStore` |
| `folder_thumbnails` | per-folder thumbnail override | `FolderThumbnailStore` |

Every store is an interface with a `SharedPreferences*` production implementation and an
`InMemory*` implementation for tests. Add both when you add a store.

`app_settings` is the one file with two owners. The album watermark is a single scalar under
`albums_mediastore_version`, written by `SharedPreferencesMediaStoreVersionStore` in
`data/MediaStoreVersionStore.kt` and injected into `AlbumsViewModel`; it was put there
deliberately rather than opening a sixth preferences file for one key. A second scalar may join
it. Anything structured must not.

The folder thumbnail override is resolved against live media rather than trusted blindly, so a
deleted or moved item falls back automatically —
**[ADR-0007](decisions/0007-persisted-folder-thumbnail-override.md)**, including its two
amendments covering the draft-and-commit picker and pre-selection.

### The album database

`easy_gallery.db`, Room, schema version 1, exported to `app/schemas/`. Two tables:

| Table | Holds |
|---|---|
| `albums` | id, name, `createdAt`, a nullable `rule`, and a nullable `coverMembershipId` |
| `album_memberships` | id, `albumId`, `mediaUri`, the healing key (`displayName` + `size` + `dateModified`), `kind`, `isOrphaned` |

An album's contents are `(rule ∪ additions) − exclusions`, which is why memberships carry a
`kind` rather than being a plain join table —
**[ADR-0012](decisions/0012-one-album-type-rule-additions-exclusions.md)**. A membership identifies
its media by a content URI with the healing key as a fallback, and an unresolvable membership is
marked orphaned and retained rather than deleted —
**[ADR-0010](decisions/0010-album-membership-dual-key-identity.md)**. The cover is a foreign key
to a membership row, chosen at random once, so it heals through the ordinary membership path —
**[ADR-0015](decisions/0015-album-cover-persisted-random-member.md)**.

The database is in scope for Android auto-backup and device transfer, which is the project
template's default kept deliberately: `allowBackup="true"` and two rules files with every rule
still commented out — **[ADR-0014](decisions/0014-album-database-included-in-auto-backup.md)**.
Adding a single `<include>` would silently narrow the whole backup, so do not "tidy" those files.

**Favourites are not in this database.** They are a `MediaStore` column, read and written through
`FavouritesDataSource` with a tier chosen from the SDK level, the `MANAGE_MEDIA` grant, and the R
extension version — **[ADR-0011](decisions/0011-favourites-backed-by-mediastore.md)**.
`MANAGE_MEDIA` is declared in the manifest, requested only in context, and never required.

---

## 7. Media operations

Copy, move, delete, and create-folder all run through `MediaRepository`. Deletes and writes use
the Android 11+ `IntentSender` flow, wrapped behind `MediaPermissionHandler` so the ViewModel
stays free of `Activity` details.

The operand of a copy or move is resolved **explicitly**, not inferred from whichever selection
mode happens to be active — **[ADR-0003](decisions/0003-explicit-media-operation-target.md)**.
Resolution order is explicit target → media selection → folder selection, and the explicit target
must be cleared on both cancel and completion.

The action set available while media is multi-selected mirrors the full-screen viewer's —
**[ADR-0005](decisions/0005-selection-scoped-media-actions.md)**. "Mirrors" means the same actions
and icons, not the same placement.

Two operations delegate outward rather than doing the work in-process:

- **Use as background** hands the image to the system crop-and-set activity —
  **[ADR-0004](decisions/0004-delegate-wallpaper-setting-to-system-cropper.md)**. This is why the
  manifest carries a `<queries>` element.
- **Share** builds the intent in `ShareIntents`, attaching a `ClipData` because
  `FLAG_GRANT_READ_URI_PERMISSION` does not cover `EXTRA_STREAM`.

---

## 8. Media metadata and playback

GPS coordinates are read **lazily, per item, only when an info surface is shown** — never during a
`MediaStore` query, because Timeline View loads every item on the device —
**[ADR-0001](decisions/0001-on-demand-media-location-metadata.md)**. Images go through
`androidx.exifinterface`; videos through `MediaMetadataRetriever`.

Video playback uses **Media3 ExoPlayer pinned at 1.5.0**. That pin is load-bearing:
`media3-ui-compose` does not exist at 1.5.0, so the viewer's play/pause, progress slider, and time
labels are hand-rolled Compose controls over a `Player.Listener` plus position polling, and the
`PlayerView` runs with `useController = false`. Upgrading media3 is a decision to raise, not a
routine bump.

---

## 9. Localisation

The app ships **10 locales**: `values/` (en) plus `ar`, `de`, `es`, `fr`, `it`, `ja`, `ko`,
`pt-rBR`, `zh-rCN`. A new user-facing string lands in all ten in the same change as its
`R.string` reference.

The language override is per-app and independent of the device language, applied through
`LocaleHelper.wrap` / `createConfigurationContext`. Two consequences follow:

- Play's per-language App Bundle splitting is disabled (`bundle.language.enableSplit = false`),
  or Play would only ever install the locales the device already had configured.
- A localised string must never be cached in anything that outlives `Activity.recreate()`.
  ViewModels resolve strings through a freshly wrapped `Context` at point of use.

Anything machine-parsed — coordinates, durations — is formatted with `Locale.US`.

---

## 10. Tech stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 (`material-icons-extended`) |
| Image loading | Coil 3 (`coil-compose`, `-gif`, `-video`, `-network-okhttp`) |
| Video playback | AndroidX Media3 ExoPlayer, pinned at 1.5.0 |
| EXIF | `androidx.exifinterface` |
| Async | Kotlin Coroutines + `StateFlow` |
| Billing | Google Play Billing (`billing-ktx`) — the tip jar |
| Media data | Android `MediaStore` — all media, and the favourite flag |
| App-owned data | Room, with KSP as the annotation processor — albums only |
| Unit tests | JUnit4, MockK, Turbine, coroutines-test; Robolectric for the `data/` Android-boundary tests; `room-testing` and `inMemoryDatabaseBuilder` for the DAO |
| Instrumented tests | Compose UI test + Espresso |

Versions are centralised in `gradle/libs.versions.toml`; app config in `app/build.gradle.kts`.

KSP needed `android.disallowKotlinSourceSets=false` in `gradle.properties`. The project uses
AGP 9's built-in Kotlin and applies no `kotlin-android` plugin, so KSP's source-set registration
is rejected without it. Removing that line breaks the build with an error that does not mention
KSP.

---

## 11. Known debt

- **`GalleryViewModel` is by a wide margin the largest file in the app**, and still growing. The
  established escape hatches are a new state holder (`DisplayPreferencesState`,
  `FolderViewPreferencesState`, `MediaViewerState`) or a feature ViewModel
  (`CreateFolderViewModel`, `BillingViewModel`, `AlbumsViewModel`, `FavouritesViewModel`). Albums
  used the second and added no album state to its body.
- **Screen names lag the canonical ones** — see the caveat in §1. `FolderListScreen` and
  `CalendarGrid` still do; `DisplayMode` no longer does, having been renamed to
  `FOLDERS`/`TIMELINE`/`ALBUMS` when Albums View landed. `FolderListScreen` now hosts three views,
  so its name is less accurate than when this entry was written, not more.
- ~~**Back from Timeline View exited the app**, because the view switcher was a toggle with no
  notion of a home view.~~ Resolved when the switcher became a dropdown: back from Timeline or
  Albums View now returns to Folders View.
- **`SortType.DATE_TAKEN` does not read EXIF.** `MediaItem` has no `dateTaken`; the transformation
  maps it to `dateAdded` and the data source never queries `DATE_TAKEN`.
- **Folders are aggregated by `BUCKET_DISPLAY_NAME`**, keeping the first path, so two folders with
  the same name collapse. Per-folder overrides and thumbnails inherit that limitation.
- **No pagination.** Timeline View holds every item on the device in memory.
- **Orphaned preference entries are not collected** after a folder is renamed or deleted.
- **Favourites are absent on API 28–29.** The `MediaStore` favourite column arrived in API 30, so
  on older devices `FavouritesViewModel.isAvailable` is false and the Favourites tile and the
  favourite action are hidden rather than degraded. Accepted by
  [ADR-0011](decisions/0011-favourites-backed-by-mediastore.md); minSdk is 28.
- **Orphaned membership rows are never collected.** [ADR-0010](decisions/0010-album-membership-dual-key-identity.md)
  retains a membership whose media cannot be resolved, deliberately, so a file that comes back is
  not lost. Nothing ever deletes one, so a long-lived album accumulates rows for media that will
  not return. There is no cost yet at Stage 1 volumes and no policy for deciding when a row is
  truly dead.
- **An album's cover cannot be chosen.** [ADR-0015](decisions/0015-album-cover-persisted-random-member.md)
  picks one member at random and persists it; the picker that would let the user change it is not
  built. A user who dislikes the cover has no way to change it short of deleting the album.
- **The `rule` column is written but never read.** `albums.rule` exists for
  [ADR-0012](decisions/0012-one-album-type-rule-additions-exclusions.md)'s rule half; every album
  created so far stores `null` and nothing evaluates it. The schema is ready; the feature is not.
- **The Kotlin runtime artifacts are forced to 2.1.0** while the catalog pins Kotlin 2.2.10.
  `app/build.gradle.kts` holds three `resolutionStrategy.force` lines for `kotlin-stdlib`,
  `kotlin-stdlib-jdk8` and `kotlin-reflect`. They predate albums and were a workaround, not a
  decision, but KSP `2.2.10-2.0.2` and the Room code it generates now compile against a 2.2.10
  compiler and run against a 2.1.0 stdlib, so the gap matters more than it did. Nothing has failed;
  the reason the lines exist is no longer recorded anywhere, which is the actual debt. Establish
  whether they are still needed before the next Kotlin bump.
- **`DisplayPreferencesState`'s KDoc names three preference scopes; there are five.**
  `PreferenceScope` gained `ALBUMS` and `ALBUM_DETAIL` when Albums View landed, and the class
  documentation still lists only Folders View, Timeline View and Folder Detail View. A one-line fix
  in `app/src/main/java/com/davide/seddio/easygallery/ui/DisplayPreferencesState.kt`, recorded here
  so it is not lost.
- **`AlbumListItem` names two different things.** It is a composable in
  `ui/components/AlbumGridItem.kt` (one Album drawn as a row) and a data class in
  `ui/AlbumsViewModel.kt` (one Album's resolved state). Both live under `ui`, so a reader has to
  check the import to know which is meant. The UI vocabulary uses the composable sense. Renaming
  either is a vocabulary change and belongs to the Designer.
- **Two user-facing strings are English-only.** `support_email_subject` and `error_no_email_app`
  exist in `values/` and in none of the other nine locales. They predate albums — added with the
  support-email feature — so they are not Stage 1 drift, but they break the rule in §9 and nothing
  will catch them. (`support_email_address` is `translatable="false"` and is correct as it is.)
