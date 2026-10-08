# ADR-0017: An automatic album's rule is a composable predicate

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

[ADR-0012](0012-one-album-type-rule-additions-exclusions.md) settled that there is one album type
and that its members are `(rule matches ∪ additions) − exclusions`. It deliberately said nothing
about what a rule *is*. `AlbumEntity` carries a nullable `rule: String?` column, shipped in schema
version 1 and never yet written to; Stage 2 is the first stage that populates it.

Three forces decide its shape, and all three point past Stage 2.

**The epic needs compound rules eventually.** Stage 2 delivers region and time
([albums.md](../../design/albums.md) §4); Stage 3 adds people and Stage 4 adds content. "These two
people, in Italy, in 2025" is a rule that spans all three families. If each stage invents its own
storage, that rule is a fourth thing to invent, after three migrations.

**A rule is always read and written whole.** Resolution loads an album and evaluates its rule
against the index. Nothing asks "which albums have a region clause"; nothing updates one clause of
a rule in place. That is the access pattern of a document, not of a relation.

**Evaluation is not SQL.** Region and time clauses are a filter over indexed columns, but content
and people clauses are vector comparison across the library (albums.md §6). Any representation
that assumes the rule can be translated into a query is wrong by Stage 3.

A fourth force comes from backup. [ADR-0014](0014-album-database-included-in-auto-backup.md) puts
the album database into Android auto-backup, so a rule written by a newer version of the app can
arrive, intact, on an older one — and the same happens on a downgrade. Whatever the format is, an
old parser will one day meet a clause type it has never heard of, and what it does then is a
decision rather than an accident.

## Decision

**An automatic album's rule is a conjunction of typed clauses, serialised as one JSON document in
the existing `albums.rule` column.**

- The envelope is `{"version": 1, "all": [ <clause>, … ]}`. Each clause is an object carrying a
  `"type"` discriminator plus the fields that type defines. `"all"` names the conjunction
  explicitly, so adding `"any"` or `"none"` later is an addition to the format rather than a break
  in it.
- **No schema change.** `albums.rule` is already nullable `TEXT`. A rule is `NULL` for a manual
  album — ADR-0012's "empty rule" — and a document for an automatic one. An empty `"all"` array is
  never written; "no rule" is `NULL` and only `NULL`.
- **Stage 2 implements two clause types**, `time` and `region`. `people` and `content` are
  anticipated by the format and simply absent. Stage 3 therefore adds a clause type and a
  resolver, not a migration.

**The scope is deliberately narrow: conjunction only.** No disjunction, no negation, and no
nesting beyond this single level. "Italy or France" is not expressible, and the honest answer
today is two albums. Widening this is a new ADR, not an implementation detail, because every
consumer — the parser, the evaluator, the editing UI, and the unknown-clause rule below — gets
harder at the same moment.

**An unknown clause type makes an album unevaluatable. It does not make it empty, and it does not
make it everything.** Parsing yields either a fully understood rule or an `Unevaluatable` marker
that carries the raw document unchanged. An unevaluatable album:

- **resolves to its manual additions minus its exclusions.** The rule contributes nothing. Nothing
  is invented and nothing the user chose by hand is lost.
- **says so.** It is shown with a visible notice that it uses a feature from a newer version of
  Easy Gallery, in both Albums View and Album Detail View — the same two surfaces Stage 1's
  `album_hidden_items_notice` had to reach, for the same reason: an album that is quietly wrong is
  worse than one that explains itself (albums.md §6).
- **is never rewritten.** No save path may persist a rule it could not fully parse, and in
  particular may not "helpfully" drop the clause it did not understand. In a conjunction, removing
  a clause *widens* the result — an album that meant "in Italy, of these people" would silently
  become "of these people", permanently, on the next edit.
- **is never deleted**, by direct analogy with ADR-0010's rule that an unresolvable membership is
  retained rather than dropped.

The same treatment applies to an unknown envelope `version` and to malformed JSON. All three land
on `Unevaluatable`; none of them throws.

## Alternatives Considered

**A column per clause family on `albums`** — `fromDate`, `toDate`, `countryCode`, `admin1Code`, and
so on. The most obvious answer and the most queryable. Rejected because it charges a migration to
every stage of the epic, and ADR-0009 made migrations a deliberate, permanent discipline rather
than a cheap operation. It also cannot express two clauses of the same family, and it produces a
wide, almost entirely null row for the manual albums that are the common case.

