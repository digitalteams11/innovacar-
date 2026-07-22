import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Ban, CheckCircle2, Loader2, Plus, Play, Wrench } from 'lucide-react';
import api from '../api/axios';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { resolveApiErrorMessage } from '../i18n/apiError';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';

const emptyForm = {
  vehicleId: '',
  title: '',
  description: '',
  serviceProvider: '',
  scheduledDate: '',
  cost: '',
  mileage: '',
};

const extractArray = (payload: any): any[] => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.vehicles)) return payload.vehicles;
  return [];
};

const toIsoLocalDateTime = (value: string) => {
  if (!value) return '';
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return `${value}:00`;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value)) return value;

  const usMatch = value.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})\s+(\d{1,2}):(\d{2})\s*(AM|PM)$/i);
  if (usMatch) {
    const [, month, day, year, rawHour, minute, meridiem] = usMatch;
    let hour = Number(rawHour);
    if (meridiem.toUpperCase() === 'PM' && hour < 12) hour += 12;
    if (meridiem.toUpperCase() === 'AM' && hour === 12) hour = 0;
    return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T${String(hour).padStart(2, '0')}:${minute}:00`;
  }

  const parsed = new Date(value);
  if (!Number.isNaN(parsed.getTime())) {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}T${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:00`;
  }

  return value;
};

