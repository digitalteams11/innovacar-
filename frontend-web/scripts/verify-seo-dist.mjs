// Fails the build when the production dist/ output has an obvious SEO or
// data-leak defect. Run via `npm run verify:seo` (wired after `vite build`
// so dist/ exists). See docs/seo-route-inventory.md for the private-path list.
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.join(ROOT, '..', 'dist');
const PROJECT_ROOT = path.join(ROOT, '..');

// Bare-word check — used only for the three SEO-facing static files
// (index.html, robots.txt, sitemap.xml), where none of these substrings
// should ever legitimately appear at all.
const FORBIDDEN_STRINGS = ['localhost', '127.0.0.1', '192.168.', '.vercel.app'];
// Scheme-prefixed, port-qualified check — used for the full JS/CSS bundle
// scan. The app intentionally ships runtime LAN-dev fallback logic
// (src/lib/api.ts constructs `http://${browserHost}:8082/api`) and UI
// placeholder/demo text (e.g. an example IP in a security-sessions demo)
// that legitimately contain bare "localhost"/"192.168." without being a
// leaked hardcoded dev API URL. React Router's own internals also use the
// literal string "http://localhost" (no port) purely as a dummy base for
// `new URL(path, base)` parsing — never sent anywhere. So this only flags
// the actual signature a leaked dev config from THIS app would have: the
// backend dev port (:8082) attached to a dev/LAN host, or a Vercel preview
// domain.
const FORBIDDEN_URL_PATTERNS = [
  'localhost:8082',
  '127.0.0.1:8082',
  'http://192.168.',
  '.vercel.app',
];
// Hash-based private routes that must never appear in a static asset as a
// literal indexable link target (they're fine inside the compiled router
// logic itself — this only scans the top-level HTML/robots/sitemap files).
const FORBIDDEN_PRIVATE_HASH_LINKS = ['#/dashboard', '#/super-admin'];

let failures = [];

function fail(message) {
  failures.push(message);
}

function readIfExists(relativePath) {
  const full = path.join(DIST, relativePath);
  if (!existsSync(full)) return null;
  return readFileSync(full, 'utf8');
}

// ── dist/index.html ─────────────────────────────────────────────────────
const indexHtml = readIfExists('index.html');
if (!indexHtml) {
  fail('dist/index.html is missing — run `vite build` first.');
} else {
  if (!/<meta\s+name="robots"/.test(indexHtml)) {
    fail('dist/index.html has no <meta name="robots"> — the raw HTML shell must ship a safe default (noindex) before React mounts.');
  }
  const robotsMatches = indexHtml.match(/<meta\s+name="robots"/g) || [];
  if (robotsMatches.length > 1) {
    fail(`dist/index.html has ${robotsMatches.length} <meta name="robots"> tags — expected exactly 1 (duplicate robots meta is ambiguous).`);
  }
  const canonicalMatches = indexHtml.match(/<link[^>]+rel="canonical"/g) || [];
  if (canonicalMatches.length > 1) {
    fail(`dist/index.html has ${canonicalMatches.length} canonical tags — expected at most 1.`);
  }
  for (const needle of FORBIDDEN_STRINGS) {
    if (indexHtml.includes(needle)) fail(`dist/index.html contains forbidden string "${needle}".`);
  }
}

