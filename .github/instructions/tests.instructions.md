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
- Name tests with backticked sentences describing behavior, e.g.
  `` fun `pre-selection refreshes after a thumbnail is committed`() ``.
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

### Robolectric tests (the Android boundary)

Robolectric **is** a `testImplementation` dependency and an established pattern here. Two tests
use it, and both sit at the point where the app genuinely has to talk to the framework:

- `DefaultMediaPermissionHandlerTest` — `@RunWith(RobolectricTestRunner::class)` with `@Config`
  and shadows, because it exercises `RecoverableSecurityException` and the `IntentSender` APIs.
- `MediaStoreDataSourceTest` — Robolectric runner plus MockK for `Context`, `ContentResolver`,
  and `Cursor`, and a `TemporaryFolder` rule for the file operations.

Use Robolectric when the thing under test *is* the Android boundary. Do not reach for it to avoid
extracting pure logic, and do not add it to a test that would otherwise run plain.

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
- Assert on observable behavior (emitted state, recorded operations), not on which methods were
  called.
## Instrumented tests (`app/src/androidTest/`)

- Drive the stateless `*Content` composable (`FolderListContent`, `FolderDetailContent`), not the
  `*Screen` wrapper. Pass **every parameter by name**: that is what makes a signature change fail
  loudly instead of silently binding to the wrong argument.
- Resolve controls by `testTag` or by content description. `testTag("delete_button")` exists on
  **both** the folder selection top bar and the media selection top bar — assert against the one
  the test actually renders.
- Media actions live inside the media selection overflow menu, so a test must open
  `cd_more_options` before the action is reachable.
- A leading icon in a dropdown item carries `contentDescription = null` on purpose. Do not give
  it one to make a test easier; it would double-announce in TalkBack.
- **There is no emulator or device on this machine.** Instrumented tests can only be
  compile-verified with `compileDebugAndroidTestKotlin`. Never report instrumented behavior as
  verified.
