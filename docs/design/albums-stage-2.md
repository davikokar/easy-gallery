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

- **2.5 is ✅ Complete (2026-10-26); 2.6 — the gazetteer — is next.** Work has landed since 2.5
  closed, outside any step, and its gate has not fully run. Read the next two items before starting.
- **What landed after 2.5, and how far it is verified.** *Between 2.5 and 2.6* below is the record.
  - **The container zero date.** A video container writes 0 for "no date", the platform renders it
    as 1904-01-01, and the extractor stored that as a capture date. The parser now treats it as
    absent, and `MIGRATION_2_3` clears the stored copies, taking the index database to version 3 —
    [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 3.
  - **A ViewModel-level API 28 test** for [albums.md](albums.md) §10 question 5, which the
    maintainer then answered (below).
  - **Gates: compiled and unit-tested only.** The full definition-of-done gate has **not** run over
    this work — not the connected suite, and not the v2→v3 migration of a real index on a device.
    Whoever runs the next gate owns both; neither is to be claimed from the unit tests. Two of its
    files are new rather than modified — the index's `3.json` and the API 28 test — and must be
    committed with it; `git status` and `git ls-files app/schemas` settle whether they have been.
- **Decided by the maintainer on 2026-10-30 — do not reopen:**
  - **[albums.md](albums.md) §10 question 5: the `< R` guard's removal stays.** Healing
    re-evaluation on API 28–29 was a latent bug, now fixed. The question keeps its history and the
    evidence's limits.
  - **The reconciler criterion under 2.6 is accepted, and binding.** It is an ordinary exit
    criterion of 2.6 now.
  - **An undated item gets a coverage notice, not a second date source** — [albums.md](albums.md)
    §8 decision 21, with the measurement behind it. 2.7 carries a date-coverage notice as an exit
    criterion. **Do not add a `dateAdded` fallback or any other date source.**
- **Still the maintainer's, in this order.** None is any agent's to take, and each is owned where
  it is linked.
  1. **[albums.md](albums.md) §10 question 7 — app-open timing.** Whether a photo that has a date
     reaches a time or region album when the app opens, or only after the next index pass. §6
     promises the first; 2.5 delivers the second. Open, and separate from the undated-item
     question, which is decided. **Do not add an app-open index pass or soften §6 while it is
     open.**
  2. **[albums.md](albums.md) §10 question 6 — the four merged-manifest permissions, with a spike
     in progress.** The evidence so far is in the question: read from WorkManager's own code,
     `RECEIVE_BOOT_COMPLETED` is probably not removable and `ACCESS_NETWORK_STATE` looks removable.
     **Neither is confirmed.** The next action is the hardware check the device disconnection
     interrupted: install, tap Index now, reboot once, and observe whether the periodic job is
     scheduled again **without** opening the app. The spike changed nothing in the manifest; **do
     not add, remove or silence any of the four** until the maintainer decides.
  3. **Bring time-only creation forward, or not.** No user can create an automatic album until 2.7;
     bringing it forward is a scope change to this plan, and 2.5's correction note says what it
     would replace. Open. **The maintainer's lean — a lean, not a decision:** yes, but only after
     question 7 is answered at least as "accept the gap and soften §6", and only after the
     date-coverage notice exists. Do not schedule it on the strength of the lean.
  4. **The Play Console restricted-permission declaration is the maintainer's own action, in the
     console.** It is to be made after question 6 is settled and before the first release that
     contains Stage 2, and it must describe the **merged** manifest — which is why it waits for
     question 6. No agent can make it or verify it from the repository. It is a Stage 2 exit
     criterion below, met only by checking the console and recording the outcome.
  5. **[albums.md](albums.md) §10 question 2** — a notification, and with it `POST_NOTIFICATIONS` —
     is still open, so every progress surface stays in-app. It blocks no step.
- **Then 2.6 — the gazetteer — and what it starts from.**
  - **It is offline**, per [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md):
    a trimmed dataset shipped as an APK asset, and every figure the ADR lists under *To be measured*
    measured and recorded. 2.6's exit criteria below are the checklist.
  - **The derived place columns are still written by nothing.** They have existed since 2.1 and are
    null on every device; 2.6's resolve pass is their first writer. Until it lands, anything that
    tests country, region or place matches nothing, everywhere, silently.
  - **A `region` clause is an unknown clause type until 2.7**, so a rule carrying one parses to
    `Unevaluatable` and shows the newer-app notice. That is ADR-0017's rule applied, not a gap for
    2.6 to close; 2.7 is the step that makes it evaluatable.
  - **[ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md)
    Amendment 1 holds the decisions 2.5 was built on** — the `time` clause's shape, the zone it is
    read in, how strict the parser is, and the rule that an undated or unindexed item never matches.
    Read them there. Its *When this format becomes binding* section is the one that matters next:
    the format is freely revisable until 2.7 first writes a rule, and not afterwards.
  - **No migration was needed for 2.5 and none was made**, to either database. The index has
    migrated since — `MIGRATION_2_3`, after 2.5, data only — and the album database never has. If
    2.6 changes the index schema, it is the next bump, migration and committed JSON; read the
    current version from the `@Database` annotation, not from here.
  - **The reconciler criterion is binding on 2.6**, accepted by the maintainer on 2026-10-30: 2.6
    closes the ADR-0020 clause 3 breach in `MediaIndexReconciler`. Its exit criteria have the detail.
  - **The trap waiting at 2.6, cheap now and a migration later:** a row whose coordinates fall
    outside [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md)'s
    distance cap resolves to null country, null region and null place — **indistinguishable from
    "not yet resolved"** — so a resolve pass selecting on "derived columns are null" would retry
    those rows forever. Not worth a column yet; decide it at 2.6.
- **Still true, and binding on what follows:**
  - **2.5 depends on 2.4 having filled the index, and must behave when it has not.** An evaluation
    over an empty or partial index would render an automatic album as simply *empty*; 2.5 answers
    that with the waiting notice, and any later clause meets the same shape.
  - **A run that cannot read the library must not advance a watermark.** `MediaIndexingWorker`
    checks the runtime permission **and** the app-op before it does anything and fails loudly,
    because the volume and generation calls are not permission-gated and the row query is. 2.4's
    record below has the whole defect; it was the third of this stage's "indexed nothing and looked
    like it worked" failures.
  - **The data-layer traps of 2.2 and 2.3 are not repeated here.** The delta composition
    (`detect` / `persistWatermarks` and why the order is structural), the selected-but-not-live race
    and its watermark clamping, and the `external` ↔ any-volume namespace rule in
    `data/MediaStoreUriAliases.kt` all still hold and are recorded under their own steps below and
    in repo memory.
  - **The per-volume watermark lives in the index database, not in `app_settings`**, which is why
    the index reached version 2 through a real `Migration(1, 2)` with no destructive fallback.
    [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 1 holds
    the reasoning. **Any later schema change is another bump, another migration and another
    committed JSON**; `fallbackToDestructiveMigration` is not available, because dropping the table
    also drops the watermark that would trigger the rebuild.
  - **An encoder's "no value" must be stored as null, never as a fact.** Null Island (0, 0) has been
    excluded at extraction since 2.3, in `isValidMediaCoordinates` in `data/MediaLocation.kt`, which
    both the image and video paths use — so 2.6's resolve pass inherits clean coordinates. The
    container zero date was not excluded, and was only found after 2.5 by looking at a real
    library. **And a fix to extraction does not reach stored rows**, because delta detection never
    re-extracts unchanged media: when the correct value is computable from the stored one, a data
    migration is the repair ([ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)
    Amendment 3); when it needs the file, it is a re-index, and that is a decision.
  - **The API 28 presence rule: only `MediaStore.VOLUME_EXTERNAL_PRIMARY` counts as present.** API 28
    has no volume-enumeration API, so reconciling anything else would delete rows for volumes nobody
    can observe — what ADR-0020 clause 4 forbids. The accepted cost: retained rows can accumulate
    for the life of an install that never leaves API 28.
  - **What a mount or unmount does to an already-present volume's version is still unknown.** The
    SM-G990B has no removable storage, so no controlled experiment was possible; the code assumes
    neither answer, and reconciliation refuses to treat an absent volume as a deletion. **Do not
    close it by inference** — it needs a device with a card. Open question 3 below carries it.
  - **`lintDebug` cannot run in this workspace**, so `NewApi` catches nothing. `AGENTS.md` *Running
    Gradle* owns the failure. **Read every API-level guard**; `isAtLeastQ()` in
    `data/MediaLocation.kt` is annotated `@ChecksSdkIntAtLeast` so that the day lint does start, it
    recognises the guard.
  - **`git ls-files app/schemas` settles whether an exported schema is tracked**; `git status` does
    not, because Room writes one directory per database class, outside `app/src/`. Run it after any
    schema change rather than trusting a sentence here.
  - **The indexing job has two API-level limits and neither is a defect.** "Index now" is expedited
    on the higher API levels only, and the expedited request deliberately carries **none** of the
    periodic job's three constraints, because the platform rejects an expedited request that carries
    them. `ARCHITECTURE.md` §11 owns both facts, the level and the reasoning. Do not "fix" either by
    making the two requests match.
  - **The shared-work-name finding from 2.4's close is resolved — do not reopen it.** It was resolved
    in commit `d68898d` by two unique work names, `media-index-periodic` and `media-index-now`, so
    Index now's `REPLACE` can only replace an earlier Index now. The same change made liveness
    three-state — `RUNNING`, `WAITING`, `IDLE` — and **only `RUNNING` disables Index now**, because a
    periodic job sits enqueued between runs and the old two-state liveness read that as "Indexing…"
    forever. `ARCHITECTURE.md` §11 keeps the struck entry and the reasoning.
  - **The time base was pinned down at 2.3.** Date-taken is stored as epoch milliseconds UTC;
    `OffsetTimeOriginal` is used where present, and a zone-less EXIF `DateTimeOriginal` is read in
    the device's zone at extraction time, as stated on `parseExifDateTakenEpochMillis`. Changing that
    later is a **re-index, not a migration**. ADR-0017 Amendment 1 mirrors it at evaluation time and
    records the accepted limit for a user who travels.
  - **The three date-taken states have names; reuse them.** `DateTakenSortState` has
    `IndexedWithDate`, `IndexedWithoutDate` and `NotIndexed`, and the evaluator uses the same three.
    An item indexed without a date is neither outstanding work nor covered — [albums.md](albums.md)
    §6's closing paragraph owns that rule, and §8 decision 21 is why it gets a coverage notice
    rather than a second date source. A video whose container wrote a zero date is one of them.
  - **One progress surface.** The Settings Media index figures are intersected with live media and
    shown as three separate figures with no combined percentage. 2.5's waiting notice points there
    rather than inventing a second style; open question 5 below says why.
  - **The shared identity is shared, and joined in memory.** `data/MediaIdentity.kt` holds
    `MediaIdentity`, `MediaHealingKey` and now `LiveMediaLookup`. There is still **no database index
    on the healing triple and none is wanted**; adding one is a Room `identityHash` change, and so
    another version bump and migration of the index database.
  - **A connected run uninstalls the app and wipes its data when it finishes**, and a run reporting
    **0 tests** is an install timeout, not a pass. `AGENTS.md` *Running Gradle* owns the first and
    repo memory has the routine for the second. A device test can also fail on its own race, as
    2.5's did; read the failure before suspecting the product.
- **Residual device checks carried from 2.5's close — not blockers, and not 2.5's exit criteria.**
  2.5 closed on a device run that proved the pipeline and the Compose notice tests; these are what
  it could not reach. Do them when something shipped can create a rule album — 2.7, or time-only
  creation if the maintainer brings it forward — or earlier by hand.
  - **A notice in a real tile or row of the installed app**, and Album Detail View's notices in the
    installed app. They have rendered only in Compose tests with fixtures. Watch above all the
    **tile notice at a font scale around 2 on a grid of three or more columns** — the tile is a fixed
    square and its notice ellipsizes — and in de, fr, it and pt-rBR, which run longest.
  - **The translations of the four `album_rule_*` strings**, written and then reworded by R3 without
    a native-speaker pass. ja, ko and ar first.
  - **A multi-volume index run, and the mount/unmount question** (open question 3). The SM-G990B has
    no removable storage.
  - **R1's gate in production composition.** The forwarding that checks `isMediaLoaded` lives in
    `MainActivity` and `FolderListScreen`; the device test constructs `AlbumsViewModel` directly, so
    R1 rests on its unit tests.
- **Unconfirmed on hardware since 2.5, beyond the gate above.** That the platform renders a zero
  container date as `19040101T000000.000Z` comes from AOSP source, not from a device; and question
  5's API 28 behaviour rests on a Robolectric test with the platform call mocked, because no API
  28–29 hardware is available here. Neither is to be described as device-verified.
- **Known debt this stage carries, each owned elsewhere — read it there:**
  - **The periodic indexing request's constraints are asserted by no test**; `buildPeriodicRequest()`
    is verified by reading it. `ARCHITECTURE.md` §11.
  - **Folder-level date-taken sorting and date-taken grouping still use `dateAdded`**, so a date
    header can disagree with the order beneath it. `ARCHITECTURE.md` §11.
  - **`MediaIndexReconciler` still holds a private live lookup** — its own by-URI and by-healing-key
    maps — which is the second copy ADR-0020 clause 3 forbids. Out of 2.5's scope and not fixed, and
    not a mechanical swap onto `LiveMediaLookup`, because reconciliation compares raw URIs.
    `ARCHITECTURE.md` §11 has the detail. **2.6 carries the binding exit criterion that closes it**,
    accepted by the maintainer on 2026-10-30, so Stage 2 cannot complete with the breach open.
  - **The app-open empty-list defect is fixed (R1); two ungated siblings remain**, both found by
    reading and neither device-observed. The Settings Media index figures read zero until media has
    loaded, because `setLiveMedia` is not gated on `isMediaLoaded`; and the New album screen can open
    on an empty folder picker in the window between `loadFolders()` reporting success and
    `getAllMedia()` returning. Neither feeds indexing or album membership. `ARCHITECTURE.md` §11
    owns both.
  - **Notice fatigue, and the product question behind it.** The waiting notice shows whenever any
    live item is not yet indexed, and an item taken today stays unindexed until the next constrained
    run — which can be days on a phone that is rarely idle on a charger. That contradicts albums.md
    §6's promise that a photo taken this afternoon appears when the app opens; it is now
    [albums.md](albums.md) §10 question 7, the maintainer's call.
  - **From the review of 2.5, recorded for the maintainer and not acted on:**
    - The index `Flow` in `AlbumsViewModel` is shared with `shareIn(..., WhileSubscribed())` under a
      `flatMapLatest` over the album list. With no stop timeout, every re-emission of that list — a
      rename, a cover change — can drop the last subscriber and restart the full-table Room query.
      The review suggests `WhileSubscribed(5_000)`; not applied.
    - Evaluation cost: every index chunk the worker commits re-evaluates every rule album, and
      `AlbumMembership.resolve` runs twice per rule album — once in `computeAlbum`, once inside the
      evaluator. Revisit before 2.7 makes rule albums common.
    - Removing from a **manual** album deletes only the member's own membership id, although
      `additionIdsByUri` can hold several addition rows for one item; the others keep it in the album.
      Pre-existing, a one-line fix, not made.
    - Several new KDoc comments from 2.5 run three to six lines, against the repo's one-line comment
      guideline. Low priority.
    - **A possible permanent waiting state.** A live item the indexer never selects by construction
      would keep every time album `WAITING_ON_INDEX` forever. The candidate is a secondary volume on
      API 28, where only the primary volume counts as present. Unverified; the device test
      ran on a device with no secondary volume, so it could not catch it.
    - **An edited file keeps its old indexed date until it is re-indexed**, because a direct URI match
      never consults the healing triple, and the album shows `COMPLETE` meanwhile. Accepted, and
      recorded as such in ADR-0017 Amendment 1.
    - Two androidTest fixtures still pass `resolved` / `stored` values to a helper that no longer
      forwards them. Harmless decoration.
    - `removeMembers` deletes the row, recording no exclusion, for a membership id with no live
      match. Unreachable in production because `removeMember` and `removeMembers` have no production
      caller; the only UI path is `removeMembersByMedia`, which has a `MediaItem` in hand. Nothing
      enforces that. R2 under 2.5 has it.
  - **No test covers a reload that fails after a successful first load** — `isMediaLoaded` should
    stay true and `allMedia` keep its old value. And `MainActivity` and `FolderListScreen` each
    check `isMediaLoaded` but forward `allMedia`, so a third forwarder that forgets the check brings
    the cold-start defect back. A single nullable-until-loaded flow would remove that risk; not done.
    The failed-first-load consequence is `ARCHITECTURE.md` §11's.
- **Working-tree hygiene — the maintainer's, before anything is committed.** What is or is not
  pending is a property of the working tree, not of this document: run `git status`, untracked
  files included, and `git ls-files app/schemas`, rather than trusting a sentence here.
  - **The post-2.5 work has to be reviewed and committed by the maintainer**, with its two new files
    — `app/schemas/com.davide.seddio.easygallery.data.MediaIndexDatabase/3.json` and
    `AlbumsViewModelMediaStoreVersionApi28Test` — which a `git add` aimed at modified files misses.
  - **The tracked `.artifacts/tmp/connectedDebugAndroidTest-20261012.logcat.txt` must be restored to
    its committed state before any commit.** It grows when a connected run writes to it, and
    `.artifacts/` is history that must not be edited (`AGENTS.md`).
- **Orchestration lessons, kept short:**
  - A Planner or Coder run sometimes returns no report although its work landed. Re-run it asking
    **only for the report**, rather than redoing the work or reconstructing it from the files.
  - A documentation pass is not complete because it says so. Check its report against the file —
    this block survived one pass still announcing 2.5 as "the first genuinely automatic album".
- **Open questions** are at the end of this document: 1 and 5 are answered and struck through; 3
  (volume resync frequency and size, and the mount/unmount unknown) and 4 (where the coverage
  notices live — location and date) are live and belong to later steps.

---

## Steps

| Step | Delivers | Status |
|---|---|---|
| **2.0** | The three Stage 2 ADRs and this document. No code. | ✅ Complete — 2026-10-08 |
| **2.1** | Index database and schema | ✅ Complete — 2026-10-09 |
| **2.2** | Delta detection | ✅ Complete — 2026-10-09 |
| **2.3** | Tier A extraction | ✅ Complete — 2026-10-09 |
| **2.4** | WorkManager pipeline, "Index now", progress and staleness | ✅ Complete — 2026-10-12 |
| **2.5** | Evaluation engine and time-only rules | ✅ Complete — 2026-10-26 |
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

- [x] A second database class exists with its own file name and version; `EasyGalleryApp` exposes it
      `by lazy` alongside the album database, and no other code constructs either.
- [x] Captured and derived columns are distinct columns, per ADR-0018. The derived place columns exist
      now and are left null until 2.6 — adding them later would be a migration for no reason.
- [x] The exported schema JSON for version 1 of the new database is **committed** under `app/schemas/`.
      `git ls-files app/schemas` now lists
      `com.davide.seddio.easygallery.data.MediaIndexDatabase/1.json`, and ADR-0020 is tracked under
      `docs/architecture/decisions/`. Both sat outside `app/src/` and had to be staged by name,
      which is why this criterion was held open; the trap is in repo memory. **`1.json` has not been
      edited since** — step 2.2's watermark table arrived as version 2 with its own JSON beside it,
      which is what a migration is for.
- [x] The index database file is excluded in `backup_rules.xml` and in **both** the `<cloud-backup>` and
      `<device-transfer>` sections of `data_extraction_rules.xml`, and **no `<include>` element has been
      added to either file**. Verified against the XML rather than against the implementation report,
      and the `-wal` / `-shm` sidecars are excluded beside the database file in all three places.
- [x] The `-wal` / `-shm` question is answered one way or the other, and the answer is written into the
      *Next session starts here* block and into repo memory.
- [x] Store interface + `InMemory*` + DAO tests, following the `AlbumStore` shape.
- [x] **All five `.github/agents/*.agent.md` definitions are corrected in this step.** Each states, in
      its own words, that the album database is the only data the app owns; this step makes that false.
      `AGENTS.md`'s documentation map says why that row is the costliest one to miss, and the Stage 1
      audit found exactly this failure in exactly these files. The stage-closing documentation audit
      would catch it too, but only after six more steps of agents believing it.

**Carried forward at 2.1, and now retired:** `connectedDebugAndroidTest` was reported here as **not
observed as met**. The suite had gone unstable on the one device available — the instrumentation
host SIGKILLed mid-suite, failures moving between unrelated tests, every named failure passing in
isolation — and the one controlled run failed only
`AlbumDetailContentTest.backButtonReturnsToAlbums`, with "No compose hierarchies found in the app",
while logcat showed foreground focus churn to a third-party app. **That verdict no longer stands:**
the suite has since run clean three times on the SM-G990B, which is the observation the
standard asks for. The instability is kept here as history, and its triage rule is in repo memory,
because the next session to meet "No compose hierarchies" should check for focus churn before
suspecting the product.

### 2.2 — Delta detection

**Delivers:** the mechanism that answers "what changed on the device since last time" —
`MediaStore.getVersion()` and `getGeneration(volume)` with the per-row `GENERATION_ADDED` /
`GENERATION_MODIFIED` columns, the API 28–29 date-watermark fallback, and reconciliation of rows
that have disappeared since the last scan. albums.md §6 describes the mechanism; this step builds
it.

> **Unblocked 2026-10-08.** albums.md §10 question 1 is answered by
> [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md), which
> is binding on this step's reconciliation and should be read before it is written.

**Exit criteria**

- [x] The watermark — the version string, and the generation number beside it where that exists — is
  persisted **keyed by volume name** and compared per key, and a change resyncs **only the rows
  carrying that volume's name**, never the whole index. That rule holds at every supported API
  level; what changes with the level is only how many keys can be filled. Where the per-volume
  version call exists, the watermark carries one entry per volume the app can enumerate. Below it,
  the single observable version is the primary volume's, so it is stored under the primary volume's
  name and a change resyncs the primary volume alone, leaving every row keyed to another volume
  untouched. **Fewer observable volumes is not a licence to resync all of them** — the rows nobody
  can currently see are exactly the ones ADR-0020 clause 4 exists to keep. Which API level each
  call arrives at is owned by [albums.md](albums.md) §6 and ADR-0020 clause 4; read them there.
  **The store question this criterion posed is answered, and the answer was no.** It asked whether
  the existing scalar watermark store could carry a key-per-volume shape. It could not: a map keyed
  by volume name is structured, `app_settings` is explicitly allowed another scalar and nothing
  structured, and a sixth preferences file was the wrong way to buy the difference. The watermark
  is a Room table in the **index database**, which took that database to version 2 through a real
  migration — [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)
  Amendment 1 holds the reasoning. `app_settings` is unchanged and still holds one scalar.
- [x] **Checkable, per branch:** a unit test over a fake volume set, for every API branch this step
  writes, shows that a changed watermark for one volume resyncs that volume's rows and leaves a
  second volume's rows in place — including the lowest branch, where the second volume's version is
  not observable at all and its rows must survive a change to the primary's. This is the same fake
  volume set the branch tests below use; do not build a second one.
- [x] **Establish what a mount or unmount does to the version of a volume that is already present**,
  and record the answer in the handoff block above and in repo memory. ADR-0020 clause 4 says why
  it matters: a resync that discards every row on a version change throws away exactly the rows
  that clause preserves, and a card going in or out is the likeliest trigger. It could not be
  settled from the platform documentation, and no device with removable storage is available here,
  so report how far you got instead of assuming either answer. **Reported, not answered** — the
  criterion asked for exactly that. The SM-G990B has no removable storage, so no controlled
  experiment was possible; the evidence and its limit are in the handoff block and in repo memory,
  and the code assumes neither answer.
- [x] The API 28–29 fallback is implemented and its known wrongness is documented where a reader will
  meet it, not only here. The caveat sits on `shouldResyncByDateModified` in
  `data/MediaIndexDeltaDetection.kt`, which is the function a reader meets first.
- [x] **Reconciliation deletes the index row. It does not tombstone it** — ADR-0020. There is no
  staleness window left to handle here, because an automatic album's contents are intersected with
  live media rather than read from the index; that intersection belongs to 2.5, not to this step.
- [x] **Index rows carry ADR-0010's identity and heal rather than being deleted on a move.** A stored
  content URI that misses falls through to the healing key, and a hit rewrites the stored URI in
  place. ADR-0020 forbids a second, independent copy of that key, so this step shares what
  `AlbumMembership.kt` already implements — the shared `data/MediaIdentity.kt` extracted at 2.1 —
  and joins both sides in memory, so the healing triple still carries no database index.
- [x] **A volume that is not currently present is not a deletion.** Reconciliation removes a row only
  for an item missing from a volume that is mounted. ADR-0020 clause 4 names the platform tools and
  the API level each one needs; read them there rather than restating them here. **The API 28 rule
  this step owed is settled: only `VOLUME_EXTERNAL_PRIMARY` counts as present**, because API 28 has
  no volume-enumeration API at all and reconciling anything else would delete rows for volumes
  nobody can observe. It is recorded in the handoff block and in repo memory, with its accepted
  cost — rows for unobservable volumes accumulate for the life of an install that never leaves
  API 28. Every branch, API 28 included, has a unit test over a fake volume set — the
  `Favourites.kt` tiering precedent. Those tests are the only evidence this step can produce,
  because the one device available has no removable storage, and the report says so rather than
  claiming device verification.
- [x] Branching by API level is pure and Android-free so every branch is unit-testable on a plain JVM —
  the `Favourites.kt` precedent. `MediaIndexDeltaDetection.kt` has no `android.*` import at all;
  `MediaIndexReconciliation.kt` is tested the way `AlbumMembership` is, on a plain JVM with a mocked
  `Uri`.
- [x] The Android boundary is covered by Robolectric tests, as the rest of `data/` is — the volume
  gateway, the snapshot provider at `@Config(sdk = P/Q/R)`, the row delta provider, and the version
  providers.

**Landed beyond the criteria, and flagged rather than claimed:** this step removed an SDK guard from
`AndroidMediaStoreVersionProvider` in `data/MediaStoreVersionStore.kt`, a **Stage 1** file it did
not scope, which changes album healing behaviour on API 28–29. It is with the maintainer, described
in the handoff block above and listed as [albums.md](albums.md) §10 question 5. The criteria above
hold with or without it, so it does not keep this step open — but it is not this step's to close
either. **Since answered: the maintainer kept it on 2026-10-30**, as a latent bug fixed; the
question records the reasoning and the evidence's limits.

### 2.3 — Tier A extraction

**Delivers:** reading EXIF location, true date-taken and dimensions for one item, as pure logic plus
the Android boundary, and a batch indexer that walks a delta and writes index rows. No scheduling
yet — the batch runs when something calls it.

**Exit criteria**

- [x] Images go through `ACCESS_MEDIA_LOCATION` + `MediaStore.setRequireOriginal` on API 29+, reusing
  the path `MediaLocation.kt` already established. Video goes through the `MediaMetadataRetriever`
  path. Neither is reimplemented. `originalMediaUriForExifRead`, `mediaCoordinatesFromExifLatLong`
  and the `isAtLeastQ` guard were lifted out of `MediaLocation.kt` as shared helpers and are called
  by the extractor; the guard is now annotated `@ChecksSdkIntAtLeast`, which matters more than usual
  here because `lintDebug` does not run in this workspace and `NewApi` is catching nothing.
- [x] A permission denial, a missing file, and a corrupt header each produce an absent value and never
  an exception escaping the batch — ADR-0001's rule, applied to a loop that will meet all three.
  Both extractor paths and the batch's own `safeExtract` catch broadly and rethrow only
  `CancellationException`; one unit test drives all three cases through a full batch.
- [x] **The date-taken semantics are stated and tested**: what time base is stored, and what a
  zone-less EXIF `DateTimeOriginal` is interpreted as. A capture timestamp compared against a zoned
  range is a classic off-by-one-day defect and 2.5 depends on this being pinned down. **The answer:
  epoch milliseconds UTC; `OffsetTimeOriginal` is used when present, and a zone-less value is
  interpreted in the device zone at extraction time.** It is stated on
  `parseExifDateTakenEpochMillis`, which is the function a reader meets first, and five plain-JVM
  tests cover both branches and the malformed case. **The consequence 2.5 inherits: the stored
  instant for a zone-less photo depends on the zone the device was in when it was indexed, so
  changing that interpretation later is a re-index, not a migration.**
- [x] Batch size and cancellation behaviour are deliberate, because 2.4 will run this under a system
  that can stop it mid-batch. Rows are chunked (64 by default, injectable) and `ensureActive()` is
  checked between chunks, between rows, and between reconciled volumes; watermarks are persisted
  **after** the pass, so a cancelled batch keeps the progress of the rows it did index and
  re-selects the rest. Two tests cancel mid-batch and assert the watermark did not advance, one per
  API branch.
- [x] **The delta is composed in the order read → detect → select**, and the ordering is now
  **structural rather than documented**: `MediaIndexDeltaDetector.detect(sdkInt)` returns the plan
  *and* the pre-pass watermarks together, and persistence moved to a separate
  `persistWatermarks(...)` the batch calls at the end. `detectAndPersist` no longer exists, so the
  wrong order is not expressible. The criterion asked for a test that would fail under the wrong
  order and there is one; it now guards the composition rather than the hazard.
- [x] Pure extraction and parsing are unit-tested on a plain JVM. The two date parsers and the batch
  orchestrator are tested plain; the extractor itself is Robolectric, as the rest of the `data/`
  Android boundary is.

**Landed beyond the criteria, and flagged rather than claimed.** Two things this step changed that
its criteria did not ask for:

- **A behavioural fix to step 2.2's already-shipped reconciliation.** A healed row whose URI sits in
  the legacy `external` namespace now keeps the volume name it was reconciled under, instead of
  being forced to `external_primary`. The old behaviour orphaned secondary-volume rows onto a volume
  name `getExternalVolumeNames()` never returns, so nothing would ever reconcile them again. 2.2's
  criteria still hold; this makes them hold on a device with a card as well as on one without.
- **The namespace split is now one rule in one place**, `data/MediaStoreUriAliases.kt`, and
  `MediaStoreRowDeltaProvider` canonicalises at the source. See the handoff block above for why the
  rule is *any volume ↔ `external`* and not the `external_primary` special case an earlier round
  assumed.

No schema change, therefore no version bump, no migration and no new JSON for this step to commit.

### 2.4 — The pipeline — *first user-visible step, and the natural shipping seam*

**Delivers:** WorkManager (the approved new dependency), the constrained periodic indexing job,
**"Index now"** in Settings as expedited work, in-app progress and staleness, and
`SortType.DATE_TAKEN` finally reading the index.

**Exit criteria**

- [x] Indexing runs under WorkManager constrained to charging, device idle, and battery not low, with
  **no network constraint** — nothing leaves the device (albums.md §6). All four are set on the
  unique periodic request in `data/MediaIndexingWork.kt`, which `MainActivity` establishes once
  media permission is held. **Verified by reading `buildPeriodicRequest()`, not by a test** — the
  instrumented test asserts the *Index now* request's constraints, and nothing asserts the periodic
  ones; that gap is now `ARCHITECTURE.md` §11's.
- [x] "Index now" exists in Settings and runs expedited — **met with a recorded limit, which is the
  honest reading of an unqualified criterion.** It is expedited on **API 31+ only**. Below that,
  expedited work means a foreground service and a notification, and albums.md §10 question 2 is
  open and reserved for the maintainer, so the request degrades to ordinary one-time work rather
  than pre-empting that answer. The limit is in `ARCHITECTURE.md` §11 beside the second half of the
  same fact: an expedited request may carry **only** network and storage constraints, so the Index
  now request deliberately carries none of the periodic job's three. Had it carried
  `requiresBatteryNotLow`, the platform would have rejected the request on every tap.
- [x] Progress and last-indexed state are visible **in-app**, in the Settings screen's Media index
  area. No notification was added; question 2 is untouched. Two properties of that surface are
  albums.md §6's honesty rule applied rather than incidental: the figures are **intersected with
  live media** before counting, so a deleted photo stops being counted without waiting for a scan,
  and **three separate figures** are shown — indexed with a date, indexed with no date, not yet
  indexed — with no combined percentage, because they are incommensurable.
- [x] **`SortType.DATE_TAKEN` reads the index**, at all four item-level sort sites: Timeline View and
  Folder Detail View through `GalleryViewModel`'s derived flows, Album Detail View and the New album
  screen through the same projection passed into `GalleryTransformations.sortMedia`. The third
  state is named explicitly in the code rather than implied — `DateTakenSortState` has
  `IndexedWithDate`, `IndexedWithoutDate` and `NotIndexed`, and the latter two both degrade to
  `dateAdded`, so an item with no EXIF date never churns and never sorts to one end.
  `ARCHITECTURE.md` §11's debt item is **narrowed rather than struck**: folder-level date-taken
  sorting still reads `Folder.dateTaken`, which `MediaStoreDataSource` aggregates from `dateAdded`,
  and `GroupByType.DATE_TAKEN_*` still groups by `dateAdded` — so a date header can disagree with
  the order of the items beneath it. Neither was in this criterion; both are now recorded where a
  reader meets them.
- [x] WorkManager appears in `gradle/libs.versions.toml` and in `ARCHITECTURE.md` §10's tech-stack
  table. The catalog entry landed with the code; **the §10 row did not, and was added on 2026-10-12
  when this step was closed.** It is listed here as met because it now is, not because it was never
  missing — an overview trigger discharged late is still the commonest way this project's
  documentation goes wrong.
- [x] Every new string is in all ten locales. Twelve `settings_index_*` / `settings_media_index`
  names, counted in each of the ten `strings.xml` files.

**Landed beyond the criteria, and the reason this step is Complete rather than nearly so.**

- **A permission-denied run was advancing the watermark past the entire library.**
  `getVersion`, `getGeneration` and `getExternalVolumeNames` are not permission-gated; the row query
  is. A run without media access therefore observed **zero rows**, selected and skipped nothing — so
  the skipped-row clamping from 2.3 never engaged — and then persisted the observed generation as if
  the whole volume had been indexed. Every later run *with* permission would have selected nothing,
  and the library would never be indexed, with no error, no retry, and no recovery short of clearing
  app data. `MediaIndexingWorker` now checks the runtime permission **and** the app-op before it
  does anything, and fails loudly instead. **This was found by running on hardware, not by review,
  and it is the third defect in this stage whose symptom was "indexes nothing and looks like it
  worked."** The other two are in 2.2 and 2.3's records. Treat that pattern as this stage's
  signature failure, not as three coincidences.
- **Liveness is derived from WorkManager, not persisted.** A persisted `is_running` was implemented
  first and removed: it survives process death, so a killed worker left the Index now action
  disabled forever. `MediaIndexingLivenessStore` reads `WorkInfo` instead.
  `SharedPreferencesMediaIndexRunStateStore` still removes the `is_running` key on every write,
  which is the scar.
- **The sixth `SharedPreferences` file was challenged and kept.** `media_index_run_state` passes
  ADR-0018 Amendment 1's three-part test — disposable, worthless without the index, must not be
  backed up — and stays in `SharedPreferences` anyway.
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) **Amendment 2**
  records why that test does not reach it, and
  [ADR-0014](../architecture/decisions/0014-album-database-included-in-auto-backup.md)
  **Amendment 1** records the backup posture, this being the first preferences file ever excluded.
  Do not reopen either without reading both.

**Found while closing this step, not triaged, and not blocking it.** "Index now" and the periodic
job share the unique work name `media-index`, and Index now enqueues with
`ExistingWorkPolicy.REPLACE` — so tapping it **cancels the periodic job**. `MainActivity`
re-establishes the periodic job on the next app open, which bounds the damage to "a user who taps
Index now and never opens the app again has no scheduled indexing". It is in `ARCHITECTURE.md` §11.
It is not a criterion of this step and the step does not wait on it, but 2.5 evaluates after each
index batch, so whoever touches scheduling next should settle it rather than inherit it.
**Since resolved, by commit `d68898d`** — two unique work names; `ARCHITECTURE.md` §11 has the
struck entry and the reasoning. Kept here as the record of what this step closed with.

> **This is where Stage 2 could stop and still have shipped something real**: the index is built,
> date-taken sort is fixed, the phone is not being drained, and no album rule exists yet. Nothing
> after this step is needed for 2.1–2.4 to be worth having.

### 2.5 — Evaluation and time-only rules

**Delivers:** the rule format of ADR-0017 — parser, `Unevaluatable` handling, the `time` clause —
the evaluator that turns a rule into members against the index, and a **test-only** way to create a
rule. The clause's shape, its zone, and the parser's strictness are fixed in ADR-0017 Amendment 1,
written before the code. **Zero geo risk**, but **no user can create an automatic album at the end
of this step**: the Album type dialog's Automatic option stays disabled until 2.7, which is also
the first step that writes `albums.rule` from a shipped code path.

> **Corrected 2026-10-26.** This line used to promise "the first genuinely automatic album", and the
> ordering note below said that if 2.6 disappointed Stage 2 would still ship a working automatic
> album. Neither held as the step is scoped: 2.5 ships the engine, not a way for a user to reach
> it. The honest fallback is that if 2.6 disappoints, **2.7 ships time-only creation without the
> region picker**. The maintainer may choose instead to bring time-only creation forward into 2.5
> or a step of its own; that is a scope change to this plan and is not assumed here.

**Exit criteria**

Ticked only where a unit test, the repository, or a run on hardware proves it. No device was
attached at 2.5's first two verifications, so the last three lines were held open for a device run;
that run, on the SM-G990B at API 36 on 2026-10-26, met them. What it did not reach is listed after
the criteria, and none of it is an exit criterion.

- [x] Rules round-trip through `albums.rule` with **no migration** to the album database.
  `AlbumRuleStorageTest` writes and reads the real column through Room; `app/schemas/` is unchanged,
  so there is no version bump and no new JSON.
- [x] `NULL` rule still means manual. Nothing about existing manual albums changes. A null column
  parses to no rule at all, and stays null after members are added and an exclusion is recorded.
- [x] An unknown clause type, an unknown envelope version, and malformed JSON all produce
  `Unevaluatable`; the album resolves to its additions minus its exclusions, shows the notice, and
  **is never rewritten**. Each of the three has its own test. This is the highest-risk code in the
  step, because the failure it guards against cannot occur on a device that has only moved forward.
  "Never rewritten" is tested byte for byte across rename, add and remove. **The "shows the notice"
  clause is proven only as far as the status that drives it**; the rendering is the Compose tests',
  which ran on hardware below.
- [x] Evaluation composes with ADR-0012: `(rule matches ∪ additions) − exclusions`, with exclusion
  winning, and tombstones surviving re-evaluation — including an exclusion surviving a re-emitted,
  changed index.
- [x] **The rule's matches are intersected with live media before display** — ADR-0020. The index
  supplies keys; the album shows items that `MediaStore` currently returns, resolved by the same
  path `AlbumMembership` already uses. A test proves that an indexed item absent from live media
  does not appear, and that it needs no scan to disappear: the row stays in the index and the item
  is gone anyway.
- [x] Evaluation runs on app open and after each index batch — **not** under the indexing constraints
  (albums.md §6). Proven at the ViewModel by an index-store emission alone, with live media
  unchanged, and separately by `setAllMedia` alone. That the real batch produces that emission was
  proven on hardware by the device test below.
- [x] Parser and evaluator are pure and unit-tested on a plain JVM. `AlbumRuleTest` and
  `AlbumRuleEvaluationTest` run without Robolectric; only the storage test uses it, because it
  exercises Room.
- [x] Every new string is in all ten locales. The four `album_rule_*_notice*` keys, counted in each of
  the ten `strings.xml` files.
- [x] `AutomaticAlbumEvaluationDeviceTest` proves the real pipeline — live `MediaStore`, the
  production extractor, a real batch into the Room index, and one rule album observed before and
  after it. **Passed on the SM-G990B.** The album went from `WAITING_ON_INDEX` before the batch to
  `COMPLETE` after it with no other trigger, its members matched an expectation computed
  independently, and no `ENVIRONMENT:` guard tripped; the figures are in the run's logcat, not here.
  The expectation excluded every item indexed without a date — about 79% of that device's library —
  which is ADR-0017 Amendment 1's no-`dateAdded`-fallback rule holding on real data; the handoff
  block says why the maintainer should know the proportion. **The first run failed on a race in the
  test, not the product:** it selected the album straight after creating it, before the album had
  appeared in `AlbumsViewModel.albums`, and the ViewModel's init collector clears a selection whose
  id is not among the computed albums — so the selected-members flow stayed empty while the album's
  list item already had the right members. The fix is test-side only, selecting the album once it
  has first been observed, and three reruns passed.
- [x] The full `connectedDebugAndroidTest` suite. **Passed on the SM-G990B** with no failures and no
  errors, `MediaIndexingWorkEnqueueTest` included. A `signal 9 (Killed)` in logcat at process
  teardown did not affect the results. The run uninstalled the app when it finished — `AGENTS.md`
  *Running Gradle* — so it was reinstalled, its media permissions re-granted, and it was seen to
  launch with Folders View and Albums View rendering.
- [x] The Compose notice tests in `AlbumDetailContentTest` and `FolderListContentTest`. **Passed in
  that run**, including Album Detail View's notices in RTL at font scale 2, the tile's state
  description, and the RTL row tests. They drive fixtures, which is the limit below.

**Not device-verified at the close.** None of these is an exit criterion above, and none can be seen
in the installed app until something shipped can create a rule album, so they did not hold the step
open. They are carried into the handoff block as residual checks:

- a notice in a real tile or row of the installed app, above all the tile notice at a font scale
  around 2 on a grid of three or more columns, and Album Detail View's notices in the installed app;
- a multi-volume index run, and the mount/unmount question — the device has no removable storage;
- the translations of the four new strings, which have had no native-speaker pass;
- R1 in production composition, below;
- every API-level guard, checked by reading only, because `lintDebug` cannot run.

**Remediation round 1 ran on hardware with the rest, with one exception.** R2 is ViewModel
behaviour and rests on its unit tests; R3's reworded notices rendered in the Compose tests above.
**R1 is still covered only by unit tests**: its gate lives in composables — `MainActivity` and
`FolderListScreen` check `isMediaLoaded` before forwarding — and the device test constructs
`AlbumsViewModel` directly, so nothing on hardware exercises the forwarding in production
composition.

**Landed beyond the criteria, flagged rather than claimed.** None of these was asked for; each was
needed for a criterion above to be true rather than merely tested.

- **`hiddenMemberCount` now counts additions only.** A rule match is not a stored row, so "stored
  minus resolved" would subtract matches from a count they were never in and report hidden members
  that do not exist. `AlbumDetailContent` therefore no longer takes a stored and a resolved count;
  it takes the hidden count directly.
- **Removing an item from a rule album always records an exclusion**, even when the item is there
  only as an addition. Deleting the addition row alone would be enough today and wrong later: once
  the item's date is indexed and falls in range, it would reappear as a match the user had already
  removed. As first landed this applied to evaluatable rules only; R2 below extended it to any
  stored rule.
- **No orphan or URI-rewrite write-back while live media is still null.** Before `MediaStore` has
  been read every membership looks unresolved, and the empty initial list used to mark every one of
  them orphaned. As first landed the guard never engaged in production, because the caller handed
  over an empty list rather than nothing; R1 below is what made it real.
- **The tile and row notices are announced through `stateDescription`.** The tile's semantics set
  its content description to the album name, so TalkBack never read any visible notice text. This
  also fixed the Stage 1 hidden-items notice, which had never been announced either.
- **The evaluator joins index URIs and live URIs through `canonicalMediaStoreUriForJoin` on both
  sides**, because an index row may be spelled with a volume name where live media uses `external`.
  The membership side **deliberately stays raw**, per ADR-0010 Amendment 1; canonicalising it would
  change which album rows count as direct matches.
- **The live lookup was extracted into `LiveMediaLookup`** in `data/MediaIdentity.kt`, so membership
  and rules share one implementation of the direct-then-healing match, as ADR-0020 clause 3
  requires. Index reconciliation still holds a private copy; that is `ARCHITECTURE.md` §11's, not
  fixed here.
- **The unevaluatable notice is reachable only through data until 2.7**, because the Automatic
  option is still disabled and no shipped path writes a rule. Until then the notice is exercised by
  tests alone; they ran on hardware at the close.

**Remediation round 1, after a review of the work above.** Three defects, each fixed in code and
unit-tested; what the device run reached for each is stated after the exit criteria above.

- **R1 — app open no longer hands `AlbumsViewModel` an empty list as if it were the library.**
  `GalleryViewModel._allMedia` starts as `emptyList()`, and `MainActivity` and `FolderListScreen`
  forwarded it at once. So on every cold start a rule album was evaluated against "the device has no
  media" and reported `COMPLETE` with no members — this stage's signature failure, "looked like it
  worked", reproduced in the evaluator — and the null-media write-back guard above never ran, so
  manual memberships were briefly written back as orphaned and then healed. The fix is
  `GalleryViewModel.isMediaLoaded`: false until `loadFolders()` has assigned the first result of
  `getAllMedia()`, never reset by a reload, and still false if that first load throws. Both callers
  forward to `setAllMedia` only once it is true. **The gate is a load signal, not emptiness**,
  because a device with genuinely no media must still deliver an empty list once loading finishes
  and get an honest empty album. The ViewModel was left as it was: it already told null from a list,
  and the defect was the caller saying "loaded" too early. `MediaIndexingViewModel.setLiveMedia` is
  deliberately **not** gated by this change; `ARCHITECTURE.md` §11 records what that leaves.
- **R2 — removal from an unevaluatable album now records an exclusion.** ADR-0012 makes a removal a
  positive persisted fact. Deleting only the addition row from an album whose rule this build cannot
  read left nothing to stop a newer build, which can read it, from re-matching the item. The test is
  now whether a rule is **stored** — `hasStoredRule`, `album.rule != null` — not whether it parses.
  Manual albums are unchanged. **One known gap, recorded by the implementer:** in `removeMembers`, a
  membership id whose item has no live match takes the plain delete-the-row path, because there is
  no `MediaItem` to build an exclusion from. It is unreachable in production because `removeMember`
  and `removeMembers` have no production caller: the only UI path is `removeMembersByMedia`
  (`ui/AlbumDetailScreen.kt`), which has a `MediaItem` in hand. Nothing enforces that.
- **R3 — the waiting notice no longer claims a scan is running.** `album_rule_waiting_notice` and
  `album_rule_waiting_notice_short` said photos were "still being scanned", which is false in the
  usual waiting case: no pass running, the periodic job held by its constraints. They now say some
  photos and videos have not been scanned yet, and point at Settings, Media index for the current
  state. The nine translations were reworded without a native-speaker pass; ja, ko and ar want one
  first. The UI vocabulary entry has since been renamed **Incomplete-album notice**, keeping
  "Still-scanning notice" as an alias; the `testTag` `album_rule_waiting_notice` keeps its older
  name because tests depend on it. Two androidTest method names still use the old name, as harmless
  leftovers: `waitingAlbumShowsStillScanningNoticePointingToSettingsWithoutAnyFigure` in
  `AlbumDetailContentTest` and `albumsViewShowsStillScanningNoticeWithoutAnyFigureOnTileAndRow` in
  `FolderListContentTest`.

### Between 2.5 and 2.6 — the container zero date, and question 5's evidence

Not a step, and not a reopening of 2.5: a defect found after 2.5 closed, by looking at a real
library, and a test written to settle an open question. Recorded here because the handoff block is
rewritten every session and this is the record that outlives it.

- **The defect.** A video container writes a creation time of 0 for "no date", which
  `METADATA_KEY_DATE` renders as `19040101T000000.000Z`, and 2.3's video parser stored it as a real
  capture date. Measured on a Samsung SM-G781B (Android 13, API 33): most videos carried it, and
  `MediaStore`'s `DATE_TAKEN` was null for all of them. Such a video counted as dated in Settings,
  sorted into 1904 under date-taken sort, and could match a `time` rule spanning 1904. The platform
  rendering was established from AOSP source, not on hardware.
- **The fix.** `parseVideoDateTakenEpochMillis` treats exactly that instant, and its textual
  renderings, as absent; one second later, one day later and a real 1950 date stay valid.
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 1
  now says such a sentinel counts as no date.
- **The migration.** `MIGRATION_2_3` nulls the stored sentinel, reusing the parser's constant, and
  takes the index database to version 3: no re-extraction, no resync, no destructive fallback, an
  unchanged identity hash, and an exported `3.json` that must be committed with the change.
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 3 records
  why a migration rather than read-time normalisation or a forced resync.
- **Question 5's evidence.** `AlbumsViewModelMediaStoreVersionApi28Test` drives the real version
  provider under a real `AlbumsViewModel` at API 28; on it the maintainer answered
  [albums.md](albums.md) §10 question 5.
- **Verification: compiled and unit-tested only.** The migration has Robolectric tests, including
  a reopen through the production builder, and the parser has plain-JVM tests. The full gate has not
  run over this work: not the connected suite, and not the v2→v3 migration of a real index on a
  device. The handoff block carries both to the next gate.

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
- `MediaIndexReconciler` resolves live media through `LiveMediaLookup` and holds no private by-URI
  or by-healing-key map; its URI key — raw or canonical — is chosen explicitly, and tests pin which
  rows count as direct matches under it. This closes the
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md)
  clause 3 breach recorded in `ARCHITECTURE.md` §11. **Proposed 2026-10-26 and accepted by the
  maintainer on 2026-10-30**, here rather than as a standalone step, because 2.6's resolve pass is
  the next change to the index's write side, and because it has to land before 2.7 makes rules
  user-writable: a reconciliation mistake then shows up in a user's album.

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
- **Date coverage is stated too**, for time albums: a date-coverage notice counts the live items
  indexed without a date, says what it counts, and is never presented as pending work — such an
  item will not be indexed into a date by waiting. Its strings are in all ten locales. Added
  2026-10-30 by [albums.md](albums.md) §8 decision 21, which is the answer to undated items instead
  of a second date source.
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
  no size budget and no coastline. Doing them first means the evaluation engine is built and tested
  before any geo work, so it is not stranded if the geo dataset turns out to be too large, too slow,
  or too inaccurate. **It does not by itself put an automatic album in a user's hands** — 2.5 has no
  creation UI. If 2.6 disappoints, the fallback is that 2.7 ships time-only creation without the
  region picker, unless the maintainer has already brought time-only creation forward (see 2.5's
  correction note).
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
- The two Room schemas are both exported and **committed** — every version's JSON, in both
  directories. `git ls-files app/schemas` against the directory listing settles it; do not count
  files from a sentence.
