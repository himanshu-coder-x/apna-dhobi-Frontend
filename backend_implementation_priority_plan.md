# Backend Implementation Priority Plan - Apna Dhobi

Based on the audit, here is the recommended roadmap for building the missing backend infrastructure.

## Phase 1 — Foundation & Auth (Week 1-2)
1.  **Server Setup**: Initialize Node.js (Express/NestJS) or Python (Django/Fastify) environment.
2.  **Database Design**: Migrate Room entities (`UserProfile`, `OrderRecord`) to a centralized relational database (PostgreSQL).
3.  **Secure Auth**: Implement real OTP verification via SMS gateway and JWT-based session management.
4.  **RBAC**: Establish Admin, Vendor, and Customer roles on the server.

## Phase 2 — Core Catalog & Vendors (Week 3-4)
1.  **Vendor API**: Create endpoints to fetch real vendor data, replacing hardcoded repo lists.
2.  **Product Management**: Implement a central product catalog with category-based filtering.
3.  **Image Storage**: Set up S3 or Cloudinary for profile and store image uploads.

## Phase 3 — Order Lifecycle (Week 5-6)
1.  **Order APIs**: Replace Room inserts with API calls to a central `orders` table.
2.  **State Machine**: Move simulation loops (`runOrderStatusSimulation`) to a robust server-side state machine.
3.  **Admin Logic**: Implement commission calculations and vendor payout scheduling.

## Phase 4 — Real-time & Payments (Week 7-8)
1.  **WebSockets**: Implement Socket.IO or Firebase for live order updates and GPS tracking.
2.  **Payment Gateway**: Integrate Razorpay/Stripe for real wallet recharges and order payments.
3.  **Notification Hub**: Move SMTP/POP logic to the backend; integrate FCM for push notifications.

## Phase 5 — Hardening & Scale (Week 9+)
1.  **Security**: Audit IDOR vulnerabilities and implement strict CORS/Rate limiting.
2.  **Secret Management**: Migrate all hardcoded keys to HashiCorp Vault or AWS Secrets Manager.
3.  **Monitoring**: Set up logging (ELK/Sentry) and performance monitoring.
