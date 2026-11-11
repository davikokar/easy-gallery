---
mode: agent
description: Audit the project's documentation for drift, contradictions, and claims that are no longer true.
---

# Documentation audit

Check every maintained document in this repository against the code, the build files, and each
other. Report findings; do not fix anything until the user says so.

The documentation map in [AGENTS.md](../../AGENTS.md) lists every document, its owner, and its
maintenance mode. That is the scope.

## Why this exists

Ownership and update triggers only catch the drift somebody anticipated. Everything else
accumulates silently until a document that agents are *required* to read is quietly wrong. This
audit is for the rest.

## What to check

### 1. Claims that duplicate a fact owned elsewhere

The most common failure in this repo, by a wide margin. Every one of these is a future lie because
nothing checks it:

- Counts of anything — ADRs, locales, tests, screens.
- Line counts and file sizes.
- Dependency and version lists that restate `gradle/libs.versions.toml` or `app/build.gradle.kts`.
- SDK levels, toolchain versions, permission lists that restate `AndroidManifest.xml`.

For each: is it still true, and should it exist at all? Prefer deleting the claim and linking to
its source over correcting the number.

### 2. Statements contradicted by the code

Verify, do not assume. Particular things worth checking:

- Does `AGENTS.md`'s tech stack match `gradle/libs.versions.toml` and `app/build.gradle.kts`?
- Does `ARCHITECTURE.md`'s file list match what is actually in `data/` and `ui/`?
- Does the navigation chain it describes match `MainActivity.kt` branch for branch?
- Does the preference-store table match the `SharedPreferences` file names in `data/`?
- Does every composable named in the UI vocabulary still exist under that name?
- Do the test conventions match what the tests actually do, and what `app/build.gradle.kts`
  configures?

### 3. Documents contradicting each other

Precedence is: an **active ADR** beats `ARCHITECTURE.md`, which beats a design document, which
beats prose in `AGENTS.md`. Report any disagreement and say which one is wrong under that rule.

### 4. Dead and missing links

Every relative markdown link resolves. Every `applyTo` glob actually matches the files its
instructions are about — and does not match files they would mislead.

### 5. Epic progress honesty

For each design document under `docs/design/`: does its progress table match reality? Is any stage
marked Complete whose exit criteria are not all met? That is a hard violation, not a nit.

### 6. Orphaned documents

Anything in `docs/` or `.github/` not listed in the documentation map, and anything in the map that
no longer exists.

## How to report

Group by document. For each finding give the file and line, what is wrong, the evidence that it is
wrong — quote the code or build file — and a recommendation: **delete the claim**, **correct it**,
or **route it to its owner**.

End with a count per document and the single worst finding overall.

Separate hard problems from nits. A document that is merely terse is not a finding. A document
that states something false is.

If a document is clean, say so plainly. Do not invent findings to look thorough.
