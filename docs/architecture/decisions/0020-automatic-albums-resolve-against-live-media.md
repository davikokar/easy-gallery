# ADR-0020: An automatic album resolves against live media, and its index rows are a deletable cache

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

[ADR-0012](0012-one-album-type-rule-additions-exclusions.md) settled that every album resolves as
`(rule matches ∪ manual additions) − manual exclusions`, and
[ADR-0017](0017-composable-album-rule-representation.md) settled what a rule is. Neither says where
"rule matches" comes from at display time, and
[ADR-0018](0018-media-index-in-a-second-database.md) deliberately scoped itself to *where the index
lives* rather than to how it is read or reconciled.

That gap had a visible symptom. [albums.md](../../design/albums.md) §6 recorded a staleness window
in the deletion direction: because indexing is constrained to charging and device idle, an item
deleted from the device keeps its index row until the next constrained run, potentially for days,
and evaluation on app open does not rescue it because evaluation reads the index rather than the
device. §10 question 1 asked what the user is shown during that window, and whether reconciliation
should delete the index row or tombstone it. It was marked answered, reopened the same day on the
grounds that the mechanism had been settled but the behaviour had not, and **blocked step 2.2** of
[albums-stage-2.md](../../design/albums-stage-2.md), which cannot write reconciliation without it.

Two existing facts make the answer available rather than inventable.

**The app already joins stored references against live media.** `AlbumsViewModel.computeAlbum`
calls `AlbumMembership.resolve(memberships, liveMedia)`, where `liveMedia` is the `MediaStore`
result that `GalleryViewModel` loads via `repository.getAllMedia()` and pushes in through
`setAllMedia`. Nothing in an album's displayed contents is rendered from stored rows alone; every
displayed item is a live `MediaItem`. That refresh happens on launch, on permission grant, and
after each of the app's own media operations, including delete.

**Retention is not a universal rule, it is a rule about user intent.**
[ADR-0010](0010-album-membership-dual-key-identity.md) retains an unresolvable membership and never
deletes it, because a removed SD card and a deleted file are indistinguishable and the membership
may be a choice the user made. [ADR-0012](0012-one-album-type-rule-additions-exclusions.md) extends
the same reasoning to exclusion tombstones. An index row carries no such intent: it is a
recomputable fact about a file, and nobody picked it.

## Decision

**An automatic album's displayed contents are the index's matching set intersected with what
`MediaStore` currently returns. A deleted photo therefore disappears immediately — on the next
media read — and not at the next scan.**

### 1. The index supplies keys; live media supplies items

The index answers "which items match this rule" and nothing else. The album's contents are that
set of keys resolved against live media by the path `AlbumMembership` already implements:
direct match on the stored content URI, then the healing key, as
[ADR-0010](0010-album-membership-dual-key-identity.md) and its Amendment 1 define them. A key that
resolves to no live item contributes no item.

This holds for both evaluation strategies [albums.md](../../design/albums.md) §6 describes. A
region or time rule computed on read is filtered through the live set before display; a Stage 3 or
Stage 4 result materialised with a generation stamp is likewise intersected with the live set
before display rather than shown from the stored set. **Stages 3 and 4 inherit this**; it is not a
Stage 2 convenience.

### 2. The index row is a cache, and reconciliation deletes it

When a scan finds that an indexed item is gone, it **deletes the index row**. There is no
tombstone, no `isDeleted` column and no retention.

A tombstone would only earn its keep if something user-visible distinguished "deleted" from "never
indexed". Under the live join above, nothing does: an item absent from `MediaStore` is absent from
every album regardless of what the index says, so the distinction has no consumer. Keeping the
index honestly a cache is also what [ADR-0018](0018-media-index-in-a-second-database.md) already
assumes when it excludes the index from backup on the grounds that it is entirely recomputable.

### 3. Moved must not look like deleted

If the index keyed items by `_ID`, another app moving a file would read as a deletion, the photo
would vanish and then reappear after the next index run. The index therefore uses **the same
identity as album membership**, not a second convention:

- the stored key is the full `MediaStore` content URI string, per ADR-0010 Amendment 1 — not the
  bare `_ID`, which is not unique in this app because images and videos are queried as separate
  collections with independent `_ID` spaces;
- a miss falls through to ADR-0010's healing key, the `DISPLAY_NAME` + `SIZE` + `DATE_MODIFIED`
  triple that `AlbumMembership` already builds from a `MediaItem`'s `name`, `size` and
  `dateModified`;
- a heal **rewrites the stored key on the index row**, exactly as it rewrites a membership's, so
  the move costs one string comparison and no file I/O;
- ADR-0010 Amendment 1's rule that resolution never calls `Uri.parse` applies here unchanged.

