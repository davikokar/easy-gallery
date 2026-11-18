# Albums — rule editing

> **Owner: Planner. Mode: routed.** This is a plan of the same kind as a stage plan, not a
> description of the app. **Rule editing is not a stage**: it sits outside the staged sequence of
> [albums.md](albums.md) §7, independent of content (Stage 4) and of people (Stage 3) — §8 decision
> 39, amended 2026-11-17. It is open, paused or archived under the stage-plan rules in
> [architecture-docs.instructions.md](../../.github/instructions/architecture-docs.instructions.md),
> and is archived when editing ships. The epic itself is [albums.md](albums.md); **its §7 table is
> the single source of truth for where the epic stands**, and this document never restates it.
>
> **This is a draft.** Written 2026-11-17. Already decided: [albums.md](albums.md) §8 decisions 26,
> 31, 36 and 39. Decision 37's below-threshold rule applies **only if content albums ever resume**.
> The steps, the exit criteria and every recommendation below are proposals until the maintainer
> answers the open questions and step R1 records the decisions. Where something was not known, it is
> a question, not a guess.

---

## Next session starts here

> **Rewrite this block as the last action of every session.** It is the handoff.

- **2026-11-17 — plan written; not started.** It takes over
  [albums-stage-4.md](albums-stage-4.md) step 4.2 and that plan's group D questions, moved here by
  the maintainer (decision 39, amended 2026-11-17).
- **Next action: the maintainer answers the open questions** — D1, D3 and D5 block step R1. D2 and
  D4 are answered.
- **Read first, on starting:** [albums.md](albums.md) §8 decisions 26, 31, 36 and 39;
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md);
  [ADR-0016](../architecture/decisions/0016-album-creation-type-choice-and-picker-screen.md)
  Amendment 2; [ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md); and D2's
  record in [albums-stage-4.md](albums-stage-4.md), which carries the facts about display overrides
  and the stored cover.
- **Binding on everything below:**
  - [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md): a rule is a
    conjunction of typed clauses; an unknown clause makes the album unevaluatable, and such a rule is
    never rewritten.
  - [ADR-0015](../architecture/decisions/0015-album-cover-persisted-random-member.md) and its
    Amendment 1: the cover and its fallback.
  - [ADR-0021](../architecture/decisions/0021-per-album-display-preference-overrides.md): an
    album's display overrides are keyed by its id.
  - [ADR-0013](../architecture/decisions/0013-albums-as-third-display-mode.md): the navigation
    chain; a new screen is a new branch (D1).
  - [albums.md](albums.md) §8 decision 16: places and dates are picked, never typed.
  - §8 decision 29: no Incomplete-album notice and no coverage notice on an album or on the New
    automatic album screen.

---

## What the user gets

The user can **edit an automatic album's place and date rule after creating it**. Today a wrong
rule is fixed only by deleting the album and creating it again (decision 31), which loses its manual
additions and exclusions.

The Album type dialog does not change. Album info stays read-only.

## Already decided

Linked, not restated:
- **An edit changes only the rule.** Additions, exclusions, the cover and the display overrides are
  untouched — [albums.md](albums.md) §8 decision 36, amended 2026-11-15.
- **The cover follows membership, not the rule** — decision 36's 2026-11-15 and 2026-11-16
  amendments.
- **An unevaluatable rule is never rewritten** —
  [ADR-0017](../architecture/decisions/0017-composable-album-rule-representation.md); decision 26
  states what that means for an editor.
- **Album info stays read-only**, and decision 31's sentence that a wrong rule is fixed only by
  deleting and recreating the album is updated when editing lands — decision 31, amended
  2026-11-15.
- **Editing does not wait on ML** — decision 39 and its 2026-11-17 amendment.

---

## Steps

Proposals until step R1 confirms them.

| Step | Delivers | Owner (proposed) | Status |
|---|---|---|---|
| **R1** | The decisions: the edit surface (D1) as an ADR-0016 amendment or a new ADR, ADR-0013 if a branch is added, D3, and these exit criteria confirmed | Planner | ⬜ Not started — blocked on D1, D3, D5 |
| **R2** | The ViewModel and the store write | Coder | ⬜ Not started |
| **R3** | The edit UI and its entry point | Designer, then Coder | ⬜ Not started |
| **R4** | Strings in every shipped locale | Coder | ⬜ Not started |
| **R5** | UI vocabulary additions, in-change | Coder or Designer | ⬜ Not started |
| **R6** | Tests, including the round-trip of every rule this build can parse | Coder | ⬜ Not started |
| **R7** | Device verification and installed-app walkthrough | verification phase | ⬜ Not started |
| **R8** | Close-out: `ARCHITECTURE.md`, decision 31's sentence, audit, archive | Planner | ⬜ Not started |

### R1 — The decisions

- **The edit surface (D1)**, as an ADR-0016 amendment or a new ADR. If it adds a `MainActivity`
  branch, ADR-0013's chain is touched as well.
- **The unknown-clause case (D3).**
- **The entry point (D5)**, by the Designer.
- This step also confirms or changes the exit criteria below.

### R2 — ViewModel and store write

