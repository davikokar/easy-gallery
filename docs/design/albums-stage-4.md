# Albums Stage 4 — content albums and rule editing

**Paused — 2026-11-17.** Content albums were paused by the maintainer after step 4.0, the
feasibility spike ([albums.md](albums.md) §8 decision 38; the figures are under *Spike outcome*
below). This plan is paused, not archived — the stage never started and never closed — and is kept
as the plan to resume from; the decisions it records stay valid unless a later decision says
otherwise.

> **Owner: Planner. Mode: routed.** This is a *stage plan*, not a description of the app. It exists
> for the length of Stage 4 and is archived when the stage closes — what archiving means, and the
> rest of the rules for this kind of document, are in
> [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md).
> The epic itself is [albums.md](albums.md); **its §7 table is the single source of truth for where
> the epic stands**, and this document never restates it.
>
> ~~**Stage 4 runs before Stage 3.** [albums.md](albums.md) §8 decision 13, as amended 2026-11-13,
> says why; §7 says why the numbers were kept.~~ **Amended 2026-11-17:** Stage 3 is next and Stage 4
> is paused ([albums.md](albums.md) §8 decisions 13, amended 2026-11-17, and 38).
>
> **This is a draft.** Written 2026-11-13. Already decided: [albums.md](albums.md) §8 decisions
> 33–37, and the maintainer's 2026-11-13 approvals of spike-only dependencies and of downloading
> about 3 GB of model weights (under *Next session starts here*). The steps, the exit criteria and
> every recommendation below are proposals until the maintainer answers the open questions and step
> 4.1 records the decisions. Where something was not known, it is a question, not a guess.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff, as in Stage 2.

- **2026-11-17 — paused by the maintainer after step 4.0** ([albums.md](albums.md) §8 decision 38).
  Step 4.0 is ✅ done; its figures are under *Spike outcome* below. Steps 4.1 to 4.8 are paused and
  were never started. Rule editing, step 4.2, leaves this plan: it continues with Stage 3 or
  standalone, taking group D with it (decision 39). Stage 3 is next and has no plan yet (decision
  13, amended 2026-11-17). **Amended 2026-11-17, later the same day:** rule editing has its own
  plan, [albums-rule-editing.md](albums-rule-editing.md), which now holds step 4.2's specification
  and group D's open questions (decision 39, amended 2026-11-17). Stage 3 has a draft plan,
  [albums-stage-3.md](albums-stage-3.md).
- **The spike's code was removed from the tree on 2026-11-17** and is retrievable from git history:
  see `git log -- tools/content-model-spike`. Every `tools/content-model-spike/` path in this plan
  now points into that history.
- **On resume, read first:**
  1. *Spike outcome* below.
  2. The resume triggers in [albums.md](albums.md) §8 decision 38, and which of them fired: the
     ML Kit Embeddings API, or the maintainer accepting a bundled model of about 0.39 GB.
  3. Decision 37: "the model ships inside the app" needs revisiting if ML Kit is the trigger, and
     with it A3, A3a and F1 below.
  4. Then this block's older entries, and group B onward, which were never discussed.
- **Still open, unchanged by the pause:** A1a, A3a, A4a, B1–B6, C1–C6, E1, E2, F1; D1 and D3–D5
  ~~go with rule editing (decision 39)~~ moved to [albums-rule-editing.md](albums-rule-editing.md)
  on 2026-11-17.
- **Superseded 2026-11-17, kept as the record of how the spike ran:** ~~**Step 4.0, the
  feasibility spike, is in progress — started 2026-11-13.**~~ Group A was answered on
  2026-11-13 (see *Open questions*) and its follow-ups were answered the same day, except the three
  still open below.
  - **Where it lives:** `tools/content-model-spike/`, with a Python 3.12 virtual environment inside
    it and a separate Gradle project for the on-device measurements, both outside the app build
    (A6, A6a). The `.gitignore` entries for the spike are added by the same change that creates the
    folder.
  - **Device:** the attached SM-G990B, serial RFCT703FBKR (A6b). The maintainer will load more
    items onto it before the measurements.
  - **What it returns:** numbers and failures, not a recommendation.
  - **Still open:**
    - **A1a:** read the `apple-amlr` licence, only if MobileCLIP is to be measured;
    - **A3a:** Play's size limits, to be checked at 4.1 before the model is chosen;
    - **A4a:** the hidden-device threshold, which the spike's memory and speed figures should set.

  The next action after the spike is a discussion of group B once its figures exist. Groups B to F
  are still unanswered.
- **2026-11-13 — rebased onto main.** `feat/album-content` now sits on main's §8 decisions 28
  (per-album display overrides, ADR-0021) and 29 (no Incomplete-album or coverage notice on an album
  or on the New automatic album screen; the coverage figures are Settings rows only). C5, B6 and E1
  were restated against that and stay questions; the exit criterion that said *Incomplete*, step
  4.6's coverage bullet, A4b's notice, D2 and the binding list were corrected, and no decision was
  taken.
- **2026-11-14 — rebased again onto main (`6ef6f54`).** The branch's decisions are now
  [albums.md](albums.md) §8 decisions 33–37, and the references to them here and in albums.md were
  updated; main's 30–32 kept their numbers. What main's new work changes for Stage 4, with no
  decision taken:
  - **Album info (decision 31).** Album Detail View now has a read-only Album info dialog, built
    from `AlbumRuleSummary` (`data/AlbumRuleSummary.kt`) by `AlbumInfoDialog`. The rule editor's
    entry point (D5) will sit beside it. `summarizeAlbumRule` picks region and time clauses out by
    type, so a content clause would be dropped without a compile error; step 4.6 now lists the
    content case.
  - **Filter media in Albums View (decision 30).** It hides an album it empties, if the album had
    items without it. It tests only resolved counts, ~~so an album hidden on a weaker device (A4b)
    is listed or hidden like any other.~~ ~~**Amended 2026-11-16 (A4b):** a content-only album hidden
    on a weaker device is absent everywhere on that device, Filter media's test included; a mixed
    album shows its wider set.~~ **Reversed 2026-11-16 (A4b; decision 37):** below the threshold an
    album with a content criterion is unevaluatable and listed, and Filter media tests its
    additions minus exclusions like any other album's. For content albums it makes B3's video
    choice visible; asked there.
  - **Change album thumbnail (decision 32, ADR-0015 Amendment 1).** A cover picked from rule
    matches is stored as an addition row, so an edit's treatment of additions reaches the cover;
    stated in D2. **Answered 2026-11-15 (D2):** an edit changes only the rule, and the cover follows
    membership, so a pinned cover stays through an edit; decision 32's accepted cost is unchanged.
  - **Settings is now the fourth item of the Main view bar.** B6 and C5 name only Settings' Media
    index area, never a path to it, so neither changed.
