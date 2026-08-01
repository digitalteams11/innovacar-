# Desktop feature inventory

As of this pass, the desktop app renders `frontend-web/src/App.tsx` directly
(see `src/main.tsx`) — every route below is defined once, in one file, and
is therefore supported in desktop automatically. This file exists to make
that explicit and to flag the small number of routes that are intentionally
web-only, plus known gaps for a future pass.

| Route | Desktop | Permission | Feature flag |
|---|---|---|---|
| `/login`, `/register`, `/forgot-password`, `/verify-reset-code`, `/reset-password`, `/verify-email` | Supported | — (public) | — |
| `/contract-sign/:token` (+ `:contractId` variant), `/client-info/:token`, `/inspection/:token` | Supported | — (public, token-gated) | — |
| `/contact` | Supported | — (public) | — |
| `/` , `/dashboard`, `/employee/dashboard` | Supported | — | — |
| `/vehicles` | Supported | VIEW_VEHICLES | VEHICLE_MANAGEMENT |
| `/reservations` | Supported | VIEW_RESERVATIONS | RESERVATION_MANAGEMENT |
| `/clients` | Supported | VIEW_CLIENTS | CLIENT_MANAGEMENT |
| `/payments` | Supported | VIEW_PAYMENTS | — |
| `/settings` | Supported | — | — |
| `/contracts`, `/contracts/:id` | Supported | VIEW_CONTRACTS | CONTRACT_MANAGEMENT |
| `/client-information-requests` | Supported | VIEW_CONTRACTS | CONTRACT_MANAGEMENT |
| `/invoices` | Supported | VIEW_INVOICES | INVOICE_GENERATION |
| `/agency` | Supported | — | — |
| `/employees` | Supported | MANAGE_EMPLOYEES | MULTI_EMPLOYEE |
| `/reports` | Supported | VIEW_REPORTS | REPORTS_BASIC |
| `/report-archive` | Supported | — | REPORT_ARCHIVE |
| `/gps-settings`, `/gps-tracking`, `/gps-alerts` | Supported | GPS_ACCESS | GPS_TRACKING |
| `/checkout` | Supported (opens same in-app flow as web) | — | — |
| `/white-label` | Supported | — | WHITE_LABEL |
| `/automation-center` | Supported | — | AUTOMATION_CENTER |
| `/maintenance` | Supported | VIEW_MAINTENANCE | VEHICLE_MANAGEMENT |
| `/role-permissions` | Supported | MANAGE_EMPLOYEES | — |
| `/operations-center` | Supported | — | — |
| `/help`, `/support`, `/tickets`, `/tickets/:id` | Supported | — | — |
| `/account-suspended` | Supported | — | — |
| `/super-admin/*` (all 27 routes: dashboard, agencies, subscriptions, gps, users, payments, support, contact-requests, help articles, notifications, analytics, settings, security, emails, marketing, contracts, reports, features, backups, data-reset, announcements, staff, roles, cancellation-requests, ai-settings) | Supported | SuperAdminRoute | — |

## Intentionally web-only (opened in system browser, not part of the app shell)

- Marketing/landing site (`frontend-web/src/marketing/*`) — public SEO site (home, features, pricing, FAQ, legal pages). Desktop is app-only; a link to `innovacar.app` should open in the system browser rather than being embedded.

## Known gaps / follow-ups (not silently dropped — tracked here)

1. **Google OAuth desktop round-trip** — the shared `GoogleAuthButton` now renders in desktop and correctly avoids hijacking the app window (see `electron/main.cjs`'s `will-navigate` handler, which opens the system browser instead). However, completing sign-in currently lands the user on the web app, not back in this app, because the backend's Spring OAuth2 success handler doesn't yet know to redirect desktop-originated logins to the registered `innovacar://oauth-callback` custom protocol (already wired up on the Electron side — `preload.cjs`'s `onOAuthCallback`, `main.cjs`'s protocol registration). Needs a backend-side change (a `state`/client-type param on the authorization request, and a conditional redirect_uri) — out of scope for this frontend-only pass.
2. **Auto-update, code signing, Windows installer signing** — not configured; needs real credentials/certificates before a production release.
3. **Native extras** (tray icon, dedicated print window, PDF save-dialog integration) — `electron/preload.cjs` only exposes `onOAuthCallback` today. Worth a follow-up pass once this shared-source foundation has been used for a while.
