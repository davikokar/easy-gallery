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

- **Next step: 2.4 — the pipeline.** First user-visible step of the stage, and the natural shipping
  seam.
- **Depends on:** steps 2.1, 2.2 and 2.3, all **Complete**. 2.3 built the batch; 2.4 is what calls
  it. WorkManager is already approved as a dependency — [albums.md](albums.md) §8 decision 18 — so
  adding it needs no further ask, but it does need a `gradle/libs.versions.toml` entry and a row in
  `ARCHITECTURE.md` §10's tech-stack table, which is an overview trigger. ADR-0018 governs the
  schema, ADR-0020 governs what a row means, and ADR-0001 governs how files are opened; 2.3 changed
  none of them.
- **The trap the previous version of this block prescribed no longer exists, and following it now
  would reintroduce a data-loss bug.** `MediaIndexDeltaDetector.detectAndPersist` **is gone**. It
  was split into `detect(sdkInt)`, which returns the plan *and* the pre-pass watermarks together,
  and `persistWatermarks(...)`, which the batch calls at the **end** of the pass. The composition is:

  ```
  val detection = detector.detect(sdkInt)          // plan + pre-pass watermarks, inseparable
  val rows      = selectRowsForReindex(detection.plan, detection.storedWatermarks, observedRows)
  …                                                 // extract and upsert
  detector.persistWatermarks(clampedWatermarks)     // last, after the rows are written
  ```

  The old order is no longer expressible, which is what the 2.3 criterion asked for. **Why 2.4 in
  particular must not undo it:** persisting first advances a volume's generation watermark past rows
  that were never indexed, and WorkManager can stop a job mid-batch, so a cancelled run would leave
  those rows permanently unselectable until something else moved the version string. 2.4's job is to
  call `MediaIndexTierABatchIndexer.indexDelta(sdkInt)`, not to recompose these pieces around it.
- **A row the delta selects but the live snapshot does not contain is normal, not an error.** The
  live media list and the per-volume delta query are taken at different moments, so anything that
  arrives between them — a camera capture, a screenshot, a download, an item leaving pending or
  trashed state, a volume mounted mid-pass — is in one and not the other. An earlier attempt made
  this throw; it was reachable by taking a photo while indexing ran, and under WorkManager it would
  have become a job that failed and retried forever. The batch now **counts** those rows as skipped
  and **clamps** that volume's watermark to strictly below the lowest skipped row, so the row is
  re-selected next pass while the rows that *were* indexed keep their progress.
  `MediaIndexTierABatchResult.skippedRows` is the number to surface or log, never to treat as a
  failure. Two conditions still fail loudly, deliberately, because neither can be a race: a selected
  row whose URI does not parse as a `MediaStore` URI, and a **total** miss when the selection is
  non-empty — the latter is the zero-indexed-rows bug that survived two review rounds.
- **The namespace split is one rule in one place: `data/MediaStoreUriAliases.kt`.** Live media always
  carries `content://media/external/…`; observed delta rows carry `content://media/<volume>/…`. The
  rule is **any volume ↔ `external`**, not an `external_primary` special case — an earlier round
  assumed the special case, which would have indexed **zero** rows on any secondary volume while
  every test stayed green, because the fakes agreed with each other rather than with production.
  No device with removable storage is available here, so that remains reasoned from the code and the
  platform's namespaces rather than observed.
  `MediaStoreRowDeltaProvider` canonicalises at the source, and a row still keeps its own concrete
  volume name; do not add a second spelling rule anywhere.
- **Also learned at 2.3, and 2.4's to act on:** an item that is indexed but whose date-taken is null
  — no EXIF date at all, and extraction deliberately does **not** fall back to `dateModified` — is a
  third state, distinct from "not yet indexed" and from "indexed with a date". It never becomes
  non-null. 2.4's `SortType.DATE_TAKEN` degrade rule is written for two states and must name the
  third; the criterion under step 2.4 below now says so.
