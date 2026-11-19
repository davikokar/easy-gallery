# ADR-0023: The face pipeline — YuNet and SFace on a bundled OpenCV, inside the indexing worker

- Status: Active
- Date: 2026-11-18
- Adopted: 2026-11-19
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer, and approved by
  him on 2026-11-19. The choices it records were taken by delegation (decisions 44–46); the
  dependency was confirmed by the maintainer on 2026-11-18.

> **Adopted 2026-11-19, when the maintainer approved it.** It was drafted on 2026-11-18 under a
> temporary `Proposed` label, because the instructions' status vocabulary is `Active | Disabled`
> and neither fits a draft. Adoption settles none of the items under *To verify* below except the
> one its questions answered: each is still settled by the check named, at the step named.
>
> **Its four questions were answered by the maintainer on 2026-11-19, as recommended**
> ([albums.md](../../design/albums.md) §8 decision 47). ~~That settles them; it does not approve the
> record, which stays `Proposed` until he does.~~ He approved the record separately, the same day.

## Context

People albums need faces found and grouped on the device. Step 3.0's spike measured candidate models
and runtimes on one phone ([albums-stage-3.md](../../design/albums-stage-3.md), *Spike outcome* and
*Licence findings*), and step 3.1 chose among them in [albums.md](../../design/albums.md) §8:

- **Decision 43** (the maintainer): the training-data risk is accepted for SFace.
- **Decision 44** (delegated): the same risk is accepted for the detector, YuNet. **Confirmed by the
  maintainer on 2026-11-18.**
- **Decision 45** (delegated): SFace fp32, average linkage at cosine distance 0.50, OpenCV bundled
  ~~and trimmed~~ for arm64-v8a, and a capability check instead of a lowest device. *The trimming
  quoted here is superseded by §2 below: OpenCV's published artifact, untrimmed (question 1,
  answered 2026-11-19).* **The maintainer
  confirmed on 2026-11-18 that OpenCV may be added as a third-party dependency** — the approval
  `AGENTS.md` *What to ask before doing* requires. That confirmation covers the library, **not** the
  permissions its merged manifest may add, and not Play-store packaging questions.
- **Decision 46** (delegated): the 40 px cut-off, the anchor's overlap, the picker's order, and
  non-faces.

What those decisions do not settle is the architecture around them: where the pipeline runs, under
which constraints, how the app knows whether a device can run it, how it is packaged and what it
owes. The constraints already binding are:

- **No cloud inference** (§11), and no network for the face path at all (decision 9).
- **Indexing runs in WorkManager, constrained** to charging, idle and battery not low (§6); **no
  app-open index pass** (decision 22); **no notification and no foreground service** (decision 23).
- **A worker that is not a foreground service is stopped after about ten minutes.** WorkManager's
  documentation offers a foreground service and a notification as the way to run longer, naming "an
  ML model locally" as a use case; decision 23 rules that out.
- **The face models and their runtime may add at most 50 MB** to the download size (A3; decision 45
  counts the runtime).

## Decision

### 1. The models

- **Detector: YuNet, fp32. Recogniser: SFace, fp32.** OpenCV Zoo's ONNX files, as measured.
- **Shipped as app assets**, read from the APK, never downloaded. **The weight files are committed
  to the repository** (question 2, answered 2026-11-19). Each file is pinned by a checksum, checked
  by the build, so a different file cannot be shipped by accident.
- **A face-pipeline version constant** names the whole pipeline: both weight files, the decode and
  detection sizes, the detector's score threshold and the 40 px cut-off. It stamps every Tier C row
  ([ADR-0018](0018-media-index-in-a-second-database.md) Amendment 5) and every anchor
  ([ADR-0022](0022-people-criterion-anchored-to-a-photo.md)). Changing any of them bumps it.
- **The grouping** is average-linkage clustering on cosine distance at 0.50 (decision 45), with its
  own grouping version (Amendment 5).

### 2. The runtime

- **OpenCV's DNN module, on the CPU, bundled in the app**, through OpenCV's own Android bindings. No
  app-written native code is part of this decision.
