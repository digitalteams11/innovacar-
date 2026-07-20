import { Sparkles, CircleHelp, LifeBuoy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import MobileBottomSheet from '../MobileBottomSheet';

interface AssistantActionMenuProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectAi: () => void;
  onSelectHelp: () => void;
  onSelectSupport: () => void;
}

/**
 * Mobile action menu opened by the single unified assistant FAB — replaces
 * having separate "Help" and "AI" floating buttons stacked on top of each
 * other. Each row is a full-width, min-44px-tall touch target with an
 * icon/title/subtitle, not a bare icon button.
 */
export default function AssistantActionMenu({ isOpen, onClose, onSelectAi, onSelectHelp, onSelectSupport }: AssistantActionMenuProps) {
  const { t } = useTranslation();

  const items = [
    {
      icon: Sparkles,
      title: t('assistant.aiAssistant'),
      subtitle: t('assistant.aiAssistantDesc'),
      onClick: onSelectAi,
    },
    {
      icon: CircleHelp,
      title: t('assistant.helpCenter'),
      subtitle: t('assistant.helpCenterDesc'),
      onClick: onSelectHelp,
    },
    {
      icon: LifeBuoy,
      title: t('assistant.contactSupport'),
      subtitle: t('assistant.contactSupportDesc'),
      onClick: onSelectSupport,
    },
  ];

  return (
    <MobileBottomSheet isOpen={isOpen} onClose={onClose} maxHeightClass="max-h-[70vh]" showDragHandle>
      <div className="space-y-1 overflow-y-auto p-2">
        {items.map((item) => (
          <button
            key={item.title}
            type="button"
            onClick={() => { item.onClick(); onClose(); }}
            className="flex min-h-11 w-full items-center gap-3 rounded-xl p-3 text-start transition-colors hover:bg-[var(--bg-hover)]"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[var(--bg-active)] text-[var(--brand-primary)]">
              <item.icon size={19} />
            </span>
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-[var(--text-primary)]">{item.title}</span>
              <span className="block truncate text-xs text-[var(--text-muted)]">{item.subtitle}</span>
            </span>
          </button>
        ))}
      </div>
    </MobileBottomSheet>
  );
}
