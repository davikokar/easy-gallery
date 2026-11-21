# ADR-0024: Face data — the disclosure, the switch that deletes it, and what never leaves it

- Status: Active
- Date: 2026-11-18
- Adopted: 2026-11-19
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer; approved by the
  maintainer on 2026-11-19

> **Adopted 2026-11-19, when the maintainer approved it.** It was drafted on 2026-11-18 under a
> temporary `Proposed` label, because the instructions' status vocabulary is `Active | Disabled`
> and neither fits a draft. Wording and placement are still **intent**, not final strings or
> layout: adoption binds the mechanism, and the Designer finalises both at step 3.6, naming any new
> control in the UI vocabulary then. This record introduces no vocabulary.
>
> **Its four questions were answered by the maintainer on 2026-11-19, as recommended**
> ([albums.md](../../design/albums.md) §8 decision 47). ~~That settles them; it does not approve the
> record, which stays `Proposed` until he does.~~ He approved the record separately, the same day.

## Context

[albums.md](../../design/albums.md) §8 decision 9 makes face grouping eager — behind a disclosure, not
behind an opt-in gate — and attaches three obligations:

1. a one-time, plain-language disclosure **when indexing first starts**, saying that faces are
   grouped on the device, that nothing leaves the phone, and where the off switch is;
2. a Settings switch that **deletes the face data**, not merely stops adding to it;
3. face data never leaves the device, never enters a backup, never appears in a log.

Its 2026-11-17 clarification says what face data is: embeddings, crops and groups. A face's position
stored as an anchor is not ([ADR-0022](0022-people-criterion-anchored-to-a-photo.md)).

Two facts shape how the obligations can be met. **Face indexing runs in a background worker with no
UI and no notification** (decision 23), so "when indexing first starts" cannot be the moment the
disclosure appears — nothing is on screen then. And **the face data lives in the index database's
Tier C tables** ([ADR-0018](0018-media-index-in-a-second-database.md) Amendment 5), beside Tier A
data that is not face data and is expensive to rebuild.

## Decision

### 1. One setting: face data on or off

- **A persisted on/off setting, on by default** — decision 9's eager grouping. It is the user's
  intent, so it is backed up with the app's other settings: a user who switched it off finds it off
  on a restored device.
- **Stored as a flat scalar in `app_settings`**, which `AGENTS.md` allows another scalar and nothing
  structured. No new preferences file. ~~The disclosure's own state (§2) is question 1.~~ The
  disclosure's own state (§2) is **not** stored there: it is per installation (question 1, answered
  2026-11-19), and `app_settings` is backed up.
- **On a device that fails the capability check** ([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md))
  there is nothing to switch, and the setting is not offered.

### 2. The disclosure

- **It precedes the first Tier C pass; it does not wait for it.** The worker runs no Tier C pass until
  the disclosure has been shown. It is shown in the app, at the first app open on a capable device
  with face data on — for a new install and for an update alike. So decision 9's "when indexing
  first starts" is read as **"before face indexing first starts"**: the only reading a background
  worker allows, and the stricter one.
- **Shown, not consented to.** Decision 9 rejected an opt-in gate. Dismissing the disclosure lets
  grouping proceed; it offers a direct way to the setting, so that saying no costs one tap.
- **What it says, as intent:** faces in your photos are found and grouped on this phone, so that you
  can make albums of people; nothing about faces leaves the phone or goes into a backup; you can turn
  this off in Settings, and turning it off deletes what was found. Plain language, in every shipped
  locale. Final wording is step 3.6's.
- **Shown once per installation** (question 1, answered 2026-11-19): again on a new or restored
  device, where face indexing starts again from nothing. Whether it has been shown is recorded
  where a backup cannot carry it; which store holds it is step 3.3's choice, under `AGENTS.md`'s
  preference-store rules.

  > **Note 2026-11-20 — adopted 2026-11-20 with [ADR-0018](0018-media-index-in-a-second-database.md)
  > Amendment 7, which the maintainer approved that day; written while the amendment was
  > proposed.** Step 3.3 chooses a new preferences
  > file, `face_device_state`, excluded from backup, shared with the capability record (Amendment 7
  > §3). Not `app_settings`, which is backed up; not Tier C, whose deletion would bring the disclosure
  > back on switching on again, against question 4. The setting of §1 stays in `app_settings`. With
  > the amendment approved, this file is the new preferences file the *Negative* bullet below
  > allows for. Nothing in step 3.3 sets the flag: the disclosure itself is step 3.6's.
