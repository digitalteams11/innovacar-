import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useToast } from '../context/ToastContext';
import { useConfirm, usePromptText, type ConfirmOptions, type PromptOptions } from '../context/ConfirmContext';
import { usePermissions } from '../context/PermissionContext';
import Modal from '../components/Modal';
import SmartClientSearch from '../components/shared/SmartClientSearch';
import InvoicePdfPreviewModal from '../components/shared/InvoicePdfPreviewModal';
import InlineActionButton from '../components/shared/InlineActionButton';
import AnimatedStatusIcon, { type StatusPhase } from '../components/shared/AnimatedStatusIcon';
import Tooltip from '../components/shared/Tooltip';
import { logDevError, toFriendlyError } from '../lib/errorMessages';
import { cn } from '../lib/utils';
import api from '../api/axios';
import {
  Plus, Download, FileText, Trash2, CheckCircle2, Clock, AlertCircle, Loader2,
  Eye, Printer, Mail, ChevronDown, FileDown, Ban, RotateCcw, FileSignature,
} from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import { FilterChips } from '../components/FilterChips';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';
import ActionMenu from '../components/shared/ActionMenu';

interface Invoice {
  id: number;
  invoiceNumber: string;
  clientName: string;
  clientId: number;
  issueDate: string;
  dueDate: string;
  amount: number;
  status: string;
  /** Local-only, populated after a successful send in this session — the
   *  GET /invoices list endpoint does not currently return these fields
   *  (see report), so this is never hydrated from the server on load. */
  emailedAt?: string;
  emailedTo?: string;
}

/** Maps the app's i18n language to the backend's supported PDF languages. */
function toPdfLang(language: string | undefined): 'fr' | 'en' | 'ar' {
  const code = (language || 'fr').slice(0, 2).toLowerCase();
  if (code === 'en' || code === 'ar') return code;
  return 'fr';
}

/** Parses filename from a Content-Disposition header (RFC 6266 + plain form). */
function filenameFromDisposition(disposition: string, fallback: string): string {
  const match = disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)"?/i);
  return match ? decodeURIComponent(match[1]) : fallback;
}

