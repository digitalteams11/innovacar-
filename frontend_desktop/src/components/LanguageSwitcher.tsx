import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Globe } from 'lucide-react';

const languages = [
  { code: 'en', label: 'EN' },
  { code: 'fr', label: 'FR' },
  { code: 'ar', label: 'AR' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();

  useEffect(() => {
    document.documentElement.dir = 'ltr';
  }, []);

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
    document.documentElement.dir = 'ltr';
  };

  return (
    <div className="flex items-center gap-1 bg-white/50 rounded-xl px-1 py-1">
      <Globe size={14} className="text-slate-400 ml-1" />
      {languages.map((lang) => (
        <button
          key={lang.code}
          onClick={() => changeLanguage(lang.code)}
          className={`px-2 py-1 rounded-lg text-[10px] font-black uppercase tracking-wider transition-all ${
            i18n.language === lang.code
              ? 'bg-brand-500 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          {lang.label}
        </button>
      ))}
    </div>
  );
}
