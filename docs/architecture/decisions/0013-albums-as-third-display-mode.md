# ADR-0013: Albums is a third display mode, and the navigation chain stays

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

Navigation in Easy Gallery is a plain `if / else if` chain in `MainActivity` with no Navigation
library and no back stack. It has five branches, selected by three ViewModel flags plus a local
permission flag, and is wrapped — except for the full-screen viewer — in a
`rememberSaveableStateHolder` that preserves each screen's scroll position.

The albums epic adds Albums View and Album Detail View. The reflexive reading is that this is two
more branches on a chain already strained, and therefore the moment to adopt a real navigation
library.

That reading is wrong about the first half. **Albums View is structurally a third `DisplayMode`,
not a new destination.** Timeline View is already `FolderListScreen` with `DisplayMode.CALENDAR`
rather than a screen of its own, switched by a top-bar toggle rather than by navigation, because it
shares the same search, sort, filter, grouping, and selection machinery. A grid of albums is the
same shape as a grid of folders and shares the same machinery again. Album Detail View is a sibling
of Folder Detail View and is a genuine new branch.

So Stage 1 adds **one** branch, not two.

> **Amended 2026-10-07 — see Amendment 1 below.** Stage 1 added **two** branches and the chain is
> now seven, because Favourites has no album id to select by and needed a branch of its own. The
> decision this reasoning supports is unaffected; only the count was wrong.

There is also a specific hazard in adopting Navigation Compose here. It performs its own saveable
state scoping per destination, which overlaps with the `rememberSaveableStateHolder` that ADR-0002
depends on — and ADR-0002's constraint is subtle: `FullImageScreen` must be rendered *outside* the
holder, because `rememberPagerState` is saveable-backed and a retained entry makes the viewer
reopen on the wrong photo. That regression has been introduced and fixed once already. A navigation
refactor is an efficient way to reintroduce it.

The place the chain genuinely fails is Stage 3. People albums need Albums → Album → person picker →
person detail, which is a real stack with real back behaviour, not a set of mutually exclusive
mode flags.

Separately, the enum is about to become actively misleading. `DisplayMode` has values `GALLERY` and
`CALENDAR` while the canonical view names are Folders View and Timeline View. Adding a third value
named for its view would leave one correct name and two wrong ones.

The switching control also has to change, and the obvious change does not work. Today
`SearchTopBar` renders an `IconButton` whose icon shows *the view you are not in* — a calendar in
Folders View, an image in Timeline View. With two views that is unambiguous: the icon is the
destination. With three views there are two destinations and one icon, so the icon can no longer
name where a tap leads.

What makes this easy to get wrong is that `FolderListScreen` **already** renders the current view's
name as the top-bar title, localised, switched on `DisplayMode`. So "make the icon show the current
state instead" produces two controls stating the same fact, one of them in an ambiguous pictogram,
and leaves nothing at all indicating what the button does.

## Decision

**Albums View is a third `DisplayMode` on `FolderListScreen`. Album Detail View is one new branch
in the existing chain. The `if / else if` chain is retained for Stage 1.**

- **The view switcher moves from the icon to the title.** The top-bar title gains a trailing
  chevron and anchors a `DropdownMenu` listing all three views, with the current one marked. The
  view-switching `IconButton` is removed from `SearchTopBar` entirely.

  > **Amended 2026-11-11 — see Amendment 2 below.** The title dropdown is replaced by a persistent
  > bottom bar of three tabs, and the main views' title becomes the app's name.
- **Rename `DisplayMode` to `FOLDERS` / `TIMELINE` / `ALBUMS`** in the same change that adds the
  third value. Adding a correctly-named value beside two misnamed ones is the worst available
  outcome, and the rename is mechanical.
- Albums View and Album Detail View each get their own `PreferenceScope`, so their sort, layout and
  column settings are independent of the other views, exactly as the existing three are.
- Pressing back from Timeline View or Albums View returns to Folders View. The absence of this
  behaviour for Timeline View is a known existing defect, and a third view makes it worse.

**Revisit at Stage 3.** This ADR is superseded, not amended, when the people-album flow needs a
real stack.

## Alternatives Considered

