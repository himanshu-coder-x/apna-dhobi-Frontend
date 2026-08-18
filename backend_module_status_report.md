# Backend Module Status Report - Apna Dhobi

This report outlines the status of various backend modules as implemented (or mocked) in the current codebase.

| Module | Frontend Status | Backend Status | Database Status | API Status | Real/Mock | Completion % | Evidence | Missing Work |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **User Authentication** | UI Exists | Missing | Local Profile Table | None | Mock | 10% | `ApnaDhobiViewModel.kt` (OTP sim) | Server-side OTP, JWT sessions, Bcrypt hashing. |
| **Vendor Management** | UI Exists | Missing | None | None | Mock | 10% | `ApnaDhobiRepository.kt` (Hardcoded list) | CRUD APIs, Vendor validation, Multi-tenancy. |
| **Order Management** | UI Exists | Missing | Local Order Table | None | Mock | 25% | `AppDatabase.kt` (OrderRecord) | Order logic, Transaction safety, Admin APIs. |
| **Cart & Checkout** | UI Exists | Missing | Local Cart Table | None | Mock | 25% | `AppDatabase.kt` (CartItem) | Server-side validation of prices/inventory. |
| **AI Assistant** | UI Exists | N/A (External) | Local Chat Table | Connected | Real (P2P) | 75% | `GeminiService.kt` | Move API key to secure backend (proxy). |
| **Email/Notifications** | UI Exists | Missing | None | Connected | Real (P2P) | 60% | `SmtpEmailSender.kt` | Centralized notification queue, Failure retry. |
| **Payment/Wallet** | UI Exists | Missing | ViewModel State | None | Mock | 10% | `ApnaDhobiViewModel.kt` (Balance var) | Gateway integration (Razorpay/Stripe), Ledger. |
| **Admin Dashboard** | UI Exists | Missing | ViewModel State | None | Mock | 10% | `AdminPremiumDashboard` UI | Real analytics, User/Vendor management APIs. |
| **Logistics/GPS** | UI Exists | Missing | ViewModel State | None | Mock | 10% | `GoogleMapsSubPanel` UI | Real-time WebSockets, Driver app APIs. |
| **Address Management**| UI Exists | Missing | Local Address Table| None | Mock | 25% | `AppDatabase.kt` (SavedAddress) | Server-side address book synchronization. |

### Module Legend
- **Frontend Status**: UI implementation in Compose.
- **Backend Status**: Server-side logic implementation.
- **Database Status**: Persistence layer (Room/SQL).
- **API Status**: Networking layer (Retrofit/Direct REST).
- **Real/Mock**: Whether the data comes from a real external source or local simulation.
- **Completion %**: Overall implementation progress based on production requirements.
