# Easy Gallery — Functionalities & Architecture

Easy Gallery is a native Android image and video gallery app built entirely with
Jetpack Compose. It browses the media already present on the device (via the
Android `MediaStore`), organized by folder, and lets the user view, sort, group,
filter, search, and manage that media without any server, account, or local
database.

- **Package:** `com.davide.seddio.easygallery`
- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose (Material 3)
- **Min SDK:** 28 (Android 9) · **Target/Compile SDK:** 36
- **Data source:** Android `MediaStore` only — no Room/DataStore, no network backend
  (`SharedPreferences` is used for the language override and display preferences)
- **Dependency injection:** none — components are instantiated directly (keeps testing simple)

---

## 1. Functionalities

### Main views

The app has **three main views**. These names are canonical — use them verbatim in issues,
prompts, and commit messages:

| Canonical name | What it shows | Entry point |
|---|---|---|
| **Folders View** | All device folders ("buckets") as a grid/list, each with a thumbnail and item count. **Default view when the app opens.** | `FolderListScreen` with `DisplayMode.GALLERY` |
| **Timeline View** | All media across every folder, flat and grouped by date taken (daily/monthly headers). | `FolderListScreen` with `DisplayMode.CALENDAR`, rendered by `CalendarGrid` |
| **Folder Detail View** | The images and videos inside one selected folder. | `FolderDetailScreen` |

Folders View and Timeline View are two modes of the same screen; the top-bar toggle in
`SearchTopBar` flips `DisplayMode` between them. Folder Detail View is reached by tapping a
folder in Folders View.

**Each view owns an independent set of display preferences** (sort, grouping, grid/list, column
count, media-type filter, info overlay), keyed by `PreferenceScope.FOLDERS` / `TIMELINE` /
`FOLDER_DETAIL`. Changing the grouping in Timeline View does not affect Folder Detail View.
See §2 *Display preferences*.

> **Naming caveat:** the code still uses older names for these concepts —
> `DisplayMode.GALLERY` / `DisplayMode.CALENDAR`, `FolderListScreen`, `CalendarGrid`. The
> user-facing string for Timeline View is already `R.string.timeline_title` ("Timeline").
> See §5 for the mismatch and the recommended rename.

### Media browsing
- **Folders View** — top-level grid/list of device folders ("buckets"), each showing a
  thumbnail and item count.
- **Folder Detail View** — the photos and videos inside a selected folder.
- **Timeline View** — a flat, cross-folder view of every image/video, grouped by date taken.
- **Full-screen viewer** — swipeable pager (`HorizontalPager`) for images and videos with
  pinch-to-zoom, pan, rotation, immersive (chrome-hidden) mode, and video playback via
  ExoPlayer (Media3).
- **Media types** — images, videos (with duration badge), and animated GIFs (decoded through Coil).

### Organizing & finding media
- **Search** — live filtering by name from a search field in the top bar.
- **Sorting** — by name, date taken, date modified, or size; ascending/descending.
- **Grouping** — media can be grouped into date headers: daily, monthly, or by file type
  (rendered as a calendar-style grouped grid).
- **Filtering** — restrict the view to selected media types (Image / Video / GIF).
- **View configuration** — switch between grid and list layouts and adjust the column count
  (pinch-to-zoom on the grid, or a column-count dialog). Sorting, grouping, filtering, layout and
  column count are configured **per view** and persist across restarts.
- **Folder curation** — pin favorite folders to the top and exclude folders from the gallery
  (with a dedicated screen to review and un-exclude them). Both persist across restarts.

### Media management (file operations)
- **Select mode** — multi-select folders or individual media items.
- **Copy / Move** — to another folder chosen through a recursive folder browser / destination picker.
- **Delete** — using the scoped-storage delete flow (Android 11+ `MediaStore.createDeleteRequest`).
- **Create folder** — with live path browsing and duplicate-name checks.
- **Rotate & share** — from the full-screen viewer.
- **Properties** — a dialog showing media metadata (name, size, date).

### App-level features
- **Per-app language override** — an in-app "Change language" picker persists a BCP-47 tag in
  SharedPreferences and applies it independently of the device's system language (see §5).
- **Localization** — UI strings are externalized and translated into 10 locales
  (en, de, ar, ko, zh-rCN, fr, es, ja, pt-rBR, it).
- **Settings** — app info/version, language selection, privacy/community links, and a
  "tip the developer" in-app purchase.
- **Tip jar (billing)** — a one-time Google Play Billing in-app product (`tip_coffee`).

---

## 2. Architecture Overview

The app follows **MVVM** with a reactive, Compose-first data flow. State is exposed as
`StateFlow`s and derived state is computed with `combine`, so the UI recomposes only when
relevant data changes.

```
MediaStore (ContentResolver queries)
        │
MediaStoreDataSource  ──implements──▶  MediaRepository (interface)
        │
GalleryViewModel  ◀── delegates state to ──▶  DisplayPreferencesState
   (central hub)                               MediaViewerState
        │           (+ CreateFolderViewModel, BillingViewModel as feature ViewModels)
        │
Compose Screens (FolderListScreen = Folders View + Timeline View,
                 FolderDetailScreen = Folder Detail View, FullImageScreen, …)
        │
Reusable Components (top bars, dialogs, grid/list items, ZoomableImage)
```

