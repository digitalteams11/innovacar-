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
  lastTestStatus: string | null; lastTestAt: string | null; lastTestErrorCode: string | null;
  activeEmailProvider?: string;
  emailProviderConfigured?: boolean;
}

interface EmailTemplate {
  id: number; name: string; templateKey: string | null; type: string; language: string;
  subject: string; bodyHtml: string | null; bodyText: string | null;
  isActive: boolean; systemDefault: boolean; updatedAt: string | null;
}

interface TemplateVar { name: string; description: string; example: string; }

const defaultSmtp: SmtpSettings = {
  lastTestStatus: null, lastTestAt: null, lastTestErrorCode: null,
  activeEmailProvider: 'ZEPTOMAIL', emailProviderConfigured: false,
};

// Distinguishes *why* an Email Center request failed instead of collapsing
// every failure into a generic "API server unavailable" — a slow/blocked SMTP
// probe, an expired session, and a missing permission all need different
// user-facing text and different next steps.
type EmailCenterErrorCode =
  | 'API_SERVER_UNREACHABLE'
  | 'SESSION_EXPIRED'
  | 'SUPER_ADMIN_PERMISSION_REQUIRED'
  | 'EMAIL_ENDPOINT_NOT_FOUND'
  | 'INVALID_SMTP_CONFIGURATION'
  | 'EMAIL_DIAGNOSTIC_INTERNAL_ERROR'
  | 'EMAIL_PROVIDER_UNAVAILABLE'
  | 'SMTP_CONNECTION_TIMEOUT';

