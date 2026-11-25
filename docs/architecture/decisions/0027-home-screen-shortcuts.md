# ADR-0027: Home-screen shortcuts — pinned, static icons, and one fresh entry resolved once

- Status: Active
- Date: 2026-11-25
- Adopted: 2026-11-25
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer, who approved it on
  2026-11-25 and decided the icon (§6) and the optional album-shortcut maintenance (§7)

> ~~**Nothing below is built yet.** The ADR is adopted ahead of the work it governs, so its link in
> [ARCHITECTURE.md](../ARCHITECTURE.md) is added by the change that lands the work, not by this one
> (*Timing*, architecture documentation instructions).~~
> **Built and verified 2026-11-25.** The code compiles, the unit suite passes, and
> `connectedDebugAndroidTest` passed on a device, an SM-G990B. What landed is described in
> [ARCHITECTURE.md](../ARCHITECTURE.md) §3, §4, §6 and §11, which now link here. Where the build
> reads this record differently, or settled what it left open, **Amendment 1** at the end says so;
> §2 and §6 are marked in place to point there. **Several device-only checks are not yet observed**
> — the note under *Verify* says which.

## Context

The maintainer asked for a way to put a folder or an album on the device's home screen, so that one
tap opens it. Nothing in the app does this today, and the obvious ways to build it collide with
decisions already taken:

- **The navigation chain has no back stack and no deep links.** `MainActivity` selects its screen by
  a plain `if / else if` over ViewModel flags and a few `rememberSaveable` values
  ([ADR-0013](0013-albums-as-third-display-mode.md)). Folder Detail View is selected by
  `GalleryViewModel.selectedFolder`, Album Detail View by `AlbumsViewModel.selectedAlbumId`, and
  Favourites by `isFavouritesSelected`, a `rememberSaveable` in `MainActivity` — Favourites has no
  album id (ADR-0013 Amendment 1). A shortcut has to arrive at one of those three states.
- **`MainActivity` is recreated on rotation and on a language change.** It declares no
  `android:configChanges` ([ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) forbids it),
  and `LocaleHelper` applies a language by `recreate()`. The intent that started the activity is
  still `getIntent()` after each of those, and after process death the system restores the activity
  with that same intent. A shortcut read naively navigates again every time.
- **The targets are not available at `onCreate`.** Folders come from `MediaStore` after the media
  permission is granted and the query has run; albums come from the Room database
  ([ADR-0009](0009-room-as-first-owned-datastore.md)) through `AlbumsViewModel.albums`, whose first
  value is an empty list that cannot be told apart from "no albums".
- **`MainActivity` is exported**, because it is the launcher activity. Any app can start it with an
  explicit intent carrying any extras, so whatever a shortcut carries is untrusted input.
- **Favourites exists only on API 30+** ([ADR-0011](0011-favourites-backed-by-mediastore.md)); the
  module's `minSdk` is 28.
- **The platform API, checked on developer.android.com on 2026-11-25** (*Create shortcuts*, *Manage
  shortcuts*, `ShortcutManager` reference):
  - Pinned shortcuts exist from API 26, below this module's `minSdk`, so `ShortcutManagerCompat`
    always takes the `ShortcutManager` path here and never the legacy `INSTALL_SHORTCUT` broadcast.
    `ShortcutManagerCompat` is in `androidx.core`, already a dependency. Pinning needs **no
    permission, no manifest entry and no `<queries>` entry**.
  - `requestPinShortcut` asks the user through the launcher; it returns `false` if the launcher does
    not support pinning, and it throws `IllegalArgumentException` if a shortcut with the same id
    exists and is **disabled**. A shortcut's intent must carry an action.
  - `updateShortcuts` and `disableShortcuts(ids, message)` act on pinned shortcuts by id. Updates are
    rate-limited only while the app is in the background. The calls may take time and belong off the
    main thread.
  - Pinned shortcuts are **backed up and restored by the system**, but their icons are not; the
    guide asks for ids that are stable strings, not identifiers generated on one device. Clearing the
    app's data removes them.
  - The guide recommends `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` and `onNewIntent` for
    a single-activity app, and says apps must update pinned shortcuts on
    `ACTION_LOCALE_CHANGED`. This record does neither, and says why below.
  - A shortcut icon may not use a tint.

