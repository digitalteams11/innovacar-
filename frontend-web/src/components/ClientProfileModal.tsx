import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle, Building2, Calendar, Car, Copy, CreditCard, Eye, EyeOff,
  FileSignature, FileText, Globe, Landmark, Loader2, Mail, MapPin,
  MessageCircle, Pencil, Phone, ReceiptText, ShieldCheck, User, Users,
} from 'lucide-react';
import api from '../api/axios';
import Modal from './Modal';
import InspectionGallery from './InspectionGallery';
import { usePermissions } from '../context/PermissionContext';

interface Client {
  id: number;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
}

interface ClientProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  client: Client | null;
  onEdit?: (client: Client) => void;
}

type Tab = 'personal' | 'identity' | 'contact' | 'reservations' | 'contracts' | 'payments' | 'inspections' | 'deposits' | 'documents';

/** CIN -> Cin, RESIDENCE_PERMIT -> ResidencePermit — matches i18n key suffixes in clients.form.documentType*. */
function toPascalCase(value: string) {
  return value.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('');
}

export default function ClientProfileModal({ isOpen, onClose, client, onEdit }: ClientProfileModalProps) {
  const { t } = useTranslation();
  const { hasPermission } = usePermissions();
  const canViewFullDocument = hasPermission('VIEW_CLIENT_DOCUMENTS_FULL');

  const [tab, setTab] = useState<Tab>('personal');
  const [revealDocument, setRevealDocument] = useState(false);

  const [details, setDetails] = useState<any>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState('');

  const [profileDocs, setProfileDocs] = useState<any>(null);
  const [profileDocsError, setProfileDocsError] = useState('');

  const [inspections, setInspections] = useState<any[]>([]);
  const [inspectionsError, setInspectionsError] = useState('');

  const [deposits, setDeposits] = useState<any>(null);
  const [depositsError, setDepositsError] = useState('');

  const mapDossierError = (err: any): string => {
    const status = err?.response?.status;
    if (!err?.response) return t('clients.details.errorNetwork', 'Connection temporarily unavailable.');
    if (status === 401) return t('clients.details.errorSessionExpired', 'Your session has expired. Please sign in again.');
    if (status === 403) return t('clients.details.errorForbidden', 'You do not have permission to view these client details.');
    if (status === 404) return t('clients.details.errorNotFound', 'Client not found.');
    if (status >= 500) return t('clients.details.errorServer', 'Unable to load client details.');
    return err?.userMessage || t('clients.details.sectionLoadError', 'Unable to load this section.');
  };

  const loadDetails = (clientId: number, signal: AbortSignal) => {
    setDetailsLoading(true);
    setDetailsError('');
    api.get(`/clients/${clientId}/details`, { signal })
      .then(({ data }) => setDetails(data))
      .catch((err) => { if (!signal.aborted) setDetailsError(mapDossierError(err)); })
      .finally(() => { if (!signal.aborted) setDetailsLoading(false); });
  };

  const loadProfileDocs = (clientId: number, signal: AbortSignal) => {
    setProfileDocsError('');
    api.get(`/clients/${clientId}/profile`, { signal })
      .then(({ data }) => setProfileDocs(data))
      .catch((err) => { if (!signal.aborted) setProfileDocsError(mapDossierError(err)); });
  };

  const loadInspections = (clientId: number, signal: AbortSignal) => {
    setInspectionsError('');
    api.get(`/clients/${clientId}/inspections`, { signal })
      .then(({ data }) => setInspections(Array.isArray(data) ? data : []))
      .catch((err) => { if (!signal.aborted) setInspectionsError(mapDossierError(err)); });
  };

  const loadDeposits = (clientId: number, signal: AbortSignal) => {
    setDepositsError('');
    api.get(`/deposits/client/${clientId}/summary`, { signal })
      .then(({ data }) => setDeposits(data))
      .catch((err) => { if (!signal.aborted) setDepositsError(mapDossierError(err)); });
  };

  useEffect(() => {
    // Reset every section's state up front so a client switch (or a modal
    // close before the previous fetch resolved) never leaves the previous
    // client's data visible under the new client's id.
    setDetails(null);
    setProfileDocs(null);
    setInspections([]);
    setDeposits(null);
    setDetailsError('');
    setProfileDocsError('');
    setInspectionsError('');
    setDepositsError('');
    setTab('personal');
    setRevealDocument(false);

    if (!isOpen || !client?.id) return;
    const clientId = client.id;
    const controller = new AbortController();

    loadDetails(clientId, controller.signal);
    loadProfileDocs(clientId, controller.signal);
    loadInspections(clientId, controller.signal);
    loadDeposits(clientId, controller.signal);

    // Cancel in-flight requests when the modal closes or the selected
    // client changes, so a slow response for client A can never land after
    // client B's view is already showing (React Query's "cancel on unmount /
    // don't reuse another client's cached details" behavior, implemented
    // manually since this codebase doesn't use React Query).
    return () => controller.abort();
  }, [isOpen, client?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const retryDetails = () => client?.id && loadDetails(client.id, new AbortController().signal);
  const retryProfileDocs = () => client?.id && loadProfileDocs(client.id, new AbortController().signal);
  const retryInspections = () => client?.id && loadInspections(client.id, new AbortController().signal);
  const retryDeposits = () => client?.id && loadDeposits(client.id, new AbortController().signal);

  if (!client) return null;

  const c = details?.client || {};
  const summary = details?.summary || {};
  const warnings: string[] = c.warnings || [];

  const documentStatus = warnings.includes('DOCUMENT_EXPIRED') ? 'expired'
    : warnings.includes('DOCUMENT_EXPIRING_SOON') ? 'expiringSoon'
    : (c.documentMasked || c.documentNumber) ? 'valid' : 'missing';

  const licenseStatus = warnings.includes('LICENSE_EXPIRED') ? 'expired'
    : warnings.includes('LICENSE_EXPIRING_SOON') ? 'expiringSoon'
    : c.drivingLicense ? 'valid' : 'missing';

  const documentValue = revealDocument && c.documentNumber ? c.documentNumber : (c.documentMasked || c.documentNumber);

  const TABS: [Tab, string][] = [
    ['personal', t('clients.details.tabPersonal', 'Personal info')],
    ['identity', t('clients.details.tabIdentity', 'Identity & license')],
    ['contact', t('clients.details.tabContact', 'Contact')],
    ['reservations', t('clients.details.tabReservations', 'Reservations')],
    ['contracts', t('clients.details.tabContracts', 'Contracts')],
    ['payments', t('clients.details.tabPayments', 'Payments')],
    ['inspections', t('clients.details.tabInspections', 'Inspections')],
    ['deposits', t('clients.details.tabDeposits', 'Deposits')],
    ['documents', t('clients.details.tabDocuments', 'Documents & signatures')],
  ];

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('clients.details.title', 'Client Details')} maxWidth="4xl">
      <div className="space-y-5 max-h-[78vh] overflow-y-auto pe-1">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 pb-5" style={{ borderBottom: '1px solid var(--border-subtle)' }}>
          <div className="w-16 h-16 rounded-xl flex items-center justify-center text-xl font-bold shrink-0"
            style={{ background: 'var(--brand-50, rgba(16,185,129,0.12))', color: 'var(--brand-600, #059669)' }}>
            {client.name.split(' ').map((part) => part[0]).join('').slice(0, 2).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-xl font-bold truncate" style={{ color: 'var(--text-primary)' }}>{client.name}</h2>
            <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
              {client.email && <span className="flex items-center gap-1"><Mail size={13} />{client.email}</span>}
              {client.phone && <span className="flex items-center gap-1"><Phone size={13} />{client.phone}</span>}
              {client.address && <span className="flex items-center gap-1"><MapPin size={13} />{client.address}</span>}
            </div>
            {warnings.length > 0 && (
              <div className="flex flex-wrap gap-2 mt-2">
                {warnings.map((w) => (
                  <span key={w} className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold"
                    style={{ background: 'var(--danger-50, #fef2f2)', color: 'var(--danger-600, #dc2626)' }}>
                    <AlertTriangle size={11} />
                    {t(`clients.warnings.${w === 'DOCUMENT_EXPIRED' ? 'documentExpired'
                      : w === 'DOCUMENT_EXPIRING_SOON' ? 'documentExpiringSoon'
                      : w === 'LICENSE_EXPIRED' ? 'licenseExpired' : 'licenseExpiringSoon'}`)}
                  </span>
                ))}
              </div>
            )}
          </div>
          {onEdit && (
            <button
              type="button"
              onClick={() => onEdit(client)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold shrink-0 self-start sm:self-center"
              style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}
            >
              <Pencil size={13} />
              {t('clients.editClient', 'Modifier le client')}
            </button>
          )}
        </div>

        {/* Summary metrics */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <Metric label={t('clients.details.metricReservations', 'Reservations')} value={summary.totalReservations ?? 0} icon={<Car size={15} />} />
          <Metric label={t('clients.details.metricContracts', 'Contracts')} value={summary.totalContracts ?? 0} icon={<FileSignature size={15} />} />
          <Metric label={t('clients.details.metricTotalPaid', 'Total Paid')} value={`${summary.totalPaid ?? 0} MAD`} icon={<CreditCard size={15} />} />
          <Metric label={t('clients.details.metricOutstanding', 'Outstanding')} value={`${summary.outstandingBalance ?? 0} MAD`} icon={<ReceiptText size={15} />} />
          <Metric label={t('clients.details.metricActiveReservations', 'Active reservations')} value={summary.activeReservations ?? 0} icon={<Car size={15} />} />
          <Metric label={t('clients.details.metricActiveContracts', 'Active contracts')} value={summary.activeContracts ?? 0} icon={<FileSignature size={15} />} />
          <Metric label={t('clients.details.metricActiveDeposits', 'Active deposits')} value={`${summary.activeDeposits ?? 0} MAD`} icon={<Landmark size={15} />} />
          <Metric label={t('clients.details.metricLastRental', 'Last rental')} value={summary.lastRentalDate || t('common.notProvided', 'Non renseigné')} icon={<Calendar size={15} />} />
        </div>

        {/* Tabs */}
        <div className="flex gap-1 p-1 rounded-lg overflow-x-auto" style={{ background: 'var(--bg-hover)' }}>
          {TABS.map(([key, label]) => (
            <button key={key} onClick={() => setTab(key)}
              className="px-3 py-2 rounded-md text-xs font-medium whitespace-nowrap"
              style={tab === key
                ? { background: 'var(--bg-card-solid)', color: 'var(--brand-600, #059669)', boxShadow: 'var(--shadow-sm, 0 1px 2px rgba(0,0,0,0.06))' }
                : { color: 'var(--text-muted)' }}>
              {label}
            </button>
          ))}
        </div>

        {/* Section-level loading/error for detail-backed tabs */}
        {['personal', 'identity', 'contact', 'reservations', 'contracts', 'payments'].includes(tab) && detailsLoading && (
          <div className="flex items-center justify-center py-16" style={{ color: 'var(--brand-500, #10b981)' }}>
            <Loader2 size={26} className="animate-spin" />
          </div>
        )}
        {['personal', 'identity', 'contact', 'reservations', 'contracts', 'payments'].includes(tab) && !detailsLoading && detailsError && (
          <SectionError message={detailsError} onRetry={retryDetails} />
        )}
        {!detailsLoading && !detailsError && (
          <>
            {tab === 'personal' && <PersonalTab client={c} />}
            {tab === 'identity' && (
              <IdentityTab
                client={c}
                documentStatus={documentStatus}
                licenseStatus={licenseStatus}
                documentValue={documentValue}
                canReveal={canViewFullDocument && Boolean(c.documentNumber)}
                revealed={revealDocument}
                onToggleReveal={() => setRevealDocument((v) => !v)}
              />
            )}
            {tab === 'contact' && <ContactTab client={c} />}
            {tab === 'reservations' && (
              <HistoryList
                empty={t('clients.details.noReservations', 'No reservations')}
                rows={(details?.reservationHistory || []).map((item: any) => ({
                  id: item.id,
                  title: item.vehicleMarque || `${t('clients.details.reservationLabel', 'Reservation')} #${item.id}`,
                  detail: `${item.dateStart} ${item.startTime || ''} ${t('clients.details.to', 'to')} ${item.dateEnd} ${item.endTime || ''}`,
                  status: item.status,
                }))}
              />
            )}
            {tab === 'contracts' && (
              <HistoryList
                empty={t('clients.details.noContracts', 'No contracts')}
                rows={(details?.contractHistory || []).map((item: any) => ({
                  id: item.id,
                  title: item.contractNumber,
                  detail: `${item.vehicleBrand || ''} ${item.vehicleModel || ''} - ${item.totalPrice || 0} MAD`,
                  status: item.status,
                }))}
              />
            )}
            {tab === 'payments' && (
              <HistoryList
                empty={t('clients.details.noPayments', 'No payments')}
                rows={(details?.paymentHistory || []).map((item: any) => ({
                  id: item.id,
                  title: item.paymentNumber || `${t('clients.details.paymentLabel', 'Payment')} #${item.id}`,
                  detail: `${item.amount || 0} MAD - ${item.paymentMethod || 'Other'} - ${item.paymentDate || ''}`,
                  status: item.status,
                }))}
              />
            )}
          </>
        )}

        {tab === 'inspections' && (
          inspectionsError
            ? <SectionError message={inspectionsError} onRetry={retryInspections} />
            : <InspectionGallery inspections={inspections} />
        )}

        {tab === 'deposits' && (
          depositsError
            ? <SectionError message={depositsError} onRetry={retryDeposits} />
            : !deposits
              ? <div className="flex items-center justify-center py-16" style={{ color: 'var(--brand-500, #10b981)' }}><Loader2 size={26} className="animate-spin" /></div>
              : <DepositsTab deposits={deposits} />
        )}

        {tab === 'documents' && (
          profileDocsError
            ? <SectionError message={profileDocsError} onRetry={retryProfileDocs} />
            : !profileDocs
              ? <div className="flex items-center justify-center py-16" style={{ color: 'var(--brand-500, #10b981)' }}><Loader2 size={26} className="animate-spin" /></div>
              : (
                <div className="space-y-4">
                  <HistoryList
                    empty={t('clients.details.noDocuments', 'No documents')}
                    rows={(profileDocs.documents || []).map((item: any) => ({
                      id: item.id,
                      title: item.name || item.type,
                      detail: `${t('clients.details.contractLabel', 'Contract')} #${item.contractId}`,
                      status: item.type,
                    }))}
                  />
                  <HistoryList
                    empty={t('clients.details.noSignatures', 'No signature history')}
                    rows={(profileDocs.signatureHistory || []).map((item: any) => ({
                      id: item.contractId,
                      title: item.contractNumber,
                      detail: `${t('clients.details.agency', 'Agency')}: ${item.agencySignedAt || t('clients.details.pending', 'Pending')} | ${t('clients.details.client', 'Client')}: ${item.clientSignedAt || t('clients.details.pending', 'Pending')}`,
                      status: item.status,
                    }))}
                  />
                </div>
              )
        )}
      </div>
    </Modal>
  );
}

function Metric({ label, value, icon }: { label: string; value: string | number; icon: React.ReactNode }) {
  return (
    <div className="p-3 rounded-lg" style={{ border: '1px solid var(--border-subtle)', background: 'var(--bg-card-solid)' }}>
      <div className="flex items-center gap-1.5" style={{ color: 'var(--text-muted)' }}>{icon}<span className="text-[10px] uppercase font-bold">{label}</span></div>
      <p className="mt-2 text-base font-bold" style={{ color: 'var(--text-primary)' }}>{value}</p>
    </div>
  );
}

function SectionError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="py-10 text-center text-sm flex flex-col items-center gap-3" style={{ color: 'var(--danger-500, #ef4444)' }}>
      <AlertTriangle size={20} />
      <span>{message}</span>
      {onRetry && (
        <button type="button" onClick={onRetry}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold"
          style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}>
          {t('common.retry', 'Retry')}
        </button>
      )}
    </div>
  );
}

