# Backend Missing Requirements - Apna Dhobi

The following backend components are essential for making the application production-ready.

| Requirement Name | Why it is Required | Existing Code | Missing Components | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **Centralized Database** | Sync data between Customers, Vendors, and Admin. | `AppDatabase.kt` (Local) | PostgreSQL/MongoDB/Firestore setup. | **P0** |
| **Authentication API** | Verify users and issue secure sessions. | `attemptUserLogin` (Mock) | SMS Gateway integration (Twilio/Firebase Auth), JWT generation. | **P0** |
| **Vendor Panel Backend** | Allow real vendors to manage orders on their own devices. | `VendorPremiumDashboard` (UI) | Vendor CRUD APIs, Payout settlement logic. | **P0** |
| **Order Processing Logic** | Handle order state transitions securely on the server. | `runOrderStatusSimulation` (Mock) | State machine, Push notification triggers. | **P1** |
| **Admin Control Plane** | Platform-wide management of commissions and services. | `AdminPremiumDashboard` (UI) | System-wide statistics APIs, Role-based Access Control (RBAC). | **P1** |
| **Real-time Tracking** | Live GPS feed for delivery agents. | `GoogleMapsSubPanel` (Mock) | WebSockets (Socket.IO) or Pub/Sub messaging. | **P2** |
| **Asset Storage** | Store vendor banners and customer profile photos. | `userProfilePhoto` (Mock) | Cloudinary or AWS S3 integration. | **P2** |
| **Secret Management** | Protect API keys and email credentials. | `smtpPass` (Hardcoded) | Vault or Environment variable configuration. | **P0** |

### Priority Legend
- **P0**: Critical blocker. Application cannot function in a real-world multi-user environment.
- **P1**: Required before production launch to ensure security and manageability.
- **P2**: Important improvement for better user experience or operational efficiency.
- **P3**: Optional enhancement for future scaling.
