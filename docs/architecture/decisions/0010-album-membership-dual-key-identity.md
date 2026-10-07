# ADR-0010: Album membership identifies media by a self-healing dual key

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

An album stores references to media items rather than copies of them (`docs/design/albums.md` §2).
The question this ADR settles is what a reference actually is.

`MediaItem` carries only a `Uri`, built in `MediaStoreDataSource` as
`ContentUris.withAppendedId(collectionUri, id)` from the `MediaStore` `_ID` column. That identifier
looks durable and is not:

- Another app moving a file can cause the row to be deleted and re-inserted, with a new `_ID`.
- A volume being unmounted and remounted, or a media rescan, can renumber rows.
- `MediaStore.getVersion()` exists precisely because the platform expects apps caching its
  identifiers to discover they have gone stale.

The obvious alternatives are no better in isolation. The absolute path breaks on rename and on
move — and the app itself offers "Move to", so it would invalidate its own album memberships. A
content hash is genuinely stable but requires reading every byte of every file, which on a large
library is the same O(n) file-open cost that ADR-0001 refused for EXIF reads.

There is a precedent in this repository, and it is the wrong one to follow. ADR-0007 resolves a
persisted folder-thumbnail override against live media and, when no match is found, falls back
silently to the automatic thumbnail. For a thumbnail that behaviour is correct: the worst outcome
is a different picture on a folder tile. For album membership the identical code is **silent data
loss** — the user's curated album quietly shrinks, with no error and no way to tell that anything
happened.

The failure mode that matters most is not exotic. A user with photos on a removable SD card pops
the card out; every membership referencing it fails to resolve. If failure means deletion, putting
the card back does not bring the albums back.

## Decision

Store **two keys** per membership row and resolve them in order, healing as a side effect.

- **Primary key:** the `MediaStore` `_ID`, as today.
- **Healing key:** `DISPLAY_NAME` + `SIZE` + `DATE_MODIFIED`. All three are already in the cursor
  projection `MediaStoreDataSource` uses, so capturing them costs no extra query columns and no
  file reads.

Resolution of a membership proceeds:

1. Look up by `_ID`. A hit resolves immediately. This is the overwhelmingly common path and carries
   no extra cost.
2. On a miss, search the current media set by healing key. On a hit, **rewrite the stored `_ID`**
   and resolve. The album has healed itself and the user never sees a problem.
3. On a second miss, mark the membership **orphaned — never delete it.** An orphaned row is
   retained in the database and hidden from the album's contents.

An orphaned membership is re-checked on subsequent resolutions, so a file that reappears — the SD
card is reinserted, the user restores from a backup, another app moves the file back — rejoins its
albums automatically.

A change in `MediaStore.getVersion()` since the last run triggers a proactive re-heal pass rather
than waiting for each membership to be resolved lazily.

## Alternatives Considered

**`_ID` alone.** Simplest, and correct almost all of the time. Rejected because its failure mode is
silent, permanent, and lands on exactly the users who care most — those with large curated
libraries on removable storage.

**Absolute path alone.** Stable across the re-insertion case that breaks `_ID`, but broken by
rename and by move, including moves the app itself performs. Rejected as trading one silent failure
for a more frequent one.

**Content hash.** The only genuinely stable identifier, and rejected on cost. Hashing every member
on every resolution is unaffordable; hashing once and storing it still requires reading every file
at least once, which is precisely the eager-full-library-read that ADR-0001 established as
unacceptable. Revisit only if the dual key proves insufficient in practice.

**`DOCUMENT_ID` / XMP original document ID.** Designed for this purpose, but not populated for most
camera output and not queried by this app today. Rejected as unreliable in the general case, though
it would make a reasonable third tier if evidence later supports it.

**Deleting unresolvable memberships, per ADR-0007's pattern.** Rejected explicitly. See Context.

## Consequences

### Positive

- The common path is unchanged in cost: a lookup by `_ID`.
- Albums survive the realistic disruptions — a file moved by another app, a rescan, a card
  remounted — without the user ever being aware there was something to survive.
- Healing is self-repairing rather than a maintenance operation: the stored `_ID` is corrected as a
  side effect of normal use.
- Nothing is ever lost silently, which is the property that makes albums trustworthy enough to
  curate.

### Negative

- The healing key is not guaranteed unique. Two files with the same name, byte size and
  modification time — duplicates copied together, or burst exports — can collide, and a heal could
  attach a membership to the wrong twin. The consequence is one wrong photo in one album, not data
  loss, and it is accepted.
- Orphaned rows accumulate with no garbage collection. A user who deletes a thousand photos keeps a
  thousand dead membership rows. They are small, and the alternative is the data loss this ADR
  exists to prevent.
- Membership rows are wider than a bare ID, and every membership write captures three extra
  columns.
- "Hidden but retained" is a state the UI must handle honestly: an album's displayed count can be
  lower than its stored membership count, and that difference must not look like a bug.

## References

- `docs/design/albums.md` §5 — identity, stated as the highest-risk Stage 1 decision
- ADR-0001 — why eager full-library file reads are refused, which rules out content hashing
- ADR-0007 — the resolve-and-fall-back-silently pattern that must *not* be reused here
- ADR-0009 — the datastore these rows live in
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — the existing
  cursor projection that already supplies the healing key columns
