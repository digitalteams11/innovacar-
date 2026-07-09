import { useState } from 'react';
import { motion, AnimatePresence, Reorder } from 'framer-motion';
import { X, GripVertical, Eye, EyeOff, RotateCcw, Check, LayoutDashboard, Pin } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { type WidgetConfig } from '../../types/dashboard';

interface Props {
  layout: WidgetConfig[];
  onReorder: (newLayout: WidgetConfig[]) => void;
  onReset: () => void;
  onClose: () => void;
}

export default function DashboardCustomizeModal({ layout, onReorder, onReset, onClose }: Props) {
  const { t } = useTranslation();
  const [local, setLocal] = useState<WidgetConfig[]>(() => [...layout]);
  const [saved, setSaved] = useState(false);

  const handleToggle = (id: string) => {
    setLocal(prev => prev.map(w => w.id === id && !w.pinned ? { ...w, visible: !w.visible } : w));
  };

  const handleSave = () => {
    onReorder(local);
    setSaved(true);
    setTimeout(() => { setSaved(false); onClose(); }, 700);
  };

  const handleReset = () => {
    onReset();
    onClose();
  };

  const visibleCount = local.filter(w => w.visible).length;

  return (
    <AnimatePresence>
      <motion.div
        key="backdrop"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 z-50 flex items-center justify-center p-4"
        style={{ backgroundColor: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(6px)' }}
        onClick={onClose}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.94, y: 24 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.94, y: 24 }}
          transition={{ type: 'spring', stiffness: 380, damping: 30 }}
          className="w-full max-w-md rounded-2xl overflow-hidden shadow-2xl"
          style={{
            backgroundColor: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            backdropFilter: 'blur(24px)',
          }}
          onClick={e => e.stopPropagation()}
        >
          {/* Header */}
          <div
            className="flex items-center justify-between px-5 py-4 border-b"
            style={{ borderColor: 'var(--border-subtle)' }}
          >
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl flex items-center justify-center bg-[var(--brand-primary)]/10">
                <LayoutDashboard size={16} className="text-[var(--brand-primary)]" />
              </div>
              <div>
                <h2 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                  {t('dashboard.customizeDashboard', 'Customize Dashboard')}
                </h2>
                <p className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                  {t('dashboard.widgetsVisible', '{{visible}} of {{total}} widgets visible', { visible: visibleCount, total: local.length })}
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-[var(--bg-hover)] transition-colors"
              style={{ color: 'var(--text-muted)' }}
            >
              <X size={16} />
            </button>
          </div>

          {/* Instructions */}
          <div className="px-5 pt-3 pb-1">
            <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
              {t('dashboard.dragToReorder', 'Drag to reorder · toggle eye to show/hide')}
            </p>
          </div>

          {/* Reorderable widget list */}
          <div className="px-5 py-2 max-h-[52vh] overflow-y-auto">
            <Reorder.Group
              axis="y"
              values={local}
              onReorder={setLocal}
              className="space-y-2 pb-2"
            >
              {local.map(widget => (
                <Reorder.Item
                  key={widget.id}
                  value={widget}
                  className="flex items-center gap-3 p-3 rounded-xl border select-none touch-none"
                  style={{
                    backgroundColor: widget.visible ? 'var(--bg-hover)' : 'transparent',
                    borderColor: widget.visible ? 'var(--border-subtle)' : 'transparent',
                    opacity: widget.visible ? 1 : 0.45,
                    cursor: widget.pinned ? 'default' : 'grab',
                  }}
                  whileDrag={{ scale: 1.02, boxShadow: '0 8px 32px rgba(0,0,0,0.18)', zIndex: 50 }}
                >
                  {/* Drag handle */}
                  {widget.pinned ? (
                    <Pin size={14} className="shrink-0" style={{ color: 'var(--brand-primary)' }} />
                  ) : (
                    <GripVertical size={16} className="shrink-0" style={{ color: 'var(--text-muted)' }} />
                  )}

                  {/* Label */}
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                      {widget.label}
                    </p>
                    <p className="text-[10px] mt-0.5 truncate" style={{ color: 'var(--text-muted)' }}>
                      {widget.description}
                    </p>
                  </div>

                  {/* Pinned badge or eye toggle */}
                  {widget.pinned ? (
                    <span
                      className="text-[9px] font-bold px-1.5 py-0.5 rounded-full shrink-0"
                      style={{ backgroundColor: 'var(--brand-primary)', color: '#fff', opacity: 0.8 }}
                    >
                      {t('dashboard.alwaysOn', 'Always on')}
                    </span>
                  ) : (
                    <button
                      onClick={() => handleToggle(widget.id)}
                      title={widget.visible ? t('dashboard.hideWidget', 'Hide widget') : t('dashboard.showWidget', 'Show widget')}
                      className="w-7 h-7 rounded-lg flex items-center justify-center hover:bg-[var(--bg-card)] transition-colors shrink-0"
                      style={{ color: widget.visible ? 'var(--brand-primary)' : 'var(--text-muted)' }}
                    >
                      {widget.visible ? <Eye size={14} /> : <EyeOff size={14} />}
                    </button>
                  )}
                </Reorder.Item>
              ))}
            </Reorder.Group>
          </div>

          {/* Footer */}
          <div
            className="flex items-center justify-between gap-3 px-5 py-4 border-t"
            style={{ borderColor: 'var(--border-subtle)' }}
          >
            <button
              onClick={handleReset}
              className="flex items-center gap-1.5 text-xs font-medium px-3 py-2 rounded-xl border transition-colors hover:bg-[var(--bg-hover)]"
              style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-muted)' }}
            >
              <RotateCcw size={12} />
              {t('dashboard.resetToDefault', 'Reset to Default')}
            </button>
            <button
              onClick={handleSave}
              className="flex items-center gap-1.5 text-xs font-semibold px-4 py-2 rounded-xl text-white transition-all"
              style={{
                backgroundColor: saved ? '#10b981' : 'var(--brand-primary)',
                minWidth: 100,
                justifyContent: 'center',
              }}
            >
              <Check size={13} />
              {saved ? t('dashboard.savedExclaim', 'Saved!') : t('dashboard.saveLayout', 'Save Layout')}
            </button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}