**Adopt Navigation Compose now.** The destination the app eventually wants, and doing it before
Albums avoids migrating album screens later. Rejected for Stage 1 on cost and risk: it is a full
rewrite of `MainActivity`'s structure to add one branch, and its saveable-state scoping collides
with ADR-0002's deliberate placement of the viewer outside the state holder — a regression this
repository has already paid for once. Deferring it to Stage 3 pays the cost when the benefit is
real.

**A hand-rolled back stack** — a `List<Screen>` of a sealed type, held in a ViewModel. No
dependency, fits the repository's flat and explicit style, and gives genuine back behaviour.
Rejected for Stage 1 as solving a problem Stage 1 does not have; a serious candidate at Stage 3,
against Navigation Compose.

**Albums View as its own screen rather than a `DisplayMode`.** Rejected because it would duplicate
the search, sort, filter and selection machinery that `FolderListScreen` already provides for two
views, in order to render a grid of the same shape.

**A three-state cycling icon button** — Folders → Timeline → Albums → Folders, with the icon
changed to depict the current view rather than the next one. Costs no layout space and is the
smallest possible change. Rejected on three counts: the icon would duplicate the title, which
already names the current view in the user's language; with the icon reassigned to state, nothing
in the bar indicates what tapping does; and Albums would sit up to two unlabelled taps from the
default view, which is poor placement for the epic's headline feature. It would also have forced a
`contentDescription` that contradicts the pictogram — describing the action while the icon depicts
the state — which is a reliable source of confusing screen-reader output.

**A segmented control**, showing all three views at once in the top bar. One tap to any view, and
fully self-describing. Rejected for Stage 1 on width: three labelled segments alongside search and
overflow is tight in English and tighter in the longer locales the app ships. Worth reconsidering
if view switching proves more frequent than expected.

**A bottom `NavigationBar`.** The platform-conventional answer for three peer destinations, used by
Google Photos and Samsung Gallery, and the only option that is reachable one-handed — the top-right
corner is the hardest part of a modern phone to reach. Rejected for Stage 1 as disproportionate: it
claims roughly 80dp permanently from a grid, must be hidden in the full-screen viewer and in both
selection modes, and is a significant change to the identity of a deliberately chrome-light app.
The dropdown does not foreclose it; if telemetry or feedback shows view switching is frequent, this
remains the upgrade.

> **Adopted 2026-11-11 by maintainer decision — see Amendment 2 below**, which records how each of
> the reasons above for rejecting it was answered.

## Consequences

> **Amended 2026-11-11 — see Amendment 2 below**, which lists the consequences here that no longer
> hold now that the switcher is a bottom bar.

### Positive

- Stage 1 adds one branch to the chain, and the chain is still legible at six.
  (**Amended 2026-10-07:** two branches, and seven. See Amendment 1.)
- Albums inherits search, sort, grouping, filtering, selection, and the saveable scroll state
  without new code.
- ADR-0002's viewer placement is untouched, so its regression stays fixed.
- The `DisplayMode` naming debt is cleared as a side effect of work already being done.
- Back from Timeline View starts working, fixing a known defect.
- All three views are named, in the user's language, in one place — so Albums is discoverable
  rather than hidden behind an unlabelled control.
- The top bar gets **simpler**: a control is removed, not added, which returns width to the title
  in the locales where it is tightest.
- Accessibility needs no special handling. The switcher is text in a menu rather than an icon whose
  description has to contradict what it depicts.
- The pattern scales. A fourth view — Stage 3 could plausibly want one for people — is another menu
  item, where it would have been a fourth stop on a cycle.

### Negative

- Two taps to change view, where a segmented control or bottom bar would take one.
- The title becomes interactive, so it needs a real touch target and a visible affordance. A bare
  `Text` with a chevron is not discoverable enough on its own, and the 48dp minimum must be met
  deliberately rather than inherited from `IconButton`.
- One more menu in an app that already leans heavily on `DropdownMenu`.
- `R.string.app_name` currently doubles as the Folders View title. It needs a proper label of its
  own, in all ten locales, rather than the application name standing in for a view name.
