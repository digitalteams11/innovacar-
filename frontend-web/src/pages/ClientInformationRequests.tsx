import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, Copy, XCircle, CheckCircle2, UserPlus, Link2, ClipboardList } from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import { GlassPageHeader } from '../components/GlassPageHeader';

interface ClientMatchSummary {
  clientId: number;
  name: string;
  phone?: string;
  email?: string;
  matchedOn: string;
}

interface SubmissionView {
  fullName: string;
  phone: string;
  email?: string;
  documentType?: string;
  documentNumber?: string;
}

interface RequestItem {
  id: number;
  temporaryName: string;
  phone: string;
  email?: string;
  status: 'SENT' | 'SUBMITTED' | 'APPROVED' | 'EXPIRED' | 'REVOKED';
  expiresAt: string;
  submittedAt?: string;
  approvedAt?: string;
  contractId?: number;
  secureLink?: string;
  submission?: SubmissionView;
  potentialDuplicates?: ClientMatchSummary[];
}

const STATUS_STYLES: Record<string, string> = {
  SENT: 'bg-slate-100 text-slate-500',
  SUBMITTED: 'bg-warning-50 text-warning-600',
  APPROVED: 'bg-success-50 text-success-600',
  EXPIRED: 'bg-slate-100 text-slate-400',
  REVOKED: 'bg-danger-50 text-danger-500',
};

