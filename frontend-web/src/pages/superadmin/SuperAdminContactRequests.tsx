import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import { ArrowRightCircle } from 'lucide-react';
import { PageHeader, FilterSelect, DataTable, Badge } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const statusOptions = [
  { value: 'NEW', label: 'New' },
  { value: 'REVIEWING', label: 'Reviewing' },
  { value: 'REPLIED', label: 'Replied' },
  { value: 'CONVERTED', label: 'Converted' },
  { value: 'ARCHIVED', label: 'Archived' },
];

const statusVariant: Record<string, any> = {
  NEW: 'info',
  REVIEWING: 'warning',
  REPLIED: 'success',
  CONVERTED: 'success',
  ARCHIVED: 'default',
};

export default function SuperAdminContactRequests() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [requests, setRequests] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [convertingId, setConvertingId] = useState<number | null>(null);

  useEffect(() => {
    fetchRequests();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const fetchRequests = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getContactRequests(statusFilter || undefined);
      setRequests(res.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load contact requests. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleConvert = async (row: any) => {
    if (row.convertedTicketId) return;
    if (!window.confirm(`Convert request ${row.requestNumber} into a support ticket?`)) return;
    setConvertingId(row.id);
    try {
      const res = await superAdminApi.convertContactRequestToTicket(row.id);
      showToast(`Converted to ticket ${res.data.ticketNumber}`, 'success');
      await fetchRequests();
      navigate(`/super-admin/support/${res.data.ticketId}`);
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.data?.message || 'Unable to convert this request to a ticket.', 'error');
    } finally {
      setConvertingId(null);
    }
  };

  const columns = [
    {
      key: 'request',
      header: 'Request',
      render: (row: any) => (
        <div>
          <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{row.requestNumber}</p>
          <p className="text-xs text-slate-500 truncate max-w-[220px]">{row.subject}</p>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      render: (row: any) => <span className="text-sm text-slate-500">{row.category || '-'}</span>,
    },
    {
      key: 'from',
      header: 'From',
      render: (row: any) => (
        <div>
          <p className="text-sm text-[#1e293b] dark:text-white">{row.requesterName || '-'}</p>
          <p className="text-xs text-slate-500">{row.requesterEmail}</p>
        </div>
      ),
    },
    {
      key: 'emailStatus',
      header: 'Email',
      render: (row: any) => (
        <Badge variant={row.emailStatus === 'SENT' ? 'success' : row.emailStatus === 'FAILED' ? 'warning' : 'default'}>
          {row.emailStatus || 'PENDING'}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row: any) => <Badge variant={statusVariant[row.status] || 'default'}>{row.status}</Badge>,
    },
    {
      key: 'created',
      header: 'Created',
      render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '-'}</span>,
    },
    {
      key: 'actions',
      header: t('superAdmin.common.actions'),
      align: 'right' as const,
      render: (row: any) => (
        <div className="flex items-center justify-end gap-1">
          {row.convertedTicketId ? (
            <button
              onClick={() => navigate(`/super-admin/support/${row.convertedTicketId}`)}
              className="text-xs font-semibold text-brand-600 dark:text-brand-400 hover:underline px-2 py-1"
            >
              View Ticket
            </button>
          ) : (
            <button
              onClick={() => handleConvert(row)}
              disabled={convertingId === row.id}
              className="flex items-center gap-1.5 text-xs font-semibold text-[#1e293b] dark:text-white bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
              title="Convert to support ticket"
            >
              <ArrowRightCircle size={14} />
              Convert to Ticket
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader
        title="Contact Requests"
        subtitle="Public contact form submissions — sales, general, and legal inquiries. Convert to a support ticket explicitly if it needs full ticket handling."
      />

      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex-1" />
        <FilterSelect
          options={statusOptions}
          value={statusFilter}
          onChange={setStatusFilter}
          placeholder="All Statuses"
          className="w-full sm:w-48"
        />
      </div>

      <DataTable
        columns={columns}
        data={requests}
        loading={loading}
        keyExtractor={(row) => row.id}
        emptyTitle="No contact requests yet"
      />
    </div>
  );
}
