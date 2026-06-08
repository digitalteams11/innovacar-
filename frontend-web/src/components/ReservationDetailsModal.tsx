import Modal from './Modal';
import {
  User, Car, Calendar, CreditCard,
  MapPin, Clock, CheckCircle2,
  FileText, Phone, Mail
} from 'lucide-react';

interface Reservation {
  id: number;
  clientNom: string;
  vehicleMarque: string;
  dateDebut: string;
  dateFin: string;
  statut: string;
  prixTotal: number;
}

interface ReservationDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  reservation: Reservation | null;
}

export default function ReservationDetailsModal({ isOpen, onClose, reservation }: ReservationDetailsModalProps) {
  if (!reservation) return null;

  const duration = Math.ceil((new Date(reservation.dateFin).getTime() - new Date(reservation.dateDebut).getTime()) / (1000 * 3600 * 24));

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Reservation Details">
      <div className="space-y-6 max-h-[80vh] overflow-y-auto no-scrollbar pr-1">
        {/* Reservation Status Banner */}
        <div className={`p-4 rounded-2xl flex items-center justify-between ${
          reservation.statut === 'CONFIRMED' ? 'bg-success-50 text-success-700 border border-success-100' :
          reservation.statut === 'PENDING' ? 'bg-warning-50 text-warning-700 border border-warning-100' :
          reservation.statut === 'RENTED' ? 'bg-brand-50 text-brand-700 border border-brand-100' :
          'bg-danger-50 text-danger-700 border border-danger-100'
        }`}>
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
              reservation.statut === 'CONFIRMED' ? 'bg-success-500' :
              reservation.statut === 'PENDING' ? 'bg-warning-500' :
              reservation.statut === 'RENTED' ? 'bg-brand-500' :
              'bg-danger-500'
            } text-white shadow-sm`}>
              <Clock size={20} />
            </div>
            <div>
              <p className="text-[10px] font-bold uppercase tracking-widest opacity-70">Current Status</p>
              <h3 className="text-sm font-bold uppercase">{reservation.statut}</h3>
            </div>
          </div>
          <div className="text-right">
             <p className="text-[10px] font-bold uppercase tracking-widest opacity-70">ID</p>
             <p className="text-sm font-mono font-bold">#RES-{reservation.id}942</p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Client Details */}
          <div className="card-premium space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 border-b pb-2">
              <User size={14} /> Client Information
            </h4>
            <div className="space-y-3">
              <div>
                <p className="text-sm font-bold text-slate-900">{reservation.clientNom}</p>
                <p className="text-xs text-slate-500 italic">Individual Client</p>
              </div>
              <div className="space-y-1.5 pt-1">
                 <p className="text-xs text-slate-500 flex items-center gap-2"><Mail size={12} /> client@example.com</p>
                 <p className="text-xs text-slate-500 flex items-center gap-2"><Phone size={12} /> +212 600-000000</p>
              </div>
            </div>
          </div>

          {/* Vehicle Details */}
          <div className="card-premium space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 border-b pb-2">
              <Car size={14} /> Vehicle Details
            </h4>
            <div className="space-y-3">
              <div>
                <p className="text-sm font-bold text-slate-900">{reservation.vehicleMarque}</p>
                <p className="text-xs text-slate-500">Economy Class • Diesel</p>
              </div>
              <div className="flex items-center gap-2 pt-1">
                 <span className="px-2 py-0.5 bg-slate-100 rounded text-[10px] font-mono font-bold text-slate-600">ABC-123-XY</span>
                 <span className="px-2 py-0.5 bg-brand-50 rounded text-[10px] font-bold text-brand-600">Available</span>
              </div>
            </div>
          </div>
        </div>

        {/* Rental Period */}
        <div className="card-premium">
           <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 border-b pb-2 mb-4">
              <Calendar size={14} /> Rental Period & Duration
           </h4>
           <div className="flex items-center justify-between">
              <div className="space-y-1 text-center flex-1">
                 <p className="text-[10px] font-bold uppercase tracking-widest text-slate-300">Pick-up</p>
                 <p className="text-sm font-bold text-slate-900">{new Date(reservation.dateDebut).toLocaleDateString()}</p>
                 <p className="text-xs text-slate-500">09:00 AM</p>
              </div>
              <div className="px-4 flex flex-col items-center">
                 <div className="h-px w-16 bg-slate-200 relative">
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-6 h-6 bg-white border border-slate-200 rounded-full flex items-center justify-center text-slate-400 text-[10px] font-bold">{duration}d</div>
                 </div>
              </div>
              <div className="space-y-1 text-center flex-1">
                 <p className="text-[10px] font-bold uppercase tracking-widest text-slate-300">Drop-off</p>
                 <p className="text-sm font-bold text-slate-900">{new Date(reservation.dateFin).toLocaleDateString()}</p>
                 <p className="text-xs text-slate-500">18:00 PM</p>
              </div>
           </div>
           <div className="mt-4 pt-4 border-t border-slate-50 flex items-center gap-2 text-xs text-slate-500 justify-center">
              <MapPin size={14} className="text-brand-500" />
              <span>Casablanca International Airport (CMN)</span>
           </div>
        </div>

        {/* Financial Summary */}
        <div className="card-premium bg-slate-900 text-white border-none shadow-elevated overflow-hidden relative">
           <div className="absolute -top-12 -right-12 w-32 h-32 bg-white/5 rounded-full blur-2xl"></div>
           <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 border-b border-white/10 pb-2 mb-4">
              <CreditCard size={14} /> Financial Summary
           </h4>
           <div className="space-y-3">
              <div className="flex justify-between text-sm">
                 <span className="text-slate-400">Daily Rate (x{duration} days)</span>
                 <span className="font-medium">{Math.round(reservation.prixTotal / duration)} DH</span>
              </div>
              <div className="flex justify-between text-sm">
                 <span className="text-slate-400">Insurance (Full Coverage)</span>
                 <span className="font-medium text-emerald-400">Included</span>
              </div>
              <div className="pt-3 border-t border-white/10 flex justify-between items-end">
                 <div>
                    <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Total Price</p>
                    <p className="text-2xl font-black text-white">{reservation.prixTotal} DH</p>
                 </div>
                 <div className="text-right">
                    <span className="px-3 py-1 bg-emerald-500/20 text-emerald-400 rounded-lg text-[10px] font-bold uppercase border border-emerald-500/30">Fully Paid</span>
                 </div>
              </div>
           </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-3">
           <button className="flex-1 flex items-center justify-center gap-2 py-3.5 bg-slate-100 text-slate-700 rounded-2xl font-bold text-sm hover:bg-slate-200 transition-all active:scale-95">
              <FileText size={18} /> Invoice
           </button>
           <button className="flex-1 flex items-center justify-center gap-2 py-3.5 bg-brand-500 text-white rounded-2xl font-bold text-sm hover:bg-brand-600 transition-all shadow-lg shadow-brand-500/20 active:scale-95">
              <CheckCircle2 size={18} /> Manage
           </button>
        </div>
      </div>
    </Modal>
  );
}
