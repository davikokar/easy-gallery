# ADR-0005: Selection-scoped media actions mirror the full-screen viewer

- Status: Active
- Date: 2026-09-22
- Decision makers: Easy Gallery project maintainer

## Context

Per-item media actions are reachable from two unrelated surfaces, and the two had drifted apart.

`FullImageScreen` acts on the single currently-viewed item and offers Delete, Share, Rotate, Info
in its bottom row, plus Copy to, Move to (ADR-0003) and Use as background (ADR-0004) in the
`MoreVert` overflow. Media multi-selection mode, rendered by `MediaSelectionTopBar`, offered only
Info, Delete, Copy to, Move to and Select all. The missing actions were not a deliberate product
rule — they were an accident of the two surfaces being built at different times — so the same
photo exposed a different action set depending on how the user had reached it. The overflow items
in `MediaSelectionTopBar` also carried no `leadingIcon`, while the viewer's equivalents did, so
even the shared actions did not look alike.

The forces that shaped the response:

- `MediaSelectionTopBar` has **two** call sites: `FolderDetailScreen` (Folder Detail View) and
  `FolderListScreen` (Timeline View). It is one component, not a per-screen bar, so any parameter
  added to it must be satisfied by both.
- Selection is a set of arbitrary size and arbitrary media type. The viewer's per-item gating
  (`item.type != MediaType.VIDEO` for Rotate, `supportsWallpaper(type)` for wallpaper) does not
  translate directly: some actions are undefined for n > 1, and some are undefined for a mixed
  IMAGE/VIDEO/GIF set.
- The viewer's existing share is built inline inside the `IconButton` `onClick` lambda, with a
  hard-coded `type` ternary and `putExtra(Intent.EXTRA_STREAM, item.uri)`. Copying that lambda
  into the top bar would create a second, divergent definition of what "share" means.
- The module does **not** set `testOptions.unitTests.isReturnDefaultValues`, so any unit test that
  touches an unmocked Android framework class fails with "not mocked". ADR-0004 already dealt with
  this by keeping `supportsWallpaper` a pure function separate from `setImageAsWallpaper`.
- ADR-0004 recorded a concrete trap that applies verbatim here: gating on one value and acting on
  another lets a state change between render and click fire an action against the wrong item.

## Decision

Per-item media actions are surfaced **identically in the full-screen viewer and in multi-selection
mode**, gated by cardinality and media type rather than by screen.

**1. The selection-mode action set mirrors the viewer's, minus what is meaningless for a
selection.** The same Material icons are used at both call sites, so an action is visually
identical wherever it appears.

> **Amended 2026-09-22 — see Amendment 1 below.** As originally written, this point left
> "mirrors" ambiguous and was implemented as placement parity with the viewer. It now means the
> same *action set* and the same *icons*; it does **not** mean the same physical placement.

**2. Gating is by cardinality and media type, not by screen.**

- **Use as background** renders only when exactly one item is selected *and*
  `supportsWallpaper(type)` is true for that item. Cropping and setting a single wallpaper from N
  sources has no meaning.
- **Rotate is never rendered in selection mode.** Rotate mutates `MediaViewerState.currentRotation`
   — transient viewer display state owned per ADR-0002 — and does **not** write to the file on
  disk. It is a viewer affordance, not a media operation, and there is no viewer to rotate when
  the user is in a grid. Offering it in selection mode would advertise a file-level transform the
  app does not perform.

**3. Intent construction lives in stateless, screen-agnostic helpers under `ui/components/`** —
the existing `WallpaperIntents.kt` and a new `ShareIntents.kt` — rather than inline in screens.
One action therefore has exactly one definition and behaves identically at every call site. The
pure parts stay Android-free so they unit-test on a plain JVM, following ADR-0004's split of
`supportsWallpaper` from `setImageAsWallpaper`.

**4. The multi-item share contract is explicit.**

- `n == 1` → `Intent.ACTION_SEND` with `putExtra(Intent.EXTRA_STREAM, uri)`.
- `n > 1` → `Intent.ACTION_SEND_MULTIPLE` with
  `putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>)`. The extra must be an
  `ArrayList`, not any `List`, because the framework reads it as a parcelable array list.
