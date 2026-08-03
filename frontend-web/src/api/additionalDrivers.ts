import api from './axios';

/** Mirrors the backend `SignatureStatus` enum exactly (entity package) — used as-is over the wire. */
export type SignatureStatus =
  | 'NOT_REQUIRED'
  | 'PENDING'
  | 'LINK_SENT'
  | 'OPENED'
  | 'SIGNED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'REVOKED';

/** Mirrors `AdditionalDriverDto` (server/src/main/java/com/carrental/dto/contract/AdditionalDriverDto.java). */
export interface AdditionalDriverDto {
  id: number;
  fullName: string;
  cin?: string | null;
  passportNumber?: string | null;
  driverLicenseNumber?: string | null;
  nationality?: string | null;
  address?: string | null;
  phone?: string | null;
  birthDate?: string | null;
  email?: string | null;
  signatureRequired: boolean;
  signatureStatus: SignatureStatus;
  linkSentAt?: string | null;
  openedAt?: string | null;
  signedAt?: string | null;
  declinedAt?: string | null;
}

/** Mirrors `AdditionalDriverRequest` — POST/PUT body. */
export interface AdditionalDriverRequest {
  fullName: string;
  cin?: string | null;
  passportNumber?: string | null;
  driverLicenseNumber?: string | null;
  nationality?: string | null;
  address?: string | null;
  phone?: string | null;
  birthDate?: string | null;
  email?: string | null;
  signatureRequired?: boolean | null;
  /** Only send true if the user explicitly confirms after a "duplicates main client" error. */
  allowDuplicateOfMainClient?: boolean | null;
}

/** Mirrors `AdditionalDriverSignatureView` (GET .../signature) — admin-only, includes the full signature image. */
export interface AdditionalDriverSignatureView {
  id: number;
  fullName: string;
  signatureStatus: SignatureStatus;
  signatureData?: string | null;
  signedAt?: string | null;
  signedIp?: string | null;
  signedUserAgent?: string | null;
  declarationVersion?: string | null;
  /** JSON string — parse to see which declarations were accepted. */
  declarationsAccepted?: string | null;
  declinedAt?: string | null;
  declineReason?: string | null;
}

/** Mirrors `AdditionalDriverSignatureLinkResponse`. */
export interface AdditionalDriverSignatureLinkResponse {
  signingUrl: string;
  expiresAt: string;
}

const base = (contractId: number | string) => `/contracts/${contractId}/additional-drivers`;

export function listAdditionalDrivers(contractId: number | string) {
  return api.get<AdditionalDriverDto[]>(base(contractId));
}

export function getAdditionalDriverSignature(contractId: number | string, driverId: number | string) {
  return api.get<AdditionalDriverSignatureView>(`${base(contractId)}/${driverId}/signature`);
}

export function createAdditionalDriver(contractId: number | string, payload: AdditionalDriverRequest) {
  return api.post<AdditionalDriverDto>(base(contractId), payload);
}

export function updateAdditionalDriver(contractId: number | string, driverId: number | string, payload: AdditionalDriverRequest) {
  return api.put<AdditionalDriverDto>(`${base(contractId)}/${driverId}`, payload);
}

export function deleteAdditionalDriver(contractId: number | string, driverId: number | string, confirm = false) {
  return api.delete(`${base(contractId)}/${driverId}`, { params: { confirm } });
}

export function sendAdditionalDriverSignatureLink(contractId: number | string, driverId: number | string) {
  return api.post<AdditionalDriverSignatureLinkResponse>(`${base(contractId)}/${driverId}/signature-link`);
}

export function resendAdditionalDriverSignatureLink(contractId: number | string, driverId: number | string) {
  return api.post<AdditionalDriverSignatureLinkResponse>(`${base(contractId)}/${driverId}/signature-link/resend`);
}

export function revokeAdditionalDriverSignatureLink(contractId: number | string, driverId: number | string) {
  return api.post<void>(`${base(contractId)}/${driverId}/signature-link/revoke`);
}

// ── Public (no-login) endpoints ──────────────────────────────────────────
// Never authenticated — token in the URL is the only credential. Never log it.

export interface PublicAdditionalDriverSigningResponse {
  driverName: string;
  maskedLicense?: string | null;
  contractNumber: string;
  agencyName?: string | null;
  agencyLogo?: string | null;
  vehicleSummary?: string | null;
  rentalStartDate?: string | null;
  rentalEndDate?: string | null;
  mainClientName?: string | null;
  signatureStatus: SignatureStatus;
  declarationVersion?: string | null;
  contractCancelled: boolean;
}

export interface AdditionalDriverDeclarationsAccepted {
  identityCorrect: boolean;
  licenseValid: boolean;
  responsibilityAccepted: boolean;
  termsRead: boolean;
  dataConsent: boolean;
}

export interface AdditionalDriverSignatureSubmitRequest {
  declarationsAccepted: AdditionalDriverDeclarationsAccepted;
  signatureData: string;
  deviceInfo?: string;
}

export interface AdditionalDriverDeclineRequest {
  reason?: string | null;
}

/** Typed shape of the public-signing error body (GlobalExceptionHandler.handlePublicSigning). */
export interface PublicSigningErrorBody {
  success: false;
  code: 'INVALID' | 'EXPIRED' | 'REVOKED' | 'ALREADY_SIGNED' | 'CANCELLED' | 'NOT_REQUIRED' | 'VALIDATION';
  message: string;
}

const publicBase = (token: string) => `/public/additional-driver-signatures/${token}`;

export function getPublicAdditionalDriverSigning(token: string) {
  return api.get<PublicAdditionalDriverSigningResponse>(publicBase(token));
}

export function markPublicAdditionalDriverOpened(token: string) {
  return api.post<void>(`${publicBase(token)}/open`);
}

export function submitPublicAdditionalDriverSignature(token: string, payload: AdditionalDriverSignatureSubmitRequest) {
  return api.post<void>(`${publicBase(token)}/sign`, payload);
}

export function declinePublicAdditionalDriverSignature(token: string, payload: AdditionalDriverDeclineRequest) {
  return api.post<void>(`${publicBase(token)}/decline`, payload);
}