## Decision

### 1. Entry: an Add shortcut item in the detail views' Overflow menu

- **An Add shortcut item is in the Browse top bar's Overflow menu of Folder Detail View and Album
  Detail View, Favourites included.** No main view has it. It sits before the Slideshow item, which
  stays last ([ADR-0025](0025-slideshow.md) §1). Its label, icon, tag and vocabulary entry are the
  Designer's.
- **It is hidden** in Media selection mode and Album media selection mode (the Browse top bar is not
  shown), in the Folder thumbnail picker and the Album cover picker (the Thumbnail picker top bar
  replaces it), and in Album Detail View until the Album is known — `AlbumsViewModel.selectedAlbum`
  is not null — because the label and id come from it.
- **If the launcher does not support pinning**, the item is still shown, and tapping it shows a
  Toast saying so. *Why not hide it:* support can change while the app is open (the user changes the
  default launcher, or App Lock), and hiding would need a re-query on every resume for an item the
  user taps rarely.

### 2. A pinned shortcut through `ShortcutManagerCompat`

- **Pinned only.** No static (`res/xml/shortcuts.xml`) and no dynamic shortcuts: a folder or album is
  the user's choice, which is what a pinned shortcut is for, and neither of the others needs adding.
- **`ShortcutManagerCompat.requestPinShortcut`**, with no success callback: nothing in the app
  changes when the user accepts, so no receiver is declared. An `IllegalArgumentException` or
  `IllegalStateException` from the call is caught and shown ~~as the same Toast as an unsupported
  launcher~~, never a crash. *Amended 2026-11-25 — Amendment 1 §1:* as a Toast of its own.
- **No new dependency, permission, manifest entry, `<queries>` entry or `android:configChanges`.**

### 3. The intent: explicit, private, primitive, and a fresh entry

- **It names `MainActivity` explicitly** by component, so no other app's activity can answer it and
  no intent filter is added.
- **Its action is app-private** — a constant in the app's own package namespace, never a platform
  action such as `ACTION_VIEW` — so that it is distinguishable from a launcher start and from any
  other app's intent.
- **Its extras are primitives only:** the target kind (folder, Album or Favourites), the album id for
  an Album, and the folder path for a folder. No `Parcelable`, no URI, no media identity, no face
  data.
- **Its flags are `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`.** A shortcut tap is a fresh
  entry: the existing task, if any, is cleared and a new `MainActivity` starts with the shortcut's
  target. It is what a static shortcut does on every launcher, and it is the rejected alternative's
  opposite (*Alternatives*).
- **The navigation chain gains no branch** (ADR-0013 holds): the shortcut sets the same state that a
  tap on a folder, an Album or Favourites sets today, and the existing branches draw it. Back from the
  opened view goes where it goes today — to Folders View for a folder, to Albums View for an Album
  or Favourites.

### 4. The pending target: read once, held saveably, resolved once, then cleared

- **Read only on a genuine start.** `MainActivity` reads the shortcut extras only when
  `savedInstanceState == null` **and** the intent does not carry
  `Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`. The first excludes rotation, a language
  `recreate()`, and restoration after process death; the second excludes the user reopening the task
  from Recents after the activity has finished, when the system re-delivers the shortcut intent with
  no saved state.
- **Validated as it is read** (§8). An intent that fails validation is a plain launch: no
  navigation and no message.
- **Held in a `rememberSaveable` in `MainActivity`** as the pending target, so that it survives a
  rotation or process death that happens *before* it resolves — while the permission prompt is up,
  for instance.
