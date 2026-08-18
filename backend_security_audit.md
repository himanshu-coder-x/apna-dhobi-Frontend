# Backend Security Audit - Apna Dhobi

| Severity | Finding | File Path | Evidence | Impact | Recommended Fix |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CRITICAL** | Hardcoded Email Credentials | `ApnaDhobiViewModel.kt` | `smtpPass = MutableStateFlow("taqk iwdr zmqy ppyd")` | Full access to the developer's Gmail account for anyone with the APK. | Remove credentials from code immediately; use an environment-controlled backend proxy. |
| **HIGH** | Client-Side Admin Bypass | `ApnaDhobiViewModel.kt` | `isAdminSessionActive = MutableStateFlow(false)` | Anyone can toggle the boolean in memory or via debugger to access Admin UI. | Move Admin auth and authorization checks to a secure backend. |
| **HIGH** | Gemini API Key Exposure | `GeminiService.kt` | `val apiKey = BuildConfig.GEMINI_API_KEY` | API key can be extracted from APK strings/BuildConfig, leading to financial cost/abuse. | Proxy AI requests through a backend server that holds the key. |
| **HIGH** | Trusting Client-Side Prices | `ApnaDhobiViewModel.kt` | `val cartFinalTotal = combine(...) { ... }` | Users can modify local state to set the order total to ₹0. | Always recalculate and validate prices on the server. |
| **MEDIUM** | Insecure Authentication | `ApnaDhobiViewModel.kt` | `suspend fun attemptUserLogin(phone: String)` | No actual verification happens on the server. Phone number alone is trusted. | Implement server-side OTP verification with real SMS providers. |
| **MEDIUM** | Weak PII Storage | `AppDatabase.kt` | `UserProfile` entity | Plaintext storage of name, email, and phone in a local SQLite file accessible on rooted devices. | Encrypt the local database or move PII to a secure remote backend. |
| **LOW** | Missing Rate Limiting | `GeminiService.kt` | Direct calling | Mobile clients can flood the Gemini API, hitting usage limits quickly. | Implement rate limiting on a backend gateway. |

### Severity Legend
- **CRITICAL**: Immediate risk of full system compromise or total data loss.
- **HIGH**: Direct access to sensitive data or functionality for unauthorized users.
- **MEDIUM**: Indirect vulnerabilities or missing industry-standard protections.
- **LOW**: Minor security improvements or best-practice deviations.