- Every new user-facing string is in all ten locales, and every string removed is removed from all
  ten in the same change.
- Stage 2's vocabulary has moved into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).
- `docs/architecture/ARCHITECTURE.md` has been updated for everything that triggers it — at minimum
  the new `data/` files, the second database in §6, the WorkManager dependency in §10's tech-stack
  table, the Known debt item in §11 that step 2.4 resolves, and links to ADRs 0017–0020 in the
  sections they govern. The deferral was recorded for 0017–0019 at step 2.0 and applies to ADR-0020
  for the same reason — it was adopted ahead of the work it governs — and is discharged here.
  **Partly discharged at 2.1, 2.2, 2.3 and 2.4**: the `data/` files of all four steps, the second
  database in §6 with its tables, schema version and migrations, and the links to ADR-0018 and
  ADR-0020 are in, as are ADR-0018's amendments — the third added after 2.5 — and ADR-0014's. 2.4
  added the WorkManager row to §10, the index's
  two paths through the data flow in §2, the `MediaIndexingViewModel` and `MediaIndexDateTakenState`
  entries in §3, the sixth preference store and the `sharedpref` backup exclusion in §6, and
  narrowed — rather than struck — the `SortType.DATE_TAKEN` item in §11. ADR-0017 went in with 2.5,
  on the `data/` rows of the rule files in §3, and ADR-0019 goes in with 2.6, the step that builds
  what it governs.
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

