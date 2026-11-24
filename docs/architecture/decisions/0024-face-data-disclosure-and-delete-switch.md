# ADR-0024: Face data — the disclosure, the switch that deletes it, and what never leaves it

- Status: Active
- Date: 2026-11-18
- Adopted: 2026-11-19
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer; approved by the
  maintainer on 2026-11-19

> **Amendment 1 (2026-11-21), at the end of this record, was adopted on 2026-11-21 on the
> maintainer's approval, with §2's option A.** It was drafted the same day under a temporary
> *proposed* label. The `Active` status above covers the Decision and Amendment 1.

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
  >
  > *Note 2026-11-24, step 3.8's audit — built at step 3.6 (2026-11-22):* called through
  > `FaceDataViewModel.peopleAvailabilityEpoch`, which `MainActivity` forwards — after the app-open
  > work, on every resume, and after either direction of the switch (Amendment 1 §3).

### 5. Switching face data back on

- **Persist "on"**; the next index pass — periodic or Index now — starts Tier C from empty.
- **Every people album is waiting until its anchor photos are face-indexed and grouped again**, then
  resolves through its anchors: the same path as after a restore (ADR-0022 §5).
- **No second disclosure** — ~~*proposed*,~~ question 4, answered 2026-11-19. The user turned it on
  from the setting that describes it.

  > **Amended 2026-11-21 — Amendment 1 §2, option A.** "Second" is literal. When the disclosure was
  > never shown on this installation — a restore whose backed-up setting was off — switching on does
  > not mark it shown: the disclosure shows then, as the installation's first, and the next pass
  > starts Tier C only after one of its buttons is pressed.

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
  *Note 2026-11-22, step 3.7 (G5a):* a native OpenCV error, provoked on the SM-G990B as a resize
  assertion, prints at priority E in two places. `Core.setErrorVerbosity(false)`, called where the
  pipeline loads, silences OpenCV's `cv::error()` line; it does not silence the JNI wrapper's
  `org.opencv.imgproc` "caught cv::Exception" line, which carries OpenCV's assertion text and an
  OpenCV source path — for the assertion provoked, no URI, box or embedding. That line is recorded as
  known limit L10 ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*), pending the
  maintainer's decision. The pass's own lines remain fixed. *2026-11-23:* the maintainer accepted L10
  as a recorded limit, with no precondition checks (*Open at 3.7*, question 5).
- **Never cached to disk.** A face thumbnail in the picker is cropped from the photo at display time
  and must not reach an image loader's disk cache. Step 3.6 verifies it.
  *Note 2026-11-23, step 3.7 (G8):* on the SM-G990B, Coil's disk-cache listing was unchanged after
  the People tab was scrolled end to end, but the cache directory never existed, so the listing could
  not have shown a write. The guarantee is the request's policy — `CachePolicy.DISABLED` in
  `ui/components/PersonFaceImage.kt`, asserted by a test; the device reading is consistent with it
  and adds nothing beyond it ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G8).

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

## Amendment 1 — 2026-11-21 — Step 3.6: the disclosure's buttons and wording, switching on, the app-open check, the switch-off order, and what is offered where (adopted 2026-11-21)

> **Adopted 2026-11-21, on the maintainer's approval** — his words "I approve the amendment and I
> choose the option you recommend": the whole amendment, §5's wording included, and option A at §2.
> Drafted by the Planner at step 3.6's refinement
> ([albums-stage-3.md](../../design/albums-stage-3.md), *3.6 — The People criterion, the disclosure
> and the switch*), from the Designer's design of the same day, and labelled *proposed* until then,
> as ADR-0018 Amendments 5 and 7 were, because the instructions' status vocabulary has no state for a
> draft; this record's `Active` status now covers this amendment too. **Nothing below was built
> before adoption.** Step 3.6's tasks S2, A and B build it; its tasks S1 and C depended on none of it
> and were built before adoption.

