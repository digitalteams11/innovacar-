import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2 } from 'lucide-react';
import { API_ORIGIN } from '../../lib/api';

/** Official Google "G" logo (per Google's brand guidelines — full color, not a mono glyph). */
function GoogleGlyph() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" />
      <path fill="#FBBC05" d="M3.964 10.706A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.706V4.962H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.038l3.007-2.332z" />
      <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.962L3.964 7.294C4.672 5.167 6.656 3.58 9 3.58z" />
    </svg>
  );
}

/**
 * Redirects the browser to the server-side Google OAuth2 Authorization Code
 * flow (GET /oauth2/authorization/google — Spring Security's own filter
 * chain, not an API call). Never touches a Client ID/Secret or ID token on
 * the frontend; the whole exchange happens between the browser, Google and
 * the backend. See server/.../security/oauth2/* and AuthController's
 * POST /api/auth/oauth2/exchange (the second leg, handled on the Login page).
 */
export default function GoogleAuthButton({
  label,
  loadingLabel,
  disabled,
}: {
  label?: string;
  loadingLabel?: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const [redirecting, setRedirecting] = useState(false);

  const handleClick = () => {
    if (redirecting || disabled) return;
    setRedirecting(true);
    window.location.href = `${API_ORIGIN}/oauth2/authorization/google`;
  };

  const busy = redirecting || disabled;

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={busy}
      aria-label={label || t('login.continueWithGoogle')}
      aria-busy={redirecting}
      className="w-full min-h-[48px] py-3 rounded-xl flex items-center justify-center gap-3 text-sm font-medium transition-all disabled:opacity-70 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
      style={{
        border: '1px solid var(--border-subtle)',
        backgroundColor: 'var(--bg-input)',
        color: 'var(--text-primary)',
      }}
    >
      {redirecting ? (
        <>
          <Loader2 size={18} className="animate-spin" aria-hidden="true" />
          <span>{loadingLabel || t('login.redirectingToGoogle')}</span>
        </>
      ) : (
        <>
          <GoogleGlyph />
          <span>{label || t('login.continueWithGoogle')}</span>
        </>
      )}
    </button>
  );
}
