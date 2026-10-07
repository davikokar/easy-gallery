# AGENTS.md

Guidance for AI agents working in this repository.

## Project

**Easy Gallery** is a native Android image and video gallery app. It browses the media already
present on the device through the Android `MediaStore`, organised by folder, and lets the user
view, sort, group, filter, search, and manage that media. It keeps no copy of the user's media;
the one thing it owns is a small Room database of albums, which stores references and user intent
rather than media. The one exception to "no network, no account" is the optional tip jar,
which uses Google Play Billing. Prefer clear, conventional code over clever abstractions, and
explain non-obvious decisions in short commit messages.

## Tech stack

- **Language:** Kotlin (Java 11 bytecode, Gradle daemon on JDK 17)
- **UI:** Jetpack Compose + Material 3 (`material-icons-extended`)
- **Architecture:** MVVM with a unidirectional data flow (UI state exposed as `StateFlow` from a
  `ViewModel`, derived state built with `combine`)
- **Persistence:** media comes live from `MediaStore` and is never copied. The app owns a Room
  database holding albums only (ADR-0009), plus five `SharedPreferences` files for the language
  override and user preferences (see *Preference stores* below). The two sit alongside each other;
  the preference stores were deliberately not migrated.
- **Dependency injection:** none. Components are instantiated directly so tests can pass fakes.
- **Image loading:** Coil 3 (`coil-compose`, `-gif`, `-video`, `-network-okhttp`)
- **Video playback:** AndroidX Media3 ExoPlayer, pinned at **1.5.0**
- **Database:** Room, processed by KSP. This needs `android.disallowKotlinSourceSets=false` in
  `gradle.properties`, because the project uses AGP 9's built-in Kotlin and applies no
  `kotlin-android` plugin. Removing that line breaks the build with an error that never mentions
  KSP.
- **Billing:** Google Play Billing (`billing-ktx`), used only by the tip jar in Settings
- **Async:** Kotlin Coroutines + Flow
- **Build:** Gradle (Kotlin DSL, `.gradle.kts`). **Every version lives in
  `gradle/libs.versions.toml` and `app/build.gradle.kts` — read them rather than restating one
  here.**
- **SDK:** minSdk 28, targetSdk 36, compileSdk 36.1
- **Tests:** JUnit4, MockK, Turbine, coroutines-test, and Robolectric for the `data/`
  Android-boundary tests, plus `room-testing` with `inMemoryDatabaseBuilder` for the DAO (unit);
  Compose UI test + Espresso (instrumented)

## Module / package layout

```
app/
  schemas/              # Room exported schemas, one JSON per version
  src/main/java/com/davide/seddio/easygallery/
    EasyGalleryApp.kt   # Application: Coil ImageLoader + locale + database singleton
    MainActivity.kt     # Compose host, permissions, IntentSender launchers
    LocaleHelper.kt     # Per-app language persistence and Context wrapping
    data/               # Repository interface, MediaStore data source, models,
                        # pure transformations, preference stores, Room database
    ui/                 # ViewModels, state holders, screens
      components/       # Reusable top bars, dialogs, media items
      theme/            # Material 3 theme
  src/test/             # Plain-JVM unit tests
  src/androidTest/      # Instrumented Compose UI tests
```

There is no `domain/` module. Pure, Android-free logic lives in `data/`
(`GalleryTransformations`, `MediaLocation`, `CameraFolder`, `AlbumMembership`, `Favourites`) and
in `ui/components/` helper files (`MediaDuration`, `ShareIntents`, `WallpaperIntents`) so it can
be unit tested on a plain JVM.

## Documentation

This project carries a fair amount of documentation and it is only useful while it is true. Every
document below has an owner and a maintenance mode, so that keeping it current is somebody's job
rather than nobody's.