That identity is private to `AlbumMembership.kt` today. Sharing it is an implementation question
for step 2.1 or 2.2, but a **second, independent copy of the healing key is not acceptable** — two
copies would drift and the drift would present as photos disappearing.

### 4. An unmounted volume is not a deletion

**This clause was not part of the question as posed; it is added because deletion and
unavailability are indistinguishable at the point reconciliation runs.** ADR-0010 exists because of
exactly that confusion, and the index meets it from the other side: a removed SD card makes a whole
volume's rows vanish from a `MediaStore` query at once, and deleting them would discard minutes of
file-opening work that has to be redone when the card goes back in.

Reconciliation therefore deletes a row only for an item missing from a volume that is currently
present. The platform supports this directly and recommends it:
`MediaStore.getRecentExternalVolumeNames(Context)` returns volumes that are not mounted but are
likely to reappear, and its documentation says apps "are encouraged to preserve any indexed
metadata related to these volumes"; `MediaStore.QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES` is
documented as being for apps synchronising `MediaStore` with their own database. Which of those to
use, and what API 28 does without them, belongs to step 2.2 — `getExternalVolumeNames` is API 29
and the other two are API 30 and 31, so the fallback is a real branch and not a formality.

**The saving is conditional on the resync being scoped per volume.**
[albums.md](../../design/albums.md) §6 says a changed `MediaStore.getVersion()` means the indexed
data is invalid and must be resynced — and if "resynced" is read as "discard every row", a version
change throws away the absent volume's rows as well, by way of the very event this clause was
written for. The platform's own shape avoids that: `MediaStore.getVersion(Context, String)` is
**per volume**, the no-argument `getVersion(Context)` is documented as returning the version for
`VOLUME_EXTERNAL_PRIMARY` alone, both are documented as returning null for a volume that is not
mounted, and `getGeneration`'s documentation tells apps to confirm the version *for that volume*
before trusting generation numbers. A version-triggered resync must therefore be scoped to the
volume whose version changed, and it cannot be triggered at all by a volume that is absent.

**What is not established is whether attaching or detaching a secondary volume perturbs the
primary volume's version string.** The documentation neither promises nor forbids it, and this
project has one device with no removable storage, so it was not tested. Step 2.2 owns both the
per-volume watermark and finding that out. Until it does, this clause's saving is claimed only
where it does not depend on the answer — an app restart, and a remount that changes no version.

Nothing user-visible depends on this clause: an item on an unmounted volume is absent from live
media and therefore absent from its albums either way. It is a cost decision, and it is recorded
here so that step 2.2 does not reach the cheaper answer by default.

### 5. Manual albums are unchanged, and a single album can be both

[ADR-0010](0010-album-membership-dual-key-identity.md) continues to govern manual additions and
exclusions without modification: unresolvable memberships are retained, orphaned, hidden, re-checked
later, and never deleted, and the album shows the **Hidden-members notice**.

**The two behaving differently is correct, not an inconsistency to be reconciled later.** A
membership records a choice the user made about a particular photo; a rule match records only that
a photo happened to satisfy a predicate. Retention protects intent, and there is no intent to
protect in a rule match. Anyone tempted to unify the two should unify them towards *neither*
answer: deleting memberships is the silent data loss ADR-0010 exists to prevent, and tombstoning
index rows is dead weight that nothing reads.

ADR-0012's formula means a single album can carry both at once. An automatic album with manual
corrections resolves its additions and exclusions under ADR-0010 — retained, healed, orphaned — and
its rule matches under this ADR — intersected with live media, rows deleted on reconciliation. The
two sets are combined by ADR-0012's set operation and neither changes the other's rules.

## Alternatives Considered

**Tombstone the index row.** Keeps a positive record that an item was once present and is now gone,
by analogy with ADR-0012's exclusion tombstones. Rejected because the analogy does not hold: an
exclusion tombstone exists to stop re-evaluation resurrecting a user's correction, and nothing
re-evaluates a deleted file back into existence. No surface in the epic needs to distinguish
"deleted" from "never indexed", and a tombstone that nothing reads is a column, a migration and a
growth curve bought for nothing. Revisit only if a concrete consumer appears.

**Render an automatic album from the index alone, and accept the staleness window.** The reading
that prompted §10 question 1: the album shows what the index says, and a deleted photo lingers
until the next constrained run, with §6's staleness UI explaining it. Rejected because the window
is days long and the thing lingering is a photo the user has just deleted, which reads as the app
failing to notice rather than as the app being honest. §6's staleness surface is for work not yet
done; it is the wrong instrument for work that is already obsolete.

**Delete the index row the moment the app itself deletes a photo.** Correct as far as it goes, and
free, since the app already refreshes media after its own delete. Rejected as insufficient rather
than wrong: it does nothing for a deletion by another app, by a file manager, or by the system,
which is the common case. The live join covers all of them with no special case, and this
alternative is simply what the live join already does without extra code.

