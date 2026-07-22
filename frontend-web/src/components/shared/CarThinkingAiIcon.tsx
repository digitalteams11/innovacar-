import { Car, Loader2, Sparkles } from 'lucide-react';

export type AiIconState = 'idle' | 'thinking' | 'disabled' | 'error';

interface Props {
  state?: AiIconState;
  size?: number;
}

/**
 * Composite car-rental AI assistant icon.
 * States: idle (car + sparkle), thinking (car + spinning dots), disabled (grey), error (amber badge).
 */
export default function CarThinkingAiIcon({ state = 'idle', size = 22 }: Props) {
  const badgeSize = Math.max(10, Math.round(size * 0.5));

  return (
    <span className="relative inline-flex items-center justify-center" aria-hidden="true">
      {/* Car body */}
      <Car
        size={size}
        className={
          state === 'disabled'
            ? 'text-slate-400'
            : state === 'error'
            ? 'text-amber-400'
            : state === 'thinking'
            ? 'text-white'
            : 'text-white'
        }
        strokeWidth={1.8}
      />

      {/* Thinking bubble / badge — top-right overlay */}
      <span
        className={`absolute -top-1.5 -end-1.5 flex items-center justify-center rounded-full
          ${state === 'thinking' ? 'w-4 h-4 bg-cyan-400/20' : 'w-3.5 h-3.5'}
        `}
      >
        {state === 'idle' && (
          <Sparkles
            size={badgeSize}
            className="text-cyan-300 drop-shadow-[0_0_4px_rgba(103,232,249,0.9)]"
            strokeWidth={2.5}
          />
        )}
        {state === 'thinking' && (
          <Loader2
            size={badgeSize}
            className="animate-spin text-cyan-300 drop-shadow-[0_0_4px_rgba(103,232,249,0.9)]"
            strokeWidth={2.5}
          />
        )}
        {state === 'disabled' && (
          <span className="block w-2.5 h-2.5 rounded-full bg-slate-500/80" />
        )}
        {state === 'error' && (
          <span
            className="flex items-center justify-center w-3 h-3 rounded-full bg-amber-400 text-[7px] font-black text-amber-900 leading-none"
            title="AI error"
          >
            !
          </span>
        )}
      </span>
    </span>
  );
}