1. ~~A deleted photo's index row — albums.md §10 question 1.~~ **Answered 2026-10-08 by
   [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md); step
   2.2 is unblocked.** Kept listed, struck through, until this plan is archived, so a session that
   remembers the block can see it was lifted rather than dropped.
2. A progress notification during indexing — albums.md §10 question 2. **Step 2.4 shipped in-app
   progress only and did not pre-empt it**, which is also why "Index now" degrades to ordinary work
   below API 31. Every later progress surface inherits the same cap until it is answered.
3. **How often does a *volume* resync happen, and how big is the primary volume's?** A resync is
   scoped to the volume whose version changed — [albums.md](albums.md) §6,
   [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md)
   clause 4 and step 2.2's criteria above own that rule, and this question no longer disputes it.
   What scoping does not do is make a resync cheap: on a device with no removable storage every row
   is on the primary volume, so the bound saves nothing there and a primary version change still
   costs re-opening every indexed file. So the open part is the frequency and the size. What moves
   the primary volume's version — an OS update, a vendor media-scanner quirk, a remount — and how
   often? If it is rare, nothing further is needed. If it is not, a mitigation has to be designed
   rather than discovered, and the obvious candidate is to treat a version change as "stop trusting
   the generation watermark" rather than "re-read this volume's rows", which belongs beside the
   watermark it would qualify. **Still open after 2.4**, which put the batch on a schedule without
   measuring either the frequency or the size; measurable on the SM-G990B, and now measurable under
   the real schedule rather than only under a manual call.

   **The related unknown has outlived step 2.2 and lives here now:** whether attaching or detaching
   a *secondary* volume perturbs the *primary* volume's version string. Step 2.2's criteria asked
   for it to be established or reported, and it was reported — the one device available has no
   removable storage, so no controlled experiment was possible. The code assumes neither answer.
   It needs a device with a card, and now that 2.4 schedules the job unattended, a wrong assumption
   costs full re-indexes without anyone watching.
