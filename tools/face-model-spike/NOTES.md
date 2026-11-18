# Face feasibility spike (Stage 3, step 3.0) — lab notebook

Aggregates only. No photo names, crops, embeddings or labels in this file.
Plan: `docs/design/albums-stage-3.md` step 3.0, answers A1-A8. Run by the Coder; the Planner transfers the findings.
Append a dated entry after every milestone. Newest at the bottom.

## Private artefacts — TO DELETE AT THE END (A7)

Everything below is gitignored (`.gitignore` block "Face feasibility spike"). Delete all of it when the spike closes,
and record the deletion in `docs/design/albums-stage-3.md`.

| Path | Holds |
|---|---|
| `tools/face-model-spike/private/` | photos pulled from the SM-G990B, face crops, per-face records, embeddings, clustering outputs, labelling page, labels, run logs. **Deleted 2026-11-18** |
| `tools/face-model-spike/models/` | downloaded model weights (public, but downloaded; not private). **Deleted 2026-11-18** |
| `tools/face-model-spike/raw/` | downloaded licence texts / model cards (not private). **Deleted 2026-11-18** |
| `tools/face-model-spike/venv/` | Python virtual environment, if one is created. **Deleted 2026-11-18** |
| `tools/face-model-spike/harness/app/build/`, `harness/build/`, `harness/.gradle/` | Gradle build output. **Deleted 2026-11-18** (also `harness/app/.cxx/` and `harness/.kotlin/`) |
| On the SM-G990B: app-internal files of `com.davide.seddio.facespike` | per-face records, embeddings, crops, logs. Removed by uninstalling the harness package. **Deleted 2026-11-18** (`adb uninstall` printed `Success`) |
| `C:/Users/seddiod/Downloads/labels.json` | **A second copy of the maintainer's labels, outside the repository** (identical content to `private/labels/labels.json`, saved four seconds later). It is not covered by any `.gitignore`, so it is the one private file that is not protected by one. **Deleted 2026-11-18** at the maintainer's request |
| `tools/face-model-spike/scripts/__pycache__/` | Python bytecode of the scripts (gitignored by `**/__pycache__/`). **Deleted 2026-11-18** |
| On the SM-G781B | nothing: it was never attached (checked again 2026-11-18), so the harness package is **not** installed on it. If it is attached later and the harness is run, `com.davide.seddio.facespike` must be uninstalled from it (aggregates only leave it) |

---

## 2026-11-17 — M0 preflight

- Devices (`adb devices -l`): **only the SM-G990B (`RFCT703FBKR`, API 36)** is attached. The **SM-G781B (`RFCRB04XXZV`) is NOT attached.**
  The brief says to stop only if the SM-G990B is missing; it is present, so work continues. The SM-G781B part of M3 cannot run
  unless it is attached later.
- SM-G990B: SoC `SM8350`, 5.49 GB RAM (`MemTotal` 5,493,784 kB), ABI arm64-v8a, `/data` 88 GB free, screen awake at preflight.
- MediaStore images on the SM-G990B (`content query content://media/external/images/media`): **1092 images** —
  944 `image/jpeg`, 90 `image/gif`, 38 `image/png`, 20 `image/webp`. Total 1.26 GB. Videos: 41 (not used: decision 40).
- Decision taken (brief left it open): the harness runs over the **1002 still images** (jpeg + png + webp). The 90 GIFs are
  excluded: the app has `MediaType.GIF` apart from `IMAGE`, and decision 40 says "images". Revisit if the Planner reads it otherwise.
- SM-G781B image count: **not measured (not attached).**
- Desktop: Windows + Git Bash, Python 3.14.2 at `C:\Python314`, JDK 17.0.5. 287 GB free on `C:`.
- Git baseline `git status --short` before the spike's first file: tracked modifications in `.github/agents/*`,
  `.github/instructions/architecture-docs.instructions.md`, `AGENTS.md`, `docs/design/albums.md`, `docs/design/albums-stage-4.md`
  (not mine, pre-existing); untracked `docs/design/albums-rule-editing.md`, `docs/design/albums-stage-3.md` (pre-existing).
  My only edits outside this folder: the `.gitignore` block (added before any private path was created).
- Next: M1, read licences before any download.

## 2026-11-17 — M1 candidates and licences

Every text below was read **before** any weights were downloaded (licence pages and model cards were saved under
`raw/licences/` for the record; they hold no private data). I am not a lawyer; this records what the text says.

**Rule I applied (A5), stated so it can be challenged.** A candidate is measured only if the *publisher of the weights* states, in
text that covers the weights file(s), a licence allowing commercial use and redistribution (Apache-2.0, MIT, BSD, CC0,
public domain). A licence on the code repository alone, with weights distributed as release assets or Drive links and no
statement about them, is treated as **unclear = failing**. Separately, I record the training data and any restriction that the
*dataset's* publisher states. **That is a caveat, not a verdict**: the weights' publishers do not invoke it, and I did not
adjudicate it. It is nonetheless material: every recognition model found was trained on a face dataset with research-only terms or
undeclared terms. **The maintainer should decide whether that caveat is acceptable before 3.1 adopts any of them.**

### Detectors

| Candidate | URLs read (2026-11-17) | Code licence | Weights licence | Training data / restriction stated | Format, size, landmarks | Verdict |
|---|---|---|---|---|---|---|
| **YuNet** (OpenCV Zoo `face_detection_yunet`) | opencv_zoo `models/face_detection_yunet/README.md` + `LICENSE`; `ShiqiYu/libfacedetection`, `libfacedetection.train` pages; WIDER FACE page | MIT (zoo dir); libfacedetection.train BSD-3-Clause | zoo README: "All files in this directory are licensed under MIT License" (covers the `.onnx`) | WIDER Face. **The WIDER FACE page states for the dataset: "Creative Common License (cc by-nc-nd)".** The weights' publisher states no restriction | ONNX, 232,589 B fp32 / 100,416 B int8; 5 landmarks (eye centres, nose tip, mouth corners) | **PASS**, dataset caveat. Measured |
| **MediaPipe BlazeFace** (short and full range) | developers.google.com face_detector page; model cards `MediaPipe BlazeFace Model Card (Short Range).pdf` and `(Full Range).pdf` | Apache-2.0 (MediaPipe) | Cards: "LICENSED UNDER Apache License, Version 2.0" | "consented images of people using a mobile AR application". **Card lists as out of scope: "Any form of surveillance or identity recognition is explicitly out of scope and not enabled by this technology"** | TFLite; 224 KB short / 1.1 MB full; 6 keypoints. Short range needs faces ≥ 20 % of the image side: poor for group photos | **PASS on licence**, with the out-of-scope sentence flagged for the maintainer (we would cluster identities, using a different model for the embedding). **Not measured**: a second detector was optional, and it needs LiteRT plus hand-written anchor decoding. Say so rather than read it as excluded |
| **ML Kit Face Detection** (bundled) | developers.google.com/ml-kit/terms (last modified 2025-05-14); Google APIs Terms of Service (2021-11-09); ML Kit face-detection Android page | n/a (closed SDK) | **No weights licence exists.** The Terms of Service say models count as "related software", bar reverse engineering, and incorporate the Google APIs ToS | The terms say ML Kit "may contact Google servers from time to time" (bug fixes, updated models) and "send metrics about the performance and utilization of the APIs in your app to Google"; the developer must inform users | Bundled variant: page says about 9 MB; unbundled about 800 KB (needs Play services download) | **EXCLUDED.** The text is an API service agreement, not a licence to redistribute a model, and it describes server contact and telemetry that the app does not otherwise have. Would be measurable if the maintainer accepts that. Not silently replaced |

### Recognition (embedding) models

