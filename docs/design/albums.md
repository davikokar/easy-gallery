# Albums — epic design

> **Status: Stage 1 complete; Stage 2 not started.** Current state of every stage is in
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

Before this epic, Easy Gallery owned nothing. Media came live from `MediaStore`, the only things
persisted were five small `SharedPreferences` files, and there was no per-media-item state
anywhere in the app.

Albums are the first data the app owns. That single change is larger than the album UI, and it is
what makes this an epic rather than a feature. Stage 1 delivered it: a Room database, a membership
model that heals, and a backup posture — ADRs 0009, 0010, and 0014.

The second change is almost as large, and is still ahead: automatic albums require a **media
index** — per-image facts computed in the background — and the app has no background infrastructure
whatsoever. No WorkManager, no Service, no `ContentObserver`. It re-queries `MediaStore` only when
the user does something.

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
| Survives uninstall | yes — the platform owns the files | the database is deleted, but auto-backup restores it on reinstall (ADR-0014) |

**Favourites is the third case, and belongs to neither column.** The mark lives in the platform's
own `MediaStore.IS_FAVORITE` column (§4), so it survives uninstall outright — with no backup
involved, and visible to other gallery apps while this one is not installed at all.

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
| 36+, **or** 30+ with R extension 16+ | `markIsFavoriteStatus()` | None — needs only the wide read permission the app already holds |
| 31+ *with* `MANAGE_MEDIA` | silent `createWriteRequest()`, then `update(IS_FAVORITE)` | None |
| 30+ without it | `createFavoriteRequest()` | A system dialog per request |
| 28–29 | — | **Favourites is unavailable** |

The first rung is wider than the obvious reading of the documentation: `markIsFavoriteStatus()`
ships in the media mainline module, so it exists on API 30–35 devices carrying R extension 16 or
later. ADR-0011's Amendment 1 records it, and the general rule — check `api-versions.xml` for an
`sdks=` attribute before trusting a `MediaStore` API's stated level.

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
| **1** | Favourites + manual albums, Albums View, Album Detail View | ✅ **Complete** — 2026-10-07 | no | no |
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

**Stage 1** — ✅ met 2026-10-07
- The checks in `AGENTS.md`'s definition of done pass.
- Favourites and manual albums work end to end: create, rename, delete, add, remove.
- Albums View and Album Detail View are reachable from the title dropdown (ADR-0013).
- Membership survives the ADR-0010 healing cases, with tests covering `_ID` change and the
  orphaned-not-deleted path.
- **An album whose members are all orphaned reads as empty and waiting, never as a bug and never as
  data loss**, in Albums View and in Album Detail View alike. This is the ADR-0014 restore case and
  the removed-SD-card case, and ADR-0015 inherits it for covers: such an album has no member to
  fall back to. `album_hidden_items_notice` already exists in all ten locales for it.
- Favourites degrades correctly across all four tiers of ADR-0011, and is absent on API 28–29.
- Every new user-facing string is in all ten locales.
- The album vocabulary has moved from this document into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md), under
  its *Albums View* and *Album Detail View* sections.
- `AGENTS.md` and `docs/architecture/ARCHITECTURE.md` no longer claim the app has no database.
- **A documentation audit has passed** — run `/docs-audit`, and fix or record everything it finds.
  Stage completion is the scheduled moment for this, because triggers only catch anticipated
  drift. Run on 2026-10-07; the findings and what was done with each are below.

> **What "met" means for Stage 1, honestly stated.** Almost everything above was checked against
> the repository rather than against a report. Three limits applied at the moment this stage was
> marked Complete. One has since been lifted: the end-to-end criterion was met at the level of
> code paths plus unit and compile-verified instrumented coverage, because **the instrumented
> suite had never been run** — it was believed at the time that no device could be attached to
> this machine, which was false. It has since run on a Samsung SM-G990B (Android 16, API 36),
> 49 tests passing, and that run found three bad tests and no product defect; see the stage log.
> The second limit stands: **the four ADR-0011 tiers cannot all be exercised**, because tier
> selection is pure and unit-tested but the write paths below API 36 need real devices at those
> levels, and only one device is available. The third is narrower and also stands: **the
> gradle-checks line is the one criterion carried on the word of the run that produced it**, not
> re-executed by the change that marked this stage Complete. Stage 2 can assume instrumented
> tests are runnable; it should still check `adb devices` rather than assume a device is there.

