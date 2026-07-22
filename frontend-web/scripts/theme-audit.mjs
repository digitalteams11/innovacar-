import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const sourceRoot = path.join(root, 'src');

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

// Tailwind light-toned surface/foreground shades this app actually uses.
const LIGHT_SHADE = /^(white|slate-50|slate-100|gray-50|gray-100|neutral-50|neutral-100|zinc-50|zinc-100)$/;
const LIGHT_TEXT_SHADE = /^(white|slate-50|slate-100|slate-200|gray-50|gray-100|gray-200|neutral-50|neutral-100|zinc-50|zinc-100)$/;

// Splits a Tailwind class token into its variant-prefix chain and base
// utility, e.g. "dark:hover:bg-slate-50" -> { prefixes: ['dark','hover'], base: 'bg-slate-50' }.
function parseToken(token) {
  const parts = token.split(':');
  const base = parts.pop();
  return { prefixes: parts, base };
}

// Only the truly "resting state" reading of a class list matters for this
// check: an unprefixed utility (applies in light mode, at rest) or a utility
// prefixed with exactly `dark:` (applies in dark mode, at rest). Anything
// carrying `hover:`, `focus:`, `active:`, `group-hover:`, etc. is an
// interaction-only style, not what's on screen by default — mixing those in
// is the single biggest source of false positives for a regex-only scanner
// (e.g. `text-slate-700 hover:bg-slate-50 dark:text-white` has no resting
// background at all; the element is transparent until hovered, in both themes).
function restingTokens(literal) {
  const light = [];
  const dark = [];
  for (const token of literal.split(/\s+/).filter(Boolean)) {
    const { prefixes, base } = parseToken(token);
    if (prefixes.length === 0) light.push(base);
    else if (prefixes.length === 1 && prefixes[0] === 'dark') dark.push(base);
  }
  return { light, dark };
}

function shadeOf(base, utility) {
  const match = base.match(new RegExp(`^${utility}-([a-z]+-[0-9]{2,3}|white|black)(?:/(\\d{1,3}))?$`));
  if (!match) return null;
  return { shade: match[1], opacity: match[2] ? Number(match[2]) : 100 };
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length;
}

// Finds individual quoted string literals ('...', "...", or a segment of a
// template literal between ${...} interpolations) anywhere in the file —
// not just inside className="...". This is the key fix over naively grabbing
// the whole className expression: a ternary like
// `${cond ? 'bg-navy text-white' : 'bg-slate-50 text-slate-500'}` is two
// SEPARATE literals, each internally a valid, readable pairing on its own —
// scanning the concatenation of both branches together produces false
// positives (sees text-white from one branch + bg-slate-50 from the other
// and wrongly concludes "light bg with light text"). Every literal is
// checked independently so only a genuine same-branch mismatch is reported.
// Deliberately single-line only ([^'\n] etc.) — a naive regex can't reliably
// track real JS string/template boundaries across newlines (apostrophes in
// JSX text, nested backticks, escaped quotes), and a multi-line "literal" that
// leaks past its real closing quote produces garbage matches. Tailwind class
// lists are conventionally written on one line/one literal anyway, so this
// trade-off costs a handful of unusually formatted lines, not real coverage.
const stringLiteralPattern = /'([^'\n]*)'|"([^"\n]*)"|`([^`\n]*)`/g;

