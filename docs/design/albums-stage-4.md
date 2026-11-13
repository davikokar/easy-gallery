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
> **This is a draft.** Written 2026-11-13. Nothing in it is approved except what
> [albums.md](albums.md) §8 decisions 27–30 already decide. The steps, the exit criteria and every
> recommendation below are proposals until the maintainer answers the open questions and step 4.1
> records the decisions. Where something was not known, it is a question, not a guess.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff, as in Stage 2.

- **Stage 4 has not started.** The next action is a discussion, not code: the maintainer goes
  through *Open questions* below, beginning with group A, which blocks step 4.0.
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

---

## What the user gets

On the **New automatic album** screen, next to Place and Date, a **Content** criterion:

- The user types a description, such as "castles".
- The screen shows how many items match, combined with any place and date chosen.
- Create writes the album.

Later, the user can **edit** an automatic album's rule. The Album type dialog does not change.

Already decided, and not reopened here:
- One automatic album with mixed criteria — [albums.md](albums.md) §8 decision 27.
- Content is free text only — decision 28.
- Rule editing is in this stage — decision 30.

---

## The model candidate: EmbeddingGemma 2

The maintainer proposed [google/embeddinggemma-2](https://huggingface.co/google/embeddinggemma-2)
and is open to alternatives.

**What its model card states (read 2026-11-13).** These are the card's claims; none has been
measured here.
- **Shared space:** text, images, video and audio are embedded into one shared space, so a typed
  query can be compared directly with an image's embedding.
- **Size:** 740M parameters in total, or about **440M with only the text and vision encoders
  loaded** (the 270M text model plus the 170M vision encoder).
- **Output:** native 768 dimensions, truncatable to 512, 256 or 128 (Matryoshka). The card calls
  this close to lossless down to 256, and says vectors must be re-normalised after truncation.
- **Languages:** 100+, with a warning that quality is not equal across them.
- **Precision:** **do not use float16**; it yields NaN or silently degraded embeddings. Use
  bfloat16 or float32.
- **Image input:** a per-image "vision token budget" from 70 to 1120, default 280, which trades
  latency for quality.
- **Prompts:** text inputs take task prefixes; images take none.
- **Licence:** labelled Apache 2.0. The link points to a Gemma licence page, and the card requires
  adherence to the Gemma Prohibited Use Policy — see A5.

**Why it is a strong candidate:**
- One model covers both the images and the query.
- It is multilingual, which decision 28 needs.
- The truncatable output keeps storage small.

**What is unknown and could rule it out:**
- **Size on disk.** Arithmetic only: 440M parameters is about 880 MB at bfloat16, about 440 MB at
  8 bits, and about 220 MB at 4 bits. Whether quantised builds keep their quality is unknown.
- **The float16 ban.** Mobile GPU paths often compute in float16, so this may force CPU inference.
- **Runtime.** Whether a mobile runtime can run it at all, vision encoder included, is unverified.
- **Speed and memory.** Per-image latency and peak memory on a phone are unknown.

**Alternatives, to measure rather than to recommend.** Contrastive image–text models such as
SigLIP 2 and MobileCLIP are smaller and widely run on-device. Their multilingual coverage, licences
and sizes are not verified here and are part of A1. The first EmbeddingGemma was text-only and
cannot embed images on its own.

---

## Steps — proposed, none approved

| Step | Delivers | Status |
|---|---|---|
| **4.0** | Feasibility spike: model, runtime, quantisation, measured on a device. No shipped code. | ⬜ |
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

For each candidate model and runtime, record:
- model file size;
- load time;
- per-image embedding time at one or two vision budgets;
- peak memory;
- per-query text embedding time;
- a quality check: a handful of queries, in English and in at least two other shipped locales,
  over real photos, with the maintainer judging the results.

No app code ships. Where the spike's code lives is A6.

### 4.1 — The decisions

The Planner writes, and the maintainer approves:
- **The model, the runtime and how the model reaches the device.** A new ADR. The runtime is a new
  dependency, an `AGENTS.md` ask-before-doing item, and its merged-manifest permissions are a
  separate one.
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

1. **Which model?** EmbeddingGemma 2 against at least one smaller contrastive model, decided on 4.0's
   measurements. *No recommendation until measured.*
2. **Which runtime?** LiteRT, ONNX Runtime or MediaPipe are the obvious candidates. It matters
   which can actually run the chosen model's vision encoder on Android, and at what precision given
   the float16 ban. This is a new dependency to approve, and it brings its own merged-manifest
   check.
3. **How does the model reach the device?** It is hundreds of MB. Options:
   - inside the APK;
   - a Play Asset Delivery pack;
   - downloaded by the app.

   Downloading conflicts with §11's no-network posture, which currently allows only the tip jar.
   Play's size limits must be checked rather than assumed. *Leaning against downloading.*
4. **What is the lowest device content albums must run on?** [albums.md](albums.md) §10 question 3.
   It decides quantisation. **Should content albums be unavailable below some RAM or API level**,
   as Favourites is below API 30?
5. **Licence and attribution.** The card says Apache 2.0, links to Gemma licence terms, and requires
   the Gemma Prohibited Use Policy. Confirm what applies. Does Settings need an attribution row, as
   GeoNames has?
6. **Where does spike code live** — `tools/`, like the gazetteer generator, or a throwaway branch?
   And which library is measured?

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
  (13 as amended, and 27–30), §10 questions 3 and 12.
- [ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md),
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md),
  [ADR-0018](../architecture/decisions/0018-media-index-in-a-second-database.md),
  [ADR-0020](../architecture/decisions/0020-automatic-albums-resolve-against-live-media.md).
- [albums-stage-2.md](albums-stage-2.md): archived; its structure and traps are the template here.
