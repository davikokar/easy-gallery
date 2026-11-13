# Albums Stage 4 — content albums and rule editing

> **Owner: Planner. Mode: routed.** This is a *stage plan*, not a description of the app. It exists
> for the length of Stage 4 and is archived when the stage closes — what archiving means, and the
> rest of the rules for this kind of document, are in
> [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md).
> The epic itself is [albums.md](albums.md); **its §7 table is the single source of truth for where
> the epic stands**, and this document never restates it.
>
> **Stage 4 runs before Stage 3.** [albums.md](albums.md) §8 decision 13, as amended 2026-11-13,
> says why; §7 says why the numbers were kept.
>
> **This is a draft.** Written 2026-11-13. Already decided: [albums.md](albums.md) §8 decisions
> 28–32, and the maintainer's 2026-11-13 approvals of spike-only dependencies and of downloading
> about 3 GB of model weights (under *Next session starts here*). The steps, the exit criteria and
> every recommendation below are proposals until the maintainer answers the open questions and step
> 4.1 records the decisions. Where something was not known, it is a question, not a guess.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff, as in Stage 2.

- **Step 4.0, the feasibility spike, is in progress — started 2026-11-13.** Group A was answered on
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
  are still untouched.
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
  - §11: no cloud inference.
  - **Spike-only dependencies, approved by the maintainer 2026-11-13:** third-party dependencies
    that live only under `tools/content-model-spike/` — the Python environment and the separate
    Gradle measurement project — and never touch `app/`. This discharges `AGENTS.md`'s
    ask-before-doing item **for the spike only**. **It does not approve adding the runtime to the
    app:** that is still decided at step 4.1 (A2).
  - **Downloads, approved by the maintainer 2026-11-13:** about 3 GB of model weights plus Python
    packages, on the development machine, for the spike.

---

## What the user gets

On the **New automatic album** screen, next to Place and Date, a **Content** criterion:

- The user types a description, such as "castles".
- The screen shows how many items match, combined with any place and date chosen.
- Create writes the album.

Later, the user can **edit** an automatic album's rule. The Album type dialog does not change.

Already decided, and not reopened here:
- One automatic album with mixed criteria — [albums.md](albums.md) §8 decision 28.
- Content is free text only — decision 29.
- Rule editing is in this stage — decision 31.

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
- It is multilingual, which decision 29 needs.
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
- **Both are multilingual on paper**, which decision 29 needs. For SigLIP 2 that rests on its
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

Step 4.0 is approved and in progress; steps 4.1 onward are proposals until step 4.1 confirms them.

| Step | Delivers | Status |
|---|---|---|
| **4.0** | Feasibility spike: model, runtime, quantisation, measured on a device. No shipped code. | 🔶 In progress — started 2026-11-13 |
| **4.1** | The decisions: ADRs and amendments, and these exit criteria confirmed | ⬜ |
| **4.2** | Rule editing for place and date rules | ⬜ |
| **4.3** | Tier B schema and store | ⬜ |
| **4.4** | Image embedding in the indexing pipeline | ⬜ |
| **4.5** | The `content` clause, query embedding and evaluation | ⬜ |
| **4.6** | The Content criterion on the New automatic album screen, its notices, and Tier B progress | ⬜ |
| **4.7** | Device verification and installed-app walkthrough | ⬜ |
| **4.8** | Close-out: `ARCHITECTURE.md`, vocabulary, audit, archive | ⬜ |

### 4.0 — Feasibility spike

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
  inside the app (decision 32), so Play's size limits (A3a) are an input to the choice. The runtime
  is a new dependency, an `AGENTS.md` ask-before-doing item, and its merged-manifest permissions
  are a separate one. The hidden-device threshold and what hidden means (A4a, A4b) belong in the
  same ADR.
- **The licence file** that ships with the weights (A5), if the chosen model needs one, and a
  full read of the Gemma Prohibited Use Policy if EmbeddingGemma 2 is the model.
- **Tier B storage**, as an ADR-0018 amendment.
- **The `content` clause format**, as an ADR-0017 amendment: what it stores, and what makes it
  unevaluatable.
- **Rule editing**, as an ADR-0016 amendment or a new ADR, depending on D1.

This step also confirms or changes the exit criteria below.

### 4.2 — Rule editing

Editing for the clauses that exist today, `time` and `region`. **Proposed before the ML steps**
(D4): it needs no model, it is useful now, and it would not be stranded if the spike rules content
out. Step 4.6 then adds the content clause to an editor that already works.