4. **Where does the coverage notice live?** ADR-0019 requires the user to be told how much of their
   library has a location, but not where. Album Detail View is the obvious home; whether the Album
   type dialog or the place picker should also warn before an empty album is created is a design
   question for 2.7, and depends on the number measured in 2.6. **Since 2026-10-30 the same question
   applies to the date-coverage notice** of albums.md §8 decision 21, and the two should be answered
   together, so a time album and a region album explain their gaps in the same place.
5. ~~**Does an unevaluatable album need its own string, or does `album_hidden_items_notice` cover
   it?**~~ **Answered at 2.5: it needed its own — and so did the waiting state.** They are different
   conditions — "members are waiting to heal" versus "this rule needs a newer app" — and conflating
   them was the cheap wrong answer this question warned about. Four keys landed in all ten locales:
   `album_rule_unevaluatable_notice` and `album_rule_waiting_notice` in full in Album Detail View,
   and their `_short` forms on the album tile and row. The hidden-items notice still shows beside
   either when additions are unresolved. **The waiting notice carries no count and no progress, by
   design**: it points to Settings, so the Settings Media index area stays the only progress
   surface and §6's honesty rule has one place to be kept rather than two. Kept listed, struck
   through, until this plan is archived.

---

## Related

- [albums.md](albums.md) — the epic. §3 membership, §4 the kinds, §6 the index, §7 staging and
  progress, §8 decisions, §9 consequences, §10 open questions, §11 non-goals.
- [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
  [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md),
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md) — the
  decisions binding on this stage, in addition to those binding on Stage 1.
- [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md) —
  how this document is maintained, and by whom.
