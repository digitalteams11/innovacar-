

interface SkeletonTableProps {
  rows?: number;
  columns?: number;
}

export default function SkeletonTable({ rows = 5, columns = 6 }: SkeletonTableProps) {
  return (
    <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
              {Array.from({ length: columns }).map((_, i) => (
                <th key={i} className="px-5 py-4">
                  <div className="h-3 w-20 bg-slate-200 dark:bg-slate-700 rounded animate-pulse" />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: rows }).map((_, r) => (
              <tr key={r} className="border-b border-[#e8e6e1]/40 dark:border-white/5">
                {Array.from({ length: columns }).map((_, c) => (
                  <td key={c} className="px-5 py-4">
                    <div className={`h-3 bg-slate-200 dark:bg-slate-700 rounded animate-pulse ${c === 0 ? 'w-32' : 'w-16'}`} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
