import { describe, it, expect, beforeEach } from 'vitest';
import { normalizeThemePreference, migrateLegacyThemeKeys } from '../ThemeContext';

const CANONICAL_KEY = 'innovacar-theme';
const LEGACY_KEYS = ['innovacar.theme.preference', 'theme', 'darkMode', 'selectedTheme', 'colorMode', 'app-theme'];

describe('normalizeThemePreference', () => {
  it('passes through the three canonical values', () => {
    expect(normalizeThemePreference('light')).toBe('light');
    expect(normalizeThemePreference('dark')).toBe('dark');
    expect(normalizeThemePreference('system')).toBe('system');
  });

  it('is case-insensitive and trims whitespace', () => {
    expect(normalizeThemePreference('DARK')).toBe('dark');
    expect(normalizeThemePreference('  Light  ')).toBe('light');
  });

  it('maps the legacy "auto" value to "system" (pre-rename localStorage compatibility)', () => {
    expect(normalizeThemePreference('auto')).toBe('system');
    expect(normalizeThemePreference('AUTO')).toBe('system');
  });

  it('falls back to "light" for null/undefined/garbage instead of throwing', () => {
    expect(normalizeThemePreference(null)).toBe('light');
    expect(normalizeThemePreference(undefined)).toBe('light');
    expect(normalizeThemePreference('')).toBe('light');
    expect(normalizeThemePreference('not-a-theme')).toBe('light');
    expect(normalizeThemePreference(42)).toBe('light');
    expect(normalizeThemePreference({})).toBe('light');
  });
});

describe('migrateLegacyThemeKeys', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('does nothing when no theme key exists on the device (default stays light/unset)', () => {
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBeNull();
  });

  it('leaves an already-canonical value untouched and removes any leftover legacy keys', () => {
    localStorage.setItem(CANONICAL_KEY, 'dark');
    localStorage.setItem('theme', 'light');
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('dark');
    for (const key of LEGACY_KEYS) expect(localStorage.getItem(key)).toBeNull();
  });

  it('adopts a legacy key value into the canonical key and removes the legacy key', () => {
    localStorage.setItem('darkMode', 'dark');
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('dark');
    expect(localStorage.getItem('darkMode')).toBeNull();
  });

  it('normalizes a legacy value while migrating (e.g. legacy "auto" -> "system")', () => {
    localStorage.setItem('selectedTheme', 'auto');
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('system');
  });

  it('falls back to the rentcar_appearance blob mode only when no dedicated legacy key exists', () => {
    localStorage.setItem('rentcar_appearance', JSON.stringify({ mode: 'dark', preset: 'neo-emerald' }));
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('dark');
  });

  it('prefers a dedicated legacy key over the rentcar_appearance blob when both are present', () => {
    localStorage.setItem('theme', 'light');
    localStorage.setItem('rentcar_appearance', JSON.stringify({ mode: 'dark' }));
    migrateLegacyThemeKeys();
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('light');
  });
});