- Two new `PreferenceScope` values mean two more persisted preference sets, more defaults in
  `ViewPreferences.defaultFor`, and more derived flows in `GalleryViewModel` — which is already
  ~850 lines.
- The decision is explicitly temporary. Stage 3 will supersede it, and the album screens written
  against the chain will have to move.

## References

- `docs/design/albums.md` §7 and §9 — staging, and the strain this epic puts on existing structure
- ADR-0002 — why `FullImageScreen` sits outside the saveable state holder, and the hazard that
  creates for any navigation refactor
- ADR-0006 — the per-scope preference model the two new scopes follow
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt` — the title and
  the view-switching `IconButton` this decision replaces
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt` — where the title is
  already switched on `DisplayMode`, which is why a state-showing icon would be redundant
- `app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt` — the branch chain
- `docs/architecture/ARCHITECTURE.md` §1 and §4 — the main views and the chain, both of which this
  changes

## Amendment 1 — 2026-10-07 — Stage 1 added two branches, not one; the chain is seven

This amendment corrects **one arithmetic claim** in Context and in Consequences → Positive. The
decision is unchanged and continues to govern: Albums View is a third `DisplayMode` on
`FolderListScreen`, Album Detail View is a branch in `MainActivity`'s `if / else if` chain, and no
navigation library is adopted for Stage 1. Only the count moved.

It is recorded as an amendment rather than a superseding ADR, following ADR-0005 Amendment 1,
ADR-0007 Amendments 1 and 2, ADR-0010 Amendment 1, and ADR-0011 Amendment 1. The original
sentences are left intact.

**What this record predicted.** "So Stage 1 adds **one** branch, not two," and "the chain is still
legible at six."

**What landed.** Two branches, and a chain of seven:

```
isManageExcludedMode     → ManageExcludedScreen
isSettingsMode           → SettingsScreen
selectedFolder != null   → FolderDetailScreen
selectedAlbumId != null  → AlbumDetailScreen
isFavouritesSelected     → AlbumDetailScreen        (isFavouritesAlbum = true)
hasPermission            → FolderListScreen
else                     → PermissionDeniedScreen
```

**Why.** The record assumed Favourites and an Album would reach Album Detail View through the same
branch. They cannot, because the selector differs in kind. An Album is selected by its database
id, and `AlbumsViewModel.selectedAlbumId` holds it. Favourites has no row in the album database at
all — it is a `MediaStore` column (ADR-0011) — so there is nothing to put in that field, and the
selection is a `rememberSaveable` boolean in `MainActivity` instead. One `Long?` cannot carry both
without a sentinel value, and a magic id for a record that does not exist is worse than a branch.
The two branches render the same screen with `isFavouritesAlbum` differing, and the
`SaveableStateProvider` keys differ for the same reason: `album_detail:$albumId` against the
literal `album_detail:favourites`.

**What this does not change.** Six was offered as evidence that the chain stays legible without a
navigation library. Seven does not overturn that — the branches are flat, mutually exclusive, and
read top to bottom — but it does spend the headroom faster than this record assumed. The judgement
that Stage 3 is the moment to revisit the chain stands, and is now slightly more urgent, not less.

The sibling claim in Consequences → Negative that `GalleryViewModel` "is already ~850 lines" is
left as written, as a record of what was true when the decision was taken. Do not read a line
count out of an ADR; `docs/architecture/ARCHITECTURE.md` §11 carries the current state of that
debt without restating a number.

## Amendment 2 — 2026-11-11 — The view switcher becomes a persistent bottom bar

This amendment replaces **one part** of the decision: the title-anchored dropdown that switches
between Folders View, Timeline View and Albums View. Everything else this record decided continues
to govern, unchanged:

- Albums View is a third `DisplayMode` on `FolderListScreen`, not a screen of its own.
- `MainActivity`'s `if / else if` chain is retained, with no navigation library and no back stack.
  This amendment adds **no branch** and reorders none.
- `DisplayMode` keeps its `FOLDERS` / `TIMELINE` / `ALBUMS` names, and Albums View and Album Detail
  View keep their own `PreferenceScope`s.
- Back from Timeline View or Albums View returns to Folders View.
- This record is still superseded, not amended, when Stage 3's people flow needs a real stack.