### 4.3 — Tier B schema and store

An embeddings table in the index database: version bump, `Migration(4, 5)`, committed `5.json`,
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

### 4.6 — The UI

- The Content criterion on the New automatic album screen.
- The live match count.
- The Content criterion in the rule editor from 4.2.
- Whatever content-coverage notice C5 decides.
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
- An item not yet embedded leaves the album **Incomplete**, never silently empty. An item whose
  inference failed is neither pending work nor covered, per §6.
- A rule whose content clause was written by another model version is not compared against the
  wrong embeddings. What it does instead is decided at 4.1 (B5, C2).
- **Rule editing:** every rule this build can parse round-trips through the editor unchanged. An
  unevaluatable rule is never rewritten. Additions and exclusions behave as D2 decides.
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
   is answered in part; §8 decision 32 holds it. **What is still open, A4a:** the threshold itself
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
was not spelled out, so this is the Planner's reading, **to be confirmed at 4.1**:
- **The Content criterion is not offered** on the New automatic album screen, and the rule editor
  does not show it. This is the part the maintainer's answer settles.
- **Indexing skips Tier B** on a device below the threshold, from the same single capability check.
  Nothing is computed that cannot be used, and it saves battery. *Proposed.*
- **An album that already has a content clause** on a device below the threshold — restored from
  backup onto a weaker phone (ADR-0014), or a threshold raised later — **reuses ADR-0017's
  unevaluatable state**, with its own notice wording, because the reason is the device and not the
  rule. No new state. It is never rewritten, so the clause is kept if the album moves back to a
  capable device. *Proposed.* The only cost is one new notice string in every shipped locale.

### B. Indexing — block 4.3 and 4.4

1. **Embedding dimension:** 768, 512 or 256. Arithmetic: float32 at 256 dimensions is 1 KB per
   image, about 10 MB for 10,000 images; at 768 it is about 30 MB.
2. **Image input:** which vision budget, and is the image decoded from the original or from a
   downscaled thumbnail?
3. **Videos:** skipped, one frame, or several? The model accepts video, at a cost per frame.
4. **Same worker or separate work?** Should Tier B run in `MediaIndexingWorker` after Tier A and the
   place resolver, or as separate unique work? How many images fit in one constrained run? At the
   measured speed, how many nights does a full library take?
5. **Model change.** A new model version invalidates every stored embedding. Is the per-row model
   stamp, analogous to `PLACE_RESOLVER_VERSION`, enough, with re-embedding as ordinary indexing
   work?
6. **Progress:** separate Tier B figures in the Settings Media index area, or folded into the
   existing three?

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
5. **Settled and unsettled for content.** A not-yet-embedded item is unsettled. A failed inference
   is a settled no-match. Does that need a **content-coverage notice**, placed as decision 25 places
   the location and date notices?
6. **Query limits:** an empty or one-character description, and a maximum length.

### D. Rule editing — block 4.2

1. **Edit surface:** the New automatic album screen in an edit mode, or a screen of its own?
   ADR-0016 Amendment 2 argued against a mode for *manual versus automatic* because the inputs
   differed; for editing, the inputs are identical. A new screen is another branch in
   `MainActivity`'s chain. ADR-0013 defers the chain's future to Stage 3, which now comes later.
2. **Additions and exclusions on edit:** kept as they are, or cleared? *Leaning kept*, because §3's
   three sets are independent of the rule, but the maintainer decides.
3. **A rule with an unknown clause:** refuse editing entirely, or edit the known clauses while
   preserving the unknown one untouched? *Leaning refuse*: simpler, and it cannot widen the album.
4. **Ship editing before the ML steps?** As proposed in 4.2. *Leaning yes.*
5. **Entry point:** where the edit action lives in Album Detail View — a Designer question.

### E. Screen — block 4.6

1. **Layout for three criteria now and four later.** Tabs will crowd at large font scales and in
   long-word locales. A summary row of the chosen criteria above per-criterion sections is one
   option. Designer.
2. **Live count while typing:** debounce, and what the count shows while the query is being
   embedded.

### F. Policy — before release

1. **Do content embeddings change the Play Data Safety answers?** They never leave the device and
   are excluded from backup. Confirm alongside decision 9's face-data check.

---

## Related

- [albums.md](albums.md): §3 membership, §4 the kinds, §6 the index, §7 progress, §8 decisions
  (13 as amended, and 28–32), §10 questions 3 and 12.
- [ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md),
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md).
- [albums-stage-2.md](albums-stage-2.md): archived; its structure and traps are the template here.
