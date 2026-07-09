import { AlertCircle, RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface ApiErrorStateProps {
  message: string;
  onRetry?: () => void;
  requestId?: string;
}

export default function ApiErrorState({ message, onRetry, requestId }: ApiErrorStateProps) {
  const { t } = useTranslation();
  return (
    <div className="glass-card flex min-h-[240px] flex-col items-center justify-center px-6 py-10 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-rose-400/25 bg-rose-500/10 text-rose-500 shadow-lg shadow-rose-500/10 dark:text-rose-200">
        <AlertCircle size={24} />
      </div>
      <h3 className="mt-5 text-sm font-extrabold text-[var(--text-primary)]">{t('common.unableToLoadInfo', 'Unable to load this information')}</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-[var(--text-muted)]">{message}</p>
      {requestId && <p className="mt-2 text-xs font-semibold text-[var(--text-faint)]">{t('common.requestId', 'Request ID')}: {requestId}</p>}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-6 inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-emerald-500 to-cyan-500 px-5 py-3 text-sm font-bold text-white shadow-lg shadow-emerald-500/20 transition hover:-translate-y-0.5 hover:shadow-xl"
        >
          <RefreshCw size={15} /> {t('common.tryAgain', 'Try again')}
        </button>
      )}
    </div>
  );
}