| Document | Owner | Mode | Update when |
|---|---|---|---|
| [AGENTS.md](AGENTS.md) | Planner | routed | a convention, dependency, or project fact changes |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | Planner | routed | the structure it describes changes — see its trigger list |
| [docs/architecture/README.md](docs/architecture/README.md) | Planner | routed | an ADR is added, or one changes status |
| [docs/architecture/decisions/](docs/architecture/decisions/) | Planner | routed | never edited; superseded by a new ADR, or amended in place when only a detail changed |
| [docs/design/albums.md](docs/design/albums.md) | Planner | routed | a stage changes status, a decision is taken, a question is answered |
| [.github/instructions/ui-vocabulary.instructions.md](.github/instructions/ui-vocabulary.instructions.md) | Designer | **in-change** | you add, rename, or remove a named screen, view, area, or control |
| [.github/instructions/tests.instructions.md](.github/instructions/tests.instructions.md) | Planner | routed | a test convention or tool changes |
| [.github/instructions/architecture-docs.instructions.md](.github/instructions/architecture-docs.instructions.md) | Planner | routed | the documentation process itself changes |
| [.github/prompts/docs-audit.prompt.md](.github/prompts/docs-audit.prompt.md) | Planner | routed | the audit's scope or categories change |
| `.github/agents/*.agent.md` | Planner | routed | an agent's role, tools, or required reading changes |
| `/memories/repo/easy-gallery-exploration.md` | anyone | append-only | you learn something that would have saved you time |

**Routed** means the owner edits it. If your work makes one of those documents wrong, say so in
your report and name the section — do not fix it yourself. The Orchestrator assigns the edit.

**In-change** means you update it yourself, in the same change, because waiting would leave it
wrong while someone else works from it. The UI vocabulary is the only one, and only for
*additions*: naming something you just built is yours, but **renaming, removing, or resolving a
collision between existing terms is routed to the Designer**, because those need the whole
vocabulary in view.

When a project-wide fact changes, **this table is the checklist** — walk every row. The list of
affected documents inside the ADR or plan that prompted the change is only a snapshot of what its
author could see; the table is maintained. `.github/agents/*.agent.md` is the row most easily
missed, and the costliest: an agent definition is injected before the agent reads anything else,
so a stale fact there is believed rather than discovered.

### Two rules that matter more than the table

- **Do not write a fact that something else already owns.** An ADR count, a line count, a
  dependency list copied from `gradle/libs.versions.toml` — each is a future lie, because nothing
  checks it. Link to the source instead. Every documentation error found in this repo so far has
  been of exactly this kind.
- **Record why, not only what.** A decision stripped of its reasoning gets reversed by the next
  person who sees only its cost. If you reverse one, say that you did and why.

A documentation audit is part of the exit criteria of each albums stage
([docs/design/albums.md](docs/design/albums.md) §7). Triggers only catch the drift you anticipated;
the audit is for the rest.

## Conventions

- Before introducing a significant architectural choice, read the
  [architecture and design documentation instructions](.github/instructions/architecture-docs.instructions.md)
  and the existing [architecture decisions](docs/architecture/README.md). Every ADR marked
  `Active` is binding.
- The system overview is [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md).
  Read it before changing the data flow, the ViewModel's responsibilities, or the view structure.
  It is **maintained, not just read**: if your change adds a state holder, a preference store, a
  navigation branch, a main view, a `data/` file, or a tech-stack dependency, update it in the
  same change. The owner, the trigger list, and the timing are defined in the
  [architecture documentation instructions](.github/instructions/architecture-docs.instructions.md).
  An ADR is written *before* the work; the overview is updated *after*, so it describes what
  actually landed.
- For UI work or references to named screens, areas, and controls, read
  [the UI vocabulary](.github/instructions/ui-vocabulary.instructions.md). Its `applyTo` glob
  covers `MainActivity.kt` as well as `ui/`, because that file holds the navigation chain and
  `PermissionDeniedScreen`.
- The albums epic is specified in [docs/design/albums.md](docs/design/albums.md). Read it before
  touching albums, favourites, the media index, or anything that owns app-side data. Its §7
  progress table is the single source of truth for which stages exist and which are done — check
  it before assuming any of the document is implemented. The **Planner maintains it**, under the
  same rules as the ADRs and the overview; everyone else reports drift rather than editing it.
  The album vocabulary used to live there and moved into the UI vocabulary instructions when
  Stage 1's screens landed.
