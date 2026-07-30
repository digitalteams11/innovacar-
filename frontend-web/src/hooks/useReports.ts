import { useCallback, useEffect, useState } from 'react';
import api from '../api/axios';

export interface ReportRow {
  id: number;
  reportType: 'MONTHLY' | 'YEARLY';
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

  const generateReport = useCallback(async (reportType: 'MONTHLY' | 'YEARLY', year?: number, month?: number) => {
    const { data } = await api.post('/reports/generate', { reportType, year, month });
    if (data.success) await fetchReports();
    return data;
  }, [fetchReports]);

  const resendReport = useCallback(async (id: number) => {
    const { data } = await api.post(`/reports/${id}/resend`);
    await fetchReports();
    return data;
  }, [fetchReports]);

  const downloadReport = useCallback(async (id: number, fileName?: string) => {
    const response = await api.get(`/reports/${id}/download`, { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName || `report_${id}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  }, []);

  return { reports, loading, error, fetchReports, generateReport, resendReport, downloadReport };
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
