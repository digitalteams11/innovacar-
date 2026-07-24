/**
 * Normalizes a stored client phone number into the digits-only, full
 * international format `wa.me` links require. Client phones in this app are
 * typically saved in local Moroccan format (06XXXXXXXX / 07XXXXXXXX) with no
 * country code, which `wa.me` cannot route — this maps 06→2126, 07→2127,
 * leaves already-international numbers (212... or +212...) alone, and
 * strips spaces/dashes/parentheses/leading '+' either way.
 */
export function normalizePhoneForWhatsApp(phone: string | null | undefined): string {
  if (!phone) return '';
  const digits = phone.replace(/[^\d]/g, '');
  if (digits.startsWith('212')) return digits;
  if (digits.startsWith('0') && digits.length === 10) return `212${digits.slice(1)}`;
  return digits;
}

/**
 * True when a normalized number is a valid Moroccan mobile line reachable on
 * WhatsApp: country code 212 followed by a 06/07 mobile prefix (6 or 7) and
 * 8 more digits — 12 digits total. Landlines and malformed input are rejected
 * so the "Open WhatsApp" button never opens an empty conversation.
 */
export function isValidMoroccanWhatsAppPhone(phone: string | null | undefined): boolean {
  return /^212[67]\d{8}$/.test(normalizePhoneForWhatsApp(phone));
}