export default function ClientInformationRequests() {
  const { t } = useTranslation();
  const { showToast } = useToast();

  const [items, setItems] = useState<RequestItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<RequestItem | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [linkExistingId, setLinkExistingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    api.get('/client-information-requests')
      .then(({ data }) => setItems(data))
      .catch(() => showToast(t('clientInfoAdmin.loadError', 'Unable to load client information requests.'), 'error'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openDetail = async (item: RequestItem) => {
    setDetailLoading(true);
    setSelected(item);
    setLinkExistingId(null);
    try {
      const { data } = await api.get(`/client-information-requests/${item.id}`);
      setSelected(data);
    } catch {
      showToast(t('clientInfoAdmin.loadError', 'Unable to load client information requests.'), 'error');
    } finally {
      setDetailLoading(false);
    }
  };

  const copyLink = (link?: string) => {
    if (!link) return;
    navigator.clipboard?.writeText(link);
    showToast(t('clientInfoAdmin.linkCopied', 'Link copied to clipboard.'), 'success');
  };

  const revoke = async (id: number) => {
    setBusyId(id);
    try {
      await api.post(`/client-information-requests/${id}/revoke`);
      showToast(t('clientInfoAdmin.revoked', 'Link revoked.'), 'success');
      setSelected(null);
      load();
    } catch (err) {
      const userMessage = (err as { userMessage?: string })?.userMessage;
      showToast(userMessage || t('clientInfoAdmin.actionFailed', 'Action failed. Please try again.'), 'error');
    } finally {
      setBusyId(null);
    }
  };

  const approve = async (id: number, action: 'CREATE_NEW' | 'LINK_EXISTING', existingClientId?: number) => {
    setBusyId(id);
    try {
      await api.post(`/client-information-requests/${id}/approve`, { action, existingClientId });
      showToast(t('clientInfoAdmin.approved', 'Client information approved.'), 'success');
      setSelected(null);
      load();
    } catch (err) {
      const userMessage = (err as { userMessage?: string })?.userMessage;
      showToast(userMessage || t('clientInfoAdmin.actionFailed', 'Action failed. Please try again.'), 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title={t('clientInfoAdmin.title', 'Client Information Requests')}
        subtitle={t('clientInfoAdmin.subtitle', 'Review and approve client self-fill submissions before they are linked to a contract.')}
        icon={ClipboardList}
      />

      {loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 size={32} className="animate-spin text-brand-500" /></div>
      ) : items.length === 0 ? (
        <div className="data-surface py-10 text-center text-sm text-slate-400">
          {t('clientInfoAdmin.empty', 'No client information requests yet.')}
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <button
              key={item.id}
              onClick={() => openDetail(item)}
              className="data-surface w-full p-4 text-start flex items-center justify-between gap-3 hover:bg-[var(--bg-hover)] transition-colors"
            >
              <div className="min-w-0">
                <p className="text-sm font-semibold text-[#1e293b] dark:text-white truncate">{item.temporaryName}</p>
                <p className="text-xs text-slate-400">{item.phone}{item.email ? ` · ${item.email}` : ''}</p>
              </div>
              <span className={`shrink-0 px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider ${STATUS_STYLES[item.status]}`}>
                {t(`clientInfoAdmin.status.${item.status}`, item.status)}
              </span>
            </button>
          ))}
        </div>
      )}

      <Modal isOpen={!!selected} onClose={() => setSelected(null)} title={selected?.temporaryName || ''} maxWidth="max-w-lg">
        {detailLoading || !selected ? (
          <div className="flex items-center justify-center py-8"><Loader2 size={24} className="animate-spin text-brand-500" /></div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider ${STATUS_STYLES[selected.status]}`}>
                {t(`clientInfoAdmin.status.${selected.status}`, selected.status)}
              </span>
              {selected.secureLink && (
                <button onClick={() => copyLink(selected.secureLink)} className="flex items-center gap-1.5 text-xs font-medium text-brand-600">
                  <Copy size={13} /> {t('clientInfoAdmin.copyLink', 'Copy link')}
                </button>
              )}
            </div>

            {selected.submission && (
              <div className="rounded-xl border border-[var(--border-subtle)] p-3 space-y-1.5 text-sm">
                <p><strong>{t('clientInfo.form.fullName', 'Full name')}:</strong> {selected.submission.fullName}</p>
                <p><strong>{t('clientInfo.form.phone', 'Primary phone')}:</strong> {selected.submission.phone}</p>
                {selected.submission.email && <p><strong>{t('clientInfo.form.email', 'Email')}:</strong> {selected.submission.email}</p>}
                {selected.submission.documentNumber && (
                  <p><strong>{t('clientInfo.sections.document', 'Identity document')}:</strong> {selected.submission.documentType} {selected.submission.documentNumber}</p>
                )}
              </div>
            )}

            {selected.potentialDuplicates && selected.potentialDuplicates.length > 0 && (
              <div className="rounded-xl border border-warning-200 bg-warning-50/60 p-3 space-y-2">
                <p className="text-xs font-bold uppercase tracking-wider text-warning-600">
                  {t('clientInfoAdmin.possibleDuplicates', 'Possible existing clients')}
                </p>
                {selected.potentialDuplicates.map((m) => (
                  <label key={m.clientId} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="radio"
                      name="link-existing"
                      checked={linkExistingId === m.clientId}
                      onChange={() => setLinkExistingId(m.clientId)}
                    />
                    <span>{m.name} — {m.phone || m.email} <span className="text-slate-400">({m.matchedOn})</span></span>
                  </label>
                ))}
              </div>
            )}

            {selected.status === 'SUBMITTED' && (
              <div className="flex flex-col gap-2 pt-2 border-t border-[var(--border-subtle)]">
                {linkExistingId != null && (
                  <button
                    onClick={() => approve(selected.id, 'LINK_EXISTING', linkExistingId)}
                    disabled={busyId === selected.id}
                    className="flex items-center justify-center gap-2 py-2.5 bg-brand-50 text-brand-600 rounded-xl font-semibold text-sm"
                  >
                    <Link2 size={15} /> {t('clientInfoAdmin.linkExisting', 'Link to selected existing client')}
                  </button>
                )}
                <button
                  onClick={() => approve(selected.id, 'CREATE_NEW')}
                  disabled={busyId === selected.id}
                  className="flex items-center justify-center gap-2 py-2.5 bg-success-50 text-success-600 rounded-xl font-semibold text-sm"
                >
                  <UserPlus size={15} /> {t('clientInfoAdmin.createNew', 'Approve & create new client')}
                </button>
                <button
                  onClick={() => revoke(selected.id)}
                  disabled={busyId === selected.id}
                  className="flex items-center justify-center gap-2 py-2.5 bg-danger-50 text-danger-500 rounded-xl font-semibold text-sm"
                >
                  <XCircle size={15} /> {t('clientInfoAdmin.revoke', 'Revoke')}
                </button>
              </div>
            )}
            {selected.status === 'SENT' && (
              <button
                onClick={() => revoke(selected.id)}
                disabled={busyId === selected.id}
                className="w-full flex items-center justify-center gap-2 py-2.5 bg-danger-50 text-danger-500 rounded-xl font-semibold text-sm"
              >
                <XCircle size={15} /> {t('clientInfoAdmin.revoke', 'Revoke')}
              </button>
            )}
            {selected.status === 'APPROVED' && (
              <div className="flex items-center gap-2 text-sm text-success-600">
                <CheckCircle2 size={15} /> {t('clientInfoAdmin.approvedNote', 'Approved and linked.')}
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
