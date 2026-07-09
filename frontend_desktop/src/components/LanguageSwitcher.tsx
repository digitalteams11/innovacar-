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
    <div className="flex items-center gap-1 bg-white/50 rounded-xl px-1 py-1">
      <Globe size={14} className="text-slate-400 ms-1" />
      {languages.map((lang) => (
        <button
          key={lang.code}
          onClick={() => changeLanguage(lang.code)}
          className={`px-2 py-1 rounded-lg text-[10px] font-bold transition-all ${
            i18n.language === lang.code
              ? 'bg-brand-500 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          <span aria-hidden="true">{lang.flag}</span> {lang.label}
        </button>
      ))}
    </div>
  );
}
