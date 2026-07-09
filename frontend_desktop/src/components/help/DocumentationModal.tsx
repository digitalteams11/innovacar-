import { useState } from 'react';
import { AlertTriangle, Check, Copy, Lightbulb, ListChecks } from 'lucide-react';
import Modal from '../Modal';
import type { ModuleHelpContent } from '../../data/helpCenterContent';

interface DocumentationModalProps {
  isOpen: boolean;
  onClose: () => void;
  content: ModuleHelpContent;
}

function buildPlainText(content: ModuleHelpContent): string {
  const lines = [`${content.title} Guide`, '', content.documentation.overview, ''];
  content.documentation.workflow.forEach((section, index) => {
    lines.push(`${index + 1}. ${section.heading}`);
    section.steps.forEach(step => lines.push(`   - ${step}`));
  });
  if (content.documentation.requiredFields.length) {
    lines.push('', 'Required fields:', ...content.documentation.requiredFields.map(field => `- ${field}`));
  }
  if (content.documentation.commonErrors.length) {
    lines.push('', 'Common errors:', ...content.documentation.commonErrors.map(error => `- ${error}`));
  }
  if (content.documentation.tips.length) {
    lines.push('', 'Tips:', ...content.documentation.tips.map(tip => `- ${tip}`));
  }
  return lines.join('\n');
}

export default function DocumentationModal({ isOpen, onClose, content }: DocumentationModalProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(buildPlainText(content));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`${content.title} Guide`} maxWidth="max-w-2xl">
      <div className="flex items-start justify-between gap-4">
        <p className="text-sm leading-6 text-slate-500">{content.documentation.overview}</p>
        <button
          type="button"
          onClick={handleCopy}
          className="flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:border-brand-300"
        >
          {copied ? <Check size={14} className="text-emerald-500" /> : <Copy size={14} />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      {content.documentation.keyActions.length > 0 && (
        <div className="mt-5">
          <h4 className="flex items-center gap-2 text-xs font-bold uppercase text-slate-400">
            <ListChecks size={14} /> Key actions
          </h4>
          <div className="mt-2 flex flex-wrap gap-2">
            {content.documentation.keyActions.map(action => (
              <span key={action} className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-600">
                {action}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="mt-6 space-y-5">
        {content.documentation.workflow.map((section, index) => (
          <div key={section.heading}>
            <h4 className="text-sm font-bold text-[#1e293b]">
              {index + 1}. {section.heading}
            </h4>
            <ul className="mt-2 space-y-1.5 ps-1">
              {section.steps.map((step, stepIndex) => (
                <li key={stepIndex} className="flex items-start gap-2 text-sm text-slate-600">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-400" />
                  {step}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      {content.documentation.requiredFields.length > 0 && (
        <div className="mt-6">
          <h4 className="text-xs font-bold uppercase text-slate-400">Required fields</h4>
          <div className="mt-2 flex flex-wrap gap-2">
            {content.documentation.requiredFields.map(field => (
              <span key={field} className="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600">
                {field}
              </span>
            ))}
          </div>
        </div>
      )}

      {content.documentation.commonErrors.length > 0 && (
        <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <h4 className="flex items-center gap-2 text-xs font-bold uppercase text-amber-700">
            <AlertTriangle size={14} /> Common errors
          </h4>
          <ul className="mt-2 space-y-1.5">
            {content.documentation.commonErrors.map((error, index) => (
              <li key={index} className="text-sm text-amber-800">{error}</li>
            ))}
          </ul>
        </div>
      )}

      {content.documentation.tips.length > 0 && (
        <div className="mt-4 rounded-xl border border-sky-200 bg-sky-50 p-4">
          <h4 className="flex items-center gap-2 text-xs font-bold uppercase text-sky-700">
            <Lightbulb size={14} /> Tips
          </h4>
          <ul className="mt-2 space-y-1.5">
            {content.documentation.tips.map((tip, index) => (
              <li key={index} className="text-sm text-sky-800">{tip}</li>
            ))}
          </ul>
        </div>
      )}
    </Modal>
  );
}
