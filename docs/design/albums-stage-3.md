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
> and 2026-11-18), 41, 42 and 43 (2026-11-18). The steps, the
> exit criteria and every recommendation below are proposals until the maintainer answers the open
> questions and step 3.1 records the decisions. Group A was answered on 2026-11-17. Where something
> was not known, it is a question, not a guess. ~~Nothing here names a face model, a licence or an
> API behaviour as fact: each is a question for the spike.~~ **Amended 2026-11-18:** the spike has
> closed. What it read and measured is under *Licence findings* and *Spike outcome*; those are
> findings, and no model, threshold or runtime is chosen until step 3.1.

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
- **Next action: the rest of step 3.1**, in the order step 3.1 lists the spike's items: the
  recogniser and its threshold (B1's algorithm with it); the size route; the lowest device; decision
  42's minimum box overlap; the 40 px small-face cut-off; the picker's sort tie-break; non-faces.
  YuNet's training-data caveat goes to the maintainer with the recogniser item.
  ~~**Next action: step 3.1, starting with the licence and training-data question.**~~ Answered
  2026-11-18 — decision 43.
- **Read first, on starting 3.1:**
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
  runtime dependency added at 3.1 will change the merged manifest again.
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
    that cannot be followed), pending step 3.1's ADR for 41 and 42.
  - [albums.md](albums.md) §8 decision 43: the training-data risk is accepted for SFace only. Any
    other recogniser, and the detector, needs its own answer; the weights' Apache-2.0 obligations
    still apply (A5).
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
    3.1.

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

**Not measured.**
- **The SM-G781B: never attached, no figures of any kind.** A4's threshold has no data below the
  SM-G990B.
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
  amended 2026-11-18. Ties are not decided yet (step 3.1).
- Thumbnails cannot be merged or split. One person shown as two thumbnails is an accepted failure:
  picking both finds little, because of the next point — decision 40, amended.
- Several people means **all of them are in the photo** — decision 35.
- It combines with place and date — decision 33: "Silvia and Davide in France, 2025".
- After a restore or a full re-index the album finds its people again through anchor photos, one
  per person, waiting on the index until face indexing reaches them — decisions 41 and 42.
- On a device below the threshold, the People criterion is not offered — A4.

The minimum box overlap that recognises the anchored face is open (group C); the spike's figures
for it are under *Spike outcome*, anchor stability.

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
- **§10 question 12 is closed** (2026-11-17), by decisions 40–42 and decision 9's clarification.
  Step 3.1's first ADR records the answer.

---

## Steps

Proposals until step 3.1 confirms them.

| Step | Delivers | Status |
|---|---|---|
| **3.0** | Feasibility spike: face detection, face embedding and clustering, measured on the phone. No shipped code. | ~~🔶 In progress — started 2026-11-17~~ ✅ Complete 2026-11-18 — *Spike outcome* above |
| **3.1** | The decisions: ADRs and amendments, and these exit criteria confirmed | ⬜ Not started |
| **3.2** | Tier C schema and store | ⬜ Not started |
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
  face data. Its one open input is the minimum box overlap, from 3.0.
- **Tier C storage**, as an ADR-0018 amendment.
- **The `people` clause format**, as an ADR-0017 amendment: what it stores — the anchors, as the ADR
  above settles them — and what makes it unevaluatable. That includes decision 42's second cause,
  found at evaluation on a rule the build understands, which the user may fix by picking again.
- **The disclosure and the delete-face-data switch** of decision 9: wording, placement, and what
  "delete" removes.
- **The Play Data Safety check** of decision 9: whether on-device face inference counts as
  collection — to be confirmed, not assumed.
- **The models, the runtime and delivery** (A1–A3), bundled preferred and within the 50 MB budget,
  with the runtime dependency and the permissions it merges approved under `AGENTS.md`; the
  threshold below which the People criterion is hidden, and whether a people album restored onto
  such a device is unevaluatable (A4); and any licence file the weights need (A5).
- **Whether ADR-0013's revisit fires** (D2).

**Handed over by the spike, 2026-11-18.** These are items for 3.1, not decisions. The figures
behind each are under *Spike outcome* and *Licence findings*.
1. **The licence and training-data question — first, because it can stop the stage.** Every
   recognition model found was trained on face data with research-only or undeclared terms. The
   maintainer chose to measure anyway and to decide shipping here. If he accepts none of the three
   measured recognisers, none is left to ship, and the stage stops until another is found.
   ✅ **Answered 2026-11-18:** the risk is accepted for SFace only — [albums.md](albums.md) §8
   decision 43. Still open: the same caveat on the detector, YuNet, which the question did not
   name.