- MIME narrowing: all IMAGE/GIF → `image/*`; all VIDEO → `video/*`; mixed → `*/*`.
- `ClipData` is attached in addition to `FLAG_GRANT_READ_URI_PERMISSION`, because that flag
  applies to the intent's data and `ClipData` URIs — **not** to URIs passed in `EXTRA_STREAM`.
  Without it the grant is not actually issued.

**5. `MediaSelectionTopBar`'s new parameters are required, not defaulted.** Giving them default
no-op lambdas would let a call site silently omit an action and reintroduce exactly the
inconsistency this record removes. Required parameters make an omission a compile error.

**6. Gate and act on the same resolved value.** The selected-media list is resolved once into
`remember(selectedMediaItems)` and that single value feeds both the "Use as background" visibility
gate and its click handler. This is ADR-0004's trap applied to a new surface: gating on one read
of the selection and acting on a later read allows the selection to change in between.

## Amendment 1 — 2026-09-22 — All selection-mode actions live in the overflow menu

This amendment narrows **decision point 1** only. The record stays `Active`; decision points 2
through 6, the Alternatives Considered and the Relationship to Existing ADRs sections below are
unchanged and continue to govern. Nothing above has been removed — the original text remains as
written, with the pointer note added in place.

This is recorded as an amendment rather than a superseding ADR because the governing decision has
not stopped applying. The action set, the cardinality and media-type gating, the single-source
intent helpers and the required-parameter rule are all intact. What changed is the presentation of
an action set inside one composable — local and easily reversible, and so below this project's own
threshold for a new record — plus a correction to wording that a reader could reasonably have
taken as a placement rule.

### What changed

Originally, selection mode split its actions the way the viewer does: Delete, Share and Info as
icon buttons in the top bar, with Use as background, Copy to, Move to and Select all in the
`MoreVert` overflow.

**In media multi-selection mode, every media action now lives in the three-dot overflow menu.**
The top bar retains only the close affordance, the selection count and the overflow button.
Delete, Share and Info move into the dropdown alongside Use as background, Copy to, Move to and
Select all.

### Why "mirrors the viewer" no longer implies placement

The two surfaces are not the same kind of surface. The viewer is a persistent, single-item screen
with room for direct actions, so it keeps its bar-button layout. The selection bar is a transient,
contextual bar that appears over the grid and disappears when the selection is cleared. Parity
between them is parity of *meaning* — the same actions, named and iconed the same way — not parity
of physical layout.

### Rationale

- **The bar is transient and contextual, and the action set is growing.** This change series took
  it from four actions to seven. Keeping the bar visually quiet avoids crowding as that set grows.
- **Icon-only buttons are ambiguous for destructive and metadata actions.** A bare trash or info
  glyph asks the user to guess. Dropdown items pair each icon with a label, which is clearer and
  is already how Copy to and Move to were presented.
- **Discoverability is unified.** In selection mode there is now exactly one place to look for
  what can be done with the selection, rather than two.

### Constraints preserved

- **Rotate is still never offered in selection mode** (decision point 2). Moving the other actions
  into the overflow does not make the overflow a home for viewer-only affordances.
- **Use as background is still gated to exactly one selected item that supports wallpaper**
  (decision point 2). Placement does not relax cardinality.
- **The same Material icons are still used for an action wherever it appears** (decision point 1).
  An action's icon does not change because its container did.
- **The `delete_button` test tag must survive the move.** The tag travels from the bar's
  `IconButton` onto the corresponding `DropdownMenuItem`, so the instrumented contract is retained
  rather than dropped.

### Consequences of this amendment

#### Positive

- The selection bar is less crowded, and stays legible as further actions are added.
- Every action is labelled rather than icon-only.
- One discovery point for everything the selection can do.

#### Negative

- **Every action now costs an extra tap**, including Delete, which was previously reachable in
  one. Frequency of use was traded for clarity and room to grow.
