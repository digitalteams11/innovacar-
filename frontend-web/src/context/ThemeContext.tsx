import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ThemePreset =
  | 'neo-emerald'
  | 'carbon-lime'
  | 'titanium-2026'
  | 'luxury-black-gold'
  | 'ocean-blue-pro'
  | 'moroccan-sand'
  | 'purple-slate'
  | 'clean-white-pro';
export type CardDensity = 'compact' | 'comfortable' | 'spacious';
export type ButtonStyle = 'solid' | 'glass' | 'soft';
export type SidebarStyle = 'floating' | 'rail' | 'compact';

export interface AppearanceSettings {
  mode: ThemeMode;
  preset: ThemePreset;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  sidebarColor: string;
  backgroundColor: string;
  surfaceColor: string;
  glassColor: string;
  glassIntensity: number;
  blur: number;
  opacity: number;
  depth: number;
  shadowStrength: number;
  animationSpeed: number;
  cornerRadius: number;
  fontFamily: string;
  cardDensity: CardDensity;
  buttonStyle: ButtonStyle;
  sidebarStyle: SidebarStyle;
  whiteLabelMode: boolean;
}

interface BrandingOverride {
  logoUrl: string;
  primaryColor: string;
  accentColor: string;
}

interface ThemeContextType {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
  resolvedTheme: 'light' | 'dark';
  appearance: AppearanceSettings;
  updateAppearance: (updates: Partial<AppearanceSettings>) => void;
  applyPreset: (preset: ThemePreset) => void;
  resetAppearance: () => void;
  /** Agency Branding (White Label) logo/colors — the source of truth when set, overriding the active preset. */
  branding: BrandingOverride | null;
  refreshBranding: () => void;
}

const STORAGE_KEY = 'rentcar_appearance';
// Tracks which user's server-side themeMode has already been pulled down to this
// device, so the one-time login sync below only ever fires once per (device, user)
// pair — not once per page load. A useRef alone resets on every refresh, which was
// the actual cause of "I picked Light, refreshed, and it went back to Dark": the
// sync effect re-ran on every refresh and re-applied whatever the server happened
// to hold (often the User entity's "light" default, if a previous save silently
// failed — see setTheme()'s catch below), overwriting the just-loaded, correct
// localStorage value every single time.
const THEME_SYNCED_USER_KEY = 'rentcar_theme_synced_user_id';

/**
 * Each 2026 SaaS theme preset defines its own primary/secondary/accent (fixed
 * across modes — these are the brand identity) plus a background/surface/
 * sidebar pairing per mode. Body/sidebar text is never stored here: it's
 * computed at apply-time from the actual background/sidebar luminance, so a
 * theme can never ship unreadable text regardless of how a color is tuned.
 */
interface PresetModeColors {
  background: string;
  surface: string;
  sidebar: string;
}

interface PresetDefinition {
  label: string;
  description: string;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  light: PresetModeColors;
  dark: PresetModeColors;
}

