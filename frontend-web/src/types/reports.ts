/** Shared report API contract types — kept in sync with the backend DTO read by
 * `ReportController.generateReport` (com.carrental.controller.ReportController).
 * Never send `any` here: a field-name drift between this file and the backend
 * is exactly the class of bug that caused report generation to 400. */

export type ReportType = 'MONTHLY' | 'YEARLY';

export interface GenerateReportRequest {
  reportType: ReportType;
  /** Year of the target period. Omit (with month) to default to the previous closed period. */
  year?: number;
  /** 1-12 (never JavaScript's zero-based Date.getMonth()). MONTHLY only — never sent for YEARLY. */
  month?: number;
  forceRegenerate?: boolean;
}

/** Mirrors the backend's structured generate-report response (spec section 9) — never a bare boolean/empty body. */
export interface GenerateReportResult {
  success: boolean;
  errorCode?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
  data?: { reportId?: number };
  report?: unknown;
}
