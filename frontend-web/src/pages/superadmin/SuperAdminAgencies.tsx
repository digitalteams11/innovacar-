import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Building2, Eye, Edit2, Plus, PauseCircle, PlayCircle,
  Trash2, Users, Car, ShieldCheck, RotateCcw, Copy
} from 'lucide-react';
import { PageHeader, SearchBar, FilterSelect, DataTable, Modal, FormField, TextInput, Badge, ActionMenu } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const statusOptions = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'TRIAL', label: 'Trial' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'EXPIRED', label: 'Expired' },
];

const statusBadgeVariant: Record<string, any> = {
  ACTIVE: 'success',
  TRIAL: 'info',
  SUSPENDED: 'danger',
  EXPIRED: 'warning',
};

export default function SuperAdminAgencies() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [agencies, setAgencies] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingAgency, setEditingAgency] = useState<any>(null);
  const [createForm, setCreateForm] = useState({
    name: '', email: '', phone: '', address: '', city: '', country: '', taxId: '',
  });

  useEffect(() => {
    fetchAgencies();
  }, [statusFilter, search]);

  const fetchAgencies = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getAgencies({
        status: statusFilter || undefined,
        search: search || undefined,
      });
      setAgencies(res.data);
    } catch (err) {
      console.error(err);
      showToast('Failed to load agencies', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    try {
      await superAdminApi.createAgency(createForm);
      setShowCreateModal(false);
      setCreateForm({ name: '', email: '', phone: '', address: '', city: '', country: '', taxId: '' });
      showToast('Agency created successfully');
      await fetchAgencies();
    } catch (err) {
      console.error(err);
      showToast('Failed to create agency', 'error');
    }
  };

  const handleUpdateStatus = async (id: number, status: string) => {
    try {
      await superAdminApi.updateAgencyStatus(id, status);
      await fetchAgencies();
      showToast(`Agency ${status.toLowerCase()} successfully`);
    } catch (err) {
      console.error(err);
      showToast('Failed to update status', 'error');
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm(t('superAdmin.agencies.confirmDelete'))) return;
    try {
      await superAdminApi.deleteAgency(id);
      await fetchAgencies();
      showToast('Agency deleted successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to delete agency', 'error');
    }
  };

  const handleRestore = async (id: number) => {
    try {
      await superAdminApi.restoreAgency(id);
      await fetchAgencies();
      showToast('Agency restored successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to restore agency', 'error');
    }
  };

  const handleVerify = async (id: number) => {
    try {
      await superAdminApi.verifyAgency(id);
      await fetchAgencies();
      showToast('Agency verified successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to verify agency', 'error');
    }
  };

  const handleDuplicate = async (agency: any) => {
    try {
      await superAdminApi.createAgency({
        name: `${agency.name} (Copy)`,
        email: `copy-${agency.email}`,
        phone: agency.phone,
        address: agency.address,
        city: agency.city,
        country: agency.country,
        taxId: agency.taxId,
      });
      await fetchAgencies();
      showToast('Agency duplicated successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to duplicate agency', 'error');
    }
  };

  const openEdit = (agency: any) => {
    setEditingAgency(agency);
    setShowEditModal(true);
  };

  const saveEdit = async () => {
    try {
      await superAdminApi.updateAgency(editingAgency.id, editingAgency);
      setShowEditModal(false);
      await fetchAgencies();
      showToast('Agency updated successfully');
    } catch (err) {
      console.error(err);
      showToast('Failed to update agency', 'error');
    }
  };

  const columns = [
    {
      key: 'name',
      header: t('superAdmin.agencies.name'),
      render: (row: any) => (
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
            <Building2 size={16} className="text-[#0a0f2c] dark:text-white/70" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{row.name}</p>
            <p className="text-xs text-slate-400">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'planName',
      header: t('superAdmin.agencies.plan'),
      render: (row: any) => (
        <span className="text-sm font-medium text-[#1e293b] dark:text-white">{row.planName || '-'}</span>
      ),
    },
    {
      key: 'status',
      header: t('superAdmin.agencies.status_label'),
      render: (row: any) => (
        <Badge variant={statusBadgeVariant[row.status] || 'default'}>{row.status}</Badge>
      ),
    },
    {
      key: 'usage',
      header: t('superAdmin.agencies.usage'),
      render: (row: any) => (
        <div className="flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
          <span className="flex items-center gap-1"><Car size={12} /> {row.vehicleCount}</span>
          <span className="flex items-center gap-1"><Users size={12} /> {row.employeeCount}</span>
        </div>
      ),
    },
    {
      key: 'subscription',
      header: t('superAdmin.agencies.subscription'),
      render: (row: any) => (
        <div className="text-xs text-slate-500 dark:text-slate-400">
          <p>{row.subscriptionActive ? 'Active' : 'Inactive'}</p>
          {row.subscriptionEndDate && <p>Until {new Date(row.subscriptionEndDate).toLocaleDateString()}</p>}
        </div>
      ),
    },
    {
      key: 'actions',
      header: t('superAdmin.agencies.actions'),
      align: 'right' as const,
      render: (row: any) => (
        <div className="flex items-center justify-end gap-1">
          <button onClick={() => navigate(`/super-admin/agencies/${row.id}`)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-brand-600 dark:hover:text-brand-400" title={t('superAdmin.agencies.view')}>
            <Eye size={16} />
          </button>
          <button onClick={() => openEdit(row)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors text-slate-400 hover:text-brand-600 dark:hover:text-brand-400" title={t('superAdmin.agencies.edit')}>
            <Edit2 size={16} />
          </button>
          {row.status !== 'SUSPENDED' ? (
            <button onClick={() => handleUpdateStatus(row.id, 'SUSPENDED')} className="p-2 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg transition-colors text-slate-400 hover:text-rose-600 dark:hover:text-rose-400" title={t('superAdmin.agencies.suspend')}>
              <PauseCircle size={16} />
            </button>
          ) : (
            <button onClick={() => handleUpdateStatus(row.id, 'ACTIVE')} className="p-2 hover:bg-emerald-50 dark:hover:bg-emerald-500/10 rounded-lg transition-colors text-slate-400 hover:text-emerald-600 dark:hover:text-emerald-400" title={t('superAdmin.agencies.activate')}>
              <PlayCircle size={16} />
            </button>
          )}
          <ActionMenu items={[
            { label: 'Verify', onClick: () => handleVerify(row.id), icon: <ShieldCheck size={14} /> },
            { label: 'Restore', onClick: () => handleRestore(row.id), icon: <RotateCcw size={14} /> },
            { label: 'Duplicate', onClick: () => handleDuplicate(row), icon: <Copy size={14} /> },
            { label: 'Delete', onClick: () => handleDelete(row.id), danger: true, icon: <Trash2 size={14} /> },
          ]} />
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title={t('superAdmin.agencies.title')} subtitle={t('superAdmin.agencies.subtitle')}>
        <button
          onClick={() => setShowCreateModal(true)}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft"
        >
          <Plus size={16} />
          <span className="hidden sm:inline">Create Agency</span>
        </button>
      </PageHeader>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <SearchBar
          placeholder={t('superAdmin.agencies.searchPlaceholder')}
          value={search}
          onChange={setSearch}
          className="flex-1"
        />
        <FilterSelect
          options={statusOptions}
          value={statusFilter}
          onChange={setStatusFilter}
          placeholder={t('superAdmin.agencies.allStatuses')}
          className="w-full sm:w-48"
        />
      </div>

      {/* Table */}
      <DataTable
        columns={columns}
        data={agencies}
        loading={loading}
        keyExtractor={(row) => row.id}
        emptyTitle={t('superAdmin.agencies.noAgencies')}
      />

      {/* Create Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create New Agency"
        size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleCreate} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              {t('superAdmin.common.create')}
            </button>
            <button onClick={() => setShowCreateModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              {t('superAdmin.common.cancel')}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Agency Name" required>
            <TextInput value={createForm.name} onChange={(v) => setCreateForm({ ...createForm, name: v })} placeholder="Enter agency name" />
          </FormField>
          <FormField label="Email" required>
            <TextInput value={createForm.email} onChange={(v) => setCreateForm({ ...createForm, email: v })} placeholder="agency@example.com" type="email" />
          </FormField>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Phone">
              <TextInput value={createForm.phone} onChange={(v) => setCreateForm({ ...createForm, phone: v })} placeholder="+212..." />
            </FormField>
            <FormField label="Tax ID">
              <TextInput value={createForm.taxId} onChange={(v) => setCreateForm({ ...createForm, taxId: v })} placeholder="Tax ID" />
            </FormField>
          </div>
          <FormField label="Address">
            <TextInput value={createForm.address} onChange={(v) => setCreateForm({ ...createForm, address: v })} placeholder="Street address" />
          </FormField>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="City">
              <TextInput value={createForm.city} onChange={(v) => setCreateForm({ ...createForm, city: v })} placeholder="City" />
            </FormField>
            <FormField label="Country">
              <TextInput value={createForm.country} onChange={(v) => setCreateForm({ ...createForm, country: v })} placeholder="Country" />
            </FormField>
          </div>
        </div>
      </Modal>

      {/* Edit Modal */}
      <Modal
        isOpen={showEditModal}
        onClose={() => setShowEditModal(false)}
        title={t('superAdmin.agencies.editAgency')}
        size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={saveEdit} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              {t('superAdmin.common.save')}
            </button>
            <button onClick={() => setShowEditModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              {t('superAdmin.common.cancel')}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          {editingAgency && ['name', 'email', 'phone', 'address', 'city', 'country', 'taxId'].map((field) => (
            <FormField key={field} label={field.charAt(0).toUpperCase() + field.slice(1)}>
              <TextInput
                value={editingAgency[field] || ''}
                onChange={(v) => setEditingAgency({ ...editingAgency, [field]: v })}
              />
            </FormField>
          ))}
        </div>
      </Modal>
    </div>
  );
}
