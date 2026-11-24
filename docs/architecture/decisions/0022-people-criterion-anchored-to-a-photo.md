# ADR-0022: A people criterion finds its person through an anchor photo

- Status: Active
- Date: 2026-11-18
- Adopted: 2026-11-19
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer; approved by the
  maintainer on 2026-11-19

> **Adopted 2026-11-19, when the maintainer approved it.** It was drafted on 2026-11-18 under a
> temporary `Proposed` label, because the status vocabulary in
> [architecture-docs.instructions.md](../../../.github/instructions/architecture-docs.instructions.md)
> is `Active | Disabled` and neither fits a draft. No people clause was to be built before adoption
> ([albums.md](../../design/albums.md) §8 decision 41); from adoption it binds Stage 3's steps 3.2
> onwards.
>
> **Its question was answered by the maintainer on 2026-11-19, as recommended** (§8 decision 47).
> ~~That settles the question; it does not approve the record, which stays `Proposed` until he does.~~
> He approved the record separately, the same day.

## Context

A people album must find "the same person" again after events that destroy everything the app
knows about faces. Face groups live in the media index, which is recomputable and excluded from
backup ([ADR-0018](0018-media-index-in-a-second-database.md)); albums live in the album database,
which is backed up ([ADR-0014](0014-album-database-included-in-auto-backup.md)). After a restore, a
reinstall or a full re-index, the index starts empty and is clustered again from scratch, and the
groups it produces have no identity that survives that: average-linkage clustering over the whole
library ([albums.md](../../design/albums.md) §8 decision 45) numbers its groups afresh each time.
A rule that pointed at a group would point at nothing.

The maintainer has already chosen the direction and its details, and this record makes them
binding rather than reopening them:

- the rule stores the photo the person's thumbnail came from and where the face is in it — decision
  41, one anchor per person;
- a face's position stored as an anchor is not face data; embeddings, crops and groups are —
  decision 9's clarification of 2026-11-17;
- how an anchor resolves, and what happens when it cannot — decision 42;
- the minimum overlap and the small-face cut-off — decision 46 (delegated);
- §10 question 12 is closed by those decisions.

## Decision

**A person in an automatic album's rule is an anchor: a reference to one photo plus the position of
one face in it. After any restore or re-index, the person is whichever face group then contains the
face found at that position.**

### 1. What an anchor stores

- **The anchor photo's media identity**, exactly the shape `data/MediaIdentity.kt` defines: the
  content URI string and the healing triple (`displayName`, `size`, `dateModified`).
  [ADR-0020](0020-automatic-albums-resolve-against-live-media.md) forbids a second identity, and
  this is not one.
- **The face's box**, as four fractions of the photo's width and height — left, top, right, bottom
  — in the photo's **upright** frame, with its EXIF orientation applied. Fractions make the box
  independent of the size the photo was decoded at, and the overlap test below is unchanged by
  scaling each axis.
- **The face-pipeline version** that produced the box ([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md)).
  It is informational: resolution does not compare it. It is stored now because a field added later
  would make every older build treat the clause as unknown.

