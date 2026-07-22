/**
 * Cookie/consent storage. Kept honest to what this app actually does today:
 * only two categories are real. "necessary" covers the rentcar_access/
 * rentcar_refresh auth-session cookies (see AuthCookieService.java) and can
 * never be turned off — the app can't function signed-in without them.
 * "preferences" covers localStorage-based theme/language settings (not
 * technically cookies, but disclosed here too for transparency).
 *
 * There is deliberately no "analytics"/"marketing" category: Innovacar sets
 * no such cookies anywhere. Add one here (and to CookieConsentBanner.tsx +
 * the Cookie Policy page) only once a real analytics/marketing integration
 * actually exists — never ship a toggle for tracking that isn't there.
 */
const STORAGE_KEY = 'rentcar_cookie_consent';

export interface CookieConsent {
  necessary: true;
  preferences: boolean;
  /** Schema version — bump if the category set ever changes, to safely re-prompt. */
  version: 1;
  decidedAt: string;
}

export function getStoredConsent(): CookieConsent | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (parsed && parsed.version === 1 && typeof parsed.preferences === 'boolean') {
      return parsed as CookieConsent;
    }
    return null;
  } catch {
    return null;
  }
}

export function storeConsent(preferences: boolean): CookieConsent {
  const consent: CookieConsent = { necessary: true, preferences, version: 1, decidedAt: new Date().toISOString() };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(consent));
  } catch {
    // Private browsing / storage disabled — the banner will just reappear
    // next visit, which is the safe failure mode (never silently assume consent).
  }
  return consent;
}

export function hasDecided(): boolean {
  return getStoredConsent() !== null;
}
