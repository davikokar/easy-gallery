# Albums Stage 2 — the index, the pipeline, and region/time albums

> **Owner: Planner. Mode: routed.** This is a *stage plan*, not a description of the app. It exists
> for the length of Stage 2 and is archived when the stage closes — what archiving means, and the
> rest of the rules for this kind of document, are in
> [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md).
> The epic itself is [albums.md](albums.md); **its §7 table is the single source of truth for where
> the epic stands**, and this document never restates it.
>
> Nothing here duplicates a fact that albums.md, an ADR, or the code already owns. Where a fact has
> an owner, this document links to it. `AGENTS.md` is explicit that a copied fact is a future lie,
> and every documentation error found in this repository so far has been of exactly that kind.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff. A session reads
> albums.md §7, then this block, then the relevant parts of `/memories/repo/easy-gallery-exploration.md`,
> before anything else — and then whatever the step itself requires. *Session protocol* below says
> what that means.

- **Next step: 2.1 — the index database and its schema.**
- **Depends on:** step 2.0, which is complete. [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)
  governs the whole of 2.1 and should be read first; it decides the separate database, the
  backup exclusion, and the captured/derived column split.
- **Learned in 2.0 that changes the work:**
  - **Step 2.5 needs no migration to the album database.** The column ADR-0017 fills already exists
    and is unused — checked against the committed album schema in 2.0. Do not plan a migration.
  - **The second database needs no Gradle change.** Room writes one schema directory per database
    class under the `room.schemaLocation` that is already configured; ADR-0018 says so. What 2.0
    adds is Stage 1's warning: generation can succeed while the JSON stays untracked, so commit it
    deliberately rather than assuming it appeared.
  - **The first real element written into either backup rules file must be an `<exclude>`.**
    [ADR-0014](../architecture/decisions/0014-album-database-included-in-auto-backup.md) explains
    what the first `<include>` would do to the album database; ADR-0018 requires the index
    exclusion and says in how many places.
  - **Settle the `-wal` / `-shm` question ADR-0018 left open**, and record the answer here and in
    repo memory.
  - **WorkManager belongs to step 2.4**, not before — albums.md §8 decision 18. Steps 2.1–2.3 must
    not pull it in.
- **Blocked, and needing the maintainer:** albums.md §10 question 1 — delete the index row or
  tombstone it — **blocks step 2.2**. It does not block 2.1, so this step can start; 2.2 cannot be
  written without it.
- **Open but blocking nothing yet:** the remaining questions at the end of this document.

---

## Steps

| Step | Delivers | Status |
|---|---|---|
| **2.0** | The three Stage 2 ADRs and this document. No code. | ✅ Complete — 2026-10-08 |
| **2.1** | Index database and schema | ⬜ Not started |
| **2.2** | Delta detection | ⬜ Not started |
| **2.3** | Tier A extraction | ⬜ Not started |
| **2.4** | WorkManager pipeline, "Index now", progress and staleness | ⬜ Not started |
| **2.5** | Evaluation engine and time-only rules | ⬜ Not started |
| **2.6** | The gazetteer | ⬜ Not started |
| **2.7** | Region rules and the place picker | ⬜ Not started |

Status uses albums.md §7's vocabulary.

### 2.0 — The decisions

