# Add "Buy Me a Coffee" In-App Purchase

Implement a consumable in-app purchase for tipping the developer, integrated into the Settings screen.

## User Review Required

> [!IMPORTANT]
> This implementation assumes a single product `tip_coffee`. If you have multiple tiers or products, the logic will need to be expanded.

> [!WARNING]
> Testing in-app purchases requires a real device with Google Play Services and a configured internal testing track in the Google Play Console.

## Proposed Changes

### UI Components

#### [MODIFY] [SettingsScreen.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/SettingsScreen.kt)
- Update `SettingsItem` composable to support an optional `subtitle`.
- Add "Community & Support" section.
- Add "☕ Buy me a coffee" item.
- Update `SettingsScreen` signature to accept `BillingViewModel`.

#### [NEW] [BillingViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/BillingViewModel.kt)
- Implement `BillingViewModel` using `BillingClient` (v7.0.0).
- Handle `onPurchasesUpdated` with immediate consumption for the consumable `tip_coffee` product.
- Provide functions to:
    - Initialize and connect to Google Play.
    - Query product details for `tip_coffee`.
    - Launch the billing flow using a provided `Activity`.
- Expose `StateFlow` for purchase status and product details.

### Core Implementation

#### [MODIFY] [MainActivity.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt)
- Instantiate `BillingViewModel` using `by viewModels()`.
- Pass `BillingViewModel` to `SettingsScreen`.

## Verification Plan

### Automated Tests
- Build the app to ensure no compilation errors.
- Unit test `BillingViewModel` if possible (mocking `BillingClient` is complex but can be done for state transitions).

### Manual Verification
- Deploy to a physical device.
- Navigate to Settings -> Community & Support.
- Tap "☕ Buy me a coffee".
- Verify Google Play Billing bottom sheet appears.
- Verify successful purchase results in a "Thank you" Toast.
- Verify the item can be purchased again (consumable check).