- **2026-11-15 — four maintainer answers recorded:** Album info stays a simple list (decision 31);
  A4b's existing-album case (decision 37; reversed 2026-11-16, below); D2, an edit changes only the
  rule (decision 36); and the
  cover follows membership (decision 32). Still open: A1a, A3a, A4a, B1–B6, C1–C6, D1, D3–D5, E1,
  E2, F1.
- **2026-11-16 — the four questions the 2026-11-15 recording raised were answered and recorded:**
  the cover fallback wording (decision 36; D2); ~~exclusions made on a wider view, accepted (decision
  37, consequence 1); a hidden content-only album is absent everywhere (decision 37; A4b); and a
  hidden album's manual additions are unreachable, confirmed (decision 37, consequence 2)~~ — the
  last three superseded by the reversal below. Still
  open: A1a, A3a, A4a, B1–B6, C1–C6, D1, D3–D5, E1, E2, F1. To be confirmed at 4.1: A4b's Tier B
  skip~~, and the Planner's reading of "everywhere"~~.
- **2026-11-16 — the hidden-album rule was reversed by the maintainer** ([albums.md](albums.md) §8
  decision 37, *reversed*): hidden albums were causing too many problems. Below the threshold, an
  album with a content criterion is now **unevaluatable** (ADR-0017's existing state), listed
  everywhere, shows its additions minus its exclusions, and carries one device notice, new in every
  shipped locale. Deleting such albums was proposed and rejected on the Planner's advice. This
  closes the three questions the previous recording raised in its report and never recorded here —
  the name clash with an invisible album, whether a cover or an addition chosen on a mixed album's
  wider view is accepted, and single-photo album views — and the Planner's reading of
  "everywhere". Also fixed: D2's moot leaning, and decision 37's attribution of "not offered". Still
  open, as before: A1a, A3a, A4a, B1–B6, C1–C6, D1, D3–D5, E1, E2, F1. To be confirmed at 4.1:
  A4b's Tier B skip, and whether ADR-0017 needs an amendment naming the device as a second cause of
  unevaluatable.
- ~~**Spike state, rechecked 2026-11-14 from `tools/content-model-spike/`; nothing new since
  2026-11-13:** EmbeddingGemma 2 split and exported as fp32, dyn8 and nbits4; SigLIP 2 exported as
  fp32, with no SigLIP 2 reference embeddings in `raw/ref/`; the desktop fp32 reference embeddings
  for EmbeddingGemma 2 at vision budgets 70 and 280 over the 300-photo sample, both in `raw/ref/`,
  finished according to `results/make_ref_gemma.log`; the Android harness under
  `android-harness/`, with a debug APK built 2026-11-13. `results/` holds three logs —
  `verify_gemma_split.log`, `export_siglip.log` and `make_ref_gemma.log` — and nothing else.
  **No phone measurement and no quality result exists yet.** `NOTES.md` ends at the SigLIP 2
  export: it records neither the budget-280 reference run nor the harness, so it is still behind
  the folder; read both.~~ Superseded 2026-11-17 by *Spike outcome* below.
- **Owed from Stage 2:** only the Play Console declaration, before any release that ships Stage 2
  ([albums.md](albums.md) §7, *Stage 2*). The definition of done and the documentation audit were
  discharged on 2026-11-13. A new ML runtime dependency (question A2) will change the merged
  manifest again, so the declaration is cheaper to do once, after A2 is settled.
- **Binding on everything below:**
  - [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md): a rule is
    a conjunction of typed clauses. An unknown clause makes the album unevaluatable, and such a
    rule is never rewritten.
  - [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md): Tier B lives in
    the index database. Any schema change is a version bump, a real migration and a committed
    JSON, with no destructive fallback.
  - [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md): a
    materialised result is intersected with live media before display.
  - [albums.md](albums.md) §6: indexing and evaluation are separate jobs, and the honesty rules
    for staleness apply.
  - §8 decision 22: no app-open index pass.
  - §8 decision 23: no notification and no foreground service.
  - §8 decision 29: an album and the New automatic album screen show no Incomplete-album notice
    and no coverage notice; coverage figures are rows of Settings' Media index area only. What
    that means for content is C5, B6 and E1.
  - §11: no cloud inference.
  - **Spike-only dependencies, approved by the maintainer 2026-11-13:** third-party dependencies
    that live only under `tools/content-model-spike/` — the Python environment and the separate
    Gradle measurement project — and never touch `app/`. This discharges `AGENTS.md`'s
    ask-before-doing item **for the spike only**. **It does not approve adding the runtime to the
    app:** that is still decided at step 4.1 (A2).
  - **Downloads, approved by the maintainer 2026-11-13:** about 3 GB of model weights plus Python
    packages, on the development machine, for the spike.

---

## Spike outcome — 2026-11-17

Step 4.0's result, in aggregates only. Source: `tools/content-model-spike/NOTES.md` and its
`results/`, as they stood on 2026-11-17, now in git history. Phone: the SM-G990B, 5.49 GB RAM.

**App-size cost.** Our ONNX conversions:

| Model | 4-bit | 8-bit | float32 |
|---|---|---|---|
| EmbeddingGemma 2 | 0.78 GB | 0.89 GB | 1.76 GB |
| SigLIP 2 | 0.91 GB | 0.97 GB | 1.50 GB |

A float32 host-side token table dominates them: 0.54 GB for EmbeddingGemma 2, 0.79 GB for SigLIP 2.
Google's official LiteRT build of EmbeddingGemma 2, text and vision, quantisation-aware int4/int8,
is **388 MB** (Hugging Face `litert-community/embeddinggemma-2-text-vision-440m-litert-lm`), so
**about 0.39 GB is the realistic bundled cost**.

**On the phone, in the foreground, 60 photos.** Total time per photo and peak memory (VmHWM):

| Variant | Per photo | Peak memory |
|---|---|---|
| EmbeddingGemma 2, 4-bit, budget 70 | ~1.3 s | 0.56 GB |
| EmbeddingGemma 2, 8-bit, budget 70 | ~1.2 s | 0.64 GB |
| EmbeddingGemma 2, float32, budget 70 | ~2.4 s | 1.44 GB |
| EmbeddingGemma 2, 4-bit and 8-bit, budget 280 | ~11–12 s; thermal_status 3 | ~1.7 GB |
| EmbeddingGemma 2, float32, budget 280 | killed by lmkd | ~2.4 GB |
| SigLIP 2, 4-bit | ~0.20 s | 0.36 GB |

**Agreement with the float32 reference.** Quantised EmbeddingGemma 2 at budget 70: 0.95–0.97.
Android's decode and resize alone cost about 0.97–0.98. SigLIP 2 at 8 bits loses most agreement
even on the desktop (cosine 0.80). Agreement is not retrieval quality.

**Not known.**
- **Background speed.** One background run, confined to cpuset `/background` and three little
  cores, was about 5× slower. Background indexing speed is otherwise unmeasured.
- **Retrieval quality.** The maintainer did not judge the retrieval-quality file, so no model was
  chosen and A4a's threshold was not set.

**What followed:** the maintainer paused content albums; the cost was too high for the
functionality ([albums.md](albums.md) §8 decision 38, with its resume triggers).

---

## What the user gets

On the **New automatic album** screen, next to Place and Date, a **Content** criterion:

- The user types a description, such as "castles".
- The screen shows how many items match, combined with any place and date chosen.
- Create writes the album.

Later, the user can **edit** an automatic album's rule. The Album type dialog does not change.
*Amended 2026-11-17: editing is no longer delivered here; see
[albums-rule-editing.md](albums-rule-editing.md).*

Already decided, and not reopened here:
- One automatic album with mixed criteria — [albums.md](albums.md) §8 decision 33.
- Content is free text only — decision 34.
- ~~Rule editing is in this stage — decision 36.~~ Rule editing is decision 36; since 2026-11-17 it
  continues with Stage 3 or standalone, not in this stage — decision 39. **Amended 2026-11-17:** its
  plan is [albums-rule-editing.md](albums-rule-editing.md) (decision 39, amended).
- The model ships inside the app, and content albums are hidden on weaker devices — decision 37.
  Since 2026-11-16 "hidden" means only that the Content criterion is not offered; an existing
  content album is unevaluatable there, never hidden.

---

## The model candidates

The maintainer proposed [google/embeddinggemma-2](https://huggingface.co/google/embeddinggemma-2)
and asked for one more candidate to be chosen (A1). **The second is SigLIP 2 Base**,
[google/siglip2-base-patch16-224](https://huggingface.co/google/siglip2-base-patch16-224). Why it,
and why not the obvious smaller one, is under *The second candidate* below.

### EmbeddingGemma 2

**What its model card states (read 2026-11-13).** These are the card's claims; none has been
measured here.
- **Shared space:** text, images, video and audio are embedded into one shared space, so a typed
  query can be compared directly with an image's embedding.
- **Size:** 740M parameters in total, or about **440M with only the text and vision encoders
  loaded** (the 270M text model plus the 170M vision encoder). The published checkpoint is one
  1.49 GB bfloat16 file holding all four modalities, plus a 32 MB tokenizer, so a text-and-image
  build is something we would have to produce, not download.
- **Output:** native 768 dimensions, truncatable to 512, 256 or 128 (Matryoshka). The card calls
  this close to lossless down to 256, and says vectors must be re-normalised after truncation.
- **Languages:** 100+, with a warning that quality is not equal across them.
- **Precision:** **do not use float16**; it yields NaN or silently degraded embeddings. Use
  bfloat16 or float32.
- **Image input:** a per-image "vision token budget" from 70 to 1120, default 280, which trades
  latency for quality.
- **Prompts:** text inputs take task prefixes; images take none.
- **Licence:** Apache 2.0. See *Licence findings* below.

**Why it is a strong candidate:**
- One model covers both the images and the query.
- It is multilingual, which decision 34 needs.
- The truncatable output keeps storage small.

**What is unknown and could rule it out:**
- **Size on disk.** Arithmetic only: 440M parameters is about 880 MB at bfloat16, about 440 MB at
  8 bits, and about 220 MB at 4 bits. Whether quantised builds keep their quality is unknown.
- **The float16 ban.** Mobile GPU paths often compute in float16, so this may force CPU inference.
- **Runtime.** Whether a mobile runtime can run it at all, vision encoder included, is unverified.
- **Speed and memory.** Per-image latency and peak memory on a phone are unknown.

### The second candidate: SigLIP 2 Base

**Chosen by the Planner on the maintainer's instruction (A1), 2026-11-13.** What its model card
shows:
- **Licence:** Apache 2.0.
- **Size:** about 0.4B parameters; the published checkpoint is 1.5 GB in float32, with a 34 MB
  tokenizer. That is comparable to EmbeddingGemma 2's text-and-vision build, **not smaller**.
- **Kind:** a contrastive image–text model. Image and text are encoded separately into one space,
  which is what a typed query against photos needs. The card describes zero-shot classification and
  image–text retrieval as intended uses.
- **Languages:** the card says little; the paper it cites is titled as multilingual. Quality per
  shipped locale is to be measured, not assumed.
- **Variants:** a 224-pixel fixed-resolution build and a `naflex` build that adapts to aspect
  ratio. The spike starts with the 224 build.

**Why this one.**
- **Same licence and the same job**, so it is a like-for-like comparison with EmbeddingGemma 2.
- **Contrastive models are the established design for this task**, so it is a baseline that the
  newer all-in-one model has to beat.
- **Both are multilingual on paper**, which decision 34 needs. For SigLIP 2 that rests on its
  paper's title, not on its model card.

**Why not the smaller family I first expected to propose, MobileCLIP.** Its model cards list far
smaller checkpoints (the smallest image-plus-text pair is under 80M parameters). But they carry the
licence label `apple-amlr`, which I did not read. If it is a research-only licence, the whole family
is unusable in a published app. **Reading it is a precondition of measuring MobileCLIP**, and is
recorded as A1a below. Their cards also say nothing about languages other than English.

**What is unknown, as for the first candidate:** runtime support, speed, memory, and quality in the
shipped locales.

### Licence findings (A5), 2026-11-13

I read the Apache 2.0 text the EmbeddingGemma 2 card links to, the Gemma Prohibited Use Policy it
refers to, and the file lists of both repositories. I am not a lawyer; this is a reading, not
advice.
- **EmbeddingGemma 2:** the page the card links to, titled as a Gemma 4 licence, is the **plain
  Apache 2.0 text**, with no Gemma-specific terms added. **SigLIP 2:** also labelled Apache 2.0.
- **What Apache 2.0 requires when the weights ship inside the app (section 4):**
  1. give recipients **a copy of the licence**;
  2. **state that the files were changed**, if they were. Converting or quantising weights counts;
  3. keep copyright and attribution notices from the source;
  4. **reproduce a NOTICE file if the project has one.** Neither repository's file list has a
     NOTICE or LICENSE file.

  Apache 2.0 allows these to be met by a text file in the distribution. **It does not require an
  on-screen attribution.** That differs from GeoNames, whose CC BY 4.0 does, and which is why
  Settings has a Place data row.
- **Acting on it, per the maintainer's instruction:** no attribution row is added to Settings. What
  is owed instead is a **licence text and a modification statement shipped as a file in the APK**,
  added in the step that first ships the weights. The spike ships nothing, so nothing is owed yet.
- **The Gemma Prohibited Use Policy** (last modified 21 February 2024, read 2026-11-13). The
  EmbeddingGemma 2 card says deployments must adhere to it, but the licence page it links to does
  not mention it. I could not settle whether it binds, so **it is treated as binding**, because
  compliance costs nothing here. **What it says:** you may not use the model, or let others use it,
  to generate infringing content; to facilitate illegal, dangerous or malicious activity (child
  exploitation, illegal goods, violent extremism, spam, scams, malware); to harass, to promote hatred,
  violence or self-harm; to track or monitor people without consent; to generate unfair or adverse
  impacts on people based on protected characteristics; to generate, gather, process or infer
  sensitive personal information without the rights and consents the law requires; to mislead or
  impersonate; to make automated decisions in domains such as finance, employment, housing or
  healthcare; or to generate sexually explicit content. **My reading for this app:** a user finds
  their own photos, on their own phone, from a description. Nothing is generated and nothing leaves
  the device. No item on the list is aimed at that. The nearest are the two about sensitive personal
  information and monitoring people, and both turn on doing it to others without consent, which is
  not what a person searching their own library does. **The Planner is not a lawyer**, and the copy
  I read was a rendered excerpt with gaps, so the full text should be read once at 4.1 before the
  model is chosen. **What keeps the app clear:** no query is logged or sent anywhere, there is no
  automatic tagging of people or of sensitive categories, and Stage 4 builds no classifier on top of
  the embeddings. That last one is a line to hold; breaking it is a new decision.
- **The app has no screen listing open-source licences** for any library it uses, so a new
  Apache 2.0 notice would be the first. Whether the existing libraries are covered some other way
  was not checked. Noted here, not fixed.

---

## Steps

~~Step 4.0 is approved and in progress; steps 4.1 onward are proposals until step 4.1 confirms them.~~
**Amended 2026-11-17:** step 4.0 is done; steps 4.1 onward are paused, and still proposals until
step 4.1 confirms them. Step 4.2 leaves this plan (decision 39) for
[albums-rule-editing.md](albums-rule-editing.md).

| Step | Delivers | Status |
|---|---|---|
| **4.0** | Feasibility spike: model, runtime, quantisation, measured on a device. No shipped code. | ~~🔶 In progress — started 2026-11-13~~ ✅ Done 2026-11-17 — *Spike outcome* above |
| **4.1** | The decisions: ADRs and amendments, and these exit criteria confirmed | ⬜ Paused 2026-11-17 |
| **4.2** | ~~Rule editing for place and date rules~~ Moved to [albums-rule-editing.md](albums-rule-editing.md) 2026-11-17 | ~~⬜ Paused 2026-11-17 — continues with Stage 3 or standalone (decision 39)~~ Moved out (decision 39, amended 2026-11-17) |
| **4.3** | Tier B schema and store | ⬜ Paused 2026-11-17 |
| **4.4** | Image embedding in the indexing pipeline | ⬜ Paused 2026-11-17 |
| **4.5** | The `content` clause, query embedding and evaluation | ⬜ Paused 2026-11-17 |
| **4.6** | The Content criterion on the New automatic album screen and in the editor, ~~the hidden-device wording~~ ~~the hidden-device behaviour~~ the below-threshold device notice (A4b), and Tier B in Settings (B6, C5) | ⬜ Paused 2026-11-17 |
| **4.7** | Device verification and installed-app walkthrough | ⬜ Paused 2026-11-17 |
| **4.8** | Close-out: `ARCHITECTURE.md`, vocabulary, audit, archive | ⬜ Paused 2026-11-17 |

### 4.0 — Feasibility spike

**Done 2026-11-17.** The result is under *Spike outcome* above.

Answers group A with numbers, on the SM-G990B and on the largest library available. The 427-item
library used at 2.7 is too small to estimate indexing time.

For each candidate model (EmbeddingGemma 2 and SigLIP 2 Base) and runtime, record:
- model file size;
- load time;
- per-image embedding time at one or two vision budgets;
- peak memory;
- per-query text embedding time;
- a quality check: a handful of queries, in English and in at least two other shipped locales,
  over real photos, with the maintainer judging the results.

No app code ships. The spike lives under `tools/` (A6), outside the app build. The weights are not
committed.

**Environment found, 2026-11-13.** These are facts about this development machine on that date,
not about the project.
- The default Python is 3.14.2. It has none of `torch`, `transformers`, `sentence-transformers`,
  `onnx`, `onnxruntime`, `ai_edge_torch`, `tensorflow`, `huggingface_hub` or `sentencepiece`.
- Python 3.12.0 is installed, and is the one the spike uses: ML packages often lag the newest
  Python, and nothing was checked about 3.14 support.
- 295 GB free on C:.
- 31.4 GB RAM, 14 cores, no NVIDIA GPU, so model conversion runs on CPU.

### 4.1 — The decisions

The Planner writes, and the maintainer approves:
- **The model, the runtime and how the model reaches the device.** A new ADR. The model ships
  inside the app (decision 37), so Play's size limits (A3a) are an input to the choice. The runtime
  is a new dependency, an `AGENTS.md` ask-before-doing item, and its merged-manifest permissions
  are a separate one. The hidden-device threshold and what hidden means (A4a, A4b) belong in the
  same ADR.
- **The licence file** that ships with the weights (A5), if the chosen model needs one, and a
  full read of the Gemma Prohibited Use Policy if EmbeddingGemma 2 is the model.
- **Tier B storage**, as an ADR-0018 amendment.
- **The `content` clause format**, as an ADR-0017 amendment: what it stores, and what makes it
  unevaluatable.
- ~~**Rule editing**, as an ADR-0016 amendment or a new ADR, depending on D1.~~ Moved to
  [albums-rule-editing.md](albums-rule-editing.md) step R1, 2026-11-17.
- ~~**Whether [albums.md](albums.md) §8 decision 37's 2026-11-15 amendment needs an ADR-0017
  amendment** — a content clause ignored, or a content-only album hidden, below the threshold. Routed
  to the Planner if so.~~ Moot: that amendment was reversed on 2026-11-16, and the rule now in force
  departs from nothing in ADR-0017.
- ~~**Whether "everywhere" includes every list of albums the user can pick from**, such as the Add
  to album list ([albums.md](albums.md) §8 decision 37, amended 2026-11-16). "Absent everywhere" is
  the maintainer's word; including those lists is the Planner's reading, to be confirmed here.~~
  Moot since the 2026-11-16 reversal: the album is listed everywhere.
- **Whether ADR-0017 needs an amendment naming the device's capability as a second cause of
  unevaluatable** (decision 37, reversed 2026-11-16) — the Planner's own note.

This step also confirms or changes the exit criteria below.

### 4.2 — Rule editing

**Amended 2026-11-17:** this step leaves the paused plan and continues with Stage 3 or standalone
([albums.md](albums.md) §8 decision 39); what follows is kept as its specification.
**Amended again 2026-11-17:** moved to [albums-rule-editing.md](albums-rule-editing.md), which now
carries its specification (decision 39, amended); the text below is kept as it stood.

~~Editing for the clauses that exist today, `time` and `region`. **Proposed before the ML steps**
(D4): it needs no model, it is useful now, and it would not be stranded if the spike rules content
out. Step 4.6 then adds the content clause to an editor that already works.~~

~~**Owed when it lands:** [albums.md](albums.md) §8 decision 31's sentence that a wrong rule is still
fixed only by deleting and recreating the album is updated (decision 31, amended 2026-11-15).~~

### 4.3 — Tier B schema and store

An embeddings table in the index database: version bump, `Migration(4, 5)`, committed `5.json`
(version 4 rechecked after the rebase onto main: `MediaIndexDatabase`'s `@Database` and
`app/schemas/` both stop at 4),
store interface with `Room*` and `InMemory*` implementations, and DAO tests. Each row is stamped
with the model version (B5). The backup exclusion needs no change, because it is the same file;
check it rather than assume it.

### 4.4 — Image embedding in the pipeline

Per-image inference inside indexing, under the existing constraints. It must honour cancellation
and the permission checks, and must not advance a watermark on a run that could not read. An item
whose inference fails is stored as **absent**, not as an approximation — §6's third state. B1–B6
decide the shape.

### 4.5 — The content clause and evaluation

- Parse and encode the clause, with strictness tests like 2.7's.
- Embed the typed query (C2).
- Compare against stored embeddings and decide membership (C1).
- Apply settled and unsettled per item (C5).
- Materialise results or compute them on read (C3).
- Combined content + region + time evaluation, with tests.
- ~~Below the threshold, a content clause is treated as absent and a content-only album is hidden
  (A4b), with tests. **Amended 2026-11-16:** the tests show a hidden album absent from Albums View,
  its counts, Filter media's test and the Add to album list (the last pending 4.1's confirmation of
  "everywhere").~~ **Replaced 2026-11-16 (A4b; decision 37, reversed):** below the threshold, an
  album whose rule contains a content criterion, alone or with other criteria, is
  **unevaluatable** (`AlbumEvaluationStatus.UNEVALUATABLE`), shows only its additions minus its
  exclusions, is listed everywhere, and its stored rule is unchanged — with tests.

### 4.6 — The UI

- The Content criterion on the New automatic album screen.
- The live match count.
- The Content criterion in the rule editor from 4.2. Below the threshold it is not shown~~, but an
  existing one is kept (A4b)~~; **amended 2026-11-16:** and an album with a content criterion is
  unevaluatable there, so the editor does not edit it on that device (A4b, D3).
- A content-coverage figure only if C5 decides there is one, and only where it decides. Since
  decision 29 no coverage figure is shown on an album or on this screen.
- Whether the Content criterion has a Status area, and what its Index band counts (E1, B6).
- A Content case for the New automatic album hint, which today asks for a place, a date, or both,
  and for the choice chips, which today have a Place and a Date form.
- A content case in the Album info dialog (decision 31): in `AlbumRuleSummary` and
  `AlbumInfoDialog`, with a test beside `AlbumRuleSummaryTest`'s. `summarizeAlbumRule` picks region
  and time clauses out by type, so without it a content clause is dropped silently, and a
  content-only rule shows the Automatic heading with nothing under it. ~~**Open, with A4b:** below
  the threshold the dialog would describe the content criterion if the rule still parses, or show
  `Unreadable`'s Newer-app wording, which names the wrong reason; a device wording needs a summary
  case that carries a reason, which decision 31 kept out of `Unreadable`.~~ ~~**Answered 2026-11-15
  (A4b; decision 31 as amended):** below the threshold the content criterion is not displayed and
  nothing else is said; a content-only album is hidden there.~~ **Amended 2026-11-16 (A4b; decisions
  31 and 37, as amended):** below the threshold the album is unevaluatable, and the dialog shows
  the device notice's wording, not the Newer-app wording. The struck observation above therefore
  applies again: today `Unreadable` carries no reason, and `AlbumInfoDialog` shows
  `album_rule_unevaluatable_notice` for it, so the device wording needs a way to tell the two
  reasons apart.
- **The device notice (A4b; decision 37, reversed 2026-11-16).** Below the threshold, an album with
  a content criterion carries its own notice wherever the Newer-app notice appears — the short
  form on the Album tile and Album row, the full form in Album Detail View — and the full wording
  in Album info, in place of the Newer-app wording. One new notice in every shipped locale, in the
  full and short forms that `album_rule_unevaluatable_notice` and
  `album_rule_unevaluatable_notice_short` have; keys chosen here.
- Tier B figures in the Settings screen's Media index area (B6).
- New strings in every shipped locale (the list is `LocaleHelper.supportedLanguageTags`).
- UI vocabulary additions, in-change.

Layout is E1.

### 4.7 — Verification

The definition of done, once, over the integrated change, with `adb devices` checked and stated.
Then an installed-app walkthrough: create "castles in {country}, {year}", see members after Index
now, edit the rule, and see the members change.

### 4.8 — Close-out

- `ARCHITECTURE.md` for what landed.
- Exit criteria checked against the repository.
- `/docs-audit`.
- Archive this plan, per the stage-plan rules.

---

## Stage 4 exit criteria — draft, confirmed or changed at 4.1

Derived from Stage 2's. **Not binding until 4.1.**

- The checks in `AGENTS.md`'s definition of done pass, with `adb devices` checked and the result
  stated.
- Every step is ✅, or explicitly dropped with the drop recorded in [albums.md](albums.md) §7.
- A content criterion combined with place and date works end to end, and **a test proves a
  combined content + region + time rule**.
- **Count consistency:** the count the screen shows for a typed description, combined with the
  other choices, equals the evaluator's member count for the rule it would write. Tested on the JVM
  and on a device, as at 2.7.
- An item not yet embedded leaves the album ~~**Incomplete**, never silently empty~~ **waiting on
  the index** (`WAITING_ON_INDEX`, ADR-0017 Amendment 2), never a settled no-match. Since decision
  29 nothing on the album shows that state, so "never silently empty" no longer holds of any album;
  whether content needs more is C5. An item whose inference failed is neither pending work nor
  covered, per §6.
- A rule whose content clause was written by another model version is not compared against the
  wrong embeddings. What it does instead is decided at 4.1 (B5, C2).
- **Rule editing:** ~~every rule this build can parse round-trips through the editor unchanged. An
  unevaluatable rule is never rewritten.~~ ~~Additions and exclusions behave as D2 decides.~~ ~~An edit
  changes only the rule: additions, exclusions, the cover and display overrides are left alone
  (D2).~~ **Amended 2026-11-17:** the rule-editing criteria moved to
  [albums-rule-editing.md](albums-rule-editing.md); what stays here is content's own. ~~Below
  the threshold, the editor keeps a content criterion it does not show (A4b).~~ Below
  the threshold, the editor does not edit an album with a content criterion (A4b, D3).
- ~~Below the threshold, a content-only album is hidden and a content clause in a mixed rule is
  ignored, with the stored rule unchanged (A4b).~~ Below the threshold, an album with a content
  criterion is unevaluatable: listed everywhere, showing only its additions minus its exclusions,
  carrying the device notice on its tile, row, Album Detail View and Album info, with its stored
  rule unchanged (A4b; decision 37, reversed 2026-11-16).
- The index database reaches its new version through a real migration, every schema JSON is
  committed, and the index — embeddings included — is still excluded from backup, checked against
  the rules files.
- The merged manifest is checked after the runtime dependency lands, and any permission it adds is
  approved or removed.
- Inference makes no network request. Model delivery follows A3.
- Indexing runs under the existing constraints, with no notification and no foreground service.
- Every new string is in every shipped locale; the vocabulary has moved; `ARCHITECTURE.md`
  describes what landed; a documentation audit has passed.

---

## Open questions

Each says what it blocks. A recommendation is given only where there is a reason for one.

### A. Model, runtime and delivery — block 4.0

1. ~~**Which model?**~~ **Answered 2026-11-13 for the spike's purposes:** EmbeddingGemma 2 against
   SigLIP 2 Base, which the Planner chose on the maintainer's instruction. The final pick is made
   on 4.0's measurements. *No recommendation until measured.* **Open follow-up, A1a:** read the
   `apple-amlr` licence before MobileCLIP is measured at all.
2. ~~**Which runtime?**~~ **Answered 2026-11-13 by the maintainer: whatever works.** The spike picks
   among LiteRT, ONNX Runtime and MediaPipe on what actually runs both encoders at a precision the
   model allows (the float16 ban, for EmbeddingGemma 2). **The choice is delegated; adding the
   dependency to the app is still approved at 4.1**, because `AGENTS.md` makes a new dependency,
   and the permissions it merges into the manifest, an ask-before-doing item. The spike's own
   runtime is in `tools/`, not in the app (A6).
   **Spike dependencies, approved 2026-11-13 by the maintainer:** third-party dependencies that
   live only under `tools/content-model-spike/` and never touch `app/` are approved, which
   discharges the ask-before-doing item **for the spike only**. **This does not approve adding the
   runtime to the app;** that is still decided at step 4.1.
3. ~~**How does the model reach the device?**~~ **Answered 2026-11-13 by the maintainer: inside the
   APK.** No download and no network, which is what §11 already requires. **What this leaves open,
   A3a:** Play's size limits for a store build were not checked and **must be checked in step 4.1
   before the model is chosen**, because an app bundle's base module is limited, and the maintainer's
   wording "in the APK" may need to mean an install-time asset pack to fit. That is a delivery
   mechanism, not a download, but it changes the build. The size budget it implies is a **criterion
   for the model choice**, not something to discover afterwards.
4. ~~**What is the lowest device content albums must run on?**~~ **Answered 2026-11-13 by the
   maintainer: content albums are hidden on weaker devices.** [albums.md](albums.md) §10 question 3
   is answered in part; §8 decision 37 holds it. **What is still open, A4a:** the threshold itself
   (RAM, API level, or a measured capability), which the spike's memory and speed figures should
   set; and **what hidden means** — answered as A4b below.
5. ~~**Licence and attribution.**~~ **Answered 2026-11-13 by the maintainer: read the terms and act
   accordingly.** Findings and action are under *Licence findings* above. **No Settings attribution
   row is added.** A licence text and modification statement ship as a file in the APK when the
   weights first ship. **A5a, answered the same day:** the maintainer asked what the Gemma Prohibited
   Use Policy says and wants to avoid breaking it. The Planner's summary is in *Licence findings*;
   the policy stays **treated as binding**, and nothing in this stage needs a different answer.
6. ~~**Where does spike code live?**~~ **Answered 2026-11-13 by the maintainer: in `tools/`.** Like
   `tools/gazetteer/`, in a directory of its own, with its raw downloads gitignored.
   - **A6a, approved 2026-11-13:** a **separate Gradle project under `tools/`, outside the app
     build**, for the speed and memory figures that only a phone gives. The app gains no dependency
     during the spike. A Python script beside it converts models and checks retrieval quality on a
     desktop.
     **Approved by the maintainer 2026-11-13:** the third-party dependencies of both — the Python
     environment and the separate Gradle measurement project — provided they live only under
     `tools/content-model-spike/` and never touch `app/`. This discharges `AGENTS.md`'s
     ask-before-doing item **for the spike only**; it does **not** approve adding the runtime to the
     app, which is still decided at step 4.1 (A2). **Also approved the same day:** downloading about
     3 GB of model weights plus Python packages on the development machine, for the spike.
   - **A6b, answered 2026-11-13:** measure on **the currently attached phone**, an SM-G990B (API 36,
     about 5.2 GB RAM, SoC SM8350). The maintainer will load more items onto it first. The weights
     are not committed to git; the download script and gitignore follow the gazetteer's pattern.
     This phone is also the reference point for A4a: what it can run is a data point for the
     threshold, and it is a mid-range device, not a low-end one.

**A4b — what "hidden" means. Answered 2026-11-13 by the maintainer: the simplest solution.** "Simplest"
was not spelled out, ~~so this is the Planner's reading, **to be confirmed at 4.1**:~~ **amended
2026-11-16:** the first and third bullets below, and the album-visibility rule, are decided by the
maintainer; the second bullet is still the Planner's proposal, to be confirmed at 4.1. **Amended
again 2026-11-16:** the album-visibility rule of the third bullet was reversed by the maintainer;
no album is hidden, and "hidden" now means only that the Content criterion is not offered:
- **The Content criterion is not offered** on the New automatic album screen, and the rule editor
  does not show it. This is the part the maintainer's answer settles. *Decided by the maintainer.*
- **Indexing skips Tier B** on a device below the threshold, from the same single capability check.
  Nothing is computed that cannot be used, and it saves battery. *Proposed* — the Planner's
  proposal, still to be confirmed at 4.1; the maintainer has not confirmed it explicitly.
- ~~**An album that already has a content clause** on a device below the threshold — restored from
  backup onto a weaker phone (ADR-0014), or a threshold raised later — **reuses ADR-0017's
  unevaluatable state** (`AlbumEvaluationStatus.UNEVALUATABLE`, today shown as the Newer-app
  notice, which decision 29 kept), with its own notice wording, because the reason is the device
  and not the rule. No new state. It is never rewritten, so the clause is kept if the album moves back to a
  capable device. *Proposed.* The only cost is one new notice string in every shipped locale.~~
  ~~**Answered 2026-11-15 by the maintainer, replacing the struck proposal** ([albums.md](albums.md)
  §8 decision 37, amended): an album whose rule has only content criteria is **hidden** on that
  device; in an album with other criteria as well, the content criterion is **ignored** and the
  rest apply. Nothing is communicated to the user, so no notice string is needed. The stored rule
  is never changed, so the full rule applies again on a capable device. Accepted costs, and the
  departure from ADR-0017's reasoning, are in the amendment. **Amended 2026-11-16 — decided by the
  maintainer:** a hidden content-only album is absent everywhere on that device — Albums View, its
  totals, and Filter media's test (decision 30) — and whether that includes the Add to album list
  is confirmed at 4.1. Exclusions made on the wider view and the unreachable manual additions of a
  hidden album were confirmed as accepted costs.~~
  **Reversed 2026-11-16 by the maintainer** ([albums.md](albums.md) §8 decision 37, *reversed*),
  because hidden albums were causing too many problems; deletion was proposed and rejected on the
  Planner's advice. Below the threshold, **any album whose rule contains a content criterion**,
  alone or with other criteria, is **unevaluatable** (`AlbumEvaluationStatus.UNEVALUATABLE`, no new
  state). It is listed normally everywhere and can be opened, renamed and deleted; its rule
  contributes nothing, so it shows its additions minus its exclusions and never widens; it carries
  one device notice in place of the Newer-app wording, also in Album info, new in every shipped
  locale; its stored rule is never changed; and the rule editor does not edit it on that device
  (D3). This is essentially the struck 2026-11-13 proposal above, now decided by the maintainer.

### B. Indexing — block 4.3 and 4.4

1. **Embedding dimension:** 768, 512 or 256. Arithmetic: float32 at 256 dimensions is 1 KB per
   image, about 10 MB for 10,000 images; at 768 it is about 30 MB.
2. **Image input:** which vision budget, and is the image decoded from the original or from a
   downscaled thumbnail?
3. **Videos:** skipped, one frame, or several? The model accepts video, at a cost per frame.
   **Since decision 30**, skipping them shows in Albums View: with Filter media set to videos only,
   a content-only album with no added video is emptied and hidden, because it had items without
   the filter. Acceptable, or a reason for at least one frame?
4. **Same worker or separate work?** Should Tier B run in `MediaIndexingWorker` after Tier A and the
   place resolver, or as separate unique work? How many images fit in one constrained run? At the
   measured speed, how many nights does a full library take?
5. **Model change.** A new model version invalidates every stored embedding. Is the per-row model
   stamp, analogous to `PLACE_RESOLVER_VERSION`, enough, with re-embedding as ordinary indexing
   work?
6. **Progress.** Restated 2026-11-13 against main. Settings' Media index area now shows, while a
   pass runs, a progress bar and the rows remaining; then four counts in this order — indexed with
   a date; "no usable date information"; "no usable location information", or the
   location-access-off sentence; not yet indexed — then deferred items, the last successful scan,
   and a failed or cancelled line. Two of the four counts measure progress and two are coverage
   figures, kept apart by wording alone (decision 29). All four are computed in
   `MediaIndexingViewModel` by intersecting the index projection with live media, not through the
   albums' join. **The question:** separate Tier B figures, or Tier B folded into the existing
   not-yet-indexed count? **It also decides the Index band**, on the New automatic album screen,
   whose only figure is that same not-yet-indexed count and whose Index now appears only while that
   count is above zero and no pass runs. If Tier B work is not in the count, the band and
   its Index now disappear while embeddings are still pending, and the screen's "indexing may still
   fill this list" wording (decision 27), chosen from the same state, would not apply to Content.
   Where a failed-inference count goes, if anywhere, is C5.

### C. Matching — block 4.5

1. **How does a similarity score become membership?** Options: a fixed cutoff, a cutoff relative to
   each query's scores, or top-K. Is the cutoff stored in the clause? Can the user adjust
   strictness? It must be deterministic: an album must not change when nothing changed. *The most
   important product question in the stage.*
2. **Is the query embedding stored in the rule, or recomputed at evaluation?**
   - Stored: it lives in the backed-up album database and ties the clause to a model version, so the
     text must be kept so it can be re-embedded.
   - Recomputed: the text encoder must be loaded whenever albums are evaluated, app open included.
3. **Materialise or compute on read?** §6 planned to materialise content results with a generation
   stamp. If 4.0 shows a full comparison is cheap, computing on read is simpler, and ADR-0020 holds
   either way. Decide on numbers.
4. **Language.** Is quality checked for every shipped locale, or for a named subset? Which text
   prefix does a query use?
5. **Settled and unsettled for content, and whether a content-coverage figure exists.** Restated
   2026-11-13 against main. A not-yet-embedded item is unsettled, so the album is waiting on the
   index. A failed inference is a settled no-match. **Main's rule now** (decision 29; ADR-0016,
   ADR-0017 and ADR-0019, each Amendment 3): no album shows the Incomplete-album notice or a
   coverage notice — not the Album tile, the Album row or Album Detail View — and the New automatic
   album screen shows no coverage notice. The two coverage figures are rows of Settings' Media index
   area only, computed with that area's other rows, never pending and never "yet". Decision 25's
   placement, which this question used to point to, is reversed. **Options, not decided here:**
   - no content-coverage figure anywhere; a failed inference is simply not a member;
   - a third coverage row in Settings in the same wording family, for example "N items have no
     usable content information", counting failed inference — which also asks whether videos count
     if B3 skips them, and whether the row disappears with the rest of Tier B on a device below the
     threshold (A4b);
   - a figure on the screen or on the album, which reopens decision 29 and is the maintainer's
     call.

   **A cost to weigh with it.** Decision 29 accepted that a waiting album says nothing about it on
   itself, when the wait was one Tier A pass. Tier B may take several nights for a full library
   (B4), so a new content album may stay short or empty, with nothing on it saying why, for much
   longer. Whether decision 29's accepted cost still holds at that length is the maintainer's
   question.
6. **Query limits:** an empty or one-character description, and a maximum length.

### D. Rule editing — ~~block 4.2~~ moved to [albums-rule-editing.md](albums-rule-editing.md)

**Moved 2026-11-17** with step 4.2 ([albums.md](albums.md) §8 decision 39, amended 2026-11-17).
D1 and D3–D5 are answered or kept open there, under the same numbers; D2's answer is also kept
here, where it was recorded.

1. ~~**Edit surface:** the New automatic album screen in an edit mode, or a screen of its own?
   ADR-0016 Amendment 2 argued against a mode for *manual versus automatic* because the inputs
   differed; for editing, the inputs are identical. A new screen is another branch in
   `MainActivity`'s chain. ADR-0013 defers the chain's future to Stage 3, which now comes later.~~
   Moved: [albums-rule-editing.md](albums-rule-editing.md) D1.
2. ~~**Additions and exclusions on edit:** kept as they are, or cleared?~~ **Answered 2026-11-15 by
   the maintainer: an edit changes only the rule.** *Pointer, 2026-11-17: this record stays here;
   [albums-rule-editing.md](albums-rule-editing.md) D2 links to it.* Manual additions, exclusions,
   the cover and the
   album's display overrides are left alone ([albums.md](albums.md) §8 decision 36, amended).
   ~~*Leaning kept*, because §3's three sets are independent of the rule, but the maintainer decides.~~
   Moot: answered above.
   **A fact from main:** an album's display overrides are keyed by its id and deleted with it
   (decision 28, ADR-0021), so an editor that replaces the album row rather than updating it would
   lose them too. **A second
   fact from main, 2026-11-14 — the stored cover:** `coverMembershipId` is on the album row too,
   so a replacing editor loses it as well. A cover the user picked from rule matches is stored as
   an addition row (decision 32, ADR-0015 Amendment 1). If additions are kept, it stays a member
   even when the edited rule stops matching it, which that amendment names and accepts. If they are
   cleared, its row goes with them, the column is set to null, and the editor must run ADR-0015's
   re-roll as every removal path must, so the user's pick is lost. The row is an ordinary addition;
   only the cover column marks it as the cover's. ~~**Question:** if additions are
   cleared, is the cover's row kept?~~ **Answered 2026-11-15: the cover follows membership, not the
   rule.** It stays for as long as its photo remains a member, as a rule match or as the user's own
   addition, so an edit that stops the rule matching a pinned cover does not reset it. It falls back
   only when the photo leaves the album — a rule edit that unmatches a photo that was never an
   addition, a removal, or the user removing the addition — and then ADR-0015's existing behaviour
   applies. **Confirmed by the maintainer 2026-11-16:** the first case can only reach a random or
   display-only fallback cover, never a user's pick, because a pick is stored as an addition. That
   is ADR-0015's rule applied to editing; decision 32's accepted cost stands and no ADR changes.
3. ~~**A rule with an unknown clause:** refuse editing entirely, or edit the known clauses while
   preserving the unknown one untouched? *Leaning refuse*: simpler, and it cannot widen the album.~~
   ~~**Cross-reference, 2026-11-15:**~~ ~~the editor keeps any criterion it does not show (A4b);~~ ~~whether
   an album with an *unknown* clause type can be edited at all remains open here. **Amended
   2026-11-16:** an album with a content criterion is unevaluatable on a device below the threshold,
   and the editor does not edit it there (A4b; [albums.md](albums.md) §8 decision 37, reversed), so
   it joins this question as a second unevaluatable case, while the unknown-clause case stays open.~~
   Moved: [albums-rule-editing.md](albums-rule-editing.md) D3.
4. ~~**Ship editing before the ML steps?** As proposed in 4.2. *Leaning yes.*~~ Moved, and answered
   there: [albums-rule-editing.md](albums-rule-editing.md) D4.
5. ~~**Entry point:** where the edit action lives in Album Detail View — a Designer question. **A
   fact from main:** the Overflow menu now ends with Change album thumbnail and then Album info
   (decisions 32 and 31), the read-only view of the rule, and the edit action will sit beside
   Album info.~~ Moved: [albums-rule-editing.md](albums-rule-editing.md) D5.

### E. Screen — block 4.6

1. **Layout for three criteria now and four later.** Restated 2026-11-13 against main. The screen
   today: the top bar holds the Album name field and the close or back button; below it, the
   match-count line, sharing its slot with the New automatic album hint; one choice chip per
   current choice; then two tabs, Place and Date, each a drill-down list whose first item is the
   Status area — the Index status header over the Index band, drawn only while a pass runs or live
   media is not yet indexed, and since decision 29 holding nothing else; and the Create automatic
   album action. **Content does not fit that shape:** it is a typed field, not a list to drill.
   Options include a third tab holding the field, a field above the tabs, or a summary row of the
   chosen criteria over per-criterion sections. Tabs will crowd at large font scales and in
   long-word locales, and Stage 3 adds a fourth criterion. Also open: whether the Content criterion
   carries a Status area at all, which turns on B6. Designer.
2. **Live count while typing:** debounce, and what the match-count line shows while the query is
   being embedded. The line is announced as it changes, so the debounce also sets how often a
   screen reader speaks.

### F. Policy — before release

1. **Do content embeddings change the Play Data Safety answers?** They never leave the device and
   are excluded from backup. Confirm alongside decision 9's face-data check.

---

## Related

- [albums.md](albums.md): §3 membership, §4 the kinds, §6 the index, §7 progress, §8 decisions
  (13 as amended, 27–32, and 33–37), §10 questions 3 and 12.
- [ADR-0015](../architecture/decisions/0015-album-cover-persisted-random-member.md) Amendment 1,
  [ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md),
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
  [ADR-0019](../architecture/decisions/0019-offline-reverse-geocoding-for-region-albums.md),
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md),
  [ADR-0021](../architecture/decisions/0021-per-album-display-preference-overrides.md).
- [albums-stage-2.md](albums-stage-2.md): archived; its structure and traps are the template here.
- [albums-rule-editing.md](albums-rule-editing.md): rule editing's own plan since 2026-11-17, with
  step 4.2 and group D.