- **Resolved once, when all three inputs are ready:** the media permission is granted, the media has
  loaded, and the album list has loaded. **`AlbumsViewModel` gains an albums-loaded flag** for the
  third, because its album list starts as an empty list that means "not yet" as well as "none".
  Favourites and folders need the media; an Album needs the album list. Waiting for all three keeps
  one rule for every kind.
- **Then cleared**, whether it opened its target or not. A later rotation, language `recreate()` or
  process death restores the cleared value and never navigates again.
- If the permission is never granted, the target stays pending on the permission-denied screen and
  resolves if it is granted in the same activity.

### 5. Identity, and what happens when the target is gone

- **Shortcut ids are stable strings:** a folder by its path, an Album by its id, Favourites by one
  fixed id. Pinning the same target again reuses its id.
- **A folder is matched by path among all loaded media**, not among the folders Folders View shows:
  **an excluded folder opens anyway.** The user pinned it; exclusion hides it from the list, it does
  not forbid opening it.
- **An Album is matched by id** in the loaded album list. Album ids are never reused: the table's key
  is `AUTOINCREMENT`, so a deleted Album's id cannot come back as another Album's.
- **Favourites** is matched only on API 30+.
- **A target that is not there** — a folder with no media left, a deleted Album, Favourites below
  API 30 — **lands on its owning main view** (Folders View for a folder, Albums View for an Album or
  Favourites) **with a Toast** saying the folder or album is no longer available.

### 6. Icons: static resources with the app's badge, never a thumbnail

- **Three static drawable resources:** `ic_shortcut_folder`, `ic_shortcut_album` and
  `ic_shortcut_favourites`. Each is a glyph — a folder, an album, a heart — **carrying a small Easy
  Gallery app-icon badge**, so the shortcut reads as this app's on any launcher. The folder glyph with
  its badge is the maintainer's chosen look, and the other two follow it. They are adaptive icons
  without a tint, which the platform forbids on shortcut icons. *Amended 2026-11-25 — Amendment 1
  §2:* the badge is a small Easy Gallery flower, drawn as a vector approximation of the launcher
  mark and copied into six files.
- **Never a thumbnail of the folder's or album's media.** *Why:* a photo — possibly of a person,
  possibly from a people album — would sit on the home screen in view of anyone holding the device,
  and would be copied into the system's shortcut storage, outside this app's control and outside
  ADR-0024's reach. It would also go stale as soon as the media changed, and the system does not
  back up shortcut icons, so a restored thumbnail shortcut loses its picture anyway. A resource icon
  restores with the app.

### 7. Labels, and keeping an Album's shortcut true

- **The label is the folder's name, the Album's name, or `favourites_title`**, resolved when the
  shortcut is pinned.
- **Renaming an Album updates its pinned shortcut's label.** **Deleting an Album disables its
  pinned shortcut** with the message `shortcut_album_deleted`, which the launcher shows when the
  disabled shortcut is tapped. Both are approved by the maintainer as an optional extra.
- **Through a publisher seam on `AlbumsViewModel`**: an interface injected through its constructor
  with a production default over `ShortcutManagerCompat`, the same shape as its `albumStore`, so
  that unit tests pass a fake and assert what was published. `renameAlbum` and `deleteAlbum` call it
  after the store write succeeds. The message is resolved through
  `LocaleHelper.wrap(application).getString(...)` at the point of use (`AGENTS.md`, *Strings and
  localisation*). Calls on ids with no pinned shortcut do nothing.
- **A disabled Album shortcut is never re-enabled**, and its id is never pinned again, because ids
  are not reused (§5). That is also what keeps `requestPinShortcut`'s disabled-id exception
  unreachable in practice; §2 catches it regardless.
- **Folders get no such maintenance.** They are renamed and deleted outside the app, which learns of
  it only through `MediaStore`; a missing folder is handled on tap (§5).

### 8. Security: the extras are untrusted

`MainActivity` is exported, so any installed app can send it the shortcut's action and extras.

- **Validated:** the kind must be one of the three known values; an album id must be greater than
  zero; a folder path must not be blank.
