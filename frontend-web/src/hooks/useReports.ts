import { useCallback, useEffect, useState } from 'react';
import api from '../api/axios';
import type { GenerateReportRequest, GenerateReportResult, ReportType } from '../types/reports';

export interface ReportRow {
  id: number;
  reportType: ReportType;
  periodStart: string;
  periodEnd: string;
  status: 'PENDING' | 'GENERATING' | 'GENERATED' | 'EMAIL_PENDING' | 'SENT' | 'FAILED' | 'CANCELLED';
  emailStatus: 'NOT_SENT' | 'PENDING' | 'SENT' | 'FAILED';
  generatedAt: string | null;
  emailSentAt: string | null;
  language: string;
  generatedBy?: string;
  failureReason?: string | null;
  aiSummaryUsed?: boolean;
  calculationVersion?: number;
  recipientEmails?: string | null;
  emailFailureCode?: string | null;
  emailFailureReason?: string | null;
}

export interface ReportPreferences {
  tenantId: number;
  reportEnabled: boolean;
  monthlyReportEnabled: boolean;
  yearlyReportEnabled: boolean;
  reportLanguage: string;
  timezone: string | null;
  primaryRecipientEmail: string | null;
  additionalRecipientEmails: string | null;
  includeAiSummary: boolean;
  includeClientDebtDetail: boolean;
}

/** Mirrors the backend's structured send-email response (spec section 8) — never a bare boolean. */
export interface SendEmailResult {
  success: boolean;
  errorCode?: string;
  message?: string;
  data?: {
    reportId: number;
    emailStatus: ReportRow['emailStatus'];
    emailSentAt: string | null;
    recipient: string | null;
  };
}

export function useReports() {
  const [reports, setReports] = useState<ReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchReports = useCallback(async (filters?: { type?: string; status?: string }) => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/reports', { params: filters });
      setReports(data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load reports');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  /** Merges a single row's fresh server state in place — no full-list refetch/flicker needed after a send. */
  const applyRowUpdate = useCallback((reportId: number, patch: Partial<ReportRow>) => {
    setReports((prev) => prev.map((r) => (r.id === reportId ? { ...r, ...patch } : r)));
  }, []);

  /**
   * Always resolves to a structured {@link GenerateReportResult} — a thrown
   * 4xx (409 already-exists, 402 not-entitled, 400 validation) is caught and
   * normalized the same way as a 200, so the caller never has to special-case
   * "thrown" vs "returned" failures. Only sends the fields the backend DTO
   * actually reads (`reportType`/`year`/`month`/`forceRegenerate`) and never
   * includes `month` for a YEARLY request.
   */
  const generateReport = useCallback(async (request: GenerateReportRequest): Promise<GenerateReportResult> => {
    const body: GenerateReportRequest = {
      reportType: request.reportType,
      ...(request.year !== undefined ? { year: request.year } : {}),
      ...(request.reportType === 'MONTHLY' && request.month !== undefined ? { month: request.month } : {}),
      ...(request.forceRegenerate ? { forceRegenerate: true } : {}),
    };
    try {
      const { data } = await api.post<GenerateReportResult>('/reports/generate', body);
      if (data.success) await fetchReports();
      return data;
    } catch (err: any) {
      const payload: GenerateReportResult | undefined = err?.response?.data;
      if (payload && typeof payload.success === 'boolean') {
        return payload;
      }
      return { success: false, errorCode: 'REPORT_GENERATION_FAILED', message: err?.userMessage || err?.message };
    }
  }, [fetchReports]);

  /**
   * Sends (first send) or re-sends a report's email. Always resolves to a
   * structured {@link SendEmailResult} — including on a 4xx response — so the
   * caller never has to special-case thrown vs. returned failures; the precise
   * backend errorCode/message is preserved either way and the row is updated
   * from whatever state the backend actually persisted (success or failure).
   */
  const sendReportEmail = useCallback(async (id: number): Promise<SendEmailResult> => {
    try {
      const { data } = await api.post<SendEmailResult>(`/reports/${id}/send-email`);
      if (data.data) {
        applyRowUpdate(id, {
          emailStatus: data.data.emailStatus,
          emailSentAt: data.data.emailSentAt,
          recipientEmails: data.data.recipient,
          emailFailureCode: data.success ? null : (data.errorCode ?? null),
          emailFailureReason: data.success ? null : (data.message ?? null),
        });
      }
      return data;
    } catch (err: any) {
      const payload: SendEmailResult | undefined = err?.response?.data;
      if (payload && typeof payload.success === 'boolean') {
        if (payload.data) {
          applyRowUpdate(id, {
            emailStatus: payload.data.emailStatus,
            emailSentAt: payload.data.emailSentAt,
            recipientEmails: payload.data.recipient,
            emailFailureCode: payload.errorCode ?? null,
            emailFailureReason: payload.message ?? null,
          });
        }
        return payload;
      }
      return { success: false, errorCode: 'REPORT_EMAIL_SEND_FAILED', message: err?.message };
    }
  }, [applyRowUpdate]);

  const downloadReport = useCallback(async (id: number, fileName?: string) => {
    const response = await api.get(`/reports/${id}/download`, { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName || `report_${id}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  }, []);

  return { reports, loading, error, fetchReports, generateReport, sendReportEmail, downloadReport, applyRowUpdate };
}

export function useReportPreferences() {
  const [preferences, setPreferences] = useState<ReportPreferences | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchPreferences = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/report-preferences');
      setPreferences(data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPreferences();
  }, [fetchPreferences]);

  const savePreferences = useCallback(async (updates: Partial<ReportPreferences>) => {
    const { data } = await api.put('/report-preferences', updates);
    setPreferences(data);
    return data;
  }, []);

  return { preferences, loading, fetchPreferences, savePreferences };
}