const findings = [];

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  const relPath = path.relative(root, file);

  for (const match of source.matchAll(stringLiteralPattern)) {
    const literal = match[1] ?? match[2] ?? match[3] ?? '';
    // Only look at literals that plausibly contain Tailwind class lists.
    if (!/\bbg-|\btext-/.test(literal)) continue;
    const line = lineOf(source, match.index);

    const { light, dark } = restingTokens(literal);

    const lightBg = light.map(b => shadeOf(b, 'bg')).find(s => s && LIGHT_SHADE.test(s.shade) && s.opacity >= 40);
    const lightText = light.map(b => shadeOf(b, 'text')).find(s => s && LIGHT_SHADE.test(s.shade) && s.opacity >= 40);
    // Any dark:bg- token counts as "there is a dark-mode override" here, even
    // an arbitrary value like dark:bg-[#1e293b] or dark:bg-[var(--x)] that
    // shadeOf() can't resolve to a named shade — the point of this check is
    // only "did the author remember dark mode needs its own background",
    // not what that background's exact color is.
    const darkBg = dark.some(b => /^bg-/.test(b));
    const darkText = dark.map(b => shadeOf(b, 'text')).find(s => s && LIGHT_TEXT_SHADE.test(s.shade) && s.opacity >= 40);

    // 1. Resting-state light-on-light in light mode: a light background and
    // a light foreground, neither behind a dark: override — invisible text
    // the moment this renders, in either theme.
    if (lightBg && lightText) {
      findings.push({ file: relPath, line, kind: 'light-bg+light-text', classes: literal });
    }

    // 2. dark:text-<light> with no dark:bg- override anywhere in the literal,
    // while the resting (light-mode) background is one of the light surface
    // utilities — in dark mode the background never changes (no dark:bg-*
    // token exists to override it), so the light dark:text- color lands on a
    // card that's still light.
    if (darkText && !darkBg && lightBg) {
      findings.push({ file: relPath, line, kind: 'dark:text-light-without-dark:bg', classes: literal });
    }

    // 3. Badge/pill pattern: identical color+shade for both bg- and text- in
    // the resting state, AND the background is fully opaque (no /NN alpha
    // suffix, or alpha high enough to read as solid) — e.g. bg-emerald-500
    // text-emerald-500 with no opacity is genuinely invisible text.
    // bg-emerald-500/10 text-emerald-500 is the extremely common, intentional
    // "soft badge" pattern (a faint tinted wash behind solid-color text) and
    // must NOT be flagged — the low-alpha background reads as a near-
    // transparent tint, not a matching solid fill.
    const bgSameShade = light.map(b => shadeOf(b, 'bg')).find(s => s && s.opacity >= 40);
    const textSameShade = light.map(b => shadeOf(b, 'text')).find(s => s);
    if (bgSameShade && textSameShade && bgSameShade.shade === textSameShade.shade) {
      findings.push({ file: relPath, line, kind: 'bg-text-same-shade', classes: literal });
    }
  }

  // 4. Suspiciously low text opacity (text-white/5..text-*/20) — reported as a
  // lower-confidence warning since legitimate decorative/disabled uses exist.
  const lowOpacityPattern = /\btext-(?:white|black|[a-z]+-[0-9]{2,3})\/(?:[0-9]|1[0-9])\b/g;
  for (const match of source.matchAll(lowOpacityPattern)) {
    findings.push({ file: relPath, line: lineOf(source, match.index), kind: 'low-opacity-text', classes: match[0] });
  }
}

const dedupSeen = new Set();
const deduped = findings.filter(item => {
  const key = `${item.file}:${item.line}:${item.kind}:${item.classes}`;
  if (dedupSeen.has(key)) return false;
  dedupSeen.add(key);
  return true;
});

const hardFailureKinds = new Set(['light-bg+light-text', 'dark:text-light-without-dark:bg', 'bg-text-same-shade']);
const hardFailures = deduped.filter(item => hardFailureKinds.has(item.kind));
const warnings = deduped.filter(item => !hardFailureKinds.has(item.kind));

const byKind = new Map();
for (const item of deduped) {
  if (!byKind.has(item.kind)) byKind.set(item.kind, []);
  byKind.get(item.kind).push(item);
}

const report = [
  '# Theme Contrast Audit',
  '',
  `Generated: ${new Date().toISOString()}`,
  `Scanned source files: ${files.length}`,
  `Hard failures: ${hardFailures.length}, Warnings (low-opacity, review manually): ${warnings.length}`,
  '',
  ...[...byKind.entries()].flatMap(([kind, items]) => [
    `## ${kind} (${items.length})`,
    '',
    ...items.map(item => `- ${item.file}:${item.line} — \`${item.classes}\``),
    '',
  ]),
].join('\n');

const reportPath = path.join(root, 'THEME_AUDIT.md');
fs.writeFileSync(reportPath, report);
console.log(`Scanned ${files.length} files.`);
console.log(`Hard failures: ${hardFailures.length}`);
console.log(`Warnings (low-opacity text, review manually): ${warnings.length}`);
console.log(`Full report written to ${reportPath}`);

if (hardFailures.length > 0) {
  process.exitCode = 1;
}
