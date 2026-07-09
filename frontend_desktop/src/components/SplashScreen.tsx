import { Car } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function SplashScreen() {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-[#0b1437]" role="status">
      <div className="text-center text-white">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-lg bg-accent-400 text-[#0b1437]">
          <Car className="splash-car" size={32} />
        </div>
        <h1 className="mt-5 text-2xl font-bold">RentCar</h1>
        <p className="mt-1 text-xs uppercase text-slate-400">{t('guidance.innovax')}</p>
        <div className="mx-auto mt-6 h-1 w-44 overflow-hidden rounded-full bg-white/10">
          <div className="splash-progress h-full bg-accent-400" />
        </div>
      </div>
    </div>
  );
}
