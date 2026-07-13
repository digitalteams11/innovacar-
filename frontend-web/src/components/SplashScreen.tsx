import { useTranslation } from 'react-i18next';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';

export default function SplashScreen() {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-[#0b1437]" role="status">
      <div className="text-center text-white">
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-2xl bg-white shadow-xl overflow-hidden">
          <img src={INNOVACAR_LOGO_URL} alt="InnovaCar" className="h-full w-full object-contain p-1" />
        </div>
        <h1 className="mt-5 text-2xl font-bold">InnovaCar</h1>
        <p className="mt-1 text-xs uppercase text-slate-400">{t('guidance.innovax')}</p>
        <div className="mx-auto mt-6 h-1 w-44 overflow-hidden rounded-full bg-white/10">
          <div className="splash-progress h-full bg-accent-400" />
        </div>
      </div>
    </div>
  );
}