> **Built 2026-11-22 — step 3.6.** What landed is described in
> [ARCHITECTURE.md](../ARCHITECTURE.md) §3 (`FaceDataViewModel`, the People picker) and §11, and in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.6 — Built and verified 2026-11-22*; this
> note records only where the build reads the amendment.
> - **Option A (§2) as written:** switching on persists the setting and nothing else; it never marks
>   the disclosure shown. Only OK and Turn off do.
> - **The cleanup sentence (§4)** is shown only when the deletion returned not scrubbed
>   (`scrubbed == false`), not when it threw: a thrown deletion rolls back and leaves nothing
>   half-done, and the start-up deletion repeats it either way.
> - **App open (§3)** runs once per `FaceDataViewModel` instance — per activity, not per process —
>   so a relaunch runs it again, which is cheap because a stored answer for the current key stops it
>   at the first read. A `Throwable` from it is caught: an `Error` in a launch without a handler
>   would end the process, and the worker repeats the same work.
> - **§5's rule for an unknown capability** is built as two answers: not offered to the picker
>   (`isPeoplePickerOffered`), available to evaluation (`peopleAvailabilityOf`).
> - **No new preferences file:** the ViewModel writes `app_settings` and `face_device_state`
>   through the existing `FaceDataSettingStore` and `FaceDeviceStateStore`.
> - **Not proven on a device** at the close of 3.6, and deferred to step 3.7: that a tap outside
>   the disclosure does not close it (§1 is pinned by its `DialogProperties` in a unit test), that a
>   phone without arm64-v8a shows incapable (§3), and a native-speaker review of §5's wording in every
>   shipped locale. *Note 2026-11-24, step 3.8's audit:* step 3.7 settled the outside tap and Back
>   ([albums-stage-3.md](../../design/albums-stage-3.md), *3.7*, G6 and W1) — on the SM-G990B the
>   disclosure stayed showing. Still open: the no-arm64-v8a path on hardware (L1), and the
>   native-speaker review, before a release.
> - *Note 2026-11-24, step 3.8's audit — §1's "durably":* `FaceDeviceStateStore.markDisclosureShown`
>   writes with `apply()`, not `commit()`, so a process death before the write reaches disk shows the
>   disclosure again — the safe direction. Routed to the Coder as a possible one-line `commit()`
>   change at the next code change; not changed yet.
> - *Note 2026-11-24, step 3.8's audit — §4's order:* only the image loader's memory cache is
>   dropped under the lock. The picker's people and the chosen people drop when the re-read reports
>   the People criterion no longer offered, after the lock is released; the end state is the same.

**Why an amendment, when adoption left wording and placement to 3.6 and asked no further approval.**
Placement and layout stay the Designer's and are not here. What is here goes further than wording: §1
reads §2's "a direct way to the setting" and qualifies E8; §2 settles a case E10 did not foresee; §3
gives the capability record an answer without a self-test, a change to
[ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) §3's checker; and §5 is the one privacy
statement the app makes. Each is a reading the maintainer should own rather than inherit from a design
file.

### 1. The disclosure: OK and Turn off, and nothing else closes it

- **Two buttons.** **OK** records the disclosure as shown, durably, before it closes. **Turn off**
  records it as shown and switches face data off by §4's order, **without §4's confirmation**. No *Not
  now*: a deferral would leave the worker's gate shut with no end.
- **Not dismissable** by Back or a tap outside. *Why:* "shown" must mean a button was pressed, and an
  accidental tap must not use up a statement made once per installation.
- **It starts no run.** The first face pass is the next periodic run, or the user's Index now. *Why:*
  heavy work started by an OK is a surprise, and the app never starts Tier A by itself either.
- **Consistent with §2, with two qualifications.** §2 says "Dismissing the disclosure lets grouping
  proceed; it offers a direct way to the setting, so that saying no costs one tap." Turn off is the
  reading under which no costs one tap; a button that opened Settings would cost a tap, the switch and
  the confirmation. The qualifications: (a) *dismissing* becomes pressing OK; (b) Turn off skips E8's
  confirmation, whose reason — discarding hours of background work — cannot hold before the first pass,
  which the disclosure gates. The deletion still runs; with nothing stored it only re-checks the files.
- **Rejected:** a secondary button that opens the Face data area — three steps where §2 asks one; Back
  counted as OK — the accidental tap above.
- *Recommended by the Designer and the Planner; adopted 2026-11-21.*

### 2. Switching on when the disclosure was never shown

**The gap, found by the Designer.** On a restored device whose backed-up setting was off, the flag is
per installation and not restored (§2), and the disclosure is shown only while the setting is on — so
it never shows. When the user later switches face data on in Settings, E10 says no second disclosure.
With nothing more, the flag stays unset, the worker's gate stays shut for ever, and every people album
waits for ever with no notice, since waiting has none (decision 42). Two ways were offered to close
it, and **the maintainer chose A on 2026-11-21**:

