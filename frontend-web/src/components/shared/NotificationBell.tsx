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

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleClick = (n: AppNotification) => {
    if (!n.read) markAsRead(n.id);
    const url = n.actionUrl || (n.contractId ? `/contracts/${n.contractId}` : null);
    if (url) navigate(url);
    setOpen(false);
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
          className="absolute end-0 top-full z-50 mt-3 w-[calc(100vw-1.5rem)] max-w-96 overflow-hidden rounded-3xl animate-scale-in sm:w-96"
          style={{
            background: 'var(--glass-bg)',
            border: '1px solid var(--glass-border)',
            boxShadow: 'var(--shadow-elevated)',
            backdropFilter: 'blur(var(--glass-blur))',
            WebkitBackdropFilter: 'blur(var(--glass-blur))',
          }}
        >
          {/* Header */}
          <div
            className="flex items-center justify-between px-4 py-3 gap-2"
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

          {/* List */}
          <div className="max-h-[360px] overflow-y-auto">
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
                      className="flex-1 text-start px-4 py-3 transition-all"
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
                            className="text-xs font-semibold truncate"
                            style={{ color: !n.read ? 'var(--text-primary)' : 'var(--text-secondary)' }}
                          >
                            {n.title}
                          </p>
                          <p className="text-[11px] mt-0.5 line-clamp-2" style={{ color: 'var(--text-muted)' }}>
                            {n.message}
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

          {/* Footer */}
          {notifications.length > 0 && (
            <div
              className="px-4 py-2 flex items-center justify-between"
              style={{ borderTop: '1px solid var(--border-subtle)' }}
            >
              <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                {notifications.length} notification{notifications.length !== 1 ? 's' : ''}
              </span>
              {readCount > 0 && (
                <button
                  onClick={() => clearReadNotifications()}
                  className="text-[11px] transition-colors"
                  style={{ color: 'var(--text-muted)' }}
                  onMouseEnter={e => (e.currentTarget as HTMLElement).style.color = '#ef4444'}
                  onMouseLeave={e => (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'}
                >
                  Clear {readCount} read
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
