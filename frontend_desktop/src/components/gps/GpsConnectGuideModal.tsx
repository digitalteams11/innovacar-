import Modal from '../Modal';
import { GPS_CONNECT_STEPS, GPS_PROVIDER_GUIDE } from '../../data/gpsFieldGuide';

interface GpsConnectGuideModalProps {
  isOpen: boolean;
  onClose: () => void;
  provider?: string;
}

export default function GpsConnectGuideModal({ isOpen, onClose, provider }: GpsConnectGuideModalProps) {
  const providerGuide = provider ? GPS_PROVIDER_GUIDE[provider] : undefined;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="How to connect your GPS platform" maxWidth="max-w-lg">
      <div className="space-y-5">
        <ol className="space-y-3">
          {GPS_CONNECT_STEPS.map((step, idx) => (
            <li key={idx} className="flex items-start gap-3">
              <span className="flex items-center justify-center w-6 h-6 rounded-full bg-brand-50 text-brand-600 text-xs font-bold shrink-0 mt-0.5">
                {idx + 1}
              </span>
              <span className="text-sm text-slate-600 dark:text-slate-300 pt-0.5">{step}</span>
            </li>
          ))}
        </ol>

        {providerGuide ? (
          <div className="border-t border-[#e8e6e1]/60 dark:border-white/10 pt-4">
            <p className="text-sm font-semibold text-[#1e293b] dark:text-white mb-2">{providerGuide.label} notes</p>
            <ul className="space-y-1.5">
              {providerGuide.notes.map((note, idx) => (
                <li key={idx} className="text-sm text-slate-500 dark:text-slate-400 flex items-start gap-2">
                  <span className="text-brand-500 mt-0.5">•</span>
                  <span>{note}</span>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="border-t border-[#e8e6e1]/60 dark:border-white/10 pt-4">
            <p className="text-sm text-slate-400">Select a GPS provider below to see provider-specific instructions here.</p>
          </div>
        )}
      </div>
    </Modal>
  );
}