**It stores nothing else.** No embedding (decision 9, and decision 41's rejected option), no crop or
thumbnail, no group or cluster id, no person name (decision 40), no detector score and no landmarks.

### 2. Where it lives

Inside the rule document in `albums.rule`, as part of a `people` clause whose format is
[ADR-0017](0017-composable-album-rule-representation.md) Amendment 4. So it lives in the album
database, is backed up with the album, and needs **no album-database migration** — ADR-0017's
promise that a new clause type costs no schema change holds. Nothing else face-related is written
to the album database.

### 3. How an anchor resolves

Resolution runs at evaluation, reads only, and is pure apart from its inputs.

1. **Find the anchor photo among live media** by the shared identity: the stored URI, then the
   healing triple, as ADR-0010 and ADR-0020 clause 3 resolve any stored reference. **The healed URI
   is not written back** (see *Never rewritten* below, and question 1, answered 2026-11-19).
2. **Not found** → the anchor is **gone**. The album is unevaluatable (decision 42).
3. **Found, but the photo has no current face result** — not yet face-indexed under the current
   pipeline version, or its faces not yet grouped ([ADR-0018](0018-media-index-in-a-second-database.md)
   Amendment 5) → **waiting on the index**, §6's pending state. Every people album passes through
   it after a restore (decision 42).
4. **Found and face-indexed** → among the photo's stored faces, take the one whose box overlaps the
   anchor's box best by intersection over union, and accept it only at **IoU ≥ 0.5** (decision 46).
   Faces under the 40 px cut-off are never candidates, because they are never stored (Amendment 5;
   decision 46). A tie is broken deterministically, by a rule step 3.5 fixes. *(Fixed 2026-11-21:
   the lower face id — see* Built 2026-11-21 *below.)*
5. **No face at IoU ≥ 0.5**, or the photo's face inference is recorded as failed → **no face at the
   anchored position**. The album is unevaluatable (decision 42).
6. **A face is found** → the person is that face's group. A group seen in one image still resolves
   (decision 42), and a face that average linkage leaves alone is a group of one (decision 42's
   2026-11-18 pointer).

Two further causes of unevaluatable come from the device rather than from the anchor — the face
pipeline fails its capability check ([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md)),
or the user has switched face data off ([ADR-0024](0024-face-data-disclosure-and-delete-switch.md)).
In both, no anchor is resolved at all. How the states combine across several people and with place
and date is ADR-0017 Amendment 4.

### 4. Never rewritten

**Resolution never changes the rule.** It does not record a heal, a new anchor, a group, or that the
anchor is gone. The stored anchor is exactly what the user picked, so a photo that returns — a
remounted card, a restored file — makes the album evaluable again (decision 42). The only change to
an anchor is the user's own: picking the person again through rule editing, which replaces that
person's anchor, or deleting the album.

### 5. What this means for backup and restore

- The anchor travels in the backup with its album; the index does not.
- On the new device every anchor's URI is stale, and resolves through the healing triple on every
  evaluation.
- Until the Tier C pass reaches each anchor photo and groups its faces, the album waits, with
  nothing on it saying so (decision 29).
- A restore that rewrites files so that the healing triple changes loses the anchor; the album is
  unevaluatable until the user picks the person again. This is ADR-0014's accepted cost for
  memberships, now also paid by anchors.

### Built 2026-11-21 — step 3.5

The resolution of §3 and the membership it leads to landed in `data/PeopleClauseEvaluation.kt`, pure
and read-only, with no write-back, as §4 requires; [ARCHITECTURE.md](../ARCHITECTURE.md) §3 describes
it and [albums-stage-3.md](../../design/albums-stage-3.md) *3.5 — Built and verified* has the gate.
What the code settled that this record left open or did not spell out:

- **The tie rule is the lower face id.** On exactly equal overlap the face with the lower id wins,
  whatever order the faces arrive in — the same tie rule grouping uses for its medoid.
- **IoU is computed in `Double` from the stored `Float`s,** and the 0.5 threshold is inclusive.
- **The person's photos are the images with any face in the anchor's group** at the current grouping
  version. A video or GIF is a settled no-match; an image with no face in the group but a face not
  yet grouped at that version is unsettled.
- **"Not found" does not wait for grouping.** Step 3 above waits while an anchor photo's faces are
  ungrouped; the code waits only while the best face at IoU ≥ 0.5 is. Once the photo's face state is
  current under the pipeline version, no face at the box is step 5's not found at once, because
  grouping changes no box and so cannot make a face appear there.
- **An anchor's Tier C data is trusted only while three triples agree** — the live photo's, the
  index row that won it, and the face snapshot's — and is waiting otherwise, so a file changed since
  it was face-indexed is never resolved against its old faces.
- **Availability is told before any anchor**, as the paragraph after §3's list says: face data off,
  then a capability record for this build saying incapable. An unknown capability counts as
  available, which leaves a gap on devices without arm64-v8a that step 3.6 must close
  ([ARCHITECTURE.md](../ARCHITECTURE.md) §11). *(Decided 2026-11-21 by
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) Amendment 1 §3: such a device is
  recorded incapable without loading anything. Step 3.6's task A builds it.)* *(Built 2026-11-22,
  step 3.6: `supportsFacePipelineAbi`, in the checker and, to skip the lock, in `FaceDataViewModel`.)*

### Built 2026-11-22 — step 3.6

The anchors are now written: the People picker builds each person's clause entry through
`AlbumRuleClause.People.anchoredAt` from the group's representative face, and a rule holds at most
`MAX_PEOPLE_PER_ALBUM` (8) people — the picker stops there and `createAutomaticAlbum` refuses more,
with the encoder's `require` caught as a backstop. Nothing in §1 to §5 changed;
[ARCHITECTURE.md](../ARCHITECTURE.md) §3 and §11 describe it.

## Alternatives Considered

**Automatic re-anchoring** — moving an anchor to another photo of the person before its photo is
deleted. Rejected for now by decision 42: it works only for deletions made inside the app, and it
rewrites rules.

**The face's embedding in the rule.** Would survive any re-clustering. Rejected by decision 9: the
album database is backed up, and an embedding is face data. Decision 41 records this.

**Accept that a people album is unevaluatable after every restore until the person is picked
again.** The option decision 41 did not choose: it makes backup worthless for people albums.

**A group or cluster id in the rule.** Rejected: groups are recomputed from scratch, so an id means
nothing after a re-index, and a group is face data.

**A separate anchor table in the album database.** Rejected for the reason ADR-0017 rejected a
clause table: an anchor is always read and written with its rule, and the table would cost the
album database its first migration since Stage 1.

**Several anchors per person.** More robust to one deletion. Rejected by decision 41's amendment:
one anchor per person.

