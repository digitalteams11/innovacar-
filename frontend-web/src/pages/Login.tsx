import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { CarFront, Loader2 } from 'lucide-react';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login({ email, password });
    } catch (err: any) {
      setError('Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="animate-fade" style={{ padding: '2rem 1.5rem', minHeight: '80vh', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <div style={{ textAlign: 'center', marginBottom: '2.5rem' }}>
        <div style={{ display: 'inline-flex', padding: '1rem', background: 'var(--primary)', color: 'white', borderRadius: '1.25rem', marginBottom: '1rem', boxShadow: '0 10px 15px -3px rgba(37, 99, 235, 0.4)' }}>
          <CarFront size={40} />
        </div>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 800 }}>Welcome Back</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.5rem' }}>Login to access your mobile portal</p>
      </div>

      {error && (
        <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '1rem', borderRadius: '1rem', marginBottom: '1.5rem', fontSize: '0.875rem', fontWeight: 600, textAlign: 'center' }}>
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.5rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Email Address</label>
          <input 
            type="email" 
            placeholder="admin@test.com"
            required
            style={{ width: '100%', padding: '1rem', borderRadius: '1rem', border: '1px solid var(--border)', background: 'white', fontSize: '1rem', outline: 'none' }}
            value={email}
            onChange={e => setEmail(e.target.value)}
          />
        </div>
        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.5rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Password</label>
          <input 
            type="password" 
            placeholder="••••••••"
            required
            style={{ width: '100%', padding: '1rem', borderRadius: '1rem', border: '1px solid var(--border)', background: 'white', fontSize: '1rem', outline: 'none' }}
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
        </div>
        <button 
          type="submit" 
          disabled={loading}
          style={{ background: 'var(--primary)', color: 'white', border: 'none', padding: '1rem', borderRadius: '1rem', fontSize: '1rem', fontWeight: 700, marginTop: '1rem', boxShadow: '0 4px 6px -1px rgba(37, 99, 235, 0.2)', display: 'flex', justifyContent: 'center', alignItems: 'center' }}
        >
          {loading ? <Loader2 className="animate-spin" /> : 'Sign In'}
        </button>
      </form>
    </div>
  );
}
