import { useState, useRef, useEffect } from 'react';
import {
  Bell, Check, FileText, QrCode, Shield, CheckCircle2, CreditCard, Car,
  Wrench, MapPin, AlertTriangle, User, Settings, Trash2, X, RefreshCw,
  BellOff, BellRing,
} from 'lucide-react';
import { useNotifications } from '../../context/NotificationContext';
import type { AppNotification } from '../../context/NotificationContext';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useToast } from '../../context/ToastContext';

// ── Module icons ───────────────────────────────────────────────────────────────

function getModuleIcon(n: AppNotification): React.ReactNode {
  const type = n.type || '';
  const mod = n.module || '';

  if (type.startsWith('CONTRACT') || type === 'QR_GENERATED' || type === 'PDF_GENERATED'
    || type === 'CLIENT_OPENED_CONTRACT' || type === 'CLIENT_SIGNED_CONTRACT'
    || mod === 'CONTRACTS') {
    if (type.includes('SIGNED') || type.includes('FULLY')) {
      return <CheckCircle2 size={14} className="text-emerald-500" />;
    }
    if (type === 'QR_GENERATED' || type === 'CONTRACT_QR_GENERATED') {
      return <QrCode size={14} className="text-blue-500" />;
    }
    return <FileText size={14} style={{ color: 'var(--text-muted)' }} />;
  }

  if (type.startsWith('RESERVATION') || mod === 'RESERVATIONS') {
    return <Car size={14} className="text-indigo-400" />;
  }

  if (type.startsWith('MAINTENANCE') || type === 'VEHICLE_BLOCKED_BY_MAINTENANCE' || mod === 'MAINTENANCE') {
    return <Wrench size={14} className="text-orange-400" />;
  }

  if (type.startsWith('VEHICLE') || mod === 'VEHICLES') {
    return <Car size={14} className="text-blue-500" />;
  }

  if (type.startsWith('PAYMENT') || type.startsWith('INVOICE') || type.startsWith('CLIENT_BALANCE') || mod === 'PAYMENTS') {
    return <CreditCard size={14} className="text-emerald-500" />;
  }

  if (type.startsWith('GPS') || mod === 'GPS') {
    return <MapPin size={14} className="text-purple-400" />;
  }

  if (type.startsWith('TRIAL') || type.startsWith('SUBSCRIPTION') || type.startsWith('PLAN') || mod === 'SUBSCRIPTION') {
    return <Shield size={14} className="text-brand-500" />;
  }

  if (type.startsWith('EMPLOYEE') || mod === 'EMPLOYEES') {
    return <User size={14} className="text-blue-400" />;
  }

  if (type.startsWith('LOGIN') || type.startsWith('PASSWORD') || type.startsWith('TWO_FACTOR')
    || type.startsWith('EMAIL_VERIFIED') || type.startsWith('SUSPICIOUS') || mod === 'SECURITY') {
    return <Shield size={14} className="text-red-400" />;
  }

  if (type.startsWith('BACKUP') || type.startsWith('SMTP') || type.startsWith('AI_')
    || type.startsWith('FEATURE') || mod === 'SYSTEM') {
    return <Settings size={14} style={{ color: 'var(--text-muted)' }} />;
  }

  if (n.severity === 'ERROR' || n.severity === 'CRITICAL') {
    return <AlertTriangle size={14} className="text-red-400" />;
  }
  if (n.severity === 'WARNING') {
    return <AlertTriangle size={14} className="text-amber-400" />;
  }

  return <Bell size={14} style={{ color: 'var(--text-muted)' }} />;
}

// ── Severity colours ───────────────────────────────────────────────────────────

