// Standalone logic test for the sanitizer in src/lib/publicUrl.ts (and the
// duplicate guards in src/lib/api.ts / src/marketing/pages.tsx). Can't
// import the real .ts module from plain Node without a loader, so this
// re-implements the exact same rule and asserts it behaves correctly —
// the regression this guards against is the production incident where a
// leftover *.vercel.app preview URL got inlined into the production bundle
// and failed scripts/verify-seo-dist.mjs.
const UNSAFE_PUBLIC_HOST_PATTERN = /(^|\.)vercel\.app$/i;

function sanitizePublicAppUrl(raw) {
  const trimmed = raw?.trim().replace(/\/+$/, '');
  if (!trimmed) return undefined;
  try {
    const { hostname, protocol } = new URL(trimmed);
    if (protocol !== 'https:') return undefined;
    if (UNSAFE_PUBLIC_HOST_PATTERN.test(hostname)) return undefined;
  } catch {
    return undefined;
  }
  return trimmed;
}

function resolvePublicAppUrl(raw) {
  return sanitizePublicAppUrl(raw) || 'https://innovacar.app';
}

const cases = [
  { raw: undefined, expected: 'https://innovacar.app', label: 'unset falls back to the production domain' },
  { raw: '', expected: 'https://innovacar.app', label: 'empty string falls back to the production domain' },
  { raw: 'https://innovacar.app', expected: 'https://innovacar.app', label: 'the real production domain passes through' },
  { raw: 'https://innovacar.app/', expected: 'https://innovacar.app', label: 'a trailing slash is stripped' },
  { raw: 'https://innovacar-abc123.vercel.app', expected: 'https://innovacar.app', label: 'a vercel.app preview URL is rejected and falls back' },
  { raw: 'https://foo.vercel.app/', expected: 'https://innovacar.app', label: 'any *.vercel.app host is rejected' },
  { raw: 'http://innovacar.app', expected: 'https://innovacar.app', label: 'a non-https scheme is rejected' },
  { raw: 'not a url', expected: 'https://innovacar.app', label: 'a malformed value is rejected' },
];

let failures = 0;
for (const { raw, expected, label } of cases) {
  const actual = resolvePublicAppUrl(raw);
  if (actual !== expected) {
    failures++;
    console.error(`  ✗ ${label}: expected "${expected}", got "${actual}" (input: ${JSON.stringify(raw)})`);
  }
}

if (failures > 0) {
  console.error(`\n[verify-public-url] ${failures} check(s) failed.\n`);
  process.exit(1);
}
console.log('[verify-public-url] all checks passed.');
