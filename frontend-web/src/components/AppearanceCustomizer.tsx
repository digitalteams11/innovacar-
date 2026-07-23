import {
  Check, Moon, Palette, RotateCcw, SlidersHorizontal, Sun, Monitor, Layers,
} from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme, presetCatalog } from '../context/ThemeContext';
import type {
  ButtonStyle, CardDensity, SidebarStyle, ThemeMode, ThemePreset,
} from '../context/ThemeContext';
import { cn } from '../lib/utils';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';

const modes: Array<{ value: ThemeMode; labelKey: string; label: string; icon: typeof Sun }> = [
  { value: 'light', labelKey: 'appearance.modeLight', label: 'Light', icon: Sun },
  { value: 'dark', labelKey: 'appearance.modeDark', label: 'Dark', icon: Moon },
  { value: 'system', labelKey: 'appearance.modeAuto', label: 'System', icon: Monitor },
];

const presetOrder: ThemePreset[] = [
  'neo-emerald',
  'carbon-lime',
  'titanium-2026',
  'luxury-black-gold',
  'ocean-blue-pro',
  'moroccan-sand',
  'purple-slate',
  'clean-white-pro',
];

export default function AppearanceCustomizer() {
  const {
    appearance, resolvedTheme, setTheme, updateAppearance, applyPreset, resetAppearance,
  } = useTheme();
  const { showToast } = useToast();
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);

  const saveAppearance = async () => {
    setSaving(true);
    try {
      // `mode` is a personal preference saved separately via
      // /api/users/me/preferences (see setTheme() in ThemeContext) — it must
      // never be pushed into the shared, tenant-wide appearance blob, which
      // is what this button saves.
      const { mode: _mode, ...appearanceForTenant } = appearance;
      const { data } = await api.put('/tenant-settings', { appearance: appearanceForTenant });
      showToast(data?.message || t('appearance.savedSuccess', 'Appearance settings saved successfully.'), 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || t('appearance.saveFailed', 'Unable to save appearance settings. Please try again.'), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-[var(--border-subtle)]">
        <div className="flex items-center gap-3">
          <span className="w-10 h-10 rounded-lg bg-[var(--bg-active)] flex items-center justify-center text-[var(--brand-primary)]">
            <Palette size={19} />
          </span>
          <div>
            <h3 className="text-base font-semibold text-[var(--text-primary)]">{t('appearance.title', 'Appearance Studio')}</h3>
            <p className="text-xs text-[var(--text-muted)]">{t('appearance.subtitle', 'Changes preview instantly; click Save to persist them.')}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={resetAppearance} className="surface-control h-9 px-3 inline-flex items-center justify-center gap-2 text-xs font-medium">
            <RotateCcw size={14} />
            {t('appearance.reset', 'Reset')}
          </button>
          <button onClick={saveAppearance} disabled={saving} className="premium-action h-9 px-3 inline-flex items-center justify-center gap-2 text-xs font-medium disabled:opacity-60">
            {saving ? <span className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" /> : <Check size={14} />}
            {saving ? t('common.saving') : t('appearance.saveAppearance', 'Save Appearance')}
          </button>
        </div>
      </header>

      <section>
        <SettingLabel
          title={t('appearance.displayMode', 'Display mode')}
          description={appearance.mode === 'system'
            ? t('appearance.followingDeviceMode', 'Following your device appearance — currently {{mode}}.', { mode: resolvedTheme === 'dark' ? t('appearance.modeDark', 'Dark') : t('appearance.modeLight', 'Light') })
            : t('appearance.currentlyUsingMode', 'Currently using {{mode}} mode', { mode: resolvedTheme })}
        />
        <div className="mt-3 grid grid-cols-3 gap-2">
          {modes.map((mode) => (
            <button
              key={mode.value}
              onClick={() => setTheme(mode.value)}
              className={cn(
                'relative min-h-20 rounded-lg border flex flex-col items-center justify-center gap-2 text-xs font-semibold transition-colors',
                appearance.mode === mode.value
                  ? 'border-[var(--brand-primary)] bg-[var(--bg-active)] text-[var(--text-primary)]'
                  : 'border-[var(--border-subtle)] bg-[var(--bg-card)] text-[var(--text-secondary)] hover:border-[var(--border-medium)]',
              )}
            >
              <mode.icon size={19} />
              {t(mode.labelKey, mode.label)}
              {appearance.mode === mode.value && <Check size={12} className="absolute top-2 end-2 text-[var(--brand-primary)]" />}
            </button>
          ))}
        </div>
      </section>

      <section>
        <SettingLabel title={t('appearance.themePreset', 'Theme preset')} description={t('appearance.themePresetDesc', 'Premium 2026 SaaS palettes — each one ships with a matching dark mode')} />
        <div className="mt-3 grid grid-cols-2 lg:grid-cols-4 gap-2.5">
          {presetOrder.map((key) => {
            const definition = presetCatalog[key];
            const swatch = definition[resolvedTheme];
            const selected = appearance.preset === key;
            return (
              <button
                key={key}
                onClick={() => applyPreset(key)}
                className={cn(
                  'relative min-h-[108px] rounded-lg border p-3 text-left transition-colors',
                  selected
                    ? 'border-[var(--brand-primary)] bg-[var(--bg-active)]'
                    : 'border-[var(--border-subtle)] bg-[var(--bg-card)] hover:border-[var(--border-medium)]',
                )}
              >
                <span className="flex h-7 overflow-hidden rounded-md border border-black/10">
                  <span className="flex-[2]" style={{ background: swatch.sidebar }} />
                  <span className="flex-1" style={{ background: definition.primaryColor }} />
                  <span className="flex-1" style={{ background: definition.secondaryColor }} />
                  <span className="flex-1" style={{ background: definition.accentColor }} />
                </span>
                <span className="block mt-2 text-xs font-semibold text-[var(--text-primary)]">{t(`appearance.themes.${key}.label`, definition.label)}</span>
                <span className="block mt-0.5 text-[10px] leading-snug text-[var(--text-muted)] line-clamp-2">{t(`appearance.themes.${key}.description`, definition.description)}</span>
                {selected && <Check size={13} className="absolute top-2 end-2 text-[var(--brand-primary)]" />}
              </button>
            );
          })}
        </div>
      </section>

      <section>
        <SettingLabel title={t('appearance.brandColors', 'Brand colors')} description={t('appearance.brandColorsDesc', 'Tune the active preset to match your agency identity')} />
        <div className="mt-3 grid sm:grid-cols-2 lg:grid-cols-5 gap-3">
          <ColorControl label={t('appearance.colorPrimary', 'Primary')} value={appearance.primaryColor} onChange={(primaryColor) => updateAppearance({ primaryColor })} />
          <ColorControl label={t('appearance.colorSecondary', 'Secondary')} value={appearance.secondaryColor} onChange={(secondaryColor) => updateAppearance({ secondaryColor })} />
          <ColorControl label={t('appearance.colorAccent', 'Accent')} value={appearance.accentColor} onChange={(accentColor) => updateAppearance({ accentColor })} />
          <ColorControl label={t('appearance.colorSidebar', 'Sidebar')} value={appearance.sidebarColor} onChange={(sidebarColor) => updateAppearance({ sidebarColor })} />
          <ColorControl label={t('appearance.colorSurface', 'Surface')} value={appearance.surfaceColor} onChange={(surfaceColor) => updateAppearance({ surfaceColor, glassColor: surfaceColor })} />
        </div>
      </section>

      <section>
        <SettingLabel title={t('appearance.glassRendering', 'Glass rendering')} description={t('appearance.glassRenderingDesc', 'Tune transparency, reflections, depth, and motion')} icon={SlidersHorizontal} />
        <div className="mt-4 grid md:grid-cols-2 gap-x-8 gap-y-5">
          <RangeControl label={t('appearance.glassIntensity', 'Glass intensity')} value={appearance.glassIntensity} unit="%" onChange={(glassIntensity) => updateAppearance({ glassIntensity })} />
          <RangeControl label={t('appearance.opacity', 'Opacity')} value={appearance.opacity} unit="%" onChange={(opacity) => updateAppearance({ opacity })} />
          <RangeControl label={t('appearance.blur', 'Blur')} value={appearance.blur} min={0} max={40} unit="px" onChange={(blur) => updateAppearance({ blur })} />
          <RangeControl label={t('appearance.glassDepth', 'Glass depth')} value={appearance.depth} unit="%" onChange={(depth) => updateAppearance({ depth })} />
          <RangeControl label={t('appearance.shadowStrength', 'Shadow strength')} value={appearance.shadowStrength} unit="%" onChange={(shadowStrength) => updateAppearance({ shadowStrength })} />
          <RangeControl label={t('appearance.animationSpeed', 'Animation speed')} value={appearance.animationSpeed} min={40} max={180} unit="%" onChange={(animationSpeed) => updateAppearance({ animationSpeed })} />
          <RangeControl label={t('appearance.cornerRadius', 'Corner radius')} value={appearance.cornerRadius} min={0} max={16} unit="px" onChange={(cornerRadius) => updateAppearance({ cornerRadius })} />
        </div>
      </section>

      <section className="grid md:grid-cols-3 gap-4">
        <SelectControl
          label={t('appearance.fontFamily', 'Font family')}
          value={appearance.fontFamily}
          options={['Inter', 'Arial', 'Georgia', 'Trebuchet MS']}
          onChange={(fontFamily) => updateAppearance({ fontFamily })}
        />
        <SelectControl
          label={t('appearance.cardDensity', 'Card density')}
          value={appearance.cardDensity}
          options={['compact', 'comfortable', 'spacious']}
          onChange={(cardDensity) => updateAppearance({ cardDensity: cardDensity as CardDensity })}
        />
        <SelectControl
          label={t('appearance.buttonStyle', 'Button style')}
          value={appearance.buttonStyle}
          options={['solid', 'glass', 'soft']}
          onChange={(buttonStyle) => updateAppearance({ buttonStyle: buttonStyle as ButtonStyle })}
        />
        <SelectControl
          label={t('appearance.sidebarStyle', 'Sidebar style')}
          value={appearance.sidebarStyle}
          options={['floating', 'rail', 'compact']}
          onChange={(sidebarStyle) => updateAppearance({ sidebarStyle: sidebarStyle as SidebarStyle })}
        />
        <label className="surface-control min-h-16 px-3 flex items-center justify-between md:col-span-2">
          <span>
            <span className="block text-xs font-semibold text-[var(--text-primary)]">{t('appearance.whiteLabelMode', 'White label mode')}</span>
            <span className="block text-[10px] text-[var(--text-muted)]">{t('appearance.whiteLabelModeDesc', 'Use agency identity throughout the shell')}</span>
          </span>
          <input
            type="checkbox"
            checked={appearance.whiteLabelMode}
            onChange={(event) => updateAppearance({ whiteLabelMode: event.target.checked })}
            className="w-4 h-4 accent-[var(--brand-primary)]"
          />
        </label>
      </section>

      <section>
        <SettingLabel title={t('appearance.livePreview', 'Live preview')} description={t('appearance.livePreviewDesc', 'What the shell looks like with these settings applied')} icon={Layers} />
        <LivePreview
          sidebarColor={appearance.sidebarColor}
          primaryColor={appearance.primaryColor}
          accentColor={appearance.accentColor}
          surfaceColor={appearance.surfaceColor}
          backgroundColor={appearance.backgroundColor}
          cornerRadius={appearance.cornerRadius}
        />
      </section>
    </div>
  );
}

function SettingLabel({ title, description, icon: Icon }: { title: string; description: string; icon?: typeof Palette }) {
  return (
    <div className="flex items-start gap-2">
      {Icon && <Icon size={15} className="mt-0.5 text-[var(--brand-primary)]" />}
      <div>
        <h4 className="text-sm font-semibold text-[var(--text-primary)]">{title}</h4>
        <p className="text-xs text-[var(--text-muted)]">{description}</p>
      </div>
    </div>
  );
}

function ColorControl({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="surface-control h-11 px-3 flex items-center gap-3">
      <input type="color" value={value} onChange={(event) => onChange(event.target.value)} className="w-6 h-6 p-0 border-0 bg-transparent cursor-pointer" />
      <span className="min-w-0">
        <span className="block text-[10px] text-[var(--text-muted)]">{label}</span>
        <span className="block text-xs font-mono uppercase text-[var(--text-primary)]">{value}</span>
      </span>
    </label>
  );
}

function RangeControl({ label, value, onChange, min = 0, max = 100, unit }: {
  label: string; value: number; onChange: (value: number) => void; min?: number; max?: number; unit: string;
}) {
  return (
    <label className="block">
      <span className="flex justify-between text-xs">
        <span className="font-medium text-[var(--text-secondary)]">{label}</span>
        <span className="font-mono text-[var(--text-muted)]">{value}{unit}</span>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="mt-2 w-full accent-[var(--brand-primary)]"
      />
    </label>
  );
}

function SelectControl({ label, value, options, onChange }: {
  label: string; value: string; options: string[]; onChange: (value: string) => void;
}) {
  return (
    <label>
      <span className="block mb-2 text-xs font-medium text-[var(--text-secondary)]">{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)} className="surface-control w-full h-10 px-3 text-sm outline-none">
        {options.map((option) => <option key={option} value={option}>{option.replace('-', ' ')}</option>)}
      </select>
    </label>
  );
}

function LivePreview({ sidebarColor, primaryColor, accentColor, surfaceColor, backgroundColor, cornerRadius }: {
  sidebarColor: string; primaryColor: string; accentColor: string; surfaceColor: string; backgroundColor: string; cornerRadius: number;
}) {
  const { t } = useTranslation();
  const sidebarIsLight = relativeLuminanceHex(sidebarColor) > 0.45;
  const sidebarText = sidebarIsLight ? 'rgba(15,23,42,0.85)' : 'rgba(255,255,255,0.92)';
  const sidebarMuted = sidebarIsLight ? 'rgba(15,23,42,0.45)' : 'rgba(255,255,255,0.45)';

  return (
    <div
      className="mt-3 overflow-hidden rounded-xl border border-[var(--border-subtle)] flex"
      style={{ background: backgroundColor, borderRadius: cornerRadius }}
    >
      <div className="w-16 sm:w-20 flex flex-col gap-2 p-2.5" style={{ background: sidebarColor }}>
        <div className="h-2 w-7 rounded-full" style={{ background: accentColor }} />
        <div className="h-2 w-9 rounded-full opacity-90" style={{ background: sidebarText }} />
        <div className="h-2 w-8 rounded-full" style={{ background: sidebarMuted }} />
        <div className="h-2 w-9 rounded-full" style={{ background: sidebarMuted }} />
      </div>
      <div className="flex-1 p-3 space-y-2.5">
        <div className="h-7 rounded-lg flex items-center justify-end px-2 gap-2" style={{ background: surfaceColor, border: '1px solid rgba(0,0,0,0.06)' }}>
          <span className="h-2 w-14 rounded-full bg-black/10" />
          <span className="h-4 w-4 rounded-full" style={{ background: primaryColor }} />
        </div>
        <div className="rounded-lg p-3 space-y-2" style={{ background: surfaceColor, border: '1px solid rgba(0,0,0,0.06)' }}>
          <div className="flex items-center justify-between">
            <span className="h-2 w-20 rounded-full bg-black/10" />
            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full text-white" style={{ background: accentColor }}>{t('appearance.new', 'NEW')}</span>
          </div>
          <span className="block h-2 w-32 rounded-full bg-black/[0.06]" />
          <div className="flex items-center gap-2 pt-1">
            <span className="h-6 px-3 rounded-lg text-[9px] font-bold text-white flex items-center" style={{ background: primaryColor }}>{t('appearance.primaryAction', 'Primary action')}</span>
            <span className="h-6 px-2 rounded-lg text-[9px] flex items-center border border-black/10 bg-white/40">{t('appearance.input', 'Input')}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function relativeLuminanceHex(hex: string): number {
  const normalized = (hex || '').replace('#', '');
  const expanded = normalized.length === 3
    ? normalized.split('').map((value) => value + value).join('')
    : normalized;
  const number = Number.parseInt(expanded, 16);
  if (Number.isNaN(number)) return 1;
  const r = number >> 16;
  const g = (number >> 8) & 255;
  const b = number & 255;
  const channel = (value: number) => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}