- For tests — unit **and** instrumented — follow
  [the test conventions](.github/instructions/tests.instructions.md).
- Tracked files have mixed line endings and there is no `.gitattributes`. Match the file you are
  editing. If `git diff --stat` reports far more changed lines than you touched, your editor
  converted the whole file; check against `git diff --ignore-all-space` before committing.
- `.artifacts/` holds committed plan/task/walkthrough output from earlier agent runs, one folder
  per run. It is history, not input. Do not edit it, and do not add to it unless asked.

### State ownership

- `GalleryViewModel` is the central state hub. It delegates to `DisplayPreferencesState`
  (per-view display preferences), `FolderViewPreferencesState` (per-folder overrides), and
  `MediaViewerState` (transient full-screen viewer state). Put new state in the narrowest of
  those that fits, not in the ViewModel body.
- **`GalleryViewModel` is the largest file in the app by a wide margin.** Treat that as a reason
  to extract, not a licence to add. The two established escapes are a new state holder like the
  three above, or a feature-scoped ViewModel — `CreateFolderViewModel`, `BillingViewModel`,
  `AlbumsViewModel`, and `FavouritesViewModel` are the precedent. Albums added **no album state**
  to `GalleryViewModel`; it only reuses the viewer delegate and the selection machinery.
- Kotlin property initialisers run top to bottom. A derived `combine(...)` flow must be declared
  **below** every property it reads, or the class fails to compile. Never work around this with
  `lateinit` or a nullable backing field.
- Navigation is a plain `if / else if` chain in `MainActivity`, with no back stack. Switching
  screens removes the previous screen from composition, so plain `remember` in a screen does not
  survive navigation. State that must survive belongs in a ViewModel or inside the
  `SaveableStateProvider` in `MainActivity` — except the full-screen viewer, which ADR-0002
  deliberately keeps outside it. ADR-0013 keeps the chain as it is: Albums View is a third
  `DisplayMode` on `FolderListScreen`, not a fourth screen.
- Screens are stateless where practical: the `*Screen` composable reads the ViewModel and passes
  plain values plus callbacks to a `*Content` composable, which is what the instrumented tests
  drive.

### Preference stores

Each store owns its own `SharedPreferences` file; do not merge them.

| File | Holds | Store |
|---|---|---|
| `app_settings` | language override; the `MediaStore.getVersion()` watermark | `LocaleHelper`, `MediaStoreVersionStore` |
| `display_preferences` | per-view `ViewPreferences` | `DisplayPreferencesStore` |
| `folder_preferences` | pinned and excluded folder paths | `FolderPreferencesStore` |
| `folder_display_preferences` | per-folder preference overrides | `FolderViewPreferencesStore` |
| `folder_thumbnails` | per-folder thumbnail override | `FolderThumbnailStore` |

Every store is an interface with a `SharedPreferences*` production implementation and an
`InMemory*` implementation for tests. Add both when you add a store.

The album watermark shares `app_settings` deliberately, to avoid a sixth preferences file for one
scalar. Another scalar may join it; anything structured belongs in Room.

### The album database

Room owns albums and nothing else — ADR-0009. It sits alongside the preference stores above,
which were deliberately **not** migrated.

- The singleton lives on `EasyGalleryApp`. Never construct a second one; Room's invalidation
  tracking depends on there being one.
- Access goes through the `AlbumStore` interface, with `RoomAlbumStore` for production and
  `InMemoryAlbumStore` for tests — the same shape as the preference stores. DAO-level tests use
  `inMemoryDatabaseBuilder`.
- Every schema change needs a version bump and a new JSON under `app/schemas/`, which is
  committed.
- Membership identity, healing, and orphan retention are ADR-0010's; read it before touching
  `AlbumMembership.kt`. Favourites are **not** in this database — they are a `MediaStore` column
  (ADR-0011), unavailable below API 30.

### Strings and localisation

- The app ships **10 locales**: `values/` (en) plus `ar`, `de`, `es`, `fr`, `it`, `ja`, `ko`,
  `pt-rBR`, `zh-rCN`. Every new user-facing string goes into all ten in the same change, and a
  string and its `R.string` reference must be added or deleted in the same change.
