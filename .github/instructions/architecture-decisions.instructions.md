---
name: Architecture Decision Records
description: "Use when creating, reviewing, updating, disabling, or superseding Architecture Decision Records (ADRs), or when work introduces a significant architectural choice."
applyTo: "docs/architecture/decisions/**/*.md"
---

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
