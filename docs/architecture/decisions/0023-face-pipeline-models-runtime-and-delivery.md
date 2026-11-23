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
>
> **Built 2026-11-20** by step 3.3, and verified by the definition of done — the gate is in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.3*, *Built and verified*. §1: the two
> ONNX files under `app/src/main/assets/face/`, pinned by `SHA256SUMS` and the build's
> `verifyFaceModelChecksums`, and `FACE_PIPELINE_VERSION` in `data/FaceIndexPass.kt`. §2:
> `org.opencv:opencv` 4.13.0 in the version catalogue, arm64-v8a only by `packaging.jniLibs`
> excludes, with `app/proguard-face.pro` for the release build (*Built — what the verify table
> found*, below). §3: `data/FaceCapability.kt` and `data/FaceDeviceStateStore.kt`. §4:
> `FaceIndexPass`, run by `MediaIndexingWorker` after Tier A and places — dormant until step 3.6
> records the disclosure as shown. §6: the notices are `NOTICE.txt`, the three licence files and
> `THIRD_PARTY_NOTICES.txt` in the same directory; the Settings row is step 3.6's. §5 is step
> 3.4's.
>
> **Built 2026-11-21** by step 3.4, and verified by the definition of done — the gate is in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.4 — Built and verified*. §4's grouping:
> `FaceGroupingPass`, run by `MediaIndexingWorker` after the face pass in the same hold of the Tier C
> lock. §5: `data/FaceGrouping.kt`, components first, measured in the worker (the last note of §5,
> and verify item 6).

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

  > **Note 2026-11-20 — ~~open, F2~~ F2 answered 2026-11-20: 4.13.0.** No record named the version:
  > the maintainer approved the library (decisions 45 and 47), not a release. Step 3.3's refinement
  > recommended 4.13.0, the build the spike measured, and asked him to confirm it; **he confirmed
  > 4.13.0 on 2026-11-20.** ~~It blocks step 3.3's Task 2, the change that adds the dependency, and
  > nothing before it.~~ It no longer blocks Task 2. The pin is still written only in
  > `gradle/libs.versions.toml` and `app/build.gradle.kts`, by Task 2, as the bullet above says.
  > **The confirmation covers the version and nothing else:** verify item 4 below still applies when
  > the dependency lands — the merged manifests are diffed, and any new permission, feature,
  > `<queries>` entry or component goes to the maintainer before it ships, as with WorkManager
  > (decision 24). The facts read are in [albums-stage-3.md](../../design/albums-stage-3.md), *3.3*,
  > *Settled before 3.3*, issue 5, and the question is F2 there.

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

> **Note 2026-11-20 — adopted 2026-11-20 with [ADR-0018](0018-media-index-in-a-second-database.md)
> Amendment 7, which the maintainer approved that day; written while the amendment was proposed.**
> Step 3.3 decides both points this section left to
> it. **Persisted**, in a new preferences file, `face_device_state`, excluded from backup (Amendment 7
> §3): step 3.6 needs the answer at every app open, where loading the weights each time is a cost and
> a native crash would be a crash at every open. Keyed by the app's version code, the face-pipeline
> version and the build fingerprint, with a start marker so that a check that kills the process twice
> is recorded incapable. **No stored reference** in the self-test: one phone cannot calibrate a
> tolerance, and a false *incapable* would hide the feature silently; the build's checksums guard the
> weights instead. The detail is [albums-stage-3.md](../../design/albums-stage-3.md), *3.3*, *Settled
> before 3.3*, issue 2.