### Layers

**Data layer** (`data/`)
- `MediaRepository` — interface abstracting all media access and file operations
  (`getFolders`, `getMediaInFolder`, `getAllMedia`, `copyFile`, `copyFolderContents`,
  `deleteMediaItems`, `updateMediaRelativePath`, `createFolder`, `folderExists`, …).
- `MediaStoreDataSource` — the concrete implementation that queries `MediaStore` through the
  `ContentResolver` and performs copy/move/delete/create operations.
- `GalleryTransformations` — a stateless object holding all pure filtering, sorting, and
  grouping logic (including date-header grouping and absolute→relative path conversion).
- `MediaPermissionHandler` / `DefaultMediaPermissionHandler` — wrap the Android 11+
  `MediaStore.createDeleteRequest` / `createWriteRequest` `IntentSender` APIs so the ViewModel
  stays free of `Activity` details.
- `DisplayPreferencesStore` — interface for persisting a `ViewPreferences` bundle per
  `PreferenceScope`; `SharedPreferencesDisplayStore` is the production implementation and
  `InMemoryDisplayPreferencesStore` is used by tests.
- `FolderPreferencesStore` — persists pinned and excluded folder paths
  (`SharedPreferencesFolderStore` / `InMemoryFolderPreferencesStore`).
- Models: `MediaItem` (+ `MediaType`), `Folder`, `ViewPreferences` (+ `PreferenceScope`), and
  `GalleryModels.kt` (`SortType`, `SortOrder`, `GroupByType`, `DisplayMode`, `ViewType`,
  `GalleryUiState`, etc.).

**Presentation layer** (`ui/`)
- `GalleryViewModel` (`AndroidViewModel`) — the central state hub. It orchestrates data
  loading, selection, and file operations, and exposes derived flows (filtered folders,
  filtered/grouped media). To keep concerns separated it **delegates** parts of its state to:
  - `DisplayPreferencesState` — one `ViewPreferences` bundle per `PreferenceScope`, plus
    per-scope search state and the global `DisplayMode`.
  - `MediaViewerState` — the transient full-screen viewer state (current item, paging list,
    immersive mode, rotation).
- `CreateFolderViewModel` — self-contained state/logic for the create-folder dialog; emits a
  `folderCreated` event the host screen listens to.
- `BillingViewModel` — encapsulates the Google Play Billing client and the tip-jar purchase flow.

**UI screens** (`ui/`) — `FolderListScreen` (hosts both **Folders View** and **Timeline View**),
`FolderDetailScreen` (**Folder Detail View**), `FullImageScreen`, `CalendarGrid` (the grouped
display used by Timeline View), `ManageExcludedScreen`, `SettingsScreen`. Folders View rendering
is factored into `FolderList` / `FolderGrid`, and dialogs into `FolderDialogs`.

**Reusable components** (`ui/components/`) — top bars (`SearchTopBar`, `SelectionTopBar`,
`MediaSelectionTopBar`), dialogs (`ColumnCountDialog`, `SortOptions`, `GroupByDialog`,
`FilterMediaDialog`, `CreateFolderDialog`, `MediaPropertiesDialog`, `FolderBrowser`), and media
widgets (`MediaGridItem`, `MediaListItem`, `ZoomableImage`).

**App entry points**
- `EasyGalleryApp` (`Application`) — configures the Coil 3 `ImageLoader` with video-frame and
  animated-image decoders, and applies the persisted locale via `attachBaseContext`.
- `MainActivity` — hosts the Compose tree, checks media permissions, and registers the
  `IntentSender` launchers for scoped-storage delete/write requests.

### Display preferences

`ViewPreferences` holds `sortType`, `sortOrder`, `viewType`, `columns`, `groupBy`, `groupOrder`,
`mediaTypes` and `showInfo`. `DisplayPreferencesState` keeps one bundle per `PreferenceScope` and
writes every change straight through to a `DisplayPreferencesStore`, so preferences survive a
restart. Each screen binds itself to exactly one scope and calls setters as
`viewModel.setGroupBy(type, scope)`; `GalleryViewModel` exposes `preferences(scope)`,
`searchQuery(scope)` and `isSearchActive(scope)` rather than one flow per field.

The derived media flows are wired to the owning scope: `filteredFolders` → `FOLDERS`,
`filteredAllMedia` / `groupedAllMedia` → `TIMELINE`, `filteredMedia` / `groupedFolderMedia` →
`FOLDER_DETAIL`.

Defaults differ per scope:

| | Folders View | Timeline View | Folder Detail View |
|---|---|---|---|
| Sort | Name, ascending | Date taken, descending | Name, ascending |
| Group by | — | Date taken (daily) | None |
| Layout | Grid, 2 columns | Grid, 3 columns | Grid, 3 columns |

Search is scoped the same way but **deliberately not persisted** — restoring a stale query on
launch would silently hide media.