function EmptyValue() {
  const { t } = useTranslation();
  return <span style={{ color: 'var(--text-muted)' }}>{t('common.notProvided', 'Non renseigné')}</span>;
}

function Field({ icon, label, value }: { icon?: React.ReactNode; label: string; value?: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2 py-2">
      {icon && <span className="mt-0.5 shrink-0" style={{ color: 'var(--text-muted)' }}>{icon}</span>}
      <div className="min-w-0">
        <p className="text-[10px] uppercase font-bold" style={{ color: 'var(--text-muted)' }}>{label}</p>
        <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>{value || <EmptyValue />}</p>
      </div>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg p-4" style={{ border: '1px solid var(--border-subtle)', background: 'var(--bg-card-solid)' }}>
      <h4 className="text-xs font-bold uppercase tracking-wide mb-2" style={{ color: 'var(--text-muted)' }}>{title}</h4>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4">{children}</div>
    </div>
  );
}

function PersonalTab({ client }: { client: any }) {
  const { t } = useTranslation();
  return (
    <Card title={t('clients.form.sectionPersonal', 'Personal information')}>
      <Field icon={<User size={14} />} label={t('clients.form.fullName', 'Full name')} value={client.fullName} />
      <Field label={t('clients.details.firstName', 'First name')} value={client.firstName} />
      <Field label={t('clients.details.lastName', 'Last name')} value={client.lastName} />
      <Field icon={<Calendar size={14} />} label={t('clients.form.birthDate', 'Date of birth')} value={client.birthDate} />
      <Field label={t('clients.form.gender', 'Gender')} value={client.gender ? String(t(`clients.gender.${client.gender.toLowerCase()}`, client.gender)) : undefined} />
      <Field icon={<Globe size={14} />} label={t('clients.form.nationality', 'Nationality')} value={client.nationality} />
      <Field icon={<Building2 size={14} />} label={t('clients.form.companyName', 'Company')} value={client.companyName} />
    </Card>
  );
}

