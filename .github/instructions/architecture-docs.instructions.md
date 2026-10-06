---
name: Architecture documentation
description: "Use when creating, reviewing, updating, disabling, or superseding Architecture Decision Records (ADRs), when work introduces a significant architectural choice, or when a change alters the structure described by the architecture overview."
applyTo: "docs/architecture/**/*.md"
---

# Architecture documentation

`docs/architecture/` holds two different kinds of document, and they are maintained differently:

| | [ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) | [decisions/](../../docs/architecture/decisions/) |
|---|---|---|
| Describes | the system as it **is** | a choice and **why** it was made |
| Written | **after** the change lands | **before** the change is implemented |
| On change | edited in place | never edited; superseded by a new ADR |
| Answers | "how does this work?" | "why is it like this, and may I change it?" |

When the two disagree, the ADR wins. The overview is a map, not a contract.

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

## Triggers

Update the overview when a change does any of these. The list is deliberately concrete so that
"does this need a doc update?" is a lookup, not a judgement call.

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