2. **The recogniser and its threshold.** The spike's input is SFace fp32 at cosine distance about
   0.50, with 0.45 as the conservative fallback, on labels that favour SFace. This is also B1's
   algorithm: average linkage, whose memory at scale is unmeasured, or an incremental clusterer,
   which is cheaper but less stable.
3. **The size route.** The fp32 stack is estimated at 45.9 MB, 4.1 MB under A3's budget. The two
   ways down both need measuring first: the int8 SFace's quality, and a trimmed OpenCV build.
   Whether the runtime library counts against "the face models" 50 MB was not asked; the spike
   counted it.
4. **The lowest device — still open, with no data.** The SM-G781B was never attached. A4's
   second half — a restored people album is unevaluatable below the threshold — is still to be
   confirmed.
5. **Decision 42's minimum box overlap.** The spike's IoU figures are the input: 1.0 on the same
   build; a median of 0.957, and a minimum of 0.75, between two builds of the detector.
6. **The 40 px small-face cut-off**, the spike's choice, which the maintainer did not object to.
   Faces under it are never grouped, so they are never offered and never match an album.
7. **The picker's sort** (decision 40, amended 2026-11-18): its tie-break, if one is needed, and
   whether the count is of images, as the two-image threshold counts. Here or at 3.6.
8. **Non-faces.** 3 of SFace 0.50's 104 thumbnails were not faces, and a detector-score cut could
   not remove them without losing real faces. Whether that is an accepted failure, like a split
   person, is not asked.

This step also confirms or changes the exit criteria below.

### 3.2 to 3.8

At the level of detail Stage 4's plan used for its later steps; each is refined when 3.1 has
settled what it depends on.
- **3.2 — Tier C schema and store.** Tables in the index database: version bump, a real migration,
  committed JSON, `Room*` and `InMemory*` store implementations, DAO tests; each row stamped with
  the model version. The backup exclusion is the same file; check it rather than assume it.
- **3.3 — The pipeline.** Detection and embedding inside indexing, over images only (decision 40), under the existing constraints,
  honouring cancellation and the permission checks, starting only after the disclosure. A failed
  inference is stored as absent, §6's third state. Not one line of face data in a log.
- **3.4 — Clustering and the person record.** Per B1 and B2, and decision 42 for an anchor.
- **3.5 — The `people` clause and evaluation.** Parse, encode and evaluate, with strictness tests
  like Stage 2's; combined people + region + time, with tests.
- **3.6 — The UI.** The People criterion with its thumbnails, with no merge and no split (decision
  40); the notice of an unevaluatable people album, whose wording is open (decision 42); the
  disclosure, and the Settings switch; strings in every shipped locale; vocabulary in-change.
- **3.7 — Verification.** The definition of done, once, with `adb devices` checked and stated;
  then an installed-app walkthrough including the delete switch.
- **3.8 — Close-out.** `ARCHITECTURE.md`, exit criteria checked against the repository,
  `/docs-audit`, archive.

---

## Stage 3 exit criteria — draft, confirmed or changed at 3.1

Derived from Stage 2's and from decision 9's obligations. **Not binding until 3.1.**

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
  - an anchor photo deleted, or no face at the anchored position above the minimum overlap, makes
    the album unevaluatable: never widened, never deleted automatically, its rule not rewritten
    except by the user picking the person again (ADR-0017, as 3.1 amends it).
- **The People picker, decision 40:** thumbnails carry no label and no person name is stored; a
  person is offered only if seen in at least two different images, and that threshold applies to
  the picker only, not to evaluation; the thumbnails are sorted by photo count, most first
  (amended 2026-11-18); videos are not face-indexed and do not count; there is no
  merge and no split.
- **Decision 9, each obligation checked:**
  - the one-time, plain-language disclosure appears when face indexing first starts, and says that
    faces are grouped on the device, that nothing leaves the phone, and where the off switch is;
  - the Settings switch deletes the face data, not merely stops adding to it, shown by a test and on
    the device;
  - face data never leaves the device (no network request in the face path), never enters a backup
    (checked against the rules files), and never appears in a log (how that is checked is decided
    at 3.1). The anchor in the album database is not face data (decision 9's 2026-11-17
    clarification), and nothing else face-related is written there.
- The Play Data Safety question of decision 9 is answered and recorded.
- The index database reaches its new version through a real migration, every schema JSON is
  committed, and the index is still excluded from backup, checked against the rules files.
- The merged manifest is checked after the runtime dependency lands, and any permission it adds is
  approved or removed.
