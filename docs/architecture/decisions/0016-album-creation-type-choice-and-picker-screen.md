# ADR-0016: Album creation is a typed choice followed by a dedicated screen

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

Stage 1 shipped album creation as a single dialog: tap **New album**, type a name, press Create.
The album then exists and is empty, and the only way to put anything in it is to leave Albums
View, find the media, multi-select it, and use **Add to album**. Creating an album and filling it
are two unconnected journeys, and the first one ends on an empty screen.

Two things make this worth changing now rather than at Stage 2.

**The name is the wrong first question.** A dialog that opens on an empty required text field
blocks the user on the decision they care least about. The album's contents are the point; the
name is a label they will want to adjust *after* seeing what is in it.

**Automatic albums are invisible.** [ADR-0012](0012-one-album-type-rule-additions-exclusions.md)
settled that there is one album type with a rule, additions, and exclusions, and that a manual
album is simply one with no rule. That is the right *storage* model, and it is not what the user
is choosing between. Stages 2–4 add region, time, content, and people albums, all of which are
"describe it and the app fills it" rather than "pick the photos". Today nothing in the UI hints
that the second kind is coming, so when it arrives it has to be discovered.

The constraint is [ADR-0013](0013-albums-as-third-display-mode.md): the navigation chain is a
plain `if / else if` in `MainActivity` with no back stack, kept deliberately until Stage 3. Any
answer here either fits in that chain or reopens a decision that was taken five ADRs ago.

## Decision

**Creating an album is two steps: choose the kind, then fill it on a screen of its own.**

- The **Add album button** and the **New album** overflow action both open the **Album type
  dialog**, which offers *Manual album* and *Automatic album*, each with one line explaining what
  it does. **Automatic is rendered disabled and labelled "Not available yet"** until Stage 2
  delivers it.
- Choosing *Manual album* opens the **New album screen** — one new branch in the existing chain,
  not a navigation library. It shows every item the Timeline scope would show, selectable by tap,
  with the album's name in an editable field in the top bar.

> **Amended 2026-10-08 — see Amendment 1 below.** The screen is no longer one flat grid of every
> item. It is a two-level browser: folders first, then the media inside the folder the user opens.
> The editable name field in the top bar is unchanged.

- **The name is pre-filled, not asked for.** `AlbumNaming.suggestName` returns the first
  `My album N` that no existing album uses, compared trimmed and case-insensitively — the same
  comparison the duplicate-name validator uses. The user edits it in place if they care.
- **The screen is draft-and-commit.** Nothing is written until Create. Backing out writes nothing
  and leaves no empty album behind. `AlbumsViewModel.createAlbum(name, members)` takes the members
  with the name, so the screen makes one call rather than creating an album and then finding it
  again to fill it. It is **not** one transaction — the album row and each membership are separate
  Room writes, so a process death mid-loop can still leave a partly filled album. That is the same
  exposure `addMembers` already has and is survivable; a transactional store method is the fix if
  it ever bites.
- **The picker shows every non-excluded item, unfiltered.** It reads `allMedia` and drops excluded
  folders, rather than reusing `filteredAllMedia`, which carries Timeline View's live search query
  and media-type filter. Those are browse state; inheriting them would silently hide items the
  user came here to pick, with no field or chip on this screen to explain why. Only the Timeline
  sort order and column count are borrowed, and only for reading — the picker never writes a
  display preference.

> **Amended 2026-10-08 — see Amendment 1 below.** The Timeline scope is no longer involved at all.
> The media level borrows the **Folder Detail** preference stack for the folder it has open. The
> reasoning in this bullet is what survived the reshape and governs it: a browse-state filter must
> not silently hide what the user came here to pick, so the folder's media-type filter is
> deliberately not applied either. The picker still writes no display preference.

- **The existing `CreateAlbumDialog` stays** for the *Add to album → New album* path, where the
  media is already chosen and a picker would be a step backwards.

## Alternatives Considered

**Keep the single name dialog and add a "pick photos now" step after it.** Smaller, and keeps one
entry point. Rejected because the album would already exist by the time the user reached the
picker, so cancelling leaves an empty album — the exact outcome the current flow produces and the
one worth removing.

**Put the picker in a full-screen dialog rather than a navigation branch.** Avoids touching the
chain. Rejected because a full-screen dialog over `FolderListScreen` gets the back button, the
system bars, and the saveable-state scoping subtly wrong, and because the chain was explicitly
kept — not merely tolerated — by ADR-0013. One more branch is the cheaper, more honest change.