function getSeverityColors(severity: string) {
  switch (severity) {
    case 'SUCCESS':  return { bg: 'rgba(16,185,129,0.08)', dot: '#10b981', icon: 'rgba(16,185,129,0.15)' };
    case 'WARNING':  return { bg: 'rgba(245,158,11,0.08)', dot: '#f59e0b', icon: 'rgba(245,158,11,0.15)' };
    case 'ERROR':    return { bg: 'rgba(239,68,68,0.08)',  dot: '#ef4444', icon: 'rgba(239,68,68,0.15)' };
    case 'CRITICAL': return { bg: 'rgba(239,68,68,0.12)', dot: '#dc2626', icon: 'rgba(239,68,68,0.18)' };
    default:         return { bg: 'rgba(var(--brand-primary-rgb, 16,185,129), 0.06)', dot: 'var(--brand-primary)', icon: 'var(--bg-hover)' };
  }
}

// ── Time display ───────────────────────────────────────────────────────────────

function getRelativeTime(dateStr: string, t: (k: string, opts?: Record<string, unknown>) => string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return t('notifications.justNow');
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('notifications.hoursAgo', { count: hours });
  const days = Math.floor(hours / 24);
  if (days === 1) return t('notifications.yesterday');
  return t('notifications.daysAgo', { count: days, defaultValue: `${days} days ago` });
}

function getNotificationTypeKey(n: AppNotification): string | null {
  const type = (n.type || '').toUpperCase();
  const title = (n.title || '').trim().toLowerCase();

  if (type === 'PAYMENT_PARTIAL' || title === 'payment pending') return 'PAYMENT_PARTIAL';
  if (type === 'CONTRACT_FULLY_SIGNED' || title === 'contract fully signed') return 'CONTRACT_FULLY_SIGNED';
  if (type === 'QR_GENERATED' || type === 'CONTRACT_QR_GENERATED' || title === 'qr code generated') return 'QR_GENERATED';
  if (type === 'CONTRACT_SIGNED_AGENCY' || title === 'contract signed by agency') return 'CONTRACT_SIGNED_AGENCY';
  if (type === 'CONTRACT_CREATED' || title === 'contract created') return 'CONTRACT_CREATED';
  if (type === 'CLIENT_INFORMATION_SUBMITTED' || title === 'client information submitted') return 'CLIENT_INFORMATION_SUBMITTED';

  return null;
}

function extractContractNumber(message?: string): string | null {
  return message?.match(/\b(?:Contract|contract)\s+([A-Z]{2,}-\d{4}-\d{5})\b/)?.[1] || null;
}

// Resolves where a notification should navigate to, in priority order:
// 1. actionUrl set by the backend (always the exact request/entity URL).
// 2. type + entityId, for the one type this app currently deep-links by id.
// 3. contractId, for older notification shapes that only carried that field.
// 4. A safe list fallback for legacy CLIENT_INFORMATION_SUBMITTED rows saved
//    before actionUrl/entityId existed on this notification type — never
//    leaves the click doing nothing, never guesses a broken route.
// Returns '' (not null) when nothing usable is found, so callers can just
// check truthiness instead of a three-way null/undefined/empty check.
function resolveNotificationUrl(n: AppNotification): string {
  if (n.actionUrl) return n.actionUrl;
  if (n.type === 'CLIENT_INFORMATION_SUBMITTED' && n.entityId) {
    return `/client-information-requests?requestId=${n.entityId}`;
  }
  if (n.contractId) return `/contracts/${n.contractId}`;
  if ((n.title || '').trim().toLowerCase() === 'client information submitted') {
    return '/client-information-requests?status=SUBMITTED';
  }
  return '';
}

