import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, Plus, Play, Wrench } from 'lucide-react';
import api from '../api/axios';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';

export default function Maintenance() {
  const { showToast } = useToast();
  const [rows, setRows] = useState<any[]>([]);
  const [vehicles, setVehicles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    vehicleId: '', title: '', description: '', serviceProvider: '',
    scheduledAt: '', cost: '', mileage: '', status: 'SCHEDULED',
  });

  const load = async () => {
    setLoading(true);
    try {
      const [maintenance, fleet] = await Promise.all([api.get('/maintenance'), api.get('/vehicles')]);
      setRows(maintenance.data);
      setVehicles(fleet.data.filter((vehicle: any) => vehicle.statut !== 'RENTED'));
    } catch (err: any) {
      showToast(err.userMessage || 'Failed to load maintenance', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const create = async () => {
    if (!form.vehicleId || !form.title) {
      showToast('Vehicle and title are required', 'error');
      return;
    }
    setSaving(true);
    try {
      await api.post('/maintenance', {
        ...form,
        vehicleId: Number(form.vehicleId),
        cost: form.cost ? Number(form.cost) : undefined,
        mileage: form.mileage ? Number(form.mileage) : undefined,
      });
      setOpen(false);
      setForm({ vehicleId: '', title: '', description: '', serviceProvider: '', scheduledAt: '', cost: '', mileage: '', status: 'SCHEDULED' });
      await load();
      showToast('Maintenance work order created');
    } catch (err: any) {
      showToast(err.userMessage || 'Failed to create work order', 'error');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (id: number, status: string) => {
    setActionId(id);
    try {
      await api.patch(`/maintenance/${id}/status`, { status });
      await load();
      showToast(`Maintenance ${status.toLowerCase()}`);
    } catch (err: any) {
      showToast(err.userMessage || 'Failed to update maintenance', 'error');
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="space-y-5 p-3 sm:p-4 lg:p-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">Vehicle Maintenance</h1>
          <p className="text-sm text-slate-500">Work orders and fleet service history</p>
        </div>
        <button onClick={() => setOpen(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium">
          <Plus size={16} /> New Work Order
        </button>
      </div>

      <div className="border border-[#e8e6e1] rounded-lg bg-white overflow-x-auto">
        <table className="w-full min-w-[760px] text-left">
          <thead className="bg-[#f5f5f0] text-[11px] uppercase text-slate-500">
            <tr><th className="p-3">Vehicle</th><th>Work</th><th>Provider</th><th>Schedule</th><th>Cost</th><th>Status</th><th className="pr-3 text-right">Actions</th></tr>
          </thead>
          <tbody className="divide-y divide-[#e8e6e1]">
            {loading ? (
              <tr><td colSpan={7} className="py-16 text-center"><Loader2 className="inline animate-spin text-brand-500" /></td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={7} className="py-16 text-center text-sm text-slate-400">No maintenance history</td></tr>
            ) : rows.map((row) => (
              <tr key={row.id} className="text-sm">
                <td className="p-3 font-medium">{row.vehicle}<div className="text-xs text-slate-400">{row.plate}</div></td>
                <td>{row.title}<div className="text-xs text-slate-400 max-w-56 truncate">{row.description}</div></td>
                <td>{row.serviceProvider || '-'}</td>
                <td>{row.scheduledAt ? new Date(row.scheduledAt).toLocaleString() : '-'}</td>
                <td>{row.cost} MAD</td>
                <td><span className="px-2 py-1 bg-slate-100 rounded text-xs font-bold">{row.status}</span></td>
                <td className="pr-3">
                  <div className="flex justify-end gap-1">
                    {row.status === 'SCHEDULED' && (
                      <button title="Start maintenance" disabled={actionId === row.id}
                        onClick={() => changeStatus(row.id, 'IN_PROGRESS')} className="p-2 text-brand-500">
                        {actionId === row.id ? <Loader2 size={16} className="animate-spin" /> : <Play size={16} />}
                      </button>
                    )}
                    {row.status === 'IN_PROGRESS' && (
                      <button title="Complete maintenance" disabled={actionId === row.id}
                        onClick={() => changeStatus(row.id, 'COMPLETED')} className="p-2 text-success-500">
                        {actionId === row.id ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal isOpen={open} onClose={() => setOpen(false)} title="New Maintenance Work Order">
        <div className="space-y-4">
          <label className="block"><span className="text-xs font-medium text-slate-500">Vehicle</span>
            <select value={form.vehicleId} onChange={(e) => setForm({ ...form, vehicleId: e.target.value })}
              className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm">
              <option value="">Select vehicle</option>
              {vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.marque} - {vehicle.plate}</option>)}
            </select>
          </label>
          <Field label="Title" value={form.title} onChange={(title) => setForm({ ...form, title })} />
          <Field label="Service provider" value={form.serviceProvider} onChange={(serviceProvider) => setForm({ ...form, serviceProvider })} />
          <Field label="Scheduled date" type="datetime-local" value={form.scheduledAt} onChange={(scheduledAt) => setForm({ ...form, scheduledAt })} />
          <div className="grid grid-cols-2 gap-3">
            <Field label="Cost (MAD)" type="number" value={form.cost} onChange={(cost) => setForm({ ...form, cost })} />
            <Field label="Mileage" type="number" value={form.mileage} onChange={(mileage) => setForm({ ...form, mileage })} />
          </div>
          <label className="block"><span className="text-xs font-medium text-slate-500">Description</span>
            <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm" rows={3} />
          </label>
          <button onClick={create} disabled={saving}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-medium">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <Wrench size={16} />} Create Work Order
          </button>
        </div>
      </Modal>
    </div>
  );
}

function Field({ label, value, onChange, type = 'text' }: {
  label: string; value: string; onChange: (value: string) => void; type?: string;
}) {
  return (
    <label className="block"><span className="text-xs font-medium text-slate-500">{label}</span>
      <input type={type} value={value} onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full px-3 py-2.5 border rounded-lg text-sm" />
    </label>
  );
}