Because the structural decision stands and only its switching control changes, this is an
amendment rather than a superseding ADR, following Amendment 1 here and the amendments to ADR-0005,
ADR-0007, ADR-0010, ADR-0011 and ADR-0018. The original text above is left intact; short notes mark
the passages this amendment overrides. The maintainer approved overriding the dropdown decision on
2026-11-11.

### Why the switcher changes

The original record rejected a bottom `NavigationBar` "for Stage 1 as disproportionate" and named
it "the upgrade" should view switching prove frequent. The trigger is not telemetry — the app has
none — but a maintainer decision to adopt a redesign whose centre is a three-tab bar beneath the
grid. The record's own Alternatives already conceded the case for it: one tap to any view, the only
option reachable one-handed, and the platform convention for three peer destinations. Its three
reasons for rejecting the bar are answered as follows.

1. *It claims roughly 80dp permanently from a grid.* Accepted as the cost.
2. *It must be hidden in the full-screen viewer and in both selection modes.* Half reversed. It is
   still absent from the viewer (point 3). It is **not** hidden during selection or search: the
   maintainer chose instead that a tab tap cancels the selection or search (point 6).
3. *It changes the identity of a deliberately chrome-light app.* Accepted by the maintainer as part
   of the redesign.

The dropdown's own Negative consequences — two taps per switch, an interactive title that needed a
deliberate touch target and affordance, one more `DropdownMenu` — go with it.

### Decision

**1. The control.** A bottom bar of three tabs, in the order **Folders, Albums, Timeline**. That is
the redesign's order and deliberately not `DisplayMode.entries` order (`FOLDERS`, `TIMELINE`,
`ALBUMS`), so the bar lists its tabs explicitly rather than iterating the enum. Each tab has an
icon and a text label. The labels reuse `folders_title`, `albums_title` and `timeline_title`, which
exist in all ten locales; the icons follow the redesign — a folder, a photo library, a clock. The
title dropdown, its trailing chevron, its check-marked menu and its `cd_switch_view` description
are removed, and `SearchTopBar` no longer takes a `DisplayMode`. The bar's canonical name belongs
to the UI vocabulary and must not be "bottom bar" or "navigation bar" alone: **Viewer bottom bar**
already exists, and Android's system navigation bar sits directly beneath this one.

> **Named 2026-11-11.** The UI vocabulary calls it the **Main view bar** (`MainViewBar`) and each
> tab a **Main view tab** (`MainViewTab`). `cd_switch_view` is removed from all ten locales.

**2. The title.** On Folders View, Timeline View and Albums View the browse top bar's title is the
app's name, `R.string.app_name` ("Easy Gallery"), and is not interactive. The selected tab now names
the current view, and the original Context's objection to a control that states the same fact the
title already states applies in reverse: the view's name is stated once, on the tab. Detail screens
keep their own titles — the folder's name in Folder Detail View, the album's name or Favourites in
Album Detail View — and their back button. No string is added. The original Negative consequence
that `app_name` should stop standing in for a view name is met differently: it is again the title,
but as the app's name on all three views, while the view names live on the tabs.

**3. Where the bar is shown.** It is persistent across the browsing screens, including both detail
screens, in every selection mode and in search.

| Shown | Hidden |
|---|---|
| Folders View, Timeline View, Albums View | Full-screen viewer |
| Folder Detail View | Folder thumbnail picker, a mode of Folder Detail View |
| Album Detail View, for an album and for Favourites | New album screen, New automatic album screen |
| | Settings screen, Manage excluded screen |
| | Permission denied screen |

The maintainer's answer named the detail screens and left the rest to a recommendation, so the
Hidden column is listed under *Pending confirmation* with its reasons.

> **Confirmed 2026-11-11 — see P1 under *Maintainer's answers* below.** The Hidden column is now
> the maintainer's decision, not a recommendation.

**4. Where it lives.** In `MainActivity`, not in any screen: inside the non-viewer `else` branch,
**below and outside** `saveableStateHolder.SaveableStateProvider(screenKey)`.

