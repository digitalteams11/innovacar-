import { useEffect, useState } from 'react';
import {
  Car, CreditCard, FileSignature, FileText, Landmark, Loader2,
  Mail, MapPin, Phone, ReceiptText
} from 'lucide-react';
import api from '../api/axios';
import Modal from './Modal';

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
}

type Tab = 'reservations' | 'contracts' | 'payments' | 'documents';

export default function ClientProfileModal({ isOpen, onClose, client }: ClientProfileModalProps) {
  const [profile, setProfile] = useState<any>(null);
  const [deposits, setDeposits] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<Tab>('reservations');

  useEffect(() => {
    if (!isOpen || !client?.id) return;
    setLoading(true);
    setError('');
    Promise.all([
      api.get(`/clients/${client.id}/profile`),
      api.get(`/deposits/client/${client.id}/summary`).catch(() => ({ data: null })),
    ])
      .then(([profileResponse, depositResponse]) => {
        setProfile(profileResponse.data);
        setDeposits(depositResponse.data);
      })
      .catch((err) => setError(err.userMessage || 'Failed to load client profile'))
      .finally(() => setLoading(false));
  }, [isOpen, client?.id]);

  if (!client) return null;
  const balance = profile?.balance || {};

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Client Profile" maxWidth="4xl">
      {loading ? (
        <div className="flex items-center justify-center py-20 text-brand-500">
          <Loader2 size={28} className="animate-spin" />
        </div>
      ) : error ? (
        <div className="py-12 text-center text-danger-500 text-sm">{error}</div>
      ) : (
        <div className="space-y-5 max-h-[78vh] overflow-y-auto pr-1">
          <div className="flex flex-col sm:flex-row sm:items-center gap-4 pb-5 border-b border-slate-100">
            <div className="w-16 h-16 bg-brand-50 rounded-xl flex items-center justify-center text-brand-600 text-xl font-bold">
              {client.name.split(' ').map((part) => part[0]).join('').slice(0, 2)}
            </div>
            <div className="flex-1">
              <h2 className="text-xl font-bold text-slate-900">{client.name}</h2>
              <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-slate-500">
                {client.email && <span className="flex items-center gap-1"><Mail size={13} />{client.email}</span>}
                {client.phone && <span className="flex items-center gap-1"><Phone size={13} />{client.phone}</span>}
                {client.address && <span className="flex items-center gap-1"><MapPin size={13} />{client.address}</span>}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
            <Metric label="Reservations" value={balance.totalRentals || 0} icon={<Car size={15} />} />
            <Metric label="Contracts" value={balance.totalContracts || 0} icon={<FileSignature size={15} />} />
            <Metric label="Total Paid" value={`${balance.totalPaid || 0} MAD`} icon={<CreditCard size={15} />} />
            <Metric label="Outstanding" value={`${balance.outstandingBalance || 0} MAD`} icon={<ReceiptText size={15} />} />
            <Metric label="Active Deposits" value={`${deposits?.activeDeposits || 0} MAD`} icon={<Landmark size={15} />} />
          </div>

          <div className="flex gap-1 p-1 bg-slate-100 rounded-lg overflow-x-auto">
            {([
              ['reservations', 'Reservations'],
              ['contracts', 'Contracts'],
              ['payments', 'Payments'],
              ['documents', 'Documents & Signatures'],
            ] as [Tab, string][]).map(([key, label]) => (
              <button key={key} onClick={() => setTab(key)}
                className={`px-3 py-2 rounded-md text-xs font-medium whitespace-nowrap ${
                  tab === key ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500'
                }`}>
                {label}
              </button>
            ))}
          </div>

          {tab === 'reservations' && (
            <HistoryList
              empty="No reservations"
              rows={(profile?.reservationHistory || []).map((item: any) => ({
                id: item.id,
                title: item.vehicleMarque || `Reservation #${item.id}`,
                detail: `${item.dateStart} ${item.startTime || ''} to ${item.dateEnd} ${item.endTime || ''}`,
                status: item.status,
              }))}
            />
          )}
          {tab === 'contracts' && (
            <HistoryList
              empty="No contracts"
              rows={(profile?.contractHistory || []).map((item: any) => ({
                id: item.id,
                title: item.contractNumber,
                detail: `${item.vehicleBrand || ''} ${item.vehicleModel || ''} - ${item.totalPrice || 0} MAD`,
                status: item.status,
              }))}
            />
          )}
          {tab === 'payments' && (
            <HistoryList
              empty="No payments"
              rows={(profile?.paymentHistory || []).map((item: any) => ({
                id: item.id,
                title: item.paymentNumber || `Payment #${item.id}`,
                detail: `${item.amount || 0} MAD - ${item.paymentMethod || 'Other'} - ${item.paymentDate || ''}`,
                status: item.status,
              }))}
            />
          )}
          {tab === 'documents' && (
            <div className="space-y-4">
              <HistoryList
                empty="No documents"
                rows={(profile?.documents || []).map((item: any) => ({
                  id: item.id,
                  title: item.name || item.type,
                  detail: `Contract #${item.contractId}`,
                  status: item.type,
                }))}
              />
              <HistoryList
                empty="No signature history"
                rows={(profile?.signatureHistory || []).map((item: any) => ({
                  id: item.contractId,
                  title: item.contractNumber,
                  detail: `Agency: ${item.agencySignedAt || 'Pending'} | Client: ${item.clientSignedAt || 'Pending'}`,
                  status: item.status,
                }))}
              />
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

function Metric({ label, value, icon }: { label: string; value: string | number; icon: React.ReactNode }) {
  return (
    <div className="p-3 border border-slate-100 rounded-lg bg-white">
      <div className="flex items-center gap-1.5 text-slate-400">{icon}<span className="text-[10px] uppercase font-bold">{label}</span></div>
      <p className="mt-2 text-base font-bold text-slate-900">{value}</p>
    </div>
  );
}

function HistoryList({ rows, empty }: {
  rows: { id: number; title: string; detail: string; status: string }[];
  empty: string;
}) {
  if (!rows.length) return <div className="py-10 text-center text-sm text-slate-400">{empty}</div>;
  return (
    <div className="divide-y divide-slate-100 border border-slate-100 rounded-lg">
      {rows.map((row) => (
        <div key={row.id} className="flex items-center gap-3 p-3">
          <FileText size={16} className="text-slate-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-slate-800 truncate">{row.title}</p>
            <p className="text-xs text-slate-400 truncate">{row.detail}</p>
          </div>
          <span className="px-2 py-1 bg-slate-100 rounded text-[10px] font-bold text-slate-600">{row.status}</span>
        </div>
      ))}
    </div>
  );
}
