# Frontend-Backend Connection Map - Apna Dhobi

This map shows how frontend actions attempt to communicate with "backend" logic.

### 1. User Authentication (Login/Signup)
**Frontend Page**: `LoginScreen` (in `MainActivity.kt`)  
**↓ Action**: User enters phone and clicks "Send OTP"  
**↓ Connection**: `ApnaDhobiViewModel.attemptUserLogin(phone)`  
**↓ Logic**: Check `UserProfile` table in Room DB.  
**↓ Result**: **BROKEN** (Local check only. No real SMS OTP is sent or verified).

### 2. Service & Vendor Browsing
**Frontend Page**: `HomeFrame` / `ProductListing` / `VendorShop`  
**↓ Action**: User views category or vendor  
**↓ Connection**: `ApnaDhobiRepository.vendors` / `ApnaDhobiRepository.products`  
**↓ Logic**: Access hardcoded Kotlin lists.  
**↓ Result**: **BROKEN** (No API request. Data is static in code).

### 3. Placing an Order
**Frontend Page**: `PaymentScreen` (in `MainActivity.kt`)  
**↓ Action**: User clicks "Confirm Order"  
**↓ Connection**: `ApnaDhobiViewModel.processCheckout(...)`  
**↓ Logic**: Insert `OrderRecord` into Room DB.  
**↓ Result**: **BROKEN** (Local insert only. No vendor or admin is notified via API).

### 4. Order Tracking
**Frontend Page**: `OrderTrackingScreen`  
**↓ Action**: User views tracking map  
**↓ Connection**: `ApnaDhobiViewModel.runOrderStatusSimulation(...)`  
**↓ Logic**: Local `delay()` loop updating Room DB status and coordinate variables.  
**↓ Result**: **BROKEN** (Simulation logic only. No real-time GPS feed from driver).

### 5. AI Consultation
**Frontend Page**: `ChatSupportPanel` (in `UserProfileDashboard`)  
**↓ Action**: User asks a question  
**↓ Connection**: `GeminiService.generateResponse(prompt)`  
**↓ Logic**: Direct HTTPS POST to Google Gemini API endpoint.  
**↓ Result**: **CONNECTED** (Real external API integration).

### 6. Email Receipts
**Frontend Page**: `processCheckout` (Internal Trigger)  
**↓ Action**: Success checkout  
**↓ Connection**: `SmtpEmailSender.sendEmail(...)`  
**↓ Logic**: Direct Socket connection to `smtp.gmail.com:465`.  
**↓ Result**: **CONNECTED** (Real P2P email delivery).

---
**Overall Connectivity Assessment**:  
The app is **90% self-contained**. It lacks a central "brain" (backend) to synchronize data between different users (e.g., Customer, Vendor, Admin).