```
selectedMedia != null   → FullImageScreen              (no bar; outside the holder — ADR-0002)
else
  ├ SaveableStateProvider(screenKey) { the branch chain, unchanged }
  └ the bar, when the branch shown is in the Shown column of point 3
```

Each consequence of that placement is deliberate.

- **The chain is unchanged.** The bar is not a branch, not a screen, and has no `screenKey`.
- **ADR-0002 is untouched.** The viewer branch renders neither the provider nor the bar, so the
  viewer stays immersive and outside the holder. Nothing done for the bar may move `FullImageScreen`
  into the `else` branch or the provider.
- **The bar holds no saveable state.** Its selected tab is derived (point 5), so it gains nothing
  from the provider. Outside every branch it stays composed while the screen above it changes, which
  is what makes it persistent rather than rebuilt on each navigation.
- **It owns the bottom system inset.** The bar draws behind, and pads for, the system navigation
  bar. Every screen's `Scaffold` applies that inset today, so while the bar is shown the area above
  it must treat the inset as consumed, or it is paid twice. Screens without the bar still need it.

  > **Landed 2026-11-11.** The bar pads itself for the bottom and side insets, and the area above
  > it consumes the bottom inset only while the bar is shown.
- **`FolderListContent` stops rendering a switcher** and loses its `onSetDisplayMode` callback. A
  bar inside `FolderListContent` was the earlier plan and would have kept that signature, but it
  cannot be persistent across detail screens without being duplicated into `FolderDetailContent`
  and `AlbumDetailContent`, each of which would then need point 6's rule — and that rule reaches
  `AlbumsViewModel` and `MainActivity`'s Favourites flag. One bar in one place is the smaller change.

**5. What "selected tab" means.** On a main view, the tab of the current `displayMode`. On a detail
screen, the main view that owns it: **Folder Detail View selects Folders; Album Detail View selects
Albums**, for an album and for Favourites alike. In every ordinary flow this equals `displayMode`,
because a detail screen opens only from its owning view and opening it leaves `displayMode` alone.
It is derived from the branch shown rather than read from `displayMode` because the two can
disagree after process death: `displayMode` is in-memory ViewModel state and resets to Folders,
while `isFavouritesSelected` is `rememberSaveable` and is restored, so Favourites can return with
`displayMode == FOLDERS`. Nothing new is persisted.

**6. Tapping a tab cancels, then switches.** A tab tap, including a tap on the tab already
selected, does all of the following in one handler.

- **a. Cancel every selection.** Folder selection and media selection in `GalleryViewModel` — media
  selection is one set shared by Timeline View, Folder Detail View and Album Detail View — and
  album selection in Albums View, the long-press mode that shows the Album selection top bar. Album
  selection is today a plain `remember` inside `FolderListContent`. A handler in `MainActivity`
  cannot reach it, and a tap on the already-selected Albums tab would not reset it, so it moves
  into `AlbumsViewModel`, where it can be cancelled.

  > **Landed 2026-11-11** as `AlbumsViewModel.albumSelectionId`. A tab tap clears it, and the
  > selected album id, through `cancelAlbumSelectionAndDetail()`.
- **b. Cancel search in all five scopes** — `FOLDERS`, `TIMELINE`, `ALBUMS`, `FOLDER_DETAIL` and
  `ALBUM_DETAIL` — which clears each query too, as closing search already does. All five rather
  than the current one, because a search left active by a back press survives in a scope the user
  is not looking at, and a tab must never open onto a silently filtered list.
- **c. Clear Show excluded** (`showExcludedTemporarily`), as switching views already does.
- **d. Leave any detail screen.** Folder Detail View by the same path as its back button, which also
  drops the folder's media, the thumbnail picker state and the Folder Detail search; Album Detail
  View by clearing the selected album id; Favourites by clearing `isFavouritesSelected`.
- **e. Set `displayMode`** to the tapped view.

Re-tapping the selected tab therefore cancels and stays on a main view, and returns to the owning
main view from a detail screen, as back would. Nothing asks before a selection is discarded. Not
cancelled, because the bar cannot be tapped while they are up: the full-screen viewer, where the
bar is absent, and modal dialogs — the Destination picker, the Create folder dialog, delete
confirmations, the display-preference dialogs — which sit above it behind their scrim.