// ── prerendered marketing pages (see scripts/prerender-marketing.mjs) ──────
const MARKETING_PAGES = [
  { file: 'index.html', urlPath: '/', expectIndexable: true },
  { file: path.join('fonctionnalites', 'index.html'), urlPath: '/fonctionnalites', expectIndexable: true },
  { file: path.join('tarifs', 'index.html'), urlPath: '/tarifs', expectIndexable: true },
  { file: path.join('confidentialite', 'index.html'), urlPath: '/confidentialite', expectIndexable: true },
  { file: path.join('conditions', 'index.html'), urlPath: '/conditions', expectIndexable: true },
  { file: path.join('cookies', 'index.html'), urlPath: '/cookies', expectIndexable: true },
  { file: path.join('securite', 'index.html'), urlPath: '/securite', expectIndexable: true },
];
for (const page of MARKETING_PAGES) {
  const html = readIfExists(page.file);
  if (!html) {
    fail(`dist/${page.file} is missing — did scripts/prerender-marketing.mjs run?`);
    continue;
  }
  if (page.expectIndexable && !/<meta\s+name="robots"\s+content="index,follow/.test(html)) {
    fail(`dist/${page.file} should be indexable ("index,follow...") but its robots meta isn't.`);
  }
  const robotsMatches = html.match(/<meta\s+name="robots"/g) || [];
  if (robotsMatches.length !== 1) {
    fail(`dist/${page.file} has ${robotsMatches.length} <meta name="robots"> tags — expected exactly 1.`);
  }
  const canonicalMatches = html.match(/<link[^>]+rel="canonical"/g) || [];
  if (canonicalMatches.length !== 1) {
    fail(`dist/${page.file} has ${canonicalMatches.length} canonical tags — expected exactly 1.`);
  } else if (!html.includes(`href="https://innovacar.app${page.urlPath}"`)) {
    fail(`dist/${page.file} canonical does not point to https://innovacar.app${page.urlPath}.`);
  }
  const rootMatch = html.match(/<div id="root">([\s\S]*)<\/body>/);
  if (!rootMatch || rootMatch[1].trim().length < 200) {
    fail(`dist/${page.file} has little or no static content inside #root — prerender may have failed silently.`);
  }
  for (const needle of FORBIDDEN_STRINGS) {
    if (html.includes(needle)) fail(`dist/${page.file} contains forbidden string "${needle}".`);
  }
}

// ── dist/robots.txt ─────────────────────────────────────────────────────
const robotsTxt = readIfExists('robots.txt');
if (!robotsTxt) {
  fail('dist/robots.txt is missing.');
} else if (!/Sitemap:\s*https:\/\/innovacar\.app\/sitemap\.xml/.test(robotsTxt)) {
  fail('dist/robots.txt is missing the "Sitemap: https://innovacar.app/sitemap.xml" declaration.');
}

// ── dist/sitemap.xml ────────────────────────────────────────────────────
const sitemapXml = readIfExists('sitemap.xml');
if (!sitemapXml) {
  fail('dist/sitemap.xml is missing.');
} else {
  if (!sitemapXml.includes('<urlset')) fail('dist/sitemap.xml is not a valid <urlset> document.');
  if (sitemapXml.includes('#/')) fail('dist/sitemap.xml contains a hash-fragment URL ("#/") — sitemap URLs must be hash-free.');
  for (const needle of FORBIDDEN_STRINGS) {
    if (sitemapXml.includes(needle)) fail(`dist/sitemap.xml contains forbidden string "${needle}".`);
  }
  const locs = [...sitemapXml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m) => m[1]);
  const seenLocs = new Set();
  // Flags anything that looks like a secret/opaque token or internal
  // identifier riding along in a sitemap URL — a UUID, a long hex string,
  // or a query string (auth tokens are always passed as query params or
  // path segments in this app, never as clean path segments).
  const TOKEN_LIKE_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{24,}|\?/i;
  for (const loc of locs) {
    if (!loc.startsWith('https://innovacar.app/')) {
      fail(`dist/sitemap.xml <loc> "${loc}" does not use the canonical https://innovacar.app origin.`);
    }
    if (TOKEN_LIKE_PATTERN.test(loc)) {
      fail(`dist/sitemap.xml <loc> "${loc}" looks like it contains a token/query string — sitemap URLs must be clean, tokenless, canonical paths.`);
    }
    if (seenLocs.has(loc)) {
      fail(`dist/sitemap.xml contains a duplicate <loc> "${loc}".`);
    }
    seenLocs.add(loc);
  }
}

// ── vercel.json routing config ──────────────────────────────────────────
// The actual production bug this suite guards against ("/sitemap" served
// the noindex SPA shell instead of redirecting to the real sitemap.xml)
// can't be reproduced from a static dist/ scan alone — it's a routing-table
// behavior. Assert the config itself is present and shaped correctly
// instead; production-verification (see docs/seo-route-inventory.md) covers
// the live HTTP behavior after deploy.
const vercelConfigRaw = readFileSync(path.join(PROJECT_ROOT, 'vercel.json'), 'utf8');
const vercelConfig = JSON.parse(vercelConfigRaw);

