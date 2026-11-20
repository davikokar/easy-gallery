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
> the answers to group E), and 48 (2026-11-20, two findings of step 3.2's review), and 49 (2026-11-20,
> ADR-0018 Amendment 7 approved and OpenCV pinned at 4.13.0). The steps, the
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
- **2026-11-20, later still — step 3.3 refined by the Planner; its pending issues settled.** The
  brief is *3.3 — Face detection and embedding* below. The review's URI-spelling concern is not a
  defect: every production writer stores the canonical spelling, checked in the code. The
  capability record and the disclosure flag go in a new preferences file, `face_device_state`,
  excluded from backup; the setting in `app_settings`. Attempts are counted in a third Tier C table,
  three at most, through `Migration(5, 6)`, and F1 is settled conservatively with a fourth rules
  section — both [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md)
  Amendment 7, with dated notes in ADR-0014, ADR-0023 and ADR-0024. The deletion's caller contract
  is split between 3.3 and 3.6. New question F2, the OpenCV pin, for the maintainer. No Gradle run
  by the Planner. *Corrected by the entry below: Amendment 7 and the three notes were cited here but
  not written.*
- **2026-11-20, later again — ADR-0018 Amendment 7 written, Proposed; four defects in this plan
  fixed.** An audit found the refinement above citing an amendment that did not exist. The Planner
  wrote it — the attempt table and `Migration(5, 6)`, F1's fourth section, `face_device_state`, the
  setting read inside the write, the start-up deletion's caller contract, and the URI-spelling
  decision, each with its reason — and dated notes in ADR-0014 (the fourth place, the second excluded
  preferences file), ADR-0023 §2 (F2, open) and §3 (the capability record), and ADR-0024 §2 (the
  disclosure flag's home) and §6 (four places), all marked proposed. Writing it made two points of
  the brief precise, and *3.3* now matches: the bound means at most three decodes, and which stops
  clear an attempt. Also fixed here: a broken sentence in the handoff, F1 still listed as open, the
  Data Safety exit criterion reading as met, and Task 1's brief checked against the code again (the
  Android-free pass and `ImageDecoder`, the order before the permission check, the live snapshot,
  the images-only query, the worker's generic error message). The README index is unchanged: an
  amendment does not enter it. No Gradle run by the Planner. *Adopted 2026-11-20 — the entry below.*
- **2026-11-20, last — ADR-0018 Amendment 7 adopted; F2 answered: OpenCV 4.13.0.** The maintainer
  approved Amendment 7 together with its dated notes in ADR-0014, ADR-0023 §3 and ADR-0024 §2 and
  §6, and pinned OpenCV at 4.13.0, the build the spike measured — [albums.md](albums.md) §8
  decision 49. The Planner adopted them: the marker under ADR-0018's title removed; Amendment 7's
  *Proposed* note replaced by an adoption line, its *Written late* admission kept as history; the
  notes marked adopted; ADR-0023 §2's F2 note answered. **The pin confirms the version only:**
  ADR-0023's verify item 4 still runs when the dependency lands, and any new permission, feature,
  `<queries>` entry or component goes to the maintainer before it ships (decision 24's precedent).
  Still open and blocking nothing: F1's residual, and ADR-0023's verify items at their steps. The
  README index is unchanged, an amendment not entering it; `ARCHITECTURE.md` is unchanged, the
  amendment being adopted ahead of the work it governs — its links are task P's. No Gradle run by
  the Planner.
- **2026-11-20, later — step 3.3 Tasks 1 and 2 built and verified; Task 3 read; Task 4 briefed.**
  The verification phase over Tasks 1 and 2 integrated passed: unit suite 1072 tests *(the figure
  at that gate; the final gate, after Task 4, is 73 files, 1077 tests, 0 failures, and
  `connectedDebugAndroidTest` 264 passed, 1 skipped, 0 failures — the entry below)*;
  `connectedDebugAndroidTest` on the SM-G990B, 264 passed and 1 skipped (the opt-in measurement).
  Task 3's licence reading is *OpenCV 4.13.0 bundled components* under *Licence findings*, with
  ADR-0023's verify row 8 and §6 settled; Task 4's brief is under *3.3*, *Tasks*. Two plan texts
  were amended in *Decided at 3.3 — the pass*: the order in `doWork`, and the `SOURCE_EXCEPTION`
  skip. **Facts task P must record**, each where its list below names:
  - (a) **Verify item 4: the merged manifests are unchanged** against the build before the
    dependency — debug md5 `8856dbd4b4257bd665c4593a325ba624`, release
    `043a521e4d0a25724364c8b8b6ea6f8d`. Nothing went to the maintainer, nothing being added.
  - (b) **Items 1, 2 and 5:** the release bundle's arm64 total is 49,613,983 B, +46,063,982 B
    against the 50 MB budget; OpenCV's library is in `lib/arm64-v8a` only and the other three ABIs
    still install, carrying the +36.1 MB of models in the base; 16 KB alignment passes.
  - (c) **The release build:** R8 removed `org.opencv.core.CvException`, which native code looks up
    by name; `app/proguard-face.pro` keeps it. The release build, installed through bundletool on
    the SM-G990B, logs `face capability checked capable=true`.
  - (d) **ADR-0022's upright frame holds on the device:** EXIF orientations 3, 6 and 8, four device
    tests.
  - (e) **Item 6 was measured in-process, not through the worker,** by
    `FaceIndexPassMeasurementDeviceTest` on a throwaway in-memory index, screen off, USB charging,
    thermal status 1 before and after. The worker's pass cannot run until 3.6 builds the disclosure
    (*Dormant until 3.6*), which is why. 1133 live items; 1002 images with a state (27 + 46 + 929),
    0 failed; 422 faces; mean 150.7 ms per image in the remainder run; peak RSS 619,804 kB, an upper
    bound for the test process, which loads the pipeline three times. The second budgeted run took
    only what the first left. The 90 GIFs are skipped and stay pending on every pass, by design (E2);
    3.5 handles pending at evaluation. At that rate the production 7-minute budget fits about 2,800
    images per run.
  - (f) **An OpenCV AAR anomaly:** Gradle's `.module` gives the AAR as 3,570,283 B; the real AAR is
    121,695,690 B. Maven Central's own `.module` carries the same stale figures, and its `.sha1` and
    `.sha256` match the cached AAR (sha256
    `a8c9b0929d8d6367bb476320a1f36587db2b57ea18d8f7521ce509514903ca04`). Recommended: no
    `verification-metadata.xml`, a project-wide mechanism out of proportion to one stale field.
  - (g) **Task 2's review, judgement calls to record or route:** the self-test's detector stage now
    runs at threshold 0 and requires a non-empty 15-column table (fixed); `SOURCE_EXCEPTION` is
    rethrown as `IOException` (recorded in *Decided at 3.3 — the pass*); native OpenCV errors may
    print under the logcat tag `cv::error()` — code-level text only, and `Core.setErrorVerbosity(false)`
    the likely switch, open for 3.7's logger check; the `libc++_shared.so` exclude applies app-wide;
    the checksum task is wired by task-name pattern, at risk if AGP renames its merge tasks; the
    capture of `Core.getBuildInformation()`'s third-party section was `/tmp/buildinfo.txt`, Task 3's
    input, not committed. Task 1's review P3: the Tier C lock is now taken before the capability
    check (*Order in `doWork`*, amended).
- **2026-11-20, last of the day — step 3.3 built and verified: Tasks 1, 2, 3, 4 and P done.** Task 4
  landed `assets/face/THIRD_PARTY_NOTICES.txt`, `NOTICE.txt`'s one line and `ThirdPartyNoticesTest`.
  **The final gate:** unit suite, 73 files, 1077 tests, 0 failures; `connectedDebugAndroidTest` on
  the SM-G990B, 264 passed and 1 skipped (the opt-in measurement), 0 failures; the merged manifests
  unchanged. Task P landed `ARCHITECTURE.md` §3, §6, §10 and §11, `AGENTS.md`, the dated notes in
  ADR-0018 Amendment 7, ADR-0022 and ADR-0023, and the test conventions; then this plan's step row,
  this handoff, *3.3 — Built and verified 2026-11-20* and [albums.md](albums.md) §7's Stage 3 row.
  Stage 3 stays In progress. No Gradle run by the Planner.
- **2026-11-20, later still — the maintainer answered 3.4's question; step 3.4 refined by the
  Planner.** His answer, verbatim: "I don't see the risk. The app is not getting installed on any
  device apart from the test device connected to the dev environment." The only install is the dev
  phone, the SM-G990B, so the gap before 3.6's disclosure exposes no user: **the opt-in dev-device
  route is accepted for 3.4's measurements and tests**, and is neither a blocker nor a risk. It is
  recorded, with the one design constraint it carries, in *3.4 — Clustering and the person record*,
  *The dev-device route — answered 2026-11-20*, which is also the brief: measurement first, **no
  schema change** (the index stays at version 6; no ADR-0018 Amendment 8), a pure grouping class,
  grouping in the worker under the Tier C lock, the person record computed on read. B2 is settled
  there, subject to the budget. One correction found on the way: 3.3's measurement did not set the
  disclosure flag, as the question below says — it ran the pass outside the worker. No Gradle run by
  the Planner.
- **Next action: step 3.4, Task 1 — Coder, with the SM-G990B attached:** the pure grouping class,
  its plain-JVM test and the opt-in measurement — *3.4*, *Tasks*, Task 1 is the brief. **It waits on
  nothing.** Then **the maintainer accepts the budget** proposed in *3.4*, *The budget* — now, or
  once Task 1's figures are in; Task 2 waits on it. If the figures fail it, he chooses among the
  options listed there, which is a new decision, and nothing after Task 1 is built until he has.
  Then Tasks 2 and 3 (store, grouping pass, worker), Task 4 (the worker measurement on the phone),
  the verification phase once, and task P.
- ~~**Next action: step 3.4 — clustering and the person record.** Owner: the Planner refines it into
  a step plan, as 3.2 and 3.3 were; then the Coder builds it.~~ Refined 2026-11-20 — the two entries
  above. It starts from what 3.3 landed: Tier C
  faces, their per-image states and the attempt table in the index database at version 6
  ([ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendments 5 to
  7, Amendment 5 carrying the grouping version); the anchor of
  [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md); and
  [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) §4
  (grouping in the worker or as its own step, resumable, never twice at once) and §5 (the memory
  requirement). 3.4's own text, under *3.2 to 3.8*, says only: "Per B1 (decision 45: average
  linkage, cosine 0.50) and B2, and decisions 42 and 46 for an anchor. Average linkage's memory and
  time in the app are unmeasured — about 800 MB for the distance matrix at 10,000 faces — so 3.4
  measures them before relying on them; a different algorithm is a new decision, not a detail."
  - **The memory budget is measured first**, before anything is built on average linkage: ADR-0023
    §5 asks for peak memory and time on the device at the largest real library and at a synthetic
    set of at least 10,000 faces, against a budget 3.4 proposes and the maintainer accepts, the
    models' own footprint counted. If average linkage cannot meet it, replacing it is a new
    decision in [albums.md](albums.md) §8 and an amendment to ADR-0023.
  - **B2, incremental clustering, is open for 3.4** (*Open questions* B2): the spike found the
    order-dependent method much less stable than average linkage, so 3.4 must not assume it.
  - ~~**Question for the maintainer, for 3.4's plan — not settled by the text above.**~~ ✅
    **Answered 2026-11-20 by the maintainer: the opt-in dev-device route is accepted** — the entry
    above, and *3.4*, *The dev-device route*. The face pass
    cannot run in the worker until 3.6 records the disclosure as shown (*Dormant until 3.6*), so no
    installed app holds a single face before 3.6, and ADR-0023 §5's "in the background" cannot be
    met through the worker before then. 3.3's precedent is an opt-in device test that sets the flag
    through the store on a throwaway in-memory index; 3.4's refinement proposes the same or another
    route for exercising and measuring clustering, and the maintainer confirms it. *Corrected
    2026-11-20:* 3.3's test sets no flag; it runs `FaceIndexPass` directly, outside the worker, with
    an in-memory setting store and index, so the disclosure gate is never consulted.
  - **Read first, on starting 3.4:** *3.3 — Built and verified 2026-11-20* below; ADR-0023 §4 and
    §5; ADR-0022; ADR-0018 Amendments 5 to 7; [albums.md](albums.md) §8 decisions 42, 45 and 46;
    *Spike outcome*'s clustering and anchor-stability findings; *Open questions* B.
- ~~**Next action: step 3.3, Task 4 — Coder**, text and one test: *3.3*, *Tasks*, Task 4 is the brief,
  with its scope and verification (unit suite and `assembleDebug`, no device).~~ Done 2026-11-20 —
  the entry above.
  - ~~**Then task P — Planner:** `ARCHITECTURE.md` §3, §6, §10 and §11; `AGENTS.md` *Tech stack*,
    *Preference stores*, *The two databases* and the call-outs (version 6, `face_device_state`,
    `proguard-face.pro`); ADR-0018 Amendment 7's *Built* note; ADR-0023's verify table with the
    measurements above; the test conventions, for the opt-in device test; this plan — step row,
    *Licence findings* if Task 4 changes anything, the handoff — and [albums.md](albums.md) §7. The
    facts are the entry above; the full list is *After it lands — Planner, task P*.~~ Done
    2026-11-20 — the entry above.
- ~~**Next action: step 3.3, Task 1 — Coder**~~ Done 2026-11-20 — the entry above; Tasks 1 to 3
  likewise. ~~, from *3.3 — Face detection and embedding*, *Tasks*,
  which is the brief.~~ **It waits on nothing:** Amendment 7 is adopted, and Task 1 needs no
  dependency and no device. The concurrent test-only fixes to `MediaIndexTierCStoreTest.kt` and
  `MediaIndexFaceDataScrubTest.kt` are done — verified 2026-11-20, 195 `MediaIndex*` tests passing.
  - **Call-outs, not approvals** (`AGENTS.md`), which the Coder names in Task 1's report: the index
    reaches **version 6 through `Migration(5, 6)`**, with `6.json` generated and committed; and a
    **new preferences file, `face_device_state`**, with both a `SharedPreferences*` and an
    `InMemory*` store. Its row in `AGENTS.md` *Preference stores* is task P's, `AGENTS.md` being
    routed. The fourth backup-rules section lands with them.
  - **Then Task 2 — Coder, with the SM-G990B attached** and `adb devices` stated: OpenCV 4.13.0,
    the models, the real pipeline, and its device and bundle checks. It **may stop at the manifest
    gate** — verify item 4 runs first, and anything the merged manifest adds goes to the maintainer
    before the work continues.
  - **Then one verification phase**, over Tasks 1 and 2 integrated, owned by whoever the brief
    names; then Tasks 3 and 4; then the Planner's task P.
- ~~**Next action: the maintainer approves ADR-0018 Amendment 7; then step 3.3, Task 1.**~~ Done
  2026-11-20 — the entry above. ~~Amendment 7
  is **Proposed**. Task 1 needs neither F2 nor a device, but it builds Amendment 7 — `Migration(5, 6)`
  to version 6 with `6.json`, the new preferences file `face_device_state`, the fourth backup
  section. **Recommended: Task 1 starts only after the approval**, because a migration and a
  preferences file, once they have run on a device, are undone only by another change. Starting
  earlier is the maintainer's call; the Coder must then be told the amendment may still change.~~
  The approval came before Task 1 started, so the recommended wait cost nothing.
  - ~~**What the maintainer is asked, and nothing else:** (a) approve ADR-0018 Amendment 7, which
    carries the notes in ADR-0014, ADR-0023 and ADR-0024, or say what to change; (b) F2, the OpenCV
    version — 4.13.0 recommended — before Task 2. F1's residual is his too, but blocks nothing and
    needs no answer now.~~ Both answered 2026-11-20.
  - ~~**On approval, the Planner:** replaces Amendment 7's *Proposed* note with an approval line,
    removes the marker under ADR-0018's title, turns the four notes' "proposed" into "approved", and
    records the approval in [albums.md](albums.md) §8, as decision 47 recorded step 3.1's.~~ Done
    2026-11-20, as decision 49.
  - ~~Then Tasks 2 to 4, the verification phase once, and the Planner's task P.~~ Reordered in the
    *Next action* above: verification after Task 2.
- **Read first, on starting 3.3 (revised 2026-11-20):** *3.3 — Face detection and embedding*
  below, which is the brief; ADR-0018 Amendments 5 to 7, ~~the last proposed, with the proposed notes~~
  all adopted, the last on 2026-11-20, with the notes
  it carries in ADR-0014, ADR-0023 §2 and §3 and ADR-0024 §2 and §6; ADR-0023 §1, §3, §4, §6 and its verify
  table; ADR-0024 §1 to §3 and §6; ADR-0022 §1; `AGENTS.md` *Preference stores*; the code as it
  stands in `data/MediaIndexStore.kt`, `data/MediaIndexingWork.kt`,
  `data/MediaIndexPlaceResolution.kt` — the paging precedent — and `EasyGalleryApp.kt`; and repo
  memory's Room, migration, scrub-test and worker traps.
- ~~**Next action: step 3.3 — face detection and embedding in the indexing pipeline, behind the
  disclosure.** Owner: the Planner refines it into a step plan, as 3.2 was, then the Coder builds
  it.~~ Refined 2026-11-20 — the entry above; every point below is answered in *3.3*, *Settled
  before 3.3*. It starts from what 3.2 and its follow-up landed: the Tier C tables at index version 5, and their rules on
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
    `people` clause where people is unavailable, and its D6, the order. ~~And **F1**, new — whether
    the rules files need a fourth section — to be settled before Tier C ships.~~ **F1 decided
    2026-11-20 at 3.3** — a fourth section, with the same exclusions (*3.3*, *Settled before 3.3*,
    issue 3; ADR-0018 Amendment 7 §2, ~~proposed~~ adopted 2026-11-20). Only its residual stays open: the maintainer's
    check of how an API 36.1 device reads the section, which blocks nothing.
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
- ~~**Read first, on starting 3.3:**~~ Superseded 2026-11-20 by the revised list above; it read:
  this block; ADR-0023, above all §3 and §4 and its verify items;
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
| **3.3** | Face detection and embedding in the indexing pipeline, behind the disclosure | ~~⬜ Not started — refined 2026-11-20 into *3.3 — Face detection and embedding*;~~ ~~Task 1 waits on the approval of ADR-0018 Amendment 7, Task 2 on F2 as well~~ ~~ADR-0018 Amendment 7 adopted and F2 answered (OpenCV 4.13.0) 2026-11-20, decision 49; Task 1 ready to build~~ ✅ Built and verified 2026-11-20 — refined, its storage adopted as ADR-0018 Amendment 7 and OpenCV pinned at 4.13.0 the same day (decision 49); Tasks 1 to 4 and task P done; the gate and the device results are under *3.3 — Built and verified 2026-11-20* |
| **3.4** | Clustering and the person record | ⬜ Not started — refined 2026-11-20 into *3.4 — Clustering and the person record*, ready to build: no schema change, so no amendment to approve; Task 1 waits on nothing, Task 2 on the maintainer's acceptance of the budget |
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

### 3.3 — Face detection and embedding — refined 2026-11-20

The Coder's brief. What the pass must do is
[ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) §1, §3, §4
and §6, [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §1–§3
and §6, [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendments
5–7 and [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) §1, and is
not restated. ~~**Amendment 7 is proposed** — written 2026-11-20, after this section first cited it —
and binds once the maintainer approves it.~~ **Amendment 7 is adopted** — written 2026-11-20, after
this section first cited it, and approved by the maintainer the same day (decision 49). What they left to 3.3 is marked **Decided at 3.3**, with its reason; each is the
Planner's and reversible by the maintainer. Planning only: no code and no Gradle run by the Planner.

#### Settled before 3.3 — the pending issues

1. **URI spellings — not a defect today; nothing to build.** The review's concern was that Tier C
   keys and `keepsTierC` match URIs exactly while Tier A might store a per-volume spelling, leaving
   two index rows — and so two face results — for one image. **Checked in the code 2026-11-20:**
   every production writer stores the `content://media/external/…` spelling. Tier A writes the
   URI `AndroidMediaStoreRowDeltaProvider` hands it, which canonicalises at the source (pinned by
   `api 30 plus canonicalizes uuid volume rows to external namespace`), and has since the commit
   that introduced Tier A (`2bcb489`), before any worker ran it (`954973f`); a heal writes the live
   item's URI, and `MediaStoreDataSource` queries `EXTERNAL_CONTENT_URI` only. So no install holds
   two spellings of one image. The pass below reads its work from index rows and writes under the
   row's own identity, never a live or observed spelling, so it can duplicate nothing the index
   does not. *What would make it real:* a new writer storing another spelling. The invariant is the
   provider's, not the store's — tests seed per-volume spellings on purpose, so enforcing it in
   `upsert` would break them for no production case. If it ever becomes real, the repair is
   Amendment 3's kind: a data migration to the canonical spelling.
2. **Where the capability result and the disclosure flag live — Decided at 3.3: a new
   preferences file, `face_device_state`, excluded from backup.** The face-data setting stays
   ADR-0024 §1's scalar in `app_settings`: key `face_data_enabled`, default on, backed up.
   - ADR-0018 Amendment 2's two tests, in order. *Structured?* No: a boolean, and a key string, an
     outcome and a start count. *Does the index's correctness depend on it?* No: a lost flag shows
     the disclosure again, a lost record re-runs the check. So a preferences file, not the index
     database — which would also need a migration for three scalars and, inside Tier C, would be
     emptied by the switch and bring the disclosure back, against E10.
   - Not `app_settings`: it is backed up, and both are per installation — the record per device.
     Not `media_index_run_state`: one store per file, and that file reports on a job.
   - **The cost is an `AGENTS.md` call-out, not an approval:** a row in *Preference stores*, a
     `SharedPreferences*` and an `InMemory*` store, and `face_device_state.xml` excluded in
     `backup_rules.xml` and in every section of `data_extraction_rules.xml` (issue 3).
   - **The capability record — Decided at 3.3.** Persisted, because 3.6 needs the answer at app
     open, where loading 39 MB of weights each time is a cost and a native crash would be a crash at
     every open. Keyed by the app's version code, `FACE_PIPELINE_VERSION` and `Build.FINGERPRINT`. A
     start marker is written before the check runs; two starts for one key with no result — the
     check killed the process twice — persist *incapable*. A deterministic failure is persisted —
     the library or a model does not load, the self-test is wrong — and an `OutOfMemoryError` is
     not. The self-test is ADR-0023 §3's synthetic input, checked for shape, finite values and a
     non-zero embedding norm, **with no stored reference**: one phone cannot calibrate a tolerance,
     and a false *incapable* would hide the feature silently; the weights are checksummed by the
     build instead.
3. **F1 — Decided at 3.3, conservatively: a fourth section with the same exclusions.**
   [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 7 §2
   holds the facts read, the decision and its residual; F1 under *Open questions* says what is
   still the maintainer's. It lands in Task 1, so it ships with the change that first writes face
   data.
4. **A deletion that is not scrubbed — the caller contract.** 3.3 builds `FaceDataStartupDeletion`
   and calls it from the worker; 3.6 adds the call at app open with the switch.
   - It runs **first in `doWork`, before Tier A, whenever the setting reads off** — not only when
     `hasFaceData()` is true (ADR-0024 §3 as Amendment 6 annotates it). In today's `doWork` that is
     after `runStateStore.markStarted` and **before `ensureMediaReadPermission`**, which throws when
     media access is revoked and must not stop a deletion that reads no media.
   - `scrubbed = false` is a result: no retry within the run, nothing persisted, nothing shown. The
     next process start repeats it; under `PERSIST` every repeat reports false again, accepted by
     Amendment 6.
   - A thrown error — `secure_delete` not reading back, an `SQLiteException` — is caught there,
     never fails the run or stops Tier A, and is repeated at the next start.
   - It logs at most that it ran and whether it scrubbed: no row count, no exception message.
   - **The Tier C lock — Decided at 3.3:** one process-wide `Mutex` on `EasyGalleryApp`, held by
     the pass for its whole run. The worker skips the pass when it is held, since the periodic job
     and Index now can overlap and two passes would double-count attempts. 3.6's switch takes it
     after persisting *off*, which is ADR-0024 §3 step 2's "stop and wait"; the setting checked
     inside the write transaction stays the backstop.
   - **3.6's:** the app-open call, the confirmation, the order of ADR-0024 §3, dropping held
     state, and whether and how Settings presents *not scrubbed* (Amendment 6 §2).
5. **The verify items, the upright frame and the OpenCV pin** — how each is checked is in Task 2
   below. The library is approved (decisions 45 and 47); ~~no record names a version. **4.13.0 is
   recommended — the build the spike measured — and the maintainer confirms it before Task 2**
   (F2).~~ **4.13.0 — the build the spike measured — confirmed by the maintainer 2026-11-20** (F2,
   answered; decision 49). The confirmation is of the version only: verify item 4 still gates the
   dependency in Task 2. Read 2026-11-20 on Maven Central: `org.opencv:opencv` offers 4.9.0 to 4.14.0, 5.0.0,
   5.0.0.1 and 5.1.0; only 4.13.0 was measured, and 5.x is a new major version. The 4.13.0 AAR is
   121.7 MB with all four ABIs; its library manifest in OpenCV's source is empty; it depends on
   `kotlin-stdlib-jdk8` 1.8.20, which `app/build.gradle.kts` already forces to 2.1.0; OpenCV's own
   build marks its `.so` files `doNotStrip`; and its Gradle metadata states a 3,570,283-byte AAR,
   not the file listed — whether Gradle resolves it cleanly is **not verified**. On this machine
   NDK 27.1 is installed, so AGP can strip and `llvm-readelf` can check alignment, and bundletool
   1.18.3 is in the Gradle cache.
6. **Not blocking 3.3:** the Play Data Safety check — the maintainer's, before the first Play
   release that ships the pass, with the Planner preparing its facts at 3.7 and 3.3 supplying the
   merged permission list; D1 — the Designer's, at 3.6; B2 — 3.4's; rule editing's D3 and D6 — its
   own plan; and F1's residual.
7. **Known debt:** Amendment 6's two accepted costs join `ARCHITECTURE.md` §11 with this change,
   the first that writes face data — task P.

#### Decided at 3.3 — the pass

- **Candidates:** index rows in an images collection whose live item is `MediaType.IMAGE`, so a
  video or a GIF never gets a state row (E2). Paged by URI cursor, as the place pass pages: no state
  row, or a state at another pipeline version. A candidate whose live item is gone, or whose live
  healing triple differs from the row's, is skipped this run — reconciliation or Tier A will move
  it first. *Checked against the code 2026-11-20:* the index row has no media-type column, so the
  query selects the images collection by the URI's `/images/media/` path; `MediaRepository` has no
  per-URI lookup, so the live items are one `getAllMedia()` snapshot per run, as Tier A takes; and
  `MediaType` is `IMAGE`, `VIDEO` or `GIF`, the last set from the MIME type, so requiring `IMAGE`
  excludes both.
- **Parameters — the spike's, all inside `FACE_PIPELINE_VERSION = 1`:** decode upright with
  `ImageDecoder` to a long side of at most 1280 px, never upscaled; YuNet on a 640 px long-side
  copy at score 0.7, its NMS threshold and top-K at OpenCV's defaults, named in the constant's
  KDoc; boxes mapped back to the decoded image; a face whose detected width or height there is under
  40 px is dropped (decision 46); SFace `alignCrop` and `feature` on the decoded image; the box
  clamped to the image and stored as fractions (ADR-0022 §1), the embedding as the recogniser
  outputs it. **The OpenCV version is inside the constant too**, because another build moves boxes
  and would mix embeddings (*Spike outcome*, int8 against fp32): a plain-JVM test fails unless
  `gradle/libs.versions.toml`'s `opencv` equals the version the constant records, as the gazetteer's
  version test does.
- **Models from memory.** Read from the APK's assets into buffers. **Never written to `filesDir`**
  or any backed-up directory: 39 MB is over Auto Backup's 25 MB quota, which stops the whole cloud
  backup, albums included (ADR-0014). If 4.13.0's Java API has no buffer overload for
  `FaceDetectorYN` or `FaceRecognizerSF`, copy them to `noBackupFilesDir` instead, and report it.
- **One write per image**, through `writeFaceIndexResults`, which gains the setting as a parameter
  and reads it inside its transaction (ADR-0024 §3): off, nothing is written.
- **Failures — Amendment 5's bound, Decided at 3.3 as three attempts**, counted as Amendment 7 §1
  says: recorded before the decode, so a process death leaves it counted. A readable file that does
  not decode or an OpenCV error on the image is written `FAILED` at once. A file that cannot be
  opened (`FileNotFoundException` or another `IOException`) skips the image; `SecurityException`
  stops the pass; both clear the attempt, as cancellation, the setting and the budget do. Anything
  else propagates, fails the run and leaves the attempt counted. A candidate found with three
  attempts already recorded is written `FAILED` without decoding. *Corrected 2026-11-20:* the pass
  is Android-free, so it never names `ImageDecoder.DecodeException` — which is itself an
  `IOException` and would otherwise read as a skip. The `FacePipeline` reports a decode failure and
  an inference failure as a type of the pass's own: Task 1's fakes throw it, and Task 2's
  `OpenCvFacePipeline` maps `DecodeException` and OpenCV's errors to it before any `IOException`
  is treated as unopenable. *Decided 2026-11-20, Task 2's review — a gap in this brief closed:* a
  `DecodeException` whose cause is `SOURCE_EXCEPTION` is the source failing to read, not the image
  failing to decode, so it is rethrown as an `IOException`: the pass skips the image and clears the
  attempt rather than storing `FAILED`.
- **Order in `doWork`:** the start-up deletion first (issue 4); Tier A; places; then, with the
  setting on: ~~the capability record, or the check now; the disclosure flag; the Tier C lock; the
  pass.~~ the Tier C lock; the capability record, or the check now; the disclosure flag; the pass.
  *Amended 2026-11-20, Task 1's review (P3):* the lock is taken before the capability check, so two
  overlapping runs can never check concurrently. The pass checks cancellation and the setting between images, and **starts no image 7
  minutes after `doWork` began** — Decided at 3.3, against WorkManager's stop at about ten;
  injectable clock. What it processed is no longer pending, so the next run resumes.
- **Reporting.** The run-state store receives nothing from the pass (Amendment 5). The pass logs
  through an injected `FaceIndexLog`, from a fixed set of lines carrying counts of images only. A
  pass-level failure fails the run with a fixed message in the output data and no throwable
  logged: no exception message from the face path leaves it (ADR-0024 §6). *Checked 2026-11-20:*
  `doWork`'s generic catch writes `error.message` to `KEY_ERROR_MESSAGE`, and the place pass logs
  its error's message and throwable, so the pass's failure is caught before that catch, never routed
  through it.
- **Dormant until 3.6.** Nothing in 3.3 sets the disclosure flag, so on an installed app the pass
  never runs before 3.6's disclosure lands — the gate ADR-0024 §2 asks for. Tests and the device
  measurement set the flag through the store.

#### Tasks — four, sequential

**The concurrent test-only fixes are done.** The changes to `MediaIndexTierCStoreTest.kt` and
`MediaIndexFaceDataScrubTest.kt` were completed and verified on 2026-11-20 — 195 `MediaIndex*` tests
passing — so they no longer gate Task 1, which edits both files further. ~~**Task 1 waits only on the
maintainer's approval of ADR-0018 Amendment 7**, which it builds; that wait is recommended, not
forced (the handoff says why).~~ **Amendment 7 was approved on 2026-11-20 (decision 49), so Task 1
waits on nothing.**

**Task 1 — Coder: attempts, the two stores, the pass and its wiring, on a stand-in pipeline.** ✅
**Done 2026-11-20** — *3.3 — Built and verified 2026-11-20* below. No
dependency and no device. Under `app/src/main/java/com/davide/seddio/easygallery/`:
`data/MediaIndexEntities.kt`, `data/MediaIndexDao.kt`, `data/MediaIndexDatabase.kt` (the attempt
table, the pending-images query, `MIGRATION_5_6` with each `createSql` copied from the generated
`6.json`, version 6); `data/MediaIndexStore.kt` (interface and both stores: recording and clearing
an attempt — `clearFaceIndexAttempt` below is a new method, its name the Coder's — the pending-images
read, and `writeFaceIndexResults` gaining the setting); `data/MediaIndexTierC.kt`
(the pending-image model, redacted); new `data/FaceDataSettingStore.kt`, `data/FaceDeviceStateStore.kt`,
`data/FaceIndexPass.kt` — Android-free: the `FacePipeline` interface, `FACE_PIPELINE_VERSION`, the
bound, the budget, the pass, the box geometry, `FaceIndexLog` and its lines,
`FaceDataStartupDeletion` — and `data/FaceCapability.kt`, Android-free: the key, the record, the
checker over a pipeline loader; `data/MediaIndexingWork.kt` (new seams on `MediaIndexingWorker`'s
`@JvmOverloads` constructor, defaulting to `EasyGalleryApp`'s wiring as its three seams do);
`EasyGalleryApp.kt`, whose loader
reports *incapable* until Task 2. `app/src/main/res/xml/backup_rules.xml` and
`data_extraction_rules.xml`. `app/schemas/…MediaIndexDatabase/6.json`, generated and committed.
Tests under `app/src/test/…/data/`: `MediaIndexDatabaseMigrationTest.kt`, `MediaIndexDaoTest.kt`,
`MediaIndexTierCStoreTest.kt`, `MediaIndexFaceDataScrubTest.kt`, `MediaIndexingWorkerTest.kt`, and new
`FaceIndexPassTest.kt` and `FaceCapabilityTest.kt` (plain JVM), `FaceDeviceStateStoreTest.kt` and
`FaceDataSettingStoreTest.kt` (Robolectric, for the `SharedPreferences*` halves), and
`BackupRulesParityTest.kt` (plain JVM, reading the XML as files). Exit criteria, each with a test:
1. 5 → 6 creates `media_index_face_attempt` empty and changes nothing else; the chain runs 1 → 6;
   a migrated file reopens with the production builder; every new test file is named
   `media_index.db` (repo memory).
2. Both stores: an attempt is recorded only while the row has the identity it names, increments,
   restarts at 1 under another pipeline version, and is cleared by a result write and by
   `clearFaceIndexAttempt`; it is dropped, re-keyed and deleted by every Tier C rule, emptied by
   `deleteAllFaceData` and counted by `hasFaceData`.
3. Both stores: `writeFaceIndexResults` with the setting off writes nothing and returns 0.
4. The pending query returns images-collection rows only, by URI path, with no state or a state at
   another version, after the cursor, up to the limit.
5. The pass, on the in-memory store and a fake pipeline: no state for a video or a GIF; gone and
   stale candidates skipped; `FAILED` at once for the two deterministic failures; `FAILED` without
   decoding after three recorded attempts; an unopenable file and a `SecurityException` clear the
   attempt; a cancellation leaves no attempt and no write for its image; the
   setting turning off mid-pass stops it; the budget stops it, and a second run resumes; boxes are
   clamped fractions; faces under 40 px are dropped.
6. **The logger test (ADR-0024 §6):** fixtures carry distinctive URIs, names, boxes and embedding
   values, and a fake pipeline throws messages containing a URI; every emitted line matches the
   fixed set and contains none of them, and neither does the run's output data.
7. The capability checker: reuse for the same key, a re-check for a new one, *incapable* after two
   unfinished starts, a deterministic failure persisted, an `OutOfMemoryError` not.
8. The start-up deletion: runs while off whatever `hasFaceData()` says; *not scrubbed* and a thrown
   error neither throw nor retry; nothing while on.
9. The worker (Robolectric, `work-testing`): off — the deletion before Tier A and before the
   permission check, and no pass; incapable, disclosure not shown, or the lock held — no pass; a
   pass failure — the run fails with the fixed message; the run state's counters are Tier A's alone.
10. The rules: `backup_rules.xml` and each of the three sections exclude the same set — the index
    file and its three sidecars, `media_index_run_state.xml`, `face_device_state.xml` — and there is
    no `<include>` anywhere.
11. The two `SharedPreferences*` stores write `app_settings` and `face_device_state`, default to on,
    not shown and no record, and survive a new instance.

**Task 1's report calls out**, as `AGENTS.md` requires of changes that need no approval but reach
wider than their diff: the index's **version 6 through `Migration(5, 6)`**, with `6.json` committed
under `app/schemas/`; and the **new preferences file `face_device_state`**, with both its stores and
its exclusion in all four places. Its *Preference stores* row in `AGENTS.md` is added at task P.

**Task 2 — Coder: OpenCV, the models and the real pipeline.** ✅ **Done 2026-11-20** — *3.3 — Built
and verified 2026-11-20* below. ~~Needs F2 answered and the SM-G990B.~~
Needs the SM-G990B. F2 is answered (decision 49): Task 2 pins `opencv` at **4.13.0** in
`gradle/libs.versions.toml`. The pin approves the version, not what it merges in — the verify item 4
stop below stands.
`gradle/libs.versions.toml`; `app/build.gradle.kts` — the dependency, `packaging.jniLibs` excludes
for OpenCV's libraries under the three other ABIs (**no `abiFilters`**), and a checksum task run
before the assets merge that fails the build on a mismatch; `app/src/main/assets/face/` — the two
OpenCV Zoo ONNX files as measured, OpenCV's and SFace's Apache-2.0 texts, YuNet's MIT notice, and a
statement that nothing was changed; new `data/OpenCvFacePipeline.kt`; `EasyGalleryApp.kt`; a
keep-rules file only if the release check below needs one. Tests: new plain
`data/FacePipelineVersionTest.kt`, and under `app/src/androidTest/…/data/`
`FaceCapabilityDeviceTest.kt`, `FaceDecodeOrientationDeviceTest.kt` and
`FaceIndexPassMeasurementDeviceTest.kt`, the last opt-in by an instrumentation argument and skipped
otherwise. Checks, each reported with its figure:
- **Verify item 4, before anything else:** the debug and release merged manifests diffed before
  and after the dependency. Any new `<uses-permission>`, `<uses-feature>` — Play filters on it —
  `<queries>`, component or `minSdk` change: **stop and ask the maintainer**. Do not remove it with
  `tools:node="remove"` on your own; decision 24 is the precedent, and he decides.
- **Item 1:** bundletool `build-apks --device-spec` for arm64-v8a, armeabi-v7a, x86 and x86_64,
  against the release bundle before and after: every spec still gets APKs; OpenCV's library is in
  arm64 only.
- **Item 2:** every `.so` in the arm64 split has LOAD segments aligned to at least 16 KB
  (`llvm-readelf -l`) and is 16 KB zip-aligned (`zipalign -c -P 16`). A failure is reported, not
  worked around.
- **Item 5:** `bundletool get-size total` for arm64, before and after: the added download size
  against 50 MB. If it is over, check first that AGP stripped OpenCV's libraries.
- **The release build:** its arm64 APKs installed through bundletool and the capability log line
  read. If debug is capable and release is not, add an OpenCV keep rule through the release
  `optimization` block and check again.
- **ADR-0022's upright frame:** a JPEG written to the test's cache with an asymmetric mark, under
  EXIF orientations 3, 6 and 8, decodes through the production decoder with the mark where the
  upright image has it. With Task 1's plain geometry tests, that puts a stored box in the upright
  frame. On a device, because `ImageDecoder`'s orientation handling is the platform's, not the JVM's.
- **The capability check on a device:** capable on an arm64 device, and the test asserts the
  opposite on one without arm64-v8a, so it is never vacuous. Emulators fail it (ADR-0023).
- **Item 6, measured in the worker on the SM-G990B:** through Index now with the screen off — images
  processed, wall time, mean time per image, the process's peak RSS, the thermal status before and
  after, and whether it was charging. Then a run with a short budget twice: the second processes
  only images the first did not. Aggregates only, nothing per image; the face data it writes stays
  on the phone (decision 9).
- **Item 8's input:** the third-party section of `Core.getBuildInformation()`, read on the device,
  copied into the report.

**Task 3 — Planner: OpenCV's bundled components.** ~~From Task 2's capture, each component's licence
read and recorded under *Licence findings*, with the notice files owed. Verify item 8.~~ ✅ **Done
2026-11-20:** *OpenCV 4.13.0 bundled components — read 2026-11-20* under *Licence findings*, sixteen
rows verified, none blocks shipping; its *What is owed, by group* is Task 4's input. ADR-0023's verify
table row 8 and §6 are settled to match.

**Task 4 — Coder: ~~those notice files~~ one notice file and its test** — ✅ **Done 2026-11-20**:
`THIRD_PARTY_NOTICES.txt`, `NOTICE.txt`'s line and `ThirdPartyNoticesTest`, inside the final gate of
*3.3 — Built and verified 2026-11-20* below. ~~under
`app/src/main/assets/face/`. Text only.~~ *Briefed 2026-11-20 from Task 3's subsection, its* What is
owed, by group *the input.*

- **Scope, and nothing else:** create `app/src/main/assets/face/THIRD_PARTY_NOTICES.txt`; add one
  line to `app/src/main/assets/face/NOTICE.txt`; create
  `app/src/test/java/com/davide/seddio/easygallery/data/ThirdPartyNoticesTest.kt`. No Kotlin under
  `src/main`, no Gradle file, no string, no change to `SHA256SUMS`.
- **One combined file, not one per component.** Step 3.6's Settings row (E5) needs a fixed, small
  set of asset names to open; sixteen files would be a list that changes whenever an OpenCV upgrade
  changes what it bundles. With one file, an upgrade changes the file's content and never its name.
  The licence texts already shipped for OpenCV and the models stay where they are.
- **The header** names the file's scope — the third-party components bundled in OpenCV 4.13.0's
  published Android library (`org.opencv:opencv`), as `Core.getBuildInformation()` prints them, and
  the C++ runtime shipped beside it; OpenCV itself and the two models are in `NOTICE.txt` — then
  lists every component with its version, one per line, as the subsection's table prints them:
  libcpufeatures (AOSP, 2016), libprotobuf 3.19.1, ADE 0.1.2e, oneTBB 2022.1.0, ittnotify 3.25.4,
  libjpeg-turbo 3.1.2-70, libwebp (decoder 0x0210), libpng 1.6.53, libtiff 4.7.1, OpenJPEG 2.5.3,
  OpenEXR (IlmImf) 2.3.0, Carotene (tegra_hal) 0.0.1, KleidiCV 0.7.0, Flatbuffers 25.9.23, LLVM
  libc++ (`libc++_shared.so`, NDK 27.2.12479018), and zlib 1.3.0.1 marked *not shipped — the device's
  own library*. Then one line: none has been modified; all are used as OpenCV publishes them.
- **Then one section per group, the licence text copied verbatim** — not summarised, not
  re-wrapped, not re-indented, comment markers kept where the source has them — from exactly these
  files, every one fetched over normal TLS and never with `curl -k`. `O` is
  `https://raw.githubusercontent.com/opencv/opencv/4.13.0/`.
  1. **Apache-2.0** — one copyright line per component, then the text once, from `O` + `LICENSE`
     (byte-identical to the shipped `LICENSE-OpenCV-Apache-2.0.txt`, and to ADE's and Flatbuffers'
     own). libcpufeatures: the whole of `O` + `3rdparty/cpufeatures/LICENSE`, which is its
     copyright line and the Apache notice. ADE: `Copyright (C) 2018 Intel Corporation`, as its
     sources at `v0.1.2e` carry it. oneTBB: Intel Corporation. Flatbuffers: Google. KleidiCV:
     `2023 - 2025 Arm Limited and/or its affiliates`, as its SPDX headers at `0.7.0` carry it. None
     has a `NOTICE` file, so Apache-2.0 §4(d) adds nothing; a holder line suffices where the
     sources' years vary (§4(c) binds source form).
  2. **BSD-3-Clause** — each text names its holder, so each is copied whole: protobuf,
     `O` + `3rdparty/protobuf/LICENSE`; libwebp, `O` + `3rdparty/libwebp/COPYING`; OpenEXR,
     `O` + `3rdparty/openexr/LICENSE`; ittnotify, preceded by the line *ittnotify is licensed
     GPL-2.0-only OR BSD-3-Clause; Easy Gallery uses it under BSD-3-Clause.*, then
     `O` + `3rdparty/ittnotify/src/ittnotify/BSD-3-Clause.txt`; Carotene, the licence comment block
     at the top of `O` + `hal/carotene/src/add.cpp`, from its first line to the end of its
     disclaimer.
  3. **libjpeg-turbo** — first this line, on its own: `This software is based in part on the work of
     the Independent JPEG Group.` — README.ijg's LEGAL ISSUES (2) requires that statement word for
     word, and upstream wraps it across two lines, so it is written out once unbroken. Then
     `O` + `3rdparty/libjpeg-turbo/LICENSE.md` whole, then the LEGAL ISSUES section of
     `O` + `3rdparty/libjpeg-turbo/README.ijg`, from its `LEGAL ISSUES` heading up to, not including,
     `REFERENCES`.
  4. **BSD-2-Clause** — OpenJPEG, `O` + `3rdparty/openjpeg/LICENSE` whole.
  5. **libtiff** — `O` + `3rdparty/libtiff/LICENSE.md` whole, the Berkeley LZW paragraph included.
  6. **libpng-2.0** — `O` + `3rdparty/libpng/LICENSE` whole.
  7. **LLVM** — the whole of `libcxx/LICENSE.TXT` from
     `https://raw.githubusercontent.com/llvm/llvm-project/3c92011b600bdf70424e2547594dd461fe411a41/libcxx/LICENSE.TXT`.
  8. **zlib** — one line: listed by `getBuildInformation()`, linked against the device's
     `libz.so`, not shipped, so nothing is owed.
- **`NOTICE.txt` gains one line, yes**, because it already is the index of the face notices — it
  names OpenCV and both models and points to each licence file. After its numbered list:
  `The third-party components bundled in OpenCV, and their licences, are in THIRD_PARTY_NOTICES.txt.`
  Nothing else in it changes.
- **`SHA256SUMS` stays as it is: the notice is not pinned.** `verifyFaceModelChecksums` requires a
  line only for `*.onnx` files and hashes only what `SHA256SUMS` lists, so an extra `.txt` passes;
  the task re-runs because its input directory changed, which is harmless. Pinning it would fail
  `FacePipelineVersionTest`'s *the model checksums list both models and nothing else*, and the
  checksums are the models' identity, which `FACE_PIPELINE_VERSION` fingerprints — a notice is not.
- **Line endings:** the asset keeps the upstream texts' LF, as fetched, UTF-8 without a BOM
  (`LICENSE.md` of libjpeg-turbo and libtiff contain non-ASCII). The test must not depend on CR or
  LF.
- **The test, plain JVM,** modelled on `FacePipelineVersionTest` and `GazetteerAssetTest`, which
  read `File("src/main/assets/…")` from the module directory (there is no
  `GazetteerAssetPackagingTest`). It reads `src/main/assets/face/THIRD_PARTY_NOTICES.txt` as UTF-8,
  splits on `\r?\n` and compares lines trimmed, and asserts: the file exists and is not blank; each
  component name above appears with its version; and each of these lines is present —
  `Apache License` and `Version 2.0, January 2004`; `Copyright (C) 2016 The Android Open Source
  Project`; `Copyright 2008 Google Inc.  All rights reserved.` (two spaces, as upstream);
  `Copyright (c) 2010, Google Inc. All rights reserved.`; `Copyright (c) 2006, Industrial Light &
  Magic, a division of Lucasfilm`; `Copyright (c) 2019 Intel Corporation. All rights reserved.`;
  the ittnotify election line; for Carotene, a line containing `(3-clause BSD License)` and one
  containing `NVIDIA Corporation` (its first line was not read here, so the test does not pin it);
  `This software is based in part on the work of the Independent JPEG Group.`; `libjpeg-turbo
  Licenses`; `LEGAL ISSUES`; `* The copyright in this software is being made available under the
  2-clauses` (OpenJPEG; upstream has a trailing space, which trimming removes); `# LibTIFF license`;
  `COPYRIGHT NOTICE, DISCLAIMER, and LICENSE`; `The LLVM Project is under the Apache License v2.0
  with LLVM Exceptions:`. A second test asserts `NOTICE.txt` names `THIRD_PARTY_NOTICES.txt`.
- **Verification,** by the Coder, no device: `testDebugUnitTest` (the full unit suite) and
  `assembleDebug`, whose asset merge runs `verifyFaceModelChecksums` over the changed directory.
  This is the "gate runs again" the *Verification* paragraph below asks for when Task 4 touches more
  than text: it adds one unit test and no main or instrumented code, so those two cover it. The
  verification phase over Tasks 1 and 2 is not repeated; Task P checks the three files are tracked.

**Why four, and sequential.** Task 2 builds on Task 1's interfaces and both edit `EasyGalleryApp.kt`.
They are split because Task 2 ~~waits on F2,~~ needs the phone and may stop at the manifest gate, and
Task 1 needs none of that; one Coder may take both in turn. Nothing runs in parallel.

**Verification** is its own phase, once, over the integrated change, owned by whoever the brief
names: the definition of done with `adb devices` stated — `connectedDebugAndroidTest` on the
SM-G990B if attached, the opt-in measurement skipped — and `git ls-files` listing `6.json` and the
two model files. Task 2's device and bundle checks are not repeated there. *Ordered 2026-11-20:*
it runs after Task 2, over Tasks 1 and 2 integrated, and before Tasks 3 and 4, which add a licence
record and text files only and do not re-run it — unless Task 4 has to touch anything but text under
`assets/face/`, the build's checksum task included, in which case the gate runs again for it. Task P
checks with `git ls-files` that Task 4's files are tracked.

**After it lands — Planner, task P.** ✅ **Done 2026-11-20:** landed in `ARCHITECTURE.md` §3, §6,
§10 and §11, `AGENTS.md`, the dated notes in ADR-0018 Amendment 7, ADR-0022 and ADR-0023, and the
test conventions; this plan and [albums.md](albums.md) §7 last. The brief was: `ARCHITECTURE.md` §3 (the new `data/` files; the store, DAO,
database, entities and worker rows; app entry points), §6 (version 6, five tables, the new
preferences file, four rules sections), §10 (OpenCV), §11 (Amendment 6's two costs), linking
ADR-0023 and ADR-0024 where they now govern; `AGENTS.md` *Tech stack*, *Preference stores*, *The two
databases* and its paragraph on ADR-0024's placed state; ADR-0018 Amendment 7 and ADR-0023's verify
table, each a dated *Built* note with the figures; the test conventions, for the opt-in device test;
this section, the steps row, the handoff, and [albums.md](albums.md) §7.

**Not 3.3's:** grouping and the memory budget (3.4); the `people` clause, and a video or GIF never
pending in evaluation (3.5); the disclosure, the switch and its confirmation, the app-open deletion
and capability check, presenting *not scrubbed*, the People criterion's visibility, the licence
row, and every string (3.6); the SM-G781B, the phones' journal modes and the favourite toggle
(3.7); a trimmed OpenCV; any Tier C `Flow` or Settings figure. No `ui/` file, no `*Content`
signature, no string.

**Needs the maintainer:** ~~before Task 1, his approval of ADR-0018 Amendment 7 (recommended, as the
handoff says); before Task 2, F2, the OpenCV pin.~~ Both given 2026-11-20 (decision 49). **Now
only, during Task 2:** his answer on anything the merged manifest adds, and his phone, the
SM-G990B, for the measurements, which process its library on the phone. **Call-outs, not
approvals:** the new preferences file `face_device_state`; index version 6 through
`Migration(5, 6)`; F1's fourth section, which he can reverse — all three inside Amendment 7.

### 3.3 — Built and verified 2026-11-20

Built as the four tasks above, then task P. What landed is not restated here: it is
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 7's *Built*
note — the attempt table, `Migration(5, 6)` to version 6, `face_device_state`, the fourth rules
section — and [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md)'s
*Built* note and verify table — the models, OpenCV 4.13.0, the capability check, the packaging, the
notices — with `ARCHITECTURE.md` §3, §6, §10 and §11 for where each piece sits.

**The gate — the definition of done, once, over the integrated change:** the unit suite, 73 files,
1077 tests, 0 failures; a device was attached, the SM-G990B, so `connectedDebugAndroidTest` ran, 264
passed and 1 skipped — the opt-in measurement — with 0 failures; the debug and release merged
manifests unchanged against the build before OpenCV (ADR-0023 verify item 4). 1072, in the handoff,
was the unit count at the earlier gate over Tasks 1 and 2. This document does not record tracking
state.

**On the device:**
- `FaceCapabilityDeviceTest`, 2 of 2: capable on the arm64 phone.
- `FaceDecodeOrientationDeviceTest`, 4 of 4: ADR-0022's upright frame holds under EXIF orientations
  3, 6 and 8.
- **Item 6, `FaceIndexPassMeasurementDeviceTest` (opt-in):** 1,133 live items; 1,002 images reached
  a state (27 + 46 + 929 over the runs), 0 failed; 422 faces; 150.7 ms per image, the remainder
  run's mean; peak RSS 619,804 kB, an upper bound, the test process loading the pipeline three
  times. Of two budgeted runs the second processed only what the first left, so the resume
  arithmetic holds. The 90 GIFs are skipped and stay pending by design (E2), for 3.5. **Its limits:**
  in the process, on a throwaway in-memory index, screen off, USB charging, thermal status 1 before
  and after — **not through the worker**, whose pass the disclosure gate keeps dormant until 3.6.
  The 2,800 images per 7-minute run that follow from the mean are arithmetic, not a measurement.
- **The release build**, its arm64 APKs installed through bundletool on the phone, logs
  `face capability checked capable=true`, once `app/proguard-face.pro` keeps
  `org.opencv.core.CvException`, which R8 had removed.

**Review outcomes.** Task 1: S1 to S6 and P3 fixed, P3 being the Tier C lock now taken before the
capability check (*Order in `doWork`*, amended). Task 2: no hard violations; the self-test's
detector stage now runs at threshold 0 and requires a non-empty 15-column detection table; a
`SOURCE_EXCEPTION` is rethrown as `IOException` (*Decided at 3.3 — the pass*). Its other judgement
calls are (g) of the handoff's entry for Tasks 1 and 2.

**Not proven by it** — each one step 3.7's, items 5 to 10 under *3.2 to 3.8*:
- the pass through the real worker, with Index now, the screen off and the production budget;
- whether OpenCV's native errors print under `cv::error()` in logcat, read in code only;
- the release build's capability and a face pass on any phone but the SM-G990B;
- the capability test's non-arm64 branch, never run on a device without arm64-v8a;
- the download budget after any later model or OpenCV change — 46,063,982 B of 50 MB today;
- ADR-0023 verify item 7, the capability check on another phone.

### 3.4 — Clustering and the person record — refined 2026-11-20

The Coder's brief. What grouping is and what it must meet is [albums.md](albums.md) §8 decisions 9,
40, 42, 45 and 46,
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendments 5–7,
[ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) §1 and §3, and
[ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) §1, §4 and §5,
and is not restated. What they left to 3.4 is marked **Decided at 3.4**, with its reason; each is the
Planner's and reversible by the maintainer. Read against the code as it stands on 2026-11-20:
`data/MediaIndexTierC.kt`, `MediaIndexEntities.kt`, `MediaIndexDao.kt`, `MediaIndexStore.kt`,
`FaceIndexPass.kt`, `OpenCvFacePipeline.kt` (128-float SFace embeddings), `MediaIndexingWork.kt` and
`EasyGalleryApp.kt` (`faceIndexLock`). Planning only: no code and no Gradle run by the Planner.

#### The dev-device route — answered 2026-11-20

The handoff asked how 3.4 could exercise and measure grouping before 3.6, when no installed app holds
a face until 3.6 shows the disclosure. **The maintainer answered on 2026-11-20:** "I don't see the
risk. The app is not getting installed on any device apart from the test device connected to the dev
environment." **The opt-in dev-device route is accepted for 3.4's measurements and tests** — the
SM-G990B, the only install — and is neither a blocker nor a risk. What it means for 3.4's tasks:
- **A design constraint, not a risk:** whatever sets the disclosure flag, or runs the pass or
  grouping outside the worker, lives in `src/androidTest` or `src/test` only — never in `src/main`,
  never in a debug-only or release path. Step 3.6's disclosure stays the only way a shipped build
  ever enables face data. Task 4's test calls the existing `markDisclosureShown()` on the production
  store, which 3.6 will call too; nothing in `src/main` gains a way to set, clear or skip the flag.
- **ADR-0024 §1–§3 still bind every real build:** the disclosure before the first pass, the switch
  that deletes, nothing leaving the device. On the dev phone, face data the measurements write stays
  on the phone (decision 9): aggregates only leave it, through logcat and the test report — never a
  box, an embedding, a group id or a URI — and Task 4 deletes what it wrote.

#### Measurement first, and the budget

**What is known, by arithmetic only.** The real library is 422 faces in 1,002 images (*3.3 — Built
and verified*), 0.42 faces per image; at that density 2,000 faces is about 4,700 images, 5,000 about
11,900 and 10,000 about 23,700. Average linkage needs every pairwise distance. Stored as a condensed
float32 triangle, n(n−1)/2 × 4 bytes: **0.36 MB at 422, 8 MB at 2,000, 50 MB at 5,000, 200 MB at
10,000**. The spike's "about 800 MB" was a full n × n float64 matrix. Computing the distances is
n²/2 dot products of 128 floats — about 6.4 × 10⁹ multiply-adds at 10,000. None of this is measured,
and whether 200 MB can be allocated at all depends on the Java heap limit of the phone, which Task 1
records.

**The budget — proposed by the Planner, for the maintainer to accept** (ADR-0023 §5):
1. **Memory: at most 256 MB at 10,000 synthetic faces** — the rise of the process's peak RSS
   (`VmHWM`) over its level just before grouping, every phase included — with no
   `OutOfMemoryError`, in the app's default heap. `android:largeHeap` is not an option here: it is a
   manifest change. *Why 256 MB:* it lets grouping cost what the pass already costs — the spike's
   harness peaked at 299 MB with SFace loaded — so the worker's peak is not raised beyond the order
   3.3 measured (619,804 kB, an upper bound). Grouping runs after the pass has closed its pipeline, so
   the two do not hold memory at once, though native memory freed need not return to the system.
2. **Time: at most 90 s at 10,000 synthetic faces**, on the SM-G990B, screen off — reading the faces,
   the distances, the clustering, the representatives and the write. *Why:* grouping starts at most
   8 minutes after `doWork` began (*Where it runs*, below) and WorkManager stops a worker at about 10.
3. **At the real library**, 422 faces, both are expected to hold by orders of magnitude; Task 1 shows
   it rather than assuming it.

**If Task 1's figures fail the budget**, nothing after Task 1 is built, and the maintainer chooses.
**The options, for him — none is decided here:**
- (a) **Same groups, less memory — components first.** Split the faces into the connected components
  of the graph whose edges are pairs at distance ≤ 0.50, and run average linkage inside each. Average
  linkage at this cut never joins two components, because an average of at most 0.50 needs at least
  one pair at most 0.50, so the groups are identical; memory is the largest component's triangle plus
  the edge list. Its worst case, one giant component, is the full triangle again: Task 1 records the
  largest component so that this can be judged.
- (b) **Same groups, the triangle outside the Java heap** — a direct buffer. It escapes the heap
  limit, not the process's RSS or the low-memory killer.
- (c) **A half-precision triangle** — half the memory; changes rounding at the threshold.
- (d) **A cap on the faces grouped** — faces beyond it need a state Amendment 5 does not have, or they
  are pending for ever.
- (e) **B2: assigning new faces to existing groups incrementally**, with a full regrouping now and
  then — order-dependent, and the spike found it much less stable.
- (f) **Approximate clustering** — a nearest-neighbour graph, or sampling.

(a) and (b) keep decision 45's groups exactly and change only how they are computed; (c) to (f)
change the groups. Under the rule in *3.2 to 3.8* each is a new decision, not a detail: a decision in
[albums.md](albums.md) §8 and an amendment to ADR-0023 §5 — and, for (d), to ADR-0018 — before
anything is built on it.

**B2 — Decided at 3.4, subject to the budget: not adopted.** Every run that finds grouping needed
(below) regroups every face from scratch with decision 45's average linkage. *Why:* the result is
then a function of the stored faces alone, independent of the order they arrived in, which is
decision 45's reason for average linkage, and what the spike measured; and the budget above is what
makes a full regrouping affordable. B2 comes back only as option (e).

#### Decided at 3.4 — the grouping

- **The algorithm — decision 45, made exact.** Agglomerative clustering with average linkage
  (UPGMA) on cosine distance, `1 − a·b / (|a||b|)`, over every stored face at
  `FACE_PIPELINE_VERSION`, cut at **0.50 inclusive**: two clusters merge while the mean distance over
  all their cross pairs is at most 0.50. It is what the spike measured — SciPy's average linkage with a
  distance cut, whose cut keeps merges at or below the threshold. Faces at another pipeline version
  are not grouped: their images are pending, and embeddings of two builds do not mix (*Decided at
  3.3*). Distances are computed in double from embeddings normalised once, and stored as a float32
  condensed triangle.
  - **The core takes a distance triangle, not embeddings**, so tests can state exact distances; a
    thin function computes the triangle from embeddings. Nearest-neighbour chain or a priority queue
    is the Coder's choice; the test holds either to a naive reference.
  - **Deterministic, and independent of input order:** the input is sorted by face id first, and a
    tie between two candidate merges goes to the pair whose lower face ids are lower. So the result
    is a function of the set of (id, embedding), whatever order the store returns.
  - **A zero-norm embedding** — which `FaceEmbedding` allows — is at distance 1.0 from every face,
    so it is a group of its own; no NaN reaches the triangle. Embeddings of unequal length are a
    bug, and fail the grouping with a fixed message that names no value.
- **`FACE_GROUPING_VERSION = 1`**, beside `FACE_PIPELINE_VERSION`: it names the algorithm, the
  metric, the 0.50 cut and its inclusiveness, the float32 triangle, the tie-break and the two rules
  below. Changing any of them bumps it, which regroups with no file I/O and no inference
  (Amendment 5).
- **What a group stores — Decided at 3.4: no table and no column; the index stays at version 6.**
  Amendment 5 left per-group summaries — image count, most recent photo, medoid face — to 3.4, stored
  or computed on read. Each face's `groupId` and `groupingVersion`, which `Migration(4, 5)` already
  created, are all that is written:
  - **`groupId` is the id of the group's representative face** (next bullet). Face ids are
    auto-generated and never reused, and groups are disjoint, so it is unique within a grouping —
    the stable id decision 46's last tie-break needs — and it records the representative without a
    column.
  - **The image count and the most recent photo are computed on read**, by a query over the faces
    and their index rows. *Why not stored:* they change with every Tier A write that touches a date,
    so storing them would add a second thing to keep true; a query over at most tens of thousands of
    rows costs no migration (Amendment 4's precedent: an index or a table is added when a measured
    query needs it).
  - So there is **no `Migration(6, 7)` and no ADR-0018 Amendment 8**. Task 2 checks that `6.json`
    is unchanged and that no `7.json` appears.
- **The representative — Decided at 3.4: the group's medoid, ties to the lowest face id.** The member
  whose summed cosine distance to the other members is smallest; a group of one is its own face.
  Computed after clustering from the embeddings, streaming per group, since clustering overwrites the
  triangle. **Deliberately not the spike's rule** — the medoid among members at least 60 px across:
  a stored box is in fractions (ADR-0022 §1), the decoded size is not stored, and Tier A's EXIF width
  and height are often absent and not upright. Restoring the 60 px rule would need the decoded size
  stored, a migration and an ADR-0018 amendment. The 40 px cut-off already removes the smallest
  faces, and the medoid favours a group's most typical face. **Measured, not assumed:** Task 1
  reports how many offered representatives are under 60 px, decoding each on the phone; if it is
  more than a few, the Planner puts the stored-size amendment to the maintainer.
- **When grouping is needed.** Some face at `FACE_PIPELINE_VERSION` has a null `groupingVersion`
  or another one; or its `groupId` names a face that no longer exists — its representative was
  deleted with its image, or dropped by a changed healing triple (Amendment 6). Otherwise nothing
  runs. **A deleted face that was not a representative does not regroup:** what is left of its group
  is still a group, and the next new face regroups everything. *Why:* detecting it would need
  bookkeeping the columns do not hold, for a change that rarely moves a group.
- **The write — one transaction at the end, conditional, by face id.** The setting is read inside it
  as the backstop (Amendment 7 §4): off, nothing is written. Each group's faces get `groupId` and
  `groupingVersion` through `UPDATE … WHERE id IN (…)`, chunked under API 28's 999 variables. An id
  deleted or re-keyed since the read is skipped or followed, by construction; a face added since the
  read keeps its null version, so grouping is needed again next run. Nothing else is touched: states,
  attempts, Tier A. *Why it is safe without comparing more:* only the Tier C pass inserts faces, and
  it holds the same lock; a heal re-keys a face but keeps its id; any other change deletes the face.
- **The read is paged by face id**, ids and embeddings only, outside a transaction; at 10,000 faces
  the embeddings are 5 MB.
- **Where it runs — Decided at 3.4: in the worker, after the pass, under the same hold of the Tier C
  lock** (ADR-0023 §4: "in the same worker or as its own step"). `runFaceIndexPass` already holds
  `faceIndexLock` across the capability check, the disclosure check and the pass; grouping runs before
  the lock is released, so it never runs twice at once (Amendment 5), and never beside a pass.
  - **After the pass stops for `COMPLETED`, `BUDGET` or `PERMISSION`**; not after `SETTING_OFF`, and
    not when the pass threw.
  - **Only if it starts within 8 minutes of `doWork`'s start** (`FACE_GROUPING_START_DEADLINE_MILLIS`,
    injectable clock); with the 90 s budget that ends before WorkManager's stop at about 10.
  - **It is not resumable, and need not be.** Cut short — the deadline, cancellation, the setting — it
    writes nothing and the next run repeats it. While the pass has a backlog, a grouping may be cut;
    once the backlog is done the pass ends early and grouping has the run's window, so nothing waits
    for ever. Grouping after a `BUDGET` stop still runs, so that groups appear while a large library
    is still being indexed.
  - **The gates are the pass's:** setting on, capable, disclosure shown. Grouping loads no model, but
    it processes face data, which ADR-0024 §2 keeps behind the disclosure.
  - **Checks between phases:** the setting and cancellation after the read, during the distances
    (every row), during clustering (every few hundred merges) and before the write. On a stop it
    returns, holding nothing — ADR-0024 §3 step 4's "grouping state" is never kept beyond the call.
- **Reporting.** The run state receives nothing (Amendment 5). Lines come from `FaceIndexLogLines`,
  carrying a fixed stop name only — grouped, not needed, setting off, past the deadline — and **no
  count of faces or groups, no id and no time**: ADR-0024 §6 allows counts of images only. A
  grouping failure is the pass's: caught under the same `catch (Throwable)`, it fails the run with
  `FACE_INDEX_FAILURE_MESSAGE` and logs no throwable. An `OutOfMemoryError` is such a failure, and
  repeated at every run it would be the budget failing — which Task 1's measurement exists to
  prevent.

#### What 3.4 produces, and what it leaves

**3.4 produces:** grouped faces in Tier C (`groupId`, `groupingVersion`); the grouping pass in the
worker; and **the person record, computed on read** — a store method returning, for the People
picker, every group at the current pipeline and grouping versions whose representative exists and
which has at least a given number of **distinct images** (decision 40's two), sorted by distinct
images descending, then the newest `capturedDateTakenEpochMillis` among its images descending with no
date last, then `groupId` ascending (decision 46). Each carries its `groupId`, its distinct-image
count, that date, and its **representative candidate**: the representative's image identity —
URI and healing triple, read from its index row — its `FaceBox` and its pipeline version. Those three
are exactly an anchor's fields under ADR-0022 §1, so the face the user sees is the face the rule will
store (decision 41). The per-group face list is not stored: it is the faces sharing a `groupId`. Every
new type has a redacted `toString`; no `Flow` (3.2's rule: 3.5 and 3.6 shape what they observe).

**Left to 3.5:** the `people` clause, building an anchor from the representative candidate, and
evaluation — resolving an anchor by IoU to a face and its group (ADR-0022 §3), and the per-image
projection evaluation needs. **Left to 3.6:** the picker; the thumbnail, **cropped from the
representative's image and box at display time and never stored** — no crop anywhere in Tier C or
the album database (Amendment 5, ADR-0022 §1), and none in an image loader's disk cache (ADR-0024
§6); the disclosure that wakes the pass and grouping on a real install; dropping held state on
switch-off.

#### Tasks — four, sequential, with the maintainer between Task 1 and Task 2

Paths are under `app/src/main/java/com/davide/seddio/easygallery/`, and tests under
`app/src/test/…/data/` or `app/src/androidTest/…/data/`. Method names are the Coder's; what each
does is not. The Kotlin files are CRLF, and new ones should be too.

**Task 1 — Coder, with the SM-G990B attached: the grouping and its measurement.** Waits on nothing.
- New `data/FaceGrouping.kt`, Android-free: `FACE_GROUPING_VERSION`, the 0.50 cut, the triangle
  core, the embedding front, the representatives, a probe for the setting, cancellation checks, and
  redacted input and output types — a face id with its embedding in, a group id per face id out.
  **Not wired:** nothing in `src/main` calls it yet.
- New `FaceGroupingTest.kt`, plain JVM. Exit criteria, each a test:
  1. equal to a naive reference average linkage written in the test, on at least 50 seeded sets of
     2 to 60 faces whose distances crowd around 0.50;
  2. a pair at exactly 0.50 merges — (1, 0, 0, 0) and (0.5, 0.5, 0.5, 0.5), exactly 0.5 in double —
     and a pair just above does not;
  3. a chain that single linkage would join and average linkage does not;
  4. on a stated triangle with d(A,B) = d(B,C) = 0.40 and d(A,C) = 0.70, and A < B < C by id, the
     groups are {A, B} and {C} whatever the input order; and 100 shuffles of a random set give one
     output;
  5. `groupId` is the medoid's id, a medoid tie goes to the lowest id, and a group of one is its
     face; empty input, one face, identical embeddings, a zero-norm embedding; unequal lengths fail
     with a message carrying no value;
  6. cancellation propagates, and the setting probe reading off stops it with no result;
  7. every new type's `toString` is redacted.
- New `FaceGroupingMeasurementDeviceTest.kt`, **opt-in** like 3.3's (an instrumentation argument,
  `faceGroupingMeasurement=true`; skipped otherwise), screen off, aggregates only:
  1. **The real library, in the process**, as 3.3's measurement does it: an in-memory index, Tier A,
     `FaceIndexPass` run directly with an in-memory setting store — never the app's own index, and no
     disclosure flag — then the grouping over its faces. Figures: faces, groups, groups with at least
     two distinct images, the largest connected component at ≤ 0.50, time per phase, the `VmHWM`
     rise, and **how many offered representatives are under 60 px** on their shorter side, decoding
     each representative's image with `decodeUprightForFaces` on the phone.
  2. **Synthetic sets of 2,000, 5,000 and 10,000 embeddings**, seeded, in ascending order so each
     `VmHWM` rise is attributable: 128-float identities with heavy-tailed sizes like the real
     library's — most seen twice, a few dozens of times — each a random unit centre plus noise
     calibrated to a mean within-identity distance of about 0.35 and between of about 1.0, the
     calibration reported. Per size: time per phase, the `VmHWM` rise, the Java heap used,
     `Runtime.maxMemory()`, `ActivityManager.memoryClass` and `largeMemoryClass`, the largest
     component. **An `OutOfMemoryError` is caught and reported as that size's result**, not as a
     test failure.
  3. Thermal status before and after, and whether the phone was charging, as 3.3 reported them.

  Helpers may be moved out of `FaceIndexPassMeasurementDeviceTest.kt` into a shared androidTest
  file, by moving code only.
- **Verification by the Coder:** `FaceGroupingTest`, `compileDebugAndroidTestKotlin`, the opt-in run
  with every figure in the report beside the budget, and mutations 1 to 5 below. Not the full gate.

**Then the maintainer — the budget.** He accepts the budget under *Measurement first*, as proposed or
changed, now or with Task 1's figures in front of him. If the figures fail it, he chooses among
options (a) to (f), and the Planner records the choice as a decision in [albums.md](albums.md) §8
and an amendment to ADR-0023 §5 before Task 2 is briefed again. The Planner records an acceptance
too: a decision in §8 and a dated note in ADR-0023 §5.

**Task 2 — Coder: the store and the grouping pass.** Needs the budget accepted.
- `data/MediaIndexDao.kt` and `data/MediaIndexStore.kt` — the interface, `RoomMediaIndexStore` and
  `InMemoryMediaIndexStore`: the faces for grouping, current pipeline version only, ids and
  embeddings, paged by id; whether grouping is needed (*When grouping is needed*); the conditional
  write by id with the setting read inside it (*The write*); and the person record (*What 3.4
  produces*). All `suspend`; no `Flow`.
- `data/MediaIndexTierC.kt` — the models those return, redacted.
- New `data/FaceGroupingPass.kt`, Android-free: needed or not, read, group, write; the setting, the
  deadline and cancellation; its log line. Its caller holds the Tier C lock and has checked the gates.
- `data/FaceIndexPass.kt` — `FaceIndexLogLines` gains the grouping line and its fixed stop names,
  so the logger test still holds one set.
- Tests: `MediaIndexTierCStoreTest.kt` through `forEachStore`, so both stores meet each rule;
  `MediaIndexDaoTest.kt` for the queries on `inMemoryDatabaseBuilder`, with an update of more than
  999 ids; new `FaceGroupingPassTest.kt`, plain JVM on the in-memory store. Exit criteria:
  1. the faces for grouping are the current pipeline version's only, in id order, paged;
  2. grouping is needed for a null version, another version and a missing representative, and not
     with none or all current; faces at another pipeline version are ignored;
  3. the write applies by id, skips a missing id, leaves faces outside it, states, attempts and
     Tier A untouched, and with the setting off writes nothing and returns 0;
  4. the person record counts distinct images — two faces in one image count once — applies the
     minimum, sorts by count, then newest date with no date last, then `groupId`; returns the
     representative's current identity, its box and pipeline version — after a heal, the healed URI;
     and leaves out a stale grouping version, a missing representative and another pipeline version;
  5. after grouping, `deleteAllFaceData` leaves no face and `hasFaceData` reads false; a heal keeps
     `groupId`; a changed triple drops the faces, and makes grouping needed if one was a
     representative;
  6. the pass: not needed — nothing written; needed — written with the version; the setting off
     before the write, the deadline past, or a cancellation — nothing written;
  7. **the logger test (ADR-0024 §6):** every line is one of the fixed set and carries no digit;
     fixtures with distinctive ids, URIs and embedding values appear in no line.
- **No schema change:** `git status app/schemas` shows nothing new, and `6.json` is unchanged.

**Task 3 — Coder: the worker.** After Task 2; the same Coder may take both in turn.
- `data/MediaIndexingWork.kt` — a `createFaceGroupingPass` seam on the `@JvmOverloads` constructor,
  defaulting to `EasyGalleryApp`'s wiring as the other seams do; `runFaceIndexPass` runs it after
  the pass, inside the same lock hold and `try`, on the stops and deadline in *Where it runs*.
- `EasyGalleryApp.kt` — `createFaceGroupingPass()`.
- `MediaIndexingWorkerTest.kt` (Robolectric, `work-testing`), exit criteria: grouping runs after the
  pass, with the lock held — the fake asserts it; not with the setting off, incapable, the disclosure
  not shown, the lock held elsewhere, after a `SETTING_OFF` stop or after the pass threw; it runs
  after `BUDGET` and `PERMISSION`; not past the deadline; a grouping failure fails the run with the
  fixed message and nothing else in the output data, no throwable logged; the run state's counters
  are Tier A's alone.

**Task 4 — Coder, with the SM-G990B attached: grouping through the worker.** After Task 3.
- New `FaceGroupingWorkerMeasurementDeviceTest.kt`, **opt-in**
  (`faceGroupingWorkerMeasurement=true`), and androidTest only by the constraint in *The dev-device
  route*. On the app's own index, on the dev phone: the setting on through
  `SharedPreferencesFaceDataSettingStore`; the disclosure marked shown through
  `SharedPreferencesFaceDeviceStateStore.markDisclosureShown()`; Index now through
  `WorkManagerMediaIndexingWorkScheduler`; the unique work awaited, and repeated until no image is
  pending, three runs at most. Figures: runs, wall time per run, the `VmHWM` rise, thermal status,
  charging, faces, groups, groups offered, and that grouping is no longer needed. **In `finally`:**
  `deleteAllFaceData()` on the app's store, and the `face_device_state` file cleared through
  `Context.getSharedPreferences` in the test — no new method in `src/main`. The face data stays on
  the phone (decision 9), and the run leaves none behind.
- It discharges the worker half of ADR-0023 verify item 6 for 3.3's pass and 3.4's grouping alike,
  with the disclosure set by a test rather than by 3.6's screen; 3.7's item 5 keeps the run through
  the real disclosure.

**Mutation checks — required.** Each applied, its test seen to fail, and the source restored and
checked by checksum, as 3.2 did; the report lists each with its failing test. 1 to 5 in Task 1, 6 to
8 in Task 2, 9 and 10 in Task 3:
1. single linkage in place of average; 2. `<` in place of `≤` at the cut; 3. a cut of 0.55;
4. the tie-break removed, input order kept; 5. the representative as the lowest id, not the medoid;
6. the write ignoring the setting; 7. "needed" ignoring a missing representative; 8. the person
record counting faces, not distinct images; 9. grouping called outside the lock, or without the
disclosure; 10. a grouping log line carrying a count.

**Verification** is its own phase, once, over Tasks 1 to 4 integrated, owned by whoever the brief
names — not by Task 4's Coder: the definition of done with `adb devices` stated,
`connectedDebugAndroidTest` on the SM-G990B if attached with every opt-in measurement skipped, and
`git status app/schemas` showing nothing new. Tasks 1 and 4's device measurements are not repeated
there.

**After it lands — Planner, task P.** What 3.4 makes stale:
- `ARCHITECTURE.md` §3 — the two new `data/` files, in the pure, Android-free list as well; the store,
  DAO, Tier C, worker and `EasyGalleryApp` rows; §6 — what `groupId` means, summaries computed on
  read, the version still 6; §11 — if the 60 px figure or a budget option leaves debt.
- `AGENTS.md` *The two databases* — the paragraph on face data, which says what each step built.
- ADR-0018 Amendment 5 — dated notes under *What Tier C stores* (no third kind of row; `groupId` is
  the representative; summaries on read) and *Keeping Tier C true* (the conditional write; the lock).
- ADR-0023 §4 (where grouping runs), §5 (the figures and the budget accepted) and verify row 6
  (Task 4's worker run).
- [albums.md](albums.md) §7's Stage 3 row, and decision 45's B2 pointer (added 2026-11-20) if the
  budget's answer changes it. The budget's decision is written when the maintainer answers, not at P.
- This plan: the step row, the handoff, a *3.4 — Built and verified* section, *Open questions* B2,
  and 3.7's list with whatever Task 4 did not prove.
- The test conventions, for the two new opt-in measurements and the rule that only test sources set
  the disclosure flag; and `.github/agents/*.agent.md`, checked for a sentence about grouping.

**Needs the maintainer.** **Before Task 1: nothing.** **Before Task 2:** his acceptance of the budget —
or, if Task 1's figures fail it, his choice among (a) to (f), a new decision. **Not needed:** no schema
change, so no amendment to approve and no call-out; no dependency, permission, preferences file,
`*Content` signature or string. The representative rule (*Decided at 3.4*) is the Planner's and he can
reverse it.

**Not 3.4's:** the `people` clause, anchors and evaluation (3.5); the picker, the crop, the disclosure,
the switch and its confirmation, every string (3.6); grouping on the SM-G781B, and the worker through
the real disclosure (3.7); B2, unless the budget fails; any Tier C `Flow`. No `ui/` file, no
`*Content` signature, no string.

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
  measures the size it actually adds. ~~**Refining it settles**, besides, where the capability
  result and the disclosure-shown flag are kept, and F1 — both in the handoff above.~~ Refined
  2026-11-20 into its own section above, which settles both.
- **3.4 — Clustering and the person record.** Per B1 (decision 45: average linkage, cosine 0.50)
  and B2, and decisions 42 and 46 for an anchor. Average linkage's memory and time in the app are
  unmeasured — about 800 MB for the distance matrix at 10,000 faces — so 3.4 measures them before
  relying on them; a different algorithm is a new decision, not a detail. Refined 2026-11-20 into
  its own section above.
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
  - **Added 2026-11-20 from 3.3** — what *3.3 — Built and verified 2026-11-20* could not prove:
    5. ADR-0023 verify item 6 again, **through the real worker**, once 3.6 shows the disclosure:
       Index now with the screen off, under the production budget — images processed, wall time,
       mean per image, peak RSS, thermal status before and after, charging or not. 3.3 measured it
       only in the process, on a throwaway in-memory index;
    6. whether OpenCV's native errors print under logcat's `cv::error()` tag while one occurs —
       outside the pass's fixed lines, so ADR-0024 §6's logger rule decides it.
       `Core.setErrorVerbosity(false)` is the likely switch; read in code only, never observed;
    7. the release build's capability, and a face pass, on at least one non-Samsung arm64 phone if
       one is available, as well as on the SM-G781B (A4);
    8. the download budget, re-checked with bundletool after any model or OpenCV change: 3.3 measured
       +46,063,982 B against 50 MB, little headroom (ADR-0023 verify item 5);
    9. ADR-0023 verify item 7 — the capability check on another phone; see its row;
    10. the non-arm64 branch of `FaceCapabilityDeviceTest`, which asserts *incapable* and has never
        run on a device without arm64-v8a.
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
- The Play Data Safety question of decision 9 is answered and recorded. *Status 2026-11-20:* **not
  answered.** This line is what closing the stage requires, not a statement that it is met. The
  question is recorded under *3.1*, *The Play Data Safety check*, and is the maintainer's, before the
  first Play release that ships the Tier C pass.
- The index database reaches its new version through a real migration, every schema JSON is
  committed, and the index is still excluded from backup, checked against the rules files.
  *2026-11-19, step 3.2: version 5 through `Migration(4, 5)`; the rules files exclude the file and
  its `-wal`, `-shm` and `-journal` sidecars in all three sections, checked against them. Open:
  `5.json` tracked, checked by `git ls-files`; and F1.* *2026-11-20, step 3.3's refinement: F1
  decided — a fourth section, ADR-0018 Amendment 7, ~~proposed~~ adopted 2026-11-20 — and the index goes to version 6 with step
  3.3; F1's 36.1 residual stays open.*
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
(E3) — so the reading is step 3.3's, from that artifact. **2026-11-20:** read — the subsection
below.

### OpenCV 4.13.0 bundled components — read 2026-11-20

Step 3.3 Task 3. The component list is the *3rdparty dependencies*, *Media I/O* and *Other
third-party libraries* lines of `Core.getBuildInformation()`, read on the SM-G990B from the
published `org.opencv:opencv:4.13.0` AAR (Task 2). It names OpenCV's CI as the build host, as
expected for the published artifact. `libc++_shared.so` is not in that list; it ships beside
`libopencv_java4.so` in the AAR (NDK 27.2.12479018). Every source below was fetched over normal TLS
on 2026-11-20 — `raw.githubusercontent.com/opencv/opencv/4.13.0/…` for what OpenCV vendors, and
for what OpenCV downloads at build time, the upstream file at the tag OpenCV's CMake pins and, where
it was cheap, the very archive OpenCV downloads, its MD5 checked against the pin.

| Component | Version (as printed) | Licence (SPDX) | What must ship with the binary | Commercial redistribution in an app | Source read | Status |
|---|---|---|---|---|---|---|
| libcpufeatures | — (AOSP, 2016) | Apache-2.0 | Apache-2.0 text (already in `assets/face/` for OpenCV) + its copyright line; no NOTICE file in the vendored copy | yes | `3rdparty/cpufeatures/LICENSE` @ opencv 4.13.0 | verified |
| libprotobuf | 3.19.1 | BSD-3-Clause | copyright notice + conditions + disclaimer (the whole `LICENSE`), in documentation with the binary | yes | `3rdparty/protobuf/LICENSE` @ opencv 4.13.0 | verified |
| ade | 0.1.2e (not printed; pinned by OpenCV's G-API build) | Apache-2.0 | Apache-2.0 text (byte-identical to OpenCV's `LICENSE`) + "Copyright (C) Intel Corporation"; no `NOTICE` file | yes | `modules/gapi/cmake/DownloadADE.cmake` @ opencv 4.13.0 (pins `v0.1.2e`, MD5 `962ce79e…`); `LICENSE` and all 69 source headers (`SPDX-License-Identifier: Apache-2.0`) in the zip OpenCV downloads from `github.com/opencv/ade`, MD5 matched | verified |
| tbb (oneTBB) | 2022.1 (tag `v2022.1.0`) | Apache-2.0 | Apache-2.0 text + Intel's copyright; oneTBB has no `NOTICE` file at the tag. Its `third-party-programs.txt` was read: hwloc is excluded by OpenCV's build (`tbbbind` filtered out); the GCC bug-62258 workaround in `exception.cpp` compiles only under `__GLIBCXX__`, and the NDK uses libc++; gperftools, Tachyon, the Python pool and doctest are not under `src/tbb` (grep of the tag's sources); its ITT copy is the ittnotify row | yes | `3rdparty/tbb/CMakeLists.txt` @ opencv 4.13.0 (pins `v2022.1.0`, MD5 `cce28e6c…`); `LICENSE.txt` and `third-party-programs.txt` @ `uxlfoundation/oneTBB` (formerly `oneapi-src`) `v2022.1.0`; the tarball OpenCV downloads, MD5 matched | verified |
| ittnotify (Intel ITT) | 3.25.4 | GPL-2.0-only OR BSD-3-Clause — **dual; the app takes BSD-3-Clause** | the BSD-3-Clause text (Intel, 2019) from `BSD-3-Clause.txt`, and a line in the notice saying it is used under BSD-3-Clause. GPL-2.0 is not elected, so its terms do not apply | yes, under the BSD-3-Clause option | `3rdparty/ittnotify/src/ittnotify/BSD-3-Clause.txt`, `ittnotify_static.c` (SPDX line), `ittnotify_config.h` (`API_VERSION_NUM 3.25.4`) @ opencv 4.13.0 | verified |
| libjpeg-turbo | 3.1.2-70 | IJG AND BSD-3-Clause (SIMD code Zlib, subsumed by IJG per its `LICENSE.md`) | **an obligation the notice must carry word for word:** for executable-only distribution the documentation "must state that 'this software is based in part on the work of the Independent JPEG Group'" (`README.ijg`, LEGAL ISSUES (2)). The BSD-3-Clause text is owed only if the TurboJPEG API is in the binary; OpenCV's `CMakeLists.txt` builds only `JPEG_SOURCES` ("WITH_TURBOJPEG is not used"), but ship it anyway — cheaper than proving absence. Ship `LICENSE.md` and `README.ijg`'s LEGAL ISSUES section | yes — IJG: "We specifically permit and encourage the use of this software as the basis of commercial products" | `3rdparty/libjpeg-turbo/LICENSE.md`, `README.ijg`, `CMakeLists.txt` @ opencv 4.13.0 | verified |
| libwebp | decoder 0x0210 | BSD-3-Clause | the whole `COPYING` (Google, 2010) | yes | `3rdparty/libwebp/COPYING` @ opencv 4.13.0. Upstream's separate `PATENTS` grant is not vendored and not read | verified |
| libpng | 1.6.53 | libpng-2.0 (PNG Reference Library License v2; v1 text also in the file for 0.5–1.6.35 code) | nothing strictly for a binary (acknowledgement "appreciated, but is not required"; the notice binds source distributions); ship the whole `LICENSE` | yes | `3rdparty/libpng/LICENSE` @ opencv 4.13.0 | verified |
| libtiff | 4.7.1 | libtiff, plus a Berkeley notice for `tif_lzw.c` (no SPDX id asserted for it) | the whole `LICENSE.md`: the Leffler/SGI notice must "appear in all copies … and related documentation"; the Berkeley paragraph asks that "any documentation … related to such distribution … acknowledge that the software was developed by the University of California, Berkeley" — met by shipping the text | yes | `3rdparty/libtiff/LICENSE.md` @ opencv 4.13.0 | verified |
| libopenjp2 (OpenJPEG) | 2.5.3 | BSD-2-Clause | the whole `LICENSE` (copyright list + conditions + disclaimer). It notes patent rights are not granted — informational, no obligation | yes | `3rdparty/openjpeg/LICENSE` @ opencv 4.13.0 | verified |
| IlmImf (OpenEXR) | 2.3.0 | BSD-3-Clause | the whole `LICENSE` (ILM, 2006) | yes | `3rdparty/openexr/LICENSE` @ opencv 4.13.0 | verified |
| tegra_hal (Carotene) | 0.0.1 | BSD-3-Clause | copyright notice ("Copyright (C) 2014, NVIDIA Corporation", 2015 on some files) + conditions + disclaimer. Carotene has no separate licence file; the text is the header every source carries | yes | `hal/carotene/README.md` and the first lines of all 57 files in `hal/carotene/src/` @ opencv 4.13.0: 56 carry the NVIDIA "(3-clause BSD License)" header, the 57th (`dummy.cpp`) is two comment lines. It moved from `3rdparty/carotene` to `hal/carotene` | verified |
| KleidiCV (+ kleidicv_hal, kleidicv_thread) | 0.7.0 | Apache-2.0 | Apache-2.0 text + "Arm Limited and/or its affiliates" copyright; no `NOTICE` file at the tag. `kleidicv_hal` is OpenCV's own glue under `hal/kleidicv` (OpenCV's Apache-2.0) | yes | `hal/kleidicv/kleidicv.cmake` @ opencv 4.13.0 (pins `0.7.0`, MD5 `e8f94e42…`); `README.md` and `LICENSES/Apache-2.0.txt` @ `gitlab.arm.com/kleidi/kleidicv` tag `0.7.0`; the tarball OpenCV downloads, MD5 matched: all 287 SPDX headers under `kleidicv/`, `kleidicv_thread/`, `adapters/` say `Apache-2.0` | verified |
| Flatbuffers | 25.9.23 | Apache-2.0 | Apache-2.0 text (byte-identical to OpenCV's `LICENSE`, checked with `cmp`); no NOTICE file vendored. Header-only | yes | `3rdparty/flatbuffers/LICENSE.txt` and `README.md` ("Origin: google/flatbuffers v25.9.23") @ opencv 4.13.0 | verified |
| zlib | 1.3.0.1 | Zlib | **nothing — it is not in the APK.** The capture prints `z`, not `build`, and `llvm-readelf -d` on the AAR's arm64 `libopencv_java4.so` shows `NEEDED libz.so` with `inflate`/`deflate` undefined: OpenCV links the device's own zlib. Even if bundled, Zlib asks nothing of a binary ("including commercial applications"; acknowledgement "appreciated but not required"). Listed in the notice as used, not shipped | yes | `3rdparty/zlib/LICENSE` @ opencv 4.13.0; the `.so`'s dynamic section | verified |
| LLVM libc++ (`libc++_shared.so`) | NDK 27.2.12479018; `.comment` reads clang 18.0.3 "based on r522817c", source `toolchain/llvm-project d8003a45…` | Apache-2.0 WITH LLVM-exception (legacy code also NCSA OR MIT) | strictly nothing: the LLVM exception lets portions "embedded into an Object form" be redistributed "without complying with the conditions of Sections 4(a), 4(b) and 4(d)". Ship the whole `libcxx/LICENSE.TXT` anyway — it costs nothing and covers the legacy NCSA/MIT portions | yes | `libcxx/LICENSE.TXT` @ `llvm/llvm-project` `3c92011b600bdf70424e2547594dd461fe411a41` (the r522817 base revision named in NDK 27.1's `clang_source_info.md`), and the locally installed NDK 27.1's toolchain `NOTICE`; the AAR's `libc++_shared.so` and NDK 27.1's carry the same `llvm-project` commit in `.comment`. NDK 27.2's own `NOTICE` was not read (not installed here) | verified (licence); 27.2's `NOTICE` not read |

**Blocks shipping? None found.** Every component is permissive and allows commercial
redistribution in an app. The one copyleft text in the list, ittnotify's GPL-2.0-only, is the
alternative of a dual licence; the app takes BSD-3-Clause and owes nothing under the GPL. Every
obligation is met by shipping text: the notices and licence texts in the table, and one sentence
that must appear verbatim — libjpeg-turbo's IJG clause, "this software is based in part on the work
of the Independent JPEG Group". **Not verified:** NDK 27.2's own `NOTICE` (the libc++ licence was
read at the source revision its binary names, through NDK 27.1); libwebp's upstream `PATENTS` grant
(not vendored, and a grant rather than an obligation); and code OpenCV carries inside its own
modules rather than as a listed third-party dependency, which falls under OpenCV's Apache-2.0 by
OpenCV's own statement (`COPYRIGHT`: "Third party copyrights are property of their respective
owners") and was not audited file by file.

**Also read.** OpenCV's root has a `COPYRIGHT` (a list of holders) but no `NOTICE` file, so Apache-2.0
§4(d) owes nothing extra for OpenCV; and the shipped `LICENSE-OpenCV-Apache-2.0.txt` is
byte-identical to OpenCV 4.13.0's `LICENSE` (`cmp`). Flatbuffers' and ADE's `LICENSE` files are
byte-identical to it too. OpenCV's `3rdparty/readme.txt` calls libjpeg-turbo's licences "three"; its
own `LICENSE.md` says two plus zlib-licensed SIMD code subsumed by IJG — the table follows
`LICENSE.md`.

**What is owed, by group** — the input to Task 4:
- **Apache-2.0**, one text for all of them: libcpufeatures (The Android Open Source Project), oneTBB
  and ADE (Intel Corporation), Flatbuffers (Google), KleidiCV (Arm Limited and/or its affiliates).
  None has a `NOTICE` file, so a copyright line each and the licence text, already shipped for
  OpenCV and SFace, is everything.
- **BSD-3-Clause**, whose text names the holder in clause 3, so one verbatim copy each: protobuf,
  libwebp, OpenEXR, ittnotify (with a line saying BSD-3-Clause is the licence taken), Carotene
  (its source header), and libjpeg-turbo's `LICENSE.md`.
- **BSD-2-Clause:** OpenJPEG. **libtiff:** its `LICENSE.md`, with the Berkeley LZW paragraph.
  **libpng-2.0:** its `LICENSE`. **IJG:** the LEGAL ISSUES section of `README.ijg` and the
  sentence above. **LLVM:** `libcxx/LICENSE.TXT`.
- **zlib:** nothing; it is the device's library. Named in the notice so a reader who sees zlib in
  `getBuildInformation()` finds why it is absent.

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
2. **B2 — Incremental clustering** as new photos arrive. ~~**Still open, for 3.4.**~~ **Settled at
   3.4's refinement, 2026-11-20, subject to the budget: not adopted** — every run that needs grouping
   regroups every face with average linkage; *3.4*, *Measurement first, and the budget*. It returns
   only as option (e) there, if Task 1's figures fail the budget. The spike found
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

   ✅ **Decided 2026-11-20 at step 3.3's refinement, conservatively** —
   [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 7 §2,
   ~~proposed until the maintainer approves the amendment~~ adopted 2026-11-20 (decision 49):
   a fourth section, `<cross-platform-transfer platform="ios">`, holding the same `<exclude>`
   lines as the other two and no `<platform-specific-params>`, landing in step 3.3's Task 1.
   **Still open, the maintainer's, blocking nothing:** how an API 36.1 device reads a section
   without the params is not verified — ignored, honoured, or the file treated as malformed, the last
   being the one case in which the section is worse than none — no such device is here, the SM-G990B being 36.0 — and if
   he wants the doubt removed by construction rather than by rules, moving `media_index.db` to
   `noBackupFilesDir`, which Android excludes from every backup mode, is a change of its own and
   a new amendment. Recommended: not now; revisit if a 36.1 device shows the section misread.
2. **F2 — Which OpenCV version is pinned?** Raised by the Planner at step 3.3's refinement,
   2026-11-20. The maintainer approved the library, not a version (decisions 45 and 47).
   **Recommended: 4.13.0**, the build the spike measured; 4.14.0 and 5.x are on Maven Central and
   were not measured, and 5.x is a new major version. ~~**Blocks step 3.3's Task 2**, not Task 1.~~
   The facts read are under *3.3*, *Settled before 3.3*, issue 5.

   ✅ **Answered 2026-11-20 by the maintainer: 4.13.0**, as recommended —
   [albums.md](albums.md) §8 decision 49, and ADR-0023 §2's note. It confirms the version and
   nothing else: ADR-0023's verify item 4 still diffs the merged manifests when the dependency lands
   in Task 2, and any new permission, feature, `<queries>` entry or component goes to him before it
   ships, as decision 24's did.

---

## Related

- [albums.md](albums.md): §3 membership, §4 *People album*, §6 the index, §7 progress, §8 decisions
  9, 13 as amended, 14, 15, 33, 35, 40, 41, 42, 43, 44, 45, 46, 47, 48 and 49, §10 questions 3 and 12.
- [albums-stage-4.md](albums-stage-4.md): paused; the precedent for a spike.
- [albums-rule-editing.md](albums-rule-editing.md): rule editing's own plan; its D6 is the order
  between the two.
- [albums-stage-2.md](albums-stage-2.md): archived; its structure is the template.
