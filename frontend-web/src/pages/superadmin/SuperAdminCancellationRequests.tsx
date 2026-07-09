import { useEffect, useState } from 'react';
import { superAdminApi } from '../../api/superAdminApi';
import { useToast } from '../../context/ToastContext';
import { CheckCircle2, XCircle, ChevronDown } from 'lucide-react';

interface CancellationRequestRow {
  id: number;
  tenantId: number;
  agencyName: string;
  reason: string;
  feedback?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNote?: string;
  createdAt: string;
}

const REASON_LABELS: Record<string, string> = {
  TOO_EXPENSIVE: 'Too expensive',
  MISSING_FEATURES: 'Missing features',
  SWITCHING_PROVIDER: 'Switching provider',
  TEMPORARY_PAUSE: 'Temporary pause',
  BUSINESS_CLOSED: 'Business closed',
  TECHNICAL_ISSUES: 'Technical issues',
  OTHER: 'Other',
};

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  APPROVED: 'bg-rose-50 text-rose-700 border-rose-200',
  REJECTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CANCELLED: 'bg-slate-50 text-slate-600 border-slate-200',
};

export default function SuperAdminCancellationRequests() {
  const { showToast } = useToast();
  const [requests, setRequests] = useState<CancellationRequestRow[]>([]);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await superAdminApi.getCancellationRequests(statusFilter || undefined);
      setRequests(data);
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to load cancellation requests.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [statusFilter]);

  const approve = async (req: CancellationRequestRow) => {
    if (!window.confirm(`Approve cancellation for ${req.agencyName}? Their subscription will be cancelled immediately.`)) return;
    setBusyId(req.id);
    try {
      await superAdminApi.approveCancellationRequest(req.id);
      showToast('Cancellation request approved. Subscription cancelled.', 'success');
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to approve this request.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const reject = async (req: CancellationRequestRow) => {
    const note = window.prompt(`Reject cancellation for ${req.agencyName}. Optional note for the record:`) || undefined;
    setBusyId(req.id);
    try {
      await superAdminApi.rejectCancellationRequest(req.id, note);
      showToast('Cancellation request rejected.', 'success');
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to reject this request.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">Cancellation Requests</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">Review and approve or reject agency cancellation requests.</p>
        </div>
        <div className="relative">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="appearance-none bg-white dark:bg-[#1a2332]/70 px-4 py-2.5 pr-10 rounded-xl shadow-soft border border-[#e8e6e1]/80 dark:border-white/5 text-sm text-[#1e293b] dark:text-white cursor-pointer outline-none"
          >
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="">All</option>
          </select>
          <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
        </div>
      </div>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Agency</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Reason</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Feedback</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Status</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Requested</th>
                <th className="text-right text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6} className="text-center py-12 text-slate-400">Loading...</td></tr>
              ) : requests.length === 0 ? (
                <tr><td colSpan={6} className="text-center py-12 text-slate-400">No cancellation requests found.</td></tr>
              ) : (
                requests.map((req) => (
                  <tr key={req.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                    <td className="px-5 py-4 text-sm font-medium text-[#1e293b] dark:text-white">{req.agencyName}</td>
                    <td className="px-5 py-4 text-sm text-slate-600 dark:text-slate-300">{REASON_LABELS[req.reason] || req.reason}</td>
                    <td className="px-5 py-4 text-sm text-slate-500 max-w-[260px] truncate" title={req.feedback}>{req.feedback || '—'}</td>
                    <td className="px-5 py-4">
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-medium border ${STATUS_STYLES[req.status]}`}>
                        {req.status}
                      </span>
                      {req.reviewNote && <p className="text-[10px] text-slate-400 mt-1">Note: {req.reviewNote}</p>}
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-500">{new Date(req.createdAt).toLocaleDateString()}</td>
                    <td className="px-5 py-4">
                      {req.status === 'PENDING' ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            disabled={busyId === req.id}
                            onClick={() => approve(req)}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100 transition-colors disabled:opacity-60"
                          >
                            <CheckCircle2 size={13} /> Approve
                          </button>
                          <button
                            disabled={busyId === req.id}
                            onClick={() => reject(req)}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 transition-colors disabled:opacity-60"
                          >
                            <XCircle size={13} /> Reject
                          </button>
                        </div>
                      ) : (
                        <p className="text-right text-xs text-slate-400">{req.reviewedBy ? `by ${req.reviewedBy}` : '—'}</p>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
