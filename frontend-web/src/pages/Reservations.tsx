import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { Calendar, User } from 'lucide-react';

interface Reservation {
  id: number;
  clientNom: string;
  vehicleMarque: string;
  dateDebut: string;
  dateFin: string;
  prixTotal: number;
  statut: 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
}

export default function Reservations() {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/reservations')
      .then(res => setReservations(res.data))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="animate-fade">
      <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1.5rem' }}>Active Bookings</h2>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>Loading...</div>
      ) : reservations.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem' }}>
          <Calendar size={48} style={{ margin: '0 auto 1rem', color: 'var(--border)' }} />
          <p style={{ fontWeight: 600, color: 'var(--text-muted)' }}>No reservations found</p>
        </div>
      ) : (
        reservations.map(res => (
          <div key={res.id} className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <div style={{ padding: '0.4rem', background: 'var(--bg-main)', borderRadius: '50%' }}>
                  <User size={16} />
                </div>
                <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>{res.clientNom}</span>
              </div>
              <span className={`badge ${
                res.statut === 'CONFIRMED' ? 'badge-success' : 
                res.statut === 'PENDING' ? 'badge-warning' : 'badge-danger'
              }`}>
                {res.statut}
              </span>
            </div>
            
            <div style={{ padding: '0.75rem', background: 'var(--bg-main)', borderRadius: '0.75rem', marginBottom: '1rem' }}>
              <p style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>Vehicle</p>
              <p style={{ fontWeight: 700 }}>{res.vehicleMarque}</p>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {new Date(res.dateDebut).toLocaleDateString()} - {new Date(res.dateFin).toLocaleDateString()}
              </div>
              <div style={{ fontWeight: 800, color: 'var(--primary)', fontSize: '1.1rem' }}>
                ${res.prixTotal}
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
