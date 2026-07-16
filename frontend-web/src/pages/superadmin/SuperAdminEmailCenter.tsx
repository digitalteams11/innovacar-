import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Mail, Send, Plus, Trash2, Edit2, CheckCircle, XCircle, AlertTriangle,
  Settings, Eye, EyeOff, Wifi, WifiOff, Clock, Copy, RotateCcw,
  Search, ChevronDown, X, Code,
} from 'lucide-react';
import { PageHeader, TabGroup, DataTable, Modal, Badge } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

// ── Types ─────────────────────────────────────────────────────────────────────

interface SmtpSettings {
  smtpProvider: string;
  smtpHost: string; smtpPort: number; smtpUsername: string; smtpPassword: string;
  hasPassword: boolean; smtpUseTls: boolean; smtpSslEnabled: boolean; smtpEnabled: boolean;
  fromEmail: string; fromName: string; smtpReplyTo: string;
  lastTestStatus: string | null; lastTestAt: string | null; lastTestErrorCode: string | null;
}

interface EmailTemplate {
  id: number; name: string; templateKey: string | null; type: string; language: string;
  subject: string; bodyHtml: string | null; bodyText: string | null;
  isActive: boolean; systemDefault: boolean; updatedAt: string | null;
}

interface TemplateVar { name: string; description: string; example: string; }

const defaultSmtp: SmtpSettings = {
  smtpProvider: 'ZOHO',
  smtpHost: '', smtpPort: 587, smtpUsername: '', smtpPassword: '',
  hasPassword: false, smtpUseTls: true, smtpSslEnabled: false, smtpEnabled: false,
  fromEmail: '', fromName: '', smtpReplyTo: '',
  lastTestStatus: null, lastTestAt: null, lastTestErrorCode: null,
};

const PROVIDER_PRESETS: Record<string, { host: string; port: number; tls: boolean }> = {
  ZOHO:   { host: 'smtp.zoho.com',    port: 587, tls: true  },
  GMAIL:  { host: 'smtp.gmail.com',   port: 587, tls: true  },
  CUSTOM: { host: '',                  port: 587, tls: true  },
};

const EXAMPLE_VARS: Record<string, string> = {
  userName: 'Mohamed Amddah', agencyName: 'Innovacar Agency', clientName: 'Ahmed Yacoubi',
  contractNumber: 'CTR-2026-00002', reservationNumber: 'RES-2026-00010',
  vehicleName: 'Hyundai i20', plateNumber: '12345-A-6',
  startDate: '2026-07-01', endDate: '2026-07-05',
  totalAmount: '2400 MAD', paidAmount: '1200 MAD', remainingAmount: '1200 MAD',
  dashboardUrl: 'https://app.innovacar.app', contractPdfUrl: 'https://app.innovacar.app/contracts/pdf/demo',
  planName: 'Pro', trialEndDate: '2026-08-01', currentYear: new Date().getFullYear().toString(),
  companyName: 'Innovax Technologies', fromName: 'RentCar',
  resetPasswordUrl: 'https://app.innovacar.app/reset/demo', supportUrl: 'https://app.innovacar.app/support',
  ticketNumber: 'TKT-2026-00001', paymentUrl: 'https://app.innovacar.app/billing',
  location: '33.5731° N, 7.5898° W', alertTime: '2026-07-01 14:30',
  deviceName: 'Chrome on Windows', ipAddress: '192.168.1.1', loginTime: '2026-07-01 14:30',
  securityUrl: 'https://app.innovacar.app/settings/security',
  replyMessage: 'We have reviewed your request and will resolve it shortly.',
  employeeName: 'Karim Benali', roleName: 'Agent', loginEmail: 'karim@innovacar.app',
  temporaryPassword: 'Temp@1234', userEmail: 'admin@innovacar.app',
};

const LANG_OPTIONS = ['EN', 'FR', 'AR'];

function previewHtml(html: string): string {
  if (!html) return '';
  let out = html;
  for (const [k, v] of Object.entries(EXAMPLE_VARS)) {
    out = out.split(`{{${k}}}`).join(v);
  }
  return out;
}

const fieldCls = 'w-full px-3 py-2.5 bg-slate-50 dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm text-[#1e293b] dark:text-white outline-none focus:border-brand-400 transition-colors';

// ── Main component ────────────────────────────────────────────────────────────