**Key the index on `_ID` and accept the move case.** Cheaper, and correct until another app
moves a file. Rejected for the reason ADR-0010 rejected it for memberships, with the same evidence,
plus the additional defect ADR-0010's Amendment 1 found: the bare `_ID` is not unique across the
two collections this app queries.

**Reconcile deletions through `MediaStore.queryDeletedFiles`.** The platform's purpose-built answer.
Not available: it is a version 37.1 / S Extensions 23 API, far above this project's floor.

## Consequences

### Positive

- **There is no deletion staleness window to explain to the user.** The behaviour that is hardest
  to defend — a photo the user deleted still sitting in an album — cannot occur, so no UI has to
  apologise for it.
- No new column, no new state machine, and no reconciliation correctness that the display depends
  on. If reconciliation is late, or is skipped entirely, the album is still right; the index is
  merely larger than it needs to be.
- The index reuses an identity and a resolution path that already exist and are already tested,
  rather than inventing a parallel one.
- The index's worst case under a failed heal is a re-index of one item, not data loss, because the
  index holds nothing that cannot be recomputed. That asymmetry with membership is what makes
  deleting safe here and unsafe there.
- Stages 3 and 4 inherit a settled answer. Face and content results are materialised sets, and the
  rule that a materialised set is intersected with live media before display is already written.

### Negative

- **Every automatic album read costs a set intersection against the full live media list**, on top
  of the same cost album membership already pays. The live list is loaded once and shared, so this
  is in-memory work rather than a query, but it is O(matches) against a hash of O(library) and it
  grows with the library.
- "Immediately" is bounded by the app's own media refresh, not by the deletion. A deletion by
  another app while Easy Gallery is in the foreground and not re-querying is not noticed until the
  next refresh. That is the same freshness every other media surface in this app has — albums are
  not made worse — but it is weaker than the word *immediately* suggests, and the app still has no
  `ContentObserver`.
- Index rows for deleted items survive until the next scan, so the index over-counts. Anything that
  reports index size, coverage, or "how much is indexed" must say which it is counting, or it will
  disagree with the library the user can see.
- A rule's match set and the album's contents are two different things, and only the second is ever
  shown. Debugging "why is this photo not in my album?" needs both, and a log or diagnostic that
  shows only the index will mislead.
- Clause 4 adds a volume-availability branch to reconciliation that is hard to test without real
  removable storage. Only one device is available to this project, so this path will ship
  unit-tested over fakes rather than exercised on hardware.
- **Clause 4 creates a retention class with no garbage collection.** Rows for items on volumes that
  are not currently present are held with no expiry and nothing to collect them, so a card that
  never comes back leaves its rows forever. [ADR-0010](0010-album-membership-dual-key-identity.md)
  recorded exactly this cost for its own retention rule — "Orphaned rows accumulate with no garbage
  collection" — and the same answer applies here: the rows are small, and the alternative is paying
  a full re-index on every remount. It is still a growth curve, and it is the one place where this
  ADR's "the index is honestly a cache, reconciliation deletes" framing does not hold.
- **Clause 4's saving is conditional, and one of its conditions is unverified.** It is only
  collected if the version-triggered resync is scoped per volume, and nobody has established what a
  mount or unmount does to another volume's version on a real device. Step 2.2 carries both.

## References

- [ADR-0010](0010-album-membership-dual-key-identity.md) — the identity reused here, and the
  retention rule this deliberately diverges from for rule matches; read to the end for Amendment 1
- [ADR-0012](0012-one-album-type-rule-additions-exclusions.md) — the formula that combines rule
  matches with additions and exclusions, and the tombstone rule that stays with exclusions
- [ADR-0017](0017-composable-album-rule-representation.md) — what produces the match set
- [ADR-0018](0018-media-index-in-a-second-database.md) — where the index lives, and the
  recomputable-cache premise this makes explicit
- [ADR-0001](0001-on-demand-media-location-metadata.md) — why re-indexing is expensive, which is
  what clause 4 protects
- [docs/design/albums.md](../../design/albums.md) §3, §6, §8, §10 question 1;
  [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.2
- `app/src/main/java/com/davide/seddio/easygallery/data/AlbumMembership.kt` — the resolve, heal and
  orphan implementation, and the private healing key
- `app/src/main/java/com/davide/seddio/easygallery/ui/AlbumsViewModel.kt` — `computeAlbum`, the
  existing live join, and `setAllMedia`, which supplies the live set
- <https://developer.android.com/reference/android/provider/MediaStore#getRecentExternalVolumeNames(android.content.Context)>
  — "apps are encouraged to preserve any indexed metadata related to these volumes"
- <https://developer.android.com/reference/android/provider/MediaStore#QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES>
  — documented for apps synchronising `MediaStore` with their own database
