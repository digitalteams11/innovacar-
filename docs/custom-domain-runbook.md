# Custom Domain Activation Runbook

This describes exactly what the Agency Branding / custom domain feature automates today,
and what a platform SUPER_ADMIN still has to do by hand before flipping a domain to `ACTIVE`.

## What's real and automated right now

- An agency can save a logo, primary/accent colors, and either an Innovacar subdomain or a
  custom domain via `POST /api/white-label`. This persists to Postgres (`white_label_settings`)
  and immediately repaints the authenticated app (`ThemeContext` overrides `--brand-primary` /
  `--brand-accent` from these values) — this is genuinely wired up, not cosmetic.
- Saving a custom domain generates a real verification token and DNS instructions
  (`GET /api/white-label` → `dnsInstructions`): a TXT record on `_innovacar-verify.<domain>`
  and a CNAME record on `<domain>` pointing at `app.<base-domain>`.
- `POST /api/white-label/domain/verify` performs a **real DNS lookup** (JDK's built-in DNS
  resolver, `DomainVerificationService`) for both records. It only reports `DNS_VERIFIED` if
  both actually resolve and match. If they don't, it reports `FAILED` with the real error.
- `GET /api/public/branding` resolves a tenant from the `Host` header (custom domain or
  `<slug>.<base-domain>` subdomain) — this is what an unauthenticated visitor's browser would
  hit if traffic for that domain reached this application. You can test it locally right now:

  ```bash
  curl -H "Host: myagency.innovacar.app" http://localhost:8082/api/public/branding
  curl -H "Host: rent.someagency.com" http://localhost:8082/api/public/branding
  ```

## What is NOT automated (and why `ACTIVE` is never claimed automatically)

Reaching `DNS_VERIFIED` proves the agency controls the domain's DNS. It does **not** mean
traffic to that domain reaches this application, or that it's served over HTTPS. That requires
infrastructure that doesn't exist yet in this environment:

1. **A public server** this app is deployed to, with a static public IP.
2. **A reverse proxy** (Nginx, Caddy, or Traefik) in front of the app that:
   - Listens on port 443 for arbitrary incoming hostnames.
   - Reads the `Host` header and forwards to this backend (which already knows how to resolve
     branding per-host via `/api/public/branding`, and per-request tenant context would need a
     parallel `Host`-based filter for authenticated traffic — not built, since there's nothing
     to route to yet).
3. **An ACME/Let's Encrypt client** (`certbot`, Caddy's built-in ACME, etc.) issuing and renewing
   a TLS certificate for each verified custom domain and subdomain.
4. **Wildcard DNS** (`*.<base-domain>`) pointed at that server, for the subdomain option to work
   for arbitrary agency slugs.

None of this exists in the current codebase or environment, so the app never marks a domain
`ACTIVE` on its own. The state machine stops at `DNS_VERIFIED`.

## Activating a domain once the infra above is deployed

1. Deploy the reverse proxy + ACME client, pointed at this app, per the DNS records the agency
   already configured (they were told the target in `dnsInstructions.cnameRecordValue`).
2. Confirm `https://<their-domain>` actually serves the app and terminates TLS correctly.
3. As SUPER_ADMIN, call:

   ```
   PUT /api/super-admin/white-label/{tenantId}/activate
   ```

   This only succeeds if the domain is currently `DNS_VERIFIED` — it's the explicit,
   human-confirmed step standing in for "SSL and routing are live for this domain."

Until step 3 happens, the UI shows `DNS_VERIFIED` with the message: "DNS verified — awaiting
platform SSL activation," never a false "your domain works."
