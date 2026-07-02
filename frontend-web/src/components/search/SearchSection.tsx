import type { GlobalSearchResult } from '../../types/globalSearch';
import { SearchResultItem } from './SearchResultItem';

export function SearchSection({
  title,
  results,
  activeId,
  onSelect,
}: {
  title: string;
  results: GlobalSearchResult[];
  activeId?: string;
  onSelect: (result: GlobalSearchResult) => void;
}) {
  if (results.length === 0) return null;
  return (
    <section className="space-y-2">
      <h3 className="px-2 text-[11px] font-black uppercase tracking-[0.18em] text-[var(--text-muted)]">{title}</h3>
      <div className="space-y-1">
        {results.map((result) => (
          <SearchResultItem
            key={result.id}
            result={result}
            active={activeId === result.id}
            onSelect={() => onSelect(result)}
          />
        ))}
      </div>
    </section>
  );
}
