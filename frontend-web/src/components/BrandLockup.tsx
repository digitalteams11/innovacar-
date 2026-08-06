/**
 * The "Innovacar / BY INNOVAX · TECHNOLOGIES" brand lockup — the single
 * source of truth for how the product name renders everywhere it appears
 * as styled text (sidebar, header, splash screen, Super Admin, landing
 * page). Previously every screen re-implemented this by hand, which is how
 * the sidebar ended up rendering "Car" in `--brand-accent` (amber) instead
 * of the actual Innovax green, and the subtitle in a washed-out 46%-opacity
 * muted token instead of solid white.
 *
 * Regression fixed here: the subtitle row used Tailwind's `truncate`
 * (overflow:hidden + ellipsis + nowrap). Inside a flex ancestor with
 * `min-w-0` (the sidebar's `<Link className="flex items-center gap-3
 * min-w-0">`), that silently clipped to "BY INNOVAX TECHNOL" whenever the
 * row got even slightly tight. Fixed by dropping `truncate` entirely — this
 * component now only ever uses `whitespace-nowrap` (never wraps awkwardly)
 * and never hides characters. If a caller's container is too narrow for the
 * full text, the fix is picking a smaller `size`/`compact`, never clipping.
 *
 * Color rule (fixed, does not follow the tenant's white-label theme colors
 * — see `--brand-green` in index.css):
 *   Innova        -> white (dark surfaces) / dark text (light surfaces)
 *   Car           -> Innovax green (subtle two-tone gradient), always
 *   BY INNOVAX    -> white (dark surfaces) / dark text (light surfaces)
 *   TECHNOLOGIES  -> Innovax green, always
 */
import type { CSSProperties } from 'react';

type Variant = 'dark' | 'light' | 'auto';
type Size = 'sm' | 'md' | 'lg';
type Orientation = 'stacked' | 'horizontal';

export interface BrandLockupProps {
  /** Tightens spacing and drops the default name size — for icon-adjacent nav contexts. */
  compact?: boolean;
  /** Show the "BY INNOVAX · TECHNOLOGIES" line. Defaults to true, false when compact. */
  showSubtitle?: boolean;
  /**
   * 'dark'  — surface is always dark regardless of theme (sidebars, email/PDF dark headers): forces white.
   * 'light' — surface is always light regardless of theme: forces dark text.
   * 'auto'  — surface follows the current light/dark theme (splash screen, marketing header): uses --text-primary.
   */
  variant?: Variant;
  size?: Size;
  /** 'stacked' (default) — name above subtitle, two lines. 'horizontal' — name and subtitle share one row, separated by a divider (very tight header contexts). */
  orientation?: Orientation;
  className?: string;
}

const NAME_SIZE: Record<Size, string> = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-2xl',
};

/**
 * Subtitle font-size + letter-spacing per size — bumped up from the
 * original 8/9px (which read as cramped) but kept realistic for where each
 * size is actually used: 'md' renders inside the real 248px desktop
 * sidebar column (shared with the logo, gap and collapse button — roughly
 * 130px actually available for this text), so it stays close to the
 * original working baseline rather than the more generous spacing 'lg'
 * (splash screen — centered, standalone, effectively unconstrained) can
 * afford. Tracking eased off at smaller sizes per spec §3.
 */
const SUBTITLE_STYLE: Record<Size, CSSProperties> = {
  sm: { fontSize: '8.5px', letterSpacing: '0.08em' },
  md: { fontSize: '9px', letterSpacing: '0.09em' },
  lg: { fontSize: '11.5px', letterSpacing: '0.14em' },
};

/** Only 'lg' (splash screen and other generous, standalone contexts) gets the decorative middle-dot separator + accent underline — the sidebar's 'md' size has real, hard width constraints where that flourish risks the exact overflow this component exists to prevent. */
const DECORATED_SIZES: Size[] = ['lg'];

function mainColor(variant: Variant): CSSProperties {
  if (variant === 'dark') return { color: '#ffffff' };
  return { color: 'var(--text-primary)' };
}

/** Subtle two-tone gradient text for the green segments — spec §6: "subtle gradient only inside Car when supported." Degrades gracefully to solid --brand-green if background-clip:text isn't supported. */
const greenGradientStyle: CSSProperties = {
  color: 'var(--brand-green)',
  backgroundImage: 'linear-gradient(135deg, var(--brand-green), var(--brand-green-hover))',
  WebkitBackgroundClip: 'text',
  backgroundClip: 'text',
  WebkitTextFillColor: 'transparent',
};

export default function BrandLockup({
  compact = false,
  showSubtitle = !compact,
  variant = 'dark',
  size = compact ? 'sm' : 'md',
  orientation = 'stacked',
  className = '',
}: BrandLockupProps) {
  const mainStyle = mainColor(variant);
  const subtitleStyle = SUBTITLE_STYLE[size];

  const nameNode = (
    <div className={`brand-lockup__name font-extrabold leading-tight tracking-tight whitespace-nowrap ${NAME_SIZE[size]}`}>
      <span className="brand-lockup__name-main" style={mainStyle}>Innova</span>
      <span className="brand-lockup__name-accent" style={greenGradientStyle}>Car</span>
    </div>
  );

  const decorated = DECORATED_SIZES.includes(size);

  const subtitleNode = showSubtitle && (
    <div
      className={`brand-lockup__company flex items-center font-semibold uppercase whitespace-nowrap ${decorated ? 'gap-[0.35em]' : 'gap-[0.3em]'}`}
      style={subtitleStyle}
    >
      <span className="brand-lockup__company-main">
        <span style={{ ...mainStyle, fontWeight: 500 }}>BY</span>{' '}
        <span style={{ ...mainStyle, fontWeight: 700 }}>INNOVAX</span>
      </span>
      {decorated && (
        <span aria-hidden="true" className="brand-lockup__company-dot" style={{ color: 'var(--brand-green)' }}>&middot;</span>
      )}
      <span className="brand-lockup__company-accent" style={{ color: 'var(--brand-green)', fontWeight: 700 }}>
        TECHNOLOGIES
      </span>
    </div>
  );

  if (orientation === 'horizontal') {
    return (
      <div
        dir="ltr"
        className={`brand-lockup flex items-baseline gap-2 min-w-0 ${className}`}
        aria-label="Innovacar by Innovax Technologies"
      >
        {nameNode}
        {subtitleNode && (
          <>
            <span aria-hidden="true" className="h-3 w-px shrink-0" style={{ background: 'currentColor', opacity: 0.25, ...mainStyle }} />
            {subtitleNode}
          </>
        )}
      </div>
    );
  }

  return (
    <div dir="ltr" className={`brand-lockup min-w-0 ${className}`} aria-label="Innovacar by Innovax Technologies">
      {nameNode}
      {subtitleNode}
      {showSubtitle && decorated && (
        <div
          aria-hidden="true"
          className="brand-lockup__underline mt-1 h-px w-8 rounded-full"
          style={{ backgroundImage: 'linear-gradient(90deg, var(--brand-green), transparent)' }}
        />
      )}
    </div>
  );
}
