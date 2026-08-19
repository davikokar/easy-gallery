# Localization Support for Easy Gallery

This plan outlines the steps to add full Android localization support to Easy Gallery, covering 10 languages and ensuring RTL support for Arabic.

## Proposed Changes

### Resources

#### [MODIFY] [strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values/strings.xml)
- Add all UI strings extracted from the code.
- Mark `app_name` as `translatable="false"` if needed (though requirement says keep it "Easy Gallery").
- Include placeholders for dynamic content (e.g., `%1$d / %2$d`).

#### [NEW] Localized strings.xml files
- [es/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-es/strings.xml)
- [zh-rCN/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-zh-rCN/strings.xml)
- [fr/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-fr/strings.xml)
- [it/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-it/strings.xml)
- [de/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-de/strings.xml)
- [pt-rBR/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-pt-rBR/strings.xml)
- [ja/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-ja/strings.xml)
- [ko/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-ko/strings.xml)
- [ar/strings.xml](file:///C:/git/easy-gallery/app/src/main/res/values-ar/strings.xml)

### Logic Refactoring

#### [MODIFY] [GalleryTransformations.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/data/GalleryTransformations.kt)
- Update `groupMedia` to accept localized strings for "Today", "Yesterday", and file type labels.
- Use `java.text.DateFormat` or localized patterns for date formatting instead of hardcoded strings.

#### [MODIFY] ViewModels
- [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt): Resolve localized strings for grouping and error states.
- [CreateFolderViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/CreateFolderViewModel.kt): Use `getApplication().getString()` for error messages.
- [BillingViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/BillingViewModel.kt): Localize the "Thank you" toast.

### UI Components

#### [MODIFY] Compose UI Files
- Replace all hardcoded strings with `stringResource(id = R.string.resource_name)`.
- Use `pluralStringResource` where applicable (though mostly using simple strings for now).
- Ensure `selectedCount / totalCount` uses formatted string resources.

### RTL and Layouts

- Verify that all `Row` and `Column` arrangements use `Start`/`End` (default in Compose).
- Check `Modifier.padding` for any `absolute` variants.
- Test Arabic layout in Compose Preview if possible.

## Verification Plan

### Automated Tests
- Run `app:assembleDebug` to ensure no resource errors.
- Run existing UI tests to ensure extraction didn't break functionality.

### Manual Verification
- Change device language to Arabic to verify RTL and translations.
- Change device language to Spanish/French to check for text overflow in buttons/dialogs.
- Verify "Easy Gallery" remains in English across all languages.
