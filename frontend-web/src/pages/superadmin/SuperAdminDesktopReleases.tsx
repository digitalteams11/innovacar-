import { useEffect, useState } from 'react';
import { MonitorSmartphone, Plus, Rocket, Archive, Ban } from 'lucide-react';
import { superAdminApi } from '../../api/superAdminApi';
import { PageHeader, Modal, FormField, TextInput, TextArea, SelectInput, Badge, EmptyState } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';
import { useConfirm } from '../../context/ConfirmContext';

const channelOptions = [
  { value: 'STABLE', label: 'Stable' },
  { value: 'BETA', label: 'Beta' },
];

const statusVariant: Record<string, 'success' | 'default' | 'warning' | 'danger'> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DEPRECATED: 'warning',
  WITHDRAWN: 'danger',
};

const defaultForm = {
  version: '', semanticVersion: '', fileName: '', downloadUrl: '',
  fileSizeBytes: '', sha256: '', minimumOs: 'Windows 10', mandatoryUpdate: false,
  channel: 'STABLE', releaseNotesEn: '', releaseNotesFr: '', releaseNotesAr: '',
};

export default function SuperAdminDesktopReleases() {
  const { showToast } = useToast();
  const confirm = useConfirm();
  const [releases, setReleases] = useState<any[]>([]);
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
      const res = await superAdminApi.getDesktopReleases();
      setReleases(res.data?.data || []);
    } catch (err) {
      console.error(err);
      showToast('Unable to load desktop releases. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setForm(defaultForm);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.version.trim() || !form.downloadUrl.trim() || !form.fileName.trim()) {
      showToast('Version, file name and download URL are required', 'warning');
      return;
    }
    setSaving(true);
    try {
      const { data } = await superAdminApi.createDesktopRelease({
        ...form,
        semanticVersion: form.semanticVersion.trim() || form.version.trim(),
        fileSizeBytes: form.fileSizeBytes ? Number(form.fileSizeBytes) : null,
      });
      showToast(data?.message || 'Release created as draft', 'success');
      setShowModal(false);
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || err?.response?.data?.message || 'Unable to create release.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const publish = async (release: any) => {
    const confirmed = await confirm({
      title: `Publish version ${release.version}?`,
      description: 'This will immediately become the "Download for Windows" target on the landing page, /desktop, and inside the app.',
      confirmLabel: 'Publish',
      cancelLabel: 'Cancel',
      tone: 'warning',
    });
    if (!confirmed) return;
    try {
      const { data } = await superAdminApi.publishDesktopRelease(release.id);
      showToast(data?.message || 'Release published', 'success');
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || err?.response?.data?.message || 'Unable to publish release.', 'error');
    }
  };

  const withdraw = async (release: any) => {
    const confirmed = await confirm({
      title: `Withdraw version ${release.version}?`,
      description: 'It will stop being downloadable everywhere immediately.',
      confirmLabel: 'Withdraw',
      cancelLabel: 'Cancel',
      tone: 'danger',
    });
    if (!confirmed) return;
    try {
      const { data } = await superAdminApi.withdrawDesktopRelease(release.id);
      showToast(data?.message || 'Release withdrawn', 'success');
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to withdraw release.', 'error');
    }
  };

  const deprecate = async (release: any) => {
    const confirmed = await confirm({
      title: `Mark version ${release.version} as deprecated?`,
      description: 'It stays downloadable but is flagged as outdated.',
      confirmLabel: 'Mark deprecated',
      cancelLabel: 'Cancel',
      tone: 'warning',
    });
    if (!confirmed) return;
    try {
      const { data } = await superAdminApi.deprecateDesktopRelease(release.id);
      showToast(data?.message || 'Release deprecated', 'success');
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to deprecate release.', 'error');
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Desktop Releases" subtitle="Manage the Windows installer that powers the public and in-app Desktop Center — no fake download links.">
        <button onClick={openCreate} className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft">
          <Plus size={16} />
          <span className="hidden sm:inline">New Release</span>
        </button>
      </PageHeader>

      <div className="bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-xl p-4 text-xs text-amber-800 dark:text-amber-200">
        <strong>Code-signing status:</strong> no Windows code-signing certificate is configured yet. Publishing a release here makes the raw, unsigned installer downloadable — do not publish to production users until signing is in place and documented.
      </div>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-sm text-slate-400">Loading...</div>
        ) : releases.length === 0 ? (
          <EmptyState icon={MonitorSmartphone} title="No releases yet" description="Create one to start promoting the Windows desktop app." />
        ) : (
          <div className="divide-y divide-[#e8e6e1]/60 dark:divide-white/5">
            {releases.map((r: any) => (
              <div key={r.id} className="p-4 sm:p-5 flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <h3 className="text-sm font-bold text-[#1e293b] dark:text-white">v{r.version}</h3>
                    <Badge variant={statusVariant[r.status] || 'default'}>{r.status}</Badge>
                    <Badge variant="default">{r.channel}</Badge>
                    {r.mandatoryUpdate && <Badge variant="warning">Mandatory</Badge>}
                  </div>
                  <p className="text-xs text-slate-500 dark:text-slate-400 truncate">{r.fileName} · {r.fileSizeBytes ? `${(r.fileSizeBytes / (1024 * 1024)).toFixed(0)} MB` : 'size unknown'}</p>
                  <p className="text-[10px] text-slate-400 mt-1 truncate">{r.downloadUrl}</p>
                </div>
                <div className="flex gap-2 shrink-0">
                  {r.status === 'DRAFT' && (
                    <button onClick={() => publish(r)} title="Publish" className="p-2 rounded-lg bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600">
                      <Rocket size={14} />
                    </button>
                  )}
                  {r.status === 'PUBLISHED' && (
                    <button onClick={() => deprecate(r)} title="Deprecate" className="p-2 rounded-lg bg-amber-50 dark:bg-amber-500/10 text-amber-600">
                      <Archive size={14} />
                    </button>
                  )}
                  {(r.status === 'PUBLISHED' || r.status === 'DEPRECATED') && (
                    <button onClick={() => withdraw(r)} title="Withdraw" className="p-2 rounded-lg bg-rose-50 dark:bg-rose-500/10 text-rose-600">
                      <Ban size={14} />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title="New Desktop Release"
        size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSave} disabled={saving} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50">
              {saving ? 'Saving...' : 'Save as Draft'}
            </button>
            <button onClick={() => setShowModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
              Cancel
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <FormField label="Version" required><TextInput value={form.version} onChange={(v) => setForm({ ...form, version: v })} placeholder="1.2.0" /></FormField>
            <FormField label="Channel"><SelectInput value={form.channel} onChange={(v) => setForm({ ...form, channel: v })} options={channelOptions} /></FormField>
          </div>
          <FormField label="File name" required><TextInput value={form.fileName} onChange={(v) => setForm({ ...form, fileName: v })} placeholder="Innovacar-Setup-1.2.0.exe" /></FormField>
          <FormField label="Download URL (HTTPS, approved host only)" required>
            <TextInput value={form.downloadUrl} onChange={(v) => setForm({ ...form, downloadUrl: v })} placeholder="https://github.com/innovacar/desktop/releases/download/v1.2.0/Innovacar-Setup-1.2.0.exe" />
          </FormField>
          <div className="grid grid-cols-2 gap-4">
            <FormField label="File size (bytes)"><TextInput value={form.fileSizeBytes} onChange={(v) => setForm({ ...form, fileSizeBytes: v })} placeholder="98452311" /></FormField>
            <FormField label="SHA-256 checksum"><TextInput value={form.sha256} onChange={(v) => setForm({ ...form, sha256: v })} placeholder="64-character hex digest" /></FormField>
          </div>
          <FormField label="Minimum OS"><TextInput value={form.minimumOs} onChange={(v) => setForm({ ...form, minimumOs: v })} placeholder="Windows 10" /></FormField>
          <FormField label="Release notes (English)"><TextArea value={form.releaseNotesEn} onChange={(v) => setForm({ ...form, releaseNotesEn: v })} placeholder={"One line per bullet point"} /></FormField>
          <FormField label="Release notes (French)"><TextArea value={form.releaseNotesFr} onChange={(v) => setForm({ ...form, releaseNotesFr: v })} /></FormField>
          <FormField label="Release notes (Arabic)"><TextArea value={form.releaseNotesAr} onChange={(v) => setForm({ ...form, releaseNotesAr: v })} /></FormField>
          <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <input type="checkbox" checked={form.mandatoryUpdate} onChange={(e) => setForm({ ...form, mandatoryUpdate: e.target.checked })} />
            Mandatory update (existing installs should be prompted to update)
          </label>
          <p className="text-xs text-slate-400">Releases are created as drafts and are never publicly downloadable until you explicitly publish them.</p>
        </div>
      </Modal>
    </div>
  );
}
