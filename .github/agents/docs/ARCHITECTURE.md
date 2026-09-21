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
- **Dependency injection:** none — components are instantiated directly (keeps testing simple)

---

## 1. Functionalities

### Media browsing
- **Folder gallery** — top-level grid/list of device folders ("buckets"), each showing a
  thumbnail and item count.
- **Folder detail** — the photos and videos inside a selected folder.
- **All-media view** — a flat, cross-folder view of every image/video.
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
  (pinch-to-zoom on the grid, or a column-count dialog).
- **Folder curation** — pin favorite folders to the top and exclude folders from the gallery
  (with a dedicated screen to review and un-exclude them).

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
Compose Screens (FolderListScreen, FolderDetailScreen, FullImageScreen, …)
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
- Models: `MediaItem` (+ `MediaType`), `Folder`, and `GalleryModels.kt`
  (`SortType`, `SortOrder`, `GroupByType`, `DisplayMode`, `ViewType`, `GalleryUiState`, etc.).

**Presentation layer** (`ui/`)
- `GalleryViewModel` (`AndroidViewModel`) — the central state hub. It orchestrates data
  loading, selection, and file operations, and exposes derived flows (filtered folders,
  filtered/grouped media). To keep concerns separated it **delegates** parts of its state to:
  - `DisplayPreferencesState` — search, filter, sort, group, view type, and column-count state,
    re-exposed unchanged.
  - `MediaViewerState` — the transient full-screen viewer state (current item, paging list,
    immersive mode, rotation).
- `CreateFolderViewModel` — self-contained state/logic for the create-folder dialog; emits a
  `folderCreated` event the host screen listens to.
- `BillingViewModel` — encapsulates the Google Play Billing client and the tip-jar purchase flow.

**UI screens** (`ui/`) — `FolderListScreen`, `FolderDetailScreen`, `FullImageScreen`,
`CalendarGrid` (grouped display), `ManageExcludedScreen`, `SettingsScreen`. Folder rendering is
factored into `FolderList` / `FolderGrid`, and dialogs into `FolderDialogs`.

**Reusable components** (`ui/components/`) — top bars (`SearchTopBar`, `SelectionTopBar`,
`MediaSelectionTopBar`), dialogs (`ColumnCountDialog`, `SortOptions`, `GroupByDialog`,
`FilterMediaDialog`, `CreateFolderDialog`, `MediaPropertiesDialog`, `FolderBrowser`), and media
widgets (`MediaGridItem`, `MediaListItem`, `ZoomableImage`).

**App entry points**
- `EasyGalleryApp` (`Application`) — configures the Coil 3 `ImageLoader` with video-frame and
  animated-image decoders, and applies the persisted locale via `attachBaseContext`.
- `MainActivity` — hosts the Compose tree, checks media permissions, and registers the
  `IntentSender` launchers for scoped-storage delete/write requests.

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
│   ├── MediaItem.kt / Folder.kt   Domain models
│   └── DefaultMediaPermissionHandler.kt
└── ui/
    ├── GalleryViewModel.kt        Central state hub
    ├── DisplayPreferencesState.kt Search/sort/group/view preferences
    ├── MediaViewerState.kt        Full-screen viewer state
    ├── CreateFolderViewModel.kt   Create-folder feature
    ├── BillingViewModel.kt        Tip-jar (Play Billing)
    ├── FolderListScreen.kt / FolderList.kt / FolderGrid.kt / FolderDialogs.kt
    ├── FolderDetailScreen.kt
    ├── FullImageScreen.kt
    ├── CalendarGrid.kt            Grouped (date/type) media display
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
