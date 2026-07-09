# Whop Billing Setup Guide

## Backend Environment Variables

Add these to your `.env` or Spring Boot application properties:

```
WHOP_API_KEY=your_whop_api_key_here
WHOP_COMPANY_ID=your_whop_company_id_here
WHOP_WEBHOOK_SECRET=your_whop_webhook_secret_here
FRONTEND_URL=http://localhost:5174
```

**Never expose these to the frontend. They are server-side only.**

---

## Checkout Modes

### Mode 1 — Stored Link (Simplest)

In Super Admin → Plans, set **Whop Checkout URL (Monthly)** and/or **Whop Checkout URL (Yearly)** for each paid plan.

When an agency clicks "Select", the backend appends `?metadata[tenant_id]=<agencyId>` and redirects.

No API key needed for this mode.

### Mode 2 — Dynamic API (Recommended for metadata)

Set `WHOP_API_KEY` in the backend and set **Whop Plan ID (Monthly)** on each plan.

The backend calls `POST /api/v2/memberships` on Whop's API, creating a checkout session with tenant metadata embedded. This enables reliable webhook correlation.

---

## Whop Webhook Setup

1. In your Whop dashboard, go to **Developer → Webhooks**.
2. Add a new webhook endpoint:
   - **URL**: `https://your-domain.com/api/webhooks/whop`
   - **Events**: `membership.went_valid`, `membership.went_invalid`, `membership.deleted`, `payment.succeeded`, `payment.failed`
3. Copy the **Webhook Secret** into `WHOP_WEBHOOK_SECRET`.

### Local Development

Whop cannot reach `localhost` or `192.168.x.x`. Use a tunnel:

```bash
# ngrok
ngrok http 8082
# → https://abc123.ngrok.io/api/webhooks/whop

# cloudflared
cloudflared tunnel --url http://localhost:8082
```

Use the tunnel URL as your Whop webhook endpoint during development.

---

## Frontend Success Return URL

After checkout, Whop redirects to:

```
http://localhost:5174/#/settings?tab=billing&payment=success
```

Production:

```
https://your-app.com/#/settings?tab=billing&payment=success
```

Configure this as the **success URL** in your Whop product settings.

---

## Subscription Activation

**Activation ONLY happens via webhook** — not from the frontend success URL alone.

Flow:
1. Agency completes checkout on Whop.
2. Whop sends `membership.went_valid` webhook to your backend.
3. Backend verifies HMAC-SHA256 signature using `WHOP_WEBHOOK_SECRET`.
4. Backend reads `metadata.tenant_id` to identify the agency.
5. Backend activates the subscription in `agency_subscriptions`.
6. Frontend polls `/api/billing/refresh-status` after redirect to show updated state.

---

## Plan Limit Enforcement

Backend enforces plan limits via `PlanLimitService` before:
- Creating a vehicle (checks `maxVehicles`)
- Creating an employee (checks `maxEmployees`)
- Enabling GPS (checks `maxGpsDevices`)

Error response when limit reached:
```json
{
  "errorCode": "PLAN_LIMIT_REACHED",
  "message": "Your Basic plan allows 5 vehicles. Upgrade to add more.",
  "data": { "feature": "vehicles", "used": 5, "limit": 5 }
}
```

---

## Error Codes

| Code | Meaning |
|---|---|
| `CHECKOUT_NOT_CONFIGURED` | Plan has no Whop checkout URL or plan ID |
| `WHOP_API_KEY_MISSING` | `WHOP_API_KEY` env var not set |
| `WHOP_CHECKOUT_FAILED` | Whop API returned an error |
| `WHOP_INVALID_SIGNATURE` | Webhook signature verification failed |
| `PLAN_NOT_FOUND` | No active plan with that code |
| `PLAN_LIMIT_REACHED` | Agency has reached a plan limit |
| `PROMO_NOT_FOUND` | Promo code does not exist or inactive |
| `PROMO_EXPIRED` | Promo code is expired |
| `PROMO_LIMIT_REACHED` | Promo code max uses reached |
