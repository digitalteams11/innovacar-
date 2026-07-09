import { useMemo, useState } from 'react';
import { CheckCircle2, Circle, PlayCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import Modal from '../Modal';
import type { ModuleHelpContent } from '../../data/helpCenterContent';

interface VideoGuideModalProps {
  isOpen: boolean;
  onClose: () => void;
  content: ModuleHelpContent;
}

function isValidVideoUrl(url: string | null): url is string {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:';
  } catch {
    return false;
  }
}

export default function VideoGuideModal({ isOpen, onClose, content }: VideoGuideModalProps) {
  const { t } = useTranslation();
  const [stepIndex, setStepIndex] = useState(0);
  const hasVideo = useMemo(() => isValidVideoUrl(content.videoUrl), [content.videoUrl]);
  const videoGuideTitle = t('help.videoGuideTitle', '{{title}} Video Guide', { title: content.title });

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={videoGuideTitle} maxWidth="max-w-2xl">
      {hasVideo ? (
        <div className="overflow-hidden rounded-xl bg-black">
          <iframe
            src={content.videoUrl as string}
            title={videoGuideTitle}
            className="aspect-video w-full"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        </div>
      ) : (
        <div>
          <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-700">
            <PlayCircle className="mt-0.5 shrink-0" size={18} />
            <p>{t('help.videoNotAvailable', 'Video not available. Use the walkthrough guide below.')}</p>
          </div>

          <ol className="mt-5 space-y-3">
            {content.walkthrough.map((step, index) => {
              const done = index < stepIndex;
              const active = index === stepIndex;
              return (
                <li
                  key={index}
                  className={`flex items-start gap-3 rounded-lg border p-3 text-sm transition-colors ${
                    active ? 'border-brand-300 bg-brand-50/60' : 'border-slate-200'
                  }`}
                >
                  {done ? (
                    <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-500" size={18} />
                  ) : (
                    <Circle className={`mt-0.5 shrink-0 ${active ? 'text-brand-500' : 'text-slate-300'}`} size={18} />
                  )}
                  <span className={done ? 'text-slate-400 line-through' : 'text-[#1e293b]'}>
                    <strong className="me-1">{t('help.stepNumber', 'Step {{number}}.', { number: index + 1 })}</strong>
                    {step}
                  </span>
                </li>
              );
            })}
          </ol>

          <div className="mt-5 flex items-center justify-between">
            <button
              type="button"
              onClick={() => setStepIndex(index => Math.max(0, index - 1))}
              disabled={stepIndex === 0}
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 disabled:opacity-30"
            >
              {t('common.previous')}
            </button>
            {stepIndex < content.walkthrough.length ? (
              <button
                type="button"
                onClick={() => setStepIndex(index => Math.min(content.walkthrough.length, index + 1))}
                className="rounded-lg bg-brand-500 px-5 py-2 text-sm font-semibold text-white"
              >
                {stepIndex === 0
                  ? t('help.startWalkthrough', 'Start walkthrough')
                  : stepIndex === content.walkthrough.length - 1
                  ? t('help.finishWalkthrough', 'Finish walkthrough')
                  : t('help.nextStep', 'Next step')}
              </button>
            ) : (
              <span className="text-sm font-semibold text-emerald-600">{t('help.walkthroughComplete', 'Walkthrough complete')}</span>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
