# ADR-0011: Favourites is MediaStore-backed with a version-tiered write path

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

Favourites is the one album present from first launch, which the user cannot create, rename or
delete, and which is populated by a heart on individual media items.

Unlike user albums, Favourites has a platform equivalent. `MediaStore.MediaColumns.IS_FAVORITE`
(API 30) is a system-wide flag: a photo favourited in Easy Gallery appears as a favourite in Google
Photos and in the system photo picker, and the mark survives Easy Gallery being uninstalled. The
alternative — an app-owned list, like every other album — is invisible to other apps and dies with
the app.

The product decision is that interoperability wins: Favourites is backed by `IS_FAVORITE`
(`docs/design/albums.md` §8.2). That decision imposes a set of platform constraints that are not
obvious and are expensive to discover late:

- `IS_FAVORITE` does not exist before API 30. The app's `minSdk` is 28.
- `MediaStore.createFavoriteRequest()` (API 30) raises a **system confirmation dialog on every
  request**. A heart tap that summons a system prompt is not a heart tap.
- `MANAGE_MEDIA` (API 31) suppresses per-operation prompts, but its documented scope is exactly
  `createWriteRequest`, `createTrashRequest` and `createDeleteRequest`. **It does not cover
  `createFavoriteRequest`.**
- Write access obtained through `createWriteRequest` is tied to the requesting Activity's lifecycle
  and explicitly does not support `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`, so it cannot be acquired
  once and kept.
- `MediaStore.markIsFavoriteStatus()` (API 36) sets the flag with no prompt and no special
  permission, requiring only the broad read permission the app already holds — but at an API level
  almost no device yet runs.

The app is well placed for this: it already declares `ACCESS_MEDIA_LOCATION` (ADR-0001), which the
platform documentation requires alongside `MANAGE_MEDIA` to fully suppress the `createWriteRequest`
dialog, and it already owns the complete `IntentSender` plumbing — `MediaPermissionHandler`,
`GalleryViewModel.pendingWriteRequest`, and the `MainActivity` launchers — built for delete and
move.

## Decision

Back Favourites with `MediaStore.IS_FAVORITE`, and write it through a **four-tier path** selected
at runtime, strongest first:

| Tier | Condition | Mechanism | Prompt |
|---|---|---|---|
| 1 | API 36+ | `markIsFavoriteStatus()` | none |
| 2 | API 31+ and `MANAGE_MEDIA` granted | `createWriteRequest()`, then `update(IS_FAVORITE)` | none |
| 3 | API 30+ | `createFavoriteRequest()` | one system dialog per request |
| 4 | API 28–29 | — | **Favourites is unavailable** |

Reading is uniform on API 30+: query with `QUERY_ARG_MATCH_FAVORITE` set to `MATCH_ONLY`. The
album needs no storage of its own and no index.

Three subordinate decisions:

- **No app-owned fallback on API 28–29.** The feature is absent there. Maintaining a parallel
  favourites store, with its own persistence, tests, and divergent behaviour, is not worth it for
  legacy hardware, and a Favourites that is *not* system-wide on some devices would make the
  feature mean two different things.
- **`MANAGE_MEDIA` is declared, requested contextually, and never required.** It is asked for after
  the user has actually met the system prompts, not at launch, and every tier below it remains
  fully functional if it is refused. It is a friction remover, not a precondition.
- **Tier 2 goes through `createWriteRequest`, never `createFavoriteRequest`.** This is the
  non-obvious consequence of `MANAGE_MEDIA`'s scope, and implementing it the apparent way produces
  an app that ships a special permission and still shows a dialog.

## Alternatives Considered

**An app-owned favourites list, like every other album.** Uniform with the rest of the epic, with
one code path, no version tiers, and no new permission. Rejected because it discards the single
property that makes Favourites worth treating specially — that the mark is shared with the rest of
the device and outlives the app.

**App-owned as the source of truth, mirrored to `IS_FAVORITE` opportunistically.** Gives the
uniform model *and* interoperability. Rejected for having two sources of truth that can disagree:
a photo favourited in Google Photos would not be favourited here, and reconciling the two is a
synchronisation problem with no correct answer.

**App-owned fallback only on API 28–29.** Rejected as above — the maintenance and behavioural
divergence are not worth it for those versions.

**Requiring `MANAGE_MEDIA` for the feature.** Rejected: it cannot be granted in-app, sends the user
to a system settings page, and would make a core feature depend on a journey many users abandon.

## Consequences

### Positive

- Favourites interoperates with the rest of the device and survives uninstall.
- The album costs no storage, no schema, and no index — it is a query.
- `MANAGE_MEDIA`, if granted, also removes the confirmation dialog the app currently shows on
  **every delete and every move** of media it does not own, which is nearly the entire library. The
  permission improves three existing interactions, not just the new one.
- The existing `IntentSender` plumbing is reused rather than duplicated.

### Negative

- Favourites does not exist on API 28–29, and the UI must omit it there rather than show a feature
  that fails.
- Four write paths means four things to test, and the device matrix to test them on is wider than
  anything the app has needed before. Tier 1 is effectively untestable on current hardware.
- Tier 2 must re-acquire write access once per Activity lifetime, so the first heart tap of a
  session carries an asynchronous round trip before the icon fills.
- `MANAGE_MEDIA` is a special-access permission with Play policy implications, on top of the
  existing policy restrictions on `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`. The Play declaration
  must be confirmed current before this ships.
- Favourites behaves differently from every other album in the app — it is a query, not a stored
  membership list — so code that assumes uniformity across albums will be wrong about this one.

## References

- `docs/design/albums.md` §4 — Favourites, including both platform traps
- ADR-0001 — `ACCESS_MEDIA_LOCATION`, already declared and required alongside `MANAGE_MEDIA`
- ADR-0012 — the album model Favourites deliberately does not follow
- `app/src/main/java/com/davide/seddio/easygallery/data/DefaultMediaPermissionHandler.kt` — the
  existing `IntentSender` wrapper
- `GalleryViewModel` lines ~418 and ~604 — the existing delete and move paths that already fall
  back to a system prompt
