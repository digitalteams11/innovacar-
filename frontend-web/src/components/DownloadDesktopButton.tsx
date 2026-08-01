import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Loader2, CheckCircle2, AlertTriangle, Mail } from 'lucide-react';
import api from '../api/axios';
import type { DesktopRelease } from '../hooks/useDesktopRelease';

type DownloadSource = 'LANDING' | 'DESKTOP_PAGE' | 'DASHBOARD_BANNER' | 'SETTINGS';
type DownloadState = 'idle' | 'preparing' | 'started' | 'failed';

/**
 * The one shared "Download for Windows" control — used on the marketing
 * homepage, /desktop, and the authenticated Desktop App page, so there is
 * exactly one place that decides what a click does (record analytics, then
 * a real browser download) instead of several components each hardcoding
 * their own installer URL.
 */
export default function DownloadDesktopButton({
  release,
  source,
  className,
}: {
  release: DesktopRelease | null;
  source: DownloadSource;
  className?: string;
}) {
  const { t } = useTranslation();
  const [state, setState] = useState<DownloadState>('idle');

  if (!release || !release.available) {
    return (
      <div className={className}>
        <div className="inline-flex items-center gap-2 rounded-xl border border-dashed px-4 py-2.5 text-sm font-medium text-slate-500 dark:text-slate-400" style={{ borderColor: 'var(--border-subtle)' }}>
          {t('desktop.comingSoon', 'Desktop app coming soon')}
        </div>
        <a
          href="mailto:support@innovacar.app?subject=Notify%20me%20when%20Innovacar%20Desktop%20is%20available"
          className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-emerald-700 hover:underline dark:text-emerald-400"
        >
          <Mail size={13} /> {t('desktop.notifyMe', 'Notify me when available')}
        </a>
      </div>
    );
  }

  const handleClick = async () => {
    setState('preparing');
    try {
      await api.post('/public/desktop/downloads', {
        releaseId: release.releaseId,
        source,
        status: 'STARTED',
      });
    } catch {
      // Analytics failure must never block the actual download.
    }
    try {
      const link = document.createElement('a');
      link.href = release.downloadUrl!;
      link.setAttribute('download', release.fileName || '');
      document.body.appendChild(link);
      link.click();
      link.remove();
      setState('started');
    } catch {
      setState('failed');
      api.post('/public/desktop/downloads', {
        releaseId: release.releaseId,
        source,
        status: 'FAILED',
      }).catch(() => {});
    }
  };

  return (
    <div className={className}>
      <button
        type="button"
        onClick={handleClick}
        disabled={state === 'preparing'}
        className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white shadow-elevated transition hover:bg-emerald-700 disabled:opacity-70 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2"
        aria-label={`${t('desktop.downloadWindows', 'Download for Windows')} — ${t('desktop.latestVersion', 'Version')} ${release.version}, ${formatSize(release.fileSizeBytes)}`}
      >
        {state === 'preparing' ? (
          <><Loader2 size={16} className="animate-spin motion-reduce:animate-none" /> {t('desktop.downloadPreparing', 'Preparing download…')}</>
        ) : (
          <><Download size={16} aria-hidden="true" /> {t('desktop.downloadWindows', 'Download for Windows')}</>
        )}
      </button>
      {state === 'started' && (
        <p className="mt-2 flex items-center gap-1.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
          <CheckCircle2 size={14} /> {t('desktop.downloadStarted', 'Download started')}
        </p>
      )}
      {state === 'failed' && (
        <p className="mt-2 flex items-center gap-1.5 text-xs font-medium text-rose-600 dark:text-rose-400">
          <AlertTriangle size={14} /> {t('desktop.downloadFailed', 'Download failed — please try again')}
        </p>
      )}
    </div>
  );
}

function formatSize(bytes?: number): string {
  if (!bytes) return '';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(0)} MB`;
}
