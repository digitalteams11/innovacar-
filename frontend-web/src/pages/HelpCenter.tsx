import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BadgeDollarSign, BookOpen, HelpCircle, Mail, MessageCircle,
  QrCode, Radar, RefreshCw, Settings2, ShieldAlert, Wrench, X,
} from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import { GlassPageHeader } from '../components/GlassPageHeader';
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
  channel?: string;
  emailStatus?: string;
  createdAt: string;
  resolution?: string;
};

type Channel = {
  id: 'CONTACT' | 'SUPPORT' | 'TECHNICAL' | 'BILLING' | 'SECURITY';
  label: string;
  description: string;
  icon: typeof HelpCircle;
  // `value` is shown to the user; `beValue` is what's actually persisted —
  // it must be one of SupportTicket.Category's real enum constants
  // (BILLING, TECHNICAL, GPS, ACCOUNT, FEATURE_REQUEST, SECURITY, OTHER,
  // UNKNOWN). Sending anything else used to be silently coerced to UNKNOWN
  // by the backend (parseEnumOrUnknown), so every ticket lost its real
  // category — this mapping keeps the user-facing label specific while the
  // stored value stays a value the backend (and Super Admin) can act on.
  categories: { value: string; label: string; beValue: string }[];
};

const CHANNELS: Channel[] = [
  {
    id: 'CONTACT',
    label: 'Contact Sales',
    description: 'General questions, pricing, demo requests, partnerships.',
    icon: Mail,
    categories: [
      { value: 'GENERAL', label: 'General question', beValue: 'OTHER' },
      { value: 'SALES', label: 'Sales', beValue: 'OTHER' },
      { value: 'SUBSCRIPTION', label: 'Pricing / Demo', beValue: 'BILLING' },
      { value: 'OTHER', label: 'Other', beValue: 'OTHER' },
    ],
  },
  {
    id: 'SUPPORT',
    label: 'Support Request',
    description: 'Account, login, contracts, reservations, vehicles.',
    icon: HelpCircle,
    categories: [
      { value: 'ACCOUNT', label: 'Account problem', beValue: 'ACCOUNT' },
      { value: 'LOGIN', label: 'Login problem', beValue: 'ACCOUNT' },
      { value: 'CONTRACT', label: 'Contract problem', beValue: 'OTHER' },
      { value: 'RESERVATION', label: 'Reservation problem', beValue: 'OTHER' },
      { value: 'VEHICLE', label: 'Vehicle problem', beValue: 'OTHER' },
    ],
  },
  {
    id: 'TECHNICAL',
    label: 'Technical Issue',
    description: 'GPS, SMTP/email, PDF generation, bugs, system errors.',
    icon: Wrench,
    categories: [
      { value: 'GPS', label: 'GPS integration', beValue: 'GPS' },
      { value: 'EMAIL_SMTP', label: 'SMTP / email problem', beValue: 'TECHNICAL' },
      { value: 'PDF', label: 'PDF generation', beValue: 'TECHNICAL' },
      { value: 'BUG', label: 'Bug report', beValue: 'TECHNICAL' },
      { value: 'DATA_RESET', label: 'Data problem', beValue: 'TECHNICAL' },
    ],
  },
  {
    id: 'BILLING',
    label: 'Billing Issue',
    description: 'Payment failures, invoices, subscription changes.',
    icon: BadgeDollarSign,
    categories: [
      { value: 'BILLING', label: 'Billing', beValue: 'BILLING' },
      { value: 'SUBSCRIPTION', label: 'Subscription', beValue: 'BILLING' },
    ],
  },
  {
    id: 'SECURITY',
    label: 'Security Issue',
    description: 'Suspicious activity, unauthorized access, data concerns.',
    icon: ShieldAlert,
    categories: [
      { value: 'SECURITY', label: 'Security concern', beValue: 'SECURITY' },
    ],
  },
];

// Displayed labels stay Low/Normal/High/Urgent (matches the rest of the product's
// vocabulary), but the value sent is one of SupportTicket.Priority's real enum
// constants (LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN) — same reasoning as beValue above.
const PRIORITIES: { value: string; label: string }[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Normal' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Urgent' },
];

