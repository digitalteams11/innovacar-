import { useEffect, useRef, useState } from 'react';
import { Search, X, Check, Building2, User as UserIcon, Mail, Loader2 } from 'lucide-react';
import { superAdminApi } from '../../api/superAdminApi';

export interface Recipient {
  email: string;
  sourceType: 'AGENCY' | 'USER' | 'EXTERNAL';
  sourceId: number | null;
  displayName: string;
}

interface DirectoryItem {
  id: number;
  type: 'AGENCY' | 'USER';
  displayName: string;
  email: string;
  role: string | null;
  agencyId: number | null;
  agencyName: string | null;
  status: string;
  verified: boolean;
  plan: string | null;
}

const QUICK_FILTERS: Array<{ key: string; label: string; type?: string; verified?: boolean }> = [
  { key: 'ALL', label: 'All' },
  { key: 'AGENCY', label: 'Agencies', type: 'AGENCY' },
  { key: 'USER', label: 'Users', type: 'USER' },
  { key: 'VERIFIED', label: 'Verified only', verified: true },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function RecipientSelector({
  selected, onChange, maxRecipients = 10,
}: {
  selected: Recipient[];
  onChange: (recipients: Recipient[]) => void;
  maxRecipients?: number;
}) {
  const [query, setQuery] = useState('');
  const [quickFilter, setQuickFilter] = useState('ALL');
  const [results, setResults] = useState<DirectoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => runSearch(query, quickFilter), 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, quickFilter]);

  useEffect(() => {
    const onOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onOutside);
    return () => document.removeEventListener('mousedown', onOutside);
  }, []);

  const runSearch = async (q: string, filter: string) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError(null);
    try {
      const active = QUICK_FILTERS.find(f => f.key === filter);
      const res = await superAdminApi.searchEmailRecipients({
        q: q.trim() || undefined,
        type: active?.type,
        verified: active?.verified,
        size: 20,
      }, controller.signal);
      setResults(Array.isArray(res.data?.items) ? res.data.items : []);
    } catch (err: any) {
      if (err?.name !== 'CanceledError' && err?.code !== 'ERR_CANCELED') {
        setError('Unable to load recipients');
        setResults([]);
      }
    } finally {
      setLoading(false);
    }
  };

  const isSelected = (email: string) => selected.some(r => r.email.toLowerCase() === email.toLowerCase());
  const atLimit = selected.length >= maxRecipients;

  const addRecipient = (r: Recipient) => {
    if (isSelected(r.email) || atLimit) return;
    onChange([...selected, r]);
  };

  const removeRecipient = (email: string) => {
    onChange(selected.filter(r => r.email.toLowerCase() !== email.toLowerCase()));
  };

  const toggleItem = (item: DirectoryItem) => {
    if (isSelected(item.email)) { removeRecipient(item.email); return; }
    addRecipient({
      email: item.email, sourceType: item.type, sourceId: item.id, displayName: item.displayName,
    });
  };

  const addExternal = () => {
    const email = query.trim();
    if (!EMAIL_RE.test(email)) { setError('Enter a valid email address'); return; }
    addRecipient({ email: email.toLowerCase(), sourceType: 'EXTERNAL', sourceId: null, displayName: email });
    setQuery('');
    setError(null);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && query.trim() && EMAIL_RE.test(query.trim())) {
      e.preventDefault();
      addExternal();
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const agencies = results.filter(r => r.type === 'AGENCY');
  const users = results.filter(r => r.type === 'USER');

  return (
    <div ref={containerRef} className="relative space-y-2">
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selected.map(r => (
            <span key={r.email} className="inline-flex items-center gap-1.5 bg-brand-50 dark:bg-brand-500/10 text-brand-700 dark:text-brand-300 text-xs font-medium ps-2.5 pe-1.5 py-1 rounded-full">
              {r.sourceType === 'AGENCY' ? <Building2 size={11} /> : r.sourceType === 'USER' ? <UserIcon size={11} /> : <Mail size={11} />}
              {r.displayName}
              <button type="button" onClick={() => removeRecipient(r.email)} className="hover:bg-brand-100 dark:hover:bg-brand-500/20 rounded-full p-0.5">
                <X size={11} />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="relative">
        <Search size={15} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          value={query}
          onChange={e => { setQuery(e.target.value); setOpen(true); setError(null); }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          disabled={atLimit}
          placeholder={atLimit ? `Maximum ${maxRecipients} recipients reached` : 'Search agencies or users, or type an email…'}
          className="w-full ps-9 pe-9 py-2.5 bg-slate-50 dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm text-[#1e293b] dark:text-white outline-none focus:border-brand-400 transition-colors disabled:opacity-50"
        />
        {loading && <Loader2 size={14} className="absolute end-3 top-1/2 -translate-y-1/2 text-slate-400 animate-spin" />}
      </div>

      {error && <p className="text-xs text-rose-500">{error}</p>}

      <div className="flex flex-wrap gap-1.5">
        {QUICK_FILTERS.map(f => (
          <button
            key={f.key}
            type="button"
            onClick={() => setQuickFilter(f.key)}
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
              quickFilter === f.key
                ? 'bg-brand-600 text-white'
                : 'bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {open && (
        <div className="absolute z-20 mt-1 w-full max-h-72 overflow-y-auto bg-white dark:bg-[#1a2234] border border-[#e8e6e1] dark:border-white/10 rounded-xl shadow-lg">
          {query.trim() && EMAIL_RE.test(query.trim()) && !isSelected(query.trim()) && (
            <button
              type="button"
              onClick={addExternal}
              className="w-full flex items-center gap-2 px-3 py-2 text-sm text-left hover:bg-slate-50 dark:hover:bg-white/5 border-b border-[#e8e6e1] dark:border-white/10"
            >
              <Mail size={14} className="text-slate-400" />
              Add external email <span className="font-medium">{query.trim()}</span>
            </button>
          )}

          {!loading && results.length === 0 && (
            <p className="px-3 py-4 text-sm text-slate-400 text-center">No recipients found</p>
          )}

          {agencies.length > 0 && (
            <RecipientGroup label="Agencies" items={agencies} isSelected={isSelected} onToggle={toggleItem} icon={<Building2 size={13} />} />
          )}
          {users.length > 0 && (
            <RecipientGroup label="Users" items={users} isSelected={isSelected} onToggle={toggleItem} icon={<UserIcon size={13} />} />
          )}
        </div>
      )}
    </div>
  );
}

function RecipientGroup({ label, items, isSelected, onToggle, icon }: {
  label: string; items: DirectoryItem[]; isSelected: (email: string) => boolean;
  onToggle: (item: DirectoryItem) => void; icon: React.ReactNode;
}) {
  return (
    <div>
      <p className="px-3 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      {items.map(item => {
        const selected = isSelected(item.email);
        return (
          <button
            key={`${item.type}-${item.id}`}
            type="button"
            onClick={() => onToggle(item)}
            className="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-left hover:bg-slate-50 dark:hover:bg-white/5"
          >
            <span className="text-slate-400 shrink-0">{icon}</span>
            <span className="flex-1 min-w-0">
              <span className="block truncate text-[#1e293b] dark:text-white">{item.displayName}</span>
              <span className="block truncate text-xs text-slate-400">{item.email}{item.agencyName ? ` · ${item.agencyName}` : ''}</span>
            </span>
            {selected && <Check size={15} className="text-brand-600 shrink-0" />}
          </button>
        );
      })}
    </div>
  );
}
