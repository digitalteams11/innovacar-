import i18n from './index';

/**
 * Translates a backend `errorCode` (e.g. "VEHICLE_NOT_FOUND") via the
 * `errors.*` namespace. Returns null if no translation exists for that code,
 * so callers can fall back to the backend's own message.
 */
export function translateErrorCode(errorCode: string | null | undefined): string | null {
  if (!errorCode) return null;
  const key = `errors.${errorCode}`;
  return i18n.exists(key) ? i18n.t(key) : null;
}

/**
 * Resolves the best user-facing message for a failed API call, in this
 * priority order:
 *  1. A translation for the backend's errorCode (errors.<errorCode>)
 *  2. The backend's own message (already language-agnostic, e.g. validation
 *     text composed server-side)
 *  3. A translated generic fallback (errors.GENERIC or a caller-supplied key)
 */
export function resolveApiErrorMessage(err: any, fallback?: string): string {
  const errorCode = err?.errorCode || err?.response?.data?.errorCode;
  const translated = translateErrorCode(errorCode);
  if (translated) return translated;

  const backendMessage = err?.response?.data?.message || err?.userMessage || err?.message;
  if (backendMessage) return backendMessage;

  return fallback || i18n.t('errors.GENERIC');
}
