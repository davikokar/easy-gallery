# ADR-0014: The album database is included in Android auto-backup

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

Albums are the first data Easy Gallery owns (ADR-0009). Everything else the app displays is
derivable: media comes live from `MediaStore`, and the five `SharedPreferences` files hold
preferences a user can shrug off and set again. An album is different. It is the product of
deliberate effort, it cannot be recomputed from anything, and losing it is not an inconvenience but
the destruction of work.

The app's backup posture today is an accident of the project template rather than a decision:

- `AndroidManifest.xml` sets `android:allowBackup="true"`.
- `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` are the generated samples, with
  every `<include>` and `<exclude>` still commented out.

Auto Backup includes, by default, the directory returned by `getDatabasePath()` — which is where
Room puts its database — and a transfer section with no rules in it is fully enabled for everything
except the cache and no-backup directories. So the album database is **already** in scope for both
cloud backup and device-to-device transfer the day it lands. Nothing has to be built to include it.
What is missing is the decision, and the reasoning that makes the consequence survivable.

The consequence is not obvious, which is why it needs recording. ADR-0010 identifies a membership by
a content URI string, and that key is device-local in every part: volume, collection, and `_ID` are
all assigned by the receiving device's `MediaStore`. A restored database therefore lands on a device
where **every** stored key is wrong at once. This is not a rare edge of the healing design; it is
the healing design being exercised across the entire library in a single pass.

Two existing facts decide whether that is acceptable:

- ADR-0010's healing key is `DISPLAY_NAME` + `SIZE` + `DATE_MODIFIED`, which describes the *file*
  rather than its row. It is as valid on the new device as it was on the old one.
- ADR-0010 never deletes an unresolvable membership. It is marked orphaned, retained, and re-checked
  later, so a membership whose file has not arrived yet waits rather than disappearing.

Sizing is not a constraint for Stage 1. Albums and their membership rows number in the hundreds to
low thousands, far inside the 25 MB per-app backup quota. Stage 2's media index is 50,000+ rows and
is **not** obviously inside it; see Consequences.

## Decision

**Include the album database in Android auto-backup.**

Concretely, this means keeping what is already there:

- `android:allowBackup="true"` stays.
- The two rules files stay as they are. An absent `<include>` means "everything the system includes
  by default", and an absent transfer section means that transfer mode is fully enabled. Adding
  rules that spell out the current behaviour would make the files look authored and would silently
  change the default for anything added later — the first `<include>` element switches the whole
  backup from "almost everything" to "only what is listed".

So there is no implementation. This ADR records a decision that was previously being taken by
default, and gives the next person a reason rather than an accident to read.

**A restore is handled by ADR-0010's existing healing, and needs no restore-specific code.** Every
membership misses its stored URI, matches on the healing key, and rewrites the URI. Memberships
whose files are not present are orphaned, retained, and re-checked when the library next changes.
There is no `BackupAgent`, no `onRestoreFinished` hook, and no migration path specific to restore.

**Scope.** This ADR covers the album database as Stage 1 defines it: albums, memberships,
exclusions, and the cover column of ADR-0015. It deliberately decides nothing about Stage 2's media
index or Stage 3's face data. `docs/design/albums.md` §8.9 already commits that face data never
enters a backup; honouring that will require an explicit `<exclude>` when Tier C lands, and adding
that first `<exclude>` must not be mistaken for an opportunity to also add `<include>` elements.

## Alternatives Considered

**Exclude the database from backup.** The conservative choice, and the one that avoids every healing
risk below. Rejected on what it costs the user: albums would die with the device. Someone who spent
an evening curating albums and then replaces their phone watches their *photos* arrive intact — Google
Photos, a device-to-device transfer, a vendor migration tool — while the structure they built over
those photos is simply gone, with no explanation and no way to have prevented it. The risks of
including it are all recoverable or cosmetic; the cost of excluding it is total and permanent.

**Exclude membership rows but back up album names and rules.** Smaller, and immune to mis-healing.
Rejected because the result is worse than either alternative: the user gets a list of their albums,
all empty, with nothing to show and no way to tell whether the app is broken or merely waiting. They
must either delete albums they wanted or refill each one by hand. An empty album is indistinguishable
from a failed one, and this alternative manufactures that state deliberately on a device where the
media is usually present and would have healed.

