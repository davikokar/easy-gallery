# Implementation Plan - Prepare for ViewModel/Unit Testing

This plan adds the necessary dependencies to support unit testing for ViewModels, Coroutines, and StateFlows.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/git/easy-gallery/gradle/libs.versions.toml)
- Add versions for:
    - `kotlinxCoroutinesTest = "1.11.0"`
    - `turbine = "1.2.1"`
    - `archCoreTesting = "2.2.0"`
- Add library definitions for:
    - `kotlinx-coroutines-test`
    - `app.cash.turbine:turbine`
    - `androidx.arch.core:core-testing`
    - `androidx.lifecycle:lifecycle-runtime-testing` (matching existing lifecycle version if possible, or latest)

#### [MODIFY] [app/build.gradle.kts](file:///C:/git/easy-gallery/app/build.gradle.kts)
- Add `testImplementation` for the newly defined libraries.

## Verification Plan

### Automated Tests
- Run `./gradlew test` (or `gradle_build(":app:assembleDebug")` followed by `gradle_build(":app:unitTests")`) to ensure the project still builds and any existing tests pass.
- Sync Gradle to ensure no dependency conflicts.

### Manual Verification
- None required as per prompt instructions (do not change app behavior).
