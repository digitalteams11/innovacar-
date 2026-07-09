import { useEffect, useState } from 'react';
import { Megaphone, X } from 'lucide-react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

interface PlatformAnnouncement {
  id: number;
  title: string;
  message: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
}

const priorityClasses: Record<string, string> = {
  LOW: 'bg-slate-50 border-slate-200 text-slate-700',
  NORMAL: 'bg-blue-50 border-blue-200 text-blue-800',
  HIGH: 'bg-amber-50 border-amber-200 text-amber-800',
  CRITICAL: 'bg-rose-50 border-rose-200 text-rose-800',
};

export default function AnnouncementBanner() {
  const { isAuthenticated } = useAuth();
  const [announcements, setAnnouncements] = useState<PlatformAnnouncement[]>([]);
  const [dismissed, setDismissed] = useState<number[]>(() => {
    try {
      return JSON.parse(sessionStorage.getItem('rentcar_dismissed_announcements') || '[]');
    } catch {
      return [];
    }
  });

  useEffect(() => {
    if (!isAuthenticated) return;
    api.get('/announcements/active')
      .then(({ data }) => setAnnouncements(data?.data || []))
      .catch(() => setAnnouncements([]));
  }, [isAuthenticated]);

  const dismiss = (id: number) => {
    const next = [...dismissed, id];
    setDismissed(next);
    sessionStorage.setItem('rentcar_dismissed_announcements', JSON.stringify(next));
  };

  const visible = announcements.filter((a) => !dismissed.includes(a.id));
  if (visible.length === 0) return null;

  return (
    <div className="space-y-2 mb-4">
      {visible.map((a) => (
        <div key={a.id} className={`flex items-start gap-3 p-3 rounded-xl border text-sm ${priorityClasses[a.priority] || priorityClasses.NORMAL}`}>
          <Megaphone size={16} className="mt-0.5 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="font-semibold">{a.title}</p>
            <p className="text-xs mt-0.5 opacity-90">{a.message}</p>
          </div>
          <button onClick={() => dismiss(a.id)} className="shrink-0 p-1 rounded-lg hover:bg-black/5">
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
