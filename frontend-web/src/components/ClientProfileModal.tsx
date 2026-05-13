import React from 'react';
import Modal from './Modal';
import { Mail, Phone, MapPin, Calendar, Star, FileText, CheckCircle2, Clock, Car, Wallet } from 'lucide-react';

interface Reservation {
  id: string;
  vehicle: string;
  date: string;
  amount: number;
  status: 'Completed' | 'Active' | 'Pending';
}

interface Client {
  id: number;
  name: string;
  email: string;
  phone: string;
  address: string;
  reservations: number;
  rating: number;
}

interface ClientProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  client: Client | null;
}

const mockReservations: Reservation[] = [
  { id: 'RES-001', vehicle: 'Dacia Logan', date: '2026-05-01', amount: 3500, status: 'Completed' },
  { id: 'RES-002', vehicle: 'Renault Clio', date: '2026-04-15', amount: 1800, status: 'Completed' },
  { id: 'RES-003', vehicle: 'Peugeot 208', date: '2026-05-12', amount: 2500, status: 'Active' },
];

export default function ClientProfileModal({ isOpen, onClose, client }: ClientProfileModalProps) {
  if (!client) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Client Profile">
      <div className="space-y-6 max-h-[80vh] overflow-y-auto no-scrollbar pr-1">
        {/* Profile Header */}
        <div className="flex flex-col items-center text-center space-y-4 pb-6 border-b border-slate-100">
          <div className="w-24 h-24 bg-brand-50 rounded-2xl flex items-center justify-center text-brand-500 text-3xl font-bold border-4 border-white shadow-soft">
            {client.name.split(' ').map((n) => n[0]).join('')}
          </div>
          <div>
            <h2 className="text-xl font-bold text-slate-900">{client.name}</h2>
            <div className="flex items-center justify-center gap-1.5 text-amber-500 mt-1">
              <Star size={16} fill="currentColor" />
              <span className="font-bold">{client.rating}</span>
              <span className="text-slate-400 font-normal text-sm ml-1">Overall Rating</span>
            </div>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-slate-50 p-4 rounded-2xl border border-slate-100 flex flex-col items-center">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Total Bookings</p>
            <p className="text-xl font-black text-brand-600">{client.reservations}</p>
          </div>
          <div className="bg-slate-50 p-4 rounded-2xl border border-slate-100 flex flex-col items-center">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Status</p>
            <span className="px-2 py-1 bg-emerald-50 text-emerald-600 rounded text-[10px] font-bold uppercase tracking-wider">Premium Member</span>
          </div>
        </div>

        {/* Contact Details */}
        <div className="space-y-4">
          <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b pb-2">Contact Details</h3>
          <div className="space-y-3">
            <div className="flex items-center gap-3 text-slate-600">
              <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-400"><Mail size={16} /></div>
              <span className="text-sm font-medium">{client.email}</span>
            </div>
            <div className="flex items-center gap-3 text-slate-600">
              <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-400"><Phone size={16} /></div>
              <span className="text-sm font-medium">{client.phone}</span>
            </div>
            <div className="flex items-center gap-3 text-slate-600">
              <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-400"><MapPin size={16} /></div>
              <span className="text-sm font-medium">{client.address}</span>
            </div>
          </div>
        </div>

        {/* Recent Reservations */}
        <div className="space-y-4">
          <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b pb-2">Recent Reservations</h3>
          <div className="space-y-3">
            {mockReservations.map((res) => (
              <div key={res.id} className="p-3 bg-white border border-slate-100 rounded-xl hover:shadow-sm transition-all flex items-center justify-between group">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-brand-50 flex items-center justify-center text-brand-500 group-hover:bg-brand-500 group-hover:text-white transition-all"><Car size={18} /></div>
                  <div>
                    <p className="text-sm font-bold text-slate-900">{res.vehicle}</p>
                    <p className="text-xs text-slate-400">{new Date(res.date).toLocaleDateString()} • {res.id}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-slate-900">{res.amount} DH</p>
                  <span className={`text-[10px] font-bold uppercase ${
                    res.status === 'Completed' ? 'text-emerald-500' : res.status === 'Active' ? 'text-brand-500' : 'text-amber-500'
                  }`}>
                    {res.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ID Verification */}
        <div className="p-4 bg-emerald-50 border border-emerald-100 rounded-2xl flex items-center gap-4">
           <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-emerald-500 shadow-sm"><CheckCircle2 size={24} /></div>
           <div>
              <p className="text-sm font-bold text-emerald-900 tracking-tight">Identity Verified</p>
              <p className="text-xs text-emerald-700/80">Passport & Driving License on file</p>
           </div>
        </div>

        <div className="flex gap-3 pt-4">
          <button className="flex-1 flex items-center justify-center gap-2 py-3 bg-slate-100 text-slate-700 rounded-xl font-bold text-sm hover:bg-slate-200 transition-all">
            <FileText size={16} /> History
          </button>
          <button className="flex-1 flex items-center justify-center gap-2 py-3 bg-brand-500 text-white rounded-xl font-bold text-sm hover:bg-brand-600 transition-all shadow-lg shadow-brand-500/20">
            <Wallet size={16} /> Payments
          </button>
        </div>
      </div>
    </Modal>
  );
}
