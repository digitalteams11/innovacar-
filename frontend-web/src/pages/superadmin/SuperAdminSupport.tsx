import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import { Eye, Plus } from 'lucide-react';
import { PageHeader, FilterSelect, DataTable, Badge, Modal, FormField, TextInput, TextArea } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const statusOptions = [
  { value: 'OPEN', label: 'Open' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CLOSED', label: 'Closed' },
];

const channelOptions = [
  { value: 'CONTACT', label: 'Contact' },
  { value: 'SUPPORT', label: 'Support' },
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'BILLING', label: 'Billing' },
  { value: 'SECURITY', label: 'Security' },
];

const priorityColors: Record<string, any> = {
  LOW: 'default',
  MEDIUM: 'info',
  HIGH: 'warning',
  URGENT: 'danger',
};

export default function SuperAdminSupport() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [tickets, setTickets] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState({ subject: '', description: '', category: 'GENERAL', priority: 'MEDIUM' });

  useEffect(() => {
    fetchTickets();
  }, [statusFilter]);

  const fetchTickets = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getTickets(statusFilter || undefined);
      setTickets(res.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load tickets. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTicket = async () => {
    try {
      await superAdminApi.createTicket(createForm);
      setShowCreateModal(false);
      setCreateForm({ subject: '', description: '', category: 'GENERAL', priority: 'MEDIUM' });
      showToast('Ticket created successfully', 'success');
      await fetchTickets();
    } catch (err) {
      console.error(err);
      showToast('Unable to create ticket. Please try again later.', 'error');
    }
  };

  const columns = [
    {
      key: 'ticket',
      header: t('superAdmin.support.ticket'),
      render: (row: any) => (
        <div>
          <p className="text-sm font-semibold text-[#1e293b] dark:text-white">#{row.id}</p>
          <p className="text-xs text-slate-500 truncate max-w-[200px]">{row.subject}</p>
        </div>
      ),
    },
    {
      key: 'channel',
      header: 'Channel',
      render: (row: any) => row.channel ? <Badge variant="default">{row.channel}</Badge> : <span className="text-sm text-slate-500">-</span>,
    },
    {
      key: 'category',
      header: t('superAdmin.support.category'),
      render: (row: any) => <span className="text-sm text-slate-500">{row.category || '-'}</span>,
    },
    {
      key: 'priority',
      header: t('superAdmin.support.priority'),
      render: (row: any) => <Badge variant={priorityColors[row.priority] || 'default'}>{row.priority || 'LOW'}</Badge>,
    },
    {
      key: 'status',
      header: t('superAdmin.support.status'),
      render: (row: any) => <Badge variant={row.status === 'OPEN' ? 'info' : row.status === 'RESOLVED' || row.status === 'CLOSED' ? 'success' : 'warning'}>{row.status}</Badge>,
    },
    {
      key: 'from',
      header: t('superAdmin.support.from'),
      render: (row: any) => <span className="text-sm text-slate-500">{row.agencyName || row.createdBy || '-'}</span>,
    },
    {
      key: 'assigned',
      header: t('superAdmin.support.assigned'),
      render: (row: any) => <span className="text-sm text-slate-500">{row.assignedTo || 'Unassigned'}</span>,
    },
    {
      key: 'created',
      header: t('superAdmin.support.created'),
      render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '-'}</span>,
    },
    {
      key: 'actions',
      header: t('superAdmin.common.actions'),
      align: 'right' as const,
      render: (row: any) => (
        <div className="flex items-center justify-end gap-1">
          <button onClick={() => navigate(`/super-admin/support/${row.id}`)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-brand-600 dark:hover:text-brand-400" title={t('superAdmin.common.view')}>
            <Eye size={16} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title={t('superAdmin.support.title')} subtitle={t('superAdmin.support.subtitle')}>
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft"
        >
          <Plus size={16} />
          <span className="hidden sm:inline">New Ticket</span>
        </button>
      </PageHeader>

      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex-1" />
        <FilterSelect
          options={channelOptions}
          value={channelFilter}
          onChange={setChannelFilter}
          placeholder="All Channels"
          className="w-full sm:w-48"
        />
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
        data={channelFilter ? tickets.filter((row) => row.channel === channelFilter) : tickets}
        loading={loading}
        keyExtractor={(row) => row.id}
        emptyTitle={t('superAdmin.support.noTickets')}
      />

      {/* Create Ticket Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create Support Ticket"
        size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleCreateTicket} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Create Ticket
            </button>
            <button onClick={() => setShowCreateModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Cancel
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Subject" required>
            <TextInput value={createForm.subject} onChange={(v) => setCreateForm({ ...createForm, subject: v })} placeholder="Enter ticket subject" />
          </FormField>
          <FormField label="Category">
            <select
              value={createForm.category}
              onChange={(e) => setCreateForm({ ...createForm, category: e.target.value })}
              className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none"
            >
              <option value="GENERAL">General</option>
              <option value="BILLING">Billing</option>
              <option value="TECHNICAL">Technical</option>
              <option value="FEATURE_REQUEST">Feature Request</option>
            </select>
          </FormField>
          <FormField label="Priority">
            <select
              value={createForm.priority}
              onChange={(e) => setCreateForm({ ...createForm, priority: e.target.value })}
              className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </FormField>
          <FormField label="Description">
            <TextArea value={createForm.description} onChange={(v) => setCreateForm({ ...createForm, description: v })} placeholder="Describe the issue..." rows={4} />
          </FormField>
        </div>
      </Modal>
    </div>
  );
}
