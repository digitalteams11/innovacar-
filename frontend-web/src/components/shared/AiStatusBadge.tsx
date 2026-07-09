import { CloudOff } from 'lucide-react';

/**
 * Passive status indicator. It intentionally does not call /api/ai/status on
 * mount because AI can be plan-restricted and optional; automatic polling was
 * producing expected 403 responses in the browser console on unrelated pages.
 */
export default function AiStatusBadge({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs text-slate-400 ${className}`}>
      <CloudOff size={12} /> AI is unavailable.
    </span>
  );
}