function StatusBadge({ status }: { status: 'valid' | 'expiringSoon' | 'expired' | 'missing' }) {
  const { t } = useTranslation();
  const styles: Record<string, { bg: string; fg: string }> = {
    valid: { bg: 'var(--success-50, #ecfdf5)', fg: 'var(--success-600, #059669)' },
    expiringSoon: { bg: 'var(--warning-50, #fffbeb)', fg: 'var(--warning-600, #d97706)' },
    expired: { bg: 'var(--danger-50, #fef2f2)', fg: 'var(--danger-600, #dc2626)' },
    missing: { bg: 'var(--bg-hover)', fg: 'var(--text-muted)' },
  };
  const s = styles[status];
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold" style={{ background: s.bg, color: s.fg }}>
      <ShieldCheck size={11} />
      {t(`clients.details.status.${status}`)}
    </span>
  );
}

function IdentityTab({ client, documentStatus, licenseStatus, documentValue, canReveal, revealed, onToggleReveal }: {
  client: any; documentStatus: 'valid' | 'expiringSoon' | 'expired' | 'missing'; licenseStatus: 'valid' | 'expiringSoon' | 'expired' | 'missing';
  documentValue?: string; canReveal: boolean; revealed: boolean; onToggleReveal: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="space-y-4">
      <div className="rounded-lg p-4" style={{ border: '1px solid var(--border-subtle)', background: 'var(--bg-card-solid)' }}>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>{t('clients.details.identityCard', 'Identity')}</h4>
          <StatusBadge status={documentStatus} />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <Field label={t('clients.form.documentType', 'Document type')} value={client.documentType ? String(t(`clients.form.documentType${toPascalCase(client.documentType)}`, client.documentType)) : undefined} />
          <div className="flex items-start gap-2 py-2">
            <div className="min-w-0 flex-1">
              <p className="text-[10px] uppercase font-bold" style={{ color: 'var(--text-muted)' }}>{t('clients.form.documentNumber', 'Document number')}</p>
              <div className="flex items-center gap-2">
                <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>{documentValue || <EmptyValue />}</p>
                {canReveal && (
                  <button type="button" onClick={onToggleReveal} className="shrink-0" style={{ color: 'var(--text-muted)' }} aria-label={revealed ? t('clients.details.hideDocument', 'Hide') : t('clients.details.revealDocument', 'Reveal')}>
                    {revealed ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                )}
              </div>
            </div>
          </div>
          <Field label={t('clients.form.documentIssueDate', 'Issue date')} value={client.documentIssueDate} />
          <Field label={t('clients.form.documentExpiryDate', 'Expiry date')} value={client.documentExpiryDate} />
          <Field label={t('clients.form.issuingCountry', 'Issuing country')} value={client.documentIssuingCountry} />
          <Field label={t('clients.form.issuingAuthority', 'Issuing authority')} value={client.documentIssuingAuthority} />
        </div>
      </div>

      <div className="rounded-lg p-4" style={{ border: '1px solid var(--border-subtle)', background: 'var(--bg-card-solid)' }}>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-xs font-bold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>{t('clients.details.licenseCard', 'Driving license')}</h4>
          <StatusBadge status={licenseStatus} />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <Field label={t('clients.form.drivingLicense', 'License number')} value={client.drivingLicense} />
          <Field label={t('clients.form.drivingLicenseCategory', 'Category')} value={client.drivingLicenseCategory} />
          <Field label={t('clients.form.issuingCountry', 'Issuing country')} value={client.drivingLicenseCountry} />
          <Field label={t('clients.form.documentIssueDate', 'Issue date')} value={client.drivingLicenseIssue} />
          <Field label={t('clients.form.documentExpiryDate', 'Expiry date')} value={client.drivingLicenseExpiry} />
        </div>
      </div>
    </div>
  );
}

function ContactAction({ href, icon, label }: { href: string; icon: React.ReactNode; label: string }) {
  return (
    <a href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer"
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold"
      style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}>
      {icon}{label}
    </a>
  );
}