const redirects = vercelConfig.redirects || [];
const hasSitemapRedirect = redirects.some((r) =>
  r.source === '/sitemap' && r.destination === '/sitemap.xml' && r.permanent === true);
if (!hasSitemapRedirect) {
  fail('vercel.json is missing a permanent redirect from "/sitemap" to "/sitemap.xml" — this is the exact production bug (Search Console: "Excluded by noindex tag" on /sitemap).');
}

const hasWwwRedirect = redirects.some((r) =>
  r.destination === 'https://innovacar.app/$1'
  && Array.isArray(r.has)
  && r.has.some((h) => h.type === 'host' && h.value === 'www.innovacar.app'));
if (!hasWwwRedirect) {
  fail('vercel.json is missing a permanent redirect from www.innovacar.app to the canonical apex domain.');
}

const headerRules = vercelConfig.headers || [];
const hasPreviewNoindexRule = headerRules.some((rule) =>
  Array.isArray(rule.missing)
  && rule.missing.some((m) => m.type === 'host' && m.value === 'innovacar.app')
  && rule.headers.some((h) => h.key === 'X-Robots-Tag' && /noindex/i.test(h.value)));
if (!hasPreviewNoindexRule) {
  fail('vercel.json is missing a host-scoped rule that forces X-Robots-Tag: noindex on any non-production host (preview deployments must never be indexable, even on paths that are indexable in production).');
}

// The path-based noindex rule (as opposed to the host-based preview rule
// just checked above) must exclude sitemap.xml/robots.txt via its own
// negative-lookahead source pattern — verify by actually running that
// regex against both paths, rather than eyeballing the pattern.
const pathNoindexRule = headerRules.find((rule) =>
  !rule.missing && rule.headers.some((h) => h.key === 'X-Robots-Tag' && /noindex/i.test(h.value)));
if (!pathNoindexRule) {
  fail('vercel.json is missing the path-based X-Robots-Tag noindex rule that protects private/unlisted paths by default.');
} else {
  const sourceRegex = new RegExp(`^${pathNoindexRule.source}$`);
  if (sourceRegex.test('/sitemap.xml')) {
    fail('vercel.json\'s path-based X-Robots-Tag noindex rule matches "/sitemap.xml" — the sitemap must never get a noindex header.');
  }
  if (sourceRegex.test('/robots.txt')) {
    fail('vercel.json\'s path-based X-Robots-Tag noindex rule matches "/robots.txt".');
  }
  if (!sourceRegex.test('/some-private-app-path')) {
    fail('vercel.json\'s path-based X-Robots-Tag noindex rule unexpectedly does NOT match an arbitrary private path — check the negative-lookahead pattern.');
  }
}

// ── whole-dist scan for leaked private links / dev URLs in built assets ──
// (JS bundles legitimately contain route strings like "/dashboard" as router
// config, so we only flag the specific hash-link forms a crawler could
// follow, plus raw localhost/LAN/preview URLs anywhere in the output.)
function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.(js|html|css)$/.test(entry)) out.push(full);
  }
  return out;
}
if (existsSync(DIST)) {
  const files = walk(DIST);
  for (const file of files) {
    const content = readFileSync(file, 'utf8');
    for (const needle of FORBIDDEN_URL_PATTERNS) {
      if (content.includes(needle)) {
        fail(`${path.relative(DIST, file)} contains forbidden URL pattern "${needle}".`);
      }
    }
    if (/\.html$/.test(file)) {
      for (const needle of FORBIDDEN_PRIVATE_HASH_LINKS) {
        if (content.includes(`href="${needle}` ) || content.includes(`href='${needle}`)) {
          fail(`${path.relative(DIST, file)} contains a crawlable link to private route "${needle}".`);
        }
      }
    }
  }
}

if (failures.length > 0) {
  console.error(`\n[verify-seo-dist] ${failures.length} SEO check(s) failed:\n`);
  for (const f of failures) console.error(`  ✗ ${f}`);
  console.error('');
  process.exit(1);
}
console.log('[verify-seo-dist] all SEO checks passed.');