**A custom `BackupAgent` that re-keys memberships during restore.** Superficially the tidy answer:
rewrite the URIs once, at restore time, instead of healing lazily. Rejected because it cannot work.
The system runs the app in a restricted mode during backup and restore in which content providers
are not initialised, so the agent cannot query `MediaStore` to find the new IDs. It would also be
re-keying against media that frequently has not been restored yet — the gallery app is commonly
installed before the photos finish arriving. The lazy, repeatable heal is not a weaker version of
this; it is the only version that is correct.

**Require the user to opt in to backing up albums.** Rejected as a setting that exists only because
the developer could not decide. Device backup is already a system-level choice the user has made,
and a second, app-level switch asking the same question in different words is the kind of
configuration this project's conventions avoid.

## Consequences

### Positive

- Albums survive device replacement, factory reset, and reinstall. That is what makes them worth the
  effort of curating in the first place.
- Nothing has to be built, and nothing has to be maintained. The cost of this decision is zero code.
- Device-to-device transfer is the case that heals best: the media usually arrives by the same
  transfer, with name, size and modification time preserved, so most memberships heal on first
  resolution and the user never learns anything happened.
- The posture is now on the record. If someone later sets `allowBackup="false"` for an unrelated
  reason — a template update, a security review, a copied snippet — it is a visible reversal of a
  documented decision rather than a silent loss of everyone's albums.

### Negative

- **Healing runs across the entire library at once.** The duplicate-collision case ADR-0010 accepts
  as rare is, on restore, given as many chances to occur as there are memberships. A library with
  burst exports or duplicated copies may attach a membership to the wrong twin. The blast radius is
  still one wrong photo in one album, never data loss, but it is no longer a remote possibility.
- **The healing key is only as durable as the restore mechanism.** It holds if the file is restored
  byte-identically with its metadata intact. `DATE_MODIFIED` is the fragile component: a restore path
  that rewrites the file, or any app that calls `setLastModified()`, changes it, and the membership
  stays orphaned even though the photo is sitting there. `docs/design/albums.md` §6 already notes
  that `DATE_MODIFIED` moves for reasons unrelated to the file's contents; this is where that bites.
- **A user restoring onto a device with none of their media gets albums that are entirely
  orphaned.** They are correct — retained, waiting, and they will populate when the photos arrive —
  but if the UI shows them as plain empty albums the user reads a bug and deletes them. Albums View
  and Album Detail View must distinguish "empty" from "waiting", in the spirit of §6's rule that an
  automatic album which is quietly incomplete is worse than one that says what it is doing. This is
  a UI obligation created by this ADR, not an optional refinement.
- **The 25 MB quota is shared with everything else the app ever backs up.** Exceeding it calls
  `onQuotaExceeded()` and stops the cloud backup entirely — it does not drop the largest file and
  keep the rest. Stage 2's index could plausibly approach that, and if it does it would take albums
  down with it. Stage 2 must decide explicitly whether the index is excluded; the index is derived
  data and can be rebuilt, so excluding it is likely correct, but it must be decided rather than
  discovered when a user's albums stop backing up.
- **An album now survives uninstall, but only conditionally.** The database is still deleted on
  uninstall; it comes back on reinstall only if the user has device backup enabled and a backup
  exists. Favourites, which the platform owns, survives outright and depends on nothing. That is a
  sharper form of the distinction `docs/design/albums.md` §2 draws — albums are app-owned,
  Favourites is platform-owned — and the §2 row states it directly.

## References

- `docs/design/albums.md` §2 (what an album is, and the uninstall row this decision is the reason
  for), §6 (staleness must be visible; `DATE_MODIFIED` is not trustworthy), §8.9 (face data never
  enters a backup)
- ADR-0009 — the database this decision covers
- ADR-0010 and its Amendment 1 — the device-local key, and the healing that makes a restore survivable
- ADR-0015 — the cover column, which is covered by this decision and heals with the membership row
  it references, so a restore leaves nothing cover-specific to repair
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml` — the current, unauthored state this adopts
- <https://developer.android.com/identity/data/autobackup> — files included by default, the 25 MB
  quota, restore timing, and the restricted mode a `BackupAgent` would run in