- **Learned in 2.2, and binding on what follows:**
  - **The per-volume watermark lives in the index database, not in `app_settings`.** It was
    implemented there first and moved: it is a map keyed by volume name, and `AGENTS.md` allows
    that file another scalar and nothing structured. The reasoning and its consequences are
    [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 1 —
    a watermark is index metadata, recomputable, worthless without the index, and must not be
    backed up, so it belongs with the data it describes.
  - **The index database is therefore at version 2, through a real `Migration(1, 2)` with no
    destructive fallback**, and both `1.json` and `2.json` are exported. That is the precedent:
    **any later schema change is another bump, another migration, and another committed JSON.**
    `fallbackToDestructiveMigration` is not available to this database — a user's index is cheap to
    lose, but silently dropping the table also drops the watermark that would have triggered the
    rebuild. 2.3 needed no schema change, so the precedent is still untested by a second use.
  - **The mount/unmount question could not be settled, and is reported rather than answered.** The
    one device available, the SM-G990B, has no removable storage: `adb shell sm list-volumes all`
    reports only `private mounted null` and `emulated;0 mounted null`, so no controlled experiment
    against a second volume was possible. What a mount or unmount does to an already-present
    volume's version string is therefore still unknown, and the code does not assume either answer:
    reconciliation refuses to treat an absent volume as a deletion, which is safe under both. It is
    in repo memory under the same heading. **Do not close it by inference** — it needs a device with
    a card, and 2.4's scheduling is where a wrong assumption would start costing full re-indexes.
  - **The API 28 presence rule is settled: only `MediaStore.VOLUME_EXTERNAL_PRIMARY` counts as
    present.** API 28 has no volume-enumeration API at all, so reconciling anything else would mean
    deleting rows keyed to volumes nobody can currently observe — exactly what ADR-0020 clause 4
    forbids. The cost is accepted and recorded: a device that never leaves API 28 never observes
    those volumes, so retained rows can accumulate for the life of the install. Also in repo memory.
  - **`lintDebug` cannot run in this workspace**, which is pre-existing and unrelated to Stage 2.
    The failure and its consequence now live in `AGENTS.md` *Running Gradle*, which owns them, and
    in repo memory; this stage does not restate them. What is Stage 2's own: 2.3 calls
    `MediaStore.setRequireOriginal`, which is API 29+, and step 2.2 shipped two unguarded
    `MediaStore` calls that lint would have caught had it started. **The 2.3 guard was checked by
    reading it** — the call sits behind `isAtLeastQ()` in `data/MediaLocation.kt`, now annotated
    `@ChecksSdkIntAtLeast(api = Q)` so that the guard is machine-recognisable the day lint does
    start. Every later step inherits the same obligation: read the guard, do not expect a tool to
    find it.
  - **The open `2.json` tracking item from 2.2 is closed, and the check that closed it is what
    carries forward.** `git ls-files app/schemas` is what settles whether an exported schema is
    tracked; `git status` does not, because Room writes one directory per database class and a
    `git add` aimed at `app/src/` reaches neither. Run it after any schema change rather than
    trusting a sentence here about what is committed today.
- **With the maintainer, and not to be acted on by the next session:** step 2.2 removed the
  `if (SDK_INT < R) return null` guard from `AndroidMediaStoreVersionProvider` in
  `data/MediaStoreVersionStore.kt` — a **Stage 1** file the step did not scope. The API fact behind
  the removal is right: `MediaStore.getVersion(Context)` is available from API 12. The consequence
  is a behaviour change to a shipped feature: `AlbumsViewModel.didMediaStoreVersionChange()`
  previously returned `false` unconditionally on API 28–29, and album membership healing
  re-evaluation now activates on those devices for the first time. There is a Robolectric test at
  `@Config(sdk = P)` at the provider level and **none at the ViewModel level**, and no device at
  that API level is available. It is the maintainer's call whether this is a latent bug fixed or a
  change to revert; it is also logged as [albums.md](albums.md) §10 question 5. **Do not change the
  code either way.**
- **Still true, from earlier sessions:**
  - **The app's live-media set is already the thing albums resolve against.**
    `AlbumsViewModel.computeAlbum` joins stored rows against the list `GalleryViewModel` loads and
    pushes in through `setAllMedia`. ADR-0020's live join is that pattern reused, not a new one, and
    2.5's intersection answers to the same shape.
  - **The shared identity exists and is shared.** `data/MediaIdentity.kt` holds `MediaIdentity` and
    `MediaHealingKey`; album membership and the index row model both build on it, and reconciliation
    heals through it with an in-memory hash join rather than a `WHERE` over the healing triple — so
    there is still **no index on those three columns and none is wanted**. Adding one is a Room
    `identityHash` change, which is now a version 3.
  - **Step 2.5 needs no migration to the album database.** The column ADR-0017 fills already exists
    and is unused. Do not plan a migration.
  - **WorkManager belongs to step 2.4**, not before — albums.md §8 decision 18. Steps 2.1–2.3 kept
    it out, and the batch they built runs when something calls it.
  - **A note for step 2.6, cheap now and a migration later:** a row whose coordinates fall outside
    [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md)'s
    distance cap resolves to null country, null region and null place — **indistinguishable from
    "not yet resolved"** — so a resolve pass selecting on "derived columns are null" would retry
    those rows forever. Not worth a column today; worth knowing at 2.6.
- **Gates as 2.3 closed:** `testDebugUnitTest` **311 tests, 0 failures**; `connectedDebugAndroidTest`
  **76 of 76** on the SM-G990B. 2.1's suite instability is history rather than an open item — see
  the note under step 2.1 below.
- **Blocked, and needing the maintainer:** nothing in 2.4 is blocked, except that its progress
  surface is capped at in-app until albums.md §10 question 2 is answered. The
  `MediaStoreVersionStore` guard above is the maintainer's and holds up no step.
- **Open but blocking nothing yet:** the remaining questions at the end of this document.

---

## Steps

| Step | Delivers | Status |
|---|---|---|
| **2.0** | The three Stage 2 ADRs and this document. No code. | ✅ Complete — 2026-10-08 |
| **2.1** | Index database and schema | ✅ Complete — 2026-10-09 |
| **2.2** | Delta detection | ✅ Complete — 2026-10-09 |
| **2.3** | Tier A extraction | ✅ Complete — 2026-10-09 |
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
isolation — and the one controlled run gave 75 of 76, failing
`AlbumDetailContentTest.backButtonReturnsToAlbums` with "No compose hierarchies found in the app"
while logcat showed foreground focus churn to a third-party app. **That verdict no longer stands:**
the suite has since run **76 of 76** three times on the SM-G990B, which is the observation the
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
either.

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

- Indexing runs under WorkManager constrained to charging, device idle, and battery not low, with
  **no network constraint** — nothing leaves the device (albums.md §6).
- "Index now" exists in Settings and runs expedited.
- Progress and last-indexed state are visible **in-app**. The notification question at the end of
  this document decides whether a notification is added; until it is answered, in-app only.
- **`SortType.DATE_TAKEN` reads the index.** What it does instead today is the Known debt item in
  `ARCHITECTURE.md` §11, and
  [ADR-0008](../architecture/decisions/0008-camera-folder-default-sort-order.md) records how it got
  there; resolving a §11 item is itself an overview trigger. An item not yet indexed must degrade to
  the current behaviour rather than disappearing or sorting to one end. **There is a third state,
  found at 2.3 and not covered by that rule: an item that *is* indexed and whose date-taken is
  null**, because it carried no EXIF date at all — extraction does not fall back to `dateModified`,
  so the column stays null rather than being filled with an approximation. It is indistinguishable
  from "not yet indexed" by the column alone and it will never become non-null, so the degrade rule
  has to name it explicitly or those items will churn or sort to one end forever.
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
- **The rule's matches are intersected with live media before display** — ADR-0020. The index
  supplies keys; the album shows items that `MediaStore` currently returns, resolved by the same
  path `AlbumMembership` already uses. A test proves that an indexed item absent from live media
  does not appear, and that it needs no scan to disappear.
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
- The two Room schemas are both exported and **committed** — which is now three JSON files across
  two directories, because the index database reached version 2 at step 2.2.
- Every new user-facing string is in all ten locales, and every string removed is removed from all
  ten in the same change.
- Stage 2's vocabulary has moved into
  [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).
- `docs/architecture/ARCHITECTURE.md` has been updated for everything that triggers it — at minimum
  the new `data/` files, the second database in §6, the WorkManager dependency in §10's tech-stack
  table, the Known debt item in §11 that step 2.4 resolves, and links to ADRs 0017–0020 in the
  sections they govern. The deferral was recorded for 0017–0019 at step 2.0 and applies to ADR-0020
  for the same reason — it was adopted ahead of the work it governs — and is discharged here.
  **Partly discharged at 2.1, 2.2 and 2.3**: the `data/` files of all three steps, the second
  database in §6 — now two tables at version 2 — and the links to ADR-0018 and ADR-0020 are in, as
  is ADR-0018's Amendment 1. ADR-0017 goes in with 2.5 and ADR-0019 with 2.6, which are the steps
  that build what each governs; WorkManager and the §11 item remain 2.4's.
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
2. A progress notification during indexing — albums.md §10 question 2. Step 2.4 waits on it.
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
   watermark it would qualify. **Still open after 2.2**, which built the watermark but measured
   neither the frequency nor the size; measurable on the SM-G990B, and worth measuring rather than
   assuming it is rare before 2.4 schedules anything.

   **The related unknown has outlived step 2.2 and lives here now:** whether attaching or detaching
   a *secondary* volume perturbs the *primary* volume's version string. Step 2.2's criteria asked
   for it to be established or reported, and it was reported — the one device available has no
   removable storage, so no controlled experiment was possible. The code assumes neither answer.
   It needs a device with a card, and 2.4 is where a wrong assumption would start costing full
   re-indexes.
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
  [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md),
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md) — the
  decisions binding on this stage, in addition to those binding on Stage 1.
- [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md) —
  how this document is maintained, and by whom.
