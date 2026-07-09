import { Car } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function PremiumLoader({ fullScreen = false }: { fullScreen?: boolean }) {
  const { t } = useTranslation();
  return (
    <div className={`${fullScreen ? 'fixed inset-0 z-[200] bg-[var(--bg-page)]' : 'min-h-[260px]'} flex items-center justify-center`}>
      <div className="w-52 text-center" role="status" aria-live="polite">
        <div className="relative h-12 overflow-hidden">
          <div className="absolute bottom-1 left-0 right-0 h-px bg-[var(--border-medium)]" />
          <Car className="premium-loader-car absolute bottom-2 text-[var(--brand-primary)]" size={30} />
        </div>
        <div className="mt-3 h-1 overflow-hidden rounded-full bg-[var(--bg-hover)]">
          <div className="premium-loader-route h-full bg-[var(--brand-primary)]" />
        </div>
        <p className="mt-3 text-xs font-medium text-[var(--text-muted)]">{t('guidance.loading')}</p>
      </div>
    </div>
  );
}
