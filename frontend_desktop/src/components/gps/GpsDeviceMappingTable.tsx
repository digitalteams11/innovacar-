import { useState } from 'react';
import { Car, Loader2, Link2, Battery, MapPinned } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import GpsFieldHelp from './GpsFieldHelp';

export interface GpsSyncDevice {
  deviceId: string;
  name: string;
  imei?: string;
  status?: string;
  latitude?: number;
  longitude?: number;
  speed?: number;
}

export interface GpsVehicleOption {
  id: number;
  marque: string;
  plate: string;
  gpsDeviceId?: string | null;
}

interface GpsDeviceMappingTableProps {
  devices: GpsSyncDevice[];
  vehicles: GpsVehicleOption[];
  onLinked: (vehicleId: number, deviceId: string) => void;
}

export default function GpsDeviceMappingTable({ devices, vehicles, onLinked }: GpsDeviceMappingTableProps) {
  const { showToast } = useToast();
  const [selections, setSelections] = useState<Record<string, string>>({});
  const [linkingDeviceId, setLinkingDeviceId] = useState<string | null>(null);

  const vehicleByDeviceId = (deviceId: string) =>
    vehicles.find((v) => v.gpsDeviceId === deviceId);

  const handleLink = async (device: GpsSyncDevice) => {
    const selectedId = selections[device.deviceId] ?? String(vehicleByDeviceId(device.deviceId)?.id ?? '');
    if (!selectedId) {
      showToast('Please select a vehicle to link this device.', 'warning');
      return;
    }

    setLinkingDeviceId(device.deviceId);
    try {
      const previousVehicle = vehicleByDeviceId(device.deviceId);
      if (previousVehicle && String(previousVehicle.id) !== selectedId) {
        await api.put(`/gps/vehicles/${previousVehicle.id}`, {
          gpsDeviceId: null,
          gpsImei: null,
          gpsEnabled: false,
        });
      }

      await api.put(`/gps/vehicles/${selectedId}`, {
        gpsDeviceId: device.deviceId,
        gpsImei: device.imei || null,
        gpsEnabled: true,
      });

      onLinked(Number(selectedId), device.deviceId);
      showToast('Device linked to vehicle successfully.', 'success');
    } catch {
      showToast('Unable to link device to vehicle. Please try again later.', 'error');
    } finally {
      setLinkingDeviceId(null);
    }
  };

  if (devices.length === 0) {
    return (
      <div className="text-center py-8 text-sm text-slate-400">
        No devices found yet. Click "Sync Devices" after a successful connection to load your provider's device list.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto -mx-3 sm:mx-0">
      <table className="w-full text-sm min-w-[640px]">
        <thead>
          <tr className="text-left text-xs font-medium text-slate-400 border-b border-[#e8e6e1]/60">
            <th className="py-2 px-3">Device / IMEI</th>
            <th className="py-2 px-3">Last Location</th>
            <th className="py-2 px-3">Status</th>
            <th className="py-2 px-3">Linked Vehicle</th>
            <th className="py-2 px-3">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[#e8e6e1]/40">
          {devices.map((device) => {
            const linked = vehicleByDeviceId(device.deviceId);
            const currentSelection = selections[device.deviceId] ?? (linked ? String(linked.id) : '');
            return (
              <tr key={device.deviceId}>
                <td className="py-2.5 px-3">
                  <p className="font-semibold text-[#1e293b]">{device.name || device.deviceId}</p>
                  <p className="text-xs text-slate-400 font-mono">{device.imei || device.deviceId}</p>
                </td>
                <td className="py-2.5 px-3 text-xs text-slate-500">
                  {device.latitude != null && device.longitude != null ? (
                    <span className="inline-flex items-center gap-1">
                      <MapPinned size={12} />
                      {device.latitude.toFixed(4)}, {device.longitude.toFixed(4)}
                    </span>
                  ) : (
                    '—'
                  )}
                  {device.speed != null && (
                    <span className="inline-flex items-center gap-1 ml-2">
                      <Battery size={12} /> {device.speed} km/h
                    </span>
                  )}
                </td>
                <td className="py-2.5 px-3 text-xs">
                  <span className="px-2 py-0.5 rounded-full bg-slate-50 text-slate-500 font-medium">
                    {device.status || 'UNKNOWN'}
                  </span>
                </td>
                <td className="py-2.5 px-3">
                  <select
                    value={currentSelection}
                    onChange={(e) => setSelections((prev) => ({ ...prev, [device.deviceId]: e.target.value }))}
                    className="w-full max-w-[200px] px-2.5 py-1.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-lg text-xs focus:outline-none focus:ring-2 ring-brand-100"
                  >
                    <option value="">Select a vehicle…</option>
                    {vehicles.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.marque} — {v.plate}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="py-2.5 px-3">
                  <button
                    onClick={() => handleLink(device)}
                    disabled={linkingDeviceId === device.deviceId}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brand-500 text-white rounded-lg text-xs font-medium hover:bg-brand-600 transition-all disabled:opacity-50"
                  >
                    {linkingDeviceId === device.deviceId ? (
                      <Loader2 size={13} className="animate-spin" />
                    ) : (
                      <Link2 size={13} />
                    )}
                    {linked ? 'Change' : 'Link'}
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <div className="flex items-center gap-1.5 mt-3 px-3 sm:px-0 text-xs text-slate-400">
        <Car size={13} />
        <span>Each device should be linked to one vehicle.</span>
        <GpsFieldHelp fieldKey="vehicleLinking" />
      </div>
    </div>
  );
}
