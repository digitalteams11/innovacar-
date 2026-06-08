import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import { MapPin, Phone, Mail, Globe, Save, Loader2 } from 'lucide-react';

interface AgencyData {
  id: number;
  name: string;
  email: string;
  address: string;
  phone: string;
  taxId: string;
  city: string;
  country: string;
}

export default function Agency() {
  const [data, setData] = useState<AgencyData | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', address: '', phone: '', taxId: '', city: '', country: '' });

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    const fetchAgency = async () => {
      try {
        const { data } = await api.get('/agency');
        setData(data);
        setForm({
          name: data.name || '',
          email: data.email || '',
          address: data.address || '',
          phone: data.phone || '',
          taxId: data.taxId || '',
          city: data.city || '',
          country: data.country || '',
        });
      } catch (err) {
        console.error('Failed to fetch agency', err);
      } finally {
        setLoading(false);
      }
    };
    fetchAgency();
  }, []);

  const saveAgency = async () => {
    setSaving(true);
    try {
      const { data: updated } = await api.put('/agency', form);
      setData(updated);
      showToast(t('toast.success', { action: 'Agency updated' }));
    } catch (err) {
      showToast('Failed to update agency');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div>
        <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('agency.title') || 'Agency Profile'}</h1>
        <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('agency.subtitle') || 'Manage your agency information'}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 card-premium p-3 sm:p-5">
          <h3 className="text-base font-bold text-[#1e293b] mb-5">General Information</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Agency Name</label>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">Email</label>
                <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">Phone</label>
                <input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Address</label>
              <input type="text" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })}
                className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">City</label>
                <input type="text" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">Country</label>
                <input type="text" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Tax ID</label>
              <input type="text" value={form.taxId} onChange={(e) => setForm({ ...form, taxId: e.target.value })}
                className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="mt-6">
            <button onClick={saveAgency} disabled={saving}
              className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-70">
              {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
              Save Changes
            </button>
          </div>
        </div>

        <div className="space-y-4">
          <div className="card-premium p-3 sm:p-5">
            <h3 className="text-base font-bold text-[#1e293b] mb-4">Contact Info</h3>
            <div className="space-y-3">
              <div className="flex items-center gap-3 text-sm text-slate-500">
                <Mail size={16} className="text-brand-500" />
                {data?.email || 'N/A'}
              </div>
              <div className="flex items-center gap-3 text-sm text-slate-500">
                <Phone size={16} className="text-brand-500" />
                {data?.phone || 'N/A'}
              </div>
              <div className="flex items-center gap-3 text-sm text-slate-500">
                <MapPin size={16} className="text-brand-500" />
                {data?.address || 'N/A'}
              </div>
              <div className="flex items-center gap-3 text-sm text-slate-500">
                <Globe size={16} className="text-brand-500" />
                {data?.city || 'N/A'}, {data?.country || 'N/A'}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
