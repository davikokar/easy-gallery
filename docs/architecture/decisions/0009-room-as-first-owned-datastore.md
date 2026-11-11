# ADR-0009: Room as the app's first owned datastore

- Status: Active
- Date: 2026-10-07
- Decision makers: Easy Gallery project maintainer

## Context

Easy Gallery owns no data. Media is a live `MediaStore` query, and the only persisted state is
five small `SharedPreferences` files holding display preferences, pinned and excluded folder
paths, per-folder overrides, and folder thumbnail overrides. Every one of them is a flat map keyed
by a folder path. There is no per-media-item state anywhere in the app, and no query engine.

The albums epic (`docs/design/albums.md`) changes that. An album is a named, app-owned collection
of references to media items, and the app must store those references itself — `MediaStore` has no
concept of a user album. This is the first data the app owns.

The forces shaping the choice:

- **Stage 1 is not the sizing constraint.** Albums and their membership rows number in the
  hundreds, perhaps low thousands. Almost any storage mechanism would serve. Stage 2's media index
  is 50,000+ rows with range queries over latitude, longitude and capture date, and Stage 4 adds
  vector similarity over content embeddings. A mechanism chosen to fit Stage 1 would be discarded
  at Stage 2.
- **The repository has no dependency-injection framework.** Components are instantiated directly
  so tests can pass fakes, and every persistence interface already ships a `SharedPreferences*`
  production implementation alongside an `InMemory*` test implementation. Whatever is chosen has to
  fit that convention rather than fight it.
- **State is exposed as `StateFlow` and derived with `combine`.** Album membership changes must
  propagate to the UI through the same mechanism as everything else.
- **The repository prefers flat, explicit code** over abstraction, which argues against anything
  that introduces a large conceptual surface.
- Adding a third-party dependency requires the maintainer's approval under `AGENTS.md`. It was
  sought and given.

## Decision

Adopt **Room** as the app's database, and choose it on the requirements of Stage 2's index rather
than Stage 1's albums.

- The database instance is a singleton held by `EasyGalleryApp`, alongside the Coil `ImageLoader`.
  There is no DI framework to own it, and a second instance would defeat Room's invalidation
  tracking.
- Data access is exposed to the rest of the app behind interfaces, in the same shape as the
  existing preference stores: a Room-backed production implementation and an `InMemory*`
  implementation for unit tests. Room's own `inMemoryDatabaseBuilder` is used for the tests that
  exercise the DAO itself.
- Queries that feed the UI return `Flow<List<T>>` so Room's invalidation tracker drives
  recomposition, rather than the app re-reading on a trigger.
- The existing `SharedPreferences` stores are **not** migrated into Room. They work, they are
  tested, and converting them would be churn with no user-visible benefit.

## Alternatives Considered

**Raw SQLite via `SupportSQLiteOpenHelper`.** No new dependency, and genuinely in keeping with the
repository's flat and explicit style. Rejected because the work it saves at Stage 1 it charges back
with interest at Stage 2: hand-written migrations, hand-written cursor mapping for every index
column, and a hand-rolled change-notification mechanism to replace Room's invalidation tracker.
That is a large amount of untested infrastructure standing between the app and its first index.

**SQLDelight.** Compile-time-verified SQL with a Kotlin-first API, and a good fit in principle.
Rejected as the less-travelled road for an Android-only project: fewer Android-specific examples,
a different mental model from the platform norm, and no advantage here large enough to pay for
that.

**Proto DataStore, or JSON/Protobuf files.** Adequate for albums alone. Rejected because they are
document stores, not query engines — every read deserialises the whole document and every write
rewrites it. At 50,000 index rows with range queries this is not a close call.

**Deferring the choice by storing albums in `SharedPreferences` for Stage 1.** Rejected as
knowingly building something to throw away, and as a sixth preferences file holding structured
relational data, which is exactly what that mechanism is bad at.

## Consequences

### Positive

- The storage mechanism is chosen once, for the whole epic, instead of being revisited at Stage 2.
- `inMemoryDatabaseBuilder` matches the established `InMemory*` test convention, so the testing
  pattern does not change.
- `Flow`-returning queries supply change notification for free, which is what makes "automatic
  albums are live" (`docs/design/albums.md` §6) affordable.
- Schema changes become visible and reviewable: a numbered migration and an exported schema JSON,
  rather than an undocumented change in how a preferences blob is parsed.

### Negative

- First annotation processor (KSP) in the project, with a build-time cost on every module build.
- Migrations become a permanent discipline. A schema change now requires a `MIGRATION_<from>_<to>`,
  a bumped version, an exported schema, and a test — and getting one wrong corrupts user data
  rather than merely failing a build.
- `AGENTS.md` and `docs/architecture/ARCHITECTURE.md` both state the app has no database. Both
  become false the day this lands and must be corrected in the same change.
- A new singleton on `EasyGalleryApp` is a pattern the app has not previously needed. It must not
  become a general-purpose service locator.
- The app now has data that can be corrupted, migrated wrongly, or lost on uninstall — a class of
  failure Easy Gallery has never had.

## References

- `docs/design/albums.md` — the albums epic, especially §1 and §6
- ADR-0010 — what an album membership row actually stores
- ADR-0012 — the album model the schema has to express
- `app/src/main/java/com/davide/seddio/easygallery/data/DisplayPreferencesStore.kt` — the
  interface-plus-two-implementations convention this follows
