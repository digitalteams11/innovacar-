import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { networkInterfaces } from 'os'

// server.host is 0.0.0.0 so the dev server accepts LAN connections (e.g. testing
// on a phone or a second machine). But 0.0.0.0 isn't a real address a browser can
// open a WebSocket back to — without an explicit hmr.host, Vite's client defaults
// to `localhost`, which only resolves for someone on the same machine as the
// server. Anyone connecting via the LAN IP gets a permanently failing HMR socket;
// Fast Refresh then silently breaks and can leave a stale module graph where a
// provider and its consumer resolve to two different module instances — this is
// what caused "useAuth must be used within an AuthProvider" even though the
// provider tree itself was correctly structured. Binding hmr.host to the actual
// LAN IP keeps the WebSocket reachable from any device that can reach the HTTP
// server, so HMR (and therefore React context) stays consistent.
function firstLanIPv4(): string | undefined {
  const interfaces = networkInterfaces()
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] ?? []) {
      if (net.family === 'IPv4' && !net.internal) return net.address
    }
  }
  return undefined
}

// Injects the Google Search Console HTML-verification meta tag into the
// built index.html at build time, from VITE_GOOGLE_SITE_VERIFICATION — the
// tag must exist in the raw HTML source (not only after React mounts) for
// Search Console to accept it. Leaves the placeholder comment untouched
// (i.e. no meta tag at all) when the env var isn't set, rather than ever
// emitting a fake/placeholder token.
function googleSiteVerificationPlugin(token: string | undefined): Plugin {
  return {
    name: 'inject-google-site-verification',
    transformIndexHtml(html) {
      if (!token) return html
      return html.replace(
        '<!--GOOGLE_SITE_VERIFICATION_META-->',
        `<meta name="google-site-verification" content="${token}" />`
      )
    },
  }
}

// Only these VITE_* keys are read by application code as public, user-facing
// URLs (see src/lib/publicUrl.ts, src/lib/api.ts, src/marketing/pages.tsx).
// Keep this list in sync with those files — do not widen it back to "every
// VITE_* key": when Vercel's "Automatically expose System Environment
// Variables" setting is on, it injects VITE_VERCEL_URL, VITE_VERCEL_BRANCH_URL
// and VITE_VERCEL_PROJECT_PRODUCTION_URL into every build, and those
// legitimately contain a *.vercel.app host (that's the deployment's own
// preview/branch hostname, by design) even for a Production build. Scanning
// every VITE_* key treated those platform-injected variables as a leaked
// preview URL and failed builds that were otherwise fine.
const PUBLIC_URL_ENV_KEYS = [
  'VITE_PUBLIC_APP_URL',
  'VITE_API_URL',
  'VITE_INNOVAX_WEBSITE_URL',
  'VITE_DESKTOP_DOWNLOAD_URL',
]

// Fails the build immediately, with the exact variable name, if one of the
// application-controlled public URL env vars above contains a Vercel preview
// deployment host. This is the actual root-cause guard: source-level
// sanitizers (src/lib/publicUrl.ts, src/lib/api.ts, src/marketing/pages.tsx)
// only cover the specific fields that code reads — but Vite inlines whichever
// of these vars is set into the bundle whether or not any of our code even
// uses it, and a leftover *.vercel.app value in the Vercel dashboard (not
// something visible from this repo — env vars aren't committed to git) will
// keep leaking into a *different* chunk every time until the dashboard value
// itself is fixed. Running this here, at config-eval time, means the build
// fails with "VITE_FOO contains a vercel.app value" instead of the opaque
// "assets/pages-xxxxx.js contains forbidden URL pattern" scripts/
// verify-seo-dist.mjs reports several build steps later.
function assertNoVercelPreviewUrls(env: Record<string, string>, isProduction: boolean) {
  if (!isProduction) return
  const offenders = PUBLIC_URL_ENV_KEYS.filter((key) => {
    const value = env[key]
    return typeof value === 'string' && /vercel\.app/i.test(value)
  })
  if (offenders.length === 0) return
  const names = offenders.join(', ')
  throw new Error(
    `[vite.config] Refusing to build: the following application public URL env var(s) contain a Vercel preview ` +
    `deployment URL, which must never ship in the production bundle: ${names}. ` +
    `Fix the value in Vercel → Project Settings → Environment Variables (Production) — ` +
    `it must point at the real https://innovacar.app domain, not a preview deployment.`
  )
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  assertNoVercelPreviewUrls(env, mode === 'production')
  return {
  plugins: [react(), googleSiteVerificationPlugin(env.VITE_GOOGLE_SITE_VERIFICATION)],
  server: {
    host: '0.0.0.0',
    port: 5174,
    strictPort: true,
    hmr: {
      host: firstLanIPv4() ?? 'localhost',
    },
  },
  }
})
