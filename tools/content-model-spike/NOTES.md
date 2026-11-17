# Content-model spike (step 4.0) - lab notebook

Not a project document. Dated entries, newest last. Every milestone: command, numbers or error text.
Raw numbers live in `results/` (gitignored: they name private photos). `raw/`, `models/`, `.venv/` are gitignored.

Setup: Python 3.12 venv in `.venv` (pins in `requirements.txt`). Phone: SM-G990B, serial RFCT703FBKR, Android 16.
Shell: Git Bash; `adb` = `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`; Windows python needs `cygpath -w` for /tmp paths.

## 2026-11-13 - continuation run: state found, housekeeping

- Previous run left: EmbeddingGemma 2 split (vision / LM / host-side token table) exported as fp32, dyn8, nbits4 under
  `models/gemma/`; `raw/ref/gemma_fp32_b70.npy` (+queries); 300 photos in `raw/photos` (manifest.json lists 300;
  the "301" is the manifest file); no SigLIP 2 work; no Android project; no results; no notes.
- `.gitignore` gained `**/__pycache__/` and `tools/content-model-spike/**/*.pyc`; `requirements.txt` pinned to the
  installed versions (CRLF). `git check-ignore -v` confirms raw/, models/, .venv/, results/, __pycache__ ignored.
- Stray `scipy-1.18.1-...whl` in `.venv/Lib/site-packages` left alone (inside the gitignored venv, origin unclear).
- Device now: 407 images + 21 videos in MediaStore (the 300-photo sample is JPEG/PNG/WebP only). Battery 100 %, USB
  powered, `settings get global low_power` = 0 (battery saver off), thermal status 0, SoC (AP) 34.0 C, skin 33.7 C.
  `/proc/meminfo`: MemTotal 5.49 GB, MemAvailable 1.33 GB (before any run), 6 CPUs reported by `nproc`.

## 2026-11-13 - EmbeddingGemma 2 split verified against the SentenceTransformer reference

Command: `cd scripts && ../.venv/Scripts/python.exe verify_gemma_split.py > ../results/verify_gemma_split.log`
Why a split: the checkpoint is one model with four modalities; the text+vision build is `vision.onnx` (patches ->
soft tokens), `lm.onnx` (embedded sequence -> 768-d, L2-normalised) and a token embedding table gathered on the host
(134M of the ~440M parameters, kept out of the graph so it can be memory-mapped). The host lays the LM input out as
[BOS, boi, soft tokens, eoi, EOS] like the Hugging Face processor, padding patches never fed.
Result (float32, PyTorch split vs `SentenceTransformer`): text 'castles' cos 1.0, 'una spiaggia al tramonto' cos 1.0;
image (budget 280) cos 1.0, 1.0, 1.0000001 on the first 3 photos. So the split reproduces the reference.
Side fact: PyTorch eager fp32 on this desktop took 24-91 s per image (b70/b280), far slower than ORT; not used for bulk.

## 2026-11-13 - SigLIP 2 exported (fp32 ONNX)

Command: `cd scripts && ../.venv/Scripts/python.exe export_siglip.py` (new: `siglip_common.py`, `export_siglip.py`,
`siglip_ort.py`, `make_reference_siglip.py`). Same split idea: `vision.onnx` (pixels [1,3,224,224] -> L2-normalised
768-d), `text.onnx` (embedded tokens [1,64,768] -> 768-d), token table gathered on the host.
Sizes on disk (fp32): vision.onnx 371,670,257 B; text.onnx 342,881,437 B; embed_table_f32.bin 786,432,000 B
(256000 x 768, the single largest piece; ~196M of the parameters). Text follows the card: lower-cased, padded to 64,
no attention mask.
Neither downloaded repo contains a LICENSE or NOTICE file (SigLIP 2 file list: .gitattributes, README.md, config.json,
model.safetensors, preprocessor_config.json, special_tokens_map.json, tokenizer.json, tokenizer.model,
tokenizer_config.json). No repository was gated: both downloaded with normal TLS checks and no token.

## 2026-11-16 - phase 1 (phone) started: state verified

- Branch feat/album-content; spike files intact; `results/` held only 3 logs. APK from 2026-11-13 is OLDER than
  Pipelines.kt (source mtime newer by ~20 h), so it is rebuilt before use. Harness reads photos from MediaStore by id
  (`inputs/photo_ids.txt` on the phone = ids of `raw/photos/manifest.json`, so reference row order = manifest order),
  parity tensors from `inputs/gemma_parity_b{70,280}` (50 files each), queries from `inputs/gemma_queries.json`.
- Device before anything: SM-G990B RFCT703FBKR, battery 100 %, USB powered, `low_power`=0 (battery saver off),
  battery temp 29.2 C, MemTotal 5,493,784 kB, MemAvailable 1,238,024 kB, **SwapTotal 8,388,604 kB (zram), SwapFree
  5,252,048 kB** (so memory pressure has a swap cushion that a pure RSS reading hides). Harness package not installed.
- Shell env used: `ANDROID_HOME/ANDROID_SDK_ROOT=${LOCALAPPDATA//\\//}/Android/Sdk`, `ANDROID_SERIAL=RFCT703FBKR`,
  `MSYS_NO_PATHCONV=1`, `ADB="$ANDROID_HOME/platform-tools/adb.exe"`.

## 2026-11-16 - harness plumbing problems found and fixed before the first measurement

1. `./gradlew :app:assembleDebug` in android-harness: everything UP-TO-DATE (the 2026-11-13 APK matched the source).
   Installed with `:app:installDebug`; `pm grant ... READ_MEDIA_IMAGES` -> granted. All 300 ids of the sample manifest are
   still in MediaStore (408 images live in total, no photos added since the sample was pulled).
