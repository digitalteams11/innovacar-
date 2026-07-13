import { useState, useRef, useEffect } from 'react';
import { Bell, Check, FileText, QrCode, Shield, CheckCircle2 } from 'lucide-react';
import { useNotifications } from '../../context/NotificationContext';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const typeIcons: Record<string, React.ReactNode> = {
  CONTRACT_SIGNED_AGENCY: <Shield size={14} className="text-brand-500" />,
  QR_GENERATED: <QrCode size={14} className="text-blue-500" />,
  CONTRACT_FULLY_SIGNED: <CheckCircle2 size={14} className="text-success-500" />,
  CONTRACT_CREATED: <FileText size={14} className="text-slate-400" />,
};

function getNotificationTypeKey(n: ReturnType<typeof useNotifications>['notifications'][0]): string | null {
  const type = (n.type || '').toUpperCase();
  const title = (n.title || '').trim().toLowerCase();

  if (type === 'PAYMENT_PARTIAL' || title === 'payment pending') return 'PAYMENT_PARTIAL';
  if (type === 'CONTRACT_FULLY_SIGNED' || title === 'contract fully signed') return 'CONTRACT_FULLY_SIGNED';
  if (type === 'QR_GENERATED' || type === 'CONTRACT_QR_GENERATED' || title === 'qr code generated') return 'QR_GENERATED';
  if (type === 'CONTRACT_SIGNED_AGENCY' || title === 'contract signed by agency') return 'CONTRACT_SIGNED_AGENCY';
  if (type === 'CONTRACT_CREATED' || title === 'contract created') return 'CONTRACT_CREATED';

  return null;
}

function extractContractNumber(message?: string): string | null {
  return message?.match(/\b(?:Contract|contract)\s+([A-Z]{2,}-\d{4}-\d{5})\b/)?.[1] || null;
}

function localizeNotification(n: ReturnType<typeof useNotifications>['notifications'][0], t: (k: string, opts?: Record<string, unknown>) => string) {
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

export default function NotificationBell() {
  const { notifications, unreadCount, markAsRead, markAllAsRead } = useNotifications();
  const { t } = useTranslation();
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

  const handleClick = (n: typeof notifications[0]) => {
    if (!n.read) markAsRead(n.id);
    if (n.contractId) {
      navigate(`/contracts/${n.contractId}`);
    }
    setOpen(false);
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="relative p-2.5 text-slate-400 hover:text-[#1e293b] hover:bg-slate-100 rounded-xl transition-all"
      >
        <Bell size={20} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -end-0.5 min-w-[18px] h-[18px] bg-danger-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center px-1">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute end-0 top-full mt-2 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 z-50 overflow-hidden animate-scale-in">
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
            <h3 className="text-sm font-bold text-[#1e293b]">{t('notifications.title')}</h3>
            {unreadCount > 0 && (
              <button
                onClick={() => markAllAsRead()}
                className="text-xs text-brand-500 font-medium hover:text-brand-600 flex items-center gap-1"
              >
                <Check size={12} /> {t('notifications.markAllRead')}
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <div className="p-6 text-center text-sm text-slate-400">
                <Bell size={24} className="mx-auto mb-2 text-slate-300" />
                {t('notifications.noNotifications')}
              </div>
            ) : (
              notifications.map((n) => {
                const display = localizeNotification(n, t);
                return (
                  <button
                    key={n.id}
                    onClick={() => handleClick(n)}
                    className={`w-full text-start px-4 py-3 border-b border-slate-50 last:border-0 hover:bg-slate-50 transition-all ${
                      !n.read ? 'bg-brand-50/30' : ''
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center shrink-0 mt-0.5">
                        {typeIcons[n.type] || <Bell size={14} className="text-slate-400" />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className={`text-xs font-semibold ${!n.read ? 'text-[#1e293b]' : 'text-slate-500'}`}>
                          {display.title}
                        </p>
                        <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-2">{display.message}</p>
                        <p className="text-[10px] text-slate-300 mt-1">
                          {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </p>
                      </div>
                      {!n.read && <div className="w-2 h-2 bg-brand-500 rounded-full shrink-0 mt-1.5" />}
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
