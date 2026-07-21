import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const sourceRoot = path.join(root, 'src');
const localeRoot = path.join(sourceRoot, 'i18n', 'locales');
const languages = ['en', 'fr', 'ar'];

const flatten = (value, prefix = '', result = new Set()) => {
  for (const [key, item] of Object.entries(value)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (item && typeof item === 'object' && !Array.isArray(item)) flatten(item, fullKey, result);
    else result.add(fullKey);
  }
  return result;
};

const localeJson = Object.fromEntries(languages.map(language => {
  const json = JSON.parse(fs.readFileSync(path.join(localeRoot, `${language}.json`), 'utf8'));
  return [language, json];
}));
const localeKeys = Object.fromEntries(languages.map(language => [language, flatten(localeJson[language])]));

// A key that exists but was never actually filled in (empty/whitespace-only string)
// slips past the missing-key check above, since the key itself is present.
const findEmptyValues = (value, prefix = '', result = []) => {
  for (const [key, item] of Object.entries(value)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (item && typeof item === 'object' && !Array.isArray(item)) findEmptyValues(item, fullKey, result);
    else if (typeof item === 'string' && item.trim() === '') result.push(fullKey);
  }
  return result;
};
const emptyValues = Object.fromEntries(languages.map(language => [language, findEmptyValues(localeJson[language])]));

// Structural key-set parity: EN is the reference locale (assumed complete). FR and AR
// must define a translation for every EN key — and must not carry keys EN doesn't have
// (usually a sign a key was renamed in one file and not the others). i18next plural
// suffixes (_one/_other, plus Arabic's _zero/_two/_few/_many) are stripped to their base
// key before comparing, since a language legitimately defining more/fewer plural forms
// than another is correct i18n behavior, not a structural mismatch.
const PLURAL_SUFFIX_RE = /_(zero|one|two|few|many|other)$/;
const baseKeysOf = keySet => new Set([...keySet].map(key => key.replace(PLURAL_SUFFIX_RE, '')));

const baseEn = baseKeysOf(localeKeys.en);
const structuralMismatch = {};
for (const language of languages) {
  if (language === 'en') continue;
  const baseLang = baseKeysOf(localeKeys[language]);
  structuralMismatch[language] = {
    missing: [...baseEn].filter(key => !baseLang.has(key)).sort(),
    extra: [...baseLang].filter(key => !baseEn.has(key)).sort(),
  };
}

const files = [];
const walk = directory => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(fullPath);
    else if (/\.(tsx|ts)$/.test(entry.name) && !entry.name.endsWith('.test.tsx') && !entry.name.endsWith('.test.ts')) {
      files.push(fullPath);
    }
  }
};
walk(sourceRoot);

const usedKeys = new Set();

// Words/short tokens that are never real user-facing sentences: brand names,
// units, enum-ish all-caps codes, currency, technical acronyms.
const IGNORE_EXACT = new Set([
  'RentCar', 'Innovacar', 'Innovax Technologies', 'MAD', 'GPS', 'CIN', 'RC',
  'ICE', 'PDF', 'CSV', 'QR', 'API', 'URL', 'ID', 'SMTP', 'OTP', '2FA', 'IBAN',
  'VIN', 'OK', 'N/A',
]);

const isLikelyEnumOrCode = text =>
  /^[A-Z0-9_]+$/.test(text) || // ENUM_VALUE / CONST
  /^[a-z][a-zA-Z0-9]*(\.[a-zA-Z0-9]+)+$/.test(text); // dotted.path.like.this (i18n key itself)

const isLikelyTechnical = text =>
  /^https?:\/\//.test(text) ||
  /^[\w.-]+@[\w.-]+\.\w+$/.test(text) || // email
  /^\/[\w/:-]*$/.test(text) || // route path
  /^#[0-9a-fA-F]{3,8}$/.test(text) || // hex color
  /^\d+$/.test(text); // pure number

function shouldIgnore(text) {
  const trimmed = text.trim();
  if (!trimmed) return true;
  if (IGNORE_EXACT.has(trimmed)) return true;
  if (isLikelyEnumOrCode(trimmed)) return true;
  if (isLikelyTechnical(trimmed)) return true;
  if (!/[A-Za-z]/.test(trimmed)) return true; // no letters at all
  if (trimmed.length < 3) return true;
  return false;
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length;
}

const hardcoded = [];