**This reverses the Planner's earlier recommendation**, which was to hide the bar during selection
and search so that the question never arose. The maintainer chose a constant bar. The hazard hiding
avoided — acting on a selection made in another view — is removed by cancelling instead. Because
media selection is shared by three views, carrying it through a tab switch would let Delete or Move
act on items that are not on screen; a preserved search has the same shape, a query typed in one
view silently filtering another.

**7. Back is unchanged, and tab taps create no history.** Back exits a selection or search first, as
today. From Timeline View or Albums View it returns to Folders View; from a detail screen, to that
screen's main view; from Folders View it leaves the app. So Folder Detail View → Timeline tab → back
lands on Folders View, not on Folder Detail View. A history of tab visits would be a back stack,
which this record defers to Stage 3.

**8. Each tab keeps its own scroll position.** Leaving a main view and coming back — by tab, through
a detail screen, through the viewer, or across rotation — restores where it was. Detail screens
already have this through their own `screenKey`. The three main views did not. They share one
holder entry, `folder_list`; Folders View and Albums View share a single grid state and a single
list state, so switching carried one view's position into the other; and Timeline View's state was
dropped whenever it left composition. Each `DisplayMode` gets its own saveable entry, nested inside
`FolderListScreen`'s existing `folder_list` entry, with its own grid and list state. Scroll position
is UI state and stays out of the ViewModels. A search cancelled by a tab tap changes the list under
a retained position; the position is restored by index and clamped, which is accepted.

**9. Colours.** Fixed values, named in `ui/theme/Color.kt`, rather than theme roles.

- Bar container `#111111`, with a 1dp top border `#222222` — from the redesign.
- Selected icon and selected label: `BrandBlue` (`#017DDF`), the app's own blue, rather than the
  redesign's `#0084DF`. Subject to P2 below for the label.
- Indicator pill behind the selected icon: `BrandBlue` at 15% alpha.
- Unselected icon and label: `#9CA3AF`.

> **Settled 2026-11-11 — see P2 under *Maintainer's answers* below.** The selected **label** is
> drawn in `BrandBlueLight` (`#64B5FF`). The selected icon and the pill stay `BrandBlue`.

WCAG 2.x contrast, measured from relative luminance; thresholds are not rounded:

| Foreground | On | Ratio | Needs | Result |
|---|---|---|---|---|
| Unselected icon and label `#9CA3AF` | `#111111` | 7.44:1 | 4.5:1 | Passes |
| Selected icon `BrandBlue` | `#111111` | 4.50:1 | 3:1 (non-text) | Passes |
| Selected icon `BrandBlue` | the pill, composited `#0F2130` | 3.90:1 | 3:1 (non-text) | Passes |
| Selected label `BrandBlue` | `#111111` | **4.496:1** | 4.5:1 (small text) | **Fails by 0.004** |
| *For comparison:* the redesign's `#0084DF` | `#111111` | 4.83:1 | 4.5:1 | Would pass |
| *For comparison:* `BrandBlueLight` `#64B5FF` | `#111111` | 8.63:1 | 4.5:1 | Would pass |

The selected state is never carried by colour alone, since the pill marks it too, so "use of
colour" holds whatever is chosen. Text contrast for the selected label does not, as specified.

> **Resolved 2026-11-11 (P2).** With the selected label in `BrandBlueLight`, every colour the bar
> draws meets its threshold. The `BrandBlue` label row above stays as the record of why it was not
> used.

### Pending confirmation

Four points below are the Planner's recommendations, not the maintainer's decisions. The bar's
implementation should not start until each is confirmed or changed, and a change is recorded here
before the work begins.

> **Answered by the maintainer on 2026-11-11 — see *Maintainer's answers* below.** All four
> recommendations were adopted. Nothing in this section still blocks implementation. The
> recommendations are left as they were put, because their reasons are the record of why.