export default function SuperAdminEmailCenter() {
  useTranslation();
  const { showToast } = useToast();

  // ── Global state ──────────────────────────────────────────────────────────
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('smtp');

  // SMTP
  const [smtp, setSmtp] = useState<SmtpSettings>(defaultSmtp);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [testEmailAddr, setTestEmailAddr] = useState('');
  const [testLoading, setTestLoading] = useState(false);
  const [testErrorCode, setTestErrorCode] = useState<string | null>(null);
  const [diagnoseLoading, setDiagnoseLoading] = useState(false);
  const [diagnoseResults, setDiagnoseResults] = useState<any[] | null>(null);

  // Templates
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [langFilter, setLangFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [templateTypes, setTemplateTypes] = useState<string[]>([]);

  // Edit modal
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<EmailTemplate | null>(null);
  const [form, setForm] = useState<Partial<EmailTemplate>>({});
  const [formVars, setFormVars] = useState<TemplateVar[]>([]);
  const [editTab, setEditTab] = useState<'edit' | 'preview' | 'variables'>('edit');
  const [saving, setSaving] = useState(false);

  // Test-send modal
  const [showTestModal, setShowTestModal] = useState(false);
  const [testTarget, setTestTarget] = useState<EmailTemplate | null>(null);
  const [testTo, setTestTo] = useState('');
  const [testSending, setTestSending] = useState(false);

  // Logs
  const [logs, setLogs] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);

  // Support Center routing
  const [routing, setRouting] = useState({
    contactEmail: '', supportEmail: '', technicalEmail: '',
    billingEmail: '', securityEmail: '', fallbackEmail: '',
  });
  const [routingLoading, setRoutingLoading] = useState(false);

  // ── Data loading ──────────────────────────────────────────────────────────
  useEffect(() => { fetchAll(); }, []);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [smtpRes, tmplRes, logsRes, analyticsRes, typesRes, routingRes] = await Promise.all([
        superAdminApi.getSmtpSettings().catch(() => ({ data: {} })),
        superAdminApi.getEmailTemplates().catch(() => ({ data: [] })),
        superAdminApi.getEmailLogs().catch(() => ({ data: [] })),
        superAdminApi.getEmailAnalytics().catch(() => ({ data: null })),
        superAdminApi.getEmailTemplateTypes().catch(() => ({ data: [] })),
        superAdminApi.getSupportRoutingSettings().catch(() => ({ data: {} })),
      ]);
      const d = smtpRes.data ?? {};
      setSmtp({
        smtpProvider: d.smtpProvider ?? 'ZOHO',
        smtpHost: d.smtpHost ?? '', smtpPort: d.smtpPort ?? 587,
        smtpUsername: d.smtpUsername ?? '', smtpPassword: '',
        hasPassword: Boolean(d.hasPassword),
        smtpUseTls: d.smtpUseTls !== undefined ? Boolean(d.smtpUseTls) : true,
        smtpSslEnabled: Boolean(d.smtpSslEnabled),
        smtpEnabled: Boolean(d.smtpEnabled),
        fromEmail: d.fromEmail ?? '', fromName: d.fromName ?? '',
        smtpReplyTo: d.smtpReplyTo ?? '',
        lastTestStatus: d.lastTestStatus ?? null, lastTestAt: d.lastTestAt ?? null,
        lastTestErrorCode: d.lastTestErrorCode ?? null,
      });
      setTemplates(Array.isArray(tmplRes.data) ? tmplRes.data : []);
      setLogs(Array.isArray(logsRes.data) ? logsRes.data : []);
      setAnalytics(analyticsRes.data);
      setTemplateTypes(Array.isArray(typesRes.data) ? typesRes.data : []);
      const r = routingRes.data ?? {};
      setRouting({
        contactEmail: r.contactEmail ?? '', supportEmail: r.supportEmail ?? '',
        technicalEmail: r.technicalEmail ?? '', billingEmail: r.billingEmail ?? '',
        securityEmail: r.securityEmail ?? '', fallbackEmail: r.fallbackEmail ?? '',
      });
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  // ── Support routing handlers ─────────────────────────────────────────────
  const handleSaveRouting = async () => {
    setRoutingLoading(true);
    try {
      const res = await superAdminApi.updateSupportRoutingSettings(routing);
      const d = res.data ?? {};
      setRouting({
        contactEmail: d.contactEmail ?? '', supportEmail: d.supportEmail ?? '',
        technicalEmail: d.technicalEmail ?? '', billingEmail: d.billingEmail ?? '',
        securityEmail: d.securityEmail ?? '', fallbackEmail: d.fallbackEmail ?? '',
      });
      showToast('Support routing settings saved successfully', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Unable to save routing settings', 'error');
    } finally { setRoutingLoading(false); }
  };

  // ── SMTP handlers ─────────────────────────────────────────────────────────
  const handleSaveSmtp = async () => {
    setSmtpLoading(true);
    try {
      const payload: any = { ...smtp };
      if (!payload.smtpPassword) delete payload.smtpPassword;
      delete payload.hasPassword; delete payload.lastTestStatus; delete payload.lastTestAt; delete payload.lastTestErrorCode;
      const res = await superAdminApi.updateSmtpSettings(payload);
      const d = res.data ?? {};
      setSmtp(prev => ({
        ...prev, ...d, smtpPassword: '',
        hasPassword: Boolean(d.hasPassword),
        lastTestErrorCode: d.lastTestErrorCode ?? prev.lastTestErrorCode,
      }));
      showToast('SMTP settings saved successfully', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Unable to save SMTP settings', 'error');
    } finally { setSmtpLoading(false); }
  };

  const handleTestSmtp = async () => {
    if (!testEmailAddr.trim()) { showToast('Enter a recipient email', 'error'); return; }
    setTestLoading(true); setTestErrorCode(null);
    try {
      const res = await superAdminApi.sendSmtpTestEmail(testEmailAddr.trim());
      if (res.data?.success) { showToast('Test email sent successfully!', 'success'); setTestErrorCode(null); }
      else { setTestErrorCode(res.data?.errorCode ?? null); showToast(res.data?.message || 'Test failed', 'error'); }
      await fetchAll();
    } catch (err: any) {
      const data = err?.response?.data;
      setTestErrorCode(data?.errorCode ?? null);
      showToast(data?.message || 'Test email failed — check SMTP settings', 'error');
    } finally { setTestLoading(false); }
  };

  const handleDiagnose = async () => {
    setDiagnoseLoading(true); setDiagnoseResults(null);
    try {
      const res = await superAdminApi.diagnoseSmtp();
      setDiagnoseResults(Array.isArray(res.data) ? res.data : [res.data]);
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Diagnostics failed', 'error');
    } finally { setDiagnoseLoading(false); }
  };

  // ── Template handlers ─────────────────────────────────────────────────────
  const openCreate = () => {
    setEditingTemplate(null);
    setForm({ name: '', templateKey: '', type: 'AUTH', language: 'EN', subject: '', bodyHtml: '', bodyText: '', isActive: true });
    setFormVars([]);
    setEditTab('edit');
    setShowEditModal(true);
  };

  const openEdit = async (t: EmailTemplate) => {
    setEditingTemplate(t);
    setForm({ ...t });
    setEditTab('edit');
    if (t.templateKey) {
      try {
        const res = await superAdminApi.getEmailTemplateVariables(t.templateKey);
        setFormVars(Array.isArray(res.data) ? res.data : []);
      } catch { setFormVars([]); }
    } else { setFormVars([]); }
    setShowEditModal(true);
  };

  const handleSave = async () => {
    if (!form.name?.trim()) { showToast('Template name is required', 'error'); return; }
    if (!form.subject?.trim()) { showToast('Subject is required', 'error'); return; }
    setSaving(true);
    try {
      if (editingTemplate) {
        await superAdminApi.updateEmailTemplate(editingTemplate.id, form);
        showToast('Template updated', 'success');
      } else {
        await superAdminApi.createEmailTemplate(form);
        showToast('Template created', 'success');
      }
      setShowEditModal(false);
      fetchAll();
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Unable to save template', 'error');
    } finally { setSaving(false); }
  };

  const handleDelete = async (t: EmailTemplate) => {
    const verb = t.systemDefault ? 'deactivate' : 'delete';
    if (!window.confirm(`${verb === 'delete' ? 'Delete' : 'Deactivate'} template "${t.name}"?`)) return;
    try {
      await superAdminApi.deleteEmailTemplate(t.id);
      showToast(`Template ${verb}d`, 'success');
      fetchAll();
    } catch (err: any) { showToast(err?.response?.data?.message || `Unable to ${verb}`, 'error'); }
  };

  const handleDuplicate = async (t: EmailTemplate) => {
    try {
      await superAdminApi.duplicateEmailTemplate(t.id);
      showToast('Template duplicated', 'success');
      fetchAll();
    } catch (err: any) { showToast(err?.response?.data?.message || 'Unable to duplicate', 'error'); }
  };

  const handleReset = async (t: EmailTemplate) => {
    if (!window.confirm(`Reset "${t.name}" to its default content?`)) return;
    try {
      await superAdminApi.resetEmailTemplate(t.id);
      showToast('Template reset to default', 'success');
      fetchAll();
    } catch (err: any) { showToast(err?.response?.data?.message || 'Reset failed', 'error'); }
  };

  const handleToggleActive = async (t: EmailTemplate) => {
    try {
      await superAdminApi.updateEmailTemplate(t.id, { isActive: !t.isActive });
      showToast(t.isActive ? 'Template deactivated' : 'Template activated', 'success');
      fetchAll();
    } catch (err: any) { showToast('Unable to update status', 'error'); }
  };

  const openTestSend = (t: EmailTemplate) => { setTestTarget(t); setTestTo(''); setShowTestModal(true); };

  const handleTestSend = async () => {
    if (!testTarget) return;
    if (!testTo.trim()) { showToast('Enter a recipient email', 'error'); return; }
    setTestSending(true);
    try {
      const res = await superAdminApi.testSendTemplate(testTarget.id, { to: testTo.trim(), variables: EXAMPLE_VARS });
      if (res.data?.success) { showToast('Test email sent!', 'success'); setShowTestModal(false); }
      else showToast(res.data?.message || 'Test send failed', 'error');
    } catch (err: any) { showToast(err?.response?.data?.message || 'Test send failed', 'error'); }
    finally { setTestSending(false); }
  };

  // ── Filtered templates ─────────────────────────────────────────────────────
  const filtered = templates.filter(t => {
    const matchSearch = !search ||
      t.name.toLowerCase().includes(search.toLowerCase()) ||
      (t.templateKey ?? '').toLowerCase().includes(search.toLowerCase()) ||
      t.subject.toLowerCase().includes(search.toLowerCase());
    const matchType   = typeFilter === 'ALL' || t.type === typeFilter;
    const matchLang   = langFilter === 'ALL' || t.language === langFilter;
    const matchStatus = statusFilter === 'ALL' || (statusFilter === 'ACTIVE' ? t.isActive : !t.isActive);
    return matchSearch && matchType && matchLang && matchStatus;
  });

  const lastTestOk = smtp.lastTestStatus === 'SENT';

  const tabs = [
    { id: 'smtp',      label: 'SMTP Settings' },
    { id: 'routing',   label: 'Support Routing' },
    { id: 'templates', label: `Templates (${templates.length})` },
    { id: 'logs',      label: 'Email Logs' },
    { id: 'analytics', label: 'Analytics' },
  ];

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Email & Communication Center" subtitle="Manage Zoho SMTP, professional email templates, and delivery logs">
        {activeTab === 'templates' && (
          <button onClick={openCreate}
            className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft">
            <Plus size={16} /><span className="hidden sm:inline">New Template</span>
          </button>
        )}
      </PageHeader>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">

        {/* ── SMTP TAB ── */}
        {activeTab === 'smtp' && (
          <div className="space-y-6">
            {/* Status cards */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {[
                { label: 'Status', value: smtp.smtpEnabled ? 'Enabled' : 'Disabled', icon: smtp.smtpEnabled ? Wifi : WifiOff, color: smtp.smtpEnabled ? 'text-emerald-600' : 'text-slate-400' },
                { label: 'From Email', value: smtp.fromEmail || '—', icon: Mail, color: 'text-brand-500' },
                { label: 'Host', value: smtp.smtpHost || '—', icon: Settings, color: 'text-slate-500' },
                { label: 'Last Test', value: smtp.lastTestStatus ? (lastTestOk ? 'Passed' : 'Failed') : 'Not tested', icon: smtp.lastTestStatus ? (lastTestOk ? CheckCircle : XCircle) : Clock, color: smtp.lastTestStatus ? (lastTestOk ? 'text-emerald-600' : 'text-rose-500') : 'text-slate-400' },
              ].map(item => (
                <div key={item.label} className="bg-slate-50 dark:bg-white/5 rounded-xl p-4">
                  <div className="flex items-center gap-2 mb-1"><item.icon size={14} className={item.color} /><span className="text-xs text-slate-500">{item.label}</span></div>
                  <p className={`text-sm font-bold truncate ${item.color}`}>{item.value}</p>
                </div>
              ))}
            </div>

            {/* Form */}
            <div className="border-t border-[#e8e6e1]/60 pt-5 space-y-4">
              <h3 className="text-sm font-bold text-[#1e293b] dark:text-white flex items-center gap-2"><Settings size={15} /> SMTP Configuration</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <label className="space-y-1.5 sm:col-span-2">
                  <span className={labelCls}>Provider</span>
                  <div className="flex gap-2">
                    {(['ZOHO', 'GMAIL', 'CUSTOM'] as const).map(p => (
                      <button key={p} type="button"
                        onClick={() => {
                          const preset = PROVIDER_PRESETS[p];
                          setSmtp(prev => ({
                            ...prev,
                            smtpProvider: p,
                            ...(p !== 'CUSTOM' ? { smtpHost: preset.host, smtpPort: preset.port, smtpUseTls: preset.tls } : {}),
                          }));
                        }}
                        className={`px-4 py-2 rounded-xl text-xs font-semibold border transition-colors ${smtp.smtpProvider === p ? 'bg-[#0a0f2c] text-white border-[#0a0f2c]' : 'bg-slate-50 dark:bg-white/5 text-slate-600 dark:text-slate-300 border-[#e8e6e1] dark:border-white/10 hover:border-slate-400'}`}
                      >{p}</button>
                    ))}
                  </div>
                  {smtp.smtpProvider === 'GMAIL' && (
                    <p className="text-xs text-blue-600 dark:text-blue-400 mt-1">Gmail: use an <strong>App Password</strong> (not your Gmail password). Enable 2-Step Verification → App Passwords in your Google Account.</p>
                  )}
                </label>
                <label className="space-y-1.5"><span className={labelCls}>SMTP Host</span><input type="text" value={smtp.smtpHost} onChange={e => setSmtp({ ...smtp, smtpHost: e.target.value })} placeholder="smtp.zoho.com" className={fieldCls} /></label>
                <label className="space-y-1.5"><span className={labelCls}>SMTP Port</span><input type="number" value={smtp.smtpPort} onChange={e => setSmtp({ ...smtp, smtpPort: Number(e.target.value) })} className={fieldCls} /></label>
                <label className="space-y-1.5"><span className={labelCls}>SMTP Username (email)</span><input type="email" value={smtp.smtpUsername} onChange={e => setSmtp({ ...smtp, smtpUsername: e.target.value })} placeholder="noreply@yourdomain.com" className={fieldCls} /></label>
                <label className="space-y-1.5">
                  <span className={labelCls}>SMTP Password</span>
                  <div className="relative">
                    <input type={showPassword ? 'text' : 'password'} value={smtp.smtpPassword} onChange={e => setSmtp({ ...smtp, smtpPassword: e.target.value })} placeholder={smtp.hasPassword ? 'Stored securely — enter a new password to replace' : 'Enter SMTP password'} className={`${fieldCls} pr-10`} />
                    <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">{showPassword ? <EyeOff size={14} /> : <Eye size={14} />}</button>
                  </div>
                  {smtp.hasPassword && !smtp.smtpPassword && <p className="text-xs text-emerald-600 mt-1">Password stored securely — enter a new one to replace.</p>}
                  {smtp.smtpPassword && <p className="text-xs text-amber-600 font-semibold mt-1">New password entered — it will replace the stored one when you save.</p>}
                  <p className="text-xs text-slate-400 mt-1">For Zoho: use an App Password from <strong>Zoho Account → Security → App Passwords</strong>. Generate it from the same mailbox account (<code className="font-mono bg-slate-100 dark:bg-white/10 px-1 rounded">{smtp.smtpUsername || 'your-email@domain.com'}</code>), not from another admin account.</p>
                </label>
                <label className="space-y-1.5"><span className={labelCls}>From Email</span><input type="email" value={smtp.fromEmail} onChange={e => setSmtp({ ...smtp, fromEmail: e.target.value })} placeholder="noreply@yourdomain.com" className={fieldCls} /></label>
                <label className="space-y-1.5"><span className={labelCls}>From Name</span><input type="text" value={smtp.fromName} onChange={e => setSmtp({ ...smtp, fromName: e.target.value })} placeholder="Innovax Technologies" className={fieldCls} /></label>
                <label className="space-y-1.5"><span className={labelCls}>Reply-To (optional)</span><input type="email" value={smtp.smtpReplyTo} onChange={e => setSmtp({ ...smtp, smtpReplyTo: e.target.value })} placeholder="support@yourdomain.com" className={fieldCls} /></label>
              </div>
              <div className="flex flex-wrap gap-4 pt-2">
                <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={smtp.smtpUseTls} onChange={e => setSmtp({ ...smtp, smtpUseTls: e.target.checked, smtpSslEnabled: e.target.checked ? false : smtp.smtpSslEnabled })} className="w-4 h-4 accent-brand-500 rounded" /><span className="text-sm text-[#1e293b] dark:text-white">Use TLS (STARTTLS, port 587)</span></label>
                <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={smtp.smtpSslEnabled} onChange={e => setSmtp({ ...smtp, smtpSslEnabled: e.target.checked, smtpUseTls: e.target.checked ? false : smtp.smtpUseTls })} className="w-4 h-4 accent-brand-500 rounded" /><span className="text-sm text-[#1e293b] dark:text-white">Use SSL (implicit, port 465)</span></label>
                <label className="flex items-center gap-2 cursor-pointer"><input type="checkbox" checked={smtp.smtpEnabled} onChange={e => setSmtp({ ...smtp, smtpEnabled: e.target.checked })} className="w-4 h-4 accent-brand-500 rounded" /><span className={`text-sm font-semibold ${smtp.smtpEnabled ? 'text-emerald-600' : 'text-slate-400'}`}>{smtp.smtpEnabled ? 'Platform email enabled' : 'Platform email disabled'}</span></label>
              </div>
              {smtp.smtpHost?.toLowerCase().includes('zoho') && (
                <div className="bg-blue-50 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/30 rounded-xl p-4 space-y-2">
                  <p className="text-xs font-bold text-blue-700 dark:text-blue-300 flex items-center gap-1.5"><AlertTriangle size={12} /> Zoho SMTP Checklist — verify before testing</p>
                  <ul className="space-y-1 text-xs text-blue-700 dark:text-blue-300">
                    <li>✓ <strong>SMTP Username</strong> is the <em>full</em> Zoho email (e.g. contact@yourdomain.com)</li>
                    <li>✓ <strong>SMTP Password</strong> is an <em>App Password</em> — Zoho Account → Security → App Passwords (NOT your Zoho login password)</li>
                    <li>✓ <strong>From Email</strong> matches the same verified Zoho address as the username</li>
                    <li>✓ <strong>Host</strong>: use <code className="font-mono bg-blue-100 dark:bg-blue-900/30 px-1 rounded">smtp.zoho.com</code> for free/standard or <code className="font-mono bg-blue-100 dark:bg-blue-900/30 px-1 rounded">smtppro.zoho.com</code> for Zoho Workplace</li>
                    <li>✓ <strong>Port 587</strong> with <strong>STARTTLS</strong> enabled</li>
                    <li>✓ <strong>SMTP Access</strong> is turned on in Zoho Mail → Settings → Mail Accounts → IMAP/SMTP</li>
                  </ul>
                </div>
              )}
              <button onClick={handleSaveSmtp} disabled={smtpLoading} className="flex items-center gap-2 px-5 py-2.5 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors">
                {smtpLoading ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Settings size={14} />} Save SMTP Settings
              </button>
            </div>

            {/* Test */}
            <div className="border-t border-[#e8e6e1]/60 pt-5 space-y-3">
              <h3 className="text-sm font-bold text-[#1e293b] dark:text-white flex items-center gap-2"><Send size={15} /> Send Test Email</h3>
              <p className="text-xs text-slate-500">Save SMTP settings first, then send a test to verify delivery.</p>
              <div className="flex gap-3">
                <input type="email" value={testEmailAddr} onChange={e => setTestEmailAddr(e.target.value)} placeholder="you@example.com" className={`flex-1 ${fieldCls}`} />
                <button onClick={handleTestSmtp} disabled={testLoading || !testEmailAddr.trim()} className="flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors">
                  {testLoading ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Send size={14} />} Send Test
                </button>
              </div>
              {smtp.lastTestAt && (
                <p className="text-xs text-slate-400">Last test: {new Date(smtp.lastTestAt).toLocaleString()} — <span className={lastTestOk ? 'text-emerald-600 font-semibold' : 'text-rose-500 font-semibold'}>{lastTestOk ? 'Passed' : 'Failed'}{smtp.lastTestErrorCode && !lastTestOk ? ` (${smtp.lastTestErrorCode})` : ''}</span></p>
              )}
              {testErrorCode && (() => {
                const isAuthFail  = testErrorCode === 'SMTP_AUTH_FAILED' || testErrorCode === 'EMAIL_AUTH_FAILED';
                const isRecipient = testErrorCode === 'TEST_RECIPIENT_INVALID' || testErrorCode === 'TEST_RECIPIENT_MISSING';
                const knownCodes  = [
                  'SMTP_AUTH_FAILED','EMAIL_AUTH_FAILED',
                  'SMTP_PASSWORD_MISSING','SMTP_HOST_MISSING','SMTP_FROM_EMAIL_MISSING',
                  'SMTP_CONNECTION_FAILED','SMTP_TLS_FAILED','EMAIL_TLS_FAILED',
                  'EMAIL_PROVIDER_UNREACHABLE','EMAIL_PROVIDER_TIMEOUT',
                  'SMTP_USERNAME_INVALID','SMTP_USERNAME_MISSING',
                  'TEST_RECIPIENT_INVALID','TEST_RECIPIENT_MISSING',
                ];
                const colorClass = isAuthFail
                  ? 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30 text-amber-800 dark:text-amber-300'
                  : 'bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/30 text-rose-800 dark:text-rose-300';
                return (
                  <div className={`flex gap-2 p-3 rounded-xl text-xs border ${colorClass}`}>
                    <AlertTriangle size={14} className="shrink-0 mt-0.5" />
                    <span>
                      {isAuthFail && (
                        <><strong>Auth failed (Zoho):</strong> Normal account passwords are rejected. Generate an <strong>App Password</strong> in Zoho Account → Security → App Passwords, then paste it in the password field and save before testing. The SMTP Username and From Email must match the same verified Zoho address.<br />Try also: use <code className="font-mono bg-amber-100 dark:bg-amber-900/30 px-1 rounded">smtppro.zoho.com</code> instead of <code className="font-mono bg-amber-100 dark:bg-amber-900/30 px-1 rounded">smtp.zoho.com</code> and run Diagnose to compare both hosts.</>
                      )}
                      {isRecipient && <><strong>Invalid test recipient.</strong> Enter a valid email address in the "Send test to" field (e.g. you@example.com).</>}
                      {testErrorCode === 'SMTP_PASSWORD_MISSING' && <><strong>No password saved.</strong> Enter your App Password above and click Save SMTP Settings first.</>}
                      {testErrorCode === 'SMTP_HOST_MISSING' && <><strong>SMTP host not set.</strong> Enter your SMTP host (e.g. smtppro.zoho.com) and save.</>}
                      {testErrorCode === 'SMTP_FROM_EMAIL_MISSING' && <><strong>From Email not set.</strong> Enter your From Email address and save.</>}
                      {(testErrorCode === 'SMTP_CONNECTION_FAILED' || testErrorCode === 'EMAIL_PROVIDER_UNREACHABLE') && <><strong>Connection failed.</strong> Check host/port and ensure outbound port 587 is not blocked by your firewall.</>}
                      {(testErrorCode === 'SMTP_TLS_FAILED' || testErrorCode === 'EMAIL_TLS_FAILED') && <><strong>TLS failed.</strong> Ensure "Use TLS" is enabled and port 587 is selected.</>}
                      {testErrorCode === 'EMAIL_PROVIDER_TIMEOUT' && <><strong>Connection timed out.</strong> The SMTP server did not respond. Check host/port or try again.</>}
                      {(testErrorCode === 'SMTP_USERNAME_INVALID' || testErrorCode === 'SMTP_USERNAME_MISSING') && <><strong>Invalid SMTP username.</strong> The username must be a full email address (e.g. contact@yourdomain.com).</>}
                      {!knownCodes.includes(testErrorCode) && <><strong>{testErrorCode}:</strong> Verify all SMTP settings and try again.</>}
                    </span>
                  </div>
                );
              })()}

              {/* Zoho Diagnostics */}
              <div className="pt-1">
                <button
                  onClick={handleDiagnose}
                  disabled={diagnoseLoading}
                  className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 dark:bg-white/5 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 rounded-xl text-xs font-semibold disabled:opacity-50 transition-colors"
                >
                  {diagnoseLoading ? <span className="w-3.5 h-3.5 border-2 border-slate-400/30 border-t-slate-500 rounded-full animate-spin" /> : <Wifi size={13} />}
                  Diagnose SMTP Connection
                </button>
                <p className="text-[10px] text-slate-400 mt-1">Tests connection + auth to your configured host (and both Zoho hosts if applicable). Password is never logged.</p>
                {diagnoseResults && (
                  <div className="mt-2 space-y-2">
                    {diagnoseResults.map((r: any, i: number) => (
                      <div key={i} className={`p-3 rounded-xl text-xs border font-mono ${r.auth === 'OK' ? 'bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/30' : r.connection === 'FAILED' ? 'bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/30' : 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30'}`}>
                        <div className="flex items-center gap-3 flex-wrap">
                          <span className="font-bold text-slate-700 dark:text-slate-200">{r.host}:{r.port}</span>
                          <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${r.connection === 'OK' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>conn: {r.connection ?? 'N/A'}</span>
                          <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${r.auth === 'OK' ? 'bg-emerald-100 text-emerald-700' : r.auth === 'N/A' ? 'bg-slate-100 text-slate-500' : 'bg-amber-100 text-amber-700'}`}>auth: {r.auth ?? 'N/A'}</span>
                          {r.errorCode && <span className="text-rose-600 dark:text-rose-400">{r.errorCode}</span>}
                        </div>
                        {(r.usernameUsed || r.fromEmailUsed) && (
                          <div className="mt-1 text-[10px] text-slate-400 dark:text-slate-500 space-y-0.5 font-sans">
                            {r.usernameUsed  && <div>username: <span className="text-slate-600 dark:text-slate-300">{r.usernameUsed}</span></div>}
                            {r.fromEmailUsed && <div>from:     <span className="text-slate-600 dark:text-slate-300">{r.fromEmailUsed}</span></div>}
                          </div>
                        )}
                        {r.hint && <p className="mt-1 text-slate-500 dark:text-slate-400 font-sans">{r.hint}</p>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ── SUPPORT ROUTING TAB ── */}
        {activeTab === 'routing' && (
          <div className="space-y-6">
            <div>
              <h3 className="text-sm font-bold text-[#1e293b] dark:text-white flex items-center gap-2"><Mail size={15} /> Help & Support Center — Routing Emails</h3>
              <p className="text-xs text-slate-500 mt-1">
                Every ticket submitted from Contact, Support, Technical, Billing or Security is routed to one of these inboxes.
                Leave a field empty to fall back to Support, then Fallback.
              </p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <label className="space-y-1.5">
                <span className={labelCls}>Contact Email (sales / general / demo)</span>
                <input type="email" value={routing.contactEmail} onChange={e => setRouting({ ...routing, contactEmail: e.target.value })} placeholder="contact@innovacar.app" className={fieldCls} />
              </label>
              <label className="space-y-1.5">
                <span className={labelCls}>Support Email (account / login / contract / reservation)</span>
                <input type="email" value={routing.supportEmail} onChange={e => setRouting({ ...routing, supportEmail: e.target.value })} placeholder="support@innovacar.app" className={fieldCls} />
              </label>
              <label className="space-y-1.5">
                <span className={labelCls}>Technical Email (GPS / SMTP / PDF / bugs)</span>
                <input type="email" value={routing.technicalEmail} onChange={e => setRouting({ ...routing, technicalEmail: e.target.value })} placeholder="technical@innovacar.app" className={fieldCls} />
              </label>
              <label className="space-y-1.5">
                <span className={labelCls}>Billing Email (optional — falls back to Support)</span>
                <input type="email" value={routing.billingEmail} onChange={e => setRouting({ ...routing, billingEmail: e.target.value })} placeholder="billing@innovacar.app" className={fieldCls} />
              </label>
              <label className="space-y-1.5">
                <span className={labelCls}>Security Email (optional — falls back to Support)</span>
                <input type="email" value={routing.securityEmail} onChange={e => setRouting({ ...routing, securityEmail: e.target.value })} placeholder="security@innovacar.app" className={fieldCls} />
              </label>
              <label className="space-y-1.5">
                <span className={labelCls}>Default Fallback Email</span>
                <input type="email" value={routing.fallbackEmail} onChange={e => setRouting({ ...routing, fallbackEmail: e.target.value })} placeholder="contact@innovacar.app" className={fieldCls} />
              </label>
            </div>
            <button onClick={handleSaveRouting} disabled={routingLoading} className="flex items-center gap-2 px-5 py-2.5 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors">
              {routingLoading ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Settings size={14} />} Save Routing Settings
            </button>
          </div>
        )}

        {/* ── TEMPLATES TAB ── */}
        {activeTab === 'templates' && (
          <div className="space-y-4">
            {/* Filters */}
            <div className="flex flex-wrap gap-2">
              <div className="relative flex-1 min-w-[200px]">
                <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search templates…" className={`${fieldCls} pl-8`} />
              </div>
              <FilterSelect value={typeFilter} onChange={setTypeFilter} options={['ALL', ...templateTypes]} label="Type" />
              <FilterSelect value={langFilter} onChange={setLangFilter} options={['ALL', ...LANG_OPTIONS]} label="Lang" />
              <FilterSelect value={statusFilter} onChange={setStatusFilter} options={['ALL', 'ACTIVE', 'INACTIVE']} label="Status" />
            </div>

            {/* Template cards */}
            {loading ? (
              <div className="py-12 text-center text-slate-400">Loading templates…</div>
            ) : filtered.length === 0 ? (
              <div className="py-12 text-center text-slate-400">
                {templates.length === 0 ? 'No templates found. Default templates will be seeded on first server start.' : 'No templates match your filters.'}
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
                {filtered.map(t => (
                  <TemplateCard key={t.id} template={t}
                    onEdit={() => openEdit(t)}
                    onDelete={() => handleDelete(t)}
                    onDuplicate={() => handleDuplicate(t)}
                    onReset={() => handleReset(t)}
                    onToggle={() => handleToggleActive(t)}
                    onTest={() => openTestSend(t)}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── LOGS TAB ── */}
        {activeTab === 'logs' && (
          <DataTable
            columns={[
              { key: 'type',      header: 'Type',      render: (row: any) => <span className="text-xs font-mono text-slate-500">{row.emailType || row.templateName || '—'}</span> },
              { key: 'recipient', header: 'Recipient', render: (row: any) => <span className="text-sm text-[#1e293b] dark:text-white">{row.recipient}</span> },
              { key: 'status',    header: 'Status',    render: (row: any) => <Badge variant={row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'}>{row.status}</Badge> },
              { key: 'error',     header: 'Error',     render: (row: any) => row.errorCode ? <span className="text-xs text-rose-500">{row.errorCode}</span> : <span className="text-xs text-slate-300">—</span> },
              { key: 'date',      header: 'Date',      render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '—'}</span> },
            ]}
            data={logs} loading={loading} keyExtractor={(row: any) => row.id}
            emptyTitle="No email logs found"
          />
        )}

        {/* ── ANALYTICS TAB ── */}
        {activeTab === 'analytics' && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
            {[
              { label: 'Total Sent',  value: analytics?.totalSent  || 0, icon: Mail },
              { label: 'Delivered',   value: analytics?.delivered  || 0, icon: CheckCircle },
              { label: 'Failed',      value: analytics?.failed     || 0, icon: XCircle },
              { label: 'Bounced',     value: analytics?.bounced    || 0, icon: AlertTriangle },
            ].map(stat => (
              <div key={stat.label} className="bg-slate-50 dark:bg-white/5 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-3"><stat.icon size={18} className="text-slate-400" /><span className="text-xs text-slate-500">{stat.label}</span></div>
                <p className="text-2xl font-bold text-[#1e293b] dark:text-white">{stat.value}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── EDIT / CREATE MODAL ── */}
      <TemplateEditModal
        open={showEditModal}
        onClose={() => setShowEditModal(false)}
        template={editingTemplate}
        form={form}
        setForm={setForm}
        vars={formVars}
        editTab={editTab}
        setEditTab={setEditTab}
        saving={saving}
        onSave={handleSave}
        templateTypes={templateTypes}
      />

      {/* ── TEST SEND MODAL ── */}
      <Modal isOpen={showTestModal} onClose={() => setShowTestModal(false)} title={`Test Send — ${testTarget?.name ?? ''}`} size="sm"
        footer={
          <div className="flex gap-3">
            <button onClick={handleTestSend} disabled={testSending || !testTo.trim()} className="flex-1 flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors">
              {testSending ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Send size={14} />} Send Test
            </button>
            <button onClick={() => setShowTestModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
          </div>
        }
      >
        <div className="space-y-3">
          <label className="space-y-1.5"><span className={labelCls}>Recipient Email</span><input type="email" value={testTo} onChange={e => setTestTo(e.target.value)} placeholder="test@example.com" className={fieldCls} /></label>
          <p className="text-xs text-slate-400">Example variable values will be used for the preview render.</p>
        </div>
      </Modal>
    </div>
  );
}

// ── Template card ─────────────────────────────────────────────────────────────

function TemplateCard({ template, onEdit, onDelete, onDuplicate, onReset, onToggle, onTest }: {
  template: EmailTemplate;
  onEdit: () => void; onDelete: () => void; onDuplicate: () => void;
  onReset: () => void; onToggle: () => void; onTest: () => void;
}) {
  const typeColor: Record<string, string> = {
    AUTH: 'bg-violet-50 text-violet-700 dark:bg-violet-500/10 dark:text-violet-400',
    CONTRACT: 'bg-teal-50 text-teal-700 dark:bg-teal-500/10 dark:text-teal-400',
    RESERVATION: 'bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400',
    PAYMENT: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400',
    SUPPORT: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400',
    GPS: 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-400',
    SYSTEM: 'bg-slate-100 text-slate-600 dark:bg-white/5 dark:text-slate-400',
  };
  const colorCls = typeColor[template.type] ?? typeColor.SYSTEM;

  return (
    <div className={`p-4 rounded-xl border transition-all ${template.isActive ? 'border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-white/[0.02]' : 'border-dashed border-slate-200 dark:border-white/5 bg-slate-50/50 dark:bg-white/[0.01] opacity-60'}`}>
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          <div className={`px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase tracking-wide ${colorCls}`}>{template.type}</div>
          {template.systemDefault && <div className="px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase tracking-wide bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">Default</div>}
          <div className="px-2 py-0.5 rounded-lg text-[10px] font-semibold uppercase bg-slate-100 dark:bg-white/5 text-slate-500">{template.language}</div>
        </div>
        <div className={`w-2 h-2 rounded-full ${template.isActive ? 'bg-emerald-400' : 'bg-slate-300'}`} title={template.isActive ? 'Active' : 'Inactive'} />
      </div>
      <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-0.5 leading-tight">{template.name}</h3>
      {template.templateKey && <p className="text-[10px] font-mono text-slate-400 mb-1">{template.templateKey}</p>}
      <p className="text-xs text-slate-500 truncate mb-3">{template.subject}</p>
      {template.updatedAt && <p className="text-[10px] text-slate-400 mb-3">Updated {new Date(template.updatedAt).toLocaleDateString()}</p>}
      {/* Actions */}
      <div className="flex flex-wrap gap-1 pt-2 border-t border-[#e8e6e1]/40 dark:border-white/5">
        <ActionBtn onClick={onEdit} title="Edit"><Edit2 size={12} /></ActionBtn>
        <ActionBtn onClick={onTest} title="Test Send" color="text-emerald-600 hover:bg-emerald-50"><Send size={12} /></ActionBtn>
        <ActionBtn onClick={onDuplicate} title="Duplicate"><Copy size={12} /></ActionBtn>
        {template.systemDefault && <ActionBtn onClick={onReset} title="Reset to Default" color="text-amber-600 hover:bg-amber-50"><RotateCcw size={12} /></ActionBtn>}
        <ActionBtn onClick={onToggle} title={template.isActive ? 'Deactivate' : 'Activate'} color={template.isActive ? 'text-slate-400 hover:bg-slate-100' : 'text-emerald-600 hover:bg-emerald-50'}>
          {template.isActive ? <EyeOff size={12} /> : <Eye size={12} />}
        </ActionBtn>
        <ActionBtn onClick={onDelete} title={template.systemDefault ? 'Deactivate' : 'Delete'} color="text-rose-500 hover:bg-rose-50">
          <Trash2 size={12} />
        </ActionBtn>
      </div>
    </div>
  );
}

function ActionBtn({ onClick, title, color = 'text-slate-500 hover:bg-slate-100', children }: {
  onClick: () => void; title: string; color?: string; children: React.ReactNode;
}) {
  return (
    <button onClick={onClick} title={title}
      className={`p-1.5 rounded-lg dark:hover:bg-white/5 transition-colors ${color}`}>
      {children}
    </button>
  );
}

// ── Template edit modal ───────────────────────────────────────────────────────

function TemplateEditModal({ open, onClose, template, form, setForm, vars, editTab, setEditTab, saving, onSave, templateTypes }: {
  open: boolean; onClose: () => void; template: EmailTemplate | null;
  form: Partial<EmailTemplate>; setForm: (f: Partial<EmailTemplate>) => void;
  vars: TemplateVar[]; editTab: 'edit' | 'preview' | 'variables';
  setEditTab: (t: 'edit' | 'preview' | 'variables') => void;
  saving: boolean; onSave: () => void; templateTypes: string[];
}) {
  const previewRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    if (editTab === 'preview' && previewRef.current) {
      const doc = previewRef.current.contentDocument;
      if (doc) {
        doc.open();
        doc.write(previewHtml(form.bodyHtml || form.bodyText || '<p style="padding:32px;font-family:Arial,sans-serif;color:#666;">No HTML body set yet.</p>'));
        doc.close();
      }
    }
  }, [editTab, form.bodyHtml, form.bodyText]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-4 bg-black/40 backdrop-blur-sm">
      <div className="w-full max-w-4xl my-6 bg-white dark:bg-[#1a2332] rounded-2xl shadow-2xl flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-[#e8e6e1]/60 dark:border-white/5 shrink-0">
          <h2 className="text-base font-bold text-[#1e293b] dark:text-white">{template ? 'Edit Template' : 'New Template'}</h2>
          <button onClick={onClose} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-xl"><X size={16} /></button>
        </div>

        {/* Sub-tabs */}
        <div className="flex gap-1 px-5 pt-3 shrink-0">
          {(['edit', 'preview', 'variables'] as const).map(tab => (
            <button key={tab} onClick={() => setEditTab(tab)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold capitalize transition-colors ${editTab === tab ? 'bg-[#0a0f2c] text-white' : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
              {tab === 'variables' ? <span className="flex items-center gap-1"><Code size={11} /> Variables</span> : tab === 'preview' ? <span className="flex items-center gap-1"><Eye size={11} /> Preview</span> : <span className="flex items-center gap-1"><Edit2 size={11} /> Edit</span>}
            </button>
          ))}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5">
          {editTab === 'edit' && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <label className="space-y-1.5"><span className={labelCls}>Template Name *</span><input value={form.name ?? ''} onChange={e => setForm({ ...form, name: e.target.value })} className={fieldCls} /></label>
                <label className="space-y-1.5">
                  <span className={labelCls}>Type</span>
                  <select value={form.type ?? 'AUTH'} onChange={e => setForm({ ...form, type: e.target.value })} className={fieldCls}>
                    {(templateTypes.length ? templateTypes : ['AUTH','CONTRACT','RESERVATION','PAYMENT','SUPPORT','GPS','SYSTEM']).map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5">
                  <span className={labelCls}>Language</span>
                  <select value={form.language ?? 'EN'} onChange={e => setForm({ ...form, language: e.target.value })} className={fieldCls}>
                    {LANG_OPTIONS.map(l => <option key={l} value={l}>{l}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5"><span className={labelCls}>Template Key (optional)</span><input value={form.templateKey ?? ''} onChange={e => setForm({ ...form, templateKey: e.target.value || null })} placeholder="e.g. WELCOME_AGENCY" className={fieldCls} /></label>
              </div>
              <label className="space-y-1.5 block"><span className={labelCls}>Subject *</span><input value={form.subject ?? ''} onChange={e => setForm({ ...form, subject: e.target.value })} className={fieldCls} /></label>
              <label className="space-y-1.5 block">
                <span className={labelCls}>HTML Body</span>
                <textarea value={form.bodyHtml ?? ''} onChange={e => setForm({ ...form, bodyHtml: e.target.value })}
                  rows={14} className={`${fieldCls} font-mono text-xs resize-y`}
                  placeholder="Full HTML email body. Use {{variableName}} for dynamic values." />
              </label>
              <label className="space-y-1.5 block">
                <span className={labelCls}>Plain Text Fallback</span>
                <textarea value={form.bodyText ?? ''} onChange={e => setForm({ ...form, bodyText: e.target.value })}
                  rows={5} className={`${fieldCls} resize-y`}
                  placeholder="Plain text version for email clients that do not support HTML." />
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" checked={form.isActive !== false} onChange={e => setForm({ ...form, isActive: e.target.checked })} className="w-4 h-4 accent-brand-500" />
                <span className="text-sm text-[#1e293b] dark:text-white">Active</span>
              </label>
            </div>
          )}

          {editTab === 'preview' && (
            <div className="space-y-3">
              <p className="text-xs text-slate-500">Variables replaced with example values. Rendered using saved HTML body.</p>
              <div className="rounded-xl overflow-hidden border border-[#e8e6e1] dark:border-white/10" style={{ height: 520 }}>
                <iframe ref={previewRef} title="Email preview" sandbox="allow-same-origin"
                  style={{ width: '100%', height: '100%', border: 'none', background: '#f4f7fb' }} />
              </div>
            </div>
          )}

          {editTab === 'variables' && (
            <div className="space-y-3">
              <p className="text-xs text-slate-500">Click a variable to copy it. Paste into Subject or HTML Body.</p>
              {vars.length === 0 ? (
                <p className="text-sm text-slate-400 py-6 text-center">No variable definitions for this template key.</p>
              ) : (
                <div className="space-y-2">
                  {vars.map(v => (
                    <VariableRow key={v.name} varDef={v} />
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex gap-3 p-5 border-t border-[#e8e6e1]/60 dark:border-white/5 shrink-0">
          <button onClick={onSave} disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors">
            {saving ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : null}
            {template ? 'Save Changes' : 'Create Template'}
          </button>
          <button onClick={onClose} className="px-5 py-2.5 bg-slate-100 dark:bg-white/5 text-[#1e293b] dark:text-white rounded-xl text-sm font-semibold transition-colors">Cancel</button>
        </div>
      </div>
    </div>
  );
}

function VariableRow({ varDef }: { varDef: TemplateVar }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(`{{${varDef.name}}}`).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };
  return (
    <button onClick={copy} className="w-full text-left flex items-start gap-3 p-3 rounded-xl border border-[#e8e6e1] dark:border-white/10 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group">
      <code className="text-xs text-brand-600 dark:text-brand-400 bg-brand-50 dark:bg-brand-500/10 px-2 py-1 rounded-lg font-mono shrink-0">{`{{${varDef.name}}}`}</code>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold text-[#1e293b] dark:text-white">{varDef.description}</p>
        <p className="text-xs text-slate-400 truncate">e.g. {varDef.example}</p>
      </div>
      <span className={`text-xs shrink-0 ${copied ? 'text-emerald-600' : 'text-slate-300 group-hover:text-slate-400'}`}>{copied ? 'Copied!' : 'Copy'}</span>
    </button>
  );
}

function FilterSelect({ value, onChange, options, label }: {
  value: string; onChange: (v: string) => void; options: string[]; label: string;
}) {
  return (
    <div className="relative">
      <select value={value} onChange={e => onChange(e.target.value)}
        className="appearance-none h-[42px] pl-3 pr-8 bg-slate-50 dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm text-[#1e293b] dark:text-white outline-none focus:border-brand-400 transition-colors cursor-pointer">
        {options.map(o => <option key={o} value={o}>{o === 'ALL' ? `All ${label}s` : o}</option>)}
      </select>
      <ChevronDown size={13} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
    </div>
  );
}

// ── Shared style tokens ───────────────────────────────────────────────────────
const labelCls = 'block text-xs font-semibold text-slate-500 uppercase tracking-wide';
