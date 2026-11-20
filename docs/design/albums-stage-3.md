# Albums Stage 3 — people albums

> **Owner: Planner. Mode: routed.** This is a *stage plan*, not a description of the app. It exists
> for the length of Stage 3 and is archived when the stage closes, or paused if it stops first — what
> both mean, and the rest of the rules for this kind of document, are in
> [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md).
> The epic itself is [albums.md](albums.md); **its §7 table is the single source of truth for where
> the epic stands**, and this document never restates it.
>
> **This is a draft.** Written 2026-11-17. Already decided: [albums.md](albums.md) §8 decisions 9
> (clarified 2026-11-17), 13 (as amended 2026-11-17), 14, 15, 33, 35, 40 (as amended 2026-11-17
> and 2026-11-18), 41, 42 and 43 (2026-11-18), and 44, 45 and 46 (2026-11-18, delegated by the
> maintainer; 44, and 45's OpenCV dependency, confirmed by him the same day), and 47 (2026-11-19,
> the answers to group E), and 48 (2026-11-20, two findings of step 3.2's review). The steps, the
> exit criteria and every recommendation below are proposals until the maintainer answers the open
> questions and step 3.1 records the decisions. Group A was answered on 2026-11-17. Where something
> was not known, it is a question, not a guess. ~~Nothing here names a face model, a licence or an
> API behaviour as fact: each is a question for the spike.~~ **Amended 2026-11-18:** the spike has
> closed. What it read and measured is under *Licence findings* and *Spike outcome*; those are
> findings, and ~~no model, threshold or runtime is chosen until step 3.1~~ the model, threshold and
> runtime were chosen at step 3.1 on 2026-11-18, by delegation — [albums.md](albums.md) §8
> decisions 44–46.
>
> **Amended 2026-11-19 — no longer a draft.** Step 3.1 closed: the maintainer approved its five
> records — [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md),
> [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md),
> [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md),
> [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4 and
> [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5 — and
> the exit criteria below were confirmed. Steps 3.2 to 3.8 are still refined as each starts; the
> open questions still open are marked so below.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff, as in Stages 2
> and 4.

- **2026-11-17 — plan drafted; Stage 3 not started.** The stage moves to In progress when its first
  binding deliverable lands ([albums.md](albums.md) §7, *Who updates this*); step 3.0's figures are
  not one, as recorded for Stage 4.
- **2026-11-17, later — the maintainer described the People picker and answered four points:**
  [albums.md](albums.md) §8 decisions 40 (unlabelled thumbnails, no person names, the two-images
  threshold, merge — since withdrawn, the app's thumbnail) and 41 (anchoring a person to a photo, a
  direction), and §10 question 12 amended.
- **2026-11-17, later still — group C answered by the maintainer.** He withdrew the merge, having
  misunderstood the question; ruled out split and undo; limited people to images; decided that a
  face's position is not face data; chose one anchor per person; and accepted the Orchestrator's
  suggestion for an anchor that cannot be followed. Recorded in [albums.md](albums.md) §8 decision
  9's clarification, decisions 40 and 41 amended, and decision 42; §10 question 12 is closed.
  Group C's one open detail is the anchor's minimum box overlap, measured by 3.0. None of this
  unblocks 3.0.
- **2026-11-17, later again — group A answered by the maintainer** (see *Open questions*). The
  spike chooses the models and the runtime (A1, A2). Bundled in the app is preferred, and the face
  models may add at most 50 MB to the download size (A3). The SM-G781B (API 33) is measured beside
  the SM-G990B, and below the threshold the People criterion is hidden (A4). Only models licensed
  for commercial use and redistribution inside an app are measured, their terms read before any
  download (A5). The spike lives in `tools/face-model-spike/` (A6). Photos, crops, embeddings and
  labels may go to the desktop for the spike only, under gitignored paths, and are all deleted at
  the end (A7). More photos were loaded onto the SM-G990B (A8).
- **2026-11-18 — step 3.0 closed by the maintainer; the spike's private data deleted.** Its
  findings are under *Spike outcome* and *Licence findings* below, written to stand on their own
  without the notebook they come from. Stage 3 stays Not started, because a spike's
  figures are not a binding deliverable ([albums.md](albums.md) §7, *Who updates this*). The same
  day the maintainer amended [albums.md](albums.md) §8 decision 40: the picker keeps the two-image
  threshold and **sorts its thumbnails by photo count, most first**, because the spike showed that
  most offered thumbnails are people seen in two photos. Every face crop, embedding, label and
  photo copy was deleted, on the desktop, in the maintainer's Downloads folder and on the
  SM-G990B; the SM-G781B never received anything (*Spike data deleted*).
  - **The spike's sources are committed and then removed from the tree, as Stage 4's were:**
    `NOTES.md` (the notebook), the harness sources and the scripts, none of which holds private
    data. They are in git history: `git log -- tools/face-model-spike`.
- **2026-11-18, later — the training-data question answered.** The maintainer accepts the
  training-data risk for SFace, and for SFace only: [albums.md](albums.md) §8 decision 43. It does
  not choose SFace as the shipping model, nor its threshold, runtime or size route. It does not
  cover the detector: YuNet's caveat was not part of the question.
- **2026-11-18, later still — the rest of 3.1's spike items decided, by delegation.** The
  Orchestrator listed step 3.1's open items to the maintainer and offered to ask them as questions;
  he answered, verbatim, "I leave them to you." The Orchestrator took them, and they are recorded
  as delegated decisions, each with its reason, in [albums.md](albums.md) §8: **44**, YuNet's
  training-data risk accepted; **45**, SFace fp32 with average linkage at cosine 0.50, OpenCV
  bundled and trimmed for arm64-v8a, and a capability check in place of a lowest device; **46**,
  the anchor's IoU ≥ 0.5, the 40 px cut-off, the picker's order and non-faces. The maintainer can
  reverse any of them. Step 3.1 is **In progress**; Stage 3 stays Not started until its first ADR
  is adopted. B2 (incremental clustering) stays open for 3.4, and D1 and D2 stay open.
- **2026-11-18, later again — the maintainer confirmed two delegated points.** He confirmed
  decision 44's acceptance of YuNet's training-data risk, and decision 45's addition of OpenCV as a
  third-party dependency — the `AGENTS.md` ask-before-doing approval. Both are recorded in
  [albums.md](albums.md) §8 as "Confirmed by the maintainer 2026-11-18"; the decisions stay on
  record as delegated. **The confirmation covers the library only**: not the permissions its merged
  manifest may add, which are checked when it lands, and not the Play-store packaging questions.
- **2026-11-18, later again — step 3.1's ADRs drafted by the Planner, for approval.** None is
  adopted; each is labelled *Proposed*, a label the instructions' status vocabulary does not have
  (it defines only `Active` and `Disabled`):
  1. [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) — anchoring
     a person to a photo. The first ADR of the stage.
  2. [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5 —
     Tier C storage, `Migration(4, 5)`, the two stamps, the per-image states, deleting face data.
  3. [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4
     — the `people` clause, one per person, and the two causes of unevaluatable found at evaluation.
  4. [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) — the
     models, the runtime, the capability check, where the pass runs, licences, and what is to be
     verified, by whom and when.
  5. [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) — the
     disclosure, the switch that deletes face data, and how decision 9's obligations are checked.
  6. The Play Data Safety check, recorded in step 3.1 below as what must be checked and by whom; not
     an ADR, and not answered.
  7. D2, ADR-0013's revisit: a recommendation under *Open questions* D2; no ADR change. **Answered
     2026-11-19, as recommended.**
- **2026-11-19 — group E answered by the maintainer, as recommended.** In his words, "go with the
  recommendations": all eleven questions, E1 to E11, are answered as the record raising each
  recommended, and each draft's text is settled to match — [albums.md](albums.md) §8 decision 47,
  and group E below. **The drafts stay Proposed:** answering their questions is not approving them,
  and the maintainer's approval is asked for separately. Until it is given the README index is
  unchanged and Stage 3 stays Not started. One answer changes a later step: by E3, step 3.3 uses
  OpenCV's published artifact untrimmed, so the trimming of decision 45 stays margin, not work.
- **2026-11-19, later — the five drafts approved by the maintainer; step 3.1 closed; Stage 3 In
  progress.** ADRs 0022–0024 are `Active`, with ADR-0017 Amendment 4 and ADR-0018 Amendment 5
  marked adopted inside their records, and the README index changed with them. An adopted ADR is a
  binding deliverable, so Stage 3 moved to In progress under [albums.md](albums.md) §7, *Who
  updates this*, with a stage-log line, and §8 decision 47 records the approval. The exit criteria
  below were confirmed against the adopted records and are binding. **`ARCHITECTURE.md` was not
  changed:** the records were adopted ahead of the work they govern, so their links are added by
  the changes that land that work — deferred under the instructions' *Timing* rule and carried as
  an exit criterion below.
- **2026-11-19, later still — step 3.2 refined into a step plan by the Planner.** It is
  *3.2 — Tier C schema and store* below: two tables and why not three, `Migration(4, 5)`, the store
  surface, the deletion mechanism — `secure_delete` and a `TRUNCATE` checkpoint, `VACUUM` rejected —
  what of *Keeping Tier C true* lands now, one added backup exclusion, the tests and the files.
  Every choice ADR-0018 Amendment 5 left to 3.2 is marked **Decided at 3.2** there, with its reason.
  None needs the maintainer's approval under `AGENTS.md`. Planning only: no code, no Gradle run.
- **2026-11-19, later again — step 3.2 built by the Coder and verified; documents updated by the
  Planner.** The gate, what the scrub test learned about Robolectric's SQLite, and the three
  properties it could not prove on the JVM are under *3.2 — Tier C schema and store*, *Built and
  verified*; those three are now step 3.7's. The Planner updated `ARCHITECTURE.md` §3 and §6,
  `AGENTS.md` *The two databases*, ADR-0018 Amendment 5 (dated notes, nothing rewritten) and the
  test conventions, and found one new open item, F1 below. No Gradle run by the Planner.
- **2026-11-20 — step 3.2 reviewed; two of its findings resolved by the maintainer.** The Reviewer
  found that Tier C is dropped on every Tier A re-extraction, which metadata-only changes and a
  volume resync also cause, and that the deletion reports scrubbed under any rollback journal mode.
  The maintainer accepted the resolution he suggested for both: [albums.md](albums.md) §8 decision
  48, recorded as ADR-0018 Amendment 6, which reverses one bullet of Amendment 5 and annotates
  ADR-0024 §3. A third finding, on a heal test, the maintainer did not rule on; the Planner decided
  it, reversibly. All three are under *3.2 — Tier C schema and store*, *Reviewed*, with the Coder's
  brief after it; step 3.2 is reopened until that lands. `ARCHITECTURE.md` §6's rollback-journal
  sentence was corrected now, because it stated a journal behaviour only one mode has; its §3 row
  changes after the follow-up lands. No Gradle run by the Planner.
- **2026-11-20, later — the 3.2 follow-up built by the Coder and verified; documents updated by
  the Planner.** Tier C is now dropped only on a changed healing triple, by `upsert` and
  `healMediaUri` alike, and a deletion reports scrubbed only under a journal mode that keeps no page
  copies — ADR-0018 Amendment 6, as briefed, with no schema change. The gate, and what it did not
  prove, are under *3.2 follow-up*, *Built and verified*; step 3.2 is ✅ again. The Planner checked
  the code against the Coder's report, then updated `ARCHITECTURE.md` §3 and §6, the test
  conventions' scrub-test paragraph, ADR-0018 Amendment 6 (a dated *Built* note), the Stage 3 row of
  [albums.md](albums.md) §7 and decision 48, step 3.7's device checks below (one added, one
  sharpened), and `designer.agent.md`'s stale sentence about the index. No Gradle run by the Planner.
- ~~**Next action: the 3.2 follow-up** — Tier C invalidation by the healing triple, the journal-mode
  check, and the heal test; *3.2 follow-up* below is the brief. Owner: the Coder, as one sequential
  task; then the verification phase runs the definition of done once; then the Planner makes the
  updates listed there under *After it lands*. No schema change: the index stays at version 5.~~
  Done 2026-11-20 — the entry above.
- **Next action: step 3.3 — face detection and embedding in the indexing pipeline, behind the
  disclosure.** Owner: the Planner refines it into a step plan, as 3.2 was, then the Coder builds
  it. It starts from what 3.2 and its follow-up landed: the Tier C tables at index version 5, and their rules on
  `MediaIndexStore` — `writeFaceIndexResults`, the identity-conditional write the pass will use,
  and `hasFaceData` and `deleteAllFaceData`, which ADR-0024 §3's repeat at start-up needs and which
  nothing calls yet. A schema change found during refinement would be `Migration(5, 6)`.
  - **3.3's, by its records:** the capability check (ADR-0023 §3); the disclosure and the record
    that it was shown (ADR-0024 §2); the face-data setting, a scalar for `app_settings`, checked
    inside the write transaction (ADR-0024 §1 and §3); the bounded retry count (ADR-0018
    Amendment 5); the pass itself, resumable and stopping cleanly (ADR-0023 §4); and the logger
    test (ADR-0024 §6). *Added 2026-11-20:* the start-up deletion, which runs whenever face data
    is off, not only while a Tier C row exists (ADR-0024 §3 as annotated by ADR-0018 Amendment 6)
    — 3.3's or 3.6's, whichever wires the setting.
  - **3.3 needs a persistence home for two things, and refinement chooses it:** the capability
    check's result, and whether the disclosure has been shown. The second is per installation, so
    it must **not** go in `app_settings`, which is backed up (ADR-0024 §2). Either may need a new
    preferences file: that is an `AGENTS.md` call-out, with a row in its *Preference stores* table
    and both a `SharedPreferences*` and an `InMemory*` store, and ADR-0018 Amendment 2's two tests
    are answered explicitly. A file that a backup must not carry also needs its `<exclude>` in every
    section of the rules files — which is where F1 bites.
  - **Verify items still open**, each at its named step: ADR-0023's items 1, 2, 4, 5 and 8 at 3.3;
    6 at 3.3 and 3.4; 7 at 3.7. ADR-0022's upright frame at 3.3, the SM-G781B at 3.7, and its
    anchor-survival measurement on every future pipeline-version bump.
  - **Open questions still open:** D1, the People criterion's layout — the Designer's, for 3.6;
    B2, incremental clustering — for 3.4; the Play Data Safety check — the maintainer's, before
    the first Play release that ships the Tier C pass, with the Planner preparing its facts at 3.7;
    rule editing's D3, still to confirm ADR-0017 Amendment 4's proposal for an album with a
    `people` clause where people is unavailable, and its D6, the order; and **F1**, new — whether
    the rules files need a fourth section — to be settled before Tier C ships.
  - *Added 2026-11-20:* a deletion that is not scrubbed is a result the caller must handle, not an
    error — the start-up repeat runs whenever face data is off, and on a `PERSIST` device every
    repeat reports not scrubbed again (ADR-0018 Amendment 6 §2). And the change that first writes
    face data adds Amendment 6's two accepted costs to `ARCHITECTURE.md` §11 — stale faces after an
    edit that keeps the healing triple, and page copies left under `PERSIST` — which stay out of it
    while nothing writes Tier C.
- ~~**Read first, on starting the 3.2 follow-up:**~~ Done 2026-11-20. ADR-0018 Amendment 6; *3.2 — Tier C schema and
  store*, from *Built and verified* to the end of *3.2 follow-up*; `data/MediaIndexStore.kt`,
  `data/MediaIndexDatabase.kt` and `data/MediaIdentity.kt`; `MediaIndexTierCStoreTest` and
  `MediaIndexFaceDataScrubTest`; the test conventions' paragraph on the scrub test; and repo
  memory's scrub-test notes.
- **Read first, on starting 3.3:** this block; ADR-0023, above all §3 and §4 and its verify items;
  ADR-0024 §1 to §3 and §6; ADR-0018 Amendment 5's per-image states and its step 3.2 notes, and
  Amendment 6 with its *Built* note;
  *3.2 — Tier C schema and store* below, for the store surface the pass writes through; `AGENTS.md`
  *Preference stores*; and F1.
- ~~**Next action: build step 3.2.**~~ Done 2026-11-19 — the entry above. ~~Owner: the Coder, as one
  sequential task, from *3.2 — Tier C schema and store* below. Then the verification phase runs the
  definition of done, once; then the Planner makes the document updates listed there under *After
  it lands*.~~ ~~Owner: the Planner
  refines it into a step plan, then the Coder builds it. Governed by ADR-0018 Amendment 5 … the
  backup rules re-checked, including any new sidecar file.~~ Refined 2026-11-19 — the entry above;
  what the amendment asked of 3.2 is answered point by point in that section.
  - ~~**Not 3.2's:** the capability check and its persistence, the disclosure's per-installation
    store, the upright-frame test and the bounded retry count are 3.3's (ADR-0022 *To verify*,
    ADR-0023 §3, ADR-0024 §2, ADR-0018 Amendment 5).~~ Carried into *Next action: step 3.3* above.
- ~~**Read first, on starting 3.2:**~~ Superseded 2026-11-19 by *Read first, on starting 3.3*
  above. *3.2 — Tier C schema and store* below, which is the brief; then
  ADR-0018 Amendment 5, and the Decision and Amendments 1–4 of the same record for the migration
  discipline it extends; ADR-0024 §3 and §6; `data/MediaIndexDatabase.kt` and its three migrations,
  and `MediaIndexDatabaseMigrationTest`; and repo memory's Room, migration and backup traps — above
  all that a migration test opening a seeded file must name it `media_index.db`.
- ~~**Next action: the maintainer approves the five drafts**~~ Done 2026-11-19 — the entry above.
  ~~and answers their questions — listed
  under *Open questions*, group E, each linking to the question in its ADR~~ (questions answered
  2026-11-19, the entry above). On approval each ADR becomes `Active` and the README index changes
  with it; an amendment's *Proposed* label is replaced
  by an approval line, as ADR-0017 Amendment 2's was. **Stage 3 moves to In progress when the first of
  them is adopted** — its first binding deliverable ([albums.md](albums.md) §7, *Who updates this*).
  Step 3.1 closes when all are adopted and the exit criteria below are confirmed.
- ~~**Read first, on reviewing the drafts:**~~ Superseded 2026-11-19 by *Read first, on starting
  3.2* above. ADR-0022, then ADR-0017 Amendment 4 and ADR-0018
  Amendment 5, which it leans on; then ADR-0023 and ADR-0024; then group E.
- ~~**Next action: the Planner drafts step 3.1's ADRs and amendments, in this order, for the
  maintainer's approval:**~~ Done 2026-11-18 — the entry above.
  1. ~~Anchoring a person to a photo — the first ADR (decisions 41, 42 and 46's overlap).~~
  2. ~~Tier C storage, as an ADR-0018 amendment.~~
  3. ~~The `people` clause, as an ADR-0017 amendment, including the new unevaluatable kinds: the
     anchor not found (decision 42), and the device that fails the capability check (decision 45).~~
  4. ~~The models, runtime and delivery ADR (decisions 43–45), including decision 45's points
     raised by the Planner: ABI packaging, 16 KB pages, how the trimmed build is produced, OpenCV's
     third-party notices.~~
  5. ~~Decision 9's disclosure and delete switch: wording, placement, what "delete" removes.~~
  6. ~~The Play Data Safety question of decision 9.~~
  7. ~~D2, ADR-0013's revisit.~~

  D1, the People criterion's layout, is the Designer's, for 3.6.
- ~~**Next action: the rest of step 3.1**, in the order step 3.1 lists the spike's items: the
  recogniser and its threshold (B1's algorithm with it); the size route; the lowest device; decision
  42's minimum box overlap; the 40 px small-face cut-off; the picker's sort tie-break; non-faces.
  YuNet's training-data caveat goes to the maintainer with the recogniser item.~~ Done 2026-11-18,
  by delegation — decisions 44–46.
  ~~**Next action: step 3.1, starting with the licence and training-data question.**~~ Answered
  2026-11-18 — decision 43.
- ~~**Read first, on drafting the ADRs:**~~ Superseded 2026-11-18 by *Read first, on reviewing the
  drafts* above.
  1. [albums.md](albums.md) §8 decisions 41–46, and decision 9 with its clarification.
  2. Step 3.1 below: what is answered and what is still to come.
  3. [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) and
     [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md), which two of
     the drafts amend, and decision 37's reversal for the unevaluatable precedent.
  4. *Spike outcome* and *Licence findings* below, for the figures and texts the ADRs cite.
- ~~**Read first, on starting 3.1:**~~ Superseded 2026-11-18 by *Read first, on drafting the ADRs*.
  1. [albums.md](albums.md) §8 decision 43, and *Licence findings* below.
  2. *Spike outcome* below, including what was **not** measured — above all the SM-G781B, so A4's
     threshold has no data.
  3. Step 3.1, and the items the spike hands to it.
  4. [albums.md](albums.md) §8 decisions 40 (amended 2026-11-18), 41 and 42.
- ~~**Step 3.0, the feasibility spike, is in progress — started 2026-11-17.**~~ **Superseded
  2026-11-18 by the entry above; kept as the record of how the spike was set up.** Stage 3 itself
  stays Not started: a spike's figures are not a binding deliverable ([albums.md](albums.md) §7,
  *Who updates this*), as recorded for Stage 4.
  - **Where it lives:** `tools/face-model-spike/`, its own Android harness outside the app build;
    weights and downloads gitignored; Python only if needed, in a gitignored virtual environment.
    The `.gitignore` entries are added by the change that creates the folder (A6).
  - **Devices:** the SM-G990B and the SM-G781B (API 33) (A4).
  - **Order:** each candidate's licence is read and recorded under *Licence findings* before
    anything is downloaded. A candidate that fails A5 is recorded as excluded, not silently swapped
    for another (A1, A5).
  - **Privacy:** A7's rules — what may leave the phone and where it goes, aggregates only in logs,
    nothing private in git, everything deleted at the end and the deletion recorded here.
  - **What it returns:** numbers and failures, not a recommendation.
  - **Still open, for 3.1:** the threshold, and whether a people album restored onto a device below
    it is unevaluatable (A4); the anchor's minimum overlap (group C); B1, B2, D1 and D2.
- ~~**Next action: step 3.0** — the licence reading first (A5), then the harness. Step 3.1 follows
  with the spike's figures.~~ Done 2026-11-18.
- ~~**Read first, on starting 3.0:**~~ Superseded 2026-11-18 by *Read first, on starting 3.1* above.
  1. Group A below, answered, and *Licence findings*.
  2. [albums.md](albums.md) §8 decision 9 — its three obligations bind the spike as well as the app,
     except for what A7 allows the spike alone.
  3. [albums-stage-4.md](albums-stage-4.md), *Spike outcome* and its step 4.0, as the precedent for
     how a spike ran and what it cost. Its harness is in git history:
     `git log -- tools/content-model-spike`.
- **Owed from Stage 2:** only the Play Console declaration ([albums.md](albums.md) §7, *Stage 2*). A
  runtime dependency added at 3.1 will change the merged manifest again — OpenCV, chosen by
  decision 45 and confirmed as a dependency by the maintainer on 2026-11-18. The Stage 2
  declaration and Stage 3's Data Safety check (step 3.1) describe the same merged manifest, so they
  are best made together.
- **Binding on everything below:**
  - [albums.md](albums.md) §8 decision 9: face clustering runs eagerly behind a one-time disclosure;
    a Settings switch deletes the face data; face data never leaves the device, never enters a
    backup, never appears in a log. Face data is embeddings, crops and groups; a face's position,
    stored as an anchor, is not (clarified 2026-11-17). The spike's one exception, for the spike
    only, is A7.
  - [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md): a rule is a
    conjunction of typed clauses; `people` is a clause type it anticipates. An unknown clause makes
    the album unevaluatable, and such a rule is never rewritten.
  - [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md): Tier C lives in
    the index database, which is excluded from backup. Any schema change is a version bump, a real
    migration and a committed JSON, with no destructive fallback.
  - [ADR-0014](../architecture/decisions/0014-album-database-included-in-auto-backup.md): the album
    database is backed up — the other half of §10 question 12's collision.
  - [albums.md](albums.md) §8 decisions 40 (the People picker), 41 (anchoring) and 42 (an anchor
    that cannot be followed), ~~pending step 3.1's ADR for 41 and 42~~ recorded for 41 and 42 in
    ADR-0022 since 2026-11-19.
  - **Step 3.1's records, adopted 2026-11-19:**
    [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) (the
    anchor), [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md)
    (the pipeline, its runtime and its verify items),
    [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) (the
    disclosure and the delete switch), ADR-0017 Amendment 4 (the `people` clause) and ADR-0018
    Amendment 5 (Tier C).
  - [albums.md](albums.md) §8 decision 43: the training-data risk is accepted for SFace only. Any
    other recogniser, and the detector, needs its own answer; the weights' Apache-2.0 obligations
    still apply (A5). **Decision 44** (delegated) gives the detector's answer, for YuNet only.
  - [albums.md](albums.md) §8 decisions 45 and 46 (delegated, 2026-11-18): the models, the
    grouping, the runtime and its delivery, the capability check, and the picker and matching
    details.
  - [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md): a
    materialised result is intersected with live media before display.
  - [ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md): the navigation
    chain, which that ADR says to revisit at Stage 3 (D2 below).
  - [albums.md](albums.md) §6: indexing and evaluation are separate jobs; the honesty rules apply.
  - §8 decisions 22 (no app-open index pass), 23 (no notification and no foreground service) and 29
    (no Incomplete-album or coverage notice on an album or on the New automatic album screen).
  - §11: no cloud inference.
  - `AGENTS.md`'s ask-before-doing items: every dependency, including a spike's, and every
    permission a dependency merges in. Stage 4's spike approvals named its own folder and do not
    carry over (A6). The face spike's own dependencies, under `tools/face-model-spike/` only and
    chosen per A1 and A2, were approved on 2026-11-17 (A6); the app's runtime is still approved at
    3.1. **OpenCV, the app's runtime, was approved by the maintainer on 2026-11-18** (decision 45's
    confirmation). The permissions its merged manifest may add are not covered, and are asked when
    it lands.

---

## Spike outcome — 2026-11-18

Step 3.0's result, in aggregates only. Source: `tools/face-model-spike/NOTES.md`, its entries M0 to
M5 and the deletion entry, as they stood on 2026-11-18. ~~**The notebook is not committed**, so this
section is written to be read without it.~~ The notebook was committed and then removed from the
tree, as Stage 4's was; it is in git history (`git log -- tools/face-model-spike`). This section is
still written to be read without it. Phone: **the SM-G990B only** (SM8350, 5.49 GB RAM, API 36,
arm64-v8a). The SM-G781B was never attached, so nothing below describes it. People are named P1
and P2 only, by image count.

**The photos (A8).** The SM-G990B held 1092 images (944 JPEG, 90 GIF, 38 PNG, 20 WebP) and 41
videos. The spike used the **1002 still images**; GIFs were excluded, which the maintainer confirmed
on 2026-11-17, and videos are out by decision 40. One detector found **533 faces in 347 images**;
655 images had none.

**Runtimes (A2).** No model that passed A5 is TFLite, so LiteRT was not used: a conversion would
have measured itself instead of the model.
- **OpenCV 4.13.0 for Android** (Apache-2.0), its DNN module on the CPU, ran YuNet and SFace (ONNX)
  and OpenFace (Torch7). ONNX Runtime was rejected because it cannot read Torch7.
- **dlib 20.0** (Boost licence), built natively with the NDK, ran dlib's own model format.
- The harness was its own app, never the app's package id. Photos were decoded to a long side of at
  most 1280 px, detection ran on a 640 px copy, and the detector's score threshold was 0.7.

**Measured on the SM-G990B**, in the foreground with the screen on, on USB power, thermal status 0
throughout. Detector YuNet fp32 in every column.

| | SFace fp32 | OpenFace nn4.small2.v1 | dlib ResNet v1 | Detector only |
|---|---|---|---|---|
| Model size on disk | 38.7 MB | 31.5 MB | 22.5 MB | 0.23 MB |
| Model load | 161 ms | 94 ms | 83 ms | 9 ms |
| Detection per photo, median / p90 | 16.9 / 28.5 ms | 21.6 / 33.9 ms | 22.4 / 35.4 ms | 15.3 / 25.7 ms |
| Embedding per face, median / p90 | 15.6 / 20.6 ms | 7.8 / 8.1 ms | 309 / 395 ms | — |
| Whole run, 1002 photos | 75 s | 87 s | 260 s | 61 s |
| Peak memory (VmHWM) | 299 MB | 246 MB | 236 MB | 207 MB |

- **Detection drifted from 15 to 22 ms (median) between identical runs**, unexplained. Read it as
  roughly 15 to 25 ms per photo, not as a difference between recognisers. Decoding added a median
  of 37 ms per photo.
- The int8 YuNet was not faster on this CPU (26.7 ms median).
- dlib runs single-threaded on its own CPU code: about 50 minutes per 10,000 faces, against about 3
  for SFace. SFace's whole run extrapolates to roughly 12 minutes per 10,000 photos at this
  library's face density — not measured at that size, and on one SoC.

**Size against A3's 50 MB budget.** Estimated download size: deflate-compressed, arm64-v8a only,
MB = 10⁶ bytes. Play's own compression was not measured.

| Combination | Estimate |
|---|---|
| **SFace fp32 + YuNet fp32 + OpenCV** | **45.9 MB** — 4.1 MB under the budget; 35.9 MB of it is the SFace model |
| SFace int8 + YuNet int8 + OpenCV | 16.8 MB — **the int8 SFace's embedding quality was not measured** |
| OpenFace + YuNet + OpenCV | 39.0 MB |
| dlib + YuNet + OpenCV + the dlib library | 31.4 MB |
| All three recognisers | 96.2 MB |

- **The OpenCV figure is an upper bound:** it is the full prebuilt library, every module included
  (9.8 MB compressed with its C++ runtime). A build limited to the modules used was not made.
- Installed, uncompressed, the SFace fp32 stack is 64.2 MB. A3 names download size, so the table is
  the figure that binds; the installed one is recorded in case 3.1 needs it.

**Clustering (input to B1 and B2).**
- Each recogniser on the distance its publisher defines: cosine for SFace and OpenFace, Euclidean
  for dlib.
- **Average-linkage clustering** does not depend on input order, but needs the full distance matrix
  — about 800 MB at 10,000 faces. Its cost in an app was not measured.
- **Incremental clustering**, one pass as faces arrive, depends on order. It was measured for
  stability only, not for quality.
- Decision 40's rule picks the groups offered: faces from at least two different images.
- The thumbnail is the group's medoid, among members at least 60 px across.
- **Small faces were cut:** a face under 40 px on its shorter side, in the 1280 px image, was not
  clustered, labelled or offered. That removed 111 of the 533 faces, leaving **422 faces in 299
  images**. The cut-off was the spike's choice; the maintainer did not object (2026-11-18).

**Quality, against the maintainer's labels.** He labelled the 422 faces on 2026-11-18: 372 with a
person, 8 not faces, 42 skipped. That is 95 people. 78 of them appear in exactly 2 images and six in
5 or more; P1 is in 68 images and P2 in 56.
- **The labels favour SFace.** He labelled card by card from one recogniser's grouping, SFace at
  0.55, so:
  - SFace's own merges are mostly invisible;
  - a person exists in the truth mostly because SFace grouped two of their faces;
  - every SFace figure at or below 0.55 is an upper bound, and at 0.55 it is nearly circular.
- What the bias does not reach: how P1 and P2 are split across thumbnails, and the mixed groups of
  OpenFace and dlib.
- There was one labeller, so label error is unmeasured, and one library.

| Recogniser | Threshold | Thumbnails | Mixed / evaluable groups | Album precision / recall | P1 / P2 recall (thumbnails) |
|---|---|---|---|---|---|
| SFace | 0.45 (strict) | 111 | 1 / 107 | 0.99 / 0.77 | 0.54 (5) / 0.30 (7) |
| SFace | **0.50** | **104** | **1 / 100** | **0.99 / 0.91** | **0.82 (2) / 0.70 (4)** |
| SFace | 0.55 | 100 | 1 / 96 | 0.99 / 0.91 | 0.82 (2) / 0.70 (3) |
| SFace | 0.60 | 91 | 9 / 88 | 0.95 / 0.94 | 0.85 (2) / 0.82 (2) |
| SFace | 0.637 (publisher) | 84 | 14 / 82 | 0.90 / 0.93 | 0.85 (2) / 0.82 (2) |
| OpenFace | 0.20 | 125 | 4 / 119 | 0.94 / 0.60 | 0.15 (12) / 0.07 (11) |
| OpenFace | 0.30 (strict) | 114 | 15 / 105 | 0.86 / 0.65 | 0.41 (6) / 0.07 (18) |
| OpenFace | 0.50 (publisher) | 55 | 31 / 52 | 0.63 / 0.83 | 0.93 (1) / 0.52 (7) |
| dlib | 0.45 (strict) | 119 | 3 / 115 | 0.99 / 0.70 | 0.34 (9) / 0.20 (9) |
| dlib | 0.55 | 79 | 27 / 74 | 0.83 / 0.90 | 0.90 (2) / 0.66 (4) |
| dlib | 0.60 (publisher) | 52 | 25 / 50 | 0.76 / 0.93 | 0.90 (1) / 0.89 (1) |

Album precision and recall are weighted over images, for one-person albums built from the
thumbnail a user would pick. A group is evaluable when at least two of its faces carry a person.
- **SFace has a plateau from 0.50 to 0.55, and a cliff at 0.60**, where mixed groups jump from 1
  to 9.
- **OpenFace and dlib have no setting that is both pure and keeps P1 and P2 together.**

**SFace at 0.50, in plain terms:**
- 104 thumbnails for 299 images, 1 of them mixing two people out of 100 evaluable.
- The thumbnail face is the group's own person in 100 of 100.
- 92 of the 94 people seen in at least two images have a thumbnail.
- Album precision about 0.99, recall about 0.91.
- **P1 is split across 2 thumbnails and P2 across 4**; their best thumbnails reach recall 0.82
  and 0.70.
- 30 of the 32 pairs seen together in at least two images can be picked as a two-person album, at
  precision 1.00.
- **79 of the 100 evaluable thumbnails are people seen in fewer than three images**, and 90 are
  people in fewer than five. This is what decided the sort in decision 40's 2026-11-18 amendment.
- 4 offered groups contain a non-face, and 3 thumbnails are non-faces.

**Anchor stability (input to decision 42's minimum overlap).**
- **Same build, detection run twice:** all 533 faces matched, box overlap (IoU) **1.0 for every
  one**. That says nothing about another phone, build or OpenCV version.
- **The int8 detector against the fp32 one**, as a first sample of a different build:
  - 525 faces matched; the face count differed in 18 of the 347 images with faces.
  - Matched IoU: median **0.957**, 5th percentile 0.88, minimum 0.75.
  - All matches at IoU 0.5 or more; 91 % at 0.9 or more.
- **Re-clustering with the input shuffled and 10 % of the other faces removed** (average linkage,
  20 seeds): the thumbnail's group stayed the same group about **98 % of the time at the strict
  thresholds** (SFace 0.45 98.5 %, OpenFace 0.30 97.9 %, dlib 0.45 98.4 %) and at SFace's 0.637
  (98.6 %).
  - Stability falls at the looser OpenFace 0.50 (87.7 %) and dlib 0.60 (95.1 %).
  - The worst single group was 50 to 60 % in every setting.
  - The incremental clusterer was clearly worse.
  - SFace 0.50 itself was not run.
  - Stability is not correctness: a stable group can be the wrong person.

**Non-faces.** 8 faces were labelled as not a face. They are 66 to 195 px wide, with detector scores
from 0.72 to 0.81. A score cut high enough to remove them would also remove 29 of the 372 real
faces.

**The spike's recommendation — an input to step 3.1, not a decision:** SFace fp32 at cosine distance
about 0.50, with average-linkage clustering and the medoid as thumbnail. It is the only operating
point measured that is both pure and consolidating. 0.45 is the conservative fallback, at a cost
in recall (0.77) and a badly split P1 and P2. **Against it, by the spike's own account:**
- the size, at 4.1 MB of headroom;
- the training-data caveat under *Licence findings*, where dlib's and OpenFace's weights texts are
  cleaner;
- labels that favour it, on one library;
- a picker that is mostly people seen twice;
- average linkage's unmeasured cost at scale.

**Adopted 2026-11-18, by delegation**, as [albums.md](albums.md) §8 decision 45, at 0.50 and not
the 0.45 fallback. The points against it above stand, and 3.4 measures the last one.

**Not measured.**
- **The SM-G781B: never attached, no figures of any kind.** A4's threshold has no data below the
  SM-G990B. *Decision 45 replaced the threshold with a capability check, 2026-11-18.*
- The int8 SFace's embedding quality, on which the 16.8 MB route rests.
- The size of a custom OpenCV build.
- BlazeFace.
- Labels not seeded by one recogniser, and a second labeller.
- The effect of the spike's five-point alignment approximations on OpenFace and dlib, which may have
  been measured at a small disadvantage.
- Also: the incremental clusterer's quality; average linkage's memory and time in an app; anchor
  stability at SFace 0.50; the cause of the detection drift; and speed in the background, since
  every run was in the foreground.
- Out of scope by decision, not gaps: faces under 40 px, GIFs and videos.

### Spike data deleted — 2026-11-18 (A7)

The maintainer asked for the deletion when he closed the spike. The Coder deleted the data; the
notebook records it.
- **Desktop, under `tools/face-model-spike/`:**
  - `private/`, holding the face crops, the per-face records and embeddings, the clustering and
    scoring outputs, the labelling page, the labels and the run logs;
  - `models/`, `raw/` and `venv/`;
  - the harness build output, including two folders the original list missed;
  - the scripts' bytecode cache.

  A label-page server still serving `private/` was stopped first.
- **Outside the repository:** the maintainer's second copy of the labels, in his Downloads folder.
  The notebook had recorded it as identical to the private copy, but a byte comparison before
  deletion showed it was not; both are gone. A compiler-daemon log of the harness build, which held
  paths and no photo data, was deleted too.
- **The SM-G990B:** the harness package was uninstalled, which removes its internal files — models,
  per-face records, crops, per-photo tables and logs. The scripts' temporary model copy in
  `/data/local/tmp` was removed by the scripts themselves.
- **How it was verified:**
  - Desktop: every path was tested and found gone. The spike folder then held only `NOTES.md`,
    the harness sources and the scripts — since committed and removed from the tree,
    `git log -- tools/face-model-spike` — and the Downloads folder only unrelated files.
  - Phone: the package is no longer listed, and `run-as` reports it unknown. `/data/local/tmp`
    holds only entries older than the spike. Shared storage has no file newer than 2026-11-16 to
    depth 4 and no spike file, and no spike folder exists under `Android/data`, `media` or `obb`.
- **The SM-G781B never received anything.** That rests on the notebook, which records no install
  or copy to it, **not on a check**: the phone was not attached when the data was deleted.
- **Photos:** the scripts pulled only 128 px face crops, all under `private/`; no full photo was
  found anywhere else.
- **Left in place:** the adb server's own log on the desktop. It is shared by every use of adb on
  the machine, holds device traffic but no string of the harness, and was judged not to be the
  spike's.

---

## What the user gets

On the **New automatic album** screen, next to Place and Date, a **People** criterion:

- The user picks one or more people from unlabelled thumbnails the app chooses, one per person, and
  names the album himself — [albums.md](albums.md) §8 decision 40.
- Only people in at least two different images are offered. Only images count; videos are not
  considered for people albums — decision 40, amended.
- The thumbnails are sorted by how many photos each person is in, most first — decision 40,
  amended 2026-11-18. ~~Ties are not decided yet (step 3.1).~~ The count is of distinct images;
  ties go to the person's most recent photo, then to a stable id — decision 46.
- Thumbnails cannot be merged or split. One person shown as two thumbnails is an accepted failure:
  picking both finds little, because of the next point — decision 40, amended.
- Several people means **all of them are in the photo** — decision 35.
- It combines with place and date — decision 33: "Silvia and Davide in France, 2025".
- After a restore or a full re-index the album finds its people again through anchor photos, one
  per person, waiting on the index until face indexing reaches them — decisions 41 and 42.
- ~~On a device below the threshold, the People criterion is not offered — A4.~~ On a device where
  the face pipeline fails its capability check, the People criterion is not offered — A4, and
  decision 45, which replaced the threshold with the check.

~~The minimum box overlap that recognises the anchored face is open (group C); the spike's figures
for it are under *Spike outcome*, anchor stability.~~ The anchored face is the detected face that
overlaps the stored position best, at IoU 0.5 or more — decision 46. Faces under 40 px are never
grouped, offered or matched.

## Already decided

Linked, not restated:
- Face clustering eagerly, behind a disclosure, with its obligations — [albums.md](albums.md) §8
  decision 9; what counts as face data — its 2026-11-17 clarification.
- People goes next, and builds the on-device ML pipeline — decision 13, amended 2026-11-17.
- A rule is a composable predicate — decision 14 and ADR-0017.
- Face data lives in the index database, excluded from backup — decision 15 and ADR-0018.
- Criteria of one automatic album, not an album kind — decision 33.
- Several people means all of them — decision 35.
- The People picker: unlabelled thumbnails chosen by the app, no person names, the two-images
  threshold, images only, and no merge — decision 40, amended 2026-11-17; thumbnails sorted by
  photo count, most first — amended 2026-11-18.
- Anchoring a person to a photo, one anchor per person — decision 41, amended 2026-11-17.
- What happens when an anchor cannot be followed — decision 42.
- The training-data risk is accepted for SFace only; that does not choose SFace — decision 43
  (2026-11-18).
- Delegated by the maintainer and taken by the Orchestrator on 2026-11-18, each reversible by him:
  YuNet's training-data risk accepted — decision 44; SFace fp32, average linkage at cosine 0.50,
  OpenCV bundled ~~and trimmed~~ for arm64-v8a — untrimmed, by decision 47's E3 — and the
  capability check — decision 45; the anchor's
  IoU ≥ 0.5, the 40 px cut-off, the picker's order and non-faces — decision 46. **The maintainer
  confirmed decision 44 and decision 45's OpenCV dependency on 2026-11-18.**
- Step 3.1's draft questions, group E, answered as recommended by the maintainer — decision 47
  (2026-11-19). ~~The drafts themselves are not yet approved.~~ He approved the drafts the same
  day: ADRs 0022–0024, ADR-0017 Amendment 4 and ADR-0018 Amendment 5 are adopted.
- **§10 question 12 is closed** (2026-11-17), by decisions 40–42 and decision 9's clarification.
  Step 3.1's first ADR records the answer — ADR-0022, adopted 2026-11-19.
- Tier C survives a re-extraction of an unchanged file, and a deletion is scrubbed only on a journal
  mode that keeps no page copies — decision 48 (2026-11-20), accepted by the maintainer from step
  3.2's review; ADR-0018 Amendment 6.

---

## Steps

~~Proposals until step 3.1 confirms them.~~ Confirmed at step 3.1's close, 2026-11-19; each later
step is refined when it starts.

| Step | Delivers | Status |
|---|---|---|
| **3.0** | Feasibility spike: face detection, face embedding and clustering, measured on the phone. No shipped code. | ~~🔶 In progress — started 2026-11-17~~ ✅ Complete 2026-11-18 — *Spike outcome* above |
| **3.1** | The decisions: ADRs and amendments, and these exit criteria confirmed | ~~🔶 In progress~~ ✅ Complete 2026-11-19 — decisions 43–46 taken 2026-11-18; ADRs 0022–0024 and amendments to ADR-0017 and ADR-0018 drafted 2026-11-18; their questions (group E) answered 2026-11-19; all five adopted 2026-11-19; exit criteria confirmed |
| **3.2** | Tier C schema and store | ~~⬜ Not started — refined 2026-11-19, ready to build~~ ~~✅ Built and verified 2026-11-19~~ ~~🔶 Built and verified 2026-11-19 — the gate is under *3.2 — Tier C schema and store*, *Built and verified*; reopened 2026-11-20 for its follow-up (decision 48)~~ ✅ Built and verified 2026-11-19; reopened 2026-11-20 for its follow-up (decision 48), built and verified 2026-11-20 — both gates are under *3.2 — Tier C schema and store* |
| **3.3** | Face detection and embedding in the indexing pipeline, behind the disclosure | ⬜ Not started |
| **3.4** | Clustering and the person record | ⬜ Not started |
| **3.5** | The `people` clause and evaluation | ⬜ Not started |
| **3.6** | The UI: the People criterion with its thumbnails, the disclosure and the delete switch | ⬜ Not started |
| **3.7** | Device verification and installed-app walkthrough | ⬜ Not started |
| **3.8** | Close-out: `ARCHITECTURE.md`, vocabulary, audit, archive | ⬜ Not started |

### 3.0 — Feasibility spike

**Complete 2026-11-18.** The result is under *Spike outcome* above, and the deletion of its data
under *Spike data deleted*. It measured the SM-G990B only: the SM-G781B was never attached. It
ended with a recommendation, which this plan records as an input to 3.1, not as a decision.

Measures, on the SM-G990B and the SM-G781B (A4) and over images only (decision 40), the candidates
the spike chooses under A1: one or more detectors and two to three recognition (embedding) models,
each passing A5 before it is downloaded. For each candidate, for detection, for embedding and for
clustering, record:
- model size on disk, and the download size it would add to the app, against A3's 50 MB budget;
- load time;
- per-photo time: detection, plus embeddings for the N faces found;
- peak memory;
- quality as the maintainer would see it in the People picker ([albums.md](albums.md) §8 decision
  40), after the at-least-two-images filter:
  - how many thumbnails are offered;
  - thumbnails that are the same person — splits, an accepted failure with no merge to fix it
    (decision 40), still measured as a rate;
  - thumbnails that mix two people, also accepted, since there is no split;
  - strangers that pass the filter;
  - whether each thumbnail the app would choose is a usable face.
- **Planner proposal, not decided:** whether an anchor face re-resolves to the same person when
  clustering is run again over the same photos, and after a re-index from scratch. Decision 41
  rests on it, and 3.1's ADR would otherwise be written without evidence. **Extended 2026-11-17:**
  also how far the detected box of the same face moves between runs, which sets decision 42's
  minimum overlap — that part is required, since decision 42 leaves the minimum to the spike.

**What it returns:** numbers and failures, not a recommendation. No app code ships. Where it lives,
what it may download and what leaves the phone are A5–A7, answered 2026-11-17.

### 3.1 — The decisions

The Planner writes, and the maintainer approves:
- **Anchoring a person to a photo** — [albums.md](albums.md) §8 decisions 41 and 42, and §10
  question 12, closed. **The first ADR of the stage.** It records the anchor — one per person, a
  media reference and the face's position, in the album database — and how it resolves: decision
  42's cases, waiting on the index versus unevaluatable, never widened. It no longer reconciles
  anything with decision 9, whose 2026-11-17 clarification settles that a face's position is not
  face data. Its one open input is the minimum box overlap, from 3.0. **Drafted 2026-11-18 as
  [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md), ~~proposed~~
  adopted 2026-11-19.**
- **Tier C storage**, as an ADR-0018 amendment. **Drafted 2026-11-18 as
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5,
  ~~proposed~~ adopted 2026-11-19.**
- **The `people` clause format**, as an ADR-0017 amendment: what it stores — the anchors, as the ADR
  above settles them — and what makes it unevaluatable. That includes decision 42's second cause,
  found at evaluation on a rule the build understands, which the user may fix by picking again.
  **Drafted 2026-11-18 as
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4,
  ~~proposed~~ adopted 2026-11-19.**
- **The disclosure and the delete-face-data switch** of decision 9: wording, placement, and what
  "delete" removes. **Drafted 2026-11-18 as
  [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md), ~~proposed~~
  adopted 2026-11-19**;
  wording and placement are intent, finalised by the Designer at 3.6.
- **The Play Data Safety check** of decision 9: whether on-device face inference counts as
  collection — to be confirmed, not assumed. **Recorded 2026-11-18 under *The Play Data Safety
  check* below; not answered.**
- **The models, the runtime and delivery** (A1–A3), bundled preferred and within the 50 MB budget,
  with the runtime dependency and the permissions it merges approved under `AGENTS.md`; the
  threshold below which the People criterion is hidden, and whether a people album restored onto
  such a device is unevaluatable (A4); and any licence file the weights need (A5). **Drafted
  2026-11-18 as [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md),
  ~~proposed~~ adopted 2026-11-19.** The dependency was confirmed by the maintainer on 2026-11-18; the permissions it
  merges are checked when it lands (ADR-0023, verify item 4).
- **Whether ADR-0013's revisit fires** (D2). **Recommendation recorded 2026-11-18 under *Open
  questions* D2: it does not.** ✅ **Answered 2026-11-19 by the maintainer, as recommended** (E11).

**Handed over by the spike, 2026-11-18.** These are items for 3.1, not decisions. The figures
behind each are under *Spike outcome* and *Licence findings*.
1. **The licence and training-data question — first, because it can stop the stage.** Every
   recognition model found was trained on face data with research-only or undeclared terms. The
   maintainer chose to measure anyway and to decide shipping here. If he accepts none of the three
   measured recognisers, none is left to ship, and the stage stops until another is found.
   ✅ **Answered 2026-11-18:** the risk is accepted for SFace only — [albums.md](albums.md) §8
   decision 43. ~~Still open: the same caveat on the detector, YuNet, which the question did not
   name.~~ ✅ **YuNet answered 2026-11-18, by delegation:** the risk is accepted for YuNet too —
   decision 44.
2. **The recogniser and its threshold.** The spike's input is SFace fp32 at cosine distance about
   0.50, with 0.45 as the conservative fallback, on labels that favour SFace. This is also B1's
   algorithm: average linkage, whose memory at scale is unmeasured, or an incremental clusterer,
   which is cheaper but less stable. ✅ **Answered 2026-11-18, by delegation:** SFace fp32, average
   linkage on cosine distance at 0.50; SFace int8 not adopted; incremental clustering (B2) open for
   3.4 — decision 45.
3. **The size route.** The fp32 stack is estimated at 45.9 MB, 4.1 MB under A3's budget. The two
   ways down both need measuring first: the int8 SFace's quality, and a trimmed OpenCV build.
   Whether the runtime library counts against "the face models" 50 MB was not asked; the spike
   counted it. ✅ **Answered 2026-11-18, by delegation:** OpenCV bundled, arm64-v8a only, trimmed
   to the modules used; trimming is margin, not a precondition; the runtime counts against the
   budget — decision 45. **Changed 2026-11-19 by E3:** OpenCV's published artifact, untrimmed;
   trimming stays margin.
4. **The lowest device — still open, with no data.** The SM-G781B was never attached. A4's
   second half — a restored people album is unevaluatable below the threshold — is still to be
   confirmed. ✅ **Answered 2026-11-18, by delegation:** no device list and no threshold; a
   capability check decides; a restored people album on an incapable device is unevaluatable,
   never hidden or deleted — decision 45.
5. **Decision 42's minimum box overlap.** The spike's IoU figures are the input: 1.0 on the same
   build; a median of 0.957, and a minimum of 0.75, between two builds of the detector. ✅
   **Answered 2026-11-18, by delegation:** IoU ≥ 0.5, best overlap wins — decision 46.
6. **The 40 px small-face cut-off**, the spike's choice, which the maintainer did not object to.
   Faces under it are never grouped, so they are never offered and never match an album. ✅
   **Answered 2026-11-18, by delegation:** kept, and never matched as an anchor either — decision
   46.
7. **The picker's sort** (decision 40, amended 2026-11-18): its tie-break, if one is needed, and
   whether the count is of images, as the two-image threshold counts. Here or at 3.6. ✅
   **Answered 2026-11-18, by delegation:** distinct images, most first; then the group's most
   recent photo date; then a stable id — decision 46.
8. **Non-faces.** 3 of SFace 0.50's 104 thumbnails were not faces, and a detector-score cut could
   not remove them without losing real faces. Whether that is an accepted failure, like a split
   person, is not asked. ✅ **Answered 2026-11-18, by delegation:** an accepted grouping failure,
   with no extra filter — decision 46.

~~**Still to come in 3.1**, in this order, each drafted by the Planner for the maintainer's
approval: the anchoring ADR; the Tier C amendment to ADR-0018; the `people` clause amendment to
ADR-0017, with the anchor-not-found and incapable-device kinds of unevaluatable; the models,
runtime and delivery ADR; decision 9's disclosure and delete switch; the Play Data Safety question;
and D2.~~ All drafted or recorded on 2026-11-18. ~~**Still to come in 3.1:** the maintainer's approval
of each draft and his answers to group E; then the exit criteria below confirmed.~~ Group E was
answered on 2026-11-19, as recommended (decision 47). ~~**Still to come in 3.1:** the maintainer's
approval of each draft, which the answers do not give; then the exit criteria below confirmed. D1
stays open for the Designer. Step 3.1 closes when the drafts are adopted and the exit criteria below
are confirmed.~~ **Step 3.1 closed on 2026-11-19:** the maintainer approved all five drafts, and
the Planner confirmed the exit criteria below against them. D1 stays open for the Designer.

#### The Play Data Safety check — recorded 2026-11-18, not answered

Not an ADR: it is a declaration about the shipped app, not a choice of structure. **Nothing here
asserts the answer.** Decision 9 says on-device inference "generally" is not collection; that is a
belief to check, not a finding.

- **The question.** Does Play's Data Safety form treat face detection, embedding and grouping that
  run and stay on the device as *collection* of user data? And does Android's own auto-backup of the
  album database — which now holds anchors, a photo reference and a face's position (ADR-0022) —
  count as collection or sharing?
- **Where the answer is.** Play's definitions of collection and sharing, in the Play Console Help
  article on the Data safety section. Its definitions sit in collapsed sections that a page fetch
  could not expand on 2026-11-18, so they were **not read** for this record. That article says the
  developer alone is responsible for the declaration.
- **What else the form must describe:** the merged manifest's final permission list, which will
  include whatever OpenCV merges in (ADR-0023, verify item 4) and already carries `INTERNET` from a
  library and the tip jar; the photos data type the app reads; and `ACCESS_MEDIA_LOCATION`.
- **Who.** The maintainer, who alone has Play Console access, fills in the form. The Planner
  prepares the facts at step 3.7 — what the face path reads, writes, keeps and backs up, and the
  merged permission list after OpenCV lands.
- **When.** Before the first Play release that ships the Tier C pass, together with the declaration
  owed from Stage 2. Its answer is recorded here and in [albums.md](albums.md) §8, closing the exit
  criterion below.

This step also confirms or changes the exit criteria below.

### 3.2 — Tier C schema and store — refined 2026-11-19

The Coder's brief. What each Tier C row holds, the two stamps, the per-image states and what a
deletion removes are [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)
Amendment 5's, and are not restated; one identity, and index rows deleted rather than retained, are
[ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md)'s. What the
amendment left to 3.2 is marked **Decided at 3.2**, with its reason.

**Schema — Decided at 3.2.** Two tables, named after `media_index_watermark`:
- `media_index_face_state`, one row per image the pass has processed: `mediaUri` TEXT primary key,
  the index row's key; `pipelineVersion` INTEGER NOT NULL; `outcome` TEXT NOT NULL, `INDEXED` or
  `FAILED`, stored by name through a type converter as `AlbumMembershipKind` is. *Done, faces* and
  *done, none* are told apart by the face rows, not stored twice.
- `media_index_face`, one row per stored face: `id` INTEGER primary key, auto-generated, so an id is
  never reused; `mediaUri` TEXT NOT NULL, indexed; `pipelineVersion` INTEGER NOT NULL, because
  ADR-0023 §1 stamps every Tier C row; `boxLeft`, `boxTop`, `boxRight`, `boxBottom` REAL NOT NULL,
  in ADR-0022 §1's frame and form; `embedding` BLOB NOT NULL, float32 little-endian, its length not
  fixed by the schema; `groupId` and `groupingVersion`, INTEGER, both null until grouped.
- **No third table.** Amendment 5's grouping bookkeeping is `groupingVersion` on each face: a face is
  grouped when it equals the current grouping version, which also says how far a grouping cut short
  got. Whether per-group summaries are stored stays 3.4's; if they are, that is `Migration(5, 6)`.
- **No foreign keys.** The store's `upsert` is `INSERT OR REPLACE`, and an `ON DELETE CASCADE` would
  fire on every Tier A replace and on a heal's delete, removing Tier C rows Amendment 5 says to keep;
  and the `InMemory*` store has no cascade to mirror. Every rule is written in the store instead.
- No other database index, by Amendment 4's precedent: one is added when a measured query needs it.

**Migration.** `MIGRATION_4_5` creates both tables and the face table's index, empty, and changes
no existing table; it is registered beside the other three, with no destructive fallback, and the
version becomes 5. Copy each `createSql` from the generated `5.json`, so the migration cannot differ
from the schema. `5.json` is outside `app/src/`: `git ls-files app/schemas` must list it.

**Store — Decided at 3.2: on `MediaIndexStore`, not a fourth store interface.** Amendment 5 puts
every Tier C change in the transaction that changes the image row, and only the store that owns
that row can do it, in Room and in memory alike; the watermark store stands apart because nothing
ties it to a row. Every test double of `MediaIndexStore` delegates with `by`, so none breaks. All
new methods are `suspend`. **No new `Flow`:** 3.4 and 3.5 shape what they observe, and a query
costs no migration.
- **New:** `writeFaceIndexResults(writes)` — one transaction; each write carries the
  `MediaIdentity` it was computed from and applies only while the index row still has that URI and
  healing triple, replacing the image's state and faces; returns how many applied. A `FAILED` write
  carries no face. `getFaceIndexState(mediaUri)`, `listFaces(mediaUri)`, `hasFaceData()`.
  `healMediaUri(fromMediaUri, healed)` — one transaction: the row moves, its Tier C rows are
  re-keyed and kept, and a target that had Tier C rows of its own loses them to the moved ones;
  `MediaIndexReconciler` uses it instead of its non-atomic delete-then-upsert.
  `deleteAllFaceData()` — below.
- **Changed:** `upsert` deletes each written URI's Tier C rows in its transaction, so a re-extracted
  image is pending — Tier A writes a row only by extracting the file, and for a stored row that is a
  re-extraction; the heal no longer goes through it. *Reversed 2026-11-20 (decision 48, ADR-0018
  Amendment 6): only when the stored row's healing triple differs — a re-extraction is not a changed
  file; see 3.2 follow-up.* Per URI, not one `IN` list, which
  API 28's SQLite caps at 999 variables. `deleteByMediaUri`, `deleteByVolume` and `deleteAll` take
  the matching Tier C rows with them. `writeResolvedPlaces` does not touch Tier C.
- Models and a pure embedding codec go in a new `data/MediaIndexTierC.kt`. Face classes compare
  embeddings by content, and neither their `toString` nor any exception message carries a box, an
  embedding or a URI (ADR-0024 §6). The `InMemory*` store copies embeddings in and out.

**Deleting face data — Decided at 3.2: `secure_delete`, then a `TRUNCATE` checkpoint.**
- `PRAGMA secure_delete = 1` from a `RoomDatabase.Callback.onOpen` in `create`, so every Tier C
  delete or update — re-extraction, heal, reconciliation, the switch — zeroes the old bytes; and again
  as the first statement of the deletion's transaction, read back and required to be 1, before both
  tables are emptied. SQLite's documentation names it, or `VACUUM`, for leaving no forensic trace;
  `FAST` leaves traces in free pages, so `1`. AOSP's current build compiles it on by default; an
  OEM's, an older one's and the JVM's need not, so it is set rather than assumed. A failed read-back in `onOpen` must not stop
  the index opening; in the deletion it fails the deletion.
- After the commit, `PRAGMA wal_checkpoint(TRUNCATE)`; a first column of 0 means the write-ahead log
  was emptied. With a rollback journal — Room's choice on a low-RAM device — it is a no-op returning
  0, and the journal is truncated at commit *— in `TRUNCATE` mode only, which is the platform's
  default rather than a setting; amended 2026-11-20, decision 48: the deletion now reads the mode,
  and `PERSIST` reports not scrubbed*. The method returns the rows deleted and whether the
  scrub completed; a reader holding the old snapshot past the busy timeout makes it incomplete, and
  the caller repeats it. With nothing stored it only checkpoints, so ADR-0024 §3's start-up rule can
  call it whenever face data is off — that wiring is 3.3's or 3.6's.
- **`VACUUM` rejected:** it rewrites all of Tier A on every switch-off, builds the copy in RAM on
  Android (`SQLITE_TEMP_STORE=3`), and one cut short by process death is only recovered by another
  full vacuum, where an interrupted checkpoint is recovered by a checkpoint.

**Keeping Tier C true — all of its store rules land here**, because they are invariants of the rows
3.2 creates and need no pipeline: ~~pending on re-extraction~~ pending on a changed healing triple
(amended 2026-11-20, decision 48), the atomic heal, deletion with the image
row, and the identity-conditional write. An absent volume keeps its rows because reconciliation never
reaches it (ADR-0020 clause 4): tested, not coded. Later, as their records say: the face-data setting
checked inside the write transaction (ADR-0024 §3), with the setting, at 3.3; the bounded retry count,
3.3; grouping writes and "never twice at once", 3.4.

**Backup — Decided at 3.2: exclude `media_index.db-journal` as well.** The deletion adds no file.
But Room's automatic journal mode is a rollback journal on a low-RAM device, which leaves a
`media_index.db-journal`; one left hot by a crash holds pages as they were before the transaction —
face data, once Tier C exists — and rules take no wildcards. One `<exclude domain="database"
path="media_index.db-journal"/>` in `backup_rules.xml` and in both sections of
`data_extraction_rules.xml`. `<exclude>` only, never `<include>` (ADR-0014).

**Tests.** Unit only.
- `MediaIndexDatabaseMigrationTest`: the seeding helper gains version 4; a 4 → 5 test — tables
  present and empty, every existing column and watermark unchanged; a migrated 4 → 5 file reopened
  with the production builder takes a face write; the existing tests register `MIGRATION_4_5`, expect
  version 5, and the full chain runs 1 → 5. **Every new test names its file `media_index.db`** (repo
  memory: a seeded file opens under Robolectric only with the production name).
- `MediaIndexDaoTest`: the Tier C queries, on `inMemoryDatabaseBuilder`.
- New `MediaIndexTierCStoreTest`, Robolectric: every rule above against both `RoomMediaIndexStore`
  and `InMemoryMediaIndexStore` — the conditional write applied, skipped for a gone URI and for a
  changed triple, replacing; `FAILED` with faces rejected; re-extraction pending *(a changed
  triple only, since 2026-11-20 — 3.2 follow-up)*; heal re-keys and
  keeps, and the collision; each delete path; `writeResolvedPlaces` leaving Tier C alone;
  `deleteAllFaceData` removing every Tier C row and nothing of Tier A, the place columns or the
  watermarks, and doing nothing a second time.
- New `MediaIndexFaceDataScrubTest`, Robolectric, a file through the production `create`: the file
  pre-created with `auto_vacuum = NONE`, the worst case; journal mode asserted to be WAL and
  `secure_delete` to be 1; every embedding carrying a fixed byte marker; some images re-extracted
  and one healed first, so earlier deletions are covered; the marker found in the files before, as a
  control; after the deletion it is in no `media_index.db*` file, open or closed. A second case on a
  low-RAM device, journal mode `truncate`. A third with a reader holding the old snapshot: not
  scrubbed; after release, repeated, scrubbed. The report states that the test fails with
  `secure_delete = 0` and without the checkpoint. **If the JVM reports any journal mode but the
  expected one, stop and report:** do not weaken the assertion.
- New `MediaIndexTierCTest`, plain JVM: codec round trip and byte order; box and embedding checks;
  `toString` carrying no value.
- `MediaIndexReconciliationTest`: a heal keeps re-keyed Tier C rows, a deleted image loses them, an
  absent volume keeps them.
- **No instrumented test.** The property is SQLite's; Robolectric runs the framework's
  `SQLiteDatabase` over a real file, and both pragmas are set, not assumed. The on-device check is
  3.7's delete-switch walkthrough, **run on the phone — never by pulling the file off it**
  (decision 9).

**Not 3.2's:** the capability check and its persistence, the disclosure's store, the upright-frame
test, the retry count, the face-data setting, the pass and its logger test — 3.3; grouping — 3.4.

**Files — one sequential Coder task.** The entities, the converter, the DAO and the store models
depend on one another, and `5.json` and `MIGRATION_4_5` must come from the final entities. Under
`app/src/main/java/com/davide/seddio/easygallery/data/`: `MediaIndexTierC.kt` (new),
`MediaIndexEntities.kt`, `MediaIndexDao.kt`, `MediaIndexDatabase.kt`, `MediaIndexStore.kt`,
`MediaIndexReconciliation.kt`. `app/src/main/res/xml/backup_rules.xml` and
`data_extraction_rules.xml` — disjoint, so they may be split off. `app/schemas/…MediaIndexDatabase/5.json`
(generated, committed). Under `app/src/test/…/data/`: `MediaIndexTierCTest.kt`,
`MediaIndexTierCStoreTest.kt`, `MediaIndexFaceDataScrubTest.kt` (new), and
`MediaIndexDatabaseMigrationTest.kt`, `MediaIndexDaoTest.kt`, `MediaIndexReconciliationTest.kt`.
Nothing in `ui/`, no `*Content` signature, no string, no manifest. The Kotlin files are CRLF and new
ones should be too; the two XML files are LF.

**Verification** is its own phase, once, owned by whoever the brief names: the definition of done,
`adb devices` stated, and `git ls-files app/schemas` listing `5.json`.

**After it lands — the Planner.** `ARCHITECTURE.md` §3 (the new file and five changed rows) and §6
(four tables, version 5, the deletion, the fourth exclusion), with ADR-0018 Amendment 5 linked from
both, per the exit criterion; `AGENTS.md` *The two databases*; a dated pointer in Amendment 5's
*Backup* and *Deleting face data* to what 3.2 chose; this step's row and the handoff. ✅ **Done
2026-11-19**, with the test conventions' Robolectric SQLite note besides.

**No approval is needed:** no dependency, permission, preferences file or `*Content` signature.

#### Built and verified — 2026-11-19

The Coder built it as one task from the brief above. **The verification phase ran the definition of
done once, over the integrated change:** the unit suite, 935 tests, 0 failures;
`compileDebugAndroidTestKotlin` compiled; a device was attached, the SM-G990B, so
`connectedDebugAndroidTest` ran, 257 of 257 passed. That run is device-verified only for what the
suite exercises: it holds no Tier C test, as the brief planned, and the 4 → 5 migration was **not**
checked by hand against the phone's existing index. The brief's last check, `git ls-files
app/schemas` listing `5.json`, belongs to the change being staged; this document does not record
tracking state. Whether the Coder showed the scrub test failing with `secure_delete = 0` and
without the checkpoint, as the brief asked, is not among the facts the Planner was given.

**What the scrub test learned about Robolectric's SQLite**, now also in the
[test conventions](../../.github/instructions/tests.instructions.md):
- A new file starts with `auto_vacuum = 1`, and the header is already written when the file opens,
  so the brief's worst case, `auto_vacuum = NONE`, takes `PRAGMA auto_vacuum = 0` followed by
  `VACUUM`.
- A non-WAL file reports `journal_mode = memory`, where a device uses `TRUNCATE`. The brief's
  *stop and report* fired here, and was resolved without weakening the assertion: the low-RAM case
  sets `TRUNCATE` on the store's write connection, so the `-journal` file exists as on a device,
  and then asserts it. The WAL case asserts WAL, as briefed. What this leaves unproven is below.
- A test's reader must hold a read snapshot only. `beginTransactionNonExclusive` is
  `BEGIN IMMEDIATE`: it takes the write lock and fails the deletion's own transaction with
  `SQLITE_BUSY`, which reads as a production bug and is not one.
- On Windows the open `-shm` cannot be read; the test treats it as empty, since it holds page
  numbers and hashes, never page content.

**Not proven on the JVM, and moved to step 3.7's walkthrough:** that a device which uses a rollback
journal really uses `TRUNCATE` and leaves no `-journal` behind; the platform's real SQLite defaults,
rather than Robolectric's; and a checkpoint blocked by a real concurrent reader. The models redact
`toString`, as the brief asked.

#### Reviewed — 2026-11-20

The Reviewer reviewed step 3.2 as built. Three of his findings reached the Planner, and they are the
only ones recorded here; the numbering is his.
- **Spec 2 — when Tier C is dropped.** `upsert` drops a URI's Tier C rows on every write, as ADR-0018
  Amendment 5 said, but Tier A rewrites rows whose file did not change: it reselects on a
  `MediaStore` generation change, which a metadata-only update causes — probably a favourite toggle
  (ADR-0011), **not verified on a device** — and reselects a whole volume when its version changes.
  Each favourite toggle would cost a face pass for that image, and a resync would delete every face
  on the volume. ✅ **Resolved 2026-11-20, as he suggested, by the maintainer's acceptance** —
  [albums.md](albums.md) §8 decision 48, ADR-0018 Amendment 6 §1: Tier C is dropped only when the
  stored row's healing triple differs from the incoming row's; an unchanged triple keeps its state
  and faces, and the pipeline-version rule is unchanged.
- **Spec 3 — the rollback journal's mode.** 3.2 set `secure_delete` rather than assuming it, but left
  the non-WAL journal mode to the platform, and outside WAL the checkpoint reads as success. Under
  `PERSIST` the journal keeps the deleted rows' pages after commit while the deletion reports
  scrubbed. ✅ **Resolved 2026-11-20, as he suggested, by the maintainer's acceptance** — decision 48,
  ADR-0018 Amendment 6 §2: the deletion reads the journal and locking modes and reports not scrubbed,
  without throwing, on a mode that keeps page copies or that it does not recognise. The Planner chose
  the check over setting a mode at open; the amendment gives the reasons.
- **Judgement 3 — the test `a heal that keeps the uri keeps the tier c rows`** heals with a changed
  `displayName` and expects the faces kept, which contradicts Spec 2's resolution. The maintainer did
  not rule on it. **Planner decision, 2026-11-20, which he can reverse: `healMediaUri` applies the
  same triple test, and the test is rewritten to match**, rather than leaving the method unconditional
  and only changing the test's data. Three reasons:
  - Amendment 6 states one rule — face data belongs to the triple it was computed from — and 3.2 put
    every Tier C rule in the store rather than in its callers. To the store, a "heal" that changes
    the triple is a changed file under a new key.
  - It costs nothing on the path that exists: the reconciler heals only through `LiveMediaLookup`'s
    healing-key match, so its healed row's triple always equals the source's, and
    `MediaIndexReconciliationTest` should pass unchanged — which the follow-up checks.
  - Fixing only the test would leave a public store method that carries faces across a changed file
    for any later caller, and the old test, kept green, would read as the intent.

  Reversing it later is the guard's removal and one test.

#### 3.2 follow-up — the Coder's brief — built and verified 2026-11-20, below

What changes and why is ADR-0018 Amendment 6, not restated. **One sequential Coder task**: (a) and
(c) change the two store write paths in both implementations and the same test file, and (b) the
same store file.

**(a) A row write keeps Tier C while the file is unchanged.** Exit criteria:
- In `RoomMediaIndexStore` and `InMemoryMediaIndexStore`, `upsert` drops a URI's Tier C rows, in its
  transaction, only when the stored row at that URI has a different healing triple from the incoming
  row, or there is no stored row. Otherwise the state and faces are untouched: same face ids, same
  pipeline and grouping versions. The Tier A columns are written as before.
- The comparison is `MediaIdentity`'s healing key, through `toMediaIdentity()` — never a second,
  field-by-field copy of it (ADR-0020).
- Still per URI inside the transaction, never one `IN` list (API 28's 999-variable cap).
  `MediaIndexDao.getByMediaUri` already exists, so no DAO change is expected; if one is made,
  `MediaIndexDaoTest` covers it.
- `upsert`'s KDoc on the interface states the rule.
- Tests in `MediaIndexTierCStoreTest`, through `forEachStore` so both stores answer the same
  assertions:
  1. **the favourite-toggle shape** — same URI and triple, a different `generationModified` and a
     changed Tier A column: state and faces unchanged, ids equal, the Tier A column updated;
  2. a changed `size` drops them;
  3. a changed `dateModified` drops them;
  4. a changed `displayName` drops them;
  5. **the pipeline version is unaffected** — a state written at version 1 is still version 1 after
     an unchanged-triple write, neither cleared nor restamped; after a changed-triple write there is
     no state;
  6. one `upsert` call holding an unchanged row and a changed row drops only the changed row's.

  The existing `re-extracting an image makes it pending…` changes the size, so it still holds; its
  name may say "a changed file" instead.
- `MediaIndexTierABatchIndexerTest`: **one** test that a row reselected with an unchanged triple keeps
  its Tier C and one whose size changed loses it, through the real indexer and the in-memory store —
  if the existing fixtures can express a reselection. If they cannot, say so in the report rather than
  building new fixtures. `MediaIndexTierAIndexer.kt` itself does not change.

**(b) The deletion reports scrubbed only on a mode that keeps no page copies.** Exit criteria:
- `RoomMediaIndexStore.deleteAllFaceData` reads `journal_mode` and `locking_mode` on the connection
  its transaction ran on — pinned as `enableSecureDelete` is — and sets `scrubbed` by Amendment 6's
  table: `wal` by the checkpoint as now; `delete` and `truncate` only with `normal` locking; `memory`
  and `off` true; `persist` and any unrecognised value false. SQLite answers in lower case; compare
  without depending on case.
- The mapping is a pure function, tested without Robolectric for every row of the table, for
  `exclusive` locking under `delete` and `truncate`, and for an unknown value.
- It does not throw for a mode: the rows are deleted and `rowsDeleted` reported either way. A
  `secure_delete` that does not read back still throws and deletes nothing, unchanged.
- The KDoc of `FaceDataDeletion.scrubbed` and of `MediaIndexStore.deleteAllFaceData` say when it is
  false — a reader on the old snapshot, or a journal mode that keeps page copies — and that the
  caller repeats it at each start while face data is off, knowing that under `PERSIST` a repeat
  reports false again. `checkpointTruncate`'s comment no longer reads as the scrub's whole evidence.
- `InMemoryMediaIndexStore` keeps `scrubbed = true`: it has no file.
- `MediaIndexFaceDataScrubTest`, a new case: production `create` on a low-RAM device, as the
  `TRUNCATE` case opens it, then `PRAGMA journal_mode = PERSIST` on the store's write connection
  **outside any transaction**, since SQLite refuses a mode change inside one; Robolectric otherwise
  reports `memory`. Assert it reads `persist`; populate as the other cases do; delete. Expect the
  rows deleted, `hasFaceData()` false and **`scrubbed = false`**. As the control that shows why, the
  byte marker is found in `media_index.db-journal` after the deletion. If the control finds no marker,
  stop and report rather than drop it: `scrubbed = false` must hold either way, but the test would no
  longer show the reason.
- The three existing scrub cases pass unchanged. The report states that the `PERSIST` case fails
  when the journal-mode check is removed.

**(c) The heal applies the same test.** Exit criteria:
- In both stores, `healMediaUri` re-keys and keeps the source's Tier C rows only when `healed`'s
  triple equals the stored source row's. Otherwise neither the source's nor the target's survive, for
  a heal that keeps the URI and for one that moves it. Its KDoc says so.
- `a heal that keeps the uri keeps the tier c rows` heals with an unchanged triple, changing only a
  column outside it — the volume name, or a captured field — and still expects the rows kept.
- A new test: a heal whose triple differs, with the URI kept and with it moved, leaves no Tier C row
  at the source or the target.
- `MediaIndexReconciliationTest` passes **unchanged** — the evidence that no heal the reconciler
  makes is affected. If it needs a change, stop and report: that would mean the reconciler heals on
  a changed triple, which contradicts its lookup.

**Files.** Under `app/src/main/java/com/davide/seddio/easygallery/data/`: `MediaIndexStore.kt` (both
stores and the interface KDoc), `MediaIndexDatabase.kt` (the journal- and locking-mode read beside
the two pragma helpers, and `checkpointTruncate`'s comment), `MediaIndexTierC.kt`
(`FaceDataDeletion`'s KDoc only). Under `app/src/test/…/data/`: `MediaIndexTierCStoreTest.kt`,
`MediaIndexFaceDataScrubTest.kt`, `MediaIndexTierABatchIndexerTest.kt` (the one test above), the
pure mapping's test — in `MediaIndexTierCTest.kt` or a new file — and `MediaIndexDaoTest.kt` only if
the DAO changes. **Not expected to change:** `MediaIndexTierAIndexer.kt`,
`MediaIndexReconciliation.kt`, the entities, the schema and its JSON — no migration, version 5
stays. The Kotlin files are CRLF; check any new one after writing it.

**Not the follow-up's:** wiring the start-up deletion (3.3 or 3.6); how Settings shows a deletion
that is not scrubbed (3.6); the phones' actual journal modes (3.7, item 1 below).

**Verification** is its own phase, once: the definition of done, with `adb devices` checked and the
result stated. No instrumented test is added.

**After it lands — the Planner.** `ARCHITECTURE.md` §3, the `MediaIndexStore.kt` row's account of
`upsert` and `healMediaUri`, and the `MediaIndexDatabase.kt` row if a helper was added; §6's deletion
paragraph, for what it reports under each journal mode, linking Amendment 6. The test conventions'
scrub-test paragraph, which says a rollback-journal test sets `TRUNCATE`. A dated "built" note in
ADR-0018 Amendment 6. This section, step 3.2's row — ✅ again when the gate passes — and the
handoff. ✅ **Done 2026-11-20**, with `designer.agent.md`'s stale sentence about the index besides;
the `MediaIndexDatabase.kt` row changed too, for the two mode reads and `deletionIsScrubbed`.

**No approval is needed:** no dependency, permission, preferences file, migration or `*Content`
signature.

#### 3.2 follow-up — built and verified 2026-11-20

The Coder built it as one task from the brief above, with no schema change: the index stays at
version 5. **The verification phase ran the definition of done once, over the integrated change:**
`compileDebugKotlin` compiled; the unit suite, 954 tests, 0 failures; `compileDebugAndroidTestKotlin`
compiled; a device was attached, the SM-G990B, so `connectedDebugAndroidTest` ran, 257 of 257
passed — device-verified only for what that suite exercises, which holds no Tier C test.

**Checked by the Planner in the code**, not only in the report: one private `keepsTierC` in
`MediaIndexStore.kt` compares `MediaIdentity`'s healing key, and both stores' `upsert` and
`healMediaUri` decide through it as (a) and (c) required; `deleteAllFaceData` reads both modes inside
its transaction on the connection that deleted, and asks the checkpoint only under `wal`; the
in-memory store reports `scrubbed = true`. The tests the brief asked for exist: `MediaIndexScrubModeTest`,
plain JVM, for every row of the table, `exclusive` locking, unknown values and case; the six row-write
tests and the three heal tests in `MediaIndexTierCStoreTest`; the one indexer test in
`MediaIndexTierABatchIndexerTest`, so the fixtures could express a reselection; and the `PERSIST`
case in `MediaIndexFaceDataScrubTest`, whose control finds the marker in `media_index.db-journal`.
The Coder reports
that each guard's removal fails the tests it should; the Planner did not re-run that.

**Not proven by it:** each phone's real journal and locking modes; that a favourite toggle advances
`generationModified` and leaves Tier C intact — the claim Amendment 6 §1 rests on, not checked on a
device; `exclusive` locking at runtime, covered by the pure mapping only; and a URI written twice in
one `upsert` call, whose order can only drop Tier C, never keep it wrongly. The first two are step
3.7's, below.

### 3.2 to 3.8

At the level of detail Stage 4's plan used for its later steps; each is refined when 3.1 has
settled what it depends on.
- **3.2 — Tier C schema and store.** ~~Tables in the index database: version bump, a real migration,
  committed JSON, `Room*` and `InMemory*` store implementations, DAO tests; each row stamped with
  the model version. The backup exclusion is the same file; check it rather than assume it.~~
  Refined 2026-11-19 into its own section above.
- **3.3 — The pipeline.** Detection and embedding inside indexing, over images only (decision 40), under the existing constraints,
  honouring cancellation and the permission checks, starting only after the disclosure. A failed
  inference is stored as absent, §6's third state. Not one line of face data in a log. YuNet and
  SFace fp32 on ~~a trimmed OpenCV~~ OpenCV's published artifact, untrimmed (E3), with the
  capability check (decision 45); ~~confirms OpenCV's module list and measures the trimmed size~~
  measures the size it actually adds. **Refining it settles**, besides, where the capability
  result and the disclosure-shown flag are kept, and F1 — both in the handoff above.
- **3.4 — Clustering and the person record.** Per B1 (decision 45: average linkage, cosine 0.50)
  and B2, and decisions 42 and 46 for an anchor. Average linkage's memory and time in the app are
  unmeasured — about 800 MB for the distance matrix at 10,000 faces — so 3.4 measures them before
  relying on them; a different algorithm is a new decision, not a detail.
- **3.5 — The `people` clause and evaluation.** Parse, encode and evaluate, with strictness tests
  like Stage 2's; combined people + region + time, with tests.
- **3.6 — The UI.** The People criterion with its thumbnails, with no merge and no split (decision
  40); the notice of an unevaluatable people album, whose wording is open (decision 42); the
  disclosure, and the Settings switch; the Settings row showing the licence notices (E5); strings in
  every shipped locale; vocabulary in-change.
- **3.7 — Verification.** The definition of done, once, with `adb devices` checked and stated;
  then an installed-app walkthrough including the delete switch, and the capability check on the
  SM-G781B if it can be attached (decision 45).
  - **Added 2026-11-19 from 3.2** — what its scrub test could not prove on the JVM, checked on the
    phone and without the database leaving it (decision 9); how each is observed is 3.7's to settle:
    1. ~~a device that uses a rollback journal really uses `TRUNCATE`, and a deletion leaves no
       `media_index.db-journal` holding face data.~~ *Amended 2026-11-20, decision 48:* the journal
       mode and locking mode each phone's index actually uses, recorded — `PERSIST` or `exclusive`
       on either phone is the trigger ADR-0018 Amendment 6 names for revisiting it — and, wherever a
       rollback journal can be reached, that a deletion reporting scrubbed leaves no
       `media_index.db-journal` holding face data. Room chooses it on a low-RAM device, which
       neither phone may be, so how to reach that mode is part of the question. *Sharpened
       2026-11-20, after the follow-up:* the modes are read after a real deletion, since the
       deletion's result depends on the modes it committed under;
    2. the platform's real SQLite defaults — `auto_vacuum`, the journal mode, and `secure_delete`
       reading back as on — rather than Robolectric's;
    3. a checkpoint blocked by a real concurrent reader is reported incomplete, and a repeat
       finishes it.
  - **Added 2026-11-20 from the 3.2 follow-up:**
    4. a favourite toggle leaves the image's Tier C rows intact through the next index run, and its
       `generationModified` advances — ADR-0018 Amendment 6 §1 rests on that and it is still only
       expected. If the generation does not advance, the toggle never reselected the row and the
       amendment's motive weakens without its rule becoming wrong.
- **3.8 — Close-out.** `ARCHITECTURE.md`, exit criteria checked against the repository,
  `/docs-audit`, archive.

---

## Stage 3 exit criteria — confirmed at 3.1, 2026-11-19

Derived from Stage 2's and from decision 9's obligations. ~~**Not binding until 3.1.**~~ **Binding
since step 3.1 closed on 2026-11-19.** The Planner checked each line against the adopted records
and decisions 43–47: lines that were labelled proposed only because those records were not yet
adopted are now plain criteria, and lines the records made more precise say so. Made concrete
on 2026-11-18 by decisions 44–46 (delegated) where those settle a figure or a policy.

- The checks in `AGENTS.md`'s definition of done pass, with `adb devices` checked and the result
  stated.
- Every step is ✅, or explicitly dropped with the drop recorded in [albums.md](albums.md) §7.
- A people criterion combined with place and date works end to end, and **a test proves a combined
  people + region + time rule**, with several people meaning all of them.
- **Count consistency:** the count the screen shows for the chosen people, with the other choices,
  equals the evaluator's member count for the rule it would write, on the JVM and on a device.
- An item not yet face-indexed leaves the album waiting on the index, never a settled no-match; an
  item whose inference failed is neither pending work nor covered (§6).
- After a restore or a full re-index, a `people` clause finds each person through its anchor, one
  per person (decisions 41 and 42), each case proved by a test:
  - the anchor's face in a different group, or in a group seen in one image only, resolves to that
    group;
  - an anchor photo not yet face-indexed leaves the album waiting on the index, not unevaluatable —
    the state every people album passes through after a restore;
  - an anchor photo deleted, or no face at the anchored position above the minimum overlap — IoU
    0.5 (decision 46), or a face there under 40 px — makes
    the album unevaluatable: never widened, never deleted automatically, its rule not rewritten
    except by the user picking the person again (ADR-0017, as 3.1 amends it).
- **The People picker, decision 40:** thumbnails carry no label and no person name is stored; a
  person is offered only if seen in at least two different images, and that threshold applies to
  the picker only, not to evaluation; the thumbnails are sorted by photo count, most first
  (amended 2026-11-18), the count being of distinct images, with ties by the group's most recent
  photo date and then a stable id (decision 46); videos are not face-indexed and do not count; faces
  under 40 px are not grouped (decision 46); there is no
  merge and no split.
- **Decision 9, each obligation checked:**
  - the one-time, plain-language disclosure appears ~~when face indexing first starts~~ before face
    indexing first starts — at the first app open on a capable device with face data on, once per
    installation, and never on an incapable device (ADR-0024 §2; E6, E7) — and says that
    faces are grouped on the device, that nothing leaves the phone, and where the off switch is;
  - the Settings switch deletes the face data, not merely stops adding to it, shown by a test and on
    the device;
  - face data never leaves the device (no network request in the face path), never enters a backup
    (checked against the rules files), and never appears in a log (~~how that is checked is decided
    at 3.1 — proposed in~~ checked as [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md)
    §6 decides, adopted 2026-11-19). The anchor in the album database is not face data (decision 9's 2026-11-17
    clarification), and nothing else face-related is written there.
- The Play Data Safety question of decision 9 is answered and recorded.
- The index database reaches its new version through a real migration, every schema JSON is
  committed, and the index is still excluded from backup, checked against the rules files.
  *2026-11-19, step 3.2: version 5 through `Migration(4, 5)`; the rules files exclude the file and
  its `-wal`, `-shm` and `-journal` sidecars in all three sections, checked against them. Open:
  `5.json` tracked, checked by `git ls-files`; and F1.*
- The merged manifest is checked after the runtime dependency — OpenCV (decision 45) — lands, and
  any permission it adds is approved or removed.
- The licence obligations of any shipped weights are met (A5): the Apache-2.0 licence and a
  statement of any change for OpenCV and SFace, and MIT's notice for YuNet, as files in the APK
  (decisions 44 and 45), and shown from a row in Settings (E5); and whatever OpenCV's bundled
  components owe, once read.
- The face models **and the OpenCV runtime** add at most 50 MB to the app's download size (A3;
  decision 45 counts the runtime), measured on the release bundle's arm64-v8a delivery once ~~the
  trimmed build lands~~ OpenCV lands (untrimmed, E3), not estimated.
- ~~Below the threshold set at 3.1, the People criterion is not offered on the New automatic album
  screen; an existing people album on such a device is unevaluatable, never hidden or deleted —
  the second half to be confirmed at 3.1 (A4).~~ Where the face pipeline fails its capability
  check — the native library or either model does not load, or the self-test embedding fails —
  the People criterion is not offered on the New automatic album screen, and an existing people
  album on that device is unevaluatable, never hidden or deleted (A4; decision 45). Each half is
  proved by a test.
- ~~**Proposed 2026-11-18 by the Planner, for the models, runtime and delivery ADR to confirm:**~~
  **Confirmed 2026-11-19 by ADR-0023's adoption:** the
  app is still offered on Play to every device it was offered to before, OpenCV notwithstanding,
  checked with bundletool; and every native library is 16 KB aligned. *Carried into
  [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) as verify
  items 1 and 2~~, awaiting approval~~.*
- ~~**Proposed 2026-11-18 by the step 3.1 drafts; binding when each is adopted:**~~ **From step
  3.1's records, binding since their adoption on 2026-11-19:**
  - switching face data off leaves no Tier C row and no deleted face data in the database's free
    pages or write-ahead log, proved by a test; a deletion cut short is repeated at the next start
    (ADR-0018 Amendment 5, ADR-0024); *2026-11-19: the deletion and its test,
    `MediaIndexFaceDataScrubTest`, landed at 3.2; the switch that calls it and the repeat at start-up
    have not, and three device-only properties are 3.7's;*
  - a video or GIF never leaves a people album waiting (ADR-0017 Amendment 4);
  - stored face boxes and anchors share the upright frame, proved on a photo with an EXIF rotation
    (ADR-0022);
  - a rule with a `people` clause round-trips under a locale with a decimal comma (ADR-0017
    Amendment 4);
  - neither new cause of unevaluatable uses the Newer-app wording (ADR-0017 Amendment 4);
  - the Tier C pass is resumable across runs and stops cleanly when the worker is stopped (ADR-0023
    §4);
  - grouping's peak memory and time are measured on the device and are within a budget the
    maintainer accepted (ADR-0023 §5);
  - switching face data off asks for confirmation first (ADR-0024 §3; E8); while it is off, no
    Tier C pass runs, the People criterion is not offered, and an existing people album is
    unevaluatable with the "people unavailable" cause, not waiting (ADR-0024 §4; E9); switching it
    back on shows no second disclosure (E10);
  - a healed anchor URI is never written back to the rule (ADR-0022 §4; E1);
  - the model weights are committed with checksums the build checks (ADR-0023 §1; E4), and
    OpenCV is packaged for arm64-v8a only, with no app-wide ABI filter (ADR-0023 §2; E3).
- Indexing runs under the existing constraints, with no notification and no foreground service.
- Every spike artefact holding face data — crops, embeddings, labels — and every photo copied off
  the phone has been deleted, on the phone and anywhere else it went, and the deletion recorded
  here (A7). ✅ **Met 2026-11-18** — *Spike data deleted*. The SM-G781B part rests on the
  notebook's record, not on a check.
- Every new string is in every shipped locale; the vocabulary has moved; `ARCHITECTURE.md`
  describes what landed; a documentation audit has passed.
- **`ARCHITECTURE.md` links ADRs 0022–0024, ADR-0017 Amendment 4 and ADR-0018 Amendment 5** from the
  sections they govern. *Deferred 2026-11-18 under the instructions' Timing rule:* they are adopted
  ahead of the work they govern, so each link is added by the change that lands that work, not by
  the change that adopts the record. *2026-11-19, step 3.2:* ADR-0018 Amendment 5 is linked from §3
  and §6; ADR-0024 from §3 and §6, for the redaction, the backup exclusion and the deletion;
  ADR-0022 from §3, for the box frame. Still to come as their work lands: ADR-0023, ADR-0017
  Amendment 4, and ADR-0022 and ADR-0024 from the sections their later steps change.

---

## Licence findings (A5)

~~**Not yet read.**~~ **Read by the spike on 2026-11-17, before any download, and transferred here
by the Planner on 2026-11-18.** The spike reads the terms of every model before downloading anything (A5,
answered 2026-11-17), and records them here in the form [albums-stage-4.md](albums-stage-4.md)'s
*Licence findings* used: what was read and when, what it requires if the weights ship, the action
taken, and that the Planner is not a lawyer. Only models whose licence allows commercial use and
redistribution inside an app are measured; any other candidate is recorded here as excluded, with
the reason.

> **Every recognition model found was trained on face data whose publishers state research-only
> terms, or whose terms are undeclared.** That includes the three that passed. The maintainer chose
> on 2026-11-17 to measure them anyway, and to decide at step 3.1 whether any of them ships.

**2026-11-18:** the maintainer accepted the training-data risk for SFace — [albums.md](albums.md) §8
decision 43. **Later the same day, by delegation,** the Orchestrator accepted it for YuNet —
decision 44 — and chose SFace fp32 and YuNet to ship, on OpenCV — decision 45. OpenFace and dlib
do not ship.

**The Planner is not a lawyer**, and neither was the spike; this records what the texts say.

**The rule applied, stated so it can be challenged.** A candidate was measured only if the
*publisher of the weights* states, in text covering the weight files, a licence that allows
commercial use and redistribution: Apache-2.0, MIT, BSD, CC0 or public domain. A licence on the code
repository alone, with the weights published as release assets or download links and nothing said
about them, counts as unclear, and unclear fails. What a *dataset's* publisher says was recorded as
a caveat, not as a verdict: the weights' publishers do not invoke it, and the spike did not
adjudicate it.

| Candidate | Role | Weights licence, as its publisher states it | Training data, and what its publisher states | Verdict |
|---|---|---|---|---|
| **YuNet** (OpenCV Zoo) | detector | MIT, for every file in its zoo directory | WIDER FACE, whose page states **CC BY-NC-ND** for the dataset | **Passed**, with that caveat. Measured |
| **MediaPipe BlazeFace** | detector | Apache-2.0, in its model cards | Consented images from a mobile AR app. **The card puts "any form of surveillance or identity recognition" out of scope** | **Passed on licence, flagged**: the app would group identities, though with another model for the embedding. Not measured: a second detector was optional |
| **ML Kit Face Detection** | detector | None. A service agreement, not a licence to redistribute a model | The terms say ML Kit may contact Google's servers and send usage metrics to Google | **Excluded**: service terms, server contact and telemetry. Confirmed by the maintainer 2026-11-17 |
| **SFace** (OpenCV Zoo) | recogniser | Apache-2.0, for every file in its zoo directory. The upstream repository shows no licence file | Not stated for the shipped checkpoint. Upstream names CASIA-WebFace, VGGFace2 and MS1MV2 from InsightFace's data zoo, **which InsightFace says is for non-commercial research only** | **Passed**, with a material caveat. Measured |
| **OpenFace nn4.small2.v1** | recogniser | Apache-2.0, stated for the trained models | FaceScrub ("a creative commons license", which one not read) and CASIA-WebFace (terms not read) | **Passed**, with that caveat. Measured, as a weak baseline |
| **dlib ResNet v1** | recogniser | Public domain, stated by its publisher — the clearest text of all | About 3 million faces: FaceScrub, VGG and images scraped from the internet; no restriction stated by the publisher | **Passed**. Measured |
| **EdgeFace** | recogniser | CC BY-NC-SA 4.0 | WebFace260M | **Excluded**: non-commercial |
| **InsightFace family** (ArcFace, buffalo_l and others) | recogniser | Non-commercial research only | MS1M, VGG2, CASIA-WebFace | **Excluded**: non-commercial |
| **GhostFaceNets** | recogniser | Nothing stated about the weights | MS1MV2, MS1MV3 | **Excluded**: unclear |
| **MobileFaceNet** (foamliu; Qualcomm AI Hub's TFLite export) | recogniser | Apache-2.0 on the code; whether it covers the weights is unclear | Refined MS-Celeb-1M | **Excluded**: unclear. The spike would have revisited it first, as a 4 MB TFLite model; the maintainer kept it excluded on 2026-11-17 |
| **FaceNet** (davidsandberg) | recogniser | Nothing stated about the weights | CASIA-WebFace, VGGFace2 | **Excluded**: unclear |
| **py-feat mobilefacenet** | recogniser | Its own card says a redistribution licence "has not been established" | Unknown | **Excluded** by its publisher's text |

**What was read**, as the notebook names it, on 2026-11-17:
- **YuNet:** opencv_zoo `models/face_detection_yunet/README.md` and `LICENSE`; the
  `ShiqiYu/libfacedetection` and `libfacedetection.train` pages; the WIDER FACE page.
- **BlazeFace:** developers.google.com's face detector page, and its two model cards (short and full
  range).
- **ML Kit:** developers.google.com/ml-kit/terms (last modified 2025-05-14), the Google APIs Terms
  of Service (2021-11-09), and ML Kit's face-detection Android page.
- **SFace:** opencv_zoo's SFace `README` and `LICENSE`; the `zhongyy/SFace` repository and its
  READMEs; the Hugging Face card `opencv/face_recognition_sface`.
- **OpenFace:** its README's Licensing section, `get-models.sh`, and
  cmusatyalab.github.io/openface/models-and-accuracies.
- **dlib:** the `davisking/dlib-models` README.
- **The excluded recognisers:** `otroshi/edgeface` and the Hugging Face card
  `Idiap/EdgeFace-S-GAMMA`; `deepinsight/insightface`'s README; `HamadYA/GhostFaceNets`;
  `foamliu/MobileFaceNet` and the Hugging Face card `qualcomm/MobileFaceNet`;
  `davidsandberg/facenet`; the Hugging Face card `py-feat/mobilefacenet`.

**Not examined**, named so nobody assumes they were: AdaFace, CVLface, the ONNX Model Zoo's ArcFace
(an InsightFace model), OpenVINO's Open Model Zoo, and Facenet-PyTorch. The terms of CASIA-WebFace,
VGG Face and MS-Celeb-1M, and which Creative Commons licence FaceScrub uses, were not read beyond
what is quoted above.

**What shipping would require, and the action taken.** Nothing ships from the spike, so nothing is
owed yet. If a measured model ships, step 3.1 decides what is owed with the model (A5):
- **SFace and OpenFace are Apache-2.0**, and so is the OpenCV runtime. Stage 4's reading of
  Apache-2.0 applies: a copy of the licence and a statement of any change, as a file in the APK.
- **YuNet is MIT**, which requires its copyright and permission notice to go with the copy.
- **dlib's weights are public domain**, and owe nothing.

The training-data caveat is not discharged by any of these. It is the maintainer's decision at 3.1.
**Decided 2026-11-18:** SFace by the maintainer (decision 43), YuNet by delegation (decision 44).
**Now owed**, with SFace, YuNet and OpenCV chosen (decision 45): the Apache-2.0 licence and a
statement of any change for OpenCV and SFace, and MIT's notice for YuNet. **Not yet read:** the
licences of the third-party components OpenCV's Android build bundles, and of the C++ runtime it
ships with, which the models, runtime and delivery ADR reads. **2026-11-18:**
[ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) defers that
reading until the way the build is produced is decided (its question 1 and verify item 8), because
which components are bundled depends on it. **2026-11-19:** decided — OpenCV's published artifact
(E3) — so the reading is step 3.3's, from that artifact.

---

## Open questions

Each says what it blocks. A recommendation is given only where there is a reason for one.

### A. The spike — answered 2026-11-17

Answered by the maintainer on 2026-11-17; they no longer block 3.0. Where he delegated a choice to
the spike, the spike records what it chose and why. ~~Still open for 3.1: A4's threshold, and
the part of A4 marked to be confirmed.~~ Both settled 2026-11-18, by delegation — decision 45.

1. **A1 — Which detection and recognition models are measured?** Candidates to check, not choices:
   - **ML Kit Face Detection** — detection only, on-device. Whether it comes as a bundled model, a
     Play-services one, or both, and what each costs, is to be verified.
   - **MediaPipe Face Detector and Face Landmarker** — what each outputs, and what it costs, is to
     be verified.
   - **Recognition (embedding) models**, such as MobileFaceNet, FaceNet and the ArcFace family.
     **Their licences must be checked first (A5).** Several popular face-recognition weights are
     licensed for non-commercial research only; which ones is to be verified, and no licence is
     named here as fact.

   Detection without recognition cannot tell people apart, so at least one recognition model has to
   pass A5 for the spike to answer anything. *No recommendation until measured.*

   ✅ **Answered 2026-11-17: delegated to the spike** — "I let you choose the models", in the
   maintainer's words. The spike chooses one or more detectors and two to three recognition
   (embedding) models, every one passing A5 before it is downloaded. A candidate that fails A5 is
   recorded under *Licence findings* as excluded, not swapped for another silently.

   **Outcome, 2026-11-18:** detector YuNet, measured; BlazeFace passed but was not measured; ML Kit
   excluded. Recognisers SFace, OpenFace nn4.small2.v1 and dlib ResNet v1, measured; six others
   excluded. See *Licence findings* and *Spike outcome*.
2. **A2 — Which runtime?** LiteRT is the likely candidate, unverified. **Proposed:** delegated to the
   spike, as Stage 4's A2 was; adding the runtime to the app is still approved at 3.1. ✅
   **Answered 2026-11-17: as proposed.** **Outcome, 2026-11-18:** OpenCV 4.13.0's DNN module, and
   dlib 20.0 built natively; not LiteRT, because no model that passed A5 is TFLite (*Spike
   outcome*). **Chosen 2026-11-18, by delegation:** OpenCV's DNN module, bundled ~~and trimmed~~ —
   decision 45; untrimmed, by E3 (decision 47).
3. **A3 — How do the models reach the device?** Bundled in the app, or through Play services or
   AICore? **Stage 4's lesson:** app size was the cost that paused content (decision 38), so the
   size budget is a criterion for the choice, not something to discover afterwards; Play's size
   limits were never checked (Stage 4's A3a). Decision 37's "the model ships inside the app" was
   content-specific; Stage 3 has not been asked. ✅ **Answered 2026-11-17: bundled in the app is
   preferred, and app size is a hard criterion** — the face models may add **at most 50 MB** to the
   download size, the maintainer's figure. Play-services or system delivery is not excluded, but not
   preferred. **Outcome, 2026-11-18:** the SFace fp32 stack is estimated at 45.9 MB, with OpenCV
   as an upper bound; an int8 route at 16.8 MB is unmeasured for quality (*Spike outcome*).
   **Chosen 2026-11-18, by delegation:** bundled, fp32, the runtime counted against the budget —
   decision 45.
4. **A4 — What is the lowest device?** [albums.md](albums.md) §10 question 3, not yet asked for
   people. Also open: what a people album is on a device that cannot run the models — decision 37
   left open whether people follows content's unevaluatable rule. The spike's phone, the SM-G990B,
   is one data point, not the threshold. ✅ **Answered 2026-11-17:** the spike also measures the
   **SM-G781B (API 33)**, beside the SM-G990B. Below the threshold, the **People criterion is
   hidden** on the New automatic album screen; the threshold itself is set at 3.1 from the spike's
   figures. **An existing people album on such a device**, reached through a backup restore,
   follows decision 37's precedent: unevaluatable, never hidden or deleted. The Orchestrator noted
   that; the maintainer did not object but was not asked explicitly — **to be confirmed at 3.1.**
   **Outcome, 2026-11-18: not answered.** The SM-G781B was never attached, so the spike has no
   figures below the SM-G990B and the threshold has no data. ✅ **Answered 2026-11-18, by
   delegation — decision 45:** the policy stands, and the threshold is replaced by a capability
   check: the native library and both models load and a self-test embedding succeeds, or the
   People criterion is hidden. The restored-album half is confirmed: unevaluatable, never hidden or
   deleted.
5. **A5 — Licences.** The spike reads the terms of every model, and **reads them before downloading
   any recognition weights**. Findings go under *Licence findings* above. What is owed if the
   weights ship follows from what is read. ✅ **Answered 2026-11-17:** only models whose licence
   allows **commercial use and redistribution inside an app** are measured at all. The terms are
   read **before any download**, not only of recognition weights, and the findings go under
   *Licence findings*. **Outcome, 2026-11-18:** recorded there; three recognisers passed, every
   one with a training-data caveat. The notices owed for what ships are listed there too
   (decisions 44 and 45).
6. **A6 — Where does the spike live?** **Proposed, as before:** in `tools/`, in a directory of its
   own (for example `tools/face-model-spike/`), with a separate Gradle harness for the phone, a
   Python virtual environment if a desktop side is needed, and weights gitignored; the `.gitignore`
   entries added by the change that creates the folder. **Needs the maintainer's approval:** the
   spike's third-party dependencies and downloads. Stage 4's approval named
   `tools/content-model-spike/` and does not carry over. ✅ **Answered 2026-11-17: approved as
   proposed** — `tools/face-model-spike/`, its own Android harness, weights and downloads
   gitignored, its dependencies chosen per A1 and A2. No Python unless needed; if it is, a virtual
   environment, also gitignored.
7. **A7 — The privacy of the spike itself.** Decision 9 says face data never leaves the device,
   never enters a backup and never appears in a log. Stage 4's spike pulled a 300-photo sample of
   private photos to the desktop. For faces:
   - **Either the maintainer explicitly consents** to photos, face crops or embeddings leaving the
     phone, for the spike only, with a stated deletion; **or the spike runs entirely on the phone**
     and only aggregate numbers leave it. Which? ✅ **Answered 2026-11-17: the maintainer
     consents** to photos, face crops and embeddings leaving the phone **to the desktop, for the
     spike only**, with everything deleted at the end and the deletion recorded.
   - **Ground truth:** who labels which faces are the same person, and where those labels live? ✅
     **Answered 2026-11-17: the maintainer labels faces on the desktop**; the labels are deleted at
     the end.
   - **Quality judging:** how the maintainer looks at clusters without crops leaving the phone, if
     that is the answer above. **Moot:** crops may leave the phone, so judging happens on the
     desktop. Not asked separately.
   - **Logs:** the harness's output, logcat included, carries aggregates only. ✅ **Answered
     2026-11-17: unchanged.**
   - **Deletion:** every face crop, embedding and label is deleted at the end, wherever it went, and
     the deletion is recorded. ✅ **Answered 2026-11-17:** so is every photo copied to the desktop.
   - **Other people's faces:** the library holds people other than the maintainer. Whether that
     changes any answer above is the maintainer's call. ✅ **Answered 2026-11-17: same rules;
     fine.**
   - **Git:** ✅ **Answered 2026-11-17: nothing private enters git.** Crops, embeddings, labels and
     photos all stay under gitignored paths.
8. **A8 — Which photos?** ~~The SM-G990B's library has 408 images (the maintainer's count,
   2026-11-17).~~ Does it hold enough faces of a few people to judge clustering, or are more loaded
   first? ✅ **Answered 2026-11-17: more were loaded** onto the SM-G990B for the spike, the same
   day. The count is measured by the spike, not stated here. **Outcome, 2026-11-18:** 1092 images,
   of which the spike used the **1002 still images**; the 90 GIFs were excluded, as the maintainer
   confirmed on 2026-11-17.

### B. Clustering — block 3.4

The spike's inputs to both are under *Spike outcome*, clustering and anchor stability.

1. **B1 — Clustering algorithm and threshold.** ✅ **Answered 2026-11-18, by delegation:**
   average-linkage agglomerative clustering on cosine distance, threshold 0.50, over SFace fp32
   embeddings — [albums.md](albums.md) §8 decision 45. Its memory and time in the app are 3.4's to
   measure.
2. **B2 — Incremental clustering** as new photos arrive. **Still open, for 3.4.** The spike found
   the order-dependent incremental method much less stable than average linkage, so 3.4 must not
   assume it (decision 45).

### C. People — answered 2026-11-17, except one detail

Decided 2026-11-17 by the maintainer, in [albums.md](albums.md) §8 decisions 40 and 41: unlabelled
thumbnails chosen by the app, no person names, the two-images threshold, ~~merging two thumbnails,~~ and
anchoring a person to a photo. What follows is what those leave open. Except C1a, each question was
raised by the Planner on 2026-11-17; ~~none has an answer~~. **Answered later the same day by the
maintainer**, in decision 9's clarification, decisions 40 and 41 amended, and decision 42. ~~One
detail stays open, at the end of C2.~~ Its one open detail was settled on 2026-11-18, by
delegation, in decision 46.

1. **C1 — Correcting the grouping.** ~~Merging is decided (decision 40).~~ **The merge was withdrawn
   the same day** (decision 40, amended): the maintainer had misunderstood the question, and accepts
   that grouping can fail.
   - **C1a — Can the user split a thumbnail that mixes two people?** Asked and not answered on
     2026-11-17. ✅ **Answered later that day: no.**
   - **C1b — Can a merge be undone?** ✅ **Answered: no** — there is no merge.
   - **C1c — Where does a merge live?** It is user intent. If it lives in the index it is lost on a
     restore or a full re-index, with the rest of the index. Do the rule's anchors carry it — for
     example one anchor per merged thumbnail (C2b)? **Moot:** there is no merge.
2. **C2 — The anchor's open details** (decision 41). Block step 3.1's first ADR.
   - **C2a — Is a face's position in a photo "face data" under decision 9?** If it is, decision 9
     keeps it out of backups, and the album database is backed up. This is the reconciliation the
     ADR owes; neither decision 41 nor this plan decides it. The tension was put to the maintainer
     when he chose. ✅ **Answered 2026-11-17 by the maintainer: no.** Decision 9 is clarified: face
     data is embeddings, crops and groups; a face's position, stored as an anchor, is not.
   - **C2b — One anchor per person, or several?** ~~For example one per merged thumbnail.~~ ✅
     **Answered: one per person** (decision 41, amended).
   - **C2c — What happens when the anchor photo is deleted?** ✅ **Answered:** the album is
     unevaluatable — never widened, never deleted automatically — until the user picks the person
     again through rule editing, or deletes it. Automatic re-anchoring was rejected for now: it
     works only for deletions made in the app, and it rewrites rules (decision 42).
   - **C2d — What happens when, after a re-index, the anchor face lands in a different group, or in
     no group?** ✅ **Answered:** a different group, or one seen in only one image, still resolves
     — the album follows it, mistakes included. An anchor photo not yet face-indexed leaves the
     album waiting on the index. No face at the anchored position makes it unevaluatable, as in
     C2c (decision 42). A face in no group is the Planner's reading, to confirm at 3.1: a group of
     its own.
   - ~~**Still open — the minimum overlap.**~~ **The minimum overlap.** The face at the anchored
     position is the detected face whose box overlaps the stored position best, above a minimum
     overlap (decision 42). Step 3.0 measures how far the box moves between runs; step 3.1 sets the
     minimum. **Measured 2026-11-18:** see *Spike outcome*, anchor stability. ✅ **Answered
     2026-11-18, by delegation:** IoU ≥ 0.5 — decision 46.
3. **C3 — Does "two different images" count videos?** It depends on whether Stage 3 indexes faces
   in videos at all, which is not decided. ✅ **Answered 2026-11-17: no.** Videos are not considered
   for people albums; face indexing covers images only (decision 40, amended).

### D. Screen and navigation — block 3.6; raised by the Planner

1. **D1 — Layout of the People criterion** on the New automatic album screen; decision 33 left it
   to the ML stages' plans. Designer.
2. **D2 — ADR-0013's revisit:** does the people flow need a real back stack, as that ADR expects at
   Stage 3?

   **Recommendation, 2026-11-18 — the Planner's, ~~awaiting the maintainer~~: no; the chain stays, and
   ADR-0013 is neither superseded nor amended.** ✅ **Answered 2026-11-19 by the maintainer: as
   recommended** (E11; decision 47). What follows is the reasoning he accepted.
   - **What ADR-0013 expected.** Its revisit fires "when the people-album flow needs a real stack",
     and it pictured that flow as Albums → Album → person picker → person detail.
   - **What was decided instead.** People is a criterion on the existing New automatic album screen,
     beside Place and Date (decision 33). There are no person names and so no person page (decision
     40), and no merge or split surface (decision 40, amended). Nothing in the decided flow goes
     deeper than the screen the user is already on, and that screen already steps out of its own
     drill levels with its back button.
   - **So the condition does not hold**, and the instructions require no record in ADR-0013 when it
     does not: that record is superseded when the condition holds, not amended to say it did not.
     The revisit is recorded here instead.
   - **What would reopen it**, each to be checked when it is decided:
     1. **D1** puts the People picker on a screen of its own, reached from the New automatic album
        screen and returning to it with the draft intact — a second level of navigation the chain
        does not model;
     2. **rule editing's D1** chooses an edit screen of its own that hosts the People criterion;
     3. any later person surface — a person's photos, naming — none of which is planned.
   - **This does not decide D1**, which stays the Designer's. It only says what D1's answer costs:
     a People criterion inside the screen costs no navigation change; a screen of its own reopens
     D2.

### E. Questions from the step 3.1 drafts — raised 2026-11-18, answered 2026-11-19

Each question is written out, with its recommendation, in the record that raises it; this list only
points there. **All eleven were answered by the maintainer on 2026-11-19, as recommended** — in his
words, "go with the recommendations" ([albums.md](albums.md) §8 decision 47). Each record marks its
own questions answered and its text settled. **Answering them did not approve the drafts**, which
~~stay Proposed until he does~~ stayed Proposed until he approved all five separately, on
2026-11-19.

1. **E1 — Write a healed anchor URI back into the rule?** Recommended: no.
   [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md), question 1.
   ✅ **Answered 2026-11-19: no** — heal at read time only.
2. **E2 — GIFs out of the Tier C pass?** Recommended: yes, images only.
   [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5,
   question 1. ✅ **Answered 2026-11-19: yes** — images only, GIFs skipped.
3. **E3 — How is the OpenCV build produced?** Recommended: OpenCV's published artifact, untrimmed,
   other ABIs left out by packaging.
   [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md),
   question 1. ✅ **Answered 2026-11-19: the published artifact, untrimmed**, arm64-v8a only by
   packaging.
4. **E4 — How do the model weights enter the repository?** Recommended: committed, with checksums.
   ADR-0023, question 2. ✅ **Answered 2026-11-19: committed, with checksums.**
5. **E5 — Licence notices: a file only, or a row in Settings too?** Recommended: a row. ADR-0023,
   question 3. ✅ **Answered 2026-11-19: a row in Settings too.**
6. **E6 — On an incapable device, no Tier C pass and no disclosure?** Recommended: yes. ADR-0023,
   question 4. ✅ **Answered 2026-11-19: yes** — neither.
7. **E7 — The disclosure once per installation, or once per person?** Recommended: once per
   installation. [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md),
   question 1. ✅ **Answered 2026-11-19: once per installation.**
8. **E8 — Confirm before deleting face data?** Recommended: yes. ADR-0024, question 2. ✅
   **Answered 2026-11-19: yes.**
9. **E9 — People criterion hidden while face data is off?** Recommended: hidden. ADR-0024,
   question 3. ✅ **Answered 2026-11-19: hidden.**
10. **E10 — Disclosure again on switching face data back on?** Recommended: no. ADR-0024,
    question 4. ✅ **Answered 2026-11-19: no.**
11. **E11 — D2:** ~~keep the chain, as recommended above?~~ ✅ **Answered 2026-11-19: the chain
    stays**, as recommended under D2; ADR-0013 is neither superseded nor amended.

### F. Found while building — raised 2026-11-19

1. **F1 — Do the rules files need a fourth section?** Raised by the Planner at step 3.2; an open
   item, **not a decision**, for 3.3 to settle before the Tier C pass ships. Android 16 QPR2 (API
   36.1) added `<cross-platform-transfer platform="ios">` to `data_extraction_rules.xml`, and the
   app's file has no such section. Android's Auto Backup page, read 2026-11-19, says that when a
   mode has no rules — its example is a missing `<device-transfer>` — that mode is fully enabled
   for all content but the no-backup and cache directories. It also says the section requires a
   `<platform-specific-params>` naming the iOS app, which this app does not have. **Not
   established:** whether a transfer to iOS happens at all for an app with no such section and no
   iOS counterpart, and so whether `media_index.db` and its sidecars could leave the device that
   way. [ADR-0014](../architecture/decisions/0014-album-database-included-in-auto-backup.md),
   [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) and
   [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §6 all
   say "three places"; that claim is to be checked, and any rule change recorded in ADR-0018 and
   applied with `<exclude>` only (ADR-0014). Owner: the Planner, with the decision 9 exit
   criterion "never enters a backup" depending on the answer.

---

## Related

- [albums.md](albums.md): §3 membership, §4 *People album*, §6 the index, §7 progress, §8 decisions
  9, 13 as amended, 14, 15, 33, 35, 40, 41, 42, 43, 44, 45, 46 and 47, §10 questions 3 and 12.
- [albums-stage-4.md](albums-stage-4.md): paused; the precedent for a spike.
- [albums-rule-editing.md](albums-rule-editing.md): rule editing's own plan; its D6 is the order
  between the two.
- [albums-stage-2.md](albums-stage-2.md): archived; its structure is the template.
