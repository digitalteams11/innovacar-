import { useEffect, useState } from 'react';
import { Megaphone, Plus, Power } from 'lucide-react';
import { superAdminApi } from '../../api/superAdminApi';
import { PageHeader, Modal, FormField, TextInput, TextArea, SelectInput, Badge, EmptyState } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const audienceOptions = [
  { value: 'ALL', label: 'All agencies' },
  { value: 'SELECTED_AGENCIES', label: 'Selected agencies (comma-separated IDs)' },
  { value: 'PLAN', label: 'Agencies on a specific plan' },
  { value: 'ROLE', label: 'Users with a specific role' },
];

const priorityOptions = [
  { value: 'LOW', label: 'Low' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

const defaultForm = {
  title: '', message: '', audience: 'ALL', audienceValue: '', priority: 'NORMAL',
  startsAt: '', endsAt: '', active: true,
};

export default function SuperAdminAnnouncements() {
  const { showToast } = useToast();
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<any>(defaultForm);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getAnnouncements();
      setAnnouncements(res.data?.data || res.data || []);
    } catch (err) {
      console.error(err);
      showToast('Unable to load announcements. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setForm(defaultForm);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.title.trim() || !form.message.trim()) {
      showToast('Title and message are required', 'warning');
      return;
    }
    setSaving(true);
    try {
      const { data } = await superAdminApi.createAnnouncement({
        ...form,
        startsAt: form.startsAt ? `${form.startsAt}:00` : null,
        endsAt: form.endsAt ? `${form.endsAt}:00` : null,
      });
      showToast(data?.message || 'Announcement created', 'success');
      setShowModal(false);
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to create announcement. Please try again later.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (announcement: any) => {
    try {
      const { data } = await superAdminApi.setAnnouncementStatus(announcement.id, !announcement.active);
      showToast(data?.message || 'Announcement updated', 'success');
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update announcement.', 'error');
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Announcements" subtitle="Broadcast platform-wide messages to agencies (shown as an in-app banner)">
        <button onClick={openCreate} className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft">
          <Plus size={16} />
          <span className="hidden sm:inline">New Announcement</span>
        </button>
      </PageHeader>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-sm text-slate-400">Loading...</div>
        ) : announcements.length === 0 ? (
          <EmptyState icon={Megaphone} title="No announcements yet" description="Create one to broadcast a message to your agencies." />
        ) : (
          <div className="divide-y divide-[#e8e6e1]/60 dark:divide-white/5">
            {announcements.map((a: any) => (
              <div key={a.id} className="p-4 sm:p-5 flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <h3 className="text-sm font-bold text-[#1e293b] dark:text-white">{a.title}</h3>
                    <Badge variant={a.active ? 'success' : 'default'}>{a.active ? 'Active' : 'Inactive'}</Badge>
                    <Badge variant={a.priority === 'CRITICAL' ? 'danger' : a.priority === 'HIGH' ? 'warning' : 'default'}>{a.priority}</Badge>
                    <span className="text-[10px] text-slate-400">{a.audience}{a.audienceValue ? `: ${a.audienceValue}` : ''}</span>
                  </div>
                  <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2">{a.message}</p>
                  <p className="text-[10px] text-slate-400 mt-1">
                    {a.startsAt ? `From ${new Date(a.startsAt).toLocaleString()}` : 'No start date'} · {a.endsAt ? `Until ${new Date(a.endsAt).toLocaleString()}` : 'No end date'}
                  </p>
                </div>
                <button
                  onClick={() => toggleActive(a)}
                  title={a.active ? 'Deactivate' : 'Activate'}
                  className={`p-2 rounded-lg shrink-0 transition-colors ${a.active ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600' : 'bg-slate-100 dark:bg-white/5 text-slate-400'}`}
                >
                  <Power size={14} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title="New Announcement"
        size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSave} disabled={saving} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50">
              {saving ? 'Saving...' : 'Send Announcement'}
            </button>
            <button onClick={() => setShowModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Cancel
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Title" required><TextInput value={form.title} onChange={(v) => setForm({ ...form, title: v })} placeholder="System maintenance tonight" /></FormField>
          <FormField label="Message" required><TextArea value={form.message} onChange={(v) => setForm({ ...form, message: v })} placeholder="We will perform scheduled maintenance from 1am to 3am..." /></FormField>
          <FormField label="Audience">
            <SelectInput value={form.audience} onChange={(v) => setForm({ ...form, audience: v })} options={audienceOptions} />
          </FormField>
          {form.audience !== 'ALL' && (
            <FormField label={form.audience === 'SELECTED_AGENCIES' ? 'Agency IDs (comma-separated)' : form.audience === 'PLAN' ? 'Plan name' : 'Role name'}>
              <TextInput value={form.audienceValue} onChange={(v) => setForm({ ...form, audienceValue: v })} placeholder={form.audience === 'SELECTED_AGENCIES' ? '12,18,42' : form.audience === 'PLAN' ? 'Premium' : 'ADMIN'} />
            </FormField>
          )}
          <FormField label="Priority"><SelectInput value={form.priority} onChange={(v) => setForm({ ...form, priority: v })} options={priorityOptions} /></FormField>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="Starts at"><input type="datetime-local" value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
            <FormField label="Ends at"><input type="datetime-local" value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
          </div>
          <p className="text-xs text-slate-400">Delivered in-app on the agency dashboard. Email/SMS/WhatsApp channels are not yet wired to a real provider, so they're intentionally left out for now rather than faking delivery.</p>
        </div>
      </Modal>
    </div>
  );
}
