import { useEffect, useState } from 'react';

import { useParams, useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Building2, ArrowLeft, Users, Car, Calendar, MapPin,
  FileText, Activity, Mail, Phone, MapPinned, Tag,
  RefreshCw, CreditCard, Zap
} from 'lucide-react';
import { PageHeader, TabGroup, ProgressBar, Badge, DataTable, EmptyState, Modal, FormField, SelectInput } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminAgencyDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [agency, setAgency] = useState<any>(null);
  const [plans, setPlans] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState('overview');
  const [loading, setLoading] = useState(true);
  const [activity, setActivity] = useState<any[]>([]);
  const [invoices, setInvoices] = useState<any[]>([]);
  const [securityLogs, setSecurityLogs] = useState<any[]>([]);
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState('');

  useEffect(() => {
    if (id) fetchAgency();
  }, [id]);

  const fetchAgency = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getAgency(Number(id));
      setAgency(res.data);
      const [activityRes, invoicesRes, securityRes, plansRes] = await Promise.all([
        superAdminApi.getAgencyActivity(Number(id)).catch(() => ({ data: [] })),
        superAdminApi.getAgencyInvoices(Number(id)).catch(() => ({ data: [] })),
        superAdminApi.getAgencySecurityLogs(Number(id)).catch(() => ({ data: [] })),
        superAdminApi.getPlans().catch(() => ({ data: [] })),
      ]);
      setActivity(activityRes.data);
      setInvoices(invoicesRes.data);
      setSecurityLogs(securityRes.data);
      setPlans(plansRes.data);
    } catch (err) {
      console.error(err);
      showToast('Failed to load agency details', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleForceRenew = async () => {
    try {
      await superAdminApi.forceRenew(Number(id));
      showToast('Subscription renewed successfully');
      await fetchAgency();
    } catch (err) {
      showToast('Failed to renew subscription', 'error');
    }
  };

  const handleExtendTrial = async () => {
    try {
      await superAdminApi.extendTrial(Number(id), 30);
      showToast('Trial extended by 30 days');
      await fetchAgency();
    } catch (err) {
      showToast('Failed to extend trial', 'error');
    }
  };

  const handleSubscribe = async () => {
    if (!selectedPlan) return;
    try {
      await superAdminApi.subscribeAgency(Number(id), selectedPlan);
      showToast(`Subscribed to ${selectedPlan} successfully`);
      setShowPlanModal(false);
      setSelectedPlan('');
      await fetchAgency();
    } catch (err) {
      showToast('Failed to subscribe agency', 'error');
    }
  };

  const handleUpdateStatus = async (status: string) => {
    try {
      await superAdminApi.updateAgencyStatus(Number(id), status);
      showToast(`Agency status updated to ${status}`);
      await fetchAgency();
    } catch (err) {
      showToast('Failed to update status', 'error');
    }
  };

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'subscription', label: 'Subscription' },
    { id: 'usage', label: 'Usage' },
    { id: 'activity', label: 'Activity' },
    { id: 'security', label: 'Security' },
  ];

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
      </div>
    );
  }

  if (!agency) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <EmptyState title="Agency not found" description="The requested agency could not be found." />
      </div>
    );
  }

  const usageItems = [
    { label: 'Vehicles', current: agency.currentVehicleCount || 0, max: agency.maxVehicles || 0, icon: Car },
    { label: 'Employees', current: agency.currentEmployeeCount || 0, max: agency.maxEmployees || 0, icon: Users },
    { label: 'GPS Devices', current: agency.currentGpsDeviceCount || 0, max: agency.maxGpsDevices || 0, icon: MapPin },
    { label: 'Reservations', current: agency.currentReservationCount || 0, max: agency.maxReservations || 0, icon: Calendar },
  ];

  const planOptions = plans.filter(p => p.isActive).map(p => ({ value: p.code, label: `${p.name} (${p.monthlyPrice} MAD/mo)` }));

  return (
    <div className="space-y-6 animate-fade">
      {/* Breadcrumb + Header */}
      <div>
        <button
          onClick={() => navigate('/super-admin/agencies')}
          className="flex items-center gap-1 text-sm text-slate-500 hover:text-[#1e293b] dark:hover:text-white transition-colors mb-3"
        >
          <ArrowLeft size={16} />
          Back to Agencies
        </button>
        <PageHeader
          title={agency.name}
          subtitle={`${agency.email} · ${agency.city || ''}, ${agency.country || ''}`}
        >
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={agency.status === 'ACTIVE' ? 'success' : agency.status === 'TRIAL' ? 'info' : agency.status === 'SUSPENDED' ? 'danger' : 'warning'}>
              {agency.status}
            </Badge>
            <button
              onClick={() => handleUpdateStatus(agency.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                agency.status === 'SUSPENDED'
                  ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 hover:bg-emerald-100'
                  : 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 hover:bg-rose-100'
              }`}
            >
              {agency.status === 'SUSPENDED' ? 'Activate' : 'Suspend'}
            </button>
          </div>
        </PageHeader>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {[
          { label: 'Vehicles', value: agency.currentVehicleCount || 0, icon: Car },
          { label: 'Employees', value: agency.currentEmployeeCount || 0, icon: Users },
          { label: 'Reservations', value: agency.currentReservationCount || 0, icon: Calendar },
          { label: 'Contracts', value: agency.currentContractCount || 0, icon: FileText },
        ].map((stat) => (
          <div key={stat.label} className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-9 h-9 rounded-lg bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
                <stat.icon size={16} className="text-[#0a0f2c] dark:text-white/70" />
              </div>
              <span className="text-xs text-slate-500 dark:text-slate-400">{stat.label}</span>
            </div>
            <p className="text-xl font-bold text-[#1e293b] dark:text-white">{stat.value}</p>
          </div>
        ))}
      </div>

      {/* Subscription Quick Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-2 flex-wrap">
        <button onClick={handleForceRenew} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90 transition-colors shadow-soft">
          <RefreshCw size={16} />
          Force Renew
        </button>
        {agency.inTrial && (
          <button onClick={handleExtendTrial} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 hover:bg-blue-100 transition-colors border border-blue-200 dark:border-blue-500/20">
            <Zap size={16} />
            Extend Trial (+30d)
          </button>
        )}
        <button onClick={() => setShowPlanModal(true)} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-white dark:bg-[#1a2332]/70 border border-[#e8e6e1]/80 dark:border-white/5 text-[#1e293b] dark:text-white hover:bg-slate-50 dark:hover:bg-white/5 transition-colors shadow-soft">
          <CreditCard size={16} />
          Change Plan
        </button>
      </div>

      {/* Tabs */}
      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      {/* Tab Content */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-4">Agency Information</h3>
                <div className="space-y-3">
                  {[
                    { label: 'Name', value: agency.name, icon: Building2 },
                    { label: 'Email', value: agency.email, icon: Mail },
                    { label: 'Phone', value: agency.phone || '-', icon: Phone },
                    { label: 'Address', value: agency.address || '-', icon: MapPinned },
                    { label: 'City', value: agency.city || '-', icon: MapPin },
                    { label: 'Country', value: agency.country || '-', icon: MapPin },
                    { label: 'Tax ID', value: agency.taxId || '-', icon: Tag },
                  ].map((item) => (
                    <div key={item.label} className="flex items-center gap-3 py-2 border-b border-[#e8e6e1]/40 dark:border-white/5 last:border-0">
                      <item.icon size={14} className="text-slate-400" />
                      <span className="text-xs text-slate-500 dark:text-slate-400 w-24">{item.label}</span>
                      <span className="text-sm text-[#1e293b] dark:text-white">{item.value}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-4">Subscription Summary</h3>
                <div className="space-y-3">
                  {[
                    { label: 'Plan', value: agency.planName || 'Trial' },
                    { label: 'Status', value: agency.subscriptionActive ? 'Active' : 'Inactive' },
                    { label: 'End Date', value: agency.subscriptionEndDate ? new Date(agency.subscriptionEndDate).toLocaleDateString() : '-' },
                    { label: 'Trial End', value: agency.trialEndDate ? new Date(agency.trialEndDate).toLocaleDateString() : '-' },
                    { label: 'Total Revenue', value: `${agency.totalRevenue?.toLocaleString() || 0} MAD` },
                  ].map((item) => (
                    <div key={item.label} className="flex items-center justify-between py-2 border-b border-[#e8e6e1]/40 dark:border-white/5 last:border-0">
                      <span className="text-xs text-slate-500 dark:text-slate-400">{item.label}</span>
                      <span className="text-sm font-medium text-[#1e293b] dark:text-white">{item.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'subscription' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                <h4 className="text-sm font-bold text-[#1e293b] dark:text-white mb-3">Current Plan</h4>
                <p className="text-2xl font-bold text-[#1e293b] dark:text-white mb-1">{agency.planName || 'Trial'}</p>
                <p className="text-xs text-slate-500 mb-4">
                  {agency.subscriptionActive ? 'Subscription is active' : 'Subscription is inactive'}
                </p>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between"><span className="text-slate-500">Max Vehicles</span><span className="font-medium text-[#1e293b] dark:text-white">{agency.maxVehicles}</span></div>
                  <div className="flex justify-between"><span className="text-slate-500">Max Employees</span><span className="font-medium text-[#1e293b] dark:text-white">{agency.maxEmployees}</span></div>
                  <div className="flex justify-between"><span className="text-slate-500">Max GPS</span><span className="font-medium text-[#1e293b] dark:text-white">{agency.maxGpsDevices}</span></div>
                  <div className="flex justify-between"><span className="text-slate-500">Max Reservations</span><span className="font-medium text-[#1e293b] dark:text-white">{agency.maxReservations}</span></div>
                </div>
                <div className="flex gap-2 mt-4">
                  <button onClick={handleForceRenew} className="flex-1 py-2 rounded-lg bg-[#0a0f2c] text-white text-xs font-medium hover:bg-[#0a0f2c]/90 transition-colors">
                    Force Renew
                  </button>
                  {agency.inTrial && (
                    <button onClick={handleExtendTrial} className="flex-1 py-2 rounded-lg bg-blue-50 text-blue-700 text-xs font-medium hover:bg-blue-100 transition-colors border border-blue-200">
                      Extend Trial
                    </button>
                  )}
                </div>
              </div>
              <div>
                <h4 className="text-sm font-bold text-[#1e293b] dark:text-white mb-3">Payment History</h4>
                {invoices.length === 0 ? (
                  <EmptyState title="No invoices" description="This agency has no invoices yet." />
                ) : (
                  <div className="space-y-2 max-h-80 overflow-y-auto">
                    {invoices.slice(0, 10).map((inv: any) => (
                      <div key={inv.id} className="flex items-center justify-between p-3 rounded-lg bg-slate-50 dark:bg-white/5">
                        <div>
                          <p className="text-sm font-medium text-[#1e293b] dark:text-white">{inv.invoiceNumber}</p>
                          <p className="text-xs text-slate-500">{inv.issueDate ? new Date(inv.issueDate).toLocaleDateString() : '-'}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm font-bold text-[#1e293b] dark:text-white">{inv.amount?.toLocaleString()} MAD</p>
                          <Badge variant={inv.status === 'PAID' ? 'success' : inv.status === 'OVERDUE' ? 'danger' : 'warning'}>
                            {inv.status}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'usage' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {usageItems.map((item) => (
                <div key={item.label} className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 rounded-lg bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
                      <item.icon size={18} className="text-[#0a0f2c] dark:text-white/70" />
                    </div>
                    <div>
                      <p className="text-sm font-bold text-[#1e293b] dark:text-white">{item.label}</p>
                      <p className="text-xs text-slate-500">{item.current} of {item.max} used</p>
                    </div>
                  </div>
                  <ProgressBar current={item.current} max={item.max} showPercentage={false} />
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'activity' && (
          <div>
            {activity.length === 0 ? (
              <EmptyState title="No activity" description="No recent activity for this agency." />
            ) : (
              <div className="space-y-3">
                {activity.map((item: any) => (
                  <div key={item.id} className="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 dark:hover:bg-white/5 transition-colors">
                    <Activity size={16} className="text-slate-400 mt-0.5" />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-[#1e293b] dark:text-white">{item.action}</p>
                      <p className="text-xs text-slate-500">{item.description}</p>
                    </div>
                    <span className="text-xs text-slate-400">{item.timestamp ? new Date(item.timestamp).toLocaleString() : '-'}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'security' && (
          <div>
            {securityLogs.length === 0 ? (
              <EmptyState title="No security logs" description="No security events for this agency." />
            ) : (
              <div className="overflow-x-auto no-scrollbar">
                <DataTable
                columns={[
                  { key: 'action', header: 'Action' },
                  { key: 'ip', header: 'IP Address', render: (row: any) => <span className="text-sm font-mono text-slate-500">{row.ipAddress || '-'}</span> },
                  { key: 'result', header: 'Result', render: (row: any) => <Badge variant={row.isSuccess ? 'success' : 'danger'}>{row.isSuccess ? 'Success' : 'Failed'}</Badge> },
                  { key: 'time', header: 'Time', render: (row: any) => <span className="text-xs text-slate-500">{row.timestamp ? new Date(row.timestamp).toLocaleString() : '-'}</span> },
                ]}
                data={securityLogs}
                keyExtractor={(row) => row.id}
              />
              </div>
            )}
          </div>
        )}
      </div>

      {/* Change Plan Modal */}
      <Modal
        isOpen={showPlanModal}
        onClose={() => { setShowPlanModal(false); setSelectedPlan(''); }}
        title="Change Subscription Plan"
        size="sm"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSubscribe} disabled={!selectedPlan} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-40">
              Subscribe
            </button>
            <button onClick={() => { setShowPlanModal(false); setSelectedPlan(''); }} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Cancel
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Select Plan" required>
            <SelectInput
              value={selectedPlan}
              onChange={setSelectedPlan}
              options={[{ value: '', label: 'Choose a plan...' }, ...planOptions]}
            />
          </FormField>
          {selectedPlan && (
            <div className="p-3 bg-emerald-50 dark:bg-emerald-500/10 rounded-xl border border-emerald-200 dark:border-emerald-500/20">
              <p className="text-xs text-emerald-700 dark:text-emerald-400 font-medium">This will immediately change the agency's subscription plan.</p>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
