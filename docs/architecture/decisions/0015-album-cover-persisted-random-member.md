# ADR-0015: An album's cover is a persisted member, chosen at random once

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

Albums View is a third `DisplayMode` on `FolderListScreen` (ADR-0013), structurally the same grid as
Folders View. A grid of tiles needs a picture per tile, so every album needs a cover. This was open
question 1 in `docs/design/albums.md` §10; this record answers it.

There is an obvious precedent and it does not transfer. ADR-0007 gives a folder an **automatic**
thumbnail — `GalleryTransformations.filterAndSortFolders` takes the first `MediaItem` it meets for
that folder path while walking the sorted media list — with an **optional persisted override** the
user can set. Nothing is stored unless the user chooses something, and the automatic value is
recomputed on every read.

That works for folders because the automatic value is cheap and stable: it is a property of a live
`MediaStore` list the app is already iterating, in a sort order that rarely changes. Albums differ in
both respects:

- **An album's member list is not a cheap property.** It is the output of
  `(rule ∪ additions) − exclusions` (ADR-0012), with every member resolved through ADR-0010's
  resolve → heal → orphan path. "The first member" is the first row of a set whose contents shift as
  healing runs and whose order is whatever the DAO happens to return.
- **An album is a composition, not a feed.** A folder accumulates whatever the camera and other apps
  put in it, so a tile that tracks its newest item is telling the truth about what a folder is. An
  album is a thing the user deliberately made. Its cover is part of how they recognise it in a grid —
  "the one with the castle" — and a cover that moves on its own reads as the app losing track of
  their album rather than as a feature.

The maintainer's product decision is that an album has a cover, that it is picked at random from the
album's members, and that the user will eventually be able to change it. The cover picker is
explicitly **not** in Stage 1.

What makes this an architecture decision rather than a UI detail is two questions that "picked at
random" does not answer on its own: **when is the randomness evaluated**, and **what does the
persisted reference point at?** The second decides whether a cover survives the disruptions ADR-0010
exists to survive, or quietly fails to while every membership around it heals.

## Decision

**An album's cover is a persisted reference to one of its members, chosen at random once and then
never re-rolled.**

### The reference is the membership row, not a media URI

The `albums` table carries a nullable `coverMembershipId: Long?`, a foreign key to the primary key
of the `album_memberships` row for the chosen item. It stores no media URI of its own.

A cover is by definition one of the album's members, so the album already holds a row for that item,
and that row already carries ADR-0010's healing key and already heals as a side effect of ordinary
resolution. Pointing the cover at the membership makes the cover inherit all of it for the cost of
one integer: no second copy of `DISPLAY_NAME` + `SIZE` + `DATE_MODIFIED` on `albums`, and — the part
that matters over time — no second healing path that can drift out of step with ADR-0010's.

A cover is therefore resolved by resolving its membership, through the ordinary
resolve → heal → orphan path. There is no cover-specific key and no cover-specific resolution logic
to keep correct.

The column is nullable, is indexed — Room requires an index on the child column of a foreign key —
and is created in Stage 1, at schema version 1. The picker is deferred; its storage is not. Adding
the affordance later is then pure UI work with no Room migration, no exported-schema change, and no
backfill over existing albums.

**The schema cannot express that the referenced membership belongs to *this* album.** A foreign key
constrains the target row to exist, not to be one of this album's rows. Stating it structurally
would need a composite reference from `albums(coverMembershipId, id)` to
`album_memberships(id, albumId)`, which puts the two tables in a reference cycle and buys an
insert-ordering problem in exchange for an invariant only this app's own code is in a position to
violate. It is a write-path invariant instead: the only code that writes `coverMembershipId` picks
from the album's own members.

### Random means random once

**The cover is chosen at the moment an album first gains a member, written to the column then, and
not computed again.** It is not re-rolled per render, per screen open, per re-evaluation, or per app
launch.

This is the single load-bearing line in this record. A cover that is random *per render* changes
every time the user opens Albums View, which is indistinguishable from a bug: the album the user
remembers as the one with the castle is now the one with a blurry hand, every tile shifts on every
visit, and the visual stability that makes a grid scannable is gone. "Random" is a decision about how
the initial value is picked, not an ongoing behaviour.

### When the cover's membership is deleted: `SET NULL`, then re-roll

Removing the cover item from the album deletes that membership row. The foreign key is declared
`onDelete = SET_NULL`, and the removal path then picks a fresh cover at random from the album's
remaining members, in the same transaction, if any remain.

