import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Info, CheckCircle2, Trash2, Eye, Bell
} from 'lucide-react';
import { useToast } from '../../context/ToastContext';

const typeIcons: Record<string, any> = {
  LOGIN: CheckCircle2,
  LOGOUT: Info,
  CREATE: CheckCircle2,
  UPDATE: Info,
  DELETE: Trash2,
  SETTINGS_CHANGE: Info,
};

const typeColors: Record<string, string> = {
  LOGIN: 'text-emerald-500 bg-emerald-50 dark:bg-emerald-500/10',
  LOGOUT: 'text-slate-500 bg-slate-50 dark:bg-slate-500/10',
  CREATE: 'text-blue-500 bg-blue-50 dark:bg-blue-500/10',
  UPDATE: 'text-amber-500 bg-amber-50 dark:bg-amber-500/10',
  DELETE: 'text-rose-500 bg-rose-50 dark:bg-rose-500/10',
  SETTINGS_CHANGE: 'text-purple-500 bg-purple-50 dark:bg-purple-500/10',
};

export default function SuperAdminNotifications() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [notifications, setNotifications] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getNotifications();
      setNotifications(res.data);
    } catch (err) {
      console.error(err);
      showToast('Failed to load notifications', 'error');
    } finally {
      setLoading(false);
    }
  };

  const markAsRead = async (id: number) => {
    try {
      await superAdminApi.markNotificationRead(id);
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
      showToast('Marked as read');
    } catch (err) {
      showToast('Failed to mark as read', 'error');
    }
  };

  const dismissAll = () => {
    setNotifications([]);
    showToast('All notifications dismissed');
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <div className="space-y-6 animate-fade">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">{t('superAdmin.notifications.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">{t('superAdmin.notifications.subtitle')}</p>
        </div>
        {unreadCount > 0 && (
          <button
            onClick={dismissAll}
            className="inline-flex items-center gap-2 bg-white dark:bg-[#1a2332]/70 hover:bg-slate-50 dark:hover:bg-white/5 text-[#1e293b] dark:text-white px-4 py-2.5 rounded-xl text-sm font-semibold border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft transition-colors"
          >
            <CheckCircle2 size={16} />
            Mark All Read
          </button>
        )}
      </div>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        <div className="px-4 sm:px-6 py-3 sm:py-4 border-b border-[#e8e6e1]/60 dark:border-white/5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Bell size={18} className="text-[#0a0f2c] dark:text-white" />
            <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{t('superAdmin.notifications.allNotifications')}</h3>
            {unreadCount > 0 && (
              <span className="px-2 py-0.5 rounded-full bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 text-xs font-bold">{unreadCount} new</span>
            )}
          </div>
          <span className="text-xs text-slate-400">{notifications.length} {t('superAdmin.notifications.total')}</span>
        </div>
        <div className="divide-y divide-[#e8e6e1]/40 dark:divide-white/5">
          {loading ? (
            <div className="text-center py-12 text-slate-400">{t('app.loading')}</div>
          ) : notifications.length === 0 ? (
            <div className="text-center py-12 text-slate-400">{t('superAdmin.notifications.noNotifications')}</div>
          ) : (
            notifications.map((n: any) => {
              const Icon = typeIcons[n.type] || Info;
              const isUnread = !n.read;
              return (
                <div key={n.id} className={`flex items-start gap-3 sm:gap-4 px-4 sm:px-6 py-3 sm:py-4 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors ${isUnread ? 'bg-blue-50/30 dark:bg-blue-500/5' : ''}`}>
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${typeColors[n.type] || 'text-slate-500 bg-slate-50 dark:bg-slate-500/10'}`}>
                    <Icon size={18} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{n.type}</p>
                      {isUnread && <span className="w-2 h-2 rounded-full bg-rose-500" />}
                      <span className="text-xs text-slate-400 ml-auto">
                        {n.timestamp ? new Date(n.timestamp).toLocaleString() : '-'}
                      </span>
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400">{n.message}</p>
                  </div>
                  {isUnread && (
                    <button
                      onClick={() => markAsRead(n.id)}
                      className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors shrink-0"
                      title="Mark as read"
                    >
                      <Eye size={16} />
                    </button>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