Exit criteria for Stages 2–4 are written when that stage is planned, not now; they depend on
decisions the preceding stage will inform. Every stage carries the documentation-audit line.

### Documentation audit

Run 2026-10-07 against [docs-audit.prompt.md](../../.github/prompts/docs-audit.prompt.md) to
discharge Stage 1's last exit criterion. Recorded here rather than in a throwaway report, because
the routed findings outlive this stage.

**Verified clean.** The ADR index in [../architecture/README.md](../architecture/README.md) lists
every file present in [../architecture/decisions/](../architecture/decisions/), all `Active`, none
missing or orphaned. [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §3's `data/` table matches
the directory file for file; its §4 navigation chain matches `MainActivity` branch for branch,
including both album branches; its §6 preference-store table and `AGENTS.md`'s copy both match the
five `PREFS_NAME`/`PREFS_FILE` constants in `data/` and `LocaleHelper`. Every composable and type
named in [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md)
resolves in `app/src/main`, and its `applyTo` glob covers `ui/` and `MainActivity.kt` as claimed.
Ten locale files exist, `album_hidden_items_notice` is in all ten, and `cd_toggle_display_mode` is
gone from all ten. Every relative link in this document resolves. Nothing in `docs/` or `.github/`
is missing from the documentation map, and nothing in the map is missing from disk.

**Fixed here.** This document's §9 said `AGENTS.md` and `ARCHITECTURE.md` "both say the app has no
database" and that Stage 1 was the moment to decide the navigation question — both were overtaken
by events. §9 also restated the branch count of `MainActivity`'s chain, which is a fact the code
owns. A cross-reference pointed at "§8.8" of the UI vocabulary, which has no numbered sections.

**Routed to the Planner — the worst finding of this audit.** All four agent definitions
(`.github/agents/coder.agent.md`, `orchestrator.agent.md`, `planner.agent.md`,
`reviewer.agent.md`) still tell the agent that the app has **no database**. That is false from
Stage 1 and it is worse than ordinary drift, because those files are injected into an agent's
context before it reads anything else — the correction landed in `AGENTS.md` and
`ARCHITECTURE.md` and stopped one file short. ADR-0009 anticipated exactly this correction and
named only those two documents, which is how the agent files were missed.

> **Closed 2026-10-07.** Three of the four were corrected on the audit day;
> `orchestrator.agent.md` was missed because it was not in that change's file list and has now
> been fixed. The same follow-up corrected a second claim that had spread the same way — see the
> stage log entry for the first connected instrumented run.

**Also routed to the Planner, as a nit.** `AGENTS.md` tells the reader that every version lives in
`gradle/libs.versions.toml` and `app/build.gradle.kts` and then restates minSdk, targetSdk, and
compileSdk a few lines later. All three are correct today; the duplication is the risk.

**Confirmed still true, already recorded.** The four debt items
[ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §11 carries from the Stage 1 review were
re-checked against the code and all still hold: the forced Kotlin 2.1.0 runtime artifacts,
`DisplayPreferencesState`'s KDoc naming three preference scopes where there are five,
`AlbumListItem` naming both a composable and a data class, and `support_email_subject` /
`error_no_email_app` existing only in `values/`.

### Stage log

One line per status change, newest last. This is the history; the table above is the current state.

| Date | Change |
|---|---|
| 2026-10-07 | Stage 0 complete. Design doc written, ADRs 0009–0013 adopted. |
| 2026-10-07 | Stage 1 started. ADR-0010 amended (the stored key is the content URI, not the `_ID`); ADR-0014 and ADR-0015 adopted. |
| 2026-10-07 | Stage 1 checkpoint. Room layer, membership resolver, `AlbumStore`, Favourites data layer and `FavouritesViewModel` landed; four gradle checks green, 214 unit tests at that point. |
| 2026-10-07 | Stage 1 UI landed — `AlbumsViewModel`, Albums View, Album Detail View, the title-dropdown switcher, the album dialogs, and Favourite / Add to album on the media surfaces. Album vocabulary migrated into `ui-vocabulary.instructions.md`, closing decision 8. ADR-0011 amended (tier 1 also applies below API 36, via R extension 16). **Stage stayed In progress**: a fully orphaned album did not yet read as empty-and-waiting in Albums View, and two review findings were open. |
| 2026-10-07 | **Stage 1 complete.** The Albums View orphan gap closed — `album_hidden_items_notice` now renders on the Album tile and the Album row as well as in Album Detail View — the two review findings closed, and the four gradle checks re-run green. The documentation audit was run; its result and the findings it routed elsewhere are recorded under *Documentation audit* above. |
| 2026-10-07 | **First connected instrumented run, after Stage 1 closed.** `connectedDebugAndroidTest` executed for the first time in this repository, on a Samsung SM-G990B (Android 16, API 36): **49 tests, 49 passing** once three bad tests were fixed. All three were test defects, not product defects — a Compose matcher that could not match in the unmerged tree, an assertion that ignored a deliberate duplicate label, and an assertion on an item below the fold. The run also forced a rename of six backticked test method names in `AlbumDetailContentTest`, which R8/D8 refuses to dex. The repository-wide claim that no device could be attached here was stale and has been corrected in `AGENTS.md`, the five agent definitions, `tests.instructions.md`, and this document. |

### Who updates this

The **Planner** owns this section, as it owns the rest of this document and the ADRs. Update it in
the same change that satisfies or breaks an exit criterion — a stage moving to In progress when its
first implementation lands, and to Complete when every line of its exit criteria holds. The
Reviewer checks that a change which completes a stage also updated the table and the log.

### ADRs binding on Stage 1

The first five were Stage 0's output. ADR-0014 and ADR-0015 were adopted at the start of Stage 1, as
was ADR-0010's Amendment 1 — read ADR-0010 to its end, because the amendment corrects what a
membership actually stores. ADR-0011 gained an Amendment 1 later in Stage 1, widening tier 1; read
that record to its end too.

| ADR | Settles |
|---|---|
| [0009](../architecture/decisions/0009-room-as-first-owned-datastore.md) | Room as the app's first owned datastore |
| [0010](../architecture/decisions/0010-album-membership-dual-key-identity.md) | How a membership identifies a media item, and how it heals |
| [0011](../architecture/decisions/0011-favourites-backed-by-mediastore.md) | The Favourites write path per API level |
| [0012](../architecture/decisions/0012-one-album-type-rule-additions-exclusions.md) | One album type: rule, additions, exclusions |
| [0013](../architecture/decisions/0013-albums-as-third-display-mode.md) | Albums as a third display mode; the navigation chain stays |
| [0014](../architecture/decisions/0014-album-database-included-in-auto-backup.md) | That the album database is backed up, and why a restore is survivable |
| [0015](../architecture/decisions/0015-album-cover-persisted-random-member.md) | That an album has a persisted cover, picked at random once |

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
8. **Vocabulary lived here during Stage 0–1**, not in
   [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).
   That file is injected into agents' context when they edit UI code, so describing screens that do
   not exist would actively mislead. **Done — the album vocabulary migrated into that file when the
   Albums View and Album Detail View code landed (2026-10-07).** The reasoning is kept because it
   applies again at every later stage: a stage's vocabulary belongs in this document until the
   screens it names exist, and moves out in the same change that builds them.
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

10. **The album database is included in Android auto-backup** (ADR-0014). `allowBackup` is already
    true and the backup rules are still the generated samples, so inclusion was going to happen by
    default; what was missing was the reasoning. Albums are the only thing in this app that cannot
    be recomputed, so losing them with the device is the one outcome worth accepting some risk to
    avoid. The non-obvious consequence is that a restored database arrives where every `MediaStore`
    key differs, so ADR-0010's healing runs across the whole library at once — survivable precisely
    because the healing key describes the file rather than its row, and because orphans are never
    deleted. The cost is accepted honestly: more chances to mis-attach on duplicates, a healing key
    that a rewriting restore can break, and the obligation that a fully orphaned album must read as
    "waiting" rather than as a bug. The §2 table row on surviving uninstall has been corrected to
    match: the database is deleted on uninstall and restored on reinstall when device backup is on.

11. **An album has a cover: one of its members, picked at random once and then persisted**
    (ADR-0015). This answers what was open question 1. Random means random *at the moment the album
    first gains a member* — never per render, which would change the cover on every open and read
    as a bug. An empty album has no cover. What is stored is a foreign key to the album's own
    membership row for that item, **not** a media URI, so the cover heals whenever the membership
    heals and survives an ADR-0014 restore for free; removing the cover item from the album nulls
    the reference and re-rolls. If the membership is merely orphaned, another member is shown **for
    display only**, without clearing the stored reference, because that value may be a choice the
    user made and the usual cause of a failure is transient. Stage 1 ships the column, **not** the
    picker; the affordance is deferred, the storage is not, so adding it later needs no migration.
    This deliberately diverges from ADR-0007's automatic-with-override folder thumbnails, for the
    reason set out in ADR-0015.

---

## 9. Consequences for the existing app

Things this epic breaks or strains, which should surprise nobody when they come up:

- **"The app has no database" was written in several places.** False from Stage 1. `AGENTS.md`
  and `ARCHITECTURE.md` were corrected when Room landed; the `.github/agents/*.agent.md`
  definitions were corrected afterwards — see *Documentation audit* in §7, where the finding is
  recorded and closed.
- **Navigation.** `MainActivity`'s `if / else if` chain has no back stack, and albums added two
  destinations to it. Decided in Stage 1: the chain stays, by
  [ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md). Stage 3 inherits
  that decision rather than reopening it.
- **`GalleryViewModel` is the largest file in the app.** None of this belongs in it. Albums get
  their own feature-scoped ViewModel, following the `CreateFolderViewModel` precedent.
- **Play policy.** `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` are now policy-restricted to declared
  use cases, and `MANAGE_MEDIA` is special access, which Stage 1 shipped. Stage 1 was meant to
  confirm the Play Console declaration before shipping and **no record exists that it was done** —
  carry it into Stage 2 rather than assuming it.
- **ADR-0001** is extended, not superseded: lazy per-item location reads remain correct for the
  info surfaces; the index becomes a second, amortised path.

---

## 10. Open questions

1. **What happens to an index entry when a photo is deleted?** `queryDeletedFiles` exists but is
   API 37, far too new. Reconciliation during scan is the realistic answer.
2. **Progress notification during indexing?** Needs `POST_NOTIFICATIONS` on API 33+. Avoidable if
   progress lives only in-app, which is the Stage 2 default unless indexing proves slow enough to
   need it.
3. **Lowest device that must run Stage 3–4 ML**, which decides model size and quantisation.
4. **Can albums be reordered, nested, or shared?** Assumed no for now; flat, unordered, local.

---

## 11. Non-goals

- Albums do not move, copy, rename, or delete media.
- No cloud, no account, no sync. The tip jar remains the app's only network-touching feature.
- No cloud inference. All ML is on-device or it does not ship.
- Albums are not exported to other apps. Favourites is the sole interoperable surface, and it is
  interoperable because the platform owns the column, not because this app publishes anything.