- **Packaged for arm64-v8a only — by packaging, not by an app-wide ABI filter.** The app must stay
  installable on every device it is offered to today; a device of another ABI simply fails the
  capability check below.
- ~~**Trimmed to the modules the pipeline uses**, confirmed at step 3.3. Trimming is margin, not a
  precondition (decision 45): untrimmed, the stack was estimated within the budget.~~ **OpenCV's
  published Android artifact, untrimmed**, with its other ABIs left out by packaging (question 1,
  answered 2026-11-19). Trimming stays available as margin, as decision 45 says it is, and is a
  change of its own if the budget ever needs it: untrimmed, the stack was estimated within the
  budget.
- Its version is pinned where every version is, in `gradle/libs.versions.toml` and
  `app/build.gradle.kts`, and is not restated here.

### 3. The capability check

**A device is capable when the native library loads, both models load, and a self-test runs
detection and an embedding to a valid result.**

- **The self-test uses a synthetic input**, not a photo of a person. Bundling a face would need its own
  rights and would itself be a face. It checks that each stage returns output of the expected shape
  with finite values, and step 3.3 decides whether it also compares against a stored reference
  within a tolerance.
- **It gates four things, from one answer:**
  1. whether the People criterion is offered on the New automatic album screen (decision 45);
  2. whether the Tier C pass runs;
  3. whether the face-data disclosure is shown ([ADR-0024](0024-face-data-disclosure-and-delete-switch.md));
  4. whether a people album is evaluable — otherwise it is unevaluatable with the "people
     unavailable" cause ([ADR-0017](0017-composable-album-rule-representation.md) Amendment 4).

  Items 2 and 3 confirm the Planner's reading recorded in decision 45; the maintainer confirmed it
  on 2026-11-19 (question 4 below).
- **Never run on the main thread.** Its result may be held for the life of the process. Whether it
  is also persisted, keyed by app version, is step 3.3's, under `AGENTS.md`'s preference-store
  rules.

### 4. Where the pipeline runs

**Inside the existing indexing worker, as a Tier C pass**, after Tier A and the place resolve pass, on
every run — the periodic job and Index now alike — and only when the device is capable, face data is
switched on and the disclosure has been shown (ADR-0024).

- **The same constraints, the same scheduling.** No new unique work, no notification, no foreground
  service, no `getForegroundInfo`, and expedited Index now only where it already is (decision 23).
- **Chunked and resumable.** It commits one image, or a small batch, at a time; checks for
  cancellation between them; holds no long transaction; and the next run continues where this one
  stopped. **That is what makes it compatible with the ten-minute limit**: a large library is
  face-indexed over several runs.
- **Grouping runs after detection and embedding**, in the same worker or as its own step, as step
  3.4 decides. It must also fit the window or be resumable, and must never run twice at once.
- **Images only** (decision 40); ~~GIFs per Amendment 5's question~~ no GIFs (Amendment 5's question,
  answered 2026-11-19).
- **No face data in any log.** What the pass may log is [ADR-0024](0024-face-data-disclosure-and-delete-switch.md)'s.

### 5. Memory — a requirement on step 3.4, not a choice of algorithm

**Average linkage needs the full pairwise distance matrix**, which grows with the square of the
number of faces: the spike estimated about 800 MB at 10,000 faces, and never measured it in an app.
A worker that runs out of memory kills the app's process with it.

- **Step 3.4 measures** grouping's peak memory and time on the device, at the largest real library
  available and at a synthetic set of at least 10,000 faces, in the background.
- **It must stay within a memory budget** that 3.4 proposes and the maintainer accepts, with the
  detection models' own footprint counted: the spike's harness peaked at about 300 MB with SFace
  loaded, in the foreground.
- **If average linkage cannot meet the budget, replacing it is a new decision** in albums.md §8, and an
  amendment to this record — incremental clustering is open question B2 — not an implementation
  detail chosen in a pull request.

### 6. Licences owed

What [albums-stage-3.md](../../design/albums-stage-3.md) *Licence findings* recorded as owed, now that
the choice is made:

- **OpenCV and SFace: Apache-2.0** — a copy of the licence, and a statement of any change made.
  ~~Whether a build trimmed by configuration counts as a change is to be read, not assumed.~~ The
  published artifact is used as published (question 1), so step 3.3 makes no trimmed build; whether
  one counts as a change is read if a trimmed build is ever made, not assumed.
