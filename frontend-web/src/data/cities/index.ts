/**
 * Lazily loads a country's city list only when that country is selected, so
 * the initial bundle only ever pays for Morocco's dataset (or nothing, if
 * the client never opens the country selector). Countries without a
 * dedicated dataset simply return an empty list — the city field then
 * falls back to manual free-text entry (spec section 4).
 */
const loaders: Record<string, () => Promise<{ default: string[] }>> = {
  MA: () => import('./ma'),
  FR: () => import('./fr'),
  ES: () => import('./es'),
};

export function hasCityDataset(countryCode: string | undefined | null): boolean {
  return !!countryCode && countryCode.toUpperCase() in loaders;
}

export async function loadCitiesForCountry(countryCode: string | undefined | null): Promise<string[]> {
  if (!countryCode) return [];
  const loader = loaders[countryCode.toUpperCase()];
  if (!loader) return [];
  const mod = await loader();
  return mod.default;
}
