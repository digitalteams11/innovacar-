import i18n from '../i18n';

/**
 * Translates a backend status/enum code for display. Codes are never
 * translated in payloads — only here, at render time. Falls back to
 * `common.states.unknown` (translated) rather than the raw code, so a
 * status the UI doesn't recognize never leaks English/enum text.
 */
function translateStatus(namespacePrefixes: string[], status: string | null | undefined): string {
  if (!status) return i18n.t('common.states.unknown');
  const code = String(status).toUpperCase();
  for (const prefix of namespacePrefixes) {
    const key = `${prefix}.${code}`;
    if (i18n.exists(key)) return i18n.t(key);
  }
  return i18n.t('common.states.unknown');
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
