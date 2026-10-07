# Architecture

This directory holds the architectural documentation for Easy Gallery.

[ARCHITECTURE.md](ARCHITECTURE.md) describes the current system: the three main views, the MVVM
data flow, the layer responsibilities, and the project structure. Read it before changing how
state is owned or how the views are composed. It is maintained by the Planner and updated in the
same change as the work it describes; the trigger list is in
[the architecture documentation instructions](../../.github/instructions/architecture-docs.instructions.md).

In-progress epics are specified under [../design/](../design/). Those documents describe work that
may not exist yet; ARCHITECTURE.md describes only what has actually shipped.

## Architecture Decision Records

Architecture Decision Records (ADRs) live in [decisions/](decisions/) and capture choices with
lasting impact on system structure, dependencies, data ownership, deployment, security,
performance, platform support, or cross-feature conventions.

Records are never deleted or renumbered. When a decision stops governing the project, a new ADR
is created and the old one is marked `Disabled` with a link between the two.

See [the architecture documentation instructions](../../.github/instructions/architecture-docs.instructions.md)
for the required format and status rules.

### Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-0001](decisions/0001-on-demand-media-location-metadata.md) | On-demand media location metadata | Active | 2026-09-21 |
| [ADR-0002](decisions/0002-viewmodel-owned-transient-viewer-state.md) | ViewModel-owned transient viewer state across configuration changes | Active | 2026-09-21 |
| [ADR-0003](decisions/0003-explicit-media-operation-target.md) | Explicit operation target for copy and move | Active | 2026-09-22 |
| [ADR-0004](decisions/0004-delegate-wallpaper-setting-to-system-cropper.md) | Delegate wallpaper setting to the system crop-and-set activity | Active | 2026-09-22 |
| [ADR-0005](decisions/0005-selection-scoped-media-actions.md) | Selection-scoped media actions mirror the full-screen viewer | Active | 2026-09-22 |
| [ADR-0006](decisions/0006-per-folder-display-preference-overrides.md) | Per-folder display preference overrides layered over global defaults | Active | 2026-09-25 |
| [ADR-0007](decisions/0007-persisted-folder-thumbnail-override.md) | Persisted per-folder thumbnail override resolved against live media | Active | 2026-09-25 |
| [ADR-0008](decisions/0008-camera-folder-default-sort-order.md) | Implicit camera-folder sort default resolved beneath explicit preferences | Active | 2026-09-26 |
| [ADR-0009](decisions/0009-room-as-first-owned-datastore.md) | Room as the app's first owned datastore | Active | 2026-10-07 |
| [ADR-0010](decisions/0010-album-membership-dual-key-identity.md) | Album membership identifies media by a self-healing dual key | Active | 2026-10-07 |
| [ADR-0011](decisions/0011-favourites-backed-by-mediastore.md) | Favourites is MediaStore-backed with a version-tiered write path | Active | 2026-10-07 |
| [ADR-0012](decisions/0012-one-album-type-rule-additions-exclusions.md) | One album type — rule, additions, and exclusions | Active | 2026-10-07 |
| [ADR-0013](decisions/0013-albums-as-third-display-mode.md) | Albums is a third display mode, and the navigation chain stays | Active | 2026-10-07 |
