import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Loader2, Search, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useGlobalSearch } from '../../hooks/useGlobalSearch';
import { usePermissions } from '../../context/PermissionContext';
import { useToast } from '../../context/ToastContext';
import type { GlobalSearchResult, SearchResultType } from '../../types/globalSearch';
import { SearchSection } from './SearchSection';

const sectionLabels: Record<SearchResultType, string> = {
  ACTION: 'Quick Actions',
  VEHICLE: 'Vehicles',
  CLIENT: 'Clients',
  RESERVATION: 'Reservations',
  CONTRACT: 'Contracts',
  EMPLOYEE: 'Employees',
  PAYMENT: 'Payments',
};

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const { showToast } = useToast();
  const { results, recentResults, loading, error, rememberResult } = useGlobalSearch(query, open);

  const quickActions = useMemo<GlobalSearchResult[]>(() => {
    const actions: GlobalSearchResult[] = [];
    if (hasPermission('CREATE_VEHICLE')) actions.push(action('action-add-vehicle', 'Add vehicle', 'Create a new fleet vehicle', '/vehicles', 'car'));
    if (hasPermission('CREATE_RESERVATION')) actions.push(action('action-create-reservation', 'Create reservation', 'Open reservations to schedule a booking', '/reservations', 'calendar'));
    if (hasPermission('CREATE_CONTRACT')) actions.push(action('action-create-contract', 'Create contract', 'Open contracts and start a rental contract', '/contracts', 'file-text'));
    if (hasPermission('CREATE_CLIENT')) actions.push(action('action-add-client', 'Add client', 'Register a new customer profile', '/clients', 'user'));
    if (hasPermission('EMPLOYEE_CREATE')) actions.push(action('action-add-employee', 'Add employee', 'Invite an employee to this agency', '/employees', 'users'));
    if (hasPermission('GPS_ACCESS')) actions.push(action('action-open-gps', 'Open GPS tracking', 'Track connected vehicles', '/gps-tracking', 'gps'));
    actions.push(action('action-open-billing', 'Open billing', 'Review plan, usage, and invoices', '/settings?tab=billing', 'credit-card'));
    return actions;
  }, [hasPermission]);

  const visibleItems = useMemo(() => {
    if (query.trim().length >= 2) return results;
    return [...quickActions, ...recentResults];
  }, [query, quickActions, recentResults, results]);

  const grouped = useMemo(() => {
    const groups = new Map<SearchResultType, GlobalSearchResult[]>();
    visibleItems.forEach((item) => {
      groups.set(item.type, [...(groups.get(item.type) || []), item]);
    });
    return groups;
  }, [visibleItems]);

  const activeItem = visibleItems[activeIndex];

  useEffect(() => {
    if (!open) return;
    setQuery('');
    setActiveIndex(0);
    window.setTimeout(() => inputRef.current?.focus(), 30);
  }, [open]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query, results.length]);

  const selectResult = useCallback((result: GlobalSearchResult) => {
    rememberResult(result);
    onClose();
    navigate(result.route);
  }, [navigate, onClose, rememberResult]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      } else if (event.key === 'ArrowDown') {
        event.preventDefault();
        setActiveIndex((index) => Math.min(index + 1, Math.max(visibleItems.length - 1, 0)));
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        setActiveIndex((index) => Math.max(index - 1, 0));
      } else if (event.key === 'Enter' && activeItem) {
        event.preventDefault();
        selectResult(activeItem);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeItem, onClose, open, selectResult, visibleItems.length]);

  useEffect(() => {
    if (error && /session expired/i.test(error)) {
      showToast(error, 'error');
      onClose();
    }
  }, [error, onClose, showToast]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[150] flex items-start justify-center bg-black/45 px-2 py-3 backdrop-blur-sm sm:px-4 sm:py-[8vh]" role="dialog" aria-modal="true">
      <button className="absolute inset-0 cursor-default" onClick={onClose} aria-label="Close search" />
      <section className="relative flex h-[calc(100vh-1.5rem)] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-[var(--glass-border)] bg-[var(--glass-bg)] shadow-2xl backdrop-blur-2xl sm:h-auto sm:max-h-[78vh]">
        <header className="flex items-center gap-3 border-b border-[var(--border-subtle)] px-4 py-3">
          <Search size={18} className="text-[var(--text-muted)]" />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search vehicles, clients, reservations, contracts..."
            className="h-10 flex-1 bg-transparent text-base font-semibold text-[var(--text-primary)] outline-none placeholder:text-[var(--text-muted)]"
          />
          {loading && <Loader2 size={18} className="animate-spin text-[var(--brand-primary)]" />}
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
            aria-label="Close search"
          >
            <X size={18} />
          </button>
        </header>

        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-3 sm:p-4">
          {error && !/session expired/i.test(error) && (
            <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm font-semibold text-rose-600">
              {error}
            </div>
          )}

          {!error && query.trim().length > 0 && query.trim().length < 2 && (
            <p className="px-2 text-sm text-[var(--text-muted)]">Type at least 2 characters to search real data.</p>
          )}

          {!loading && !error && query.trim().length >= 2 && results.length === 0 && (
            <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] px-4 py-8 text-center">
              <p className="text-sm font-bold text-[var(--text-primary)]">No matching records found.</p>
              <p className="mt-1 text-xs text-[var(--text-muted)]">Try a plate number, phone, contract number, or client name.</p>
            </div>
          )}

          {!error && query.trim().length < 2 && recentResults.length > 0 && (
            <SearchSection title="Recent" results={recentResults} activeId={activeItem?.id} onSelect={selectResult} />
          )}

          {!error && Array.from(grouped.entries()).map(([type, items]) => (
            <SearchSection
              key={type}
              title={sectionLabels[type] || type}
              results={items}
              activeId={activeItem?.id}
              onSelect={selectResult}
            />
          ))}

          {!error && query.trim().length < 2 && visibleItems.length === 0 && (
            <div className="rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] px-4 py-8 text-center">
              <p className="text-sm font-bold text-[var(--text-primary)]">Start with a quick action or search your agency data.</p>
            </div>
          )}
        </div>

        <footer className="hidden items-center gap-3 border-t border-[var(--border-subtle)] px-4 py-2 text-[11px] text-[var(--text-muted)] sm:flex">
          <span>↑↓ Navigate</span>
          <span>Enter Open</span>
          <span>Esc Close</span>
        </footer>
      </section>
    </div>
  );
}

function action(id: string, title: string, subtitle: string, route: string, icon: string): GlobalSearchResult {
  return { id, type: 'ACTION', title, subtitle, route, icon, score: 100 };
}
