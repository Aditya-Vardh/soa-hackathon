# Review Checklist — Section 9, Team 9

- [x] `mvn clean package` succeeds and packages executable fat JARs for all 6 modules.
- [x] All six applications compile and run on their designated ports (`8080`–`8084`, `8761`).
- [x] Eureka discovery registration and Gateway routing configured (`lb://` routes).
- [x] Gateway routes verified for CORS, root and wildcard path matching (`/attempts`, `/feedback`, `/entitlements`, `/auth`).
- [x] Reservation cancellation leaves token count unchanged (Verified by automated test).
- [x] Starting a reserved retry consumes exactly one token (Verified by automated test).
- [x] Feedback acknowledgement is strictly enforced before retry start (Verified by automated test).
- [x] Signed JWTs and roles (`ROLE_LEARNER`, `ROLE_INSTRUCTOR`) are validated at Gateway and domain services.
- [x] Record-level authorization prevents learners from accessing other learners' data (Verified by automated test).
- [x] Event producer and consumer connected to `soa.s09.t09.events` with event metadata (`eventId`, `aggregateId`, `schemaVersion`, `occurredAt`, `correlationId`).
- [x] Duplicate event IDs produce one business effect (Idempotent deduplication verified by automated test).
- [x] Separate JPA persistence in all domain services with durable entities.
- [x] Supplementary teacher-approval extension for additional retries implemented without auto-starting retries (Verified by automated test).
- [x] Frontend production build succeeds (`npm run build` in 8.12s with TypeScript + Vite).
- [x] Premium visual identity with heading font, Inter font, responsive layout, Framer Motion, and background video.
- [x] Real Clerk Authentication integrated with `@clerk/clerk-react` and local signed JWT fallback.
