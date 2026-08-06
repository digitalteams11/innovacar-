/**
 * The "Innovacar / BY INNOVAX TECHNOLOGIES" brand lockup — the single
 * source of truth for how the product name renders everywhere it appears
 * as styled text (sidebar, header, splash screen, Super Admin, landing
 * page). Previously every screen re-implemented this by hand, which is how
 * the sidebar ended up rendering "Car" in `--brand-accent` (amber) instead
 * of the actual Innovax green, and the subtitle in a washed-out 46%-opacity
 * muted token instead of solid white.
 *
 * Color rule (fixed, does not follow the tenant's white-label theme colors
 * — see `--brand-green` in index.css):
 *   Innova        -> white (dark surfaces) / dark text (light surfaces)
 *   Car           -> Innovax green, always
 *   BY INNOVAX    -> white (dark surfaces) / dark text (light surfaces)
 *   TECHNOLOGIES  -> Innovax green, always
 */
type Variant = 'dark' | 'light' | 'auto';
type Size = 'sm' | 'md' | 'lg';

export interface BrandLockupProps {
  /** Tightens spacing and drops the default name size — for icon-adjacent nav contexts. */
  compact?: boolean;
  /** Show the "BY INNOVAX TECHNOLOGIES" line. Defaults to true, false when compact. */
  showSubtitle?: boolean;
  /**
   * 'dark'  — surface is always dark regardless of theme (sidebars, email/PDF dark headers): forces white.
   * 'light' — surface is always light regardless of theme: forces dark text.
   * 'auto'  — surface follows the current light/dark theme (splash screen, marketing header): uses --text-primary.
   */
  variant?: Variant;
  size?: Size;
  className?: string;
}

const NAME_SIZE: Record<Size, string> = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-2xl',
};

const SUBTITLE_SIZE: Record<Size, string> = {
  sm: 'text-[8px]',
  md: 'text-[9px]',
  lg: 'text-xs',
};

function mainColor(variant: Variant): React.CSSProperties {
  if (variant === 'dark') return { color: '#ffffff' };
  if (variant === 'light') return { color: 'var(--text-primary)' };
  return { color: 'var(--text-primary)' };
}

export default function BrandLockup({
  compact = false,
  showSubtitle = !compact,
  variant = 'dark',
  size = compact ? 'sm' : 'md',
  className = '',
}: BrandLockupProps) {
  const mainStyle = mainColor(variant);
  const greenStyle: React.CSSProperties = { color: 'var(--brand-green)' };

  return (
    <div className={`brand-lockup min-w-0 ${className}`} aria-label="Innovacar by Innovax Technologies">
      <div className={`brand-name font-extrabold leading-tight tracking-tight truncate ${NAME_SIZE[size]}`}>
        <span className="brand-name-main" style={mainStyle}>Innova</span>
        <span className="brand-name-accent" style={greenStyle}>Car</span>
      </div>
      {showSubtitle && (
        <div
          className={`brand-company font-semibold uppercase truncate ${SUBTITLE_SIZE[size]}`}
          style={{ letterSpacing: '0.12em', opacity: 1 }}
        >
          <span className="brand-company-main" style={mainStyle}>BY INNOVAX</span>
          <span className="brand-company-accent" style={greenStyle}> TECHNOLOGIES</span>
        </div>
      )}
    </div>
  );
}
