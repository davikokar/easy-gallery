# ADR-0025: Slideshow — what plays, in what order, who owns it, and how video fades

- Status: Active
- Date: 2026-11-24
- Adopted: 2026-11-24
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer; the product
  questions (1–22) were answered by the maintainer on 2026-11-24, who also approved the new pattern
  of §10 (the first code to keep the screen on and to hide the system bars)

> ~~**Nothing below is built yet.** The Planner's step plan for the slideshow (2026-11-24) builds it.~~
> **Built and verified 2026-11-24.** The code compiles, the unit suite passes, and
> `connectedDebugAndroidTest` passed on a device after two test fixes (Amendment 1 §7). What landed
> is described in [ARCHITECTURE.md](../ARCHITECTURE.md) §1, §3, §4, §6, §8 and §11; where the build
> reads this record differently, or decided what it left open, **Amendment 1** at the end says so and
> why. §9's video crossfade carries device checks, listed at its end, that only a run on hardware can
> settle; its fallback is decided here so that a failed check does not reopen the record. **Three of
> the seven are not yet observed** — the playlist of the device run held photos only — and the note
> under the list says which. **Amendment 2** records a later remediation of video handling: a video
> that never renders a first frame is now skipped instead of freezing the slideshow.

> **Corrected in place, 2026-11-24, before any of it was built.** The first text did not match two
> of the maintainer's answers: it gave the defaults as a 5-second interval with Include GIFs on, and
> the Order labels as As shown / Reversed / Random. The maintainer's defaults are a 3-second interval
> with Include GIFs off (§5), and the labels are Ascending / Descending / Random (§3), with unchanged
> meaning. An in-place correction rather than an amendment, because nothing had been built on the
> wrong text.

## Context

The maintainer asked for a slideshow of a folder or an album: the items play full screen, one after
another, with a transition, until the end or for ever. Nothing in the app does this today, and
building it touches more than one screen:

- **Where it starts and what it plays.** Folder Detail View and Album Detail View each show a list
  that is *filtered* (the Search field and the persisted Filter media setting), *sorted* (by a
  per-container sort that may be Random), and sometimes *grouped*. The Full-screen viewer opens on
  the filtered, flat-sorted list and ignores grouping (`GalleryViewModel.selectMedia`,
  `AlbumDetailScreen`'s `openMedia`). The slideshow has to say which of those it follows.
- **Random sort cannot be recomputed.** `GalleryTransformations.sortMedia` shuffles anew on every call
  under `SortType.RANDOM`, and both detail views call it again on every upstream emission. The order
  the view shows therefore exists only as whatever list was last emitted; it can be captured, not
  rebuilt.
- **Orientation is not known today.** `MediaItem` carries no dimensions, and `MediaStoreDataSource`
  queries none. `MediaStore.MediaColumns.WIDTH` and `HEIGHT` exist from API 16 and are the media's
  stored dimensions, before rotation; `MediaStore.MediaColumns.ORIENTATION` is API 29, while images
  have had `MediaStore.Images.ImageColumns.ORIENTATION` (the same `"orientation"` column) since API 1.
  On API 28 a video's rotation is not in `MediaStore` at all. Verified on developer.android.com,
  2026-11-24.
- **Video cannot simply be faded.** The viewer draws video through media3's `PlayerView`, whose
  default surface is a `SurfaceView`. A `SurfaceView`'s content is a separate surface composited by the
  system, not drawn into the app's view hierarchy, so a Compose `graphicsLayer` alpha over it fades
  nothing. media3's own guidance (*Choose a surface type for PlayerView*, developer.android.com,
  read 2026-11-24) names `TextureView` as the choice when the surface must be animated, at the cost of
  up to 30% more power during playback on some devices, no HDR output and no secure (DRM) output.
  media3 is pinned at 1.5.0 (`gradle/libs.versions.toml`), where `media3-ui-compose`'s `PlayerSurface`
  does not exist and `PlayerView`'s surface type is fixed by layout attributes at construction;
  `Player.setVideoTextureView(TextureView)` and `TrackSelectionParameters.Builder.setTrackTypeDisabled`
  are both in 1.5's API (`api.txt` at 1.5.1, read through Context7 on 2026-11-24).
- **Nothing in the app keeps the screen on or hides the system bars.** `MainActivity` calls
  `enableEdgeToEdge` and nothing else touches the window. A slideshow left on the default screen
  timeout goes dark after 30 seconds.
