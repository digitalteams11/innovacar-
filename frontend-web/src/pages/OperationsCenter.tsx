import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle, BookOpen, CheckCircle2, Headphones,
  Laptop, LockKeyhole, MessageCircle, Paperclip, Plus, RefreshCw, Scale, Send, ShieldCheck,
  ShieldOff, Smartphone, Trash2, X,
} from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { FilterChips } from '../components/FilterChips';
import ApiErrorState from '../components/ApiErrorState';
import PremiumLoader from '../components/PremiumLoader';

type Ticket = {
  id: number;
  ticketNumber: string;
  subject: string;
  description: string;
  status: string;
  priority: string;
  category: string;
  createdAt: string;
  resolution?: string;
};

type Article = {
  id: number;
  title: string;
  category: string;
  summary: string;
  content: string;
};

type LegalDocument = {
  id: number;
  title: string;
  version: string;
  content: string;
  accepted: boolean;
  publishedAt: string;
};

type Session = {
  id: number;
  ipAddress?: string;
  userAgent?: string;
  location?: string;
  createdAt: string;
  expiresAt: string;
};

type ActivityItem = {
  id: number;
  action: string;
  description?: string;
  performedBy?: string;
  ipAddress?: string;
  successful: boolean;
  createdAt: string;
};

type TrustedDevice = {
  id: number;
  name: string;
  browser?: string;
  operatingSystem?: string;
  ipAddress?: string;
  trusted: boolean;
  blocked: boolean;
  lastSeenAt: string;
};

type LoginHistoryItem = {
  id: number;
  ipAddress?: string;
  success: boolean;
  suspicious: boolean;
  failureReason?: string;
  createdAt: string;
};

type HealthService = { status: 'OPERATIONAL' | 'NOT_CONFIGURED' | 'DEGRADED'; message: string };

type SupportMessage = {
  id: number;
  senderName: string;
  senderType: 'AGENCY' | 'SUPPORT';
  message: string;
  attachmentName: string;
  attachmentType: string;
  attachmentData: string;
  read: boolean;
  createdAt: string;
};

type OperationsData = {
  tickets: Ticket[];
  complaints: Ticket[];
  knowledge: Article[];
  legalDocuments: LegalDocument[];
  security: {
    sessions: Session[];
    activity: ActivityItem[];
    failedLoginAttempts: number;
    lastLoginAt?: string;
    passwordExpiresAt?: string;
    devices: TrustedDevice[];
    loginHistory: LoginHistoryItem[];
  };
  health: Record<string, HealthService>;
};

const emptyOperationsData: OperationsData = {
  tickets: [],
  complaints: [],
  knowledge: [],
  legalDocuments: [],
  security: {
    sessions: [],
    activity: [],
    failedLoginAttempts: 0,
    devices: [],
    loginHistory: [],
  },
  health: {},
};

function unwrapOperations(payload: unknown): OperationsData {
  const source = payload && typeof payload === 'object' && 'data' in payload
    ? (payload as { data?: unknown }).data
    : payload;
  const value = source && typeof source === 'object' ? source as Partial<OperationsData> : {};
  const security = value.security || emptyOperationsData.security;
  return {
    tickets: Array.isArray(value.tickets) ? value.tickets : [],
    complaints: Array.isArray(value.complaints) ? value.complaints : [],
    knowledge: Array.isArray(value.knowledge) ? value.knowledge : [],
    legalDocuments: Array.isArray(value.legalDocuments) ? value.legalDocuments : [],
    security: {
      sessions: Array.isArray(security.sessions) ? security.sessions : [],
      activity: Array.isArray(security.activity) ? security.activity : [],
      failedLoginAttempts: Number(security.failedLoginAttempts || 0),
      lastLoginAt: security.lastLoginAt,
      passwordExpiresAt: security.passwordExpiresAt,
      devices: Array.isArray(security.devices) ? security.devices : [],
      loginHistory: Array.isArray(security.loginHistory) ? security.loginHistory : [],
    },
    health: value.health && typeof value.health === 'object' ? value.health : {},
  };
}

