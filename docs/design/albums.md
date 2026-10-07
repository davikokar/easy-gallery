# Albums — epic design

> **Status: Stage 0 complete. No album code exists yet.** Current state of every stage is in
> [§7](#7-staging-and-progress), which is the single source of truth for where this epic stands.
> This document is the product narrative for the albums epic. It holds the shape of the feature,
> the decisions already taken, and the questions still open, so that none of it has to be
> rediscovered. Binding technical decisions are recorded separately as ADRs in
> [../architecture/decisions/](../architecture/decisions/); where one exists, the ADR wins.
>
> **Owner:** Planner. Update this document when the shape of the epic changes — a stage changing
> status, a decision being taken, an open question being answered or added.

---

## 1. Why this is a big deal

Easy Gallery currently owns nothing. Media comes live from `MediaStore`, and the only things
persisted are five small `SharedPreferences` files keyed by folder path. There is no per-media-item
state anywhere in the app.

Albums are the first data the app owns. That single change is larger than the album UI, and it is
what makes this an epic rather than a feature.

The second change is almost as large: automatic albums require a **media index** — per-image facts
computed in the background — and the app today has no background infrastructure whatsoever. No
WorkManager, no Service, no `ContentObserver`. It re-queries `MediaStore` only when the user does
something.

---

## 2. What an album is

An **album** is a named, app-owned collection of media items. It is **virtual**: it holds
references, never copies. Creating an album moves no files, and deleting an album deletes no
media.

### Album is not Folder

A gallery app now has two kinds of container, and they must never blur:

| | **Folder** | **Album** |
|---|---|---|
| Exists in | the filesystem / `MediaStore` buckets | the app's own database |
| Created by | the camera, other apps, the user's file manager | the user, inside Easy Gallery |
| An item belongs to | exactly one | any number, including none |
| Deleting it | deletes files | deletes nothing |
| Survives uninstall | yes | no (except Favourites — see §4) |

Existing folder behaviour — pinning, excluding, per-folder display overrides, custom thumbnails —
is untouched by this epic and does not extend to albums.

### Favourite is not Pinned

The app already has **pinned folders**. Favourites is a different thing at a different level:
pinning orders *folders*, favouriting marks *media items*. Never let one word stand for both.

---

## 3. The membership model

Every album, of every kind, resolves its members the same way:

```
members = (rule matches ∪ manual additions) − manual exclusions
```

- A **manual album** has no rule. Its members are purely its additions.
- An **automatic album** has a rule, and the user may still add and remove individual items.

This is one type with three sets, not two types. It fell out of the decision that automatic albums
must be hand-correctable, and it is simpler than modelling manual and automatic separately.

**An exclusion is a tombstone.** "Does not currently match the rule" and "the user removed this"
must be stored distinctly, or every re-evaluation resurrects the photo the user just took out.

---

## 4. The four kinds

### Manual album
The user names it and picks the items. No rule, no index, no ML.

### Favourites
One album, present from first launch, which the user cannot create, rename, or delete. Populated
by a heart on individual items.

Favourites is **system-backed**, not app-owned: it reads and writes `MediaStore`'s own
`IS_FAVORITE` column, so a photo favourited in Easy Gallery shows as a favourite in Google Photos
and other galleries, and the mark survives uninstalling this app. It is the only part of this epic
whose data outlives the app.

That choice costs a four-rung write path, because Android's favourite APIs differ sharply by
version:

| API | Mechanism | User-visible prompt |
|---|---|---|
| 36+ | `markIsFavoriteStatus()` | None — needs only the wide read permission the app already holds |
| 31+ *with* `MANAGE_MEDIA` | silent `createWriteRequest()`, then `update(IS_FAVORITE)` | None |
| 30+ without it | `createFavoriteRequest()` | A system dialog per request |
| 28–29 | — | **Favourites is unavailable** |

Reading is uniform and cheap on 30+: query with `QUERY_ARG_MATCH_FAVORITE` / `MATCH_ONLY`.

Two traps worth stating plainly, because both are easy to get wrong and expensive to discover
late:

- **`MANAGE_MEDIA` does not cover `createFavoriteRequest()`.** Its documented scope is
  `createWriteRequest`, `createTrashRequest`, and `createDeleteRequest` only. The favourite path
  under `MANAGE_MEDIA` goes *through* `createWriteRequest` and then writes the column directly.
  Implementing favourites the obvious way means shipping the permission and still showing the
  dialog.
- **Write grants are not persistable.** They are tied to the Activity's lifecycle and explicitly
  do not support `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`, so the grant must be re-acquired each
  session — silently, but with an async round trip before the first heart of that session fills in.

### Content album
The user types a description — "castles" — and the album collects matching photos. Free text means
open-vocabulary image–text matching (embedding the query and the images into a shared space), not
a fixed-label classifier. ML Kit's image labelling cannot answer arbitrary words.

### People album
The user picks one or more people and the album collects photos of them. Requires face detection
*and* recognition: detection finds faces, embeddings and clustering decide which faces are the same
person. ML Kit detects but does not recognise.

### Region and time album
The user picks an area and/or a date range. Needs no ML — only indexed EXIF location and date — but
is still impossible without the index, for the reason in §6.

---

## 5. Identity: the problem that loses people's albums

An album stores references. What is a reference?

`MediaItem` carries only a `Uri`, built from the `MediaStore` `_ID`. That ID looks durable and is
not: if another app moves a file, or a volume is remounted, or the scanner re-inserts a row, the
same photo returns with a new `_ID` and the album silently loses it. A path breaks on rename. A
content hash is stable but means reading every byte of every file.

Whatever is chosen needs a **healing strategy**: a way to recognise that the item now at a new ID
is the one the album used to hold.

> **Do not copy the ADR-0007 pattern here.** Folder thumbnails resolve a stored URI against live
> media and fall back silently when it does not match. For a thumbnail that is correct. For album
> membership the same code is data loss.

This is ADR-0010 and it is the single highest-risk decision in Stage 1.

---

## 6. The media index

Automatic albums of every kind read from a persistent, per-image index built in the background.

Region albums prove why the index is unavoidable. **ADR-0001** established that EXIF location is
read lazily, per item, only when an info surface is shown — never during a query — because Timeline
View loads every item on the device and eager EXIF reads there are O(n) file opens, minutes on a
large library. A region album needs location for every item. There is no way to build one without
either superseding ADR-0001 or amortising the cost into an index. That is why the index is its own
stage rather than part of each album kind.

### Tiers

| Tier | Holds | Cost | Built |
|---|---|---|---|
| **A** | EXIF location, true date-taken, dimensions | one file open per image, no ML | eagerly |
| **B** | content embeddings | ML inference per image | eagerly |
| **C** | face detections and embeddings | ML inference per image, biometric data | eagerly, behind a disclosure (§8.9) |

Tier A pays for itself outside this epic: it fixes `SortType.DATE_TAKEN`, which today does not read
EXIF at all and silently sorts by `dateAdded`, and it gives the app its first real answer to "what
changed on the device".

### Detecting what changed

The app currently re-reads everything on launch. An incremental indexer needs better, and the
platform provides it:

- **`MediaStore.getVersion()`** — an opaque string. Changed since last run ⇒ assume everything is
  invalid, resync fully.
- **`MediaStore.getGeneration(volume)`** with the per-row `GENERATION_ADDED` / `GENERATION_MODIFIED`
  columns — monotonically increasing. Keep a watermark; only rows above it need work.

Both are more robust than `DATE_ADDED` / `DATE_MODIFIED`, which move when an app calls
`setLastModified()` or the user's clock is wrong. Both are API 30+; 28–29 falls back to a date
watermark and accepts being occasionally wrong.

### Indexing and evaluation are different jobs

This is the distinction that keeps the feature from becoming a battery problem:

| | **Indexing** | **Evaluation** |
|---|---|---|
| Cost | expensive | cheap |
| Scope | per image | per album |
| Frequency | once per image, ever | often |
| Where | WorkManager, constrained | `viewModelScope`, on demand |

**Indexing** runs under WorkManager, constrained to charging, device idle, and battery not low,
with a manual "Index now" in Settings as expedited work. No network constraint — nothing leaves the
device.

**Evaluation** is not background work. It runs on app open and after each index batch:

- Region and time rules are a filter over indexed columns. Compute on read, materialise nothing.
- Content and people rules need vector comparison across the library. Materialise results with a
  generation stamp and re-evaluate only images newer than it.

So "charging and idle" is right for the index and wrong for evaluation — a photo taken this
afternoon should appear in *Castles* when the app opens, not next time the phone is plugged in.

### Be honest about staleness

An automatic album that is quietly incomplete is worse than one that says "still scanning 2,340
photos". Surface last-evaluated state and remaining work. This costs a little UI and buys all of
the user's trust in the feature.

---

## 7. Staging and progress

Each stage is independently shippable. **This table is the single source of truth for where the
epic stands.** It is updated when a stage's exit criteria are met — not when work starts feeling
nearly done.

| Stage | Delivers | Status | Index? | ML? |
|---|---|---|---|---|
| **0** | This document and the Stage 1 ADRs | ✅ **Complete** — 2026-10-07 | — | — |
| **1** | Favourites + manual albums, Albums View, Album Detail View | ⬜ Not started | no | no |
| **2** | The index (Tier A), background pipeline, region/time albums | ⬜ Not started | yes | no |
| **3** | People albums | ⬜ Not started | Tier C | yes |
| **4** | Content albums | ⬜ Not started | Tier B | yes |

Status is one of ⬜ Not started · 🔶 In progress · ✅ Complete. Stages 3 and 4 are each roughly the
size of 0–2 combined.

### Exit criteria

A stage is Complete only when every line holds. Until then it is In progress, however much of it
is written.

**Stage 0** — ✅ met 2026-10-07
- The epic is specified in this document, including the membership model and the known traps.
- Every decision needing the maintainer's approval under `AGENTS.md` has been asked and answered.
- ADRs 0009–0013 are written, `Active`, and indexed in `docs/architecture/README.md`.

**Stage 1**
- The four gradle checks in `AGENTS.md` pass.
- Favourites and manual albums work end to end: create, rename, delete, add, remove.
- Albums View and Album Detail View are reachable from the title dropdown (ADR-0013).
- Membership survives the ADR-0010 healing cases, with tests covering `_ID` change and the
  orphaned-not-deleted path.
- Favourites degrades correctly across all four tiers of ADR-0011, and is absent on API 28–29.
- Every new user-facing string is in all ten locales.
- The album vocabulary has moved from this document into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md) (§8.8).
- `AGENTS.md` and `docs/architecture/ARCHITECTURE.md` no longer claim the app has no database.
- **A documentation audit has passed** — run `/docs-audit`, and fix or record everything it finds.
  Stage completion is the scheduled moment for this, because triggers only catch anticipated
  drift.

