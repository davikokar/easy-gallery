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
> ADR-0018 Amendment 7 approved and OpenCV pinned at 4.13.0), and 50 (2026-11-20, grouping's budget
> accepted, components first, and ~~ADR-0018 Amendment 8's size-aware representative~~ the plain medoid
> as representative — Amendment 8's size-aware rule withdrawn the same day on Task 2's measurement). The steps, the
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
    images per run. *(Superseded 2026-11-23 by step 3.7's G7, through the real worker: about 1,600
    per run on the SM-G990B — *3.7*, G7; ADR-0023's row 6.)*
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
  schema change** (the index stays at version 6; no ADR-0018 Amendment 8 — *added later the same
  day:* one was adopted and reversed before it was built, so this stands; the entry *2026-11-20,
  latest* below says why), a pure grouping class,
  grouping in the worker under the Tier C lock, the person record computed on read. B2 is settled
  there, ~~subject to the budget~~ not adopted (*2026-11-20:* the budget met, decision 50). One correction found on the way: 3.3's measurement did not set the
  disclosure flag, as the question below says — it ran the pass outside the worker. No Gradle run by
  the Planner.
- ~~**Next action: step 3.4, Task 1 — Coder, with the SM-G990B attached:** the pure grouping class,
  its plain-JVM test and the opt-in measurement — *3.4*, *Tasks*, Task 1 is the brief. **It waits on
  nothing.** Then **the maintainer accepts the budget** proposed in *3.4*, *The budget* — now, or
  once Task 1's figures are in; Task 2 waits on it. If the figures fail it, he chooses among the
  options listed there, which is a new decision, and nothing after Task 1 is built until he has.
  Then Tasks 2 and 3 (store, grouping pass, worker), Task 4 (the worker measurement on the phone),
  the verification phase once, and task P.~~ Done 2026-11-20 — the two entries below.
- **2026-11-20, later — step 3.4's Task 1 built and verified on the SM-G990B.** `data/FaceGrouping.kt`,
  `FaceGroupingTest` (28 tests) and the opt-in `FaceGroupingMeasurementDeviceTest`; six mutation
  checks each failed its test; the full unit suite 1105 tests, 0 failures. The figures are in *3.4*,
  *Task 1 — measured 2026-11-20*: both budget limits held at 10,000 faces (30.7 s, +213.9 MB RSS), but
  the Java heap peaked at about 233 MB of 256, so the full triangle tops out near 10,500 faces; and 22
  of the 104 representatives offered were under 60 px. The maintainer has edited `FaceGrouping.kt` by
  hand since; the next task reads it as it stands.
- **2026-11-20, later still — the maintainer's answer, decision 50:** "Let's go with your
  recommendations." The budget is accepted as met; grouping splits faces into components first, which
  gives the same groups (the proof is under *3.4*, *Measurement first*, option (a)); and
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) **Amendment 8 is
  adopted**: each face's short side in pixels, `Migration(6, 7)` to index version 7, the representative
  preferring a face at least 60 px across, `FACE_GROUPING_VERSION` 2. It reverses the refinement's "no
  schema change". The Planner recorded it in [albums.md](albums.md) §8 decision 50 and §7, decision
  45's B2 pointer, ADR-0018 (the amendment, a marker under its title, a pointer in Amendment 5),
  ADR-0023 §5's dated note, and here: the step row, *3.4*'s budget, *Decided at 3.4* and *Tasks*,
  renumbered to five. No Gradle run by the Planner. *Amendment 8 was reversed later the same day,
  before it was built — the entry after next.*
- ~~**Next action: step 3.4, Task 2 — Coder:** components first and the size-aware representative, in
  `FaceGrouping.kt` and its tests, then the opt-in measurement at 10,000 and 20,000 faces on the
  SM-G990B — *3.4*, *Tasks*, Task 2 is the brief, with its file list. **It waits on nothing; no
  approval is pending.** Then Task 3 (schema, store, DAO and the grouping pass — `Migration(6, 7)`),
  Task 4 (the worker), Task 5 (grouping through the worker on the phone), the verification phase
  once, and task P.~~ Done 2026-11-20 — the entry below.
- **2026-11-20, latest — step 3.4's Task 2 built and verified, and Amendment 8 reversed.**
  Components first and the size-aware representative in `data/FaceGrouping.kt`, `FaceGroupingTest`
  and `FaceGroupingMeasurementDeviceTest`, uncommitted; the figures are under *3.4*, *Tasks*, Task 2.
  Components first holds the budget with room to spare — 10,000 faces in 12.8 s, 20,000 in 50.9 s.
  **The size-aware rule fixed 1 of the 22** small representatives: 21 of the 104 offered groups have
  no member at least 60 px across. The Planner recommended dropping the stored size, and the
  maintainer answered "let's go with your recommendations": a permanent schema change is not worth
  one thumbnail. So **ADR-0018 Amendment 8 is reversed before anything of it was built** — no
  column, no `Migration(6, 7)`, the index stays at version 6, the representative is the plain medoid
  — and the refinement's "no schema change" stands after all. This is the one place the reversal is
  told; the records are [albums.md](albums.md) §8 decision 50 (amended) and §7, ADR-0018 (Amendment
  8 marked reversed, the marker under its title, the pointer in Amendment 5), ADR-0023 §5 (a second
  dated note with Task 2's figures), and here: the step row, *3.4*'s *Decided at 3.4*, *Tasks* with a
  new Task 2b, Task 3 rewritten without the schema, and open question B2. No Gradle run by the
  Planner.
- ~~**Next action: step 3.4, Task 2b — Coder:** remove the size-aware representative from
  `FaceGrouping.kt`, `FaceGroupingTest` and `FaceGroupingMeasurementDeviceTest`, and set
  `FACE_GROUPING_VERSION` back to 1 — *3.4*, *Tasks*, Task 2b is the brief, with its file list. **It
  waits on nothing; nothing is pending for the maintainer.** Then Task 3 (store, DAO and the grouping
  pass — no schema change), Task 4 (the worker), Task 5 (grouping through the worker on the phone),
  the verification phase once, and task P.~~ Done 2026-11-21 — the entry below.
- **2026-11-21 — step 3.4 built and verified: Tasks 2b, 3, 4, 5, the verification phase and task P
  done.** Plain medoid and `FACE_GROUPING_VERSION` 1 again; the store's grouping methods and the
  People picker's query `listFaceGroups`; `data/FaceGroupingPass.kt`, run by the worker after the
  face pass under the same hold of the Tier C lock; the opt-in worker measurement on the phone. **No
  schema change** — the index stays at version 6. **The gate**, on the SM-G990B: unit 75 files, 1174
  tests, 0 failures; `connectedDebugAndroidTest` 266 tests, 0 failures, 3 skipped (the opt-in
  measurements); the merged manifests byte-identical. Through the real worker: the face pass 1,002
  images in 113.2 s, grouping 292 ms, a no-op second run. The figures, the review's judgement calls
  on Task 5's test and what is not proven are in *3.4 — Built and verified 2026-11-21*; 3.7's list
  gained items 11 to 14. Task P landed `ARCHITECTURE.md` §2, §3, §6 and §11, `AGENTS.md`, ADR-0018
  Amendment 5's *Built* notes and Amendment 8's reversal annotations, ADR-0023's *Built* note, §4, §5
  and verify row 6, and the test conventions; then this plan — the step row, the Tasks, the stale
  Amendment 8 text struck and annotated, open question B2, this handoff — and [albums.md](albums.md)
  §7's Stage 3 row. Stage 3 stays In progress. No Gradle run by the Planner.
- ~~**Next action: step 3.5, Task 1 — the clause.** Owner: the Coder. Step 3.5 was refined on
  2026-11-21 into *3.5 — The people clause and its evaluation* below: three Coder tasks, sequential
  because they share `AlbumRuleEvaluation.kt` and `ui/AlbumsViewModel.kt`, then the verification
  phase, which alone runs the definition of done, then task P. **Nothing is pending for the
  maintainer:** no schema change, no ADR or amendment, no dependency, permission, preferences file or
  string. The two choices the records left to 3.5 are taken there — a tie between equal overlaps goes
  to the lower face id; the cause of unevaluatable is a field beside `UNEVALUATABLE`, not a new status.~~
  Done 2026-11-21 — the entry below.
  - **Read first:** that section whole — its four *Decided at 3.5* parts and *Tasks*; ADR-0017
    Amendments 1, 2 and 4; ADR-0022 §1, §3 and §4; `data/AlbumRule.kt`, `data/AlbumRuleJson.kt` and
    `test/.../data/AlbumRuleTest.kt`, whose strictness tests Task 1's follow.
  - **Task 1's report calls out** the interim branch it leaves in `AlbumRuleEvaluation.kt` (a people
    album waits), which Task 2 removes.
  - **Left to 3.6 by it**, listed under *3.2 to 3.8*: the cause carried to the notices before any
    build that writes a `people` clause ships, the availability refresh's callers, and a durable
    capability answer on a phone with no pipeline.
- **2026-11-21, latest — step 3.5 built and verified: Tasks 1, 2, 3, the verification phase and task
  P done.** The `people` clause, parsed strictly with three new reasons, its `toString`s redacted, and
  `anchoredAt`; `observeFaceEvaluationProjection`, a second projection with no embedding;
  `data/PeopleClauseEvaluation.kt` (resolution at IoU ≥ 0.5, a tie to the lower face id, the
  per-item answers) and `data/PeopleAvailability.kt`; `AlbumUnevaluatableCause` beside
  `UNEVALUATABLE`; in `AlbumsViewModel`, a second shared flow for people albums only and
  `refreshPeopleAvailability()`. **No schema change** in either database, and no string. **The
  gate**, on the SM-G990B: unit 78 files, 1293 tests, 0 failures; `connectedDebugAndroidTest` 266
  tests, 0 failures, 3 skipped (the opt-in measurements); the merged manifests byte-identical;
  `app/schemas` unchanged. Two Reviewers, no hard violations; every follow-up done but a one-line
  cleanup. The figures, the review's dispositions and what is not proven are in *3.5 — Built and
  verified 2026-11-21*; 3.6's brief under *3.2 to 3.8* now lists five items it must do, and 3.7's
  list gained items 15 and 16. Task P landed `ARCHITECTURE.md` §2, §3, §6 and §11, `AGENTS.md`,
  ADR-0017 Amendment 4's, ADR-0022's and ADR-0024 §4's notes in an earlier run, and ADR-0018
  Amendment 5's *Built* note, this plan and [albums.md](albums.md) §7's Stage 3 row in this one.
  Stage 3 stays In progress. No Gradle run by the Planner.