function localizeNotification(n: AppNotification, t: (k: string, opts?: Record<string, unknown>) => string) {
  const typeKey = getNotificationTypeKey(n);

  if (typeKey) {
    const contractNumber = extractContractNumber(n.message);
    return {
      title: t(`notifications.types.${typeKey}.title`, { defaultValue: n.title }),
      message: contractNumber
        ? t(`notifications.types.${typeKey}.message`, { contractNumber: `\u2068${contractNumber}\u2069`, defaultValue: n.message })
        : n.message,
    };
  }

  return { title: n.title, message: n.message };
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function NotificationBell() {
  const { t } = useTranslation();
  const {
    notifications,
    unreadCount,
    loading,
    browserPermission,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearReadNotifications,
    requestBrowserPermission,
  } = useNotifications();

  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const ref = useRef<HTMLDivElement>(null);
  const { showToast } = useToast();
  // Tracks notification ids with a click currently in flight, so a fast
  // double-click/double-tap can't fire markAsRead (and its API call) twice —
  // React state (n.read) only flips after the await below resolves, so
  // checking n.read alone isn't enough to block the second click.
  const pendingClicksRef = useRef<Set<number>>(new Set());

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleClick = async (n: AppNotification) => {
    if (pendingClicksRef.current.has(n.id)) return;
    pendingClicksRef.current.add(n.id);
    try {
      if (!n.read) await markAsRead(n.id);
      // Close the panel before navigating, not after — otherwise the
      // dropdown briefly re-renders on top of the destination page.
      setOpen(false);
      const url = resolveNotificationUrl(n);
      if (url) navigate(url);
    } catch {
      showToast(t('notifications.openError', 'Unable to open notification'), 'error');
    } finally {
      pendingClicksRef.current.delete(n.id);
    }
  };

  const readCount = notifications.filter((n) => n.read).length;

  return (
    <div ref={ref} className="relative">
      {/* Bell button */}
      <button
        onClick={() => setOpen(!open)}
        aria-label={t('notifications.title')}
        className="relative rounded-2xl border border-transparent p-2.5 transition-all hover:border-[var(--border-subtle)]"
        style={{ color: 'var(--text-muted)' }}
        onMouseEnter={e => {
          (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)';
          (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)';
        }}
        onMouseLeave={e => {
          (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
          (e.currentTarget as HTMLElement).style.background = 'transparent';
        }}
      >
        <Bell size={20} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -end-0.5 min-w-[18px] h-[18px] bg-danger-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center px-1">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          // Below `sm`, this is viewport-fixed (not anchored to the bell
          // button) — an `absolute end-0` panel sized near-100vw-wide
          // overflows past the *opposite* screen edge on narrow phones,
          // since its anchor point is wherever the bell sits in the header,
          // not the viewport edge. Fixed + inset-3 guarantees it always
          // fits within the real viewport regardless of header layout.
          // From `sm` up there's enough width for the original
          // anchor-relative dropdown.
          className="fixed inset-x-3 top-[calc(env(safe-area-inset-top)+4.5rem)] z-50 flex max-h-[calc(100dvh-6rem)] flex-col overflow-hidden rounded-3xl animate-scale-in sm:absolute sm:inset-x-auto sm:end-0 sm:top-full sm:mt-3 sm:w-96 sm:max-h-[520px]"
          style={{
            background: 'var(--glass-bg)',
            border: '1px solid var(--glass-border)',
            boxShadow: 'var(--shadow-elevated)',
            backdropFilter: 'blur(var(--glass-blur))',
            WebkitBackdropFilter: 'blur(var(--glass-blur))',
          }}
        >
          {/* Header — shrink-0 so it never gets squeezed by the scrolling list below it */}
          <div
            className="flex shrink-0 items-center justify-between px-4 py-3 gap-2"
            style={{ borderBottom: '1px solid var(--border-subtle)' }}
          >
            <h3 className="text-sm font-bold shrink-0" style={{ color: 'var(--text-primary)' }}>
              {t('notifications.title')}
              {unreadCount > 0 && (
                <span className="ms-2 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-danger-500 text-white">
                  {unreadCount}
                </span>
              )}
            </h3>
            <div className="flex items-center gap-2">
              {/* Browser notification toggle */}
              {browserPermission !== 'unsupported' && browserPermission !== 'granted' && (
                <button
                  onClick={requestBrowserPermission}
                  title={t('notifications.enablePush')}
                  className="p-1 rounded-lg transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ color: 'var(--text-muted)' }}
                >
                  <BellRing size={13} />
                </button>
              )}
              {browserPermission === 'granted' && (
                <span title={t('notifications.pushEnabled')} style={{ color: 'var(--text-muted)' }}>
                  <BellRing size={13} className="text-emerald-500" />
                </span>
              )}
              {/* Clear read */}
              {readCount > 0 && (
                <button
                  onClick={() => clearReadNotifications()}
                  title={t('notifications.clearReadCount', { count: readCount })}
                  className="p-1 rounded-lg transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ color: 'var(--text-muted)' }}
                >
                  <Trash2 size={13} />
                </button>
              )}
              {/* Mark all read */}
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllAsRead()}
                  className="text-[11px] font-medium flex items-center gap-1 transition-colors"
                  style={{ color: 'var(--brand-primary)' }}
                >
                  <Check size={11} /> {t('notifications.markAllRead')}
                </button>
              )}
            </div>
          </div>

          {/* List — the only scrolling region; min-h-0 lets it actually
              shrink inside the flex column instead of overflowing the
              panel's own max-height on short screens. */}
          <div className="min-h-0 flex-1 overflow-y-auto sm:max-h-[360px] sm:flex-none">
            {loading && notifications.length === 0 ? (
              <div className="p-6 text-center flex items-center justify-center gap-2" style={{ color: 'var(--text-muted)' }}>
                <RefreshCw size={14} className="animate-spin" />
                <span className="text-sm">{t('common.loading')}</span>
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-6 text-center">
                <BellOff size={24} className="mx-auto mb-2" style={{ color: 'var(--text-muted)' }} />
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                  {t('notifications.noNotifications')}
                </p>
              </div>
            ) : (
              notifications.map((n) => {
                const colors = getSeverityColors(n.severity || 'INFO');
                const display = localizeNotification(n, t);
                return (
                  <div
                    key={n.id}
                    className="group flex items-start gap-0 w-full"
                    style={{
                      background: !n.read ? colors.bg : 'transparent',
                      borderBottom: '1px solid var(--border-subtle)',
                    }}
                  >
                    <button
                      onClick={() => handleClick(n)}
                      aria-label={`${display.title}${!n.read ? ` (${t('notifications.unread')})` : ''}`}
                      className="flex-1 text-start px-4 py-3 transition-all cursor-pointer"
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}
                    >
                      <div className="flex items-start gap-3">
                        <div
                          className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl"
                          style={{ background: !n.read ? colors.icon : 'var(--bg-hover)', border: '1px solid var(--border-subtle)' }}
                        >
                          {getModuleIcon(n)}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p
                            className="text-xs font-semibold line-clamp-2 break-words"
                            style={{ color: !n.read ? 'var(--text-primary)' : 'var(--text-secondary)' }}
                          >
                            {display.title}
                          </p>
                          <p className="text-[11px] mt-0.5 line-clamp-2" style={{ color: 'var(--text-muted)' }}>
                            {display.message}
                          </p>
                          <p className="text-[10px] mt-1" style={{ color: 'var(--text-muted)' }}>
                            {getRelativeTime(n.createdAt, t)}
                          </p>
                        </div>
                        {!n.read && (
                          <div
                            className="w-2 h-2 rounded-full shrink-0 mt-1.5"
                            style={{ background: colors.dot }}
                          />
                        )}
                      </div>
                    </button>
                    {/* Delete button — appears on hover */}
                    <button
                      onClick={() => deleteNotification(n.id)}
                      title={t('notifications.deleteNotification')}
                      className="opacity-0 group-hover:opacity-100 transition-opacity p-3 self-stretch flex items-center"
                      style={{ color: 'var(--text-muted)' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#ef4444'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'}
                    >
                      <X size={12} />
                    </button>
                  </div>
                );
              })
            )}
          </div>

          {/* Footer — shrink-0, same reason as the header */}
          {notifications.length > 0 && (
            <div
              className="shrink-0 px-4 py-2 flex items-center justify-between"
              style={{ borderTop: '1px solid var(--border-subtle)' }}
            >
              <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                {t('notifications.countLabel', { count: notifications.length })}
              </span>
              {readCount > 0 && (
                <button
                  onClick={() => clearReadNotifications()}
                  className="text-[11px] transition-colors"
                  style={{ color: 'var(--text-muted)' }}
                  onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#ef4444'}
                  onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'}
                >
                  {t('notifications.clearReadCount', { count: readCount })}
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