Exit criteria for Stages 2–4 are written when that stage is planned, not now; they depend on
decisions the preceding stage will inform. Every stage carries the documentation-audit line.

### Stage log

One line per status change, newest last. This is the history; the table above is the current state.

| Date | Change |
|---|---|
| 2026-10-07 | Stage 0 complete. Design doc written, ADRs 0009–0013 adopted. |

### Who updates this

The **Planner** owns this section, as it owns the rest of this document and the ADRs. Update it in
the same change that satisfies or breaks an exit criterion — a stage moving to In progress when its
first implementation lands, and to Complete when every line of its exit criteria holds. The
Reviewer checks that a change which completes a stage also updated the table and the log.

### Stage 0 output

The five ADRs below are binding on Stage 1.

| ADR | Settles |
|---|---|
| [0009](../architecture/decisions/0009-room-as-first-owned-datastore.md) | Room as the app's first owned datastore |
| [0010](../architecture/decisions/0010-album-membership-dual-key-identity.md) | How a membership identifies a media item, and how it heals |
| [0011](../architecture/decisions/0011-favourites-backed-by-mediastore.md) | The Favourites write path per API level |
| [0012](../architecture/decisions/0012-one-album-type-rule-additions-exclusions.md) | One album type: rule, additions, exclusions |
| [0013](../architecture/decisions/0013-albums-as-third-display-mode.md) | Albums as a third display mode; the navigation chain stays |

