import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { Car, Tag } from 'lucide-react';

interface Vehicle {
  id: number;
  marque: string;
  prixJour: number;
  statut: 'AVAILABLE' | 'RENTED' | 'MAINTENANCE';
}

export default function Vehicles() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/vehicles')
      .then(res => setVehicles(res.data))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="animate-fade">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 800 }}>Fleet Inventory</h2>
        <span style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--primary)' }}>{vehicles.length} Units</span>
      </div>

      {loading ? (
        [1, 2, 3, 4].map(i => (
          <div key={i} className="card" style={{ height: '80px', opacity: 0.5, background: '#eee' }}></div>
        ))
      ) : (
        vehicles.map(vehicle => (
          <div key={vehicle.id} className="card">
            <div className="vehicle-item">
              <div className="vehicle-img">
                <Car size={24} />
              </div>
              <div className="vehicle-info" style={{ flex: 1 }}>
                <h4>{vehicle.marque}</h4>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', marginTop: '0.25rem' }}>
                  <Tag size={12} style={{ color: 'var(--text-muted)' }} />
                  <span style={{ fontSize: '0.875rem', fontWeight: 700 }}>${vehicle.prixJour}/day</span>
                </div>
              </div>
              <span className={`badge ${
                vehicle.statut === 'AVAILABLE' ? 'badge-success' : 
                vehicle.statut === 'RENTED' ? 'badge-warning' : 'badge-danger'
              }`}>
                {vehicle.statut}
              </span>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
