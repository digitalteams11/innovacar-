import i18n from '../i18n';

/**
 * Translates a backend status/enum code for display. Codes are never
 * translated in payloads — only here, at render time. Falls back to
 * `common.states.unknown` (translated) rather than the raw code, so a
 * status the UI doesn't recognize never leaks English/enum text.
 */
function translateStatus(namespacePrefixes: string[], status: string | null | undefined): string {
  if (!status) return i18n.t('common.states.unknown');
  const code = normalizeStatusCode(status);
  for (const prefix of namespacePrefixes) {
    const key = `${prefix}.${code}`;
    if (i18n.exists(key)) return i18n.t(key);
  }
  return i18n.t('common.states.unknown');
}

function normalizeEnumValue(value: string | null | undefined): string {
  return String(value || '')
    .trim()
    .replace(/[\s-]+/g, '_')
    .toUpperCase();
}

export function normalizeStatusCode(value: string | null | undefined): string {
  return normalizeEnumValue(value);
}

function translateEnum(namespace: string, value: string | null | undefined, fallback = ''): string {
  const code = normalizeEnumValue(value);
  if (!code) return fallback;
  const key = `enum.${namespace}.${code}`;
  if (i18n.exists(key)) return i18n.t(key);
  return fallback || i18n.t('common.states.unknown');
}

export const translateReservationStatus = (status?: string | null): string =>
  translateStatus(['reservations.statusLabel'], status);

export const translateContractStatus = (status?: string | null): string =>
  translateStatus(['contracts.statusLabel'], status);

export const translateVehicleStatus = (status?: string | null): string =>
  translateStatus(['vehicles.statusLabel'], status);

export const translatePaymentStatus = (status?: string | null): string =>
  translateStatus(['payments.statusLabel'], status);

export const translateFleetHealthStatus = (status?: string | null): string =>
  translateStatus(['dashboard.fleetHealthTier'], status);

export const translateVehicleCategory = (category?: string | null): string =>
  translateEnum('vehicleCategory', category, category || '');

export const translateFuelType = (fuel?: string | null): string =>
  translateEnum('fuel', fuel, fuel || '');

export const translateTransmission = (transmission?: string | null): string =>
  translateEnum('transmission', transmission, transmission || '');

export const translateFuelLevel = (level?: string | null): string =>
  translateEnum('fuelLevel', level, level || '');