function ContactTab({ client }: { client: any }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const phoneDigits = (client.phone || '').replace(/[^\d+]/g, '');

  const copyContact = () => {
    const text = [client.fullName, client.phone, client.email].filter(Boolean).join(' • ');
    navigator.clipboard?.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {client.phone && <ContactAction href={`tel:${phoneDigits}`} icon={<Phone size={13} />} label={t('clients.details.call', 'Call')} />}
        {client.phone && <ContactAction href={`https://wa.me/${phoneDigits.replace('+', '')}`} icon={<MessageCircle size={13} />} label="WhatsApp" />}
        {client.email && <ContactAction href={`mailto:${client.email}`} icon={<Mail size={13} />} label={t('clients.details.sendEmail', 'Email')} />}
        <button type="button" onClick={copyContact}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold"
          style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}>
          <Copy size={13} />{copied ? t('clients.details.copied', 'Copied') : t('clients.details.copy', 'Copy')}
        </button>
      </div>

      <Card title={t('clients.form.sectionAddress', 'Contact & address')}>
        <Field icon={<Phone size={14} />} label={t('clients.form.phone', 'Phone')} value={client.phone} />
        <Field icon={<Phone size={14} />} label={t('clients.form.secondaryPhone', 'Secondary phone')} value={client.secondaryPhone} />
        <Field icon={<Mail size={14} />} label={t('clients.form.email', 'Email')} value={client.email} />
        <Field icon={<MapPin size={14} />} label={t('clients.form.address', 'Address')} value={client.address} />
        <Field label={t('clients.form.city', 'City')} value={client.city} />
        <Field label={t('clients.form.country', 'Country')} value={client.country} />
      </Card>

      <Card title={t('clients.details.emergencyContact', 'Emergency contact')}>
        <Field icon={<Users size={14} />} label={t('clients.details.emergencyContactName', 'Contact name')} value={client.emergencyContactName} />
        <Field icon={<Phone size={14} />} label={t('clients.details.emergencyContactPhone', 'Contact phone')} value={client.emergencyContactPhone} />
      </Card>
    </div>
  );
}

