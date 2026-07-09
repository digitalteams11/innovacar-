import { AlertCircle, RefreshCw } from 'lucide-react';

export default function ApiErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex min-h-[240px] flex-col items-center justify-center border border-rose-100 bg-white px-6 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600">
        <AlertCircle size={22} />
      </div>
      <h3 className="mt-4 text-sm font-bold text-[#1e293b]">Unable to load this information</h3>
      <p className="mt-2 max-w-md text-sm text-slate-500">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-5 flex items-center gap-2 rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-semibold text-white">
          <RefreshCw size={15} /> Try again
        </button>
      )}
    </div>
  );
}