- ~~**Next action: step 3.6 — the UI: the People criterion, the disclosure, the switch, the strings
  and the notices.** Owner: **the Designer first**, for D1 — where the People picker lives — and the
  criterion's layout (*Open questions* D1); then the Planner refines 3.6 into a step plan, as 3.2 to
  3.5 were; then the Coder builds it. 3.6's own text, under *3.2 to 3.8*, says: the People criterion
  with its thumbnails, with no merge and no split (decision 40); the notice of an unevaluatable
  people album, whose wording is open (decision 42); the disclosure, and the Settings switch; the
  Settings row showing the licence notices (E5); strings in every shipped locale; vocabulary
  in-change — and the five items *Added 2026-11-21 from 3.5*, each a must: a durable answer on a
  phone without arm64-v8a, the app-open capability check and start-up deletion, the refresh's
  callers, a limit on people per album, and the cause carried to the notices.~~ Designed and refined
  2026-11-21 — the entry below; its sub-items are kept as they were.
  - **Read first:** 3.6's text and its five items;
    [the UI vocabulary](../../.github/instructions/ui-vocabulary.instructions.md);
    [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §1 to §5;
    ADR-0017 Amendment 4 (the notices, one per album); ADR-0022 §5 (a restore); *Open questions* D1
    and D2; *3.5 — Built and verified 2026-11-21* below.
  - **It starts from what landed:** `MediaIndexStore.listFaceGroups`, the People picker's query
    (3.4); `AlbumRuleClause.People.anchoredAt`, what the picker calls when a person is selected;
    `PeopleAvailability` and `AlbumsViewModel.refreshPeopleAvailability()`; `AlbumUnevaluatableCause`,
    which stops at `AlbumRuleEvaluation`; and the persisted capability record and disclosure flag in
    `face_device_state`, the setting in `app_settings` and `FaceCapabilityChecker` (3.3) — none of
    which anything in `ui/` calls or writes yet.
  - **For the maintainer, before 3.6's picker is built — one real question: the limit on people per
    album**, a product choice (3.6, item 4; *Open questions* D3). **Recommended: 8.** It is the most that always fits the
    16 KiB cap whatever the anchor photos' names — 8 fit with 255-character names escaped
    throughout, 10 do not — so the picker can never build a rule the encoder refuses; and a member
    must show every chosen person (decision 35), so an album of more than eight people in one photo
    is rare. The `require` is caught in `createAutomaticAlbum` as well, as the backstop.
  - **Reserved — not before 3.6**, named so that nobody waits on them:
    - **the disclosure's wording.** ADR-0024's adoption note says its wording and placement are
      intent, not final strings; adoption bound the mechanism, and the Designer finalises both at
      3.6. The record asks no further approval. It is the one privacy statement the app makes, so
      the maintainer may want to read it at 3.6's review;
    - **D1**, the Designer's, not his — unless the answer is a screen of its own, which reopens D2
      (ADR-0013's back stack), answered by him on 2026-11-19 on the premise that it is not;
    - **the translations**, 16 locales beyond English (`LocaleHelper.supportedLanguageTags`).
      `AGENTS.md` requires every string in every shipped locale in the same change and names no
      translator; Stage 2's precedent is that the change writes them and a native-speaker pass
      stays outstanding ([albums-stage-2.md](albums-stage-2.md)). Nothing asks the maintainer to
      supply them; the disclosure is where a native-speaker pass matters most — before a release,
      not before 3.6;
    - rule editing's D6, constrained, not answered; and the Play Data Safety check, before the
      first Play release that ships the Tier C pass.
- **2026-11-21, later — the maintainer answered D3; the Designer designed 3.6; the Planner refined
  it.** D3: at most **8 people in one album**, as recommended, and start 3.6. The Designer answered
  D1 — **a third tab, People, on the New automatic album screen**, so D2 is not reopened — and designed
  the picker, the disclosure, the Settings switch, the notices, the licence row and the Album info
  entry, in a scratch file outside the repository. The Planner copied its decisions, wording drafts,
  string keys, vocabulary proposals and file list into *3.6 — The People criterion, the disclosure
  and the switch* below, which is now the record, split the work into tasks S1, C, S2, A and B, and
  drafted [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md)
  **Amendment 1, Proposed**, for the six points the records reserve for the maintainer or leave open
  — a marker sits under the record's title. D1 and D3 are marked answered under *Open questions*; the
  step row says what waits. No code, no Gradle run, no commit.
- ~~**Next action: the maintainer approves ADR-0024 Amendment 1 and reads the disclosure's wording;
  meanwhile step 3.6's tasks S1 and C.**~~ Done 2026-11-21 — the entry below; its sub-items are kept
  as they were. *3.6 — The People criterion, the disclosure and the switch*
  is the brief: its *Tasks* hold each task's files, exit criteria and mutation checks.
  - **For the maintainer — one approval, with one choice and one reading in it:**
    1. **Amendment 1 as a whole** — §1 OK and Turn off, not dismissable, no run started (recommended
       by both); §3 the app-open check, with *incapable* recorded without loading on a device that
       lists no arm64-v8a, and the start-up deletion at app open (recommended); §4 the switch-off
       order and the memory drop, the whole image memory cache if crops cannot be removed alone
       (recommended); §5's table of what is offered where, an unknown capability hidden on screens and
       available to evaluation (recommended); §6 the Place and Date counts following the chosen people
       (recommended by both);
    2. **the choice at §2** — when face data is switched on and the disclosure was never shown on this
       installation: **A, the disclosure shows** (the Planner's recommendation), or B, switching on
       marks it shown (the Designer's);
    3. **the disclosure's wording, §5** — the app's one privacy statement; the third paragraph, on
       anchors being backed up, recommended kept. Approving it approves task S2's English.
  - **Not for him:** D1, the layout, the notices' wording, the licence row, the vocabulary, the task
    split, and `FaceDataViewModel` with the disclosure at `MainActivity`'s root — each follows a
    precedent `AGENTS.md` names. The translations stay the implementer's, a native-speaker review
    before a release.
  - **Task order:** **S1 → C**, now — Coder, one at a time; neither depends on the amendment. Then,
    after the approval, **S2 → A → B** — A may run beside C if the approval comes while C runs, no file
    being shared. Then **the verification phase, once — it owns the definition of done**, with `adb
    devices` deciding step 4 and the device-only checks listed in *3.6*; then **task P** (Planner; the
    vocabulary's edits to existing entries routed to the Designer).
  - **Read first, for any 3.6 task:** *3.6* whole; the UI vocabulary; ADR-0024 §1 to §6 and Amendment
    1; ADR-0017 Amendment 4; `AGENTS.md` *Strings and localisation*; the test conventions.
- **2026-11-21, later still — tasks S1 and C built and verified; Amendment 1 approved, option A.**
  S1: the 24 S1 keys in all 17 locales, `LocaleResourceParityTest` 11/11. C: the notices by cause
  through one pure mapping (`AlbumStatusNoticeKind`, `albumStatusNoticeKind`) and the People entry in
  the Album info dialog; unit 79 classes, 1310 tests, 0 failures; `FolderListContentTest` 51,
  `AlbumDetailContentTest` 38 and `AlbumInfoDialogTest` 15 pass on the SM-G990B; two planned
  mutations not run (*3.6*, *Tasks*, Task C). The maintainer approved
  [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) Amendment 1 as
  drafted, the disclosure's wording unchanged, and chose **option A at §2** — switching face data on
  when the disclosure was never shown on this installation shows it — in his words "I approve the
  amendment and I choose the option you recommend". The Planner adopted the amendment in the record,
  made *3.6* coherent with option A (the switch-on path does not mark the disclosure shown; Task A's
  criterion 4 and a mutation check say so), added dated pointers in ADR-0022 and ADR-0023 §3, and
  wrote task S2's brief in full. No code, no Gradle run, no commit.
- ~~**Next action: step 3.6, task S2 — Coder: the privacy strings, 18 keys in 17 locales, from the
  approved English.**~~ Done 2026-11-22, and with it A, B, the verification phase and task P — the
  entry below; its sub-items are kept as they were. *3.6*, *Tasks*, Task S2 is the whole brief — keys, final English, the per-locale
  *face data* terms S1 fixed, the rules, the exit criteria and the mutation checks.
  - **Then:** task A (face data: the ViewModel, Settings, the disclosure, the licences, app open); then
    task B (the People picker; after A, and after C, which is done); then **the verification phase,
    once — it owns the definition of done**, `adb devices` deciding step 4 and the device-only checks
    in *3.6* included, the short notices at font scale 2.0 among them; then **task P** (Planner; the
    vocabulary's edits to existing entries — Newer-app notice, Album info dialog — routed to the
    Designer).
  - **Open, none blocking:** the short notices at 74–85 characters in some locales against ~60; the
    native-speaker review, priority the three notices, the *face data* term and, once S2 lands, the
    disclosure (*3.6*, *Open after S1 and C*).
  - **For the maintainer: nothing pending** except reading the translated privacy text before a
    release.
- **2026-11-22 — step 3.6 built and verified: tasks S2, A and B (as B1 and B2), the verification
  phase and task P done.** The privacy strings in all 17 locales; `FaceDataViewModel` with the Face
  data area, the Face licences row, the disclosure at `MainActivity`'s root, the Delete face data
  dialog, the app-open check and start-up deletion, and the no-arm64 answer; the People tab, its
  tiles and face crops, at most 8 people, the counts through the evaluator. **No schema change.**
  **The gate**, on the SM-G990B, after the review's fixes: unit 84 files, 1427 tests, 0 failures;
  `connectedDebugAndroidTest` 334 tests, 0 failures, 0 errors, 3 skipped (the opt-in measurements);
  the merged manifests byte-identical; `app/schemas` unchanged. Two Reviewers, no hard violations;
  their fixes done, three judgement calls left as built. The figures, the dispositions and what is
  not proven are in *3.6 — Built and verified 2026-11-22*; 3.7's list gained items 17 to 24. Task P
  landed `ARCHITECTURE.md` and `AGENTS.md` in an earlier run, and in this one the *Built* notes on
  ADR-0024 Amendment 1, ADR-0017 Amendment 4, ADR-0022 and ADR-0023 §3, three conventions in the
  test instructions (Back on a dialog by key, a harness's copy of a rule, a leftover mutant), this
  plan and [albums.md](albums.md) §7's Stage 3 row. The UI vocabulary's edits to existing entries are
  still the Designer's. Stage 3 stays In progress. No Gradle run by the Planner, no commit.
- ~~**Next action: step 3.7 — device verification and the installed-app walkthrough; then step 3.8,
  the close-out.**~~ Refined 2026-11-22 — the two entries below; its sub-items are kept as they
  were. 3.7's text under *3.2 to 3.8* requires "the definition of done, once, with `adb
  devices` checked and stated; then an installed-app walkthrough including the delete switch, and
  the capability check on the SM-G781B if it can be attached (decision 45)", and its items 1 to 24.
  3.8's requires "`ARCHITECTURE.md`, exit criteria checked against the repository, `/docs-audit`,
  archive" — the stage moves to Complete only then, on the criteria checked against the repository
  ([albums.md](albums.md) §7).
  - **It starts from** the integrated 3.6 change on `feat/album-people`, not yet committed, on top
    of `d0357c7` (step 3.5); 3.6's gate as the baseline (*3.6 — Built and verified 2026-11-22*), its
    manifest md5s included; and `ARCHITECTURE.md` §11, which names what each 3.7 item would settle.
  - **Owner, in order:** the Planner refines 3.7 into a step plan, as 3.2 to 3.6 were — its 24 items
    grouped by phone and by how each is observed without the database leaving the phone (decision 9);
    the Coder runs the device checks and measurements; the Designer makes the vocabulary's edits to
    existing entries (*3.6 — Built and verified*, *Not proven by it*) before 3.8; the Planner does 3.8.
  - **For the maintainer, himself — none of it can be done by an agent:**
    1. **commit 3.6.** As of 2026-11-22, 14 files are untracked — the new `ui/` and `ui/components/`
       files (`FaceDataViewModel`, `PeoplePicker`, `PersonFaceImage`, `FaceCrop`, `FaceDataDialogs`,
       `AlbumStatusNotices`) and their unit and device tests — and 52 tracked files modified: code
       under `app/src/main/java`, the 17 `strings.xml`, tests, `AGENTS.md`, `ARCHITECTURE.md`, four
       ADRs, two instruction files and this plan. Stage the untracked ones explicitly, and compare
       `git diff --stat` with `git diff --ignore-all-space --stat` for line endings;
    2. **attach the second phone, the SM-G781B (A4)**, for items 7, 9 and 14 and 3.7's capability
       check, and a phone without arm64-v8a if one exists, for items 10, 16 and 24 — or say that none
       does;
    3. **run the walkthrough on the installed app** — the disclosure, the switch both ways, the delete,
       a people album. `connectedDebugAndroidTest` uninstalls the app, so reinstall with
       `:app:installDebug` and grant the media permission first (`AGENTS.md`, *Running Gradle*);
    4. **the translated privacy text read and approved before a release** — a native-speaker review,
       priority the disclosure, the Delete face data dialog, the Face data row and the three people
       notices;
    5. **the Play Data Safety check**, before the first Play release that ships the Tier C pass
       (*The Play Data Safety check — recorded 2026-11-18, not answered*);
    6. and, still open from 3.4, whether to add a heap-based cap on one giant component (item 13,
       `ARCHITECTURE.md` §11).
  - **Read first:** 3.7's text and items 1 to 24; *3.6 — Built and verified 2026-11-22*;
    `ARCHITECTURE.md` §11; the test conventions on opt-in measurements and on setting the disclosure
    flag; `AGENTS.md` *Running Gradle*.
- **2026-11-22, later — the SM-G781B's capability check done; step 3.7 refined, 3.8's entry condition
  made explicit.** 3.6 is committed, `fc63620` on `feat/album-people` (checked with `git log`; the
  tree was clean). The second phone was attached: `FaceCapabilityDeviceTest` and
  `FaceDecodeOrientationDeviceTest`, 6 of 6, capable on arm64 as on the SM-G990B — item 9 and ADR-0023
  verify item 7 settled, recorded in that row and in *3.7*, G1. No phone without arm64-v8a is
  available, so the incapable path stays unmeasured on hardware. The Planner refined 3.7 into *3.7 —
  Device verification and the installed-app walkthrough*: every one of items 1 to 24 placed once —
  agent items G1 to G9 with exit criteria, the maintainer's walkthrough W1 to W8 with the delete and
  Turn off paths, known limits L1 to L9 — each with a recommendation; P1, the Data Safety facts, and
  V1, the vocabulary's edits; and four questions for the maintainer. *3.8 — Close-out* now has a
  seven-part entry condition. Stage 3 stays In progress. No code, no Gradle run by the Planner.
- ~~**Next action: step 3.7, G2 — Coder: the device tests, tests only** (*3.7*, (i), G2 is the brief,
  with its file list). Then G3, the definition of done once — the verification phase owns it — and G4
  and G5 on the SM-G990B, `RFCT703FBKR`; then G6, which leaves the disclosure showing for the
  maintainer; then his walkthrough W1 to W7, with the agent at the terminal for G7 to G9. **G2 waits
  on nothing.**~~ Started 2026-11-22 — the two entries below; its sub-items are kept as they were.
  - **For the maintainer, before W1 — none of it blocks G2 to G5:** *Open at 3.7*'s four questions —
    the SM-G781B again for L3; the giant-component cap (L4); the Play Data Safety answered before 3.8
    or carried; the known limits accepted. Still his, before a release and not 3.7's gate: the
    native-speaker review of the translated privacy text.
  - **Owners after the walkthrough:** the Planner records the figures ~~and prepares P1~~ (P1 done
    2026-11-22); the Designer makes V1 (running 2026-11-22); then 3.8, only when its entry condition
    holds.
  - **Read first:** *3.7 — Device verification and the installed-app walkthrough* whole, above all
    its rules; *3.8 — Close-out*'s entry condition; ADR-0023's verify table; ADR-0024 §6 and Amendment
    1 §1 and §2; `ARCHITECTURE.md` §11; the test conventions on opt-in measurements; `AGENTS.md`
    *Running Gradle*; repo memory's notes on `am instrument`, `ANDROID_SERIAL` and waking the phone.
- **2026-11-22, later still — P1 done; ADR-0022's SM-G781B line closed; G2 and V1 running.** The
  Planner drafted the Play Data Safety facts as *P1* under *The Play Data Safety check* — what the face
  pass processes and keeps, that none of it leaves the device or uses the network, no analytics, the
  backup exclusions of the index and `face_device_state`, the deletion switch, the merged permission
  list read from the manifests on disk, and draft answers per Play Console category — **a draft for
  the maintainer to confirm in the console, not an answer**; the exit criterion's status line says
  so. ADR-0022's *To verify* line for the SM-G781B is marked verified in place, dated, on G1's four
  orientation tests. No code, no Gradle run by the Planner. **On disk at this entry, checked with
  `git status`:** HEAD is `fc63620` (step 3.6); uncommitted are this plan (3.7 and 3.8's refinement,
  P1, this handoff), ADR-0022 and ADR-0023's verify row 7 — and, **running at this time**, G2's
  device tests (the Coder; new files under `androidTest/.../data/` and edits to
  `FaceDataDialogsTest.kt` and `NewAutomaticAlbumContentTest.kt`) and V1's edits to the UI
  vocabulary (the Designer). Neither is finished or verified; their reports say what landed.
- ~~**Next action: G2 finishes — Coder, tests only** (*3.7*, (i), G2); **then G3, the definition of
  done once over G2 — the verification phase owns it**, `adb devices` stated, the merged-manifest
  md5s and `git status app/schemas` checked. Then G4 and G5 on the SM-G990B, `RFCT703FBKR`; then G6,
  which leaves the disclosure showing; then the maintainer's walkthrough W1 to W7, the agent at the
  terminal for G7 to G9. **V1 finishes beside G2** — the Designer, the vocabulary file only, so no
  file is shared with G2. **P1 is done.**~~ Done 2026-11-22, G2 to G6 — the two entries below; its
  sub-items are kept as they were.
  - **For the maintainer, before W1 — none of it blocks G2 to G5:** *Open at 3.7*'s four questions,
    question 3 now with P1's draft to answer from. Still his, before a release: the native-speaker
    review of the translated privacy text.
  - **Owners after the walkthrough:** the Planner records the figures in *3.7*; then 3.8, only when
    its entry condition holds — V1 and P1 being two of its parts.
  - **Read first:** as in the struck entry above.
- **2026-11-22, latest — G2 to G6 done on the SM-G990B; two production fixes; three new known
  limits.** G2's device tests landed — four new classes and a shared throwaway index under
  `androidTest/.../data/`, and methods in `FaceDataDialogsTest` and `NewAutomaticAlbumContentTest`.
  **G3, the definition of done, ran once** over the integrated change: unit 84 classes, 1427 tests,
  0 failures, after a forced `cleanTestDebugUnitTest` so that the parity test re-read `res/`;
  `connectedDebugAndroidTest` 346, 0 failures, 0 errors, 6 skipped by design; the merged manifests'
  md5s and `app/schemas` unchanged. **The production diff of 3.7 so far:** `Core.setErrorVerbosity(false)`
  in `OpenCvFacePipeline.load()` (G5a), and German `automatic_album_section_people` "Personen" →
  "Leute" (G4, item 17). G4 to G6 are recorded under *3.7* with their figures. **Findings:** the
  SM-G990B's index runs `wal` with `normal` locking, so ADR-0018 Amendment 6's trigger did not fire;
  a favourite toggle keeps Tier C, as Amendment 6 §1 expected; `ARCHITECTURE.md` §11's grouping
  capacity was wrong — about 31,000 faces after a full-budget face pass, not 37,000 (L11); a native
  OpenCV error still prints one `org.opencv.imgproc` line (L10); the download total is 49,712,393 B,
  287,607 B under 50,000,000 B if the total is what is bounded (L12). **The disclosure is showing on
  the SM-G990B**, for W1. The Planner updated this plan, `ARCHITECTURE.md` §11, ADR-0023 (rows 5 and
  6, §5 and its *Built* note) and ADR-0024 §6, in place and dated; no code, no Gradle run by the
  Planner. **On disk, checked with `git status`:** HEAD `fc63620`; uncommitted, the G2 tests, the two
  fixes, the vocabulary file (V1's), ADR-0022, ADR-0023, ADR-0024, `ARCHITECTURE.md` and this plan.
  *Corrected 2026-11-22, by a later Planner run:* ADR-0023's row 5 and ADR-0024 §6's note were not
  yet on disk when this entry was written; that run added both, dated. ~~ADR-0023's row 6 waits for G7,
  and its *Built* section's last bullet, on `cv::error()`, is still unamended.~~ *Both done since:* the
  *Built* bullet's dated note on 2026-11-23, and row 6 from G7 on 2026-11-23.
- ~~**Next action: the maintainer's walkthrough, W1 to W7, on the SM-G990B** (*3.7*, (ii)), starting
  from the disclosure G6 left showing, **with an agent at the terminal** for G7 (during W3), G8
  (during W4) and G9 (after W5). He says "W3 done" and so on at each step, so that the agent takes its
  reading at the right moment; a step that fails stops the walkthrough there. W8 is optional.~~
  Unchanged on 2026-11-23, and restated with G10 in the second entry after this one; the sub-items
  below are kept as they were, and their *For the maintainer* item is answered in the entry of
  2026-11-23.
  - **State on the phone:** the debug app installed on the SM-G990B (`RFCT703FBKR`) by G6, the media
    permissions granted, and the disclosure showing — neither OK nor Turn off pressed, no index run
    started.
  - **Nothing reinstalls the app until W7 is done:** `connectedDebugAndroidTest` uninstalls it and
    wipes its data. G7 to G9 use logcat, `run-as` and `am instrument` only. A defect found is fixed
    by a Coder task of its own, checked again at the step that found it, and G3 then runs once more
    over the final tree — after W7.
  - **For the maintainer — *Open at 3.7*, questions 1 to 5**, none blocking W1: the SM-G781B again
    (L3); the giant-component cap and, new, grouping's 31,000-face capacity, with four options (L4,
    L11); the Play Data Safety answer, from P1; L1 to L12 accepted; and, new, the
    `org.opencv.imgproc` line (L10). Still his before a release: the native-speaker review — with
    "Leute" now on its list.
  - **Still open among the agent items:** ~~G2's planned mutation per regular test is not in the
    reports the Planner has; the Coder states it, or runs it, before 3.8.~~ G2's planned mutation per
    regular test is **deferred until after W7** — it needs a connected run, which would wipe the
    walkthrough's state — and is **M1** in 3.8's entry condition: the Coder runs it after W7. ~~V1 is on disk, uncommitted;~~
    *V1 is committed, in `94f8e9c` (2026-11-23);*
    its report is not part of this entry, and 3.8's entry condition 5 checks it.
  - **Then:** the Planner records W1 to W7 and G7 to G9 in *3.7*, and ADR-0023's row 6; the Coder runs
    M1; then 3.8, only when its ~~seven-part~~ eight-part entry condition holds.
  - **Read first:** *3.7* whole — its rules, G7 to G9, (ii) and (iii); *3.8*'s entry condition;
    ADR-0024 §6 and Amendment 1 §1 and §2; the UI vocabulary's *Settings screen* and *New automatic
    album screen*; `AGENTS.md` *Running Gradle*; repo memory on `am instrument`, `run-as`,
    `clear_refs` and waking the phone.
- **2026-11-23 — the maintainer's answers to *Open at 3.7* recorded; nothing run.** Question 1: yes,
  L3 runs on the SM-G781B if he attaches it and its owner agrees — **G10**, after W7 and M1, accepted
  to run and not yet run; if it is not attached, L3 falls back to accepted. Question 2: (a) — L11 a
  recorded limit, revisited on a real report; L4 debt, with no heap-based cap. Question 4: L1 to L12
  accepted, so 3.8's entry condition 3 is met. Question 5: L10 a recorded limit, no precondition
  checks. Question 3: his position on the Data Safety form recorded under *The Play Data Safety
  check*, resolving P1's points 1 and 2 — his reading, not quoted from Play's help text, which the
  Planner never read; **points 3 and 4 open and the form not submitted**, so entry condition 6 is not
  met. Also: ADR-0023's *Built* note on `cv::error()` and ADR-0024 §6's L10 note, dated; W8's note on
  item 18; [albums.md](albums.md) §8 decision 9's Data Safety pointer; `ARCHITECTURE.md` §11's L4, L10
  and L11 entries marked accepted. Planner, docs only; no code, no
  Gradle run. **On disk, by `git status`:** HEAD `fc63620`; uncommitted, the G2 tests, the two fixes,
  V1's vocabulary file, ADR-0022 to ADR-0024, `ARCHITECTURE.md`, [albums.md](albums.md) and this plan.
- ~~**Next action: still the maintainer's walkthrough, W1 to W7, on the SM-G990B** (*3.7*, (ii)), from
  the disclosure G6 left showing, **with an agent at the terminal** for G7 (during W3), G8 (during W4)
  and G9 (after W5). He says "W3 done" and so on at each step; a step that fails stops the walkthrough
  there. W8 is optional, best done right after W5 (its note). **Then M1** — the Coder, a connected run
  on the SM-G990B. **Then G10** — L3 on the SM-G781B, only if the maintainer attaches it and its owner
  agrees, and with the SM-G990B detached. **Then 3.8**, only when its eight-part entry condition holds.~~
  Done 2026-11-23 — the entry below; the sub-items are kept as they were.
  - **State on the phone, as far as this plan knows:** the debug app on the SM-G990B (`RFCT703FBKR`),
    installed by G6, the media permissions granted, the disclosure showing — neither OK nor Turn off
    pressed, no index run started.
  - **Nothing reinstalls the app until W7 is done.** `connectedDebugAndroidTest` — M1 and G10
    included — uninstalls it and wipes its data. G7 to G9 use logcat, `run-as` and `am instrument`
    only. A defect found is fixed by a Coder task of its own, checked again at the step that found it,
    and G3 then runs once more over the final tree.
  - **For the maintainer:** question 3 — say that the form is submitted, with points 3 and 4 settled,
    or declare it carried; until then 3.8's entry condition 6 does not hold. Whether to attach the
    SM-G781B for G10. Still his before a release: the native-speaker review, "Leute" on its list.
  - **Then:** the Planner records W1 to W7, G7 to G9, M1 and G10 in *3.7*, and ADR-0023's row 6.
  - **Read first:** *3.7* whole — its rules, G7 to G10, (ii) and (iii); *3.8*'s entry condition;
    *The Play Data Safety check*; ADR-0024 §6 and Amendment 1 §1 and §2; `AGENTS.md` *Running
    Gradle*; repo memory on `am instrument`, `run-as`, `clear_refs`, `ANDROID_SERIAL` and waking the
    phone.
- **2026-11-23, later — step 3.7's device work done: W1 to W7, G7 to G9, M1, and G10 in part;
  question 3 carried; two new limits.** Recorded under *3.7* from the maintainer's and the Coder's
  reports; the Planner ran nothing. The maintainer had committed 3.7's tests, fixes and documents as
  `94f8e9c` the same day. **Results:** W1 to W7 done, W8 skipped by his decision. G7 — the real
  worker after the real disclosure: 1,002 images in 254.2 s, **253.7 ms per image with the screen
  off**, about 2.25 times 3.4's figure, so **about 1,655 images per 7-minute run on this phone, not
  2,800**; ADR-0023's row 6 amended. G8 — the disk cache unchanged, within a stated limit;
  People-tab jank 8.30 % against Folders View's 3.52 %, no stutter seen, a polish candidate not
  routed; 2 of 104 crops not recognisable. G9 — after a real deletion, `wal`, `normal`,
  `secure_delete` 1, no face data. M1 — all nine regular new tests caught a named mutation, every
  mutation reverted. G10 on the SM-G781B — capability and item 1's modes recorded (`wal`, `normal`);
  the release build not checked, no signing key; the face pass stopped at thermal status 2; closed by
  the maintainer as option A, L3 accepted as it stands. **Decisions:** question 3 carried past 3.8
  (option B), which meets entry condition 6; "Leute" approved (option A), still on the
  native-speaker review list. **New, for the maintainer:** L13, no thermal back-off; L14, the worker
  measurement test's permission race (question 6); W4's count comparison not reported (question 7).
  Also corrected: this handoff's V1 and row-6 lines, ADR-0023's row 7, and `ARCHITECTURE.md` §11's
  journal-mode and face-data UI entries. Planner, docs only; no code, no Gradle run, no phone.
  **On disk, by `git status`:** HEAD `94f8e9c`; uncommitted, this plan, ADR-0023 and
  `ARCHITECTURE.md`.
- ~~**Next action: the maintainer** — *Open at 3.7*, question 6 (L13 and L14: accepted or not) and
  question 7 (W4's count on a device); then commit this run's documents. **Then the verification
  phase** runs the definition of done once over the committed tree, with `adb devices` stated —
  entry condition 4, which G3 no longer covers (its note). **Then the Planner** checks the eight
  entry conditions against the repository and starts 3.8 only if all hold.~~ Questions 6 and 7
  answered and the gate started, the entry below.
  - ~~**Read first:** *3.8 — Close-out*; *3.7*'s (iii) and *Open at 3.7*; `AGENTS.md` *Definition of
    done* and *Running Gradle*.~~
- **2026-11-23, later still — *Open at 3.7* questions 6 and 7 answered; the gate running.** Question
  6: **A** — L13 and L14 accepted, L13 revisited on a real report or a user complaint, L14's
  test-side fix deferred; entry condition 3 now holds for L1 to L14. Question 7: **A**, done by the
  maintainer by hand on the SM-G990B after a fresh debug install and a fresh face index (OK on the
  disclosure, Index now, the People tab): one automatic album created from people tiles, the New
  automatic album screen's match count and the album's member count the same. The figures, the tiles
  and any place or date mix were not reported to the Planner. Recorded under W4 and against the exit
  criterion *Count consistency*, whose wording a hand observation meets; no device test covers people
  counts, a gap accepted with option A. Entry condition 2's W4 caveat is closed. Also brought up to
  date: `ARCHITECTURE.md` §11 — L13 and L14 added, L12 marked accepted, and the face-data UI entry's
  People tab label, Face licences dialog and outside-tap clauses struck as settled by G4, G6, W1 and
  W2; this plan's L12 annotation, and L13's "production" corrected — Index now has no charging or
  idle constraint, which leaves the acceptance as it was. Planner, docs only; no code, no Gradle run,
  no phone. **On disk, by `git status` at the start of this run:** HEAD `94f8e9c`; uncommitted, this
  plan, ADR-0023 and `ARCHITECTURE.md` from the previous run, which this run edited again. **Running
  at this time:** the verification phase's definition-of-done gate over the committed tree — entry
  condition 4; its result is in its own report and not claimed here.
- ~~**Next action: record the gate, then review, then commit, then 3.8.**~~ *Items 1 and 2 done
  2026-11-23, the entry below; the list is kept as it was.* In order:
  1. **The gate's result** — entry condition 4. The next session reads the verification phase's
     report and **records the gate's figures** in condition 4's annotation (*3.8 — Close-out*,
     *Entry condition*). If it failed, the fix is a task of its own and the gate runs again.
  2. **The Reviewer's pass over the 3.7 change** — not yet run. A finding that changes code or tests
     sends the gate round again, so condition 4 is re-recorded.
  3. **The maintainer's commit** of the documents on disk — entry condition 7.
  4. **Then the Planner** checks the eight entry conditions against the repository and starts 3.8
     only if all hold.
  - **Nothing is open for the maintainer on *Open at 3.7*:** all seven questions are answered. His
    parts before 3.8 are the commit, and the Reviewer's findings if any need his decision. Carried
    past 3.8 and still his before the first Play release that ships the face pass: question 3's Play
    Data Safety points 3 and 4 and the submission, and the native-speaker review, "Leute" on its list.
  - **Read first:** *3.8 — Close-out*; *3.7*'s (iii); `AGENTS.md` *Definition of done*.
- **2026-11-23, latest — the gate recorded; the Reviewer's pass applied to the documents.** The
  gate over `94f8e9c` is recorded in 3.8's entry condition 4: unit and compile green; the first
  connected run failed on two timeouts in `MediaIndexingWorkEnqueueTest`, a worker test 3.7 did not
  touch, and the second was green — recorded as an intermittent flake. The Reviewer approved 3.7 with
  changes; *Review of 3.7* records its test findings and what was and was not changed, and its
  documentation findings are applied, dated, here, in `ARCHITECTURE.md` §6 and §11, ADR-0022's
  *To verify*, ADR-0023 §5 and rows 5 and 6, ADR-0024 §6 and [albums.md](albums.md) §7 and §8
  decision 9. Corrected among them: about 1,600 images per run, not 1,655; L11's 31,000 not reduced
  by Tier A. Planner, docs only; no code, no Gradle run, no phone. **On disk at this entry:** HEAD
  `94f8e9c`; uncommitted, these documents and — **being applied at this time** by the Coder — the
  review's fixes to four test files under `app/src/androidTest`, not verified.
- ~~**Next action: the gate once more, then the commit, then 3.8.**~~ *Item 1 done 2026-11-23, final,
  the entry below; the list is kept as it was.* In order:
  1. **The verification phase runs the definition of done once over the final tree**, after the
     Coder's review fixes, with `adb devices` stated, and its figures are recorded in 3.8's entry
     condition 4.
  2. **The maintainer commits** the documents and the test fixes — entry condition 7, checked with
     `git status`.
  3. **The Planner** checks the eight entry conditions against the repository and starts 3.8 only
     if all hold.
  - **Read first:** *3.8 — Close-out*; *Review of 3.7*; `AGENTS.md` *Definition of done*.
- **2026-11-23, final — the final gate and the re-review recorded.** The definition of done ran once
  over the final tree — HEAD `94f8e9c` plus the Reviewer's four test fixes and the documents — on the
  SM-G990B, the only device attached, and passed: 84 classes and 1427 unit tests, 28 classes and 346
  connected test cases, 0 failures, 0 errors, the same six opt-in skips; `MediaIndexingWorkEnqueueTest`
  3 of 3, the first gate's flake not recurring; the merged manifests and `app/schemas` unchanged;
  `git diff HEAD -- app/src/main` empty. Recorded in 3.8's entry condition 4, now met, with the
  earlier gate history kept there; G3's note annotated. The Reviewer re-reviewed the four fixes:
  **approve**, 0 blocker, 0 major, 0 minor; its four optional cleanups **deferred**, with the reason,
  under *Review of 3.7*, and the earlier findings marked fixed there. The lines above that say the
  gate must run again, and the 1,655 figure in the 2026-11-23 *later* entry, are dated history and
  left as they were. Also: [albums.md](albums.md) §7's Stage 3 row. Planner, docs only; no code, no
  Gradle run, no phone. **On disk, by `git status` at this entry:** HEAD `94f8e9c`; uncommitted, the
  four test files and the six documents listed under *Next action* below.
- **Next action: the maintainer's commit, then 3.8.** In order:
  1. **The maintainer commits** exactly these ten files, and nothing under `app/src/main` — there is
     no change there. Its code and tests are the tree the final gate ran over; only documents were
     edited after it, so a code or test edit before the commit would send the gate round again.
     - `app/src/androidTest/java/com/davide/seddio/easygallery/ui/components/FaceDataDialogsTest.kt`
     - `app/src/androidTest/java/com/davide/seddio/easygallery/data/FaceNativeErrorLogDeviceTest.kt`
     - `app/src/androidTest/java/com/davide/seddio/easygallery/data/MediaIndexTierCScaleDeviceTest.kt`
     - `app/src/androidTest/java/com/davide/seddio/easygallery/ui/NewAutomaticAlbumContentTest.kt`
     - `docs/architecture/ARCHITECTURE.md`
     - `docs/architecture/decisions/0022-people-criterion-anchored-to-a-photo.md`
     - `docs/architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md`
     - `docs/architecture/decisions/0024-face-data-disclosure-and-delete-switch.md`
     - `docs/design/albums-stage-3.md`
     - `docs/design/albums.md`
  2. **Then the Planner** checks the eight entry conditions against the repository — `git status`,
     `git log` and `git ls-files`, not this document — and, only if all hold, starts 3.8's steps in
     order. Condition 7 is the one open.
  - **Nothing else is open for the maintainer before 3.8.** The four deferred cleanups are optional
    and not a condition. Carried past 3.8 and still his before the first Play release that ships the
    face pass: question 3's Play Data Safety points 3 and 4 and the submission, and the native-speaker
    review, "Leute" on its list.
  - **Read first:** *3.8 — Close-out*, its entry condition and its steps; *Review of 3.7*;
    `AGENTS.md` *Documentation*.
- ~~**Next action: step 3.5 — the `people` clause and evaluation.** Owner: the Planner refines it into
  a step plan, as 3.2 to 3.4 were; then the Coder builds it.~~ Refined 2026-11-21 — the *Next action:
  step 3.5, Task 1* entry above.
  **Nothing is pending for the maintainer
  before the refinement** — 3.5's records leave no question open to him (below).
  - **3.5's own text**, under *3.2 to 3.8*, says only: "Parse, encode and evaluate, with strictness
    tests like Stage 2's; combined people + region + time, with tests." *3.4*, *What 3.4 produces*,
    adds: "**Left to 3.5:** the `people` clause, building an anchor from the representative
    candidate, and evaluation — resolving an anchor by IoU to a face and its group (ADR-0022 §3), and
    the per-image projection evaluation needs."
  - **It starts from what 3.4 landed:** `MediaIndexStore.listFaceGroups`, the People picker's query,
    whose representative candidate carries exactly an anchor's fields;
    [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) §1 (the
    anchor: the photo's media identity, the box as upright fractions, the pipeline version, nothing
    else) and §3 (resolution: gone, waiting, IoU ≥ 0.5, no face at the anchored position);
    [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4
    (one `people` clause per person, each with one anchor; its parser strictness; settled and
    unsettled, a video or GIF a settled no-match; the two causes of unevaluatable found at
    evaluation; never rewritten); [albums.md](albums.md) §8 decisions 41 and 42 (an anchor that
    cannot be followed: a pending photo is waiting, a deleted photo or no face at the position is
    unevaluatable) and 46 (IoU ≥ 0.5, the best overlap wins, faces under 40 px never candidates).
  - **Left by those records to 3.5 itself, the Planner's to settle in the refinement, not the
    maintainer's:** the deterministic tie-break between equal overlaps (ADR-0022 §3 step 4: "a rule
    step 3.5 fixes"); and how the evaluator reports which cause of unevaluatable applies — "Whether
    that status gains a sibling or carries a cause is 3.5's choice" (ADR-0017 Amendment 4), whose
    `AlbumEvaluationStatus.UNEVALUATABLE` doc comment becomes too narrow either way. The notices'
    wording is 3.6's, with the People criterion's layout (D1, the Designer's).
  - **Open, but not before 3.5:** a heap-based cap on one giant component, open for the maintainer
    in `ARCHITECTURE.md` §11 — grouping's, not the clause's; and rule editing's D6, the order
    against 3.6, which ADR-0017 Amendment 4 constrains and does not answer.
  - **Read first, on starting 3.5:** *3.4 — Built and verified 2026-11-21* below; ADR-0022 §1, §3
    and §4; ADR-0017 Amendments 1, 2 and 4 (Stage 2's strictness, and the people clause);
    [albums.md](albums.md) §8 decisions 35, 41, 42 and 46; ADR-0018 Amendment 5 (a pending image).
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
| **3.4** | Clustering and the person record | ~~⬜ Not started~~ ~~🔶 In progress — refined 2026-11-20 into *3.4 — Clustering and the person record*;~~ ~~ready to build: no schema change, so no amendment to approve; Task 1 waits on nothing, Task 2 on the maintainer's acceptance of the budget~~ ~~Task 1 ✅ built and verified 2026-11-20; the budget accepted, components first~~ ~~and ADR-0018 Amendment 8 adopted the same day (decision 50), so there is a schema change after all — `Migration(6, 7)`, in Task 3; Task 2 next, waiting on nothing~~ ~~adopted the same day (decision 50); Task 2 ✅ built and verified 2026-11-20, and on its measurement ADR-0018 Amendment 8 reversed before it was built (decision 50, amended) — no schema change, the index stays at version 6; Task 2b next, waiting on nothing~~ ✅ Built and verified 2026-11-21 — refined 2026-11-20; the budget accepted with components first (decision 50); ADR-0018 Amendment 8 adopted and reversed before it was built (decision 50, amended), so no schema change and the index stays at version 6; Tasks 1, 2, 2b, 3, 4, 5 and task P done; the gate and the device results are under *3.4 — Built and verified 2026-11-21* |
| **3.5** | The `people` clause and evaluation | ~~⬜ Not started — refined 2026-11-21 into *3.5 — The people clause and its evaluation*, ready to build: Tasks 1 to 3, sequential, then verification and task P; nothing waits on the maintainer~~ ✅ Built and verified 2026-11-21 — refined the same day; Tasks 1, 2, 3, the verification phase and task P done; no schema change in either database, no string; the gate and the review's dispositions are under *3.5 — Built and verified 2026-11-21* |
| **3.6** | The UI: the People criterion with its thumbnails, the disclosure and the delete switch | ~~⬜ Not started — refined 2026-11-21 into *3.6 — The People criterion, the disclosure and the switch*, waiting on the maintainer's approval of ADR-0024 Amendment 1 for tasks S2, A and B; tasks S1 and C ready to build.~~ ~~🔶 In progress — refined 2026-11-21 into *3.6 — The People criterion, the disclosure and the switch*, approved 2026-11-21 (ADR-0024 Amendment 1, option A at §2): **S1 and C built and verified; S2, A and B ready to build**, S2 next.~~ ✅ Built and verified 2026-11-22 — refined and approved 2026-11-21 (ADR-0024 Amendment 1, option A at §2); tasks S1, C, S2, A and B, the verification phase and task P done; no schema change in either database; the gate, the review's dispositions and what is not proven are under *3.6 — Built and verified 2026-11-22*. D1 answered by the Designer (a third tab), D3 by the maintainer (8 people) |
| **3.7** | Device verification and installed-app walkthrough | ~~⬜ Not started — next~~ ~~🔶 In progress~~ ✅ Done 2026-11-23 — its final gate and the re-review are at the end of this cell; its commit is 3.8's entry condition 7, the maintainer's. Refined 2026-11-22 into *3.7 — Device verification and the installed-app walkthrough*: agent items G1 to G9 (G10 added 2026-11-23), the maintainer's walkthrough W1 to W8, known limits ~~L1 to L9~~ ~~L1 to L12~~ L1 to L14. **G1 ✅ done 2026-11-22** — the capability check on the SM-G781B (ADR-0023 verify item 7); ~~G2 next~~ **G2 to G6 ✅ done 2026-11-22** — G2's planned mutation check deferred until after W7 as M1; **the maintainer's walkthrough W1 to W7 next**, with G7 at W3, G8 at W4 and G9 after W5. **2026-11-23:** *Open at 3.7* answered — L1 to L12 accepted; L3 accepted to run as **G10**, after M1; question 3's position given, the form not submitted. **2026-11-23, later:** W1 to W7 ✅ (W8 skipped by the maintainer), G7 to G9 ✅, M1 ✅, G10 🔶 partial and closed as option A; question 3 carried past 3.8 (option B); new limits L13 and L14 pending his acceptance; the definition of done to run again over the committed tree. **2026-11-23, later still:** questions 6 and 7 answered (A, A) — L13 and L14 accepted, W4's count observed equal by hand on the SM-G990B; the definition of done running over the committed tree, its figures to be recorded under 3.8's entry condition 4. **2026-11-23, latest:** the gate ran over `94f8e9c` — the first connected run failed on two worker-test timeouts, recorded as an intermittent flake, and the second was green; the Reviewer approved with changes (*Review of 3.7*); its test fixes are in the working tree, so ~~**the gate runs once more over the final tree**~~ the gate runs once more over the final tree (3.8's entry condition 4). **2026-11-23, final:** the gate ran once more over the final tree — `94f8e9c` plus the four test fixes and the documents — and passed, the flake not recurring; the Reviewer re-reviewed the fixes and approved, four optional cleanups deferred (*Review of 3.7*). Nothing of 3.7 is left but the maintainer's commit |
| **3.8** | Close-out: `ARCHITECTURE.md`, vocabulary, audit, archive | ⬜ Not started — starts only when the ~~seven~~ eight conditions of *3.8 — Close-out*, *Entry condition*, hold. *2026-11-23, final:* 1 to 6 and 8 met; 7 waits on the maintainer's commit |

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
  check* below; not answered.** *2026-11-23: the maintainer's position given there; the form not yet
  submitted.*
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

*Carried past 3.8 by the maintainer, 2026-11-23 (option B) — the* Carried *bullet at the end of this
section. The heading is kept as it was, so that links to it hold.*

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
  *(2026-11-20: OpenCV merged in nothing — verify item 4. The list as it stands is in P1 below.)*
- **Who.** The maintainer, who alone has Play Console access, fills in the form. The Planner
  prepares the facts at step 3.7 — what the face path reads, writes, keeps and backs up, and the
  merged permission list after OpenCV lands. *(Prepared 2026-11-22 — P1 below.)*
- **When.** Before the first Play release that ships the Tier C pass, together with the declaration
  owed from Stage 2. Its answer is recorded here and in [albums.md](albums.md) §8, closing the exit
  criterion below.

**P1 — the facts for the form, drafted 2026-11-22 by the Planner. A draft for the maintainer to
confirm in the Play Console, not an answer.** Each fact was checked against the repository on that
date, as cited. Play's own definitions were looked for again the same day and were again behind
collapsed sections a page fetch cannot open; the Android developer guide to the form's categories
(*Review how your app collects and shares user data*) was read, and its category names are used
below. So every draft answer rests on reading *collected* as *transmitted off the device* — the
first thing the maintainer confirms in the console's help text.

- **What the face pass processes, on the device only.** Images the app can already read through
  `MediaStore` — not videos, not GIFs (E2) — decoded upright in the app's process, only when face
  data is on, the disclosure has been answered and the capability check passed on arm64-v8a
  (ADR-0023 §3, ADR-0024 §1 and §2). It keeps, in `media_index.db` (schema `6.json`): per face of 40
  px or more, the photo's content URI, a box as four fractions, a 128-value embedding and a group id,
  each with its pipeline or grouping version (`media_index_face`); per image, an outcome
  (`media_index_face_state`) and a retry count (`media_index_face_attempt`). The People picker's
  crops are cut from the photo at display time and never reach Coil's disk cache
  (`ui/components/PersonFaceImage.kt`, `CachePolicy.DISABLED`; ~~G8 checks it on the device~~
  *checked at G8, 2026-11-23:* the policy holds; the device listing could not show a write, Coil's
  disk-cache directory never having existed).
  `face_device_state` holds whether the disclosure was shown and the capability record;
  `app_settings` holds the switch, one boolean.
- **None of it leaves the device, and the face pass uses no network.** The models are in the APK
  (`assets/face/`) and OpenCV is linked into it; nothing is downloaded. The face path makes no
  network call (ADR-0024 §6, checked by review); the merged manifest's `INTERNET` comes from a
  library and the tip jar, not from the face path. No analytics, crash-reporting or advertising SDK
  is a dependency (`gradle/libs.versions.toml` and `app/build.gradle.kts`, read 2026-11-22). The
  face path logs only `FaceIndexLogLines`' fixed lines, counts and no face data, enforced by a unit
  test — and logcat stays on the device in any case. ~~**Open:** whether native OpenCV prints under
  `cv::error()` is G5a's.~~ *Observed 2026-11-22 (G5a):* a native OpenCV error prints one line
  outside those fixed lines, under `org.opencv.imgproc` — OpenCV's assertion text and source path,
  for the assertion provoked no face data; accepted 2026-11-23 as known limit L10.
- **Excluded from backup.** `media_index.db` with its `-wal`, `-shm` and `-journal`, and
  `face_device_state.xml`, are excluded in all four sections — `backup_rules.xml`, and
  `data_extraction_rules.xml`'s cloud backup, device transfer and cross-platform transfer (read
  2026-11-22). **Backed up, and not face data** (decision 9's clarification): the album database's
  anchors — a photo's URI, file name, size and modification time, a box as four fractions and a
  pipeline version (ADR-0022 §1) — and the switch in `app_settings`.
- **The deletion switch.** Settings' Face data switch, after the Delete face data dialog confirms,
  stops Tier C work and deletes every Tier C row in one transaction with `secure_delete` on, then
  truncates the write-ahead log; it says when that scrub could not finish, and repeats the deletion
  at every app open while face data is off (ADR-0024 §3 and Amendment 1 §3 and §4;
  `MediaIndexStore.deleteAllFaceData`). Turn off on the disclosure stops it before anything is
  stored. Uninstalling the app or clearing its storage removes everything.
- **The merged permission list,** read from the debug and release merged manifests on disk on
  2026-11-22, md5s equal to 3.6's (`8856dbd4…`, `043a521e…`; G3 re-checks them over the final tree):
  `READ_EXTERNAL_STORAGE` (to API 32), `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`,
  `ACCESS_MEDIA_LOCATION`, `MANAGE_MEDIA`, `com.android.vending.BILLING`, `INTERNET`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and the app's own
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; `<queries>` for the wallpaper cropper and Play
  Billing's two services. **The face pass added none of them.**

*Draft answers, per category, for the face pass only:*

| Play Console category | What the face pass touches | Draft answer |
|---|---|---|
| Data collection — "collects or shares any required user data types?" | — | **No, for the face pass.** The app-wide answer also covers the tip jar, location and backup — the Stage 2 declaration |
| Photos and videos — Photos | reads images on the device | not collected, not shared: processed on the device only |
| Personal info — Other info | embeddings and groups derived from faces; the guide names no face or biometric type | not collected, not shared: stored on the device only, deletable by the switch |
| Location | nothing; `ACCESS_MEDIA_LOCATION` is the place index's (Stage 2) | none from the face pass |
| App activity; Web browsing | nothing | none |
| App info and performance — Crash logs, Diagnostics | fixed log lines that stay on the device; no SDK | not collected |
| Device or other IDs | nothing | none |
| Financial info — Purchase history | nothing; the tip jar is Google Play Billing's | none from the face pass; Stage 2's declaration |
| Security practices — encrypted in transit; deletion request | asked only when something is collected (Play's form, step 5) | not applicable to face data. If asked for another type, the in-app deletion for face data is the Face data switch |

**For the maintainer to confirm in the console, beyond the table:**
1. that Play's *collected* means transmitted off the device, so on-device processing is not
   collection — decision 9's "generally", still unread at its source; ✅ *resolved by the maintainer
   2026-11-23 — his position below;*
2. whether Android Auto Backup of the album database, anchors included, counts as collection — the
   Help article's FAQ on data a user uploads to Google Drive asks it, and was not readable. It
   predates Stage 3: the album database has been backed up since ADR-0014; ✅ *resolved by the
   maintainer 2026-11-23 — his position below;*
3. the app-wide answers for what P1 does not cover — the tip jar, `ACCESS_MEDIA_LOCATION`, Stage 2's
   declaration; **still open 2026-11-23;**
4. that the privacy policy Settings links to, hosted outside this repository, describes on-device
   face grouping and the switch. Not checked here. **Still open 2026-11-23.**

~~**Not answered.** It closes when the maintainer submits the form and the outcome is recorded here and
in [albums.md](albums.md) §8 — or he declares it carried (*Open at 3.7*, question 3).~~ *Superseded
2026-11-23 by the record below; the closing rule is unchanged.*

**The maintainer's position — 2026-11-23, his resolution of points 1 and 2.** In his words:

> Neither feature contradicts a no-data-collected-or-shared declaration provided the ML library sends
> no hidden telemetry and the backup rules are clean. (1) On-device face detection, embedding and
> grouping is not collection: Play defines Collect as transmitting data off the device and treats
> on-device access and processing as out of scope; third-party SDKs may send diagnostics, which would
> count. (2) Android Auto Backup of the album database is not collection (OS-managed, stored in the
> user's own Google Drive, end-to-end encrypted on Android 9+ with the screen lock, and E2EE data
> unreadable by the developer is exempt) and not sharing (not developer-initiated third-party
> transfer).

- **Its source.** The maintainer supplied it, as his reading of Play's definitions. It is **not**
  quoted from Play's help text: the Help article's definitions were never readable to the Planner
  (behind collapsed sections on 2026-11-18 and again on 2026-11-22), and the Planner did not re-read
  any source text for this record. What he confirms it against, when he submits, is the Play
  Console's own help text.
- **Its two provisos, against the repository** — checked by the Planner on 2026-11-23 unless cited:
  - *The ML library sends no hidden telemetry.* No analytics, crash-reporting or advertising SDK is
    among the dependencies in `gradle/libs.versions.toml`. OpenCV merged in no permission — the
    merged manifests were byte-identical before and after it (ADR-0023, verify item 4; P1 above) —
    and the face path makes no network call (ADR-0024 §6, by review). What this does not prove: the
    app holds `INTERNET`, for Coil's network loader and the tip jar, so the permission list alone
    cannot rule out a native library using it. None is known to; no traffic capture was made.
  - *The backup rules are clean.* `media_index.db` with its `-wal`, `-shm` and `-journal`, and
    `face_device_state.xml`, are excluded in all four sections — `backup_rules.xml`, and
    `data_extraction_rules.xml`'s cloud backup, device transfer and cross-platform transfer. What is
    backed up — the album database's anchors, and the switch in `app_settings` — is not face data
    (decision 9's clarification; ADR-0022 §1).
- **Points 1 and 2: resolved by the maintainer, 2026-11-23.** **Points 3 and 4 are still open, and
  the form is not yet submitted.**
- **State: position given 2026-11-23; submission and points 3–4 outstanding.** It closes when the
  maintainer submits the form and the outcome is recorded here and in [albums.md](albums.md) §8 — or
  he declares it carried (*Open at 3.7*, question 3). ~~Until one of those, 3.8's entry condition 6 is
  not met.~~ *Met the same day, by the carrying below.*
- **Carried past 3.8 — the maintainer's decision, 2026-11-23 (option B)**, as Stage 2 carried its Play
  Console declaration. It stays a pre-release item: points 3 and 4 — the app-wide answers, and the
  externally hosted privacy policy describing on-device face grouping and the Face data switch — and
  the submission remain open before the first Play release that ships the face pass. Entry condition
  6 is met by this declaration; the exit criterion below is carried, not met.

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
  *(Superseded 2026-11-23 by step 3.7's G7, through the real worker: about 1,600 per run on the
  SM-G990B — *3.7*, G7; ADR-0023's row 6.)*
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
40, 42, 45 and 46, and 50 (2026-11-20),
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendments 5–7, ~~and 8
(2026-11-20)~~ (Amendment 8, adopted and reversed 2026-11-20, binds nothing),
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

**Accepted 2026-11-20, as met** ([albums.md](albums.md) §8 decision 50; a dated note in ADR-0023 §5):
the maintainer accepted the budget as proposed, with Task 1's figures below in front of him. Task 2
is unblocked.

#### Task 1 — measured 2026-11-20

On the SM-G990B, screen off, thermal status 0 throughout. `FaceGroupingMeasurementDeviceTest`, opt-in.
- **The real library:** 422 faces in 299 images with faces; **164 groups, 104 with at least two
  distinct images** — the groups the picker would offer; the largest group 57 faces; **the largest
  connected component at ≤ 0.50, 88 faces**. Grouping 241 ms; peak RSS +3.2 MB.
- **Synthetic sets**, in ascending order:

  | Faces | Time | Java heap rise | Peak RSS rise |
  |---|---|---|---|
  | 2,000 | 1.2 s | +11.3 MB | +10.8 MB |
  | 5,000 | 7.5 s | +61.9 MB | +57.8 MB |
  | 10,000 | 30.7 s — 18.7 s distances, 11.9 s clustering | +224.5 MB | +213.9 MB |

  At 10,000 in a fresh process: 30.9 s and +212.6 MB. The Java heap peaked at about **233 MB of its
  256 MB maximum** (`memoryClass` 256, `largeMemoryClass` 512). No `OutOfMemoryError`. The largest
  component of the synthetic 10,000 was 60 faces.
- **Representatives under 60 px:** of the 104 offered, each its group's medoid, **22 have a face under
  60 px** on the shorter side of the decoded frame, 35 under 80 px; the median is 108 px, the
  smallest 40 px.
- **Tests:** `FaceGroupingTest`, 28 tests; six mutation checks, each seen to fail its test; the full
  unit suite 1105 tests, 0 failures.

**What the figures mean.** Both limits hold at 10,000, but **the binding limit is the Java heap, not
RSS**: the float32 triangle is 200 MB at 10,000 faces against a 256 MB heap, so the full-triangle
ceiling is about **10,500 faces** — about 25,000 images at the real library's density. Option (a)
below lifts it at no cost to the groups, and is adopted (decision 50). The 22 representatives under
60 px led to ADR-0018 Amendment 8 (*The representative*, below), reversed the same day when Task 2
measured that it fixed one of them.

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

**The figures met the budget, and (a) is adopted anyway — 2026-11-20** (decision 50), because it lifts
the Java-heap ceiling above. It is not the "new decision" the paragraph above reserves: that is for a
change to the groups, and (a) gives the same groups. It is recorded as part of decision 50 all the same,
because it moves the ceiling the budget was judged against. **Why the groups are identical**, as a
proof the tests must hold:
- Let the graph's edges be the pairs whose distance **as stored in the float32 triangle** is at most
  0.50, compared exactly as the clustering compares it. Two different components have no edge between
  them, so every cross pair is above 0.50.
- Average linkage merges two clusters only when the mean of their cross-pair distances is at most
  0.50. If each cluster lies inside one component and the two lie in different components, every term
  of that mean is above 0.50, so the mean is too — and also after rounding, since the update is a
  weighted mean computed in double and rounded to float, and rounding to nearest is monotone. So no
  such pair ever merges, and by induction every cluster stays inside one component.
- Merges in one component change no distance inside another, so each component's merges happen in
  the same order as if it were alone, ties included, provided its faces keep their ascending face-id
  order. The medoid~~, and Amendment 8's representative,~~ is computed per group from the embeddings.
- So clustering each component alone gives exactly the groups and representatives of the whole.

**What it costs and leaves.** Finding the components needs every pairwise distance once, as now, but
keeps none: a union-find of an `IntArray` per face — about 40 KB at 10,000 faces, 80 KB at 20,000 —
while the unit vectors, already held today, are 10 MB at 10,000 and 20 MB at 20,000. Each component's
triangle is then built and freed in turn: at the largest component measured, 88 faces, about 15 KB.
**The ceiling becomes time, not memory:** the distances stay quadratic in all faces, 18.7 s at 10,000,
so about 75 s at 20,000; ~~Task 2 measures it~~ Task 2 measured 12.8 s at 10,000 and 50.9 s at 20,000. **Its worst case** is one giant component, which is the full
triangle again; the cap on faces a triangle can hold applies to a component, not to the input. Nothing
measured comes near it.

**B2 — Decided at 3.4, ~~subject to the budget~~ the budget met (decision 50): not adopted.** Every run that finds grouping needed
(below) regroups every face from scratch with decision 45's average linkage. *Why:* the result is
then a function of the stored faces alone, independent of the order they arrived in, which is
decision 45's reason for average linkage, and what the spike measured; and the budget above is what
makes a full regrouping affordable. B2 comes back only as option (e). **2026-11-20:** the budget was
accepted as met (decision 50); B2 is not adopted.

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
  - **Components first — added 2026-11-20** (decision 50). The faces are split into the connected
    components of the graph of pairs whose float32 distance is at most 0.50, by a union-find over one
    pass of the distances, and average linkage runs inside each component, its faces in ascending
    face-id order. Same groups, proved under *Measurement first*, option (a); the cap on faces a
    triangle can hold applies to a component.
- ~~**`FACE_GROUPING_VERSION = 1`**~~ ~~**`FACE_GROUPING_VERSION = 2`** (2026-11-20, ADR-0018
  Amendment 8 §3: the representative rule changed, and `groupId` is the representative)~~
  **`FACE_GROUPING_VERSION = 1`** again (2026-11-20, later: Amendment 8 reversed; Task 2b sets it
  back), beside
  `FACE_PIPELINE_VERSION`: it names the algorithm, the
  metric, the 0.50 cut and its inclusiveness, the float32 triangle, the tie-break and the two rules
  below. Changing any of them bumps it, which regroups with no file I/O and no inference
  (Amendment 5). The component split changes none of them.
  - **When it bumps — Decided at 3.4, 2026-11-20:** only when grouping's result for the same stored
    faces would change — which faces share a group, or the `groupId` written for them. A change to
    how the result is computed that leaves it identical bumps nothing: components first is the
    example, proved under *Measurement first*, option (a). *Why:* a bump makes every installed index
    regroup, and a stored `groupingVersion` must name the rule that produced the stored ids, no more.
  - **Why 1 and not 2:** version 2 named only the size-aware representative, which is withdrawn; no
    build has persisted a grouping at any version, grouping not being wired until Task 4, so no
    stored value can be misread; and components first gives the same groups and representatives as
    the plain medoid Task 1 built under version 1. So 1 still names exactly the rule that will run.
- **What a group stores — Decided at 3.4: no table and no column; the index stays at version 6.**
  ~~*Amended 2026-11-20:* one column on the face row, for the representative — ADR-0018 Amendment 8,
  next bullet but one; the index reaches version 7.~~ *Restored 2026-11-20, later:* Amendment 8 was
  reversed before it was built.
  Amendment 5 left per-group summaries — image count, most recent photo, medoid face — to 3.4, stored
  or computed on read. Each face's `groupId` and `groupingVersion`, which `Migration(4, 5)` already
  created, are all that ~~is~~ grouping writes:
  - **`groupId` is the id of the group's representative face** (next bullet). Face ids are
    auto-generated and never reused, and groups are disjoint, so it is unique within a grouping —
    the stable id decision 46's last tie-break needs — and it records the representative without a
    column.
  - **The image count and the most recent photo are computed on read**, by a query over the faces
    and their index rows. *Why not stored:* they change with every Tier A write that touches a date,
    so storing them would add a second thing to keep true; a query over at most tens of thousands of
    rows costs no migration (Amendment 4's precedent: an index or a table is added when a measured
    query needs it).
  - So there is **no `Migration(6, 7)`** ~~and no ADR-0018 Amendment 8~~. ~~Task 2 checks that `6.json`
    is unchanged and that no `7.json` appears.~~ ~~**Reversed 2026-11-20** (decision 50): there is a
    `Migration(6, 7)` and an ADR-0018 Amendment 8, for the face's pixel size — not for anything
    grouping writes. Task 3 builds it; `7.json` is new and `6.json` unchanged.~~ *Restored 2026-11-20,
    later:* Amendment 8 exists only as a reversed record. The verification phase checks that
    `git status app/schemas` shows nothing.
- **The representative — Decided at 3.4: the group's medoid, ties to the lowest face id.** The member
  whose summed cosine distance to the other members is smallest; a group of one is its own face.
  Computed after clustering from the embeddings, streaming per group, since clustering overwrites the
  triangle. ~~**Deliberately not the spike's rule** — the medoid among members at least 60 px across:
  a stored box is in fractions (ADR-0022 §1), the decoded size is not stored, and Tier A's EXIF width
  and height are often absent and not upright. Restoring the 60 px rule would need the decoded size
  stored, a migration and an ADR-0018 amendment. The 40 px cut-off already removes the smallest
  faces, and the medoid favours a group's most typical face. **Measured, not assumed:** Task 1
  reports how many offered representatives are under 60 px, decoding each on the phone; if it is
  more than a few, the Planner puts the stored-size amendment to the maintainer.~~
  ~~**Amended 2026-11-20 — the spike's rule restored**, by ADR-0018 Amendment 8, adopted with decision
  50, after Task 1 found 22 of 104 offered representatives under 60 px. Each face stores
  `boxShortSidePx`, the shorter side of its box as detected in the decoded frame, 0 for unknown. The
  representative is the medoid unless its short side is under
  `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS` (60); then the member with the smallest summed distance to
  the whole group among those at least 60 px, ties to the lower id; if none is, the medoid. Unknown
  counts as under 60. The amendment holds the reasons, the column and the rule for rows written
  before it.~~
  **Amended 2026-11-20, later — the plain medoid, as decided first** (decision 50, amended). Task 2
  built the spike's 60 px rule and measured it: 21 of the 104 offered groups have no member at least
  60 px across, so it fixed 1 of the 22 small representatives. Small thumbnails — 22 of 104 under
  60 px, the smallest 40 px — are accepted as the picker's behaviour for now; a lower threshold, a
  quality score from the detector or upscaling at 3.6 would be a new decision.
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
- **The read is paged by face id**, ids and embeddings only ~~— and, from 2026-11-20, each face's
  `boxShortSidePx`~~ (withdrawn the same day), outside a transaction; at 10,000 faces
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

#### Tasks — ~~four, sequential, with the maintainer between Task 1 and Task 2~~ ~~five, sequential (renumbered 2026-11-20)~~ Tasks 1, 2, 2b, 3, 4 and 5, sequential (2026-11-20)

*Amended 2026-11-20, later.* Tasks 1 and 2 are built. Amendment 8 being reversed, **Task 2b** removes
the size-aware representative Task 2 built; **Task 3 loses the migration** and every short-side field
(rewritten below, not struck, because no Coder has started it); Tasks 4 and 5 keep their numbers.

*Renumbered 2026-11-20, after decision 50.* Task 1 is built. Components first and the size-aware
representative change `FaceGrouping.kt`, so that is a task of its own, **Task 2**; what was Task 2,
the store and the grouping pass, is **Task 3** and gains the migration; the worker is **Task 4**; the
worker measurement on the phone is **Task 5**. Each task's file list below is complete: nothing runs in
parallel, but each Coder edits only the files his task names.

Paths are under `app/src/main/java/com/davide/seddio/easygallery/`, and tests under
`app/src/test/…/data/` or `app/src/androidTest/…/data/`. Method names are the Coder's; what each
does is not. The Kotlin files are CRLF, and new ones should be too.

**Task 1 — Coder, with the SM-G990B attached: the grouping and its measurement.** ✅ **Built and
verified 2026-11-20** — the figures are *Task 1 — measured 2026-11-20*, above; the brief is kept as
it was. `data/FaceGrouping.kt` has been hand-edited by the maintainer since; Task 2 starts from the
file as it stands, not from this brief.
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

**Then the maintainer — the budget.** ✅ **Accepted 2026-11-20, as met** (decision 50), with option (a)
and ADR-0018 Amendment 8 adopted the same day (and reversed later that day, after Task 2). ~~He accepts the budget under *Measurement first*, as proposed or
changed, now or with Task 1's figures in front of him. If the figures fail it, he chooses among
options (a) to (f), and the Planner records the choice as a decision in [albums.md](albums.md) §8
and an amendment to ADR-0023 §5 before Task 2 is briefed again. The Planner records an acceptance
too: a decision in §8 and a dated note in ADR-0023 §5.~~ Both records are written.

**Task 2 — Coder: components first and the size-aware representative.** ✅ **Built and verified
2026-11-20** on the SM-G990B, uncommitted; the brief below is kept as it was. **Its representative
rule is withdrawn** (decision 50, amended; ADR-0018 Amendment 8 reversed) and Task 2b removes it.
What it measured:
- **Components first, synthetic:** 10,000 faces in **12.8 s, +30.4 MB Java heap, +17.5 MB RSS**;
  20,000 in **50.9 s, +46.9 MB heap, +39.1 MB RSS** — against Task 1's 30.7 s and +224.5 MB heap at
  10,000 with the full triangle. The largest synthetic component, 60 faces.
- **The real library:** 422 faces, 147 components, 164 groups, 104 offered; the largest component
  88 faces — the same groups as Task 1's.
- **The size-aware representative:** 21 of the 104 offered groups have no member at least 60 px
  across; offered representatives at least 60 px, **83 of 104 with the rule against 82 of 104 with
  the plain medoid** — 1 of Task 1's 22 fixed; the median 108 px, the smallest 40 px, unchanged.
- **Tests:** the full unit suite 1126 tests, 0 failures; 12 mutation checks, each seen to fail its
  test (the report lists them; 8 and 9 below among them).
- ADR-0023 §5 carries the synthetic figures and what bounds the heap now, in a dated note.

~~Waits on nothing.~~ Plain JVM,
except its last item, which needs the SM-G990B. No schema, store or worker file.
- `data/FaceGrouping.kt` — **read as it stands**, the maintainer having edited it since Task 1:
  - `FaceGroupingInput` gains the face's short side in pixels, an `Int`, 0 meaning unknown; still
    redacted.
  - **Components first** (*Decided at 3.4*; the proof under *Measurement first*, option (a)): one pass
    over every pair, each distance computed and rounded to float32 exactly as the triangle stores it
    and compared with the cut exactly as the clustering compares it, joining the pairs at or below
    0.50 in a union-find; then, per component of two or more faces, in ascending face-id order, its
    own triangle, average linkage and representatives, the triangle freed before the next. A component
    of one is its own group with no triangle. The face-count cap applies to a component, with its
    fixed message. The setting and cancellation are checked at every row of the pass, as for the
    distances now, and before each component.
  - **The representative** — ADR-0018 Amendment 8 §3, with `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS = 60`
    beside `FACE_GROUPING_CUT`; unknown counts as under 60.
  - **`FACE_GROUPING_VERSION = 2`**, its KDoc naming the 60 px rule and the order of preference.
  - The full-triangle path stays reachable from the tests, as the reference the split is held to.
- `app/src/test/…/data/FaceGroupingTest.kt` — Task 1's tests adapted to the new input, and these
  exit criteria, each a test:
  1. **identical groups and representatives** to the full-triangle path on at least 200 seeded random
     sets of 2 to 200 faces, drawn so that each set has several components and cross pairs near
     0.50, with random short sides including some under 60 and some 0;
  2. a pair at exactly 0.50 is joined, and a set of two components whose closest cross pair is just
     above 0.50 stays two; the edge test reads the float32 value — on a stated triangle, or a seeded
     pair found by search, whose double distance is above 0.50 and whose float32 value is 0.50;
  3. 100 shuffles of a random set give one output, through the split;
  4. the representative: a medoid at 60 px or more is kept; a medoid under 60 gives the usable member
     with the smallest summed distance — not the lowest id, not the largest face; a tie among usable
     members goes to the lower id; exactly 60 is usable; no usable member gives the medoid; 0 is
     treated as under 60; a group of one under 60 is itself;
  5. a singleton component, and a zero-norm face, each a group of its own.
- `app/src/androidTest/…/data/FaceGroupingMeasurementDeviceTest.kt` — synthetic faces carry random
  short sides; per size, the component pass timed as a phase of its own. **Run on the phone** with
  `faceGroupingMeasurement=true`, `faceGroupingMeasurementReal=false` and
  `faceGroupingMeasurementSizes=10000,20000`: time per phase, the Java heap and `VmHWM` rises, the
  largest component. Expected: the heap rise falls from Task 1's +224.5 MB at 10,000 to tens of MB,
  and time stays near Task 1's distances, about 19 s at 10,000 and 75 s at 20,000 — measured, not
  assumed. The real library's count of representatives under 60 px after the rule needs stored sizes,
  so it is Task 5's.
- **Verification by the Coder:** `FaceGroupingTest`, `compileDebugAndroidTestKotlin`, the opt-in run
  with every figure in the report, and mutations 6 to 9 below. Not the full gate.

**Task 2b — Coder: a small revision — the representative back to the plain medoid.** ✅ **Done
2026-11-21** — *3.4 — Built and verified 2026-11-21* below; the brief is kept as it was. After Task 2;
~~waits on nothing.~~ Plain JVM. ADR-0018 Amendment 8 is reversed (decision 50, amended), so the
size-aware rule Task 2 built is removed; components first stays exactly as built. No schema, store or
worker file. Its files, and only these:
- `data/FaceGrouping.kt` — **read as it stands**:
  - `FaceGroupingInput` loses `boxShortSidePx`: a face id and its embedding, as in Task 1; still
    redacted. Whatever carries the sizes inside the grouping goes with it.
  - `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS` and the usable-member branch are deleted. **The
    representative is the plain medoid**: the member whose summed cosine distance to the other
    members is smallest, a tie within the medoid tolerance going to the lower face id, a group of one
    being its own face — as Task 1 built it.
  - **`FACE_GROUPING_VERSION = 1`**, its KDoc naming the plain medoid and saying why it is 1 again: no
    build has persisted a grouping, and components first yields the same groups and representatives
    as Task 1's version 1; and the rule, in one line, that the version bumps only when grouping's
    result for the same stored faces would change (*Decided at 3.4*, *When it bumps*).
  - Components first, the full-triangle reference path, the cap, the probes and the messages are not
    touched.
- `app/src/test/…/data/FaceGroupingTest.kt` — the size tests deleted (Task 2's criterion 4); the
  random short sides dropped from the property test's input. **Kept, each a test:**
  1. the components-first property test — identical groups and representatives to the full-triangle
     path on at least 200 seeded sets, as Task 2 wrote it;
  2. the float32 edge test, the shuffle test, the singleton and zero-norm tests (Task 2's 2, 3, 5);
  3. Task 1's criterion 5: `groupId` is the medoid's id; a medoid tie goes to the lower id — a stated
     triangle with two members of equal summed distance, the higher-id one first in the input; the
     medoid is not the lowest id when another member is more central.
- `app/src/androidTest/…/data/FaceGroupingMeasurementDeviceTest.kt` — **the short-side figures go**:
  the synthetic faces' random short sides, and the real-library run's count of representatives under
  60 px together with whatever records sizes for it (the pipeline wrapper that captures each
  `RawFace`'s pixels, and the matching by box). *Why delete rather than keep it as information:* no
  decision uses the figure any more — decision 50, amended, accepted 22 of 104 — and the code that
  produces it is the most intricate in the file; if 3.6's thumbnail work wants it again, Task 1's
  method, decoding each representative, recomputes it. **Kept:** every size measurement — time per
  phase, the Java heap and `VmHWM` rises, `maxMemory`, the memory classes, the largest component,
  thermal status and charging — and the real library's faces, components, groups and groups offered.
  The instrumentation arguments are unchanged: `faceGroupingMeasurement=true`,
  `faceGroupingMeasurementReal=false`, `faceGroupingMeasurementSizes=…`.
- **Exit check, by search:** no `boxShortSidePx`, `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS`, short side or
  60 px left anywhere under `app/src`.
- **Verification by the Coder:** `FaceGroupingTest`, the full unit suite (Task 2's 1126, less the
  deleted size tests), `compileDebugAndroidTestKotlin`, and ~~mutations 5 and 17~~ mutation 5 below
  (*corrected 2026-11-21:* there is no mutation 17; the list ends at 16). If the SM-G990B
  is attached, one opt-in run with the real library and `faceGroupingMeasurementSizes=2000`, showing
  164 groups and 104 offered as before — the groups must not move; if it is not attached, say so and
  rely on the property test. Not the full gate.

**Task 3 — Coder: the store and the grouping pass — no schema change.** ✅ **Done 2026-11-21** —
*3.4 — Built and verified 2026-11-21* below; the brief is kept as it was. After Task 2b.

*Rewritten 2026-11-20, later, when ADR-0018 Amendment 8 was reversed, before any Coder had started
it.* What it lost: `MediaIndexFaceEntity.boxShortSidePx`, version 7 and `MIGRATION_6_7` in
`MediaIndexDatabase.kt`, `7.json`, the short side on `FaceDetection` and `StoredFace`,
`faceDetectionOf` computing it, `MediaIndexDatabaseMigrationTest.kt`, and the compile fixes in four
test files the new required field would have forced — the amendment says what each was for. **The
index stays at version 6; the Tier C pass writes nothing new.** What grouping writes uses the two
columns `Migration(4, 5)` already created on `media_index_face`: `groupId` (`Long?`) and
`groupingVersion` (`Int?`), on `MediaIndexFaceEntity` and `StoredFace` as they stand.

Its files, and only these:
- `data/MediaIndexTierC.kt` — the models the store returns for grouping and for the person record,
  each with a redacted `toString`. `FaceDetection`, `StoredFace` and the encoders unchanged.
- `data/MediaIndexDao.kt` and `data/MediaIndexStore.kt` — the interface, `RoomMediaIndexStore` and
  `InMemoryMediaIndexStore`, all `suspend`, no `Flow`:
  - **the faces for grouping:** current `FACE_PIPELINE_VERSION` only, ids and embeddings, paged by id;
  - **whether grouping is needed** (*When grouping is needed*): a face at the current pipeline version
    with a null `groupingVersion` or another one, or whose `groupId` names a face that no longer
    exists; faces at another pipeline version ignored;
  - **the write**, one transaction with the setting read inside it (*The write*; Amendment 7 §4):
    each face's `groupId` — the id of its group's representative face, the plain medoid — and
    `groupingVersion` = `FACE_GROUPING_VERSION`, by id, chunked under 999 variables; off, nothing
    written and 0 returned;
  - **the person record** (*What 3.4 produces*): groups at the current pipeline and grouping
    versions whose representative exists, with at least a given number of distinct images — the
    picker passes two (decision 40) — sorted by distinct images, most first, then the newest
    `capturedDateTakenEpochMillis` with no date last, then `groupId`; each with its `groupId`, its
    distinct-image count, that date and the representative's anchor fields — the image's identity
    (URI and healing triple, from its index row), its `FaceBox` and its pipeline version
    (ADR-0022 §1).
  - `deleteAllFaceData` needs nothing: grouping writes only columns of face rows, which it already
    deletes under `secure_delete`.
- `data/FaceIndexPass.kt` — `FaceIndexLogLines` only: the grouping line and its fixed stop names —
  grouped, not needed, setting off, past the deadline — so the logger test still holds one set.
- New `data/FaceGroupingPass.kt`, Android-free: needed or not; read; `groupFaces`, which splits into
  components itself; write; the setting, the deadline and cancellation; its fixed-message log line.
  Its caller holds the Tier C lock and has checked the gates (Task 4); it takes no lock of its own.
- Tests: `MediaIndexTierCStoreTest.kt` through `forEachStore`, so both stores meet each rule;
  `MediaIndexDaoTest.kt` for the queries on `inMemoryDatabaseBuilder`, with an update of more than
  999 ids; `FaceIndexPassTest.kt` for the fixed set of log lines only; new `FaceGroupingPassTest.kt`,
  plain JVM on the in-memory store. Exit criteria:
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
  6. the pass: not needed — nothing written; needed — written with the version, `groupId` the
     medoid's id; the setting off before the write, the deadline past, or a cancellation — nothing
     written;
  7. **the logger test (ADR-0024 §6):** every line is one of the fixed set and carries no digit;
     fixtures with distinctive ids, URIs and embedding values appear in no line.
- **No schema change:** `MediaIndexEntities.kt`, `MediaIndexDatabase.kt` and `app/schemas` are not
  touched, and `git status app/schemas` shows nothing.

**Task 4 — Coder: the worker.** ✅ **Done 2026-11-21** — *3.4 — Built and verified 2026-11-21*
below; the brief is kept as it was. After Task 3; the same Coder may take Tasks 3 and 4 in turn.
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

**Task 5 — Coder, with the SM-G990B attached: grouping through the worker.** ✅ **Done 2026-11-21**
— *3.4 — Built and verified 2026-11-21* below, with the review's judgement calls on this test; the
brief is kept as it was, except where it is struck. After Task 4.
- New `app/src/androidTest/…/data/FaceGroupingWorkerMeasurementDeviceTest.kt`, its only file,
  **opt-in** (`faceGroupingWorkerMeasurement=true`), and androidTest only by the constraint in *The
  dev-device route*: whatever sets the disclosure flag stays in `src/androidTest`. On the app's own
  index, on the dev phone: the setting on through
  `SharedPreferencesFaceDataSettingStore`; the disclosure marked shown through
  `SharedPreferencesFaceDeviceStateStore.markDisclosureShown()`; Index now through
  `WorkManagerMediaIndexingWorkScheduler`; the unique work awaited, and ~~repeated until no image is
  pending, three runs at most~~ repeated until the face pass reports `stop=COMPLETED`, six runs at
  most (*as built, accepted at review 2026-11-21:* the GIFs stay pending by design, so "no image
  pending" never holds). Figures: runs, wall time per run, the `VmHWM` rise, thermal status,
  charging, faces, groups, groups offered, that grouping is no longer needed, ~~and **how many offered
  representatives are under 60 px**, from the stored short sides — against Task 1's 22 of 104~~
  (*withdrawn 2026-11-20 with ADR-0018 Amendment 8:* no short side is stored). *As built, accepted
  at review 2026-11-21:* the test also deletes any face data already on the phone before it starts.
  **In `finally`:**
  `deleteAllFaceData()` on the app's store, and the `face_device_state` file cleared through
  `Context.getSharedPreferences` in the test — no new method in `src/main`. The face data stays on
  the phone (decision 9), and the run leaves none behind.
- It discharges the worker half of ADR-0023 verify item 6 for 3.3's pass and 3.4's grouping alike,
  with the disclosure set by a test rather than by 3.6's screen; 3.7's item 5 keeps the run through
  the real disclosure. ~~It is likely the first time `Migration(6, 7)` runs on a device; the migration
  is proved by Task 3's test, not by this run.~~ *Withdrawn 2026-11-20 with Amendment 8:* there is
  no `Migration(6, 7)`; the index stays at version 6.

**Mutation checks — required.** Each applied, its test seen to fail, and the source restored and
checked by checksum, as 3.2 did; the report lists each with its failing test. 1 to 5 were Task 1's,
done; ~~6 to 8 in Task 2, 9 and 10 in Task 3~~ renumbered 2026-11-20: 6 to 9 in Task 2, 10 to 14 in
Task 3, 15 and 16 in Task 4:
1. single linkage in place of average; 2. `<` in place of `≤` at the cut; 3. a cut of 0.55;
4. the tie-break removed, input order kept; 5. the representative as the lowest id, not the medoid;
6. the component edge test on the double distance, not the float32 value; 7. components joined only
below the cut, not at it; ~~8. the representative ignoring the short side; 9. an unknown short side
treated as usable;~~ 10. the write ignoring the setting; 11. "needed" ignoring a missing representative;
12. the person record counting faces, not distinct images; ~~13. the migration deleting face rows;
14. `faceDetectionOf` storing the clamped short side;~~ 15. grouping called outside the lock, or without
the disclosure; 16. a grouping log line carrying a count.

*Amended 2026-11-21:* 8 and 9 were run in Task 2 and their code was removed by Task 2b; 13 and 14
were for Amendment 8's migration and short side, reversed before they were built. Neither pair
applies to the code as it stands, and the numbers are not reused.

**Verification** ✅ **Done 2026-11-21** — the gate is under *3.4 — Built and verified 2026-11-21*
below. It is its own phase, once, over Tasks 1 to 5 integrated, owned by whoever the brief
names — not by Task 5's Coder: the definition of done with `adb devices` stated,
`connectedDebugAndroidTest` on the SM-G990B if attached with every opt-in measurement skipped, and
`git status app/schemas` showing ~~`7.json` new and nothing else~~ nothing (*corrected 2026-11-20
with Amendment 8's reversal:* no schema change). Tasks 1, 2 and 5's device measurements
are not repeated there.

**After it lands — Planner, task P.** ✅ **Done 2026-11-21:** landed in `ARCHITECTURE.md` §2, §3,
§6 and §11, `AGENTS.md` *The two databases*, ADR-0018 Amendment 5's *Built* notes and Amendment 8's
reversal annotations, ADR-0023's *Built* note, §4, §5 and verify row 6, and the test conventions;
this plan and [albums.md](albums.md) §7 last. The brief was — what 3.4 makes stale:
- `ARCHITECTURE.md` §3 — the two new `data/` files, in the pure, Android-free list as well; the store,
  DAO, Tier C, worker and `EasyGalleryApp` rows; §6 — what `groupId` means, summaries computed on
  read, ~~the version still 6~~ ~~the index at version 7 with `Migration(6, 7)`, and the face row's
  `boxShortSidePx` and what 0 means (ADR-0018 Amendment 8)~~ the version still 6 (*restored
  2026-11-20 with Amendment 8's reversal*); §11 — if the 60 px figure or a budget
  option leaves debt.
- `AGENTS.md` *The two databases* — the paragraph on face data, which says what each step built ~~and
  the migrations paragraph, which gains a sixth, `Migration(6, 7)` (Amendment 8)~~ (*withdrawn
  2026-11-20 with Amendment 8:* no sixth migration).
- ADR-0018 Amendment 5 — dated notes under *What Tier C stores* (no third kind of row; `groupId` is
  the representative; summaries on read ~~— the pointer to Amendment 8's column is written~~) and
  *Keeping Tier C true* (the conditional write; the lock). ~~Amendment 8 — a *Built* note, and the
  marker under the record's title removed.~~ *Amended 2026-11-20:* Amendment 8 is reversed, so it
  takes no *Built* note; it is marked reversed instead.
- ADR-0023 §4 (where grouping runs), §5 (Task 2's figures at 10,000 and 20,000 faces, under the
  dated note of 2026-11-20, which already holds Task 1's and the acceptance) and verify row 6
  (Task 5's worker run).
- [albums.md](albums.md) §7's Stage 3 row. ~~, and decision 45's B2 pointer (added 2026-11-20) if the
  budget's answer changes it. The budget's decision is written when the maintainer answers, not at P.~~
  Decision 50 and decision 45's pointer were written 2026-11-20.
- This plan: the step row, the handoff, a *3.4 — Built and verified* section, *Open questions* B2,
  and 3.7's list with whatever Task 5 did not prove.
- The test conventions (`.github/instructions/tests.instructions.md`), for the opt-in measurements —
  Task 1's `faceGroupingMeasurement=true`, with `faceGroupingMeasurementReal=false` to skip the real
  library and `faceGroupingMeasurementSizes=10000` (comma-separated) to choose the synthetic sizes,
  and Task 5's `faceGroupingWorkerMeasurement=true` — and the rule that only test sources set the
  disclosure flag; and `.github/agents/*.agent.md`, checked for a sentence about grouping.

**Needs the maintainer.** ~~**Before Task 1: nothing.** **Before Task 2:** his acceptance of the budget —
or, if Task 1's figures fail it, his choice among (a) to (f), a new decision. **Not needed:** no schema
change, so no amendment to approve and no call-out; no dependency, permission, preferences file,
`*Content` signature or string. The representative rule (*Decided at 3.4*) is the Planner's and he can
reverse it.~~ **Nothing pending, from 2026-11-20:** the budget is accepted ~~and Amendment 8 adopted~~
(decision 50). **Not needed:** no dependency, permission, preferences file, `*Content` signature or
string. ~~The migration is Amendment 8's, already approved. What is the Planner's — the column's
form, the rule for rows written before it, the grouping-version bump (Amendment 8), and the
representative rule's details — he can reverse, most cheaply before the migration first runs on the
phone.~~ *Amended 2026-11-20:* Amendment 8 was reversed before it was built (decision 50, amended),
so there is no migration and nothing of it to reverse.

**Not 3.4's:** the `people` clause, anchors and evaluation (3.5); the picker, the crop, the disclosure,
the switch and its confirmation, every string (3.6); grouping on the SM-G781B, and the worker through
the real disclosure (3.7); B2, ~~unless the budget fails~~ not adopted (decision 50); any Tier C `Flow`. No `ui/` file, no
`*Content` signature, no string.

### 3.4 — Built and verified 2026-11-21

Built as Tasks 1, 2, 2b, 3, 4 and 5 above, then task P. What landed is not restated here: it is
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5's dated
*Built* notes — grouping fills `groupId` and `groupingVersion` on the face row and nothing else, the
person record computed on read — and
[ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md)'s *Built*
note, §4, §5 and verify row 6, with `ARCHITECTURE.md` §3, §6 and §11 for where each piece sits:
`data/FaceGrouping.kt` (components first, average linkage at 0.50, the plain medoid,
`FACE_GROUPING_VERSION` 1), `data/FaceGroupingPass.kt`, the store's grouping methods with the People
picker's query `listFaceGroups`, and the pass run by `MediaIndexingWorker` after the face pass under
the same hold of the Tier C lock. **No schema change:** the index stays at version 6; ADR-0018
Amendment 8 was adopted and reversed before it was built (decision 50, amended).

**The gate — the definition of done, once, over the integrated change:** `compileDebugKotlin` OK;
the unit suite, 75 files, 1174 tests, 0 failures; `compileDebugAndroidTestKotlin` OK; a device was
attached, the SM-G990B, so `connectedDebugAndroidTest` ran: 266 tests, 0 failures, 3 skipped — the
three opt-in measurements. The debug and release merged manifests byte-identical to the build before
3.4 (debug md5 `8856dbd4b4257bd665c4593a325ba624`, release `043a521e4d0a25724364c8b8b6ea6f8d`). This
document does not record tracking state.

**The grouping core** (`FaceGroupingMeasurementDeviceTest`, opt-in, on the phone):
- synthetic: 2,000 faces in 0.58 s; 5,000 in 3.2 s; 10,000 in 12.8 s, +30.4 MB Java heap and
  +17.5 MB RSS; 20,000 in 50.9 s, +46.9 MB heap and +39.1 MB RSS; the largest synthetic component,
  60 faces;
- the real library: 422 faces in 299 images, 147 components, the largest 88 faces; 164 groups, 104
  offered; grouping 125 to 292 ms.

**Through the real worker** (`FaceGroupingWorkerMeasurementDeviceTest`, opt-in, Index now on the
app's own index, the disclosure set by the test): run 1 SUCCEEDED — Tier A 1,133 rows; the face pass
1,002 images, 0 failed, the 90 GIFs skipped, `stop=COMPLETED` in 113.2 s; grouping `stop=GROUPED` in
292 ms; peak RSS rise 372,096 kB. A second, no-op run took 568 ms with grouping `NOT_NEEDED`. Thermal
status 0, charging. The face data was then deleted — 1,424 rows, scrubbed — and the flags restored.

**Review outcomes.** No hard violations. Fixed: the device test now restores the flags under the
held Tier C lock before deleting; two comments in `FaceGrouping.kt` corrected; the device test re-run
once on the phone afterwards, passed. **Judgement calls on Task 5's test, accepted as built:**
- **at most six runs, not the brief's three** — a ceiling, not a count; run 1 completed;
- **it stops on `stop=COMPLETED`, not on "no image pending"** — the GIFs stay pending by design (E2),
  so the brief's condition could never hold;
- **it deletes any face data already on the phone before it starts** — so run 1 measures a full pass
  from empty; the phone is the only install, face data is recomputable, and before 3.6 no installed
  app holds any.

**Not proven by it** — each step 3.7's, items 11 to 14 under *3.2 to 3.8*, or debt already in
`ARCHITECTURE.md` §11:
- a production-size library: grouping and the face pass at 10,000 faces and more, with the store's
  read and write cost, which is unmeasured there — the 10,000 and 20,000 figures are the core alone,
  on synthetic faces;
- the SM-G781B (A4), and any non-Samsung phone;
- the heap ceiling: one connected component of about 10,500 faces fails with `OutOfMemoryError`, not
  the fixed "too many" message, and nothing enforces a limit before it — a heap-based cap is open for
  the maintainer (`ARCHITECTURE.md` §11);
- the deadline, checked only at the start: after a face pass that used its 7-minute budget, ~~about
  37,000 faces fit before WorkManager's stop, about 68,000 in a whole window — quadratic arithmetic
  from the 20,000-face figure, not a measurement~~ *(corrected 2026-11-22 by step 3.7's G5c, through
  the real store: about 31,000 and about 56,000 — *3.7*, G5c and L11)*; beyond that every run is cut
  and writes nothing;
- the GIFs, still pending (3.5), and the worker through the real disclosure (3.7 item 5).

### 3.5 — The people clause and its evaluation — refined 2026-11-21

Refined by the Planner on 2026-11-21 from the code as it stands after 3.4, read file by file, not
from earlier reports. It does not restate the records it builds on:
[ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4 (the
clause, its strictness, settled and unsettled, the two causes found at evaluation, never rewritten);
[ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) §1, §3 and §4 (the
anchor, its resolution, no write-back); [albums.md](albums.md) §8 decisions 35, 41, 42 and 46;
[ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §4 (face data off
is "people unavailable", not waiting); ADR-0018 Amendment 5 (a pending image).

**Nothing in it needs the maintainer.** No schema change in either database, no ADR amendment, no
dependency, permission or preferences file, no new architectural pattern. Every choice below is one
those records leave to 3.5: the clause's code shape, the tie rule (ADR-0022 §3 step 4), and how the
evaluator reports a cause (Amendment 4).

#### Decided at 3.5 — the clause

- **`AlbumRuleClause.People(anchor: MediaIdentity, box: FaceBox, pipelineVersion: Int)`**, in
  `data/AlbumRule.kt` — exactly ADR-0022 §1's fields. It reuses the shared identity type, which
  ADR-0020 forbids duplicating, and Tier C's `FaceBox`, whose `init` already holds the fractions in
  [0, 1] with a positive width and height. Its own `init` requires `pipelineVersion ≥ 1` and a
  non-empty URI and display name.
- **Redacted `toString`s.** An anchor names a file and where a face is in it, so `People`'s
  `toString` prints no field (ADR-0024 §6; Amendment 4's *Negative*). So does
  `AlbumRule.Unevaluatable`'s for `raw`, which it prints today: an unreadable people rule's text names
  its anchor photo. `reason` still prints. No message of a `require` names a value.
- **The stored form is Amendment 4's example, in its key order:** `type`, `pipeline`, `uri`,
  `displayName`, `size`, `dateModified`, then `box` with `left`, `top`, `right`, `bottom`. Strings go
  through the encoder's existing escaping, non-ASCII as `\uXXXX`.
- **The box's numbers are written with `Float.toString`**, which no locale affects and which reads
  back to the same float exactly — never `String.format` or any `%f`. That meets Amendment 4's
  `Locale.US` requirement, whose point is no decimal comma, and keeps the round trip exact, so a rule
  built from the picker's representative stores its box bit for bit.
- **Parsed strictly** — Amendment 4's list, with the reason values `UnevaluatableReason` owns:
  - an unknown key, at the clause level or inside `box` → `UNKNOWN_CLAUSE_FIELD`;
  - `uri` or `displayName` missing, not a string, or empty; `size` or `dateModified` missing, or not
    an integer token within `Long` → **`INVALID_ANCHOR`**, new;
  - `pipeline` missing, not an integer token, outside `Int`, or below 1 →
    **`INVALID_PIPELINE_VERSION`**, new. Any value from 1 up is accepted, a newer build's included:
    it is informational (ADR-0022 §1), unlike a `region` clause's `dataset`;
  - `box` missing or not an object, one of its four missing, a value that is not a number, or one
    that read as a `Float` is not finite or not in [0, 1], or `left ≥ right` or `top ≥ bottom` →
    **`INVALID_FACE_BOX`**, new. **The checks are made on the `Float` values**, after rounding,
    because those are what `FaceBox` holds: two edges that differ only beyond float precision are a
    malformed box, never an exception out of `FaceBox`'s `init`. `1e400` reads as infinity and is
    rejected; `0` and `1` are numbers and are accepted.
    *Noted 2026-11-21, from the review:* so a text edge just above 1 that rounds to `1f` is accepted.
    ADR-0017 Amendment 4's "in [0, 1]" is read as the stored `Float`'s range, which is what
    `FaceBox` holds, so the two do not disagree.
  - Everything Amendments 1 and 2 already reject applies unchanged — duplicate keys, the depth cap
    (`box` is depth 4 of 8), the length cap, the envelope version.

  All are parse failures, so the album shows the Newer-app notice, as Amendment 4 says.
- **No embedding, crop, group id, score or name has a key**, so the unknown-field rule refuses any
  document that tries to carry one.
- **Combination is the conjunction, unchanged.** One clause per person: two people are two clauses,
  and a photo is a member only when it has a face in each one's group — both in the picture (decision
  35). If two anchors resolve to the same group, one face satisfies both: grouping's answer that they
  are one person, the album following the group's mistakes (decision 42). Any mix with `region` and
  `time` is ANDed; the evaluator already takes any number of each.
- **No version bump and no schema change.** The envelope stays at `"version": 1`: a new clause type
  is what the Decision's unknown-clause rule exists for. A build before 3.5 reads a `people` clause as
  `UNKNOWN_CLAUSE_TYPE`, and a field a later build adds reads here as `UNKNOWN_CLAUSE_FIELD` — the
  Newer-app notice both times, its wording true both times. The rule lives in `albums.rule`, nullable
  `TEXT` since the album database's version 1 (ADR-0017; ADR-0022 §2), so the album database does not
  change; the index database gains a query and no column. *Checked against the code 2026-11-21:*
  `AlbumRule.Evaluatable` holds a list of `AlbumRuleClause`, stored whole through `AlbumRuleJson`.
  The verification phase checks that `git status app/schemas` shows nothing.
- **The encoder's 16 KiB cap stays.** A clause is about 250 characters, more with a long or non-ASCII
  file name: about fifty people fit in practice, and as few as eight with 255-character names escaped
  throughout. How many people the People criterion lets the user pick is 3.6's, and it must not let
  the encoder's `require` fail on save.

#### Decided at 3.5 — building an anchor

- **`AlbumRuleClause.People.anchoredAt(representative: FaceGroupRepresentative)`** copies the
  representative's identity, box and pipeline version, and nothing else. `listFaceGroups` read them
  from the index row and the face row together, so the clause stores the face the user saw (decision
  41). It is what 3.6's picker calls when a person is selected. Pure; no UI.

#### Decided at 3.5 — resolution and evaluation

*Checked against the code 2026-11-21:* `MediaIndexLiveJoin.build` keeps, for each live item, the one
index row that won it — a direct URI match beats a heal — and drops the row's identity once it has
read the date and place; Tier C is keyed by that row's **stored** URI, and `FaceIndexPass` only
processes a live image whose healing triple equals the row's; `observeEvaluationProjection` reads
`media_index` alone, so no Tier C write makes it emit, and `InMemoryMediaIndexStore` keeps Tier C in
maps that emit nothing.

- **The join keeps the winning row's identity.** `JoinedMedia` gains `indexedIdentity: MediaIdentity?`
  — the stored URI and healing triple of the row that won the item, null when none did. Required, no
  default: null already means *not indexed*, and a default would let a caller say that of an indexed
  item. `MediaIndexLiveJoin` also exposes, built lazily once per join like `liveMedia`, the
  `LiveMediaLookup` over its live media and its items by live URI, so every anchor of every album
  resolves against the one join (ADR-0020) and no evaluation builds its own lookup.
- **Tier C is trusted for a live item only when three healing triples agree:** the live item's, the
  winning row's, and the one the face projection read with the face state (next bullet). Otherwise
  the item is **pending** for a `people` clause — never settled, never "not found". *Why:* it is the
  face pass's own rule (it skips an image whose triple differs) and Amendment 6's (a changed triple
  drops Tier C); and the Tier A and Tier C projections are two flows that can be one emission apart,
  so a file changed under one URI could otherwise be compared against the faces of its previous
  content and show a false "anchor not found" for a moment. With the check, skew costs a moment of
  waiting. It compares `MediaHealingKey`s; it is not a second identity (ADR-0020).
- **An anchor resolves** — ADR-0022 §3, in this order, once per clause per evaluation:
  1. `lookup.find(anchor)` over the join's live media — the stored URI, then the healing triple. No
     live item → **not found**. The healed URI is not written back (ADR-0022 §4).
  2. The live item is a video or a GIF → **not found**: neither is ever face-indexed (decision 40;
     E2), so waiting would never end. An anchor is always built from an image, so this is a restore
     or heal landing on another kind of file.
  3. No winning row, no face state at `FACE_PIPELINE_VERSION`, or the three triples disagree →
     **waiting**.
  4. The state is `FAILED` → **not found** (ADR-0022 §3 step 5).
  5. Among the image's faces at that version, the one with the best IoU against the anchor's box,
     computed in `Double` from the stored `Float`s, accepted only at **IoU ≥ 0.5, inclusive**
     (decision 46). None → **not found**. An image indexed with no face at all is this case.
  6. **The tie rule** (ADR-0022 §3 step 4): between faces with exactly equal IoU, **the lower face
     id**. *Why:* an exact tie needs two stored faces overlapping one box identically — in practice
     a duplicate detection, which the detector's non-maximum suppression makes rare — so the rule is
     about determinism, not about picking better. The face id is the stable id the codebase already
     breaks ties with (grouping's merges, the representative, `listFaceGroups`, decision 46).
     *Rejected:* the larger face first — a box can tie on area too, so it needs the id after it
     anyway, and nothing says the larger face is the right person. Face ids differ between devices,
     so on a restored device a tie may land on the other face: accepted, as rare as the tie.
  7. The chosen face has a null `groupId`, or a `groupingVersion` other than
     `FACE_GROUPING_VERSION` → **waiting**: grouping has not reached it.
  8. Otherwise the person is that face's `groupId`. A `groupId` naming a representative that has
     since been deleted still names a group — its faces share it until the next grouping, which
     `isFaceGroupingNeeded` already schedules — so it is not treated as pending.
- **Per live item, for a clause resolved to group G** — Amendment 4's table, made exact. An added or
  excluded item is handled before any clause, as today.

  | Live item | Answer |
  |---|---|
  | a video or a GIF | settled no-match |
  | an image with no winning row, no face state at the current version, or triples that disagree | unsettled |
  | an image whose state is `FAILED` | settled no-match |
  | an image with a face whose `groupId` is G at the current grouping version | match |
  | otherwise, an image with a face not grouped at the current grouping version | unsettled |
  | otherwise — no face, or every face in another group | settled no-match |

  A clause whose anchor is **waiting** answers unsettled for every item, so the album waits.
  **Members of the group are images, not faces:** an image is a member when any of its faces is in
  G, so two people in one photo match one clause each (decision 35). Then Amendment 2's rules,
  unchanged: a member when every clause matches; waiting when some clause is unsettled and none is a
  settled no-match; region and time ANDed exactly as today.
- **The evaluator's input — a second, separate projection.** `MediaIndexStore` gains
  `observeFaceEvaluationProjection(pipelineVersion: Int): Flow<FaceEvaluationSnapshot>`: per stored
  URI with a face state at that version, the index row's identity, the outcome, and the faces — id,
  box, `groupId`, `groupingVersion`; **no embedding**. Room reads it with **one** query, the face
  state joined to `media_index` and left-joined to its faces at the same version, so one emission is
  one consistent snapshot of a grouping write, which is one transaction (3.4, *The write*); Room
  re-runs it on a write to any of the three tables. `InMemoryMediaIndexStore` gains a Tier C revision
  flow, bumped by every method that changes a Tier C row — face results, groups, a heal, an upsert
  that drops Tier C, the deletes — and builds the same snapshot from it and its rows. Its types live
  in `data/MediaIndexTierC.kt`, with `toString`s that print no URI and no box (ADR-0024 §6).
- **Why a second flow, not a wider `observeEvaluationProjection`:** Room invalidates by table, so
  widening the existing projection would make every region and time album re-evaluate on every face
  pass batch, and `MediaIndexingViewModel`, which also collects it, would carry face rows it never
  reads; Stage 2's rule that a library pays for the index flow only while it needs it would break for
  every library with face data. A second flow keeps the Tier A join exactly as it is; the link
  between the two is `JoinedMedia.indexedIdentity`.
- **The evaluator stays pure.** `AlbumRuleEvaluator.evaluate` gains a required
  `people: PeopleEvaluationInput?` — the availability (next section) and the face snapshot. A rule
  with no `people` clause never reads it, so callers without one pass null; a `people` clause given
  null answers unsettled for every item — **not loaded yet means waiting**, never unevaluatable.
  Required, not defaulted, so every caller states it. The resolution and the per-item answers live in
  a new `data/PeopleClauseEvaluation.kt`; `AlbumRuleEvaluation.kt` keeps the combination.
  *As built, 2026-11-21:* the input is `PeopleEvaluationInput(availability: PeopleAvailability,
  faces: FaceEvaluationSnapshot)`, both non-null — when it is given, the snapshot is there. Only the
  parameter is nullable. Null skips the availability check, so it is never unavailable, and
  `resolveAnchor` returns waiting for it **before** the anchor's lookup in the join, so a gone photo
  is not reported as not found while the face data is still loading.
- **In `AlbumsViewModel`, only a people album subscribes.** A second shared flow — the face
  projection at `FACE_PIPELINE_VERSION`, combined with the availability — shared `WhileSubscribed`
  like `liveJoin`, and combined into `observeComputedAlbum` **only for a rule with a `people` clause**.
  A manual album and a region or time album collect exactly what they collect today. The People
  picker (3.6) reads `listFaceGroups`, not this flow.
- **Accepted limits**, each also the memberships': a stale anchor URI that directly matches an
  unrelated file on a restored device is followed (ADR-0010's direct-URI-first rule, ADR-0014's
  cost); the face projection's cost at 10,000 faces and more is unmeasured — a full snapshot per
  emission while a people album is on screen — and belongs with 3.7 item 11.

#### Decided at 3.5 — status, causes and availability

*Checked against the code 2026-11-21:* `AlbumEvaluationStatus` has four values and its consumers
are `AlbumsViewModel` (which copies it into `AlbumListItem`), `AlbumGridItem`'s exhaustive
`when (status)` and `AlbumDetailScreen`'s `== UNEVALUATABLE` for the Newer-app notice band.
`AlbumInfoDialog` reads `AlbumRuleSummary`, built from the parsed rule, not the status. No `ui/` file
has a `when` over clause types or over `UnevaluatableReason`.

- **A cause on `UNEVALUATABLE`, not a sibling status.** `AlbumEvaluationStatus` keeps its four
  values. `AlbumRuleEvaluation.kt` gains `enum class AlbumUnevaluatableCause { RULE_NOT_UNDERSTOOD,
  PEOPLE_UNAVAILABLE_FACE_DATA_OFF, PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE, ANCHOR_NOT_FOUND }`, and
  `AlbumRuleEvaluation` a required `unevaluatableCause`, non-null exactly when the status is
  `UNEVALUATABLE` — checked in its `init`. The two "people unavailable" reasons are two values
  because Amendment 4's notice names which. `UNEVALUATABLE`'s doc comment is widened, as Amendment 4
  requires: the rule contributes nothing, because this build does not understand it or a `people`
  clause cannot be evaluated here; the cause says which.
  *Why:* everything Amendment 4 says of an unevaluatable album holds "for both causes without
  exception" — additions minus exclusions, never widened, never deleted — so it is one state with a
  reason, the shape `AlbumRule.Unevaluatable(raw, reason)` already has. A sibling status would
  compile at the `==` in `AlbumDetailScreen` and silently drop the notice band for the new statuses;
  only `AlbumGridItem`'s `when` would catch it.
- **The mapping** (decision 42; Amendment 4's *One notice per album* order). Per album, the first row
  that applies:

  | Condition | Status | Cause |
  |---|---|---|
  | no rule | `MANUAL` | — |
  | the rule did not parse | `UNEVALUATABLE` | `RULE_NOT_UNDERSTOOD` |
  | a `people` clause, and the availability is face data off | `UNEVALUATABLE` | `PEOPLE_UNAVAILABLE_FACE_DATA_OFF` |
  | a `people` clause, and the availability is device incapable | `UNEVALUATABLE` | `PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE` |
  | live media not read yet (join null) | `WAITING_ON_INDEX` | — |
  | some anchor **not found** | `UNEVALUATABLE` | `ANCHOR_NOT_FOUND` |
  | some item unsettled and no clause a settled no-match for it — a waiting anchor, a pending image, people input not loaded | `WAITING_ON_INDEX` | — |
  | otherwise | `COMPLETE` | — |

  The availability is tested before the join because it needs no media: "before any anchor"
  (Amendment 4). One anchor not found makes the album unevaluatable even while another waits, as one
  unknown clause does. In every `UNEVALUATABLE` row the members are the additions minus the
  exclusions, as today. **Waiting is** the anchor photo not yet face-indexed at the current pipeline
  version, its row not yet written, the triples not yet agreeing, or the chosen face not yet grouped;
  **not found is** the photo gone or neither an image, its inference failed, or no face at IoU ≥ 0.5
  once its state is current.
- **`ui/` in 3.5: one file, and no presentation.** Only `ui/AlbumsViewModel.kt` changes, for the new
  parameter and the wiring. `AlbumGridItem` and `AlbumDetailScreen` compile unchanged — no status
  value is added — and every cause shows what `UNEVALUATABLE` shows today, the Newer-app notice;
  waiting shows what it shows today. `AlbumRuleSummary` picks clauses with `filterIsInstance`, so a
  rule's `people` clauses are simply absent from the Album info dialog — compiles, and is 3.6's. **The cause stops at `AlbumRuleEvaluation`:**
  carrying it into `ComputedAlbum` and `AlbumListItem`, the notices' wording and presentation, and
  their strings in every shipped locale at once are 3.6's. *The interim is safe because it is
  unreachable:* the Newer-app wording is wrong for both new causes (Amendment 4), but no build before
  3.6's picker writes a `people` clause, so only a test or a hand-edited database reaches it. **3.6
  must branch on the cause before any build that can write a `people` clause ships.**
- **Availability is an input, derived outside the evaluator.**
  `enum class PeopleAvailability { AVAILABLE, FACE_DATA_OFF, DEVICE_INCAPABLE }` sits with the
  evaluator; the pure derivation, `peopleAvailabilityOf(faceDataEnabled: Boolean, capability:
  FaceCapabilityState?, currentKey: String)`, and the source that reads it, in a new
  `data/PeopleAvailability.kt`:
  1. the face-data setting off → `FACE_DATA_OFF` (ADR-0024 §4). First when both apply, because it is
     the one the user can fix;
  2. a capability record for **the current key** with `capable == false` → `DEVICE_INCAPABLE`;
  3. otherwise `AVAILABLE` — no record, a record for another key, or a check started and unfinished
     (`capable` null). **Unknown is not incapable.** *Why:* ADR-0023 §3 records one answer per key —
     another build, pipeline or device is another question — and the check loads the pipeline, so
     evaluation must never run it (it is 3.6's, at app open). Reading unknown as unavailable would
     show "people unavailable" on the first open after every app update and on every restored device
     until the check ran — a wrong notice. Reading it as waiting is true: without an answer the worker
     runs no face pass, Tier C stays empty and the album waits, as ADR-0022 §5 says of a restore;
  4. **the disclosure is not read.** Not shown is not unavailable: the flag is per installation, so
     a restored device always starts there; 3.6 shows the disclosure at the next open, and a user who
     declines it switches face data off, which is rule 1. Until then the worker's own gate leaves Tier
     C empty and the album waits — for a while, not for ever, which is what separates it from
     ADR-0024 §4's reason for not waiting.

  **The exception, 3.6's to close:** `FaceCapabilityChecker` records nothing when this build has no
  pipeline for the device (`FacePipelineUnavailableException` — a phone without arm64-v8a) or runs out
  of memory, so on such a phone rule 3 holds for ever and a restored people album waits for ever —
  the failure ADR-0024 §4 names. 3.6's app-open check must leave a durable answer for every outcome
  that is not transient, or the availability must read an equally cheap fact; 3.5 does not guess one.
- **The source.** `PeopleAvailabilitySource` with `read(): PeopleAvailability`;
  `StoredPeopleAvailabilitySource` reads `FaceDataSettingStore`, `FaceDeviceStateStore.capabilityState()`
  and the current key; `InMemoryPeopleAvailabilitySource` is the test double, settable. It owns no
  file, so `AGENTS.md`'s preference-store table does not change. The key comes from a new
  `EasyGalleryApp.faceCapabilityKey()`, extracted from `createFaceCapabilityChecker` so the checker
  and the availability can never read two keys. `AlbumsViewModel` takes the source as a constructor
  seam, defaulting to the stored one on `EasyGalleryApp` and to `InMemoryPeopleAvailabilitySource`
  (`AVAILABLE`) otherwise, as its index store falls back to `InMemoryMediaIndexStore`.
- **When it is read:** on `evaluationDispatcher`, at every emission of the face projection and at
  `refreshPeopleAvailability()`, a new public method that bumps an epoch. Switching face data off
  deletes Tier C, so the projection emits and the album turns unavailable with no call. Switching it
  on, and the check's result, change nothing in Tier C, so **3.6 calls the refresh** — from the
  switch, after the capability check, and on resume (the precedent is
  `FavouritesViewModel.refreshCapabilityState()` from `MainActivity.onResume`). In 3.5 only tests call
  it.

#### Tasks — three, sequential, then verification and task P

Sequential because they share files: `AlbumRuleEvaluation.kt` is Task 1's and Task 2's,
`ui/AlbumsViewModel.kt` Task 2's and Task 3's. Each task compiles and passes its own tests before the
next starts; the definition of done runs once, in the verification phase. Nothing waits on a
measurement or on the maintainer. No string, no resource, no vocabulary term, no schema, no
dependency, no permission, no preferences file.

**Task 1 — the clause** (Coder). ✅ **Done 2026-11-21** — *3.5 — Built and verified 2026-11-21*
below; the brief is kept as it was. *Decided at 3.5 — the clause* and *building an anchor*.
- **Files:** `data/AlbumRule.kt` (`People`, its `init`, `anchoredAt`, the three reasons, the two
  redacted `toString`s); `data/AlbumRuleJson.kt` (parse and encode); `data/AlbumRuleEvaluation.kt` —
  **an interim branch only**, so the exhaustive `when` compiles: a `people` clause answers unsettled
  for every item, so a people album waits; Task 2 replaces it. Tests: `test/.../data/AlbumRuleTest.kt`,
  `test/.../data/AlbumRuleStorageTest.kt`, `test/.../data/AlbumRuleEvaluationTest.kt` (the interim
  branch, one test).
- **Exit criteria, each with a test:**
  1. `parse(encode(rule)) == rule` for one and several `people` clauses, mixed with `region` and
     `time`; a non-ASCII display name; box values `0`, `1`, `1f / 3`, `0.1f` and the smallest positive
     float — **the box read back bit for bit**;
  2. the encoded text has Amendment 4's key order, and no `Locale` changes it (run under a
     decimal-comma default locale);
  3. one test per strictness row of *the clause*, each asserting its exact reason: unknown key in the
     clause and in `box`; `uri` and `displayName` missing, not a string, empty; `size` and
     `dateModified` missing, a string, `1.0`, beyond `Long`; `pipeline` missing, `"1"`, `0`, beyond
     `Int`; `box` missing, an array, a member missing, a string member, `1e400`, `-0.1`, `1.1`,
     `left == right`, `top > bottom`; **two edges that differ in the text and equal as `Float`s give
     `INVALID_FACE_BOX`, not `UNEXPECTED_ERROR`**; a duplicate key inside `box` gives `DUPLICATE_KEY`;
  4. `pipeline` 99 parses;
  5. the `toString` of a `People` clause, of an `Evaluatable` rule holding one, and of an
     `Unevaluatable` rule whose raw text names a file, contain no URI, display name or box number —
     the reason still prints;
  6. `anchoredAt` copies the representative's identity, box and pipeline version;
  7. a people rule stored through the album store reads back equal (`AlbumRuleStorageTest`);
  8. the interim: a people album evaluates to `WAITING_ON_INDEX`, never `COMPLETE`.
- **Mutation checks**, each run, seen to fail the named test, and reverted: the box checks made on
  the parsed `Double` instead of the `Float` (criterion 3's equal-as-`Float` test); the box encoded
  with `String.format("%f")` (criteria 1 and 2); unknown keys not checked inside `box`; `People`'s
  default `toString` restored; `Unevaluatable`'s `raw` printed again.

**Task 2 — the projection and the pure evaluation** (Coder). ✅ **Done 2026-11-21** — *3.5 — Built
and verified 2026-11-21* below; the brief is kept as it was, its caller list corrected. *Resolution
and evaluation*, and the cause and the mapping of *status, causes and availability*.
- **Files:** `data/MediaIndexTierC.kt` (the snapshot types, redacted); `data/MediaIndexDao.kt` (the one
  query and its row type); `data/MediaIndexStore.kt` (`observeFaceEvaluationProjection` on the
  interface, Room, and the in-memory store with its revision flow); `data/MediaIndexLiveJoin.kt`
  (`indexedIdentity`, the lazy lookup and by-URI items); **new** `data/PeopleClauseEvaluation.kt`
  (`PeopleAvailability`, `PeopleEvaluationInput`, anchor resolution with the IoU and the tie rule, the
  per-item answers); `data/AlbumRuleEvaluation.kt` (`AlbumUnevaluatableCause`, the field and its
  `init`, the widened doc comment, the `people` parameter, the mapping; the interim branch removed);
  `ui/AlbumsViewModel.kt` — **compile only**: its one call passes `people = null`. Tests:
  `test/.../data/MediaIndexDaoTest.kt`, `test/.../data/MediaIndexTierCStoreTest.kt` (through its
  `forEachStore`, both stores), **new** `test/.../data/PeopleClauseEvaluationTest.kt`,
  `test/.../data/AlbumRuleEvaluationTest.kt`, and the callers the two new required values break:
  `test/.../data/AutomaticAlbumFacetsTest.kt`, `test/.../ui/AutomaticAlbumPickerStateTest.kt`,
  `androidTest/.../ui/NewAutomaticAlbumContentTest.kt` and, *corrected 2026-11-21 from the build*,
  `androidTest/.../ui/RegionAlbumEvaluationDeviceTest.kt` — fixed, not defaulted.
- **Exit criteria, each with a test:**
  1. the snapshot holds only faces at the asked pipeline version; a state with no faces is present
     with an empty list; no embedding is read; both stores return the same snapshot;
  2. **it re-emits, in both stores, after** `writeFaceIndexResults`, `writeFaceGroups`,
     `healMediaUri`, an `upsert` that drops Tier C, `deleteByMediaUri`, `deleteByVolume` and
     `deleteAllFaceData`;
  3. the join's `indexedIdentity` is the winning row's — the direct match's when a healed row also
     reaches the item — and null for an item no row reached;
  4. every resolution outcome of *An anchor resolves*, one test each, steps 1 to 8; IoU exactly 0.5
     accepted and just under rejected; **a tie goes to the lower face id with the faces given in
     descending id order**;
  5. every row of the per-item table, one test each; three triples that disagree give waiting, never
     not found;
  6. every row of the mapping table, in order: face data off beats an anchor not found; an anchor
     not found beats another anchor waiting; unavailability with the join still null is
     `UNEVALUATABLE`; a people clause with `people = null` waits;
  7. combined: two people, both in the picture, AND a region AND a time range — members exactly the
     images with a face in each group, in the region, in the range; a GIF and a video in the library
     leave it `COMPLETE`;
  8. `AlbumRuleEvaluation`'s `init` rejects `UNEVALUATABLE` without a cause, and a cause on any other
     status; an unparsed rule's cause is `RULE_NOT_UNDERSTOOD`;
  9. every Stage 2 evaluation test passes unchanged apart from the added argument.
- **Mutation checks:** `>` for `≥` at 0.5; the tie taken in list order; `groupingVersion` ignored;
  a GIF answered unsettled (criterion 7 never completes); the triple check dropped (criterion 5);
  the unavailable and not-found checks swapped; the in-memory revision not bumped in
  `writeFaceGroups`; the Room query's pipeline-version filter dropped.

**Task 3 — availability and the wiring** (Coder). ✅ **Done 2026-11-21** — *3.5 — Built and verified
2026-11-21* below; the brief is kept as it was. *Status, causes and availability*: the source, the
refresh, and *only a people album subscribes*.
- **Files:** **new** `data/PeopleAvailability.kt` (`peopleAvailabilityOf(faceDataEnabled: Boolean,
  capability: FaceCapabilityState?, currentKey: String)`, `PeopleAvailabilitySource`,
  `StoredPeopleAvailabilitySource`, `InMemoryPeopleAvailabilitySource`); `EasyGalleryApp.kt`
  (`faceCapabilityKey()`, extracted, used by `createFaceCapabilityChecker` too); `ui/AlbumsViewModel.kt`
  (the source seam, the shared people flow, `refreshPeopleAvailability()`, the combine for people
  albums only). Tests: **new** `test/.../data/PeopleAvailabilityTest.kt`;
  `test/.../ui/AlbumsViewModelTest.kt`; **new** `test/.../ui/PeopleAlbumReevaluationTest.kt`,
  Robolectric, set up as `AlbumsViewModelMediaStoreVersionApi28Test` is.
- **Exit criteria, each with a test:**
  1. the derivation, every rule of *Availability* — off; incapable under the current key; incapable
     under another key is `AVAILABLE`; no record; `capable` null; off beats incapable; the disclosure
     never read — and the stored source over the in-memory stores;
  2. a people album is `UNEVALUATABLE` while the source says face data off, and after
     `refreshPeopleAvailability()` with it on is `WAITING_ON_INDEX` — no Tier C write between;
  3. with the in-memory index store, a people album goes from `WAITING_ON_INDEX` to `COMPLETE` with
     the expected members on `writeFaceGroups` alone;
  4. **a manual album and a region or time album never subscribe to the face projection**, counted
     as the existing delegating store counts projection subscriptions;
  5. **Room invalidation** (`PeopleAlbumReevaluationTest`): over an in-memory Room index database, a
     people album waits while its anchor's face is ungrouped, and `writeFaceGroups` alone — no Tier A
     write — moves it to `COMPLETE` with the expected members.
- **Mutation checks:** the face projection combined into every rule album (criterion 4); the
  availability read once at construction (criterion 2); the Room store's face projection replaced
  by a one-shot read (criterion 5).

**Verification phase — once, over the integrated change; it owns the gate.** ✅ **Done 2026-11-21**
— the gate is under *3.5 — Built and verified 2026-11-21* below. The definition of
done, steps 1 to 5. `adb devices` settles step 4 and the report says which ran: 3.5 adds no
behaviour that only a device shows — the evaluator is pure and the Room flow is proved under
Robolectric — so the device run is the existing suite, `NewAutomaticAlbumContentTest`'s changed
helper included. Also: `git status app/schemas` shows nothing, so both databases keep their versions;
the debug and release merged manifests byte-identical to the build before 3.5 (3.4's figures are
under *3.4 — Built and verified 2026-11-21*), since no dependency changes; no `Log` call in the new or
changed `data/` code; and `git diff --stat` against `git diff --ignore-all-space --stat`, for line
endings. No task runs `connectedDebugAndroidTest` itself.

**Not 3.5's** — each 3.6's unless named: the People criterion, its thumbnails, its visibility and the
picker's call to `anchoredAt`; the disclosure and the Settings switch; the app-open capability check
and the start-up deletion's call; the callers of `refreshPeopleAvailability()`; carrying the cause to
`AlbumListItem`, the notices' wording and presentation, and their strings in every locale; `people`
clauses in `AlbumRuleSummary` and the Album info dialog; how many people one album may hold under the
16 KiB cap; a durable capability answer on a phone with no pipeline; rule editing's D6.

**Task P — afterwards** (Planner). ✅ **Done 2026-11-21:** landed in `ARCHITECTURE.md` §2, §3, §6
and §11, `AGENTS.md`, ADR-0017 Amendment 4's *Built* note, ADR-0022's §3 step 4 note and *Built*
note, ADR-0024 §4's note, ADR-0018 Amendment 5's *Built* note; this plan and [albums.md](albums.md)
§7 last. The brief was — on the built code, not on reports: `ARCHITECTURE.md` §3 (the two
new `data/` files, `JoinedMedia.indexedIdentity`), §6 (the second flow, people albums only) and §11
(the interim notice until 3.6, the non-arm64 wait, the face projection's unmeasured cost); `AGENTS.md`
(*The two databases*' face-data paragraph, which says nothing in `ui/` reads face data — now
`AlbumsViewModel` does); ADR-0017 Amendment 4, a *Built* note (the cause on `UNEVALUATABLE`, the
three reasons, the widened doc comment); ADR-0022 §3 step 4, a note naming the tie rule, and a *Built*
note; ADR-0018 Amendment 5, a note on the new query with no schema change; [albums.md](albums.md) §7's
Stage 3 row; this plan — the step row, a *Built and verified* section, the handoff, and any item for
3.7; the test conventions only if a convention changed. No ADR is expected: every choice here is one
the records leave to 3.5.

### 3.5 — Built and verified 2026-11-21

Built as Tasks 1, 2 and 3 above, then task P. What landed is not restated here: it is
[ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendment 4's
*Built* note (the clause, its three new reasons, the cause on `UNEVALUATABLE`),
[ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md)'s §3 step 4 note
and *Built* note (the tie to the lower face id),
[ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md) Amendment 5's *Built*
note (`observeFaceEvaluationProjection`) and
[ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §4's note, with
`ARCHITECTURE.md` §2, §3, §6 and §11 for where each piece sits: `data/PeopleClauseEvaluation.kt`,
`data/PeopleAvailability.kt`, `JoinedMedia.indexedIdentity` and the join's one shared lookup, and in
`AlbumsViewModel` the second shared flow, collected by people albums only, and
`refreshPeopleAvailability()`. **No schema change** in either database, and no string, dependency,
permission or preferences file.

**The gate — the definition of done, once, over the integrated change:** `compileDebugKotlin` OK;
the unit suite, 78 files, 1293 tests, 0 failures — 1292 at the gate, and one added by the review's
follow-up; `compileDebugAndroidTestKotlin` OK; a device was attached, the SM-G990B, so
`connectedDebugAndroidTest` ran: 266 tests, 0 failures, 3 skipped — the three opt-in measurements.
It ran before the follow-up, whose changes are covered by unit tests only. The debug and release
merged manifests byte-identical to 3.4's (debug md5 `8856dbd4b4257bd665c4593a325ba624`, release
`043a521e4d0a25724364c8b8b6ea6f8d`); `app/schemas` unchanged. **Mutation checks**, each run, seen to
fail its test and reverted: Task 1, 12; Task 2, 15; Task 3, 6; and 1 more for the negative-zero
follow-up. This document does not record tracking state.

**Review outcomes.** Two Reviewers; no hard violations. **Fixed:** a box edge of negative zero is
normalised to `+0f`, with a test; `computeAlbum`'s default for `people` removed; the availability
epoch updated atomically; one lookup removed from `peopleItemAnswer`; `MediaIndexLiveJoin` shares the
lookup built in `build()`; the depth comment corrected; the re-evaluation test strengthened with a
probe album. **Left, a one-line cleanup:** `peopleItemAnswer`'s `join` parameter, now unused —
declared in `data/PeopleClauseEvaluation.kt`, passed at its one call in `AlbumRuleEvaluation.kt`.
**Judgement calls, accepted as built:** recorded in `ARCHITECTURE.md` §11 and the ADR notes above;
also a box edge written just above 1 that rounds to `1f` is accepted — noted under *Decided at 3.5 —
the clause* — and `MediaIdentity`'s and `MediaHealingKey`'s `toString`s still print the URI and the
name, which 3.6's brief carries under ADR-0024 §6.

**Not proven by it** — each 3.6's or 3.7's, under *3.2 to 3.8*, or debt in `ARCHITECTURE.md` §11:
- a phone without arm64-v8a: nothing records an answer there, so a restored people album waits for
  ever — 3.6 must close it, 3.7 observes it if such a phone exists;
- the cost at production size: the face projection is a full snapshot per emission, re-run on Tier A
  and face-pass batches while a people album is on screen — 3.7 item 11;
- the cause's presentation: every cause still shows the Newer-app notice until 3.6 branches on it;
- the refresh: nothing outside tests calls `refreshPeopleAvailability()` yet — 3.6's;
- any of it through the UI: no build writes a `people` clause before 3.6's picker.

### 3.6 — The People criterion, the disclosure and the switch — refined 2026-11-21

Refined by the Planner on 2026-11-21 from the Designer's design of the same day, checked against the
code as it stands after 3.5. **The design was written to a scratch file outside the repository
(`/tmp/design36.md`), which is not durable, so this section is the record:** its decisions, wording
drafts, string keys, vocabulary proposals and file list are copied below in compact form, with its
reasoning kept only where a decision would be reversed without it. It does not restate the records it
builds on: [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) §1 to
§6; [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md) Amendments 3
and 4 (no notice for waiting; the causes, one notice per album);
[ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md) §1 and §5;
[ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md) and D2 (the chain stays);
[ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md) Amendment 2
(the picker counts over the evaluator's join);
[ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md) Amendment 1
(the Place data row, the licence row's pattern); [albums.md](albums.md) §8 decisions 29, 33, 35, 40,
41 and 42.

**The maintainer, 2026-11-21:** at most **8 people in one album** (D3, as recommended), and start 3.6.

**What waited on him — [ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md)
Amendment 1 — approved 2026-11-21, with option A at §2.** ~~Proposed.~~ Six points the records
reserve for him or leave open, each drafted with the Planner's and the Designer's recommendation: (1)
the disclosure's model and its *Turn off*; (2) ~~switching face data on from Settings marks a
never-shown disclosure shown~~ switching face data on from Settings when the disclosure was never
shown on this installation — **option A, chosen: the disclosure shows**; (3) the app-open
capability check, with a durable *incapable* when the device lists no arm64-v8a, and the start-up
deletion at app open; (4) the switch-off order, the memory drop and the not-scrubbed sentence; (5) the
disclosure's wording and the hiding rules; (6) the Place and Date counts cross-filtered by the chosen
people. ~~**He approves the amendment as a whole and reads the disclosure's wording**~~ **He approved
the amendment as a whole, the disclosure's wording unchanged** (*Strings*, group S2), in his words "I
approve the amendment and I choose the option you recommend". Nothing else here needs him. Tasks S1
and C built nothing the amendment governs and were built before it was approved; S2, A and B waited
for it and may now start (*Tasks* below).

**No new architectural pattern** under `AGENTS.md`, so no approval is asked on that ground:
- `FaceDataViewModel` is a feature-scoped ViewModel, the escape `AGENTS.md` names — `CreateFolderViewModel`,
  `BillingViewModel`, `AlbumsViewModel`, `FavouritesViewModel` and `MediaIndexingViewModel` are the
  precedent. The Tier C lock it takes is the one `EasyGalleryApp`'s comment already reserves for "the
  face-data switch" (ADR-0024 §3; ADR-0018 Amendment 7 §5), and its app-open check follows
  `FavouritesViewModel.refreshCapabilityState()`, called from `MainActivity.onResume`.
- The disclosure is hosted at `MainActivity`'s root, after the screen chain, as the Manage-media
  rationale dialog (driven by `FavouritesViewModel`) and the Add to album dialog already are.
- No navigation change (D1 below); no schema change in either database; no dependency, permission,
  preferences file or store — the setting, the flag and the record are 3.3's stores. Strings in all 17
  shipped locales (`LocaleHelper.supportedLanguageTags`).

#### Decided at 3.6 — D1 and the People picker

- **D1 — a third tab, People, on the New automatic album screen:** Place | Date | People
  (`TAB_PLACE` 0, `TAB_DATE` 1, `TAB_PEOPLE` 2). The Designer's answer, 2026-11-21. No screen of its
  own, no navigation change: ADR-0013 is untouched and D2 is not reopened, its trigger 1 not firing.
  *Why:* one draft — name, choice chips, match-count line, FAB — so People ANDs with Place and Date
  and the count stays live; a screen of its own is the second level the chain does not model, and
  would have to carry the draft across.
- **Offered only when** the face-data setting is on **and** the capability record for the current key
  says `capable == true`. Otherwise the screen is exactly today's two tabs — no entry point, no teaser
  (E9). *Unknown is hidden here and available to evaluation (3.5's rule 3), on purpose:* evaluation
  must not call a never-checked phone unavailable, which would put a wrong notice on every people album
  after every update; the picker must not offer a tab that may vanish when the check ends. Amendment
  1 §5.
- **If it stops being offered while open** — the check ends incapable, the setting goes off — the tab
  falls back to Place and the draft's people and the picker's people data are dropped. `selectedTab` is
  `rememberSaveable`: a restored 2 is coerced to 0 when People is not offered.
- **Tabs:** three weighted `Role.Tab` boxes; horizontal padding 16 dp → 8 dp; label `titleSmall`, one
  line, ellipsis, the full label as content description. Device check: "Personen" (de) and "Persone"
  (it) at font scale 2.0 on a 360 dp width.
- **The grid:** `LazyVerticalGrid(GridCells.Adaptive(96.dp))`, tag `new_automatic_album_people_grid`;
  content padding 16 dp at the sides, 8 dp top and **96 dp bottom — the FAB clearance lives here,
  never in a strip**; 12 dp spacing both ways. Full-span header items, each keyed, in this order: (a)
  the Status area and Index band — the same component and expanded flag as the other tabs, only when
  the band is not Hidden; its Running text is this tab's own; (b) the People hint, tag
  `new_automatic_album_people_hint`, a polite live region — the hint below 8 chosen, the limit sentence
  at 8, `%1$d` filled from `MAX_PEOPLE_PER_ALBUM` and never typed into a translation; (c) the tiles.
- **Person tile** (`PersonTile`; tag `new_automatic_album_person_<index>`, index = position in the
  list, **never the group id**): an 80 dp circular face crop, centred in a cell at least 96 dp wide;
  under it a 14 dp photo icon and the group's photo count (`NumberFormat.getIntegerInstance(locale)`,
  `labelMedium`). The count is `FaceGroupSummary.imageCount`, global, not cross-filtered: a number is
  not a label (decision 40), and it says why the order is what it is. **The order is exactly
  `listFaceGroups`'s** — photo count descending, newest date, group id — with no re-sort, search or
  filter.
- **Selection:** the whole cell `toggleable(role = Role.Checkbox)`; selected is a 3 dp primary ring and
  a `CheckCircle` badge at TopEnd (tag `new_automatic_album_person_selected_<index>`), with **no
  scrim**, which would dim the face being recognised — unlike the Media tile, on purpose.
- **The cap, 8 (D3):** at 8 chosen every unselected tile is disabled (alpha 0.38, no click) and the
  hint says why — no toast, no error; selected tiles stay enabled so one can always be removed. The
  state's toggle returns false at the cap, the first backstop; `createAutomaticAlbum` catches the
  encoder's `require` (*3.2 to 3.8*, 3.6 item 4), the second.
- **The face crop:** from the representative's image and `FaceBox` (fractions of the upright frame,
  ADR-0022 §1). The box's centre; side = 1.8 × the larger of its width and height **in pixels**;
  clamped to the image's short side; **shifted** to stay inside the image rather than shrunk; square.
  Computed at display time, never stored; the rectangle arithmetic in its own Android-free file,
  unit-tested. The mechanism is the Coder's, within these limits: a Coil request with a custom
  `Transformation`, or an `ImageDecoder` path; decode capped near 1536 px; EXIF-upright;
  **`diskCachePolicy(DISABLED)`** — Coil writes its disk cache only from network fetches, so this is
  belt and braces, but it makes ADR-0024 §6's "never to a disk cache" checkable in code and in a test;
  cancelled when the tile leaves composition. Loading, image gone and decode failed look alike: a
  neutral circle with a person icon, and the tile stays pickable — the anchor is data, not pixels.
- **Accessibility:** one merged node; content description from the plural
  `automatic_album_person_description` ("Person 3 of 41, 12 photos", quantity = the photo count); the
  checkbox role carries the checked state. **No URI, box or group id in any semantics, tag or
  string.** RTL by construction: TopEnd, no absolute left or right.
- **One People chip**, after Place and Date (`Icons.Default.People`, a plural label, tag
  `new_automatic_album_people_chip`, content description "Clear people"), clears every chosen person;
  one person is removed by tapping its tile. Per-person chips rejected: up to 8 chips of repeated
  crops, a second way to do what the tiles do.
- **AND, in the UI:** the hint says "all appear in the same photo"; **the match-count line is the
  evaluator's member count for people + place + date — a must**, the stage's exit criterion; the Place
  and Date lists' counts reflect the chosen people (Amendment 1 §6, adopted 2026-11-21). The People grid is
  **not** cross-filtered (decision 40, global counts), so a place and a person can give 0: the line says
  0 and Create stays enabled — an empty album is legal, and indexing may be partial.
- **Hint with nothing chosen:** `automatic_album_hint_choose_with_people` replaces
  `automatic_album_hint_choose` when People is offered. **FAB:** `canCreate` = a valid name **and** (a
  region, a time, or at least one person).
- **The tab's states**, each tagged: Loading (`new_automatic_album_people_loading`); Ready, the grid;
  Empty — `listFaceGroups` returned nothing, which covers "pass not run", "running" and "nobody in two
  photos" — one message (`new_automatic_album_people_empty`), and with the band Hidden a tonal Index now
  button under it (`new_automatic_album_people_index_now`, reusing `settings_index_now`), since Index
  now runs the face pass once the disclosure is shown; Failed — the one-shot query threw — a message and
  Retry (`new_automatic_album_people_failed`, `new_automatic_album_people_retry`). One person is a grid
  of one, not a state. The tab's states are its own: the screen-wide `LOADING_NAMES`, `NAMES_FAILED` and
  `WAITING_FOR_MEDIA` keep governing the lists and the count line, and do not gate the grid.
- **Freshness:** loaded at the tab's first reveal in a visit; reloaded when indexing leaves RUNNING;
  **not reloaded while any person is chosen** — a regroup can change group ids, and the anchors come
  from the list the user saw (decision 41). The draft keeps each chosen `FaceGroupSummary`, not only its
  id, and builds the clause with `AlbumRuleClause.People.anchoredAt(summary.representative)`.
- **Where the state lives:** in `AutomaticAlbumPickerState`, owned by `AlbumsViewModel`, dropped by
  `close()` with the rest of the draft — nothing in `GalleryViewModel`, no new state holder. **The picker
  subscribes to the face projection only while at least one person is chosen**, so 3.5's "only a people
  album subscribes" still holds otherwise. The cross-filter is one set of matching items computed per
  emission from that snapshot, then a set lookup per item inside `AutomaticAlbumFacets` — never a face
  lookup per facet entry; that is why the cost argument for dropping it does not hold (Amendment 1 §6).

#### Decided at 3.6 — the disclosure, the switch and app open

Each point marked **(Am. 1 §n)** is decided in ADR-0024 Amendment 1, ~~drafted and binding nothing
until the maintainer approved it~~ adopted 2026-11-21 on his approval; what is unmarked is
presentation the adopted records leave to 3.6.

- **The Face data disclosure** (`FaceDataDisclosureDialog`, content tag `face_data_disclosure`), hosted
  at `MainActivity`'s root. **Shown while all hold:** the media permission is granted, the setting is
  on, the capability record for the current key says capable, the disclosure has not been shown on
  this installation, and the Full-screen viewer is closed — it waits for the viewer rather than
  covering it. Derived from the stores by `FaceDataViewModel`, so a recreate shows it again until a
  button is pressed. Never on an incapable or unknown device (E6). **Shown, not consented (Am. 1
  §1):** two buttons — **OK** (`face_data_disclosure_ok`) marks it shown, durably, before it closes;
  **Turn off** (`face_data_disclosure_turn_off`) marks it shown and runs the switch-off below without
  its confirmation, since nothing can exist before the first pass and the deletion is idempotent. No
  *Not now*: a deferral would leave the worker's gate shut with no end. **Not dismissable** by Back or
  an outside tap (`dismissOnBackPress` and `dismissOnClickOutside` false): "shown" means a button was
  pressed, and an accidental tap must not use up a once-per-installation statement. The body scrolls,
  as the Place data dialog's does, so the buttons stay visible at font scale 2.0. **It starts no run:**
  the first face pass is the next periodic run, or the user's Index now. *Why:* heavy work started by
  an OK is a surprise, and the app never starts Tier A by itself either.
- **Its info mode**, opened from the Face data info row in Settings: the same body, one button,
  Close (`face_data_disclosure_close`), no side effect.
- **The Face data area in Settings** (`FaceDataSettingsArea`, tag `face_data_area`, heading "People"),
  directly under the Media index area and above App, with the same divider. Two rows: the **Face data
  row** (`face_data_row`) — a switch, the whole row `toggleable(role = Role.Switch)`, at least 56 dp,
  `Icons.Default.Face` — and the **Face data info row** (`face_data_info_row`, "About face data").
  `SettingsItem` gains an optional trailing slot, default null, so existing calls are unchanged.
  **The area is hidden unless the capability record for the current key says capable**, whatever the
  setting — ADR-0024 §1, "not offered" — so a user who switched it off can still switch it on. The
  subtitle follows the state: on; off; deleting (row disabled, a progress indicator in place of the
  switch); and, after a deletion that returned `scrubbed == false`, the cleanup sentence — transient,
  UI state only, a polite live region; true is silent, the Off subtitle being the confirmation. *Why
  the sentence is true:* the rows are gone either way and the start-up deletion repeats the scrub
  (ADR-0018 Amendments 6 and 7 §5). **No Tier C figure in Settings**, on or off (ADR-0024 §4); the scan's
  progress stays in the Media index area.
- **Switching off (Am. 1 §4).** The tap opens the **Delete face data dialog** (`DeleteFaceDataDialog`,
  `face_data_delete_dialog`, buttons `face_data_delete_confirm` in the error colour and
  `face_data_delete_cancel`) before anything changes; the switch stays on until it is confirmed, and
  Cancel leaves it on (E8). Confirmed, `FaceDataViewModel`, in its own scope: persist off → take
  `faceIndexLock` with `lock()`, which waits for a running pass, itself stopped at its next check →
  `deleteAllFaceData()` → drop what memory holds — the Coil memory cache cleared, and the picker's
  people data and chosen people, which go when People stops being offered → release the lock →
  `AlbumsViewModel.refreshPeopleAvailability()`. Cut short by the process ending, it is finished by
  the start-up deletion at the next start, the setting being already off (ADR-0024 §3).
- **Switching on:** persist on → `refreshPeopleAvailability()`; no confirmation, and no dialog when
  the disclosure was already shown on this installation (E10). **If the disclosure was never shown on
  this installation** — a restore whose backed-up setting was off — ~~the Planner recommends that the
  disclosure then shows, which the derivation above does with no extra code; the Designer recommended
  that switching on marks it shown instead. The maintainer chooses~~ **the disclosure shows: option A,
  chosen by the maintainer on 2026-11-21 (Am. 1 §2)**; the Designer's option B, switching on marks it
  shown, was not chosen. Precisely: the switch persists the setting on and does **not** call
  `markDisclosureShown()`; the disclosure's condition above — permission granted, setting on,
  capable, not shown, viewer closed — now holds, so the dialog appears at `MainActivity`'s root over
  Settings, from the same derivation and with no code of its own; until a button is pressed the
  worker's gate stays shut, and Tier C is not written; **OK** marks it shown, the first face pass
  being the next periodic run or Index now; **Turn off** marks it shown and runs the switch-off above
  without its confirmation, so the setting returns to off and the disclosure is used up. Once per
  installation is unchanged: this is the installation's first disclosure, and a later switch-on shows
  none.
- **At app open (Am. 1 §3),** in `FaceDataViewModel`'s creation, off the main thread — so once per
  Activity, not on a rotation, and free when an answer is stored: (i) **the capability check** — read
  the stored record first; only when there is no answer for the current key, take `faceIndexLock`
  with `lock()` and run `FaceCapabilityChecker`, which reads the record again. The lock because the
  worker takes it before its own check, and two checks at once load the models twice. Whatever the
  setting, since the area above needs the answer. **No arm64-v8a in `Build.SUPPORTED_ABIS`: the
  checker records incapable under the current key without loading anything** — given the ABI list by
  its caller, so it stays pure Kotlin and the worker's check records the same answer; the key names
  the build, so a later build that ships other ABIs is asked afresh. Out of memory stays transient,
  and so does `FacePipelineUnavailableException` on a device that does list arm64-v8a. This closes
  *3.2 to 3.8*, 3.6 item 1. (ii) **The start-up deletion**, when the setting reads off:
  `createFaceDataStartupDeletion().run()`, under the lock, its result not shown (3.6 item 2). (iii)
  Then `refreshPeopleAvailability()`.
- **`refreshPeopleAvailability()`'s callers** (3.6 item 3): after the app-open check; from
  `MainActivity.onResume`, beside `favouritesViewModel.refreshCapabilityState()`; and from both
  directions of the switch, the disclosure's Turn off included.
- **Nothing identifying anywhere:** no log line in `FaceDataViewModel`; no URI, box, group id or
  count of faces in any string or semantics.

#### Decided at 3.6 — the notices, the licence row and the Album info dialog

- **One status notice per album**, chosen by one pure function over `AlbumListItem.status` and its
  cause, in a new `ui/components` file, used by the tile, the row and the detail view alike — three
  surfaces branching separately would drift:

  | Status, cause | Notice | Tag | Icon |
  |---|---|---|---|
  | `UNEVALUATABLE`, `RULE_NOT_UNDERSTOOD` | Newer-app notice, unchanged | `album_rule_unevaluatable_notice` | `SystemUpdate` |
  | `UNEVALUATABLE`, `PEOPLE_UNAVAILABLE_FACE_DATA_OFF` | Face-data-off notice | `album_people_face_data_off_notice` | `FaceRetouchingOff` |
  | `UNEVALUATABLE`, `PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE` | Device-incapable notice | `album_people_device_incapable_notice` | `FaceRetouchingOff` |
  | `UNEVALUATABLE`, `ANCHOR_NOT_FOUND` | Person-not-found notice | `album_people_anchor_not_found_notice` | `PersonOff` |
  | `WAITING_ON_INDEX`, `COMPLETE`, `MANUAL` | none — decisions 29 and 42, ADR-0017 Amendment 3 | — | — |

  The hidden-members notice is unchanged and stacks below the status notice. Tile and row show the
  short wording, also as `stateDescription` as today; the Album Detail View shows the full wording in
  `AlbumNoticeBand`, a polite live region as today; the Album info dialog repeats none. **No action in
  any notice:** the Face-data-off wording names Settings, one tap away on the Main view bar, so no
  callback is threaded through `AlbumDetailContent`; Person-not-found offers no re-pick, rule editing
  being its own plan — its text says how it clears instead. No wording interpolates an anchor or a
  `MediaIdentity` (ADR-0024 §6). *Observation, not changed:* after a restore every people album may
  wait for days with nothing said, as decision 42 accepted.
- **The cause carried** (3.6 item 5): `AlbumRuleEvaluation` → `ComputedAlbum` → `AlbumListItem`, a
  new required, nullable field — fixed in every caller, not defaulted.
- **The Face licences row** (`face_licences_row`, "Face recognition licences", subtitle "OpenCV, YuNet
  and SFace", `Icons.Default.Description`) — the **last row of the Legal section**, after the Place
  data row, by the same pattern (ADR-0019 Amendment 1; E5); **never hidden**: the models and OpenCV are
  in the APK whatever the setting or the device. Named for what it covers: a row called "Open-source
  licences" would claim a completeness the app does not have — Compose, Coil, Room and Media3 have no
  notice surface; that gap is older than Stage 3 and not 3.6's. The **Face licences dialog**
  (`FaceLicencesDialog`, content tag `face_licences_notice`, OK): one translated intro sentence, then
  the five files under `assets/face/`, each under its file name as a heading, in this order —
  `NOTICE.txt`, `LICENSE-OpenCV-Apache-2.0.txt`, `LICENSE-SFace-Apache-2.0.txt`,
  `LICENSE-YuNet-MIT.txt`, `THIRD_PARTY_NOTICES.txt`. Verbatim and never translated, like the GeoNames
  text. They are large (`THIRD_PARTY_NOTICES.txt` about 58 KB) and hard-wrapped: a `LazyColumn` of
  paragraphs split on blank lines, monospace `bodySmall`, read on `Dispatchers.IO` with
  `produceState`; loading shows progress, a failure a sentence and OK. Device check: a `LazyColumn` in
  `AlertDialog`'s text slot gets a bounded height and OK stays visible at font scale 2.0 and in
  landscape; the fallback is a `Dialog` with a `Surface` capped by `heightIn(max)`.
- **The Album info dialog:** `AlbumRuleSummary.Automatic` gains `AlbumRuleCondition.People(count)` —
  pure data, built from the rule's `people` clauses, no anchor in it — after Place and Date. Its entry
  (`album_info_people`): label "People", value the plural count, and a second line, "Only photos that
  show all of them." Shown whether or not people are available now, since it describes the rule; a
  people-only rule still shows the Automatic heading.

#### Strings — keys and English drafts

English **intent**, the Designer's drafts; the implementer of each strings task writes them into all
17 folders (`values` and the 16 of `LocaleHelper.supportedLanguageTags`, Indonesian in `values-in`).
**A native-speaker review stays outstanding** — Stage 2's precedent — and matters most for S2, before a
release. `LocaleResourceParityTest` enforces the keys, the placeholders and each locale's plural
categories (`PLURAL_CATEGORIES`); every plural item carries its count. Never translated: the licence
files' text, "OpenCV", "YuNet", "SFace". Reused, not added: `action_ok`, `action_close`,
`action_retry`, `settings_index_now`.

**S1 — ~~may be written before the amendment is approved~~ ✅ written 2026-11-21 (task S1):**
- *People picker:* `automatic_album_section_people` "People"; `automatic_album_hint_choose_with_people`
  "Choose a place, a date, people, or any mix of them"; `automatic_album_people_hint` "Pick the people
  who should all appear in the same photo. You can pick up to %1$d."; `automatic_album_people_limit`
  "You have picked the most people an album can have (%1$d). Tap a person to remove them.";
  `automatic_album_people_loading` "Finding people"; `automatic_album_people_failed` "People could not
  be loaded"; `automatic_album_people_empty` "No people to pick yet. Someone appears here once their
  face is found in at least two photos. Faces are found in the background, a little at a time, mostly
  while the phone is charging."; `automatic_album_people_index_running` "Reading your photos and
  videos, including finding faces. This can take a while."; plural
  `automatic_album_person_description` "Person %1$d of %2$d, %3$d photo" / "… %3$d photos" (quantity =
  the photo count); plural `automatic_album_people_chip` "%1$d person" / "%1$d people";
  `automatic_album_clear_people` "Clear people".
- *Album info:* plural `album_info_people_count` "%1$d person" / "%1$d people"; `album_info_people_note`
  "Only photos that show all of them."
- *Notices, full and short (short at most about 60 characters: a fixed square tile, three lines at 11
  sp):* `album_people_face_data_off_notice` "Face data is turned off, so the people in this album can't
  be found. Only the items you added by hand are shown. Turn face data on in Settings to bring the rest
  back." / `_short` "Face data is off. Only items added by hand are shown.";
  `album_people_device_incapable_notice` "This phone can't find people in photos, so the people in this
  album can't be found here. Only the items you added by hand are shown." / `_short` "People
  unavailable here. Only items added by hand are shown."; `album_people_anchor_not_found_notice` "A
  person in this album can't be found in your photos, perhaps because the photo they were chosen from
  is gone. Only the items you added by hand are shown. If the photo comes back the album fills in
  again; otherwise create the album again." / `_short` "A person can't be found. Only items added by
  hand are shown."
- *Licences:* `settings_face_licences` "Face recognition licences"; `settings_face_licences_credit`
  "OpenCV, YuNet and SFace"; `face_licences_intro` "Notices for the software and models that find faces
  on this phone."; `face_licences_loading` "Loading notices"; `face_licences_failed` "The notices could
  not be read."

**S2 — ~~waits for the approval of Amendment 1, which governs what they say~~ governed by Amendment
1, approved 2026-11-21 with this English unchanged — the text below is final English, not intent:**
- *The disclosure* (Am. 1 §5): `face_data_disclosure_title` "People in your photos";
  `face_data_disclosure_p1` "Easy Gallery finds and groups the faces in your photos, so that you can
  make albums of people."; `_p2` "This happens on your phone only. What it finds is never sent anywhere
  and is not included in backups."; `_p3` "Faces are never named. An album of people remembers one
  photo for each person you pick, and that is saved and backed up with the album." — ~~**recommended
  kept**~~ **kept, approved 2026-11-21**: anchors are not face data (decision 9, clarified) but are backed up (ADR-0024 §6), and
  without it `_p2` reads as if nothing about faces reached a backup; `_p4` "Finding faces takes time
  and mostly runs in the background while the phone is charging."; `_p5` "You can turn this off at any
  time in Settings, under Face data. Turning it off deletes everything that was found.";
  `face_data_turn_off` "Turn off".
- *Settings:* `settings_people` "People"; `settings_face_data` "Face data"; `settings_face_data_on`
  "Faces in your photos are found and grouped on this phone, so you can make albums of people.";
  `settings_face_data_off` "Off. Faces are not looked for and no face data is kept. Turn on to find and
  group faces on this phone, so you can make albums of people. Nothing leaves your phone.";
  `settings_face_data_deleting` "Deleting face data…"; `settings_face_data_cleanup_pending` "Face data
  is deleted. Cleaning up its files will finish the next time the app starts.";
  `settings_face_data_about` "About face data".
- *The Delete face data dialog* (Am. 1 §4): `face_data_delete_title` "Delete face data?";
  `face_data_delete_message_1` "This turns off face data and deletes the faces Easy Gallery found in
  your photos, on this phone. Your photos, places and dates are not affected.";
  `face_data_delete_message_2` "Albums of people stay, but show only the items you added by hand. They
  fill in again if you turn face data back on and your photos are scanned again, which can take a long
  time."; `face_data_delete_confirm` "Delete and turn off".

#### Vocabulary — the Designer's proposals, added in-change by the implementer

Collision-checked by the Designer against
[the UI vocabulary](../../.github/instructions/ui-vocabulary.instructions.md): "People", "Person",
"Face data" and "licences" do not occur in it. **Additions are in-change** — each task adds the terms
for what it builds, in the same change. **The edits to existing entries are not additions:** they are
routed to the Designer, who makes them in task P (`AGENTS.md`, *Documentation*).
- **New** — Task B: People picker (the third section; `new_automatic_album_people_tab`,
  `new_automatic_album_people_grid`); Person tile (`PersonTile`; a grid cell like the Folder, Media and
  Album tiles, deliberately not a "thumbnail", which already names the Folder thumbnail and the Album
  cover — the image inside is the *face crop*); People chip; People hint; the People picker's empty,
  loading and failed states. Task A: Face data area (heading "People"; not the Media index area); Face
  data row; Face data info row; Face data disclosure, with its info mode; Delete face data dialog, a
  sibling of the Delete album dialog; Face licences row and Face licences dialog, siblings of the Place
  data row and dialog. Task C: Face-data-off notice, Device-incapable notice and Person-not-found
  notice, siblings of the Newer-app notice and together the *status notices*, one per album; the People
  entry of the Album info dialog.
- **Edits, routed to the Designer:** Status area and Index band — "appears nowhere but these two
  pickers" becomes three, and the band's Running text differs on the People picker; New automatic
  album screen — "by place and date" becomes "by place, date and people", "two sections reached from
  tabs" becomes three, the third present only when face data is on and the device capable, the choice
  chips gain the People chip, the hint's wording; Automatic album — the rule adds people to "a place, a
  date range, or both"; *Pick* — the People picker picks up to eight, so "a single thing" widens;
  Settings screen — adds the Face data area and the Face licences row; Newer-app notice — no longer the
  only status notice; Album info dialog — the People entry.

#### Tasks — S1, C, S2, A and B, then verification and task P

**Order: S1 → C → *the maintainer approves Amendment 1* → S2 → A → B → verification → task P.**
~~**Where it stands, 2026-11-21:** S1 and C ✅ built and verified; Amendment 1 ✅ approved, option
A at §2; **next S2**, then A, then B (C, which B follows in `AlbumsViewModel.kt`, is done), then the
verification phase, then task P.~~
**Where it stands, 2026-11-22:** ✅ all done — S1, C, S2, A, B (as B1 and B2), the verification phase
and task P; *3.6 — Built and verified 2026-11-22* below has the gate. The briefs below are kept as
they were, with dated notes where the build settled something differently.
- **Shared files, which decide the order:** the 17 `res/values*/strings.xml` — S1 and S2 only; a later
  task that finds a key missing adds it to all 17 and says so in its report. `ui/AlbumsViewModel.kt` —
  C, then B. `MainActivity.kt` — A, then B. No other file is in two tasks.
- **Before approval:** S1, then C — neither builds anything the amendment governs. **After it:** S2,
  then A, then B. A and C share no file, so if the approval comes while C is running A may run beside
  it; the default is sequential, the habit of this repository, one Coder at a time.
- **Why five tasks and not one,** against the habit of one sequential task per feature: 3.6 has three
  areas — face data (Settings, disclosure, app open), the notices, the picker — that share almost no
  file, and two pieces of work (S1, C) need nothing from the maintainer, so splitting lets them start
  while he reads. **Why not more:** the picker is one area — its state, screen, tile and crop share the
  draft, and splitting it would put `AutomaticAlbumPickerState.kt` and `NewAutomaticAlbumScreen.kt`
  into two tasks; the strings are one task per approval state, because 17 files edited by several
  tasks at once are the usual collision.
- **Each task** ends compiling (`compileDebugKotlin`), its own unit tests passing, and
  `compileDebugAndroidTestKotlin` passing whenever it changed a `*Content` or screen signature. **No
  task runs `connectedDebugAndroidTest`;** the verification phase runs the definition of done once.
- **Signatures, no defaults (`AGENTS.md`):** `NewAutomaticAlbumContent` (People offered, the people
  UI state, toggle, clear, retry, Index now — B); `SettingsScreen` (+ `FaceDataViewModel` — A);
  `AlbumListItem` (+ the cause — C), which reaches `AlbumGridItem`, the Album row and
  `AlbumDetailContent`. Every test that builds one is fixed, not defaulted.
- **Each task's report** names the vocabulary terms it added (additions only) and every document its
  change made wrong, by section, for task P.

**Task S1 — the strings the amendment does not govern** (Coder). Waits on nothing. ✅ **Done
2026-11-21:** the 24 S1 keys of *Strings* in all 17 locales; `LocaleResourceParityTest` 11/11; the
unit suite green (79 classes, 1310 tests, 0 failures, run with task C). Open items it leaves are
under *Open after S1 and C* below.
- **Files:** `app/src/main/res/values*/strings.xml`, all 17 — the S1 keys of *Strings* above.
- **Exit criteria:** `LocaleResourceParityTest` passes — every key in every folder, each locale's
  plural categories, the placeholders, a count in every plural item, escaped apostrophes; the English
  matches the drafts above.
- **Mutation checks:** one key dropped from one locale; the count dropped from one plural item; `%1$d`
  dropped from one translation — each seen to fail the parity test, then reverted.

**Task C — the cause to the notices, and the Album info dialog** (Coder). After S1; waits on no
approval. ✅ **Done 2026-11-21:** the notices chosen by cause through one pure mapping,
`AlbumStatusNoticeKind` and `albumStatusNoticeKind` in `ui/components/AlbumStatusNotices.kt`, used by
the tile, the Album row and `AlbumNoticeBand`; the People entry in the Album info dialog. Unit tests
11 (`AlbumStatusNoticesTest`) + 3 (`AlbumsViewModelTest`) + 3 (`AlbumRuleSummaryTest`); Compose
tests 4 + 5 + 4 added to `FolderListContentTest` (51 pass), `AlbumDetailContentTest` (38) and
`AlbumInfoDialogTest` (15), run on the SM-G990B; unit suite 79 classes, 1310 tests, 0 failures. Every
mutation check that was run was killed. **Not run, and recorded as not run:** two of the planned
mutations — the hidden-members notice put first, and face data off / incapable swapped as seen by the
Compose layer *(2026-11-22: the final gate reports Task C's mutations as 6 or more, all killed, without
saying whether these two are among them)*. The vocabulary gained the new entries only; the edits to existing
entries are routed to the Designer (*Open after S1 and C*).
- **Files:** `ui/AlbumsViewModel.kt` (the cause on `ComputedAlbum` and `AlbumListItem` — nothing else);
  **new** `ui/components/AlbumStatusNotices.kt` (the pure mapping of *the notices*, Android-free apart
  from resource ids, so it is unit-tested); `ui/components/AlbumGridItem.kt` (the tile's and row's
  notices through the mapping); `ui/AlbumDetailScreen.kt` (`AlbumNoticeBand` through the mapping);
  `data/AlbumRuleSummary.kt` (`AlbumRuleCondition.People`); `ui/components/AlbumInfoDialog.kt` (the
  People entry); the vocabulary, additions only. Tests: **new**
  `test/.../ui/components/AlbumStatusNoticesTest.kt`; `test/.../ui/AlbumsViewModelTest.kt`;
  `test/.../data/AlbumRuleSummaryTest.kt`; `androidTest/.../ui/FolderListContentTest.kt`,
  `androidTest/.../ui/AlbumDetailContentTest.kt`, `androidTest/.../ui/components/AlbumInfoDialogTest.kt`,
  and every other test the new required field breaks.
- **Exit criteria, each with a test:**
  1. each cause reaches `AlbumListItem`: face data off and incapable through
     `InMemoryPeopleAvailabilitySource`, an anchor not found through the in-memory index store, an
     unparsed rule as `RULE_NOT_UNDERSTOOD`; a waiting, complete and manual album carry none;
  2. the mapping, every row of the table, waiting included;
  3. in Compose, for each of the four notices: the tile and the row show the short wording and
     expose it as `stateDescription`; the Album Detail View shows the full wording under its tag; the
     hidden-members notice stacks after it; a waiting people album shows none;
  4. the Album info dialog shows the People entry with the plural count and the note, after Place and
     Date, for a people-only rule under the Automatic heading; `AlbumRuleSummary`'s `toString` holds no
     URI, display name or box;
  5. no notice string has a placeholder (the parity test sees none to compare).
- **Mutation checks:** face data off and incapable swapped in the mapping; the cause dropped in
  `toListItem` (every cause becomes the Newer-app notice); the hidden-members notice put first; the
  People count taken from every clause rather than the `people` ones.

**Open after S1 and C** — none blocks S2, A or B:
- **Short notices longer than the ~60-character budget in some locales** — 74 to 85 characters for
  some `*_notice_short` translations against the ~60 the tile's three lines at 11 sp were drafted
  for. The tile ellipsizes rather than overflows; whether it reads acceptably is a device check —
  **the tile at font scale 2.0**, in the verification phase if a device is attached, else carried to
  3.7. Shortening a translation is the remedy, not a layout change.
- **Native-speaker review outstanding**, before a release, Stage 2's precedent. **Priority:** the
  three notices (full and short), and the term *face data* per locale, which the notices fixed and
  **S2 must reuse verbatim**: ar بيانات الوجوه; de Gesichtsdaten; es datos faciales; fr données
  faciales; in data wajah; it dati dei volti; ja 顔データ; ko 얼굴 데이터; pl dane twarzy; pt-rBR dados
  faciais; ro datele despre chipuri; th ข้อมูลใบหน้า; tr yüz verileri; uk дані облич; vi dữ liệu khuôn
  mặt; zh-rCN 人脸数据. After S2, the disclosure joins the priority list — the app's one privacy
  statement.
- **The vocabulary's edits to existing entries, routed to the Designer:** task C added only the new
  entries. The existing **Newer-app notice** entry (it still describes one notice for every
  unevaluatable cause, where the cause now picks one of four) and the existing **Album info dialog**
  entry (it does not mention the People entry) still need an edit. Task P routes both.

**Task S2 — the strings the amendment governs** (Coder). After the approval — ✅ given 2026-11-21 —
and after S1 — ✅ done. ~~**Next.**~~ ✅ **Done 2026-11-22** — the 18 keys in all 17 locales, the
English verbatim; *3.6 — Built and verified 2026-11-22* below. The brief is kept as it was. This brief is complete: the Coder needs nothing outside it, the
files and `AGENTS.md` *Strings and localisation*.
- **Files:** `app/src/main/res/values/strings.xml` and the 16 `values-*` folders of
  `LocaleHelper.supportedLanguageTags` — `ar de es fr in it ja ko pl pt-rBR ro th tr uk vi zh-rCN`,
  Indonesian in `values-in`, never `values-id`. Nothing else: no code, no test, no vocabulary.
  Insert the block after the S1 block in each file, at the same place in all 17. **Line endings
  differ per file** — `values-in`, `-pl`, `-ro` and `-tr` are CRLF, the rest LF, and some files have
  no final newline: match each file, and compare `git diff --stat` with `git diff
  --ignore-all-space --stat` afterwards.
- **The 18 keys, English final — approved 2026-11-21 with ADR-0024 Amendment 1 §5 and §4; copy
  verbatim into `values/strings.xml`**, a plain `<string>` each, none `translatable="false"`:

  | Key | English |
  |---|---|
  | `face_data_disclosure_title` | People in your photos |
  | `face_data_disclosure_p1` | Easy Gallery finds and groups the faces in your photos, so that you can make albums of people. |
  | `face_data_disclosure_p2` | This happens on your phone only. What it finds is never sent anywhere and is not included in backups. |
  | `face_data_disclosure_p3` | Faces are never named. An album of people remembers one photo for each person you pick, and that is saved and backed up with the album. |
  | `face_data_disclosure_p4` | Finding faces takes time and mostly runs in the background while the phone is charging. |
  | `face_data_disclosure_p5` | You can turn this off at any time in Settings, under Face data. Turning it off deletes everything that was found. |
  | `face_data_turn_off` | Turn off |
  | `settings_people` | People |
  | `settings_face_data` | Face data |
  | `settings_face_data_on` | Faces in your photos are found and grouped on this phone, so you can make albums of people. |
  | `settings_face_data_off` | Off. Faces are not looked for and no face data is kept. Turn on to find and group faces on this phone, so you can make albums of people. Nothing leaves your phone. |
  | `settings_face_data_deleting` | Deleting face data… |
  | `settings_face_data_cleanup_pending` | Face data is deleted. Cleaning up its files will finish the next time the app starts. |
  | `settings_face_data_about` | About face data |
  | `face_data_delete_title` | Delete face data? |
  | `face_data_delete_message_1` | This turns off face data and deletes the faces Easy Gallery found in your photos, on this phone. Your photos, places and dates are not affected. |
  | `face_data_delete_message_2` | Albums of people stay, but show only the items you added by hand. They fill in again if you turn face data back on and your photos are scanned again, which can take a long time. |
  | `face_data_delete_confirm` | Delete and turn off |

  **Reused, not added:** `action_ok` (the disclosure's OK), `action_close` (its info mode),
  `action_cancel` (the Delete face data dialog's Cancel). **Not S2's:** every S1 key, already in.
- **Translation rules:**
  1. **The same terms as S1.** *Face data* is the locale's term in the list under *Open after S1 and
     C*, in every S2 string that names it, inflected only as the grammar demands; `settings_face_data`
     is that term as a row title. *Under Face data* in `_p5` names the row: write the locale's
     `settings_face_data` text there, and the locale's existing `settings_title` for *Settings*. *People*
     (`settings_people`) matches the locale's existing `automatic_album_section_people`. *Albums of
     people* and *items you added by hand* match the wording the locale's S1 notices already use.
  2. **"Easy Gallery" is never translated** (`app_name` is `translatable="false"`); nor anything else
     — no licence text is in S2.
  3. **No placeholders and no plurals:** none of the 18 has a `%` argument or a `<plurals>`; a
     translation must not add one. `PLURAL_CATEGORIES` is untouched.
  4. **Escape every apostrophe** as `\'` (French, Italian and others will have them) and write the
     ellipsis in `_deleting` as the literal `…`, as `settings_index_running` does.
  5. **Register:** plain, second person, the register the locale's existing Settings strings use; a
     statement, not a consent request — the disclosure has OK, not *Agree*.
  6. **Meaning over form for `_p2` and `_p3`**, the privacy claims: "never sent anywhere", "not
     included in backups", and "that is saved and backed up with the album" must each survive
     translation as a claim, neither softened nor widened.
- **Exit criteria:** `LocaleResourceParityTest` passes (all 17 folders, the 18 keys in each, no key
  extra, placeholders and apostrophes); the English in `values/strings.xml` is the table above,
  character for character; the unit suite stays green (`compileDebugUnitTestKotlin
  testDebugUnitTest`); `compileDebugKotlin` passes. No `connectedDebugAndroidTest` (the verification
  phase owns it). The report lists, per locale, the term used for *face data* and any string the
  Coder is least sure of, for the native-speaker review.
- **Mutation checks**, each seen to fail the parity test and then reverted: one S2 key dropped from one
  locale; an unescaped apostrophe in one translation; a `%1$s` added to one translation; one S2 key
  misspelt in one locale (missing and extra at once).

**Task A — face data: the ViewModel, Settings, the disclosure, the licences, app open** (Coder). After
S2. ✅ **Done 2026-11-22** — *3.6 — Built and verified 2026-11-22* below, with the review's fixes; the
brief is kept as it was.
- **Files:** `data/FaceCapability.kt` (the supported ABIs as a constructor value; none of them
  `arm64-v8a` → incapable recorded under the current key, no loader call); `EasyGalleryApp.kt` (passes
  `Build.SUPPORTED_ABIS`; whatever `FaceDataViewModel` needs built); **new** `ui/FaceDataViewModel.kt`
  (the setting, the capability and the disclosure as state; the app-open check and start-up deletion;
  the switch in both directions; the disclosure's two buttons; the cleanup sentence's transient state;
  seams for the stores, the lock, the deletion, the checker, the Coil memory cache and the refresh
  callback, as `AlbumsViewModel`'s are); **new** `ui/components/FaceDataDialogs.kt` (the disclosure in
  both modes, the Delete face data dialog, the Face licences dialog); `ui/SettingsScreen.kt` (a
  stateless `FaceDataSettingsArea`, as `MediaIndexSettingsArea` is; the Face licences row;
  `SettingsItem`'s trailing slot; the new parameter); `MainActivity.kt` (the ViewModel, the root host,
  the `onResume` refresh, the `SettingsScreen` argument, the refresh callback into `AlbumsViewModel`);
  the vocabulary, additions only. Tests: `test/.../data/FaceCapabilityTest.kt`; **new**
  `test/.../ui/FaceDataViewModelTest.kt`; `androidTest/.../ui/SettingsScreenTest.kt`; **new**
  `androidTest/.../ui/components/FaceDataDialogsTest.kt`; `androidTest/.../ui/SettingsSupportIntentTest.kt`
  if the signature reaches it.
- **Exit criteria, each with a test:**
  1. the checker with no `arm64-v8a` records incapable under the current key and never calls the
     loader; with it, unchanged; `FacePipelineUnavailableException` and out of memory still record
     nothing;
  2. the app-open check reads a stored answer without the lock and without the checker; with none,
     it takes the lock before the checker; the start-up deletion runs only while the setting is off;
     the refresh follows both;
  3. **switching off, in order:** persist off → lock → delete → memory cache cleared → unlock →
     refresh, asserted on recording fakes; nothing happens before the confirmation; Cancel changes
     nothing; the cleanup sentence on `scrubbed == false` and silence on true; *(made precise
     2026-11-22 by the review: silence too when the deletion throws — it rolls back, and the start-up
     deletion repeats it — so the sentence follows a returned `false` only)*
  4. switching on: persist on and refresh, no deletion, no confirmation; ~~the never-shown case as
     Amendment 1 §2 is approved~~ **the never-shown case as Amendment 1 §2 option A, adopted
     2026-11-21: switching on when the disclosure was never shown leaves it not shown —
     `markDisclosureShown()` is never called on the switch-on path — and the disclosure's condition
     true**, on a capable device with the permission granted and the viewer closed; switching on when it
     was already shown leaves the condition false;
  5. the disclosure's derivation — every condition of *the disclosure*, one test each, the viewer
     open included; OK marks it shown; Turn off marks it shown and runs criterion 3's order without the
     confirmation; Back and an outside tap leave it showing; the info mode has one button and no side
     effect;
  6. Settings: the Face data area absent while the capability is unknown or incapable, present while
     capable with the setting on or off; the Face licences row present in every state, its dialog
     showing the five file names in order (rows below the fold reached with `performScrollTo` before
     asserting);
  7. no `Log` call in `FaceDataViewModel` or the dialogs, read in review.
- **Mutation checks:** delete before persist-off; the lock not taken for the check; the ABI test
  inverted; `dismissOnBackPress` true; the area shown on an unknown capability; the refresh dropped
  from the switch-on path; `markDisclosureShown()` called on the switch-on path (option B, not
  chosen) — criterion 4 must fail.

**Task B — the People picker** (Coder). After A, which it reads — the People-offered state — and after
C, which it follows in `AlbumsViewModel.kt`. ✅ **Done 2026-11-22**, built as B1 and B2 — *3.6 — Built
and verified 2026-11-22* below, with the review's fixes and `PersonFaceOrientationDeviceTest`; the
brief is kept as it was.
- **Files:** `ui/AutomaticAlbumPickerState.kt` (the chosen summaries, `MAX_PEOPLE_PER_ALBUM` = 8, the
  toggle and its false at the cap, clear, the groups' load and freeze, the People-offered input and
  the drop when it goes false, the face projection subscribed only while a person is chosen, the match
  count through the evaluator, the member set for the cross-filter, `canCreate`);
  `data/AutomaticAlbumFacets.kt` (the member-set predicate, ~~if §6 is approved as recommended~~ §6
  being adopted 2026-11-21);
  `ui/AlbumsViewModel.kt` (`listFaceGroups` as the picker's source, the face projection handed to the
  picker, the `require` caught in `createAutomaticAlbum`); `ui/NewAutomaticAlbumScreen.kt` (the third
  tab and its fallback, the chip, the hint, the new parameters); **new** `ui/PeoplePicker.kt` (the
  grid, `PersonTile`, the states); **new** `ui/components/FaceCrop.kt` (the pure rectangle) and the
  crop request beside the tile; `MainActivity.kt` (People offered into the screen); the vocabulary,
  additions only. Tests: **new** `test/.../ui/components/FaceCropTest.kt`;
  `test/.../ui/AutomaticAlbumPickerStateTest.kt`; `test/.../data/AutomaticAlbumFacetsTest.kt`;
  `test/.../ui/AlbumsViewModelTest.kt`; `androidTest/.../ui/NewAutomaticAlbumContentTest.kt`.
- **Exit criteria, each with a test:**
  1. the crop: the 1.8 margin; clamped to the short side; shifted, not shrunk, at each edge and corner;
     portrait, landscape and square images; a box of the whole frame;
  2. the toggle: select, deselect, the order kept; the ninth returns false and changes nothing; clear;
     the rule holds one `people` clause per chosen person, each equal to `anchoredAt` of the summary
     shown;
  3. **the match count equals the evaluator's member count** for people alone, people + region, people
     + time and all three, over the in-memory index store with grouped faces;
  4. the cross-filter: each Place and Date count equals the evaluator's member count for that entry
     with the chosen people;
  5. no reload while a person is chosen; a reload when indexing leaves RUNNING with none chosen;
  6. People offered going false drops the chosen people and the groups, and the tab falls back to
     Place; a restored tab index 2 with People not offered shows Place;
  7. no subscription to the face projection while nobody is chosen — counted as 3.5's criterion 4
     counts them;
  8. an oversized rule given to `createAutomaticAlbum` returns false with nothing written;
  9. in Compose: the tab only when offered; tiles in the list's order; select and deselect; at 8 the
     unselected tiles disabled and the limit hint shown; the chip clears; loading, empty, failed and
     Retry; Index now only with the band Hidden; **the last tile clear of the FAB, by bounds after
     `performScrollToNode`** at font scale 2.0; the badge at the end edge in RTL; the content
     description's pattern; no tag or description holding a URI or a group id;
  10. the crop request sets `diskCachePolicy(DISABLED)` — asserted on the built request.
- **Mutation checks:** the cap at 9; the freeze dropped; the cross-filter predicate ignored by one
  facet; the projection subscribed at open; `diskCachePolicy` removed; the clamp done by shrinking.

**Device-only checks** — in the verification phase if a device is attached, else carried to 3.7, and
the report says which: the tab labels at font scale 2.0 on a 360 dp width (de, it); the tile notices'
ellipsis at 2.0; `AlertDialog` with a `LazyColumn` — a bounded height, OK visible, at 2.0 and in
landscape; the crop's decode cost scrolling 100 or more tiles, and sharpness for small faces (21 of
104 groups have no member at least 60 px across, *3.4*); **Coil's disk cache directory unchanged
after the People picker renders** (ADR-0024 §6), and the memory cache emptied by a switch-off; the
disclosure shown once, on a capable phone with face data on, and the first face pass through the real
worker after it — which is also 3.7's item 5. *(2026-11-22: the gate ran none of these as a
separate check; the ones the review named are 3.7's items 17 to 24, and the first face pass stays
item 5.)*

**Verification phase — once, over the integrated change; it owns the gate.** ✅ **Done 2026-11-22** —
the gate is under *3.6 — Built and verified 2026-11-22* below. The definition of done,
steps 1 to 5; `adb devices` settles step 4 and the report says which ran. Also: `git status
app/schemas` empty — neither database changes version; the debug and release merged manifests
byte-identical to 3.5's (*3.5 — Built and verified 2026-11-21*); no `Log` call in the new `ui/` code;
`git diff --stat` against `git diff --ignore-all-space --stat` for line endings. No task runs
`connectedDebugAndroidTest` itself.

**Task P — afterwards** (Planner; the vocabulary's edits the Designer's). ✅ **Done 2026-11-22**,
except the vocabulary's edits, which stay routed to the Designer (*3.6 — Built and verified
2026-11-22*, *Not proven by it*): `ARCHITECTURE.md` and `AGENTS.md` in an earlier run; the *Built*
notes on ADR-0024 Amendment 1, ADR-0017 Amendment 4, ADR-0022 and ADR-0023 §3, this plan and
[albums.md](albums.md) §7 in this one. On the built code, not on
reports: `ARCHITECTURE.md` §1 and §2 (the third tab; the Face data area and the licences row; the
disclosure at the root), §3 (`FaceDataViewModel`, the new `ui/` and `ui/components` files, the ABI
answer in `FaceCapability.kt`) and §11 (what is closed — the interim notice, the non-arm64 wait, the
uncalled refresh — and what is left); `AGENTS.md` (*State ownership*: `FaceDataViewModel` among the
feature ViewModels; *Preference stores*: "nothing in `ui/` writes either … step 3.6 does" made true;
*The two databases*: "nothing in `ui/` calls the deletion or the picker query yet"); ADR-0024 —
a *Built* note on Amendment 1 (its marker was already turned into an adoption line on 2026-11-21);
ADR-0023 §3, a *Built* note under the dated note of 2026-11-21 on the ABI
answer; ADR-0017 Amendment 4, a *Built* note on the notices; the UI vocabulary's edits, routed to the
Designer — at least the existing **Newer-app notice** and **Album info dialog** entries (*Open after S1
and C*); [albums.md](albums.md) §7's Stage 3 row and decision 33's D1 pointer; this plan — the step
row, a *Built and verified* section, the handoff, 3.7's list.

**Not 3.6's:** rule editing, and with it re-picking a person who cannot be found (its own plan); a
notice for a waiting album (decision 42); naming, merging or splitting people (decision 40); a
complete open-source licences screen; the Play Data Safety check (before the first Play release that
ships the Tier C pass); the face projection's cost at production size (3.7 items 11 and 15).

### 3.6 — Built and verified 2026-11-22

Built as tasks S1, C, S2, A and B above, then the verification phase and task P. What landed is not
restated here: `ARCHITECTURE.md` §1, §2, §3 and §11 describe it, with
[ADR-0024](../architecture/decisions/0024-face-data-disclosure-and-delete-switch.md) Amendment 1's
*Built* note, [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md)
Amendment 4's, [ADR-0022](../architecture/decisions/0022-people-criterion-anchored-to-a-photo.md)'s
and [ADR-0023](../architecture/decisions/0023-face-pipeline-models-runtime-and-delivery.md) §3's.
Per task, in one line each:
- **S1** — the 24 keys the amendment does not govern, in all 17 locales; **S2** — the 18 privacy keys,
  the approved English verbatim, the *face data* terms of *Open after S1 and C* reused.
- **C** — the notices by cause through `albumStatusNoticeKind`, and the People entry in the Album
  info dialog.
- **A** — `ui/FaceDataViewModel.kt`; the Face data area and the Face licences row in Settings;
  `ui/components/FaceDataDialogs.kt` (the disclosure in both modes, the Delete face data dialog, the
  Face licences dialog); the disclosure hosted at `MainActivity`'s root; the app-open check and
  start-up deletion; `supportsFacePipelineAbi` in `data/FaceCapability.kt`.
- **B** — the People tab: `ui/PeoplePicker.kt`, `ui/components/FaceCrop.kt`, the chosen people and
  `MAX_PEOPLE_PER_ALBUM` (8) in `AutomaticAlbumPickerState`, the match count and the Place and Date
  counts through the evaluator's member set, `listFaceGroups` as the source, the `require` caught in
  `createAutomaticAlbum`. Built as B1 and B2, two passes of the one task.

**No schema change** in either database; no dependency, permission or preferences file.

**The gate — the definition of done, once, over the integrated change, after the review's fixes**
(Coder-owned; checked by the Planner against the progress note and the result XMLs, not by a run):
`compileDebugKotlin` OK; the unit suite, 84 files, 1427 tests, 0 failures;
`compileDebugAndroidTestKotlin` OK; a device was attached, the SM-G990B, so
`connectedDebugAndroidTest` ran: 334 tests, 0 failures, 0 errors, 3 skipped — the three opt-in
measurements, skipped by design. Per class: `SettingsScreenTest` 38, `FaceDataDialogsTest` 16,
`NewAutomaticAlbumContentTest` 87, `PersonFaceOrientationDeviceTest` 1, `FolderListContentTest` 51,
`AlbumDetailContentTest` 38, `AlbumInfoDialogTest` 15. The debug and release merged manifests
byte-identical to 3.5's (debug md5 `8856dbd4b4257bd665c4593a325ba624`, release
`043a521e4d0a25724364c8b8b6ea6f8d`); `app/schemas` unchanged. Lint does not run on this machine
(`AGENTS.md`, *Running Gradle*), so no lint result is claimed. **Mutation checks**, each seen to fail
its test and reverted: Task C, 6 or more; Task A, 14 unit and 1 device, plus 2 for the review's
fixes; B1, 12; B2, 6; the picker's review fixes, 4 (S1, S3, S4, S6 of that review). The gate's report
does not say whether Task C's two mutations recorded as not run on 2026-11-21 are among its six.
This document does not record tracking state.

**Review outcomes.** Two Reviewers; no hard violations. **Fixed, task A:** the cleanup sentence shown
only when the deletion returned `scrubbed == false`, not when it threw; a `Throwable` caught at app
open; the Face data row's minimum height. **Fixed, the picker:** the chosen people read from the
draft; no transient count of 0 on the first chosen person; `togglePerson` requires equality with the
listed summary; Coil's `Precision.INEXACT` on the crop request; the reveal effect keyed on
`NOT_LOADED`; a reload on clear after a skipped reload; and a new device test,
`PersonFaceOrientationDeviceTest`, a rotated JPEG's crop. **Judgement calls, left as built** and
recorded in `ARCHITECTURE.md` §11: the cleanup sentence is also cleared on a rotation; the licence
files' order is pinned only on the constant list; the no-arm64 test lives in two places, the
ViewModel and the checker.

**Not proven by it** — each 3.7's (items 17 to 24 under *3.2 to 3.8*) or debt in `ARCHITECTURE.md` §11:
- **the native-speaker review** of the translated privacy text, before a release — priority the
  disclosure's P1 to P5, the Delete face data dialog, the Face data row and the three people notices,
  with the *face data* term per locale listed under *Open after S1 and C*;
- string lengths: several locales' tile and notice strings run 74 to 85 characters against about 60,
  unchecked at font scale 2.0;
- the device-only checks of *Device-only checks* above that the gate did not run — 3.7's list;
- **the UI vocabulary's edits to existing entries**, routed to the Designer and not yet made: the
  Newer-app notice, the Album info dialog, the Status area (three pickers now), the New automatic
  album screen (place, date and people) and Pick — tasks C and B only annotated them;
- the three opt-in measurement tests, skipped in the gate by design.

### 3.7 — Device verification and the installed-app walkthrough — refined 2026-11-22

Refined by the Planner from 3.7's text and its items 1 to 24 under *3.2 to 3.8*, which stay the
source list. Every item is placed below exactly once: an **agent item** (G — not A, which already
names the questions of group A), a step of the **maintainer's walkthrough** (W), or a **known limit**
(L), each with its exit criterion and a recommendation. ~~**No production code is planned.**~~
*Corrected 2026-11-22:* none was planned, and two production fixes landed, both made by the Coder
during G4 and G5 and both inside G3's run — `Core.setErrorVerbosity(false)` in
`OpenCvFacePipeline.load()`, two lines (G5a), and the German People tab label,
`automatic_album_section_people` "Personen" → "Leute" in `values-de` (G4, item 17). The agent
items otherwise add device tests only; a defect
any item finds is fixed by a Coder task of its own, and G3 then runs again over the final tree.
Planning only: no code, no Gradle run by the Planner.

**Rules for every item.**
- **Nothing face-related leaves the phone** (decision 9). No `adb pull`, `run-as cat` or copy of
  `media_index.db` or any of its sidecars. No screenshot, screen recording or image of the People tab,
  a Person tile, a face crop or a member photo is taken off the phone: those are judged by the
  maintainer's eyes, on the phone. Allowed off the phone: `uiautomator` dumps, which carry text and
  bounds and no URI or group id (3.6 Task B, criterion 9); logcat filtered to the app's fixed lines;
  pragma values, counts, timings, memory and thermal readings; and the names and sizes of the files in
  Coil's disk-cache directory.
- **`adb devices` is checked and stated** before each phase. The SM-G990B is serial `RFCT703FBKR`;
  with two phones attached, `ANDROID_SERIAL` is exported for Gradle and `adb -s` used. The phone is
  awake and unlocked before every run (repo memory).
- **`connectedDebugAndroidTest` uninstalls the app and wipes its data** — the dev phone's albums
  included, as at every gate since Stage 1. It runs only in G3 to G5, before the walkthrough. From G6
  onwards nothing reinstalls the app: an agent item that needs the installed app's state runs its
  test through `installDebugAndroidTest` and `adb shell am instrument`, which leave the app and its
  data in place (the step 2.4 precedent in repo memory).

#### (i) Agent items — on the SM-G990B unless said otherwise, in this order

- **G1 — the capability check on the second phone (item 9; ADR-0023 verify item 7).** ✅ **Done
  2026-11-22.** The SM-G781B — Android 13, API 33; ABIs arm64-v8a, armeabi-v7a and armeabi; about
  5.5 GiB RAM — with the debug build, through the instrumented tests: `FaceCapabilityDeviceTest` 2 of
  2 and `FaceDecodeOrientationDeviceTest` 4 of 4 (EXIF orientations normal, 3, 6 and 8), 6 of 6
  passed. Its log: capable on `abi=arm64`, `embeddingSize=128`; OpenCV 4.13.0, aarch64, NDK
  27.2.12479018, linked 16 KB page-aligned — the same as the SM-G990B. Recorded in ADR-0023's verify
  row 7. It also shows ADR-0022's upright frame on that phone — recorded in ADR-0022's *To verify*
  on 2026-11-22. **Not settled by it:** item 7's
  release build and face pass and item 14's grouping on that phone (L3), and the incapable path —
  no phone without arm64-v8a is available, so it stays unmeasured on hardware (L1).
- **G2 — the device tests 3.7 needs. Coder, one task, tests only.** New
  `androidTest/.../data/MediaIndexSqliteDeviceTest.kt` (items 1 to 3; a regular test, on a
  throwaway file-backed index, plus one method that only reads the production index's modes);
  new `androidTest/.../data/FaceNativeErrorLogDeviceTest.kt` (item 6, opt-in); new
  `androidTest/.../data/FaceFavouriteToggleDeviceTest.kt` (item 4, opt-in — it writes the production
  index and toggles a real favourite, putting both back, as `FaceGroupingWorkerMeasurementDeviceTest`
  puts back the disclosure); new `androidTest/.../data/MediaIndexTierCScaleDeviceTest.kt` (items 11,
  12 and 15, opt-in); new methods in `androidTest/.../ui/components/FaceDataDialogsTest.kt` (item 22)
  and `androidTest/.../ui/NewAutomaticAlbumContentTest.kt` (item 17). What each asserts is in G4 and
  G5. **Exit:** all compile; each regular test seen to fail under one planned mutation and reverted;
  no line any of them logs holds a URI, a box, an embedding or a group id. *Recommended: now.*
  ✅ **Built and run 2026-11-22**, with one exit point ~~unreported~~ deferred — the planned
  mutation check, below. The four files named, plus
  `androidTest/.../data/MediaIndexThrowawayDatabase.kt`, the throwaway file-backed index they share,
  and the methods in the two UI test files; all compile (G3). The three opt-in classes skip by default
  through `assumeTrue` on their argument. The Coder reviewed every line they log: counts, pragma values
  and timings only. **Deviation, recorded:** `FaceFavouriteToggleDeviceTest` indexes into a throwaway
  in-memory index, not the production one (G5b). ~~🔶 **Not in the reports the Planner has:** the one
  planned mutation per regular test — the Coder states it, or runs it, before 3.8.~~ ⏸ **Deferred
  2026-11-22 until after W7:** the planned mutation per regular test needs a connected run, which
  uninstalls the app and would wipe the disclosure state the walkthrough starts from. It is not
  dropped: it is **M1** in 3.8's entry condition.
  ✅ **M1 done 2026-11-23** (3.8's entry condition 8), by the Coder on the SM-G990B after W7: all
  nine regular new tests failed under a named mutation and passed restored. In
  `MediaIndexDatabase.kt`: **A** `secure_delete` off; **B** the WAL never checkpointed (`'wal'` →
  `true`); **C** truncate/delete → `false`; **D** `else` → `true`. In the strings: **E** `values-de`
  back to "Personen"; **F** `values-it` "Persone riconosciute". In `FaceDataDialogs.kt`: **G**
  `requiredHeight(4000.dp)`. Every mutation reverted; `git status` and `git diff` empty at the end;
  a clean confirmation run of `MediaIndexSqliteDeviceTest`, `NewAutomaticAlbumContentTest` and
  `FaceDataDialogsTest` together, 112 tests, 0 failures. **Caveat:** G was caught at the
  title-displayed assertion — a dialog taller than the window — not at the later screen-bounds
  assertions, and no mutation aimed at those was tried.
- **G3 — the definition of done, once, over G2.** The verification phase owns it: `AGENTS.md` steps
  1 to 5, `adb devices` deciding step 4; the merged manifests byte-identical to 3.6's (debug md5
  `8856dbd4b4257bd665c4593a325ba624`, release `043a521e4d0a25724364c8b8b6ea6f8d`); `git status
  app/schemas` empty. The opt-in tests skip by design. **Exit:** 0 failures, the counts stated. If any
  later item changes code or tests, G3 runs again over the final tree — 3.8's entry condition 4.
  ✅ **Done 2026-11-22**, once, over the integrated change — G2's tests and the two production fixes
  above; the SM-G990B attached. `compileDebugKotlin` OK; `testDebugUnitTest` 84 classes, 1427 tests,
  0 failures, `LocaleResourceParityTest`'s 11 included **after a forced `cleanTestDebugUnitTest`** —
  the parity test reads `res/`, which Gradle does not track as its input, so an up-to-date unit task
  would not have re-run it after the German string changed; `compileDebugAndroidTestKotlin` OK;
  `connectedDebugAndroidTest` 346 test cases, 0 failures, 0 errors, 6 skipped — the three earlier
  opt-in measurements, and `FaceFavouriteToggleDeviceTest`, `FaceNativeErrorLogDeviceTest` and
  `MediaIndexTierCScaleDeviceTest`, which skip by default through `assumeTrue`. The merged manifests'
  md5s unchanged (debug `8856dbd4b4257bd665c4593a325ba624`, release
  `043a521e4d0a25724364c8b8b6ea6f8d`); `app/schemas` unchanged. Recorded from the Coder's report;
  the Planner ran nothing.
  🔶 **Run again 2026-11-23 over the committed tree, `94f8e9c`** — 3.8's entry condition 4, which
  holds the figures: green on a second connected run after the first failed on two timeouts in
  `MediaIndexingWorkEnqueueTest`, a worker test 3.7 did not touch — **an intermittent flake,
  recorded as such, not a pass without qualification.** The Reviewer's test fixes came after it
  (*Review of 3.7*), so it ~~**runs once more over the final tree.**~~ runs once more over the final
  tree.
  ✅ **Ran once more 2026-11-23 over the final tree** — `94f8e9c` plus the Reviewer's four test fixes
  and the documents, the tree as it is to be committed — on the SM-G990B: green, 1427 unit tests and
  346 connected test cases, 0 failures, the same six opt-in skips; `MediaIndexingWorkEnqueueTest` 3 of
  3, the flake not recurring. The figures are in 3.8's entry condition 4, for which this is the run
  that counts. Recorded from the verification phase's report; the Planner ran nothing.
- **G4 — what G3's run records, and one landscape run.** *Recommended: all now — cheap.*
  - **Items 1 to 3.** `MediaIndexSqliteDeviceTest` records this phone's `journal_mode`,
    `locking_mode`, `auto_vacuum` and `secure_delete` as `MediaIndexDatabase` opens the index, and
    `ActivityManager.isLowRamDevice()`. On a throwaway file-backed index opened with the production
    callbacks, with synthetic marker bytes in place of face data: a deletion under the phone's own
    mode reports scrubbed and leaves no marker in the database or its `-wal`; with `journal_mode`
    forced to `TRUNCATE`, a deletion reporting scrubbed leaves no `-journal` holding a marker; a
    checkpoint blocked by a real concurrent read transaction reports incomplete, and a repeat finishes
    it (the JVM scrub test's three cases, `MediaIndexFaceDataScrubTest`, on real SQLite). **Exit:** the
    values recorded in this section; `secure_delete` reads 1; the three behaviours hold. **`PERSIST`
    or `exclusive` in any reading stops 3.7 and goes to the maintainer** — ADR-0018 Amendment 6's
    trigger for revisiting it.
  - **Item 17.** The New automatic album screen's tab row, People offered, at font scale 2.0 in a
    360 dp width, in German and in Italian: all three tabs shown and no label ellipsized or clipped,
    judged from each label's text layout, not from a screenshot. **Exit:** passes; or the overflowing
    labels listed and routed — a shorter translation is the Coder's, a scrolling tab row is a layout
    change and the Designer's.
  - **Item 22.** The Face licences dialog at font scale 2.0: its height bounded by the window and its
    OK visible after the longest licence text loads. Run in G3 in portrait, then its methods once
    more by class filter with the phone in landscape (`settings put system accelerometer_rotation 0`
    and `user_rotation 1`, put back afterwards). **Exit:** passes in both orientations.
  - ✅ **Done 2026-11-22, all three, on the SM-G990B (API 36).**
    - **Items 1 to 3** — `MediaIndexSqliteDeviceTest` 5 of 5. Read after a real deletion:
      `journal_mode` `wal`, `locking_mode` `normal`, `auto_vacuum` 1 (the production index),
      `secure_delete` 1; `isLowRamDevice` false; the deletion reported scrubbed. **No `PERSIST` and no
      `exclusive`: ADR-0018 Amendment 6's revisit trigger did not fire.** With a rollback journal
      forced, a deletion reporting scrubbed left no `-journal` holding a marker; a `PERSIST` control
      correctly reported not scrubbed; a checkpoint blocked by a real concurrent reader reported
      incomplete, and a repeat finished it. **Found:** on this phone's F2FS storage a small
      rollback-journal transaction never writes a journal file to disk — 0 bytes at 100 and at 2,000
      rows, 11.7 MB at 20,000 — so the rollback tests force a spill with `PRAGMA cache_size=10`;
      without it they would pass without a journal to inspect. Room choosing a rollback journal on a
      low-RAM device stays unreachable on either phone (L7).
    - **Item 17** — Italian passes. In German, "Personen" was genuinely ellipsized at font scale 2.0
      in a 360 dp tab; Place and Date fit. **Fixed by a shorter translation:**
      `automatic_album_section_people` in `values-de` is now "Leute" (informal; "Menschen" is as long
      as "Personen"). The same key labels the People entry of the Album info dialog, which now reads
      "Leute" too. Both German tests pass. **"Leute" joins the native-speaker review list** — it is
      informal, and German says "Personen" everywhere else. *2026-11-23:* **approved by the
      maintainer (option A)**; still on the native-speaker review list.
    - **Item 22** — the Face licences dialog keeps OK on screen at font scale 2.0, in portrait and in
      landscape: 2 of 2.
- **G5 — the opt-in runs**, each `connectedDebugAndroidTest` with a class filter and its opt-in
  argument, all before G6 because each run uninstalls the app.
  - **G5a, item 6.** Provoke one native OpenCV error through the pipeline's own call path and read
    logcat for the tag `cv::error()`. **Exit:** recorded whether a line prints, and its shape. If it
    prints, a one-line Coder fix — `Core.setErrorVerbosity(false)` where the pipeline loads, the
    likely switch — then G5a and G3 again. ADR-0024 §6 already decides that the face path logs only
    its fixed lines, so this needs no approval. *Recommended: now — cheap.*
    ✅ **Observed 2026-11-22; one line remains, for the maintainer.** `FaceNativeErrorLogDeviceTest`
    provoked a native error through the pipeline's own call path — a resize assertion. OpenCV prints
    in **two** places, both priority E: the tag `cv::error()`, and the tag `org.opencv.imgproc` — the
    Java wrapper's "caught cv::Exception" line. ~~`Core.setErrorVerbosity(false)` … the likely
    switch~~ *Corrected:* `Core.setErrorVerbosity(false)`, now in `OpenCvFacePipeline.load()`, silences
    the first and **not** the second, and no Java-side switch for the second was found. The second
    carries OpenCV's assertion text and an OpenCV source path; for the assertion provoked, no URI, box
    or embedding. So ADR-0024 §6 holds for the pass's own lines, and a native error still prints one
    OpenCV-originated line: **L10**, *Open at 3.7* question 5, and ADR-0024 §6's note of 2026-11-22.
  - **G5b, item 4.** With the disclosure set through the store and Index now run, so the phone holds
    faces: toggle the favourite of one image with faces, run Index now again, and compare. **Exit:**
    the image's Tier C rows unchanged in count and content (compared in the process, never logged),
    and its `generationModified` advanced; the favourite and the disclosure put back. If the generation
    does not advance, that is recorded as item 4 says — the toggle never reselected the row, ADR-0018
    Amendment 6 §1's motive weakens and its rule stands. *Recommended: now — the one premise of that
    amendment never observed.*
    ✅ **Done 2026-11-22.** `FaceFavouriteToggleDeviceTest` 1 of 1: a favourite toggle left the
    image's face rows and face state intact through the next delta run; the image's generation
    advanced in `MediaStore` and in the index; its healing triple unchanged; the favourite put back,
    and the restore checked, by the test. **ADR-0018 Amendment 6 §1's expectation is confirmed on
    this phone, API 36.** *Deviation from the text above:* the test ran its own face pass over the
    phone's real library — 1,133 rows, 1,002 images — into a throwaway **in-memory** index, not the
    production index; nothing persisted.
  - **G5c, items 11, 12 and 15, synthetic.** A throwaway file-backed index seeded with synthetic Tier
    C rows — random unit embeddings, invented URIs, `FaceGroupingMeasurementDeviceTest`'s generator —
    at 10,000, 20,000 and 37,000 faces: the time and peak memory of the grouping pass through the real
    Room store (the paged read, the grouping, the one-transaction write), and of one emission of the
    face evaluation projection. **Exit:** the figures recorded; `ARCHITECTURE.md` §11's deadline
    arithmetic of about 37,000 faces confirmed or corrected by the 37,000 figure; the emissions per
    Index now run computed from the batch sizes in the code, times the measured cost of one. A failure
    at 37,000 is a finding to record, not a defect to fix in 3.7. *Recommended: now — one test file
    on an existing generator, and the only production-size figure there can be for the store and the
    projection; the real library at that size stays L5.*
    ✅ **Measured 2026-11-22 — done for what synthetic faces can show:** item 12 settled; item 11 for
    the store and grouping at size, a real library at that size staying open (L5); item 15's time per
    emission, its emission count during an Index now run staying open (last bullet below).
    `MediaIndexTierCScaleDeviceTest`, through the real Room store on a
    throwaway file-backed index, charging, thermal status 1 throughout; every grouping stopped
    `GROUPED`. Times in ms, memory in kB; `listFaceGroups` at the picker's threshold of two images, run
    first, then at one image:

    | Faces | Grouping total | compute | paged read | write | Peak RSS rise | Java heap rise | Projection, one emission (median) | `listFaceGroups` ≥ 2 / ≥ 1 |
    |---|---|---|---|---|---|---|---|---|
    | 10,000 | 21,075 | 19,114 | 916 | 1,045 | 31,648 | 39,608 | 177 | 118 / 76 |
    | 20,000 | 75,688 | 71,873 | 2,112 | 1,703 | 53,724 | 57,949 | 410 | 173 / 151 |
    | 37,000 | 256,336 | 249,469 | 3,930 | 2,937 | 79,960 | 82,558 | 767 | 383 / 278 |

    - **The store's cost is measured, and small:** the paged read and the one-transaction write
      together are 9.3 %, 5.0 % and 2.7 % of the total. Memory does not bind: at 37,000 the Java heap
      rose about 81 MB against its 256 MB cap. The projection costs about 20 µs per face, linear.
    - **Grouping is quadratic:** ×3.59 from 10,000 to 20,000; ×3.39 from 20,000 to 37,000, against
      ×3.42 predicted. Its compute alone took 71.9 s at 20,000, where 3.4's Task 2 measured the same
      core at 50.9 s; thermal status was 1 here and 0 then, and the difference is not explained.
    - **`ARCHITECTURE.md` §11's arithmetic was wrong, and is corrected there.** It said grouping after
      a full 7-minute face pass has about 3 minutes (180 s) and fits "about 37,000 faces, about
      68,000 in a whole window", scaled from the core-only 50.9 s. Through the real store the 20,000
      figure is 75.7 s, so **grouping fits about 31,000 faces in 180 s, and about 56,000 in a whole
      600 s window**; the 37,000 run gives the same two figures. **37,000 faces, at 256 s, does not
      fit in 180 s.** At the real library's density, 0.42 faces per image, that is about 74,000 and
      133,000 photos. The consequence is **L11**.
    - **Not computed: how often the projection emits during an Index now run.** Read in the code by
      the Planner, not measured: `FACE_PASS_PAGE_SIZE`, 50, is the page the face pass *reads*; it
      *writes* once per image, each in a transaction of its own (`FaceIndexPass`), and the projection's
      query joins `media_index_face_state`, `media_index` and `media_index_face`, so every image
      written — and every Tier A upsert chunk — invalidates it. The upper bound is one re-run per
      image, about 2,800 in a full-budget run at ADR-0023 row 6's 150.7 ms per image. Room runs the
      query again once for any number of invalidations that arrive while it runs, so in practice it
      re-runs back to back for as long as the face pass writes and something collects it — at 37,000
      faces, one 0.8 s query after another. Nothing collects it with the screen off, so W3 cannot
      show it. Left to L5, or to the maintainer's eye at W4 if he wants it watched. *2026-11-22:*
      **item 15's emission count stays open** — to G7 if it can be counted there, otherwise L5.
      *2026-11-23:* not countable from outside the process at G7, so **L5**; and G7's 253.7 ms per
      image puts the upper bound at about ~~1,655~~ 1,600 re-runs in a full-budget run, not 2,800.
  - **G5d, item 8 — ADR-0023 verify item 5's re-check.** The release bundle measured with bundletool,
    `get-size total` for arm64-v8a. Nothing in the models or OpenCV has changed since 3.3, so it should
    read 49,613,983 B, +46,063,982 B; a different figure is the finding. **Exit:** within the 50 MB
    budget, recorded in ADR-0023's row 5. *Recommended: now — cheap.*
    ✅ **Measured 2026-11-22; a different figure, which is the finding.** bundletool 1.18.3,
    `get-size total`, arm64-v8a, API 34, density 420, on the release bundle rebuilt that day
    (`bundleRelease`): **49,712,393 B**, 98,410 B above 3.3's 49,613,983 B — the app code and
    resources of 3.4 to 3.6. The models and OpenCV are unchanged, so what they add is still
    +46,063,982 B; inferred, not re-measured, since no build without them was made. **Which 50 MB,
    and of what, is not written down anywhere.** A3 and ADR-0023 budget what the face models and
    their runtime *add*: 3,936,018 B under 50,000,000 B. Read against the **total**, the margin is
    287,607 B (0.58 %) under 50,000,000 B and 2,716,407 B under 50 MiB (52,428,800 B). *Recommended:*
    50,000,000 B as the strict reading of "50 MB". Under the total reading every further feature
    byte counts; under the record's own, only model and runtime bytes do. **L12**; ADR-0023's row 5.
  - **G5e, item 10, in part.** The debug app installed as a 32-bit process on the SM-G990B (`adb
    install -r --abi armeabi-v7a`, confirmed by `dumpsys package`'s `primaryCpuAbi`; the Coder checks
    that the flag does this for this APK before relying on it), then `FaceCapabilityDeviceTest` through
    `am instrument`. **Exit:** 2 of 2 pass with `abi=arm` in the log — its branch for an app not running
    as arm64 is no longer vacuous. **It does not prove items 16 and 24:** the phone still lists
    arm64-v8a, so the production check takes the "library missing" path, not the "no arm64-v8a" one.
    Reinstall normally afterwards. *Recommended: now — cheap — recorded for exactly what it shows.*
    ✅ **Done 2026-11-22, for exactly what it shows; items 16 and 24 still open (L1).** The SM-G990B can run 32-bit processes
    (`abilist32` armeabi-v7a; `app_process32` present). The debug and test APKs installed with
    `--abi armeabi-v7a` were a real 32-bit install: OpenCV and `libc++_shared` ship for arm64-v8a
    only, so neither was there. `FaceCapabilityDeviceTest` 2 of 2, logging `abi=arm …
    runsArm64=false capable=false` and `load: unavailable on abi=arm, as expected`. **It exercises the
    "library missing" branch, not "no arm64-v8a in `Build.SUPPORTED_ABIS`"** — the phone still lists
    arm64-v8a — so items 16 and 24 stay unmeasured on hardware (L1). Both packages were uninstalled
    afterwards and reinstalled normally.
- **G6 — before the walkthrough: item 23, and the hand-over.** `installDebug`, the media permissions
  granted, the app opened: on this capable phone with face data on, the disclosure shows at first
  open. Read its bounds from a `uiautomator` dump, tap outside them with `input tap`, dump again.
  **Exit:** the disclosure still showing, OK and Turn off present, nothing else changed. **Leave it
  showing** — W1 starts from it. *Recommended: now — cheap.*
  ✅ **Done 2026-11-22.** Fresh install, media permissions granted, `MainActivity` launched: the
  disclosure showed within 4 s — text-only `uiautomator` dumps: the title "People in your photos",
  five body paragraphs, Turn off and OK. A tap on the scrim 199 px below the card left it showing;
  one Back left it showing. Neither OK nor Turn off was tapped, and no index run started. **The app
  is left open on the SM-G990B with the disclosure showing**, for W1.
- **G7 — at W3: item 5, ADR-0023 verify item 6 through the real worker after the real disclosure.**
  While the maintainer runs Index now with the screen off: thermal status and charging before and
  after (`dumpsys thermalservice`, `dumpsys battery`); the app's peak RSS through `run-as` on
  `/proc/<pid>/status`, `VmHWM` reset before the run (repo memory: `clear_refs` works); and from
  `logcat -s FaceIndexPass` the images processed, the wall time and the mean per image. **Exit:**
  `face pass finished` with 0 failed, then grouping finished; every `FaceIndexPass` line one of
  `FaceIndexLogLines`; the figures recorded here and in ADR-0023's row 6. *Recommended: now — logcat
  only.* *Added 2026-11-22:* item 15's emission count, if it can be counted from outside the process
  during this run; nothing collects the projection with the screen off, so otherwise it stays L5.
  ✅ **Done 2026-11-23, at W3** — the real worker after the real disclosure, Index now, screen off,
  USB at 100 %. Worker 11:32:33 to 11:36:57, **264.2 s**: Tier A and places about 9.2 s (334 rows
  placed); **the face pass 254.2 s for 1,002 images** — `indexed=1002 failed=0 skipped=90` (the
  GIFs), stop `COMPLETED`, **mean 253.7 ms per image**, timed from the `capability checked` line;
  grouping stop `GROUPED`, at most 735 ms. Budget `facePassBudgetMillis=420000`, 60.5 % used — so
  **about ~~1,655~~ 1,600 images per 7-minute window on this phone**. *Corrected 2026-11-23 at
  review:* 1,655 divided the whole 420 s by the mean, but the budget is counted from the worker's
  start (`FaceIndexPass`), so on a backlog run the face pass has about 420 − 9.2 s after Tier A and
  places — about 1,619 images at 253.7 ms; counted from the worker's start, this run used about
  62.7 % of the budget. Thermal status 0 throughout (AP peak
  38.7 °C while the screen was briefly on, battery 34.0, skin 33.7); battery 100 %, charging; no
  process death. `VmHWM` 480,228 → 551,016 kB at the start of the face phase — a lifetime
  high-water mark: *deviation*, it could not be reset under `run-as`; peak sampled `VmRSS` 386,204 kB.
  Every `FaceIndexPass` line was one of the fixed forms; 0 lines under `cv::error()` and
  `org.opencv.imgproc`; the only E and W lines were unrelated (ExifInterface,
  WindowOnBackDispatcher, a libc property, a GC histogram). **The exit holds.**
  - **Slower than measured before.** 3.4's Task 5 ran the same 1,002 images through the worker in
    113.2 s, about 113 ms per image (grouping 292 ms): this run is about 2.25 times slower per image,
    with the screen off; 3.3's in-process figure was 150.7 ms. **So the 2,800-images-per-run
    projection no longer holds on this phone: about ~~1,655~~ 1,600 per window.** A larger library takes more
    runs; nothing fails. Recorded in ADR-0023's row 6.
    **The cause of the slowdown is not established** (raised at review, 2026-11-23). Known: the same
    phone and the same 1,002 images; G7 ran the installed app's worker from Index now, screen off,
    thermal status 0, charging at 100 %; 3.4's Task 5 ran the worker from an instrumented test,
    thermal status 0, charging; 3.3's in-process run was screen off at thermal status 1. **Not
    known:** 3.4 Task 5's screen state, which its record does not give, and any other difference
    between the runs, none having been compared. Each figure is one run, so G7's is the only
    measurement of the rate L11 now uses.
  - **Item 15's emission count** was not countable from outside the process: it stays **L5**.
  - **Settings' Media index label** after the run showed the later periodic no-op run — "Last
    indexed 12:09", "Waiting for conditions" — which overwrote the Index now run's label:
    `media_index_run_state` holds the last run's outcome, so the label described the later run.
- **G8 — at W4: items 19 and 21.** Coil's disk-cache directory (`cache/image_cache` by Coil's
  default; the Coder confirms it against `EasyGalleryApp`) listed through `run-as`, names and sizes
  only, with the New automatic album screen open on its Place tab, and again after the People grid
  has been scrolled end to end. `dumpsys gfxinfo` reset before that scroll and read after it, and the
  same for a scroll of Folders View's grid as the baseline. **Exit:** the two listings identical
  (item 21; ADR-0024 §6); the jank figures recorded beside the baseline. A difference in the listing
  stops 3.7. Jank well above the baseline, or stutter the maintainer sees, is a finding routed to the
  Designer and the Coder, not fixed in 3.7 unless he asks. *Recommended: now — cheap.*
  ✅ **Done 2026-11-23, at W4, with one limit stated.**
  - **Item 21 — passes, within a limit.** The listing was identical at all three readings, but no
    `cache/image_cache` directory ever existed, so the listing alone could not have shown a cache
    write. The guarantee is the request's policy — `diskCachePolicy(CachePolicy.DISABLED)` in
    `ui/components/PersonFaceImage.kt`, line 64 at `94f8e9c`, asserted by an existing test; the
    device reading is consistent with it and adds nothing beyond it.
  - **Item 19 — a recorded finding.** `dumpsys gfxinfo`, a scroll of Folders View's grid as the
    baseline against a scroll of the People tab:

    | Scroll | Frames | Janky | 50th | 90th | 95th | 99th | Missed Vsync | High input latency |
    |---|---|---|---|---|---|---|---|---|
    | Folders View (baseline) | 540 | 19 (3.52 %) | 8 ms | 10 ms | 13 ms | 85 ms | 8 | 1038 |
    | People tab | 265 | 22 (8.30 %) | 7 ms | 15 ms | 26 ms | 150 ms | 8 | 412 |

    The cost is on the UI thread — 20 slow-UI-thread frames, GPU 99th percentile 6 ms. The sample is
    small: 104 tiles, about 3 frames behind the 99th percentile, and the first-render burst may be in
    the window. **The maintainer saw no stutter.** By the exit criterion it is a finding, not fixed in
    3.7, and **not routed**, since he did not ask: a **polish candidate** — prefetching, or smaller
    crops.
  - **Item 20 — by the maintainer's eye, 2 of the 104 Person tiles seen are not recognisable.** 3.4
    measured 21 of 104 groups with no member at least 60 px across, so this is much better than
    feared. He reported W4 done and asked for no remedy, so no new decision.
- **G9 — after W5: item 1, read after a real deletion.** `MediaIndexSqliteDeviceTest`'s read-only
  method through `am instrument`, on the production index the walkthrough has just deleted from: the
  modes it committed under, and its Tier C row count. **Exit:** modes recorded, beside G4's; 0 Tier C
  rows; `PERSIST` or `exclusive` goes to the maintainer as in G4. *Recommended: now — cheap.*
  ✅ **Done 2026-11-23, after W5.** `MediaIndexSqliteDeviceTest#theProductionIndexModesAreRecorded`
  through `am instrument`, with `mediaIndexExpectNoFaceData=true`: `journal_mode` `wal`,
  `locking_mode` `normal`, `auto_vacuum` 1, `secure_delete` 1, `hasFaceData=false` — G4's modes, **no
  `PERSIST` and no `exclusive`**, and no Tier C row. No `media_index.db-journal`; `media_index.db-wal`
  0 bytes. The app's `firstInstallTime` unchanged: only the test APK was installed, and then removed.
  **The `face data deletion ran scrubbed=` line is not in the log, by design:** `FaceIndexPass.kt`
  logs it only for the worker's repeat deletion (line 329 at `94f8e9c`); the Settings switch calls
  `deleteAllFaceData()` from `FaceDataViewModel` (line 166), which logs nothing. So W5's scrubbed
  result was not read from outside; W5 showed no cleanup sentence, which shows only when a deletion
  reports not scrubbed.
- **G10 — after M1: L3 on the SM-G781B. Added 2026-11-23, the maintainer's answer to *Open at 3.7*
  question 1.** ~~⏳ **Accepted to run, not yet run.**~~ 🔶 **Run in part 2026-11-23; closed by the
  maintainer as option A, below.** Item 7's release-build capability check and a
  face pass, item 14's grouping and the worker, and item 1's modes, on that phone — as L3 describes.
  **Only if the phone is attached and its owner agrees, which the maintainer owns.** **After W7 and M1
  on the SM-G990B, never before:** the two phones must not both be attached for a connected run, and
  the run wipes the SM-G781B's app data. **Exit:** the figures recorded here, beside G4's and G7's,
  and in ADR-0023's verify table where a row covers them; `PERSIST` or `exclusive` goes to the
  maintainer as in G4. **If the maintainer does not attach it,** G10 is recorded as not run and L3
  falls back to accepted (question 4).
  **Run 2026-11-23 on the SM-G781B** — Android 13, API 33, arm64-v8a, 5,763,308 kB RAM; 5,081 images
  and 412 videos in `MediaStore`:
  - **Done:** `FaceCapabilityDeviceTest` 2 of 2, `FaceDecodeOrientationDeviceTest` 4 of 4 and
    `MediaIndexSqliteDeviceTest` 5 of 5 — one connected run, 11 tests, 0 failures. Its log:
    `abi=arm64 runsArm64=true capable=true`, `embeddingSize=128`. **Item 1's modes for that phone's
    production index:** `journal_mode` `wal`, `locking_mode` `normal`, `auto_vacuum` 1,
    `secure_delete` 1, `lowRamDevice=false` — **no `PERSIST` and no `exclusive`**, so no revisit;
    under a forced `TRUNCATE` a deletion reported scrubbed, and the `PERSIST` control reported not
    scrubbed.
  - **Not done (a) — the release build.** `app/build.gradle.kts` has no `signingConfig`, and the only
    release APK on disk, `app-release-unsigned.apk` of 2026-11-10, predates OpenCV and is not
    installable. Nothing was signed and no key invented: **item 7's release part needs the signing
    key.**
  - **Not done (b) — the face pass, grouping and worker figures.**
    `FaceGroupingWorkerMeasurementDeviceTest` first failed with its `ENVIRONMENT` message: `MediaStore`
    returned no live items to the app's process, because under a Gradle connected run on a fresh
    install the runtime permission grant had not reached `MediaStore` in the already-running process
    — a race on the test's side, not an app defect; with the permissions granted beforehand it ran.
    On that retry Tier A indexed 5,493 items in about 59 s and the face pass began (`capable=true`);
    75 s into it the phone reached **thermal status 2** (AP 41.6 °C, battery 38.1, skin 40.4;
    battery 64 %, charging; screen awake — it had started warm, at thermal status 1 and skin
    38.0 °C), and the run was stopped under the brief's own safety rule. `VmRSS` 381–408 MB;
    `VmHWM` 523,156 kB, the test process included. No face-pass figure. Thermal status stayed at 2
    at idle afterwards, so the heat was not the app's load alone. `FaceIndexPassMeasurementDeviceTest`
    was not run. **New limits from it: L13 and L14**, ~~pending the maintainer~~ accepted by him
    2026-11-23, later still (question 6).
  - **(c) Unexplained, read-only:** the first connected run's production-modes line read
    `hasFaceData=true`; three later runs read `false`. The cause is not identified.
  - **Afterwards:** the app and test packages uninstalled — the connected run wipes the app's data,
    so any face data indexed there is gone; no signing created; no repository change; no face data
    left the phone. **Read in the repository:** `app/src/main` has no reference to the thermal status
    or `PowerManager` — the face pass has no thermal back-off (L13).
  - **The maintainer's decision, 2026-11-23 — option A:** accept L3 as it stands — the capability
    and item 1 recorded, the face pass's cost on a 2020 phone left unknown, the thermal observation
    recorded — with no retry.

#### (ii) The walkthrough — the maintainer, by hand, on the installed debug app on the SM-G990B

From G6's state, the agent at the terminal for G7 to G9. He records each step's outcome here, or
tells the Planner, who does. A step that fails stops the walkthrough at that step.
*Outcomes recorded 2026-11-23 by the Planner, from the maintainer's report: W1 to W7 done, no step
failed, no defect found; W8 skipped by his decision.*
- **W1 — the disclosure at first open.** The title and five paragraphs as approved (ADR-0024
  Amendment 1 §5); Back does nothing; OK closes it. **Exit:** it closes, and no indexing run starts
  by itself (Amendment 1 §1) — Settings' Media index area shows none running.
  ✅ **Done 2026-11-23, from a fresh state:** the disclosure had already been answered overnight on
  this install, so the app's data was reset with `pm clear` first — no albums or results were lost.
  The disclosure matched the approved text; Back and a tap outside left it showing (as at G6); OK
  closed it; no indexing started by itself.
- **W2 — Settings.** The Face data area with its switch on; the Face licences row opens the dialog
  with the licence texts; turned to landscape, its OK still visible. **Exit:** as described.
  ✅ **Done 2026-11-23** — the Face data area, the Face data info row, and the Face licences row and
  its dialog, landscape included, as specified; no deviation reported.
- **W3 — Index now with the screen off** (G7 measures). **Exit:** Settings shows the run finished.
  ✅ **Done 2026-11-23** — G7's figures. Settings afterwards showed the later periodic no-op run's
  label, which had overwritten the Index now run's (G7).
- **W4 — the People tab** (G8 measures). On the New automatic album screen the People tab shows
  unlabelled thumbnails sorted by photo count, most first; scroll the whole grid fast (item 19); look
  at the tiles of small faces (item 20) and note how many are not recognisable. Choose one or two
  people and a place or a date; create the album. **Exit:** the album's member count equals the count
  the screen showed (count consistency, on a device); the Album info dialog lists the People entry;
  the small faces judged acceptable — if not, a remedy is a new decision, since the 60 px
  representative of ADR-0018 Amendment 8 was reversed for fixing 1 of 22 (decision 50, amended).
  ✅ **Done 2026-11-23, by the maintainer's report, with its count check not reported.** The People
  tab showed 104 people, the last 11 visible with 2 photos each; 2 of the 104 crops not recognisable
  (G8, item 20); no stutter seen (G8, item 19). He created an album with people. **Match count not
  reported:** neither the count the screen showed nor the album's member count was given, so the
  exit's count consistency is **not confirmed on a device**. An agent cannot read either from outside
  the process — both are in the UI, and the database stays on the phone (decision 9). Unit tests
  cover the count equality. The Album info dialog was not reported separately. *Open at 3.7*,
  question 7.
  ✅ **Count consistency observed on a device by hand, 2026-11-23, later still** (question 7, option
  A). On the SM-G990B, after a fresh debug install and a fresh face index — OK on the disclosure,
  Index now, the People tab — the maintainer created one automatic album from people tiles: the
  match count the New automatic album screen showed and the created album's member count were the
  same. **Not reported to the Planner:** the count itself, which tiles were chosen, and whether a
  place or a date was mixed in — so this is one album, one combination, by eye. No automated device
  test covers people counts (`RegionAlbumEvaluationDeviceTest` covers place and date only); that gap
  is accepted with option A. The JVM half stands on the unit tests named under the exit criterion
  *Count consistency*.
- **W5 — Delete face data.** In Settings, switch face data off. **Exit:** a confirmation dialog asks
  first (E8); after Delete, no cleanup sentence shows — it shows only when the deletion reported not
  scrubbed, which is then recorded; the people album shows the people-unavailable notice, not
  waiting and not the Newer-app wording, and is neither deleted nor rewritten (Album info shows the
  same people); the People tab is not offered. **G9 runs here.**
  ✅ **Done 2026-11-23, no deviation reported** — the Delete face data dialog asked first; no cleanup
  sentence; the people-unavailable notice; the album kept; no People tab. G9 ran here.
- **W6 — back on.** Force-stop and reopen the app — face data off, so the start-up deletion runs —
  then switch face data on. **Exit:** no crash; no second disclosure (E10); after Index now, the people
  album finds its people through their anchors and shows the members and count of W4.
  ✅ **Done 2026-11-23, no deviation reported.** G9's instrumentation run force-stopped the app,
  standing in for the force-stop. Reopened with face data off, no crash; switching on showed no
  second disclosure; the album found its people again. Its count against W4's was not reported
  (question 7).
- **W7 — Turn off from the disclosure.** The agent clears the app's data (`pm clear` — the albums
  go with it) and grants the media permissions; the app opened, the disclosure shows; Turn off.
  **Exit:** face data off in Settings; the People tab not offered; switching face data on shows no
  disclosure, since Turn off marked it shown (Amendment 1 §1 and §2); Index now then indexes faces.
  ✅ **Done 2026-11-23, no deviation reported** — `pm clear`, a fresh disclosure, Turn off; no People
  tab; switching on showed no disclosure; Index now.
- **W8 — optional: the long strings.** App language German, then Italian, at the phone's largest
  font: the New automatic album tab row (item 17, beside G4's test) and the people notices on an
  album tile (item 18, L6). **Exit:** what ellipsizes, noted for the native-speaker review.
  *Added 2026-11-23:* item 18 needs a people album with a notice on its tile, and W7's `pm clear`
  deletes the albums. So do W8 right after W5, while W4's album shows the people-unavailable notice;
  or, after W7, recreate a people album and bring it to a notice first.
  ⏭ **Skipped 2026-11-23 by the maintainer's decision, as recommended:** the tab row at font scale
  2.0 in German is covered by tests (G4, item 17); item 18 was not observed on a device and stays
  **L6**.

#### (iii) Known limits — recorded, not run

Each is the maintainer's to accept (*Open at 3.7* below), and each that stays true is in
`ARCHITECTURE.md` §11 after 3.8. **Accepted 2026-11-23 by the maintainer, L1 to L12** (question 4);
L3 is also accepted to run, as G10. *Added 2026-11-23, from G10:* **L13 and L14, ~~pending his
acceptance~~** (question 6) — question 4's answer does not cover them. **Accepted 2026-11-23, later
still (question 6, option A)**, so all fourteen are accepted.
- **L1 — items 10, 16 and 24 on a phone without arm64-v8a.** None is available. Proven by unit
  tests — `supportsFacePipelineAbi`, `FaceDataViewModel`, `PeopleAvailability` — and the test's
  branch in part by G5e (done 2026-11-22: the "library missing" path, on a 32-bit install).
  *Recommended: accept.*
- **L2 — item 7 on a non-Samsung arm64 phone.** None is available; both phones are Samsung.
  *Recommended: accept; run G1 and G7 there if one ever is.*
- **L3 — items 7 and 14 on the SM-G781B:** the release build's capability and a face pass, grouping
  and the worker. They need that phone again, and they index the faces in its photos (*Open at 3.7*,
  question 1). If not run: the same code, capable and upright there (G1); what stays unknown is time
  and memory on a 2020 phone. *Recommended: run them if the phone's owner agrees — about a quarter of
  an hour with `FaceGroupingWorkerMeasurementDeviceTest`; otherwise accept.* *Added 2026-11-22:* item
  1's modes on that phone are not read either; `MediaIndexSqliteDeviceTest` runs there unchanged, in
  the same session. *2026-11-23:* **accepted to run, not yet run** — G10, after W7 and M1, if the
  maintainer attaches the phone and its owner agrees; otherwise L3 stands accepted (questions 1 and 4).
  *2026-11-23, later:* **G10 ran in part** — the capability and item 1's modes recorded on the
  SM-G781B; the release build (no signing key), the face pass, grouping and the worker not measured,
  the face pass stopped at thermal status 2. **Accepted as it stands by the maintainer (option A),
  with no retry.** New from it: L13 and L14.
- **L4 — item 13, one giant connected component.** About 10,500 faces in one component fails with
  `OutOfMemoryError`, not the fixed message (3.4's Task 1: the Java heap at 233 of 256 MB at 10,000).
  The largest real component measured is 88. *Recommended: accept as debt, no heap-based cap now*
  (*Open at 3.7*, question 2). *Accepted 2026-11-23 as debt, with no heap-based cap.*
- **L5 — items 11, 12 and 15 at real scale.** No library of 10,000 faces or more is on either phone.
  G5c's synthetic figures and the arithmetic stand in for a real library through the worker, the
  real window, and the real emission count during Index now. *Recommended: accept.* *Narrowed
  2026-11-22 by G5c:* the grouping, the store's read and write, one projection emission and the
  picker's query are measured at 10,000, 20,000 and 37,000 synthetic faces. What stays unmeasured is a
  real library at that size through the worker and its real window, and how often the projection
  emits during Index now — by reading, up to once per image written (G5c). *2026-11-23:* G7 could
  not count the emissions from outside the process; this stays.
- **L6 — item 18, the long translations on an album tile.** The tile ellipsizes by design; the Album
  row and Album Detail View wrap. The remedy is a shorter translation, at the native-speaker review.
  *Recommended: accept; W8 notes which.*
- **L7 — Room's own rollback-journal choice.** Room picks `TRUNCATE` only on a low-RAM device, which
  neither phone is (G4 records it); G4's forced `TRUNCATE` stands in. *Recommended: accept.*
  *2026-11-22:* G4 read `isLowRamDevice` false on the SM-G990B; on its F2FS storage the forced
  rollback journal had to be made to spill, with `PRAGMA cache_size=10`, before there was a file to
  inspect.
- **L8 — the image memory cache emptied by a switch-off** (3.6's device-only list): not observable
  from outside the process; its unit test stands. *Recommended: accept.*
- **L9 — F1's residual,** how an API 36.1 device reads the fourth rules section: no such device.
  *Recommended: accept; it blocks nothing* (*Open questions* F1).
- **L10 — one OpenCV line on a native error. Added 2026-11-22, from G5a; ~~pending the maintainer~~.**
  A native OpenCV error prints one line under `org.opencv.imgproc`, priority E — the Java wrapper's
  "caught cv::Exception", with OpenCV's assertion text and an OpenCV source path — which
  `Core.setErrorVerbosity(false)` does not silence and for which no Java-side switch was found. For
  the resize assertion provoked it held no URI, box or embedding. It is outside `FaceIndexLogLines`
  and outside what the logger test proves, so ADR-0024 §6 is met for the pass's own lines only.
  OpenCV's value-printing checks can put operand values — sizes, or coordinates — in such a text; none
  was observed. *Recommended: accept as a recorded limit:* the line names OpenCV's own source and
  assertion, not face data, and a native error is already a per-image failure bounded by three
  attempts. The alternative, a precondition check before each OpenCV call so that no assertion is
  reached, is code in the face path that cannot cover every assertion OpenCV may raise (*Open at
  3.7*, question 5). *Accepted 2026-11-23 as a recorded limit; no precondition checks.*
- **L11 — grouping's capacity in one worker run. Added 2026-11-22, from G5c.** Grouping fits about
  31,000 faces after a face pass that used its full 7-minute budget, and about 56,000 in a whole
  window — on the SM-G990B; less on a slower phone (L3)~~, and less again by whatever Tier A and the
  place pass took first~~. *Corrected 2026-11-23 at review:* the face pass's 420 s budget is counted
  from the worker's start (`FaceIndexPass`), so a full-budget pass ends about 420 s in and grouping
  always has about 180 s: what Tier A and the place pass take comes out of the face pass's images,
  and out of the whole-window figure, not out of the 31,000. It writes once, at the end, and is not resumable (*3.4*, *Where it runs*;
  ADR-0023 §4's *Built* note), so a grouping cut by WorkManager's stop writes nothing that run.
  - **Up to about 31,000 faces:** it fits, whatever the face pass did.
  - **About 31,000 to 56,000:** every run whose face pass uses its whole budget — every run while the
    pass has a backlog, at about ~~2,800~~ ~~1,655~~ 1,600 images a run (the SM-G990B, screen off — G7,
    2026-11-23) — is cut and spends its remaining minutes for
    nothing. Once the backlog is done the pass ends early, grouping starts sooner and fits, and the
    groups appear. So *3.4*'s "nothing waits for ever" holds, but no group appears, and none changes,
    until the backlog is done.
  - **Above about 56,000:** grouping cannot finish even in a whole window, so every run is cut, no
    group ever appears, and the People picker never offers anyone. Safe — nothing is written — but
    the CPU is spent on every run. The same failure the old arithmetic put above 68,000.
  - The largest real library measured is 422 faces. *Recommended: accept, and revisit on a real
    report* (*Open at 3.7*, question 2, with the options). *Accepted 2026-11-23 — option (a): a
    recorded limit, revisited on a real report.*
- **L12 — the download budget's margin. Added 2026-11-22, from G5d.** The arm64 total is
  49,712,393 B: 287,607 B under 50,000,000 B if the total is what "50 MB" bounds. A3 and ADR-0023
  bound what the face models and OpenCV *add*, +46,063,982 B, unchanged since 3.3, 3,936,018 B under.
  Neither says decimal or binary. *Recommended: accept, reading "50 MB" as 50,000,000 B and the budget
  as the record words it — what the face path adds — with the total recorded beside it at every
  re-check, since nothing else in the app has a size budget; the maintainer confirms the reading.*
  *Accepted 2026-11-23 as recommended, with that reading (question 4).*
- **L13 — the face pass and grouping have no thermal back-off. Added 2026-11-23, from G10; ~~pending
  the maintainer~~.** Nothing in `app/src/main` reads the thermal status or `PowerManager`. On the
  SM-G781B a face pass reached thermal status 2 75 s in, while charging with the screen on after a
  warm start (G10); on the SM-G990B thermal status stayed 0 throughout a full run (G7). Production
  runs charging and idle with the screen off (WorkManager's constraints), the opposite of G10's
  conditions. *Recommended: accept, and revisit on a real report or a user complaint.* If it bites,
  the options are: read the thermal status between pages and stop the pass as a budget stop does —
  a small Coder change, with a note in ADR-0018 or ADR-0023 — or lower the per-run budget.
  *Accepted 2026-11-23, later still (question 6, option A):* revisited on a real report or a user
  complaint. *Corrected the same day:* "production" above is the periodic job; Index now carries no
  charging or idle constraint (`ARCHITECTURE.md` §11, the indexing job's two API-level limits), so a
  user-started run can meet G10's conditions. The acceptance and its trigger are unchanged by it.
- **L14 — `FaceGroupingWorkerMeasurementDeviceTest` fails on a fresh install under a Gradle connected
  run. Added 2026-11-23, from G10; ~~pending the maintainer~~.** On the SM-G781B it failed with its
  `ENVIRONMENT` message: the permission grant had not reached `MediaStore` in the already-running
  process. A test-side race, not an app defect; the test is opt-in. Run it with the permissions
  granted beforehand, through `am instrument`. *Recommended: accept; the test-side fix deferred.*
  *Accepted 2026-11-23, later still (question 6, option A), the test-side fix deferred.*

#### Also in 3.7 — not a device check

- **P1 — the Play Data Safety facts. Planner.** ✅ **Done 2026-11-22**, as *P1* under *The Play Data
  Safety check*: the facts, the merged permission list read from the manifests on disk (md5s equal
  to 3.6's; G3 re-checks them), draft answers per Play Console category, and four points for the
  maintainer to confirm in the console. A draft, not an answer. ~~Prepared under *The Play Data Safety check*: what
  the face path reads, writes, keeps and backs up, and the merged permission list — unchanged since
  3.3, by G3's md5s.~~ For the maintainer to answer before the first Play release that ships the Tier
  C pass; the exit criterion needs the answer recorded, or carried (*Open at 3.7*, question 3).
  *2026-11-23:* points 1 and 2 resolved by the maintainer's position, recorded under *The Play Data
  Safety check*; points 3 and 4 still open; the form not submitted. *2026-11-23, later:* **carried
  past 3.8 by the maintainer (option B)** — *Open at 3.7*, question 3.
- **V1 — the UI vocabulary's edits to existing entries. Designer.** Routed since 3.6 (*3.6 — Built
  and verified 2026-11-22*, *Not proven by it*); 3.8's entry condition 5. ✅ **Committed in
  `94f8e9c`, 2026-11-23** — the Newer-app notice, the Album info dialog's People entry, the Status
  area, the New automatic album screen and Pick, checked with `git show`; read by the Planner for
  presence, not reviewed as the Designer's work.

#### Open at 3.7 — for the maintainer

1. **The SM-G781B again?** For L3: the release build's capability check and a face pass, grouping and
   the worker on it — and, added 2026-11-22, item 1's journal and locking modes. It runs face indexing
   over that phone's photos, on that phone — whose photos
   they are, and whether that is acceptable, is his to say. *Recommended: yes, if the phone's owner
   agrees; otherwise accept L3.* **Answered 2026-11-23: "go with the recommendation"** — yes, run L3,
   provided the phone is attached and its owner agrees, which the maintainer owns. Scheduled after W7
   and M1 on the SM-G990B, as **G10**: the two phones must not both be attached for a connected run,
   and it wipes that phone's app data. Accepted to run, not yet run; if he does not attach it, L3
   falls back to accepted. *2026-11-23, later:* **G10 ran in part, and the maintainer closed it as
   option A** — L3 accepted as it stands, no retry (G10).
2. **A heap-based cap on one giant component (L4)**, open since 3.4. *Recommended: no cap now;
   accepted as debt, revisited on a real report.* **Added 2026-11-22 — grouping's time capacity
   (L11):** about 31,000 faces after a full-budget face pass, about 56,000 in a whole window. The
   options:
   - (a) **Accept it as a recorded limit, revisited on a real report.** Costs nothing now. Above
     31,000 faces groups wait for the face backlog; above 56,000 they never appear, and each run
     spends its remaining window on a grouping that writes nothing. *Recommended:* the largest real
     library is 422 faces, and 31,000 faces is about 74,000 photos at its density.
   - (b) **Make grouping resumable.** 97 % of its time at 37,000 is ~~the all-pairs search for
     components~~ compute — the all-pairs search for components, the linkage and the medoid together,
     which G5c did not time apart *(corrected 2026-11-23 at review)*; its state between runs is a union-find over every face and a cursor into the pairs.
     Persisting it is a new table, `Migration(6, 7)` and an ADR-0018 amendment, ADR-0023 §4's "not
     resumable part-way" amended, a decision in [albums.md](albums.md) §8, and a restart rule for
     faces added or deleted meanwhile. Any size then finishes, over several runs.
   - (c) **Make grouping cheaper.** Two routes, both unmeasured. *Incremental components:* new faces
     compared against all, not all against all; the groups stay exact because *3.4*'s proof holds for
     any partition coarser than the true components — but it needs a component id stored per face,
     so a migration and an amendment as in (b). *Faster distances:* a vectorised or native dot
     product, with no schema change — but a different rounding can move a pair across 0.50, so it
     needs the proof re-checked and perhaps `FACE_GROUPING_VERSION` bumped. If L11 ever bites, this
     is the cheapest first step.
   - (d) **Change nothing and record nothing further.** The corrected figure is already in
     `ARCHITECTURE.md` §11; but without his acceptance L11 stays open, and 3.8's entry condition 3
     does not hold.

   **Answered 2026-11-23: (a)** — L11 accepted as a recorded limit, revisited on a real report; L4
   accepted as debt, with no heap-based cap.
3. **The Play Data Safety question — answered before 3.8, or carried.** The exit criterion needs it
   answered and recorded. *Recommended: answered, from P1, before 3.8.* Carrying it, as Stage 2
   carried its Play Console declaration, is a departure only he can declare. **2026-11-23: position
   given; submission and points 3–4 outstanding.** The maintainer's position, resolving P1's points 1
   and 2, is recorded under *The Play Data Safety check*. Points 3 and 4 are open, the form is not
   submitted, and the question is not declared carried — so it is **not yet answered** in the sense
   3.8's entry condition 6 needs. **Answered 2026-11-23, later: B — carried past 3.8**, as Stage 2
   carried its Play Console declaration. It stays a pre-release item: points 3 and 4 — the app-wide
   answers, and the externally hosted privacy policy describing on-device face grouping and the Face
   data switch — stay open, with the submission, before the first Play release that ships the face
   pass. By that declaration 3.8's entry condition 6 is met.
4. **The known limits ~~L1 to L9~~ L1 to L12, accepted** (L10 to L12 added 2026-11-22). *Recommended:
   all, as recommended for each.* **Answered 2026-11-23: L1 to L12 accepted.**
5. **The `org.opencv.imgproc` line (L10)**, added 2026-11-22: accept it as a recorded limit, or add
   precondition checks so that no OpenCV assertion is reached. *Recommended: accept* — the line
   names OpenCV's source and assertion, not face data; a precondition check cannot cover every
   assertion. Choosing the checks is a Coder task in the face path, then G5a and G3 again.
   **Answered 2026-11-23: L10 accepted as a recorded limit; no precondition checks.**
6. **L13 and L14, accepted?** Added 2026-11-23 from G10; question 4's answer named L1 to L12 and does
   not extend to them. L13: the face pass and grouping have no thermal back-off. L14: the opt-in
   worker measurement test's permission race on a fresh install. *Recommended: accept both — L13
   revisited on a real report or a user complaint, L14's test-side fix deferred.* ~~**Open.**~~
   **Answered 2026-11-23, later still: A — both accepted,** L13 revisited on a real report or a user
   complaint, L14's test-side fix deferred. With question 4's answer, L1 to L14 are accepted, and
   3.8's entry condition 3 is met.
7. **W4's count on a device.** The Stage 3 exit criterion *Count consistency* asks for the screen's
   count and the evaluator's member count to agree "on the JVM and on a device"; W4's comparison was
   not reported, and no device test covers people counts — `RegionAlbumEvaluationDeviceTest` checks
   place and date only (read 2026-11-23). The options:
   - (a) **he reads the two numbers on the phone:** a people album created on the New automatic album
     screen, the count it showed beside the album's member count. Face data is on after W7.
     *Recommended* — cheap, and it needs nothing to leave the phone;
   - (b) **accept the unit tests for it,** the device half recorded as not observed — a departure
     from the criterion's wording, only his to declare, stated in [albums.md](albums.md) §7 at
     close;
   - (c) **a Coder task, an opt-in device test** in the shape of `RegionAlbumEvaluationDeviceTest`
     for people, over the app's real index — then the definition of done again.

   ~~**Open.**~~ 3.8's step 2 checks this criterion and cannot mark it met without one of the three.
   **Answered and closed 2026-11-23, later still: A** — done by the maintainer by hand on the
   SM-G990B, after a fresh debug install and a fresh face index: one album created from people tiles,
   the screen's match count and the album's member count the same. The figures, the tiles and any
   place or date mixed in were not reported. Recorded under W4 and against the exit criterion.

#### Review of 3.7 — 2026-11-23

The Reviewer's pass over 3.7's change — the code and tests at `94f8e9c`, and the documents on disk.
**Verdict on the code and tests: approve with changes** — 0 blocker, 0 major, 4 minor, and nits. Its
documentation findings are applied, dated, in this plan, `ARCHITECTURE.md`, [albums.md](albums.md)
and ADRs 0022 to 0024; its one major, the gate's evidence, is 3.8's entry condition 4 — *met
2026-11-23, final*. *After the fixes:* **approve** — the re-review at the end of this section.
- **Changed — the Coder, tests only:**
  - the Face licences dialog's landscape run in `FaceDataDialogsTest` now puts
    `accelerometer_rotation` back as well as `user_rotation` (minor);
  - `FaceNativeErrorLogDeviceTest` waits for a deterministic logcat marker instead of a fixed sleep,
    and filters by the app's pid (minor); its silent-mode check asserts only that `cv::error()` stays
    silent, where it contradicted the accepted L10 (minor);
  - `MediaIndexTierCScaleDeviceTest` fails with an `ENVIRONMENT` message when no size finished (nit);
  - `NewAutomaticAlbumContentTest`'s environment failure carries the `ENVIRONMENT` prefix (nit).

  ~~**Applied in the working tree, to be verified by the second gate (entry condition 4); not yet
  committed.** That they pass is not claimed here.~~ ✅ **Fixed 2026-11-23 — verified by the final
  gate and approved by the re-review:** the `accelerometer_rotation` restore; the native-error race,
  and the silent-mode check's contradiction with L10; the scale test's `ENVIRONMENT` failure;
  `NewAutomaticAlbumContentTest`'s `ENVIRONMENT` prefix. The final gate ran over them (3.8's entry
  condition 4) and the phone read `accelerometer_rotation` 1 and `user_rotation` 0 after it. In the
  working tree, **not yet committed** — 3.8's entry condition 7.
- **Not changed, with the reason:**
  - `MediaIndexSqliteDeviceTest`'s hidden-API reflection branch below API 35, which the SM-G990B (API
    36) does not exercise (minor): answered by evidence — the SM-G781B, API 33, ran all five of its
    tests, the reader-blocked checkpoint test included, green on 2026-11-23 (G10);
  - `theProductionIndexModesAreRecorded` is not opt-in and creates the production index file in every
    connected run (nit): accepted — it only reads, and the app's own first open does the same.
- **Re-review of the four test fixes — 2026-11-23.** **Verdict: approve** — 0 blocker, 0 major,
  0 minor; four optional cleanups. **All four deferred:** applying any of them changes tests again,
  which would need another gate over the final tree, and the remediation is capped at two rounds —
  the review and this one. They are candidates for a later change, not a condition of 3.8:
  - (a) `FaceDataDialogsTest.kt`, the licences tests' `finally` (about lines 291 to 296): one failed
    restore step — an exception in `setRotation(UNFREEZE)` or in the first `settings put` — can skip
    the other restore. The cleanup: each step in `runCatching`, the first failure rethrown after
    all have run.
  - (b) `MediaIndexTierCScaleDeviceTest.kt` (about line 78): the new `ENVIRONMENT` failure message
    omits the size and the reason, and could label a real out-of-memory or stop result at 10,000
    faces as an environment problem. The cleanup: size and reason in the message, and `ENVIRONMENT`
    only when the monitor set an abort reason.
  - (c) `FaceNativeErrorLogDeviceTest.kt`: the logged field `ownProcess` is now always true, and
    `linesAfterMarker` and the KDoc describe a narrower set after the pid filter than they say.
  - (d) `FaceDataDialogsTest.kt`: `Settings.System.ACCELEROMETER_ROTATION` is read through the
    constant but written as the literal `accelerometer_rotation` — the same string.

### 3.8 — Close-out — refined 2026-11-22

**Entry condition — all ~~seven~~ eight hold (M1 added 2026-11-22), checked by the Planner against this document and the
repository, not against reports, before any of 3.8 starts:**
*2026-11-23, final:* 1 to 6 and 8 met; 7 not held until the maintainer's commit. 3.8 not started.
1. Every agent item G1 to G9 is ✅ with its figures recorded under *3.7 — Device verification and
   the installed-app walkthrough*, or moved to the known limits by the maintainer. *2026-11-23:* G10,
   added after M1, is either run and recorded, or recorded as not run because the SM-G781B was not
   attached — L3 then standing accepted. Either settles it for this condition. ✅ *Met 2026-11-23,
   later:* G1 to G9 ✅ with their figures; G10 run in part and recorded, closed by the maintainer as
   option A.
2. The walkthrough W1 to W7 is done, each step's outcome recorded; every defect it found is fixed by
   its own task and checked again at the step that found it. ✅ *Met 2026-11-23, later:* W1 to W7
   done and recorded, no defect found; W8 skipped. W4's count comparison was not reported — recorded
   as such; it bears on the exit criterion *Count consistency*, question 7, not on this condition.
   *2026-11-23, later still:* that caveat is closed — W4's count observed equal by hand on the
   SM-G990B (question 7, option A).
3. The known limits ~~L1 to L9~~ L1 to L12 (three added 2026-11-22) are accepted by the maintainer,
   with the date. ✅ *Met 2026-11-23: all twelve accepted (Open at 3.7, question 4).* *2026-11-23,
   later:* L13 and L14 were added from G10 after that answer; this condition is not extended to them
   by default, but they are open until he answers question 6. ✅ *Met for L1 to L14, 2026-11-23,
   later still:* L13 and L14 accepted (question 6, option A).
4. The definition of done has passed once over the final tree — after the last code or test change
   of 3.7 — with `adb devices` checked and stated (G3, or its re-run). ⏳ *Not met 2026-11-23,
   later:* G3 ran on 2026-11-22 over the then-uncommitted tree; the maintainer then edited
   `MediaIndexSqliteDeviceTest.kt` and `MediaIndexTierCScaleDeviceTest.kt` before committing
   `94f8e9c`, and M1's clean run covered three classes only. Both files are new in that commit, so
   `git log -p` cannot show what changed after G3. **A full re-run over the committed tree is
   required** — the verification phase's. ⏳ *2026-11-23, later still — pending on this one run
   only:* the verification phase's definition-of-done gate over the committed tree (HEAD `94f8e9c`)
   was running while this was written. Its result is in its own report and is not claimed here.
   **Next session: record the gate's figures here** — the commit it ran over, `adb devices` as
   stated, `testDebugUnitTest`'s classes, tests and failures (with `LocaleResourceParityTest` re-run,
   as G3 noted), and `connectedDebugAndroidTest`'s cases, failures, errors and skips — or, if it
   failed, the failure, whose fix is a task of its own followed by the gate again.
   🔶 *2026-11-23, latest — passed once over `94f8e9c`, with a flake; to be re-run once after the
   review's fixes.* From the verification phase's report, recorded by the Planner, who ran nothing:
   over the committed tree at `94f8e9c`, on the SM-G990B, the only device `adb devices` listed.
   `compileDebugKotlin` OK; `testDebugUnitTest` after a forced `cleanTestDebugUnitTest`: 84 classes,
   1427 tests, 0 failures (`LocaleResourceParityTest` 11, `BackupRulesParityTest` 9);
   `compileDebugAndroidTestKotlin` OK. `connectedDebugAndroidTest`, **first run failed:** 346 test
   cases, 2 failures, both in `MediaIndexingWorkEnqueueTest` —
   `indexNowRunsWorkerToSuccessAndPreservesOrGrowsIndexRows` (`MediaIndexingWorkEnqueueTest.kt:153`)
   and `permissionDeniedRunDoesNotAdvanceWatermarksAndLaterGrantedRunIndexes` (`:300`), both "Timed
   out waiting for … work to finish"; BUILD FAILED in 11m12s. That class alone then passed, 3 of 3,
   in 54 s. **Second full run green:** 346 test cases, 0 failures, 0 errors, 6 skipped — the same six
   opt-in tests as G3 — BUILD SUCCESSFUL in 7m46s. The merged manifests' md5s unchanged (debug
   `8856dbd4b4257bd665c4593a325ba624`, release `043a521e4d0a25724364c8b8b6ea6f8d`); `app/schemas`
   unchanged. **The two timeouts are an intermittent flake in a worker test 3.7 did not touch,
   recorded as a finding, not as a pass without qualification;** no fix is planned for it here.
   ~~⏳ **Not met:**~~ the Reviewer's four test fixes (*Review of 3.7*) are a test change after this gate,
   so by this condition's own rule **the gate runs once more over the final tree** — the
   verification phase's. **Record that second run's figures here**, with the commit or working tree
   it ran over and `adb devices` as stated; if the worker-test timeouts recur, that is recorded with
   this run's, as a finding.
   ✅ *Met 2026-11-23, final — this is the run that counts for this condition;* the two above are
   kept as history. From the verification phase's report, recorded by the Planner, who ran nothing.
   The definition of done ran once over the final tree — HEAD `94f8e9c` plus the Reviewer's four test
   fixes and the documents, uncommitted: the tree as it is to be committed (condition 7) — on the
   SM-G990B, the only device attached (`adb devices -l`: `RFCT703FBKR device product:r9qxxx
   model:SM_G990B`). `compileDebugKotlin` OK, all up to date. `cleanTestDebugUnitTest
   compileDebugUnitTestKotlin testDebugUnitTest`: 84 classes, 1427 tests, 0 failures, 0 errors,
   0 skipped (`LocaleResourceParityTest` 11, `BackupRulesParityTest` 9).
   `compileDebugAndroidTestKotlin` OK, up to date — the test files had been compiled by the earlier
   targeted runs after their last edit. `connectedDebugAndroidTest`, the full suite once: BUILD
   SUCCESSFUL in 7m12s, 28 classes, 346 test cases, 0 failures, 0 errors, 6 skipped — the same six
   opt-in tests: `FaceGroupingMeasurementDeviceTest`, `FaceGroupingWorkerMeasurementDeviceTest`,
   `FaceIndexPassMeasurementDeviceTest`, `FaceFavouriteToggleDeviceTest`,
   `FaceNativeErrorLogDeviceTest`, `MediaIndexTierCScaleDeviceTest`. `MediaIndexingWorkEnqueueTest`
   3 of 3: the first gate's flake did not recur — which does not withdraw it as a finding. The merged
   manifests' md5s unchanged (debug `8856dbd4b4257bd665c4593a325ba624`, release
   `043a521e4d0a25724364c8b8b6ea6f8d`); `app/schemas` unchanged; `git diff HEAD -- app/src/main`
   empty — the review's fixes changed no production code. After the suite the phone read
   `accelerometer_rotation` 1 and `user_rotation` 0: the licences tests left them as they were. The
   connected run installed and uninstalled the app; it is not installed now, its data wiped.
   `lintDebug` is not part of this — it does not run on this machine (`AGENTS.md`, *Running Gradle*).
5. The Designer's edits to the UI vocabulary's existing entries are made (V1). ✅ *Met 2026-11-23:*
   committed in `94f8e9c` (V1).
6. The maintainer's answers to *Open at 3.7* are recorded — question 3 answered, or its carrying
   declared by him. *2026-11-23:* questions 1, 2, 4 and 5 answered and recorded. Question 3:
   **position given 2026-11-23; submission and points 3–4 outstanding.** ~~**Not yet met** — it is met
   when he says the form is submitted and its outcome is recorded, or declares the question carried.~~
   ✅ *Met 2026-11-23, later:* he declared question 3 carried past 3.8 (option B). Questions 6 and 7,
   added later the same day, are open; they bear on condition 3 and on the exit criteria, not on
   this one as worded. *2026-11-23, later still:* both answered, A and A; all seven questions are
   answered.
7. 3.7's tests and any fix are committed; checked with `git status` and `git ls-files`, never asserted
   in a document. *2026-11-23:* held at `94f8e9c` — the working tree clean before this run's
   documents; it holds again only once they are committed. ⏳ *2026-11-23, later still:* not held —
   `git status` at the start of this run showed the plan, ADR-0023 and `ARCHITECTURE.md` uncommitted
   from the previous run, and this run edited the plan and `ARCHITECTURE.md` again. It holds once the
   maintainer has committed them, checked with `git status`.
   ⏳ *2026-11-23, final — not held; the only condition open.* `git status` at this entry: HEAD
   `94f8e9c`; uncommitted, the four test files —
   `app/src/androidTest/java/com/davide/seddio/easygallery/ui/components/FaceDataDialogsTest.kt`,
   `app/src/androidTest/java/com/davide/seddio/easygallery/data/FaceNativeErrorLogDeviceTest.kt`,
   `app/src/androidTest/java/com/davide/seddio/easygallery/data/MediaIndexTierCScaleDeviceTest.kt`,
   `app/src/androidTest/java/com/davide/seddio/easygallery/ui/NewAutomaticAlbumContentTest.kt` — and
   six documents: `ARCHITECTURE.md`, ADR-0022, ADR-0023, ADR-0024, this plan and
   [albums.md](albums.md). It holds once the maintainer has committed exactly those, `git status`
   clean afterwards. **The commit contains no change under `app/src/main`** — none exists,
   `git diff HEAD -- app/src/main` empty — and its code and tests are the tree the final gate ran
   over (condition 4); only documents were edited after it. A code or test edit before the commit
   would send the gate round again.
8. **M1 — G2's planned mutations**, added 2026-11-22, deferred from G2 until after W7: one planned
   mutation per regular new test — `MediaIndexSqliteDeviceTest`, and the new methods in
   `FaceDataDialogsTest` and `NewAutomaticAlbumContentTest` — each seen to fail it and each reverted,
   `git diff app/src/main` empty afterwards. Run by the Coder after W7, never before: it needs a
   connected run, which uninstalls the app. Recorded under G2 with each mutation named. ✅ *Met
   2026-11-23* — G2's M1 note.

**Then, in order — Planner:**
1. `ARCHITECTURE.md`: §11 brought to what 3.7 settled and left — each accepted limit present, each
   settled item gone — and the exit criterion's links to ADRs 0022 to 0024, ADR-0017 Amendment 4 and
   ADR-0018 Amendment 5 checked in the sections they govern. 3.7's device tests trigger nothing else.
2. Every line of *Stage 3 exit criteria* checked against the repository, one by one, with its evidence
   — a file, a test, or a recorded device result — as Stage 2's lines were in [albums.md](albums.md) §7.
3. `/docs-audit` ([docs-audit.prompt.md](../../.github/prompts/docs-audit.prompt.md)), its findings
   fixed or recorded.
4. **Archive:** the exit criteria and the record that each was met move into [albums.md](albums.md)
   §7, *Stage 3*; this plan gains `Archived — Stage 3 closed YYYY-MM-DD` beneath its title; its row in
   `AGENTS.md`'s documentation map goes, and every row of that map is walked — `AGENTS.md`
   *Conventions* and the `.github/agents/*.agent.md` that name Stage 3 as in progress included.
5. **Only then** Stage 3 moves to ✅ Complete in [albums.md](albums.md) §7, with a stage-log line —
   or, if the maintainer declares it complete with a criterion carried, the departure is stated
   there, as Stage 2's was.

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
  like Stage 2's; combined people + region + time, with tests. Refined 2026-11-21 into its own
  section above.
- **3.6 — The UI.** The People criterion with its thumbnails, with no merge and no split (decision
  40); the notice of an unevaluatable people album, whose wording is open (decision 42); the
  disclosure, and the Settings switch; the Settings row showing the licence notices (E5); strings in
  every shipped locale; vocabulary in-change. Refined 2026-11-21 into its own section above, which
  carries the five items below into its tasks: items 1 to 3 into Task A, item 4 into Task B (the
  limit, 8, answered — D3), item 5 into Task C. Built and verified 2026-11-22 — all five items done
  (*3.6 — Built and verified 2026-11-22*).
  - ~~**Added 2026-11-21 from 3.5** — left to 3.6 by *3.5*'s *status, causes and availability*:
    carrying `AlbumUnevaluatableCause` to `AlbumListItem` and branching the notice on it **before any
    build that can write a `people` clause ships** — until then every cause shows the Newer-app
    notice; calling `refreshPeopleAvailability()` from the switch, after the capability check and on
    resume; a durable capability answer on a phone with no pipeline, without which a restored people
    album waits for ever there; and how many people one album may hold under the encoder's 16 KiB
    cap.~~ *Made explicit 2026-11-21, when 3.5 was built* — the five items below.
  - **Added 2026-11-21 from 3.5 — 3.6 must do each of these; none is optional:**
    1. **A durable capability answer on a phone without arm64-v8a.** The face pipeline ships for
       arm64-v8a only, and minSdk 28 also allows an armeabi-v7a phone. There `FaceCapabilityChecker`
       catches `FacePipelineUnavailableException` and records nothing, so the availability stays
       unknown, which is available, and a restored people album **waits for ever** — the failure
       ADR-0024 §4 names. Either treat a `Build.SUPPORTED_ABIS` without `arm64-v8a` as
       `DEVICE_INCAPABLE`, or record a per-key "no pipeline" answer when the exception fires — the
       key names the build, so the next build is still asked afresh. Out of memory stays transient
       and records nothing.
    2. **The app-open capability check and the start-up deletion's call.** Built in 3.3 and 3.2, and
       called by nothing in `ui/`: without the check no answer is ever recorded, and without the
       deletion's call face data switched off is not removed again at start-up (ADR-0024).
    3. **`refreshPeopleAvailability()` called from the switch, in both directions, after the
       capability check, and on resume** — the precedent is
       `FavouritesViewModel.refreshCapabilityState()` from `MainActivity.onResume`. *Why both
       directions:* switching face data on, and the check's answer, write no Tier C row; switching
       it off deletes Tier C, but when Tier C is already empty no row changes, Room never
       invalidates, and the projection does not emit — so without the call the album keeps its old
       status.
    4. **A limit on the number of people per album, and/or catching the encoder's `require` in
       `createAutomaticAlbum`.** The rule's 16 KiB cap holds about 8 to 50 people, depending on the
       anchor photos' name lengths: 8 fit with 255-character names escaped throughout, 10 do not.
       Today an oversized rule throws from `AlbumRuleJson.encode` on the calling thread, before the
       launch, so nothing is written but nothing catches it either. The limit itself is a product
       choice for the maintainer (*Next session starts here*).
    5. **The cause carried to the notices.** `AlbumUnevaluatableCause` carried from
       `AlbumRuleEvaluation` through `ComputedAlbum` to `AlbumListItem`, and the notice branching on
       it, **before any build that can write a `people` clause ships** — until then every cause
       shows the Newer-app notice, whose wording is wrong for the three new causes (ADR-0017
       Amendment 4). The notices' strings go into every shipped locale
       (`LocaleHelper.supportedLanguageTags`) in the same change. And under ADR-0024 §6:
       `MediaIdentity`'s and `MediaHealingKey`'s `toString`s still print the URI and the display
       name, so no log line, message or string may interpolate an anchor or either type.
- **3.7 — Verification.** The definition of done, once, with `adb devices` checked and stated;
  then an installed-app walkthrough including the delete switch, and the capability check on the
  SM-G781B if it can be attached (decision 45). Refined 2026-11-22 into its own section above, which
  places each of the items below once — as an agent item, a walkthrough step or a known limit; the
  items stay here as the source list.
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
       deletion's result depends on the modes it committed under; 🔶 **the SM-G990B done 2026-11-22**
       (*3.7*, G4: `wal`, `normal`, no `PERSIST`, no `exclusive`; a forced rollback journal scrubbed),
       read again after the walkthrough's real deletion at G9; the SM-G781B's not read (L3); Room's
       low-RAM choice unreachable (L7); ✅ *2026-11-23:* G9 read the same modes after W5's real
       deletion, with no Tier C row, and G10 read `wal` and `normal` on the SM-G781B;
    2. the platform's real SQLite defaults — `auto_vacuum`, the journal mode, and `secure_delete`
       reading back as on — rather than Robolectric's; ✅ **done 2026-11-22** on the SM-G990B — G4;
    3. a checkpoint blocked by a real concurrent reader is reported incomplete, and a repeat
       finishes it. ✅ **Done 2026-11-22** — G4.
  - **Added 2026-11-20 from the 3.2 follow-up:**
    4. a favourite toggle leaves the image's Tier C rows intact through the next index run, and its
       `generationModified` advances — ADR-0018 Amendment 6 §1 rests on that and it is still only
       expected. If the generation does not advance, the toggle never reselected the row and the
       amendment's motive weakens without its rule becoming wrong. ✅ **Done 2026-11-22** — G5b: it
       holds on the SM-G990B, API 36, in a throwaway in-memory index.
  - **Added 2026-11-20 from 3.3** — what *3.3 — Built and verified 2026-11-20* could not prove:
    5. ADR-0023 verify item 6 again, **through the real worker**, once 3.6 shows the disclosure:
       Index now with the screen off, under the production budget — images processed, wall time,
       mean per image, peak RSS, thermal status before and after, charging or not. 3.3 measured it
       only in the process, on a throwaway in-memory index; ✅ **done 2026-11-23** — G7: 1,002 images
       in 254.2 s, 253.7 ms per image, 0 failed, thermal status 0, charging; ADR-0023's row 6;
    6. whether OpenCV's native errors print under logcat's `cv::error()` tag while one occurs —
       outside the pass's fixed lines, so ADR-0024 §6's logger rule decides it.
       `Core.setErrorVerbosity(false)` is the likely switch; read in code only, never observed;
       ✅ **observed 2026-11-22** — G5a: it silences `cv::error()`, and one `org.opencv.imgproc` line
       remains (L10, ~~for the maintainer~~ accepted by him 2026-11-23);
    7. the release build's capability, and a face pass, on at least one non-Samsung arm64 phone if
       one is available, as well as on the SM-G781B (A4); *2026-11-23:* on the SM-G781B the debug
       build's capability only (G1, G10) — the release build needs the signing key and the face pass
       stopped at thermal status 2; L3 accepted as it stands; no non-Samsung phone (L2);
    8. the download budget, re-checked with bundletool after any model or OpenCV change: 3.3 measured
       +46,063,982 B against 50 MB, little headroom (ADR-0023 verify item 5); ✅ **re-checked
       2026-11-22** — G5d: total 49,712,393 B, +98,410 B of app code; ADR-0023's row 5; L12;
    9. ~~ADR-0023 verify item 7 — the capability check on another phone; see its row;~~ ✅ **Done
       2026-11-22** on the SM-G781B, 6 of 6 — *3.7*, G1, and ADR-0023's row 7;
    10. the non-arm64 branch of `FaceCapabilityDeviceTest`, which asserts *incapable* and has never
        run on a device without arm64-v8a. ~~🔶 **In part 2026-11-22**~~ ✅ **Done 2026-11-22 for what
        it shows** — G5e: run as a 32-bit process on the SM-G990B, the "library missing" path; the "no
        arm64-v8a" path, with items 16 and 24, still open — no phone without arm64-v8a (L1).
  - **Added 2026-11-21 from 3.4** — what *3.4 — Built and verified 2026-11-21* could not prove:
    11. a production-size library: grouping and the face pass at 10,000 faces and more through the
        worker, the store's paged read and its one-transaction write included — 3.4 measured the
        grouping core alone at that size, on synthetic faces, and the store only at 422 faces;
        ~~🔶 **synthetic, through the real store, 2026-11-22**~~ ✅ **done 2026-11-22 for what
        synthetic faces show** — G5c, through the real store, at 10,000, 20,000 and 37,000 faces; a
        real library at that size through the worker still open (L5);
    12. the worker window under the start-only deadline: grouping that starts within 8 minutes is
        not stopped after it, so how much of WorkManager's window a large grouping uses after a full
        face pass, against the arithmetic of about 37,000 faces (`ARCHITECTURE.md` §11); ✅ **measured
        and corrected 2026-11-22** — G5c: about 31,000 faces after a full-budget face pass, about
        56,000 in a whole window (L11);
    13. one giant connected component against the heap ceiling — about 10,500 faces fails with
        `OutOfMemoryError`, not the fixed message — unless the maintainer has chosen a heap-based
        cap by then (`ARCHITECTURE.md` §11); *accepted as debt 2026-11-23, no heap-based cap (L4);*
    14. grouping, and the worker run with it, on the SM-G781B (A4) — 3.4 ran on the SM-G990B only;
        *2026-11-23:* not measured — G10 stopped at thermal status 2 before grouping; L3 accepted as
        it stands.
  - **Added 2026-11-21 from 3.5** — what *3.5 — Built and verified 2026-11-21* could not prove:
    15. item 11 extended to evaluation: with a people album on screen, the face projection re-runs
        as a full snapshot on every Tier A and face-pass batch — measured at a production-size
        library, its time per emission and how often it emits during an Index now run; ~~🔶~~ ✅ **time
        per emission measured 2026-11-22** — G5c: 177, 410 and 767 ms at 10,000, 20,000 and 37,000
        faces; **how often it emits during an Index now run stays open** — to G7 if it can be counted
        there, otherwise L5; *2026-11-23:* not countable at G7 — L5;
    16. on a phone without arm64-v8a, if one can be attached: a restored people album does not wait
        for ever once 3.6 has closed the gap (*3.6*, item 1) — it shows the device-incapable
        notice. If no such phone exists, say so; it is then proven by unit tests only.
  - **Added 2026-11-22 from 3.6** — the device-only checks of *3.6*'s *Device-only checks* that its
    gate did not run, on the SM-G990B unless said otherwise (*3.6 — Built and verified 2026-11-22*):
    17. the New automatic album screen's tab labels at font scale 2.0 on a 360 dp width, in German
        and Italian; ✅ **done 2026-11-22** — G4: Italian fits; German fixed by "Leute";
    18. the tile and notice strings that run 74 to 85 characters in several locales against about
        60 in English, at font scale 2.0 — the tile ellipsizes; shortening a translation is the
        remedy, not a layout change; *2026-11-23:* not observed — W8 skipped; L6;
    19. the face crop's decode cost while scrolling 100 or more Person tiles; ✅ **measured
        2026-11-23** — G8: 8.30 % janky frames against Folders View's 3.52 %, no stutter seen; a polish
        candidate;
    20. the crop's sharpness for small faces — 21 of the 104 groups measured at 3.4 have no member
        at least 60 px across; ✅ **judged 2026-11-23** — G8: 2 of 104 not recognisable;
    21. Coil's disk-cache directory unchanged after the People picker renders (ADR-0024 §6) —
        asserted on the request's policy by a test, never observed; ✅ **2026-11-23** — G8: unchanged,
        within a limit — the directory never existed;
    22. the Face licences dialog at font scale 2.0 and in landscape — a bounded height, its button
        visible; ✅ **done 2026-11-22** — G4, portrait and landscape;
    23. a tap outside the disclosure leaves it showing, on a device — pinned today by its
        `DialogProperties` in a test only; ✅ **done 2026-11-22** — G6: a scrim tap and Back both
        left it showing;
    24. with items 10 and 16, on a phone without arm64-v8a if one can be attached: the capability
        recorded incapable at app open, and the Face data area and the People tab not offered.
- **3.8 — Close-out.** `ARCHITECTURE.md`, exit criteria checked against the repository,
  `/docs-audit`, archive. Refined 2026-11-22 into its own section above, with an explicit entry
  condition.

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
  *Evidence recorded 2026-11-23, later still, for 3.8's step 2 to check:* **on the JVM**, the unit
  tests `AutomaticAlbumFacetsTest` ("the match count equals the evaluator's member count for every
  mix of people, place and date"), `AutomaticAlbumPickerStateTest` ("the match count is the
  evaluator's member count for people alone, with a place, a date, and all three") and
  `AlbumsViewModelTest` ("the picker's match count is the member count of the album created from it,
  for people with place and date"); **on a device, observed by hand** — the maintainer, on the
  SM-G990B, one people album, the screen's count and the album's member count the same, the figures,
  tiles and any place or date mix not reported (*3.7*, W4; *Open at 3.7*, question 7). The wording
  says "on a device", not "by a device test", so a hand observation meets it as written. **Accepted
  gap:** no automated device test covers people counts — `RegionAlbumEvaluationDeviceTest` covers
  place and date only.
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
    clarification), and nothing else face-related is written there. *2026-11-22, step 3.7's G5a:* a
    native OpenCV error still prints one line of OpenCV's own under `org.opencv.imgproc`, outside the
    pass's logger — L10, ~~pending the maintainer~~ accepted by him 2026-11-23 as a recorded limit.
- The Play Data Safety question of decision 9 is answered and recorded. *Status 2026-11-20:* **not
  answered.** This line is what closing the stage requires, not a statement that it is met. The
  question is recorded under *3.1*, *The Play Data Safety check*, and is the maintainer's, before the
  first Play release that ships the Tier C pass. *2026-11-22:* its facts and draft answers are
  prepared there (P1); **still not answered.** *2026-11-23:* the maintainer's position recorded there —
  on-device processing and the album database's Auto Backup are neither collection nor sharing —
  resolving P1's points 1 and 2; points 3 and 4 open, the form not submitted: **still not met.**
  *2026-11-23, later:* **carried past 3.8 by the maintainer (option B)**, as Stage 2 carried its Play
  Console declaration — so it is carried, not met; points 3 and 4 and the submission stay a
  pre-release item (*The Play Data Safety check*).
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
  trimmed build lands~~ OpenCV lands (untrimmed, E3), not estimated. *2026-11-22, step 3.7's G5d:*
  they add +46,063,982 B, unchanged; the whole arm64 download is 49,712,393 B — ~~which of the two "50
  MB" bounds is L12's question for the maintainer.~~ *2026-11-23:* L12 accepted — the budget is what
  the face path adds, 3,936,018 B under 50,000,000 B, with the total recorded beside it at every
  re-check (ADR-0023's row 5).
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
   3.4's refinement, 2026-11-20, ~~subject to the budget~~ the budget met (decision 50; *built
   2026-11-21*): not adopted** — every run that needs grouping
   regroups every face with average linkage, split into components first; *3.4*, *Measurement first,
   and the budget*. ~~It returns only as option (e) there, if Task 1's figures fail the budget.~~
   *2026-11-20:* the figures met it, so option (e) is not taken; incremental grouping would be a new
   decision. The spike found
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
   to the ML stages' plans. Designer. ✅ **Answered 2026-11-21 by the Designer: a third tab, People,
   beside Place and Date** — no screen of its own and no navigation change, so D2 is not reopened (its
   trigger 1 does not fire). The layout and the reasons are in *3.6 — The People criterion, the
   disclosure and the switch*, *Decided at 3.6 — D1 and the People picker*.
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
3. **D3 — How many people may one album hold?** *Raised by the Planner 2026-11-21, from 3.5;
   ✅ **Answered 2026-11-21 by the maintainer: at most 8 people in one album, as recommended.** Built
   as `MAX_PEOPLE_PER_ALBUM` in the picker, with the encoder's `require` caught in
   `createAutomaticAlbum` as the backstop (*3.6*, Task B).
   blocks 3.6's picker; the maintainer's — a product choice.* The rule's 16 KiB cap holds about 8 to
   50 `people` clauses, depending on the anchor photos' name lengths. **Recommended: 8**, the most
   that always fits — the reasoning is in the handoff's *Next action: step 3.6* and *3.6*, item 4.

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
