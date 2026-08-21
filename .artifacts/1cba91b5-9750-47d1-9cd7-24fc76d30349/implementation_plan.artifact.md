# Migration to Play Billing Library 9.1.0

The app was recently updated to Play Billing Library 9.1.0. This major version update introduced several breaking changes in the `BillingClient` initialization and callback structures.

## Proposed Changes

### UI Components

#### [MODIFY] [BillingViewModel.kt](file:///C:/git/easy-gallery/app/src/main/java/com/davide/seddio/easygallery/ui/BillingViewModel.kt)
- **Update `BillingClient` initialization**:
    - Move the `PurchasesUpdatedListener` (`this`) into the `newBuilder` constructor.
    - Update `enablePendingPurchases()` to use `PendingPurchasesParams` as required by the new API.
- **Update `queryProductDetailsAsync`**:
    - Refactor the lambda to handle the new `ProductDetailsResult` object instead of the separate `billingResult` and `productDetailsList` parameters.
- **Service Connection (Recommended)**:
    - While not strictly required for the fix, I will ensure the connection handling remains robust under the new library version.

## Verification Plan

### Automated Tests
- Run `./gradlew app:assembleDebug` to verify that the compilation errors are resolved.

### Manual Verification
- Verify the "Buy me a coffee" feature in the settings screen.
- Ensure the toast message "Thank you so much for your support!" still appears after a successful (simulated) purchase.