- **Only ever matched against data already loaded** — the media list and the album list. A path or id
  that matches nothing is a missing target (§5), nothing more.
- **Never used for file access**: the path is compared as a string, not opened, listed, resolved or
  passed to `MediaStore` as a selection.
- **Never returned to the caller**: no `setResult`, no echo of the extras in any message. The worst
  another app can do is open a folder or album the user already has, in this app, on screen.

## Alternatives Considered

**`FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` with `onNewIntent`.** The guide's
recommendation for a single-activity app, and it keeps the running activity. Rejected: the running
activity may be in any of the chain's states — the Full-screen viewer, a slideshow, a picker, a
selection, an open dialog, a New album draft — held across `GalleryViewModel`, `AlbumsViewModel`,
`SlideshowViewModel`, the viewer delegate and `MainActivity`'s own `rememberSaveable` values.
`onNewIntent` would have to reset every one of them before applying the target, and each later
feature would have to remember to join that reset. A fresh task reaches a known state by
construction, and §4's one rule — read on a genuine start, resolve once — covers every path into the
activity. The cost, an in-progress task lost on a shortcut tap, is in *Consequences*.

**A new navigation branch, or a Navigation library with deep links.** ADR-0013 keeps the chain and
says why; the shortcut needs only states the chain already draws.

**A trampoline activity.** An invisible activity that receives the shortcut and forwards it. It would
add a second exported entry point and a manifest change, and still deliver to `MainActivity` the same
problem §4 solves.

**Thumbnail icons.** Rejected by the maintainer for privacy (§6).

**A bare glyph with no badge, or the app's launcher icon unchanged.** A bare glyph does not say which
app the shortcut opens; the launcher icon does not say which folder. The badge is the maintainer's
choice between them.

**Resolve as soon as the target's own data has loaded.** A folder or Favourites would open before
the album list loads, an Album only after it. Two timings for one feature, for a gain of a fraction
of a second.

**Update the Favourites label on `ACTION_LOCALE_CHANGED`.** The guide asks for it. Rejected: it needs
a manifest-declared receiver, and the app's per-app language (`LocaleHelper`) never fires the
broadcast, so it would fix only the rarer half of the problem.

**Store the pinned shortcuts in the app, to maintain them.** The system already holds them and
answers `updateShortcuts` and `disableShortcuts` by id; a copy would drift.

## Consequences

### Positive

- A folder, an Album or Favourites is one tap from the home screen, with no new dependency,
  permission, manifest entry or navigation branch.
- A shortcut tap always lands in a known state, and rotation, language changes, process death and
  Recents never repeat it.
- No media and no face data leave the app through a shortcut: it carries a kind, an id or a path, and
  a label.
- An Album's shortcut follows its renames and stops working, with an explanation, when it is
  deleted.

### Negative

- **A shortcut tap discards whatever the user was doing in the app** — an open viewer, a slideshow,
  a selection, a dialog, an unsaved New album draft — because it clears the task.
- **The Favourites label is fixed in the language it was pinned in**; a folder's or Album's label is
  the user's own name and does not translate anyway.
- **After a restore on a new device, folder shortcuts may point at nothing**: the system restores
  the shortcuts, but a folder's path may differ there. Album shortcuts survive, because the album
  database is backed up with its ids ([ADR-0014](0014-album-database-included-in-auto-backup.md)).
  Either way, a target that is gone lands on its main view with a Toast (§5).
- **A folder's shortcut keeps its label if the folder is renamed** outside the app; tapping it then
  lands on Folders View with the missing-target Toast.
- **An Album's name sits on the home screen and in the system's shortcut storage and backup.** It is
  a name the user typed, possibly a person's; no photo goes with it.
- **The icon's badge may double up** on a launcher that draws its own app badge on pinned shortcuts.
- `AlbumsViewModel` gains a constructor parameter and an albums-loaded flag; `MainActivity` gains a
  pending target. `FolderDetailContent` and `AlbumDetailContent` gain a callback, which breaks their
  instrumented tests by design.