function triggerBlobDownload(data: BlobPart, contentType: string, filename: string) {
  const blob = new Blob([data], { type: contentType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export default function Invoices() {
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [clientData, setClientData] = useState<any>({});
  const [form, setForm] = useState({ invoiceNumber: '', issueDate: new Date().toISOString().split('T')[0], dueDate: '', amount: '', status: 'PENDING' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [exporting, setExporting] = useState(false);
  const [previewInvoice, setPreviewInvoice] = useState<Invoice | null>(null);
  const [previewDownloading, setPreviewDownloading] = useState(false);

  const { showToast } = useToast();
  const confirm = useConfirm();
  const promptText = usePromptText();
  const { t, i18n } = useTranslation();
  const { hasPermission } = usePermissions();
  const lang = toPdfLang(i18n.language);

  const canExport = hasPermission('INVOICE_EXPORT');
  const canViewPdf = hasPermission('INVOICE_VIEW') || hasPermission('INVOICE_PDF_DOWNLOAD');
  const canDownloadPdf = hasPermission('INVOICE_PDF_DOWNLOAD') || hasPermission('INVOICE_VIEW');
  const canEmailSend = hasPermission('INVOICE_EMAIL_SEND');

  useEffect(() => { fetchInvoices(); }, []);

  const fetchInvoices = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/invoices');
      setData(data);
    } catch (err) {
      console.error('Failed to fetch invoices', err);
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { key: 'All', label: t('invoices.all') },
    { key: 'DRAFT', label: t('invoices.draft') },
    { key: 'ISSUED', label: t('invoices.issued') },
    { key: 'PAID', label: t('invoices.paid') },
    { key: 'PARTIALLY_PAID', label: t('invoices.partiallyPaid') },
    { key: 'PENDING', label: t('invoices.pending') },
    { key: 'OVERDUE', label: t('invoices.overdue') },
    { key: 'CANCELLED', label: t('invoices.cancelled') },
    { key: 'REFUNDED', label: t('invoices.refunded') },
  ];

  const filteredData = data.filter((i) => {
    const matchesTab = activeTab === 'All' || i.status === activeTab;
    const q = searchQuery.toLowerCase();
    return matchesTab && ((i.clientName || '').toLowerCase().includes(q) || i.invoiceNumber?.toLowerCase().includes(q));
  });

  const totalPaid = data.filter((i) => i.status === 'PAID').reduce((sum, i) => sum + i.amount, 0);
  const totalPending = data.filter((i) => i.status === 'PENDING').reduce((sum, i) => sum + i.amount, 0);
  const totalOverdue = data.filter((i) => i.status === 'OVERDUE').reduce((sum, i) => sum + i.amount, 0);

  // ── Export filter shared by the PDF (POST body) and CSV (GET query) endpoints ──
  const currentExportFilter = () => ({
    search: searchQuery.trim() || undefined,
    status: activeTab !== 'All' ? activeTab : undefined,
  });

  /**
   * Real server-side PDF report — replaces the old client-side comma-join
   * export. Mirrors Contracts.tsx's exportCSV: blob-vs-JSON-error detection
   * (axios still delivers structured error bodies as a Blob when
   * responseType is 'blob'), Content-Disposition filename parsing, and a
   * dedicated NO_MATCHING_INVOICES message instead of a generic failure.
   */
  const exportInvoicesPdf = async () => {
    if (exporting) return;
    setExporting(true);
    try {
      const response = await api.post('/invoices/export/pdf', currentExportFilter(), {
        responseType: 'blob',
        params: { lang },
      });
      const contentType = String(response.headers['content-type'] || '');
      if (contentType.includes('application/json')) {
        const text = await (response.data as Blob).text();
        const parsed = JSON.parse(text);
        const err: any = new Error(parsed.message || t('invoices.exportFailed', 'Failed to generate the PDF export.'));
        err.code = parsed.code;
        throw err;
      }
      const disposition = String(response.headers['content-disposition'] || '');
      const filename = filenameFromDisposition(disposition, 'factures.pdf');
      triggerBlobDownload(response.data, contentType || 'application/pdf', filename);
      showToast(t('toast.dataExported'));
    } catch (err: any) {
      let message = t('invoices.exportFailed', 'Failed to generate the PDF export.');
      const responseData = err?.response?.data;
      if (responseData instanceof Blob) {
        try {
          const parsed = JSON.parse(await responseData.text());
          if (parsed.code === 'NO_MATCHING_INVOICES') message = t('invoices.noMatchingInvoices', 'Aucune facture ne correspond aux filtres sélectionnés.');
          else if (parsed.message) message = parsed.message;
        } catch {
          /* body wasn't JSON either — keep the generic message */
        }
      } else if (err?.code === 'NO_MATCHING_INVOICES') {
        message = t('invoices.noMatchingInvoices', 'Aucune facture ne correspond aux filtres sélectionnés.');
      } else if (err?.message) {
        message = err.message;
      }
      showToast(message, 'error');
    } finally {
      setExporting(false);
    }
  };

  /** Secondary CSV export — same filter/error contract as the PDF export. */
  const exportInvoicesCsv = async () => {
    if (exporting) return;
    setExporting(true);
    try {
      const response = await api.get('/invoices/export/csv', {
        responseType: 'blob',
        params: currentExportFilter(),
      });
      const contentType = String(response.headers['content-type'] || '');
      if (contentType.includes('application/json')) {
        const text = await (response.data as Blob).text();
        const parsed = JSON.parse(text);
        const err: any = new Error(parsed.message || t('invoices.exportFailed', 'Failed to generate the CSV export.'));
        err.code = parsed.code;
        throw err;
      }
      const disposition = String(response.headers['content-disposition'] || '');
      const filename = filenameFromDisposition(disposition, 'factures.csv');
      triggerBlobDownload(response.data, contentType || 'text/csv', filename);
      showToast(t('toast.dataExported'));
    } catch (err: any) {
      let message = t('invoices.exportFailed', 'Failed to generate the CSV export.');
      const responseData = err?.response?.data;
      if (responseData instanceof Blob) {
        try {
          const parsed = JSON.parse(await responseData.text());
          if (parsed.code === 'NO_MATCHING_INVOICES') message = t('invoices.noMatchingInvoices', 'Aucune facture ne correspond aux filtres sélectionnés.');
          else if (parsed.message) message = parsed.message;
        } catch {
          /* body wasn't JSON either — keep the generic message */
        }
      } else if (err?.code === 'NO_MATCHING_INVOICES') {
        message = t('invoices.noMatchingInvoices', 'Aucune facture ne correspond aux filtres sélectionnés.');
      } else if (err?.message) {
        message = err.message;
      }
      showToast(message, 'error');
    } finally {
      setExporting(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    setClientData({});
    setFieldErrors({});
    setForm({ invoiceNumber: '', issueDate: new Date().toISOString().split('T')[0], dueDate: '', amount: '', status: 'PENDING' });
    setIsModalOpen(true);
  };

  const openEdit = (invoice: Invoice) => {
    setEditingId(invoice.id);
    setClientData({ clientId: invoice.clientId, clientFullName: invoice.clientName });
    setFieldErrors({});
    setForm({ invoiceNumber: invoice.invoiceNumber, issueDate: invoice.issueDate, dueDate: invoice.dueDate, amount: String(invoice.amount), status: invoice.status });
    setIsModalOpen(true);
  };

  const saveInvoice = async () => {
    const errors: Record<string, string> = {};
    if (!clientData.clientId) errors.client = t('invoices.validationErrors.clientRequired');
    if (!form.invoiceNumber.trim()) errors.invoiceNumber = t('invoices.validationErrors.invoiceNumberRequired');
    if (!form.issueDate) errors.issueDate = t('invoices.validationErrors.issueDateRequired');
    if (!form.dueDate) errors.dueDate = t('invoices.validationErrors.dueDateRequired');
    if (!form.amount.trim()) errors.amount = t('invoices.validationErrors.amountRequired');
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      showToast(t('invoices.fillRequiredFields'), 'warning');
      return;
    }
    try {
      const payload: any = {
        invoiceNumber: form.invoiceNumber,
        issueDate: form.issueDate,
        dueDate: form.dueDate,
        amount: Number(form.amount),
        status: form.status,
      };
      if (clientData.clientId) {
        payload.clientId = clientData.clientId;
      }
      if (editingId !== null) {
        await api.put(`/invoices/${editingId}`, payload);
        showToast(t('toast.success', { action: t('invoices.invoiceUpdatedAction') }));
      } else {
        await api.post('/invoices', payload);
        showToast(t('toast.success', { action: t('invoices.invoiceCreatedAction') }));
      }
      setIsModalOpen(false);
      setFieldErrors({});
      fetchInvoices();
    } catch (err: any) {
      showToast((err as any).userMessage || t('invoices.saveFailed'), 'error');
    }
  };

  const updateFormField = (field: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const selectClient = (client: any) => {
    setClientData(client);
    setFieldErrors((prev) => {
      if (!prev.client) return prev;
      const next = { ...prev };
      delete next.client;
      return next;
    });
  };

  const inputClass = (field: string) =>
    `w-full px-4 py-2.5 bg-[#f5f5f0] border rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white transition-all ${
      fieldErrors[field] ? 'border-danger-500 focus:border-danger-500' : 'border-[#e8e6e1] focus:border-brand-300'
    }`;

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

  const deleteInvoice = async (id: number) => {
    const confirmed = await confirm({
      title: t('confirm.deleteItem.title', 'Delete this item?'),
      description: t('invoices.deleteConfirm') || 'Delete this invoice?',
      confirmLabel: t('common.delete', 'Delete'),
      cancelLabel: t('actions.cancel', 'Cancel'),
      tone: 'danger',
    });
    if (!confirmed) return;
    try {
      await api.delete(`/invoices/${id}`);
      fetchInvoices();
      showToast(t('invoices.deletedSuccess'), 'success');
    } catch (err) {
      showToast(t('invoices.deleteFailed'), 'error');
    }
  };

  const STATUS_BADGE_CONFIG: Record<string, { className: string; icon: any }> = {
    PAID: { className: 'bg-success-50 text-success-500', icon: CheckCircle2 },
    PARTIALLY_PAID: { className: 'bg-success-50 text-success-500', icon: CheckCircle2 },
    PENDING: { className: 'bg-warning-50 text-warning-500', icon: Clock },
    ISSUED: { className: 'bg-warning-50 text-warning-500', icon: Clock },
    DRAFT: { className: 'bg-slate-100 text-slate-500', icon: FileSignature },
    OVERDUE: { className: 'bg-danger-50 text-danger-500', icon: AlertCircle },
    CANCELLED: { className: 'bg-slate-100 text-slate-500', icon: Ban },
    REFUNDED: { className: 'bg-brand-50 text-brand-500', icon: RotateCcw },
  };

  const statusLabel = (status: string) => {
    switch (status) {
      case 'PAID': return t('invoices.paid');
      case 'PENDING': return t('invoices.pending');
      case 'OVERDUE': return t('invoices.overdue');
      case 'DRAFT': return t('invoices.draft');
      case 'ISSUED': return t('invoices.issued');
      case 'PARTIALLY_PAID': return t('invoices.partiallyPaid');
      case 'CANCELLED': return t('invoices.cancelled');
      case 'REFUNDED': return t('invoices.refunded');
      default: return status;
    }
  };

  const invoiceStatusBadge = (invoice: Invoice) => {
    const config = STATUS_BADGE_CONFIG[invoice.status] || STATUS_BADGE_CONFIG.PENDING;
    const Icon = config.icon;
    return (
      <span className={`inline-flex w-fit items-center gap-1.5 rounded-lg px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${config.className}`}>
        <Icon size={12} />
        {statusLabel(invoice.status)}
      </span>
    );
  };

  const markAsPaid = async (id: number) => {
    try {
      await api.patch(`/invoices/${id}/pay`);
      fetchInvoices();
      showToast(t('toast.success', { action: 'Invoice marked as paid' }));
    } catch (err) {
      showToast(t('invoices.markPaidFailed'), 'error');
    }
  };

  const handleEmailSent = (id: number, emailedTo: string) => {
    setData((current) => current.map((inv) => (
      inv.id === id ? { ...inv, emailedAt: new Date().toISOString(), emailedTo } : inv
    )));
  };

  const openPreview = (invoice: Invoice) => setPreviewInvoice(invoice);
  const closePreview = () => setPreviewInvoice(null);

  const downloadInvoicePdf = async (invoice: Invoice) => {
    const response = await api.get(`/invoices/${invoice.id}/pdf`, {
      responseType: 'blob',
      params: { mode: 'attachment', lang },
    });
    const disposition = String(response.headers['content-disposition'] || '');
    const filename = filenameFromDisposition(disposition, `facture-${invoice.invoiceNumber}.pdf`);
    triggerBlobDownload(response.data, 'application/pdf', filename);
  };

  const printInvoicePdf = async (invoice: Invoice) => {
    const response = await api.get(`/invoices/${invoice.id}/pdf`, {
      responseType: 'blob',
      params: { mode: 'inline', lang },
    });
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    window.setTimeout(() => URL.revokeObjectURL(url), 60000);
  };

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title={t('invoices.title')}
        subtitle={t('invoices.subtitle')}
        icon={FileText}
        actions={<>
          {canExport && (
            <ActionMenu
              ariaLabel={t('invoices.exportMenu', 'Export options')}
              disabled={exporting}
              triggerClassName="surface-control flex h-10 items-center gap-2 px-4 font-medium text-sm active:scale-95 disabled:cursor-wait disabled:opacity-60"
              trigger={<>
                {exporting ? <Loader2 size={18} className="animate-spin" /> : <Download size={18} />}
                {t('invoices.exportPdf', 'Exporter en PDF')}
                <ChevronDown size={14} />
              </>}
              items={[
                { label: t('invoices.exportPdf', 'Exporter en PDF'), icon: <FileDown size={15} />, onClick: exportInvoicesPdf, disabled: exporting },
                { label: t('invoices.exportCsv', 'Exporter en CSV'), icon: <Download size={15} />, onClick: exportInvoicesCsv, disabled: exporting },
              ]}
            />
          )}
          <button onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
            <Plus size={18} /> {t('invoices.newInvoice')}
          </button>
        </>}
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
        <div className="metric-surface">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalPaid')}</p>
          <h3 className="text-xl font-bold text-success-500">{totalPaid.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'PAID').length} {t('invoices.invoices')}</p>
        </div>
        <div className="metric-surface">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalPending')}</p>
          <h3 className="text-xl font-bold text-warning-500">{totalPending.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'PENDING').length} {t('invoices.invoices')}</p>
        </div>
        <div className="metric-surface">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalOverdue')}</p>
          <h3 className="text-xl font-bold text-danger-500">{totalOverdue.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'OVERDUE').length} {t('invoices.invoices')}</p>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <FilterChips options={tabs.map((tab) => ({ id: tab.key, label: tab.label }))} activeId={activeTab} onChange={setActiveTab} />
        <SearchInput className="w-full lg:w-[360px]" placeholder={t('invoices.searchPlaceholder')} value={searchQuery} onChange={setSearchQuery} />
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <ResponsiveDataView
          mobile={
            filteredData.length === 0 ? (
              <div className="data-surface py-10 text-center text-sm text-slate-400">{t('invoices.noInvoicesFound', 'No invoices found')}</div>
            ) : (
              <div className="space-y-3">
                {filteredData.map((invoice) => (
                  <div key={invoice.id} className="data-surface space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5 font-mono text-xs font-bold text-slate-400">
                          <FileText size={13} /> {invoice.invoiceNumber}
                        </span>
                        <h3 className="mt-1 truncate text-sm font-semibold text-[#1e293b] dark:text-white">{invoice.clientName}</h3>
                      </div>
                      {invoiceStatusBadge(invoice)}
                    </div>
                    <div className="flex items-center justify-between text-xs text-slate-400">
                      <span>{new Date(invoice.issueDate).toLocaleDateString()} → {new Date(invoice.dueDate).toLocaleDateString()}</span>
                      <span className="text-sm font-bold text-[#1e293b] dark:text-white">{invoice.amount.toLocaleString()} MAD</span>
                    </div>
                    <div className="flex flex-wrap items-center gap-1 border-t border-[var(--border-subtle)] pt-3">
                      {canViewPdf && (
                        <button
                          onClick={() => openPreview(invoice)}
                          aria-label={t('invoices.view', 'Voir')}
                          className="flex h-11 w-11 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
                        >
                          <Eye size={17} />
                        </button>
                      )}
                      {canDownloadPdf && (
                        <InlineActionButton
                          icon={Download}
                          label={t('invoices.download', 'Télécharger')}
                          onAction={() => downloadInvoicePdf(invoice)}
                          context={`invoice-download-${invoice.id}`}
                        />
                      )}
                      {canDownloadPdf && (
                        <InlineActionButton
                          icon={Printer}
                          label={t('invoices.print', 'Imprimer')}
                          onAction={() => printInvoicePdf(invoice)}
                          context={`invoice-print-${invoice.id}`}
                        />
                      )}
                      {canEmailSend && (
                        <InvoiceEmailButton invoice={invoice} lang={lang} t={t} confirm={confirm} promptText={promptText} onSent={handleEmailSent} />
                      )}
                      <div className="ml-auto flex items-center gap-2">
                        {invoice.status !== 'PAID' && (
                          <button
                            onClick={() => markAsPaid(invoice.id)}
                            className="flex min-h-11 items-center justify-center gap-2 rounded-lg bg-success-50 px-3 text-sm font-semibold text-success-600"
                          >
                            <CheckCircle2 size={15} /> {t('invoices.markAsPaid', 'Mark as paid')}
                          </button>
                        )}
                        <button
                          onClick={() => openEdit(invoice)}
                          className="flex min-h-11 items-center justify-center gap-2 rounded-lg bg-brand-50 px-3 text-sm font-semibold text-brand-600"
                        >
                          <FileText size={15} /> {t('common.edit', 'Edit')}
                        </button>
                        <ActionMenu
                          ariaLabel={t('invoices.actions')}
                          items={[
                            { label: t('common.delete', 'Delete'), icon: <Trash2 size={15} />, onClick: () => deleteInvoice(invoice.id), danger: true },
                          ]}
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )
          }
          desktop={
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">{t('invoices.invoiceId')}</th>
                  <th className="px-5 py-4">{t('invoices.client')}</th>
                  <th className="px-5 py-4">{t('invoices.date')}</th>
                  <th className="px-5 py-4">{t('invoices.dueDate')}</th>
                  <th className="px-5 py-4">{t('invoices.amount')}</th>
                  <th className="px-5 py-4">{t('invoices.status')}</th>
                  <th className="px-5 py-4 text-right">{t('invoices.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/50">
                {filteredData.map((invoice) => (
                  <tr key={invoice.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <FileText size={14} className="text-slate-400 group-hover:text-brand-500 transition-colors" />
                        <span className="font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">{invoice.invoiceNumber}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 font-medium text-[#1e293b]">{invoice.clientName}</td>
                    <td className="px-5 py-4 text-sm text-slate-400 font-normal">{new Date(invoice.issueDate).toLocaleDateString()}</td>
                    <td className="px-5 py-4 text-sm text-slate-400 font-normal">{new Date(invoice.dueDate).toLocaleDateString()}</td>
                    <td className="px-5 py-4 font-bold text-[#1e293b]">{invoice.amount.toLocaleString()} MAD</td>
                    <td className="px-5 py-4">{invoiceStatusBadge(invoice)}</td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {canViewPdf && (
                          <button
                            onClick={() => openPreview(invoice)}
                            aria-label={t('invoices.view', 'Voir')}
                            className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"
                          >
                            <Eye size={17} />
                          </button>
                        )}
                        {canDownloadPdf && (
                          <InlineActionButton
                            icon={Download}
                            label={t('invoices.download', 'Télécharger')}
                            onAction={() => downloadInvoicePdf(invoice)}
                            context={`invoice-download-${invoice.id}`}
                          />
                        )}
                        {canDownloadPdf && (
                          <InlineActionButton
                            icon={Printer}
                            label={t('invoices.print', 'Imprimer')}
                            onAction={() => printInvoicePdf(invoice)}
                            context={`invoice-print-${invoice.id}`}
                          />
                        )}
                        {canEmailSend && (
                          <InvoiceEmailButton invoice={invoice} lang={lang} t={t} confirm={confirm} promptText={promptText} onSent={handleEmailSent} />
                        )}
                        {invoice.status !== 'PAID' && (
                          <button onClick={() => markAsPaid(invoice.id)} className="px-3 py-1.5 bg-success-50 text-success-500 rounded-lg text-[10px] font-bold uppercase tracking-wider hover:bg-success-500 hover:text-white transition-all">{t('invoices.pay')}</button>
                        )}
                        <button onClick={() => openEdit(invoice)} className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"><FileText size={17} /></button>
                        <button onClick={() => deleteInvoice(invoice.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredData.length === 0 && (
                  <tr><td colSpan={7} className="px-5 py-8 text-center text-slate-400 text-sm">{t('invoices.noInvoicesFound')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
          }
        />
      )}

      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingId ? 'Edit Invoice' : t('invoices.newInvoice')}
        footer={
          <button onClick={saveInvoice} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">{editingId ? 'Save Changes' : t('invoices.newInvoice')}</button>
        }
      >
        <div className="space-y-4">
          <SmartClientSearch value={clientData} onSelect={selectClient} required />
          {fieldError('client')}
          {clientData.clientFullName && (
            <div className="p-3 bg-brand-50/50 rounded-xl border border-brand-100 text-sm">
              <span className="text-brand-500 font-medium">{clientData.clientFullName}</span>
            </div>
          )}
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Invoice Number *</label><input type="text" value={form.invoiceNumber} onChange={(e) => updateFormField('invoiceNumber', e.target.value)} aria-invalid={Boolean(fieldErrors.invoiceNumber)} className={inputClass('invoiceNumber')} />{fieldError('invoiceNumber')}</div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.date')} *</label><input type="date" value={form.issueDate} onChange={(e) => updateFormField('issueDate', e.target.value)} aria-invalid={Boolean(fieldErrors.issueDate)} className={inputClass('issueDate')} />{fieldError('issueDate')}</div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.dueDate')} *</label><input type="date" value={form.dueDate} onChange={(e) => updateFormField('dueDate', e.target.value)} aria-invalid={Boolean(fieldErrors.dueDate)} className={inputClass('dueDate')} />{fieldError('dueDate')}</div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.amount')} (MAD) *</label><input type="number" value={form.amount} onChange={(e) => updateFormField('amount', e.target.value)} aria-invalid={Boolean(fieldErrors.amount)} className={inputClass('amount')} />{fieldError('amount')}</div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.status')}</label>
              <select value={form.status} onChange={(e) => updateFormField('status', e.target.value)} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="DRAFT">{t('invoices.draft')}</option>
                <option value="ISSUED">{t('invoices.issued')}</option>
                <option value="PENDING">{t('invoices.pending')}</option>
                <option value="PARTIALLY_PAID">{t('invoices.partiallyPaid')}</option>
                <option value="PAID">{t('invoices.paid')}</option>
                <option value="OVERDUE">{t('invoices.overdue')}</option>
                <option value="CANCELLED">{t('invoices.cancelled')}</option>
                <option value="REFUNDED">{t('invoices.refunded')}</option>
              </select>
            </div>
          </div>
        </div>
      </Modal>

      {previewInvoice && (
        <InvoicePdfPreviewModal
          isOpen={Boolean(previewInvoice)}
          invoiceId={previewInvoice.id}
          invoiceNumber={previewInvoice.invoiceNumber}
          lang={lang}
          downloading={previewDownloading}
          onClose={closePreview}
          onDownload={async () => {
            if (previewDownloading) return;
            setPreviewDownloading(true);
            try {
              await downloadInvoicePdf(previewInvoice);
            } catch (err) {
              showToast(t('invoices.downloadFailed', 'Unable to download this invoice PDF.'), 'error');
            } finally {
              setPreviewDownloading(false);
            }
          }}
        />
      )}
    </div>
  );
}

interface InvoiceEmailButtonProps {
  invoice: Invoice;
  lang: string;
  t: TFunction;
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  promptText: (options: PromptOptions) => Promise<string | null>;
  onSent: (id: number, emailedTo: string) => void;
}

/**
 * Per-invoice send/resend action — never optimistic: the icon only turns
 * green once POST /invoices/{id}/email confirms success. Resolves the
 * client's email on file first (GET /clients/{id}) and asks for confirmation
 * before sending; if no address is on file it prompts for one instead.
 * Cancelling either dialog leaves the button in its idle state (not an
 * error) — only a real failed send flips it to the red/retry state.
 */
function InvoiceEmailButton({ invoice, lang, t, confirm, promptText, onSent }: InvoiceEmailButtonProps) {
  const [phase, setPhase] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const settleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (settleTimerRef.current) clearTimeout(settleTimerRef.current);
  }, []);

  const handleClick = async () => {
    if (phase === 'loading') return;

    let targetEmail: string | null = null;
    if (invoice.clientId) {
      try {
        const { data } = await api.get(`/clients/${invoice.clientId}`);
        targetEmail = data?.email || null;
      } catch {
        /* best-effort — falls through to the manual-entry prompt below */
      }
    }

    if (!targetEmail) {
      const entered = await promptText({
        title: t('invoices.emailConfirmTitle', 'Envoyer la facture par e-mail ?'),
        description: t('invoices.emailNoAddressOnFile', 'Aucune adresse e-mail connue pour ce client. Saisissez une adresse pour envoyer la facture.'),
        placeholder: t('invoices.emailPlaceholder', 'client@exemple.com'),
        confirmLabel: t('invoices.sendEmail', 'Envoyer'),
        cancelLabel: t('actions.cancel', 'Cancel'),
        required: true,
      });
      if (!entered) return; // cancelled — stay idle, not an error
      targetEmail = entered.trim();
    } else {
      const confirmed = await confirm({
        title: t('invoices.emailConfirmTitle', 'Envoyer la facture par e-mail ?'),
        description: t('invoices.emailConfirmBody', { email: targetEmail, defaultValue: `Envoyer la facture ${invoice.invoiceNumber} à ${targetEmail} ?` }),
        confirmLabel: t('invoices.sendEmail', 'Envoyer'),
        cancelLabel: t('actions.cancel', 'Cancel'),
        tone: 'info',
      });
      if (!confirmed) return; // cancelled — stay idle, not an error
    }

    if (settleTimerRef.current) clearTimeout(settleTimerRef.current);
    setPhase('loading');
    setErrorMsg(null);
    try {
      const { data } = await api.post(
        `/invoices/${invoice.id}/email`,
        targetEmail ? { toEmail: targetEmail } : {},
        { params: { lang } },
      );
      if (!data?.success) {
        throw new Error(data?.message || t('invoices.emailSendFailed', 'Failed to send the invoice email.'));
      }
      onSent(invoice.id, data.emailedTo || targetEmail || '');
      setPhase('success');
      settleTimerRef.current = setTimeout(() => setPhase('idle'), 2000);
    } catch (err) {
      logDevError(`invoice-email-${invoice.id}`, err);
      setErrorMsg(toFriendlyError(err, t('invoices.emailSendFailed', 'Failed to send the invoice email.')).message);
      setPhase('error');
    }
  };

  const alreadySent = Boolean(invoice.emailedAt);
  const displayPhase: StatusPhase = phase !== 'idle' ? phase : alreadySent ? 'success' : 'idle';
  const label = phase === 'error'
    ? (errorMsg || t('invoices.emailSendFailed', 'Failed to send the invoice email.'))
    : displayPhase === 'success' && invoice.emailedAt
      ? t('invoices.emailSentAt', { date: new Date(invoice.emailedAt).toLocaleString(), defaultValue: `Envoyée le ${new Date(invoice.emailedAt).toLocaleString()}` })
      : t('invoices.sendEmail', 'Envoyer par e-mail');

  return (
    <Tooltip label={label}>
      <button
        type="button"
        onClick={handleClick}
        disabled={phase === 'loading'}
        aria-label={label}
        aria-live="polite"
        className={cn(
          'relative flex min-h-11 min-w-11 items-center justify-center rounded-lg p-2 transition-colors',
          'hover:bg-[var(--bg-hover)] disabled:cursor-not-allowed disabled:opacity-40',
          displayPhase === 'success' && 'text-emerald-600 dark:text-emerald-400',
          displayPhase === 'error' && 'text-red-600 dark:text-red-400',
        )}
      >
        <AnimatedStatusIcon phase={displayPhase} idleIcon={Mail} successIcon={CheckCircle2} />
      </button>
    </Tooltip>
  );
}
