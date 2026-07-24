import { describe, it, expect } from 'vitest';
import { normalizeThemePreference } from '../ThemeContext';

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