`AlbumStore` has no write that changes an existing album's rule today; it creates, renames and
deletes. The edit **updates** the album row's rule in place rather than replacing the row, because
the display overrides and the stored cover hang off the album (D2's record). Which ViewModel owns
the edit state follows D1; `GalleryViewModel` gains nothing (`AGENTS.md`, *State ownership*).

### R3 — The edit UI

Per D1 and D5.

### R4 — Strings

Every new string in every shipped locale (`LocaleHelper.supportedLanguageTags`), enforced by
`LocaleResourceParityTest`.

### R5 — Vocabulary

Additions to [ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md)
in the same change; any rename is routed to the Designer.

### R6 — Tests

- Round-trip: every rule this build can parse, including combinations creation never writes, comes
  back from the editor unchanged when nothing is changed.
- An unevaluatable rule is not rewritten, whatever D3 decides about editing it.
- An edit leaves additions, exclusions, the cover and the display overrides untouched.
- The cover follows membership through an edit.

### R7 — Verification

The definition of done, once, over the integrated change, with `adb devices` checked and stated.
Then an installed-app walkthrough: create a place-and-date album, add and remove an item by hand,
pick a cover, edit the rule, and see the members change with the hand edits and the cover kept.

### R8 — Close-out

- Decision 31's delete-and-recreate sentence updated in [albums.md](albums.md).
- `ARCHITECTURE.md` for what landed — at minimum if a `MainActivity` branch or a state holder was
  added.
- Exit criteria checked against the repository.
- `/docs-audit`.
- Archive this plan, per the stage-plan rules.

---

## Exit criteria — draft, confirmed or changed at R1

**Not binding until R1.**

- The checks in `AGENTS.md`'s definition of done pass, with `adb devices` checked and the result
  stated.
- Every step is ✅, or explicitly dropped with the drop recorded in this plan.
- Every rule this build can parse round-trips through the editor unchanged. An unevaluatable rule is
  never rewritten.
- An edit changes only the rule: additions, exclusions, the cover and display overrides are left
  alone, each proved by a test (decision 36, amended).
- The edit updates the album row rather than replacing it, so the album keeps its id.
- Album info is still read-only, and decision 31's sentence is updated.
- Every new string is in every shipped locale; the vocabulary has its additions; `ARCHITECTURE.md`
  describes what landed; a documentation audit has passed.

---

## Open questions

Numbered as they were in [albums-stage-4.md](albums-stage-4.md) group D, from which they moved on
2026-11-17.

1. **D1 — Edit surface:** the New automatic album screen in an edit mode, or a screen of its own?
   *Blocks R1.* ADR-0016 Amendment 2 argued against a mode for *manual versus automatic* because
   the inputs differed; for editing, the inputs are identical. A new screen is another branch in
   `MainActivity`'s chain. ADR-0013 defers the chain's future to Stage 3, which is now next
   (decision 13, amended 2026-11-17), so whether this lands before or after that revisit matters to
   both.
2. **D2 — Additions and exclusions on edit.** ✅ **Answered 2026-11-15 by the maintainer: an edit
   changes only the rule** ([albums.md](albums.md) §8 decision 36, amended). The full record, with
   the cover and display-override facts behind it, stays in
   [albums-stage-4.md](albums-stage-4.md) D2.
3. **D3 — A rule with an unknown clause:** refuse editing entirely, or edit the known clauses while
   preserving the unknown one untouched? *Blocks R1.* *Leaning refuse*: simpler, and it cannot widen
   the album. **If content albums ever resume**, an album with a content criterion is unevaluatable
   on a device below the threshold and the editor does not edit it there (decision 37, reversed
   2026-11-16), a second unevaluatable case.
4. **D4 — Ship editing before the ML steps?** ✅ **Moot; answered 2026-11-17** by decision 39 and
   this plan: editing no longer waits on ML.
5. **D5 — Entry point** in Album Detail View's Overflow menu, beside Album info. *Blocks R3;
   Designer.* The menu today has Change album thumbnail before Album info (decisions 32 and 31).
6. **D6 — Order with Stage 3.** *Raised by the Planner 2026-11-17; blocks nothing yet.* Whichever
   ships second inherits the other: if Stage 3's `people` clause lands first, the editor must edit
   or refuse it; if editing lands first, Stage 3 adds the People criterion to the editor.

   **Amended 2026-11-17 — constrained, not answered.** By [albums.md](albums.md) §8 decision 42,
   a people album whose anchor photo is deleted, or whose face is no longer found, is unevaluatable,
   and the user fixes it by picking the person again through rule editing. So whichever ships
   second, the editor must **edit** the people criterion; "or refuse it" is no longer an option. The
   order itself is still open, so D6 is only constrained. It also bears on D3: that album is
   unevaluatable on a rule this build understands, not through an unknown clause, so D3's leaning
   to refuse editing must not reach it.

---

## Related

- [albums.md](albums.md): §3 membership, §8 decisions 26, 28, 31, 32, 36, 37 and 39.
- [albums-stage-4.md](albums-stage-4.md): paused; where step 4.2 and group D came from.
- [albums-stage-3.md](albums-stage-3.md): Stage 3's draft plan (D6).
