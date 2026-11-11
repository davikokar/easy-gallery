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

> **Amended 2026-10-07 — see Amendment 1 below.** The last bullet understates where
> `markIsFavoriteStatus()` is available. It also exists on API 30–35 devices whose R extension is
> at version 16 or later, which is a large share of current hardware rather than almost none.

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

> **Amended 2026-10-07 — see Amendment 1 below.** Tier 1's condition is wider than "API 36+":
> `markIsFavoriteStatus()` is also present on API 30–35 devices carrying R extension version 16 or
> later. The tier order, the four mechanisms and every other condition are unchanged. The original
> row is left intact.

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

## Amendment 1 — 2026-10-07 — Tier 1 is also reachable below API 36, via R extension 16

This amendment **widens the condition on tier 1 only**. Everything else in the record continues to
govern verbatim: the four tiers and their order, tier 2 going through `createWriteRequest` rather
than `createFavoriteRequest`, `MANAGE_MEDIA` being optional and late-asked, the uniform
`QUERY_ARG_MATCH_FAVORITE` read path, the absence of the feature on API 28–29, and every
alternative rejected above.

It is recorded as an in-place amendment rather than a superseding ADR, following ADR-0005
Amendment 1 and ADR-0007 Amendments 1 and 2. The decision with lasting structural impact — that
Favourites is `MediaStore`-backed and written through a runtime-selected tiered path, strongest
first — has not stopped applying. Only the predicate that selects the strongest tier has moved.
The superseded row in the Decision table is annotated in place and left intact.

### What changed

Tier 1 is selected when:

```
sdkInt >= 36 || (sdkInt >= 30 && rExtensionVersion >= 16)
```

where `rExtensionVersion` is `SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R)`.

`MediaStore.markIsFavoriteStatus()` is **not** a plain API 36 addition. The platform's own
`api-versions.xml` records it as
`markIsFavoriteStatus … sdks="30:16,31:16,33:16,34:16,35:16,36:16,0:36"` — that is, present on any
API 30–36 device whose R extension is at version 16 or later, and unconditionally from API 36. The
narrow gate was therefore not wrong, merely incomplete: it refused the promptless path on devices
that in fact offer it, and sent them down tier 2 or tier 3 instead.

### Why the record was written narrowly

Not a mistake being papered over. R extension versions are easy to overlook: they do not appear in
`Build.VERSION.SDK_INT`, they are not expressed by `@RequiresApi`, and the method's documentation
reads as an ordinary addition at the API level it became unconditional. "API 36+" is the obvious
reading, and it is the reading the original record took. Discovering the `sdks=` attribute in
`api-versions.xml` is what turned an obvious reading into a demonstrably wider one.

The general lesson, worth more than this one gate: **for a `MediaStore` or media-provider API, check
`api-versions.xml` for an `sdks=` attribute before assuming the API level in the documentation is
the floor.** Mainline modules ship these APIs ahead of the platform release, and the gap is several
years of devices.

### Consequences of this amendment

**Positive**

- Strictly better behaviour, on real hardware rather than hypothetical hardware. A current API 34
  or 35 device with an up-to-date media module gets a promptless heart tap, where before it got a
  system dialog on every tap or depended on `MANAGE_MEDIA` being granted.
- Tier 1 stops being effectively untestable. The Consequences above call it so on the assumption
  it needed API 36; the extension path makes it reachable on shipping devices today. What has
  since been observed is narrower than what is now reachable — see the follow-up note below.

**Negative**

- The tier predicate is no longer readable from `SDK_INT` alone, so a bug report's API level is not
  enough to say which path a device took. The extension version has to be asked for too.
- One more thing that varies independently of the API level, on a path that already had four
  branches.

### Code and tests this governs

- `app/src/main/java/com/davide/seddio/easygallery/data/Favourites.kt` — the pure tier selection,
  which holds the widened predicate.
- `FavouritesTest`, including `r extension 16 enables direct mark before api 36`, which pins the
  widened gate. That test was correct against the code and ahead of this record; it is now
  correct against both.

### References added by this amendment

- `android.os.ext.SdkExtensions.getExtensionVersion(int)` — the extension version lookup, itself
  API 30+
- The platform SDK's `data/api-versions.xml`, `sdks=` attribute — the authority for which APIs ship
  in a mainline module ahead of their platform release

### Follow-up 2026-10-07 — what has been exercised, and on which API level

Recorded because the Positive bullet above claims tier 1 is now reachable on shipping hardware,
and the evidence behind that claim is narrower than the claim itself.

- **Tier selection** is unit-tested across the whole matrix in `FavouritesTest`, including the
  widened predicate. That is independent of any hardware.
- **Tier 1's execution has been observed on a device at API 36.** The instrumented suite has run
  green there — 49 tests, 49 passing, on a Samsung SM-G990B running Android 16. An API 36 device
  selects tier 1 through the `sdkInt >= 36` half of the predicate.
- **The R extension half of the predicate remains unobserved.** It is the half this amendment
  exists for, and an API 36 device cannot exercise it: the API-level test short-circuits before
  the extension version is consulted. Observing it needs a device at API 30–35 carrying R
  extension 16 or later — in practice a current API 34 or 35 phone with an up-to-date media
  module. Until one runs the suite, that path is reasoned from `api-versions.xml` rather than
  seen.
- **Tiers 2, 3 and 4 remain unexercised**, needing devices at API 31–35, at API 30, and at
  API 28–29 respectively.

The durable statement is the list above — which API levels have been observed — not which hardware
happens to be attached to a development machine on a given day.