- **Rotation and background look the same to a lifecycle observer.** `MainActivity` has no
  `android:configChanges` ([ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) forbids it), so
  rotation recreates the activity and delivers `ON_STOP` exactly as leaving the app does. The
  maintainer wants the two treated differently (question 10).
- **The face data disclosure** is shown at the app's root over every screen but the Full-screen
  viewer ([ADR-0024](0024-face-data-disclosure-and-delete-switch.md) Amendment 1 §5). A slideshow is
  as immersive as the viewer.

## Decision

### 1. Entry: the last item of the detail views' Overflow menu

- **A Slideshow item is the last item of the Browse top bar's Overflow menu in Folder Detail View and
  in Album Detail View, Favourites included** (question 1). No main view has it.
- **In Album Detail View the order becomes … Change album thumbnail, Album info, Slideshow.** The UI
  vocabulary said the Album info item is the last; the maintainer's instruction that Slideshow is last
  wins, and Album info moves to directly before it. Nothing ties Album info to the last place but the
  sentence that said so; the vocabulary edit is the Designer's.
- It opens the **Slideshow dialog**. ~~The item is always shown; a container with nothing to play shows
  the dialog's zero state (§5) rather than hiding the item.~~ *Amended 2026-11-24 — Amendment 1 §1:*
  the item is shown whenever the folder or album holds any media at all, before the Search field and
  Filter media, and hidden only when it holds none. A container whose media the options exclude still
  shows the item, and the dialog's zero state.

### 2. What plays: the container's whole membership, filtered only by the slideshow's own switches

- **The source is everything the container holds**: the folder's media (`GalleryViewModel.mediaInFolder`),
  the album's resolved members, or the Favourites list `MainActivity` already passes to Album Detail
  View. **Neither the Search field nor the saved Filter media setting applies** (question 2): a search
  is a momentary lookup, and Filter media is a persisted browse setting the slideshow dialog does not
  show — the same reasoning that took the persisted filter off the New album screen.
- **Images always play.** Videos play only with **Include videos** on, GIFs only with **Include GIFs**
  on.
- **Orientation** (question 12): **All** plays everything; **Landscape only** plays items wider than
  tall; **Portrait only** items taller than wide, judged on rotation-corrected dimensions (§4). A
  square item, or one whose size is unknown, plays only under All.

### 3. Order: Ascending, Descending or Random — relative to the order the view shows

- **The base order is the flat order the view sorts by**, ignoring grouping, as the Full-screen viewer
  does (question 5): `GalleryTransformations.sortMedia` over the whole membership, with the view's
  effective sort type and order (per-folder and per-album overrides and the camera default included)
  and the index's date-taken projection.
- **Order** in the dialog (question 3): **Ascending** plays the base order — the list as the view
  shows it; **Descending** plays that list reversed; **Random** shuffles it. Ascending and Descending
  are **relative to the folder's or album's own sort**, not a sort of their own: if the view is sorted
  newest first, Ascending plays newest first. That is why the labels name no sort field — the field is
  whatever the view is sorted by, and the dialog does not repeat it. In code the option is an enum with
  `ASCENDING`, `DESCENDING` and `RANDOM`, labelled by the string keys `slideshow_order_ascending`,
  `slideshow_order_descending` and `slideshow_order_random`.
- **When the view's own sort is Random** (question 4) there is no order to recompute, so **the order is
  captured when Play is pressed**: the list the view is showing at that moment, in that order. Items the
  view hides (by the search or the media-type filter, which do not apply here) have no shown position;
  they are inserted into that list at random positions, so that turning Include videos on over an
  images-only view does not put every video at the end. Ascending plays this captured list; Descending
  plays it backwards. There is no fallback to Date taken.
- **For any other sort**, recomputing over the whole membership gives the shown items in the shown
  relative order — the sort is stable and filtering keeps order — so no capture is needed, and none is
  done.
- **Loop with Random** (question 17) reshuffles at the start of each pass, and a new pass never starts
  on the item just shown, unless the playlist has one item.
- ~~**Known debt, recorded and not fixed** (question 22): `sortMedia` sorts media by Size and Path as by
  date added (its `else` branch). The slideshow follows the view, so it inherits this.~~ *Fixed later,
  2026-11-24:* `sortMedia` now sorts media by Path and by Size, with ties broken by path and then URI
  in either direction, so the slideshow, which follows the view, follows both sorts too. The decision
  of this section — the base order is the view's flat sort — is unchanged, so this is a note, not an
  amendment; what landed is in ARCHITECTURE.md §11.

