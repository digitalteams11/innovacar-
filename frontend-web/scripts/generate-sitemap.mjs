// Generates public/sitemap.xml from src/seo/public-routes.json — the same
// registry the app's TS code reads (src/seo/publicRoutes.ts) — so the
// sitemap can never list a URL that isn't a real, deliberately-registered
// public route. Run via `npm run generate:sitemap`, wired before `vite build`
// so `public/sitemap.xml` exists when Vite copies the public/ dir into dist/.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const REGISTRY_PATH = path.join(ROOT, '..', 'src', 'seo', 'public-routes.json');
const OUTPUT_PATH = path.join(ROOT, '..', 'public', 'sitemap.xml');
const CANONICAL_ORIGIN = 'https://innovacar.app';

const routes = JSON.parse(readFileSync(REGISTRY_PATH, 'utf8'));

for (const route of routes) {
  if (!route.path || !route.path.startsWith('/')) {
    throw new Error(`Invalid sitemap route "${route.path}" — must be a path starting with "/"`);
  }
  if (route.path.includes('#')) {
    throw new Error(`Sitemap route "${route.path}" contains "#" — hash fragments are not valid sitemap URLs`);
  }
}

const urlEntries = routes
  .map((route) => {
    const loc = `${CANONICAL_ORIGIN}${route.path}`;
    const lastmod = route.lastmod ? `\n    <lastmod>${route.lastmod}</lastmod>` : '';
    return `  <url>\n    <loc>${loc}</loc>${lastmod}\n  </url>`;
  })
  .join('\n');

const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urlEntries}\n</urlset>\n`;

writeFileSync(OUTPUT_PATH, xml, 'utf8');
console.log(`[generate-sitemap] wrote ${routes.length} URL(s) to ${path.relative(process.cwd(), OUTPUT_PATH)}`);
if (routes.length === 0) {
  console.log('[generate-sitemap] NOTE: 0 public routes registered — see src/seo/publicRoutes.ts for why.');
}
