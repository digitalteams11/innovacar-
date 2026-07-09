import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import {
  Database, Download, FileSpreadsheet, ShieldAlert, Loader2, ExternalLink,
} from 'lucide-react';

interface PrivacyTabProps {
  inspectionRetentionDays: number;
}

function toCsv(rows: any[], columns: string[]): string {
  const escape = (value: any) => {
    const str = value === null || value === undefined ? '' : String(value);
    return `"${str.replace(/"/g, '""')}"`;
  };
  const header = columns.map(escape).join(',');
  const body = rows.map((row) => columns.map((col) => escape(row[col])).join(',')).join('\n');
  return `${header}\n${body}`;
}

function downloadFile(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export default function PrivacyTab({ inspectionRetentionDays }: PrivacyTabProps) {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [exporting, setExporting] = useState<string | null>(null);
  const [showDeactivateModal, setShowDeactivateModal] = useState(false);
  const [deactivateReason, setDeactivateReason] = useState('');
  const [deactivateConfirm, setDeactivateConfirm] = useState(false);
  const [submittingDeactivate, setSubmittingDeactivate] = useState(false);

  const exportData = async (key: 'vehicles' | 'clients' | 'contracts' | 'reservations') => {
    setExporting(key);
    try {
      const { data } = await api.get(`/${key}`);
      const rows = Array.isArray(data) ? data : data?.data || [];
      if (rows.length === 0) {
        showToast(`No ${key} found to export.`, 'warning');
        return;
      }
      const columns = Object.keys(rows[0]).filter((k) => typeof rows[0][k] !== 'object');
      const csv = toCsv(rows, columns);
      downloadFile(`${key}-export-${new Date().toISOString().split('T')[0]}.csv`, csv, 'text/csv');
      showToast(`${key.charAt(0).toUpperCase() + key.slice(1)} exported successfully.`, 'success');
    } catch (err: any) {
      showToast(err?.userMessage || `Unable to export ${key}. Please try again later.`, 'error');
    } finally {
      setExporting(null);
    }
  };

  const downloadAccountData = async () => {
    setExporting('account');
    try {
      const [agencyRes, settingsRes] = await Promise.all([
        api.get('/agency'),
        api.get('/tenant-settings'),
      ]);
      const payload = {
        agency: agencyRes.data,
        operationalSettings: { ...settingsRes.data, smtpPasswordEncrypted: undefined },
        exportedAt: new Date().toISOString(),
      };
      downloadFile(`account-data-${new Date().toISOString().split('T')[0]}.json`, JSON.stringify(payload, null, 2), 'application/json');
      showToast('Account data downloaded successfully.', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to download account data. Please try again later.', 'error');
    } finally {
      setExporting(null);
    }
  };

  const submitDeactivationRequest = async () => {
    if (!deactivateConfirm) {
      showToast('Please confirm you understand this request before submitting.', 'warning');
      return;
    }
    setSubmittingDeactivate(true);
    try {
      await api.post('/operations-center/tickets', {
        subject: 'Account deactivation request',
        description: deactivateReason.trim() || 'Agency admin requested account deactivation from Settings > Privacy & Data.',
        kind: 'SUPPORT',
        category: 'ACCOUNT',
        priority: 'HIGH',
      });
      showToast('Deactivation request submitted. Our team will contact you to confirm.', 'success');
      setShowDeactivateModal(false);
      setDeactivateReason('');
      setDeactivateConfirm(false);
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to submit your request. Please try again later.', 'error');
    } finally {
      setSubmittingDeactivate(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Data Export */}
      <div className="card-premium p-4 sm:p-6 space-y-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <FileSpreadsheet size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Export Your Data</h3>
            <p className="text-sm text-slate-400 font-normal">Download your agency data as CSV files</p>
          </div>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {(['vehicles', 'clients', 'contracts', 'reservations'] as const).map((key) => (
            <button
              key={key}
              onClick={() => exportData(key)}
              disabled={exporting === key}
              className="flex items-center justify-center gap-2 px-3 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-medium hover:bg-white hover:border-brand-300 transition-all disabled:opacity-60"
            >
              {exporting === key ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
              {key.charAt(0).toUpperCase() + key.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {/* Account data download */}
      <div className="card-premium p-4 sm:p-6 space-y-4">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <Database size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Download Account Data</h3>
            <p className="text-sm text-slate-400 font-normal">Your agency profile and operational settings as JSON</p>
          </div>
        </div>
        <button
          onClick={downloadAccountData}
          disabled={exporting === 'account'}
          className="flex items-center gap-2 px-4 py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium hover:bg-brand-600 transition-all disabled:opacity-60"
        >
          {exporting === 'account' ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
          Download Account Data
        </button>
      </div>

      {/* Data retention */}
      <div className="card-premium p-4 sm:p-6 space-y-2">
        <h3 className="text-base font-bold text-[#1e293b]">Data Retention</h3>
        <p className="text-sm text-slate-500">
          Vehicle inspection photos and videos are kept for <span className="font-semibold text-[#1e293b]">{inspectionRetentionDays} days</span> after the reservation end date, based on your Operations settings. Other agency records (clients, contracts, reservations) are retained for as long as your account remains active.
        </p>
        <button onClick={() => navigate('/operations-center')} className="inline-flex items-center gap-1.5 text-xs font-medium text-brand-600 hover:text-brand-700 mt-1">
          Contact support about data requests <ExternalLink size={12} />
        </button>
      </div>

      {/* Danger zone */}
      <div className="card-premium p-4 sm:p-6 space-y-4 border border-rose-100">
        <div className="flex items-center gap-3 pb-5 border-b border-rose-100">
          <div className="w-10 h-10 rounded-xl bg-rose-50 flex items-center justify-center">
            <ShieldAlert size={20} className="text-rose-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Danger Zone</h3>
            <p className="text-sm text-slate-400 font-normal">Request account deactivation</p>
          </div>
        </div>
        <p className="text-sm text-slate-500">
          Requesting account deactivation opens a support ticket reviewed by our team. We do not delete your data automatically — this protects you from accidental loss.
        </p>
        <button
          onClick={() => setShowDeactivateModal(true)}
          className="px-4 py-2.5 bg-rose-50 text-rose-600 rounded-xl text-sm font-medium hover:bg-rose-100 transition-all"
        >
          Request Account Deactivation
        </button>
      </div>

      {showDeactivateModal && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowDeactivateModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">Request Account Deactivation</h3>
            <p className="text-sm text-slate-500 mb-4">
              This will open a support ticket with our team to review and process your deactivation request. Your data will not be deleted automatically.
            </p>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">Reason (optional)</label>
            <textarea
              value={deactivateReason}
              onChange={(e) => setDeactivateReason(e.target.value)}
              rows={3}
              className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm mb-4 focus:outline-none focus:ring-2 ring-rose-100 resize-none"
            />
            <label className="flex items-start gap-2 mb-4 text-sm text-slate-600">
              <input type="checkbox" checked={deactivateConfirm} onChange={(e) => setDeactivateConfirm(e.target.checked)} className="mt-0.5 w-4 h-4 accent-rose-500" />
              I understand this request will be reviewed by the RentCar team before any action is taken.
            </label>
            <div className="flex gap-3">
              <button
                onClick={submitDeactivationRequest}
                disabled={submittingDeactivate}
                className="flex-1 bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {submittingDeactivate ? 'Submitting...' : 'Submit Request'}
              </button>
              <button onClick={() => setShowDeactivateModal(false)} className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