function classifyEmailCenterError(err: any): { code: EmailCenterErrorCode; message: string } {
  const status = err?.response?.status;
  const data = err?.response?.data;
  const backendMessage = typeof data?.message === 'string' ? data.message : undefined;

  // No HTTP response reached us at all — real connectivity/CORS/timeout
  // failure, not a backend-produced error. This is the only case that should
  // ever show "API server unavailable".
  if (!err?.response) {
    return {
      code: 'API_SERVER_UNREACHABLE',
      message: backendMessage || 'Could not reach the API server. Please check your connection and try again.',
    };
  }
  if (data?.errorCode === 'SMTP_CONNECTION_TIMEOUT') {
    return { code: 'SMTP_CONNECTION_TIMEOUT', message: backendMessage || 'The SMTP connection timed out.' };
  }
  switch (status) {
    case 401:
      return { code: 'SESSION_EXPIRED', message: backendMessage || 'Your session has expired. Please sign in again.' };
    case 403:
      return { code: 'SUPER_ADMIN_PERMISSION_REQUIRED', message: backendMessage || 'Super Admin permission is required for this action.' };
    case 404:
      return { code: 'EMAIL_ENDPOINT_NOT_FOUND', message: backendMessage || 'This email endpoint could not be found. The app may be misconfigured.' };
    case 400:
      return { code: 'INVALID_SMTP_CONFIGURATION', message: backendMessage || 'The SMTP configuration is invalid.' };
    case 502:
    case 503:
      return { code: 'EMAIL_PROVIDER_UNAVAILABLE', message: backendMessage || 'The email provider is currently unavailable.' };
    default:
      return { code: 'EMAIL_DIAGNOSTIC_INTERNAL_ERROR', message: backendMessage || 'An internal error occurred while processing the email request.' };
  }
}

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

  // Email (ZeptoMail — configured via Railway env vars, not editable here)
  const [smtp, setSmtp] = useState<SmtpSettings>(defaultSmtp);
  const [testEmailAddr, setTestEmailAddr] = useState('');
  const [testLoading, setTestLoading] = useState(false);
  const [testErrorCode, setTestErrorCode] = useState<string | null>(null);

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
        superAdminApi.getSmtpSettings().catch((err: any) => {
          const { message } = classifyEmailCenterError(err);
          showToast(message, 'error');
          return { data: {} };
        }),
        superAdminApi.getEmailTemplates().catch(() => ({ data: [] })),
        superAdminApi.getEmailLogs().catch(() => ({ data: [] })),
        superAdminApi.getEmailAnalytics().catch(() => ({ data: null })),
        superAdminApi.getEmailTemplateTypes().catch(() => ({ data: [] })),
        superAdminApi.getSupportRoutingSettings().catch(() => ({ data: {} })),
      ]);
      const d = smtpRes.data ?? {};
      setSmtp({
        lastTestStatus: d.lastTestStatus ?? null, lastTestAt: d.lastTestAt ?? null,
        lastTestErrorCode: d.lastTestErrorCode ?? null,
        activeEmailProvider: d.activeEmailProvider ?? 'ZEPTOMAIL',
        emailProviderConfigured: Boolean(d.emailProviderConfigured),
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

  // ── Email test handler (ZeptoMail — config is Railway env vars, nothing to save here) ──
  const handleTestSmtp = async () => {
    if (!testEmailAddr.trim()) { showToast('Enter a recipient email', 'error'); return; }
    setTestLoading(true); setTestErrorCode(null);
    try {
      const res = await superAdminApi.sendSmtpTestEmail(testEmailAddr.trim());
      if (res.data?.success) { showToast('Test email sent successfully!', 'success'); setTestErrorCode(null); }
      else { setTestErrorCode(res.data?.errorCode ?? null); showToast(res.data?.message || 'Test failed', 'error'); }
      await fetchAll();
    } catch (err: any) {
      const { code, message } = classifyEmailCenterError(err);
      setTestErrorCode(err?.response?.data?.errorCode ?? code);
      showToast(message, 'error');
    } finally { setTestLoading(false); }
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
            {/* Delivery mechanism — email always sends via ZeptoMail's HTTPS API now;
                SMTP is never attempted (Railway blocks outbound SMTP ports 465/587 at
                the network level). Configuration lives entirely in Railway env vars,
                not in this database-backed form, so there is nothing to edit here. */}
            <div className={`flex items-center gap-2 rounded-xl border px-4 py-3 ${smtp.emailProviderConfigured ? 'border-blue-200 bg-blue-50 dark:border-blue-500/30 dark:bg-blue-500/10' : 'border-amber-200 bg-amber-50 dark:border-amber-500/30 dark:bg-amber-500/10'}`}>
              <Wifi size={16} className={`shrink-0 ${smtp.emailProviderConfigured ? 'text-blue-600 dark:text-blue-400' : 'text-amber-600 dark:text-amber-400'}`} />
              <p className={`text-sm ${smtp.emailProviderConfigured ? 'text-blue-700 dark:text-blue-300' : 'text-amber-800 dark:text-amber-300'}`}>
                {smtp.emailProviderConfigured
                  ? <><strong>ZeptoMail API active</strong> — all email is sent over HTTPS, not SMTP.</>
                  : <><strong>ZeptoMail is not configured.</strong> Set <code className="font-mono bg-black/5 dark:bg-white/10 px-1 rounded">ZEPTOMAIL_API_TOKEN</code>, <code className="font-mono bg-black/5 dark:bg-white/10 px-1 rounded">EMAIL_FROM_EMAIL</code>, and optionally <code className="font-mono bg-black/5 dark:bg-white/10 px-1 rounded">EMAIL_FROM_NAME</code> as environment variables on Railway.</>}
              </p>
            </div>

            {/* Status cards */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {[
                { label: 'Provider', value: smtp.activeEmailProvider || 'ZEPTOMAIL', icon: smtp.emailProviderConfigured ? Wifi : WifiOff, color: smtp.emailProviderConfigured ? 'text-emerald-600' : 'text-slate-400' },
                { label: 'Configured', value: smtp.emailProviderConfigured ? 'Yes' : 'No', icon: smtp.emailProviderConfigured ? CheckCircle : XCircle, color: smtp.emailProviderConfigured ? 'text-emerald-600' : 'text-rose-500' },
                { label: 'Last Test', value: smtp.lastTestStatus ? (lastTestOk ? 'Passed' : 'Failed') : 'Not tested', icon: smtp.lastTestStatus ? (lastTestOk ? CheckCircle : XCircle) : Clock, color: smtp.lastTestStatus ? (lastTestOk ? 'text-emerald-600' : 'text-rose-500') : 'text-slate-400' },
              ].map(item => (
                <div key={item.label} className="bg-slate-50 dark:bg-white/5 rounded-xl p-4">
                  <div className="flex items-center gap-2 mb-1"><item.icon size={14} className={item.color} /><span className="text-xs text-slate-500">{item.label}</span></div>
                  <p className={`text-sm font-bold truncate ${item.color}`}>{item.value}</p>
                </div>
              ))}
            </div>

            {/* Test */}
            <div className="border-t border-[#e8e6e1]/60 pt-5 space-y-3">
              <h3 className="text-sm font-bold text-[#1e293b] dark:text-white flex items-center gap-2"><Send size={15} /> Send Test Email</h3>
              <p className="text-xs text-slate-500">Sends a real test email through ZeptoMail to verify delivery end-to-end.</p>
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
                const isAuthFail  = testErrorCode === 'EMAIL_API_AUTH_FAILED';
                const isRecipient = testErrorCode === 'TEST_RECIPIENT_INVALID' || testErrorCode === 'TEST_RECIPIENT_MISSING';
                const knownCodes  = [
                  'EMAIL_API_AUTH_FAILED', 'EMAIL_API_RATE_LIMITED', 'EMAIL_API_PROVIDER_ERROR',
                  'EMAIL_API_REQUEST_REJECTED', 'EMAIL_API_TIMEOUT', 'EMAIL_CONFIGURATION_MISSING',
                  'TEST_RECIPIENT_INVALID', 'TEST_RECIPIENT_MISSING',
                ];
                const colorClass = isAuthFail
                  ? 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30 text-amber-800 dark:text-amber-300'
                  : 'bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/30 text-rose-800 dark:text-rose-300';
                return (
                  <div className={`flex gap-2 p-3 rounded-xl text-xs border ${colorClass}`}>
                    <AlertTriangle size={14} className="shrink-0 mt-0.5" />
                    <span>
                      {isAuthFail && <><strong>ZeptoMail rejected the API token.</strong> Verify ZEPTOMAIL_API_TOKEN in Railway is correct and active.</>}
                      {isRecipient && <><strong>Invalid test recipient.</strong> Enter a valid email address in the "Send test to" field (e.g. you@example.com).</>}
                      {testErrorCode === 'EMAIL_API_RATE_LIMITED' && <><strong>Rate-limited.</strong> ZeptoMail is throttling requests — wait a moment and try again.</>}
                      {testErrorCode === 'EMAIL_API_PROVIDER_ERROR' && <><strong>ZeptoMail server error.</strong> Try again shortly; check ZeptoMail's status page if it persists.</>}
                      {testErrorCode === 'EMAIL_API_REQUEST_REJECTED' && <><strong>Request rejected.</strong> Verify the sending domain (EMAIL_FROM_EMAIL) is verified in your ZeptoMail account.</>}
                      {testErrorCode === 'EMAIL_API_TIMEOUT' && <><strong>Request timed out.</strong> The call to ZeptoMail did not complete in time. Try again.</>}
                      {testErrorCode === 'EMAIL_CONFIGURATION_MISSING' && <><strong>Not configured.</strong> Set ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL as environment variables on Railway.</>}
                      {!knownCodes.includes(testErrorCode) && <><strong>{testErrorCode}:</strong> Verify ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL are set correctly in Railway.</>}
                    </span>
                  </div>
                );
              })()}
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
