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
