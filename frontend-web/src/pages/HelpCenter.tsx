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
  categories: { value: string; label: string }[];
};

const CHANNELS: Channel[] = [
  {
    id: 'CONTACT',
    label: 'Contact Sales',
    description: 'General questions, pricing, demo requests, partnerships.',
    icon: Mail,
    categories: [
      { value: 'GENERAL', label: 'General question' },
      { value: 'SALES', label: 'Sales' },
      { value: 'SUBSCRIPTION', label: 'Pricing / Demo' },
      { value: 'OTHER', label: 'Other' },
    ],
  },
  {
    id: 'SUPPORT',
    label: 'Support Request',
    description: 'Account, login, contracts, reservations, vehicles.',
    icon: HelpCircle,
    categories: [
      { value: 'ACCOUNT', label: 'Account problem' },
      { value: 'LOGIN', label: 'Login problem' },
      { value: 'CONTRACT', label: 'Contract problem' },
      { value: 'RESERVATION', label: 'Reservation problem' },
      { value: 'VEHICLE', label: 'Vehicle problem' },
    ],
  },
  {
    id: 'TECHNICAL',
    label: 'Technical Issue',
    description: 'GPS, SMTP/email, PDF generation, bugs, system errors.',
    icon: Wrench,
    categories: [
      { value: 'GPS', label: 'GPS integration' },
      { value: 'EMAIL_SMTP', label: 'SMTP / email problem' },
      { value: 'PDF', label: 'PDF generation' },
      { value: 'BUG', label: 'Bug report' },
      { value: 'DATA_RESET', label: 'Data problem' },
    ],
  },
  {
    id: 'BILLING',
    label: 'Billing Issue',
    description: 'Payment failures, invoices, subscription changes.',
    icon: BadgeDollarSign,
    categories: [
      { value: 'BILLING', label: 'Billing' },
      { value: 'SUBSCRIPTION', label: 'Subscription' },
    ],
  },
  {
    id: 'SECURITY',
    label: 'Security Issue',
    description: 'Suspicious activity, unauthorized access, data concerns.',
    icon: ShieldAlert,
    categories: [
      { value: 'SECURITY', label: 'Security concern' },
    ],
  },
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
  const [form, setForm] = useState({ subject: '', description: '', category: '', priority: 'NORMAL' });
  const [submitting, setSubmitting] = useState(false);

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
    setForm({ subject: '', description: '', category: channel.categories[0]?.value ?? 'OTHER', priority: 'NORMAL' });
  };

  const submitTicket = async () => {
    if (!activeChannel) return;
    if (!form.subject.trim() || !form.description.trim()) {
      showToast('Subject and message are required.', 'warning');
      return;
    }
    setSubmitting(true);
    try {
      const response = await api.post('/operations-center/tickets', {
        ...form,
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
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to submit your request. Please try again later.', 'error');
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
                      <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${priorityClass[ticket.priority] ?? priorityClass.NORMAL}`}>{ticket.priority}</span>
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-[var(--bg-surface)] p-6 shadow-xl">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-[var(--text-primary)]">{activeChannel.label}</h3>
              <button onClick={() => setActiveChannel(null)} className="rounded-lg p-1.5 text-[var(--text-muted)] hover:bg-[var(--bg-hover)]"><X size={18} /></button>
            </div>
            <div className="mt-4 space-y-3">
              <div>
                <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Category</label>
                <select
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value })}
                  className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                >
                  {activeChannel.categories.map((cat) => (
                    <option key={cat.value} value={cat.value}>{cat.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Priority</label>
                <select
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: e.target.value })}
                  className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                >
                  <option value="LOW">Low</option>
                  <option value="NORMAL">Normal</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Subject</label>
                <input
                  value={form.subject}
                  onChange={(e) => setForm({ ...form, subject: e.target.value })}
                  maxLength={150}
                  className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                  placeholder="Short summary of your request"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold text-[var(--text-secondary)]">Message</label>
                <textarea
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  maxLength={5000}
                  rows={5}
                  className="w-full rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
                  placeholder="Describe your question or issue in detail"
                />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button onClick={() => setActiveChannel(null)} className="rounded-lg border border-[var(--border-subtle)] px-4 py-2 text-sm font-semibold text-[var(--text-secondary)]">Cancel</button>
              <button
                onClick={submitTicket}
                disabled={submitting}
                className="rounded-lg bg-[var(--brand-primary)] px-4 py-2 text-sm font-semibold text-[#171817] disabled:opacity-60"
              >
                {submitting ? 'Submitting...' : 'Submit Request'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