---

## 8. Decisions already taken

Recorded here so they are not relitigated. Those with lasting technical weight become ADRs.

1. **Albums are virtual.** References only; no file is moved or copied.
2. **Favourites is system-backed** via `MediaStore.IS_FAVORITE`, not an app-owned list.
3. **Favourites is unavailable on API 28–29.** No app-owned fallback store will be built for those
   versions; a parallel implementation is not worth maintaining for legacy hardware.
4. **Automatic albums are hand-correctable**, which collapses manual and automatic into one type
   with three sets (§3).
5. **Automatic albums are live**, re-evaluated as the library changes, on the split schedule in §6.
6. **The index is built eagerly**, not only when a feature needs it. All three tiers behave the
   same way; there is no per-feature gating of indexing work.
7. **`MANAGE_MEDIA` is optional and late-asked.** Declared, requested contextually after the user
   has felt the system prompts, never a precondition. Every feature works without it.
8. **Vocabulary lives here during Stage 0–1**, not in
   [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).
   That file is injected into agents' context when they edit UI code, so describing screens that do
   not exist would actively mislead. Migrating the album vocabulary into it is part of Stage 1's
   definition of done.
9. **Face clustering runs eagerly, behind a disclosure — not behind an opt-in gate.** The decisive
   argument is product, not legal: the people picker shows one face per person, which means
   clustering must already have run across the library. Gate it lazily and the feature's entry
   point becomes an empty screen and a progress bar. The obligations that come with that:
   - A **one-time, plain-language disclosure** when indexing first starts — not buried in a privacy
     policy. It must say that faces are grouped on the device, that nothing leaves the phone, and
     where the off switch is.
   - A Settings switch that **deletes the face data**, not merely stops adding to it.
   - Face data never leaves the device, never enters a backup, never appears in a log.

   Note on the reasoning: an earlier draft argued this was a GDPR requirement. That was overstated.
   For an app with no telemetry the developer is likely not a data controller at all, and the
   user's own library falls under the household exemption. The real checkable question is whether
   Play's Data Safety form treats on-device-only inference as "collection" — it generally does not,
   but confirm it before Stage 3 rather than assuming.