**Offer the album kinds as a submenu on the overflow action instead of a dialog.** One fewer tap.
Rejected because a disabled menu item with an explanation reads badly, and the explanations are
the point: without them "Automatic album" means nothing to a first-time user.

**Hide Automatic album until Stage 2 ships it.** Nothing is more honest than not showing an
unavailable feature. Rejected because the type choice is then a dialog with one option, which is
not a choice, and the flow would have to be redesigned again at Stage 2 — at which point every
user's muscle memory is wrong. Showing it disabled costs one dialog row and makes the Stage 2
landing a change in state rather than a change in shape.

**Reuse `GalleryViewModel`'s media selection mode for the picker.** It already exists and already
drives tiles and checkmarks. Rejected because that mode is bound to the media *action* bar — delete,
share, copy, move — which is wrong here, and because entering it would mean the New album screen
mutating central state it does not own. The screen holds its own selection in `rememberSaveable`
instead, which is smaller and survives rotation through the state holder that already wraps the
chain.

## Consequences

### Positive

- An album is created with contents, in one journey, and the name is a detail rather than a gate.
- Cancelling creates nothing. The previous flow could leave empty albums behind.
- Automatic albums have a visible, explained home before they exist, so Stage 2 fills a slot
  rather than inventing one.
- `createAlbum(name, members)` writes the album and its members together, which `addMembers` —
  which reads resolved state and so cannot be called before the album is observable — could not do.
### Negative

- The chain gains another branch; the current list is in
  [ARCHITECTURE.md](../ARCHITECTURE.md) §4, which owns it. ADR-0013 weighed a shorter chain than
  this one, and the case for a real stack grows with every branch; Stage 3 still owns that
  decision.
- The New album screen's saveable state must be dropped when it closes, or the holder that keeps
  every other screen's scroll position would reopen it on the previous name and selection. This is
  the same class of trap ADR-0002 records for the viewer, from the opposite direction.
- `FolderListContent` lost `onCreateAlbum` and gained `onStartManualAlbum`, breaking the
  instrumented tests that pass every parameter by name. That is the intended behaviour of that
  convention, and the tests were updated.
- Two creation paths now exist — the type dialog from Albums View and the name dialog from *Add to
  album*. They are deliberately different because their inputs are different, but it is one more
  thing to keep consistent.
- A disabled control ships in the UI. If Stage 2 slips, users meet a dead end repeatedly; the
  "Not available yet" label is what makes that acceptable rather than broken.

## References

- [ADR-0012](0012-one-album-type-rule-additions-exclusions.md) — one album type: rule, additions,
  exclusions. The storage model this UI deliberately does not mirror.
- [ADR-0013](0013-albums-as-third-display-mode.md) — the navigation chain stays until Stage 3.
- [ADR-0015](0015-album-cover-persisted-random-member.md) — the cover is assigned when an album
  first gains a member, which now happens during creation rather than after it.
- [docs/design/albums.md](../../design/albums.md) §4, §7.
- `app/src/main/java/com/davide/seddio/easygallery/ui/NewAlbumScreen.kt`,
  `ui/AlbumDialogs.kt`, `data/AlbumNaming.kt`.

## Amendment 1 — 2026-10-08 — The picker is a two-level folder browser, not a flat grid

This amendment corrects **what the New album screen shows and how its Create action is
presented**. Everything else in the record continues to govern verbatim: the typed choice through
the **Album type dialog**, Automatic shown disabled until Stage 2, the pre-filled editable name,
draft-and-commit with nothing written until Create, the single `createAlbum(name, members)` call,
the screen holding its own selection rather than entering `GalleryViewModel`'s media selection
mode, `CreateAlbumDialog` surviving for the *Add to album* path, and every alternative rejected
above.

It is recorded as an in-place amendment rather than a superseding ADR, following ADR-0005
Amendment 1, ADR-0007 Amendments 1 and 2, ADR-0010 Amendment 1, ADR-0011 Amendment 1, and
ADR-0013 Amendment 1. The decision with lasting structural impact — that manual creation gets a
dedicated draft-and-commit screen of its own, reached through a type choice, with the name
pre-filled rather than demanded — has not stopped applying. Only the shape of the picking surface
inside that screen has moved. The superseded sentences in the Decision are annotated in place and
left intact.

### What changed

1. **Two levels instead of one.** The screen opens on a **folder level** that reuses Folders
   View's own folder list, order, and layout — grid or list, at its column count. Opening a folder
   moves to the **media level**, which shows that folder's media and is the only level where media
   is selected.

