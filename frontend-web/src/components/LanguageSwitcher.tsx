import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';

const languages = [
  { code: 'en', label: 'English', flag: 'US' },
  { code: 'fr', label: 'Français', flag: 'FR' },
  { code: 'ar', label: 'العربية', flag: 'MA' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const { updateCurrentUser } = useAuth();
  const { showToast } = useToast();

  // Mirrors UserMenu.tsx's selectLanguage — the two language pickers must persist
  // the same way, or a change made here silently doesn't survive refresh/other devices.
  const changeLanguage = (lng: string) => {
    if (i18n.language === lng) return;
    i18n.changeLanguage(lng);
    api.put('/users/me/preferences', { language: lng })
      .then(({ data }) => {
        const saved = data?.data;
        if (saved?.language) updateCurrentUser({ language: saved.language });
      })
      .catch((err: any) => {
        showToast(err?.userMessage || 'Unable to save language preference.', 'error');
      });
  };

  return (
    <div
      className="flex items-center gap-1 rounded-xl px-1 py-1"
      style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)' }}
    >
      <Globe size={14} className="ms-1" style={{ color: 'var(--text-muted)' }} />
      {languages.map((lang) => (
        <button
          key={lang.code}
          onClick={() => changeLanguage(lang.code)}
          className="px-2 py-1 rounded-lg text-[10px] font-bold transition-all"
          style={
            i18n.language === lang.code
              ? { background: 'var(--brand-primary)', color: '#fff' }
              : { color: 'var(--text-muted)' }
          }
        >
          <span aria-hidden="true">{lang.flag}</span> {lang.label}
        </button>
      ))}
    </div>
  );
}