- **A — the disclosure shows. Chosen; recommended by the Planner.** Its conditions (§5) all hold the
  moment the setting is switched on, so the dialog, hosted at the app's root, appears over Settings
  with no code of its own. *Why:* §2 makes the disclosure precede the first Tier C pass on each
  installation, and this installation has had none; E10 rejected a *second* disclosure and this is
  the first. *Cost:* one dialog right after a deliberate switch-on, once per installation; its Turn
  off just after an on is odd and harmless.
- ~~**B — switching on marks it shown.**~~ *Not chosen; it was the Designer's recommendation.* *Why
  it was offered:* the user turned it on from the row whose Off subtitle states the disclosure's
  facts, which is E10's own reason. *Why not:* on that installation the full statement — backups,
  deletion, the anchor sentence — would never be shown, and the Off subtitle would have to carry it.

**What happens, precisely.** In Settings, with the disclosure never shown on this installation:

1. The switch turns the setting **on** and does nothing else — it does **not** record the
   disclosure as shown. Switching on needs no confirmation (E8 confirms only the switch-off).
2. The disclosure's condition (§5's table) now holds — media permission granted, setting on,
   capability record for the current key says capable, not yet shown, the Full-screen viewer closed
   — so the dialog shows, hosted at the app's root (`MainActivity`), over Settings.
3. Until a button is pressed, the worker's gate stays shut: Tier C is not written, because the
   disclosure has not been shown (§2). Leaving the app without pressing one leaves the flag unset,
   and the dialog shows again the next time its condition holds.
4. **OK** records the disclosure as shown; the first face pass is the next periodic run or Index now
   (§1). **Turn off** records it as shown and switches face data off by §4's order without the
   confirmation (§1) — the setting returns to off, the disclosure is used up, and switching on again
   later shows nothing.

"Once per installation" is unchanged: this is the installation's first disclosure, and a second
switch-on shows none. On an installation where the disclosure was already shown, switching on shows
nothing (E10).

### 3. App open: the capability check, a durable answer without arm64-v8a, the start-up deletion

- **When:** when the app's face-data ViewModel is created — once per activity, not on a rotation —
  off the main thread. Free whenever an answer is stored.
- **The check:** read the stored record first; only when there is no answer for the current key, take
  the Tier C lock and run the checker. *Why the lock:* the worker takes it before its own check, and
  two checks at once load the models twice and miscount ADR-0023 §3's unfinished starts. *Why waiting
  for it rather than skipping:* its only other holder is the worker, which records an answer before
  anything else, so the wait ends with an answer to read.
- **Whatever the setting.** §1 offers the setting only on a capable device, so a user who switched it
  off needs an answer to see the switch again. The self-test uses a synthetic input and reads no media.
