# Cleanup Plan - Conservative Cleanup

This plan outlines a conservative cleanup pass to remove dead code, optimize imports, and fix minor formatting issues without affecting the application's behavior.

## User Review Required

> [!NOTE]
> This cleanup is conservative and focuses on removing unused functions, stale imports, and template files. No functional changes to the app's features (MediaStore interaction, UI layout, etc.) are included.

## Proposed Changes

### [Component] Tests Cleanup

#### [DELETE] [ExampleUnitTest.kt](file:///C:/git/easy-gallery/app/src/test/java/com/davide/seddio/easygallery/ExampleUnitTest.kt)
#### [DELETE] [ExampleInstrumentedTest.kt](file:///C:/git/easy-gallery/app/src/androidTest/java/com/davide/seddio/easygallery/ExampleInstrumentedTest.kt)

### [Component] Data Layer Cleanup

#### [MODIFY] [MediaStoreDataSource.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt)
- Remove unused private recursive functions: `scanDirectory(File)` and `deleteRecursive(File)`.

#### [MODIFY] [GalleryModels.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/GalleryModels.kt)
- Fix indentation for `GalleryUiState.Success` to match the project style.

### [Component] UI & ViewModel Cleanup

#### [MODIFY] [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Remove unused public functions: `performOperation(Folder)` and `toggleMediaType(MediaType)`.
- Remove stale imports: `java.text.SimpleDateFormat` and `java.util.*`.

#### [MODIFY] [MediaSelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaSelectionTopBar.kt)
- Remove `onRename` parameter.
- Remove "Rename" menu item from the dropdown menu.

#### [MODIFY] [SelectionTopBar.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/components/SelectionTopBar.kt)
- Remove `onRename` parameter.
- Remove "Rename" menu item from the dropdown menu.

#### [MODIFY] [FolderListScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderListScreen.kt)
- Remove `onRename` placeholder calls.
- Remove stale `java.io.File` import.

#### [MODIFY] [FolderDetailScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/FolderDetailScreen.kt)
- Remove `onRename` placeholder calls.
- Remove stale `java.text.SimpleDateFormat` and `java.util.*` imports.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :app:testDebugUnitTest`
- Verify that `GalleryViewModelTest.kt` and other real tests still pass.

### Manual Verification
- Compile and build the app: `./gradlew :app:assembleDebug`
- Verify that the "Rename" option is no longer visible in selection menus.