> **Corrected 2026-10-08 — see Corrections below.** The folder level no longer reuses Folders
> View's folder *flow*. It has a dedicated one that applies no media-type filter and no search
> query. It still borrows that view's sort order, layout, pinning, exclusion, and thumbnail
> overrides, so what it shows is otherwise the same list.

2. **Selection accumulates across folders.** Going back to the folder level and opening another
   folder adds to the same set; the running count stays in the line below the top bar.
3. **Create moved to a floating action button.** It sits bottom-right carrying a tick icon,
   mirroring the **Add album button** in Albums View, rather than sitting as a text action in the
   top-right of the top bar.
4. **The navigation icon is level-dependent.** At the media level it is a back arrow returning to
   the folder level; at the folder level it stays the Close action that abandons the whole flow.
   The system back gesture follows the same rule. The editable name field stays in the top bar at
   both levels, so the album can be renamed at any point.
5. **The media level borrows the Folder Detail preference stack, not the Timeline one.** The
   opened folder's sort, grouping, layout, and column count are resolved exactly as Folder Detail
   View would resolve them, so a folder looks in the picker the way the user has already arranged
   it. Its **media-type filter is deliberately not applied**, and no search query is applied, for
   the reason the original bullet gives.
6. **The three-layer resolution became shared, pure logic.** It was a private flow inside
   `GalleryViewModel` that could only resolve the *currently selected* folder. It is now a plain
   function over a layer bundle in `data/FolderViewOverrides.kt`, which resolves an arbitrary
   folder path; `GalleryViewModel` exposes the bundle and goes through the same function for its
   own selected folder. See [ARCHITECTURE.md](../ARCHITECTURE.md) §5.
7. **The picker does not select the folder.** Opening a folder here does **not** call
   `GalleryViewModel.selectFolder(...)`, because that branch of `MainActivity`'s chain outranks
   this one and the app would navigate to Folder Detail View. The open folder is screen-local
   `rememberSaveable` state, as the selection already was.

### Why the two-level shape

The flat grid asked the user to find their photos in one undifferentiated list of everything on
the device. That is the hardest possible way to pick: the album a person is making is usually
"that trip" or "those screenshots", and the thing that already groups their library that way is
the folder structure they have lived with for years. Folders are not merely a filter over the
flat list — they are how the user already knows where things are, which is the whole reason
Folders View is the view the app opens on. Making the picker start where the app starts means the
user navigates by memory rather than by scrolling.

Borrowing the Folder Detail preferences follows from the same argument. A folder the user has set
to date-taken-descending and grouped by day is a folder they can find things in; showing it to
them in a different order at the one moment they are hunting for specific items would undo the
benefit of showing folders at all.

The Create affordance moved because a two-level browser needs its navigation icon for *back*, and
a top-bar layout holding an editable text field, a back arrow, and a text action is crowded at the
best of times. A floating button is also the affordance Albums View already uses for "make an
album", so the gesture that starts the flow and the gesture that finishes it now match.

### Consequences of this amendment

**Positive**

- Picking is navigation rather than search. The user reaches a folder in one tap and sees only its
  contents, instead of scrolling the whole device.
- The picker inherits whatever arrangement each folder already has, so it needs no sort, group, or
  layout controls of its own — and still writes no preference, so looking at a folder here cannot
  change how it looks anywhere else.
- Extracting the layering made it testable on a plain JVM for an arbitrary folder path, where
  before it was reachable only through `GalleryViewModel`'s selected folder.
- Create is in the thumb's reach at the bottom of the screen, on a surface whose main gesture is
  scrolling.

**Negative**

- Picking media that genuinely does span folders now costs a trip back out per folder. The
  accumulating selection and the running count are what make that survivable; a flat grid would
  still be the better shape for that one case, and it is the case this change judged rarer.
- **The folder level inherits Folders View's live search query and folder-level media-type
  filter**, because it reuses that view's folder list rather than building its own. A folder
  hidden by a search the user left active in Folders View is not reachable in the picker. This is
  a weaker form of exactly the exposure the original bullet refused for media, kept because
  rebuilding an unfiltered folder list would duplicate pinning, exclusion, and thumbnail-override
  handling. If it bites, the fix is an unfiltered folder flow, not a filter control on this
  screen.

  > **No longer accepted, 2026-10-08 — see Corrections below.** The media-type half was fixed the
  > same day, and the reason given above for accepting it was wrong. The search half remains and
  > is now accepted on a narrower, different argument.

