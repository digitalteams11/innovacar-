import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';

interface User {
  id: number;
  email: string;
  role: string;
  tenantId: number;
  tenantName: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (credentials: any) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    const storedUser = localStorage.getItem('user');

    if (storedToken && storedUser) {
      setToken(storedToken);
      setUser(JSON.parse(storedUser));
    }

    const handleAuthError = () => {
      logout();
    };

    window.addEventListener('auth-error', handleAuthError);
    return () => window.removeEventListener('auth-error', handleAuthError);
  }, []);

  const login = async (credentials: any) => {
    try {
      const { data } = await api.post('/auth/login', credentials);
      localStorage.setItem('token', data.accessToken);
      localStorage.setItem('user', JSON.stringify(data));
      setToken(data.accessToken);
      setUser(data);
      navigate('/');
    } catch (err: any) {
      console.error('Login failed:', err.response?.status, err.response?.data);
      throw err;
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
    navigate('/login');
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
