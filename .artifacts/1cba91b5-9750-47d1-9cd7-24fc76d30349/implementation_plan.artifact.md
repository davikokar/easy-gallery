# Fix: Reactive Localization for Timeline Grouping

The issue is that the `GalleryViewModel` caches localized strings (like "Today" and "Yesterday") and the `StateFlow` for grouped media doesn't re-trigger when the app's language is changed. Since the `ViewModel` survives activity recreation, it keeps the old translations until the app is restarted.

## Proposed Changes

### [LocaleHelper.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/LocaleHelper.kt)
- Add an `applyLocale(context: Context)` method. This method will update the `Configuration` of the provided context (and its application context) to match the persisted language. This ensures that even the `Application` context, which is long-lived, can provide updated strings at runtime.

### [GalleryViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/GalleryViewModel.kt)
- Add a `_localeTrigger` `MutableStateFlow<Long>` initialized with the current time.
- Change `todayLabel`, `yesterdayLabel`, and `fileTypeLabels` to be updated via a new `updateLabels()` method.
- Update `groupedAllMedia` and `groupedFolderMedia` `combine` blocks to include `_localeTrigger` as an input. This forces the grouping logic to re-run whenever the locale changes.
- Add an `onLocaleChanged()` method that calls `updateLabels()` and updates `_localeTrigger`.

### [SettingsScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/SettingsScreen.kt)
- In the `onLanguageSelected` callback:
    1. Persist the new language tag.
    2. Call `LocaleHelper.applyLocale(context)` to update the current and application contexts.
    3. Call `viewModel.onLocaleChanged()` to refresh the ViewModel's state.
    4. Call `activity?.recreate()` to refresh the UI.

## Verification Plan

### Manual Verification
1. Navigate to the "Timeline" tab.
2. Observe grouping headers (e.g., "Today", "August 2026").
3. Go to Settings and change the language (e.g., to Spanish or Arabic).
4. Go back to the "Timeline" tab.
5. Verify that headers like "Today" change to "Hoy" (Spanish) or "اليوم" (Arabic) immediately without restarting the app.
6. Verify Month names also update correctly.