---

## 9. Consequences for the existing app

Things this epic breaks or strains, which should surprise nobody when they come up:

- **`AGENTS.md` and `ARCHITECTURE.md` both say the app has no database.** False from Stage 1.
- **Navigation.** `MainActivity`'s `if / else if` chain is five branches with no back stack.
  Albums adds at least two destinations. Stage 1 is the moment to decide whether the chain retires,
  rather than discovering it in Stage 3.
- **`GalleryViewModel` is the largest file in the app.** None of this belongs in it. Albums get
  their own feature-scoped ViewModel, following the `CreateFolderViewModel` precedent.
- **Play policy.** `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` are now policy-restricted to declared
  use cases, and `MANAGE_MEDIA` is special access. The declaration should be confirmed current
  *before* Stage 1, not mid-epic.
- **ADR-0001** is extended, not superseded: lazy per-item location reads remain correct for the
  info surfaces; the index becomes a second, amortised path.

---

## 10. Open questions

1. **Does an album need a cover?** Folders resolve one automatically with an override (ADR-0007).
   Albums could reuse the idea, pick the newest member, or let the user choose.
2. **What happens to an index entry when a photo is deleted?** `queryDeletedFiles` exists but is
   API 37, far too new. Reconciliation during scan is the realistic answer.
3. **Progress notification during indexing?** Needs `POST_NOTIFICATIONS` on API 33+. Avoidable if
   progress lives only in-app, which is the Stage 2 default unless indexing proves slow enough to
   need it.
4. **Lowest device that must run Stage 3–4 ML**, which decides model size and quantisation.
5. **Can albums be reordered, nested, or shared?** Assumed no for now; flat, unordered, local.

---

## 11. Non-goals

- Albums do not move, copy, rename, or delete media.
- No cloud, no account, no sync. The tip jar remains the app's only network-touching feature.
- No cloud inference. All ML is on-device or it does not ship.
- Albums are not exported to other apps. Favourites is the sole interoperable surface, and it is
  interoperable because the platform owns the column, not because this app publishes anything.