- The licence obligations of any shipped weights are met (A5).
- The face models add at most 50 MB to the app's download size (A3).
- Below the threshold set at 3.1, the People criterion is not offered on the New automatic album
  screen; an existing people album on such a device is unevaluatable, never hidden or deleted —
  the second half to be confirmed at 3.1 (A4).
- Indexing runs under the existing constraints, with no notification and no foreground service.
- Every spike artefact holding face data — crops, embeddings, labels — and every photo copied off
  the phone has been deleted, on the phone and anywhere else it went, and the deletion recorded
  here (A7). ✅ **Met 2026-11-18** — *Spike data deleted*. The SM-G781B part rests on the
  notebook's record, not on a check.
- Every new string is in every shipped locale; the vocabulary has moved; `ARCHITECTURE.md`
  describes what landed; a documentation audit has passed.

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
decision 43.

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

---

## Open questions

Each says what it blocks. A recommendation is given only where there is a reason for one.

### A. The spike — answered 2026-11-17

Answered by the maintainer on 2026-11-17; they no longer block 3.0. Where he delegated a choice to
the spike (A1, A2), the spike records what it chose and why. Still open for 3.1: A4's threshold, and
the part of A4 marked to be confirmed.

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
   outcome*).
3. **A3 — How do the models reach the device?** Bundled in the app, or through Play services or
   AICore? **Stage 4's lesson:** app size was the cost that paused content (decision 38), so the
   size budget is a criterion for the choice, not something to discover afterwards; Play's size
   limits were never checked (Stage 4's A3a). Decision 37's "the model ships inside the app" was
   content-specific; Stage 3 has not been asked. ✅ **Answered 2026-11-17: bundled in the app is
   preferred, and app size is a hard criterion** — the face models may add **at most 50 MB** to the
   download size, the maintainer's figure. Play-services or system delivery is not excluded, but not
   preferred. **Outcome, 2026-11-18:** the SFace fp32 stack is estimated at 45.9 MB, with OpenCV
   as an upper bound; an int8 route at 16.8 MB is unmeasured for quality (*Spike outcome*).
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
   figures below the SM-G990B and the threshold has no data.
5. **A5 — Licences.** The spike reads the terms of every model, and **reads them before downloading
   any recognition weights**. Findings go under *Licence findings* above. What is owed if the
   weights ship follows from what is read. ✅ **Answered 2026-11-17:** only models whose licence
   allows **commercial use and redistribution inside an app** are measured at all. The terms are
   read **before any download**, not only of recognition weights, and the findings go under
   *Licence findings*. **Outcome, 2026-11-18:** recorded there; three recognisers passed, every
   one with a training-data caveat.
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

1. **B1 — Clustering algorithm and threshold.**
2. **B2 — Incremental clustering** as new photos arrive.

### C. People — answered 2026-11-17, except one detail

Decided 2026-11-17 by the maintainer, in [albums.md](albums.md) §8 decisions 40 and 41: unlabelled
thumbnails chosen by the app, no person names, the two-images threshold, ~~merging two thumbnails,~~ and
anchoring a person to a photo. What follows is what those leave open. Except C1a, each question was
raised by the Planner on 2026-11-17; ~~none has an answer~~. **Answered later the same day by the
maintainer**, in decision 9's clarification, decisions 40 and 41 amended, and decision 42. One
detail stays open, at the end of C2.

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
   - **Still open — the minimum overlap.** The face at the anchored position is the detected face
     whose box overlaps the stored position best, above a minimum overlap (decision 42). Step 3.0
     measures how far the box moves between runs; step 3.1 sets the minimum. **Measured
     2026-11-18:** see *Spike outcome*, anchor stability.
3. **C3 — Does "two different images" count videos?** It depends on whether Stage 3 indexes faces
   in videos at all, which is not decided. ✅ **Answered 2026-11-17: no.** Videos are not considered
   for people albums; face indexing covers images only (decision 40, amended).

### D. Screen and navigation — block 3.6; raised by the Planner

1. **D1 — Layout of the People criterion** on the New automatic album screen; decision 33 left it
   to the ML stages' plans. Designer.
2. **D2 — ADR-0013's revisit:** does the people flow need a real back stack, as that ADR expects at
   Stage 3?

---

## Related

- [albums.md](albums.md): §3 membership, §4 *People album*, §6 the index, §7 progress, §8 decisions
  9, 13 as amended, 14, 15, 33, 35, 40, 41, 42 and 43, §10 questions 3 and 12.
- [albums-stage-4.md](albums-stage-4.md): paused; the precedent for a spike.
- [albums-rule-editing.md](albums-rule-editing.md): rule editing's own plan; its D6 is the order
  between the two.
- [albums-stage-2.md](albums-stage-2.md): archived; its structure is the template.