> **Note 2026-11-21 — [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) Amendment 1 §3,
> adopted 2026-11-21, changes this checker.** With no `arm64-v8a` in the ABI list its caller passes,
> it records *incapable* under the current key without loading anything; the worker and the app-open
> check, run under the Tier C lock, record the same answer. Not built until step 3.6's task A.
> *Built 2026-11-22, step 3.6:* `supportsFacePipelineAbi` in `data/FaceCapability.kt`, the ABI list
> passed from `Build.SUPPORTED_ABIS`; `FaceDataViewModel` makes the same test first, only to skip the
> lock. Not observed on a phone without arm64-v8a (step 3.7).

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
  *Built 2026-11-21:* in the same worker, after the face pass and inside the same hold of the Tier C
  lock, unless the pass stopped because the setting went off. It starts only within 8 minutes of
  the worker's start and writes once, in one transaction at the end, so a run cut short writes
  nothing and the next repeats it; it is not resumable part-way.
- **Images only** (decision 40); ~~GIFs per Amendment 5's question~~ no GIFs (Amendment 5's question,
  answered 2026-11-19).
- **No face data in any log.** What the pass may log is [ADR-0024](0024-face-data-disclosure-and-delete-switch.md)'s.

### 5. Memory — a requirement on step 3.4, not a choice of algorithm

**Average linkage needs the full pairwise distance matrix**, which grows with the square of the
number of faces: the spike estimated about 800 MB at 10,000 faces, and never measured it in an app.
A worker that runs out of memory kills the app's process with it.

- **Step 3.4 measures** grouping's peak memory and time on the device, at the largest real library
  available and at a synthetic set of at least 10,000 faces, in the background.
  *Measured: the synthetic sets in the process (Tasks 1 and 2, the notes below), the real library of
  422 faces in the worker on 2026-11-21 (the last note); a real library of 10,000 faces or more is
  step 3.7's.* *2026-11-23: step 3.7 measured the store and grouping at synthetic scale instead — the
  note of that date below.*
- **It must stay within a memory budget** that 3.4 proposes and the maintainer accepts, with the
  detection models' own footprint counted: the spike's harness peaked at about 300 MB with SFace
  loaded, in the foreground.
- **If average linkage cannot meet the budget, replacing it is a new decision** in albums.md §8, and an
  amendment to this record — incremental clustering is open question B2 — not an implementation
  detail chosen in a pull request.

> **Note 2026-11-20 — measured, and the budget accepted** ([albums.md](../../design/albums.md) §8
> decision 50). Step 3.4's Task 1 measured grouping on the SM-G990B; the figures are in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.4*, *Task 1 — measured 2026-11-20*. The
> maintainer accepted the plan's budget — **at most 256 MB rise in peak RSS and at most 90 s at
> 10,000 synthetic faces** — as met: 10,000 faces took 30.7 s and +213.9 MB RSS, with no
> `OutOfMemoryError`. **The binding limit is the Java heap, not RSS:** the float32 triangle is 200 MB
> at 10,000 faces against the app's 256 MB heap (peak about 233 MB used), so about 10,500 faces is
> the ceiling. **Grouping therefore splits the faces into connected components first** — pairs at or
> below the 0.50 cut — and runs average linkage inside each. That yields **exactly the same groups**,
> so it is not the replacement of average linkage the third bullet above makes a new decision; it
> changes how the groups are computed, not what they are. Memory then follows the largest component
> (88 faces on the real library, 60 in the synthetic 10,000), and time, quadratic in all faces for
> the distances, becomes the ceiling instead. B2 stays not adopted. ~~Step 3.4's Task 2 builds the
> split and measures it at 10,000 and 20,000 faces.~~ *Done — Task 2 built the split and measured it,
> the note below.*

> **Note 2026-11-20, later — components first measured** (step 3.4's Task 2, on the SM-G990B; the
> figures are in [albums-stage-3.md](../../design/albums-stage-3.md), *3.4*, Task 2). **10,000
> synthetic faces: 12.8 s, +30.4 MB Java heap, +17.5 MB RSS; 20,000: 50.9 s, +46.9 MB heap, +39.1 MB
> RSS** — both well inside the accepted budget, and 20,000 now fits where the whole-set triangle could
> not. **The heap now binds on the largest component's triangle**, about 2k² bytes for k faces in one
> component (float32, half the matrix), so the ceiling is about 10,500 faces *in one component*
> rather than in the library. The real library's largest component is 88 faces (422 faces, 147
> components, 164 groups, 104 offered); the synthetic set's is 60, and synthetic data is too
> idealised to say how close a real library of 10,000 faces would come to that bound. Time, quadratic
> in all faces for the component search, is the practical ceiling.

