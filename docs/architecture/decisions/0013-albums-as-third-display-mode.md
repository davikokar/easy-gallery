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

## Consequences

### Positive

- Stage 1 adds one branch to the chain, and the chain is still legible at six.
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
