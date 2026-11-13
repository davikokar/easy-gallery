# Albums — epic design

> **Status: Stages 1 and 2 complete; Stage 4 (content) is planned and runs before Stage 3
> (people).** Current state of every stage is in [§7](#7-staging-and-progress), which is the single
> source of truth for where this epic stands. Stage 4's plan is [albums-stage-4.md](albums-stage-4.md);
> Stage 2's is archived at [albums-stage-2.md](albums-stage-2.md), and its exit criteria and their
> record are in §7.
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

The second change was almost as large, and Stage 2 delivered it: automatic albums require a **media
index** — per-image facts computed in the background — and the app had no background infrastructure
whatsoever. No WorkManager, no Service, no `ContentObserver`. It re-queried `MediaStore` only when
the user did something. The app now has a constrained WorkManager job, a Tier A index (§6) and
region and time albums built on it; Tiers B and C are still ahead.

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

> **How the user sees them — §8 decision 28, 2026-11-13.** The user sees two kinds of album,
> Manual and Automatic, and Favourites. Content, people, region and time are **not** separate album
> kinds to the user: they are **criteria** of one automatic album, mixed freely on the **New
> automatic album** screen. "Castles in Italy, 2024" is content + place + date; "Silvia and Davide
> in France, 2025" is people + place + date. Every chosen criterion must hold — ADR-0017's
> conjunction. The subsections below keep their historical names because other documents cite
> them; read *Content album*, *People album* and *Region and time album* as the three families of
> criterion an automatic album can carry.

### Manual album
The user names it and picks the items. No rule, no index, no ML.

Creation is a typed choice, not a name prompt: the **Album type dialog** offers Manual and
Automatic, and Manual opens a screen that picks the members with the name pre-filled and editable
above them. Nothing is written until Create, so cancelling leaves no empty album.
[ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md).
Automatic was shown disabled and labelled through Stage 1; Stage 2 step 2.7 enables it, and it
opens a screen of its own, **New automatic album**, which picks a place and a date range rather than
items — the same ADR's Amendment 2.

That screen picks by **browsing, not by searching**. It opens on the same folders the user
already knows from Folders View, in the order and layout that view uses — but with neither its
search query nor its media-type filter applied, so a folder the user has filtered out of browsing
is still there to pick from. Opening one shows its media arranged the way that folder is
already arranged in Folder Detail View, and that is the only level where items are chosen.
Selection carries across folders, and a floating tick button commits the album. The reasoning,
and what it costs for an album whose members genuinely span many folders, is in Amendment 1 of
the same ADR; the Corrections below it record which inherited filters were removed and why.

The UI therefore shows two kinds where storage has one (§3). That divergence is deliberate: "pick
the photos" and "describe what you want" are different questions to ask a person, even though the
answer is stored as the same row with or without a rule.

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

**Free text only** (§8 decision 29): the user types the description, and no keywords are suggested.
It is the one criterion that is typed rather than picked. It combines with place and date, and
later with people. Planned as Stage 4 in [albums-stage-4.md](albums-stage-4.md), which carries the
model, runtime and matching questions.

### People album
The user picks one or more people and the album collects photos of them. Requires face detection
*and* recognition: detection finds faces, embeddings and clustering decide which faces are the same
person. ML Kit detects but does not recognise.

**Picking several people means all of them are in the photo** (§8 decision 30). "Silvia or Davide"
is not expressible. How a person gets a name, and where that name lives when face data is excluded
from backup and the albums are not, is open — §10 question 12.

### Region and time album
The user picks an area and/or a date range. Needs no ML — only indexed EXIF location and date — but
is still impossible without the index, for the reason in §6. **Shipped in Stage 2.** Both are picked
from what the index actually found, never typed (§8 decision 16), and a rule may combine them. In
Stage 2 the rule is set at creation and cannot be edited (§8 decision 26); Stage 4 adds editing
(§8 decision 31).

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

The index lives in its **own** Room database, separate from the album database and excluded from
backup, and keeps captured EXIF facts in different columns from the place codes derived from them —
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md), which is binding on
everything in this section.

### Detecting what changed

The app currently re-reads everything on launch. An incremental indexer needs better, and the
platform provides it:

- **`MediaStore.getVersion()`** — an opaque string, and a **per-volume** one. Changed since last
  run ⇒ assume that volume's indexed data is invalid and resync it. Resyncing the *whole* index on
  any version change would discard rows for volumes that are merely absent, which is what
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md)
  clause 4 exists to prevent; that clause is binding here.
- **`MediaStore.getGeneration(volume)`** with the per-row `GENERATION_ADDED` / `GENERATION_MODIFIED`
  columns — monotonically increasing. Keep a watermark; only rows above it need work.

**That watermark lives in the index database, keyed by volume name** —
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 1, which
is binding here. So the index holds two kinds of thing: per-item facts, and the bookkeeping that
decides which items to look at next. Both are recomputable and neither is backed up, which is the
whole of the argument for keeping them together.

Both are more robust than `DATE_ADDED` / `DATE_MODIFIED`, which move when an app calls
`setLastModified()` or the user's clock is wrong. They do not arrive at the same API level, and the
fallback is narrower than "no delta detection below 30" because of it. `getGeneration` is API 30, so
on 28–29 the per-row delta falls back to a date watermark and accepts being occasionally wrong. A
version string is available throughout — `getVersion(Context)` since API 12, the per-volume
`getVersion(Context, String)` since API 29 — so the coarse "this volume's indexed data is invalid"
signal survives that fallback, narrowing on 28 to the primary volume alone.

### Indexing and evaluation are different jobs

This is the distinction that keeps the feature from becoming a battery problem:

| | **Indexing** | **Evaluation** |
|---|---|---|
| Cost | expensive | cheap |
| Scope | per image | per album |
| Frequency | once per image, ever | often |
| Where | WorkManager, constrained | `viewModelScope`, on demand |