function DepositsTab({ deposits }: { deposits: any }) {
  const { t } = useTranslation();
  const history = deposits.history || [];
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <Metric label={t('clients.details.metricActiveDeposits', 'Active deposits')} value={`${deposits.activeDeposits ?? 0} MAD`} icon={<Landmark size={15} />} />
        <Metric label={t('clients.details.totalDeposits', 'Total deposits')} value={`${deposits.totalDeposits ?? 0} MAD`} icon={<Landmark size={15} />} />
        <Metric label={t('clients.details.returnedDeposits', 'Returned')} value={`${deposits.returnedDeposits ?? 0} MAD`} icon={<Landmark size={15} />} />
        <Metric label={t('clients.details.pendingDeposits', 'Pending')} value={deposits.pendingCount ?? 0} icon={<Landmark size={15} />} />
      </div>
      <HistoryList
        empty={t('clients.details.noDeposits', 'No deposits')}
        rows={history.map((item: any) => ({
          id: item.id,
          title: `${item.depositType || t('clients.details.depositLabel', 'Deposit')} - ${item.amount || 0} ${item.currency || 'MAD'}`,
          detail: `${t('clients.details.contractLabel', 'Contract')} ${item.contractNumber || '-'}`,
          status: String(t(`contractDetails.depositStatusValues.${item.status}`, item.status)),
        }))}
      />
    </div>
  );
}

function HistoryList({ rows, empty }: {
  rows: { id: number; title: string; detail: string; status: string }[];
  empty: string;
}) {
  if (!rows.length) return <div className="py-10 text-center text-sm" style={{ color: 'var(--text-muted)' }}>{empty}</div>;
  return (
    <div className="divide-y rounded-lg" style={{ borderColor: 'var(--border-subtle)', border: '1px solid var(--border-subtle)' }}>
      {rows.map((row) => (
        <div key={row.id} className="flex items-center gap-3 p-3">
          <FileText size={16} className="shrink-0" style={{ color: 'var(--text-muted)' }} />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>{row.title}</p>
            <p className="text-xs truncate" style={{ color: 'var(--text-muted)' }}>{row.detail}</p>
          </div>
          <span className="px-2 py-1 rounded text-[10px] font-bold" style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}>{row.status}</span>
        </div>
      ))}
    </div>
  );
}
