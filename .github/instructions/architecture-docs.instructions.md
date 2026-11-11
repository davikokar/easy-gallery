---
name: Architecture and design documentation
description: "Use when creating, reviewing, updating, disabling, or superseding Architecture Decision Records (ADRs), when work introduces a significant architectural choice, when a change alters the structure described by the architecture overview, or when a change advances an epic that has a design document."
applyTo: "docs/architecture/**/*.md, docs/design/**/*.md"
---

# Architecture and design documentation

> **Maintenance — owner: Planner. Mode: routed.**
> This file is the rules for the rules. It changes only when the documentation process itself
> changes, which should be rare. Report a gap rather than editing it. The full documentation map —
> every document, its owner, and its mode — is in [AGENTS.md](../../AGENTS.md).

The project keeps three kinds of long-lived document, and they are maintained differently. All
three are owned by the **Planner**; Coder and Designer report drift rather than editing them. A
fourth kind, the **stage plan**, is deliberately short-lived and is defined at the end of this
file.

| | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) | [decisions/](../../docs/architecture/decisions/) | [design/](../../docs/design/) |
|---|---|---|---|
| Describes | the system as it **is** | a choice and **why** it was made | an epic as it is **intended** |
| Horizon | the present | forever | until the epic ships |
| Written | **after** the change lands | **before** the change is implemented | **before** the epic starts, kept current throughout |
| On change | edited in place | never edited; superseded by a new ADR | edited in place |
| Answers | "how does this work?" | "why is it like this, and may I change it?" | "what are we building, and how far along are we?" |

When any two disagree, the **ADR wins** — it is the only one of the three that records a binding
decision. The overview is a map. A design document is a plan, and plans describe work that may not
exist yet.

> A design document is **not** a description of the current app. Read its progress table before
> assuming any of it is implemented.

# Architecture Decision Records

Store ADRs in `docs/architecture/decisions/` and maintain their index in
`docs/architecture/README.md`.

## When an ADR is required

Record a decision when it has lasting impact on system structure, dependencies, data ownership,
deployment, security, performance, platform support, or cross-feature conventions. Do not create
an ADR for routine implementation details or choices that are local and easily reversible.

## File naming

Use sequential, zero-padded names:

`NNNN-short-kebab-case-title.md`

Never reuse an ADR number, delete historical ADRs, or renumber existing records.

## Required format

```markdown
# ADR-NNNN: Decision title

- Status: Active | Disabled
- Date: YYYY-MM-DD
- Decision makers: Names or roles

## Context

Describe the problem, constraints, and forces that made a decision necessary.

## Decision

State the chosen approach clearly.

## Alternatives Considered

Describe credible alternatives and why they were not selected.

## Consequences

### Positive

- Expected benefit.

### Negative

- Cost, limitation, or risk accepted by this decision.

## References

- Related ADRs, issues, documentation, or source files.
```

## Status rules

- `Active`: the decision currently governs the project.
- `Disabled`: the decision no longer governs the project. Add `- Disabled date: YYYY-MM-DD` and
  either `- Superseded by: ADR-NNNN` or `- Disabled reason: ...` beneath the status metadata.
- A new ADR starts as `Active` once the decision is adopted.
- Do not rewrite the original context or decision when architecture changes. Create a new ADR,
  then mark the old one `Disabled` and link both records.

## Index

For every ADR change, update `docs/architecture/README.md` with its number, title, status, date,
and relative link. Keep entries ordered by ADR number.

# Maintaining the architecture overview

`docs/architecture/ARCHITECTURE.md` is required reading for every agent, so letting it drift
misleads every later task. It has an owner and a defined trigger.

## Owner

The **Planner** owns it, as it owns ADRs.

- A plan whose work hits any trigger below must include updating the overview as an **explicit,
  final step**, owned by Planner, with `docs/architecture/ARCHITECTURE.md` in its file list.
- **Coder and Designer do not silently rewrite it.** If implementation leaves the overview
  inaccurate, say so in your report and name the section. The Orchestrator routes the edit.
- The **Reviewer** checks that a triggering change updated it, and reports a miss as a finding.
- Working directly rather than through the agents does not remove the obligation; it just makes
  you the owner for that change.

## Timing

An ADR is written **before** implementation, because it is a decision others must follow. The
overview is updated **after**, in the same change, because it must describe what actually landed
rather than what was planned. Do not update it from a plan.

The *lands a new ADR* trigger below obeys the same rule, and it is the one that looks like a miss
when it is being obeyed. An ADR adopted **ahead** of the work it governs has nothing for the
overview to describe yet, so its link is added by the change that lands that work, not by the
change that adopts the decision. When you defer it for that reason, **say so where the deferral is
visible** — in the stage plan, or in the record of the change that adopted the ADR — and carry the
link as an exit criterion so it cannot be forgotten. A deferral nobody recorded is
indistinguishable from a trigger nobody applied.

## Triggers

Update the overview when a change does any of these. The list is deliberately concrete so that
"does this need a doc update?" is a lookup, not a judgement call. The last trigger carries the one
exception — *Timing* above makes it conditional on whether the ADR was adopted ahead of the work it
governs. The test is still mechanical, but do not apply that trigger without reading the paragraph.

