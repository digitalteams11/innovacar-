// Prerenders the public marketing pages (src/marketing/pages.tsx) to static
// HTML at build time, using dist/index.html (already built by `vite build`,
// with correct hashed asset links) as the template. This is the "Acceptable"
// prerender approach for a pure client-side SPA — see docs/seo-route-inventory.md.
//
// Run via `npm run prerender:marketing`, wired after `vite build` and before
// `verify:seo` so dist/ has the final marketing HTML before the SEO checks run.
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import { transformWithOxc } from 'vite';
import { renderToStaticMarkup } from 'react-dom/server';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.join(ROOT, '..', 'dist');
const PAGES_SRC = path.join(ROOT, '..', 'src', 'marketing', 'pages.tsx');
const TEMPLATE_PATH = path.join(DIST, 'index.html');
const CANONICAL_ORIGIN = 'https://innovacar.app';

// ── Root cause of the "unstyled text/nav flash on refresh" bug ──────────────
// src/marketing/MarketingApp.tsx imports its own marketing.css, but
// MarketingApp itself is dynamically import()'d from main.tsx (intentional
// code-splitting, so the authenticated app bundle never ships marketing-only
// CSS). Vite therefore emits that CSS as its own hashed chunk
// (dist/assets/MarketingApp-*.css), separate from the critical-path
// index-*.css this template's <head> already references. The prerendered
// body markup below (im-page/im-header/im-brand/...) is real HTML painted
// immediately on load, but its actual layout rules only exist in that
// separate chunk — which Vite doesn't request until index-*.js has
// downloaded, parsed, and executed the dynamic import(). That gap between
// "raw prerendered markup paints" and "MarketingApp-*.css chunk link is
// injected at runtime" is the flash. Fix: reference that same hashed file
// directly in <head> here too, so it loads in parallel with index-*.css
// instead of waiting on JS execution — no change to the code-splitting
// itself, no duplicate CSS shipped to the authenticated app.
function findMarketingCssHref() {
  const assetsDir = path.join(DIST, 'assets');
  const match = readdirSync(assetsDir).find((f) => /^MarketingApp-.*\.css$/.test(f));
  if (!match) {
    throw new Error(
      '[prerender-marketing] could not find dist/assets/MarketingApp-*.css — ' +
      'has MarketingApp.tsx stopped importing marketing.css, or did the build output change shape?'
    );
  }
  return `/assets/${match}`;
}

// ── Compile src/marketing/pages.tsx (TS+JSX) to plain ESM so this plain
// Node script can import it directly, without adding a bundler/loader
// dependency just for this one file. Written under node_modules/.tmp so
// bare imports (react, react-dom/server) resolve via normal Node lookup.
const source = readFileSync(PAGES_SRC, 'utf8');
const { code, errors } = await transformWithOxc(source, PAGES_SRC, {});
if (errors.length > 0) {
  throw new Error(`[prerender-marketing] failed to compile pages.tsx:\n${errors.map((e) => e.message ?? String(e)).join('\n')}`);
}
const tmpDir = path.join(ROOT, '..', 'node_modules', '.tmp');
mkdirSync(tmpDir, { recursive: true });
const tmpFile = path.join(tmpDir, 'marketing-pages.mjs');
writeFileSync(tmpFile, code, 'utf8');
const { MARKETING_PAGES } = await import(`${pathToFileURL(tmpFile).href}?t=${Date.now()}`);

const template = readFileSync(TEMPLATE_PATH, 'utf8');
const marketingCssHref = findMarketingCssHref();
if (!/<link rel="stylesheet"[^>]*>/.test(template)) {
  throw new Error('[prerender-marketing] dist/index.html has no <link rel="stylesheet"> to anchor the marketing CSS link after — template shape changed.');
}

// Static (non-React) neutral placeholder shown instead of the marketing body
// when route-bootstrap.js detects a "#/..." hash-route refresh (see the CSS
// in index.html's <head>). Kept intentionally tiny and framework-free — it
// only has to survive the few hundred ms before main.tsx mounts the real
// app, which immediately replaces all of #root's children regardless of
// which of these two static branches was visible. Uses the same CSS custom
// properties the real app's theme system defines (already resolved before
// paint by theme-bootstrap.js) so it never itself flashes the wrong theme.
const APP_BOOT_SHELL_HTML = `<div id="app-boot-shell" style="display:none;position:fixed;inset:0;z-index:9999;align-items:center;justify-content:center;background:var(--bg-page,#f4f8fb);min-height:100dvh">
  <div style="text-align:center">
    <div style="margin:0 auto;width:64px;height:64px;border-radius:16px;background:#ffffff;display:flex;align-items:center;justify-content:center;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,.15)">
      <img src="/brand/innovacar-logo.png" alt="" style="width:100%;height:100%;object-fit:contain;padding:4px" />
    </div>
    <p style="margin:14px 0 0;font-family:Inter,system-ui,sans-serif;font-weight:700;font-size:14px;color:var(--text-primary,#0f172a)">InnovaCar</p>
  </div>
</div>`;

