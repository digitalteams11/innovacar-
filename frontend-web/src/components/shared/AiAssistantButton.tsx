import { useCallback, useState } from 'react';
import AiChatPanel from './AiChatPanel';
import CarThinkingAiIcon, { type AiIconState } from './CarThinkingAiIcon';

/**
 * Floating launcher for the AI assistant.
 * Shows in idle/disabled/error/thinking states â€” never fully hidden,
 * so Super Admin can always open it and navigate to settings.
 */
export default function AiAssistantButton({ module }: { module?: string }) {
  const status: 'idle' | 'disabled' = 'disabled';
  const [open, setOpen] = useState(false);
  const [thinking, setThinking] = useState(false);

  const handleThinkingChange = useCallback((v: boolean) => setThinking(v), []);

  const iconState: AiIconState = thinking ? 'thinking' : status === 'disabled' ? 'disabled' : 'idle';

  return (
    <>
      {/* Desktop only — mobile reaches the AI assistant through the unified
          MobileAssistantFab menu instead (this button previously sat at
          bottom-5/20px, entirely inside the bottom nav bar's 0-66px
          footprint, with a lower z-index than the nav — so the nav visually
          swallowed it on small screens). */}
      <button
        onClick={() => setOpen((c) => !c)}
        title="AI Assistant"
        aria-label="Open AI assistant"
        className={`fixed bottom-5 end-4 sm:end-6 z-40 hidden w-11 h-11 rounded-full shadow-lg items-center justify-center transition-transform hover:scale-105 lg:flex
          ${status === 'disabled'
            ? 'bg-slate-700/80 hover:bg-slate-700 cursor-pointer'
            : 'bg-[#0a0f2c] hover:bg-[#0a0f2c]/90'
          }`}
      >
        <CarThinkingAiIcon state={iconState} size={22} />
      </button>

      {open && (
        <AiChatPanel
          module={module}
          onClose={() => setOpen(false)}
          onThinkingChange={handleThinkingChange}
        />
      )}
    </>
  );
}
