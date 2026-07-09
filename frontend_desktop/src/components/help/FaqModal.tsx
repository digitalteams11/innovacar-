import { useMemo, useState } from 'react';
import { ChevronDown, Search } from 'lucide-react';
import Modal from '../Modal';
import type { ModuleHelpContent } from '../../data/helpCenterContent';

interface FaqModalProps {
  isOpen: boolean;
  onClose: () => void;
  content: ModuleHelpContent;
}

export default function FaqModal({ isOpen, onClose, content }: FaqModalProps) {
  const [query, setQuery] = useState('');
  const [openIndex, setOpenIndex] = useState<number | null>(0);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return content.faq;
    return content.faq.filter(
      entry => entry.q.toLowerCase().includes(normalized) || entry.a.toLowerCase().includes(normalized),
    );
  }, [content.faq, query]);

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`${content.title} FAQ`} maxWidth="max-w-xl">
      <div className="relative">
        <Search className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
        <input
          value={query}
          onChange={event => { setQuery(event.target.value); setOpenIndex(0); }}
          placeholder="Search frequently asked questions..."
          className="w-full rounded-lg border border-slate-200 py-2.5 ps-9 pe-3 text-sm focus:border-brand-300 focus:outline-none"
        />
      </div>

      <div className="mt-4 space-y-2">
        {filtered.length === 0 && (
          <p className="py-6 text-center text-sm text-slate-400">No questions match "{query}".</p>
        )}
        {filtered.map((entry, index) => {
          const open = openIndex === index;
          return (
            <div key={entry.q} className="overflow-hidden rounded-lg border border-slate-200">
              <button
                type="button"
                onClick={() => setOpenIndex(open ? null : index)}
                className="flex w-full items-center justify-between gap-3 p-3.5 text-start text-sm font-semibold text-[#1e293b] hover:bg-slate-50"
              >
                {entry.q}
                <ChevronDown size={16} className={`shrink-0 text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`} />
              </button>
              {open && <p className="border-t border-slate-100 p-3.5 text-sm leading-6 text-slate-600">{entry.a}</p>}
            </div>
          );
        })}
      </div>
    </Modal>
  );
}
