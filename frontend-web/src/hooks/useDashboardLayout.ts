import { useState, useCallback } from 'react';
import { type WidgetConfig, DEFAULT_WIDGET_LAYOUT } from '../types/dashboard';

const KEY_PREFIX = 'rentcar_dashboard_layout_';

function loadLayout(userId: string): WidgetConfig[] {
  try {
    const raw = localStorage.getItem(`${KEY_PREFIX}${userId}`);
    if (!raw) return DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
    const stored: WidgetConfig[] = JSON.parse(raw);
    // Merge: keep stored items but add any new defaults
    const storedIds = new Set(stored.map(w => w.id));
    const merged = [...stored];
    DEFAULT_WIDGET_LAYOUT.forEach(d => {
      if (!storedIds.has(d.id)) merged.push({ ...d, order: merged.length });
    });
    return merged.sort((a, b) => a.order - b.order);
  } catch {
    return DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
  }
}

function saveLayout(userId: string, layout: WidgetConfig[]) {
  try {
    localStorage.setItem(`${KEY_PREFIX}${userId}`, JSON.stringify(layout));
  } catch { /* storage full */ }
}

export function useDashboardLayout(userId?: string | number) {
  const uid = String(userId ?? 'default');
  const [layout, setLayout] = useState<WidgetConfig[]>(() => loadLayout(uid));

  const commit = useCallback((next: WidgetConfig[]) => {
    const normalized = next.map((w, i) => ({ ...w, order: i }));
    setLayout(normalized);
    saveLayout(uid, normalized);
  }, [uid]);

  /** Toggle a widget's visibility. Pinned widgets cannot be hidden. */
  const toggle = useCallback((id: string) => {
    setLayout(prev => {
      const next = prev.map(w =>
        w.id === id && !w.pinned ? { ...w, visible: !w.visible } : w,
      );
      saveLayout(uid, next);
      return next;
    });
  }, [uid]);

  /** Replace the full ordered list (from drag-drop in the modal). */
  const reorder = useCallback((newOrdered: WidgetConfig[]) => {
    commit(newOrdered);
  }, [commit]);

  /** Restore factory defaults for this user. */
  const reset = useCallback(() => {
    const defaults = DEFAULT_WIDGET_LAYOUT.map(w => ({ ...w }));
    setLayout(defaults);
    localStorage.removeItem(`${KEY_PREFIX}${uid}`);
  }, [uid]);

  /** Whether a widget with this id should render at all. */
  const isVisible = useCallback((id: string): boolean => {
    const w = layout.find(x => x.id === id);
    return w?.visible !== false;
  }, [layout]);

  /** Sorted list of visible widget ids, in user-defined order. */
  const sortedVisibleIds = layout
    .filter(w => w.visible)
    .sort((a, b) => a.order - b.order)
    .map(w => w.id);

  return { layout, toggle, reorder, reset, isVisible, sortedVisibleIds };
}
