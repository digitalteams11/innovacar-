# Google Search Console setup

## 1. Add the property

1. Open [Google Search Console](https://search.google.com/search-console).
2. **Add property** → choose **Domain property** (not "URL prefix") → enter `innovacar.app`.
   - A Domain property covers `innovacar.app`, `www.innovacar.app`, and both `http`/`https` under one property — preferred over URL-prefix so www/non-www/http/https variants don't need separate properties.

## 2. Verify via Namecheap DNS TXT record

1. In Search Console, copy the TXT verification value it shows you (starts with `google-site-verification=`).
2. Log in to [Namecheap](https://www.namecheap.com).
3. **Domain List** → find `innovacar.app` → **Manage**.
4. **Advanced DNS** tab.
5. **Add New Record**.
6. Type: **TXT Record**.
7. Host: `@`.
8. Value: the value Search Console gave you.
9. TTL: **Automatic**.
10. **Save**.
11. Back in Search Console, click **Verify**. DNS propagation can take a few minutes to a few hours.
12. **Do not remove the TXT record after verification** — Search Console periodically re-checks it; removing it can revoke verification.

## 3. Alternative: HTML meta tag verification

Only needed if the DNS method above isn't available to you. This repo already supports it:

1. In Search Console, choose **URL prefix** instead of Domain property, enter `https://innovacar.app`, and pick the **HTML tag** verification method.
2. Copy the `content="..."` value from the tag Search Console shows you (not the whole tag — just the token).
3. Set it as an environment variable in Vercel → Project Settings → Environment Variables:
   ```
   VITE_GOOGLE_SITE_VERIFICATION=<token>
   ```
4. Redeploy. `frontend-web/vite.config.ts` injects `<meta name="google-site-verification" content="...">` into the built `index.html` at build time (see the `googleSiteVerificationPlugin`), so the tag exists in the raw production HTML before React ever mounts — required for Search Console to accept it.
5. Click **Verify** in Search Console.

Never hardcode a token directly in `index.html` — always go through `VITE_GOOGLE_SITE_VERIFICATION`.

## 4. Submit the sitemap

**Sitemaps** (left nav) → enter `sitemap.xml` → **Submit**. Full URL: `https://innovacar.app/sitemap.xml`.

Note: as of this writing the sitemap is intentionally empty (see `docs/seo-route-inventory.md` — no clean, hash-free public page exists yet to list). Submit it anyway so Search Console starts tracking it; entries will appear automatically once real public pages are added to `frontend-web/src/seo/public-routes.json` and the site is rebuilt.

## 5. URL Inspection

Once real public pages exist, use **URL Inspection** (top search bar in Search Console) for each one, e.g.:
- `https://innovacar.app/`
- `https://innovacar.app/contact` (once migrated off the `#/contact` hash URL — see route inventory)

Only use **Request Indexing** after confirming the page returns HTTP 200 and the correct title/description/canonical in the inspection tool's rendered HTML tab.

## 6. What to monitor regularly

- **Page indexing** — catches pages Google can't or won't index, and why.
- **Sitemaps** — submission status and discovered-vs-indexed counts.
- **Core Web Vitals** — field data from real users.
- **HTTPS** — certificate/protocol issues.
- **Manual actions** — spam/policy penalties, should always read "no issues detected".
- **Security issues** — malware/hacked-content warnings.
- **Search performance** — impressions, clicks, average position, once there's indexed content to measure.

## 7. Do not

- Do not click **Request Indexing** repeatedly for the same URL — it has a low daily quota and doesn't speed up crawling.
- Do not remove the DNS TXT verification record.
- Do not treat Search Console verification as tied to Google Analytics — they're independent; `VITE_GOOGLE_SITE_VERIFICATION` has no dependency on `VITE_GA_MEASUREMENT_ID`.
