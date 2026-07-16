import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import api from '../api/axios';
import { inferSoundEvent, useNotificationSound } from './NotificationSoundContext';
import { useAuth } from './AuthContext';

export interface AppNotification {
  id: number;
  title: string;
  message: string;
  type: string;
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR' | 'CRITICAL';
  module: string;
  entityType: string;
  entityId: number | null;
  actionUrl: string;
  contractId?: number | null;
  tenantId: number;
  read: boolean;
  createdAt: string;
}

interface NotificationContextType {
  notifications: AppNotification[];
  unreadCount: number;
  loading: boolean;
  browserPermission: NotificationPermission | 'unsupported';
  markAsRead: (id: number) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  deleteNotification: (id: number) => Promise<void>;
  clearReadNotifications: () => Promise<void>;
  requestBrowserPermission: () => Promise<void>;
  refresh: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

const APP_TITLE = 'RentCar';
const POLL_INTERVAL_MS = 30_000;
// At capped 30s backoff intervals, this is ~10 minutes of continuous failure
// before giving up on the real-time channel entirely (the 30s notifications
// poll keeps working regardless — this only stops the SSE reconnect loop).
const MAX_CONSECUTIVE_SSE_FAILURES = 20;

function showBrowserNotification(n: AppNotification) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  const notif = new window.Notification(n.title, {
    body: n.message,
    icon: '/icons/icon-192.png',
    tag: `rentcar-${n.id}`,
    silent: false,
  });
  notif.onclick = () => {
    window.focus();
    if (n.actionUrl) {
      window.location.hash = `#${n.actionUrl}`;
    } else if (n.contractId) {
      window.location.hash = `#/contracts/${n.contractId}`;
    }
  };
}

