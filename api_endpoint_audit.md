# API Endpoint Audit - Apna Dhobi

Since no dedicated backend exists, the only "endpoints" currently in the project are those for third-party external services.

| Method | Endpoint | Purpose | Authentication | Role | Handler File | Model | Validation | Current Status | Risk |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| POST | `https://generativelanguage.googleapis.com/v1beta/models/...` | AI Support Chat | API Key | Any | `GeminiService.kt` | JSON Payload | Basic | Connected | Key exposure in APK. |
| SOCKET| `smtp.gmail.com:465` | Send Emails | User/Pass | Any | `SmtpEmailSender.kt`| Plaintext/HTML| None | Connected | Hardcoded credentials in VM. |
| SOCKET| `pop.gmail.com:995` | Fetch Emails | User/Pass | Any | `PopEmailFetcher.kt` | Plaintext | None | Connected | Credential theft risk. |

### Internal "Pseudo-Endpoints" (ViewModel Functions)
These functions act as API handlers by performing local database operations and simulations.

| Function | Purpose | Database Model | Real/Mock |
| :--- | :--- | :--- | :--- |
| `registerUserProfile` | Create new user | `UserProfile` | Local (Persistent) |
| `processCheckout` | Create new order | `OrderRecord` | Local (Persistent) |
| `handleSendMessage` | Send chat message | `SupportMessage`| Hybrid (AI Real + DB) |
| `createWalletDepositRequest`| Request recharge | `WalletDepositRequest`| Local (State only) |
| `runOrderStatusSimulation`| Simulate backend | `OrderRecord` | Mock Logic |

**Conclusion**: 0 Custom Backend endpoints currently implemented.
