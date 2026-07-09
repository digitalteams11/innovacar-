import { useEffect, useState } from 'react';
import { superAdminApi } from '../../api/superAdminApi';
import {
  FileText, AlertTriangle, CheckCircle, Clock, Eye, Download
} from 'lucide-react';
import { PageHeader, DataTable, Badge, StatCard, SearchBar } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminContracts() {
  const { showToast } = useToast();
  const [contracts, setContracts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    fetchContracts();
  }, []);

  const fetchContracts = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getAllContracts().catch(() => ({ data: [] }));
      setContracts(res.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load contracts. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleExport = () => {
    const csv = [
      ['Contract #', 'Client', 'Agency', 'Status', 'Start', 'End', 'Value'].join(','),
      ...contracts.map(c => [
        c.contractNumber || c.id, c.clientName, c.agencyName,
        c.status, c.startDate, c.endDate, c.totalAmount
      ].join(','))
    ].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `contracts-export-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Contracts exported to CSV', 'success');
  };

  const statusColors: Record<string, any> = {
    ACTIVE: 'success',
    PENDING: 'warning',
    EXPIRED: 'danger',
    COMPLETED: 'default',
    CANCELLED: 'default',
  };

  const filteredContracts = contracts.filter((c) =>
    search ? (c.contractNumber?.toLowerCase().includes(search.toLowerCase()) || c.clientName?.toLowerCase().includes(search.toLowerCase())) : true
  );

  const expiringSoon = contracts.filter((c: any) => {
    if (!c.endDate) return false;
    const end = new Date(c.endDate);
    const now = new Date();
    const diffDays = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    return diffDays > 0 && diffDays <= 7;
  });

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Global Contract Oversight" subtitle="Monitor all contracts across all agencies">
        <button
          onClick={handleExport}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft"
        >
          <Download size={16} />
          Export
        </button>
      </PageHeader>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <StatCard title="Total Contracts" value={contracts.length} icon={FileText} loading={loading} />
        <StatCard title="Active" value={contracts.filter((c) => c.status === 'ACTIVE').length} icon={CheckCircle} loading={loading} />
        <StatCard title="Pending" value={contracts.filter((c) => c.status === 'PENDING').length} icon={Clock} loading={loading} />
        <StatCard title="Expiring (7d)" value={expiringSoon.length} icon={AlertTriangle} loading={loading} />
      </div>

      {expiringSoon.length > 0 && (
        <div className="bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-2xl p-4 flex items-center gap-3">
          <AlertTriangle size={20} className="text-amber-500" />
          <p className="text-sm text-amber-700 dark:text-amber-400">
            <span className="font-semibold">{expiringSoon.length} contracts</span> are expiring within the next 7 days.
          </p>
        </div>
      )}

      <SearchBar placeholder="Search contracts..." value={search} onChange={setSearch} className="max-w-md" />

      <DataTable
        columns={[
          { key: 'contract', header: 'Contract', render: (row: any) => (
            <div>
              <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{row.contractNumber || `#${row.id}`}</p>
              <p className="text-xs text-slate-500">{row.agencyName || '-'}</p>
            </div>
          )},
          { key: 'client', header: 'Client', render: (row: any) => <span className="text-sm text-slate-500">{row.clientName || '-'}</span> },
          { key: 'vehicle', header: 'Vehicle', render: (row: any) => <span className="text-sm text-slate-500">{row.vehicleInfo || '-'}</span> },
          { key: 'status', header: 'Status', render: (row: any) => <Badge variant={statusColors[row.status] || 'default'}>{row.status}</Badge> },
          { key: 'startDate', header: 'Start', render: (row: any) => <span className="text-xs text-slate-500">{row.startDate ? new Date(row.startDate).toLocaleDateString() : '-'}</span> },
          { key: 'endDate', header: 'End', render: (row: any) => <span className="text-xs text-slate-500">{row.endDate ? new Date(row.endDate).toLocaleDateString() : '-'}</span> },
          { key: 'value', header: 'Value', render: (row: any) => <span className="text-sm font-medium text-[#1e293b] dark:text-white">{row.totalAmount?.toLocaleString() || 0} MAD</span> },
          { key: 'actions', header: '', align: 'right' as const, render: () => (
            <button
              className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors"
              title="View"
            >
              <Eye size={16} />
            </button>
          )},
        ]}
        data={filteredContracts}
        loading={loading}
        keyExtractor={(row) => row.id}
        emptyTitle="No contracts found"
      />
    </div>
  );
}
