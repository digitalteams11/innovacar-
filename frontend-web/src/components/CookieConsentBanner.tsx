import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Cookie } from 'lucide-react';
import { getStoredConsent, hasDecided, storeConsent } from '../lib/cookieConsent';

export default function CookieConsentBanner() {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);
  const [customizing, setCustomizing] = useState(false);
  const [preferencesChecked, setPreferencesChecked] = useState(true);

  useEffect(() => {
    if (!hasDecided()) setVisible(true);
    else setPreferencesChecked(getStoredConsent()?.preferences ?? true);
  }, []);

  if (!visible) return null;

  const acceptAll = () => { storeConsent(true); setVisible(false); };
  const rejectNonEssential = () => { storeConsent(false); setVisible(false); };
  const saveCustom = () => { storeConsent(preferencesChecked); setVisible(false); };

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-label={t('cookieBanner.title') as string}
      className="fixed inset-x-0 bottom-0 z-[200] p-3 sm:p-4"
    >
      <div className="mx-auto max-w-3xl rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] p-4 shadow-2xl sm:p-5">
        <div className="flex items-start gap-3">
          <Cookie size={20} className="mt-0.5 shrink-0 text-[var(--brand-primary)]" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-bold text-[var(--text-primary)]">{t('cookieBanner.title')}</p>
            <p className="mt-1 text-xs text-[var(--text-muted)]">{t('cookieBanner.description')}</p>

            {customizing && (
              <div className="mt-3 space-y-2 rounded-xl border border-[var(--border-subtle)] p-3">
                <label className="flex items-start gap-2.5 text-xs">
                  <input type="checkbox" checked disabled className="mt-0.5 h-4 w-4 shrink-0 rounded opacity-60" />
                  <span>
                    <span className="font-semibold text-[var(--text-primary)]">{t('cookieBanner.categories.necessary.title')}</span>
                    <span className="block text-[var(--text-muted)]">{t('cookieBanner.categories.necessary.description')}</span>
                  </span>
                </label>
                <label className="flex items-start gap-2.5 text-xs">
                  <input
                    type="checkbox"
                    checked={preferencesChecked}
                    onChange={(e) => setPreferencesChecked(e.target.checked)}
                    className="mt-0.5 h-4 w-4 shrink-0 rounded"
                  />
                  <span>
                    <span className="font-semibold text-[var(--text-primary)]">{t('cookieBanner.categories.preferences.title')}</span>
                    <span className="block text-[var(--text-muted)]">{t('cookieBanner.categories.preferences.description')}</span>
                  </span>
                </label>
              </div>
            )}

            <div className="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:items-center">
              {customizing ? (
                <button
                  onClick={saveCustom}
                  className="min-h-[44px] flex-1 rounded-xl bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700 sm:flex-none"
                >
                  {t('cookieBanner.savePreferences')}
                </button>
              ) : (
                <>
                  <button
                    onClick={rejectNonEssential}
                    className="min-h-[44px] flex-1 rounded-xl border border-[var(--border-subtle)] px-4 text-sm font-semibold text-[var(--text-secondary)] sm:flex-none"
                  >
                    {t('cookieBanner.rejectNonEssential')}
                  </button>
                  <button
                    onClick={() => setCustomizing(true)}
                    className="min-h-[44px] flex-1 rounded-xl border border-[var(--border-subtle)] px-4 text-sm font-semibold text-[var(--text-secondary)] sm:flex-none"
                  >
                    {t('cookieBanner.customize')}
                  </button>
                  <button
                    onClick={acceptAll}
                    className="min-h-[44px] flex-1 rounded-xl bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700 sm:flex-none"
                  >
                    {t('cookieBanner.acceptAll')}
                  </button>
                </>
              )}
              <a href="/cookies" className="text-xs font-semibold text-[var(--text-muted)] underline sm:ms-auto">
                {t('cookieBanner.learnMore')}
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
