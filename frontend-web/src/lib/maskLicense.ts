/**
 * Mirrors the backend's `MaskingUtil.maskLicense` exactly (server/src/main/java/com/carrental/util/MaskingUtil.java)
 * so the admin ContractDetails view and the public additional-driver signing
 * page always show the same masked value for the same license number.
 * Keeps the first 2 and last 2 characters, masks the middle with '*'.
 */
export function maskLicense(value: string | null | undefined): string {
  if (value == null) return '';
  const trimmed = value.trim();
  if (trimmed.length <= 4) {
    return '*'.repeat(trimmed.length);
  }
  const head = trimmed.slice(0, 2);
  const tail = trimmed.slice(-2);
  return `${head}${'*'.repeat(trimmed.length - 4)}${tail}`;
}
