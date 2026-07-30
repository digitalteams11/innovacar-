/**
 * Central place that turns a raw error (Error, axios error, backend JSON
 * payload, plain string) into a short, friendly, non-technical message a
 * user can read in a tooltip or compact toast — never a stack trace, SQL
 * fragment, or "500 Internal Server Error". The original error always still
 * goes to the browser console via `logDevError` so developers keep full
 * detail without it ever reaching the UI (spec: "keep logs in the console
 * for developers but do not expose technical errors to users").
 */

const TECHNICAL_LEAK_PATTERN =
  /(exception|stack trace|sql|jdbc|hibernate|nullpointer|null pointer|database|constraint|axios|fetch error|internal server|timeout of \d|econnrefused|enotfound|\b5\d\d\b|at com\.|at java\.|at org\.)/i;

const OFFLINE_PATTERN = /(network error|failed to fetch|offline|no internet|err_internet_disconnected)/i;
const SERVICE_UNAVAILABLE_PATTERN = /(service unavailable|bad gateway|gateway timeout|\b502\b|\b503\b|\b504\b)/i;

export type FriendlyErrorKind = 'offline' | 'unavailable' | 'generic';

export interface FriendlyError {
  message: string;
  kind: FriendlyErrorKind;
}

/** Logs the *real* error for developers — always call this alongside toFriendlyError. */
export function logDevError(context: string, error: unknown) {
  // eslint-disable-next-line no-console
  console.error(`[${context}]`, error);
}

/**
 * Extracts the best available message from an axios error / Error / string /
 * backend JSON body ({message, error, errorCode}), then sanitizes it.
 */
export function toFriendlyError(error: unknown, fallback = 'Something went wrong. Please try again.'): FriendlyError {
  const raw = extractRawMessage(error);
  if (!raw) return { message: fallback, kind: 'generic' };

  if (OFFLINE_PATTERN.test(raw)) {
    return { message: 'You appear to be offline.', kind: 'offline' };
  }
  if (SERVICE_UNAVAILABLE_PATTERN.test(raw)) {
    return { message: 'This service is temporarily unavailable.', kind: 'unavailable' };
  }
  if (TECHNICAL_LEAK_PATTERN.test(raw)) {
    return { message: fallback, kind: 'generic' };
  }

  let clean = raw.trim();
  if (clean.toLowerCase().startsWith('error: ') && clean.length > 7) {
    clean = clean.slice(7);
  }
  if (clean.length > 160) {
    clean = clean.slice(0, 157) + '...';
  }
  return { message: clean || fallback, kind: 'generic' };
}

function extractRawMessage(error: unknown): string | null {
  if (!error) return null;
  if (typeof error === 'string') return error;
  if (typeof error !== 'object') return null;

  const err = error as {
    response?: { data?: { message?: string; error?: string; errorCode?: string }; status?: number };
    message?: string;
    code?: string;
  };

  const data = err.response?.data;
  if (data?.message && typeof data.message === 'string') return data.message;
  if (data?.error && typeof data.error === 'string') return data.error;

  if (err.code === 'ERR_NETWORK' || err.message === 'Network Error') return 'Network Error';
  if (err.response?.status && err.response.status >= 500) return `HTTP ${err.response.status}`;

  if (typeof err.message === 'string') return err.message;
  return null;
}
