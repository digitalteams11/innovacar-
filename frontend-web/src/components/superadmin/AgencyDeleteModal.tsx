import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Loader2, ShieldAlert } from 'lucide-react';
import Modal from './Modal';
import { superAdminApi } from '../../api/superAdminApi';
import { translateApiError } from '../../api/axios';

interface BlockingReason {
  code: string;
  message: string;
}

interface DeletionImpact {
  agencyId: number;
  agencyName: string;
  users: number;
  vehicles: number;
  clients: number;
  reservations: number;
  contracts: number;
  activeContracts: number;
  payments: number;
  invoices: number;
  documents: number;
  activeSubscription: boolean;
  protectedAgency: boolean;
  canDeleteImmediately: boolean;
  blockingReasons: BlockingReason[];
}

interface AgencyDeleteModalProps {
  isOpen: boolean;
  onClose: () => void;
  agency: { id: number; name: string; email?: string } | null;
  /** Called after a successful permanent delete — parent removes the row / refetches. */
  onDeleted: (id: number) => void;
}

/**
 * Real confirmation modal for permanent agency deletion — replaces the old
 * generic yes/no confirm() dialog for this specific destructive action.
 * Loads real deletion-impact counts, and only enables the final button once
 * the Super Admin has typed the agency's exact name AND checked the
 * "this is permanent" box. The backend re-validates everything here
 * server-side regardless (this is UX, not the security boundary).
 */
export default function AgencyDeleteModal({ isOpen, onClose, agency, onDeleted }: AgencyDeleteModalProps) {
  const { t } = useTranslation();
  const [impact, setImpact] = useState<DeletionImpact | null>(null);
  const [loadingImpact, setLoadingImpact] = useState(false);
  const [impactError, setImpactError] = useState('');
  const [typedName, setTypedName] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  useEffect(() => {
    if (!isOpen || !agency?.id) return;
    setTypedName('');
    setAcknowledged(false);
    setDeleteError('');
    setImpact(null);
    setLoadingImpact(true);
    setImpactError('');
    superAdminApi.getAgencyDeletionImpact(agency.id)
      .then(({ data }) => setImpact(data))
      .catch((err) => setImpactError(translateApiError(err, t)))
      .finally(() => setLoadingImpact(false));
  }, [isOpen, agency?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!agency) return null;

  const nameMatches = typedName.trim() === agency.name;
  const canConfirm = nameMatches && acknowledged && impact?.canDeleteImmediately === true && !deleting;

  const handleConfirm = async () => {
    if (!canConfirm) return;
    setDeleting(true);
    setDeleteError('');
    try {
      await superAdminApi.deleteAgencyPermanently(agency.id, typedName.trim());
      onDeleted(agency.id);
      onClose();
    } catch (err) {
      setDeleteError(translateApiError(err, t));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('superAdmin.agencies.deleteModal.title', 'Delete agency permanently?')} size="md">
      <div className="p-6 space-y-4">
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {t('superAdmin.agencies.deleteModal.description',
            'This action will permanently remove the agency and its associated data. This cannot be undone.')}
        </p>

        {loadingImpact && (
          <div className="flex items-center justify-center py-8 text-slate-400">
            <Loader2 size={22} className="animate-spin" />
          </div>
        )}

        {!loadingImpact && impactError && (
          <div className="flex items-start gap-2 rounded-lg bg-rose-50 dark:bg-rose-500/10 p-3 text-sm text-rose-700 dark:text-rose-300">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            <span>{impactError}</span>
          </div>
        )}

        {!loadingImpact && impact && (
          <>
            <div className="grid grid-cols-2 gap-2 text-xs rounded-lg border border-slate-200 dark:border-white/10 p-3">
              <ImpactRow label={t('superAdmin.agencies.deleteModal.owner', 'Owner email')} value={agency.email || '—'} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.users', 'Users')} value={impact.users} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.vehicles', 'Vehicles')} value={impact.vehicles} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.clients', 'Clients')} value={impact.clients} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.contracts', 'Contracts')} value={impact.contracts} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.reservations', 'Reservations')} value={impact.reservations} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.payments', 'Payments')} value={impact.payments} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.invoices', 'Invoices')} value={impact.invoices} />
              <ImpactRow label={t('superAdmin.agencies.deleteModal.documents', 'Documents')} value={impact.documents} />
              <ImpactRow
                label={t('superAdmin.agencies.deleteModal.subscription', 'Subscription')}
                value={impact.activeSubscription
                  ? t('superAdmin.agencies.deleteModal.active', 'Active')
                  : t('superAdmin.agencies.deleteModal.inactive', 'Inactive')}
              />
            </div>

            {impact.blockingReasons.length > 0 && (
              <div className="space-y-1.5 rounded-lg bg-amber-50 dark:bg-amber-500/10 p-3">
                <p className="flex items-center gap-1.5 text-xs font-bold text-amber-800 dark:text-amber-300">
                  <ShieldAlert size={14} />
                  {t('superAdmin.agencies.deleteModal.blockedTitle', 'This agency cannot be deleted permanently yet:')}
                </p>
                <ul className="list-disc ps-5 text-xs text-amber-800 dark:text-amber-300 space-y-0.5">
                  {impact.blockingReasons.map((reason) => (
                    <li key={reason.code}>{reason.message}</li>
                  ))}
                </ul>
                <p className="text-xs text-amber-700 dark:text-amber-400 pt-1">
                  {t('superAdmin.agencies.deleteModal.archiveInstead',
                    'Archive the agency instead to safely block access while preserving its data.')}
                </p>
              </div>
            )}

            {impact.canDeleteImmediately && (
              <div className="space-y-3 pt-2">
                <div>
                  <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                    {t('superAdmin.agencies.deleteModal.typeNamePrompt', 'Type the exact agency name to confirm:')} <strong>{agency.name}</strong>
                  </label>
                  <input
                    type="text"
                    value={typedName}
                    onChange={(e) => setTypedName(e.target.value)}
                    className="w-full rounded-lg border border-slate-300 dark:border-white/10 bg-white dark:bg-white/5 px-3 py-2 text-sm text-slate-900 dark:text-white"
                    autoComplete="off"
                  />
                </div>
                <label className="flex items-start gap-2 text-xs text-slate-600 dark:text-slate-300">
                  <input
                    type="checkbox"
                    checked={acknowledged}
                    onChange={(e) => setAcknowledged(e.target.checked)}
                    className="mt-0.5"
                  />
                  {t('superAdmin.agencies.deleteModal.acknowledge', 'I understand that this action is permanent.')}
                </label>
              </div>
            )}
          </>
        )}

        {deleteError && (
          <div className="flex items-start gap-2 rounded-lg bg-rose-50 dark:bg-rose-500/10 p-3 text-sm text-rose-700 dark:text-rose-300">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            <span>{deleteError}</span>
          </div>
        )}

        <div className="flex items-center justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5"
          >
            {t('actions.cancel', 'Cancel')}
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={!canConfirm}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-bold text-white bg-rose-600 hover:bg-rose-700 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {deleting && <Loader2 size={14} className="animate-spin" />}
            {t('superAdmin.agencies.deleteModal.confirmButton', 'Delete permanently')}
          </button>
        </div>
      </div>
    </Modal>
  );
}

function ImpactRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex justify-between gap-2">
      <span className="text-slate-400">{label}</span>
      <span className="font-semibold text-slate-700 dark:text-slate-200 truncate">{value}</span>
    </div>
  );
}
