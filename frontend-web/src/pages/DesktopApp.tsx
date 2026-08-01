import { useTranslation } from 'react-i18next';
import { Monitor, Bell, FileDown, Printer, Zap, RefreshCw, ShieldCheck, LifeBuoy } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { GlassCard } from '../components/GlassCard';
import { useDesktopRelease } from '../hooks/useDesktopRelease';
import DownloadDesktopButton from '../components/DownloadDesktopButton';

const BENEFITS = [
  { icon: Monitor, key: 'dedicatedWorkspace', fallback: 'A dedicated workspace, separate from your browser tabs' },
  { icon: Bell, key: 'nativeNotifications', fallback: 'Native Windows notifications' },
  { icon: FileDown, key: 'securePdf', fallback: 'Secure PDF saving with a native file dialog' },
  { icon: Printer, key: 'printing', fallback: 'Direct printing support' },
  { icon: Zap, key: 'fastAccess', fallback: 'Fast access — launch straight into your dashboard' },
  { icon: RefreshCw, key: 'futureUpdates', fallback: 'Automatic updates (coming soon)' },
];

function formatSize(bytes?: number): string {
  if (!bytes) return '—';
  return `${(bytes / (1024 * 1024)).toFixed(0)} MB`;
}

export default function DesktopApp() {
  const { t, i18n } = useTranslation();
  const { release, loading } = useDesktopRelease();
  const lang = (i18n.language || 'en').slice(0, 2) as 'en' | 'fr' | 'ar';
  const notes = release?.releaseNotes?.[lang] ?? release?.releaseNotes?.en ?? [];

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-10">
      <GlassPageHeader
        title={t('desktop.title', 'Innovacar Desktop')}
        subtitle={t('desktop.subtitle', 'A native Windows workspace for your Innovacar account')}
      />

      <GlassCard className="p-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              {loading
                ? t('common.loading', 'Loading…')
                : release?.available
                  ? `${t('desktop.latestVersion', 'Latest version')}: ${release.version}`
                  : t('desktop.comingSoon', 'Desktop app coming soon')}
            </p>
            {release?.available && (
              <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
                {t('desktop.releaseDate', 'Release date')}: {release.releaseDate ? new Date(release.releaseDate).toLocaleDateString() : '—'}
                {' · '}
                {t('desktop.fileSize', 'File size')}: {formatSize(release.fileSizeBytes)}
              </p>
            )}
          </div>
          <DownloadDesktopButton release={release} source="SETTINGS" />
        </div>
      </GlassCard>

      <GlassCard className="p-6">
        <h2 className="text-sm font-bold mb-4" style={{ color: 'var(--text-primary)' }}>
          {t('desktop.benefitsTitle', 'Why use the desktop app')}
        </h2>
        <div className="grid sm:grid-cols-2 gap-4">
          {BENEFITS.map(({ icon: Icon, key, fallback }) => (
            <div key={key} className="flex items-start gap-3">
              <Icon size={18} className="mt-0.5 shrink-0 text-emerald-600" aria-hidden="true" />
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>{t(`desktop.benefits.${key}`, fallback)}</p>
            </div>
          ))}
        </div>
      </GlassCard>

      <GlassCard className="p-6">
        <h2 className="text-sm font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
          {t('desktop.systemRequirements', 'System requirements')}
        </h2>
        <ul className="text-sm space-y-1 list-disc list-inside" style={{ color: 'var(--text-secondary)' }}>
          <li>{release?.minimumOs || 'Windows 10'} {t('desktop.orLater', 'or later')}</li>
          <li>{t('desktop.req64bit', '64-bit processor')}</li>
          <li>{t('desktop.reqInternet', 'Internet connection')}</li>
          <li>{t('desktop.reqAccount', 'An active Innovacar account')}</li>
        </ul>
      </GlassCard>

      {release?.available && notes.length > 0 && (
        <GlassCard className="p-6">
          <h2 className="text-sm font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
            {t('desktop.releaseNotes', 'Release notes')}
          </h2>
          <ul className="text-sm space-y-1 list-disc list-inside" style={{ color: 'var(--text-secondary)' }}>
            {notes.map((line, i) => <li key={i}>{line}</li>)}
          </ul>
          {release.sha256 && (
            <p className="mt-3 text-xs font-mono break-all" style={{ color: 'var(--text-muted)' }}>
              {t('desktop.checksum', 'SHA-256 checksum')}: {release.sha256}
            </p>
          )}
        </GlassCard>
      )}

      <GlassCard className="p-6">
        <h2 className="text-sm font-bold mb-3 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
          <ShieldCheck size={16} className="text-emerald-600" /> {t('desktop.sameAccount', 'Same account, same data')}
        </h2>
        <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
          {t('desktop.sameData', 'No new account is required. Sign in with the same Innovacar email and password (or Google account) you already use on the web — your vehicles, reservations, contracts and permissions are the exact same data, kept in sync automatically.')}
        </p>
      </GlassCard>

      <GlassCard className="p-6">
        <h2 className="text-sm font-bold mb-3" style={{ color: 'var(--text-primary)' }}>
          {t('desktop.installation.title', 'Installation steps')}
        </h2>
        <ol className="text-sm space-y-1.5 list-decimal list-inside" style={{ color: 'var(--text-secondary)' }}>
          <li>{t('desktop.installation.step1', 'Open Innovacar Setup.')}</li>
          <li>{t('desktop.installation.step2', 'Follow the installation steps.')}</li>
          <li>{t('desktop.installation.step3', 'Launch Innovacar.')}</li>
          <li>{t('desktop.installation.step4', 'Sign in using the same Innovacar account.')}</li>
        </ol>
      </GlassCard>

      <GlassCard className="p-6">
        <h2 className="text-sm font-bold mb-2 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
          <LifeBuoy size={16} /> {t('desktop.support', 'Need help?')}
        </h2>
        <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
          {t('desktop.supportCopy', 'If the installer or sign-in doesn\'t work as expected, contact support and we\'ll help you get set up.')}{' '}
          <a href="mailto:support@innovacar.app" className="font-semibold text-emerald-700 hover:underline dark:text-emerald-400">
            support@innovacar.app
          </a>
        </p>
      </GlassCard>
    </div>
  );
}
