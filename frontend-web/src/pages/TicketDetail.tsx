import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, MessageCircle, Paperclip, Send } from 'lucide-react';
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
  createdAt: string;
  resolution?: string;
};

type Message = {
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

function formatDate(value?: string) {
  if (!value) return 'Not available';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function TicketDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [messageText, setMessageText] = useState('');
  const [attachment, setAttachment] = useState<{ name: string; type: string; data: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadTicket = useCallback(async () => {
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
      ];
      const found = combined.find((t) => String(t.id) === id) ?? null;
      setTicket(found);
      if (!found) setError(true);
    } catch (requestError) {
      console.error(requestError);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadMessages = useCallback(async () => {
    if (!id) return;
    try {
      const response = await api.get<unknown>(`/operations-center/tickets/${id}/messages`);
      const payload = response.data && typeof response.data === 'object' && 'data' in response.data
        ? (response.data as { data?: unknown }).data
        : response.data;
      setMessages(Array.isArray(payload) ? payload as Message[] : []);
    } catch (requestError) {
      console.error(requestError);
    }
  }, [id]);

  useEffect(() => {
    const timer = window.setTimeout(() => { loadTicket(); loadMessages(); }, 0);
    const interval = window.setInterval(loadMessages, 5000);
    return () => {
      window.clearTimeout(timer);
      window.clearInterval(interval);
    };
  }, [loadTicket, loadMessages]);

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
    if (!id || (!messageText.trim() && !attachment)) return;
    setSubmitting(true);
    try {
      await api.post(`/operations-center/tickets/${id}/messages`, {
        message: messageText,
        attachmentName: attachment?.name,
        attachmentType: attachment?.type,
        attachmentData: attachment?.data,
      });
      setMessageText('');
      setAttachment(null);
      await loadMessages();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to send this message. Please try again.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const headerAction = useMemo(() => (
    <button onClick={() => navigate('/tickets')} className="inline-flex items-center gap-2 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-sm text-[var(--text-secondary)]">
      <ArrowLeft size={15} /> Back to tickets
    </button>
  ), [navigate]);

  if (loading && !ticket) return <PremiumLoader />;
  if (error || !ticket) return <ApiErrorState message="This ticket could not be found." onRetry={loadTicket} />;

  return (
    <div className="space-y-5">
      <GlassPageHeader
        title={ticket.ticketNumber}
        subtitle={ticket.subject}
        icon={MessageCircle}
        actions={headerAction}
      />

      <div className="data-surface p-5">
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {ticket.channel && <span className="rounded-md bg-[var(--bg-hover)] px-2 py-1 font-bold text-[var(--text-secondary)]">{ticket.channel}</span>}
          <span className="rounded-md bg-amber-50 px-2 py-1 font-bold text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">{ticket.priority}</span>
          <span className="rounded-md bg-emerald-50 px-2 py-1 font-bold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">{ticket.status}</span>
          <span className="text-[var(--text-muted)]">{formatDate(ticket.createdAt)}</span>
        </div>
        <p className="mt-3 text-sm text-[var(--text-secondary)]">{ticket.description}</p>
        {ticket.resolution && <p className="mt-3 border-l-2 border-emerald-500 pl-3 text-sm text-[var(--text-secondary)]">{ticket.resolution}</p>}
      </div>

      <div className="data-surface flex flex-col p-5">
        <h2 className="text-sm font-semibold text-[var(--text-secondary)]">Conversation</h2>
        <div className="mt-4 max-h-[420px] space-y-3 overflow-y-auto">
          {messages.length === 0 && <p className="py-6 text-center text-sm text-[var(--text-muted)]">No messages yet.</p>}
          {messages.map((message) => (
            <div key={message.id} className={`max-w-[80%] rounded-xl p-3 text-sm ${message.senderType === 'SUPPORT' ? 'ml-auto bg-[var(--brand-primary)]/10' : 'bg-[var(--bg-hover)]'}`}>
              <div className="flex items-center justify-between gap-3 text-xs text-[var(--text-muted)]">
                <span className="font-semibold text-[var(--text-secondary)]">{message.senderName}</span>
                <span>{formatDate(message.createdAt)}</span>
              </div>
              {message.message && <p className="mt-1 text-[var(--text-primary)]">{message.message}</p>}
              {message.attachmentName && <p className="mt-1 text-xs text-[var(--brand-primary)]">{message.attachmentName}</p>}
            </div>
          ))}
        </div>
        <div className="mt-4 flex items-end gap-2">
          <label className="cursor-pointer rounded-lg border border-[var(--border-subtle)] p-2.5 text-[var(--text-muted)]">
            <Paperclip size={16} />
            <input type="file" className="hidden" onChange={(e) => chooseAttachment(e.target.files?.[0])} />
          </label>
          <textarea
            value={messageText}
            onChange={(e) => setMessageText(e.target.value)}
            rows={2}
            className="flex-1 rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
            placeholder="Write a reply..."
          />
          <button
            onClick={sendMessage}
            disabled={submitting}
            className="rounded-lg bg-[var(--brand-primary)] p-2.5 text-[#171817] disabled:opacity-60"
          >
            <Send size={16} />
          </button>
        </div>
        {attachment && <p className="mt-2 text-xs text-[var(--text-muted)]">Attached: {attachment.name}</p>}
      </div>
    </div>
  );
}