function updateDocumentTitle(unreadCount: number) {
  const base = APP_TITLE;
  document.title = unreadCount > 0 ? `(${unreadCount}) ${base}` : base;
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [browserPermission, setBrowserPermission] = useState<NotificationPermission | 'unsupported'>(
    'Notification' in window ? Notification.permission : 'unsupported'
  );
  const { playSound } = useNotificationSound();
  const { isAuthenticated } = useAuth();
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const consecutiveFailuresRef = useRef(0);
  const isAuthenticatedRef = useRef(isAuthenticated);
  const knownIdsRef = useRef<Set<number>>(new Set());
  isAuthenticatedRef.current = isAuthenticated;

  const isPublicPage = () =>
    window.location.hash.startsWith('#/contract-sign') ||
    window.location.hash.startsWith('#/inspection');

  const fetchNotifications = useCallback(async () => {
    if (!isAuthenticated || isPublicPage()) return;
    try {
      setLoading(true);
      const { data } = await api.get('/notifications?limit=50');
      const items: AppNotification[] = data?.data?.items ?? data ?? [];
      setNotifications(items);
      knownIdsRef.current = new Set(items.map((n: AppNotification) => n.id));
    } catch {
      // silently fail — dashboard must not crash
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  // Legacy unread fetch (used on SSE connected event)
  const fetchUnread = useCallback(async () => {
    if (!isAuthenticated || isPublicPage()) return;
    try {
      const { data } = await api.get('/notifications/unread');
      const items: AppNotification[] = Array.isArray(data) ? data : [];
      setNotifications((prev) => {
        const existingIds = new Set(prev.map((n) => n.id));
        const newItems = items.filter((n) => !existingIds.has(n.id));
        if (newItems.length === 0) return prev;
        const merged = [...newItems, ...prev];
        knownIdsRef.current = new Set(merged.map((n) => n.id));
        return merged;
      });
    } catch {
      // silently fail
    }
  }, [isAuthenticated]);

  const connectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (!isAuthenticatedRef.current) return;
    const token = localStorage.getItem('token');
    if (!token) return;

    const baseUrl = api.defaults.baseURL || '';
    const sseUrl = `${baseUrl}/sse/subscribe?access_token=${encodeURIComponent(token)}`;

    let es: EventSource;
    try {
      es = new EventSource(sseUrl, { withCredentials: true });
    } catch {
      return;
    }
    eventSourceRef.current = es;

    es.onopen = () => {
      reconnectAttemptsRef.current = 0;
      consecutiveFailuresRef.current = 0;
    };

    es.addEventListener('notification', (event) => {
      try {
        const n: AppNotification = JSON.parse(event.data);
        const isNew = !knownIdsRef.current.has(n.id);
        if (isNew) {
          knownIdsRef.current.add(n.id);
          setNotifications((prev) => [n, ...prev]);
          playSound(inferSoundEvent(n));
          showBrowserNotification(n);
          // Forward contract-related events so pages can auto-refresh
          if (n.contractId && n.type?.includes('CONTRACT')) {
            window.dispatchEvent(new CustomEvent('contract:updated', {
              detail: {
                contractId: n.contractId,
                type: n.type,
                contractNumber: '',
                clientName: '',
              },
            }));
          }
        }
      } catch {
        // ignore malformed payload
      }
    });

    es.addEventListener('contract_event', (event) => {
      try {
        const detail = JSON.parse(event.data);
        window.dispatchEvent(new CustomEvent('contract:updated', { detail }));
      } catch {
        // ignore malformed payload
      }
    });

    es.addEventListener('connected', () => {
      fetchUnread();
    });

    es.onerror = () => {
      es.close();
      if (eventSourceRef.current === es) eventSourceRef.current = null;
      if (!isAuthenticatedRef.current) return;

      // EventSource's API never exposes the HTTP status code that caused the
      // error (a browser limitation, not something this code can work around),
      // so a permanently-invalid session (401/403) looks identical to a
      // transient network blip. A hard ceiling on consecutive failed attempts
      // is the practical substitute for "stop reconnecting on 401/403": once
      // reconnection has failed this many times in a row, the session is
      // treated as genuinely dead rather than retried forever at 30s
      // intervals. The 30s notifications poll (startPolling) keeps working
      // regardless, so this only silently drops the *real-time* channel.
      consecutiveFailuresRef.current += 1;
      if (consecutiveFailuresRef.current >= MAX_CONSECUTIVE_SSE_FAILURES) return;

      const attempt = Math.min(reconnectAttemptsRef.current + 1, 5);
      reconnectAttemptsRef.current = attempt;
      const delay = Math.min(3000 * 2 ** (attempt - 1), 30000);

      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = setTimeout(() => {
        if (isAuthenticatedRef.current) connectSSE();
      }, delay);
    };
  }, [fetchUnread, playSound]);

  // Polling for unread count — catches events that SSE may miss
  const startPolling = useCallback(() => {
    if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    pollIntervalRef.current = setInterval(async () => {
      if (!isAuthenticatedRef.current || document.hidden) return;
      try {
        const { data } = await api.get('/notifications/unread-count');
        const serverCount = data?.data?.count ?? data?.count ?? 0;
        const localCount = notifications.filter((n) => !n.read).length;
        // If server has more unread than we know about, re-fetch
        if (serverCount > localCount) {
          fetchNotifications();
        }
      } catch {
        // ignore
      }
    }, POLL_INTERVAL_MS);
  }, [fetchNotifications, notifications]);

  useEffect(() => {
    if (!isAuthenticated) {
      setNotifications([]);
      knownIdsRef.current = new Set();
      reconnectAttemptsRef.current = 0;
      consecutiveFailuresRef.current = 0;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      updateDocumentTitle(0);
      return;
    }
    fetchNotifications();
    connectSSE();
    startPolling();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }
    };
  }, [isAuthenticated]); // eslint-disable-line react-hooks/exhaustive-deps

  // Update browser tab title whenever unread count changes
  const unreadCount = notifications.filter((n) => !n.read).length;
  useEffect(() => {
    updateDocumentTitle(unreadCount);
  }, [unreadCount]);

  // ── Actions ──────────────────────────────────────────────────────────────────

  const markAsRead = useCallback(async (id: number) => {
    try {
      await api.patch(`/notifications/${id}/read`);
      setNotifications((prev) => prev.map((n) => n.id === id ? { ...n, read: true } : n));
    } catch {
      // silently fail
    }
  }, []);

  const markAllAsRead = useCallback(async () => {
    try {
      await api.patch('/notifications/mark-all-read');
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch {
      // Fallback to legacy endpoint
      try {
        await api.patch('/notifications/read-all');
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      } catch {
        // silently fail
      }
    }
  }, []);

  const deleteNotification = useCallback(async (id: number) => {
    try {
      await api.delete(`/notifications/${id}`);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      knownIdsRef.current.delete(id);
    } catch {
      // silently fail
    }
  }, []);

  const clearReadNotifications = useCallback(async () => {
    try {
      await api.delete('/notifications/clear-read');
      setNotifications((prev) => prev.filter((n) => !n.read));
    } catch {
      // silently fail
    }
  }, []);

  const requestBrowserPermission = useCallback(async () => {
    if (!('Notification' in window)) {
      setBrowserPermission('unsupported');
      return;
    }
    const permission = await window.Notification.requestPermission();
    setBrowserPermission(permission);
  }, []);

  const refresh = useCallback(async () => {
    await fetchNotifications();
  }, [fetchNotifications]);

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        unreadCount,
        loading,
        browserPermission,
        markAsRead,
        markAllAsRead,
        deleteNotification,
        clearReadNotifications,
        requestBrowserPermission,
        refresh,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export const useNotifications = () => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within NotificationProvider');
  }
  return context;
};