2. FIRST FAILURE: files pushed with `adb push` into `/sdcard/Android/data/<pkg>/files` are owned by uid `shell`; the app
   (uid u0_a596, in group ext_data_rw) got `EACCES` writing `results/*.json` and an NPE on `dir.listFiles()` of the pushed
   models dir (FUSE enforces the owner). Tried: (a) app-created results dir after `rmdir` of the shell-made one: writes work
   but the shell-owned models/inputs stay unreadable -> NPE at `Job.execute` (MainActivity.kt:110). Fix chosen: the harness
   now uses the INTERNAL files dir (`ctx.filesDir`, one-line change in MainActivity.kt) and files are streamed in with
   `adb exec-in "run-as <pkg> sh -c 'cat > files/<rel>'" < local` (debug build), results pulled with `adb exec-out run-as
   <pkg> cat`. md5 of a pushed 87 MB `lm.onnx` equals the local one; push speed ~60 MB/s. Internal storage is also the
   realistic place a shipped model would live.
3. `adb.exe` cannot resolve Git Bash `/c/...` paths for `push` ("cannot stat"): not needed any more (exec-in reads the
   local file via shell redirection).
4. Thermal: with the screen ON the AP sensor sits at 37-38 C (idle with screen off: 30.8 C), so the cool-down wait never
   ends; the run script puts the phone to sleep (KEYCODE_SLEEP) while cooling and wakes it right before the launch.
   Screen-off AP went 38 -> 32.7 C in 90 s.
- Driver: `scripts/run_phone_variant.sh <kind> <variant> <budget> [limit] [threads] [timeout]` (pushes what is missing,
  cools, launches, samples /proc/<pid>/status every ~3 s from the host, pulls `results/device/<tag>.*`, greps logcat).
  Peak-memory counters: harness `peak_pss_kB_sampled` (Debug.MemoryInfo totalPss, 40 ms sampler) AND `VmHWM` (kernel
  high-water RSS, `proc_status_end`); the host sampler's VmHWM is a cross-check.

## 2026-11-16 - smoke run on the phone (gemma nbits4 b70, 5 photos): the pipeline works end to end

Command: `bash scripts/run_phone_variant.sh gemma nbits4 70 5 4 900`. Two script bugs found on the way: (a) the
"already pushed" marker file `.ok` sat inside the parity dir and was listed as a parity input -> `BufferUnderflowException`
(markers moved to `files/.markers/`); (b) inputs were skipped because `photo_ids.txt` existed from an earlier test.
Result (status ok): load 1,953 ms (vision session 871, lm session 1,077, table+special 6 ms); 24 queries 30-49 ms each;
parity inputs ~1.04-1.10 s each; MediaStore photos: decode+preprocess 294-377 ms, vision 944-1,048 ms, LM 109-173 ms;
embedding dim 768; peak PSS 495,351 kB, VmHWM 544,004 kB (host sampler agrees). AP sensor 37.3 C before (screen just
woken) -> 63.6 C after a 74 s run, thermal status still 0. Decision: the matrix uses limit=60 photos + 50 parity inputs
per run (N >= 50), 4 ORT intra-op threads, one run per variant x budget.

## 2026-11-16 14:15 - continuation run (phase 1 part 2): state found

- Matrix driver `bash scripts/run_phone_matrix.sh 60 dyn8 nbits4 70 280` (cygwin pid 46078, started 13:57) is alive (`kill -0` ok);
  gemma-dyn8-b70 finished 14:06 (status ok, wall 135 s, external max VmHWM 643,584 kB, AP 38.6 -> 52.1 C, thermal_status 0 -> 1,
  MemAvailable before 1,339,744 kB, SwapFree before 5,381,424 kB); gemma-dyn8-b280 was in its cool-down at 14:11 (AP 34.7 C). I wait, I do not touch it.
- Argument order: `run_phone_matrix.sh [limit] "variants" "budgets"`; `ps` strips the quotes, so the real call is `60 "dyn8 nbits4" "70 280"`
  (observed: dyn8 b70, dyn8 b280 running; then models/gemma/dyn8 removed from the phone, then nbits4 b70, nbits4 b280).
