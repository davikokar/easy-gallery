# Architecture

This directory holds the architectural documentation for Easy Gallery.

## Architecture Decision Records

Architecture Decision Records (ADRs) live in [decisions/](decisions/) and capture choices with
lasting impact on system structure, dependencies, data ownership, deployment, security,
performance, platform support, or cross-feature conventions.

Records are never deleted or renumbered. When a decision stops governing the project, a new ADR
is created and the old one is marked `Disabled` with a link between the two.

See [.github/instructions/architecture-decisions.instructions.md](../../.github/instructions/architecture-decisions.instructions.md)
for the required format and status rules.

### Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-0001](decisions/0001-on-demand-media-location-metadata.md) | On-demand media location metadata | Active | 2026-09-21 |
| [ADR-0002](decisions/0002-viewmodel-owned-transient-viewer-state.md) | ViewModel-owned transient viewer state across configuration changes | Active | 2026-09-21 |
| [ADR-0003](decisions/0003-explicit-media-operation-target.md) | Explicit operation target for copy and move | Active | 2026-09-22 |
| [ADR-0004](decisions/0004-delegate-wallpaper-setting-to-system-cropper.md) | Delegate wallpaper setting to the system crop-and-set activity | Active | 2026-09-22 |
