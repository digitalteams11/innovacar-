// Prerenders the public marketing pages (src/marketing/pages.tsx) to static
// HTML at build time, using dist/index.html (already built by `vite build`,
// with correct hashed asset links) as the template. This is the "Acceptable"
// prerender approach for a pure client-side SPA — see docs/seo-route-inventory.md.
//
// Run via `npm run prerender:marketing`, wired after `vite build` and before
// `verify:seo` so dist/ has the final marketing HTML before the SEO checks run.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import { transformWithOxc } from 'vite';
import { renderToStaticMarkup } from 'react-dom/server';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.join(ROOT, '..', 'dist');
const PAGES_SRC = path.join(ROOT, '..', 'src', 'marketing', 'pages.tsx');
const TEMPLATE_PATH = path.join(DIST, 'index.html');
const CANONICAL_ORIGIN = 'https://innovacar.app';

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

  // Static content for first paint / crawlers. React re-renders over this on
  // hydration-equivalent client mount (main.tsx); a brief content replace on
  // load is acceptable for this MVP, real content is already visible without JS.
  html = html.replace(/<div id="root"><\/div>/, `<div id="root">${bodyHtml}</div>`);

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
