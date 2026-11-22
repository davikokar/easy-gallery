---
description: Testing conventions for Easy Gallery unit and instrumented tests.
applyTo: "app/src/test/**/*.kt, app/src/androidTest/**/*.kt"
---

# Test conventions

> **Maintenance — owner: Planner. Mode: routed.**
> If a convention or tool here is wrong or missing, say so in your report and name the section;
> do not edit it yourself. Conventions bind every test in the repo, so they change deliberately.

These rules apply only to test files. They complement the project-wide rules in
[AGENTS.md](../../AGENTS.md).

## Structure

- One behavior per test. Prefer several small tests over one big one.
- **Naming differs by source set, and this is not a style preference.**
  - Unit tests (`app/src/test/`): backticked sentences describing behavior, e.g.
    `` fun `pre-selection refreshes after a thumbnail is committed`() ``. This is the convention
    here and it works.
  - Instrumented tests (`app/src/androidTest/`): **plain identifiers only**, e.g.
    `fun preSelectionRefreshesAfterThumbnailIsCommitted()`. See the warning under *Instrumented
    tests* below — a backticked name with spaces breaks the entire suite, not just that test.
- Follow Arrange / Act / Assert, separated by blank lines.
- Mirror the production package under `app/src/test/java/com/davide/seddio/easygallery/`.

## Unit tests (`app/src/test/`)

These run on the JVM, and the module splits into two kinds. Pick the right one deliberately.

### Plain-JVM tests (the default)

The module does **not** set `isReturnDefaultValues`, so in a plain JUnit test **any unmocked
`android.*` call throws "not mocked"**. That shapes most of the rules below.

- Never call `android.net.Uri.parse(...)`, `Environment.DIRECTORY_*`, `Resources.getSystem()`, or
  any other real framework API from a plain test or from the production code it exercises.
  Resolve values from existing objects instead.
- Keep pure logic Android-free on purpose so it can be tested here: `GalleryTransformations`,
  `MediaLocation`, `CameraFolder`, `MediaDuration`, `ShareIntents`, and `WallpaperIntents` all
  exist in that shape. Follow it for new logic rather than widening the Robolectric surface.
- Anything locale-sensitive (coordinate formatting, durations) is pinned to `Locale.US`. Test it
  by setting the default locale to a comma-decimal locale such as Italy or Germany, and restore
  the previous default in `@After`.
- Resource files are checked here too, as files: `LocaleResourceParityTest` parses
  `src/main/res/values*/strings.xml` relative to the module directory, Gradle's working directory
  for unit tests, with no `Context` or Robolectric. Extend it, rather than adding a second
  per-locale check, when a localisation rule changes. The same pattern covers other files the
  build ships or reads: `BackupRulesParityTest` parses `src/main/res/xml/` with the JDK's XML
  parser, `ThirdPartyNoticesTest` and `FacePipelineVersionTest` read `src/main/assets/face/`, and
  the latter reads `../gradle/libs.versions.toml`. Compare text files line by line after splitting
  on `\r?\n`, so a test does not depend on the file's line endings.

### Robolectric tests (the Android boundary)

Robolectric **is** a `testImplementation` dependency and an established pattern here. Use it when
the thing under test *is* the Android boundary — which is what every current user has in common:

- `DefaultMediaPermissionHandlerTest` — `@RunWith(RobolectricTestRunner::class)` with `@Config`
  and shadows, because it exercises `RecoverableSecurityException` and the `IntentSender` APIs.
- `MediaStoreDataSourceTest` — Robolectric runner plus MockK for `Context`, `ContentResolver`,
  and `Cursor`, and a `TemporaryFolder` rule for the file operations.
- `AlbumDaoTest`, `AlbumStoreTest`, `MediaIndexDaoTest` and `MediaIndexStoreTest` — Robolectric
  runner plus `Room.inMemoryDatabaseBuilder`, because Room's generated code needs a real `Context`
  and a working SQLite. Both databases are tested this way; it is the pattern for any new DAO or
  `Room*` store.
