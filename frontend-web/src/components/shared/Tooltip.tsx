import { cn } from '../../lib/utils';

interface TooltipProps {
  label?: string | null;
  children: React.ReactNode;
  side?: 'top' | 'bottom';
}

/**
 * Minimal hover/focus tooltip — no JS state, pure CSS via group-hover /
 * group-focus-within, so it works identically for mouse and keyboard.
 * Deliberately dark regardless of theme (standard tooltip convention) so it
 * reads clearly over any surface it appears above.
 */
export default function Tooltip({ label, children, side = 'top' }: TooltipProps) {
  if (!label) return <>{children}</>;
  return (
    <span className="relative inline-flex group/tooltip">
      {children}
      <span
        role="tooltip"
        className={cn(
          'pointer-events-none absolute z-50 max-w-[200px] whitespace-normal rounded-md bg-gray-900 px-2 py-1 text-center text-xs font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover/tooltip:opacity-100 group-focus-within/tooltip:opacity-100',
          side === 'top' ? 'bottom-full left-1/2 -translate-x-1/2 mb-1.5' : 'top-full left-1/2 -translate-x-1/2 mt-1.5',
        )}
      >
        {label}
      </span>
    </span>
  );
}