- **No `arm64-v8a` in `Build.SUPPORTED_ABIS`: incapable is recorded under the current key without
  loading anything.** The face pipeline ships for arm64-v8a only (ADR-0023 §2), so there is nothing to
  self-test, and without an answer a restored people album waits for ever on such a phone — the failure
  §4 names (step 3.5's finding). The check sits in the checker, the ABI list given by its caller, so it
  stays pure Kotlin and the worker records the same answer. The key names the app's version, so a later
  build that ships other ABIs is asked afresh. Out of memory stays transient, and so does a pipeline
  that fails to load on a device that does list arm64-v8a. *Rejected:* recording "no pipeline" when
  the loader throws — it loads native code to learn what the ABI list already says.
- **The start-up deletion**, while the setting reads off, under the lock, its result not shown —
  [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 7 §5's second caller.
- **Then the albums' people availability is re-read** — after the check, on every resume, and after
  either direction of the switch, the disclosure's Turn off included. Neither the setting nor the
  check writes Tier C, so without the call no album would change (§4's *Built* note).

### 4. Switching off: the order, and what memory drops

§3's four steps, made precise: the confirmation (E8), in which nothing changes and Cancel leaves the
setting on → persist off → take the Tier C lock, which waits for a running pass, itself stopped at its
next check → delete all face data → drop what memory holds: **the image loader's memory cache**, which
holds the picker's face crops — cleared whole unless the crops are keyed so they can be removed alone,
a one-off cost of re-decoding thumbnails — and the picker's people and the chosen people, dropped
because the People criterion stops being offered → release the lock → re-read the people availability.
Embeddings and grouping state are held only by the worker's pass, which frees them when it stops, so
the lock's wait covers them. In the face-data ViewModel's scope; cut short by the process ending, it is
finished by the start-up deletion at the next start, the setting being already off.

**A deletion not scrubbed** (ADR-0018 Amendment 6) shows one sentence under the switch, until Settings
is left — "Face data is deleted. Cleaning up its files will finish the next time the app starts." —
which is true because the rows are gone and the start-up deletion repeats the scrub. Scrubbed, nothing
is said: the Off subtitle is the confirmation. No Tier C figure in Settings, on or off.

### 5. The wording, and what is offered where

**The disclosure's wording** — English intent; every shipped locale in the same change, a
native-speaker review outstanding before a release:

> **People in your photos**
>
> Easy Gallery finds and groups the faces in your photos, so that you can make albums of people.
>
> This happens on your phone only. What it finds is never sent anywhere and is not included in backups.
>
> Faces are never named. An album of people remembers one photo for each person you pick, and that is
> saved and backed up with the album.
>
> Finding faces takes time and mostly runs in the background while the phone is charging.
>
> You can turn this off at any time in Settings, under Face data. Turning it off deletes everything
> that was found.
>
> [Turn off] [OK] — from Settings, *About face data*: [Close]

**The third paragraph was optional, recommended kept, and is kept: approved 2026-11-21 with the rest
of the wording.** An anchor is not face data (decision 9,
clarified 2026-11-17) and is backed up (§6); without the sentence, the second paragraph reads as if
nothing about faces ever reached a backup.

**What is offered where:**

| Surface | Offered when |
|---|---|
| The disclosure | media permission granted, setting on, capability record for the current key says capable, not yet shown on this installation, the Full-screen viewer closed |
| The Face data area in Settings | capability record for the current key says capable — setting on or off |
| The People criterion | setting on, and the record says capable |
| The Face licences row | always — the models and OpenCV are in the APK whatever the device |
| A people album's notice | by its cause, always (ADR-0017 Amendment 4) |

**An unknown capability is hidden here and available to evaluation**, on purpose. Evaluation must not
call a never-checked phone unavailable, which would put a wrong notice on every people album after
every update (step 3.5's rule); a screen must not offer a control that may vanish when the check ends.
§3's check is what keeps "unknown" short: one background check per build.

### 6. The Place and Date counts follow the chosen people

The match-count line must be the evaluator's member count with the chosen people — the stage's exit
criterion, not a choice. **Adopted 2026-11-21, as recommended by the Designer and the Planner: the
Place and Date lists'
counts follow the chosen people too**, as each already follows the other's choice. *Why the cost
argument against it does not hold:* the count line already needs the face snapshot; the lists reuse it
as one set of matching items per emission and a set lookup per item, never a face lookup per entry, and
the picker subscribes to the snapshot only while a person is chosen. *Not measured:* the snapshot's own
cost at production size is step 3.7's (items 11 and 15); if it proves too slow it is the snapshot that
needs fixing, and the count line alone is the fallback. The People list itself is never cross-filtered
(decision 40).

### What the maintainer was asked, and what he was not

- **Asked:** to approve this amendment as a whole, choosing A or B at §2 (A recommended); and to read
  §5's wording, changing it if he wished — approving it approves the English of step 3.6's task S2.
  ✅ **Answered 2026-11-21: approved as a whole, §5's wording unchanged, option A.**
- **Not asked:** D1 (the Designer's; a third tab, no navigation change); the layout; the notices'
  wording (ADR-0017 Amendment 4 leaves it to 3.6); the licence row (E5); the vocabulary; the split into
  tasks; the face-data ViewModel and the dialog at the app's root, each following a precedent; the
  translations.

### Consequences

- **Positive:** the restored-with-face-data-off gap and the wait for ever without arm64-v8a are both
  closed; a switch-off leaves no face crop in memory; one off path, from Settings and from the
  disclosure alike.
- **Negative:** the models are loaded once per build at app open even with face data off; a switch-off
  may empty the whole image memory cache; under §2's option A, one dialog follows a deliberate
  switch-on, once per installation, and only where the disclosure was never shown.
