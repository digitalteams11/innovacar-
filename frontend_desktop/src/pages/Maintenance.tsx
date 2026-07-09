import { useEffect, useMemo, useState } from 'react';
import { Ban, CheckCircle2, Loader2, Plus, Play, Wrench } from 'lucide-react';
import api from '../api/axios';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';

const emptyForm = {
  vehicleId: '',
  title: '',
  description: '',
  serviceProvider: '',
  scheduledDate: '',
  cost: '',
  mileage: '',
  status: 'SCHEDULED',
};

const extractArray = (payload: any): any[] => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.vehicles)) return payload.vehicles;
  return [];
};

const toIsoLocalDateTime = (value: string) => {
  if (!value) return '';
  return value.length === 16 ? `${value}:00` : value;
};

const apiMessage = (err: any, fallback: string) =>
  err?.response?.data?.message || err?.userMessage || err?.message || fallback;

export default function Maintenance() {
  const { showToast } = useToast();
  const [rows, setRows] = useState<any[]>([]);
  const [vehicles, setVehicles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const stats = useMemo(() => {
    const scheduled = rows.filter((row) => row.status === 'SCHEDULED').length;
    const inProgress = rows.filter((row) => row.status === 'IN_PROGRESS').length;
    const completed = rows.filter((row) => row.status === 'COMPLETED').length;
    const cost = rows.reduce((sum, row) => sum + Number(row.cost || 0), 0);
    return { scheduled, inProgress, completed, cost };
  }, [rows]);

  const load = async () => {
    setLoading(true);
    try {
      const [maintenance, fleet] = await Promise.all([api.get('/maintenance'), api.get('/vehicles')]);
      setRows(extractArray(maintenance.data));
      setVehicles(extractArray(fleet.data).filter((vehicle: any) =>
        !['RENTED', 'MAINTENANCE', 'OUT_OF_SERVICE'].includes(vehicle.statut || vehicle.status)));
    } catch (err: any) {
      showToast(apiMessage(err, 'Unable to load maintenance information. Please try again later.'), 'error');
      setRows([]);
      setVehicles([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const validateForm = () => {
    if (!form.vehicleId) return 'Vehicle is required';
    if (!form.title.trim()) return 'Title is required';
    if (!form.scheduledDate) return 'Scheduled date is required';
    if (form.cost && Number(form.cost) < 0) return 'Cost must be greater than or equal to 0';
    if (form.mileage && Number(form.mileage) < 0) return 'Mileage must be greater than or equal to 0';
    return '';
  };

  const create = async () => {
    const validation = validateForm();
    if (validation) {
      showToast(validation, 'warning');
      return;
    }
    setSaving(true);
    try {
      const response = await api.post('/maintenance', {
        vehicleId: Number(form.vehicleId),
        title: form.title.trim(),
        serviceProvider: form.serviceProvider.trim() || null,
        scheduledDate: toIsoLocalDateTime(form.scheduledDate),
        cost: form.cost ? Number(form.cost) : 0,
        mileage: form.mileage ? Number(form.mileage) : 0,
        description: form.description.trim() || null,
        status: form.status,
      });
      setOpen(false);
      setForm(emptyForm);
      await load();
      window.dispatchEvent(new Event('rentcar-data-updated'));
      showToast(response.data?.message || 'Maintenance work order created successfully', 'success');
    } catch (err: any) {
      showToast(apiMessage(err, 'Unable to create maintenance work order.'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (id: number, status: string) => {
    setActionId(id);
    try {
      const response = await api.patch(`/maintenance/${id}/status`, { status });
      await load();
      window.dispatchEvent(new Event('rentcar-data-updated'));
      showToast(response.data?.message || `Maintenance ${status.toLowerCase()} successfully`, 'success');
    } catch (err: any) {
      showToast(apiMessage(err, 'Unable to update maintenance. Please try again later.'), 'error');
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="space-y-5 p-3 sm:p-4 lg:p-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">Vehicle Maintenance</h1>
          <p className="text-sm text-slate-500">Work orders, service alerts, cost summary and fleet health</p>
        </div>
        <button onClick={() => setOpen(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium">
          <Plus size={16} /> New Work Order
        </button>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard label="Scheduled maintenance" value={stats.scheduled} />
        <SummaryCard label="In progress maintenance" value={stats.inProgress} />
        <SummaryCard label="Completed maintenance" value={stats.completed} />
        <SummaryCard label="Cost summary" value={`${stats.cost.toLocaleString()} MAD`} />
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-[#e8e6e1] bg-white p-4">
          <p className="text-sm font-bold text-[#1e293b]">Vehicle Health</p>
          <p className="mt-1 text-sm text-slate-500">
            {stats.inProgress > 0
              ? `${stats.inProgress} vehicle${stats.inProgress > 1 ? 's are' : ' is'} currently blocked for maintenance.`
              : 'No vehicles are currently blocked by maintenance.'}
          </p>
        </div>
        <div className="rounded-lg border border-[#e8e6e1] bg-white p-4">
          <p className="text-sm font-bold text-[#1e293b]">Upcoming Service Alerts</p>
          <p className="mt-1 text-sm text-slate-500">
            {stats.scheduled > 0
              ? `${stats.scheduled} scheduled work order${stats.scheduled > 1 ? 's need' : ' needs'} attention.`
              : 'No upcoming service alerts.'}
          </p>
        </div>
      </div>

      <div className="border border-[#e8e6e1] rounded-lg bg-white overflow-x-auto">
        <table className="w-full min-w-[860px] text-left">
          <thead className="bg-[#f5f5f0] text-[11px] uppercase text-slate-500">
            <tr><th className="p-3">Vehicle</th><th>Work</th><th>Provider</th><th>Schedule</th><th>Cost</th><th>Mileage</th><th>Status</th><th className="pr-3 text-right">Actions</th></tr>
          </thead>
          <tbody className="divide-y divide-[#e8e6e1]">
            {loading ? (
              <tr><td colSpan={8} className="py-16 text-center"><Loader2 className="inline animate-spin text-brand-500" /></td></tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-16 text-center">
                  <p className="text-sm font-semibold text-slate-600">No maintenance work orders yet.</p>
                  <p className="mt-1 text-sm text-slate-400">Create your first work order to keep your fleet healthy.</p>
                </td>
              </tr>
            ) : rows.map((row) => (
              <tr key={row.id} className="text-sm">
                <td className="p-3 font-medium">{row.vehicle}<div className="text-xs text-slate-400">{row.plate}</div></td>
                <td>{row.title}<div className="text-xs text-slate-400 max-w-56 truncate">{row.description}</div></td>
                <td>{row.serviceProvider || '-'}</td>
                <td>{row.scheduledDate || row.scheduledAt ? new Date(row.scheduledDate || row.scheduledAt).toLocaleString() : '-'}</td>
                <td>{Number(row.cost || 0).toLocaleString()} MAD</td>
                <td>{row.mileage ?? '-'}</td>
                <td><span className={`px-2 py-1 rounded text-xs font-bold ${statusClass(row.status)}`}>{row.status}</span></td>
                <td className="pr-3">
                  <div className="flex justify-end gap-1">
                    {row.status === 'SCHEDULED' && (
                      <button title="Start Maintenance" disabled={actionId === row.id}
                        onClick={() => changeStatus(row.id, 'IN_PROGRESS')} className="p-2 text-brand-500 disabled:opacity-50">
                        {actionId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Play size={16} />}
                      </button>
                    )}
                    {row.status === 'IN_PROGRESS' && (
                      <button title="Complete Maintenance" disabled={actionId === row.id}
                        onClick={() => changeStatus(row.id, 'COMPLETED')} className="p-2 text-success-500 disabled:opacity-50">
                        {actionId === row.id ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                      </button>
                    )}
                    {['SCHEDULED', 'IN_PROGRESS'].includes(row.status) && (
                      <button title="Cancel Maintenance" disabled={actionId === row.id}
                        onClick={() => changeStatus(row.id, 'CANCELLED')} className="p-2 text-red-500 disabled:opacity-50">
                        {actionId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Ban size={16} />}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal isOpen={open} onClose={() => !saving && setOpen(false)} title="New Maintenance Work Order">
        <div className="space-y-4">
          <label className="block"><span className="text-xs font-medium text-slate-500">Vehicle *</span>
            <select value={form.vehicleId} onChange={(e) => setForm({ ...form, vehicleId: e.target.value })}
              className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm">
              <option value="">Select vehicle</option>
              {vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.marque || vehicle.brand} - {vehicle.plate || vehicle.plateNumber}</option>)}
            </select>
          </label>
          <Field label="Title *" value={form.title} onChange={(title) => setForm({ ...form, title })} />
          <Field label="Service provider" value={form.serviceProvider} onChange={(serviceProvider) => setForm({ ...form, serviceProvider })} />
          <Field label="Scheduled date *" type="datetime-local" value={form.scheduledDate} onChange={(scheduledDate) => setForm({ ...form, scheduledDate })} />
          <div className="grid grid-cols-2 gap-3">
            <Field label="Cost (MAD)" type="number" value={form.cost} onChange={(cost) => setForm({ ...form, cost })} />
            <Field label="Mileage" type="number" value={form.mileage} onChange={(mileage) => setForm({ ...form, mileage })} />
          </div>
          <label className="block"><span className="text-xs font-medium text-slate-500">Description</span>
            <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm" rows={3} />
          </label>
          <button onClick={create} disabled={saving}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium disabled:opacity-70">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <Wrench size={16} />} Create Work Order
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
  if (status === 'COMPLETED') return 'bg-emerald-100 text-emerald-700';
  if (status === 'CANCELLED') return 'bg-red-100 text-red-700';
  return 'bg-slate-100 text-slate-700';
}

function Field({ label, value, onChange, type = 'text' }: {
  label: string; value: string; onChange: (value: string) => void; type?: string;
}) {
  return (
    <label className="block"><span className="text-xs font-medium text-slate-500">{label}</span>
      <input type={type} value={value} min={type === 'number' ? '0' : undefined} onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm" />
    </label>
  );
}
