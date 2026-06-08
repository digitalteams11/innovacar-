import { useState, useEffect } from 'react';
import { Car, Fuel, Gauge, AlertCircle, Check, Loader2 } from 'lucide-react';
import api from '../../api/axios';

interface Vehicle {
  id: number;
  marque: string;
  category: string;
  plate: string;
  fuel: string;
  transmission: string;
  year: number;
  color: string;
  prixJour: number;
  prixSemaine?: number;
  prixMois?: number;
  depositAmount?: number;
  insuranceFees?: number;
  deliveryFees?: number;
  extraMileageCost?: number;
  statut: string;
  imageUrl?: string;
  gpsEnabled?: boolean;
}

interface SmartVehicleSelectorProps {
  startDate: string;
  startTime?: string;
  endDate: string;
  endTime?: string;
  value?: number;
  onSelect: (vehicle: Vehicle) => void;
}

export default function SmartVehicleSelector({
  startDate, startTime = '09:00', endDate, endTime = '18:00', value, onSelect
}: SmartVehicleSelectorProps) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<Vehicle | null>(null);

  useEffect(() => {
    if (startDate && endDate) {
      fetchAvailableVehicles();
    }
  }, [startDate, startTime, endDate, endTime]);

  useEffect(() => {
    if (value && vehicles.length > 0) {
      const v = vehicles.find((ve) => ve.id === value);
      if (v) setSelectedVehicle(v);
    }
  }, [value, vehicles]);

  const fetchAvailableVehicles = async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/availability/vehicles', {
        params: { startDate, startTime, endDate, endTime }
      });
      setVehicles(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelect = (vehicle: Vehicle) => {
    setSelectedVehicle(vehicle);
    onSelect(vehicle);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 size={24} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (!startDate || !endDate) {
    return (
      <div className="flex items-center gap-2 p-4 bg-warning-50 text-warning-600 rounded-xl text-sm">
        <AlertCircle size={16} />
        <span>Please select rental dates first to see available vehicles</span>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-[320px] overflow-y-auto pr-1">
        {vehicles.map((vehicle) => {
          const isSelected = selectedVehicle?.id === vehicle.id;
          return (
            <button
              key={vehicle.id}
              onClick={() => handleSelect(vehicle)}
              className={`relative flex items-start gap-3 p-4 rounded-2xl border-2 text-left transition-all ${
                isSelected
                  ? 'border-brand-400 bg-brand-50/50 shadow-sm'
                  : 'border-slate-100 bg-white hover:border-slate-200 hover:shadow-sm'
              }`}
            >
              {isSelected && (
                <div className="absolute top-2 right-2 w-6 h-6 bg-brand-500 rounded-full flex items-center justify-center">
                  <Check size={14} className="text-white" />
                </div>
              )}
              <div className="w-12 h-12 bg-slate-100 rounded-xl flex items-center justify-center text-slate-400 shrink-0">
                {vehicle.imageUrl ? (
                  <img src={vehicle.imageUrl} alt="" className="w-full h-full object-cover rounded-xl" />
                ) : (
                  <Car size={22} />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-[#1e293b]">{vehicle.marque}</p>
                <p className="text-[11px] text-slate-400 mt-0.5">{vehicle.category} • {vehicle.year} • {vehicle.color}</p>
                <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1.5">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1"><Fuel size={9} /> {vehicle.fuel}</span>
                  <span className="text-[10px] text-slate-400 flex items-center gap-1"><Gauge size={9} /> {vehicle.transmission}</span>
                  {vehicle.gpsEnabled && <span className="text-[10px] text-brand-400 font-bold">GPS</span>}
                </div>
                <div className="flex items-baseline gap-1.5 mt-2">
                  <span className="text-lg font-black text-brand-500">{vehicle.prixJour}</span>
                  <span className="text-[10px] text-slate-400 font-medium">MAD/day</span>
                </div>
              </div>
            </button>
          );
        })}
      </div>
      {vehicles.length === 0 && (
        <div className="text-center py-6 text-slate-400 text-sm">
          No available vehicles for the selected dates
        </div>
      )}
    </div>
  );
}