- **P1 — Where the bar is hidden.** Recommended: every screen in point 3's Hidden column.
  - *Full-screen viewer.* ADR-0002 renders it outside the chain and the holder, and its own Viewer
    bottom bar and immersive mode own the bottom of the screen. A second bar would compete with the
    Viewer action row and defeat immersive mode.
  - *Folder thumbnail picker.* A draft-and-commit mode in which back commits and Close cancels
    (ADR-0007). A tab tap would have to commit or discard silently, and neither is what was asked.
  - *New album screen, New automatic album screen.* One-shot drafts with their own close control,
    writing nothing until Create (ADR-0016). A tab tap would discard a draft without a prompt.
  - *Settings screen, Manage excluded screen.* Management screens reached from an overflow menu,
    with a back arrow, not browsing destinations. Settings also opens from Folder Detail View.
  - *Permission denied screen.* There is no media to browse; every tab would lead to an empty view.
- **P2 — The selected label's colour.** Recommended: keep `BrandBlue` for the selected icon and the
  pill as specified, and draw only the **selected label** in `BrandBlueLight` (8.63:1).
  Alternative: keep `BrandBlue` for the label at 4.496:1 and record the shortfall as known debt.
- **P3 — The app icon beside the title.** The main views' title currently shows the app's cream
  logo before the text, tagged `main_view_app_icon`; the redesign shows text only. Recommended: keep
  the logo beside "Easy Gallery" — it is the app's mark and was added deliberately, and the
  redesign's subject is the bar.
- **P4 — Re-tapping the selected tab.** Recommended: cancel only, as point 6 says, with no scroll to
  the top. Scroll-to-top on re-tap is a common convention but was not asked for, and it works
  against point 8.

### Maintainer's answers — 2026-11-11

These are decisions, and they govern the bar's implementation.

- **P1 — The bar is hidden** on the full-screen viewer, the Folder thumbnail picker, the New album
  screen, the New automatic album screen, the Settings screen, the Manage excluded screen, and the
  Permission denied screen: point 3's Hidden column, as drawn. There are two reasons. The viewer is
  immersive (ADR-0002), and its Viewer bottom bar owns the bottom of the screen. The rest are
  focused flows, where a tab tap would silently abandon work in progress. Hiding the bar removes
  that tap, so no prompt is needed. The maintainer accepted this as a refinement of the original
  answer, "always show the bar". *Always* means every browsing screen — point 3's Shown column —
  and so point 6's cancel rule never has to reach a draft, the picker, or a settings flow.
- **P2 — Two blues, split by contrast.** The selected tab's **icon** and its **indicator pill** use
  the app blue, `BrandBlue` `#017DDF`, with the pill at about 15% alpha. The selected **label** uses
  the app's lighter blue, `BrandBlueLight` `#64B5FF`, at 8.63:1 on `#111111`. The reason is point
  9's table. `BrandBlue` text on the bar measures 4.496:1, which misses WCAG 1.4.3's 4.5:1 for small
  text by 0.004, and the threshold is not rounded. The icon is non-text, needs only 3:1 under 1.4.11,
  and meets it on the container and on the pill, so the icon keeps the app's own blue. The
  alternative, `BrandBlue` for the label plus a known-debt entry, was declined. The rest of point 9
  is unchanged: container `#111111`, top border `#222222`, unselected icon and label `#9CA3AF`.
- **P3 — The logo stays.** On Folders View, Timeline View and Albums View the app's logo
  (`main_view_app_icon`) stays before the "Easy Gallery" title, although the redesign shows text
  only. The logo is the app's mark and was added deliberately, and the redesign's subject is the
  bar. Read point 2 with this: the title is the app's name, preceded by its logo.
- **P4 — Re-tapping cancels and does not scroll.** Re-tapping the selected tab does what point 6
  lists. It cancels every selection and search, clears Show excluded, and closes a detail screen,
  returning to its main view. It does **not** scroll to the top. Scroll-to-top was not asked for,
  and it would undo the per-tab position that point 8 exists to keep.

### Consequences that change

Of the original Consequences:

- Positive, *"All three views are named, in the user's language, in one place"* — still holds, and
  more strongly: the bar shows all three at all times.
- Positive, *"The top bar gets simpler"* — holds further: the title is now static text.
- Positive, *"Accessibility needs no special handling. The switcher is text in a menu…"* — **no
  longer holds.** The switcher is a set of labelled tabs, and the selected label's contrast (P2) is
  the special handling now needed.
  *(Settled 2026-11-11 by P2: the selected label is drawn in `BrandBlueLight`.)*
