import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { 
  Car, 
  Plus, 
  Search,
  Filter,
  MoreHorizontal,
  Tag
} from 'lucide-react';
import { cn } from '../lib/utils';

interface Vehicle {
  id: number;
  marque: string;
  prixJour: number;
  statut: 'AVAILABLE' | 'RENTED' | 'MAINTENANCE';
}

const StatusBadge = ({ status }: { status: string }) => {
  const styles: any = {
    AVAILABLE: "bg-green-100 text-green-700 border-green-200",
    RENTED: "bg-blue-100 text-blue-700 border-blue-200",
    MAINTENANCE: "bg-orange-100 text-orange-700 border-orange-200"
  };

  return (
    <span className={cn("px-3 py-1 rounded-full text-xs font-bold border", styles[status])}>
      {status}
    </span>
  );
};

export default function Vehicles() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    const fetchVehicles = async () => {
      try {
        const { data } = await api.get('/vehicles');
        setVehicles(data);
      } catch (err) {
        console.error('Failed to fetch vehicles', err);
      } finally {
        setLoading(false);
      }
    };
    fetchVehicles();
  }, []);

  const filteredVehicles = vehicles.filter(v => 
    v.marque.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">Fleet Management</h2>
          <p className="text-gray-500 font-medium">Manage and monitor your vehicle inventory</p>
        </div>
        <button className="flex items-center gap-2 bg-blue-600 text-white px-5 py-2.5 rounded-xl font-bold hover:bg-blue-700 transition-all shadow-lg shadow-blue-500/20">
          <Plus size={20} />
          Add Vehicle
        </button>
      </div>

      <div className="flex items-center gap-4 bg-white p-4 rounded-2xl border border-gray-100 shadow-sm">
        <div className="relative flex-1 group">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-blue-600 transition-colors" size={20} />
          <input 
            type="text" 
            placeholder="Search vehicles by marque..."
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
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Vehicle</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Status</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider">Daily Rate</th>
              <th className="px-6 py-4 text-xs font-bold text-gray-400 uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading ? (
              [1, 2, 3].map(i => (
                <tr key={i} className="animate-pulse">
                  <td className="px-6 py-6"><div className="h-5 w-48 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-6 w-24 bg-gray-100 rounded-full"></div></td>
                  <td className="px-6 py-6"><div className="h-5 w-16 bg-gray-100 rounded"></div></td>
                  <td className="px-6 py-6"><div className="h-8 w-8 bg-gray-100 rounded ml-auto"></div></td>
                </tr>
              ))
            ) : filteredVehicles.length > 0 ? (
              filteredVehicles.map((vehicle) => (
                <tr key={vehicle.id} className="hover:bg-gray-50/50 transition-colors group">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-xl bg-gray-50 flex items-center justify-center text-gray-400 group-hover:text-blue-600 transition-colors">
                        <Car size={24} />
                      </div>
                      <div>
                        <p className="text-sm font-bold text-gray-900">{vehicle.marque}</p>
                        <p className="text-xs text-gray-400 font-medium">ID: #{vehicle.id}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <StatusBadge status={vehicle.statut} />
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-1.5 text-sm font-bold text-gray-900">
                      <Tag size={14} className="text-gray-400" />
                      ${vehicle.prixJour}
                    </div>
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
                <td colSpan={4} className="px-6 py-12 text-center text-gray-400 font-medium">
                  No vehicles found matching your search.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
