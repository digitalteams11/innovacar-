import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface DesktopReleaseInfo {
  version: string;
  releaseNotes?: { en?: string[]; fr?: string[]; ar?: string[] };
  fileSizeBytes?: number;
}

interface UpdaterBridge {
  getAppVersion: () => Promise<string>;
  check: (channel?: string) => Promise<{ available: boolean; release?: DesktopReleaseInfo }>;
  download: () => Promise<{ filePath: string }>;
  install: () => Promise<void>;
  dismiss: (version: string) => Promise<boolean>;
  onEvent: (callback: (payload: { type: string; [key: string]: unknown }) => void) => () => void;
}

type UpdateState =
  | { phase: 'idle' }
  | { phase: 'available'; release: DesktopReleaseInfo }
  | { phase: 'downloading'; release: DesktopReleaseInfo; percent: number | null }
  | { phase: 'downloaded'; release: DesktopReleaseInfo }
  | { phase: 'error'; message: string };

function getUpdaterBridge(): UpdaterBridge | null {
  const bridge = (window as unknown as { electronAPI?: { updater?: UpdaterBridge } }).electronAPI;
  return bridge?.updater ?? null;
}

/**
 * Desktop-only UI — deliberately not part of the shared frontend-web tree
 * (see main.tsx's comment on why everything else is shared). Renders nothing
 * when not running inside Electron, and nothing until the main process
 * actually reports an available/downloaded update.
 */
export default function UpdateNotifier() {
  const { t, i18n } = useTranslation();
  const [state, setState] = useState<UpdateState>({ phase: 'idle' });
  const updater = getUpdaterBridge();

  useEffect(() => {
    if (!updater) return;
    return updater.onEvent((payload) => {
      switch (payload.type) {
        case 'available':
          setState({ phase: 'available', release: payload.release as DesktopReleaseInfo });
          break;
        case 'progress': {
          const percent = (payload.percent as number | null) ?? null;
          setState((prev) =>
            prev.phase === 'downloading' || prev.phase === 'available'
              ? { phase: 'downloading', release: (prev as { release: DesktopReleaseInfo }).release, percent }
              : prev,
          );
          break;
        }
        case 'downloaded':
          setState({ phase: 'downloaded', release: payload.release as DesktopReleaseInfo });
          break;
        case 'error':
          setState({ phase: 'error', message: String(payload.message || 'Update failed') });
          break;
        default:
          break;
      }
    });
  }, [updater]);

  const handleDownload = useCallback(() => {
    if (!updater || state.phase !== 'available') return;
    setState({ phase: 'downloading', release: state.release, percent: null });
    updater.download().catch((err) => {
      setState({ phase: 'error', message: String(err?.message || 'Download failed') });
    });
  }, [updater, state]);

  const handleRestart = useCallback(() => {
    if (!updater) return;
    updater.install().catch((err) => {
      setState({ phase: 'error', message: String(err?.message || 'Install failed') });
    });
  }, [updater]);

  const handleDismiss = useCallback(() => {
    if (!updater) {
      setState({ phase: 'idle' });
      return;
    }
    const version = 'release' in state ? state.release.version : undefined;
    setState({ phase: 'idle' });
    if (version) updater.dismiss(version).catch(() => {});
  }, [updater, state]);

  if (!updater || state.phase === 'idle') return null;

  const lang = i18n.language?.startsWith('fr') ? 'fr' : i18n.language?.startsWith('ar') ? 'ar' : 'en';

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 20,
        insetInlineEnd: 20,
        zIndex: 9999,
        width: 340,
        borderRadius: 12,
        boxShadow: '0 12px 32px rgba(0,0,0,0.35)',
        background: 'var(--card-bg, #101826)',
        color: 'var(--card-fg, #f4f6fb)',
        border: '1px solid rgba(255,255,255,0.08)',
        padding: 16,
        fontSize: 13,
      }}
      role="status"
    >
      {state.phase === 'available' && (
        <>
          <strong>{t('desktop.update.availableTitle')}</strong>
          <p style={{ margin: '6px 0 10px' }}>
            {t('desktop.update.availableBody', { version: state.release.version })}
          </p>
          {state.release.releaseNotes?.[lang] && state.release.releaseNotes[lang]!.length > 0 && (
            <ul style={{ margin: '0 0 10px', paddingInlineStart: 18, opacity: 0.85 }}>
              {state.release.releaseNotes[lang]!.slice(0, 3).map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={handleDownload} style={primaryBtn}>
              {t('desktop.update.download')}
            </button>
            <button onClick={handleDismiss} style={secondaryBtn}>
              {t('desktop.update.later')}
            </button>
          </div>
        </>
      )}
      {state.phase === 'downloading' && (
        <>
          <strong>{t('desktop.update.downloading')}</strong>
          <div style={{ marginTop: 10, height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.15)' }}>
            <div
              style={{
                height: '100%',
                borderRadius: 3,
                background: '#4f7cff',
                width: state.percent != null ? `${state.percent}%` : '30%',
                transition: 'width 0.2s',
              }}
            />
          </div>
        </>
      )}
      {state.phase === 'downloaded' && (
        <>
          <strong>{t('desktop.update.downloadedTitle')}</strong>
          <p style={{ margin: '6px 0 10px' }}>
            {t('desktop.update.downloadedBody', { version: state.release.version })}
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={handleRestart} style={primaryBtn}>
              {t('desktop.update.restartNow')}
            </button>
            <button onClick={handleDismiss} style={secondaryBtn}>
              {t('desktop.update.later')}
            </button>
          </div>
        </>
      )}
      {state.phase === 'error' && (
        <>
          <strong>{t('desktop.update.errorTitle')}</strong>
          <p style={{ margin: '6px 0 10px', opacity: 0.85 }}>{state.message}</p>
          <button onClick={handleDismiss} style={secondaryBtn}>
            {t('desktop.update.dismiss')}
          </button>
        </>
      )}
    </div>
  );
}

const primaryBtn: React.CSSProperties = {
  flex: 1,
  padding: '8px 12px',
  borderRadius: 8,
  border: 'none',
  background: '#4f7cff',
  color: '#fff',
  cursor: 'pointer',
  fontWeight: 600,
};

const secondaryBtn: React.CSSProperties = {
  flex: 1,
  padding: '8px 12px',
  borderRadius: 8,
  border: '1px solid rgba(255,255,255,0.2)',
  background: 'transparent',
  color: 'inherit',
  cursor: 'pointer',
};