- Positive, *"The pattern scales. A fourth view … is another menu item"* — **no longer holds as
  written.** A fourth tab fits, but a bar conventionally carries three to five destinations, so
  there is now a ceiling where a menu had none.
- Positive, *"ADR-0002's viewer placement is untouched"* — still holds; point 4 keeps it so.
- Negative, *"Two taps to change view"* — **no longer holds**; it is one tap.
- Negative, *"The title becomes interactive…"* — **no longer holds**; the title is not interactive.
- Negative, *"One more menu in an app that already leans heavily on `DropdownMenu`"* — **no longer
  holds**.
- Negative, *"`R.string.app_name` currently doubles as the Folders View title. It needs a proper
  label of its own"* — **no longer applies**, for the reason given in point 2.
- The other consequences still hold, including the two extra preference scopes and the decision's
  temporariness.

New consequences:

- **Positive.** One tap to any main view from any browsing screen, within reach of the thumb.
- **Positive.** Selection and search can no longer leak from one view into another.
- **Positive.** Folders View and Albums View stop sharing a scroll position, and Timeline View stops
  losing its own — a pre-existing defect that point 8 fixes.
- **Negative.** The bar takes height permanently from five views, both detail screens included.
- **Negative.** A tab tap discards a selection without confirmation, so an accidental tap loses a
  large selection.
- **Negative.** `MainActivity` composes persistent UI outside the chain for the first time. Anything
  later proposed for that position must be weighed on its own, not assumed to follow from this.
- **Negative.** `FolderListContent`'s signature changes — `onSetDisplayMode` goes and album
  selection is hoisted — which breaks `FolderListContentTest` by design.
- **Negative.** The bar's colours are fixed rather than theme roles and will not follow a future
  light theme or dynamic colour. Acceptable while the theme is dark-only.
- **Negative.** Stage 3's navigation rewrite inherits the bar. It fits either candidate, a
  `Scaffold` around a `NavHost` or a hand-rolled stack, but the rewrite must carry points 5 and 6,
  not only the widget.
- **Positive.** (P1) No tab tap can abandon a draft, the thumbnail picker, or a settings flow,
  because the bar is not shown there.
- **Negative.** (P2) The selected tab draws its icon and its label in two different blues. This
  inconsistency is accepted so that the label meets text contrast.

### Alternatives considered for this amendment

- **The bar inside `FolderListContent` only.** The earlier plan: the cheapest, with no `*Content`
  signature change. Rejected because the maintainer wants the bar on the detail screens too.
- **A bar in each `*Content` composable.** Rejected: three copies, each needing point 6's rule with
  its reach into `AlbumsViewModel` and `MainActivity`, and a bar rebuilt on every navigation.
- **Hide the bar during selection and search.** The Planner's earlier recommendation, overridden by
  the maintainer as recorded in point 6.
- **Keep selection and search across a tab switch.** Rejected: the shared media selection would let
  actions apply to items that are not on screen.

### References

- The redesign supplied by the maintainer (`stitch_gallery_app_redesign/code.html`), not in the
  repository — the bar's order, icons, and the `#111111` / `#222222` / `#9CA3AF` values
- ADR-0002 — the viewer's placement outside the holder, which point 4 preserves
- ADR-0007 — the Folder thumbnail picker's draft-and-commit rule, behind P1
- ADR-0016 — the two album-creation screens, behind P1
- `app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt` — the chain and the holder the
  bar sits beside
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt` — the shared grid and
  list state, and the local album selection, that points 6 and 8 change
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/SearchTopBar.kt` — the dropdown
  removed
- `app/src/main/java/com/davide/seddio/easygallery/ui/theme/Color.kt` — `BrandBlue`,
  `BrandBlueLight`, and the bar's colours
- `docs/architecture/ARCHITECTURE.md` §1, §3, §4 and §11 — updated after the bar lands, not before
  (updated 2026-11-11, once the bar had landed)
- WCAG 2.2 success criteria 1.4.1, 1.4.3 and 1.4.11