Pinned and excluded folders are persisted separately through `FolderPreferencesStore`.

### State-management pattern
- `MutableStateFlow` for source-of-truth state; `combine(...)` for derived, read-only state.
- `collectAsState()` in composables for reactive recomposition.
- The repository interface enables mock-based testing without a DI framework.

---

## 3. Project Structure

```
app/src/main/java/com/davide/seddio/easygallery/
├── EasyGalleryApp.kt          Application: Coil ImageLoader + locale
├── MainActivity.kt            Compose host, permissions, IntentSender launchers
├── LocaleHelper.kt            Per-app language persistence & Context wrapping
├── data/
│   ├── MediaRepository.kt         Repository interface
│   ├── MediaStoreDataSource.kt    MediaStore-backed implementation
│   ├── GalleryTransformations.kt  Pure filter/sort/group logic
│   ├── GalleryModels.kt           Enums, UI-state, permission-handler interface
│   ├── ViewPreferences.kt         Per-view preference bundle + PreferenceScope
│   ├── DisplayPreferencesStore.kt Preference persistence (SharedPreferences / in-memory)
│   ├── FolderPreferencesStore.kt  Pinned/excluded folder persistence
│   ├── MediaItem.kt / Folder.kt   Domain models
│   └── DefaultMediaPermissionHandler.kt
└── ui/
    ├── GalleryViewModel.kt        Central state hub
    ├── DisplayPreferencesState.kt Per-scope display preferences
    ├── MediaViewerState.kt        Full-screen viewer state
    ├── CreateFolderViewModel.kt   Create-folder feature
    ├── BillingViewModel.kt        Tip-jar (Play Billing)
    ├── FolderListScreen.kt / FolderList.kt / FolderGrid.kt / FolderDialogs.kt
    │                               Folders View + Timeline View (switched by DisplayMode)
    ├── FolderDetailScreen.kt       Folder Detail View
    ├── FullImageScreen.kt
    ├── CalendarGrid.kt            Grouped (date/type) media display — used by Timeline View
    ├── ManageExcludedScreen.kt
    ├── SettingsScreen.kt
    ├── components/                Top bars, dialogs, media grid/list items, ZoomableImage
    └── theme/                     Color, Type, Theme (Material 3)
```

---

## 4. Tech Stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 (`material-icons-extended`) |
| Image loading | Coil 3 (`coil-compose`, `-gif`, `-video`, `-network-okhttp`) |
| Video playback | AndroidX Media3 ExoPlayer |
| Async | Kotlin Coroutines + `StateFlow` |
| Billing | Google Play Billing (`billing-ktx`) |
| Data | Android `MediaStore` (no DB, no network) |
| Testing | JUnit4, MockK, Turbine, coroutines-test, Robolectric; Espresso + Compose UI test |

Versions are centralized in `gradle/libs.versions.toml`; app config lives in
`app/build.gradle.kts`.

---

## 5. Notable Design Decisions

- **Three main views, two screens** — Folders View and Timeline View share `FolderListScreen`
  and are switched by a `DisplayMode` flag rather than by navigation, because they share the
  same top bar, search, sort, filter, and selection machinery. Folder Detail View is a separate
  screen. *Known debt:* the code names (`DisplayMode.GALLERY`/`CALENDAR`, `FolderListScreen`,
  `CalendarGrid`) predate the canonical names above and should be renamed to
  `DisplayMode.FOLDERS`/`TIMELINE`, `FoldersScreen`, and `TimelineGrid` so code and docs agree.
- **Per-view display preferences** — sort/group/layout settings are keyed by `PreferenceScope`
  rather than shared between views. Previously a single `picture*` set was read by both Timeline
  View and Folder Detail View, so changing the grouping in one silently changed the other.
  Preferences are written through to `SharedPreferences` on every change; search state is scoped
  but intentionally left in memory.
- **MediaStore-only data model** — the app owns no copy of the user's media and needs no
  storage/network permissions beyond media access; everything is a live query.
- **Repository interface + no DI** — abstraction is provided by `MediaRepository`, and
  dependencies are passed directly, which keeps unit tests simple (mock the interface).
- **Scoped-storage compliant operations** — deletes/writes go through the Android 11+
  `IntentSender` request flow, wrapped behind `MediaPermissionHandler` so the ViewModel stays
  platform-detail-free.
- **State delegation** — display preferences and viewer state were extracted from
  `GalleryViewModel` into `DisplayPreferencesState` and `MediaViewerState`, and create-folder /
  billing into their own ViewModels, to keep each concern self-contained and testable.
- **Per-app locale via `createConfigurationContext`** — `LocaleHelper` wraps the `Context` to
  apply the chosen language. Because the app overrides the locale independently of the device
  language, Play's per-language App Bundle splitting is disabled
  (`bundle.language.enableSplit = false`) so every locale's resources are always installed;
  localized strings are resolved through a freshly wrapped `Context` at point of use to survive
  `Activity.recreate()`.

---

*This document describes the current source layout under
`app/src/main/java/com/davide/seddio/easygallery/`.*