- Two levels mean two scroll positions. The folder level's survives for the life of the screen;
  the media level's is reset per folder, so returning to a folder returns to its top.
- There is now a third place that must agree about what a folder looks like — Folder Detail View,
  the per-folder override dialogs, and this picker. The shared resolver is what keeps them
  agreeing; resolving the layers by hand anywhere else would reintroduce the drift.
- The disabled Create button is a floating action button, which Material does not give a
  disabled visual state. It is marked disabled for accessibility and ignores taps, but it does not
  look different, so a blank or duplicate name reads as an unresponsive button until the hint line
  is noticed.

  > **Discharged 2026-10-08 — see Corrections below.** The disabled state is now rendered, and the
  > control no longer advertises a click action while announcing itself disabled.


### Corrections — 2026-10-08

Two of the negatives above stopped being true on the day they were written. They are corrected
here rather than deleted, because a negative that was discharged and a stated reason that did not
hold are both worth seeing.

**1. The folder level no longer inherits Folders View's media-type filter, and the reason given
for accepting that it did was wrong.**

The reason recorded above — that "rebuilding an unfiltered folder list would duplicate pinning,
exclusion, and thumbnail-override handling" — described a rewrite that was never necessary. The
filtering is already a parameter of `GalleryTransformations.filterAndSortFolders`, so an
unfiltered list is the same call with a different argument, and every behaviour the reason named
as expensive to duplicate comes along for free. The cost was misjudged, not merely outweighed.

The two halves of the inheritance also turned out not to be the same problem, and collapsing them
into one bullet is what let the worse half pass:

- **The media-type filter is persisted**, in `display_preferences` under the `FOLDERS` scope. A
  user who once set Folders View to show images only would never again see a video-only folder in
  the picker, on any later launch, with nothing on that screen to explain it or undo it. That is
  precisely the outcome this record's own governing bullet refuses for media — "a browse-state
  filter must not silently hide what the user came here to pick" — applied one level up. It is now
  fixed, and the governing bullet applies at both levels without exception.
- **The search query is not persisted** and is cleared on next launch. The exposure is bounded by
  one session, the user typed the query themselves moments earlier, and leaving the picker and
  clearing it is a two-tap undo. It is accepted on that basis — a self-healing session artefact —
  and explicitly not on the discarded cost argument above.

`GalleryViewModel` now carries `newAlbumFolders`, a dedicated `StateFlow<GalleryUiState>` beside
`filteredFolders`. It calls the same `filterAndSortFolders` with an empty query and every
`MediaType`, and passes Folders View's sort order, pinning, exclusion, `showExcludedTemporarily`
and thumbnail overrides through unchanged, so the two lists differ in exactly those two
parameters and nothing else. A unit test in `GalleryViewModelTest` holds the pair against each
other: with a `FOLDERS` media-type filter active, a video-only folder is absent from
`filteredFolders` and present in `newAlbumFolders`.

**2. The Create floating button now renders a genuinely disabled state.** When the name is blank
or duplicate it draws as a disabled circular surface rather than as an identical enabled button,
and it is no longer a `FloatingActionButton` carrying an `onClick` while its semantics say
`disabled()` — a control that both advertises an action and announces itself unavailable. The
`new_album_create_button` tag is on both renderings, so the instrumented tests reach it either
way; note that the node behind that tag is not always a button.

### Code and tests this governs

- `ui/NewAlbumScreen.kt` — both levels, the accumulating selection, the level-dependent
  navigation icon and system back, and the floating Create action in both its enabled and
  disabled renderings.
- `data/FolderViewOverrides.kt` — the layer bundle and the pure resolver, now shared.
- `ui/GalleryViewModel.kt` — exposes the layer bundle, resolves its own selected folder through
  the same function, and owns `newAlbumFolders`, the picker's unfiltered folder stream.
- The plain-JVM test for the extracted resolver under `app/src/test/.../data/`, the
  `filteredFolders`/`newAlbumFolders` divergence test in `GalleryViewModelTest`, and the
  instrumented tests for the screen under `app/src/androidTest/.../ui/` — which cover a blank and
  a duplicate name creating nothing, and the Create button clearing the last row at both levels in
  both grid and list layouts.

### References added by this amendment

- [ADR-0006](0006-per-folder-display-preference-overrides.md) and
  [ADR-0008](0008-camera-folder-default-sort-order.md) — the two layers the media level now
  resolves. They were not referenced by the original record because the flat grid did not touch
  them.
- [ARCHITECTURE.md](../ARCHITECTURE.md) §5 — where the shared resolution is described.
