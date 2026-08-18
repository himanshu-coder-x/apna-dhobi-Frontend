# Database Audit - Apna Dhobi

## Connection Analysis
- **Type**: Room Persistence Library (SQLite).
- **Initialization**: `ApnaDhobiViewModel.kt` (Lines 37-41).
- **Database Name**: `apna_dhobi_db`.
- **Scope**: Local to the mobile device. No synchronization with a remote central database.

## Models / Entities
Located in `app/src/main/java/com/example/data/AppDatabase.kt`.

### 1. `CartItem`
- **Fields**: `id`, `productId`, `productName`, `category`, `originalPrice`, `discountPrice`, `quantity`, `vendorId`, `vendorName`, `dryCleaningType`, `userNotes`, `reviewRating`.
- **Status**: Complete for local cart usage.

### 2. `UserProfile`
- **Fields**: `phone` (Primary Key), `name`, `email`, `isGoogleSignedIn`, `referralCodeUsed`, `signupTimestamp`.
- **Status**: Basic. Missing password hash field, roles, and session tokens.

### 3. `SavedAddress`
- **Fields**: `id` (Auto-gen), `label`, `addressLine`, `isDefault`.
- **Status**: Functional for local storage.

### 4. `OrderRecord`
- **Fields**: `id` (Auto-gen), `vendorName`, `itemsSummary`, `totalPrice`, `pickupSlot`, `deliverySlot`, `paymentMethod`, `status`, `timestamp`.
- **Status**: Simplified. Missing relationships to `UserProfile` and individual line items.

### 5. `SupportMessage`
- **Fields**: `id` (Auto-gen), `sender`, `text`, `timestamp`.
- **Status**: Complete for chat history.

## Missing Models (Required for Real Backend)
- **Vendors**: Currently hardcoded in `ApnaDhobiRepository.kt`. Needs a dedicated DB table.
- **Products/Services**: Currently hardcoded. Needs a central catalog table.
- **Payments**: Ledger table for transactions.
- **Logistics**: Tables for delivery agents, vehicle status, and route logs.
- **Roles/Permissions**: Table for mapping users to Admin, Vendor, or Customer roles.

## Static/Mock Data Findings
- **Vendors List**: Hardcoded in `ApnaDhobiRepository.kt` (Lines 83-138).
- **Services Catalog**: Hardcoded in `ApnaDhobiRepository.kt` (Lines 71-81).
- **Products Catalog**: Hardcoded in `ApnaDhobiRepository.kt` (Lines 140-192).

## Data-Integrity Risks
- **Local-Only**: If the user uninstalls the app or clears cache, all data (including order history and profile) is permanently lost.
- **Price Manipulation**: Since calculations happen on the frontend (`ApnaDhobiViewModel`), a compromised app could modify `cartFinalTotal` before local DB insertion.
- **Primary Key Safety**: `CartItem` uses a composite-like string `${vendorId}_${productId}` which is prone to conflicts if naming conventions change.
