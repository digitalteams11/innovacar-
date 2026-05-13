import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import ReservationDetailsModal from '../components/ReservationDetailsModal';
import { Search, Plus, Download, ChevronRight, Clock, MapPin, CreditCard, Trash2, Edit3, Eye } from 'lucide-react';
import api from '../api/axios';

interface Reservation {
  id: number;
  clientNom: string;
  vehicleMarque: string;
  dateDebut: string;
  dateFin: string;
  statut: string;
  prixTotal: number;
}

export default function Reservations() {
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [selectedReservation, setSelectedReservation] = useState<Reservation | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ clientNom: '', vehicleMarque: '', dateDebut: '', dateFin: '', statut: 'PENDING', prixTotal: '' });

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    const fetchReservations = async () => {
      try {
        const { data } = await api.get('/reservations');
        // Map backend data to frontend format
        const mapped = data.map((r: any) => ({
          id: r.id,
          clientNom: `Client #${r.id}`,
          vehicleMarque: r.vehicleMarque,
          dateDebut: r.dateStart,
          dateFin: r.dateEnd,
          statut: 'CONFIRMED',
          prixTotal: Number(r.totalPrice),
        }));
        setData(mapped);
      } catch (err) {
        console.error('Failed to fetch reservations', err);
      } finally {
        setLoading(false);
      }
    };
    fetchReservations();
  }, []);

  const handleAction = (label: string) => {
    showToast(t('toast.success', { action: label }));
  };

  const tabs = [
    { key: 'All', label: t('reservations.all') },
    { key: 'Confirmed', label: t('reservations.confirmed') },
    { key: 'Pending', label: t('reservations.pending') },
    { key: 'Rented', label: t('reservations.rented') },
    { key: 'Cancelled', label: t('reservations.cancelled') },
  ];

  const filteredData = data.filter((res) => {
    const matchesTab = activeTab === 'All' || res.statut === activeTab.toUpperCase();
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      res.clientNom.toLowerCase().includes(q) ||
      res.vehicleMarque.toLowerCase().includes(q) ||
      String(res.id).includes(q);
    return matchesTab && matchesSearch;
  });

  const exportCSV = () => {
    const headers = ['ID', 'Client', 'Vehicle', 'Start Date', 'End Date', 'Status', 'Total'];
    const rows = filteredData.map((r) => [r.id, r.clientNom, r.vehicleMarque, r.dateDebut, r.dateFin, r.statut, r.prixTotal]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'reservations.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const openCreate = () => {
    setEditingId(null);
    setForm({ clientNom: '', vehicleMarque: '', dateDebut: '', dateFin: '', statut: 'PENDING', prixTotal: '' });
    setIsModalOpen(true);
  };

  const openEdit = (res: Reservation) => {
    setEditingId(res.id);
    setForm({
      clientNom: res.clientNom,
      vehicleMarque: res.vehicleMarque,
      dateDebut: res.dateDebut,
      dateFin: res.dateFin,
      statut: res.statut,
      prixTotal: String(res.prixTotal),
    });
    setIsModalOpen(true);
  };

  const saveReservation = () => {
    if (!form.clientNom || !form.vehicleMarque || !form.dateDebut || !form.dateFin || !form.prixTotal) {
      showToast('Please fill all fields');
      return;
    }
    if (editingId !== null) {
      setData((prev) =>
        prev.map((r) => (r.id === editingId ? { ...r, ...form, prixTotal: Number(form.prixTotal) } : r))
      );
      showToast(t('toast.success', { action: t('reservations.edit') }));
    } else {
      const newId = Math.max(...data.map((r) => r.id), 0) + 1;
      setData((prev) => [...prev, { ...form, id: newId, prixTotal: Number(form.prixTotal) }]);
      showToast(t('toast.newReservationCreated'));
    }
    setIsModalOpen(false);
  };

  const deleteReservation = (id: number) => {
    if (confirm(t('reservations.deleteConfirm') || 'Delete this reservation?')) {
      setData((prev) => prev.filter((r) => r.id !== id));
      showToast(t('reservations.delete'));
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('reservations.title')}</h1>
          <p className="text-slate-500 font-normal text-sm mt-0.5">{t('reservations.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={exportCSV}
            className="flex items-center gap-2 px-5 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all"
          >
            <Download size={18} />
            {t('reservations.exportCSV')}
          </button>
          <button
            onClick={openCreate}
            className="flex items-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all"
          >
            <Plus size={18} />
            {t('reservations.createReservation')}
          </button>
        </div>
      </div>

      <div className="card-premium flex flex-col md:flex-row md:items-center gap-3 p-3">
        <div className="flex p-1 bg-[#f5f5f0] rounded-xl">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => {
                setActiveTab(tab.key);
                showToast(t('toast.filterApplied', { action: tab.label }));
              }}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-all active:scale-95 ${
                activeTab === tab.key
                  ? 'bg-white text-brand-500 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="flex-1 relative group">
          <Search size={17} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
          <input
            type="text"
            placeholder={t('reservations.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
          />
        </div>
      </div>

      <div className="card-premium overflow-hidden p-0">
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                <th className="px-5 py-4">{t('reservations.id')}</th>
                <th className="px-5 py-4">{t('reservations.clientDetails')}</th>
                <th className="px-5 py-4">{t('reservations.vehicleAgency')}</th>
                <th className="px-5 py-4">{t('reservations.datesDuration')}</th>
                <th className="px-5 py-4">{t('reservations.status')}</th>
                <th className="px-5 py-4">{t('reservations.totalPrice')}</th>
                <th className="px-5 py-4 text-right">{t('reservations.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e8e6e1]/50">
              {loading ? (
                [1, 2, 3].map(i => (
                  <tr key={i} className="animate-pulse">
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-24 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-40 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-6 w-20 bg-gray-100 rounded-full"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-16 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-8 w-8 bg-gray-100 rounded ml-auto"></div></td>
                  </tr>
                ))
              ) : filteredData.length > 0 ? (
                filteredData.map((res) => (
                  <tr
                    key={res.id}
                    className="hover:bg-[#f5f5f0]/40 transition-colors group"
                  >
                    <td className="px-5 py-4">
                      <span className="font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">#RES-{res.id}942</span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 bg-[#f5f5f0] rounded-lg flex items-center justify-center text-slate-600 font-bold group-hover:bg-white group-hover:shadow-sm transition-colors text-sm">
                          {res.clientNom.charAt(0)}
                        </div>
                        <div>
                          <div className="text-sm font-medium text-[#1e293b]">{res.clientNom}</div>
                          <div className="text-[11px] text-slate-400 font-normal">{t('reservations.individualClient')}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-sm font-medium text-[#1e293b]">{res.vehicleMarque}</div>
                      <div className="flex items-center gap-1 mt-0.5 text-slate-400">
                        <MapPin size={11} />
                        <span className="text-xs font-normal">{t('reservations.casablancaAirport')}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2 text-sm font-medium text-[#1e293b]">
                        <span>{new Date(res.dateDebut).toLocaleDateString()}</span>
                        <ChevronRight size={13} className="text-slate-300" />
                        <span>{new Date(res.dateFin).toLocaleDateString()}</span>
                      </div>
                      <div className="flex items-center gap-1 mt-0.5 text-slate-400">
                        <Clock size={11} />
                        <span className="text-xs font-normal">5 {t('reservations.rentalDays')}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5 w-fit ${
                        res.statut === 'CONFIRMED' ? 'bg-success-50 text-success-500' :
                        res.statut === 'PENDING' ? 'bg-warning-50 text-warning-500' :
                        res.statut === 'RENTED' ? 'bg-brand-50 text-brand-500' :
                        'bg-danger-50 text-danger-500'
                      }`}>
                        <div className={`w-1.5 h-1.5 rounded-full ${
                          res.statut === 'CONFIRMED' ? 'bg-success-500' :
                          res.statut === 'PENDING' ? 'bg-warning-500' :
                          res.statut === 'RENTED' ? 'bg-brand-500' :
                          'bg-danger-500'
                        }`}></div>
                        {res.statut === 'CONFIRMED' ? t('reservations.confirmed') : res.statut === 'PENDING' ? t('reservations.pending') : res.statut === 'RENTED' ? t('reservations.rented') : t('reservations.cancelled')}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-sm font-bold text-[#1e293b]">{res.prixTotal} DH</div>
                      <div className="flex items-center gap-1 mt-0.5 text-success-500">
                        <CreditCard size={11} />
                        <span className="text-[10px] font-bold uppercase tracking-wider">{t('reservations.paid')}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => {
                            setSelectedReservation(res);
                            setIsDetailsModalOpen(true);
                          }}
                          className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"
                        >
                          <Eye size={17} />
                        </button>
                        <button
                          onClick={() => openEdit(res)}
                          className="p-2 text-slate-400 hover:text-warning-500 hover:bg-warning-50 rounded-lg transition-all"
                        >
                          <Edit3 size={17} />
                        </button>
                        <button
                          onClick={() => deleteReservation(res.id)}
                          className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"
                        >
                          <Trash2 size={17} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="px-5 py-8 text-center text-slate-400 text-sm">{t('reservations.noResults') || 'No reservations found'}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="p-5 bg-[#f5f5f0]/40 border-t border-[#e8e6e1]/50 flex items-center justify-between">
          <p className="text-xs font-bold text-slate-400">{t('reservations.showing')}</p>
          <div className="flex gap-2">
            <button className="px-4 py-2 bg-white border border-[#e8e6e1] rounded-xl text-xs font-bold text-slate-300 cursor-not-allowed">{t('reservations.previous')}</button>
            <button
              onClick={() => handleAction(t('reservations.next'))}
              className="px-4 py-2 bg-white border border-[#e8e6e1] rounded-xl text-xs font-bold text-[#1e293b] hover:bg-[#f5f5f0] hover:shadow-sm active:scale-95 transition-all"
            >
              {t('reservations.next')}
            </button>
          </div>
        </div>
      </div>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('reservations.edit') : t('reservations.createReservation')}>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.clientDetails')}</label>
            <input type="text" value={form.clientNom} onChange={(e) => setForm({ ...form, clientNom: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.vehicleAgency')}</label>
            <input type="text" value={form.vehicleMarque} onChange={(e) => setForm({ ...form, vehicleMarque: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.datesDuration')}</label>
              <input type="date" value={form.dateDebut} onChange={(e) => setForm({ ...form, dateDebut: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.datesDuration')} (fin)</label>
              <input type="date" value={form.dateFin} onChange={(e) => setForm({ ...form, dateFin: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.status')}</label>
              <select value={form.statut} onChange={(e) => setForm({ ...form, statut: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="PENDING">{t('reservations.pending')}</option>
                <option value="CONFIRMED">{t('reservations.confirmed')}</option>
                <option value="RENTED">{t('reservations.rented')}</option>
                <option value="CANCELLED">{t('reservations.cancelled')}</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('reservations.totalPrice')}</label>
              <input type="number" value={form.prixTotal} onChange={(e) => setForm({ ...form, prixTotal: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="pt-2">
            <button onClick={saveReservation} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
              {editingId ? t('reservations.saveChanges') || 'Save Changes' : t('reservations.createReservation')}
            </button>
          </div>
        </div>
      </Modal>

      <ReservationDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        reservation={selectedReservation}
      />
    </div>
  );
}
