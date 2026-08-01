import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Megaphone, X } from 'lucide-react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

interface PlatformAnnouncement {
  id: number;
  title: string;
  message: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
  type: 'GENERIC' | 'DESKTOP_AVAILABLE' | 'NEW_MAJOR_DESKTOP_VERSION' | 'DESKTOP_SECURITY_UPDATE' | 'DESKTOP_MAINTENANCE' | 'DESKTOP_COMING_SOON';
  platform: 'WINDOWS' | 'MAC' | 'LINUX' | null;
  dismissible: boolean;
  actionUrl: string | null;
}

const priorityClasses: Record<string, string> = {
  LOW: 'bg-slate-50 border-slate-200 text-slate-700 dark:bg-slate-800/60 dark:border-slate-700 dark:text-slate-200',
  NORMAL: 'bg-blue-50 border-blue-200 text-blue-800 dark:bg-blue-900/30 dark:border-blue-800 dark:text-blue-200',
  HIGH: 'bg-amber-50 border-amber-200 text-amber-800 dark:bg-amber-900/30 dark:border-amber-800 dark:text-amber-200',
  CRITICAL: 'bg-rose-50 border-rose-200 text-rose-800 dark:bg-rose-900/30 dark:border-rose-800 dark:text-rose-200',
};

const DESKTOP_TYPES = new Set([
  'DESKTOP_AVAILABLE', 'NEW_MAJOR_DESKTOP_VERSION', 'DESKTOP_SECURITY_UPDATE',
  'DESKTOP_MAINTENANCE', 'DESKTOP_COMING_SOON',
]);

// A desktop-promotion banner is only relevant after the user has had a few
// sessions to get oriented — never on their very first login/registration.
const MIN_SESSIONS_BEFORE_DESKTOP_PROMO = 3;
const SESSION_COUNT_KEY = 'innovacar_session_count';
const SESSION_COUNTED_FLAG = 'innovacar_session_counted';

function isWindowsDesktopBrowser(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  const isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(ua);
  return !isMobile && /Windows/i.test(ua);
}

function bumpAndGetSessionCount(): number {
  try {
    if (!sessionStorage.getItem(SESSION_COUNTED_FLAG)) {
      const next = (Number(localStorage.getItem(SESSION_COUNT_KEY)) || 0) + 1;
      localStorage.setItem(SESSION_COUNT_KEY, String(next));
      sessionStorage.setItem(SESSION_COUNTED_FLAG, '1');
      return next;
    }
    return Number(localStorage.getItem(SESSION_COUNT_KEY)) || 0;
  } catch {
    return MIN_SESSIONS_BEFORE_DESKTOP_PROMO; // storage unavailable — don't block the banner forever
  }
}

export default function AnnouncementBanner() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [announcements, setAnnouncements] = useState<PlatformAnnouncement[]>([]);
  const [locallyDismissed, setLocallyDismissed] = useState<number[]>([]);
  const isWindows = isWindowsDesktopBrowser();
  const sessionCount = bumpAndGetSessionCount();

  useEffect(() => {
    if (!isAuthenticated) return;
    api.get('/announcements/active', { params: isWindows ? { platform: 'WINDOWS' } : undefined })
      .then(({ data }) => setAnnouncements(data?.data || []))
      .catch(() => setAnnouncements([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  const dismiss = (id: number) => {
    setLocallyDismissed((prev) => [...prev, id]);
    api.post(`/announcements/${id}/dismiss`).catch(() => {
      // Best-effort: even if the network call fails, the local hide above
      // keeps this session's UX correct; it will simply reappear next
      // session rather than staying dismissed forever.
    });
  };

  const visible = announcements.filter((a) => {
    if (locallyDismissed.includes(a.id)) return false;
    if (DESKTOP_TYPES.has(a.type)) {
      if (!isWindows) return false; // never show desktop promotion on non-Windows/mobile browsers
      if (sessionCount < MIN_SESSIONS_BEFORE_DESKTOP_PROMO) return false;
    }
    return true;
  });
  if (visible.length === 0) return null;

  return (
    <div className="space-y-2 mb-4">
      {visible.map((a) => (
        <div
          key={a.id}
          role="status"
          className={`flex items-start gap-3 p-3 rounded-xl border text-sm motion-reduce:transition-none ${priorityClasses[a.priority] || priorityClasses.NORMAL}`}
        >
          <Megaphone size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
          <div className="flex-1 min-w-0">
            <p className="font-semibold">{a.title}</p>
            <p className="text-xs mt-0.5 opacity-90">{a.message}</p>
            {a.actionUrl && (
              <button
                type="button"
                onClick={() => navigate(a.actionUrl!)}
                className="mt-2 text-xs font-semibold underline underline-offset-2 hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current rounded"
              >
                {t('desktop.announcement.learnMore', 'Learn more')}
              </button>
            )}
          </div>
          {a.dismissible && (
            <button
              onClick={() => dismiss(a.id)}
              aria-label={t('desktop.announcement.notNow', 'Not now')}
              className="shrink-0 p-1 rounded-lg hover:bg-black/5 dark:hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current"
            >
              <X size={14} />
            </button>
          )}
        </div>
      ))}
    </div>
  );
}
