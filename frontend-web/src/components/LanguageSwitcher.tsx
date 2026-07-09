import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';

const languages = [
  { code: 'en', label: 'English', flag: 'US' },
  { code: 'fr', label: 'Français', flag: 'FR' },
  { code: 'ar', label: 'العربية', flag: 'MA' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
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