- `<plurals>` forms are per-locale CLDR: `ar` has zero/one/two/few/many/other; `ja`/`ko`/`zh-rCN`
  have only `other`; the rest have one/other.
- Never cache a localised string in anything that outlives `Activity.recreate()`. ViewModels
  resolve strings through `LocaleHelper.wrap(application).getString(...)` at point of use.
- Format coordinates, durations, and anything parsed back by machine with `Locale.US`.

## Definition of done

Before claiming a task is complete:

1. Code compiles: `./gradlew compileDebugKotlin`
2. Unit tests compile and pass: `./gradlew compileDebugUnitTestKotlin testDebugUnitTest`
3. Instrumented tests compile: `./gradlew compileDebugAndroidTestKotlin`
4. Instrumented tests **run, if a device is attached**. Check with `adb devices`. If one is
   listed, `./gradlew connectedDebugAndroidTest` is part of done. If none is, step 3 stands as
   the fallback. Either way, say in your report which of the two you did.
5. New logic has at least one test.

Step 4 is conditional on purpose. Whether a device is plugged in is a property of the machine,
not of the change, so making the run unconditional would leave the definition of done
unsatisfiable half the time — and a gate that cannot be met gets ignored rather than met. Step 3
is kept unconditionally even when step 4 runs, because it is fast and it is what fails loudly
when a `*Content` signature changes.

### Running Gradle

There is no `local.properties` in the repo, so every Gradle invocation needs the SDK path in the
environment. On this machine the SDK is at the Windows default:

```bash
cd /c/git/easy-gallery && ANDROID_HOME="${LOCALAPPDATA//\\//}/Android/Sdk" \
  ANDROID_SDK_ROOT="$ANDROID_HOME" ./gradlew compileDebugKotlin
```

- Resolve the path **once** and reuse it. If `$LOCALAPPDATA` is not set in your shell, the literal
  path is `C:/Users/<user>/AppData/Local/Android/Sdk`. A missing or empty `ANDROID_HOME` fails
  with "SDK location not found", which reads like a real build failure but is not one.
- Always prefix with `cd /c/git/easy-gallery &&`. A leftover `cd` makes `./gradlew` fail with
  "No such file or directory", which a grep-filtered pipeline reports as a clean build. Treat
  empty Gradle output as suspicious, not as success.
- `--offline` does not work (not everything is cached).
- `lintDebug` is configured with `abortOnError = false`, so it is informational, not a gate.
- **A device may or may not be attached. `adb devices` settles it; do not assume either way.**
  When one is listed, `connectedDebugAndroidTest` runs here and instrumented behaviour can be
  genuinely verified — the suite has run on hardware, so "it cannot run" is not a safe
  assumption. When none is listed, `compileDebugAndroidTestKotlin` is the fallback, not the
  ceiling. Claim device-verified behaviour only for what you actually ran and observed, and say
  which of the two you did.

## What to ask before doing

- Adding a new third-party dependency, or upgrading a pinned one (notably media3 1.5.0, which
  governs what video-control APIs exist).
- Adding a new Android permission or `<queries>` entry.
- Introducing a new architectural pattern not described above, or contradicting an active ADR.
- Adding `android:configChanges` to `MainActivity` — ADR-0002 forbids it.

These do not need approval, but do need to be called out in your report because their blast
radius is wider than the diff looks:

- Changing a `*Content` composable's signature. It breaks every instrumented test that drives it,
  which is deliberate — fix the tests, do not add a default to silence them.
- Adding a new `SharedPreferences` file, which needs both a `SharedPreferences*` and an
  `InMemory*` implementation and an entry in the table above.

## Memory

Repository memory lives at `/memories/repo/easy-gallery-exploration.md`. It is a long log of
traps this codebase has already cost someone a session: the viewer/`SaveableStateHolder`
regression, media3 1.5.0's missing Compose controls, the `PlayerView` touch-dispatch dead end,
locale decimal separators, per-folder override layering order, and more. Read the sections
relevant to your task **before** starting, and append a short note when you learn something that
would have saved you time.