- SigLIP reference: `scripts/make_reference_siglip.py` is NOT running any more; its log ends with the final line ('image split vs HF cosine
  (every 6th): min 0.9999995 mean 1.0 n 50') and `raw/ref/siglip_fp32_images.npy` (921,728 B, 14:05) + `siglip_fp32_queries.npy` exist. Not started again.
- git: branch feat/album-content; changed: the two docs files (other agent), NOTES.md, android-harness MainActivity.kt (filesDir change); untracked: phone_report.py, run_phone_matrix.sh, run_phone_variant.sh.

## 2026-11-16 14:25 - Matryoshka truncation (desktop only, from the fp32 reference)

Command: `cd scripts && ../.venv/Scripts/python.exe matryoshka.py` (new script; writes results/matryoshka.md/.json). 300 photos, 24 queries,
truncate to d dims + re-normalise, both query and image, vs the same reference scored at 768. First fix: shape error in my self-cosine
(compared (300,d) with (300,768)); now cut vs the first d dims of the normalised full vector.

| run | self cos img mean/min | self cos qry mean | Spearman of query score lists mean/min | top-8 overlap mean/min |
|---|---|---|---|---|
| b70 d512 | 0.8812 / 0.8724 | 0.8799 | 0.9735 / 0.9500 | 0.8802 / 0.7500 |
| b70 d256 | 0.7226 / 0.6978 | 0.7120 | 0.9188 / 0.8638 | 0.7865 / 0.5000 |
| b70 d128 | 0.6252 / 0.5745 | 0.6035 | 0.7012 / 0.5835 | 0.4948 / 0.0000 |
| b280 d512 | 0.8811 / 0.8662 | 0.8799 | 0.9714 / 0.9542 | 0.9010 / 0.6250 |
| b280 d256 | 0.7235 / 0.6892 | 0.7120 | 0.9156 / 0.8678 | 0.8073 / 0.5000 |
| b280 d128 | 0.6206 / 0.5582 | 0.6035 | 0.7098 / 0.6051 | 0.5573 / 0.2500 |

Self cos = cosine between the cut-and-renormalised vector and the full vector (= share of the norm in the first d dims).

## 2026-11-16 14:45 - matrix results so far (driver still running; times are phone wall clock)

All runs: limit 60 photos + 50 parity tensors + 24 queries, 4 ORT threads, USB powered, screen woken by the driver, FLAG_KEEP_SCREEN_ON in the harness.
- gemma-dyn8-b70 (status ok, wall 135 s): load 2,709 ms (vision session 1,212 + LM session 1,438 + table 10); first query 75 ms, 24 queries median/p95 39/54 ms;
  60 photos total 1,193/1,327 ms = decode+preprocess 66/300 + vision 986/1,110 + LM 135/155; 50 parity inputs 1,064/1,194 ms; peak PSS (harness sampler, 40 ms)
  582 MB, VmHWM 628 MB (host sampler 628 MB, equal); RSS after load 599 MB; AP 38.6 -> 52.1 C at the end (65.7 max sampled), thermal_status 0 -> 1;
  MemAvailable before 1,308 MB (driver: 1,339,744 kB), min during 925 MB.
- gemma-nbits4-b70 (ok, wall 150 s): load 2,125 ms (vision 1,010 + LM 1,056 + table 5); first query 36 ms, queries 39/48 ms; photos total 1,325/1,575 ms
  (decode+pre 64/328, vision 1,103/1,228, LM 146/157); parity 1,192/1,273 ms; peak PSS 491 MB, VmHWM 544 MB (host 544 MB); AP 36.6 -> 50.8 C, thermal 0 -> 1;
  MemAvailable before 1,709 MB (the phone had killed apps in between, see below), min during 1,458 MB.
- gemma-dyn8-b280: **KILLED by lmkd after 431 s.** Harness process (pid 5556) gone with no result json and no .done; queries were written
  (`_qry.f32` 73,728 B = 24 x 768 x 4) but the parity file was not, so the process died INSIDE the 50-input parity loop (the 'SPIKE' progress lines
  only exist in the photo loop and there are none). Host sampler: VmHWM 1,169,516 kB at +14 s, 1,713,300 kB at +40 s (RssAnon 1.67 GB), RSS 1.63-1.71 GB
  until +339 s, then RSS 855 MB with VmSwap 911 MB at +372 s (pages pushed to zram), pid last seen +419 s at RSS 970 MB. MemAvailable during 716-844 MB,
  AP 60-67 C, thermal_status 2 at the end. logcat: at 14:14:36-14:14:51 (the first 15 s of the run) lmkd killed ~dozens of cached apps
  ('Reclaim ... reason: low watermark is breached and swap is low (5418816kB < 4613732kB)', later 'min2x watermark is breached even after kill');
  then 14:21:39.683 `lmkd: Reclaim 'com.davide.seddio.contentspike' (5556), uid 10596, oom_score_adj 850, state 18 to free 1133260kB rss, 627348kB swap;
  reason: low watermark is breached and swap is low (5154060kB < 4613732kB)` and 14:21:41.078 `Zygote: Process 5556 exited due to signal 9 (Killed)`.
  Open: oom_score_adj 850 is a cached-app value, but the harness holds FLAG_KEEP_SCREEN_ON; screen/focus state at the kill was not recorded (the sampler does not
  log it) -> to add to the driver for the fp32 runs. The `.json/.done/_img/_parity` pulls of this run are 'cat: ... No such file' text (driver writes the error
  into the file); phone_report.py now reads runs from `*.runmeta.json` and tolerates that.
- Agreement with the desktop fp32 reference (phone_report.py, aggregates): dyn8 b70 parity inputs cos mean/min 0.9639/0.9227 (N 50), top-8 mean/min 0.807/0.625;
  MediaStore pipeline cos 0.9486/0.8740 (N 60), top-8 0.646/0.250; query embeddings cos 0.9935/0.9801 (N 24).
  nbits4 b70 parity cos 0.9786/0.9607, top-8 0.880/0.625; MediaStore cos 0.9649/0.8198, top-8 0.661/0.250; queries 0.9905/0.9784.
- phone_report.py rewritten (runmeta-driven, tolerant of killed runs, md + csv + json, lmkd lines).

## 2026-11-16 15:25 - desktop control for the quantised runs, and the phone dropped off adb

- Desktop (no phone): `run_desktop_variant.py gemma dyn8 70 60 4` then `... nbits4 70 60 4` (same 60 photos, PIL preprocess, desktop onnxruntime, 4 threads;
  median image 1.41 s dyn8, 3.16 s nbits4) and the new `scripts/desktop_vs_device.py <variant> 70`. N = 60 photos, 24 queries, aggregates only.
  | b70 | cos(desktop, fp32 ref) mean/min | cos(phone, desktop) mean/min | cos(phone, fp32 ref) mean/min | top-8 desktop vs ref | top-8 phone vs desktop | top-8 phone vs ref |
  |---|---|---|---|---|---|---|
  | dyn8 | 0.9578 / 0.8957 | 0.9643 / 0.8911 | 0.9486 / 0.8740 | 0.729 / 0.375 | 0.682 / 0.375 | 0.646 / 0.250 |
  | nbits4 | 0.9768 / 0.9404 | 0.9790 / 0.8455 | 0.9649 / 0.8198 | 0.755 / 0.500 | 0.719 / 0.375 | 0.662 / 0.250 |
  Query embeddings phone vs desktop same variant: dyn8 0.9986 mean (min 0.9935), nbits4 0.9996 (min 0.9992). Reading: most of the disagreement with the fp32 reference is
  the quantisation itself (it reproduces on the desktop with a different runtime and preprocessor); the phone path (Android decode/resize + ARM onnxruntime) adds a further
  ~0.01-0.015 of mean cosine. top-8 over only 60 images is coarse (each query has 8 of 60 slots; 0.125 = one swap).
- **14:47:25 the phone stopped answering adb.** The driver's `adb shell dumpsys thermalservice` (inside its cooling loop, i.e. BEFORE the nbits4-b280 launch, no load on the phone)
  hung from 14:47:25 until I looked at 15:14. `adb devices` still said `device`, but `adb shell echo hi`, `cat /proc/uptime`, `true` all timed out (20 s),
  `get-state` said `device`. `adb -s RFCT703FBKR reconnect` -> device 'not found'; `adb kill-server; start-server` -> `(no serial number) offline transport_id:1`, still offline
  after 3 more minutes of polling (15:21). So adbd or the USB link is stuck; this needs a physical re-plug / USB-debugging re-authorisation. The matrix driver (pid 46078) is still alive and now
  loops its cooling step with empty readings (matrix_a.out: `cooling: ap_c= bat_c= ...`, adb: device 'RFCT703FBKR' not found).
  gemma-nbits4-b280 has NOT run.

## 2026-11-16 20:10 - continuation run (phase 1 part 3): state found; nbits4 b280 DID finish on the phone

- Verified (host clock 20:09): matrix driver pid 46078 is gone, `matrix_a.out` ends `MATRIX_DONE`. The phone is back on adb (`adb devices -l` -> `RFCT703FBKR device product:r9qxxx model:SM_G990B`,
  `shell echo ok` ok). Phone: uptime 7 d 10 h (no reboot), battery 100 %, USB powered, `low_power` 0, thermal status 0, MemTotal 5,493,784 kB, MemAvailable 1,509,028 kB, SwapFree 6,070,748 of 8,388,604 kB,
  /data 88 GB free. Harness package installed, `files/` = 431 MB (models/gemma left over, inputs, results).
- The drop: the logcat shows the USB cable was off 15:46:47-15:57:09 (maintainer re-plug); the matrix driver resumed at ~15:57 and ran gemma-nbits4-b280 (cooling loop had empty readings 15:16-15:42).
- **gemma-nbits4-b280 timed out in the driver (3,601 s, `died=2`) but the harness kept running and FINISHED at 17:44 on the phone (status ok); the driver had already pulled partial files.** I pulled the real ones
  (`adb exec-out run-as ... cat files/results/gemma-nbits4-b280*`); the timeout-time pulls are in `results/device/partial_timeout_pull/`. Process 12976 was still alive afterwards (idle).
  Result (60 photos + 50 parity + 24 queries, 4 threads): load 2,180 ms; queries 39/48 ms; parity 50 inputs median/p95 59,818/63,447 ms (first one 7,483 ms, the rest 51-65 s); MediaStore photos total median/p95 59,262/62,061 ms
  = decode+pre 210/713 + vision 53,719/55,269 + LM 5,230/6,347; image wall 3,569 s; peak PSS (sampled) 1,613,822 kB, VmHWM 1,652,652 kB; no kill; MemAvailable 680-1,020 MB during, VmSwap up to 818 MB.
  Compare nbits4 b70: vision 1.1 s, VmHWM 544 MB. A 4x token budget gives a ~50x vision time and 3x peak memory: this is NOT the compute of the model.
- **Cause candidate found on the leftover process 12976: `oom_score_adj` 850, `/proc/<pid>/cgroup` `cpuset:/background`, `Cpus_allowed_list: 0-2`; `topResumedActivity` is
  `com.android.settings/.Settings$UsbDetailsActivity` (the USB-mode dialog the re-plug opened).** A background app is confined to 3 little cores, which explains both the ~50x slowdown after the first
  parity input (the first one took 7.5 s) and the earlier dyn8 b280 kill at oom_score_adj 850 (cached/background class, killed first by lmkd). The harness holds FLAG_KEEP_SCREEN_ON, which does not help when another
  activity is on top. So the nbits4 b280 numbers above are CONTAMINATED by losing the foreground (to be re-run once the driver can guarantee/record focus). Not yet proven for the dyn8 b280 kill (no focus logging then).

## 2026-11-16 20:30 - driver now logs foreground/screen state; smoke run confirms the foreground hypothesis

- `run_phone_variant.sh` changes (all in scripts/, nothing else): bounded adb for state reads (`timeout 45`), `fs()` = `focus=<window> wake=<mWakefulness> display=<Display State>` in START/PRE/POST and in every
  sample (columns added to samples.csv: oom_score_adj, cpuset, cpus_allowed, focus, wake, display; sample period is now ~9 s because of the dumpsys calls), a count of samples whose focus is not the harness
  and up to 3 `KEYCODE_BACK` presses to dismiss an intruding activity (logged as `FOCUS NOT HARNESS`), runmeta gets samples / samples_focus_not_harness / back_presses, `TAG_SUFFIX` (e.g. `-fg`)
  so a re-run never overwrites a result, SigLIP queries + parity pixels pushed once (marker `.markers/siglip_inputs`), timeout no longer claims a run failed (the harness is left running, see 20:10 entry).
  New `scripts/run_phone_queue.sh` (sequential runs, stops if adb stops answering), `scripts/note.sh`, `scripts/show_run.py`.
- Mistake made and repaired: my first smoke call used the real tag `gemma-nbits4-b70` (the script only appended 2 lines to its run.log before I killed it; I removed them; json/f32 files untouched, mtime 14:39).
- Smoke `TAG_SUFFIX=-smoke bash scripts/run_phone_variant.sh gemma nbits4 70 3 4 1200` (3 photos): ok, wall 67 s. PRE state (screen woken, before launch) already showed `focus=com.android.settings/...UsbDetailsActivity`
  (the USB dialog from the re-plug is still on the phone); after `am start` the harness is `focus=com.davide.seddio.contentspike/.MainActivity`, `oom_score_adj 0`, `cpuset /top-app`, `Cpus_allowed_list 0-7`,
  all 7 samples. So the harness only loses CPUs/priority when something else takes the focus; the 8-core top-app set is what b70 ran with.
- SigLIP inputs already existed (made by make_reference_siglip.py 2026-11-16 13:58-14:05): `raw/inputs/siglip_queries.json` (24 x 64 ids, lower-cased, padded) and `raw/inputs/siglip_pixels/*.bin` (50 files of 602,112 B = 3x224x224 f32;
  every 6th manifest photo, desktop HF preprocessing). The harness has a SiglipPipeline with a parity mode and its own on-device preprocessing (ImageDecoder target 224x224, (x/255-0.5)/0.5); nothing to write.
- Quantised SigLIP on the desktop: `quantise.py siglip dyn8` -> EXIT 0 (vision.onnx 95.8 MB, text.onnx 86.4 MB; table fp32 786 MB copied unchanged); `quantise.py siglip nbits4` running.

## 2026-11-16 20:35 - continuation run (phase 1 part 4): state verified; corrections

- Verified: phone on adb (`RFCT703FBKR device`), echo ok, battery 100 % USB, temperature 39.6 C (battery), MemAvailable 760 MB / SwapFree 5.9 GB while the queue's first run is in progress; focus = harness MainActivity, wake Awake.
  Queue pid 88790 -> run_phone_variant.sh pid 88797 alive; gemma-nbits4-b280-fg started 20:27:33 (PRE focus was the USB dialog `UsbDetailsActivity`, then `am start` took focus).
- SigLIP quantised models on the host: `quantise_siglip_nbits4.log` ends `EXIT=0`; models/siglip/nbits4 = vision.onnx 63,131,667 B, text.onnx 56,239,375 B, embed table 786,432,000 B (fp32, unchanged). dyn8: vision 95,776,642 B, text 86,421,182 B.
- CORRECTION to the 20:30 entry: "SigLIP queries + parity pixels pushed once" describes what the driver now does, NOT something that had happened: at 20:30 nothing SigLIP was on the phone (no `.markers/siglip_inputs`, no files/models/siglip). It is pushed by the first SigLIP run.
- RESOLVED discrepancy on gemma-nbits4-b70 peak memory (read from results/device/gemma-nbits4-b70.json and .runmeta.json): `VmHWM` (kernel high-water RSS, /proc/<pid>/status at run end) = 556,896 kB = 543.9 MiB;
  the harness's sampled PSS peak (`peak_pss_kB_sampled`, Debug.MemoryInfo totalPss, 40 ms) = 502,601 kB = 490.8 MiB. The 14:45 entry's "544 MB" and "491 MB" are those two counters in MiB; the raw
  "544,004 kB / 495,351 kB" of the 13th-16th smoke entry belong to the earlier 5-photo smoke run (the later 3-photo smoke has VmHWM 566,576 kB, PSS peak 509,839 kB), not to the 60-photo run.
  From now on peak memory is quoted in kB with the counter named; earlier "MB" are MiB. (An earlier line of this entry blaming the smoke run for the 544 was wrong and is replaced.)

## 2026-11-16 20:58 - gemma-nbits4-b280-fg finished (foreground guaranteed)

Queue run 1 of 2 (`run_phone_variant.sh gemma nbits4 280 60 4 7200`, TAG_SUFFIX -fg), status ok, wall 1,206 s (host driver), image loop 721 s on the phone. Every one of the 141 samples: focus = harness MainActivity,
oom_score_adj 0, cpuset /top-app, Cpus_allowed_list 0-7, wake Awake, display ON; samples_focus_not_harness 0, BACK presses 0 (the PRE state, before launch, still showed the USB dialog; `am start` took the focus).
- Load 2,066 ms (vision session 944 + LM session 1,070 + table/special 5). 24 queries median/p95 38/46 ms (N 24).
- Per photo (N 60, ms, median/p95): total 12,075/12,516 = decode+preprocess 108/447 + vision 11,158/11,602 + LM 759/816. 50 parity inputs (N 50) 9,007/12,267 ms (first 7,558; slowed over the run).
- Peak memory: harness sampled PSS peak 1,664,243 kB; `VmHWM` (kernel, harness end) 1,685,636 kB (host sampler max VmHWM equal); max sampled VmRSS 1,680,864 kB; RSS after load 481,200 kB (VmHWM then 552,116 kB);
  PSS after parity loop 1,656,047 kB (memory is allocated in the parity/image phase, not at load). Max VmSwap 174,692 kB. MemAvailable before 1,779,592 kB, minimum during 728,772 kB; SwapFree before 6,273,384 kB.
- Thermal: AP 33.8 C before -> 51.7 C at the POST reading (max sampled 65.9 C); thermal_status **0 -> 3** (env_end battery 44.2 C, battery level 100 -> 96 while on USB). Status 3 means the platform asked for throttling; part of the 10 s vision time may be throttling, not measured separately.
- No kill of the harness (lmkd killed cached apps at the start: 'Reclaim ... reason: min2x watermark is breached', e.g. at 20:27:50).
- vs the CONTAMINATED run (20:10 entry: harness in background, cpuset /background, 3 cores): vision 53,719/55,269 ms then -> 11,158/11,602 ms now (4.8x faster), decode+pre 210/713 -> 108/447, LM 5,230/6,347 -> 759/816;
  peak PSS 1,613,822 -> 1,664,243 kB and VmHWM 1,652,652 -> 1,685,636 kB (the same within 2 %). So the ~50x vision slowdown vs b70 (1.1 s) was largely the background/CPU-set artefact (now ~10x), and the memory
  footprint at b280 (about 1.7 GB resident, ~3x b70) is NOT an artefact: it reproduces in the foreground.

## 2026-11-16 21:00 - agreement of gemma-nbits4-b280-fg with the fp32 desktop reference (phone_report.py; raw/ref/gemma_fp32_b280.npy)

N = 50 parity inputs (desktop-preprocessed tensors), 60 MediaStore photos (phone decode/resize), 24 queries; top-8 over those same images (coarse: 0.125 = one swap). Aggregates only.
| run | query cos mean/min | parity cos mean/min | parity top-8 mean/min | MediaStore cos mean/min | MediaStore top-8 mean/min |
|---|---|---|---|---|---|
| nbits4 b280 -fg | 0.9905 / 0.9784 | 0.9822 / 0.9727 | 0.901 / 0.750 | 0.9798 / 0.9522 | 0.786 / 0.500 |
| nbits4 b280 contaminated (20:10) | identical to 4 decimals (same model, same inputs: being in the background changed time, not values) | | | | |
| nbits4 b70 (for comparison) | 0.9905 / 0.9784 | 0.9786 / 0.9607 | 0.880 / 0.625 | 0.9649 / 0.8198 | 0.661 / 0.250 |
Note: the b280 desktop fp32 reference is a different reference from b70 (b280_ref vs b70_ref), so rows compare each budget with its own fp32 model; they do not show that b280 is a better retrieval model.
`scripts/phone_report.py` now keeps `-fg`/`-smoke` tags, a `note` column (foreground all N samples / focus not recorded / CONTAMINATED / partial_timeout), a `kills` column, SigLIP rows (budget 224) and the regex budget parse.
`scripts/desktop_vs_device.py <variant> [budget] [kind] [suffix]` now also takes siglip (e.g. `desktop_vs_device.py dyn8 224 siglip`); desktop SigLIP dyn8/nbits4 embeddings for the 60 photos already exist in results/emb.

## 2026-11-16 21:27 - gemma-dyn8-b280-fg finished: NO kill in the foreground; queue done

Queue run 2 of 2 (`gemma dyn8 280 60 -fg`), status ok, wall 1,162 s, image loop 693 s. All 137 samples: focus = harness, oom_score_adj 0, cpuset /top-app, cpus 0-7, Awake/ON; samples_focus_not_harness 0, BACK presses 0.
(PRE state again showed the USB dialog before launch; `am start` took the focus.) lmkd killed cached apps at the start; nothing for the harness.
- Load 2,509 ms (vision 1,075 + LM 1,380 + table 3). 24 queries 36/46 ms (N 24). Photos (N 60, ms median/p95): total 10,969/19,417 = decode+pre 108/447 + vision 10,157/18,002 + LM 652/1,244 (the p95 is 2x the nbits4 one: a throttled stretch, see thermal).
  50 parity inputs 8,514/11,453 ms (N 50). (decode+pre median/p95 108/447 is identical to the nbits4 -fg run by coincidence: per-photo arrays differ.)
- Peak memory: sampled PSS peak 1,745,023 kB; `VmHWM` (kernel, harness end) 1,749,852 kB (host sampler max equal); max sampled VmRSS 1,748,056 kB; RSS after load 625,760 kB (VmHWM then 671,092 kB); max VmSwap 502,380 kB.
  MemAvailable before 1,897,796 kB, minimum during 688,608 kB; SwapFree before 6,147,632 kB.
- Thermal: AP 33.8 C before -> 53.9 C at POST (max sampled 66.8 C); thermal_status 0 -> 3; battery 45.4 C, level 100 -> 94 on USB.
- Agreement with raw/ref/gemma_fp32_b280.npy: queries cos mean/min 0.9935/0.9801 (N 24); parity (N 50) cos 0.9679/0.9349, top-8 0.792/0.500; MediaStore photos (N 60) cos 0.9600/0.9141, top-8 0.771/0.500.
- FACTS for the budget-280 question (facts only): the 14:21 dyn8-b280 lmkd kill DID NOT reproduce in the foreground (same model, same inputs, same 60 photos): the harness peaked at 1.75 GB VmHWM with MemAvailable never below 689 MB and survived.
  CORRECTION to the maintainer's hypothesis (also in my 20:10 wording): that kill (14:14-14:21) happened BEFORE the cable was re-plugged (15:46-15:57), so the USB-mode dialog cannot have been on top then; what held the focus at 14:14-14:21 is
  UNRECORDED (the driver had no focus logging; the lmkd line says oom_score_adj 850, i.e. the harness was classed cached/background, so it was not the resumed activity). Only the nbits4-b280 background run (after 15:57) is tied to the dialog.
  nbits4-b280-fg gave the same embeddings as its contaminated run (queries and agreement identical to 4 decimals); dyn8's contaminated run has no image/parity file, only queries (identical cos 0.9935/0.9801).
  Memory is NOT an artefact: ~1.65-1.75 GB resident in the foreground. Time is partly an artefact (nbits4 vision 53.7 s -> 11.2 s) but a b280 photo is still ~10 s of vision vs ~1.0-1.1 s at b70, with thermal_status 3 reached at the end of both runs.

## 2026-11-16 21:32 - SigLIP queue started

Command: `nohup bash scripts/run_phone_queue.sh "siglip dyn8 224 60 - 3600" "siglip nbits4 224 60 - 3600" "siglip fp32 224 60 - 3600" > results/queue_c.out` (bash pid 116201). A first call with unquoted args failed at once (`$2: unbound variable`: the queue takes ONE quoted spec per run, "-" = no tag suffix); nothing ran. Tags: siglip-dyn8, siglip-nbits4, siglip-fp32 (no budget in the tag; budget 224 fixed).

## 2026-11-16 21:55 - SigLIP 2 (224) on the phone: dyn8, nbits4, fp32 all ran, no failures

Queue (pid 116201) finished 21:54, QUEUE_DONE, no kill, no focus loss (PRE state showed the USB dialog as before; `am start` took the focus; every sample focus = harness; runs are 25-57 s so only 3-7 host samples: the 40 ms in-process sampler is the peak-memory source).
All: 60 MediaStore photos + 50 parity pixel tensors (every 6th manifest photo, desktop HF preprocessing) + 24 queries, 4 threads, USB powered, battery saver off. MemAvailable before 1,958,404 / 1,985,516 / 2,027,384 kB (dyn8/nbits4/fp32).
| run | load ms (vision+text) | peak PSS sampled kB | VmHWM end kB | photo ms median/p95 (N 60) | = decode+pre | + vision | parity ms med/p95 (N 50) | query ms med/p95 (N 24) | AP pre -> post C, thermal |
|---|---|---|---|---|---|---|---|---|---|
| siglip-dyn8 | 773 (455+259) | 393,486 | 450,620 | 187/334 | 45/190 | 139/149 | 156/180 | 44/53 | 33.8 -> 62.2, 0 -> 0 |
| siglip-nbits4 | 649 (361+226) | 312,603 | 361,560 | 201/351 | 42/191 | 159/166 | 176/186 | 50/57 | 33.8 -> 49.2, 0 -> 0 |
| siglip-fp32 | 1,805 (819+930) | 875,134 | 924,252 | 441/610 | 50/218 | 389/412 | 400/413 | 119/130 | 33.8 -> 65.2, 0 -> 0 |
Agreement with raw/ref/siglip_fp32_{images,queries}.npy (rows by manifest order; phone_report.py):
| run | query cos mean/min (N 24) | parity cos mean/min (N 50) | parity top-8 mean/min | MediaStore cos mean/min (N 60) | MediaStore top-8 mean/min |
|---|---|---|---|---|---|
| dyn8 | 0.9404 / 0.8188 | 0.7793 / 0.5921 | 0.542 / 0.125 | 0.7998 / 0.5859 | 0.406 / 0.125 |
| nbits4 | 0.9951 / 0.9706 | 0.9648 / 0.9309 | 0.854 / 0.750 | 0.9508 / 0.8869 | 0.703 / 0.500 |
| fp32 | 1.0000 / 1.0000 | 1.0000 / 1.0000 | 1.000 / 1.000 | 0.9730 / 0.8997 | 0.734 / 0.375 |
Desktop control (`desktop_vs_device.py <variant> 224 siglip`; same 60 photos, desktop PIL/HF preprocessing, desktop onnxruntime; results/desktop_vs_device_siglip-*.json), N 60:
| variant | cos(desktop, fp32 ref) mean/min | cos(phone, desktop) mean/min | cos(phone, fp32 ref) mean/min | top-8 desktop vs ref | top-8 phone vs desktop | top-8 phone vs ref | query cos phone vs desktop |
|---|---|---|---|---|---|---|---|
| dyn8 | 0.8082 / 0.6010 | 0.9699 / 0.9293 | 0.7998 / 0.5859 | 0.406 / 0.125 | 0.802 / 0.625 | 0.406 / 0.125 | 0.9996 / 0.9993 |
| nbits4 | 0.9667 / 0.9384 | 0.9714 / 0.8923 | 0.9508 / 0.8869 | 0.750 / 0.500 | 0.760 / 0.500 | 0.703 / 0.500 | 0.9996 / 0.9965 |
| fp32 | 1 (it is the reference) | 0.9730 / 0.8997 | 0.9730 / 0.8997 | 1 | 0.734 / 0.375 | 0.734 / 0.375 | 1.0000 |
Readings (facts only): (1) SigLIP dyn8 (dynamic int8) loses most of the agreement on the DESKTOP too (image cos 0.81, query cos 0.94 vs fp32): quantisation, not the phone. (2) fp32 on the phone with desktop-preprocessed tensors is identical to the reference (cos 1.0000, N 50),
so the ARM onnxruntime path is exact and the 0.973 / 0.90 (N 60) of fp32 on MediaStore photos is purely the Android decode+resize path (ImageDecoder to 224x224 vs HF/PIL resize): that cost is as large as nbits4 quantisation (desktop 0.967). (3) All three models fit easily: highest VmHWM 924,252 kB (fp32).
SigLIP at 224 is ~3-5x cheaper per photo than Gemma b70 (140-390 ms vision vs ~1,000-1,100 ms) and has no LM stage.

## 2026-11-16 22:00 - task 4 started: gemma fp32 b70 on the phone

quality_for_maintainer.md written (results/, gitignored, 7 models, ids = MediaStore _ID; scripts/quality_listing.py). Started `run_phone_queue.sh "gemma fp32 70 60 - 3600"` (queue_d.out, bash pid 121573). Expect ~1.7 GB resident vs ~1.9-2.0 GB MemAvailable before; a kill is a valid result.

## 2026-11-17 05:16 - gemma-fp32-b70 on the phone: ran, NO kill, adb still answers

(The host clock jumped from 21:56 on 11-16 to 05:09 on 11-17 between the previous note and the start of this run, apparently a host suspend; the heading of the 22:00 entry above is my guess, the queue really started at 05:09:00 host time.
 The phone was not affected: uptime 674,456 s, i.e. no reboot.)
Command `run_phone_queue.sh "gemma fp32 70 60 - 3600"`, status ok, wall 274 s, died 0, kills 0 (no lmkd line for the harness), BACK presses 0, all 29 samples focus = harness, oom_score_adj 0, cpuset /top-app, cpus 0-7.
PRE state: MemAvailable 1,922,720 kB, SwapFree 6,412,240 kB, AP 31.3 C, thermal 0, battery saver off, USB powered, 100 %, focus = USB dialog before `am start`, then the harness.
- Models on the phone: vision.onnx 671,873,223 B, lm.onnx 548,297,260 B, embed table 536,870,912 B. Load 4,124 ms (vision 1,623 + LM 2,396 + table 11); 24 queries 66/80 ms (N 24).
- Photos (N 60, ms median/p95): total 2,375/2,615 = decode+pre 63/335 + vision 2,033/2,239 + LM 265/296. 50 parity inputs 2,341/2,554 ms (N 50). Image loop 145 s.
- Peak memory: sampled PSS peak 1,407,193 kB; `VmHWM` 1,444,168 kB (host sampler max equal); RSS right after load 1,386,664 kB (VmHWM then equal; unlike the quantised runs it is all allocated at load: fp32 weights, RssAnon 1,307,432 kB); max VmSwap 27,848 kB.
  MemAvailable minimum during the run 764,200 kB (before 1,922,720 kB). This is 3.2x the nbits4 b70 VmHWM (556,896 kB) and 2.3x dyn8 b70 (643,584 kB host max).
- Thermal: AP 31.3 -> 66.3 C at POST (max sampled 66.3), thermal_status 0 -> 1; battery 37.3 C.
- Agreement with raw/ref/gemma_fp32_b70.npy: queries 1.0000/1.0000; parity (N 50) cos 1.0000/1.0000, top-8 1.000/1.000 (the ARM fp32 path reproduces the reference); MediaStore (N 60) cos 0.9811/0.8698, top-8 0.724/0.500
  -> the Android decode/resize path alone costs cos 0.981 mean (min 0.870) at b70, i.e. it is the fp32 "phone path cost" for the b70 desktop-control table (nbits4 phone 0.9649, dyn8 0.9486).
- fp32 b280 started next (queue_e.out): if b70 survived, b280 is the case closest to the memory limit.


# Summary


## Results on the SM-G990B (5.5 GB RAM)

60 photos, 4 threads, phone charging and in the foreground:

| Model and variant | Vision time per photo | Peak memory | Agreement with float32 (embeddings, on-phone path) | Result |
|---|---|---|---|---|
| EmbeddingGemma 2, 4-bit, budget 70 | 1.1 s | 557 MB | 0.965 | ok |
| EmbeddingGemma 2, 8-bit, budget 70 | 1.0 s | 644 MB | 0.949 | ok |
| EmbeddingGemma 2, float32, budget 70 | 2.0 s | 1.44 GB | 0.981 | ok |
| EmbeddingGemma 2, 4-bit, budget 280 | 11.2 s | 1.69 GB | 0.980 | ok |
| EmbeddingGemma 2, 8-bit, budget 280 | 10.2 s | 1.75 GB | 0.960 | ok |
| EmbeddingGemma 2, float32, budget 280 | | about 2.4 GB | | **killed after 54 s** |
| SigLIP 2, 4-bit | 0.16 s | 362 MB | 0.951 | ok |
| SigLIP 2, 8-bit | 0.14 s | 451 MB | 0.800 | ok |
| SigLIP 2, float32 | 0.39 s | 924 MB | 0.973 | ok |

- **EmbeddingGemma 2 at budget 280:** it fits in the foreground except as float32. It is about 10x slower, and the phone reached thermal status 3, which means it was throttling. At that speed, about 5,300 photos would take roughly 15 hours. The earlier kill and slowdown at 280 were partly a foreground artefact: the USB-mode dialog from your re-plug put the harness in the background. The first 8-bit kill happened before that re-plug, and what held the foreground then wasn't recorded.
- **SigLIP 2:** it uses much less memory and time than EmbeddingGemma 2 on the phone. Its 8-bit variant loses most of its accuracy even on the desktop, while 4-bit holds up. Float32 on the phone is bit-for-bit the reference, so the phone's maths is exact.
- **The phone path:** decoding and resizing photos on Android costs about 0.97–0.98 agreement by itself, as much as 4-bit quantisation.
- **Quantised EmbeddingGemma 2 agreement:** the 8-bit and 4-bit EmbeddingGemma 2 numbers at budget 70 are in the table above. Agreement with float32 is not retrieval quality, so judge it from the quality file.
- **Rough indexing time:** from the medians, 5,300 photos would take about 1.9 hours for 4-bit EmbeddingGemma 2 at budget 70, and about 18 minutes for 4-bit SigLIP 2. That is my arithmetic, not a measured run.

## A caveat on the speed figures

The real indexing job runs in the background under WorkManager. The one background run (4-bit, budget 280) was about 5x slower, confined to 3 little cores. Nobody has measured budget 70 or SigLIP 2 in the background. If indexing really runs that way, every time above gets longer.

## What I need from you

The retrieval-quality file is at `quality_for_maintainer.md`. It lists the top 8 photos for each of 24 queries (English, Italian, German, Spanish) for seven model variants, using MediaStore ids. Please open some results and judge relevance, especially the Italian ones. That matters more than the agreement figures and decides which model goes forward.

## What's left

- The notes don't yet record the float32 budget-280 kill, so the Coder should add it and regenerate the summary table.
- The measurement app is still installed on the phone and can be removed.
- The Planner records the results in the Stage 4 plan once you've judged the quality, which closes step 4.0.

Nothing is committed.

## 2026-11-17 05:22 - gemma-fp32-b280 on the phone: KILLED by lmkd after about 53 s

- Run gemma-fp32-b280 (EmbeddingGemma 2, float32, budget 280, 4 threads, limit 60 s), foreground guaranteed, started 05:21:30 after a cooling wait. Source files: `results/device/gemma-fp32-b280.runmeta.json`, `.run.log`, `.logcat.txt`.
- Driver log: `PROCESS GONE without .done after 53s`; runmeta: died=1, wall_s=54. No result json was written.
- Memory: external max VmHWM 2,408,764 kB. MemAvailable before the run 1,922,584 kB (PRE line); after the kill 2,388,120 kB (POST line).
- Foreground: focus was the harness in every sample (samples_focus_not_harness 0 of 6 samples, back_presses 0), so this kill is NOT a foreground artefact, unlike the earlier budget-280 kills.
- Kill reason, from logcat (lmkd, 05:22:19.253): `Reclaim 'com.davide.seddio.contentspike' (28538), uid 10596, oom_score_adj 0, state 2 to free 1426924kB rss, 1103816kB swap; reason: min2x watermark is breached even after kill`. The harness was at oom_score_adj 0, so lmkd killed the foreground app itself; ahead of it lmkd had already reclaimed cached processes.
- Thermal: thermal_status 0 before and after; ap_c 33.9 before, 65.4 after (read right after the kill).
- Conclusion: EmbeddingGemma 2 float32 at budget 280 does not fit on this 5.5 GB phone even in the foreground. 4-bit and 8-bit at 280 do (1.69 GB and 1.75 GB, see the table in the summary above); float32 at budget 70 ran (1.44 GB).
- `results/phone_summary.md` regenerated with scripts/phone_report.py; it now includes this kill.

## 2026-11-17 - Spike closed

Spike closed 2026-11-17: content albums paused by the maintainer; harness uninstalled from the phone; folder to be removed in a later commit (git history keeps it).
