# Whop Webhook — Local Development Setup

When developing locally, Whop cannot reach `localhost` to deliver webhooks.
You need a tunnel that exposes your local Spring Boot server to the public internet.

## Option A — ngrok (recommended)

### 1. Install ngrok
```bash
# macOS
brew install ngrok

# Windows (winget)
winget install ngrok

# Or download from https://ngrok.com/download
```

### 2. Authenticate (one-time)
```bash
ngrok config add-authtoken <YOUR_NGROK_AUTH_TOKEN>
```

### 3. Start the tunnel
Spring Boot runs on port 8080 by default:
```bash
ngrok http 8080
```

ngrok will print a public URL like:
```
Forwarding  https://abc123.ngrok-free.app -> http://localhost:8080
```

### 4. Register the webhook in Whop
1. Log in to your [Whop Developer Dashboard](https://whop.com/developer)
2. Go to **Webhooks** → **Add Endpoint**
3. Set the URL to:
   ```
   https://abc123.ngrok-free.app/api/webhooks/whop
   ```
4. Select the events you want to receive:
   - `membership.went_valid`
   - `membership.went_invalid`
   - `membership.deleted`
   - `membership.expired`
   - `payment.succeeded`
   - `payment.failed`
5. Copy the **Signing Secret** shown after saving.

### 5. Set environment variables
In your Spring Boot `.env` or `application-local.yml`:
```yaml
whop:
  api:
    key: apik_XXXXXXXXXXXX           # Your Whop API key — NEVER commit this
  webhook:
    secret: whsec_XXXXXXXXXXXX       # Signing secret from step 4
```

Or as OS environment variables before starting the server:
```bash
export WHOP_API_KEY=apik_XXXXXXXXXXXX
export WHOP_WEBHOOK_SECRET=whsec_XXXXXXXXXXXX
```

---

## Option B — Cloudflare Tunnel (no account required for quick tests)

```bash
# Install cloudflared
# https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/

cloudflared tunnel --url http://localhost:8080
```

Cloudflare prints a URL like `https://random-words.trycloudflare.com`.
Use that as the webhook endpoint in the Whop dashboard (same steps as above).

> Note: Cloudflare Quick Tunnels are ephemeral — the URL changes on restart.
> For persistent development use ngrok or a named Cloudflare Tunnel.

---

## Verifying the signature locally

If `WHOP_WEBHOOK_SECRET` is set, `WhopWebhookController` verifies every
incoming event with HMAC-SHA256. If the secret is blank, verification is
skipped with a warning in the logs.

To send a test event manually:
```bash
# Build the HMAC-SHA256 signature of the raw JSON body
SECRET="whsec_XXXXXXXXXXXX"
BODY='{"event":"membership.went_valid","id":"mem_test123","metadata":{"tenant_id":"1"}}'
SIG=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $2}')

curl -X POST http://localhost:8080/api/webhooks/whop \
  -H "Content-Type: application/json" \
  -H "whop-signature: sha256=$SIG" \
  -d "$BODY"
```

Expected response:
```json
{"success": true, "message": "Subscription activated for tenant 1"}
```

---

## Checkout flow end-to-end

1. Agency admin opens **Settings → Billing**
2. Clicks **Upgrade Plan** → selects a plan → clicks **Continue to Checkout**
3. Frontend calls `POST /api/billing/checkout` → backend calls Whop API (or returns static URL)
4. Frontend redirects to the Whop-hosted checkout page (`window.location.href = checkoutUrl`)
5. User completes payment on Whop
6. Whop sends `membership.went_valid` to your webhook URL
7. `WhopWebhookController` activates the tenant's subscription in the DB
8. User is redirected back to `<your-app>/settings?tab=billing&payment=success`
9. `BillingTab` detects the query param, calls `POST /api/billing/refresh-status`, and shows the activation banner

### Configure the return URL in Whop
In the Whop Developer Dashboard → **Products** → your product → **Checkout settings**,
set the **Success redirect URL** to:
```
https://your-app.com/settings?tab=billing&payment=success
```

For local development:
```
http://localhost:5173/settings?tab=billing&payment=success
```

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `401 Invalid webhook signature` | `WHOP_WEBHOOK_SECRET` doesn't match the key in Whop dashboard |
| `Event received — tenant not identified` | Checkout URL missing `metadata[tenant_id]=` param |
| `Event already processed` | Duplicate delivery — safely ignored (idempotent) |
| Subscription not activating after payment | Webhook not reaching your server — check ngrok logs |
| `WHOP_PLAN_NOT_CONFIGURED` on checkout | Plan in DB has no `whop_plan_id` or `whop_checkout_url_monthly` set |
