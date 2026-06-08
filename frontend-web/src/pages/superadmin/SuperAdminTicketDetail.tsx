import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import { ArrowLeft, Clock, User, Tag, Send, CheckCircle } from 'lucide-react';
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
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('details');

  useEffect(() => {
    if (id) fetchTicket();
  }, [id]);

  const fetchTicket = async () => {
    setLoading(true);
    try {
      const [ticketRes, notesRes] = await Promise.all([
        superAdminApi.getTicket(Number(id)),
        superAdminApi.getTicketNotes(Number(id)).catch(() => ({ data: [] })),
      ]);
      setTicket(ticketRes.data);
      setNotes(notesRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (status: string) => {
    try {
      await superAdminApi.updateTicket(Number(id), { status });
      fetchTicket();
      showToast(`Ticket marked as ${status.toLowerCase()}`);
    } catch (err) {
      console.error(err);
    }
  };

  const handleAddNote = async () => {
    if (!newNote.trim()) return;
    try {
      await superAdminApi.addTicketNote(Number(id), { content: newNote, isInternal: true });
      setNewNote('');
      fetchTicket();
      showToast('Note added successfully');
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
          { label: 'Category', value: ticket.category || '-', icon: Tag },
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
      </div>
    </div>
  );
}