// The Organization JSON-LD already in the shell (see index.html) is generic
// site-wide info — keep it. Only the homepage additionally gets a
// SoftwareApplication block describing the real product, no invented
// ratings/review counts/user numbers.
const SOFTWARE_APPLICATION_JSONLD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'Innovacar',
  applicationCategory: 'BusinessApplication',
  operatingSystem: 'Web',
  description: 'Logiciel de gestion pour agences de location de voitures : flotte, contrats, paiements et suivi GPS.',
  url: CANONICAL_ORIGIN,
};

let written = 0;
for (const [routePath, { meta, Component }] of Object.entries(MARKETING_PAGES)) {
  const canonical = `${CANONICAL_ORIGIN}${routePath}`;
  const bodyHtml = renderToStaticMarkup(Component());

  let html = template;

  // Load the marketing layout's own CSS chunk in parallel with the critical
  // path bundle instead of waiting for main.tsx's runtime dynamic import to
  // request it — this is the actual fix for the unstyled-flash bug (see
  // findMarketingCssHref() above for the full root-cause explanation).
  html = html.replace(
    /(<link rel="stylesheet"[^>]*>)/,
    `$1\n    <link rel="stylesheet" crossorigin href="${marketingCssHref}">`
  );

  // <title>
  html = html.replace(/<title>.*?<\/title>/s, `<title>${escapeHtml(meta.title)}</title>`);

  // <html lang="...">
  html = html.replace(/<html lang="[^"]*"/, '<html lang="fr"');

  // robots meta — these pages are genuinely public marketing content,
  // unlike the app shell's safe default of noindex.
  html = html.replace(
    /<meta\s+name="robots"[^>]*>/,
    '<meta name="robots" content="index,follow,noarchive">'
  );

  // meta description — add if absent, replace if present.
  const descTag = `<meta name="description" content="${escapeHtml(meta.description)}">`;
  if (/<meta\s+name="description"/.test(html)) {
    html = html.replace(/<meta\s+name="description"[^>]*>/, descTag);
  } else {
    html = html.replace('</title>', '</title>\n    ' + descTag);
  }

  // canonical — add if absent, replace if present.
  const canonicalTag = `<link rel="canonical" href="${canonical}">`;
  if (/<link[^>]+rel="canonical"/.test(html)) {
    html = html.replace(/<link[^>]+rel="canonical"[^>]*>/, canonicalTag);
  } else {
    html = html.replace('</title>', '</title>\n    ' + canonicalTag);
  }

  // Open Graph
  const ogTags = [
    `<meta property="og:type" content="website">`,
    `<meta property="og:title" content="${escapeHtml(meta.title)}">`,
    `<meta property="og:description" content="${escapeHtml(meta.description)}">`,
    `<meta property="og:url" content="${canonical}">`,
    `<meta property="og:image" content="${CANONICAL_ORIGIN}/brand/innovacar-logo.png">`,
    `<meta name="twitter:card" content="summary">`,
  ].join('\n    ');
  html = html.replace('</title>', `</title>\n    ${ogTags}`);

  if (routePath === '/') {
    html = html.replace(
      '</head>',
      `  <script type="application/ld+json">${JSON.stringify(SOFTWARE_APPLICATION_JSONLD)}</script>\n  </head>`
    );
  }

  // Static content for first paint / crawlers. React replaces this on client
  // mount (main.tsx uses createRoot, not hydrateRoot, so this is a plain
  // replace, not a hydration match).
  //
  // dist/index.html — the routePath === '/' output — is not exclusive to the
  // marketing homepage: this is a HashRouter SPA, so a URL fragment
  // ("#/dashboard", "#/contracts", ...) is never sent to the server, and
  // every authenticated route is served this exact same file on refresh.
  // Baking the marketing body straight into #root unconditionally would
  // briefly paint the landing page before main.tsx's module script loads and
  // decides which app to mount — the "wrong page flashes on refresh" bug.
  // route-bootstrap.js (loaded synchronously in <head>, before this markup is
  // even parsed) sets data-app-route="true" on <html> the instant it sees a
  // "#/..." fragment, and the matching CSS in <head> hides #marketing-prerender-root
  // / shows #app-boot-shell for that case — so a hash-route refresh paints
  // the neutral shell instead, never the landing page.
  const rootMarkup = routePath === '/'
    ? `<div id="marketing-prerender-root">${bodyHtml}</div>${APP_BOOT_SHELL_HTML}`
    : bodyHtml;
  html = html.replace(/<div id="root"><\/div>/, `<div id="root">${rootMarkup}</div>`);

  const outPath = routePath === '/'
    ? path.join(DIST, 'index.html')
    : path.join(DIST, routePath.replace(/^\//, ''), 'index.html');
  mkdirSync(path.dirname(outPath), { recursive: true });
  writeFileSync(outPath, html, 'utf8');
  written += 1;
  console.log(`[prerender-marketing] wrote ${path.relative(DIST, outPath)}`);
}

console.log(`[prerender-marketing] done — ${written} page(s) prerendered.`);

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