**Indexing** runs under WorkManager, constrained to charging, device idle, and battery not low. No
network constraint — nothing leaves the device. A manual **"Index now"** — in Settings, and in the
New automatic album screen's pickers while some media is not yet indexed (§8 decision 27) — runs the same
batch on demand, and runs it *expedited* **only on the API levels where the platform grants that
without a foreground service and a notification**; below them it runs as ordinary one-time work, and
the worker never runs as a foreground service. That was a holding position while §10 question 2 was
open; it is now decided, because indexing shows no notification at all (§8 decision 23).
Which level that is, and why the two requests therefore carry deliberately different constraint
sets, are owned by [../architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §11 and not
restated here. **This paragraph used to say "as expedited work" flatly**, which overstated what
step 2.4 shipped.

**Evaluation** is not background work. It runs on app open, **once the first read of live media has
finished** — until then an automatic album reports that it is waiting, not that it is complete —
and after each index batch. **The gate is `GalleryViewModel`'s media-loaded flag, not the emptiness
of the media list**: the list starts empty before `MediaStore` has been read, an empty list cannot be
told from an empty library, and forwarding it made every rule album read as complete and empty on
every cold start until Stage 2 step 2.5's remediation added the flag. Where the gate sits is owned
by [../architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §3. After a batch, evaluation
is **driven by Room invalidation** of the one in-process index database that the indexing
worker and the ViewModel share, with no WorkManager involvement (invalidation fires on each write
the batch commits, so evaluation can also run during a batch):

- Region and time rules are a filter over indexed columns. Compute on read, materialise nothing.
- Content and people rules need vector comparison across the library. Materialise results with a
  generation stamp and re-evaluate only images newer than it.

So "charging and idle" is right for the index and wrong for evaluation. ~~A photo taken this
afternoon should appear in *Castles* when the app opens, not next time the phone is plugged in.~~
**That promise was struck on 2026-11-10** (§8 decision 22, answering §10 question 7), because
nothing delivers it. What holds is this: evaluation is cheap and runs on app open, but an item
matches a rule only once the index has reached it, and the index reaches a new photo at the next
index pass — the periodic constrained job, or "Index now". Until then the album's Incomplete-album
notice says it is incomplete and points at indexing; that notice is the bridge. A new photo appears
after the next index pass, not when the app opens.

### Be honest about staleness

An automatic album that is quietly incomplete is worse than one that says "still scanning 2,340
photos". Surface last-evaluated state and remaining work. This costs a little UI and buys all of
the user's trust in the feature.

**Whatever that surface counts, it must say what it is counting.** The index keeps a deleted item's
row until the next scan, so it over-counts the library the user can actually see; a figure for
"indexed", "covered", or "left to do" that does not distinguish index rows from live items will
disagree with the gallery and look like a bug. ADR-0020 records why the over-count exists; this is
the section that owns the surface which has to be honest about it.

Staleness cuts both ways, and the second direction used to be the one that got missed. It no longer
exists. An item **deleted** from the device does keep its index row until the next constrained run,
which may be days away — but an automatic album's displayed contents are not rendered from the
index. The index supplies the matching keys; those keys are resolved against what `MediaStore`
currently returns, exactly as album memberships already are, so a deleted photo drops out on the
next media read rather than at the next scan.
[ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md) is
binding here and states what that costs. The staleness that remains is therefore the **addition**
direction only, which the paragraph above covers: a photo that has not been indexed yet cannot
match a rule yet, and the user is told so.

**A third state exists and it is not staleness at all.** An item can be fully indexed and still
carry no value for the column a rule tests — the index stores an absent fact as absent rather than
substituting an approximation, so the row will never change and waiting will not help it. Such an
item must not be counted as outstanding work, because that count would never reach zero, and must
not be counted as covered either, because the album that tests the column will not contain it. The
missing-GPS case is already visible as ADR-0019's coverage notice; Stage 2 step 2.3 found the same
shape for date-taken, where a photo with no EXIF date at all gets a null rather than a `dateModified`
fallback, and Tiers B and C will meet it again whenever inference declines to produce a result.

---

## 7. Staging and progress

Each stage is independently shippable. **This table is the single source of truth for where the
epic stands.** It changes when a stage's status changes, and *Who updates this* at the end of this
section defines when that is: In progress when the stage's first deliverable lands, Complete only
when every exit criterion holds — not when work starts feeling nearly done.

| Stage | Delivers | Status | Index? | ML? |
|---|---|---|---|---|
| **0** | This document and the Stage 1 ADRs | ✅ **Complete** — 2026-10-07 | — | — |
| **1** | Favourites + manual albums, Albums View, Album Detail View | ✅ **Complete** — 2026-10-07 | no | no |
| **2** | The index (Tier A), background pipeline, region/time albums — plan archived at [albums-stage-2.md](albums-stage-2.md) | ✅ **Complete** — 2026-11-12 | yes | no |
| **4** | Content albums (Tier B) and rule editing — **runs before Stage 3**; planned in [albums-stage-4.md](albums-stage-4.md) | ⬜ Not started — plan drafted 2026-11-13 | Tier B | yes |
| **3** | People albums — after Stage 4 | ⬜ Not started | Tier C | yes |

Stage 4's step 4.0, a feasibility spike that ships no code, started 2026-11-13; the stage moves to
In progress when a binding deliverable lands (step 4.1's ADRs or later), per *Who updates this*.

Status is one of ⬜ Not started · 🔶 In progress · ✅ Complete. Stages 3 and 4 are each roughly the
size of 0–2 combined. **The rows are in execution order, and the numbers were deliberately not
swapped**: ADRs 0009, 0013, 0014, 0016, 0017, 0018 and 0020 say "Stage 3" for people and "Stage 4"
for content, and an ADR is never edited to follow a renumbering. §8 decision 13's amendment says
why content goes first.

### Exit criteria

A stage is Complete only when every line holds — however much of it is written, and however close
it feels. What a stage that is not yet Complete *is* depends on whether it has started: Not started
until its first deliverable lands, In progress from that moment onwards, per *Who updates this*
below.

**Stage 0** — ✅ met 2026-10-07
- The epic is specified in this document, including the membership model and the known traps.
- Every decision needing the maintainer's approval under `AGENTS.md` has been asked and answered.
- ADRs 0009–0013 are written, `Active`, and indexed in `docs/architecture/README.md`.

**Stage 1** — ✅ met 2026-10-07
- The checks in `AGENTS.md`'s definition of done pass.
- Favourites and manual albums work end to end: create, rename, delete, add, remove.
- Albums View and Album Detail View are reachable from the title dropdown (ADR-0013).
  > **Note 2026-11-11.** True when this stage was marked Complete, and left as written because it
  > records what was met. The title dropdown no longer exists: ADR-0013 Amendment 2 replaced it
  > with the **Main view bar**, and Albums View is now reached from its Albums tab, Album Detail
  > View from Albums View as before. Not a reopening of Stage 1.
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

**Stage 2** — ✅ declared complete by the maintainer 2026-11-12. The criteria moved here from
[albums-stage-2.md](albums-stage-2.md) when that plan was archived; the step-by-step record of
how each was met stays in that document. Steps 2.0–2.7 are all ✅.
- The checks in `AGENTS.md`'s definition of done pass. *Re-run 2026-11-13 over `2b23e16`, the tree
  after every post-2.7 commit, with documentation-only edits on top. `adb devices` listed the
  SM-G990B (API 36). Results: `compileDebugKotlin` passed; 797 unit tests passed with no failures
  or skips; `compileDebugAndroidTestKotlin` passed; and `connectedDebugAndroidTest` passed 224 of
  224.*
- Every step is ✅ with its own exit criteria met. *See the plan's step table.*
- The index survives the non-happy-path cases. *No GPS: the coverage notices and the resolver's
  "no coordinates" settled no-match (2.6, 2.7). Permission denied:
  `MediaIndexingWorkerTest` — a missing media permission fails the run before any indexing starts,
  so no watermark advances. Mid-scan cancellation: the same test class — a cancelled worker marks
  the run cancelled, not failed. Never indexed: the waiting notice (2.5) and the Settings Media
  index figures.*
- An automatic album with an unevaluatable rule reads as explained-and-waiting, never as empty and
  never as a bug, in Albums View and Album Detail View. *Compose-tested in both
  (`AlbumDetailContentTest`, `FolderListContentTest`). **Not seen in the installed app**: only a
  rule this build cannot read produces one, so it takes written data. Accepted as sufficient
  because the state is reachable only by data.*
- Region-album and date coverage is visible to the user. *Seen in the installed app at 2.7 —
  §8 decision 25.*
- The index database is excluded from backup and the album database is still included. *Checked
  2026-11-12 against `backup_rules.xml` and both sections of `data_extraction_rules.xml`: the
  index database with its `-wal` and `-shm` files and `media_index_run_state.xml` are excluded in
  all three places, and no `<include>` exists.*
- Both Room schemas are exported and committed. *`git ls-files app/schemas` lists the album
  database's `1.json` and the index database's `1.json` to `4.json`, matching the directory.*
- Every new user-facing string is in all ten locales. *Checked at 2.7; not recounted here.*
- Stage 2's vocabulary has moved into the UI vocabulary instructions. *Done at 2.7.*
- `ARCHITECTURE.md` describes everything that triggered it. *Discharged at 2.7's phase 6.*
- **The Play Console restricted-permission declaration is confirmed done.** *Not verifiable from
  the repository and not recorded as done by this change. It is the maintainer's own action in the
  console, and must describe the **merged** manifest's final permission list — §10 question 6
  and §9. Carried, not met.*
- A documentation audit has passed. *Run 2026-11-13 against
  [docs-audit.prompt.md](../../.github/prompts/docs-audit.prompt.md). It found two hard
  findings, both fixed in the same change:
  - `AGENTS.md` said WorkManager adds four permissions to the merged manifest, but the source
    manifest removes one of them. The count was deleted.
  - `coder.agent.md` said "three main views", where `ARCHITECTURE.md` says five. The count was
    deleted.

  It also found two nits in `ARCHITECTURE.md` §3, both fixed: `SortOptions` was listed as a
  dialog, and `FavouriteIndicator` was missing. Not covered by this pass: `tests.instructions.md`
  against the test code, and Stage 1's criteria one by one.*

> **What "complete" means for Stage 2, honestly stated.** The maintainer declared the stage
> finished on 2026-11-12 with three criteria carried. Two were discharged on 2026-11-13: the
> definition of done and the documentation audit, both recorded above. **One is still carried:
> the Play Console declaration.** No agent can make or verify it. It is owed before any release
> that ships Stage 2, and it is met when the maintainer records the outcome here. Until then, this
> stage departs from the rule that a stage is Complete only when every line holds, and the
> departure is stated rather than hidden.

**Stage 4** — ⬜ not started. Its exit criteria are **drafted** in
[albums-stage-4.md](albums-stage-4.md) and become binding when its step 4.1 confirms them; they move
here when that plan is archived.

**Stage 3** — ⬜ not started. Exit criteria are written when it is planned, after Stage 4; they
depend on decisions Stage 4 will inform. Every stage carries the documentation-audit line.

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

> **A step landing inside an already-in-progress stage takes no line, and that absence is the rule
> rather than an oversight.** The log moves when a stage's *status* moves; a stage that is already
> 🔶 does not change status because one of its steps finished, so Stage 2 shows a single entry
> while several of its steps are done. Those steps' record lives in
> [albums-stage-2.md](albums-stage-2.md) until that document is archived, at which point it is
> folded in here. Recorded explicitly because a missing line is indistinguishable from a forgotten
> one, and this judgement had to be re-made and re-explained each time a Stage 2 step closed. The
> entries above reading "Stage 1 stays Complete" are the other case: work landing against a
> **closed** stage, where what is worth recording is that its exit criteria were re-checked and
> held.

| Date | Change |
|---|---|
| 2026-10-07 | Stage 0 complete. Design doc written, ADRs 0009–0013 adopted. |
| 2026-10-07 | Stage 1 started. ADR-0010 amended (the stored key is the content URI, not the `_ID`); ADR-0014 and ADR-0015 adopted. |
| 2026-10-07 | Stage 1 checkpoint. Room layer, membership resolver, `AlbumStore`, Favourites data layer and `FavouritesViewModel` landed; four gradle checks green, 214 unit tests at that point. |
| 2026-10-07 | Stage 1 UI landed — `AlbumsViewModel`, Albums View, Album Detail View, the title-dropdown switcher, the album dialogs, and Favourite / Add to album on the media surfaces. Album vocabulary migrated into `ui-vocabulary.instructions.md`, closing decision 8. ADR-0011 amended (tier 1 also applies below API 36, via R extension 16). **Stage stayed In progress**: a fully orphaned album did not yet read as empty-and-waiting in Albums View, and two review findings were open. |
| 2026-10-07 | **Stage 1 complete.** The Albums View orphan gap closed — `album_hidden_items_notice` now renders on the Album tile and the Album row as well as in Album Detail View — the two review findings closed, and the four gradle checks re-run green. The documentation audit was run; its result and the findings it routed elsewhere are recorded under *Documentation audit* above. |
| 2026-10-07 | **First connected instrumented run, after Stage 1 closed.** `connectedDebugAndroidTest` executed for the first time in this repository, on a Samsung SM-G990B (Android 16, API 36): **49 tests, 49 passing** once three bad tests were fixed. All three were test defects, not product defects — a Compose matcher that could not match in the unmerged tree, an assertion that ignored a deliberate duplicate label, and an assertion on an item below the fold. The run also forced a rename of six backticked test method names in `AlbumDetailContentTest`, which R8/D8 refuses to dex. The repository-wide claim that no device could be attached here was stale and has been corrected in `AGENTS.md`, the five agent definitions, `tests.instructions.md`, and this document. |
| 2026-10-08 | **Album creation reshaped, within Stage 1.** ADR-0016 adopted: the Add album button now opens the Album type dialog, Automatic is shown disabled until Stage 2, and Manual opens the New album screen — an eighth branch in `MainActivity`'s chain — which names the album and picks its members before writing anything. `AlbumsViewModel.createAlbum` gained an optional members argument so the album and its first members are written together, and `data/AlbumNaming.kt` supplies the pre-filled `My album N`. 230 unit tests and 67 instrumented tests pass, the latter on the SM-G990B. Stage 1 stays Complete: no exit criterion was broken, every new string is in all ten locales, and the album vocabulary additions landed in `ui-vocabulary.instructions.md` in the same change. |
| 2026-10-08 | **New album picker reshaped again, same day.** The flat grid of every item on the device became a two-level browser: folders first, then the media inside the opened folder, with selection accumulating across folders and Create moved to a bottom-right floating tick button. The media level borrows the folder's Folder Detail sort and grouping but not its media-type filter, and the three-layer Folder Detail resolution was extracted from `GalleryViewModel` into pure shared logic in `data/FolderViewOverrides.kt` so it can resolve an arbitrary folder path. ADR-0016 amended in place rather than superseded — the decision to give manual creation a dedicated draft-and-commit screen did not change, only the picking surface inside it. 234 unit tests and 70 instrumented tests pass, the latter on the SM-G990B. Stage 1 stays Complete: no exit criterion was broken. |
| 2026-10-08 | **Picker remediation, closing out the reshape above.** Review found that the reshape had left the picker's *folder* level inheriting Folders View's media-type filter, which is persisted — so a user who once filtered Folders View to images would never see a video-only folder in the picker again. `GalleryViewModel` gained `newAlbumFolders`, which runs the same `filterAndSortFolders` with an empty query and every media type while keeping that view's sort, pinning, exclusion and thumbnail overrides; a unit test now holds it against `filteredFolders`. The inherited *search* query stays, on the narrower argument that it is not persisted. Five smaller findings closed with it: the Create button renders a real disabled state and no longer advertises a click action while announcing itself disabled; instrumented tests now prove a blank or duplicate name creates nothing and check the Create button against the last row at both levels in both layouts; `AlbumsViewModel` takes an injected name formatter so `suggestAlbumName()` is unit-testable; and the album-name field uses a real `label` rather than a `contentDescription`. **236 unit tests and 76 instrumented tests** pass, the latter on the SM-G990B. ADR-0016 Amendment 1 carries the corrections, including that the reason it had recorded for accepting the folder filter did not hold. Stage 1 stays Complete: no exit criterion was broken. |
| 2026-10-08 | **The New album screen's Create button made to float.** It had not been floating: the screen reserved an empty strip along the bottom of its body so the **Create album action** could never overlap anything, leaving a dead band of background above the button at every scroll position — including the top, where nothing needed clearing. The clearance moved into the lazy containers' `contentPadding`, which is the mechanism Albums View's **Add album button** already used; `FolderGrid`, `FolderList`, `MediaGrid`, `MediaList` and `GroupedMediaContent` each gained an optional `contentPadding` whose default reproduces their previous behaviour exactly, so no other call site changed. ADR-0016 and its Amendment 1 needed nothing: neither ever described the strip, and the decision they record is untouched. The bounds-comparing instrumented tests added by the remediation above had passed throughout, because they prove the last row can be scrolled clear of the button, not that the button floats — so the convention behind both controls was written into `ui-vocabulary.instructions.md`, where both are defined, rather than left to be rediscovered a third time. 236 unit tests and 76 instrumented tests pass, the latter on the SM-G990B. Stage 1 stays Complete: no exit criterion was broken. |
| 2026-10-08 | **Stage 2 started.** Step 2.0 landed: ADR-0017 (an automatic album's rule is a composable predicate), ADR-0018 (the media index is a second database, excluded from backup), and ADR-0019 (offline reverse geocoding for region albums) adopted, and the stage plan written at [albums-stage-2.md](albums-stage-2.md). No code; nothing user-visible. The stage's seven implementation steps and its exit criteria live in that document, and the rule for when a stage becomes In progress was clarified above to say that a binding decision counts as a deliverable. |
| 2026-11-12 | **Stage 2 complete, with three criteria carried.** Steps 2.0–2.7 are done: the Tier A index in its own database (four schema versions, three migrations), the constrained WorkManager pipeline and Index now, delta detection, the offline gazetteer and place resolver, the evaluation engine, and region and time albums created from the **New automatic album** screen. The stage's exit criteria moved into §7 and [albums-stage-2.md](albums-stage-2.md) was archived; its open question about volume resync became §10 question 11. Declared complete by the maintainer; the definition-of-done re-run after `56126a6`, the Play Console declaration and the documentation audit are carried — see §7, *Stage 2*. |
| 2026-11-13 | **Stage 2 stays Complete; two of its three carried criteria discharged.** The definition of done passed on the SM-G990B (797 unit tests, 224 of 224 connected tests), and `/docs-audit` ran, with its findings fixed in the same change. The Play Console declaration is still carried. The same day, Stage 4 (content albums and rule editing) was planned to run before Stage 3, in [albums-stage-4.md](albums-stage-4.md) (§8 decisions 13 as amended, and 28–32). |

### Who updates this

The **Planner** owns this section, as it owns the rest of this document and the ADRs. Update it in
the same change that satisfies or breaks an exit criterion — a stage moving to In progress when its
**first step's deliverable lands, whether that deliverable is code or a binding decision**, and to
Complete when every line of its exit criteria holds. The Reviewer checks that a change which
completes a stage also updated the table and the log.

> **Rule clarified 2026-10-08.** This previously said a stage moves to In progress "when its first
> implementation lands". That was written when a stage's planning output was a stage of its own —
> Stage 0 — and it leaves a stage whose ADRs are adopted and whose plan is written reading as
> *Not started*, which is exactly the state a fresh session must not misread. Stage 2's step 2.0 is
> three binding ADRs and a stage plan, and it has landed, so Stage 2 has started. Stage 1's rows,
> criteria and history are unaffected.

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
| [0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md) | That creating an album starts with choosing its kind, and manual creation gets its own screen |

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

12. **Creating an album asks what kind first, and manual creation gets a screen** (ADR-0016). The
    name is the wrong first question — the contents are the point, so the name is pre-filled with
    the first unused `My album N` and edited in place above the picker. The type dialog exists as
    much for Stages 2–4 as for today: it shows Automatic albums, disabled and labelled, so that
    when they arrive they fill a slot the user has already seen rather than appearing from
    nowhere. The accepted costs are a disabled control shipping in the UI, an eighth branch in a
    navigation chain ADR-0013 deliberately kept, and two creation paths — the type dialog from
    Albums View, and the existing name dialog from **Add to album**, where the media is already
    chosen and a picker would be a step backwards. **Step 2.7 fills the slot**: Automatic is
    enabled and opens its own screen, which discharges the disabled-control cost — ADR-0016
    Amendment 2, approved by the maintainer on 2026-11-11.

13. **The stage order stands: region and time first, then people, then content.** Region and time
    is the only automatic family that forces the index and the background pipeline into existence
    **without also dragging in ML**. It is therefore the cheap rehearsal for the expensive stages:
    the index, the delta detection, the WorkManager pipeline, the staleness UI and the rule format
    all get built and shipped against a problem whose correctness can be checked by looking at a
    photo. Reordering to put people first would mean debugging the pipeline and the model at the
    same time, with no working example of either.

    **Amended 2026-11-13 by the maintainer: content (Stage 4) now goes before people (Stage 3).**
    The first sentence of this decision still holds. Only the order of the two ML stages changed,
    and the original text gave no reason for that order. The same rehearsal argument now applies
    one level up:
    - Content builds what people also needs: an on-device model and runtime, model packaging,
      inference inside the indexing worker, a Tier B table reached by a real migration, and
      evaluation by vector comparison. It builds them on data whose mistakes are cheap.
    - It carries none of decision 9's face-data obligations: the disclosure, the deletion switch
      and the Data Safety check.
    - Its UI is one more criterion on an existing screen, not a cluster naming, merging and
      splitting surface.
    - How people are named is still open (§10 question 12).

    The stage numbers were kept, for the reason given under the §7 table.

14. **An automatic album's rule is a composable predicate, not a bespoke set of columns**
    ([ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md)). A rule is
    stored as a conjunction of typed clauses in the `rule` column that already exists, so Stage 3
    adds a clause type rather than a migration, and "these two people, in Italy, in 2025" falls out
    for free. The scope is deliberately narrow — AND only, no OR, no negation, no nesting — and
    widening it is a new ADR. The case the format is designed around is an **unknown clause type**,
    which arrives via an ADR-0014 restore onto an older version or a downgrade: such an album is
    *unevaluatable*, resolves to its manual additions minus its exclusions, says so, and is never
    rewritten. Dropping a clause you do not understand silently widens the album, permanently.

15. **The media index lives in a second, separate database, excluded from backup**
    ([ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)). The decisive
    argument is a collision ADR-0014 left open: it backs the album database up on purpose, while
    §8.9 below commits that face data never enters a backup — and **Android backup rules select
    files, not tables**, so one database file cannot be half backed up. A second file can be
    excluded outright, and should be: the index is entirely recomputable from the device's own
    media, so backing it up spends the shared 25 MB quota on data that will be rebuilt anyway and
    risks taking the albums down with it. The same ADR splits **captured** index data (raw EXIF
    facts, one file open each) from **derived** data (place codes computed from the stored
    coordinates), which is what makes the geocoding choice below reversible for the price of a
    resolve pass rather than a re-index.

16. **Places and dates are picked, never typed, and the names come from a trimmed offline
    gazetteer** ([ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md)).
    The app already knows which places the user's photos are in, so it offers exactly those —
    countries with counts, drilling into regions and places — which is the same browse-not-search
    argument ADR-0016 Amendment 1 made for the manual picker. The consequence is the whole
    decision: the dataset only ever has to answer **location → name**, never **name → location**,
    which removes the need for a searchable multilingual gazetteer and with it most of GeoNames'
    bulk. A trimmed `cities15000` nearest-place table answers country, region and place from one
    artifact and one code path. Two things to expect rather than discover: **most photos have no
    GPS at all**, so region albums will cover a minority of many libraries and the UI must say so;
    and nearest-place misattributes near borders, for which Natural Earth polygons are the recorded
    upgrade path. How the dataset is generated and stamped, the distance cap, and the language
    names are shown in are settled in
    [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md)
    Amendment 1; the 100 km cap was kept, gaps and all, by the maintainer — §10 question 8.

17. **Suggested albums are deferred, not rejected.** "The system notices you have forty photos from
    Cornwall in June 2026 and offers to make an album of them" is a real future ambition and is
    **not** a non-goal — do not read §11 as having ruled it out. It is deferred because it needs
    everything Stage 2 is building (the index, the rule format, and evaluation) plus a clustering
    heuristic over place and time and a surface for offering something the user did not ask for,
    and because every one of those is better designed after watching how people use rules they
    wrote themselves. Nothing in ADR-0017 or ADR-0018 blocks it: a suggestion is a proposed rule,
    in the format rules are already stored in.

18. **WorkManager is approved as a new dependency**, discharging the `AGENTS.md` ask-before-doing
    item for it. It is added at step 2.4 of [albums-stage-2.md](albums-stage-2.md), not before, so
    the pure data-layer steps stay free of it. Recorded here so nobody asks again.

    **This approval covers the dependency and not the permissions it merges in.** When WorkManager
    landed at step 2.4 it added `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
    `FOREGROUND_SERVICE` to the **merged** manifest, none of which the source manifest declares.
    That is a separate ask-before-doing item under `AGENTS.md`, it was not asked before the
    dependency landed, and it was raised afterwards as §10 question 6. "Nobody asks again" above
    means nobody re-asks whether the library may be used; it never meant the permissions — but it
    read as though it did, which is how they went unexamined. They have since been examined and
    decided, one by one, in decision 24.

19. **A deleted photo disappears from an automatic album immediately, not at the next scan**
    ([ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md)).
    This answers open question 1. The mechanism is that an automatic album's contents are **not**
    rendered from the index: the index supplies matching keys, and those keys are resolved against
    live `MediaStore` media by the same path album memberships already use, so an item that is gone
    contributes nothing. Because nothing user-visible then depends on telling "deleted" from "never
    indexed", **the index row is pure cache and reconciliation deletes it** rather than tombstoning
    it. Three things the ADR pins down that are easy to get wrong later: the index keys on
    ADR-0010's identity — the content URI plus the healing key, healed and rewritten in place — so
    a file another app moves is not mistaken for one deleted; an **unmounted volume is not a
    deletion**, which is the ADR's own addition rather than part of the question, because
    discarding a card's rows costs a full re-index when it comes back; and **manual albums do not
    change**. ADR-0010 keeps retaining an orphaned membership and showing the Hidden-members notice,
    because that membership may be a choice the user made and a removed card is indistinguishable
    from a deletion — a rule match carries no such intent. The two behaving differently is correct,
    and §3's formula means one album can hold both kinds at once: its additions and exclusions
    follow ADR-0010, its rule matches follow ADR-0020.

20. **The delta watermark is a table in the index database, not a preference.** It was written to
    `app_settings` first and moved. The watermark is a map keyed by volume name; `AGENTS.md` and
    `ARCHITECTURE.md` §6 both allow that file another *scalar* and nothing structured, and a sixth
    preferences file was not worth buying the difference. The reasoning is
    [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 1: a
    watermark is index metadata — recomputable, worthless without the index, and actively harmful
    if restored onto a new device, because it would suppress the very resync that rebuilds the
    index. Two consequences outlive Stage 2. The index database reached **version 2 through a real
    migration with no destructive fallback**, which is the precedent every later schema change
    follows, Stages 3 and 4 included when Tier B and Tier C add their tables — and which the
    data-only version 3 of ADR-0018 Amendment 3 has since followed. And "delete the index" stays
    one whole-file operation, which is what §8.9's required Settings switch will need.

21. **An item with no capture date gets a coverage notice, not a second date source.** Decided by
    the maintainer on 2026-10-30. The question was whether a time album should fall back to some
    other date when a photo or video carries no capture date, given how much of a real library has
    none. The evidence says a second source would recover almost nothing. It was measured on a
    Samsung SM-G781B (Android 13, API 33), over a real library of about 5,300 items indexed by the
    production pipeline — one device, not a rule:
    - only about one item in six had a usable capture date;
    - about nine in ten of the undated items were messaging-app (WhatsApp) images carrying **no EXIF
      segment at all**, so the file holds nothing to fall back to;
    - `MediaStore`'s own `DATE_TAKEN` was null for **every** undated item, so the platform has no
      better answer either;
    - EXIF `DateTime` or `DateTimeDigitized` without `DateTimeOriginal` was negligible — none in a
      sample of 100, a handful in a full census;
    - the Camera and Screenshots folders were fully dated.

    What is undated is overwhelmingly media *received* rather than taken, whose capture date is not
    on the device in any form. A second source would also have needed an amendment to
    [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 1,
    which defines what "no date" means for a `time` clause and rules out `dateAdded` because it
    invents members; that reasoning is untouched. So the answer is to say it rather than paper over
    it: time albums get a **date-coverage notice**, the counterpart of ADR-0019's location coverage
    notice, which is an exit criterion of step 2.7 in [albums-stage-2.md](albums-stage-2.md). It
    counts live items indexed without a date, says what it counts, and is never presented as
    pending work, because §6's closing paragraph already rules that such an item is neither
    outstanding nor covered. The only earlier measurement, step 2.5's on the SM-G990B, points the
    same way, and was taken before a video container's zero creation date was treated as absent
    ([ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 3), so
    if anything it understated the undated share. **Not decided by this:** how soon a photo that
    *does* have a date reaches an album, which was §10 question 7 and is decision 22.

22. **A new photo reaches a time or region album after the next index pass, not when the app
    opens.** Decided by the maintainer on 2026-11-10, answering §10 question 7 with its option
    "accept the gap and soften §6". Evaluation still runs on app open, but an item matches a `time`
    or `region` clause only once the index has reached it —
    [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 1
    rules out a `dateAdded` fallback, and a place is written only by the resolver in the worker — and
    the index reaches a new item at the periodic constrained job or at "Index now". §6's promise was
    rewritten to say so, with the original struck rather than deleted, and the Incomplete-album
    notice is the bridge. **Why not the alternatives.** An app-open index pass would add battery and
    file-open cost to every start-up, and running outside decision 18's constraints pulls the
    scheduling toward expedited or foreground work, which decision 23 rules out by refusing a
    notification. Extracting on view would pay ADR-0001's per-item cost on the evaluation path, which
    §6 keeps separate from indexing on purpose. Narrowing the gap by relaxing a constraint or
    shortening the period was not taken either; it would be a decision of its own. **For step 2.7:**
    no app-open index pass; the coverage notices and the Incomplete-album notice carry the honesty.

23. **Indexing shows no notification; its progress is in-app only.** Decided by the maintainer on
    2026-11-10, answering §10 question 2. Progress and last-indexed state stay ~~in the Settings
    screen's Media index area~~ **in-app** — Settings' Media index area holds the figures that
    measure progress and the last-indexed state, and the New automatic album screen shows, outside a
    pass, the not-yet-indexed figure Settings also shows, and during one only that it is running
    (decision 27) — and the app does not request `POST_NOTIFICATIONS`. Two properties of
    step 2.4 that were provisional while the question was open are now decided: "Index now" runs as
    expedited work only where the platform grants that without a foreground service and a
    notification — API 31+ — and as ordinary one-time work below it; and the worker has no
    `getForegroundInfo`, so it never runs as a foreground service. **Why:** the reasoning on record
    is the question's own — a notification was needed only if indexing proved slow enough to need
    one, and in-app progress avoids both a new runtime permission on API 33+ and a foreground service
    below API 31. It also keeps the app-open index pass of decision 22 off the table. **Not decided
    by this:** whether the `FOREGROUND_SERVICE` permission WorkManager merges in can be removed; that
    was §10 question 6, since answered by decision 24, which keeps it.

    **Amended 2026-11-12, a detail only.** The struck phrase put all in-app progress in Settings;
    decision 27 added a second in-app place that says where indexing stands. What this decision
    settled is unchanged — no notification, no `POST_NOTIFICATIONS`, no foreground service — and so
    is the narrower rule that progress is *measured* only in Settings. The picker shows one of
    Settings' figures, the count of live media not yet indexed, and only outside a pass, so it never
    shows a pass advancing. This amendment first said the picker showed no figure at all; the
    maintainer's change to decision 27 the same day made that untrue, and these sentences replaced it.

24. **Of the four permissions WorkManager merges in, `ACCESS_NETWORK_STATE` is removed and
    `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` are kept.** Decided by the
    maintainer on 2026-11-10, answering §10 question 6, which also holds the hardware evidence. The
    removal is a `tools:node="remove"` entry in `app/src/main/AndroidManifest.xml`. **Why remove
    `ACCESS_NETWORK_STATE`:** nothing here needs it — indexing carries no network constraint, and
    the only consumer found in WorkManager serves its pre-API-23 alarm scheduler and is declared
    disabled — and an unused network permission misstates §6's *nothing leaves the device* to anyone
    who inspects the app. **Why keep `RECEIVE_BOOT_COMPLETED`:** WorkManager's jobs do not survive a
    reboot on their own and are re-registered by its boot receiver, so removing it would most likely
    stop background indexing after every reboot until the app is next opened. **Why keep `WAKE_LOCK`
    and `FOREGROUND_SERVICE`:** they are WorkManager's standard requirements. The app runs no
    foreground work (decision 23), so `FOREGROUND_SERVICE` may well go unused, but removing a
    permission the library expects was judged not worth the risk. **For whoever moves WorkManager
    next:** an upgrade can merge in a permission this decision never saw, or come to need the one it
    removed, so the merged manifest is rechecked when the version changes. **Not decided by this:**
    the `INTERNET` permission a library adds to the merged manifest, which question 6 records as an
    observation for the Play declaration of §9.

25. **The coverage notices appear in the place picker and in Album Detail View only, and count
    what waiting will not change.** Decided by the maintainer on 2026-11-11, answering open
    question 4 of [albums-stage-2.md](albums-stage-2.md), which asked where the location coverage
    notice of ADR-0019 and the date-coverage notice of decision 21 live.
    - **Where.** In the **New automatic album** screen's picker and in Album Detail View; never on
      an Album tile or Album row, and never in Settings. The location notice is for an album with a
      `region` clause, the date notice for one with a `time` clause, and an album with both shows
      both.
    - **What.** Library-wide counts of live items: for location, those indexed with no coordinates
      or resolved beyond the cap; for date, those indexed with no date. Counts, not percentages.
      **Never pending work** — an unindexed or pending item is in neither count, because that is
      the Incomplete-album notice's business. The wording never says "yet", "still" or "scanning".
    - **When location access is off.** On API 29+ without `ACCESS_MEDIA_LOCATION`, the location
      notice says that location access is off instead of giving a count. It reads the permission
      state and adds no permission.
    - **Stacking, in Album Detail View:** the status notice — Newer-app or Incomplete-album — then
      location, then date, then Hidden-members. **An unevaluatable album shows no coverage notice**:
      its rule contributes nothing, so a statement about what the rule can reach would be about a
      rule that is not being applied.

    **Why.** The picker is where the user decides, so it is where a short album should be explained
    before it is created — ADR-0019's "working feature reads as broken" failure is cheapest to
    prevent there. Album Detail View is where the album is looked at afterwards. A tile or row is a
    poor home: the tile is a fixed square whose notice already ellipsizes at large font scales, and
    a library-wide figure repeated on every tile says one thing many times; the status notices stay
    there because they are per album. ~~Settings is the one progress surface (§6)~~ **Settings is
    the one surface that measures indexing progress**, and a coverage
    figure beside the indexing figures would read as progress. **The counts are library-wide**
    because a per-album version cannot be computed: a photo with no coordinates has no region it
    could be missing from. **Never pending, never "yet"**, because §6's closing paragraph rules that
    such an item is neither outstanding nor covered, and wording that promises a change which will
    not come is the dishonesty §6 exists to prevent. **The permission case gets its own wording**
    because without `ACCESS_MEDIA_LOCATION` extraction stores no location at all, so a count would
    blame the photos for the app's missing access; what happens to those rows after a later grant is
    §10 question 9.

    **Amended 2026-11-12, a detail only.** "The one progress surface" stopped being literally true
    when decision 27 put an Index band in the picker, beside these notices. The intent survives in a
    narrower form: progress is still measured only in Settings, and the one indexing figure the
    picker shows — the count not yet indexed, outside a pass — is Settings' own, so there is still
    one set of indexing figures. A coverage count does now sit beside it; decision 27 records that
    as an accepted cost and how the two are kept apart. In the picker the notices now sit inside a
    collapsible Status area, which lets the user put them away but does not change where they
    appear. The struck citation of §6 also overstated it: §6 asks that a surface say what it counts,
    not that there be only one. This amendment first said the band carried no count; the
    maintainer's change to decision 27 the same day made that untrue, and these sentences replaced it.

26. **An automatic album's rule cannot be edited in Stage 2 step 2.7.** Decided by the maintainer
    on 2026-11-11. The rule is set at creation; rename, add, remove and delete work on an automatic
    album as on any other. **Why:** an editor must round-trip every rule this build can parse,
    including combinations the creation screen never writes, and must refuse an unevaluatable rule
    without rewriting it —
    [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md)'s
    never-rewrite rule — which makes it a feature of its own rather than a variant of creation.
    Creation alone delivers the stage's goal. **The cost:** a wrong rule is fixed by deleting the
    album and creating it again, losing its manual additions and exclusions. Adding editing later is
    a decision of its own, and any editor inherits the never-rewrite rule. **That later decision
    is 31**, taken 2026-11-13: editing ships in Stage 4. This decision stays true of Stage 2 as
    shipped.

27. **The New automatic album screen offers Index now while media is not yet indexed, says how
    many items that is, and never starts indexing by itself.** Requested by the maintainer, and in
    this form decided by the maintainer on 2026-11-12, replacing details first settled in planning —
    see *Why the maintainer changed it* below. Landed 2026-11-12, after Stage 2 closed; Stage 2's
    status is unchanged. Decisions 23 and 25 were amended in place for it.
    - **What.** Each picker list, at every drill level, opens with a collapsible **Status area**: a
      header, then the **Index band**, then that picker's coverage notice — location on the Place
      picker, date on the Date picker. The Index band has three forms. **Not indexed:** outside a
      pass, when X — the live media not yet indexed, the same figure Settings' Media index area
      shows — is above zero, an icon-only Index now beside a text giving X as not indexed yet, the
      whole row one button. **Running:** while a pass runs, a progress indicator and a
      running text, with no count and no action. **Absent:** X is zero and no pass runs. The header
      appears only when the area has something to show, judged by the same rules that decide
      whether each coverage notice draws, so it never heads an empty area. One expanded flag serves
      both pickers: it starts expanded, survives a tab switch, a drill and rotation, and resets
      when the screen closes. While indexing may still fill an empty list — nothing is indexed yet,
      or a pass is running — the list says so rather than that nothing was found.
    - **What not.** No percentage and no progress: X is a plain count, measured against nothing.
      Opening the screen starts nothing.

    **Why.** The picker is where an index that has not reached the library is felt — as an empty
    or short list — and before this the only Index now was in Settings, away from that moment.
    **Never on open**, for decision 22's reasons: an index pass that a screen starts is the app-open
    pass that decision rejected, moved one tap later. **No action while running**, because Index now
    replaces a running Index now rather than leaving it alone, and Settings disables it in the same
    state for the same reason. **An empty list that indexing may still fill does not say nothing
    was found**, because that message over an unindexed library is ADR-0019's
    working-feature-reads-as-broken failure. It may say the work has not happened, unlike decision
    25's coverage wording, because here waiting — or tapping Index now — does change the answer.

    **Why the maintainer changed it, 2026-11-12.** The first version, settled in planning and
    awaiting confirmation, stated where indexing stood without any figure and offered Index now in
    every state but running, "up to date" included. The maintainer replaced it the same day with
    the form above. **A button that does nothing is noise**, so with nothing to index there is no
    band and no button. **A count says how much is missing**, which a state word cannot. **The
    accepted cost:** a "not yet indexed" count now sits beside the coverage notice's "no usable
    location" count — the pairing the first version avoided because the two are incommensurable
    and invite being added up. They are kept apart by wording, the band's count being work that
    waits and saying "yet" while a coverage count is never pending and never says it (decision 25),
    and by the band's count being the same figure Settings shows, so there is still one set of
    indexing figures and progress is still measured only there. **The collapse** exists so that a
    user who has read the area can put it away. This supersedes the planning-settled details —
    state only, no count, Index now offered outside a pass even with nothing to index — and the
    first version's state texts were removed with them.

    **A consequence, recorded rather than changed.** The expanded flag does not follow the area's
    content: an area collapsed by the user, emptied — the index catches up and no coverage notice
    has anything to say — and refilled by new unindexed media comes back collapsed, with the new
    count behind its header. **Not decided by this:** decision 22's gap between a new photo and its
    album; the band makes the gap one tap shorter for a user who notices it, and closes it for
    nobody else.

28. **To the user, content, people, place and date are criteria of one automatic album, not four
    album kinds.** Decided by the maintainer on 2026-11-13. The **New automatic album** screen that
    Stage 2 built with Place and Date gains Content in Stage 4 and People in Stage 3, and any mix
    can be chosen: "castles in Italy, 2024", "Silvia and Davide in France, 2025". **Why it fits
    what is already built:** ADR-0017 stores a rule as a conjunction of typed clauses, chosen so
    that "these two people, in Italy, in 2025" would fall out without a format change. ADR-0016's
    split is between "pick the photos" and "describe what you want", and every criterion is part
    of describing. **Consequence:** each ML stage adds a criterion to an existing screen. It does
    not add an album type to the Album type dialog or a creation screen of its own. How the screen
    holds three and then four criteria, beside decision 27's Status area, is a design question
    for Stage 4's plan.

29. **The content criterion is free text only.** Decided by the maintainer on 2026-11-13. The
    user types a description and no keywords are suggested. **It is the one exception to decision
    16** (places and dates are picked, never typed). That decision's argument was specific to
    places: picking from what the index found removes the need for a name→location lookup. A
    content description has no finite list to pick from. Open-vocabulary matching is what §4 says
    a content album is for, and suggestions would need a vocabulary nobody has chosen. **What it
    obliges:**
    - The user types in any shipped locale, so query quality must hold beyond English.
    - The screen's live match count must equal the members the album will get, the consistency
      Stage 2 step 2.7 required of the place and date counts.

    How a typed description becomes a yes/no membership is Stage 4's open question, not settled
    here.

30. **Picking several people means all of them are in the photo.** Decided by the maintainer on
    2026-11-13. It is the reading ADR-0017's conjunction already gives. "Silvia or Davide" is not
    expressible, and adding it would be the `any` extension ADR-0017 leaves room for: a new ADR.
    How the clause stores its people is Stage 3's to decide.

31. **An automatic album's rule becomes editable in Stage 4.** Decided by the maintainer on
    2026-11-13. This is the later decision that decision 26 said would be needed. **Why now:** with
    three criteria, and a content match whose strictness can only be judged by looking at the
    result, delete-and-recreate becomes a bad cost, because it loses the album's manual additions
    and exclusions. **What it inherits:** the editor must round-trip every rule this build can
    parse, including combinations the creation screen never writes, and must not rewrite an
    unevaluatable rule (ADR-0017). **Open:** the edit surface, what happens to additions and
    exclusions on edit, and the case of a rule with an unknown clause. All three are
    [albums-stage-4.md](albums-stage-4.md)'s questions.

32. **The content model ships inside the app, and content albums are hidden on weaker devices.**
    Decided by the maintainer on 2026-11-13, answering questions A3 and A4 of
    [albums-stage-4.md](albums-stage-4.md). **Inside the app:** no download and no network, which is
    what §11 already requires; the tip jar stays the only feature that touches the network. **Hidden
    on weaker devices:** below a threshold, content albums are not offered, as Favourites is not
    offered below API 30 (decision 3). **Reasons on record:** the maintainer gave none; these are the
    ones the existing documents supply. Decision 3 already accepts that a feature may be absent on
    hardware that cannot support it, and §10 question 3 expected the lowest device to decide the
    model's size. **What hidden means:** the maintainer chose "the simplest solution". The Planner's
    reading, to be confirmed by step 4.1, is that the Content criterion is not offered, Tier B
    indexing is skipped from the same capability check, and an existing album carrying a content
    clause on such a device is treated as unevaluatable (ADR-0017) with its own notice wording and
    is never rewritten. **Not decided, and owed by step 4.1:** the threshold itself, which the
    feasibility spike's memory and speed figures should set.

    **A consequence the maintainer should know:** with the model in the app, its size adds to every
    install, including on devices where the feature is hidden, unless the delivery mechanism can be
    conditional. Play's size limits have not been checked; the plan lists that as A3a.

    **Also approved by the maintainer on 2026-11-13:** third-party dependencies that live only under
    `tools/content-model-spike/`, for the feasibility spike and never the app, and the download of
    about 3 GB of model weights; adding any runtime to the app is still decided at step 4.1.

---

## 9. Consequences for the existing app

Things this epic breaks or strains, which should surprise nobody when they come up:

- **"The app has no database" was written in several places.** False from Stage 1. `AGENTS.md`
  and `ARCHITECTURE.md` were corrected when Room landed; the `.github/agents/*.agent.md`
  definitions were corrected afterwards — see *Documentation audit* in §7, where the finding is
  recorded and closed.
- **Navigation.** `MainActivity`'s `if / else if` chain has no back stack, and albums added two
  destinations to it, then a third when ADR-0016 reshaped creation. Decided in Stage 1: the chain
  stays, by [ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md). Stage 3
  inherits that decision rather than reopening it, with one more branch of evidence than ADR-0013
  weighed.
- **`GalleryViewModel` is the largest file in the app.** None of this belongs in it. Albums get
  their own feature-scoped ViewModel, following the `CreateFolderViewModel` precedent.
- **Play policy.** `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` are now policy-restricted to declared
  use cases, and `MANAGE_MEDIA` is special access, which Stage 1 shipped. Stage 1 was meant to
  confirm the Play Console declaration before shipping and **no record exists that it was done** —
  carry it into Stage 2 rather than assuming it. It was a Stage 2 exit criterion, carried
  at that stage's close (§7), widened to cover `ACCESS_MEDIA_LOCATION`, which Stage 2
  makes load-bearing for a user-facing feature for the first time. **Whoever makes that declaration
  must read §10 question 6 first**: Stage 2 also put permissions into the merged manifest that the
  source manifest does not declare — WorkManager's, one of them since removed by §8 decision 24 —
  and the merged manifest carries `INTERNET` from a library as well. A declaration describes the
  merged manifest's final permission list, not the source manifest.
- **ADR-0001** is extended, not superseded: lazy per-item location reads remain correct for the
  info surfaces; the index becomes a second, amortised path. Both
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) and
  [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md) build on
  it rather than around it, and the indexer reuses its `setRequireOriginal` path rather than
  reimplementing one.

---

## 10. Open questions

1. **When a photo is deleted, is its index row deleted or tombstoned — and what does the user see
   until the scan notices?** — ✅ **Answered 2026-10-08, by
   [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md).** The
   decision is summarised as §8 decision 19; the ADR is what binds.

   **The history is kept deliberately, because it is worth more than a clean entry.** This question
   was marked Answered earlier the same day, reopened within hours, and only then answered properly.
   What was settled first was the *mechanism* — deletion is reconciled during the scan, because
   `queryDeletedFiles` is far out of reach, as ADR-0020's *Alternatives Considered* records — and
   closing the question on that was premature: the mechanism does not say what the user sees, and
   the two halves that remained (delete the row or tombstone it, and what is shown during the
   staleness window) were the maintainer's to decide rather than anyone's to infer. The lesson
   generalises: a question about behaviour is not answered by settling the plumbing underneath it.

   The answer dissolves the second half rather than answering it. There is no staleness window in
   the deletion direction, because the album never displayed the index's view in the first place.
   §6's *Be honest about staleness* has been corrected to say so; it previously described this as an
   open problem and pointed here.

   **No longer blocks step 2.2.**
2. **Progress notification during indexing?** — ✅ **Answered 2026-11-10 by the maintainer: no
   notification.** The question as raised is kept below, then the answer. Needs
   `POST_NOTIFICATIONS` on API 33+. Avoidable if progress lives only in-app, which is the Stage 2
   default unless indexing proves slow enough to need it. — **Deliberately still open, 2026-10-08.**
   It was raised during Stage 2 planning and left open at the maintainer's request, to be discussed
   rather than inferred; a new runtime permission is an `AGENTS.md` ask-before-doing item in any
   case. Step 2.4 ships in-app progress only until it is answered.

   **The answer: progress stays in-app, in the Settings screen's Media index area, and the app does
   not request `POST_NOTIFICATIONS`.** It is what step 2.4 already shipped, so the code was not
   changed by the answer. §8 decision 23 holds it, with the two properties it makes permanent — the
   API 31 limit on an expedited "Index now" and the worker's lack of `getForegroundInfo`.
3. **Lowest device that must run Stage 3–4 ML**, which decides model size and quantisation. —
   **Partly answered 2026-11-13 by the maintainer, for content albums:** weaker devices do not get
   the feature (§8 decision 32). The threshold is still open; the Stage 4 spike's figures should set
   it. Stage 3's people albums have not been asked.
4. **Can albums be reordered, nested, or shared?** Assumed no for now; flat, unordered, local.
5. **Does album healing re-evaluation stay switched on for API 28–29, or is it switched back off?**
   — ✅ **Answered 2026-10-30 by the maintainer: it stays on.** The question as raised is kept below,
   then the answer. **Raised 2026-10-09, with the maintainer.** Step 2.2 of
   [albums-stage-2.md](albums-stage-2.md) removed the `if (SDK_INT < R) return null` guard from
   `AndroidMediaStoreVersionProvider` in `data/MediaStoreVersionStore.kt`, a **Stage 1** file that
   step did not scope. The API fact is right — `MediaStore.getVersion(Context)` is available from
   API 12, not API 30 — so the guard was wrong. The consequence is not a documentation fix but a
   **behaviour change to a shipped Stage 1 feature**:
   `AlbumsViewModel.didMediaStoreVersionChange()` returned `false` unconditionally on API 28–29 and
   now does not, so membership healing re-evaluation activates on those devices for the first time.
   Either reading is defensible — a latent bug fixed, or an unscoped change to working behaviour —
   and the evidence is thin on purpose: there is a Robolectric test at `@Config(sdk = P)` at the
   provider level, **none at the ViewModel level** *(true when raised, no longer — see below)*, and
   no device at that API level is available here. This is listed as an epic question rather than a
   stage detail because it touches Stage 1's shipped behaviour, not Stage 2's new code. Nothing in
   Stage 2 is blocked on it.

   **The answer: keep the guard's removal.** The guard was wrong — `MediaStore.getVersion(Context)`
   exists from API 12 — so re-evaluation being off on API 28–29 was a latent bug, and turning it on
   is a fix to keep, not an unscoped change to revert. The evidence was strengthened before the
   decision: `AlbumsViewModelMediaStoreVersionApi28Test`, under `app/src/test/` in the `ui`
   package, runs at API 28 and drives the real `AndroidMediaStoreVersionProvider` under a real
   `AlbumsViewModel`. It proves that a version change re-runs evaluation at API 28, and that nothing
   re-runs without one. Its limits are stated rather than hidden: the platform call itself is
   mocked, and no API 28–29 hardware is available here, so the behaviour has not been seen on a
   device. The code was not changed by the answer.
6. **What is to be done about the four permissions WorkManager adds to the merged manifest?** —
   ✅ **Answered 2026-11-10 by the maintainer: `ACCESS_NETWORK_STATE` is removed; the other three
   are kept.** The question as raised and the spike's evidence are kept below, then the answer.
   **Raised 2026-10-12, with the maintainer, and unresolved.** Step 2.4 of
   [albums-stage-2.md](albums-stage-2.md) added WorkManager, which §8 decision 18 had already
   approved. What that approval did not cover is what the library brings with it: the **merged**
   manifest now declares `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
   `FOREGROUND_SERVICE`, none of which `app/src/main/AndroidManifest.xml` asks for and none of
   which was examined before the dependency landed. `AGENTS.md` makes a new permission an
   ask-before-doing item and now says explicitly that a dependency can add one without the source
   manifest changing; this is the case that made it say so, and the check is against the merged
   manifest rather than the source one.

   **Why it is not a formality.** `ACCESS_NETWORK_STATE` sits awkwardly beside §6's *nothing leaves
   the device* and beside §11's *no cloud, no account, no sync*. It grants no network access and
   nothing in this app reads it — but it is visible to anyone who inspects the app's permissions,
   and this app's whole posture is that there would be nothing to find. It also lands on the Play
   declaration §9 already carries forward with no record that it was ever completed, which is the
   second reason to settle this before shipping rather than after: the declaration and the Data
   Safety answers have to describe whatever the merged manifest actually says.

   **The shape of the answer, with none of it taken here.** Either accept all four, record why each
   is there, and cover them in the Play declaration; or remove the ones a single constrained job
   does not need — `tools:node="remove"` in the source manifest is the mechanism — and establish on
   hardware that scheduling still survives the cases the removed ones exist for, a reboot in
   particular; or reconsider the dependency, which would unpick step 2.4 and is the least likely.
   **This is the maintainer's to decide.** Nothing in Stage 2 is blocked on it, no step is to close
   it by inference, and no agent is to add, remove, or silence any of the four meanwhile.

   **Evidence from a spike after step 2.5 — evidence, not a decision, and the spike is not
   finished.** Read from WorkManager 2.10.5's own code; none of it is confirmed on hardware.
   - On API 23 and above WorkManager schedules through `SystemJobScheduler`, and the jobs it builds
     are `setPersisted(false)`, so the platform does not keep them across a reboot. They are
     re-registered from WorkManager's own database — by `RescheduleReceiver`, which WorkManager
     enables at runtime while unfinished work exists and which listens for `BOOT_COMPLETED`,
     `TIME_SET` and `TIMEZONE_CHANGED`, or else on the app's next process start.
   - **`RECEIVE_BOOT_COMPLETED` is therefore probably not removable.** The prediction is that
     without it periodic indexing stops after every reboot until the app is next opened.
   - **`ACCESS_NETWORK_STATE` looks removable.** The only consumer found is
     `ConstraintProxy$NetworkStateProxy`, which is disabled on API 23 and above.
   - The spike records nothing yet about `WAKE_LOCK` or `FOREGROUND_SERVICE`.
   - **The hardware check is pending**, because the device disconnected: install, tap Index now,
     reboot once, and observe whether the periodic job is scheduled again **without** opening the
     app. That is what would turn the first prediction into a fact.

   The spike changed nothing in the manifest. Until the check has run, the predictions above are
   inference, and this question is not to be closed on them.

   **The answer: remove `ACCESS_NETWORK_STATE`; keep `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
   `FOREGROUND_SERVICE`.** The mechanism is the one named above, `tools:node="remove"` in
   `app/src/main/AndroidManifest.xml`. §8 decision 24 holds it; the reasons per permission are:
   - **`ACCESS_NETWORK_STATE`, removed.** Indexing has no network constraint — the periodic request
     says `NetworkType.NOT_REQUIRED`, and Index now sets none. The only consumer found,
     `ConstraintProxy$NetworkStateProxy`, is declared disabled and serves the alarm-based scheduler
     WorkManager uses below API 23, beneath this app's minSdk. And an unused network permission sits
     wrongly beside an app whose posture is that nothing leaves the device.
   - **`RECEIVE_BOOT_COMPLETED`, kept,** on the spike's first finding: the jobs are built
     non-persisted and `RescheduleReceiver` re-registers them after a reboot, so removing it would
     most likely stop background indexing after every reboot until the app is next opened.
   - **`WAKE_LOCK` and `FOREGROUND_SERVICE`, kept** as WorkManager's standard requirements. The app
     does no foreground work (§8 decision 23), but removal was judged not worth the risk.

   **Why closing it now does not break the rule above.** That paragraph forbade closing the
   question on the spike's *predictions*, and the answer rests on none of them. The prediction that
   was never checked — that the boot permission is needed — concerns a permission the answer keeps,
   so being wrong about it costs an unneeded permission, not a broken schedule. The one permission
   removed was checked on hardware.

   **Evidence for the removal — SM-G990B, API 36, 2026-11-10.**
   - The merged debug and release manifests differ from their earlier state by exactly that one
     permission; every component and the `<queries>` element are unchanged.
   - `dumpsys package` no longer lists it for the installed app.
   - The periodic job is registered with charging, battery-not-low and idle constraints and no
     network constraint.
   - Index now ran to SUCCESS, the place resolve pass included, with no `SecurityException` and no
     WorkManager error.
   - The unit tests and the connected suite passed.

   **Not verified: reboot survival.** The hardware check above never ran. The answer does not change
   what it would test, because the boot permission stays, but that the schedule survives a reboot
   is still inference rather than observation.

   **An observation, not a decision: the merged manifest also declares `INTERNET`, from a
   library.** Which library was not traced; it is outside this question, which is about
   WorkManager's four. It is recorded because the Play declaration §9 carries forward has to
   describe the merged manifest's final permission list, and that list includes it.
7. **Should a photo taken today reach a time or region album when the app opens, or only after the
   next index pass?** — ✅ **Answered 2026-11-10 by the maintainer: only after the next index
   pass.** The question as raised is kept below, then the answer. **Raised 2026-10-26, with the
   maintainer.** §6 promised the first: "a photo taken this afternoon should appear in *Castles*
   when the app opens, not next time the phone is plugged in." Stage 2 step 2.5 of
   [albums-stage-2.md](albums-stage-2.md) delivers the second. Evaluation does run on app open, as
   §6 says, but an item the index has not reached never matches a `time` clause —
   [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 1,
   which rules out a `dateAdded` fallback because it invents members — and a region clause will need
   the index the same way. Indexing runs only under its constraints (charging, idle, battery not low)
   or on "Index now", so a new photo can wait days on a phone that is rarely idle on a charger. The
   waiting notice makes the album honest about this; it does not make it fast. The stage plan names
   the effect "notice fatigue" without reconciling it with §6, which is why it is here.

   **The options, none of them taken here.**
   - **Accept the gap and soften §6**, so it promises that a new photo appears after the next index
     pass, with the waiting notice as the bridge. No code; the cost is the promise itself.
   - **Run a delta index pass on app open**, over only the rows the watermark selects, outside the
     periodic job's constraints. That is a scheduling decision in what §8 decision 18 settled as
     constrained background work, it adds battery and file-open cost to app start, and it sits next
     to §10 questions 2 and 6 — an unconstrained or expedited request is where a foreground service,
     a notification and the merged-manifest permissions come into play.
   - **Extract on view instead of indexing early**: evaluate the not-yet-indexed live items of a rule
     album by reading their date at evaluation time, in-process and bounded to what is unindexed.
     This pays ADR-0001's per-item cost on the evaluation path, which §6 separates from indexing on
     purpose, and would need its own decision.
   - **Narrow the gap without closing it**, for instance by relaxing a constraint on the periodic
     job or shortening its period. Cheaper, still not "when the app opens".

   **Not the undated-item question, which is decided.** §8 decision 21 settles what happens to an
   item that will *never* have a date: a coverage notice, and no second date source. This question
   was about items that *have* a date the index has not read yet. Whether time-only album creation
   would be brought forward from step 2.7 depended on its answer, and the maintainer's lean was to
   do so only once §6 had been softened; that is now moot, because 2.7 is the next step and enables
   time and region creation together.

   **The answer: accept the gap and soften §6.** A new photo reaches a time or region album after
   the next index pass — the periodic constrained job or "Index now" — not when the app opens. §6
   now promises that, with its original sentence struck rather than deleted, and the
   Incomplete-album notice is the bridge. §8 decision 22 holds the decision and why an app-open
   index pass and extract-on-view were rejected. **For step 2.7:** no app-open index pass; the
   coverage notices and the Incomplete-album notice carry the honesty. Reopening it is a new
   decision.
8. **Is the 100 km nearest-place cap kept, raised, or answered with a denser dataset?** — ✅
   **Answered 2026-11-10 by the maintainer: kept, with its known gaps.** The question as raised is
   kept below, then the answer. **Raised 2026-11-10, with the maintainer.** Approval 4 of step 2.6 in
   [albums-stage-2.md](albums-stage-2.md) made the cap "confirmed or changed from M5 before 2.6
   closes", and M5 does neither:
   [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md)
   Amendment 1's own test — no photo taken in a populated area lands beyond the cap — fails in sparse
   regions — east Iceland, much of Oceania, and the more than 1% of places that have no other place
   within 100 km. The amendment's *Measured* section owns the figures; the one library measured on a
   device had no row beyond the cap, which says nothing about sparse regions.

   **The options, none of them taken here.**
   - **Keep 100 km and accept the gaps.** No code. A photo from east Iceland or rural Oceania has no
     place, no region and no country, and joins the "no location" share the coverage notice counts.
   - **Raise the cap, to about 180 km.** Closes the probed gaps, and files wilderness and offshore
     photos under towns that far away — the "album is simply lying" failure the cap exists for.
   - **Densify to `cities5000`.** Answers the gaps with real places, and is likely to break the
     approved 1 MB budget, given what `cities15000` alone already costs (M1).

   **Whichever is chosen, a change costs more than the constant.** The loader requires the asset
   header's dataset version to equal `PLACE_RESOLVER_VERSION`, so a cap-only change still means
   regenerating the assets, and the generator has no re-stamp mode while GeoNames replaces its files
   in place — the amendment's M5 records it. Step 2.6 stayed In progress until this was answered,
   because approval 4 made a decision on the cap a condition of closing it.

   **The answer: keep 100 km and accept the gaps.** A photo taken in east Iceland — about 176.6 km
   from the nearest place — in sparse Oceania, or near any of the more than 1% of places with no
   neighbour within the cap gets no place, no region and no country, and is counted in the "no
   location" share the coverage notice reports. **Why:** a missing place is honest and a wrong one is
   the "album is simply lying" failure the cap exists to prevent; raising the cap trades the first for
   the second, and densifying is likely to break the approved size budget. ADR-0019 Amendment 1's M5
   verdict records the decision beside the evidence. **Reopening it is a new decision**, and costs
   what the paragraph above says — a `PLACE_RESOLVER_VERSION` bump and regenerated assets, not a
   constant.
9. **Should an image indexed while `ACCESS_MEDIA_LOCATION` was denied be re-extracted after a later
   grant?** — **Raised 2026-11-11, with the maintainer, and recorded as a known gap that step 2.7
   does not fix.** On API 29+ Tier A reads an image's EXIF through ADR-0001's `setRequireOriginal`
   path, and without the permission that read yields nothing — no location, and no date either. The
   row is stored as final: delta detection never re-extracts unchanged media, so granting the
   permission later recovers neither value. The user sees those images as "no location" and "no
   date" in the coverage notices of §8 decision 25 — after the grant, not before it, because while
   the permission is off the location notice says so instead of counting.

   **Why it is not fixed in 2.7.** Recovering the values needs the file, which makes it a re-index,
   and a re-index is a decision of its own — [albums-stage-2.md](albums-stage-2.md)'s rule for any
   fix to extraction that cannot be computed from the stored row. Knowing which rows to re-extract would need the index to
   record that a row was extracted without the permission, which is a schema change; re-extracting
   every image row on a grant would not need one, but costs a file open per image. **Neither is
   taken here.** Nothing in Stage 2 is blocked on it.
10. **Should media in excluded folders count in rule albums?** — **Current behaviour recorded
    2026-11-11: it does.** Since step 2.5 the evaluator matches rules against the gated live set,
    which includes media in folders excluded from Folders View, and step 2.7's picker counts over
    the same set, so a region or time album can contain items the user has excluded from browsing.
    **Why it is kept for now.** Exclusion is a browsing preference over folders, and §2 keeps folder
    behaviour separate from albums; and the picker and the evaluator must read one live set, or the
    count a picker entry shows stops being the number of members the album gets. **The maintainer
    may revisit it.** A change would alter that one live set for both readers in the same change,
    and would be a decision recorded here.
11. **How often does a volume resync happen, and does attaching a secondary volume perturb the
    primary volume's version?** — **Carried here 2026-11-12 from Stage 2's plan, still open.** A
    resync is scoped to the volume whose version changed (§6, ADR-0020 clause 4), but scoping does
    not make it cheap: on a device with no removable storage every row is on the primary volume, so
    a primary version change re-opens every indexed file. What moves that version, and how often, is
    unmeasured, and the pipeline now runs unattended. Whether a mount or unmount changes an
    already-present volume's version is unknown because the only device available has no removable
    storage; the code assumes neither answer and reconciliation refuses to treat an absent volume as
    a deletion. **Do not close it by inference** — it needs a device with a card. Full text in
    [albums-stage-2.md](albums-stage-2.md), *Open questions*, item 3.

12. **How does a person get a name, and where does it live?** — **Raised 2026-11-13; deferred by
    the maintainer to Stage 3's planning.** "Silvia and Davide in France" needs named people, but
    face clusters are anonymous until the user names them. Where the name lives collides with
    backup:
    - Face data lives in the index, which is excluded from backup (decision 9, ADR-0018).
    - Albums are backed up (ADR-0014).
    - So a rule that refers to a person by a cluster in the index would refer to nothing after a
      restore or a full re-index.

    Content criteria do not have this problem, because they store the user's own text. This is
    the hardest open design question in Stage 3, and it needs an ADR before people albums are
    built. Nothing in Stage 4 depends on it.

Questions scoped to a single stage live in that stage's plan and only arrive here if they outlive
it. Stage 2's plan is archived; its one live question is 11 above. Stage 4's are in
[albums-stage-4.md](albums-stage-4.md).

---

## 11. Non-goals

- Albums do not move, copy, rename, or delete media.
- No cloud, no account, no sync. The tip jar remains the app's only network-touching feature.
- No cloud inference. All ML is on-device or it does not ship.
- Albums are not exported to other apps. Favourites is the sole interoperable surface, and it is
  interoperable because the platform owns the column, not because this app publishes anything.