- New strings in every shipped locale: the item's label, the unsupported-launcher Toast, the
  missing-target Toast, and `shortcut_album_deleted`.

## Verify

**Unit (plain JVM or Robolectric):**

1. Parsing and validating the extras: each kind round-trips; an unknown kind, an album id of zero or
   less, a blank path, and a missing extra each yield no target.
2. The read rule: a target is read with no saved state and no `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`,
   and not with either.
3. Resolution waits for all three inputs, resolves once, and is cleared after; an excluded folder
   resolves; a missing folder, a missing Album id, and Favourites below API 30 each resolve to their
   owning main view with the missing-target message.
4. `AlbumsViewModel` with a fake publisher: a rename publishes the new label for that id; a delete
   disables that id; a failed store write publishes nothing; the albums-loaded flag is false until
   the first load and true after it, including when there are no albums.

**Instrumented (compile always; run when a device is attached):**

5. The Add shortcut item is shown in Folder Detail View, Album Detail View and Favourites, and not in
   either selection mode, either picker, or Album Detail View before the Album is known.

**Device only** — no test can observe a launcher:

6. Pinning each kind shows the launcher's confirmation and puts the icon and label on the home screen.
7. Tapping each from a cold start, and with the app open on another screen, opens its target; Back
   returns to its owning main view.
8. After landing, a rotation, a language change in Settings, and a process kill followed by a return
   do not navigate again; neither does reopening the task from Recents after backing out.
9. Renaming an Album changes its shortcut's label; deleting it disables the shortcut, and tapping it
   shows `shortcut_album_deleted`.
10. The three icons render with their badge, and do not show a second badge drawn by the launcher. If
    a launcher does, that is recorded as an amendment with the observation; it does not reopen §6's
    choice of a static icon.

> **What was run, 2026-11-25** (the verification phase's report, not re-run by the Planner). Items
> 1, 3 and 4 are `HomeShortcutsTest` and `AlbumsViewModelTest`, and item 5 is
> `FolderDetailContentTest` and `AlbumDetailContentTest`, all passing. Item 2 and the
> resolve-once-then-clear half of item 3 live in `MainActivity` and have **no unit test**: on the
> device, `HomeShortcutLaunchDeviceTest` covers a folder target opening, an unknown Album id landing
> on Albums View, and a `recreate()` after Back not navigating again.
>
> **Observed on the SM-G990B**, with the shortcut's intent sent by `adb shell am start` — action,
> extras and `NEW_TASK | CLEAR_TASK` — rather than by a launcher: a cold start opened its target; a
> warm start, the app open elsewhere, opened a folder; malformed extras did nothing and missing
> targets landed on their main view with the Toast; neither a rotation nor a start carrying
> `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` navigated again. On a fresh install the face data disclosure
> showed first, and after its OK the target opened. The Add shortcut item opened the launcher's own
> confirmation, whose preview showed the icon with **one** badge; the request was cancelled there.
>
> **Not yet observed:** tapping a pinned icon from the launcher, so item 6's icon and label on the
> home screen, item 7 as written, and item 10 on the home screen; an Album target end to end with a
> real Album; item 9, a rename or delete reaching a pinned shortcut; and of item 8, a language
> change in Settings, a process kill followed by a return, and reopening the task from Recents.

## References

- [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) — no `configChanges`; recreation is the
  rule §4 is written for
- [ADR-0009](0009-room-as-first-owned-datastore.md),
  [ADR-0014](0014-album-database-included-in-auto-backup.md) — album ids, and their survival in a
  restore
- [ADR-0011](0011-favourites-backed-by-mediastore.md) — Favourites only on API 30+
- [ADR-0013](0013-albums-as-third-display-mode.md) — the navigation chain stays; Favourites has no
  album id
- [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) — face data never leaves the app, the
  reason §6 forbids thumbnails
- [ADR-0025](0025-slideshow.md) §1 — the Slideshow item stays last
- `AGENTS.md` *State ownership*, *Strings and localisation*, *What to ask before doing*
- developer.android.com, read 2026-11-25: *Create shortcuts* (pinned shortcuts, backup and restore),
  *Manage shortcuts* (intent flags, locale changes, disabling, rate limiting), `ShortcutManager`
  reference (`requestPinShortcut`, `updateShortcuts`, `disableShortcuts`)

## Amendment 1 — 2026-11-25 — What the build settled

> **Written by the Planner after the build, from the code** (`ui/components/HomeShortcuts.kt`,
> `MainActivity.kt`, `ui/AlbumsViewModel.kt`, the two detail screens, `SearchTopBar.kt` and the
> `ic_shortcut_*` drawables). §1 departs from §2's text, and §2 and §6 are marked in place to point
> here. The rest states precisely what the Decision said loosely. No choice the maintainer made is
> changed. The Planner ran no build and no device for this amendment.

### 1. A failed pin has a message of its own

`pinHomeShortcut` returns `Requested`, `NotSupported` or `Failed`. An unsupported launcher, or
`requestPinShortcut` returning `false`, is `NotSupported` and shows `shortcut_not_supported`; an
`IllegalArgumentException` or `IllegalStateException` is `Failed` and shows `shortcut_add_failed`.
A request the launcher accepted shows nothing, since the launcher draws its own confirmation. The
second message is the Designer's (the UI vocabulary's *Add shortcut item*); nothing in §2's
reasoning depended on the two sharing one.

