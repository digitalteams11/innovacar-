import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import { ArrowLeft, Clock, User, Tag, Send, CheckCircle, Mail, RefreshCw, Sparkles } from 'lucide-react';
import { PageHeader, Badge, TextArea, TabGroup } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminTicketDetail() {
  const { id } = useParams<{ id: string }>();
  useTranslation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [ticket, setTicket] = useState<any>(null);
  const [notes, setNotes] = useState<any[]>([]);
  const [newNote, setNewNote] = useState('');
  const [messages, setMessages] = useState<any[]>([]);
  const [reply, setReply] = useState('');
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('details');
  const [resending, setResending] = useState(false);
  const [draftingReply, setDraftingReply] = useState(false);

  useEffect(() => {
    if (id) fetchTicket();
  }, [id]);

  const fetchTicket = async () => {
    setLoading(true);
    try {
      const [ticketRes, notesRes, messagesRes] = await Promise.all([
        superAdminApi.getTicket(Number(id)),
        superAdminApi.getTicketNotes(Number(id)).catch(() => ({ data: [] })),
        superAdminApi.getTicketMessages(Number(id)).catch(() => ({ data: [] })),
      ]);
      setTicket(ticketRes.data);
      setNotes(notesRes.data);
      setMessages(messagesRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleReply = async () => {
    if (!reply.trim()) return;
    try {
      await superAdminApi.sendTicketMessage(Number(id), { message: reply });
      setReply('');
      await fetchTicket();
      showToast('Reply sent', 'success');
    } catch (err) {
      console.error(err);
      showToast('Unable to send this reply.', 'error');
    }
  };

  const handleGenerateAiDraft = async () => {
    setDraftingReply(true);
    try {
      const res = await superAdminApi.generateTicketAiDraftReply(Number(id));
      setReply(res.data?.data?.draft ?? '');
      showToast('AI draft generated. Review before sending.', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err?.response?.data?.message ?? 'Unable to generate an AI draft right now.', 'error');
    } finally {
      setDraftingReply(false);
    }
  };

  const handleStatusChange = async (status: string) => {
    try {
      await superAdminApi.updateTicket(Number(id), { status });
      fetchTicket();
      showToast(`Ticket marked as ${status.toLowerCase()}`, 'success');
    } catch (err) {
      console.error(err);
    }
  };

  const handleResendEmail = async () => {
    setResending(true);
    try {
      const res = await superAdminApi.resendTicketEmail(Number(id));
      const status = (res.data as { emailStatus?: string })?.emailStatus;
      showToast(status === 'SENT' ? 'Email resent' : 'Delivery still failing', status === 'SENT' ? 'success' : 'warning');
      await fetchTicket();
    } catch (err) {
      console.error(err);
      showToast('Unable to resend this email.', 'error');
    } finally {
      setResending(false);
    }
  };

  const handleAddNote = async () => {
    if (!newNote.trim()) return;
    try {
      await superAdminApi.addTicketNote(Number(id), { content: newNote, isInternal: true });
      setNewNote('');
      fetchTicket();
      showToast('Note added successfully', 'success');
    } catch (err) {
      console.error(err);
    }
  };

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <p className="text-slate-400">Ticket not found</p>
      </div>
    );
  }

  const tabs = [
    { id: 'details', label: 'Details' },
    { id: 'conversation', label: `Conversation (${messages.length})` },
    { id: 'notes', label: `Internal Notes (${notes.length})` },
  ];

  const priorityColors: Record<string, any> = {
    LOW: 'default', MEDIUM: 'info', HIGH: 'warning', URGENT: 'danger',
  };

  return (
    <div className="space-y-6 animate-fade">
      <button onClick={() => navigate('/super-admin/support')} className="flex items-center gap-1 text-sm text-slate-500 hover:text-[#1e293b] dark:hover:text-white transition-colors mb-3">
        <ArrowLeft size={16} />
        Back to Support
      </button>

      <PageHeader title={`Ticket #${ticket.id}`} subtitle={ticket.subject}>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={priorityColors[ticket.priority] || 'default'}>{ticket.priority || 'LOW'}</Badge>
          <Badge variant={ticket.status === 'OPEN' ? 'info' : ticket.status === 'RESOLVED' || ticket.status === 'CLOSED' ? 'success' : 'warning'}>{ticket.status}</Badge>
        </div>
      </PageHeader>

      {/* Ticket Info Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {[
          { label: 'From', value: ticket.agencyName || ticket.createdBy || '-', icon: User },
          { label: 'Channel / Category', value: [ticket.channel, ticket.category].filter(Boolean).join(' / ') || '-', icon: Tag },
          { label: 'Assigned To', value: ticket.assignedTo || 'Unassigned', icon: User },
          { label: 'Created', value: ticket.createdAt ? new Date(ticket.createdAt).toLocaleString() : '-', icon: Clock },
        ].map((item) => (
          <div key={item.label} className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
            <div className="flex items-center gap-2 mb-2">
              <item.icon size={14} className="text-slate-400" />
              <span className="text-xs text-slate-500">{item.label}</span>
            </div>
            <p className="text-sm font-medium text-[#1e293b] dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      {/* Routing / Email delivery */}
      {(ticket.destinationEmail || ticket.emailStatus) && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#e8e6e1]/80 bg-white p-4 shadow-soft dark:border-white/5 dark:bg-[#1a2332]/70">
          <div className="flex items-center gap-2 text-sm">
            <Mail size={15} className="text-slate-400" />
            <span className="text-slate-500">Routed to</span>
            <span className="font-medium text-[#1e293b] dark:text-white">{ticket.destinationEmail || '-'}</span>
            {ticket.emailStatus && (
              <Badge variant={ticket.emailStatus === 'SENT' ? 'success' : ticket.emailStatus === 'FAILED' ? 'danger' : 'default'}>{ticket.emailStatus}</Badge>
            )}
          </div>
          <button
            onClick={handleResendEmail}
            disabled={resending}
            className="inline-flex items-center gap-2 rounded-xl border border-[#e8e6e1] px-3 py-2 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-50 dark:border-white/10 dark:text-slate-300 dark:hover:bg-white/5"
          >
            <RefreshCw size={13} className={resending ? 'animate-spin' : ''} /> Resend email
          </button>
        </div>
      )}

      {/* Status Actions */}
      <div className="flex flex-wrap gap-2">
        {['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].map((status) => (
          <button
            key={status}
            onClick={() => handleStatusChange(status)}
            disabled={ticket.status === status}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
              ticket.status === status
                ? 'bg-[#0a0f2c] text-white'
                : 'bg-white dark:bg-[#1a2332]/70 border border-[#e8e6e1]/80 dark:border-white/5 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'
            } disabled:opacity-60`}
          >
            {status.replace('_', ' ')}
          </button>
        ))}
      </div>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'details' && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-2">Description</h3>
              <p className="text-sm text-slate-600 dark:text-slate-300 whitespace-pre-wrap">{ticket.description || 'No description provided.'}</p>
            </div>
            {ticket.resolution && (
              <div className="p-4 bg-emerald-50 dark:bg-emerald-500/10 rounded-xl border border-emerald-200 dark:border-emerald-500/20">
                <h3 className="text-sm font-bold text-emerald-700 dark:text-emerald-400 mb-2 flex items-center gap-2">
                  <CheckCircle size={16} />
                  Resolution
                </h3>
                <p className="text-sm text-emerald-700 dark:text-emerald-300">{ticket.resolution}</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'notes' && (
          <div className="space-y-4">
            <div className="flex flex-col sm:flex-row gap-3">
              <div className="flex-1 min-w-0">
                <TextArea value={newNote} onChange={setNewNote} placeholder="Add an internal note..." rows={3} />
              </div>
              <button
                onClick={handleAddNote}
                disabled={!newNote.trim()}
                className="self-end px-4 py-2.5 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white rounded-xl text-sm font-medium transition-colors disabled:opacity-40"
              >
                <Send size={16} />
              </button>
            </div>
            <div className="space-y-3">
              {notes.length === 0 ? (
                <p className="text-sm text-slate-400 text-center py-8">No internal notes yet.</p>
              ) : notes.map((note: any) => (
                <div key={note.id} className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-medium text-[#1e293b] dark:text-white">{note.createdBy || 'Support Agent'}</span>
                    <span className="text-xs text-slate-400">{note.createdAt ? new Date(note.createdAt).toLocaleString() : '-'}</span>
                  </div>
                  <p className="text-sm text-slate-600 dark:text-slate-300">{note.content}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'conversation' && (
          <div className="space-y-4">
            <div className="max-h-[430px] space-y-3 overflow-auto rounded-lg bg-slate-50 p-4 dark:bg-white/5">
              {messages.length === 0 && <p className="py-8 text-center text-sm text-slate-400">No conversation messages yet.</p>}
              {messages.map((message: any) => (
                <div key={message.id} className={`flex ${message.senderType === 'SUPPORT' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[85%] rounded-lg px-4 py-3 ${message.senderType === 'SUPPORT' ? 'bg-[#b69152] text-[#171817]' : 'bg-white text-slate-700 shadow-sm dark:bg-[#242c38] dark:text-slate-200'}`}>
                    <p className="text-[11px] font-bold opacity-60">{message.senderName}</p>
                    <p className="mt-1 whitespace-pre-wrap text-sm">{message.message}</p>
                    {message.attachmentData && (
                      <a href={message.attachmentData} download={message.attachmentName} className="mt-2 block text-xs font-semibold underline">
                        {message.attachmentName}
                      </a>
                    )}
                    <p className="mt-2 text-[10px] opacity-50">{message.createdAt ? new Date(message.createdAt).toLocaleString() : ''}</p>
                  </div>
                </div>
              ))}
            </div>
            <div className="flex items-end gap-3">
              <div className="flex-1"><TextArea value={reply} onChange={setReply} placeholder="Reply to the agency..." rows={3} /></div>
              <button
                onClick={handleGenerateAiDraft}
                disabled={draftingReply}
                title="Generate AI draft reply"
                className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-[#b69152]/40 text-[#b69152] disabled:opacity-40"
              >
                <Sparkles size={16} className={draftingReply ? 'animate-pulse' : ''} />
              </button>
              <button onClick={handleReply} disabled={!reply.trim()} className="inline-flex h-11 w-11 items-center justify-center rounded-lg bg-[#b69152] text-[#171817] disabled:opacity-40"><Send size={16} /></button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