- **Not shown on an incapable device**, where no face processing happens (ADR-0023 question 4,
  answered 2026-11-19).

### 3. Switching face data off

In this order:

1. **Persist "off".** From here on no Tier C pass starts, and a running one stops at its next check.
2. **Stop any running Tier C work** and wait for it, so that no result computed before the switch is
   written after the deletion. Tier C writes are conditional on the setting being on, in the same
   transaction, as the backstop.
3. **Delete all face data** — every Tier C row, in one transaction, leaving no deleted bytes in the
   file — as ADR-0018 Amendment 5 defines. Tier A is untouched, so places, dates and region and time
   albums are unaffected.
4. **Drop anything held in memory**: loaded embeddings, grouping state, cached picker thumbnails.

- **Confirmed before it happens** — ~~*proposed*,~~ question 2, answered 2026-11-19. The deletion
  is recomputable, but it discards hours of background work and makes every people album
  unevaluatable until it is redone.
- **Idempotent.** Switching off with nothing stored does nothing. **At every process start, if the
  setting is off and any Tier C row exists — a deletion cut short by process death — the deletion
  runs again.**

  > **Amended 2026-11-20 — [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 6.** A
  > deletion can commit every row and still report its scrub incomplete — a reader holding the old
  > snapshot, or a rollback journal that keeps page copies — and then no Tier C row exists to
  > trigger the repeat. The start-up deletion therefore runs whenever the setting is off; with
  > nothing stored it only re-checks the files.

### 4. While face data is off

- **No Tier C pass runs**, periodic or Index now. Tier A and the place pass run as before.
- **The People criterion is not offered** on the New automatic album screen — ~~*proposed*,~~
  question 3, answered 2026-11-19.
- **An existing people album keeps its anchors**, which are not face data (ADR-0022), and is
  **unevaluatable with the "people unavailable" cause**, its notice saying that face grouping is off
  ([ADR-0017](0017-composable-album-rule-representation.md) Amendment 4). Not waiting: waiting would
  never end, the permanent-waiting failure ADR-0018 Amendment 4 names. Its manual additions still
  show, its rule is untouched, and nothing is deleted.
- **Settings shows the setting off**, and no Tier C figure.

  > **Built 2026-11-21 — step 3.5, evaluation only.** `data/PeopleAvailability.kt` reads the setting
  > and the persisted capability record, never the pipeline, and a people album is unevaluatable
  > with `PEOPLE_UNAVAILABLE_FACE_DATA_OFF` while the setting reads off — checked before the
  > capability, and before any anchor. The disclosure is not an input. `AlbumsViewModel` re-reads
  > the availability on every face-snapshot emission, but switching face data off when Tier C is
  > already empty changes no row and so emits nothing: **the switch step 3.6 builds must call
  > `AlbumsViewModel.refreshPeopleAvailability()`**, in both directions, and nothing calls it yet.
  > The notice's wording is step 3.6's.

### 5. Switching face data back on

- **Persist "on"**; the next index pass — periodic or Index now — starts Tier C from empty.
- **Every people album is waiting until its anchor photos are face-indexed and grouped again**, then
  resolves through its anchors: the same path as after a restore (ADR-0022 §5).
- **No second disclosure** — ~~*proposed*,~~ question 4, answered 2026-11-19. The user turned it on
  from the setting that describes it.

### 6. Never leaves, never backed up, never logged — how each is checked

- **Never leaves the device.** The face path makes no network call, and has no code that could. The
  app's merged manifest does carry `INTERNET`, from a library and the tip jar, so the check is a review
  of the face path's code, not an absent permission.
- **Never backed up.** Tier C is in `media_index.db`, excluded with its sidecars in all three places;
  the stage's exit criteria check the rules files. **Anchors are backed up and are not face data.**
  *Note 2026-11-20, ~~proposed~~ adopted 2026-11-20 with ADR-0018 Amendment 7 §2: four places, a
  `<cross-platform-transfer platform="ios">` section being added with the same exclusions (F1).*
- **Never logged.** The Tier C pass logs through an injected logger, and a unit test asserts that
  every line it emits carries only counts of images processed — never a box, an embedding, a group
  id, a per-image face count, or a URI or file name with anything about faces. Exception messages from
  the face path are held to the same rule. Step 3.3 builds the test; the Reviewer checks every `Log`
  call in the face path.
- **Never cached to disk.** A face thumbnail in the picker is cropped from the photo at display time
  and must not reach an image loader's disk cache. Step 3.6 verifies it.

## Alternatives Considered

**An opt-in gate.** Rejected by decision 9: the picker would open on an empty screen and a progress
bar.

**Show the disclosure from the worker, as a notification.** Rejected by decision 23, which allows no
notification and no `POST_NOTIFICATIONS`.

**Delete the whole index file.** Rejected in ADR-0018 Amendment 5: it would throw away Tier A for no
privacy gain.

**A switch that only stops adding face data.** Rejected by decision 9's own words.

**People albums waiting while face data is off.** Rejected: waiting implies the answer will come,
and while the switch is off it will not.

**Delete people albums, or their anchors, when face data is switched off.** Rejected: anchors are not
face data, an album holds the user's manual additions and display choices, and switching back on
would then lose the albums for good — the reasoning decision 37's reversal gave against deletion.

## Consequences

### Positive

- Every one of decision 9's obligations has a mechanism and a check.
- Switching off is complete — rows and leftover bytes alike — and switching back on loses nothing the
  user made.
- Region and time albums never notice the switch.

### Negative

- **A user who never opens the app after an update never gets face grouping**: the disclosure gates
  the first pass. Intended, but it means people albums depend on an app open, which nothing else in
  indexing does (decision 22).
- A third notice wording for unevaluatable, shared with the incapable device under one cause.
- Re-enabling costs a full face re-index, over many constrained runs (ADR-0023).
- ~~Two more scalars may join `app_settings` (question 1), and `AGENTS.md`'s preference-store table must
  say so when they land.~~ One more scalar joins `app_settings`, the setting; the disclosure's state,
  per installation (question 1), needs a store a backup does not carry, which may be a new
  preferences file. `AGENTS.md`'s preference-store table must say so when either lands.

## Questions for the maintainer — answered 2026-11-19

> **Answered by the maintainer on 2026-11-19**, in his words "go with the recommendations". Every
> question below was answered as recommended. The questions are left as they were put, because their
> reasons are the record of why; the text above has been settled to match.

1. **Is the disclosure shown once per installation, or once per person?** Once per installation
   shows it again on a new or restored device, where face indexing starts again from nothing; it needs
   a place to record "shown" that is not backed up. Once per person records it with the setting, in
   `app_settings`, and it travels with the backup. *Recommended: once per installation*, because
   decision 9 ties it to indexing starting, and on a new phone indexing starts again. ~~Which?~~ ✅
   **Answered 2026-11-19: once per installation, as recommended.** (E7.)
2. **Confirm before deleting** when face data is switched off? *Recommended: yes.* ~~Yes or no?~~ ✅
   **Answered 2026-11-19: yes, as recommended.** (E8.)
3. **While face data is off, is the People criterion hidden**, as on an incapable device, or shown
   with a pointer to Settings? *Recommended: hidden*, so the screen never offers what it cannot do.
   ~~Which?~~ ✅ **Answered 2026-11-19: hidden, as recommended.** (E9.)
4. **On switching face data back on, is the disclosure shown again?** *Recommended: no.* ~~Yes or
   no?~~ ✅ **Answered 2026-11-19: no, as recommended.** (E10.)

E7 to E10 are the numbers these carry in [albums-stage-3.md](../../design/albums-stage-3.md) group E.

## References

- [docs/design/albums.md](../../design/albums.md) §6, §8 decisions 9 (clarified 2026-11-17), 22, 23,
  29, 37 (reversal) and 45
- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md) step 3.6 and the stage's exit
  criteria for decision 9
- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 5 — what a deletion removes
- [ADR-0017](0017-composable-album-rule-representation.md) Amendment 4 — the "people unavailable"
  cause
- [ADR-0022](0022-people-criterion-anchored-to-a-photo.md) — why anchors stay
- [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) — the capability check and the worker
- [ADR-0014](0014-album-database-included-in-auto-backup.md) Amendment 1 — the backup rules the
  index's exclusion sits in
- `AGENTS.md` *Preference stores*; `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`
