# Walkthrough - "Buy Me a Coffee" In-App Purchase Integration

I have successfully implemented the "Buy Me a Coffee" consumable in-app purchase. This allows users to support development through a simple tip, which is immediately consumed to allow for repeat purchases.

## Changes

### UI & UX Improvements

#### [SettingsScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/SettingsScreen.kt)
- **Enhanced Settings Items**: Updated the `SettingsItem` component to support optional subtitles for better context.
- **New Section**: Added a "Community & Support" section at the bottom of the Settings screen.
- **Support Action**: Integrated the "☕ Buy me a coffee" item with the subtitle "Support independent app development".

### Core Implementation

#### [BillingViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/BillingViewModel.kt)
- **Billing Lifecycle**: Handles the full lifecycle of `BillingClient` (connection, querying, purchasing, and closing).
- **Consumable Handling**: Automatically consumes the `tip_coffee` purchase immediately upon success.
- **User Feedback**: Displays a "Thank you" Toast when a purchase is successfully processed and consumed.

#### [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- **Integration**: Wired the `BillingViewModel` into the application flow and passed it to the `SettingsScreen`.

## Verification Results

### Automated Tests
- **Build**: `app:assembleDebug` completed successfully, ensuring all library references (Billing Library v7) and code changes are valid.

### Manual Verification Instructions
> [!TIP]
> To test the implementation:
> 1. Deploy the app to a physical device with Google Play Services.
> 2. Go to **Settings**.
> 3. Scroll to **Community & Support**.
> 4. Tap **☕ Buy me a coffee**.
> 5. Verify the Google Play purchase dialog appears for "tip_coffee".
> 6. Complete the purchase and verify the "Thank you" Toast appears.
