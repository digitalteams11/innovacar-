import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { TrendingUp, Car, CheckCircle2 } from 'lucide-react';

export default function Dashboard() {
  const [stats, setStats] = useState({ totalVehicles: 0, rentedVehicles: 0, totalRevenue: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/dashboard')
      .then(res => setStats(res.data))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="animate-fade">
      <div className="card" style={{ background: 'linear-gradient(135deg, #2563eb, #1d4ed8)', color: 'white', border: 'none' }}>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '0.5rem' }}>Business Overview</h2>
        <p style={{ opacity: 0.8, fontSize: '0.875rem' }}>Track your fleet performance on the go.</p>
        <div style={{ marginTop: '1.5rem', display: 'flex', alignItems: 'baseline', gap: '0.5rem' }}>
          <span style={{ fontSize: '2.5rem', fontWeight: 800 }}>${stats.totalRevenue.toLocaleString()}</span>
          <span style={{ opacity: 0.8, fontSize: '0.875rem', fontWeight: 600 }}>Total Revenue</span>
        </div>
      </div>

      <div className="stat-grid">
        <div className="stat-card">
          <div style={{ color: '#2563eb', marginBottom: '0.5rem' }}><Car size={20} /></div>
          <span className="stat-val">{stats.totalVehicles}</span>
          <span className="stat-label">Total Fleet</span>
        </div>
        <div className="stat-card">
          <div style={{ color: '#10b981', marginBottom: '0.5rem' }}><CheckCircle2 size={20} /></div>
          <span className="stat-val">{stats.rentedVehicles}</span>
          <span className="stat-label">Rented</span>
        </div>
      </div>

      <h3 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '1rem' }}>Quick Actions</h3>
      <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <div style={{ padding: '0.75rem', borderRadius: '1rem', background: '#fef3c7', color: '#d97706' }}>
          <TrendingUp size={24} />
        </div>
        <div>
          <h4 style={{ fontWeight: 700 }}>Growth Insight</h4>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Revenue up 12% this month</p>
        </div>
      </div>
    </div>
  );
}