export const presetCatalog: Record<ThemePreset, PresetDefinition> = {
  'neo-emerald': {
    label: 'Neo Emerald',
    description: 'Modern green SaaS, clean and fresh.',
    primaryColor: '#10B981',
    secondaryColor: '#0F766E',
    accentColor: '#F59E0B',
    light: { background: '#F8FAFC', surface: '#FFFFFF', sidebar: '#064E3B' },
    dark: { background: '#071A16', surface: '#0B2520', sidebar: '#031F1A' },
  },
  'carbon-lime': {
    label: 'Carbon Lime',
    description: 'Dark developer dashboard, strong modern contrast.',
    primaryColor: '#84CC16',
    secondaryColor: '#22C55E',
    accentColor: '#A3E635',
    light: { background: '#F9FAFB', surface: '#FFFFFF', sidebar: '#111827' },
    dark: { background: '#050505', surface: '#111111', sidebar: '#0A0A0A' },
  },
  'titanium-2026': {
    label: 'Titanium 2026',
    description: 'Minimal Apple-like, graphite and silver.',
    primaryColor: '#64748B',
    secondaryColor: '#94A3B8',
    accentColor: '#38BDF8',
    light: { background: '#F8FAFC', surface: '#FFFFFF', sidebar: '#1E293B' },
    dark: { background: '#0F172A', surface: '#1E293B', sidebar: '#020617' },
  },
  'luxury-black-gold': {
    label: 'Luxury Black Gold',
    description: 'Premium rental/luxury car style. Gold is an accent only — never a full sidebar fill.',
    primaryColor: '#C8A24A',
    secondaryColor: '#8B6F2E',
    accentColor: '#EAB308',
    light: { background: '#FAFAF9', surface: '#FFFFFF', sidebar: '#111111' },
    dark: { background: '#050505', surface: '#171717', sidebar: '#0A0A0A' },
  },
  'ocean-blue-pro': {
    label: 'Ocean Blue Pro',
    description: 'Corporate clean blue, professional but not heavy.',
    primaryColor: '#0284C7',
    secondaryColor: '#0E7490',
    accentColor: '#06B6D4',
    light: { background: '#F8FAFC', surface: '#FFFFFF', sidebar: '#0F172A' },
    dark: { background: '#020617', surface: '#0F172A', sidebar: '#020617' },
  },
  'moroccan-sand': {
    label: 'Moroccan Sand',
    description: 'Warm Moroccan professional theme, not yellow-heavy.',
    primaryColor: '#B45309',
    secondaryColor: '#0F766E',
    accentColor: '#D97706',
    light: { background: '#FFFBEB', surface: '#FFFFFF', sidebar: '#292524' },
    dark: { background: '#1C1917', surface: '#292524', sidebar: '#0C0A09' },
  },
  'purple-slate': {
    label: 'Purple Slate',
    description: 'Modern SaaS, creative but professional.',
    primaryColor: '#7C3AED',
    secondaryColor: '#4F46E5',
    accentColor: '#A78BFA',
    light: { background: '#F8FAFC', surface: '#FFFFFF', sidebar: '#1E1B4B' },
    dark: { background: '#0F0A1F', surface: '#1E1B4B', sidebar: '#090514' },
  },
  'clean-white-pro': {
    label: 'Clean White Pro',
    description: 'Very clean enterprise dashboard.',
    primaryColor: '#111827',
    secondaryColor: '#6B7280',
    accentColor: '#10B981',
    light: { background: '#F9FAFB', surface: '#FFFFFF', sidebar: '#FFFFFF' },
    dark: { background: '#111827', surface: '#1F2937', sidebar: '#030712' },
  },
};

type PresetColorFields = Pick<AppearanceSettings,
  'primaryColor' | 'secondaryColor' | 'accentColor' | 'backgroundColor' | 'surfaceColor' | 'sidebarColor' | 'glassColor'>;

function presetColorsFor(preset: ThemePreset, mode: 'light' | 'dark'): PresetColorFields {
  const definition = presetCatalog[preset] ?? presetCatalog['neo-emerald'];
  const modeColors = definition[mode];
  return {
    primaryColor: definition.primaryColor,
    secondaryColor: definition.secondaryColor,
    accentColor: definition.accentColor,
    backgroundColor: modeColors.background,
    surfaceColor: modeColors.surface,
    sidebarColor: modeColors.sidebar,
    glassColor: modeColors.surface,
  };
}

const defaultAppearance: AppearanceSettings = {
  // Default must be LIGHT — never default new/cleared-storage users to
  // System, which is functionally equivalent to "may render Dark" and
  // violates the explicit "LIGHT stays light until the user changes it" rule.
  mode: 'light',
  preset: 'neo-emerald',
  ...presetColorsFor('neo-emerald', 'light'),
  glassIntensity: 58,
  blur: 22,
  opacity: 78,
  depth: 50,
  shadowStrength: 40,
  animationSpeed: 100,
  cornerRadius: 10,
  fontFamily: 'Inter',
  cardDensity: 'comfortable',
  buttonStyle: 'solid',
  sidebarStyle: 'floating',
  whiteLabelMode: false,
};

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

