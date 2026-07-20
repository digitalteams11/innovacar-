import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import AssistantActionMenu from './AssistantActionMenu';
import AiAssistantSheet from './AiAssistantSheet';

const FIRST_USE_KEY = 'innovacar_assistant_fab_seen';

/**
 * The one unified mobile floating assistant button — replaces the previous
 * pair of independently-positioned "Help" (GuidanceSystem) and "AI"
 * (AiAssistantButton) floating buttons, which visually overlapped each
 * other and the bottom navigation bar on small screens (the AI button sat
 * at bottom-5/20px, entirely inside the bottom nav's 0–66px footprint, with
 * a lower z-index than the nav — the nav painted over it).
 *
 * Rendered once in the authenticated app shell (Layout.tsx), mobile-only
 * (lg:hidden — desktop keeps the separate Help/AI buttons). Positioned
 * relative to --mobile-nav-height + the safe-area inset, the same pattern
 * MobileBottomSheet already uses, so it always clears the bottom nav
 * regardless of device/safe-area size.
 */
export default function MobileAssistantFab() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [showFirstUseLabel, setShowFirstUseLabel] = useState(
    () => typeof window !== 'undefined' && !localStorage.getItem(FIRST_USE_KEY),
  );

  useEffect(() => {
    if (!showFirstUseLabel) return;
    const timer = window.setTimeout(() => setShowFirstUseLabel(false), 4000);
    return () => window.clearTimeout(timer);
  }, [showFirstUseLabel]);

  const dismissFirstUseLabel = () => {
    if (!showFirstUseLabel) return;
    localStorage.setItem(FIRST_USE_KEY, '1');
    setShowFirstUseLabel(false);
  };

  const openMenu = () => {
    dismissFirstUseLabel();
    setMenuOpen(true);
  };

  return (
    <>
      <div
        className="fixed z-[55] end-4 lg:hidden"
        style={{ bottom: 'calc(var(--mobile-nav-height, 66px) + env(safe-area-inset-bottom) + 16px)' }}
      >
        {showFirstUseLabel && (
          <div className="motion-safe:animate-fade mb-2 flex justify-end">
            <span className="rounded-full bg-[var(--bg-card-solid)] px-3 py-1.5 text-xs font-medium text-[var(--text-primary)] shadow-elevated border border-[var(--border-subtle)]">
              {t('assistant.firstUseLabel')}
            </span>
          </div>
        )}
        <button
          type="button"
          onClick={openMenu}
          aria-label={t('assistant.openMenu')}
          aria-expanded={menuOpen}
          aria-controls="mobile-assistant-menu"
          className="flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-[#0f766e] to-[#10b981] text-white shadow-elevated transition-transform motion-safe:hover:scale-105 motion-safe:active:scale-95"
        >
          <Sparkles size={24} />
        </button>
      </div>

      <div id="mobile-assistant-menu">
        <AssistantActionMenu
          isOpen={menuOpen}
          onClose={() => setMenuOpen(false)}
          onSelectAi={() => setAiOpen(true)}
          onSelectHelp={() => window.dispatchEvent(new CustomEvent('open-help-center'))}
          onSelectSupport={() => navigate('/support')}
        />
      </div>

      <AiAssistantSheet isOpen={aiOpen} onClose={() => setAiOpen(false)} />
    </>
  );
}