- **YuNet: MIT** — its copyright and permission notice with the copy.
- **OpenCV's bundled third-party components and the C++ runtime it ships with: not yet read.**
  ~~Which components are bundled depends on how the build is produced (verify item 3 below), so they
  are read once that is known.~~ With the published artifact chosen (question 1), what it bundles is
  known, and step 3.3 reads it (verify item 8).
- **Where:** as files in the APK, **and a row in Settings that shows them**, as the Place data row
  does for GeoNames (question 3, answered 2026-11-19). The row is a Designer task at step 3.6, with
  its strings in every shipped locale.

## To verify, and who verifies

*Headed "Verify before adopting" while this record was a draft.* Adoption on 2026-11-19 settled
only item 3, through question 1; every other item is still open. None is to be settled by
assertion; each is settled by the check named, by whom and when the table says.

| # | What | How it is settled | Who, when |
|---|---|---|---|
| 1 | **arm64-only packaging must not filter the app on Play.** Play shows an app with native libraries only to devices whose ABI it includes ([Filters on Google Play](https://developer.android.com/google/play/filters), *CPU Architecture*). The release bundle already ships an AndroidX native library for four ABIs, so packaging OpenCV for arm64 alone should keep the app on every device; an app-wide ABI filter would remove it from the others. | bundletool device specs for each ABI the app is offered to today, before and after: same set of devices, OpenCV present only on arm64 | Coder at 3.3; Reviewer checks; exit criterion |
| 2 | **16 KB pages.** Play requires apps targeting API 35 or higher to support 16 KB memory pages on 64-bit devices and blocks updates that do not from 2027-02-01 ([Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)). | every `.so` in the release bundle checked for 16 KB ELF alignment and packaging — OpenCV's, the C++ runtime, and the AndroidX one already shipped | Coder at 3.3; exit criterion |
| 3 | **How the ~~trimmed~~ OpenCV build is produced** — OpenCV's published Android artifact, a build made by a script under `tools/` and committed as a prebuilt, or a native build inside Gradle. Each has different reproducibility, build-time and licence-reading costs. | question 1 below. ✅ **Settled 2026-11-19:** the published artifact, untrimmed | ~~the maintainer, before 3.3~~ the maintainer, 2026-11-19 |
| 4 | **The merged manifest.** The confirmation covers the library, not what it merges in. | merged debug and release manifests diffed before and after; any new permission, `<queries>` entry or component goes to the maintainer before it ships | Coder at 3.3, the change that adds OpenCV |
| 5 | **The download-size budget** | measured on the release bundle's arm64-v8a delivery with bundletool, not estimated | Coder at 3.3; again at 3.7 |
| 6 | **Background speed, memory and heat** — every spike figure was taken in the foreground, on USB power | measured in the worker, on the device | Coder at 3.3 and 3.4 |
| 7 | **The capability check on another phone** | the SM-G781B, if it can be attached | 3.7 |
| 8 | **The licences of the bundled components** | read ~~once item 3 is decided~~ from the published artifact, item 3 being settled | Planner at 3.3 |

## Alternatives Considered

**ONNX Runtime.** Runs both models too, but would add a second runtime for nothing: OpenCV is needed
in any case. Decision 45.

**LiteRT (TensorFlow Lite).** No model that passed the licence rule is TFLite, and converting one would
measure the conversion. Decision 45.

**SFace int8, OpenFace, dlib.** Not adopted, for decision 45's reasons: int8's embedding quality was
never measured, OpenFace mixes people, and dlib is about twenty times slower per face.

**ML Kit Face Detection, or Play-delivered models.** Excluded by the licence reading — service terms,
server contact and telemetry ([albums-stage-3.md](../../design/albums-stage-3.md), *Licence
findings*).

**An app-wide `abiFilters` for arm64-v8a.** Simplest to write. Rejected because Play would stop
offering the app to non-arm64 devices that install it today (verify item 1).

**A device list or a speed threshold.** Rejected by decision 45: there is no data below the one phone
measured, and a check cannot be wrong about a device the way a list can.

**A foreground worker with a notification**, so that a pass can run longer than ten minutes. Rejected
by decision 23.

**A separate worker or unique work for faces.** Not chosen: it would duplicate the constraints, the
scheduling and the run-state reporting of the existing one, and make overlapping runs more likely
rather than less.

## Consequences

### Positive

- Everything runs on the device, offline, with no account and no download.
- No device is excluded by guesswork: a device either runs the pipeline or is told plainly that it
  cannot.
- The existing worker, constraints and Settings reporting are reused rather than paralleled.

### Negative

- **Every install pays the models' size**, including devices that fail the check, within the 50 MB
  budget but with little headroom untrimmed.
- **The app gains native code for the first time**, with its own failure modes: a crash in native
  code takes the process down rather than throwing an exception the worker can catch.
- **A large library is face-indexed over many runs**, each waiting for the phone to be charging and
  idle. People albums stay waiting meanwhile, with nothing on the album saying so (decision 29).
- **Emulators fail the capability check** unless arm64, so instrumented tests of the pipeline need an
  arm64 device, and tests elsewhere must not assume capability.
- ~~Licence notices are owed in the APK, and possibly in the UI.~~ Licence notices are owed in the
  APK and shown from a Settings row, with new strings in every shipped locale (question 3).
- **About 39 MB of weights are committed to git** and stay in its history for good (question 2).

## Questions for the maintainer — answered 2026-11-19

> **Answered by the maintainer on 2026-11-19**, in his words "go with the recommendations". Every
> question below was answered as recommended. The questions are left as they were put, because their
> reasons are the record of why; the text above has been settled to match.

1. **How is the OpenCV build produced?** (a) OpenCV's published Android artifact, untrimmed, with its
   other ABIs left out by packaging; (b) a trimmed build made by a script under `tools/` and
   committed as a prebuilt; (c) a native build from source inside the Gradle build. *The Planner
   recommends (a) for step 3.3* — the untrimmed stack was estimated within the budget, so trimming
   can wait until margin is needed, and (b) or (c) is then a change of its own. ~~Which?~~ ✅
   **Answered 2026-11-19: (a), as recommended** — the published artifact, untrimmed, arm64-v8a only
   by packaging. (E3.)
2. **How do the model weights enter the repository?** Committed as binary files, as the gazetteer's
   assets are — about 39 MB, kept in git history for good — or fetched by a build task against
   pinned checksums, which puts a download in the build (not in the app). *Recommended: committed,
   with checksums.* ~~Which?~~ ✅ **Answered 2026-11-19: committed, with checksums, as
   recommended.** (E4.)
3. **Licence notices:** files in the APK only, or also a row in Settings that shows them, as the Place
   data row does for GeoNames? A row is a Designer task at 3.6 and new strings in every locale.
   *Recommended: a row*, because nothing else in the app would let a user find them. ~~Which?~~ ✅
   **Answered 2026-11-19: a row too, as recommended.** (E5.)
4. **Confirm the Planner's reading of decision 45:** on a device that fails the check, the Tier C
   pass does not run and the disclosure is not shown. *Recommended: yes* — a disclosure of
   processing that never happens would be untrue. ~~Confirm?~~ ✅ **Answered 2026-11-19: confirmed,
   as recommended.** (E6.)

E3 to E6 are the numbers these carry in [albums-stage-3.md](../../design/albums-stage-3.md) group E.

## References

- [docs/design/albums.md](../../design/albums.md) §6, §8 decisions 9, 22, 23, 29, 40, 43, 44, 45 and
  46, §11
- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md) — *Spike outcome*, *Licence
  findings*, steps 3.3, 3.4 and 3.7, question B2
- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 5,
  [ADR-0022](0022-people-criterion-anchored-to-a-photo.md),
  [ADR-0017](0017-composable-album-rule-representation.md) Amendment 4,
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md)
- `data/MediaIndexingWork.kt` — the worker and its two unique works
- <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>
  — the ten-minute limit and the foreground alternative
- <https://developer.android.com/google/play/filters>,
  <https://developer.android.com/guide/practices/page-sizes>