`SET NULL` is the only defensible one of the four behaviours. `CASCADE` would delete the *album*
because the user removed a photo from it. `RESTRICT` would refuse to remove an item that happens to
be the cover, turning an internal detail into a rule the user has to work around. `NO ACTION` leaves
a dangling reference, which is the state a foreign key exists to prevent.

The re-roll beside it is what keeps the user-visible rule whole: an album with members has a cover.
It does not weaken "random once", because the trigger is an explicit act by the user on that exact
item. The picture they took out of the album cannot go on being its cover, and nothing they chose is
discarded behind their back.

### Empty albums, and the re-rolls there are

An album with no members has no cover: the column is null and Albums View shows the empty-album
treatment. An album that loses its last member has its column cleared by `SET NULL` for free, and
the next member added picks a fresh cover. With the removal re-roll above, those are the only two
ways a cover ever changes until the picker ships.

### Fallback is display-only, and never written back

With the reference pointing at a membership row, there is exactly one way a cover fails to resolve:
the membership is **orphaned** under ADR-0010 — the card is out of the phone, another app moved the
file a moment ago, the library has just been restored and has not healed yet. Removal is handled
above and is not a resolution failure.

ADR-0010 never deletes an orphan, so the row and the reference both survive it. Albums View falls
back to **another resolved member, for display only**. The stored value is neither overwritten nor
cleared, and when the membership heals the album's own cover returns by itself — which is the whole
point of referencing the row rather than copying the URI out of it.

If *every* member is orphaned there is nothing to fall back to. That album must read as empty and
waiting, never as broken; it is the ADR-0014 restore case, and it is a UI obligation that record
already states.

Not writing back is the second decision that matters. Once the picker exists, the column may hold a
choice the user made deliberately, and every cause of an orphaned membership listed above is
*transient*. Replacing the stored reference on a resolution failure would convert a temporary
condition into the permanent destruction of a user's decision, silently, at exactly the moment the
app is least sure of itself.

The rule is therefore simple and checkable: **`coverMembershipId` is written only by explicit
events** — the first member being added to an empty album, the cover's own item being removed from
it, or (later) the user picking a cover. A resolution failure is not an event that writes.

### Stage 1 ships no picker

Stage 1 has no affordance to change a cover. The album detail surfaces do not offer one, and no
string is added for one.

## Relationship to ADR-0007

Folder thumbnails are **automatic with an optional override**. Album covers are **persisted always**.
Two surfaces that look like the same problem — a picture on a tile in a grid — are built differently,
and this section exists so that whoever notices does not have to guess whether it was an oversight.

The difference comes from what the automatic value costs and how stable it is. For a folder it is a
by-product of a list walk the app performs anyway, over data that changes only when the filesystem
changes, so recomputing it is both free and predictable, and there is no stored value to be wrong.
For an album the equivalent value is the first row of a resolved, healed, set-arithmetic membership —
expensive to produce, and liable to differ between two reads for reasons that have nothing to do with
the user. Making an album's cover automatic would therefore buy nothing and cost stability on the one
property a curated collection most needs to keep.

The two records do not conflict. ADR-0007 continues to govern folder thumbnails unchanged.

## Alternatives Considered

**Reuse ADR-0007's model exactly — automatic, with an optional override.** The consistency argument is
real, and this was the starting assumption. Rejected because the property that makes ADR-0007 safe is
absent here: a cheap, stable recomputation. An album's automatic cover would drift after a heal, after
an add, and potentially between two renders, for a container the user deliberately composed.

**Use the newest member as the cover, computed on read.** Deterministic, needs no column, and mirrors
the folder behaviour closely. Rejected because it changes the cover every time the user adds a photo,
which is correct for a folder and wrong for an album for the reason in Context. It also pushes a sort
over every album's membership into every Albums View render.

**Re-roll the random choice on each render, or each open.** Rejected; this is the failure the Decision
is written to prevent. See "Random means random once".

**Ship the cover picker in Stage 1.** Rejected on scope. Stage 1 already carries Favourites with
ADR-0011's four-rung write path, manual albums, and two new views. A picker is a whole interaction
mode — ADR-0007's folder-thumbnail picker needed an ADR and two amendments before it settled — and
none of that is load-bearing for albums existing. Because the storage ships now, deferring the
affordance costs a later migration of nothing.

**Store the cover as a bare media URI on `albums`, in ADR-0010 Amendment 1's key form.** The first
draft of this record, and the obvious reading of "the cover is a media item". Rejected because such
a column has no healing key beside it: after an `_ID` renumbering, a volume remount, or a restore
under ADR-0014, every cover in the database misses at once and every album falls back to
display-only — permanently, since until the picker ships there is no way for the user to put it
right. The two ways out were to duplicate `DISPLAY_NAME` + `SIZE` + `DATE_MODIFIED` onto `albums`
and run a second healing path, or to point at the row that already has them. The second is less to
build and, more importantly, less to keep in step with ADR-0010 as it changes. This was caught while
the schema was still unwritten at version 1, where the difference costs a column definition rather
than a migration.