### 2. The icons' badge

Each of the three icons is an adaptive icon in `res/drawable/` over a shared `BrandBlue`
background: a white Material glyph — `Folder`, `PhotoAlbum`, `Favorite` — with, at its lower right
inside the safe zone, **a small Easy Gallery flower**: the five-petal mark on a dark-blue disc with a
white ring. Each also has a monochrome layer, alpha only, where the badge is a ring and petals. The
flower is **a vector approximation of the launcher mark**, not a reference to the launcher icon's
resource, and it is **copied into six files** — the three foregrounds and the three monochrome
layers — so a change to the app's mark has to be repeated in all six. The overview's §11 carries
that.

### 3. Ids carry their kind

The ids §5 calls "a folder by its path, an Album by its id" are `folder:<path>` and `album:<id>`;
Favourites' is `favourites`. The prefix keeps a folder whose path reads as a number from ever
sharing an Album's id.

### 4. Read once, held, resolved once — the mechanics

- The pending target is saved as primitives and, on restore, passed back through the same
  validation the intent went through, so a restored value is checked as the intent was.
- **`AlbumsViewModel.albumsLoaded`** is true once the album store has emitted, with or without
  albums. It is set in two halves, because the album list's `StateFlow` would conflate an empty first
  emission with its empty initial value: an empty emission is seen before the `stateIn`, a
  non-empty one from `albums` itself.
- Resolution reads the media and album lists from the flows' current values rather than from the
  states collected in composition, so the flags and the lists they vouch for cannot be a frame
  apart.
- A folder target is built from the items at its path with no exclusion and every media type, so
  a folder holding only videos opens whatever the filter. It then opens through
  `GalleryViewModel.selectFolder`, which loads a folder **by bucket name**; two folders sharing a
  name therefore show each other's media through a shortcut, as they already did through Folders
  View (overview §11).

### 5. The publisher's production implementation never throws

`AndroidAlbumShortcutPublisher` runs on `Dispatchers.IO` and logs a platform `RuntimeException`
rather than throwing it, because it runs after the Album's write has succeeded and must neither
undo nor crash a rename or delete. It is `AlbumsViewModel`'s last constructor parameter, defaulted.
A label update builds the same `ShortcutInfoCompat` as pinning — id, intent and icon — so an update
cannot change what the shortcut opens. Nothing on the shortcut path logs a path or a name; the pin
function logs the kind only.

### Consequences

No new consequence beyond the Decision's, except §2's six copies of the badge and §4's bucket-name
collision, which is the existing folder-aggregation debt reaching a new entry point rather than a
debt of its own.