- **Regression risk to the existing instrumented tests** that located Delete, Share and Info in
  the bar. `mediaSelectionModeShowsDeleteButton` and `mediaSelectionModeShowsShareAction` in
  `FolderDetailContentTest` assert those nodes are displayed without opening the overflow, and
  must now open it first — as `rotateOptionIsMissingFromSelectionMenu` in the same file already
  does. `selectionModeShowsDeleteButton` in `FolderListContentTest` is **not** affected: it
  exercises `isSelectionMode`, which renders the folder-selection `SelectionTopBar`, a separate
  component that carries its own `delete_button` tag.

#### Neutral

- **No API or parameter change to `MediaSelectionTopBar`.** The same required callbacks —
  `onDelete`, `onShare`, `onInfoClick` — are simply rendered in a different place. Decision point
  5's required-parameter rule is unaffected.
- **Both call sites are unaffected.** Folder Detail View and Timeline View pass exactly what they
  passed before.

## Alternatives Considered

**Leave selection mode with its reduced action set.** Rejected. The subset was not a product
decision, and the inconsistency is discoverable by the user: the same photo offers Share in one
place and not the other, with nothing explaining the difference.

**Give the selection bar its own copy of the share-intent construction.** Rejected. That is how
the two surfaces diverged in the first place. Two inline copies of the MIME ternary and the
`EXTRA_STREAM` call would drift, and the `ClipData` fix below would have had to be applied twice.

**Add a per-screen action-set parameter (e.g. `allowedActions: Set<MediaAction>`).** Rejected. It
encodes the wrong axis. The real constraints are how many items are selected and what type they
are; letting a screen declare its own permitted set is the mechanism by which the two surfaces
would drift again, and it hides the cardinality rules in the call sites.

**Offer Rotate in selection mode as a batch file-level rotation.** Rejected as out of scope. It is
a different feature: it requires writing EXIF orientation or re-encoding N files, `SecurityException`
recovery on scoped storage, and progress reporting. Wiring the existing transient-rotation call
into a batch surface would look like that feature while doing none of it.

**Give the new `MediaSelectionTopBar` parameters default values to avoid touching both call
sites.** Rejected. It converts a compile error into a silently missing button.

**Cap the number of items that can be shared at once.** Rejected. Any cap would be an arbitrary
number, and the real limits (share-target truncation, binder transaction size) vary by device and
by receiving app. See the negative consequence below.

## Consequences

### Positive

- The action surface is consistent: an action means the same thing, looks the same and is reached
  the same way whether the user is in the viewer, in Folder Detail View or in Timeline View.
- Each intent has a single source of truth in `ui/components/`, so a fix lands once and applies to
  every call site.
- Attaching `ClipData` also **fixes the pre-existing viewer share**, which only worked because
  receiving apps typically already hold `READ_MEDIA_IMAGES` and could open the URI on their own
  authority. A receiver without that permission could not read the shared item; now the grant is
  genuinely issued.
- **No new string resources.** Every key needed (`cd_share`, `share_media_title`,
  `menu_use_as_wallpaper`, `menu_copy_to`, `menu_move_to`, `action_delete`, `properties_title`)
  already exists in all 10 locales, so nothing ships untranslated.
- **No ViewModel changes and no screen-signature changes.** The work is confined to
  `MediaSelectionTopBar`, its two call sites and the intent helpers.
- The pure helpers are unit-testable on a plain JVM without Robolectric or MockK, matching the
  existing `WallpaperIntentsTest` and `MediaLocationTest` pattern.

### Negative

- **The blast radius exceeds the originating request.** `MediaSelectionTopBar` is shared by Folder
  Detail View and Timeline View, so a change asked for in one view necessarily lands in both.
  There is no way to scope it to one screen without forking the component, which would recreate
  the divergence this record removes.
- **Large selections may fail at the platform boundary.** A share of many items can be truncated
  by the receiving app, or can overflow the binder transaction limit and raise
  `TransactionTooLargeException`. This is accepted: no cap is imposed, and the failure is left to
  surface from the share sheet rather than being pre-empted by an arbitrary limit.
