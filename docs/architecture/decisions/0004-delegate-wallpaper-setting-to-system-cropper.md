# ADR-0004: Delegate wallpaper setting to the system crop-and-set activity

- Status: Active
- Date: 2026-09-22
- Decision makers: Easy Gallery project maintainer

## Context

The full-screen viewer is gaining a "Use as background" action in the `MoreVert` overflow menu
that ADR-0003 introduced to `FullImageScreen` for "Copy to" / "Move to". Setting a wallpaper is
the first action in this app that hands a media item to a *system* activity for a destructive
device-level change, so the mechanism, the permission surface and the failure path all had to be
settled before the menu item ships.

The constraints that shaped the decision:

- `minSdk` is 28 and `targetSdk` is 36, so every `WallpaperManager` API considered here is
  available unconditionally; there is no version-gated branch to write.
- This repo has an established rule against `Intent.resolveActivity`, which returns `null` under
  Android 11+ package visibility even when a handler exists. ADR-0001 adopted try/catch plus a
  Toast fallback chain for the `geo:` maps launch, and the existing `Intent.ACTION_SEND` share in
  `FullImageScreen` follows the same shape. Anything added here has to match that precedent.
- `WallpaperManager.getCropAndSetWallpaperIntent()` is not exempt from that filtering. It calls
  `PackageManager.queryIntentActivities` **from the calling app's own UID**, so Android 11+
  package-visibility rules apply to us, and the wallpaper cropper is not in the set of packages
  that are automatically visible. Without a declaration, the lookup finds nothing.
- `MediaStoreDataSource` types any `image/gif` item as `MediaType.GIF`, distinct from
  `MediaType.IMAGE`, so the viewer can already discriminate the three media types at the call
  site without inspecting MIME strings.

## Decision

Wallpaper setting is **delegated to the system crop-and-set activity**. The app builds no bitmap,
holds no wallpaper permission, and owns no success UI.

- The action launches `WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)`
  with the item's MediaStore content URI. The result is intentionally ignored.
- The `SET_WALLPAPER` permission is **not** requested. No bitmap is decoded or set in-process.
- The repo's **first** `<queries>` element is declared in `AndroidManifest.xml`, for the action
  `android.service.wallpaper.CROP_AND_SET_WALLPAPER` with `<data android:mimeType="image/*"/>`.
- The launch is pre-checked with `isWallpaperSupported()` and `isSetWallpaperAllowed()`, then
  degrades through a fixed chain: crop-and-set intent → `Intent.ACTION_ATTACH_DATA` chooser →
  Toast.
- The launch catches **both `IllegalArgumentException` and `ActivityNotFoundException`**.
  `getCropAndSetWallpaperIntent` throws `IllegalArgumentException` — not
  `ActivityNotFoundException` — when the URI is not a content URI, when its MIME type does not
  resolve to `image/*`, or when no activity can handle the action. Catching only
  `ActivityNotFoundException`, which is the pattern used everywhere else in this repo, would
  crash the app.
- The action is offered for `MediaType.IMAGE` **only**. `MediaType.VIDEO` is excluded because a
  static wallpaper cannot represent it. `MediaType.GIF` is excluded as a deliberate **product**
  rule: MediaStore types GIFs as `image/gif` and the system cropper would accept one, but the
  result is a frozen first frame presented as if animation were expected.
- The menu item stays **visible** when wallpaper support is unavailable and reports the failure
  with a Toast, rather than being conditionally hidden.

## Alternatives Considered

**Set the wallpaper in-process with `WallpaperManager.setStream` / `setBitmap`.** Rejected. It
requires the `SET_WALLPAPER` permission, must run off the main thread, and decodes the full-size
source — potentially a 50 MP image — into the app heap, which is a direct OOM path in a gallery
whose whole purpose is holding large photos. It also strips the user of the crop UI and of the
home-screen / lock-screen target choice, and it makes the app responsible for its own progress,
success and failure reporting. The app would take on every cost in exchange for control it does
not need.

**Use `Intent.ACTION_ATTACH_DATA` as the primary path.** Rejected as primary, kept as fallback.
The chooser it raises is generic: it surfaces unrelated targets such as "set as contact photo"
alongside the wallpaper option, so a menu item labelled "Use as background" would open a picker
that is not about backgrounds. It is retained only as the second rung of the fallback chain,
where an imperfect affordance beats no affordance.