- `MediaIndexingWorkerTest` — Robolectric runner plus `work-testing`'s `TestListenableWorkerBuilder`,
  driving the real `doWork` through the worker's injectable constructor; the pattern for a worker.
- `SharedPreferencesFolderViewPreferencesStoreTest` — Robolectric runner against real
  `SharedPreferences`, because what it proves is which file a store writes, that clearing a group
  in one file leaves the other alone, and that keys survive a new instance. The pattern for a
  `SharedPreferences*` store; it does not license touching `SharedPreferences` from a ViewModel
  test (*Doubles*).

The `InMemory*` half of a store is the counter-example: `MediaIndexInMemoryStoreTest` runs plain,
because an in-memory implementation touches no Android API. Do not put a Robolectric runner on one
out of symmetry with its Room sibling.

A test of what reaches the database **file** — `MediaIndexFaceDataScrubTest` — meets Robolectric's
SQLite, which is not a device's. `auto_vacuum` takes effect only through a following `VACUUM`. A
non-WAL file reports `journal_mode` `memory`, so a rollback-journal test sets the mode it means —
`TRUNCATE`, or `PERSIST` for the case that must not report a scrub — with `PRAGMA journal_mode`
on the store's write connection **outside any transaction**, since SQLite refuses a mode change
inside one, and then asserts it. What a mode means for the result is the pure
`deletionIsScrubbed`, tested plain in `MediaIndexScrubModeTest`; the file test proves the files.
A concurrent reader in a test holds a read snapshot only:
`beginTransactionNonExclusive` is `BEGIN IMMEDIATE` and blocks the code under test's own write.
Whatever such a test sets rather than observes is still to be checked on a device.

Do not reach for it to avoid extracting pure logic, and do not add it to a test that would
otherwise run plain.

## Flows and coroutines

- Assert on `Flow` emissions with **Turbine** (`flow.test { ... }`), not by collecting into a
  list manually.
- Use `runTest` and the injected test dispatcher. Never call `Thread.sleep`.
- Advance virtual time with the test scheduler instead of real delays.

## Doubles

- Prefer the hand-written `FakeMediaRepository` over mocking frameworks for repository behavior,
  and extend it when a test needs to observe a new operation. `GalleryViewModelTest` and
  `CreateFolderViewModelTest` both depend on it, so a change to `MediaRepository` breaks both.
- Inject the `InMemory*` implementation of every preference store rather than letting a test
  touch `SharedPreferences`. A relaxed MockK `Application` makes preference reads return mock
  defaults, which is safe but means persisted defaults will not match production.
- Reserve MockK for Android types a fake cannot stand in for — `Context`, `ContentResolver`,
  `Cursor`, `Uri` — as `MediaStoreDataSourceTest` does.
- MockK records every call on a mock, so a plain-JVM test that builds many mocks and evaluates them
  thousands of times can exhaust the test heap. Call `clearAllMocks(answers = false)` between
  evaluations, or avoid the mocks.
- Assert on observable behavior (emitted state, recorded operations), not on which methods were
  called.
## Instrumented tests (`app/src/androidTest/`)

- Drive the stateless `*Content` composable (`FolderListContent`, `FolderDetailContent`,
  `AlbumDetailContent`), not the `*Screen` wrapper. Pass **every parameter by name**: that is what
  makes a signature change fail loudly instead of silently binding to the wrong argument.
- Resolve controls by `testTag` or by content description. `testTag("delete_button")` exists on
  **both** the folder selection top bar and the media selection top bar — assert against the one
  the test actually renders.
- Media actions live inside the media selection overflow menu, so a test must open
  `cd_more_options` before the action is reachable.
- A leading icon in a dropdown item carries `contentDescription = null` on purpose. Do not give
  it one to make a test easier; it would double-announce in TalkBack.
- **Never use a backticked method name containing spaces here.** R8/D8 rejects them when dexing
  `androidTest`, with "Space characters in SimpleName ... are not allowed prior to DEX version
  040". The consequence is not one failing test: **the whole suite fails to build and nothing
  runs at all**, and the error names a DEX constraint rather than the test, so it does not read
  as a naming problem. Six methods in `AlbumDetailContentTest` had to be renamed before the suite
  could execute for the first time. Use plain identifiers; keep the backticked style for
  `app/src/test/`, where it works.
