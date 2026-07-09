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

const localeKeys = Object.fromEntries(languages.map(language => {
  const json = JSON.parse(fs.readFileSync(path.join(localeRoot, `${language}.json`), 'utf8'));
  return [language, flatten(json)];
}));

const files = [];
const walk = directory => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(fullPath);
    else if (/\.(tsx|ts)$/.test(entry.name)) files.push(fullPath);
  }
};
walk(sourceRoot);

const usedKeys = new Set();
const hardcoded = [];
const keyPattern = /\bt\(\s*['"`]([^'"`${}]+)['"`]/g;
const textPattern = />\s*([A-Z][A-Za-z0-9 ,.'!?/&:+()-]{2,})\s*</g;

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  for (const match of source.matchAll(keyPattern)) usedKeys.add(match[1]);
  for (const match of source.matchAll(textPattern)) {
    const text = match[1].trim();
    if (!text || text.includes('{') || /^(RentCar|MAD|GPS|CIN|RC|ICE)$/.test(text)) continue;
    const line = source.slice(0, match.index).split('\n').length;
    hardcoded.push(`${path.relative(root, file)}:${line} - ${text}`);
  }
}

const missing = {};
for (const language of languages) {
  missing[language] = [...usedKeys].filter(key => !localeKeys[language].has(key)).sort();
}

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
  `## Possible Visible Hardcoded Text (${hardcoded.length})`,
  '',
  ...(hardcoded.length ? hardcoded.map(item => `- ${item}`) : ['None.']),
  '',
  'Dynamic translation keys are reviewed separately because static analysis cannot resolve template expressions.',
].join('\n');

const reportPath = path.join(root, 'TRANSLATION_AUDIT.md');
fs.writeFileSync(reportPath, report);
console.log(report);
