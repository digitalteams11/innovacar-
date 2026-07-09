# RentCar SaaS — Core Flow Manual Test Plan

Precondition: Backend on port 8082, frontend on port 5173/5174. Clean database (business tables empty, config tables seeded via Flyway).

---

## 1. Login

**Steps:**
1. Open `http://localhost:5173`
2. Enter valid admin credentials
3. Click Login

**Expected:**
- Redirect to `/` (Dashboard)
- No console errors
- No 401 spam
- `localStorage.token` is set

---

## 2. Dashboard from Empty DB

**Steps:**
1. After login, view dashboard

**Expected:**
- All stat cards show **0** (fleet, contracts, reservations, revenue)
- No hardcoded "5000 MAD", no fake fleet count
- No 404 or 500 errors in console
- Charts show empty state (no data message)
- `GET /api/dashboard` returns 200 with all counts = 0

---

## 3. Create Client

**Steps:**
1. Navigate to `/clients`
2. Click "Add Client"
3. Fill in: full name, phone, CIN
4. Save

**Expected:**
- `POST /api/clients` returns 201
- Client appears in the list
- Duplicate phone/CIN attempt returns `CLIENT_ALREADY_EXISTS` (409) with inline error

---

## 4. Create Vehicle

**Steps:**
1. Navigate to `/vehicles`
2. Click "Add Vehicle"
3. Fill in: brand/model, plate, daily price
4. Save

**Expected:**
- `POST /api/vehicles` returns 201
- Vehicle appears in the list with status AVAILABLE
- `GET /api/dashboard` now shows `fleet = 1`, `availableVehicles = 1`

---

## 5. Create Reservation

**Steps:**
1. Navigate to `/reservations`
2. Click "New Reservation"
3. Select the created client, vehicle, pick future dates
4. Save

**Expected:**
- `POST /api/reservations` returns 201
- Reservation appears in the list
- Dashboard `reservations` count increases by 1
- Vehicle selector for those dates no longer shows vehicle (blocked by reservation)

---

## 6. Create Contract — Existing Client

**Steps:**
1. Navigate to `/contracts`
2. Click "New Contract" → select existing client, vehicle, dates
3. Set `dailyPrice`, `paidAmount` ≤ total
4. Submit

**Expected:**
- `POST /api/contracts/direct-create` returns 201
- Contract appears in list
- Dashboard: `activeContracts` increases
- Vehicle status changes to RENTED or RESERVED

---

## 7. Create Contract — Inline New Client

**Steps:**
1. Click "New Contract" → switch to "New Client" tab in modal
2. Fill client inline fields + select vehicle + dates
3. Submit

**Expected:**
- Backend creates client, then contract in one request
- No duplicate `CLIENT_ALREADY_EXISTS` if CIN/phone is new
- Contract created, new client appears in `/clients`

---

## 8. Error Cases — Contract Creation

| Scenario | Expected Error Code | Expected HTTP |
|---|---|---|
| `paidAmount` > total | `PAID_AMOUNT_EXCEEDS_TOTAL` | 400 |
| Duplicate contract number | `CONTRACT_NUMBER_EXISTS` | 409 |
| Vehicle booked for those dates | `VEHICLE_NOT_AVAILABLE` | 409 |
| Duplicate client (CIN/phone) | `CLIENT_ALREADY_EXISTS` | 409 |

**Expected frontend behavior:**
- Modal stays open
- Inline error banner appears (no toast-only dismiss)
- No fake success

---

## 9. Dashboard After Contract

**Steps:**
1. Refresh dashboard after creating a contract

**Expected:**
- `activeContracts` ≥ 1
- `monthlyRevenue` reflects `paidAmount` from the contract's payment
- `fleet`, `availableVehicles`, `reservedVehicles`, `rentedVehicles` are correct counts
- `GET /api/dashboard` and `GET /api/dashboard/summary` both return 200 with same data

---

## 10. Delete (Soft Delete) Contract

**Steps:**
1. In contracts list, click delete on the created contract
2. Confirm

**Expected:**
- `DELETE /api/contracts/{id}` returns 200
- Contract disappears from main list
- `GET /api/contracts/trash` returns the contract

---

## 11. Restore Contract — NORMAL Mode

**Steps:**
1. Navigate to trash
2. Click "Restore" → NORMAL mode

**Expected:**
- `POST /api/contracts/{id}/restore` with `{ "mode": "NORMAL" }` returns 200
- If vehicle is available: contract restored to original status
- If vehicle booked: returns `RESTORE_VEHICLE_CONFLICT` (409) with conflict details

---

## 12. Restore Contract — DRAFT_ONLY Mode

**Steps:**
1. In trash, click "Restore as Draft"

**Expected:**
- `POST /api/contracts/{id}/restore` with `{ "mode": "DRAFT_ONLY" }` returns 200
- Contract restored as DRAFT regardless of vehicle availability
- No `RESTORE_VEHICLE_CONFLICT` error

---

## 13. Purge Contract

**Steps:**
1. In trash, click "Delete Permanently" on a trashed contract

**Expected:**
- `DELETE /api/contracts/{id}/purge` returns 200
- Contract completely gone from trash list
- No 409 error (FK children deleted before contract row)
- Vehicle still exists and is not deleted

---

## 14. Vehicle Availability Consistency

**Steps:**
1. Book vehicle A for dates 2026-08-01 → 2026-08-05
2. Check `GET /api/availability/vehicles?startDate=2026-08-01&endDate=2026-08-05`
3. Check `GET /api/vehicles/available?startDate=2026-08-01&endDate=2026-08-05`
4. Try creating a contract for vehicle A on overlapping dates

**Expected:**
- Both availability endpoints exclude vehicle A
- Direct-create returns `VEHICLE_NOT_AVAILABLE` (409) with conflict details
- After deleting the blocking contract/reservation, vehicle A reappears in both endpoints

---

## 15. No Console Errors Checklist

After all steps above, open browser DevTools → Console and Network tabs:

- [ ] No 404 on `/api/reservations` or any core module
- [ ] No 500 on dashboard load
- [ ] No `DATA_CONFLICT` errors for known cases (only for genuinely unknown constraints)
- [ ] No `/api/api/...` double-prefix URLs
- [ ] No 401 spam when navigating between pages
- [ ] No fake success (check network tab: POST must return 201, not 200 with fake data)
- [ ] Refresh page keeps session (token persists in localStorage)
- [ ] `GET /api/health` returns 200 (public, no auth needed)
- [ ] `GET /api/me` returns current user (requires auth)

---

## Backend Quick Checks (curl)

```bash
# Health (public)
curl http://localhost:8082/api/health

# Dashboard (requires Bearer token)
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/dashboard
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/dashboard/summary

# Available vehicles
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8082/api/vehicles/available?startDate=2026-07-01&endDate=2026-07-05"
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8082/api/availability/vehicles?startDate=2026-07-01&endDate=2026-07-05"

# Reservations (should return 200 + empty array from clean DB)
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/reservations

# Reservations ping
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/reservations/ping
```
