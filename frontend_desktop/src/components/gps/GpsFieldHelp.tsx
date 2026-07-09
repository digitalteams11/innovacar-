import { useState } from 'react';
import { CircleHelp, Info, MapPin, Lightbulb, ShieldAlert } from 'lucide-react';
import Modal from '../Modal';
import { GPS_FIELD_GUIDE, GPS_PROVIDER_GUIDE } from '../../data/gpsFieldGuide';

interface GpsFieldHelpProps {
  fieldKey: keyof typeof GPS_FIELD_GUIDE;
  provider?: string;
}

export default function GpsFieldHelp({ fieldKey, provider }: GpsFieldHelpProps) {
  const [open, setOpen] = useState(false);
  const entry = GPS_FIELD_GUIDE[fieldKey];
  if (!entry) return null;

  const providerNotes = provider ? GPS_PROVIDER_GUIDE[provider] : undefined;

  return (
    <>
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          setOpen(true);
        }}
        className="inline-flex items-center justify-center w-5 h-5 rounded-full text-slate-400 hover:text-brand-600 hover:bg-brand-50 transition-colors"
        aria-label={`Help: ${entry.title}`}
        title={entry.title}
      >
        <CircleHelp size={15} />
      </button>

      <Modal isOpen={open} onClose={() => setOpen(false)} title={entry.title} maxWidth="max-w-md">
        <div className="space-y-4">
          <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">{entry.text}</p>

          {entry.whereToFind && (
            <div className="flex items-start gap-2.5 bg-blue-50 dark:bg-blue-500/10 rounded-xl p-3">
              <MapPin size={16} className="text-blue-500 shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-blue-700 dark:text-blue-300">Where to find it</p>
                <p className="text-sm text-blue-600 dark:text-blue-300/90 mt-0.5">{entry.whereToFind}</p>
              </div>
            </div>
          )}

          {entry.example && (
            <div className="flex items-start gap-2.5 bg-slate-50 dark:bg-white/5 rounded-xl p-3">
              <Info size={16} className="text-slate-400 shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-slate-600 dark:text-slate-300">Example</p>
                <p className="text-sm font-mono text-slate-500 dark:text-slate-400 mt-0.5 break-all">{entry.example}</p>
              </div>
            </div>
          )}

          {entry.usage && (
            <div className="flex items-start gap-2.5 bg-emerald-50 dark:bg-emerald-500/10 rounded-xl p-3">
              <Lightbulb size={16} className="text-emerald-500 shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-emerald-700 dark:text-emerald-300">How RentCar uses this</p>
                <p className="text-sm text-emerald-600 dark:text-emerald-300/90 mt-0.5">{entry.usage}</p>
              </div>
            </div>
          )}

          {entry.security && (
            <div className="flex items-start gap-2.5 bg-rose-50 dark:bg-rose-500/10 rounded-xl p-3">
              <ShieldAlert size={16} className="text-rose-500 shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-rose-700 dark:text-rose-300">Security</p>
                <p className="text-sm text-rose-600 dark:text-rose-300/90 mt-0.5">{entry.security}</p>
              </div>
            </div>
          )}

          {providerNotes && (
            <div className="border-t border-[#e8e6e1]/60 dark:border-white/10 pt-3">
              <p className="text-xs font-semibold text-[#1e293b] dark:text-white mb-2">{providerNotes.label}-specific notes</p>
              <ul className="space-y-1.5">
                {providerNotes.notes.map((note, idx) => (
                  <li key={idx} className="text-sm text-slate-500 dark:text-slate-400 flex items-start gap-2">
                    <span className="text-brand-500 mt-0.5">•</span>
                    <span>{note}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </Modal>
    </>
  );
}
