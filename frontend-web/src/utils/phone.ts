/**
 * Normalizes phone numbers before submission (spec section 7). Moroccan
 * local-format numbers (0X…) are rewritten to +212 E.164; anything already
 * in international format (+…) or belonging to another country is passed
 * through untouched — we must never reject/mangle a valid foreign number
 * just because the selected country happens to be Morocco.
 */
export function normalizePhone(raw: string, countryCode: string | undefined | null): string {
  const trimmed = raw.trim().replace(/[\s.-]/g, '');
  if (!trimmed) return trimmed;
  if (trimmed.startsWith('+')) return trimmed;
  if ((countryCode || '').toUpperCase() === 'MA' && /^0[5-7]\d{8}$/.test(trimmed)) {
    return `+212${trimmed.slice(1)}`;
  }
  return trimmed;
}

export function isValidPhone(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed) return false;
  return /^\+?[0-9][0-9\s.-]{6,}$/.test(trimmed);
}