const tabs = [
  { id: 'support', label: 'Support' },
  { id: 'complaints', label: 'Complaints' },
  { id: 'knowledge', label: 'Knowledge' },
  { id: 'legal', label: 'Legal' },
  { id: 'security', label: 'Security' },
  { id: 'health', label: 'System health' },
];

const priorityClass: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-600 dark:bg-white/5 dark:text-slate-300',
  MEDIUM: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  HIGH: 'bg-orange-50 text-orange-700 dark:bg-orange-500/10 dark:text-orange-300',
  CRITICAL: 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300',
};

function formatDate(value?: string) {
  if (!value || value.startsWith('-999')) return 'Not available';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function OperationsCenter() {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('support');
  const [data, setData] = useState<OperationsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [expandedArticle, setExpandedArticle] = useState<number | null>(null);
  const [expandedLegal, setExpandedLegal] = useState<number | null>(null);
  const [form, setForm] = useState({ subject: '', description: '', category: 'TECHNICAL', priority: 'MEDIUM' });
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [messageText, setMessageText] = useState('');
  const [attachment, setAttachment] = useState<{ name: string; type: string; data: string } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await api.get<unknown>('/operations-center');
      setData(unwrapOperations(response.data));
    } catch (requestError) {
      console.error(requestError);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const submitTicket = async () => {
    if (!form.subject.trim() || !form.description.trim()) {
      showToast('Subject and description are required.', 'warning');
      return;
    }
    setSubmitting(true);
    try {
      await api.post('/operations-center/tickets', {
        ...form,
        kind: activeTab === 'complaints' ? 'COMPLAINT' : 'SUPPORT',
      });
      showToast(activeTab === 'complaints' ? 'Complaint submitted successfully' : 'Support request submitted successfully', 'success');
      setCreating(false);
      setForm({ subject: '', description: '', category: 'TECHNICAL', priority: 'MEDIUM' });
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to submit your request. Please try again later.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const acceptDocument = async (documentId: number) => {
    try {
      await api.post(`/operations-center/legal/${documentId}/accept`);
      showToast('Acceptance recorded successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to record acceptance. Please try again later.', 'error');
    }
  };

  const revokeSession = async (sessionId: number) => {
    try {
      await api.delete(`/operations-center/security/sessions/${sessionId}`);
      showToast('Session revoked successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to revoke this session.', 'error');
    }
  };

  const updateDevice = async (device: TrustedDevice, action: 'trust' | 'block') => {
    try {
      await api.patch(`/security-center/devices/${device.id}/${action}`, {
        [action === 'trust' ? 'trusted' : 'blocked']:
          action === 'trust' ? !device.trusted : !device.blocked,
      });
      showToast(action === 'trust'
        ? (device.trusted ? 'Device trust removed' : 'Device trusted successfully')
        : (device.blocked ? 'Device unblocked' : 'Device blocked'), 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to update this device.', 'error');
    }
  };

  const removeDevice = async (deviceId: number) => {
    try {
      await api.delete(`/security-center/devices/${deviceId}`);
      showToast('Device removed successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to remove this device.', 'error');
    }
  };

  const loadMessages = useCallback(async (ticketId: number) => {
    try {
      const response = await api.get<unknown>(`/operations-center/tickets/${ticketId}/messages`);
      const payload = response.data && typeof response.data === 'object' && 'data' in response.data
        ? (response.data as { data?: unknown }).data
        : response.data;
      setMessages(Array.isArray(payload) ? payload : []);
    } catch (requestError) {
      console.error(requestError);
    }
  }, []);

  useEffect(() => {
    if (!selectedTicket) return;
    const initial = window.setTimeout(() => loadMessages(selectedTicket.id), 0);
    const interval = window.setInterval(() => loadMessages(selectedTicket.id), 5000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(interval);
    };
  }, [selectedTicket, loadMessages]);

  const chooseAttachment = (file?: File) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      showToast('Attachments must not exceed 5 MB.', 'warning');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setAttachment({ name: file.name, type: file.type, data: String(reader.result) });
    reader.readAsDataURL(file);
  };

  const sendMessage = async () => {
    if (!selectedTicket || (!messageText.trim() && !attachment)) return;
    setSubmitting(true);
    try {
      await api.post(`/operations-center/tickets/${selectedTicket.id}/messages`, {
        message: messageText,
        attachmentName: attachment?.name,
        attachmentType: attachment?.type,
        attachmentData: attachment?.data,
      });
      setMessageText('');
      setAttachment(null);
      await loadMessages(selectedTicket.id);
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to send this message. Please try again.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const tickets = activeTab === 'complaints' ? data?.complaints ?? [] : data?.tickets ?? [];
  const headerAction = useMemo(() => (
    ['support', 'complaints'].includes(activeTab) ? (
      <button
        onClick={() => setCreating(true)}
        className="inline-flex items-center gap-2 rounded-lg bg-[var(--brand-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--brand-primary-foreground,#171817)]"
      >
        <Plus size={16} />
        {activeTab === 'complaints' ? 'New complaint' : 'New request'}
      </button>
    ) : (
      <button onClick={load} className="inline-flex items-center gap-2 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-sm text-[var(--text-secondary)]">
        <RefreshCw size={15} /> Refresh
      </button>
    )
  ), [activeTab, load]);

  if (loading && !data) return <PremiumLoader />;
  if (error || !data) return <ApiErrorState message="Unable to load the operations center. Please try again later." onRetry={load} />;

  return (
    <div className="space-y-5">
      <GlassPageHeader
        title="Operations Center"
        subtitle="Support, guidance, legal records, account security and service health in one workspace."
        icon={Headphones}
        actions={headerAction}
      />

      <FilterChips options={tabs} activeId={activeTab} onChange={setActiveTab} />

      {['support', 'complaints'].includes(activeTab) && (
        <div className="data-surface overflow-hidden">
          {tickets.length === 0 ? (
            <div className="flex min-h-64 flex-col items-center justify-center p-8 text-center">
              {activeTab === 'complaints' ? <AlertTriangle size={28} className="text-[var(--brand-primary)]" /> : <Headphones size={28} className="text-[var(--brand-primary)]" />}
              <h2 className="mt-4 font-semibold text-[var(--text-primary)]">
                {activeTab === 'complaints' ? 'No complaints submitted' : 'No support requests'}
              </h2>
              <p className="mt-2 max-w-md text-sm text-[var(--text-muted)]">
                {activeTab === 'complaints' ? 'Your complaint tracking history will appear here.' : 'Create a request when your team needs help from Innovax Technologies.'}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-[var(--border-subtle)]">
              {tickets.map((ticket) => (
                <article key={ticket.id} className="grid gap-4 p-5 md:grid-cols-[1fr_auto]">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-xs text-[var(--brand-primary)]">{ticket.ticketNumber}</span>
                      <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${priorityClass[ticket.priority] ?? priorityClass.MEDIUM}`}>{ticket.priority}</span>
                      <span className="rounded-md bg-emerald-50 px-2 py-1 text-[10px] font-bold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">{ticket.status}</span>
                    </div>
                    <h2 className="mt-3 font-semibold text-[var(--text-primary)]">{ticket.subject}</h2>
                    <p className="mt-1 text-sm text-[var(--text-muted)]">{ticket.description}</p>
                    {ticket.resolution && <p className="mt-3 border-l-2 border-emerald-500 pl-3 text-sm text-[var(--text-secondary)]">{ticket.resolution}</p>}
                  </div>
                  <div className="flex items-start gap-3">
                    <time className="text-xs text-[var(--text-muted)]">{formatDate(ticket.createdAt)}</time>
                    <button onClick={() => setSelectedTicket(ticket)} className="inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)]">
                      <MessageCircle size={14} /> Conversation
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'knowledge' && (
        <div className="grid gap-4 lg:grid-cols-2">
          {data.knowledge.map((article) => (
            <article key={article.id} className="data-surface p-5">
              <div className="flex items-center gap-2 text-xs font-semibold text-[var(--brand-primary)]"><BookOpen size={15} /> {article.category}</div>
              <h2 className="mt-3 text-lg font-semibold text-[var(--text-primary)]">{article.title}</h2>
              <p className="mt-2 text-sm text-[var(--text-muted)]">{article.summary}</p>
              {expandedArticle === article.id && <p className="mt-4 border-t border-[var(--border-subtle)] pt-4 text-sm leading-6 text-[var(--text-secondary)]">{article.content}</p>}
              <button onClick={() => setExpandedArticle(expandedArticle === article.id ? null : article.id)} className="mt-4 text-sm font-semibold text-[var(--brand-primary)]">
                {expandedArticle === article.id ? 'Close guide' : 'Read guide'}
              </button>
            </article>
          ))}
        </div>
      )}

      {activeTab === 'legal' && (
        <div className="space-y-3">
          {data.legalDocuments.map((document) => (
            <article key={document.id} className="data-surface p-5">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-3">
                  <Scale size={19} className="mt-0.5 text-[var(--brand-primary)]" />
                  <div>
                    <h2 className="font-semibold text-[var(--text-primary)]">{document.title}</h2>
                    <p className="mt-1 text-xs text-[var(--text-muted)]">Version {document.version} · Published {formatDate(document.publishedAt)}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {document.accepted ? (
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300"><CheckCircle2 size={14} /> Accepted</span>
                  ) : (
                    <button onClick={() => acceptDocument(document.id)} className="rounded-lg bg-[var(--brand-primary)] px-3 py-2 text-xs font-semibold text-[var(--brand-primary-foreground,#171817)]">Accept document</button>
                  )}
                  <button onClick={() => setExpandedLegal(expandedLegal === document.id ? null : document.id)} className="rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)]">Review</button>
                </div>
              </div>
              {expandedLegal === document.id && <p className="mt-5 border-t border-[var(--border-subtle)] pt-5 text-sm leading-6 text-[var(--text-secondary)]">{document.content}</p>}
            </article>
          ))}
        </div>
      )}

      {activeTab === 'security' && (
        <div className="grid gap-5 xl:grid-cols-2">
          <section className="data-surface p-5 xl:col-span-2">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <Smartphone size={18} className="text-[var(--brand-primary)]" />
                <div>
                  <h2 className="font-semibold text-[var(--text-primary)]">Known devices</h2>
                  <p className="text-xs text-[var(--text-muted)]">Trust devices you recognize and block access you do not.</p>
                </div>
              </div>
              {data.security.passwordExpiresAt && (
                <span className="rounded-md bg-[var(--bg-hover)] px-3 py-2 text-xs text-[var(--text-secondary)]">
                  Password expires {formatDate(data.security.passwordExpiresAt)}
                </span>
              )}
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              {data.security.devices.length === 0 && <p className="py-8 text-center text-sm text-[var(--text-muted)] md:col-span-2">No known devices recorded yet.</p>}
              {data.security.devices.map((device) => (
                <article key={device.id} className="rounded-lg border border-[var(--border-subtle)] p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-semibold text-[var(--text-primary)]">{device.name || 'Browser device'}</p>
                      <p className="mt-1 text-xs text-[var(--text-muted)]">{device.browser} · {device.operatingSystem} · {device.ipAddress}</p>
                      <p className="mt-1 text-xs text-[var(--text-muted)]">Last seen {formatDate(device.lastSeenAt)}</p>
                    </div>
                    <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${device.blocked ? 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300' : device.trusted ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' : 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300'}`}>
                      {device.blocked ? 'BLOCKED' : device.trusted ? 'TRUSTED' : 'REVIEW'}
                    </span>
                  </div>
                  <div className="mt-4 flex gap-2">
                    <button onClick={() => updateDevice(device, 'trust')} disabled={device.blocked} className="inline-flex items-center gap-1.5 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)] disabled:opacity-40">
                      <ShieldCheck size={14} /> {device.trusted ? 'Untrust' : 'Trust'}
                    </button>
                    <button onClick={() => updateDevice(device, 'block')} className="inline-flex items-center gap-1.5 rounded-lg border border-red-200 px-3 py-2 text-xs font-semibold text-red-600 dark:border-red-500/20">
                      <ShieldOff size={14} /> {device.blocked ? 'Unblock' : 'Block'}
                    </button>
                    <button title="Remove device" onClick={() => removeDevice(device.id)} className="ml-auto rounded-lg p-2 text-[var(--text-muted)] hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10">
                      <Trash2 size={15} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>
          <section className="data-surface p-5">
            <div className="flex items-center gap-2"><Laptop size={18} className="text-[var(--brand-primary)]" /><h2 className="font-semibold text-[var(--text-primary)]">Active sessions</h2></div>
            <div className="mt-4 divide-y divide-[var(--border-subtle)]">
              {data.security.sessions.length === 0 && <p className="py-8 text-center text-sm text-[var(--text-muted)]">No active sessions found.</p>}
              {data.security.sessions.map((session) => (
                <div key={session.id} className="flex items-start justify-between gap-4 py-4">
                  <div>
                    <p className="text-sm font-medium text-[var(--text-primary)]">{session.location || session.ipAddress || 'Current device'}</p>
                    <p className="mt-1 max-w-sm truncate text-xs text-[var(--text-muted)]">{session.userAgent || 'Device information unavailable'}</p>
                    <p className="mt-1 text-xs text-[var(--text-muted)]">Started {formatDate(session.createdAt)}</p>
                  </div>
                  <button onClick={() => revokeSession(session.id)} className="rounded-lg border border-red-200 px-3 py-2 text-xs font-semibold text-red-600 dark:border-red-500/20">Revoke</button>
                </div>
              ))}
            </div>
          </section>
          <section className="data-surface p-5">
            <div className="flex items-center gap-2"><ShieldCheck size={18} className="text-[var(--brand-primary)]" /><h2 className="font-semibold text-[var(--text-primary)]">Login history</h2></div>
            <div className="mt-4 max-h-[430px] divide-y divide-[var(--border-subtle)] overflow-auto">
              {data.security.loginHistory.length === 0 && <p className="py-8 text-center text-sm text-[var(--text-muted)]">No login history recorded yet.</p>}
              {data.security.loginHistory.map((item) => (
                <div key={item.id} className="py-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-medium text-[var(--text-primary)]">{item.success ? 'Successful sign in' : item.failureReason || 'Failed sign in'}</span>
                    <span className={`h-2 w-2 rounded-full ${item.success ? 'bg-emerald-500' : 'bg-red-500'}`} />
                  </div>
                  <p className="mt-1 text-xs text-[var(--text-muted)]">{item.ipAddress || 'Unknown IP'} · {formatDate(item.createdAt)}{item.suspicious ? ' · Suspicious' : ''}</p>
                </div>
              ))}
            </div>
          </section>
        </div>
      )}

      {activeTab === 'health' && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Object.entries(data.health).map(([name, service]) => {
            const healthy = service.status === 'OPERATIONAL';
            const optional = service.status === 'NOT_CONFIGURED';
            return (
              <article key={name} className="data-surface p-5">
                <div className="flex items-center justify-between">
                  <span className="capitalize font-semibold text-[var(--text-primary)]">{name}</span>
                  {healthy ? <CheckCircle2 size={18} className="text-emerald-500" /> : optional ? <LockKeyhole size={18} className="text-amber-500" /> : <AlertTriangle size={18} className="text-red-500" />}
                </div>
                <p className="mt-4 text-xs font-bold tracking-wide text-[var(--text-muted)]">{service.status.replace('_', ' ')}</p>
                <p className="mt-2 text-sm text-[var(--text-secondary)]">{service.message}</p>
              </article>
            );
          })}
        </div>
      )}

      {creating && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm">
          <div className="data-surface w-full max-w-xl p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">{activeTab === 'complaints' ? 'Submit complaint' : 'Create support request'}</h2>
                <p className="mt-1 text-sm text-[var(--text-muted)]">Your request receives a permanent tracking number.</p>
              </div>
              <button onClick={() => setCreating(false)} className="rounded-lg p-2 text-[var(--text-muted)] hover:bg-[var(--bg-hover)]"><X size={18} /></button>
            </div>
            <div className="mt-5 space-y-4">
              <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} placeholder="Subject" className="w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <div className="grid grid-cols-2 gap-3">
                <select value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })} className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none">
                  <option value="TECHNICAL">Technical</option>
                  <option value="BILLING">Billing</option>
                  <option value="SUBSCRIPTION">Subscription</option>
                  <option value="GPS">GPS</option>
                  <option value="SECURITY">Security</option>
                  <option value="SERVICE">Service</option>
                </select>
                <select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })} className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none">
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>
              <textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} rows={6} placeholder="Describe what happened and the result you need." className="w-full resize-none rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <button disabled={submitting} onClick={submitTicket} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-[var(--brand-primary)] px-4 py-3 text-sm font-semibold text-[var(--brand-primary-foreground,#171817)] disabled:opacity-50">
                <Send size={16} /> {submitting ? 'Submitting...' : 'Submit request'}
              </button>
            </div>
          </div>
        </div>
      )}

      {selectedTicket && (
        <div className="fixed inset-0 z-[110] flex items-end justify-end bg-black/45 backdrop-blur-sm sm:p-4">
          <section className="flex h-[92vh] w-full max-w-xl flex-col overflow-hidden bg-[var(--bg-card)] shadow-2xl sm:h-[calc(100vh-2rem)] sm:rounded-lg sm:border sm:border-[var(--border-subtle)]">
            <header className="flex items-start justify-between border-b border-[var(--border-subtle)] p-5">
              <div>
                <p className="font-mono text-xs text-[var(--brand-primary)]">{selectedTicket.ticketNumber}</p>
                <h2 className="mt-1 font-semibold text-[var(--text-primary)]">{selectedTicket.subject}</h2>
              </div>
              <button onClick={() => { setSelectedTicket(null); setMessages([]); }} className="rounded-lg p-2 text-[var(--text-muted)] hover:bg-[var(--bg-hover)]"><X size={18} /></button>
            </header>
            <div className="flex-1 space-y-4 overflow-auto p-5">
              {messages.length === 0 && <p className="py-12 text-center text-sm text-[var(--text-muted)]">No messages yet. Start the conversation below.</p>}
              {messages.map((message) => (
                <div key={message.id} className={`flex ${message.senderType === 'AGENCY' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[86%] rounded-lg px-4 py-3 ${message.senderType === 'AGENCY' ? 'bg-[var(--brand-primary)] text-[var(--brand-primary-foreground,#171817)]' : 'border border-[var(--border-subtle)] bg-[var(--bg-hover)] text-[var(--text-primary)]'}`}>
                    <p className="text-[11px] font-bold opacity-65">{message.senderName}</p>
                    {message.message && <p className="mt-1 whitespace-pre-wrap text-sm">{message.message}</p>}
                    {message.attachmentData && (
                      <a href={message.attachmentData} download={message.attachmentName} className="mt-3 flex items-center gap-2 rounded-md bg-black/10 px-3 py-2 text-xs font-semibold">
                        <Paperclip size={13} /> {message.attachmentName}
                      </a>
                    )}
                    <p className="mt-2 text-[10px] opacity-55">{formatDate(message.createdAt)} · {message.read ? 'Read' : 'Sent'}</p>
                  </div>
                </div>
              ))}
            </div>
            <footer className="border-t border-[var(--border-subtle)] p-4">
              {attachment && (
                <div className="mb-3 flex items-center justify-between rounded-lg bg-[var(--bg-hover)] px-3 py-2 text-xs text-[var(--text-secondary)]">
                  <span className="truncate">{attachment.name}</span>
                  <button onClick={() => setAttachment(null)}><X size={14} /></button>
                </div>
              )}
              <div className="flex items-end gap-2">
                <label className="inline-flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-[var(--border-subtle)] text-[var(--text-muted)]" title="Attach file">
                  <Paperclip size={17} />
                  <input type="file" accept="image/*,application/pdf,audio/*" className="hidden" onChange={(event) => chooseAttachment(event.target.files?.[0])} />
                </label>
                <textarea value={messageText} onChange={(event) => setMessageText(event.target.value)} rows={2} placeholder="Write a message..." className="min-h-11 flex-1 resize-none rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-2.5 text-sm text-[var(--text-primary)] outline-none" />
                <button disabled={submitting || (!messageText.trim() && !attachment)} onClick={sendMessage} className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-[var(--brand-primary)] text-[var(--brand-primary-foreground,#171817)] disabled:opacity-40"><Send size={17} /></button>
              </div>
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}