function systemTheme(): 'light' | 'dark' {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function hexToRgb(hex: string) {
  const normalized = (hex || '').replace('#', '');
  const expanded = normalized.length === 3
    ? normalized.split('').map((value) => value + value).join('')
    : normalized;
  const number = Number.parseInt(expanded, 16);
  if (Number.isNaN(number)) return { r: 255, g: 255, b: 255 };
  return { r: number >> 16, g: (number >> 8) & 255, b: number & 255 };
}

/** WCAG-ish relative luminance, used only to decide whether text on top of a
 * given color should be light or dark — never to police exact contrast ratios. */
function relativeLuminance(hex: string): number {
  const { r, g, b } = hexToRgb(hex);
  const channel = (value: number) => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/**
 * Picks a readable text color (and a muted variant) for any background,
 * regardless of how light/dark/saturated the theme's chosen color is. This
 * is what guarantees a theme can never ship invisible sidebar/body text —
 * e.g. a white "Clean White Pro" sidebar automatically gets dark text
 * instead of the old hardcoded white-on-white bug.
 */
function readableTextOn(bgHex: string): { text: string; muted: string } {
  const isLight = relativeLuminance(bgHex) > 0.45;
  return isLight
    ? { text: 'rgba(15, 23, 42, 0.92)', muted: 'rgba(15, 23, 42, 0.46)' }
    : { text: 'rgba(255, 255, 255, 0.94)', muted: 'rgba(255, 255, 255, 0.46)' };
}

function loadAppearance(): AppearanceSettings {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return defaultAppearance;
    const parsed = JSON.parse(stored);
    // A preset removed in a previous theme-catalog upgrade (e.g. the old
    // mustard-toned "luxury-gold") must not leave its stale colors behind —
    // reset both the preset key and its color fields to a current default.
    if (!presetCatalog[parsed.preset as ThemePreset]) {
      Object.assign(parsed, { preset: defaultAppearance.preset, ...presetColorsFor(defaultAppearance.preset, 'light') });
    }
    return { ...defaultAppearance, ...parsed };
  } catch {
    return defaultAppearance;
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, user, updateCurrentUser } = useAuth();
  const { showToast } = useToast();
  const [appearance, setAppearance] = useState<AppearanceSettings>(loadAppearance);
  const [loadedRemoteAppearance, setLoadedRemoteAppearance] = useState(false);
  const [branding, setBranding] = useState<BrandingOverride | null>(null);
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(
    appearance.mode === 'system' ? systemTheme() : appearance.mode,
  );
  // Set true the instant the user explicitly changes the mode via setTheme()
  // (this session). Once true, no later server sync/fetch may overwrite the
  // mode for the rest of this session — a local explicit choice always wins
  // over a stale or late backend response (see setTheme() and the sync-once
  // effect below).
  const hasUserChangedThemeRef = useRef(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setLoadedRemoteAppearance(false);
      return;
    }
    setLoadedRemoteAppearance(false);
    let cancelled = false;
    api.get('/tenant-settings')
      .then(({ data }) => {
        if (cancelled) return;
        const settings = data?.data && typeof data.data === 'object' ? data.data : data;
        if (settings?.appearance && typeof settings.appearance === 'object') {
          // `mode` (light/dark/auto) is a personal preference now sourced
          // from the user's own account, not the shared agency appearance —
          // never let the tenant-wide fetch override it.
          const { mode: _tenantMode, ...appearanceWithoutMode } = settings.appearance;
          setAppearance((current) => {
            const merged = { ...current, ...appearanceWithoutMode };
            if (!presetCatalog[merged.preset as ThemePreset]) {
              const mode = current.mode === 'system' ? systemTheme() : current.mode;
              Object.assign(merged, { preset: defaultAppearance.preset, ...presetColorsFor(defaultAppearance.preset, mode) });
            }
            return merged;
          });
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoadedRemoteAppearance(true);
      });
    return () => { cancelled = true; };
  }, [isAuthenticated]);

  const fetchBranding = useCallback(() => {
    if (!isAuthenticated) {
      setBranding(null);
      return;
    }
    api.get('/white-label')
      .then(({ data }) => {
        if (data?.primaryColor && data?.accentColor) {
          setBranding({
            logoUrl: data.logoUrl || '',
            primaryColor: data.primaryColor,
            accentColor: data.accentColor,
          });
        } else {
          setBranding(null);
        }
      })
      .catch(() => setBranding(null));
  }, [isAuthenticated]);

  useEffect(() => {
    fetchBranding();
  }, [fetchBranding]);

  // Clears the sync-once marker on logout (not on a mere refresh, since a refresh
  // never transitions isAuthenticated through false) so the next login re-adopts
  // whatever the server holds — e.g. the user changed their theme from another
  // device in the meantime — matching "backend wins right at authentication".
  useEffect(() => {
    if (isAuthenticated) return;
    try { localStorage.removeItem(THEME_SYNCED_USER_KEY); } catch { /* non-fatal */ }
  }, [isAuthenticated]);

  // Pulls the user's server-saved theme mode down to this device exactly once per
  // (device, user) — genuinely once, surviving refreshes — not once per page load.
  // A fresh login on a new device/browser (no marker yet) adopts the server value,
  // same as before; every refresh after that trusts the already-loaded localStorage
  // value instead of re-pulling and potentially reverting a choice the server copy
  // is stale on. localStorage.getItem/setItem can legitimately throw (private
  // browsing, storage disabled) — treated as "never synced", which just means this
  // one-time pull happens again next time, never breaking theme switching itself.
  useEffect(() => {
    if (!isAuthenticated || !user?.id) {
      return;
    }
    // A local explicit change this session always wins — never let a sync
    // (even a first-time one) clobber a choice the user just made.
    if (hasUserChangedThemeRef.current) return;
    let syncedUserId: string | null = null;
    try {
      syncedUserId = localStorage.getItem(THEME_SYNCED_USER_KEY);
    } catch { /* private browsing / storage disabled — fall through and re-sync */ }
    if (syncedUserId === String(user.id)) return;
    try {
      localStorage.setItem(THEME_SYNCED_USER_KEY, String(user.id));
    } catch { /* non-fatal — worst case this sync-once check runs again next reload */ }
    if (user.themeMode && user.themeMode !== appearance.mode) {
      setAppearance((current) => ({ ...current, mode: user.themeMode as ThemeMode }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- appearance.mode intentionally
    // excluded: only user.id/user.themeMode should re-arm this one-time sync, never a later
    // local mode change (that would defeat the whole point of syncing only once).
  }, [isAuthenticated, user?.id, user?.themeMode]);

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const updateResolvedTheme = () => {
      setResolvedTheme(appearance.mode === 'system' ? (media.matches ? 'dark' : 'light') : appearance.mode);
    };
    updateResolvedTheme();
    media.addEventListener('change', updateResolvedTheme);
    return () => media.removeEventListener('change', updateResolvedTheme);
  }, [appearance.mode]);

  // Re-derive background/surface/sidebar from the active preset whenever the
  // resolved mode flips (light <-> dark), so a theme stays readable and on-
  // brand in both modes instead of dragging light-mode colors into dark mode.
  useEffect(() => {
    const definition = presetCatalog[appearance.preset];
    if (!definition) return;
    const modeColors = definition[resolvedTheme];
    setAppearance((current) => {
      if (
        current.backgroundColor === modeColors.background
        && current.surfaceColor === modeColors.surface
        && current.sidebarColor === modeColors.sidebar
      ) {
        return current;
      }
      return {
        ...current,
        backgroundColor: modeColors.background,
        surfaceColor: modeColors.surface,
        sidebarColor: modeColors.sidebar,
        glassColor: modeColors.surface,
      };
    });
  }, [resolvedTheme, appearance.preset]);

  // useLayoutEffect (not useEffect): applies the `dark` class and CSS vars
  // synchronously after DOM mutation but before the browser paints, shrinking
  // the flash window for brand-color-dependent foreground text (see
  // readableTextOn() below) — the inline script in index.html already
  // handles the class/color-scheme before React even mounts, this effect
  // just needs to apply the same thing on every subsequent state change.
  useLayoutEffect(() => {
    const root = document.documentElement;
    const glass = hexToRgb(appearance.glassColor);
    const density = appearance.cardDensity === 'compact' ? 0.78 : appearance.cardDensity === 'spacious' ? 1.2 : 1;
    const duration = Math.max(40, appearance.animationSpeed);
    const sidebarText = readableTextOn(appearance.sidebarColor);
    const resolvedPrimary = branding?.primaryColor || appearance.primaryColor;
    const resolvedAccent = branding?.accentColor || appearance.accentColor;
    // Same luminance-safe approach the sidebar already uses, applied to every place
    // brand colors are used as a solid button/badge background — e.g. .premium-action
    // in index.css previously hardcoded `color: #171817`, which is nearly invisible
    // against the clean-white-pro preset's near-black #111827 primary (the reported
    // "New Contract" label is invisible bug). A custom white-label primary/accent
    // color is exactly as likely to be dark as any built-in preset, so this must be
    // computed here rather than assumed.
    const primaryText = readableTextOn(resolvedPrimary);
    const accentText = readableTextOn(resolvedAccent);

    root.classList.toggle('dark', resolvedTheme === 'dark');
    root.style.colorScheme = resolvedTheme;
    root.dataset.themePreset = appearance.preset;
    root.dataset.buttonStyle = appearance.buttonStyle;
    // Keeps the mobile/PWA browser chrome tint in sync with the resolved
    // theme (the inline anti-flash script in index.html sets an approximate
    // value before this ever runs; this corrects it to the exact surface).
    const themeColorMeta = document.querySelector('meta[name="theme-color"]');
    if (themeColorMeta) {
      themeColorMeta.setAttribute('content', resolvedTheme === 'dark' ? '#0F172A' : '#F8FAFC');
    }
    // Agency Branding (White Label) colors are the source of truth when configured —
    // they override the preset's primary/accent so saving branding repaints the app immediately.
    root.style.setProperty('--brand-primary', resolvedPrimary);
    root.style.setProperty('--brand-primary-foreground', primaryText.text);
    root.style.setProperty('--brand-secondary', appearance.secondaryColor);
    root.style.setProperty('--brand-accent', resolvedAccent);
    root.style.setProperty('--brand-accent-foreground', accentText.text);
    root.style.setProperty('--bg-sidebar', appearance.sidebarColor);
    root.style.setProperty('--text-sidebar', sidebarText.text);
    root.style.setProperty('--text-sidebar-muted', sidebarText.muted);
    root.style.setProperty('--bg-page', appearance.backgroundColor);
    root.style.setProperty('--bg-page-raised', appearance.surfaceColor);
    root.style.setProperty('--bg-card-solid', appearance.surfaceColor);
    // Every preset's background is either clearly light or clearly dark, so
    // the existing light/dark `--text-primary` neutrals (toggled by the
    // `.dark` class above) already stay readable against it — intentionally
    // not overridden here to keep this change scoped to Appearance Studio
    // and the sidebar, rather than re-tinting body text across the whole app.
    root.style.setProperty('--user-glass-rgb', `${glass.r}, ${glass.g}, ${glass.b}`);
    root.style.setProperty('--glass-opacity', String(appearance.opacity / 100));
    root.style.setProperty('--glass-intensity', String(appearance.glassIntensity / 100));
    root.style.setProperty('--glass-blur', `${appearance.blur}px`);
    root.style.setProperty('--glass-depth', String(appearance.depth / 100));
    root.style.setProperty('--shadow-strength', String(appearance.shadowStrength / 100));
    root.style.setProperty('--motion-speed', `${duration / 100}`);
    root.style.setProperty('--radius-card', `${appearance.cornerRadius}px`);
    root.style.setProperty('--density-scale', String(density));
    root.style.setProperty('--font-ui', `'${appearance.fontFamily}', Inter, system-ui, sans-serif`);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(appearance));
  }, [appearance, resolvedTheme, branding]);

  useEffect(() => {
    if (!isAuthenticated || !loadedRemoteAppearance) return;
    const timer = window.setTimeout(() => {
      // `mode` is excluded — it's a personal preference saved separately via
      // /api/users/me/preferences, not part of the shared agency appearance.
      const { mode: _mode, ...appearanceForTenant } = appearance;
      api.put('/tenant-settings', { appearance: appearanceForTenant }).catch(() => undefined);
    }, 700);
    return () => window.clearTimeout(timer);
  }, [appearance, isAuthenticated, loadedRemoteAppearance]);

  const setTheme = useCallback((mode: ThemeMode) => {
    hasUserChangedThemeRef.current = true;
    setAppearance((current) => ({ ...current, mode }));
    if (!isAuthenticated) return;
    api.put('/users/me/preferences', { themeMode: mode })
      .then(({ data }) => {
        const saved = data?.data;
        if (saved?.themeMode) updateCurrentUser({ themeMode: saved.themeMode });
      })
      .catch((err: any) => {
        showToast(err?.userMessage || 'Unable to save preferences. Changes may not persist after refresh.', 'error');
      });
  }, [isAuthenticated, showToast, updateCurrentUser]);

  const updateAppearance = useCallback((updates: Partial<AppearanceSettings>) => {
    setAppearance((current) => ({ ...current, ...updates }));
  }, []);

  const applyPreset = useCallback((preset: ThemePreset) => {
    setAppearance((current) => {
      const mode = current.mode === 'system' ? systemTheme() : current.mode;
      return { ...current, ...presetColorsFor(preset, mode), preset };
    });
  }, []);

  const resetAppearance = useCallback(() => setAppearance(defaultAppearance), []);

  const value = useMemo<ThemeContextType>(() => ({
    theme: appearance.mode,
    setTheme,
    resolvedTheme,
    appearance,
    updateAppearance,
    applyPreset,
    resetAppearance,
    branding,
    refreshBranding: fetchBranding,
  }), [appearance, applyPreset, branding, fetchBranding, resetAppearance, resolvedTheme, setTheme, updateAppearance]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

// Context and hook intentionally share this module so provider internals remain private.
// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}