**The box in pixels.** Rejected: pixels depend on the size the photo was decoded at, which is a
pipeline parameter that may change.

## Consequences

### Positive

- People albums survive backup and restore without any face data entering a backup.
- No album-database migration; the anchor is a few fields of JSON.
- A re-index that regroups faces differently changes what the album contains, not whether it works.
- Resolution is pure and testable on the JVM, beside `AlbumRuleEvaluation.kt`.

### Negative

- **The album follows the group's mistakes.** If the anchor's face lands in a mixed group, the album
  holds both people's photos; if it lands in a fragment of a split person, it misses the rest
  (decisions 40 and 42).
- **Heal without write-back** means every restored anchor stays stale for good. If its photo is then
  edited by another app, so that the healing triple changes, the anchor is gone although the photo
  exists. ~~Question 1 offers the alternative.~~ Question 1 weighed the alternative, and the
  maintainer accepted this cost on 2026-11-19.
- **Anything that moves the face in the photo breaks the anchor**: another app rotating or cropping
  the file, or a new pipeline whose boxes move by more than the overlap allows. The album becomes
  unevaluatable until the person is picked again.
- **Duplicates** sharing a healing triple can heal the anchor to the other copy. For a true copy the
  face is in the same place and nothing changes; otherwise the face is not found.
- A new unevaluatable cause, and its notice wording in every shipped locale (step 3.6).

### To verify

- **The upright frame.** Step 3.3 must store detected boxes in the same frame as anchors, proved by
  a test on a photo with a non-zero EXIF orientation. ✅ **Verified 2026-11-20 on the SM-G990B:**
  `FaceDecodeOrientationDeviceTest` (four tests: orientation 1 as the control, then 3, 6 and 8)
  decodes a JPEG with an asymmetric mark through the pipeline's production decoder, `decodeUprightForFaces`, and
  finds the mark where the upright image has it; `FaceIndexPassTest` covers the box geometry on the
  plain JVM. Together they put a stored box in the upright frame.
- **Anchor survival across pipeline changes.** The spike measured IoU 1.0 on one build and a median
  of 0.957 between the int8 and fp32 detector on one phone
  ([albums-stage-3.md](../../design/albums-stage-3.md), *Spike outcome*). ~~**Proposed as binding**~~
  **Binding since this record's adoption, on any build that bumps the face-pipeline version:** it measures how many anchors still resolve
  between the old and the new pipeline on a real library, and states the figure, before it ships —
  the counterpart of the remap obligation ADR-0017 Amendment 2 puts on a dataset bump.
- The other phone, the SM-G781B, at step 3.7 if it can be attached. ✅ **Verified 2026-11-22 on the
  SM-G781B** (Android 13, API 33), with the debug build: `FaceDecodeOrientationDeviceTest`'s four
  tests — orientation 1 as the control, then 3, 6 and 8 — passed, so the upright frame holds on that
  phone as on the SM-G990B. Recorded with the capability check in
  [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md)'s verify row 7 and in
  [albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G1. It covers the decoder only: a face
  pass and grouping on that phone were ~~not run~~ not measured (*3.7*, L3) — *2026-11-23:* G10
  started a face pass there and stopped it at thermal status 2, with no figures (*3.7*, G10).

## Questions for the maintainer — answered 2026-11-19

> **Answered by the maintainer on 2026-11-19**, in his words "go with the recommendations". The
> question is left as it was put, because its reasons are the record of why.

1. **Should a healed anchor URI be written back into the rule?** *Proposed: no.* Writing it back
   would keep anchors working when a restored photo is later edited, but it is an automatic rule
   rewrite, which decision 42 cites as a reason to reject re-anchoring. Album memberships do write
   their heals back (ADR-0010), so this is a deliberate difference. ~~Yes or no?~~ ✅ **Answered
   2026-11-19: no, as recommended.** An anchor is healed at read time only, and §4 stands as
   written. (Group E's E1 in [albums-stage-3.md](../../design/albums-stage-3.md).)

## References

- [docs/design/albums.md](../../design/albums.md) §8 decisions 9 (clarified 2026-11-17), 29, 35,
  40, 41, 42 and 46; §10 question 12
- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md) step 3.1, *Spike outcome* (anchor
  stability)
- [ADR-0010](0010-album-membership-dual-key-identity.md), [ADR-0020](0020-automatic-albums-resolve-against-live-media.md)
  — the identity and its healing
- [ADR-0014](0014-album-database-included-in-auto-backup.md) — why the anchor travels and the index
  does not
- [ADR-0017](0017-composable-album-rule-representation.md) Amendment 4 — the clause that carries
  the anchor
- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 5 — the face rows an anchor
  resolves against
- [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md),
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) — the two device-side causes of
  unevaluatable
- `data/MediaIdentity.kt`, `data/AlbumRuleEvaluation.kt`