const quickHelp = [
  { icon: QrCode, title: 'How to send a QR signature', body: 'Open a contract, choose "Sign via QR" and share the code with your client.' },
  { icon: BookOpen, title: 'How to generate a PDF', body: 'From any signed contract, use the "Export PDF" action in the details view.' },
  { icon: Radar, title: 'How to configure GPS', body: 'Go to GPS Settings and connect your tracking provider credentials.' },
  { icon: Settings2, title: 'How to configure SMTP', body: 'Super Admin > Email Center lets you set up your outgoing mail server.' },
];

const priorityClass: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-600 dark:bg-white/5 dark:text-slate-300',
  NORMAL: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  MEDIUM: 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300',
  HIGH: 'bg-orange-50 text-orange-700 dark:bg-orange-500/10 dark:text-orange-300',
  URGENT: 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300',
  CRITICAL: 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300',
};

function formatDate(value?: string) {
  if (!value) return 'Not available';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function HelpCenter() {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [activeChannel, setActiveChannel] = useState<Channel | null>(null);
  const [form, setForm] = useState({ subject: '', description: '', category: '', priority: 'MEDIUM' });
  const [submitting, setSubmitting] = useState(false);
  const [touched, setTouched] = useState(false);

  const subjectValid = form.subject.trim().length >= 5 && form.subject.trim().length <= 150;
  const descriptionValid = form.description.trim().length >= 10 && form.description.trim().length <= 5000;
  const categoryValid = form.category.trim().length > 0;
  const priorityValid = form.priority.trim().length > 0;
  const formValid = subjectValid && descriptionValid && categoryValid && priorityValid;

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await api.get<unknown>('/operations-center');
      const payload = response.data && typeof response.data === 'object' && 'data' in response.data
        ? (response.data as { data?: unknown }).data
        : response.data;
      const value = payload && typeof payload === 'object' ? payload as { tickets?: unknown; complaints?: unknown } : {};
      const combined = [
        ...(Array.isArray(value.tickets) ? value.tickets as Ticket[] : []),
        ...(Array.isArray(value.complaints) ? value.complaints as Ticket[] : []),
      ].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setTickets(combined);
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

  const openChannel = (channel: Channel) => {
    setActiveChannel(channel);
    setTouched(false);
    setForm({ subject: '', description: '', category: channel.categories[0]?.value ?? 'OTHER', priority: 'MEDIUM' });
  };

  const submitTicket = async () => {
    if (!activeChannel || submitting) return;
    setTouched(true);
    if (!formValid) {
      showToast('Please fix the highlighted fields before submitting.', 'warning');
      return;
    }
    const selectedCategory = activeChannel.categories.find((cat) => cat.value === form.category);
    setSubmitting(true);
    try {
      const response = await api.post('/operations-center/tickets', {
        subject: form.subject.trim(),
        description: form.description.trim(),
        category: selectedCategory?.beValue ?? 'OTHER',
        priority: form.priority,
        channel: activeChannel.id,
        kind: 'SUPPORT',
      });
      const ticketNumber = (response.data as { ticket?: { ticketNumber?: string } })?.ticket?.ticketNumber;
      showToast(
        ticketNumber ? `Request submitted — ${ticketNumber}. We sent it to the right team.` : 'We sent your request to the right team.',
        'success'
      );
      setActiveChannel(null);
      await load();
    } catch (requestError: any) {
      console.error(requestError);
      const status = requestError?.response?.status;
      const serverMessage = requestError?.response?.data?.message;
      const message = status === 400 && serverMessage
        ? serverMessage
        : status === 401
        ? 'Your session has expired. Please sign in again.'
        : status === 403
        ? 'You do not have permission to submit a request.'
        : 'Unable to submit your request. Please try again later.';
      showToast(message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const headerAction = useMemo(() => (
    <button onClick={load} className="inline-flex items-center gap-2 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-sm text-[var(--text-secondary)]">
      <RefreshCw size={15} /> Refresh
    </button>
  ), [load]);

  if (loading && tickets.length === 0) return <PremiumLoader />;
  if (error) return <ApiErrorState message="Unable to load the Help Center. Please try again later." onRetry={load} />;

  return (
    <div className="space-y-6">
      <GlassPageHeader
        title="Help & Support Center"
        subtitle="Ask a question, report a problem, and track every request in one place."
        icon={HelpCircle}
        actions={headerAction}
      />

      <section>
        <h2 className="mb-3 text-sm font-semibold text-[var(--text-secondary)]">Quick help</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {quickHelp.map((item) => (
            <div key={item.title} className="data-surface p-4">
              <item.icon size={18} className="text-[var(--brand-primary)]" />
              <h3 className="mt-3 text-sm font-semibold text-[var(--text-primary)]">{item.title}</h3>
              <p className="mt-1 text-xs text-[var(--text-muted)]">{item.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold text-[var(--text-secondary)]">Contact support</h2>
        <p className="mb-3 text-xs text-[var(--text-muted)]">
          If your system stopped working, choose Technical. If you have a question before buying, choose Contact. If you need help with your account, choose Support.
        </p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {CHANNELS.map((channel) => (
            <button
              key={channel.id}
              onClick={() => openChannel(channel)}
              className="data-surface flex flex-col items-start p-4 text-left transition hover:border-[var(--brand-primary)]"
            >
              <channel.icon size={20} className="text-[var(--brand-primary)]" />
              <h3 className="mt-3 text-sm font-semibold text-[var(--text-primary)]">{channel.label}</h3>
              <p className="mt-1 text-xs text-[var(--text-muted)]">{channel.description}</p>
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold text-[var(--text-secondary)]">My tickets</h2>
        <div className="data-surface overflow-hidden">
          {tickets.length === 0 ? (
            <div className="flex min-h-48 flex-col items-center justify-center p-8 text-center">
              <MessageCircle size={26} className="text-[var(--brand-primary)]" />
              <h3 className="mt-3 font-semibold text-[var(--text-primary)]">No requests yet</h3>
              <p className="mt-2 max-w-md text-sm text-[var(--text-muted)]">Choose a channel above to reach the right team.</p>
            </div>
          ) : (
            <div className="divide-y divide-[var(--border-subtle)]">
              {tickets.map((ticket) => (
                <button
                  key={ticket.id}
                  onClick={() => navigate(`/tickets/${ticket.id}`)}
                  className="grid w-full gap-2 p-4 text-left md:grid-cols-[1fr_auto] md:items-center"
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-xs text-[var(--brand-primary)]">{ticket.ticketNumber}</span>
                      {ticket.channel && <span className="rounded-md bg-[var(--bg-hover)] px-2 py-1 text-[10px] font-bold text-[var(--text-secondary)]">{ticket.channel}</span>}
                      <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${priorityClass[ticket.priority] ?? priorityClass.MEDIUM}`}>{ticket.priority}</span>
                      <span className="rounded-md bg-emerald-50 px-2 py-1 text-[10px] font-bold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">{ticket.status}</span>
                    </div>
                    <h3 className="mt-2 text-sm font-semibold text-[var(--text-primary)]">{ticket.subject}</h3>
                  </div>
                  <time className="text-xs text-[var(--text-muted)]">{formatDate(ticket.createdAt)}</time>
                </button>
              ))}
            </div>
          )}
        </div>
      </section>

      {activeChannel && (
        <div className="fixed inset-0 z-[100]">
          {/* Backdrop — opacity/blur belongs here only, never on the dialog surface itself. */}
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => !submitting && setActiveChannel(null)} />
          <div className="relative flex min-h-full items-center justify-center p-0 sm:p-4">
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="support-modal-title"
              className="relative flex h-[100dvh] w-full flex-col overflow-hidden bg-white shadow-2xl dark:bg-[#0f172a] sm:h-auto sm:max-h-[90vh] sm:w-[calc(100%-16px)] sm:max-w-2xl sm:rounded-2xl sm:border sm:border-[#e8e6e1] sm:dark:border-white/10"
            >
              {/* Sticky header */}
              <div className="flex shrink-0 items-start justify-between gap-4 border-b border-[#e8e6e1] px-5 py-4 dark:border-white/10 sm:px-6">
                <div>
                  <h3 id="support-modal-title" className="text-lg font-bold text-[#1e293b] dark:text-white">
                    {activeChannel.label}
                  </h3>
                  <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                    Tell us what happened and our support team will respond.
                  </p>
                </div>
                <button
                  onClick={() => !submitting && setActiveChannel(null)}
                  aria-label="Close"
                  className="shrink-0 rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-emerald-500/40 dark:hover:bg-white/5 dark:hover:text-slate-200"
                >
                  <X size={18} />
                </button>
              </div>

              {/* Scrollable body — independent from header/footer */}
              <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-[#1e293b] dark:text-slate-200">
                      Category <span className="text-rose-500">*</span>
                    </label>
                    <select
                      value={form.category}
                      onChange={(e) => setForm({ ...form, category: e.target.value })}
                      className="h-11 w-full rounded-lg border border-[#e8e6e1] bg-white px-3 text-[16px] text-[#1e293b] outline-none transition-colors focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/30 dark:border-white/10 dark:bg-[#1e293b] dark:text-white sm:text-sm"
                    >
                      {activeChannel.categories.map((cat) => (
                        <option key={cat.value} value={cat.value}>{cat.label}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-[#1e293b] dark:text-slate-200">
                      Priority <span className="text-rose-500">*</span>
                    </label>
                    <select
                      value={form.priority}
                      onChange={(e) => setForm({ ...form, priority: e.target.value })}
                      className="h-11 w-full rounded-lg border border-[#e8e6e1] bg-white px-3 text-[16px] text-[#1e293b] outline-none transition-colors focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/30 dark:border-white/10 dark:bg-[#1e293b] dark:text-white sm:text-sm"
                    >
                      {PRIORITIES.map((p) => (
                        <option key={p.value} value={p.value}>{p.label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="md:col-span-2">
                    <label className="mb-1.5 block text-sm font-medium text-[#1e293b] dark:text-slate-200">
                      Subject <span className="text-rose-500">*</span>
                    </label>
                    <input
                      value={form.subject}
                      onChange={(e) => setForm({ ...form, subject: e.target.value })}
                      maxLength={150}
                      placeholder="Short summary of your request"
                      className={`h-11 w-full rounded-lg border bg-white px-3 text-[16px] text-[#1e293b] outline-none transition-colors placeholder:text-slate-400 focus:ring-2 dark:bg-[#1e293b] dark:text-white dark:placeholder:text-slate-500 sm:text-sm ${
                        touched && !subjectValid
                          ? 'border-rose-400 focus:border-rose-500 focus:ring-rose-500/30'
                          : 'border-[#e8e6e1] focus:border-emerald-500 focus:ring-emerald-500/30 dark:border-white/10'
                      }`}
                    />
                    <p className={`mt-1 text-xs ${touched && !subjectValid ? 'text-rose-500' : 'text-slate-400 dark:text-slate-500'}`}>
                      {touched && !subjectValid ? 'Subject must be between 5 and 150 characters.' : `${form.subject.trim().length}/150`}
                    </p>
                  </div>
                  <div className="md:col-span-2">
                    <label className="mb-1.5 block text-sm font-medium text-[#1e293b] dark:text-slate-200">
                      Message <span className="text-rose-500">*</span>
                    </label>
                    <textarea
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                      maxLength={5000}
                      rows={6}
                      placeholder="Describe your question or issue in detail"
                      className={`min-h-[150px] w-full rounded-lg border bg-white px-3 py-2.5 text-[16px] text-[#1e293b] outline-none transition-colors placeholder:text-slate-400 focus:ring-2 dark:bg-[#1e293b] dark:text-white dark:placeholder:text-slate-500 sm:text-sm ${
                        touched && !descriptionValid
                          ? 'border-rose-400 focus:border-rose-500 focus:ring-rose-500/30'
                          : 'border-[#e8e6e1] focus:border-emerald-500 focus:ring-emerald-500/30 dark:border-white/10'
                      }`}
                    />
                    <p className={`mt-1 text-xs ${touched && !descriptionValid ? 'text-rose-500' : 'text-slate-400 dark:text-slate-500'}`}>
                      {touched && !descriptionValid ? 'Message must be between 10 and 5000 characters.' : `${form.description.trim().length}/5000`}
                    </p>
                  </div>
                </div>
              </div>

              {/* Sticky footer */}
              <div className="flex shrink-0 items-center justify-end gap-3 border-t border-[#e8e6e1] px-5 py-4 dark:border-white/10 sm:px-6">
                <button
                  onClick={() => setActiveChannel(null)}
                  disabled={submitting}
                  className="min-h-[44px] rounded-lg border border-[#e8e6e1] px-4 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-50 dark:border-white/10 dark:text-slate-300 dark:hover:bg-white/5"
                >
                  Cancel
                </button>
                <button
                  onClick={submitTicket}
                  disabled={submitting}
                  className="min-h-[44px] rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {submitting ? 'Submitting…' : 'Submit Request'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
