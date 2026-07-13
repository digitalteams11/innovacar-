import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import en from './locales/en.json';
import fr from './locales/fr.json';
import ar from './locales/ar.json';

function applyDocumentDirection(language: string) {
  document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  document.documentElement.lang = language;
}

// Apply direction/lang synchronously from whatever language was persisted
// last session, before i18next finishes initializing — avoids a flash of
// LTR/English chrome while the RTL stylesheet/attributes catch up.
try {
  const storedLang = window.localStorage.getItem('i18nextLng');
  if (storedLang) applyDocumentDirection(storedLang.split('-')[0]);
} catch {
  // localStorage unavailable (privacy mode, SSR, etc.) — languageChanged
  // below still applies direction once i18next resolves a language.
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      fr: { translation: fr },
      ar: { translation: ar },
    },
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
    },
    // Dev-only visibility into missing keys — rule is "never silently fall
    // back to English", so a missing ar/fr key must be loud in development
    // even though production still shows the safe (en) fallback text.
    saveMissing: import.meta.env.DEV,
    missingKeyHandler: (languages, _namespace, key) => {
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.warn(`[i18n] Missing translation key "${key}" for language(s): ${languages.join(', ')}`);
      }
    },
  })
  .then(() => {
    applyDocumentDirection(i18n.resolvedLanguage || i18n.language);
  });

i18n.on('languageChanged', (language) => {
  applyDocumentDirection(language);
});

export default i18n;