### 4. Dimensions on `MediaItem`

- **`MediaItem` gains `displayWidth: Int?` and `displayHeight: Int?`**, trailing and defaulting to
  null, so no existing constructor call changes. They are the dimensions *as displayed*: the stored
  `WIDTH`/`HEIGHT` swapped when the orientation is 90 or 270 degrees, and null when either is missing
  or not positive.
- **`MediaStoreDataSource` reads them in its one media query.** `WIDTH` and `HEIGHT` always; the
  `"orientation"` column for images on every API level, and for videos **only on API 29+**, behind an
  explicit `Build.VERSION.SDK_INT` guard — lint does not run here, so the guard is checked by reading.
- **On API 28 a video's orientation is unknown**, so a video recorded in portrait whose stored
  dimensions are landscape counts as landscape. Accepted by the maintainer (question 12) and recorded
  as known debt in ARCHITECTURE.md §11.
- *Why on `MediaItem` and not a second query at dialog time:* the dialog's count is live (§5) and
  needs every item's orientation on each change; three integer columns in the query the app already
  runs cost nothing measurable, and the pure classification is unit-testable on a plain JVM.

### 5. The dialog and the options

- **Live count** (question 13): the dialog shows how many items will play under its current draft; at
  zero it says "Nothing to play" and Play is disabled.
- **The options** (question 20): **Interval** (1 to 60 seconds, a − / + stepper, default 3),
  **Transition** (Fade or None, default Fade), **Include videos** (default on), **Include GIFs**
  (default off), **Order** (Ascending, Descending, Random; default Ascending), **Loop** (default off),
  **Orientation** (All, Landscape only, Portrait only; default All). The defaults are the
  maintainer's; changing any of them later needs no new record.
- **Interval is the time an item is fully visible**, not counting the fade (question 15). It does not
  apply to videos, which play to their end (§8); the dialog says so.
- **Saved only when Play is pressed** (question 19). Dismissing the dialog discards the draft. The
  options area is collapsed each time the dialog opens. **One global set**, not per folder or album.
