# AGENTS.md

Guidance for AI agents working in this repository.

## Project

**Easy Gallery** is a native Android image and video gallery app. It browses the media already
present on the device through the Android `MediaStore`, organised by folder, and lets the user
view, sort, group, filter, search, and manage that media. It keeps no copy of the user's media and
has no local database. The one exception to "no network, no account" is the optional tip jar,
which uses Google Play Billing. Prefer clear, conventional code over clever abstractions, and
explain non-obvious decisions in short commit messages.

## Tech stack

- **Language:** Kotlin 2.1.0 (Java 11 bytecode, Gradle daemon on JDK 17)
- **UI:** Jetpack Compose + Material 3 (`material-icons-extended`)
- **Architecture:** MVVM with a unidirectional data flow (UI state exposed as `StateFlow` from a
  `ViewModel`, derived state built with `combine`)
- **Persistence:** none of its own — media comes live from `MediaStore`. `SharedPreferences` holds
  only the language override and user preferences (see *Preference stores* below).
- **Dependency injection:** none. Components are instantiated directly so tests can pass fakes.
- **Image loading:** Coil 3 (`coil-compose`, `-gif`, `-video`, `-network-okhttp`)
- **Video playback:** AndroidX Media3 ExoPlayer, pinned at **1.5.0**
- **Billing:** Google Play Billing (`billing-ktx`), used only by the tip jar in Settings
- **Async:** Kotlin Coroutines + Flow
- **Build:** Gradle (Kotlin DSL, `.gradle.kts`), versions centralised in `gradle/libs.versions.toml`
- **SDK:** minSdk 28, target/compile 36
- **Tests:** JUnit4, MockK, Turbine, coroutines-test, and Robolectric for the `data/`
  Android-boundary tests (unit); Compose UI test + Espresso (instrumented)

## Module / package layout

```
app/
  src/main/java/com/davide/seddio/easygallery/
    EasyGalleryApp.kt   # Application: Coil ImageLoader + locale
    MainActivity.kt     # Compose host, permissions, IntentSender launchers
    LocaleHelper.kt     # Per-app language persistence and Context wrapping
    data/               # Repository interface, MediaStore data source, models,
                        # pure transformations, preference stores
    ui/                 # ViewModels, state holders, screens
      components/       # Reusable top bars, dialogs, media items
      theme/            # Material 3 theme
  src/test/             # Plain-JVM unit tests
  src/androidTest/      # Instrumented Compose UI tests
```

There is no `domain/` module. Pure, Android-free logic lives in `data/`
(`GalleryTransformations`, `MediaLocation`, `CameraFolder`) and in `ui/components/` helper files
(`MediaDuration`, `ShareIntents`, `WallpaperIntents`) so it can be unit tested on a plain JVM.

## Documentation

This project carries a fair amount of documentation and it is only useful while it is true. Every
document below has an owner and a maintenance mode, so that keeping it current is somebody's job
rather than nobody's.

| Document | Owner | Mode | Update when |
|---|---|---|---|
| [AGENTS.md](AGENTS.md) | Planner | routed | a convention, dependency, or project fact changes |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | Planner | routed | the structure it describes changes — see its trigger list |
| [docs/architecture/decisions/](docs/architecture/decisions/) | Planner | routed | never edited; superseded by a new ADR |
| [docs/design/albums.md](docs/design/albums.md) | Planner | routed | a stage changes status, a decision is taken, a question is answered |
| [.github/instructions/ui-vocabulary.instructions.md](.github/instructions/ui-vocabulary.instructions.md) | Designer | **in-change** | you add, rename, or remove a named screen, view, area, or control |
| [.github/instructions/tests.instructions.md](.github/instructions/tests.instructions.md) | Planner | routed | a test convention or tool changes |
| [.github/instructions/architecture-docs.instructions.md](.github/instructions/architecture-docs.instructions.md) | Planner | routed | the documentation process itself changes |
| `.github/agents/*.agent.md` | Planner | routed | an agent's role, tools, or required reading changes |
| `/memories/repo/easy-gallery-exploration.md` | anyone | append-only | you learn something that would have saved you time |

**Routed** means the owner edits it. If your work makes one of those documents wrong, say so in
your report and name the section — do not fix it yourself. The Orchestrator assigns the edit.

**In-change** means you update it yourself, in the same change, because waiting would leave it
wrong while someone else works from it. The UI vocabulary is the only one, and only for
*additions*: naming something you just built is yours, but **renaming, removing, or resolving a
collision between existing terms is routed to the Designer**, because those need the whole
vocabulary in view.

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
  [the UI vocabulary](.github/instructions/ui-vocabulary.instructions.md). It also covers
  `MainActivity.kt`, which holds the navigation chain and `PermissionDeniedScreen` but sits
  outside the instruction file's `applyTo` glob.
- The albums epic is specified in [docs/design/albums.md](docs/design/albums.md). Read it before
  touching albums, favourites, the media index, or anything that owns app-side data. Its §7
  progress table is the single source of truth for which stages exist and which are done — check
  it before assuming any of the document is implemented. The **Planner maintains it**, under the
  same rules as the ADRs and the overview; everyone else reports drift rather than editing it.
  It also holds the album vocabulary until Stage 1 lands, at which point that moves into the UI
  vocabulary instructions.
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
  three above, or a feature-scoped ViewModel — `CreateFolderViewModel` and `BillingViewModel` are
  the precedent.
- Kotlin property initialisers run top to bottom. A derived `combine(...)` flow must be declared
  **below** every property it reads, or the class fails to compile. Never work around this with
  `lateinit` or a nullable backing field.
- Navigation is a plain `if / else if` chain in `MainActivity`, with no back stack. Switching
  screens removes the previous screen from composition, so plain `remember` in a screen does not
  survive navigation. State that must survive belongs in a ViewModel or inside the
  `SaveableStateProvider` in `MainActivity` — except the full-screen viewer, which ADR-0002
  deliberately keeps outside it.
- Screens are stateless where practical: the `*Screen` composable reads the ViewModel and passes
  plain values plus callbacks to a `*Content` composable, which is what the instrumented tests
  drive.

### Preference stores

Each store owns its own `SharedPreferences` file; do not merge them.

| File | Holds | Store |
|---|---|---|
| `app_settings` | language override | `LocaleHelper` |
| `display_preferences` | per-view `ViewPreferences` | `DisplayPreferencesStore` |
| `folder_preferences` | pinned and excluded folder paths | `FolderPreferencesStore` |
| `folder_display_preferences` | per-folder preference overrides | `FolderViewPreferencesStore` |
| `folder_thumbnails` | per-folder thumbnail override | `FolderThumbnailStore` |

Every store is an interface with a `SharedPreferences*` production implementation and an
`InMemory*` implementation for tests. Add both when you add a store.

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
4. New logic has at least one test.

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
- **There is no emulator or connected device on this machine.** `connectedDebugAndroidTest`
  cannot run here; instrumented tests can only be compile-verified. Never claim device-verified
  behaviour.

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