**Store the cover as an `isCover` flag on a membership row instead of a foreign key on `albums`.**
The other way of attaching the cover to a membership, and it heals exactly as well. Rejected because
it admits an invalid state the schema cannot prevent — two rows flagged at once, with nothing saying
which wins — where a single nullable column on `albums` makes "at most one cover" structural. It
also turns "which item is the cover?" into a query over membership rather than a field read on the
album.

**Let an album have no cover at all, and render a generated placeholder — initials, a colour, an
icon.** Defensible, and cheaper than all of the above. Rejected because this is a gallery: an album of
photographs represented by a coloured square with a letter in it is a worse answer than any photograph
from inside it, and the app would be the only thing in its own grid not showing a picture.

## Consequences

### Positive

- An album's cover is stable. It is the same picture on every open, which is what lets a user
  recognise an album at a glance and refer to it by what it looks like.
- **The cover heals, and healing it costs nothing.** It rides on the membership row's healing key,
  so an `_ID` renumbering, a volume remount, or a whole-library restore under ADR-0014 repairs the
  cover at the same moment, and by the same pass, as the membership it points at.
- There is one persisted media reference in the album schema — the membership row — so there is one
  key convention to learn, one healing path to test, and one place the `Uri.parse` prohibition has
  to be observed.
- Adding the picker later is a pure UI change — no migration, no schema version bump, no backfill.
- Rendering a cover costs one membership resolution per album, not a sort or a scan over its
  membership.
- A null column is an honest representation of an empty album, rather than a sentinel that has to be
  interpreted.
- "At most one cover per album" is structural rather than a rule the write path has to maintain.

### Negative

- Stage 1 ships a column the user cannot change, which is dead weight until the picker lands. That is
  deliberate: it is cheaper than the migration avoiding it would cost.
- A random pick can be a poor cover — a dark frame, a screenshot, the back of someone's head — and
  until the picker ships the only remedy is to empty the album and re-add its contents. That is a real
  rough edge of Stage 1 and should not be presented as a feature.
- **A cover can only be an item that has a membership row, and from Stage 2 that is not every
  member.** Under ADR-0012 an automatic album's members are `(rule matches ∪ additions) −
  exclusions`, and a rule match carries no stored row — so it cannot be referenced as a cover. The
  likely escape is that choosing, or randomly picking, a rule-matched item materialises an addition
  row for it, which is a no-op under the union and therefore changes no membership. That is **not**
  decided here: Stage 2 must settle it when automatic albums are planned, and this record's "chosen
  when the album first gains a member" is defined over membership rows until it does.
- **A cover that is not a member is now foreclosed.** The bare-URI column left that possibility open
  without deciding it; a foreign key into membership closes it. Accepted, because this record's own
  definition of a cover is "one of its members" — but reopening it would now take a new column and a
  new ADR, not a change of mind.
- Resolving a cover reads two tables rather than one. Negligible in practice: Albums View resolves
  memberships anyway, and the cover is one of the rows it already has in hand.
- **"The cover's membership belongs to this album" is a code invariant, not a schema constraint.**
  Nothing in the database stops a bug pointing one album's cover at another album's row. The write
  path is the only guard, and a test should assert it.
- Nothing stops two albums picking the same photo as their cover, so a user with overlapping albums
  may see duplicate tiles. Accepted; enforcing uniqueness would mean an album's cover depended on
  which album was created first.
- The cover is written by write paths rather than by the screen that shows it, and there are now two
  of them — the first member being added, and the cover's item being removed — plus the picker
  later. Every path that adds a first member or removes a member must go through them, or some
  albums acquire a cover and others keep a null one while full of photos.

## References

- `docs/design/albums.md` §10 open question 1, answered by this record; §8, where the decision is
  indexed
- ADR-0007 — folder thumbnails, the precedent this deliberately diverges from, and its Amendments 1
  and 2 for how much interaction design a picker turned out to need
- ADR-0010 and its Amendment 1 — the membership row this column points at, the healing the cover
  inherits from it, and the `Uri.parse` prohibition that applies wherever that row is resolved
- ADR-0012 — the membership arithmetic a cover is chosen from, and the reason a rule match has no
  row to reference
- ADR-0013 — Albums View, the grid that needs a cover per tile
- ADR-0014 — backup and restore, the case this record is shaped by: a restored cover heals with its
  membership instead of missing permanently
