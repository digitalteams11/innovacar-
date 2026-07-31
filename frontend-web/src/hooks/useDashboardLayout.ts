import { useState, useCallback, useEffect } from 'react';
import { type WidgetConfig, DEFAULT_WIDGET_LAYOUT } from '../types/dashboard';
import api from '../api/axios';
import { usePermissions } from '../context/PermissionContext';
import { useFeatureAccess } from '../context/FeatureAccessContext';

const KEY_PREFIX = 'rentcar_dashboard_layout_';

function mergeWithDefaults(stored: WidgetConfig[]): WidgetConfig[] {
  const storedIds = new Set(stored.map(w => w.id));
  const merged = [...stored];
  DEFAULT_WIDGET_LAYOUT.forEach(d => {
    if (!storedIds.has(d.id)) merged.push({ ...d, order: merged.length });
  });
  return merged.sort((a, b) => a.order - b.order);
}

function loadLocalLayout(userId: string): WidgetConfig[] {
  try {
    const raw = localStorage.getItem(`${KEY_PREFIX}${userId}`);
    if (!raw) return DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
    return mergeWithDefaults(JSON.parse(raw));
  } catch {
    return DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
  }
}

function saveLocalLayout(userId: string, layout: WidgetConfig[]) {
  try {
    localStorage.setItem(`${KEY_PREFIX}${userId}`, JSON.stringify(layout));
  } catch { /* storage full */ }
}

/**
 * Dashboard widget customization — persisted server-side (spec section 16:
 * "Do not rely only on localStorage") so it survives a refresh, a fresh
 * login, another browser, or another device. localStorage is kept only as
 * an instant-paint cache: the layout renders immediately from it on mount,
 * then is reconciled with `GET /api/dashboard/layout` (the real source of
 * truth) a moment later. Every save writes to both.
 */
export function useDashboardLayout(userId?: string | number) {
  const uid = String(userId ?? 'default');
  const [layout, setLayout] = useState<WidgetConfig[]>(() => loadLocalLayout(uid));
  const { hasPermission } = usePermissions();
  const { hasFeature } = useFeatureAccess();

  /** A widget is allowed if it has no gate, or its gate is satisfied — role/pack visibility (spec sections 17-18). */
  const isAllowed = useCallback((w: WidgetConfig): boolean => {
    if (w.requiredPermission && !hasPermission(w.requiredPermission)) return false;
    if (w.requiredFeature && !hasFeature(w.requiredFeature)) return false;
    return true;
  }, [hasPermission, hasFeature]);

  useEffect(() => {
    api.get('/dashboard/layout')
      .then(({ data }) => {
        const widgetsJson = data?.data?.widgetsJson;
        if (!widgetsJson) return;
        const parsed = JSON.parse(widgetsJson) as WidgetConfig[];
        const merged = mergeWithDefaults(parsed);
        setLayout(merged);
        saveLocalLayout(uid, merged);
      })
      .catch(() => { /* offline/failed — the localStorage-seeded layout already rendered */ });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid]);

  const persist = useCallback((next: WidgetConfig[]) => {
    saveLocalLayout(uid, next);
    api.put('/dashboard/layout', { widgets: next }).catch(() => { /* best-effort; localStorage still has it */ });
  }, [uid]);

  const commit = useCallback((next: WidgetConfig[]) => {
    const normalized = next.map((w, i) => ({ ...w, order: i }));
    setLayout(normalized);
    persist(normalized);
  }, [persist]);

  /** Toggle a widget's visibility. Pinned widgets cannot be hidden. */
  const toggle = useCallback((id: string) => {
    setLayout(prev => {
      const next = prev.map(w =>
        w.id === id && !w.pinned ? { ...w, visible: !w.visible } : w,
      );
      persist(next);
      return next;
    });
  }, [persist]);

  /** Replace the full ordered list (from drag-drop in the modal). */
  const reorder = useCallback((newOrdered: WidgetConfig[]) => {
    commit(newOrdered);
  }, [commit]);

  /** Restore factory defaults for this user. */
  const reset = useCallback(() => {
    const defaults = DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
    setLayout(defaults);
    localStorage.removeItem(`${KEY_PREFIX}${uid}`);
    api.put('/dashboard/layout', { widgets: defaults }).catch(() => { /* best-effort */ });
  }, [uid]);

  /** Whether a widget with this id should render at all — user preference AND role/pack gate. */
  const isVisible = useCallback((id: string): boolean => {
    const w = layout.find(x => x.id === id);
    if (!w) return false;
    return w.visible !== false && isAllowed(w);
  }, [layout, isAllowed]);

  /** Sorted list of visible, role/pack-allowed widget ids, in user-defined order. */
  const sortedVisibleIds = layout
    .filter(w => w.visible && isAllowed(w))
    .sort((a, b) => a.order - b.order)
    .map(w => w.id);

  return { layout, toggle, reorder, reset, isVisible, sortedVisibleIds };
}
