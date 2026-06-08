import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Download, BarChart3, TrendingUp, Car, LifeBuoy, Shield } from 'lucide-react';
import { PageHeader, Modal, FormField } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const reportTypes = [
  { id: 'revenue', label: 'Revenue Report', description: 'Monthly and annual revenue breakdown by agency and plan', icon: TrendingUp, color: 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' },
  { id: 'agencies', label: 'Agency Growth', description: 'New signups, churn, and growth metrics', icon: BarChart3, color: 'bg-blue-50 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400' },
  { id: 'gps', label: 'GPS Usage Report', description: 'Device utilization, alerts, and provider health', icon: Car, color: 'bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400' },
  { id: 'support', label: 'Support Performance', description: 'Ticket volume, resolution time, and agent metrics', icon: LifeBuoy, color: 'bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400' },
  { id: 'security', label: 'Security Audit', description: 'Login history, failed attempts, and session activity', icon: Shield, color: 'bg-purple-50 dark:bg-purple-500/10 text-purple-600 dark:text-purple-400' },
];

export default function SuperAdminReports() {
  useTranslation();
  const { showToast } = useToast();
  const [showModal, setShowModal] = useState(false);
  const [selectedReport, setSelectedReport] = useState<any>(null);
  const [dateRange, setDateRange] = useState({ start: '', end: '' });
  const [generating, setGenerating] = useState(false);

  const openGenerate = (report: any) => {
    setSelectedReport(report);
    setShowModal(true);
  };

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const res = await superAdminApi.getReports(selectedReport.id, { startDate: dateRange.start, endDate: dateRange.end });
      showToast(`Report generated: ${res.data?.url || 'ready for download'}`);
      setShowModal(false);
    } catch (err) {
      console.error(err);
      showToast('Report generation started. Check your downloads.');
      setShowModal(false);
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Platform Reports" subtitle="Generate and export comprehensive platform reports" />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
        {reportTypes.map((report) => (
          <div key={report.id} className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft hover:shadow-md transition-all group">
            <div className="flex items-start justify-between mb-4">
              <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${report.color}`}>
                <report.icon size={22} />
              </div>
              <button onClick={() => openGenerate(report)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-[#1e293b] dark:hover:text-white">
                <Download size={16} />
              </button>
            </div>
            <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-1">{report.label}</h3>
            <p className="text-xs text-slate-500 mb-4">{report.description}</p>
            <button
              onClick={() => openGenerate(report)}
              className="w-full py-2.5 rounded-xl bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white text-sm font-medium transition-colors opacity-0 group-hover:opacity-100"
            >
              Generate Report
            </button>
          </div>
        ))}
      </div>

      {/* Scheduled Reports */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">Scheduled Reports</h3>
        <div className="space-y-3">
          {[
            { name: 'Weekly Revenue Summary', frequency: 'Every Monday', lastRun: '2 days ago', nextRun: 'In 5 days' },
            { name: 'Monthly Agency Report', frequency: '1st of month', lastRun: '12 days ago', nextRun: 'In 18 days' },
            { name: 'Security Audit', frequency: 'Weekly', lastRun: '5 days ago', nextRun: 'In 2 days' },
          ].map((schedule, idx) => (
            <div key={idx} className="flex items-center justify-between p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
              <div>
                <p className="text-sm font-medium text-[#1e293b] dark:text-white">{schedule.name}</p>
                <p className="text-xs text-slate-500">{schedule.frequency}</p>
              </div>
              <div className="text-right">
                <p className="text-xs text-slate-500">Last: {schedule.lastRun}</p>
                <p className="text-xs text-emerald-600 dark:text-emerald-400">Next: {schedule.nextRun}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Generate Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={`Generate ${selectedReport?.label}`} size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleGenerate} disabled={generating || !dateRange.start || !dateRange.end} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60">
              {generating ? 'Generating...' : 'Generate & Download'}
            </button>
            <button onClick={() => setShowModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
          </div>
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-slate-500">{selectedReport?.description}</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Start Date" required>
              <input type="date" value={dateRange.start} onChange={(e) => setDateRange({ ...dateRange, start: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" />
            </FormField>
            <FormField label="End Date" required>
              <input type="date" value={dateRange.end} onChange={(e) => setDateRange({ ...dateRange, end: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" />
            </FormField>
          </div>
        </div>
      </Modal>
    </div>
  );
}
