# ADR-0002: ViewModel-owned transient viewer state across configuration changes

- Status: Active
- Date: 2026-09-21
- Decision makers: Easy Gallery project maintainer

## Context

`MainActivity` does not declare `android:configChanges`. Every configuration change — device
rotation, dark-mode toggle, font/display-size change, system locale change — therefore destroys
and recreates the Activity, as does the app's own `activity.recreate()` call from the in-app
language switcher.

Activity recreation surfaced a concrete bug: full-screen video playback restarted from position 0
on rotation. The screen-level `ExoPlayer` in `FullImageScreen.kt` is created with `remember {}`
and released in a `DisposableEffect`, so recreation disposed the composition, released the player,
and rebuilt it with no position. The viewer itself survived only because `selectedMedia` and
`currentMediaList` already live in `MediaViewerState` inside `GalleryViewModel`, which is retained
across recreation by the `ViewModelStore`.

Two existing constraints shaped the response:

- `MainActivity.attachBaseContext` is where the per-app language override is applied via
  `LocaleHelper.wrap` / `createConfigurationContext`. It re-runs on Activity recreation and does
  **not** run on a configuration change the Activity handles itself.
- Viewer state is deliberately excluded from the saveable machinery. `FullImageScreen` is rendered
  **outside** `rememberSaveableStateHolder`'s `SaveableStateProvider` in `MainActivity` because a
  retained `rememberPagerState` slot previously caused the viewer to reopen on the wrong item.

## Decision

Easy Gallery lets `MainActivity` be recreated on configuration change and does not declare
`android:configChanges` on it.

Consequently, any transient viewer state that must survive a configuration change is owned by
`GalleryViewModel` — specifically by `MediaViewerState` — and not by composition-local `remember`
and not by `rememberSaveable`.

Applied to the playback bug: `MediaViewerState` stores `(uri, positionMs, playWhenReady)`. The
value is captured in the `ON_STOP` lifecycle handler in `FullImageScreen`, before `pause()` is
called, and consumed when the player binds to a media item.

## Alternatives Considered

**Declare `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` on
`MainActivity`.** Rejected. It masks only one recreation trigger; dark-mode, font/display-size and
system locale changes, plus the app's own `activity.recreate()` from the in-app language switcher,
all still recreate the Activity and would still restart the video. Critically,
`attachBaseContext` does not re-run on a configuration change the Activity handles itself, and
that is exactly where the per-app language override is applied. This repo has already been burned
twice by locale-override fragility — deprecated `Resources.updateConfiguration` flakiness, and
Play App Bundle per-language resource splitting — so weakening the path that keeps the override
reliable is disproportionate. The blast radius is app-wide and far exceeds the bug.

**Use `rememberSaveable` inside `FullImageScreen`.** Rejected. Saved-instance state survives
process death while `selectedMedia` does not, so the playback position and the item it belongs to
would have different lifetimes — a latent stale-restore trap. It also re-entangles viewer state
with the saveable machinery that already caused a real regression, which is why `FullImageScreen`
is deliberately rendered outside `SaveableStateProvider`.

**Hoist the `ExoPlayer` instance itself into `GalleryViewModel`.** Rejected. `ExoPlayer.Builder`
requires a `Context`, so the ViewModel would hold Activity-derived state past recreation (leak
risk) unless built from the Application context. It would additionally require release in
`onCleared()` and an explicit surface re-attachment protocol, and it places a media resource
inside an already ~700-line god class.

## Consequences

### Positive

- One mechanism fixes every recreation trigger, not just rotation.
- `attachBaseContext` keeps re-running on every configuration change, so the per-app language
  override stays reliable.
- The retained state is plain Kotlin data in `MediaViewerState`, unit-testable on a plain JVM with
  no Robolectric or instrumentation.

### Negative

- Every future piece of transient viewer state faces the same requirement: it must be
  ViewModel-owned or live inside the `SaveableStateProvider`. Plain `remember` in a screen does not
  survive this repo's `if/else` navigation either.
- There is a brief visible re-layout on rotation while the Activity is recreated and the player
  rebinds.
- State is intentionally lost on process death. Accepted: the viewer is transient by design.
- Guardrail for future contributors: do **not** "fix" a future configuration-change bug by adding
  `android:configChanges` to the manifest. Move the affected state into the ViewModel instead.

## References

- `app/src/main/java/com/davide/seddio/easygallery/ui/MediaViewerState.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt`
- `app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt` — `attachBaseContext`,
  `rememberSaveableStateHolder` / `SaveableStateProvider` usage
- `app/src/main/AndroidManifest.xml` — `MainActivity` declaration, intentionally without
  `android:configChanges`
