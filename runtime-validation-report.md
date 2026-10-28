# Runtime Validation Report

**Generated:** 2026-10-29T00:40:00+05:30  
**Target:** `D:\laboratory-retry-entitlement-team9-starter\lab-retry-entitlement`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Environment | PASS | 0 | Java 21.0.12, Maven 3.9.16, Node 24.22.1, npm 11.19.1, Docker daemon available |
| Backend build/tests | PASS | 0 | `mvn clean package`; all reactor tests passed |
| Frontend build | PASS | 0 | `npm run build`; TypeScript and Vite production build passed |
| Startup | PASS | 0 | Discovery, auth, attempt, feedback, entitlement, and gateway started successfully |
| API integration | PASS | 0 | Authenticated multi-service workflow completed through gateway |
| Browser E2E | UNVERIFIED | 1 | Browser tool expected Chromium headless-shell 1200, while installed browser cache was incompatible; exact error recorded below |

**Overall:** PASS for build, startup, and API integration; browser E2E remains unverified.

## Issues Found and Fixed

| # | Severity | Issue | Resolution |
|---:|---|---|---|
| 1 | HIGH | Gateway returned 500 while routing through Eureka because services advertised unresolvable `ADITYA.mshome.net` hostnames. | Configured all Eureka clients with `prefer-ip-address: true`, `ip-address: 127.0.0.1`, and stable instance IDs. Gateway auth/status and protected routes now work. |
| 2 | MEDIUM | Frontend attempted learner attempt creation without JSON content type/body and attempted instructor-only feedback issuance as a learner. | Frontend now sends JSON, loads feedback for learners, and only displays feedback issuance for instructor sessions. |
| 3 | LOW | Starting duplicate service processes produced port-in-use failures during validation. | Stopped the project JVMs and restarted one instance per configured port. This was an operational issue, not an application defect. |

## API Evidence

The following workflow was executed through `http://localhost:8080`:

1. Learner login returned a signed JWT.
2. Instructor issued feedback for a learner attempt.
3. Learner acknowledged feedback.
4. Learner seeded two tokens.
5. Learner reserved a retry.
6. Cancellation returned `tokens=2`.
7. Learner reserved again and started the retry.
8. Start returned `tokens=1`.

Additional checks:

- Missing JWT on protected entitlement route: HTTP 401.
- Gateway `/auth/status`: HTTP 200.
- Authorized entitlement read: HTTP 200.
- Eureka registry showed all six services UP after restart.

## Browser Validation

Node.js and Chromium installation were attempted independently. Chromium download
completed, but the VS Code browser validation tool requested a different cached
headless-shell build:

```text
Executable doesn't exist at
C:\Users\adity\AppData\Local\ms-playwright\chromium_headless_shell-1200\...
```

The frontend dev server itself was verified at `http://127.0.0.1:5173` with HTTP
200, and the production build passed. Browser rendering and click-flow evidence
remain unverified until the validation tool's pinned browser cache is available.

## Test Evidence

- Backend Surefire reports: `*/target/surefire-reports/*.xml`
- Frontend build output: `frontend/dist/`
- Backend command: `mvn clean package`
- Frontend command: `npm run build`