- Whether these can be **run** depends on whether a device is attached — `adb devices` settles it.
  With one, `./gradlew connectedDebugAndroidTest` runs the suite and instrumented behaviour can be
  verified for real. With none, `compileDebugAndroidTestKotlin` is the fallback, and instrumented
  behaviour must then be reported as compile-verified only, never as observed.
- A device test that measures or depends on device content must **fail** with a message starting
  `ENVIRONMENT:` when the device lacks what it needs — no live media, no item with a date or
  coordinates, a batch that indexed nothing — rather than passing vacuously.
- A device **measurement** that is slow or reads the phone's whole library is **opt-in**: it reads
  an instrumentation argument and skips through `Assume.assumeTrue` unless it is `true`, so
  `connectedDebugAndroidTest` reports it skipped rather than running it.
  `FaceIndexPassMeasurementDeviceTest` is the example, enabled with
  `-Pandroid.testInstrumentationRunnerArguments.faceIndexMeasurement=true`. It logs aggregates
  only, and works on a throwaway in-memory store, never the app's real one. The `ENVIRONMENT:`
  rule above still applies once it is opted in.
  Step 3.4 added two more: `FaceGroupingMeasurementDeviceTest` (`faceGroupingMeasurement=true`;
  `faceGroupingMeasurementReal=false` skips the real library, and
  `faceGroupingMeasurementSizes=2000,5000` — comma-separated — chooses the synthetic sizes), and
  `FaceGroupingWorkerMeasurementDeviceTest` (`faceGroupingWorkerMeasurement=true`), the one opt-in
  test that runs the real worker on the app's own index.
- **Only test sources set the face-data disclosure flag**, and a test that does lives in
  `androidTest` only. It snapshots the setting and `face_device_state` first and restores them
  exactly afterwards, in a step that runs even when the measurement fails — **flags first, then the
  face-data deletion**, holding the Tier C lock across both so no periodic run can write face data
  in between — and then checks the restore as a separate step.
  `FaceGroupingWorkerMeasurementDeviceTest` is the example.
- A test of the face pipeline needs an arm64 device; on another ABI it must assert the opposite
  answer rather than pass vacuously, as `FaceCapabilityDeviceTest` does. Emulators fail the
  capability check (ADR-0023).
- `androidTest` is in the same module, so it may use the app's `internal` members — the
  gazetteer's linear-scan oracle is the example. Acceptable for oracles and test seams.
- A device's real screen size matters: an item below the fold is not in the semantics tree in a
  usable state. Call `performScrollTo()` before asserting on anything low in a scrollable screen.
- **To press Back on a Compose dialog, send the key; do not use `Espresso.pressBack()`.** On the
  SM-G990B it intermittently fails with `RootViewWithoutFocusException`, because it waits for the
  picked root to take window focus and a dialog window does not always take it. `waitForIdle()`,
  then `InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)` —
  `pressSystemBack()` in `FaceDataDialogsTest` is the example.
- A `*Content` test that needs the screen to react to its own taps drives it through a small
  harness that holds the state and re-implements the ViewModel's action — `PickerHarness` in
  `NewAutomaticAlbumContentTest`, whose person toggle stops at `MAX_PEOPLE_PER_ALBUM` as the
  ViewModel does. The harness's copy of the rule proves only what the screen draws for it; the rule
  itself must also be pinned by a unit test of the real state holder, as
  `AutomaticAlbumPickerStateTest` pins the cap, so the two cannot drift apart unnoticed.

## Mutation checks

- A mutation check edits production code by hand to prove a test fails. **Restore the file before
  anything else runs**, and check that it is restored — `git diff` on the file, or its md5 against
  the copy taken before — rather than trusting the editor. A mutant left behind reproduces real test
  failures in the next run, which then read as a code defect: check for one before diagnosing.
