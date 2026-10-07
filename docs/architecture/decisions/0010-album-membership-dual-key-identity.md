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

> **Amended 2026-10-07 — see Amendment 1 below.** Wherever this section names the `MediaStore`
> `_ID` as the primary key — the bullet above and steps 1 and 2 below — read **the full content
> URI string** instead. `_ID` is not unique in this app, because images and videos are queried as
> two separate collections with independent `_ID` spaces. The healing key and the three-step
> resolve → heal → orphan behaviour are unchanged. The original text is left intact.

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

## Amendment 1 — 2026-10-07 — The stored primary key is the content URI, not the `_ID`

This amendment corrects **one bullet and two steps** of the Decision: what the primary key is, and
what healing rewrites. Everything else in the record is unchanged and continues to govern — the
healing key and its three columns, the resolve → heal → orphan order, orphans being retained and
never deleted, orphans being re-checked on later resolutions, the proactive re-heal pass on a
`MediaStore.getVersion()` change, and every alternative rejected in Alternatives Considered.

It is recorded as an amendment rather than a superseding ADR, following ADR-0005 Amendment 1 and
ADR-0007 Amendments 1 and 2. The decision with lasting structural impact — a dual key, self-healing
on miss, never deleting — has not stopped applying. What changed is which string the primary key
is. Disabling this record and restating it in a new one would retire a decision that still governs.
The superseded sentence in the Decision is annotated in place and left intact.

### The defect

The original record assumed the `MediaStore` `_ID` identifies a media item within this app. **It
does not.** `MediaStoreDataSource.queryMedia` is invoked twice, once against
`MediaStore.Images.Media.EXTERNAL_CONTENT_URI` and once against
`MediaStore.Video.Media.EXTERNAL_CONTENT_URI`, and each collection has its own `_ID` space. Image
24 and video 24 are different files that coexist on every device. A membership row holding the bare
integer `24` cannot say which one it means.

This was invisible at the model level, which is why it survived into the record: `MediaItem`
exposes only a `Uri` and never the raw ID, so nothing in the code the ADR was written against ever
shows the two ID spaces side by side. The collection is chosen in the data source and then
disappears into the URI.

### What changed

- **The stored primary key is the full `MediaStore` content URI string** — for example
  `content://media/external/images/media/24`. It encodes the volume, the collection and the ID, and
  is therefore unique across the whole of what this app queries. It is also exactly what
  `MediaItem.uri` already holds, built by `ContentUris.withAppendedId`, so the stored form is
  canonical and needs no normalisation before comparison.
- **Healing rewrites the stored URI**, not an ID. Step 2 is otherwise identical: healing-key hit,
  rewrite, resolve.
- The common path keeps the same cost. It is a string equality instead of an integer equality,
  against a column that is indexed either way.

Storing a URI string as a media reference is already the repository's practice: ADR-0007 persists
`Folder.path -> MediaItem.uri.toString()` for folder thumbnail overrides. This amendment makes
album membership consistent with it rather than inventing a second convention.

### Resolution must never call `Uri.parse`

**Resolve by comparing the stored `String` against `item.uri.toString()`, and then use the live
`MediaItem`'s own `Uri` instance.** Never reconstruct a `Uri` from the stored string.

Two independent reasons, and the first is a hard one:

- `android.net.Uri.parse` is a stubbed platform method. This module does not set
  `isReturnDefaultValues`, so calling it in ViewModel or test code throws
  `java.lang.RuntimeException: Method parse in android.net.Uri not mocked`. Repository memory
  records this costing a full session already, during ADR-0007 Amendment 2, which reached the same
  rule for the same reason.
- Reusing the live item's `Uri` instance keeps identity comparisons working. Set and map lookups
  keyed on `Uri` match by identity against MockK-produced instances in unit tests; a freshly parsed
  equivalent `Uri` does not.

### Consequences of this amendment

**Positive**

- The primary key is actually unique, which is what the original record believed it was.
- No parsing anywhere on the resolution path — the comparison is between two strings the app
  already holds.
- Consistent with the only existing precedent in the repository for persisting a media reference.

**Negative**

- A membership row is wider than it would be with a bare integer. Negligible against the three
  healing-key columns it already carries.
- A content URI embeds the volume name. Media on a removable volume whose name changes misses the
  primary lookup even though the file is unchanged, and falls through to the healing key. That is
  the designed path and it resolves correctly, but it means the card-remount case is now more
  likely to heal than to hit directly. The user still sees nothing.
- After a backup restore (ADR-0014) every stored URI misses at once, because the whole key space is
  device-local. That is the case ADR-0014 exists to reason about, and healing is what makes it
  survivable.

### References added by this amendment

- ADR-0007 Amendment 2 — the same `Uri.parse` prohibition, reached independently
- ADR-0014 — backup and restore, which turns "the primary key is device-local" into a whole-library
  heal
- ADR-0015 — the album cover column, which stores no key of its own: it is a foreign key to the
  membership row holding this one, and so heals when that row does
- `/memories/repo/easy-gallery-exploration.md` — the "not mocked" trap, recorded after it was hit