- Adds, removes, or renames a **state holder** or a **ViewModel** (§3).
- Adds or removes a **`SharedPreferences` store** (§6).
- Adds, removes, or reorders a branch in `MainActivity`'s **navigation chain** (§4).
- Changes which **`PreferenceScope`** a derived flow is bound to, or adds a resolution layer to
  the Folder Detail stack (§5).
- Adds, removes, or retargets a **main view** (§1).
- Adds or removes a **dependency** that appears in the tech-stack table, or changes a pinned
  version (§10).
- Adds a file to `data/` (§3 lists them individually).
- Resolves, or newly introduces, an item in **Known debt** (§11).
- Lands a new ADR, which needs its link added to the section it governs.

A change that only adds a control, a string, a dialog, or a test does **not** trigger an update.
Neither does a bug fix that leaves the structure intact.

## How to write it

- Describe structure; do not restate decisions. Where a section is governed by an ADR, link to
  the ADR and summarise it in at most two sentences. Anyone needing the reasoning follows the
  link.
- Never contradict an active ADR. If the overview and an ADR disagree, the overview is wrong.
- Keep the section numbering stable — the triggers above and the Reviewer's checks cite it.
- Record a limitation you accepted in Known debt rather than omitting it.

# Maintaining an epic design document

An epic large enough to span several changes gets a design document under `docs/design/`, named
for the epic. It carries the product narrative, the decisions taken, the open questions, and the
progress table. Its purpose is that none of that has to be rediscovered by whoever picks the epic
up next — possibly weeks later, in a fresh context, having read none of the conversation that
produced it.

Current epics: [albums.md](../../docs/design/albums.md).

## Owner

The **Planner**, as with ADRs and the overview.

- **Coder and Designer do not edit it.** If implementation contradicts it, or reveals it to be
  wrong or incomplete, say so in your report and name the section. The Orchestrator routes the
  edit.
- The **Reviewer** checks that a change advancing an epic left its document true, and treats a
  stage marked Complete whose exit criteria are not all met as a hard violation.

## What it must contain

- **A progress table** that is the single source of truth for where the epic stands, with one
  status per stage.
- **Exit criteria per stage**, concrete enough that "complete" is checkable rather than a feeling.
  Write them for a stage when that stage is planned; do not invent them years ahead. **A stage that
  has a plan of its own keeps its criteria there while the plan is open**, and they return here
  when it is archived — see *Stage plans* below. The row for such a stage says where they are.
- **A stage log** — one line per status change. The table is the present; the log is the history.
- **Decisions already taken**, so they are not relitigated. Anything with lasting technical weight
  also becomes an ADR; the list here is the index to them plus the product decisions that do not
  warrant one.
- **Open questions**, with a recommendation where one exists.

## When to update it

- A stage changes status, or an exit criterion is met or broken.
- A decision is taken, or a previously taken one is reversed.
- An open question is answered, or a new one is found.
- The shape of the epic changes — a stage added, split, reordered, or dropped.

Update it in the **same change** as the work it describes. A design document updated later is a
design document that was wrong in between, and the window is exactly when someone else picks it up.

## How to write it

- Record **why**, not just what. A decision without its reasoning gets reversed by the next person
  who sees only its cost.
- When you reverse a decision, say that you reversed it and why. ADR-0011's note that an earlier
  GDPR argument was overstated is worth more than silently deleting the claim, because it stops
  the bad reasoning being rediscovered and reused.
- Keep the section numbering stable — ADRs and agent instructions cite it.
- Never contradict an active ADR. If the document and an ADR disagree, the document is wrong.
- Do not let it drift into describing the current app. That is the overview's job, and a design
  document that blurs the two will be read as a description of something that does not exist.

## Stage plans

A stage large enough to span several sessions may be given a plan of its own beside the epic
document, named `<epic>-stage-N.md`. It is the only document here that is deliberately
short-lived, and it carries exactly what the epic document should not: that stage's step
breakdown, the stage's exit criteria, and the handoff block the next session starts from. The epic
document keeps the progress table, the decisions, and the open questions, and the stage plan never
restates them.

- **Owner: the Planner.** Routed, exactly as the epic document is.
- The epic document links to the stage plan from the row of the stage in progress, so there is one
  way in and no second source of truth about where the epic stands.
- **Archived, when the stage closes, means this.** Everything that outlives the stage — at minimum
  its exit criteria and the record that each was met — moves into the epic document, and the plan
  gains an `Archived — Stage N closed YYYY-MM-DD` line beneath its title and stops being
  maintained. **It is not moved and it is not deleted.** ADRs cite stage plans by path, and an ADR
  is never edited to chase a file that moved. An archived plan is a finished document at a stable
  address, which is what a citation needs.
- **Its row in [AGENTS.md](../../AGENTS.md)'s documentation map goes at the same moment**, because
  that map registers documents somebody is keeping true and an archived plan is no longer one.
  Replace the row with the next stage's plan if one is being opened; otherwise remove it. Leaving
  it listed makes the next reader walk a row that cannot be acted on.