// 1. t('key') usage — tracks which keys are actually referenced.
const keyPattern = /\bt\(\s*['"`]([^'"`${}]+)['"`]/g;

// 2. JSX text nodes: >Some Text<
const jsxTextPattern = />\s*([A-Z][A-Za-z0-9 ,.'!?/&:+()%-]{2,})\s*</g;

// 3. Common i18n-relevant JSX/string props with a plain string literal value.
const PROP_NAMES = ['placeholder', 'title', 'aria-label', 'label', 'alt', 'description'];
const propPattern = new RegExp(
  `\\b(${PROP_NAMES.join('|')})\\s*=\\s*["']([^"'{}][^"']{1,120})["']`,
  'g'
);

// 4. toast.error/success/info/warning("...") calls.
const toastPattern = /\btoast\.(?:error|success|info|warning)\(\s*['"`]([^'"`${}]{2,})['"`]/g;

// 5. Object literal keys commonly used for tab/menu/column config:
//    label: "...", title: "...", description: "...", header: "..."
const objectLiteralPattern = /\b(label|title|description|header|name)\s*:\s*["']([^"'{}][^"']{1,120})["']/g;

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  const relPath = path.relative(root, file);

  for (const match of source.matchAll(keyPattern)) usedKeys.add(match[1]);

  const record = (index, text, kind) => {
    if (shouldIgnore(text)) return;
    hardcoded.push({ file: relPath, line: lineOf(source, index), text: text.trim(), kind });
  };

  for (const match of source.matchAll(jsxTextPattern)) {
    if (match[1].includes('{')) continue;
    record(match.index, match[1], 'jsx-text');
  }
  for (const match of source.matchAll(propPattern)) {
    record(match.index, match[2], `prop:${match[1]}`);
  }
  for (const match of source.matchAll(toastPattern)) {
    record(match.index, match[1], 'toast');
  }
  for (const match of source.matchAll(objectLiteralPattern)) {
    record(match.index, match[2], `object:${match[1]}`);
  }
}

// Dedupe identical file:line:kind entries produced by overlapping patterns.
const seen = new Set();
const dedupedHardcoded = hardcoded.filter(item => {
  const key = `${item.file}:${item.line}:${item.kind}:${item.text}`;
  if (seen.has(key)) return false;
  seen.add(key);
  return true;
});

// i18next resolves a countable key like `foo.bar` (called as `t('foo.bar', { count })`)
// against suffixed variants (`foo.bar_one`, `foo.bar_other`, and Arabic's extra
// `_zero`/`_two`/`_few`/`_many` forms) — it never needs the bare key to exist in the
// locale file itself. Without this, every pluralized key falsely reports as "missing"
// in every language, which defeats the whole point of the audit (a real gap gets lost
// in the noise of dozens of false ones).
const PLURAL_SUFFIXES = ['_zero', '_one', '_two', '_few', '_many', '_other'];
function keyExists(keySet, key) {
  if (keySet.has(key)) return true;
  return PLURAL_SUFFIXES.some(suffix => keySet.has(`${key}${suffix}`));
}

const missing = {};
for (const language of languages) {
  missing[language] = [...usedKeys].filter(key => !keyExists(localeKeys[language], key)).sort();
}

const byFile = new Map();
for (const item of dedupedHardcoded) {
  if (!byFile.has(item.file)) byFile.set(item.file, []);
  byFile.get(item.file).push(item);
}
const fileCounts = [...byFile.entries()]
  .map(([file, items]) => [file, items.length])
  .sort((a, b) => b[1] - a[1]);

const report = [
  '# Translation Audit',
  '',
  `Generated: ${new Date().toISOString()}`,
  `Scanned source files: ${files.length}`,
  `Static translation keys used: ${usedKeys.size}`,
  '',
  ...languages.flatMap(language => [
    `## Missing ${language.toUpperCase()} Keys (${missing[language].length})`,
    '',
    ...(missing[language].length ? missing[language].map(key => `- \`${key}\``) : ['None.']),
    '',
  ]),
  '## Empty Translation Values',
  '',
  ...languages.flatMap(language => [
    `### ${language.toUpperCase()} (${emptyValues[language].length})`,
    '',
    ...(emptyValues[language].length ? emptyValues[language].map(key => `- \`${key}\``) : ['None.']),
    '',
  ]),
  '## Structural Key-Set Parity (EN is the reference locale)',
  '',
  ...languages.filter(l => l !== 'en').flatMap(language => [
    `### ${language.toUpperCase()}: missing (${structuralMismatch[language].missing.length})`,
    '',
    ...(structuralMismatch[language].missing.length ? structuralMismatch[language].missing.map(key => `- \`${key}\``) : ['None.']),
    '',
    `### ${language.toUpperCase()}: extra / unexpected (${structuralMismatch[language].extra.length})`,
    '',
    ...(structuralMismatch[language].extra.length ? structuralMismatch[language].extra.map(key => `- \`${key}\``) : ['None.']),
    '',
  ]),
  `## Possible Visible Hardcoded Text (${dedupedHardcoded.length})`,
  '',
  '### By file',
  '',
  ...fileCounts.map(([file, count]) => `- ${file}: ${count}`),
  '',
  '### Detail',
  '',
  ...(dedupedHardcoded.length
    ? dedupedHardcoded.map(item => `- ${item.file}:${item.line} [${item.kind}] - ${item.text}`)
    : ['None.']),
  '',
  'Dynamic translation keys are reviewed separately because static analysis cannot resolve template expressions.',
].join('\n');

const reportPath = path.join(root, 'TRANSLATION_AUDIT.md');
fs.writeFileSync(reportPath, report);
console.log(`Scanned ${files.length} files.`);
console.log(`Hardcoded candidates: ${dedupedHardcoded.length}`);
console.log(`Missing FR keys: ${missing.fr.length}, Missing AR keys: ${missing.ar.length}`);
console.log(
  `Structural parity — FR missing/extra: ${structuralMismatch.fr.missing.length}/${structuralMismatch.fr.extra.length}, `
  + `AR missing/extra: ${structuralMismatch.ar.missing.length}/${structuralMismatch.ar.extra.length}`
);
console.log(
  `Empty values — EN: ${emptyValues.en.length}, FR: ${emptyValues.fr.length}, AR: ${emptyValues.ar.length}`
);
console.log(`Full report written to ${reportPath}`);

const structuralFailure = languages
  .filter(l => l !== 'en')
  .some(l => structuralMismatch[l].missing.length > 0 || structuralMismatch[l].extra.length > 0);
const emptyValueFailure = languages.some(l => emptyValues[l].length > 0);

if (dedupedHardcoded.length > 0 || missing.fr.length > 0 || missing.ar.length > 0 || structuralFailure || emptyValueFailure) {
  process.exitCode = 1;
}
