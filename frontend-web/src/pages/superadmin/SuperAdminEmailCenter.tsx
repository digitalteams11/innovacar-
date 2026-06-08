import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Mail, Send, Plus, Trash2, Edit2, CheckCircle, XCircle, AlertTriangle } from 'lucide-react';
import { PageHeader, TabGroup, DataTable, Modal, FormField, TextInput, TextArea, Badge, ToggleSwitch } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminEmailCenter() {
  useTranslation();
  const { showToast } = useToast();
  const [templates, setTemplates] = useState<any[]>([]);
  const [logs, setLogs] = useState<any[]>([]);
  const [analytics, setAnalytics] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('templates');
  const [showTemplateModal, setShowTemplateModal] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<any>(null);
  const [templateForm, setTemplateForm] = useState<any>({});
  const [showTestModal, setShowTestModal] = useState(false);
  const [testEmail, setTestEmail] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [templatesRes, logsRes, analyticsRes] = await Promise.all([
        superAdminApi.getEmailTemplates().catch(() => ({ data: [] })),
        superAdminApi.getEmailLogs().catch(() => ({ data: [] })),
        superAdminApi.getEmailAnalytics().catch(() => ({ data: null })),
      ]);
      setTemplates(templatesRes.data);
      setLogs(logsRes.data);
      setAnalytics(analyticsRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveTemplate = async () => {
    try {
      if (editingTemplate) {
        await superAdminApi.updateEmailTemplate(editingTemplate.id, templateForm);
      } else {
        await superAdminApi.createEmailTemplate(templateForm);
      }
      setShowTemplateModal(false);
      fetchData();
      showToast('Template saved successfully');
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeleteTemplate = async (id: number) => {
    if (!window.confirm('Delete this template?')) return;
    try {
      await superAdminApi.deleteEmailTemplate(id);
      fetchData();
      showToast('Template deleted successfully');
    } catch (err) {
      console.error(err);
    }
  };

  const handleSendTest = async () => {
    try {
      await superAdminApi.sendTestEmail({ to: testEmail, templateId: editingTemplate?.id });
      setShowTestModal(false);
      showToast('Test email sent');
    } catch (err) {
      console.error(err);
    }
  };

  const tabs = [
    { id: 'templates', label: 'Templates' },
    { id: 'logs', label: 'Email Logs' },
    { id: 'analytics', label: 'Analytics' },
  ];

  const templateTypes = [
    { value: 'WELCOME', label: 'Welcome Email' },
    { value: 'INVOICE', label: 'Invoice' },
    { value: 'PASSWORD_RESET', label: 'Password Reset' },
    { value: 'SUBSCRIPTION_REMINDER', label: 'Subscription Reminder' },
    { value: 'MAINTENANCE', label: 'Maintenance' },
    { value: 'ANNOUNCEMENT', label: 'Announcement' },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Email & Communication Center" subtitle="Manage email templates, delivery, and analytics">
        <button onClick={() => { setEditingTemplate(null); setTemplateForm({ name: '', subject: '', bodyHtml: '', bodyText: '', type: 'WELCOME', isActive: true }); setShowTemplateModal(true); }} className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft">
          <Plus size={16} />
          <span className="hidden sm:inline">New Template</span>
        </button>
      </PageHeader>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'templates' && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
            {templates.map((template) => (
              <div key={template.id} className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl border border-[#e8e6e1]/40 dark:border-white/5 hover:border-brand-300 dark:hover:border-brand-500/30 transition-all">
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 rounded-lg bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
                    <Mail size={18} className="text-[#0a0f2c] dark:text-white/70" />
                  </div>
                  <div className="flex gap-1">
                    <button onClick={() => { setEditingTemplate(template); setTemplateForm({ ...template }); setShowTemplateModal(true); }} className="p-1.5 hover:bg-white dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors">
                      <Edit2 size={14} />
                    </button>
                    <button onClick={() => { setEditingTemplate(template); setShowTestModal(true); }} className="p-1.5 hover:bg-white dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-emerald-600 transition-colors">
                      <Send size={14} />
                    </button>
                    <button onClick={() => handleDeleteTemplate(template.id)} className="p-1.5 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg text-slate-400 hover:text-rose-600 transition-colors">
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
                <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-1">{template.name}</h3>
                <p className="text-xs text-slate-500 mb-2 truncate">{template.subject}</p>
                <Badge variant={template.isActive ? 'success' : 'default'}>{template.isActive ? 'Active' : 'Inactive'}</Badge>
                <span className="ml-2 text-xs text-slate-400">{template.type}</span>
              </div>
            ))}
            {templates.length === 0 && (
              <div className="col-span-full text-center py-12 text-slate-400">No email templates found.</div>
            )}
          </div>
        )}

        {activeTab === 'logs' && (
          <DataTable
            columns={[
              { key: 'template', header: 'Template', render: (row: any) => <span className="text-sm font-medium text-[#1e293b] dark:text-white">{row.templateName || '-'}</span> },
              { key: 'recipient', header: 'Recipient', render: (row: any) => <span className="text-sm text-slate-500">{row.recipient}</span> },
              { key: 'status', header: 'Status', render: (row: any) => <Badge variant={row.status === 'DELIVERED' ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'BOUNCED' ? 'warning' : 'info'}>{row.status}</Badge> },
              { key: 'sent', header: 'Sent', render: (row: any) => <span className="text-xs text-slate-500">{row.sentAt ? new Date(row.sentAt).toLocaleString() : '-'}</span> },
            ]}
            data={logs}
            loading={loading}
            keyExtractor={(row) => row.id}
            emptyTitle="No email logs found"
          />
        )}

        {activeTab === 'analytics' && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
            {[
              { label: 'Total Sent', value: analytics?.totalSent || 0, icon: Mail },
              { label: 'Delivered', value: analytics?.delivered || 0, icon: CheckCircle },
              { label: 'Failed', value: analytics?.failed || 0, icon: XCircle },
              { label: 'Bounced', value: analytics?.bounced || 0, icon: AlertTriangle },
            ].map((stat) => (
              <div key={stat.label} className="bg-slate-50 dark:bg-white/5 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-3">
                  <stat.icon size={18} className="text-slate-400" />
                  <span className="text-xs text-slate-500">{stat.label}</span>
                </div>
                <p className="text-2xl font-bold text-[#1e293b] dark:text-white">{stat.value}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Template Modal */}
      <Modal isOpen={showTemplateModal} onClose={() => setShowTemplateModal(false)} title={editingTemplate ? 'Edit Template' : 'New Template'} size="lg"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSaveTemplate} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Save</button>
            <button onClick={() => setShowTemplateModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Template Name" required><TextInput value={templateForm.name || ''} onChange={(v) => setTemplateForm({ ...templateForm, name: v })} /></FormField>
          <FormField label="Type" required>
            <select value={templateForm.type || 'WELCOME'} onChange={(e) => setTemplateForm({ ...templateForm, type: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none">
              {templateTypes.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </FormField>
          <FormField label="Subject" required><TextInput value={templateForm.subject || ''} onChange={(v) => setTemplateForm({ ...templateForm, subject: v })} /></FormField>
          <FormField label="HTML Body"><TextArea value={templateForm.bodyHtml || ''} onChange={(v) => setTemplateForm({ ...templateForm, bodyHtml: v })} rows={6} /></FormField>
          <FormField label="Plain Text Body"><TextArea value={templateForm.bodyText || ''} onChange={(v) => setTemplateForm({ ...templateForm, bodyText: v })} rows={4} /></FormField>
          <ToggleSwitch checked={templateForm.isActive !== false} onChange={(v: boolean) => setTemplateForm({ ...templateForm, isActive: v })} label="Active" />
        </div>
      </Modal>

      {/* Test Email Modal */}
      <Modal isOpen={showTestModal} onClose={() => setShowTestModal(false)} title="Send Test Email" size="sm"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSendTest} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Send Test</button>
            <button onClick={() => setShowTestModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
          </div>
        }
      >
        <FormField label="Recipient Email" required><TextInput value={testEmail} onChange={setTestEmail} placeholder="test@example.com" type="email" /></FormField>
      </Modal>
    </div>
  );
}