> **Note 2026-11-21 — measured in the worker** (step 3.4's Task 5, `FaceGroupingWorkerMeasurementDeviceTest`,
> opt-in, on the SM-G990B: the app's own index, Index now through the real scheduler, the disclosure
> marked shown by the test and every preference restored after; figures in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.4 — Built and verified*). The first run
> succeeded: Tier A 1,133 rows, the face pass 1,002 images indexed, 0 failed, 90 GIFs skipped, stopped
> `COMPLETED` in 113.2 s, then grouping `GROUPED` in 292 ms — 422 faces, 164 groups, 104 offered. A
> second, no-op run took 568 ms with grouping `NOT_NEEDED`. Peak RSS rose 372,096 kB in the first
> run, models and the whole pass included; thermal status 0; charging. Grouping on the real library
> is far inside the budget; what remains is a production-size library, where the store's paged read
> and one-transaction write — not in the synthetic figures above — and one large component are
> unmeasured ([ARCHITECTURE.md](../ARCHITECTURE.md) §11; step 3.7).

> **Note 2026-11-23 — step 3.7** (G5c, 2026-11-22, `MediaIndexTierCScaleDeviceTest`, opt-in, on the
> SM-G990B; the figures are in [albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G5c).
> **The store is measured:** through the real Room store on a throwaway file-backed index, at
> 10,000, 20,000 and 37,000 synthetic faces, the paged read and the one-transaction write are under
> a tenth of grouping's time, and memory does not bind. **Time does:** grouping fits about 31,000
> faces after a full-budget face pass and about 56,000 in a whole window — known limit L11, accepted
> by the maintainer on 2026-11-23 as a recorded limit. No real library of 10,000 faces or more exists
> on either phone; that stays unmeasured, accepted as L5, and one giant component stays debt, L4.

### 6. Licences owed

What [albums-stage-3.md](../../design/albums-stage-3.md) *Licence findings* recorded as owed, now that
the choice is made:

- **OpenCV and SFace: Apache-2.0** — a copy of the licence, and a statement of any change made.
  ~~Whether a build trimmed by configuration counts as a change is to be read, not assumed.~~ The
  published artifact is used as published (question 1), so step 3.3 makes no trimmed build; whether
  one counts as a change is read if a trimmed build is ever made, not assumed.
- **YuNet: MIT** — its copyright and permission notice with the copy.
- **OpenCV's bundled third-party components and the C++ runtime it ships with:** ~~**not yet read.**~~
  ~~Which components are bundled depends on how the build is produced (verify item 3 below), so they
  are read once that is known.~~ With the published artifact chosen (question 1), what it bundles is
  known, and step 3.3 reads it (verify item 8). **Read 2026-11-20** (verify item 8): all permissive
  (Apache-2.0, BSD-2/3-Clause, libtiff, libpng-2.0, IJG, Apache-2.0 WITH LLVM-exception; ittnotify's
  GPL-2.0-only OR BSD-3-Clause taken as BSD-3-Clause). Owed: their notices and licence texts in one
  file in the APK, and libjpeg-turbo's IJG sentence verbatim. zlib is the device's own and is not
  shipped. The table and the groups are in
  [albums-stage-3.md](../../design/albums-stage-3.md) *OpenCV 4.13.0 bundled components*.
- **Where:** as files in the APK, **and a row in Settings that shows them**, as the Place data row
  does for GeoNames (question 3, answered 2026-11-19). The row is a Designer task at step 3.6, with
  its strings in every shipped locale.

### Built — what the verify table found, besides its rows (2026-11-20)

- **R8 removed a class OpenCV's native code needs.** `libopencv_java4.so` raises
  `org.opencv.core.CvException` by looking it up by name, which R8 cannot see, so the release build
  stripped it and a native error would have reached Kotlin as a plain `Exception`.
  `app/proguard-face.pro` keeps the class, through the release `optimization` block. With it, the
  release build installed through bundletool on the SM-G990B logs `face capability checked
  capable=true`.
- **OpenCV's published Gradle metadata is stale.** The `.module` file gives the AAR as 3,570,283 B;
  the AAR is 121,695,690 B. Maven Central's own `.module` carries the same figure, and its `.sha1` and
  `.sha256` match the AAR Gradle cached (sha256
  `a8c9b0929d8d6367bb476320a1f36587db2b57ea18d8f7521ce509514903ca04`), so the artifact is the one
  published and only the size field is wrong upstream. **Recommended: no
  `gradle/verification-metadata.xml` for this.** Dependency verification is project-wide — every
  artifact would need an entry, and every upgrade a regenerated file — which is out of proportion to
  one stale field. A change of its own if the project ever wants it.
- **Native OpenCV errors may print under OpenCV's own logcat tag, `cv::error()`**, outside the
  pass's fixed lines. Read in code only, not observed; `Core.setErrorVerbosity(false)` is the likely
  switch. Step 3.7's logger check settles it against ADR-0024 §6.
  *Note 2026-11-23 — observed at step 3.7, G5a (2026-11-22), on the SM-G990B:* a native error prints
  in two places, both priority E. `Core.setErrorVerbosity(false)`, now called in
  `OpenCvFacePipeline.load()`, silences the `cv::error()` line; it does not silence the JNI wrapper's
  `org.opencv.imgproc` "caught cv::Exception" line, and no Java-side switch for it was found. That
  line is ADR-0024 §6's note of 2026-11-22 and known limit L10 in
  [albums-stage-3.md](../../design/albums-stage-3.md) *3.7*, which the maintainer accepted as a
  recorded limit on 2026-11-23, with no precondition checks.

## To verify, and who verifies

*Headed "Verify before adopting" while this record was a draft.* Adoption on 2026-11-19 settled
only item 3, through question 1; every other item is still open. None is to be settled by
assertion; each is settled by the check named, by whom and when the table says. *2026-11-20:* step
3.3 settled items 1, 2, 4 and 8 and the first half of 5, and measured item 6 in the process rather
than in the worker — each marked in its row; items 5's re-check, 6's worker run and 7 remain.
*2026-11-22:* step 3.7 settled item 7 on the SM-G781B, marked in its row; item 5's re-check and what
row 6 still owes remain 3.7's. *2026-11-22, later:* item 5 re-checked, marked in its row; what row 6
still owes remains 3.7's, at G7. *2026-11-23:* step 3.7 measured row 6 through the real worker after
the real disclosure (G7), and ran row 7's remainder on the SM-G781B in part (G10), the rest accepted
by the maintainer — both marked in their rows.

| # | What | How it is settled | Who, when |
|---|---|---|---|
| 1 | **arm64-only packaging must not filter the app on Play.** Play shows an app with native libraries only to devices whose ABI it includes ([Filters on Google Play](https://developer.android.com/google/play/filters), *CPU Architecture*). The release bundle already ships an AndroidX native library for four ABIs, so packaging OpenCV for arm64 alone should keep the app on every device; an app-wide ABI filter would remove it from the others. | bundletool device specs for each ABI the app is offered to today, before and after: same set of devices, OpenCV present only on arm64. ✅ **Settled 2026-11-20:** all four ABIs — arm64-v8a, armeabi-v7a, x86, x86_64 — still get APKs; `libopencv_java4.so` is in `lib/arm64-v8a` only | Coder at 3.3; Reviewer checks; exit criterion |
| 2 | **16 KB pages.** Play requires apps targeting API 35 or higher to support 16 KB memory pages on 64-bit devices and blocks updates that do not from 2027-02-01 ([Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)). | every `.so` in the release bundle checked for 16 KB ELF alignment and packaging — OpenCV's, the C++ runtime, and the AndroidX one already shipped. ✅ **Settled 2026-11-20:** every `.so` in the arm64 split has LOAD segments aligned at `0x4000`, and passes `zipalign -c -P 16` | Coder at 3.3; exit criterion |
| 3 | **How the ~~trimmed~~ OpenCV build is produced** — OpenCV's published Android artifact, a build made by a script under `tools/` and committed as a prebuilt, or a native build inside Gradle. Each has different reproducibility, build-time and licence-reading costs. | question 1 below. ✅ **Settled 2026-11-19:** the published artifact, untrimmed | ~~the maintainer, before 3.3~~ the maintainer, 2026-11-19 |
| 4 | **The merged manifest.** The confirmation covers the library, not what it merges in. | merged debug and release manifests diffed before and after; any new permission, `<queries>` entry or component goes to the maintainer before it ships. ✅ **Settled 2026-11-20:** both byte-identical to the build before the dependency — debug md5 `8856dbd4b4257bd665c4593a325ba624`, release `043a521e4d0a25724364c8b8b6ea6f8d`; nothing went to the maintainer, nothing being added | Coder at 3.3, the change that adds OpenCV |
| 5 | **The download-size budget** | measured on the release bundle's arm64-v8a delivery with bundletool, not estimated. ✅ **Measured 2026-11-20:** `get-size total` for arm64 is 49,613,983 B, **+46,063,982 B** against the 50 MB budget — within it, with little headroom. The models' 36.1 MB are in the base, so every ABI downloads them. ✅ **Re-checked 2026-11-22** (step 3.7, G5d): bundletool 1.18.3 `get-size total`, arm64-v8a, API 34, density 420 spec, on a rebuilt release bundle — **49,712,393 B**, 98,410 B above the earlier figure; the growth is the app code and resources of steps 3.4 to 3.6, the models and OpenCV being unchanged. Margin: 287,607 B (0.58 %) under 50,000,000 B, and 2,716,407 B under 50 MiB. The plan does not say which 50 MB is meant, so 50,000,000 B is taken as the strict reading ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, L12). *2026-11-23: L12 accepted by the maintainer — "50 MB" is 50,000,000 B, and the budget is what the face path adds, +46,063,982 B, 3,936,018 B under it; the total is recorded beside it at every re-check.* | Coder at 3.3; again at 3.7 |
| 6 | **Background speed, memory and heat** — every spike figure was taken in the foreground, on USB power | measured in the worker, on the device. 🔶 **Measured 2026-11-20 in the process, not in the worker** — the worker's pass cannot run until step 3.6 records the disclosure as shown. `FaceIndexPassMeasurementDeviceTest` (opt-in) on the SM-G990B, on a throwaway in-memory index, screen off, USB charging, thermal status 1 before and after: 1,133 live items; 1,002 images reached a state (27 + 46 + 929 over the runs), 0 failed; 422 faces; 150.7 ms per image, the remainder run's mean; peak RSS 619,804 kB, an upper bound, the test process loading the pipeline three times. Of two budgeted runs, the second processed only what the first left. 90 GIFs skipped by design. ~~At that rate the 7-minute budget fits about 2,800 images per run.~~ *(Superseded 2026-11-23, below: about 1,655 per run on this phone with the screen off.)* Still owed: the run through the worker, with Index now and the screen off. ✅ **Measured in the worker 2026-11-21** (step 3.4's Task 5, SM-G990B, Index now, the disclosure set by the test): run 1 — face pass 1,002 images, 0 failed, 90 GIFs skipped, `COMPLETED` in 113.2 s, then grouping `GROUPED` in 292 ms; run 2, no-op, 568 ms, grouping `NOT_NEEDED`; peak RSS rise 372,096 kB in run 1; thermal 0, charging; §5's last note. Still owed at 3.7: a production-size library, the SM-G781B, a non-Samsung phone, and the run through the real disclosure. ✅ **Measured through the real disclosure 2026-11-23** (step 3.7, G7, SM-G990B, Index now, screen off, USB at 100 %): worker 11:32:33 to 11:36:57, 264.2 s; Tier A and places about 9.2 s (334 rows placed); face pass 1,002 images in 254.2 s, 0 failed, 90 GIFs skipped, `COMPLETED`, **253.7 ms per image**; grouping `GROUPED` in at most 735 ms. It used 60.5 % of the 420,000 ms budget, so **about ~~1,655~~ 1,600 images fit one 7-minute run on this phone, not 2,800** *(corrected 2026-11-23 at review: the budget is counted from the worker's start, so a backlog run's face pass has about 420 − 9.2 s after Tier A and places — about 1,619 images at 253.7 ms)* — a larger library takes more runs; nothing fails. That is about 2.25 times slower per image than 3.4's worker run of the same images (113.2 s, about 113 ms per image), this one with the screen off; 3.3's in-process figure was 150.7 ms. The cause of the slowdown is not established, and 3.4's run did not record the screen's state ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G7). Thermal status 0 throughout (AP peak 38.7 °C while the screen was briefly on), charging, no process death; `VmHWM` 480,228 → 551,016 kB at the face phase's start — a lifetime high-water mark, which could not be reset under `run-as` — and peak sampled `VmRSS` 386,204 kB. Only the fixed `FaceIndexPass` lines; none under `cv::error()` or `org.opencv.imgproc`. Still unmeasured, and accepted by the maintainer at 3.7: a production-size library (L5); the face pass on the SM-G781B, stopped at thermal status 2 (row 7; L3, L13); a non-Samsung phone (L2) | Coder at 3.3 and 3.4; the rest at 3.7 |
| 7 | **The capability check on another phone** | the SM-G781B, if it can be attached. ✅ **Settled 2026-11-22** on the SM-G781B — Android 13, API 33; ABIs arm64-v8a, armeabi-v7a and armeabi; about 5.5 GiB RAM — with the debug build, through the instrumented tests: `FaceCapabilityDeviceTest` 2 of 2 and `FaceDecodeOrientationDeviceTest` 4 of 4 (orientations normal, 3, 6 and 8) passed. Its log: capable on `abi=arm64`, `embeddingSize=128`; OpenCV 4.13.0, aarch64, NDK 27.2.12479018, linked 16 KB page-aligned — as on the SM-G990B. Not settled by it: the release build and a face pass on that phone, and the incapable path on hardware, no phone without arm64-v8a being available ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G1, L1 and L3). 🔶 **Partial 2026-11-23** (step 3.7, G10, the debug build): item 1's modes read on that phone — `wal`, `normal`, `secure_delete` 1, not low-RAM; the release build not checked, there being no signing key; the face pass started capable and was stopped 75 s in at thermal status 2, with no figures. Accepted as it stands by the maintainer, with no retry (L3, option A; the thermal finding is L13) | 3.7 |
| 8 | **The licences of the bundled components** | read ~~once item 3 is decided~~ from the published artifact, item 3 being settled. ✅ **Settled 2026-11-20:** every component `getBuildInformation()` lists, and `libc++_shared.so`, read at the version OpenCV 4.13.0 pins — all permissive, none blocks commercial redistribution; what is owed is in [albums-stage-3.md](../../design/albums-stage-3.md) *OpenCV 4.13.0 bundled components* | Planner at 3.3, 2026-11-20 |

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