**Delivers:** [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
[ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md), and this
document. No Kotlin, no Gradle, no resources, no manifest.

**Exit criteria**

- [x] The three ADRs exist, are `Active`, and are indexed in [../architecture/README.md](../architecture/README.md).
- [x] albums.md §7 links to this document and §8 carries the new decisions.
- [x] Stage 2's exit criteria are written (below), as §7 requires of a stage at planning time.
- [x] No ADR contradicts an active one. ADR-0017 gives ADR-0012's "rule" a form; ADR-0018 extends
      ADR-0014 rather than reversing it; ADR-0019 extends ADR-0001 rather than superseding it.
- [x] `ARCHITECTURE.md` is **deliberately not updated** for the three new ADRs, although landing an
      ADR is one of its triggers. Nothing has landed for the overview to describe; the links go in
      with the steps that build what each ADR governs, and carrying them is a Stage 2 exit criterion
      below. The rule that makes this a deferral rather than a miss is in
      [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md).

### 2.1 — Index database and schema

**Delivers:** the second Room database of ADR-0018 — entities and DAO for Tier A, a store interface
with a Room-backed implementation and an `InMemory*` implementation, DAO tests on
`inMemoryDatabaseBuilder`, the singleton on `EasyGalleryApp`, and the backup exclusion.

**Exit criteria**

- A second database class exists with its own file name and version; `EasyGalleryApp` exposes it
  `by lazy` alongside the album database, and no other code constructs either.
- Captured and derived columns are distinct columns, per ADR-0018. The derived place columns exist
  now and are left null until 2.6 — adding them later would be a migration for no reason.
- The exported schema JSON for version 1 of the new database is **committed** under `app/schemas/`.
- The index database file is excluded in `backup_rules.xml` and in **both** the `<cloud-backup>` and
  `<device-transfer>` sections of `data_extraction_rules.xml`, and **no `<include>` element has been
  added to either file**.
- The `-wal` / `-shm` question is answered one way or the other, and the answer is written into the
  *Next session starts here* block and into repo memory.
- Store interface + `InMemory*` + DAO tests, following the `AlbumStore` shape.
- **All five `.github/agents/*.agent.md` definitions are corrected in this step.** Each states, in
  its own words, that the album database is the only data the app owns; this step makes that false.
  `AGENTS.md`'s documentation map says why that row is the costliest one to miss, and the Stage 1
  audit found exactly this failure in exactly these files. The stage-closing documentation audit
  would catch it too, but only after six more steps of agents believing it.

### 2.2 — Delta detection

**Delivers:** the mechanism that answers "what changed on the device since last time" —
`MediaStore.getVersion()` and `getGeneration(volume)` with the per-row `GENERATION_ADDED` /
`GENERATION_MODIFIED` columns, the API 28–29 date-watermark fallback, and reconciliation of rows
that have disappeared since the last scan. albums.md §6 describes the mechanism; this step builds
it.

**Exit criteria**

- A version/generation watermark is persisted and compared, with a full resync when
  `getVersion()` has changed. A store for that watermark already exists — `AGENTS.md`'s
  preference-store table says which — so extend it or sit beside it, and do not add a sixth
  preferences file without surfacing it, as `AGENTS.md` requires.
- The API 28–29 fallback is implemented and its known wrongness is documented where a reader will
  meet it, not only here.
- **Deletion is reconciled during the scan** — the settled half of albums.md §10 question 1. The
  open half **blocks this step**: whether reconciliation deletes the index row or tombstones it,
  and what the user sees until the scan notices, is the maintainer's decision and is not to be
  inferred. Whichever way it goes, the staleness window albums.md §6 now records has to be handled
  here rather than discovered later.
- Branching by API level is pure and Android-free so every branch is unit-testable on a plain JVM —
  the `Favourites.kt` precedent.
- The Android boundary is covered by Robolectric tests, as the rest of `data/` is.

### 2.3 — Tier A extraction

**Delivers:** reading EXIF location, true date-taken and dimensions for one item, as pure logic plus
the Android boundary, and a batch indexer that walks a delta and writes index rows. No scheduling
yet — the batch runs when something calls it.

**Exit criteria**

- Images go through `ACCESS_MEDIA_LOCATION` + `MediaStore.setRequireOriginal` on API 29+, reusing
  the path `MediaLocation.kt` already established. Video goes through the `MediaMetadataRetriever`
  path. Neither is reimplemented.
- A permission denial, a missing file, and a corrupt header each produce an absent value and never
  an exception escaping the batch — ADR-0001's rule, applied to a loop that will meet all three.
- **The date-taken semantics are stated and tested**: what time base is stored, and what a
  zone-less EXIF `DateTimeOriginal` is interpreted as. A capture timestamp compared against a zoned
  range is a classic off-by-one-day defect and 2.5 depends on this being pinned down.
- Batch size and cancellation behaviour are deliberate, because 2.4 will run this under a system
  that can stop it mid-batch.
- Pure extraction and parsing are unit-tested on a plain JVM.

### 2.4 — The pipeline — *first user-visible step, and the natural shipping seam*

**Delivers:** WorkManager (the approved new dependency), the constrained periodic indexing job,
**"Index now"** in Settings as expedited work, in-app progress and staleness, and
`SortType.DATE_TAKEN` finally reading the index.

**Exit criteria**

- Indexing runs under WorkManager constrained to charging, device idle, and battery not low, with
  **no network constraint** — nothing leaves the device (albums.md §6).
- "Index now" exists in Settings and runs expedited.
- Progress and last-indexed state are visible **in-app**. The notification question at the end of
  this document decides whether a notification is added; until it is answered, in-app only.
- **`SortType.DATE_TAKEN` reads the index.** What it does instead today is the Known debt item in
  `ARCHITECTURE.md` §11, and
  [ADR-0008](../architecture/decisions/0008-camera-folder-default-sort-order.md) records how it got
  there; resolving a §11 item is itself an overview trigger. An item not yet indexed must degrade to
  the current behaviour rather than disappearing or sorting to one end.
- WorkManager appears in `gradle/libs.versions.toml` and in `ARCHITECTURE.md` §10's tech-stack
  table, which is an overview trigger.
- Every new string is in all ten locales.

> **This is where Stage 2 could stop and still have shipped something real**: the index is built,
> date-taken sort is fixed, the phone is not being drained, and no album rule exists yet. Nothing
> after this step is needed for 2.1–2.4 to be worth having.

### 2.5 — Evaluation and time-only rules

**Delivers:** the rule format of ADR-0017 — parser, `Unevaluatable` handling, the `time` clause —
the evaluator that turns a rule into members against the index, and the first genuinely automatic
album. **Zero geo risk**: if 2.6 disappoints, Stage 2 still ships a working automatic album.

**Exit criteria**

- Rules round-trip through `albums.rule` with **no migration** to the album database.
- `NULL` rule still means manual. Nothing about existing manual albums changes.
- An unknown clause type, an unknown envelope version, and malformed JSON all produce
  `Unevaluatable`; the album resolves to its additions minus its exclusions, shows the notice, and
  **is never rewritten**. Each of the three has its own test. This is the highest-risk code in the
  step, because the failure it guards against cannot occur on a device that has only moved forward.
- Evaluation composes with ADR-0012: `(rule matches ∪ additions) − exclusions`, with exclusion
  winning, and tombstones surviving re-evaluation.
- Evaluation runs on app open and after each index batch — **not** under the indexing constraints
  (albums.md §6).
- Parser and evaluator are pure and unit-tested on a plain JVM.
- Every new string is in all ten locales.

### 2.6 — The gazetteer

**Delivers:** the trimmed dataset of ADR-0019 as an APK asset, the bucket-indexed nearest-place
lookup, the distance cap, the resolve pass that fills ADR-0018's derived columns, and the CC BY
attribution surface.

**Exit criteria**

- Every figure ADR-0019 lists under *To be measured* has been measured, and the measurements are
  recorded — in the ADR where they change a stated consequence, otherwise in repo memory.
- The lookup uses a coarse grid bucket index. A linear scan does not pass this step.
- The distance cap exists, and beyond it place, region and country are all null rather than guessed.
- The resolve pass reads only the index's stored coordinates. It opens no media file.
- **A CC BY attribution is visible in Settings.** There is no attribution surface today, so this
  creates one; its string is in all ten locales.
- Nearest-place matching, bucketing and the cap are pure and unit-tested on a plain JVM, including
  the antimeridian and the poles.

### 2.7 — Region rules and the place picker

**Delivers:** the `region` clause, the browse-don't-type place picker, the coverage notice, and the
flipping of ADR-0016's **Automatic album** option from disabled to live.

**Exit criteria**

- The picker offers only countries, regions and places the index actually found, with counts, and
  the date picker offers only years and months that have photos. Nothing is typed and nothing is
  parsed.
- Compound region + time rules work, because ADR-0017's conjunction makes them fall out — one test
  proves it.
- **Coverage is stated**, not implied: the user is told how much of their library has a location.
  ADR-0019 names this as the most likely way for a working feature to read as broken.
- The **Album type dialog**'s Automatic option is enabled, and the "Not available yet" label is
  removed from the UI and from all ten locale files in the same change.
- Stage 2's vocabulary moves from this document into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md) in this
  step, per albums.md §8 decision 8 — in the same change that builds the screens it names.
- Every new string is in all ten locales.

### Why the order is what it is

- **2.1–2.3 are pure and data-layer.** They have no UI, no scheduling and no user-visible
  behaviour, so each ends at a clean boundary and survives a session gap without leaving the app in
  a half-state.
- **2.4 is the natural "stop here and ship" seam.** Index built, date-taken sort fixed, battery
  behaviour settled, no album rules yet. Everything before it is infrastructure; everything after it
  is a feature on top. If Stage 2 has to be shipped early, this is the line.
- **2.5 before 2.6 and 2.7 is deliberate risk ordering.** Time rules need no dataset, no licence,
  no size budget and no coastline. Doing them first means Stage 2 delivers a working automatic
  album even if the geo dataset turns out to be too large, too slow, or too inaccurate — and if it
  does, 2.6 and 2.7 can be deferred without stranding the evaluation engine.
- **2.6 before 2.7** so the UI is built against measured data rather than against assumptions about
  it. The coverage notice in particular cannot be designed sensibly until it is known how many
  photos have coordinates at all.

---

## Stage 2 exit criteria

albums.md §7 requires a stage's exit criteria to be written when that stage is planned. These are
derived from Stage 1's set and from the content above. **The stage is Complete only when every line
holds**, however much of it is written; albums.md §7 owns what the statuses mean and when they
change.

- The checks in `AGENTS.md`'s definition of done pass, including `adb devices` being checked rather
  than assumed and the result being stated.
- Every step above is ✅ with its own exit criteria met, or has been explicitly dropped and the drop
  recorded in albums.md §7's stage log.
- The index survives the cases that are not the happy path: no GPS on any photo, permission denied,
  a mid-scan cancellation, and a device that has never been indexed.
- **An automatic album with an unevaluatable rule reads as explained-and-waiting, never as empty and
  never as a bug**, in Albums View and Album Detail View alike — the ADR-0017 case, and the direct
  descendant of Stage 1's fully-orphaned-album criterion.
- **Region-album coverage is visible to the user.** A library where few photos carry GPS produces an
  honest, short album and says why, rather than looking broken (ADR-0019, albums.md §6).
- The index database is excluded from backup and the album database is still included — verified
  against the rules files, not assumed.
- The two Room schemas are both exported and **committed**.
- Every new user-facing string is in all ten locales, and every string removed is removed from all
  ten in the same change.
- Stage 2's vocabulary has moved into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).
- `docs/architecture/ARCHITECTURE.md` has been updated for everything that triggers it — at minimum
  the new `data/` files, the second database in §6, the WorkManager dependency in §10's tech-stack
  table, the Known debt item in §11 that step 2.4 resolves, and links to ADRs 0017–0019 in the
  sections they govern. The last of those was deliberately deferred from step 2.0 and is discharged
  here.
- **The Play Console restricted-permission declaration is confirmed done.** albums.md §9 carries it
  forward from Stage 1 with no record that it was ever completed, and names the permissions at
  stake. Stage 2 adds one more: it makes `ACCESS_MEDIA_LOCATION` load-bearing for a user-facing
  feature for the first time, which is a Data Safety question in its own right. This criterion is
  met by checking the Play Console and recording the outcome — not by assuming it.
- **A documentation audit has passed** — run `/docs-audit`, and fix or record everything it finds.
  Every stage carries this line, and stage completion is the scheduled moment for it, because
  triggers only catch the drift that was anticipated.

---

## Session protocol

**First action of a session:**

1. Read albums.md §7 — the stage table, the current stage's exit criteria, and the last line of the
   stage log.
2. Read *Next session starts here* at the top of this document.
3. Read the parts of `/memories/repo/easy-gallery-exploration.md` relevant to the step in hand.

Those three **first**, before anything else — they are what tells you which step is next and what it
has to obey. They do not replace the reading your agent definition and `AGENTS.md` require, nor the
ADRs the step itself cites; each step above names its own. If the three do not tell you what the
next step is and where its rules live, that is a defect in this document.

**Last action of a session:**

1. Update the step's status in the table above, and tick what its exit criteria now hold.
2. **Rewrite *Next session starts here*** — the next step, what it depends on, and anything learned
   that changes it. Rewrite it; do not append to it.
3. Append any newly learned trap to `/memories/repo/easy-gallery-exploration.md`, at the top. That
   file contains the same report twice and `str_replace` cannot be trusted to be unique in it.
4. Append a line to albums.md §7's stage log **only when the stage's overall status changes** —
   Not started → In progress, or In progress → Complete. A step finishing is not a status change.
   Keeping that log coarse is what stops it becoming a diary; this document is where the detail
   belongs, and this document is archived when the stage closes.

Report drift rather than fixing it, except where `AGENTS.md` says otherwise. The UI vocabulary is
the one document updated in-change, and only for additions.

---

## Open questions

The first two are the epic's, not this stage's; albums.md §10 states them and owns them.

1. A deleted photo's index row — albums.md §10 question 1. It **blocks step 2.2**.
2. A progress notification during indexing — albums.md §10 question 2. Step 2.4 waits on it.
3. **How often does a full resync actually happen?** `getVersion()` changing means discarding the
   whole index and rebuilding it. If that fires more often than expected — on an OS update, a
   volume remount, a vendor media-scanner quirk — the cost is a complete re-index rather than a
   delta, and the mitigation would have to be designed rather than discovered. Measurable in 2.2 on
   the SM-G990B; recommend measuring rather than assuming it is rare.
4. **Where does the coverage notice live?** ADR-0019 requires the user to be told how much of their
   library has a location, but not where. Album Detail View is the obvious home; whether the Album
   type dialog or the place picker should also warn before an empty album is created is a design
   question for 2.7, and depends on the number measured in 2.6.
5. **Does an unevaluatable album need its own string, or does `album_hidden_items_notice` cover
   it?** They are different conditions — "members are waiting to heal" versus "this rule needs a
   newer app" — and conflating them would be the cheap wrong answer. Decide in 2.5; if a new string
   is needed it is ten locale files.

---

## Related

- [albums.md](albums.md) — the epic. §3 membership, §4 the kinds, §6 the index, §7 staging and
  progress, §8 decisions, §9 consequences, §10 open questions, §11 non-goals.
- [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
  [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md) — the
  decisions binding on this stage, in addition to those binding on Stage 1.
- [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md) —
  how this document is maintained, and by whom.