**A `rule_clauses` child table, one row per clause.** Genuinely credible: it is queryable in SQL,
it handles an unknown `type` string as ordinary data, and it fits the relational model the album
tables already use. Rejected because the query ability buys nothing — no code path wants to find
albums by clause — while the costs are real: a table, a foreign key, a clause ordering to define
and maintain, and a second write path for a value that is always read and written as a whole. A
value that is only ever handled whole belongs in one column.

**A full boolean expression tree, with AND, OR, NOT and nesting.** More expressive, and barely
harder to serialise. Rejected because the difficulty is not in the format. Evaluation needs
short-circuit semantics; the UI needs an expression editor, which is a substantial feature nobody
has asked for; and the unknown-clause rule above stops being stateable, because an unknown clause
under a negation widens or narrows depending on where it sits in the tree. Conjunction answers the
requirement that prompted this ADR.

**A query string — a SQL fragment, or a small expression DSL.** Rejected on three counts: it is an
injection surface written into a file that auto-backup carries between devices; it cannot describe
vector similarity, so Stages 3 and 4 would need a second mechanism anyway; and it cannot be
validated without being executed.

**Protobuf, or another binary encoding.** Smaller, and versioned by construction. Rejected because
it costs a dependency and a code-generation step to save a few hundred bytes per album, and because
it is unreadable in a bug report and in a database dump. JSON can be read by the person trying to
work out why a restored album is behaving oddly, which is precisely when it matters.

## Consequences

### Positive

- Stage 3 adds a clause type rather than a schema. The column, the parse contract, and the
  unknown-clause behaviour are settled once, before any of them has a second caller.
- Compound rules fall out for free. There is no "and also" feature to build later.
- **Stage 2's rules need no schema change at all**, because `albums.rule` has been nullable `TEXT`
  since version 1. The first index-related migration is therefore in the *other* database
  ([ADR-0018](0018-media-index-in-a-second-database.md)), not this one.
- An album written by a newer version degrades into something a user can read and recover from,
  instead of into an empty album that is indistinguishable from data loss.
- Parsing and evaluation are pure and Android-free, so they are unit-testable on a plain JVM
  alongside `AlbumMembership.kt` and `GalleryTransformations.kt`.

### Negative

- JSON in a column is opaque to SQL. "Which albums use a region clause?" becomes a Kotlin scan over
  every album. Accepted, because nothing needs to ask.
- Without a serialisation dependency the parser is hand-written, and a hand-written parser is
  exactly where the unknown-clause rule will be got wrong. Unknown clause type, unknown envelope
  version, and malformed JSON each need their own test, and all three must land on `Unevaluatable`
  rather than on an exception escaping into `viewModelScope`.
- Conjunction-only will be felt. Two albums is a worse answer than one album with an `OR`, and the
  pressure to widen the format will come from users rather than from developers.
- An unevaluatable album needs a user-facing string in all ten locales and a notice on two
  surfaces. That is real work created by a case that should never occur on a device that has only
  ever moved forward.
- The format cannot enforce that a clause and the index agree about time zones, units, or code
  vocabulary. A `region` clause storing an admin1 code means nothing unless the indexer writes the
  same vocabulary. That contract is held by tests and by ADR-0019, not by the schema.

## References

- [ADR-0012](0012-one-album-type-rule-additions-exclusions.md) — one album type; this ADR gives its
  "rule" a concrete form and must not contradict it
- [ADR-0009](0009-room-as-first-owned-datastore.md) — the datastore, and the migration discipline
  this decision is shaped to avoid paying per stage
- [ADR-0010](0010-album-membership-dual-key-identity.md) — the retain-rather-than-drop precedent the
  unknown-clause rule follows
- [ADR-0014](0014-album-database-included-in-auto-backup.md) — why a newer version's rule can arrive
  on an older app
- [ADR-0018](0018-media-index-in-a-second-database.md) — the index a rule is evaluated against
- [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) — the vocabulary a `region` clause
  is written in
- [docs/design/albums.md](../../design/albums.md) §3, §4, §6, §7;
  [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.5
- `app/src/main/java/com/davide/seddio/easygallery/data/AlbumEntities.kt` — the `rule` column this
  decision fills