export default function Maintenance() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [rows, setRows] = useState<any[]>([]);
  const [vehicles, setVehicles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [repairing, setRepairing] = useState(false);
  const [updatingMaintenanceId, setUpdatingMaintenanceId] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [orphanCount, setOrphanCount] = useState(0);

  const stats = useMemo(() => {
    const scheduled  = rows.filter((r) => r.status === 'SCHEDULED').length;
    const inProgress = rows.filter((r) => r.status === 'IN_PROGRESS').length;
    const completed  = rows.filter((r) => r.status === 'COMPLETED').length;
    const cost       = rows.reduce((sum, r) => sum + Number(r.cost || 0), 0);
    return { scheduled, inProgress, completed, cost };
  }, [rows]);

  const load = async () => {
    setLoading(true);
    try {
      const [maintenance, fleet, orphan] = await Promise.all([
        api.get('/maintenance'),
        api.get('/vehicles'),
        api.get('/maintenance/orphan-count').catch(() => ({ data: { data: { count: 0 } } })),
      ]);

      const items = extractArray(maintenance.data);
      setRows(items);

      // All vehicles for the fleet dropdown (exclude only actively blocked ones)
      setVehicles(extractArray(fleet.data).filter((v: any) =>
        !['RENTED', 'OUT_OF_SERVICE'].includes(v.statut || v.status)));

      const orphans = orphan?.data?.data?.count ?? 0;
      setOrphanCount(orphans);

      console.debug('[MAINTENANCE_LIST_DEBUG]', {
        listEndpoint: '/maintenance',
        itemsCount: items.length,
        scheduled: items.filter((r: any) => r.status === 'SCHEDULED').length,
        inProgress: items.filter((r: any) => r.status === 'IN_PROGRESS').length,
        completed: items.filter((r: any) => r.status === 'COMPLETED').length,
        totalCost: items.reduce((s: number, r: any) => s + Number(r.cost || 0), 0),
        firstItem: items[0] ?? null,
        orphanVehicles: orphans,
        rawResponse: maintenance.data,
      });
    } catch (err: any) {
      showToast(resolveApiErrorMessage(err, t('maintenance.toast.loadFailed')), 'error');
      setRows([]);
      setVehicles([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const updateFormField = (field: keyof typeof emptyForm, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const validateForm = () => {
    const errors: Record<string, string> = {};
    if (!form.vehicleId) errors.vehicleId = t('maintenance.validation.vehicleRequired');
    if (!form.title.trim()) errors.title = t('maintenance.validation.titleRequired');
    if (!form.scheduledDate) errors.scheduledDate = t('maintenance.validation.scheduledDateRequired');
    if (form.cost && Number(form.cost) < 0) errors.cost = t('maintenance.validation.costInvalid');
    if (form.mileage && Number(form.mileage) < 0) errors.mileage = t('maintenance.validation.mileageInvalid');
    setFieldErrors(errors);
    return Object.values(errors)[0] || '';
  };

  const create = async () => {
    const validation = validateForm();
    if (validation) { showToast(validation, 'warning'); return; }
    setSaving(true);
    try {
      const vehicleId = Number(form.vehicleId);
      const scheduledDateIso = toIsoLocalDateTime(form.scheduledDate);
      const costNumber = form.cost ? Number(form.cost) : 0;
      const mileageNumber = form.mileage ? Number(form.mileage) : 0;
      const selectedVehicleObject = vehicles.find((vehicle: any) => Number(vehicle.id) === vehicleId) ?? null;
      const payload = {
        vehicleId,
        title: form.title.trim(),
        serviceProvider: form.serviceProvider.trim() || '',
        scheduledDate: scheduledDateIso,
        cost: costNumber,
        mileage: mileageNumber,
        description: form.description.trim() || '',
      };
      console.debug('[MAINTENANCE_FORM_DEBUG]', {
        selectedVehicleObject,
        vehicleId,
        scheduledDateInput: form.scheduledDate,
        scheduledDateIso,
        costNumber,
        mileageNumber,
        payload,
        endpoint: '/maintenance',
      });
      console.debug('[MAINTENANCE_FRONTEND_PAYLOAD]', payload);
      const response = await api.post('/maintenance', payload);
      setOpen(false);
      setForm(emptyForm);
      setFieldErrors({});
      await load();
      window.dispatchEvent(new Event('rentcar-data-updated'));
      const warnings: string[] = response.data?.warnings || [];
      if (warnings.includes('NOTIFICATION_CREATE_FAILED')) {
        showToast(t('maintenance.toast.createdWithNotificationWarning'), 'warning');
      } else {
        showToast(t('maintenance.toast.createdSuccess'), 'success');
      }
    } catch (err: any) {
      showToast(resolveApiErrorMessage(err, t('maintenance.toast.createFailed')), 'error');
    } finally {
      setSaving(false);
    }
  };

  const openCreate = () => {
    setForm(emptyForm);
    setFieldErrors({});
    setOpen(true);
  };

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

  const inputClass = (field: string) =>
    `mt-1 w-full px-3 py-2.5 border rounded-lg text-sm ${
      fieldErrors[field] ? 'border-danger-500 ring-2 ring-danger-500/20' : ''
    }`;

  const statusSuccessKey = (status: string) => {
    switch (status) {
      case 'IN_PROGRESS': return 'maintenance.toast.startedSuccess';
      case 'COMPLETED': return 'maintenance.toast.completedSuccess';
      case 'CANCELLED': return 'maintenance.toast.cancelledSuccess';
      default: return 'maintenance.toast.statusUpdatedSuccess';
    }
  };

  const changeStatus = async (id: number, status: string) => {
    // Guard against double-click/spam: only one PATCH per work order at a time.
    if (updatingMaintenanceId === id) return;
    setUpdatingMaintenanceId(id);
    try {
      console.debug('[MAINTENANCE_STATUS_PATCH_DEBUG]', { id, endpoint: `/maintenance/${id}/status`, payload: { status } });
      const response = await api.patch(`/maintenance/${id}/status`, { status });
      await load();
      window.dispatchEvent(new Event('rentcar-data-updated'));
      const warnings: string[] = response.data?.warnings || [];
      if (warnings.includes('NOTIFICATION_CREATE_FAILED')) {
        showToast(t('maintenance.toast.statusUpdatedWithNotificationWarning'), 'warning');
      } else {
        showToast(t(statusSuccessKey(status)), 'success');
      }
    } catch (err: any) {
      showToast(resolveApiErrorMessage(err, t('maintenance.toast.statusUpdateFailed')), 'error');
    } finally {
      setUpdatingMaintenanceId(null);
    }
  };

  const repairOrphans = async () => {
    setRepairing(true);
    try {
      await api.post('/maintenance/repair-missing-work-orders');
      await load();
      showToast(t('maintenance.toast.repairComplete'), 'success');
    } catch (err: any) {
      showToast(resolveApiErrorMessage(err, t('maintenance.toast.repairFailed')), 'error');
    } finally {
      setRepairing(false);
    }
  };

  return (
    <div className="space-y-5 p-3 sm:p-4 lg:p-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('maintenance.title')}</h1>
          <p className="text-sm text-slate-500">{t('maintenance.subtitle')}</p>
        </div>
        <button onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium">
          <Plus size={16} /> {t('maintenance.newWorkOrder')}
        </button>
      </div>

      {/* Repair banner — shown when vehicles are in MAINTENANCE status without a work order */}
      {orphanCount > 0 && (
        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-500" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-amber-800">
              {t('maintenance.repairBanner', { count: orphanCount })}
            </p>
            <p className="mt-0.5 text-xs text-amber-700">
              {t('maintenance.repairBannerDescription')}
            </p>
          </div>
          <button
            onClick={repairOrphans}
            disabled={repairing}
            className="shrink-0 flex items-center gap-1.5 rounded-md bg-amber-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-600 disabled:opacity-60"
          >
            {repairing ? <Loader2 size={13} className="animate-spin" /> : <Wrench size={13} />}
            {t('maintenance.repairNow')}
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard label={t('maintenance.stats.scheduled')} value={stats.scheduled} />
        <SummaryCard label={t('maintenance.stats.inProgress')} value={stats.inProgress} />
        <SummaryCard label={t('maintenance.stats.completed')} value={stats.completed} />
        <SummaryCard label={t('maintenance.stats.costSummary')} value={`${stats.cost.toLocaleString()} MAD`} />
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-[#e8e6e1] bg-white p-4">
          <p className="text-sm font-bold text-[#1e293b]">{t('maintenance.vehicleHealth.title')}</p>
          <p className="mt-1 text-sm text-slate-500">
            {stats.inProgress > 0
              ? t('maintenance.vehicleHealth.blocked', { count: stats.inProgress })
              : t('maintenance.vehicleHealth.none')}
          </p>
        </div>
        <div className="rounded-lg border border-[#e8e6e1] bg-white p-4">
          <p className="text-sm font-bold text-[#1e293b]">{t('maintenance.serviceAlerts.title')}</p>
          <p className="mt-1 text-sm text-slate-500">
            {stats.scheduled > 0
              ? t('maintenance.serviceAlerts.needsAttention', { count: stats.scheduled })
              : t('maintenance.serviceAlerts.none')}
          </p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16"><Loader2 className="animate-spin text-brand-500" size={28} /></div>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border border-[#e8e6e1] bg-white py-16 text-center">
          <p className="text-sm font-semibold text-slate-600">{t('maintenance.empty.title')}</p>
          <p className="mt-1 text-sm text-slate-400">{t('maintenance.empty.subtitle')}</p>
        </div>
      ) : (
        <ResponsiveDataView
          mobile={
            <div className="space-y-3">
              {rows.map((row) => (
                <div key={row.id} className="rounded-lg border border-[#e8e6e1] bg-white p-4 space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-[#1e293b]">{row.vehicle}</p>
                      <p className="text-xs text-slate-400">{row.plate}</p>
                    </div>
                    <span className={`shrink-0 rounded px-2 py-1 text-xs font-bold ${statusClass(row.status)}`}>
                      {String(t(`maintenance.statusLabel.${row.status}`, { defaultValue: row.status?.replace('_', ' ') }))}
                    </span>
                  </div>
                  <div className="text-sm text-[#1e293b]">
                    {row.title}
                    {row.description && <p className="mt-0.5 text-xs text-slate-400">{row.description}</p>}
                  </div>
                  <div className="grid grid-cols-2 gap-2 border-t border-[#e8e6e1] pt-3 text-xs text-slate-500">
                    <span>{row.serviceProvider || '-'}</span>
                    <span className="text-right font-semibold text-[#1e293b]">{Number(row.cost || 0).toLocaleString()} MAD</span>
                    <span>{(row.scheduledDate || row.scheduledAt) ? new Date(row.scheduledDate || row.scheduledAt).toLocaleDateString() : '-'}</span>
                    <span className="text-right">{row.mileage ?? '-'}</span>
                  </div>
                  {(row.status === 'SCHEDULED' || row.status === 'IN_PROGRESS') && (
                    <div className="flex items-center gap-2 border-t border-[#e8e6e1] pt-3">
                      {row.status === 'SCHEDULED' && (
                        <button title={t('maintenance.actions.start')} disabled={updatingMaintenanceId === row.id}
                          onClick={() => changeStatus(row.id, 'IN_PROGRESS')}
                          className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-brand-50 text-sm font-semibold text-brand-600 disabled:opacity-50">
                          {updatingMaintenanceId === row.id ? <Loader2 size={15} className="animate-spin" /> : <Play size={15} />} {t('maintenance.actions.start')}
                        </button>
                      )}
                      {row.status === 'IN_PROGRESS' && (
                        <button title={t('maintenance.actions.complete')} disabled={updatingMaintenanceId === row.id}
                          onClick={() => changeStatus(row.id, 'COMPLETED')}
                          className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-success-50 text-sm font-semibold text-success-600 disabled:opacity-50">
                          {updatingMaintenanceId === row.id ? <Loader2 size={15} className="animate-spin" /> : <CheckCircle2 size={15} />} {t('maintenance.actions.complete')}
                        </button>
                      )}
                      <button title={t('maintenance.actions.cancel')} disabled={updatingMaintenanceId === row.id}
                        onClick={() => changeStatus(row.id, 'CANCELLED')}
                        className="flex min-h-11 min-w-11 items-center justify-center rounded-lg bg-danger-50 text-red-500 disabled:opacity-50">
                        {updatingMaintenanceId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Ban size={16} />}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          }
          desktop={
      <div className="border border-[#e8e6e1] rounded-lg bg-white overflow-x-auto">
        <table className="w-full min-w-[860px] text-left">
          <thead className="bg-[#f5f5f0] text-[11px] uppercase text-slate-500">
            <tr>
              <th className="p-3">{t('maintenance.table.vehicle')}</th>
              <th>{t('maintenance.table.work')}</th>
              <th>{t('maintenance.table.provider')}</th>
              <th>{t('maintenance.table.schedule')}</th>
              <th>{t('maintenance.table.cost')}</th>
              <th>{t('maintenance.table.mileage')}</th>
              <th>{t('maintenance.table.status')}</th>
              <th className="pe-3 text-right">{t('maintenance.table.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e8e6e1]">
            {rows.map((row) => (
              <tr key={row.id} className="text-sm">
                <td className="p-3 font-medium">
                  {row.vehicle}
                  <div className="text-xs text-slate-400">{row.plate}</div>
                </td>
                <td>
                  {row.title}
                  <div className="text-xs text-slate-400 max-w-56 truncate">{row.description}</div>
                </td>
                <td>{row.serviceProvider || '-'}</td>
                <td>
                  {(row.scheduledDate || row.scheduledAt)
                    ? new Date(row.scheduledDate || row.scheduledAt).toLocaleString()
                    : '-'}
                </td>
                <td>{Number(row.cost || 0).toLocaleString()} MAD</td>
                <td>{row.mileage ?? '-'}</td>
                <td>
                  <span className={`px-2 py-1 rounded text-xs font-bold ${statusClass(row.status)}`}>
                    {String(t(`maintenance.statusLabel.${row.status}`, { defaultValue: row.status?.replace('_', ' ') }))}
                  </span>
                </td>
                <td className="pe-3">
                  <div className="flex justify-end gap-1">
                    {row.status === 'SCHEDULED' && (
                      <button title={t('maintenance.actions.start')} disabled={updatingMaintenanceId === row.id}
                        onClick={() => changeStatus(row.id, 'IN_PROGRESS')}
                        className="p-2 text-brand-500 disabled:opacity-50">
                        {updatingMaintenanceId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Play size={16} />}
                      </button>
                    )}
                    {row.status === 'IN_PROGRESS' && (
                      <button title={t('maintenance.actions.complete')} disabled={updatingMaintenanceId === row.id}
                        onClick={() => changeStatus(row.id, 'COMPLETED')}
                        className="p-2 text-emerald-600 disabled:opacity-50">
                        {updatingMaintenanceId === row.id ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                      </button>
                    )}
                    {['SCHEDULED', 'IN_PROGRESS'].includes(row.status) && (
                      <button title={t('maintenance.actions.cancel')} disabled={updatingMaintenanceId === row.id}
                        onClick={() => changeStatus(row.id, 'CANCELLED')}
                        className="p-2 text-red-500 disabled:opacity-50">
                        {updatingMaintenanceId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Ban size={16} />}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
          }
        />
      )}

      <Modal isOpen={open} onClose={() => !saving && setOpen(false)} title={t('maintenance.modal.title')}>
        <div className="space-y-4">
          <label className="block">
            <span className="text-xs font-medium text-slate-500">{t('maintenance.modal.vehicle')}</span>
            <select value={form.vehicleId} onChange={(e) => updateFormField('vehicleId', e.target.value)}
              aria-invalid={Boolean(fieldErrors.vehicleId)}
              className={inputClass('vehicleId')}>
              <option value="">{t('maintenance.modal.selectVehicle')}</option>
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.marque || v.brand} - {v.plate || v.plateNumber}
                  {(v.statut || v.status) === 'MAINTENANCE' ? t('maintenance.modal.inMaintenanceSuffix') : ''}
                </option>
              ))}
            </select>
            {fieldError('vehicleId')}
          </label>
          <Field label={t('maintenance.modal.titleField')} value={form.title} onChange={(title) => updateFormField('title', title)} error={fieldErrors.title} />
          <Field label={t('maintenance.modal.serviceProvider')} value={form.serviceProvider} onChange={(serviceProvider) => updateFormField('serviceProvider', serviceProvider)} />
          <Field label={t('maintenance.modal.scheduledDate')} type="datetime-local" value={form.scheduledDate} onChange={(scheduledDate) => updateFormField('scheduledDate', scheduledDate)} error={fieldErrors.scheduledDate} />
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('maintenance.modal.cost')} type="number" value={form.cost} onChange={(cost) => updateFormField('cost', cost)} error={fieldErrors.cost} />
            <Field label={t('maintenance.modal.mileage')} type="number" value={form.mileage} onChange={(mileage) => updateFormField('mileage', mileage)} error={fieldErrors.mileage} />
          </div>
          <label className="block">
            <span className="text-xs font-medium text-slate-500">{t('maintenance.modal.description')}</span>
            <textarea value={form.description} onChange={(e) => updateFormField('description', e.target.value)}
              className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm" rows={3} />
          </label>
          <button onClick={create} disabled={saving}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium disabled:opacity-70">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <Wrench size={16} />} {t('maintenance.modal.create')}
          </button>
        </div>
      </Modal>
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-[#e8e6e1] bg-white p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-2 text-2xl font-bold text-[#1e293b]">{value}</p>
    </div>
  );
}

function statusClass(status: string) {
  if (status === 'IN_PROGRESS') return 'bg-orange-100 text-orange-700';
  if (status === 'COMPLETED')   return 'bg-emerald-100 text-emerald-700';
  if (status === 'CANCELLED')   return 'bg-red-100 text-red-700';
  return 'bg-slate-100 text-slate-700';
}

function Field({ label, value, onChange, type = 'text', error = '' }: {
  label: string; value: string; onChange: (value: string) => void; type?: string; error?: string;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-500">{label}</span>
      <input type={type} value={value} min={type === 'number' ? '0' : undefined}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={Boolean(error)}
        className={`mt-1 w-full px-3 py-2.5 border rounded-lg text-sm ${error ? 'border-danger-500 ring-2 ring-danger-500/20' : ''}`} />
      {error && <p className="mt-1 text-xs font-medium text-danger-500">{error}</p>}
    </label>
  );
}
