import { useEffect, useState } from 'react';
import { Globe2, Palette, Upload } from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';

export default function WhiteLabel() {
  const [form, setForm] = useState({
    logoUrl: '',
    primaryColor: '#0b1437',
    accentColor: '#c9a96e',
    customDomain: '',
    domainStatus: 'PENDING',
  });
  const { showToast } = useToast();

  useEffect(() => {
    api.get('/white-label').then(({ data }) => {
      if (data) setForm((current) => ({ ...current, ...data }));
    }).catch(console.error);
  }, []);

  const uploadLogo = (file?: File) => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setForm((current) => ({ ...current, logoUrl: String(reader.result) }));
    reader.readAsDataURL(file);
  };

  const save = async () => {
    try {
      const { data } = await api.post('/white-label', form);
      setForm((current) => ({ ...current, ...data }));
      showToast('White-label settings saved');
    } catch (error: any) {
      showToast(error.userMessage || 'Failed to save white-label settings');
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <div>
        <h1 className="text-xl font-bold text-[#1e293b]">White Label</h1>
        <p className="text-sm text-slate-500 mt-1">Configure the agency logo, colors, and customer-facing domain.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <section className="bg-white border border-[#e8e6e1] p-5">
          <div className="flex items-center gap-2 mb-5"><Upload size={18} /><h2 className="font-bold text-[#1e293b]">Agency Logo</h2></div>
          <div className="h-28 border border-dashed border-slate-300 flex items-center justify-center bg-slate-50 mb-4">
            {form.logoUrl ? <img src={form.logoUrl} alt="Agency logo" className="max-h-20 max-w-[80%] object-contain" /> : <span className="text-sm text-slate-400">No logo uploaded</span>}
          </div>
          <input type="file" accept="image/png,image/jpeg,image/webp" onChange={(e) => uploadLogo(e.target.files?.[0])} className="text-sm" />
        </section>

        <section className="bg-white border border-[#e8e6e1] p-5">
          <div className="flex items-center gap-2 mb-5"><Palette size={18} /><h2 className="font-bold text-[#1e293b]">Brand Colors</h2></div>
          <div className="grid grid-cols-2 gap-4">
            {[
              ['primaryColor', 'Primary Color'],
              ['accentColor', 'Accent Color'],
            ].map(([key, label]) => (
              <label key={key} className="text-xs font-semibold text-slate-500">
                {label}
                <div className="mt-2 flex items-center gap-2">
                  <input type="color" value={(form as any)[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} className="w-10 h-10 border-0 bg-transparent" />
                  <input value={(form as any)[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} className="w-full px-3 py-2 border border-[#e8e6e1] rounded-lg text-sm" />
                </div>
              </label>
            ))}
          </div>
        </section>
      </div>

      <section className="bg-white border border-[#e8e6e1] p-5">
        <div className="flex items-center gap-2 mb-5"><Globe2 size={18} /><h2 className="font-bold text-[#1e293b]">Custom Domain</h2></div>
        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-3">
          <input value={form.customDomain || ''} onChange={(e) => setForm({ ...form, customDomain: e.target.value })}
            placeholder="rent.myagency.com" className="px-3 py-2.5 border border-[#e8e6e1] rounded-lg text-sm outline-none focus:border-brand-400" />
          <span className="px-3 py-2.5 bg-slate-100 text-slate-500 text-xs font-bold rounded-lg">{form.domainStatus || 'PENDING'}</span>
        </div>
      </section>

      <div className="flex justify-end">
        <button onClick={save} className="px-5 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-semibold hover:bg-brand-600">Save Branding</button>
      </div>
    </div>
  );
}
