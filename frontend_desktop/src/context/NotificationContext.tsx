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
  contractId?: number;
  tenantId: number;
  read: boolean;
  createdAt: string;
}

interface NotificationContextType {
  notifications: AppNotification[];
  unreadCount: number;
  markAsRead: (id: number) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  refresh: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const { playSound } = useNotificationSound();
  const { isAuthenticated } = useAuth();
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const isAuthenticatedRef = useRef(isAuthenticated);
  isAuthenticatedRef.current = isAuthenticated;

  const fetchUnread = useCallback(async () => {
    // Don't fetch notifications on public pages or when not authenticated
    const isPublicPage = window.location.hash.startsWith('#/contract-sign') || window.location.hash.startsWith('#/inspection');
    if (!isAuthenticated || isPublicPage) return;
    try {
      const { data } = await api.get('/notifications/unread');
      setNotifications(data);
    } catch {
      // silently fail
    }
  }, [isAuthenticated]);

  const connectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }

    // Re-check at connect time (not just at effect-setup time) so a timer
    // fired from a stale closure never reconnects after logout.
    if (!isAuthenticatedRef.current) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    const baseUrl = api.defaults.baseURL || '';
    // Native EventSource cannot set an Authorization header, so the access
    // token travels as a query param; the backend only accepts it on this
    // one SSE route.
    const sseUrl = `${baseUrl}/sse/subscribe?access_token=${encodeURIComponent(token)}`;

    let es: EventSource;
    try {
      es = new EventSource(sseUrl, { withCredentials: true });
    } catch {
      // Never let a browser-level SSE failure crash the app.
      return;
    }
    eventSourceRef.current = es;

    es.onopen = () => {
      reconnectAttemptsRef.current = 0;
    };

    es.addEventListener('notification', (event) => {
      try {
        const notification: AppNotification = JSON.parse(event.data);
        setNotifications((prev) => [notification, ...prev]);
        playSound(inferSoundEvent(notification));
      } catch {
        // Malformed event payload — ignore, never crash the UI for this.
      }
    });

    es.addEventListener('connected', () => {
      fetchUnread();
    });

    es.onerror = () => {
      es.close();
      if (eventSourceRef.current === es) eventSourceRef.current = null;
      if (!isAuthenticatedRef.current) return;

      // Exponential backoff (3s, 6s, 12s... capped at 30s) to avoid
      // hammering the backend with reconnect attempts/401s.
      const attempt = Math.min(reconnectAttemptsRef.current + 1, 5);
      reconnectAttemptsRef.current = attempt;
      const delay = Math.min(3000 * 2 ** (attempt - 1), 30000);

      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = setTimeout(() => {
        if (isAuthenticatedRef.current) connectSSE();
      }, delay);
    };
  }, [fetchUnread, playSound]);

  useEffect(() => {
    if (!isAuthenticated) {
      setNotifications([]);
      reconnectAttemptsRef.current = 0;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      return;
    }
    fetchUnread();
    connectSSE();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, [fetchUnread, connectSSE, isAuthenticated]);

  const markAsRead = useCallback(async (id: number) => {
    try {
      await api.patch(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch {
      // silently fail
    }
  }, []);

  const markAllAsRead = useCallback(async () => {
    try {
      await api.patch('/notifications/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch {
      // silently fail
    }
  }, []);

  const unreadCount = notifications.filter((n) => !n.read).length;

  return (
    <NotificationContext.Provider
      value={{ notifications, unreadCount, markAsRead, markAllAsRead, refresh: fetchUnread }}
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