- **Stored in a new preferences file, `slideshow_options`**, through `SlideshowOptionsStore` with a
  `SharedPreferences*` and an `InMemory*` implementation, under `AGENTS.md`'s preference-store rules.
  [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 2's two tests, in order: it is not
  structured — seven flat scalars of fixed shape; and no index depends on it. *Why not `app_settings`:*
  that file may take *a* scalar, and this is one feature's bundle of seven, the shape
  `display_preferences` already holds for display settings. *Why not `display_preferences`:* one store
  per file. **It is backed up** — the options are the user's choice, like display preferences — so it
  needs no backup rule: the rules files only exclude.
- The dialog is not one of the five display-preference dialogs: it has no *Apply only to this folder*
  checkbox and is not draft-and-commit in ADR-0006's sense, because nothing in it is per container.

### 6. Who owns it: a feature ViewModel, `SlideshowViewModel`

- **A new Activity-scoped `SlideshowViewModel`** (`by viewModels()` in `MainActivity`) owns the saved
  options, the playlist, the current position, the phase (countdown, playing, paused), the interval
  timer and the recorded video position. `GalleryViewModel` and `AlbumsViewModel` gain nothing: the
  detail screens compute the source order (§3) and hand it over with the options when Play is pressed.
  It is the precedent `AGENTS.md` names — a feature-scoped ViewModel rather than more state in the hub.
- **The playlist is fixed when Play is pressed** (question 14). An item that fails to load is skipped.
  *Amended 2026-11-24 — Amendment 2 §1–§2:* a video also counts as failing to load when it ends
  without having rendered a first frame, or has played for 10 seconds without one.
  A pass in which no item could be shown ends the slideshow, so that a looping slideshow of vanished
  media cannot spin.
- **Rotation continues where it was** (question 10): the ViewModel and its timer survive the
  activity's recreation. The video player lives in the screen, as the viewer's does, and the video's
  position is recorded at `ON_STOP` before pausing and consumed when the player binds again —
  [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md)'s pattern, as plain functions, never a
  `StateFlow`.
- **Background pauses, and the slideshow stays paused on return** (question 10). `ON_STOP` is a
  pause only when the activity is not changing configurations (`Activity.isChangingConfigurations`);
  otherwise it is a rotation and nothing is paused. Going to the background during the countdown
  leaves the slideshow paused on its first item.
- **Not restored after process death.** Nothing of it is saved. The detail view it was started from is
  not restored either, so the user lands where any process death lands them.

### 7. Navigation: a branch above the Full-screen viewer

- **The Slideshow screen is its own branch at the top of `MainActivity`'s chain**, above the
  Full-screen viewer and, like it, **outside the `SaveableStateProvider`** and without the Main view
  bar. The chain stays a plain `if / else if` ([ADR-0013](0013-albums-as-third-display-mode.md)).
- **The detail view underneath keeps its saved state** under its own `screenKey` while it is out of
  composition, exactly as it does under the viewer. The end of a non-looping slideshow, Back, and the
  close control all simply end the slideshow, and the chain falls through to the detail view where the
  user left it (question 11). The dialog's open flag is cleared when Play is pressed, so it does not
  reopen on return.
- **`resolveShownScreen` gains the branch** as a new `ShownScreen.SLIDESHOW`, first, on which the
  Main view bar is hidden. The chain's order is written three times (ARCHITECTURE.md §4); all three
  change together.

### 8. Playback

- **Countdown** (question 8): three seconds over black before the first item. A tap or Back during it
  cancels and returns to the detail view.
- **While playing** (question 9): Back exits. A tap pauses or resumes, and shows the play/pause and
  close controls for three seconds. There is **no swipe, no previous and no next**. *As built, while
  paused the controls stay shown — Amendment 1 §3.*
- **Videos play to their end, muted, with no sound at all** (question 6): the audio track type is
  disabled in the player's track selection, so no audio is decoded and no audio focus is requested,
  and the volume is zero as a second guard. Music playing in another app carries on.
- **GIFs animate for the interval, then move on** (question 7). Pausing stops the interval, not the
  GIF's own animation.
- **Fade is about 500 ms** between any two items (question 16). The interval timer starts when the
  incoming item is fully visible. An incoming item is shown only once it is ready — an image or GIF
  decoded, a video's first frame rendered — so a slow load lengthens the outgoing item's time on
  screen; the next image is prefetched while the current one shows to keep that short. **None** cuts
  instead of fading. *Amended 2026-11-24 — Amendment 2 §2:* the wait for a video's first frame is
  bounded at 10 seconds of playing, after which the video is skipped; no such bound applies to a
  still.

### 9. Video fades by drawing into a `TextureView`

The maintainer asked for a true crossfade for videos too, if achievable.

- **Achievable, on a `TextureView`.** The slideshow does not use `PlayerView`. It hosts a plain
  `TextureView` in an `AndroidView` and gives it to the player with `setVideoTextureView`. A
  `TextureView` is drawn into the app's own view hierarchy, so a Compose `graphicsLayer` alpha on the
  `AndroidView` fades the video like any image. The view is sized to the video's aspect ratio from
  `Player.Listener.onVideoSizeChanged`, letterboxed on black.
- **One player, and the outgoing video becomes a still.** A video only leaves the screen at its end,
  so the outgoing layer never needs to move: when a video ends, its last frame is copied with
  `TextureView.getBitmap()` and shown as an image while the next item fades in over it. One player and
  one decoder, as in the viewer; no second player is ever alive. An incoming video fades in from its
  first rendered frame (`onRenderedFirstFrame`) and plays through the fade.
- **The costs, accepted:** `TextureView` draws more power than `SurfaceView` during playback — up to
  30% on some devices, by media3's own account — and gives no HDR or secure output. This app plays only
  local files and holds no DRM content; a slideshow is a short, foreground use. The viewer keeps its
  `SurfaceView`, so the cost is only the slideshow's.
- **Fallback, decided now: a dip to black.** If a device check below fails — the `TextureView` does not
  fade, or the last-frame copy is blank or wrong — transitions that involve a video become a dip to
  black instead (the outgoing item fades to black, the incoming fades in from black, half the fade
  each), and photo-to-photo and GIF transitions keep the crossfade. The change is recorded as an
  amendment with the observation that forced it; it does not reopen the choice of `TextureView`, which
  the dip needs too for the incoming fade.
- **Must be verified on a device** (no plain-JVM or Compose test can observe them):
  1. A video fades in and out with the rest, with no black flash at its start and no frozen frame left
     behind, in both orientations.
  2. The last-frame copy shows the video's final frame, not black, at the start of the next fade.
  3. No audio is heard, and music playing in another app does not pause or duck.
  4. The screen stays on through a slideshow longer than the device's screen timeout, and times out as
     usual while paused.
  5. The status and navigation bars are hidden on the Slideshow screen, return when it ends by each of
     its four ways out (the end, Back, the close control, a cancelled countdown), and stay hidden after
     a rotation.
  6. A rotation mid-video continues the video from where it was, and mid-photo continues the interval;
     leaving the app pauses it, and returning leaves it paused.
  7. The face data disclosure, when due, waits until the slideshow ends.

  > **Status 2026-11-24, after the verification phase's device run.** The playlist held **photos
  > only**, so no video was in it. Observed, read from `dumpsys window` and `dumpsys power` rather
  > than from screenshots: **5** in part — the bars hidden on the Slideshow screen and restored when
  > it ended (which of the four ways out were each exercised is not recorded, nor the bars after a
  > rotation); **4** in part — the screen kept on while playing (the screen-bright wake lock held for
  > the app), its lapse while paused not recorded; **6** for photos — a rotation keeps the slideshow
  > playing, and leaving the app pauses it and it stays paused on return. Also observed: images
  > advance at the interval. **Not observed:** **1**, **2** and **3**, and the mid-video half of
  > **6**, all of which need a video in the playlist; and **7**, which is held by unit tests of
  > `faceDataDisclosureDue` and was not provoked on the device. The fallback above stays decided and
  > untriggered until 1 and 2 are run.

### 10. The screen stays on, and the system bars hide, only here

- **The screen is kept on only during the countdown and while playing** (question 18), through the
  hosting view's `keepScreenOn`, cleared when paused and when the screen leaves composition. It needs
  no permission: it is the window flag, not a wake lock.
- **The status and navigation bars are hidden on the Slideshow screen and restored when it ends**,
  through `WindowCompat.getInsetsController(window, view)` from `androidx.core`, already a dependency,
  with the bars revealed transiently by a swipe from the edge. They are hidden again when the screen is
  recomposed after a rotation.
- **This is the first code in the app that does either, and the maintainer approved it as a new
  pattern on 2026-11-24.** It needs **no new dependency, no new permission, no `<queries>` entry and
  no `android:configChanges`**. Any later screen that wants either follows this one: scoped to its
  composable, restored on dispose, never set from the activity for the whole app.

### 11. The face data disclosure waits for the slideshow

The disclosure waits while the Slideshow screen is shown, as it waits for the Full-screen viewer
(question 21) — [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) Amendment 2.

## Alternatives Considered

**Keep the viewer's `PlayerView` on a `SurfaceView` and dip to black for video.** Works on every API
level with no new surface, and costs no extra power. Rejected as the first choice because the
maintainer asked for a true crossfade if achievable, and it is; kept as the fallback in §9.

**`PlayerView` with `surface_type="texture_view"`.** At the pinned 1.5.0 the surface type is a layout
attribute, so it would mean inflating an XML layout for one attribute, and it would bring `PlayerView`'s
shutter and its touch handling, which have cost this repository a session before (the viewer's tap
dead end, repository memory). A bare `TextureView` needs only the aspect ratio, which the player reports.

**Upgrade media3 for `media3-ui-compose`'s `PlayerSurface`.** A dependency upgrade the maintainer must
approve, for a composable that still needs a `TextureView` to fade. Not needed.

**Two players, so that an outgoing video keeps moving during the fade.** Two decoders alive at once —
the trap the viewer was fixed for. The outgoing video has ended anyway; its last frame is all there is
to show.

**Follow the view's search and Filter media.** Rejected by the maintainer (question 2).

**Fall back to Date taken when the view's sort is Random.** Rejected by the maintainer (question 4):
the user should see what they were looking at.

**Recompute the order at Play under Random.** Impossible: a recomputation is a new shuffle (Context).

**Put the slideshow state in `GalleryViewModel`.** It is the largest file in the app, and albums would
need it too; `AGENTS.md` says to extract.

**Keep the player in the ViewModel, so rotation never rebuilds it.** Smoother across a rotation, but a
ViewModel holding an `ExoPlayer` is not testable on a plain JVM without a factory, and ADR-0002's
record-and-consume is already proven on the viewer.

**Store the options in `app_settings`.** See §5.

**Hide the Slideshow item when nothing can play.** The count depends on the options, which the user can
change in the dialog; a hidden item would hide the way to change them. *Still rejected — Amendment 1
§1 hides the item only for a container with no media at all, where no option can change the count.*

## Consequences

### Positive

- One feature ViewModel holds the whole slideshow; the detail screens add a menu item and a dialog.
- A true crossfade for every kind of item, with a decided fallback if a device disagrees.
- Rotation, background and the face data disclosure each have a defined behaviour.
- `MediaItem` now knows each item's displayed shape, which any later feature can use.

### Negative

- Video in the slideshow draws more power than in the viewer, and plays without HDR.
- Two more fields on `MediaItem` and up to three more columns in the app's main `MediaStore` query.
- On API 28, video orientation is unknown, so the orientation filter can misplace portrait videos.
- A new preferences file, and so a row in `AGENTS.md`'s table and two store implementations.
- `FolderDetailContent` and `AlbumDetailContent` gain a callback, which breaks their instrumented tests
  by design. *As built, two parameters each: `hasSlideshowMedia` and `onSlideshowClick` (Amendment 1
  §1).*
- A slideshow does not survive process death.
- Under the view's Random sort, items the view hides land at random places in an otherwise captured
  order — true to the view's order for what was shown, arbitrary for what was not.

## References

- [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) — record-and-consume of the video
  position; no `configChanges`
- [ADR-0006](0006-per-folder-display-preference-overrides.md),
  [ADR-0008](0008-camera-folder-default-sort-order.md),
  [ADR-0021](0021-per-album-display-preference-overrides.md) — the effective sort the base order uses
- [ADR-0013](0013-albums-as-third-display-mode.md) — the navigation chain stays
- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 2 — the two tests for a preferences
  file
- [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) Amendment 2 — the disclosure waits
- `AGENTS.md` *State ownership*, *Preference stores*, *What to ask before doing*
- developer.android.com: `MediaStore.MediaColumns` (`WIDTH`, `HEIGHT`, `ORIENTATION`); media3 *Choose
  a surface type for PlayerView*; media3 *battery consumption*
- media3 1.5.1 `api.txt`: `Player.setVideoTextureView`, `TrackSelectionParameters.Builder.setTrackTypeDisabled`

## Amendment 1 — 2026-11-24 — What the build changed, and what it settled

> **Written by the Planner after the build, from the code** (`data/SlideshowOptions.kt`,
> `data/SlideshowOptionsStore.kt`, `data/SlideshowPlaylist.kt`, `ui/SlideshowViewModel.kt`,
> `ui/SlideshowScreen.kt`, `ui/components/SlideshowDialog.kt`, and the two detail screens). §1 and §3
> depart from the Decision's text, and the Decision's §1 and §8 are marked in place to point here.
> The rest settles what the Decision left to the build, or states precisely what it said
> loosely. None of it changes a choice the maintainer made: the defaults, the labels, the file and
> the pattern of §10 are as decided, and the defaults and the file's name and keys were checked
> against the code and its tests on 2026-11-24.

### 1. The Slideshow item is hidden for a container with no media at all

**What landed.** `FolderDetailContent` and `AlbumDetailContent` each take `hasSlideshowMedia` beside
`onSlideshowClick`, and pass the callback to the top bar only when it is true, so the item is not
drawn otherwise. The screens compute it from the whole membership — `GalleryViewModel.mediaInFolder`,
or the album's resolved members, or the Favourites list — **before** the Search field and Filter
media. A view that search or the filter has emptied still shows the item, and so does a container
whose media the options exclude, such as only videos with Include videos off: there the dialog shows
its zero state, as §5 says.

**Why it departs from §1.** §1's reason for always showing the item is the one in *Alternatives*: the
count depends on the options, and hiding the item would hide the way to change them. That reason has
no force for a container that holds nothing — no option makes an empty folder play — so the item
there would open a dialog whose only possible state is "Nothing to play". Hiding an Overflow item that
cannot act has precedent here: Album Detail View's Change album thumbnail item is not drawn for an
Album showing no item.

**Cost.** Two parameters instead of one on each `*Content` composable, which broke their
instrumented tests as §1's *Negative* bullet expected.

### 2. `SlideshowUiState.itemSerial`

The state carries a counter that changes every time an item is entered — including the **same** item
again, which a one-item loop or a reshuffled pass can produce. The screen keys its per-item work on
it (which slide is incoming, when to report the item shown), never on the URI. *Why:* keyed on the
URI, a one-video loop would never restart its video, and a report of "shown" for one pass could be
taken for the next. A one-item loop of a still is the exception the code names: nothing new is
drawn, and the ViewModel restarts the interval itself.

### 3. The controls hide three seconds after a tap — while playing

§8 says a tap shows the controls for three seconds. As built that holds **while playing**; **while
paused the controls stay shown** until the slideshow resumes, and then hide three seconds after the
tap that resumed it. They never show during the countdown. *Why:* a paused still image looks exactly
like a playing one, and the Resume control is the only sign on screen that the slideshow is paused
and how to continue — which matters most after §6's background pause, when the user returns to a
screen they did not pause themselves.

### 4. The recorded video position is keyed by the video's URI

`recordVideoPosition(uri, positionMs)` records only when `uri` is the current item's, and
`consumeVideoPosition(uri)` returns a position only to the video it was read from; the record is also
cleared whenever an item is entered. *Why:* §6 says "ADR-0002's pattern", and ADR-0002's consume is
keyed by URI for the same reason — a position read at `ON_STOP` must never seek a different video,
which an item boundary crossed during a rotation would otherwise allow. They stay plain functions,
never state.

### 5. Leaving the app during the countdown

As §6 says, and precisely: `pauseForBackground` during the countdown **cancels** it and leaves the
slideshow `PAUSED` on its first item. On return the countdown is not replayed; the first item is drawn
paused, with the controls shown (§3), and a tap starts it. *Why not restart the countdown:* §6 says
the slideshow stays paused on return, and a countdown is a way of starting, not a pause.

### 6. The dialog's form

The Slideshow dialog is an `AlertDialog`, like the app's other dialogs, with Play as its confirm
button and Cancel as its dismiss. In the options area **Transition, Order and Orientation are radio
groups**, every choice visible at once because none has more than three; **Include videos, Include
GIFs and Loop are switches**; and the Interval stepper's − and + are disabled at 1 and 60 seconds.
The area scrolls inside the dialog. The live count ignores Order, which changes no count, so it never
pays for a shuffle. §5 left the form open; the Designer named the parts in the UI vocabulary.

### 7. What the two test fixes were

The two device failures of the verification phase were test defects, not product ones. The tests
asserted a 48 dp minimum on the options toggle and the Interval stepper's buttons through their
**layout** size, which Material 3's `IconButton` and `IconToggleButton` draw at 40 dp while extending
their **touch** target to 48 dp. They now assert the touch size with `assertTouchWidthIsEqualTo` and
`assertTouchHeightIsEqualTo(48.dp)` — the size the 48 dp accessibility minimum is about. The choice
and switch rows are laid out at least 48 dp tall.

### 8. Two details of §8 and §9 the build had to settle

- **A video reports itself shown at its first rendered frame**, not when its fade-in ends. The
  ViewModel ignores a video's end until the video has been reported shown, and a video shorter than
  the fade would otherwise end unreported and stall the slideshow. Videos have no interval, so §8's
  "the interval timer starts when the incoming item is fully visible" is unaffected; it holds for
  stills as written. *Amended 2026-11-24 — Amendment 2 §1:* still true of the ViewModel, but no
  longer the whole story. Reporting at the first frame did not close the stall: the report is made
  after a recomposition, which a very short clip can outrun, and a video with no first frame was never
  reported at all. The screen now reports a video shown and ended together when it ends having drawn
  a frame, and failed when it ends without one.
- **The video's `TextureView` is drawn at a near-zero alpha, not zero, before its first frame** — in
  the code's own words, so that the compositor keeps consuming its frames; the fade then runs from
  there.

### Consequences

- **Positive:** an empty folder or album offers no action that cannot act; a paused slideshow says
  that it is paused.
- **Negative:** the item's presence now depends on the membership, so the instrumented tests drive one
  more parameter; and three of §9's device checks, all of them about video, remain unobserved until a
  playlist with a video is run on hardware (ARCHITECTURE.md §11).

## Amendment 2 — 2026-11-24 — A video that never shows a first frame is skipped

> **Written by the Planner after a remediation, from the code** (`ui/SlideshowScreen.kt`,
> `ui/SlideshowViewModel.kt`, `SlideshowViewModelTest` and `SlideshowContentTest`). It changes no
> choice the maintainer made. The Decision's §6 and §8 and Amendment 1 §8 are marked in place to
> point here. The Planner ran no build and no device for this amendment.

### Why

As built under Amendment 1 §8, a video was revealed at its first rendered frame, reported shown from
there, and its end was ignored until it had been reported shown. Two videos fell through that rule,
and neither raised a player error, so nothing skipped them:

- **A video that never renders a first frame** — an audio-only file, say, whose one track the
  slideshow disables (§8). It was never revealed and never reported shown, so its end, if it came, was
  ignored. The slideshow froze on the outgoing item **with the screen kept on** (§10), until the user
  tapped or left. Whether such a file reaches its end at once or never ends was not observed; the
  remediation covers both (§1 and §2 below).
- **A video short enough to end before it was reported.** The report comes from a `LaunchedEffect`
  that runs after the recomposition the first frame causes. A clip that ended inside that window had
  its end ignored, and no second end followed: the same freeze.

### 1. The screen reports a video's end by whether it drew a frame

On `Player.STATE_ENDED`, when the newest slide on the stage is a video:

- **It rendered a first frame:** its last frame is captured as before (§9), then `onItemShown()` and
  `onVideoEnded()` are called together, in that order. `onItemShown` is idempotent per item, so the
  `LaunchedEffect`'s report, if it comes later, changes nothing.
- **It rendered none:** `onItemFailed()`, and the video is skipped as a player error already skipped
  it (`onPlayerError`, unchanged).

*Why the ViewModel's rule is kept rather than relaxed:* the screen now satisfies it instead. The only
way a video's end advances the slideshow is still through an item the ViewModel counts as shown —
which is what §6's guard against an endlessly looping pass counts. An end with nothing drawn goes
through the failure path, which that guard already treats correctly.

### 2. The ViewModel bounds the wait for a first frame

`SlideshowViewModel` holds a first-frame watchdog, `loadJob`, of `VIDEO_FIRST_FRAME_TIMEOUT_MILLIS`
= 10 seconds. While the phase is `PLAYING`, a video not reported shown within 10 seconds counts as
failed and is skipped.

- **It runs only for a video, only while playing, and only until the video is reported shown.** It
  never runs for an image or a GIF.
- **Started** when a video is entered, when the countdown ends on a video, and on resuming a video
  not yet shown. **Stopped** when the item is reported shown, when another item is entered (and
  started again for it if it is a video), on any pause — a tap or the background pause of §6 — and
  when the slideshow is cancelled.
- **A resume restarts a full 10 seconds.** Paused time does not count, and time played before the
  pause is not credited. A rotation is not a pause (§6), so the bound keeps running through the
  player's rebuild.
- **A pass in which nothing was shown still ends the slideshow**, looping or not (§6). A loop of
  videos that never render ends after one pass rather than retrying each every 10 seconds.

*Why both §1 and §2:* §1 handles an end the screen can see; §2 handles a video that never ends and
never errors, which no listener reports. *Why 10 seconds:* the files are local and start in well
under a second, so the bound catches only a video that will never render, with a wide margin for a
slow device. It is a safety bound, not one of the maintainer's options, and may change without a new
record. *Why in the ViewModel:* it owns the slideshow's other timers, it survives the rotation that
rebuilds the player, and it is testable on a plain JVM with a test dispatcher.

### 3. Still unbounded: a video that stalls after its first frame

A video that renders a first frame and then stalls — never reaching its end and never raising an
error — is **not** bounded. Once it is reported shown the watchdog has stopped, and a video has no
interval (§5), so the slideshow stays on it, with the screen kept on, until the user taps or leaves.
A known gap, recorded and not fixed (ARCHITECTURE.md §11).

### 4. What is verified, and what is not

- **Unit-tested, at ViewModel level** (`SlideshowViewModelTest`): a video reported shown and ended in
  the same turn advances once; `onItemFailed` skips; and the watchdog — it fires at 10 seconds and not
  before, not once the video is shown, again for each video entered, never for an image, not while
  paused and a full 10 seconds after a resume, not after the background pause, only from the end of
  the countdown, ends a looping pass that showed nothing, and leaves no timer to advance twice.
- **The screen's two `STATE_ENDED` branches are not driven by any test.** The player is created inside
  the screen and cannot be faked, so which branch a real video takes is checked by reading the code.
  One instrumented test, `SlideshowContentTest.videoThatCannotBeOpenedIsReportedAsFailed`, drives one
  error path: a video URI that cannot be opened is reported failed, through `onPlayerError`.
- **Not observed on a device:** a real audio-only file, a real very short clip, or the watchdog firing
  on hardware. They join §9's video checks that a playlist holding a video has yet to run.

### Consequences

- **Positive:** a video that cannot show a picture costs at most 10 seconds of playing instead of the
  whole slideshow, and a very short clip advances.
- **Negative:** such a video holds the screen for up to 10 seconds, and for a full 10 again after each
  resume; a video whose first frame takes longer than 10 seconds of playing is skipped though it would
  have played; a video that stalls after its first frame is still unbounded (§3); and the screen's half
  of the remediation is verified by reading, not by a test or a device.
