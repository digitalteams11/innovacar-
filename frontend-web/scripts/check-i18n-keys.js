#!/usr/bin/env node
/**
 * Compares en.json (source of truth) against fr.json and ar.json.
 * Fails (exit 1) if fr or ar is missing any key that exists in en.
 * Extra keys (e.g. Arabic plural forms like foo_zero/_two/_few/_many) are
 * reported for visibility only and never fail the build.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const LOCALES_DIR = path.join(__dirname, '..', 'src', 'i18n', 'locales');

function loadJson(name) {
  const file = path.join(LOCALES_DIR, name);
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function flatten(obj, prefix = '') {
  let keys = [];
  for (const key of Object.keys(obj)) {
    const value = obj[key];
    const full = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      keys = keys.concat(flatten(value, full));
    } else {
      keys.push(full);
    }
  }
  return keys;
}

// i18next pluralization suffixes valid for locales with >2 plural forms
// (Arabic has 6: zero/one/two/few/many/other). A key like "foo_few" in ar.json
// with no direct "foo_few" in en.json is expected, not a missing/extra bug —
// as long as the base "foo" (or "foo_one"/"foo_other") exists in en.json.
const PLURAL_SUFFIXES = ['_zero', '_one', '_two', '_few', '_many', '_other'];

function baseKey(key) {
  for (const suffix of PLURAL_SUFFIXES) {
    if (key.endsWith(suffix)) return key.slice(0, -suffix.length);
  }
  return key;
}

// Keys present in sourceKeys but missing from targetKeys. A plural-form
// source key (e.g. "foo_few") is considered present if the target at least
// has the base key ("foo") — some locales don't need every plural category.
function findMissing(sourceKeys, targetKeys) {
  const targetSet = new Set(targetKeys);
  return sourceKeys.filter((key) => {
    if (targetSet.has(key)) return false;
    const base = baseKey(key);
    return !(base !== key && targetSet.has(base));
  });
}

// Keys present in targetKeys but absent from sourceKeys, excluding
// legitimate plural-form expansions (e.g. ar.json's "foo_few" when
// en.json only defines "foo").
function findExtra(targetKeys, sourceKeys) {
  const sourceSet = new Set(sourceKeys);
  return targetKeys.filter((key) => {
    if (sourceSet.has(key)) return false;
    const base = baseKey(key);
    return !(base !== key && sourceSet.has(base));
  });
}

// Keys whose value is an empty/whitespace-only string — a key that exists
// but was never actually filled in, which the plain missing-key check can't
// catch since the key itself is present.
function findEmptyValues(obj, prefix = '') {
  let empties = [];
  for (const key of Object.keys(obj)) {
    const value = obj[key];
    const full = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      empties = empties.concat(findEmptyValues(value, full));
    } else if (typeof value === 'string' && value.trim() === '') {
      empties.push(full);
    }
  }
  return empties;
}

function main() {
  const en = loadJson('en.json');
  const fr = loadJson('fr.json');
  const ar = loadJson('ar.json');

  const enKeys = flatten(en);
  const frKeys = flatten(fr);
  const arKeys = flatten(ar);

  const missingFr = findMissing(enKeys, frKeys);
  const missingAr = findMissing(enKeys, arKeys);

  const extraFr = findExtra(frKeys, enKeys);
  const extraAr = findExtra(arKeys, enKeys);

  const emptyEn = findEmptyValues(en);
  const emptyFr = findEmptyValues(fr);
  const emptyAr = findEmptyValues(ar);

  console.log(`en: ${enKeys.length} keys, fr: ${frKeys.length} keys, ar: ${arKeys.length} keys`);

  let hasError = false;

  if (emptyEn.length > 0) {
    hasError = true;
    console.error(`\n${emptyEn.length} empty value(s) in en.json:`);
    emptyEn.forEach((k) => console.error(`  - ${k}`));
  }
  if (emptyFr.length > 0) {
    hasError = true;
    console.error(`\n${emptyFr.length} empty value(s) in fr.json:`);
    emptyFr.forEach((k) => console.error(`  - ${k}`));
  }
  if (emptyAr.length > 0) {
    hasError = true;
    console.error(`\n${emptyAr.length} empty value(s) in ar.json:`);
    emptyAr.forEach((k) => console.error(`  - ${k}`));
  }

  if (missingFr.length > 0) {
    hasError = true;
    console.error(`\nMissing ${missingFr.length} key(s) in fr.json:`);
    missingFr.forEach((k) => console.error(`  - ${k}`));
  }

  if (missingAr.length > 0) {
    hasError = true;
    console.error(`\nMissing ${missingAr.length} key(s) in ar.json:`);
    missingAr.forEach((k) => console.error(`  - ${k}`));
  }

  if (extraFr.length > 0) {
    console.log(`\n(info) ${extraFr.length} extra key(s) in fr.json not in en.json:`);
    extraFr.forEach((k) => console.log(`  - ${k}`));
  }

  if (extraAr.length > 0) {
    console.log(`\n(info) ${extraAr.length} extra key(s) in ar.json not in en.json (Arabic plural forms are expected here):`);
    extraAr.forEach((k) => console.log(`  - ${k}`));
  }

  if (hasError) {
    console.error('\ni18n key check FAILED: fr/ar translations are missing keys present in en.json.');
    process.exit(1);
  }

  console.log('\ni18n key check PASSED: fr and ar have every key defined in en.json.');
}

main();