**Hide the menu item unless `isWallpaperSupported()` returns true.** Rejected. The item would
vanish with no explanation on devices and work profiles where wallpaper changes are restricted,
which is silent feature loss — the hardest class of bug to diagnose from a user report, because
there is nothing to report. A visible item plus a Toast states the reason.

## Consequences

### Positive

- Zero permissions added. There is no Google Play Data Safety implication, in contrast to
  ADR-0001's `ACCESS_MEDIA_LOCATION`.
- No in-process bitmap decoding, therefore no OOM path introduced on large images.
- The user gets the OEM crop UI and the home-screen / lock-screen target choice for free,
  including whatever parallax, blur and depth-effect options the OEM ships.
- Every failure mode is user-visible: the pre-checks, the two caught exception types and the
  final Toast mean the action never fails silently.
- The implementation is a stateless, fire-and-forget launch with no ViewModel state, no
  coroutine and no new repository surface.

### Negative

- **The app cannot know whether a wallpaper was actually applied.** The result is deliberately
  ignored, and confirmation lives in the other app. Any future request for in-app confirmation
  ("Wallpaper set") cannot be satisfied without revisiting this ADR.
- Behaviour varies by OEM. The crop UI, the available targets and the quality of the result are
  outside the app's control and cannot be tested exhaustively.
- The `<queries>` declaration is a manifest-level coupling to a string action name. If the action
  is renamed or the MIME filter narrowed by a future platform release, the lookup fails silently
  and the feature degrades to the fallback chain with no compile-time signal.
- On a device with no handler at all, an advertised menu item resolves to a Toast. This is the
  accepted cost of keeping the item visible.
- This ADR introduces `<queries>` to a manifest that ADR-0001 explicitly kept free of it. The
  package-visibility surface is no longer empty, and future additions must be justified
  individually rather than assumed free.
- The `IllegalArgumentException` catch is a repo-local exception to an otherwise uniform
  intent-launch pattern. It looks redundant next to the neighbouring `ActivityNotFoundException`
  handlers and is a plausible target for a well-meaning cleanup that would reintroduce the crash.

## Relationship to Existing ADRs

This record supersedes nothing. ADR-0001, ADR-0002 and ADR-0003 all remain `Active`.

- **[ADR-0001](0001-on-demand-media-location-metadata.md)** — unaffected. Its precedent that the
  app never uses `Intent.resolveActivity` and always wraps launches in try/catch with a Toast
  fallback is **extended** here: the same shape is used, with a second exception type and a
  supporting `<queries>` entry that ADR-0001 did not need.
- **[ADR-0002](0002-viewmodel-owned-transient-viewer-state.md)** — satisfied by construction. The
  action is stateless and fire-and-forget, so there is no transient viewer state to survive
  Activity recreation. If a future in-app target picker (home / lock / both) is ever added, its
  state must be ViewModel-owned per ADR-0002 and must not use plain `remember`.
- **[ADR-0003](0003-explicit-media-operation-target.md)** — not applicable. Setting a wallpaper is
  not a repository copy or move, produces no new file, and must **not** be routed through
  `performOperationWithPath` or `startOperationForMedia`; doing so would set an explicit operation
  target that nothing clears. ADR-0003's layout trap still binds: no new dialog may be introduced
  inside the `AnimatedVisibility` bottom bar.

## References

- `app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt` — the `MoreVert`
  overflow menu and the existing `Intent.ACTION_SEND` share launch
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaLocationUi.kt` — the
  try/catch + Toast fallback chain this decision follows
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaItem.kt` — `MediaType`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — MIME-to-
  `MediaType` mapping, where `image/gif` becomes `MediaType.GIF`
- `app/src/main/AndroidManifest.xml` — the new `<queries>` element
- `WallpaperManager.getCropAndSetWallpaperIntent(Uri)`, `isWallpaperSupported()`,
  `isSetWallpaperAllowed()`
- `Intent.ACTION_ATTACH_DATA`
- [ADR-0001](0001-on-demand-media-location-metadata.md) — intent-launch precedent; still `Active`
- [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) — ViewModel-owned transient viewer
  state; still `Active`
- [ADR-0003](0003-explicit-media-operation-target.md) — explicit operation target; still `Active`,
  and deliberately not used by this feature
