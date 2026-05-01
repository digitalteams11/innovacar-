import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { 
  CalendarRange, 
  Plus, 
  Search,
  Filter,
  MoreHorizontal,
  User,
  Car
} from 'lucide-react';
import { cn } from '../lib/utils';

interface Reservation {
  id: number;
  clientNom: string;
  vehicleMarque: string;
  dateDebut: string;
  dateFin: string;
  prixTotal: number;
  statut: 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
}

const StatusBadge = ({ status }: { status: string }) => {
  const styles: any = {
    PENDING: "bg-yellow-100 text-yellow-700 border-yellow-200",
    CONFIRMED: "bg-blue-100 text-blue-700 border-blue-200",
    COMPLETED: "bg-green-100 text-green-700 border-green-200",
    CANCELLED: "bg-red-100 text-red-700 border-red-200"
  };

  return (
    <span className={cn("px-3 py-1 rounded-full text-xs font-bold border", styles[status])}>
      {status}
    </span>
  );
};

export default function Reservations() {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    const fetchReservations = async () => {
      try {
        const { data } = await api.get('/reservations');
        setReservations(data);
      } catch (err) {
        console.error('Failed to fetch reservations', err);
      } finally {
        setLoading(false);
      }
    };
    fetchReservations();
  }, []);

  const filteredReservations = reservations.filter(r => 
    r.clientNom.toLowerCase().includes(searchTerm.toLowerCase()) ||
    r.vehicleMarque.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">Reservations</h2>
          <p className="text-gray-500 font-medium">Manage and track your vehicle bookings</p>
        </div>
        <button className="flex items-center gap-2 bg-blue-600 text-white px-5 py-2.5 rounded-xl font-bold hover:bg-blue-700 transition-all shadow-lg shadow-blue-500/20">
          <Plus size={20} />
          New Reservation
        </button>
      </div>

      <div className="flex items-center gap-4 bg-white p-4 rounded-2xl border border-gray-100 shadow-sm">
        <div className="relative flex-1 group">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-blue-600 transition-colors" size={20} />
          <input 
            type="text" 
            placeholder="Search by client or vehicle..."
            className="w-full pl-10 pr-4 py-2 bg-gray-50 border-none rounded-xl text-sm font-medium focus:ring-2 focus:ring-blue-500/20 focus:bg-white transition-all"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <button className="flex items-center gap-2 px-4 py-2 text-sm font-bold text-gray-600 hover:bg-gray-100 rounded-xl transition-colors">
          <Filter size={18} />
          Filters
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50/50 border-b border-gray-100">
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Client</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Vehicle</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Dates</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Status</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Total</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading ? (
              [1, 2, 3].map(i => (
                <tr key={i} className="animate-pulse">
                  <td className="px-6 py-6"><div className="h-5 w-32 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-5 w-24 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-5 w-40 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-6 w-20 bg-gray-100 rounded-full"></div></td>
                  <td className="px-6 py-6"><div className="h-5 w-16 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-8 w-8 bg-gray-100 rounded ml-auto"></div></td>
                </tr>
              ))
            ) : filteredReservations.length > 0 ? (
              filteredReservations.map((res) => (
                <tr key={res.id} className="hover:bg-gray-50/50 transition-colors group">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 group-hover:text-blue-600 transition-colors">
                        <User size={16} />
                      </div>
                      <p className="text-sm font-bold text-gray-900">{res.clientNom}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm font-medium text-gray-600">
                    <div className="flex items-center gap-2">
                      <Car size={14} className="text-gray-400" />
                      {res.vehicleMarque}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm font-medium text-gray-500">
                    {new Date(res.dateDebut).toLocaleDateString()} - {new Date(res.dateFin).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4">
                    <StatusBadge status={res.statut} />
                  </td>
                  <td className="px-6 py-4 text-sm font-bold text-gray-900">
                    ${res.prixTotal}
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-all">
                      <MoreHorizontal size={20} />
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center text-gray-400 font-medium">
                  No reservations found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