| Candidate | URLs read (2026-11-17) | Code licence | Weights licence | Training data / restriction stated | Format, size | Verdict |
|---|---|---|---|---|---|---|
| **SFace** (OpenCV Zoo `face_recognition_sface`, MobileFaceNet with SFace loss) | opencv_zoo SFace README + LICENSE; `zhongyy/SFace` repo page and its torch/mxnet READMEs; HF `opencv/face_recognition_sface` card | Apache-2.0 | zoo README: "All files in this directory are licensed under Apache 2.0 License" (covers the `.onnx`). **The upstream `zhongyy/SFace` repo shows no licence file.** | Zoo does not say what the shipped checkpoint was trained on. Upstream READMEs name CASIA-WebFace, VGGFace2 and MS1MV2, "downloaded from Data Zoo of InsightFace". **InsightFace's README: its training data "(and the models trained with these data) are available for non-commercial research purposes only."** | ONNX, 112x112 input; 38,696,353 B fp32, 9,896,933 B int8 | **PASS**, with a material caveat: whether the checkpoint used data under that statement is undeclared. Measured |
| **OpenFace nn4.small2.v1** | OpenFace README (Licensing); `get-models.sh`; cmusatyalab.github.io/openface/models-and-accuracies | Apache-2.0 | README: "the source code and trained Torch and Python model files are copyright Carnegie Mellon University and licensed under the Apache 2.0 License"; `get-models.sh` repeats it for the downloaded models | "FaceScrub and CASIA-WebFace" (stated by the publisher; no restriction stated by it). FaceScrub page: "released under a creative commons license"; CASIA terms not read | Torch7 `.t7` (OpenCV can load it), 31,510,785 B, 96x96 input, 128-d, 3.7 M parameters; LFW accuracy 0.9292 stated by the publisher, a weak baseline | **PASS**, dataset caveat. Measured as a deliberately weak baseline |
| **dlib ResNet v1** (`dlib_face_recognition_resnet_model_v1`) | davisking/dlib-models README | Boost (dlib); not relevant to the weights | README: "anyone can do whatever they want with these model files as I've released them into the public domain" | "about 3 million faces ... face scrub dataset, the VGG dataset ... and a large number of images I scraped from the internet". The same README flags where a *dataset* excludes commercial use (the 68-landmark model); it states none for this one | dlib `.dat` (dlib's own serialisation, 21,428,389 B bz2), 150x150 input, 128-d; LFW 0.9938 stated | **PASS** (clearest licence text of all). Measurement depends on converting a non-standard format; see the plan below |
| **EdgeFace** (Idiap) | `otroshi/edgeface` LICENSE; HF `Idiap/EdgeFace-S-GAMMA` card | BSD-3-Clause (code repo) | HF card: "released under CC BY-NC-SA 4.0" | WebFace260M (12M and 4M subsets) | | **EXCLUDED**: weights non-commercial |
| **InsightFace family** (buffalo_l, `w600k_*`, ArcFace ONNX/mxnet models) | `deepinsight/insightface` README, License section | MIT (code) | README: "The training data ... (and the models trained with these data) are available for non-commercial research purposes only", also for the auto-downloaded models; contact address given for licensing | MS1M, VGG2, CASIA-WebFace | | **EXCLUDED**: non-commercial |
| **GhostFaceNets** | `HamadYA/GhostFaceNets` README + LICENSE | MIT (repo) | **No statement about the weights**, which are GitHub release `.h5` assets | MS1MV2 / MS1MV3 (InsightFace datasets, see above) | | **EXCLUDED**: weights licence unclear; dataset publisher's statement above |
| **MobileFaceNet** (`foamliu/MobileFaceNet`, and Qualcomm AI Hub's export `qualcomm/MobileFaceNet`) | foamliu README + LICENSE; HF card `qualcomm/MobileFaceNet` | Apache-2.0 (repo) | foamliu: none for the release `.pt`. Qualcomm card: front matter `license: apache-2.0`, but its License section only points at foamliu's code licence, and its TFLite/ONNX files are S3 downloads, not files of the HF repo | "Refined MS-Celeb-1M" | Qualcomm export: TFLite float, about 4 MB, 112x112, 128-d | **EXCLUDED**: unclear whether the licence covers the weights. **This is the one exclusion I would revisit first**: a 4 MB TFLite model that suits LiteRT, if the maintainer accepts repo-level Apache-2.0 plus an MS-Celeb-1M training set |
| **FaceNet** (`davidsandberg/facenet`) | README + LICENSE.md | MIT (repo) | **No statement about the weights**, which are Google Drive links | CASIA-WebFace, VGGFace2; README: "give proper credit to those providing the training dataset" | Inception-ResNet-v1, tens of MB | **EXCLUDED**: weights licence unclear |
| **py-feat `mobilefacenet`** | HF `py-feat/mobilefacenet` card | MIT (py-feat code) | The card itself says an explicit redistribution licence for the checkpoint "has not been established" | unknown | | **EXCLUDED** by its own publisher's text |

Not examined (named so nobody assumes they were): AdaFace, CVLface, ONNX Model Zoo ArcFace (an InsightFace model, so likely under the
statement above), OpenVINO Open Model Zoo, Facenet-PyTorch. Training-data terms of CASIA-WebFace, FaceScrub (which Creative Commons
licence), VGG Face and MS-Celeb-1M were not read beyond what is quoted above.

### Result of M1

- **Recognition models passing A5: three** (SFace, OpenFace nn4.small2.v1, dlib ResNet v1), so the spike continues. All three carry a
  training-data caveat; none is clean on that axis.
- **Detectors passing and measurable: YuNet.** BlazeFace passes but is not measured (stated above). ML Kit is excluded.
- Weights downloaded after the reading, into the gitignored `models/`: YuNet fp32 + int8, SFace fp32 + int8, OpenFace `.t7`, dlib `.dat.bz2`.
  Sizes above. (The first dlib download timed out at 15,479,897 of 21,428,389 B; resumed.)
- Format consequence: **none of the passing models is TFLite.** YuNet and SFace are ONNX, OpenFace is Torch7, dlib is dlib's own format.
  So LiteRT, which the brief prefers, cannot run them without a conversion that adds a failure mode of its own. The runtime is chosen in M2.
- Next: M2, decide the runtime and build the harness.

## 2026-11-17 — Maintainer's answers since M1 (recorded before M2 resumes)

- **Training-data caveat:** measure SFace, OpenFace nn4.small2.v1 and dlib ResNet **now**. The shipping decision stays at step 3.1, and the
  M1 caveat (every recogniser was trained on a face dataset with research-only or undeclared terms) stays attached to every number below.
- **MobileFaceNet (Qualcomm TFLite): stays excluded.**
- **ML Kit Face Detection: stays excluded** (server contact + telemetry).
- **GIFs: excluded; still images only** (jpeg/png/webp). Confirms the M0 choice (1002 still images on the SM-G990B).
- **SM-G781B:** the maintainer is attaching it now. Check `adb devices -l` at M3; if it is still not listed then, do the SM-G990B work,
  record that it was missing, and continue without waiting.
- State found on resume: M0 and M1 done; a previous run had started the harness in `harness/` (Embedders.kt, Job.kt, MainActivity.kt,
  Gradle files; `private/logs/build1.log`) and downloaded `raw/aar/opencv-4.13.0.aar` and `raw/align_dlib.py`, but wrote no M2 entry.
  `models/dlib_face_recognition_resnet_model_v1.dat` was already decompressed. Continuing from there, not restarting.

## 2026-11-17 — M2 runtime and harness

**Runtime choice.** Two runtimes, each chosen because it is the only one that loads that format without a conversion:
- **OpenCV 4.13.0 for Android (Apache-2.0, Maven `org.opencv:opencv`)**, DNN module on CPU: YuNet (ONNX, via `FaceDetectorYN`), SFace
  (ONNX, via `FaceRecognizerSF`, which also does the 5-point alignment) and OpenFace (Torch7, via `Dnn.readNetFromTorch`). One runtime also
  gives the image ops (resize, similarity-transform estimation, warp). ONNX Runtime was rejected: it cannot read Torch7, and a second
  runtime for two of the three models adds download size for nothing. LiteRT: no passing model is TFLite (M1), and a conversion adds a
  failure mode that would be measured instead of the model.
- **dlib 20.0 (Boost licence) built with NDK 27.1 / CMake 3.22.1 into `libdlibrec.so`**, about 70 lines of JNI around dlib's own `anet_type`
  (the network type copied from dlib's python binding). dlib's `.dat` is its own serialisation, so running it natively is the only route
  that does not first reimplement and then re-validate the network. It built first time (~3 min) and runs on the phone, so dlib is
  measured **on the phone**, not desktop-only.

**Pipeline (`harness/`, package `com.davide.seddio.facespike`, never the app's id).** Over the still images (jpeg/png/webp; 1002 on the SM-G990B):
`ImageDecoder` downscaled so the long side <= 1280 (EXIF orientation honoured) -> BGR "working image" -> YuNet on a copy with long side 640
(score threshold 0.7, NMS 0.3) -> box and landmarks mapped back to working coordinates -> per recogniser: align the five landmarks (eye
centres, nose tip, mouth corners) onto that model's template -> embed. Templates: SFace uses OpenCV's own `alignCrop`; OpenFace uses the
template from its own `align_dlib.py` (the five points recomputed and checked against the file: 26.63,16.06 / 67.79,15.26 / 47.24,40.52 /
29.48,61.62 / 66.76,60.88 at 96 px); dlib uses the 5 corresponding points of its 68-point chip template (size 150, padding 0.25, from
`get_face_chip_details`): 47.55,46.60 / 100.48,46.60 / 74.01,76.56 / 50.41,103.02 / 97.61,103.02. **The dlib and OpenFace alignments are
my five-point approximations of what those models were trained with (68-point chips); a small quality penalty is possible and I did not
measure it.** Recognisers run in the same process on the same detected faces, so face sets are identical across recognisers.
Per photo the harness records decode, detection and per-face align and embed times (first photo excluded from statistics: lazy init), VmHWM
and VmRSS from `/proc/self/status`, battery/thermal state at start and end. `dump=true` also writes per-face records (image id, box, landmarks,
score, embeddings), a 128x128 crop per face and a per-photo CSV into the app's internal storage. The activity keeps the screen on and runs in the foreground.
Scripts: `scripts/device.sh` (install, push models via `run-as`, wake + keyguard + focus logging, run, pull), `scripts/m3_g990b.sh`.

**Native-library sizes, arm64-v8a (the only ABI kept):**

| Library | Raw | deflate -9 |
|---|---|---|
| `libopencv_java4.so` (prebuilt, ALL modules, not stripped to the modules we use) | 23,972,544 B | 9,437,536 B |
| `libc++_shared.so` | 1,292,904 B | 403,232 B |
| `libdlibrec.so` (dlib + JNI, `--gc-sections`) | 1,708,008 B | 450,350 B |

Models, raw / deflate -6: SFace fp32 38,696,353 / 35,879,298; SFace int8 9,896,933 / 6,897,106; OpenFace 31,510,785 / 28,945,046;
dlib 22,466,066 / 20,928,274 (the published `.bz2` is 21,428,389); YuNet fp32 232,589 / 202,484; YuNet int8 100,416 / 55,996.
These feed the download-size comparison in the final entry. A custom OpenCV build limited to core, imgproc, calib3d, dnn and objdetect would be
much smaller than the prebuilt above; **I did not build one**, so the OpenCV figure is an upper bound.

**Smoke run, 30 images on the SM-G990B (screen on, foreground):** all three recognisers loaded and ran; 29 faces in 30 images; 0 failed
images. dlib took ~166 ms per face on one core (dlib's own CPU convolution, no BLAS), SFace ~17 ms, OpenFace ~6 ms. No bug found in the harness.
Gotchas hit: `adb push`/`install` need Windows-style paths when `MSYS_NO_PATHCONV=1` (`pwd -W`); an empty `--es recs ""` is dropped by
`am start`, so detector-only runs omit the extra.

- `git status --short -uall tools/face-model-spike` run from the repo root (see the end of this file for the final output).
- Next: M3.

## 2026-11-17 — M3 measurements

State found on resume: the SM-G990B runs had all been done (`private/g990b/`, `scripts/m3_g990b.sh`, log ends `M3_G990B_DONE`, exit 0), and a
first M4 analysis script (`scripts/analyse.py`, `private/analysis/`) existed; neither was written up. I **verified** the data instead of re-running it:
1002 images in every run, 0 failed, `images_ok` 1002; `full` run has 533 per-face records, 533 crops (`tar` lists 534 entries = 533 files + the directory),
and its record count equals `faces_found`. Every run: screen awake, `mCurrentFocus` = launcher before and the harness `MainActivity` after (also at each
60 s poll), so the harness was in the foreground throughout. Phone on USB power (`plugged` 2); thermal status 0 (none) at start and end of every run;
battery temperature 33.5 to 36.8 degrees C over the whole session.

**Phones attached.** `adb devices -l` at the start of M3 and again before finishing it: **only the SM-G990B (`RFCT703FBKR`) is listed. The SM-G781B
(`RFCRB04XXZV`) was missing both times**, so there are **no SM-G781B numbers** (timing, memory, face counts). The M3 requirement for it is *not met*; nothing
was installed on it. The harness runs unchanged if it is attached later (`scripts/m3_g990b.sh` with the serial and `--ez dump true` omitted, aggregates only).

**SM-G990B (SM8350, 5.5 GB RAM, API 36, 4 threads), 1002 still images (944 jpeg, 38 png, 20 webp), one process and one run per recogniser (fresh VmHWM).**
Detector YuNet fp32 (232,589 B, load 9 to 11 ms) for every row; decode is not counted in detection.

| | SFace (fp32) | OpenFace nn4.small2.v1 | dlib ResNet v1 | Detector only |
|---|---|---|---|---|
| Model size on disk | 38,696,353 B | 31,510,785 B | 22,466,066 B | 232,589 B |
| Model load | 161 ms | 94 ms | 83 ms | 9 ms |
| Detection per photo, median / p90 | 16.9 / 28.5 ms | 21.6 / 33.9 ms | 22.4 / 35.4 ms | 15.3 / 25.7 ms |
| Align per face, median / p90 | 0.21 / 0.32 ms | 0.36 / 0.47 ms | 0.74 / 0.98 ms | |
| Embed per face, median / p90 | 15.6 / 20.6 ms | 7.8 / 8.1 ms | 309.0 / 394.7 ms | |
| Whole run, 1002 images | 75.3 s | 87.3 s | 260.1 s | 61.3 s |
| Peak RSS, VmHWM | 299 MB | 246 MB | 236 MB | 207 MB |
| Faces found / images with >= 1 face / failed images | 533 / 347 / 0 | 533 / 347 / 0 | 533 / 347 / 0 | 533 / 347 / 0 |
| Thermal status start / end | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Foreground check | harness in front, awake | same | same | same |

Reading the table honestly:
- Faces are the same 533 in every row because one detector feeds every recogniser. 655 images have none, 276 have one, 32 have two, 31 have 3 to 5,
  8 have 6 or more. 111 of the 533 faces have a smaller box side under 40 px in the 1280 px working image (so small that an embedding is unlikely to be
  reliable); that figure is the reason M4 does not cluster them.
- **The detection column drifts between runs (15 to 22 ms median) though the detector, images and settings are identical.** Thermal state stayed 0 and the
  phone was on USB power, so I cannot attribute it; run order (detector-only first, dlib last) and battery temperature 33.5 to 35.6 degrees C moved together with it.
  Read detection as roughly 15 to 25 ms per photo, not as a difference between recognisers.
- The all-three run with `dump=true` (one process, crops written) is slower per step (detection median 23.2 / p90 36.3 ms, SFace embed 21.4 / 28.9 ms,
  dlib embed 312 / 377 ms; 279.8 s whole run, VmHWM 371 MB with three models resident). I use the separate runs above for the per-recogniser figures.
- dlib is slow because its network runs on dlib's own single-threaded CPU convolution; 309 ms median per face is about 1 s per 3 faces. For this
  1002 image library (533 faces) that is under 3 minutes of embedding; at 10,000 faces it would be about 50 minutes. SFace would be about 3 minutes.
  OpenFace about 1.3 minutes.
- Decode (`ImageDecoder`, long side 1280): median 37 ms, p90 77 ms per photo in the detector-only run.
- Detector repeat and int8: see M4. int8 YuNet (100,416 B): detection median 26.7 / p90 40.8 ms in its own run, so not faster on this CPU.

**Added download size against the 50 MB budget (A4).** Deflate -9 sizes from M2 (libraries) and deflate -6 (models), arm64-v8a only; MB = 10^6 bytes.
Play's own compression and a possible custom OpenCV build were **not measured**; the OpenCV figure is the full prebuilt `libopencv_java4.so` (all modules), so an upper bound.

| Combination | Parts (compressed) | Total |
|---|---|---|
| **Best realistic: SFace fp32 + YuNet fp32 + OpenCV** (the only measured recogniser that fits comfortably) | 35.88 + 0.20 + 9.44 + 0.40 (libc++) | **45.9 MB**; under 50 MB with 4.1 MB headroom |
| Same with the int8 models (SFace int8 + YuNet int8) | 6.90 + 0.06 + 9.84 | 16.8 MB. **The SFace int8 embedding quality was not measured**; only the int8 detector was (M4) |
| OpenFace + YuNet fp32 + OpenCV | 28.95 + 0.20 + 9.84 | 39.0 MB |
| dlib + YuNet fp32 + OpenCV + `libdlibrec.so` | 20.93 + 0.45 + 0.20 + 9.84 | 31.4 MB |
| **Upper bound: all three recognisers + OpenCV + dlib library** | 35.88 + 28.95 + 20.93 + 0.20 + 9.84 + 0.45 | **96.2 MB: nearly twice the budget** |

Installed (uncompressed) size of the SFace-fp32 stack is 232,589 + 38,696,353 + 23,972,544 + 1,292,904 = 64.2 MB. Whether the 50 MB budget means download or install
size is a question for the Planner; both are given.

- `git status --short -uall tools/face-model-spike` after M3: only `NOTES.md`, `scripts/*` and harness sources (listed in the final entry).
- Next: M4 (the analysis script from the previous run is reused; it clusters dlib on cosine distance, which is wrong for dlib, fixed first).

## 2026-11-17 — M4 grouping, picker view, anchor stability, labelling package (final entry of this run)

No labels exist yet, so **nothing below says whether a group is right**; it says how many groups there are, how they behave under change, and what the
maintainer has to do next. Data: the 533 faces of the SM-G990B `full` run. **Faces whose smaller box side is under 40 px in the 1280 px working image
(111 of 533) are not clustered, offered or labelled**; that is my pre-filter (an embedding of a face that small is unreliable), the Planner may reverse it.
That leaves **422 faces from 299 images** in every figure below. Scripts: `scripts/analyse.py`, `scripts/build_label_page.py`, `scripts/serve_labels.py`.

**Fix to the previous run's analysis.** It clustered dlib on cosine distance. That collapses: dlib's descriptors are not zero-centred, so the median pair was
0.14 apart, the 1st percentile 0.045, and a threshold of 0.2 made one cluster. Each recogniser now uses the distance its publisher defines: SFace cosine; OpenFace cosine (its
vectors are unit length, so this is half the squared L2 the OpenFace paper thresholds); dlib Euclidean on the raw descriptor. The first sweep's dlib rows were discarded.

**Pairwise distance over all 422 faces** (1st / 5th percentile / median): SFace 0.37 / 0.64 / 0.90; OpenFace 0.23 / 0.44 / 0.89; dlib 0.44 / 0.55 / 0.75.
Publishers' same-person thresholds, as distances: SFace 0.637 (OpenCV's cosine similarity 0.363), OpenFace about 0.5 (squared L2 about 1.0 to 1.1), dlib 0.6.
Agreement of the 5 nearest neighbours between recognisers (chance is 0.01): SFace-OpenFace 0.35, SFace-dlib 0.44, OpenFace-dlib 0.36. They see related but different structure.

**Clustering, B1 (what I tried).** Two simple approaches, each swept over a threshold t:
1. *Average-linkage agglomerative* on the full distance matrix (scipy), cut at distance t. Order-independent. It needs the n x n matrix: fine for 422 faces,
   about 800 MB as float64 at 10,000, so an app would need a blocked or approximate version. That cost is **not** measured.
2. *Incremental mean-distance*: faces in turn; each joins the existing cluster with the smallest mean distance to its members if below t, otherwise starts a new one.
   One pass, order-dependent, the shape an app could run as faces arrive.

**Offered groups, decision 40's rule** (a group is offered only if its faces come from at least 2 different images), average-linkage; the incremental method gives counts within 7
of them (the largest gap is dlib 0.6: 52 against 59). Clusters / singletons / **offered** / faces in offered groups / largest group:

| Recogniser | t | Clusters | Singletons | **Offered** | Faces in offered | Largest |
|---|---|---|---|---|---|---|
| SFace | 0.30 | 217 | 91 | 126 | 331 | 17 |
| SFace | **0.45** (strict) | 178 | 67 | **111** | 355 | 37 |
| SFace | 0.55 | 164 | 60 | 100 | 362 | 57 |
| SFace | **0.637** (publisher) | 124 | 38 | **84** | 379 | 65 |
| OpenFace | 0.20 | 210 | 85 | 125 | 337 | 14 |
| OpenFace | **0.30** (strict) | 160 | 46 | **114** | 376 | 29 |
| OpenFace | 0.40 | 101 | 15 | 86 | 407 | 58 |
| OpenFace | **0.50** (publisher) | 59 | 3 | **55** | 417 | 66 |
| dlib | 0.40 | 211 | 79 | 132 | 343 | 12 |
| dlib | **0.45** (strict) | 183 | 64 | **119** | 358 | 23 |
| dlib | 0.55 | 103 | 24 | 79 | 398 | 62 |
| dlib | **0.60** (publisher) | 65 | 11 | **52** | 407 | 62 |

The full sweep (t from 0.05 or 0.20 to 0.80 or 1.00) is in `private/analysis/summary.json`. Reading it: at matched strictness the three recognisers offer about 100 to 120
thumbnails for this library (422 faces, 299 images) and about 50 to 85 at the publishers' thresholds. The median offered group is **2 faces from 2 images** at the strict values and
4 at OpenFace 0.5 and dlib 0.6. A library of this size therefore shows a hundred or so unnamed faces, most of them pairs. Whether those pairs are the same person is what the labels will say.
Without labels, the largest groups at the publisher thresholds (62 to 66 faces) may be one frequently photographed person or an over-merged blob; I cannot tell which.
**Threshold I would show, provisionally: the strict one** (SFace 0.45, OpenFace 0.30, dlib 0.45), because a wrong person under a name-less thumbnail is worse than the same person offered twice.
The final value waits for the labels.

**Thumbnail rule (one face per offered group).** The group's *medoid*, the member with the smallest mean distance to the other members, restricted to members whose smaller box side is at
least 60 px in the working image (all members if none qualifies). Deterministic, independent of input order given the group.

**Anchor stability (decision 42).**
(a) *Detection run twice* on the same 1002 images, same phone, same build and settings: 533 faces both times, **all 533 matched, box IoU exactly 1.0 for every one (min 1.0), 0 unmatched,
0 images where the face count differs.** YuNet on this CPU is deterministic, so a box anchor survives re-detection by the same build; this says nothing about another phone, build or OpenCV version.
As a first sensitivity point I also ran the int8 YuNet (100,416 B) on the same images: 535 faces; 525 matched; 8 fp32 and 10 int8 faces unmatched; 18 images where the count differs; matched-box IoU
min 0.75, 5th percentile 0.88, median 0.957, mean 0.948; 100 % of matches at IoU >= 0.5, 91 % at >= 0.9. So a *different detector build* moves boxes by a few percent and
changes the face count for about 5 % of the images that have faces (18 of 347).
(b) *Re-clustering with shuffled input order*, 20 seeds. Definition of "the same group": the candidate group containing the thumbnail face and the reference offered group each contain at least 50 %
of the other's faces. Reported per recogniser/threshold as the mean over groups of the share of seeds in which the thumbnail's group is the same; in brackets, the share of groups that were the same in all 20 seeds.
- *Average-linkage, order only:* 100 % for every group at every setting. That is by construction (the method does not depend on order), **not** a result.
- *Average-linkage, order shuffled and a random 10 % of the other faces removed* (a library that grows or loses photos; the thumbnail face is always kept):
  SFace 0.45 98.5 % (91 %), SFace 0.637 98.6 % (91 %), OpenFace 0.30 97.9 % (86 %), **OpenFace 0.50 87.7 % (27 %)**, dlib 0.45 98.4 % (88 %), **dlib 0.60 95.1 % (64 %)**; the worst single group is 50 to 60 % in every setting.
- *Incremental (order-dependent), order shuffled:* SFace 0.45 96.5 % (90 %), SFace 0.637 91.0 % (73 %), OpenFace 0.30 93.5 % (77 %), **OpenFace 0.50 54.2 % (10 %)**, dlib 0.45 95.3 % (83 %), **dlib 0.60 66.7 % (25 %)**.
- A *random member* as anchor instead of the medoid: within 0.1 to 3.7 points under average-linkage (with 10 % removed; dlib 0.60 is the 3.7) and 1 to 9 points worse under the incremental method, so the medoid rule helps only a little.
So stability falls as the threshold loosens (big groups merge and split when a few faces change), it is high at the strict values, and an order-dependent clusterer is clearly worse than an order-independent one.
Stability is not correctness: a stable group can still be the wrong person.

**Labelling package** (`private/label/index.html`, built by `scripts/build_label_page.py`; opened in a check: served over HTTP, the page returns 200, a sample of the face crops load by the relative path
`../g990b/full/<id>_<k>.jpg`, all 422 crops exist on disk, the page script passes `node --check`, a test save returned 204 and was deleted afterwards; **not tested in a browser**, no automation).
*Design chosen:* faces are labelled **once**; all three recognisers share the same detected faces, so each recogniser's offered groups are scored later against those per-face labels, and no face is
labelled three times. The page shows the faces in **100 cards** (a base partition: SFace, average-linkage, 0.55, groups of at least 2 faces; 364 faces, medoid first) followed by **58 single faces** that did not group;
that is 158 things to look at, not 422. A face that is the thumbnail of an offered group is badged S, O or D (the recogniser, at its publisher threshold); the tooltip lists every threshold. Sampling was **not** needed.
- *Open it:* from `tools/face-model-spike` run `venv/Scripts/python scripts/serve_labels.py` (serves `private/` on 127.0.0.1 only) and open <http://127.0.0.1:8765/label/index.html>. Autosaves to `private/labels/labels.json` three
  seconds after each change; **Save** forces it. (Opening `private/label/index.html` directly also works: progress stays in the browser and **Download JSON** writes `labels.json`; move it to `private/labels/`.)
- *Do this:* for each card type **one name** for the person (a letter or a short name; names are matched ignoring case, so typing the same name on two cards says "same person"; the name box suggests the names used so far).
  Click a face that does **not** belong to the card: it turns red and gets its own box; type the right name, leave it empty to skip that face, or type `!` if it is not a face at all. Leave a card's name empty to skip the
  whole card. Singles at the bottom: name the ones you recognise, skip the rest (unlabelled faces are left out of every metric). "Only show cards with unlabelled faces" hides the finished ones.
- *Time:* about 100 cards at roughly 15 to 20 s each is 25 to 35 minutes; the 58 singles are optional and add about 5 to 10. **Estimate, not measured.** The cards are ordered largest first, so stopping early
  still labels the most faces. Faces skipped now cost recall information later, not correctness.
- *Output:* `private/labels/labels.json`: `{version, base, labels: [{idx, id, k, name, card}]}`, `idx` being the line number in `private/g990b/full.faces.jsonl` (0-based). Nothing leaves `private/`.

**Skipped / not done, plainly.**
- **SM-G781B: no measurements at all** (not attached at either check). M3's second phone is open; the Planner should know the SM-G990B numbers are one SoC (SM8350) only.
- No quality metrics (as instructed): precision, recall, purity and the threshold choice need the labels.
- SFace int8 embedding quality was never measured, only the int8 detector; the 16.8 MB combination rests on it.
- BlazeFace was not measured (M1/M2). A custom OpenCV build was not built (45.9 MB uses the full prebuilt).
- Detection per-photo timing drifted 15 to 22 ms between identical runs (M3); unexplained.
- Cosine on dlib and the first sweep: replaced, see above. `.vscode/settings.json` appeared untracked in the repo root at 16:58 (a tool setting, `chat.tools.global.autoApprove`); not created by the scripts, left alone.

**Current to-delete list (A7), unchanged in kind:** `tools/face-model-spike/private/` (now also `analysis/`, `label/`, and `labels/` once the maintainer saves), `models/`, `raw/`, `venv/`,
`harness/app/build/`, `harness/build/`, `harness/.gradle/`; on the SM-G990B the app `com.davide.seddio.facespike` (its internal files hold per-face records and crops: uninstall it); the SM-G781B has nothing on it.
Also not committed and not mine to remove now: `.vscode/settings.json` at the repo root.

**`git status --short -uall tools/face-model-spike`** (run from the repo root, after M4): `NOTES.md`; `harness/` sources only (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`,
`gradle/wrapper/*`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/cpp/{CMakeLists.txt,dlibrec.cpp}`, `app/src/main/java/.../{Embedders,Job,MainActivity}.kt`); `scripts/` (`analyse.py`,
`build_label_page.py`, `device.sh`, `m3_g990b.sh`, `serve_labels.py`, `summarise_runs.py`). No photo, crop, embedding, label or log appears. Nothing outside `tools/face-model-spike` and the `.gitignore` block was touched.

- Next: the maintainer labels (above); then score each recogniser's groups against the labels (purity, recall, per-threshold choice), and run the SM-G781B if it is attached.

## 2026-11-18 — M5 quality metrics, recommendation, close-out (final entry of this run)

**State found on resume.** M0 to M4 done; the maintainer finished labelling on 2026-11-18. I read `scripts/device.sh`, `scripts/analyse.py` and `harness/app/build.gradle.kts` in their
current state before scoring. M5 touches none of them: it scores the stored per-face records of the 2026-11-17 SM-G990B run (`private/g990b/full.faces.jsonl`) with `analyse.py`'s own functions
(average linkage on each recogniser's distance, the offered-group rule of decision 40, the medoid thumbnail rule), so the groups are exactly M4's. New script: `scripts/score.py`
(`venv/Scripts/python scripts/score.py private/g990b private/labels/labels.json private/analysis --pick sface=0.45:0.637:0.5,openface=0.3:0.5:0.2,dlib=0.45:0.6`; aggregates go to
`private/analysis/m5_scores.json`). Persons are renamed P1, P2, ... by descending image count inside the process; no name is written to any file.

**Decisions taken (maintainer's, and mine).**
- The **40 px small-face cut-off is kept** (the maintainer did not object). 111 of the 533 detected faces are therefore in no number below: not clustered, not labelled, not offered.
- **The SM-G781B is still not attached** (`adb devices -l`, 2026-11-18: only the SM-G990B, `RFCT703FBKR`). It is skipped unless it appears; there are no SM-G781B numbers anywhere in this spike.
- Mine, because the brief left them open: a group is **evaluable** only if at least 2 of its faces carry a person label (otherwise "majority share" is 1.0 by construction); "a thumbnail shows person p" means the
  thumbnail face itself is labelled p (what the user sees); the group "a user would pick" for p is, among the offered groups whose thumbnail shows p, the one holding the most of p's images (ties: the purer);
  an album image is **confirmed** if the truth puts the person (or pair) in it, **wrong** if not, and **unknown** (excluded from precision, counted) if the picked group's faces in it are unlabelled and
  none confirms it; a person's truth images are the images where a face is labelled with that person, so truth only covers faces of at least 40 px. I added a **"regulars" subset (persons with at least 5 images)**
  and the two main subjects, because of the label distribution below: every median over 94 persons is 1.00 almost regardless of the setting.
- Sweep: SFace 0.30 to 0.60 in steps of 0.05 plus the publisher's 0.637; OpenFace 0.20 to 0.50 in steps of 0.05; dlib 0.40 to 0.60 in steps of 0.05. Strict and publisher values are those of M4.
  **"Recommended" column:** the loosest threshold per recogniser that keeps weighted album precision (item 7) at or above 0.95 and mixed groups at or under about 5 % of the evaluable ones; for SFace that rule also
  admits 0.55, which I did not choose because 0.55 is the partition the maintainer labelled from (below); OpenFace meets the rule nowhere in the swept range, and 0.20 is simply its purest swept value; for dlib it is the strict value 0.45, so dlib has no third column.

**Labels.** 422 faces were scored (every face of at least 40 px, from 299 images). **372 faces got a person label, 95 persons, 8 are `!` (not a face), 42 were skipped** (empty). 288 images carry a person label.
Persons by image count: 78 have exactly 2 images, 9 have 4, 1 has 3, 1 has 1, then 6, 6, 8, 10, and two large ones with **68 and 56 images (P1, P2; 125 of the 372 labelled faces)**. 94 persons have at least 2 images;
32 pairs of persons appear together in at least 2 images. Two other things the labels show: the 8 non-faces are detector false positives of 66 to 195 px with YuNet scores of 0.72 to 0.81, so they are not small, and a score cut
high enough to remove them would also remove 29 of the 372 real faces (the lowest real score is 0.70); they are three whole cards of non-faces and one non-face inside a card.

**Bias, stated plainly.** The maintainer labelled card by card from **one recogniser's grouping: SFace, average linkage, 0.55** (100 cards of at least 2 faces = 364 faces, plus 58 single faces; my recomputation reproduces the cards:
99 of the 99 labelled cards lie inside one recomputed cluster). The maintainer typed one name per card and **3 of the 360 labelled card faces, in 2 cards, differ from their card's majority name** (one of them a non-face); 38 of the 58 singles were skipped,
20 were named. That favours SFace in four ways, and I cannot size any of them:
1. SFace's own errors at or below 0.55 are mostly invisible: two different people SFace merged, and the eye did not catch, carry one label. The same merge by OpenFace or dlib across two SFace cards shows up as a mixed group. **Every SFace purity figure at or below 0.55 is an upper bound, and at 0.55 the groups are the cards, so that figure is nearly circular.**
2. Persons exist in the truth almost only because SFace grouped at least two of their faces: 88 of 95 persons sit in exactly one card; only 6 persons were found spread over several cards or singles, which is the number of SFace misses the labelling itself revealed.
   "People found" for SFace (92 of 94 at 0.50) is therefore close to circular; a person only another recogniser groups is mostly a skipped single and absent from the truth, which shows as a not-evaluable group, not as a hit (OpenFace strict 9 not-evaluable groups, SFace strict 4).
3. Merges of two real people that SFace made are scored as one person, so a recogniser that keeps them apart is charged with a split.
4. The 42 skipped faces (38 of them singles SFace left alone) leave every recogniser's metrics, so a face SFace could not place is under-represented.
What the bias does **not** reach: fragmentation of the main subjects (P1 and P2 across many thumbnails) and OpenFace's and dlib's mixed groups are visible whatever SFace did; the unproven part is the size of SFace's margin, not the order.
The label file was labelled once; there was no second labeller, so label error is also unmeasured.

**Reading the numbers.** Definitions (the numbering is the brief's): 1 offered thumbnails and distinct persons they cover (group majority); 2 persons with at least 2 images, how many have a thumbnail showing them; 3 persons on more than one thumbnail, and the extra thumbnails;
4 mixed groups (majority share under 1.0, under 0.8, over evaluable groups) and groups holding a non-face; 5 thumbnail face = group's majority person; 6 strangers = evaluable offered groups whose majority person has fewer than 3 (5) images in the truth;
7 single-person album precision/recall per person, median / min, and weighted (micro: summed over images), the persons at 5 images or more, and recall if the worst split group of a person was picked; 8 two-person albums over the 32 co-appearing pairs
(album = images with a face of both picked groups). Item 8's precision is 1.00 in every cell (an album image must contain a face of both picked groups, a stricter test than item 7's); its recall is what varies.
The compact curves first, then the three requested columns per recogniser.

SFace (distance 1 - cosine; publisher 0.637):

| t | offered | people found | split persons/extra | mixed/evaluable | with `!` | 7 micro precision/recall | P1 / P2 recall (thumbnails) |
|---|---|---|---|---|---|---|---|
| 0.3 | 126 | 90/94 | 11/32 | 0/122 | 3 | 1.0/0.676 | 0.25 (13) / 0.179 (10) |
| 0.35 | 124 | 90/94 | 10/30 | 0/120 | 3 | 1.0/0.694 | 0.279 (12) / 0.179 (11) |
| 0.4 | 116 | 90/94 | 9/22 | 0/112 | 3 | 1.0/0.729 | 0.368 (8) / 0.25 (8) |
| 0.45 | 111 | 90/94 | 9/17 | 1/107 | 3 | 0.992/0.774 | 0.544 (5) / 0.304 (7) |
| 0.5 | 104 | 92/94 | 6/8 | 1/100 | 4 | 0.991/0.907 | 0.824 (2) / 0.696 (4) |
| 0.55 | 100 | 92/94 | 3/4 | 1/96 | 4 | 0.991/0.913 | 0.824 (2) / 0.696 (3) |
| 0.6 | 91 | 84/94 | 3/3 | 9/88 | 3 | 0.948/0.936 | 0.853 (2) / 0.821 (2) |
| 0.637 | 84 | 76/94 | 3/3 | 14/82 | 3 | 0.897/0.932 | 0.853 (2) / 0.821 (2) |

OpenFace nn4.small2.v1 (distance 1 - cosine; publisher about 0.5):

| t | offered | people found | split persons/extra | mixed/evaluable | with `!` | 7 micro precision/recall | P1 / P2 recall (thumbnails) |
|---|---|---|---|---|---|---|---|
| 0.2 | 125 | 86/94 | 14/33 | 4/119 | 3 | 0.943/0.604 | 0.147 (12) / 0.071 (11) |
| 0.25 | 118 | 80/94 | 11/32 | 13/112 | 4 | 0.9/0.623 | 0.206 (9) / 0.071 (16) |
| 0.3 | 114 | 74/94 | 10/30 | 15/105 | 4 | 0.864/0.652 | 0.412 (6) / 0.071 (18) |
| 0.35 | 97 | 68/94 | 6/19 | 21/89 | 4 | 0.822/0.683 | 0.485 (3) / 0.143 (14) |
| 0.4 | 86 | 60/94 | 6/17 | 30/81 | 5 | 0.777/0.752 | 0.838 (2) / 0.143 (13) |
| 0.45 | 68 | 49/94 | 5/12 | 35/64 | 5 | 0.681/0.752 | 0.838 (2) / 0.25 (9) |
| 0.5 | 55 | 42/94 | 2/7 | 31/52 | 4 | 0.627/0.825 | 0.926 (1) / 0.518 (7) |

dlib ResNet v1 (Euclidean on the raw descriptor; publisher 0.6):

| t | offered | people found | split persons/extra | mixed/evaluable | with `!` | 7 micro precision/recall | P1 / P2 recall (thumbnails) |
|---|---|---|---|---|---|---|---|
| 0.4 | 132 | 90/94 | 12/38 | 0/127 | 3 | 1.0/0.647 | 0.176 (14) / 0.107 (14) |
| 0.45 | 119 | 89/94 | 10/25 | 3/115 | 3 | 0.988/0.704 | 0.338 (9) / 0.196 (9) |
| 0.5 | 103 | 82/94 | 9/16 | 10/100 | 3 | 0.952/0.79 | 0.559 (6) / 0.446 (5) |
| 0.55 | 79 | 65/94 | 5/7 | 27/74 | 2 | 0.827/0.896 | 0.897 (2) / 0.661 (4) |
| 0.6 | 52 | 47/94 | 0/0 | 25/50 | 1 | 0.76/0.931 | 0.897 (1) / 0.893 (1) |

**SFace** at strict 0.45, publisher 0.637, recommended 0.50:

| | strict 0.45 | publisher 0.637 | recommended 0.5 |
|---|---|---|---|
| 1 Offered thumbnails | 111 | 84 | 104 |
| 1 Distinct persons they cover (group majority) | 90 | 78 | 92 |
| 1 Groups with fewer than 2 person-labelled faces (not evaluable) | 4 | 2 | 4 |
| 2 Persons with >=2 images in truth | 94 | 94 | 94 |
| 2 ... with at least one thumbnail showing them | 90 | 76 | 92 |
| 2 ... with none | 4 | 18 | 2 |
| 3 Persons on more than one thumbnail | 9 | 3 | 6 |
| 3 Extra thumbnails from splits | 17 | 3 | 8 |
| 4 Mixed groups, majority share < 1.0 (of evaluable) | 1 of 107 | 14 of 82 | 1 of 100 |
| 4 Mixed groups, majority share < 0.8 | 0 | 13 | 0 |
| 4 Groups containing a non-face | 3 | 3 | 4 |
| 5 Thumbnail face = group's majority person | 107 of 107 | 79 of 82 | 100 of 100 |
| 5 Thumbnail face is another person / a non-face / unlabelled | 0 / 3 / 1 | 0 / 2 / 3 | 0 / 3 / 1 |
| 6 Strangers: majority person < 3 images (of evaluable) | 79 | 65 | 79 |
| 6 Strangers: majority person < 5 images | 90 | 74 | 90 |
| 7 Single-person albums built | 90 | 76 | 92 |
| 7 Precision median / min / mean | 1.00 / 0.80 / 1.00 | 1.00 / 0.40 / 0.94 | 1.00 / 0.67 / 0.99 |
| 7 Recall median / min / mean | 1.00 / 0.30 / 0.97 | 1.00 / 0.67 / 0.99 | 1.00 / 0.50 / 0.99 |
| 7 Weighted (micro) precision / recall | 0.992 / 0.774 | 0.897 / 0.932 | 0.991 / 0.907 |
| 7 Persons with precision 1.0 / recall 1.0 | 89 / 85 | 65 / 73 | 90 / 88 |
| 7 Regulars (>=5 images): found / persons | 6 / 6 | 5 / 6 | 6 / 6 |
| 7 Regulars: precision median / min | 1.00 / 0.80 | 1.00 / 0.57 | 1.00 / 0.80 |
| 7 Regulars: recall median / min (missing counted as 0) | 0.77 / 0.30 | 0.93 / 0.00 | 1.00 / 0.70 |
| 7 Recall if the worst split group was picked: median / min (persons with several groups) | 0.50 / 0.04 (9) | 0.09 / 0.07 (3) | 0.75 / 0.04 (6) |
| 7 Album images the truth cannot judge (unlabelled faces) | 0 | 3 | 0 |
| 8 Pairs co-appearing in >=2 images / with both persons pickable | 32 / 29 | 32 / 21 | 32 / 30 |
| 8 Precision median / min | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| 8 Recall median / min / mean | 1.00 / 0.00 / 0.87 | 1.00 / 0.50 / 0.96 | 1.00 / 0.00 / 0.92 |
| 8 Weighted (micro) precision / recall | 1.0 / 0.703 | 1.0 / 0.862 | 1.0 / 0.855 |
| 8 Pairs whose album has recall 0 | 3 | 0 | 2 |
| Main subjects P1 / P2: recall (thumbnails) | 0.544 (5) / 0.304 (7) | 0.853 (2) / 0.821 (2) | 0.824 (2) / 0.696 (4) |

**OpenFace** at strict 0.30, publisher 0.50, best swept 0.20 (no threshold meets the rule above):

| | strict 0.3 | publisher 0.5 | recommended 0.2 |
|---|---|---|---|
| 1 Offered thumbnails | 114 | 55 | 125 |
| 1 Distinct persons they cover (group majority) | 75 | 45 | 86 |
| 1 Groups with fewer than 2 person-labelled faces (not evaluable) | 9 | 3 | 6 |
| 2 Persons with >=2 images in truth | 94 | 94 | 94 |
| 2 ... with at least one thumbnail showing them | 74 | 42 | 86 |
| 2 ... with none | 20 | 52 | 8 |
| 3 Persons on more than one thumbnail | 10 | 2 | 14 |
| 3 Extra thumbnails from splits | 30 | 7 | 33 |
| 4 Mixed groups, majority share < 1.0 (of evaluable) | 15 of 105 | 31 of 52 | 4 of 119 |
| 4 Mixed groups, majority share < 0.8 | 15 | 29 | 4 |
| 4 Groups containing a non-face | 4 | 4 | 3 |
| 5 Thumbnail face = group's majority person | 103 of 105 | 46 of 52 | 119 of 119 |
| 5 Thumbnail face is another person / a non-face / unlabelled | 0 / 3 / 7 | 2 / 2 / 4 | 0 / 3 / 3 |
| 6 Strangers: majority person < 3 images (of evaluable) | 67 | 35 | 78 |
| 6 Strangers: majority person < 5 images | 78 | 41 | 91 |
| 7 Single-person albums built | 74 | 42 | 86 |
| 7 Precision median / min / mean | 1.00 / 0.20 / 0.93 | 0.63 / 0.12 / 0.64 | 1.00 / 0.43 / 0.98 |
| 7 Recall median / min / mean | 1.00 / 0.07 / 0.95 | 1.00 / 0.50 / 0.94 | 1.00 / 0.07 / 0.94 |
| 7 Weighted (micro) precision / recall | 0.864 / 0.652 | 0.627 / 0.825 | 0.943 / 0.604 |
| 7 Persons with precision 1.0 / recall 1.0 | 63 / 67 | 13 / 36 | 83 / 76 |
| 7 Regulars (>=5 images): found / persons | 5 / 6 | 5 / 6 | 5 / 6 |
| 7 Regulars: precision median / min | 1.00 / 0.43 | 0.67 / 0.46 | 1.00 / 0.43 |
| 7 Regulars: recall median / min (missing counted as 0) | 0.46 / 0.00 | 0.72 / 0.00 | 0.32 / 0.00 |
| 7 Recall if the worst split group was picked: median / min (persons with several groups) | 0.50 / 0.02 (10) | 0.51 / 0.02 (2) | 0.50 / 0.03 (14) |
| 7 Album images the truth cannot judge (unlabelled faces) | 3 | 26 | 2 |
| 8 Pairs co-appearing in >=2 images / with both persons pickable | 32 / 20 | 32 / 7 | 32 / 24 |
| 8 Precision median / min | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| 8 Recall median / min / mean | 1.00 / 0.00 / 0.90 | 1.00 / 0.00 / 0.82 | 1.00 / 0.00 / 0.83 |
| 8 Weighted (micro) precision / recall | 1.0 / 0.661 | 1.0 / 0.793 | 1.0 / 0.625 |
| 8 Pairs whose album has recall 0 | 2 | 1 | 4 |
| Main subjects P1 / P2: recall (thumbnails) | 0.412 (6) / 0.071 (18) | 0.926 (1) / 0.518 (7) | 0.147 (12) / 0.071 (11) |

**dlib** at strict 0.45 (also its recommended value) and publisher 0.60:

| | strict 0.45 | publisher 0.6 |
|---|---|---|
| 1 Offered thumbnails | 119 | 52 |
| 1 Distinct persons they cover (group majority) | 90 | 49 |
| 1 Groups with fewer than 2 person-labelled faces (not evaluable) | 4 | 2 |
| 2 Persons with >=2 images in truth | 94 | 94 |
| 2 ... with at least one thumbnail showing them | 89 | 47 |
| 2 ... with none | 5 | 47 |
| 3 Persons on more than one thumbnail | 10 | 0 |
| 3 Extra thumbnails from splits | 25 | 0 |
| 4 Mixed groups, majority share < 1.0 (of evaluable) | 3 of 115 | 25 of 50 |
| 4 Mixed groups, majority share < 0.8 | 3 | 25 |
| 4 Groups containing a non-face | 3 | 1 |
| 5 Thumbnail face = group's majority person | 114 of 115 | 43 of 50 |
| 5 Thumbnail face is another person / a non-face / unlabelled | 0 / 3 / 2 | 4 / 1 / 4 |
| 6 Strangers: majority person < 3 images (of evaluable) | 80 | 36 |
| 6 Strangers: majority person < 5 images | 93 | 43 |
| 7 Single-person albums built | 89 | 47 |
| 7 Precision median / min / mean | 1.00 / 0.50 / 0.99 | 1.00 / 0.22 / 0.74 |
| 7 Recall median / min / mean | 1.00 / 0.20 / 0.96 | 1.00 / 0.50 / 0.97 |
| 7 Weighted (micro) precision / recall | 0.988 / 0.704 | 0.76 / 0.931 |
| 7 Persons with precision 1.0 / recall 1.0 | 87 / 82 | 24 / 42 |
| 7 Regulars (>=5 images): found / persons | 6 / 6 | 6 / 6 |
| 7 Regulars: precision median / min | 1.00 / 1.00 | 1.00 / 0.43 |
| 7 Regulars: recall median / min (missing counted as 0) | 0.83 / 0.20 | 0.95 / 0.83 |
| 7 Recall if the worst split group was picked: median / min (persons with several groups) | 0.50 / 0.03 (10) | - / - (0) |
| 7 Album images the truth cannot judge (unlabelled faces) | 0 | 16 |
| 8 Pairs co-appearing in >=2 images / with both persons pickable | 32 / 29 | 32 / 9 |
| 8 Precision median / min | 1.00 / 1.00 | 1.00 / 1.00 |
| 8 Recall median / min / mean | 1.00 / 0.00 / 0.86 | 1.00 / 0.50 / 0.93 |
| 8 Weighted (micro) precision / recall | 1.0 / 0.676 | 1.0 / 0.909 |
| 8 Pairs whose album has recall 0 | 4 | 0 |
| Main subjects P1 / P2: recall (thumbnails) | 0.338 (9) / 0.196 (9) | 0.897 (1) / 0.893 (1) |

What the picker would show, in plain terms (SFace 0.50): 104 thumbnails for 299 images, **79 of the 100 evaluable ones belong to a person who appears in exactly 2 images of the truth and 90 to a person with fewer than 5** (the strangers proxy; at the strict values the share is 74 % for SFace
(79 of 107), 64 % for OpenFace (67 of 105) and 70 % for dlib (80 of 115)). 4 offered groups contain a non-face and 3 thumbnails are non-faces. The two main subjects are the albums that matter and the ones the threshold affects most: at SFace 0.45 P1 is spread over 5 thumbnails (best recall 0.54) and P2 over 7 (0.30);
at 0.50 and 0.55 P1 sits in 2 thumbnails (0.82) and P2 in 4 or 3 (0.70); at 0.60 both are in 2 thumbnails (0.85, 0.82) but 9 of 88 groups are mixed and weighted precision falls to 0.948. **SFace has a plateau (0.50 to 0.55) and then a cliff (0.60).**
OpenFace has no plateau: at 0.20 P1 is in 12 thumbnails, at 0.40 P1 is whole (0.84) but 30 of 81 groups are mixed. dlib has none either: at 0.45 P1 is in 9 thumbnails, at 0.55 P1 is 0.90 but 27 of 74 groups are mixed.

**Recommendation for 3.1 to consider (an input, not a decision): SFace (fp32) at cosine distance 0.50, average-linkage clustering, medoid thumbnail.** On these labels it is the only recogniser with an operating point that is both pure and consolidating:
weighted album precision 0.991 and recall 0.907, 1 mixed group in 100, thumbnail correct in 100 of 100 evaluable groups, 92 of 94 people with a thumbnail, both main subjects in 2 and 4 thumbnails, 30 of 32 co-appearing pairs buildable at precision 1.00.
OpenFace never reaches that purity at a useful recall, and dlib reaches it only where the main subjects are scattered over 9 thumbnails; both are also the two whose alignment is my five-point approximation (M2), so they were measured at a small disadvantage.
SFace embeds a face in 15.6 ms against dlib's 309 ms, and its full stack (45.9 MB download, 4.1 MB under the budget) is the one measured as the best realistic combination.
0.50 rather than 0.55: nearly the same figures on this library (P2 in 4 thumbnails instead of 3, 6 split persons instead of 3), but 0.55 is the labelling base. Weighted precision is flat (0.991 to 0.992) from 0.45 to 0.55, so 0.45 is the conservative fallback if the maintainer prefers one more step of margin from the cliff; it costs recall (0.774) and splits the main subjects badly.
**What argues against it, plainly:**
- **Size.** The measured stack leaves 4.1 MB of the 50 MB budget, and 35.9 MB of it (deflated; 38.7 MB raw) is the fp32 model. The int8 SFace (16.8 MB stack) has **not** had its embedding quality measured. A custom OpenCV build is unbuilt.
- **Licence.** The zoo's SFace files are Apache-2.0, but the checkpoint's training data is undeclared and its upstream names datasets that InsightFace says are non-commercial research only (M1). OpenFace (Apache-2.0, FaceScrub + CASIA) and above all dlib (public domain, stated by its publisher) are cleaner on the weights text. The maintainer's acceptance of the caveat is still the open question for 3.1.
- **The evidence is biased toward it** (above), on one library: 1002 images, 299 with a clusterable face, one phone, and a truth in which 78 of 95 persons have exactly 2 images and two people dominate. The plateau might be narrower on a library the labels did not come from, and the 0.55-to-0.60 cliff is close.
- **Even at 0.50 the picker is mostly strangers:** 79 of 100 groups are people who appear in exactly 2 photos; 3 non-face thumbnails and 8 extra thumbnails from splits remain, and a detector-score cut cannot remove the non-faces without dropping about 8 % of real faces.
  Whether a picker full of 2-photo people is what decision 40 wants is a product question these numbers raise, not answer.
- **Cost not measured in the app:** average linkage needs the n x n matrix (about 800 MB as float64 at 10,000 faces); the incremental clusterer is cheaper but order-dependent and was only measured for stability (M4), not for these metrics.
  Speed on the SM-G990B: about 75 s for 1002 images, which extrapolates to roughly 12 minutes per 10,000 photos at this face density (not measured at that size, one SoC).

**Still unmeasured, plainly.** The SM-G781B (no numbers of any kind: timing, memory, face counts). SFace int8 embedding quality (only the int8 detector was measured). The size of a custom OpenCV build (45.9 MB uses the full prebuilt library).
BlazeFace (never measured). Labels that were not seeded by one recogniser, and a second labeller. The order-dependent clusterer's quality metrics. The effect of my five-point alignment approximations on OpenFace and dlib. Faces under 40 px, and GIFs and videos (out of scope by decision).
Detection timing drift of 15 to 22 ms between identical runs (M3, unexplained).

**Complete to-delete list (A7), nothing deleted yet.** `tools/face-model-spike/private/` (photos' crops, per-face records, embeddings, `analysis/` including `m5_*`, `label/`, `labels/` with `labels.json` and `labels.prev.json`, `logs/`, `smoke/`, `g990b/`); `models/`; `raw/`; `venv/`;
`harness/app/build/`, `harness/build/`, `harness/.gradle/`; `scripts/__pycache__/`; **`C:/Users/seddiod/Downloads/labels.json`** (outside the repository, the maintainer's second copy of the labels); on the SM-G990B (`RFCT703FBKR`) the package **`com.davide.seddio.facespike`**
(`adb uninstall`; its internal files hold per-face records and crops); the SM-G781B has nothing on it. Not mine and left alone: `.vscode/settings.json` at the repository root.

**`git status --short -uall tools/face-model-spike`** (from the repository root, after M5): `NOTES.md`; `harness/` sources (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`,
`app/src/main/cpp/{CMakeLists.txt,dlibrec.cpp}`, `app/src/main/java/.../{Embedders,Job,MainActivity}.kt`); `scripts/` (`analyse.py`, `build_label_page.py`, `device.sh`, `m3_g990b.sh`, `score.py`, `serve_labels.py`, `summarise_runs.py`). No photo, crop, embedding, label, log or name is listed.
Nothing outside `tools/face-model-spike` was touched, nothing was committed.

- Next: the Planner transfers the findings into `docs/design/albums-stage-3.md`; the maintainer decides the licence caveat, the size budget reading and the picker's content; then the to-delete list above is executed and recorded in that plan.

## 2026-11-18 — Spike closed; private data deleted

The maintainer closed step 3.0 and asked for the spike data to be deleted (A7, decision 9). Executed by the Coder; nothing was committed and the Easy Gallery app was not touched.

**Deleted, desktop (all under `tools/face-model-spike/` unless absolute):** `private/` (about 16 MB: `analysis/`, `g990b/`, `label/`, `labels/` with `labels.json` and `labels.prev.json`, `logs/`, and the rest), `models/` (119 MB), `raw/` (169 MB), `venv/` (225 MB),
`harness/app/build/`, `harness/.gradle/`, `harness/app/.cxx/`, `harness/.kotlin/` (not in the original list; Kotlin compiler session files of the harness build), `scripts/__pycache__/`. `harness/build/` did not exist.
**Outside the repository:** `C:/Users/seddiod/Downloads/labels.json` (the maintainer's copy of the labels). A byte comparison against `private/labels/labels.json` just before deletion reported a difference at byte 77, so the two copies were **not** byte-identical as recorded above; both are gone, so it no longer matters.
`/tmp/kotlin-daemon.2026-11-17.16-32-13-940.00.log` (Kotlin compiler daemon log of the harness build, 19 KB; it holds paths of the harness build, no photo data) was also deleted.
**A lock was found and cleared:** `scripts/serve_labels.py` was still running as an http server (python PIDs 18520 and its child 44116, serving `private/`). Both were stopped (`taskkill`) before the deletion. No Gradle or Kotlin daemon of the harness was running. No other process referenced the spike folder afterwards.

**Deleted, SM-G990B (`RFCT703FBKR`):** `com.davide.seddio.facespike` uninstalled (`adb uninstall` printed `Success`), which removes its internal storage (`files/models`, `files/results` with per-face records, crops, per-photo CSVs, run logs).
Where the scripts wrote on the phone, from reading `device.sh` and `m3_g990b.sh`: only the app's internal `files/` and the transient `/data/local/tmp/facespike_models`, which `device.sh` removes itself after copying. Nothing is written to shared storage, and the scripts take no screenshots or logcat dumps.

**How verified.**
- Desktop: every path above tested with `[ -e ]`, each reported gone; `ls -A tools/face-model-spike` shows `harness/ NOTES.md scripts/` only; `Downloads` holds only three unrelated `.epub` files.
- Phone: `pm list packages -u | grep -i facespike` empty; `run-as com.davide.seddio.facespike ls` says `unknown package`; `/data/local/tmp` holds only `.studio` and `dalvik-cache` (both pre-date the spike), nothing newer than 2026-11-16; `/sdcard` has no file newer than 2026-11-16 to depth 4 and no `*facespike*`, model or `*.faces.jsonl` file; `/sdcard/Android/{data,media,obb}/*facespike*` do not exist.
- SM-G781B: not attached (`adb devices` lists only the SM-G990B), and no entry in this file ever records an install or copy to it. It could not be queried, so "nothing on it" rests on the record, not on a check.
- `git status --short -uall tools/face-model-spike` lists only `NOTES.md`, `harness/` sources and `scripts/*`.

**Ambiguous, listed and left alone.** `/tmp/adb.log` (48 KB, 2026-11-18 10:15) is the adb server's own log; it has no `facespike` string but does contain device traffic, and it is shared with every adb use on this machine, so it was not deleted.
`/tmp/add.py` belongs to a different spike (`content-model-spike`, an unrelated notes-appending helper), not this one. The `.vscode/settings.json` at the repository root is, as noted in M4, not this spike's.
Photos pulled from the phone: by the scripts, only per-face 128 px crops were ever pulled (`pull_dump`), and those were under `private/`; I did not find a photo anywhere else.
