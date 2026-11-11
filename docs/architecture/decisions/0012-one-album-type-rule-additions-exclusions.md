# ADR-0012: One album type — rule, additions, and exclusions

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

The albums epic introduces four kinds of album: manual, Favourites, content, people, and
region/time (`docs/design/albums.md` §4). Two of those shapes look fundamentally different:

- A **manual album** is a container. Its contents are the list the user put in it.
- An **automatic album** is a query. Its contents are whatever currently matches a stored rule.

Modelled naively these are different types with different storage, different lifecycles, and
different invalidation. A container is written once and read back; a query is re-evaluated whenever
the library changes.

The requirement that collapses them is that **automatic albums must be hand-correctable**: the user
can remove a photo an automatic album wrongly matched, and add one it missed. "Everything matching
*castles*, except this one, plus that one."

That requirement is incompatible with modelling an automatic album as a pure query, because a pure
query has nowhere to record the exception. Once an automatic album must carry a set of manual
additions and a set of manual removals, it has become a container with a rule attached — which is
the same thing a manual album is, with the rule empty.

A further constraint comes from re-evaluation. If a removal is stored merely as "absent from the
result", the next re-evaluation re-matches the photo and puts it back. The user removes it again,
and it returns again. A removal has to be a positive, persisted fact.

## Decision

There is **one album type**. Every album, of every kind, resolves its members as:

```
members = (rule matches ∪ manual additions) − manual exclusions
```

- A **manual album** has an empty rule. Its members are its additions.
- An **automatic album** has a rule, and may also carry additions and exclusions.
- Exclusion takes precedence over inclusion. If an item is both explicitly added and explicitly
  excluded, it is out.

**An exclusion is a tombstone**, stored as a positive fact rather than inferred from absence. "This
item does not currently match the rule" and "the user removed this item" are different states and
are stored differently. The tombstone persists across re-evaluations, so a removed item stays
removed even while it continues to match.

Additions and exclusions both reference media items, and so both use the dual-key identity and
healing defined in ADR-0010. A tombstone that cannot be resolved is retained, not dropped — losing
one re-admits a photo the user deliberately removed.

**Favourites is deliberately outside this model.** It is a `MediaStore` query with no stored
membership of any kind (ADR-0011). It is an album in the user interface and not in the schema.

## Alternatives Considered

**Two types — `ManualAlbum` and `AutomaticAlbum`.** The obvious modelling, and defensible while
automatic albums are read-only. Rejected because hand-correction gives the automatic type both of
the manual type's sets, at which point the two differ only in whether a rule field is populated.
Two types would mean two storage shapes, two resolution paths, and a UI that has to branch on
album kind for every operation that ought to be uniform.

**Automatic albums are read-only.** Removes the problem entirely and is markedly simpler. Rejected
on product grounds: every automatic classifier is wrong sometimes, and an album the user can see is
wrong but cannot fix is worse than no album.

**Converting an automatic album to a manual one on first edit.** Freezes the current matches into
an addition list and discards the rule. Simple, and it avoids tombstones completely. Rejected
because it silently destroys the thing the user wanted — the album stops tracking new photos the
moment they correct a single mistake, and nothing tells them that happened.

**Storing exclusions as negative weights or as a filter expression appended to the rule.** Rejected
as more expressive than the problem requires, and not implementable for rules whose matching is a
vector similarity rather than a predicate.

## Consequences

### Positive

- One schema, one resolution path, one set of UI operations across every album kind.
- A manual album is not a special case; it is the degenerate case, which means the common code path
  is also the simplest one.
- An automatic album can be corrected without losing its automatic behaviour, so a user fixing one
  wrong photo does not silently freeze the album.
- Changing an album's rule later, or adding a rule to an album created as manual, requires no
  migration — only populating a field.

### Negative

- Every album pays for three sets, including manual albums whose rule is always empty and whose
  exclusion set is almost always empty.
- Tombstones accumulate and are never garbage-collected. An automatic album repeatedly corrected
  grows a permanent exclusion list, and those rows outlive the media they reference.
- Resolution is a set operation rather than a table read, so the membership of an automatic album
  cannot be answered by a single simple query.
- Favourites breaks the uniformity this ADR establishes. Code that assumes every album has stored
  membership will be wrong about exactly one album, and that must be explicit wherever albums are
  enumerated.

## References

- `docs/design/albums.md` §3 — the membership model and the tombstone requirement
- ADR-0010 — the identity that additions and exclusions both depend on
- ADR-0011 — Favourites, the deliberate exception to this model
- ADR-0009 — the datastore the three sets live in