- **Mixed-type selections degrade to `*/*`.** Android's documentation discourages `*/*` because it
  offers the user targets that cannot handle the content. It is nonetheless the only honest MIME
  type for a heterogeneous set, and is the documented fallback.
- The cardinality rules live at the call site, in composable conditionals, rather than in a single
  declarative table. Adding a further action means re-deriving whether it is n == 1, n >= 1, or
  type-gated.

### Neutral

- **Share and "Use as background" do not exit selection mode.** This matches the viewer, which
  stays open after either action. The user can share a selection and then act on the same
  selection again.
- **Delete and Copy to / Move to exit through their existing flows**, unchanged by this record.
  The asymmetry is inherited, not introduced: the first group does not consume the selection, the
  second does.

## Relationship to Existing ADRs

This record supersedes nothing. ADR-0001 through ADR-0004 all remain `Active`.

- **[ADR-0003](0003-explicit-media-operation-target.md)** — unchanged. Selection-mode Copy to /
  Move to already resolve through the **selection branch** of `performOperationWithPath`
  (precedence step 2), not through the explicit operation target. No new entry point is added to
  that pipeline and the precedence order is untouched.
- **[ADR-0004](0004-delegate-wallpaper-setting-to-system-cropper.md)** — unchanged. This adds a
  **second call site** to the same `setImageAsWallpaper` mechanism. The delegation to the system
  cropper, the absence of `SET_WALLPAPER`, the `<queries>` entry, the dual
  `IllegalArgumentException` / `ActivityNotFoundException` catch and the Toast fallback chain all
  apply unmodified. ADR-0004's gate-and-act trap is restated in decision point 6 because the new
  call site is a fresh opportunity to fall into it.
- **[ADR-0001](0001-on-demand-media-location-metadata.md)** — its constraint still binds. GPS/EXIF
  is read only when exactly **one** item is shown, never N times. The Info action in selection
  mode continues to route through `MediaPropertiesDialog`, whose location block is inside its
  `if (media.size == 1)` branch. Nothing here may cause a per-item EXIF read across a selection.
- **[ADR-0002](0002-viewmodel-owned-transient-viewer-state.md)** — reinforced. Rotate's exclusion
  from selection mode is a direct consequence of `currentRotation` being ViewModel-owned
  *transient viewer* state rather than a file mutation.

## References

- `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaSelectionTopBar.kt` — the
  shared selection top bar and its new required parameters
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt` — Folder Detail View
  call site of `MediaSelectionTopBar`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt` — Timeline View call
  site of `MediaSelectionTopBar`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt` — the viewer action row
  and overflow menu this set mirrors
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/WallpaperIntents.kt` —
  `supportsWallpaper`, `setImageAsWallpaper`
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/ShareIntents.kt` — new share
  intent construction and MIME narrowing
- `app/src/main/java/com/davide/seddio/easygallery/ui/MediaViewerState.kt` — `currentRotation`,
  the transient state that keeps Rotate viewer-only
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaPropertiesDialog.kt` — the
  `media.size == 1` branch that preserves ADR-0001's constraint
- `app/src/androidTest/java/com/davide/seddio/easygallery/ui/FolderDetailContentTest.kt` — the
  instrumented `delete_button` / Share / overflow assertions affected by Amendment 1
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt` — the
  folder-selection bar, a distinct component that also carries a `delete_button` tag and is **not**
  covered by this record
- `Intent.ACTION_SEND`, `Intent.ACTION_SEND_MULTIPLE`, `Intent.EXTRA_STREAM`,
  `Intent.FLAG_GRANT_READ_URI_PERMISSION`, `ClipData`
- [ADR-0001](0001-on-demand-media-location-metadata.md) — on-demand location metadata; still
  `Active`, constraint preserved
- [ADR-0002](0002-viewmodel-owned-transient-viewer-state.md) — ViewModel-owned transient viewer
  state; still `Active`
- [ADR-0003](0003-explicit-media-operation-target.md) — explicit operation target; still `Active`,
  unchanged by this record
- [ADR-0004](0004-delegate-wallpaper-setting-to-system-cropper.md) — delegate wallpaper setting to
  the system cropper; still `Active`, gaining a second call site
