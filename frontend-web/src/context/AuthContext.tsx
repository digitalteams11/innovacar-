import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import api from '../api/axios';

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'EMPLOYEE' | 'AGENT' | 'CLIENT';

export interface User {
  id: number;
  email: string;
  role: UserRole;
  tenantId: number;
  tenantName: string;
  emailVerified?: boolean;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  jobTitle?: string;
  avatarUrl?: string;
}

export interface UserProfile {
  fullName: string;
  email: string;
  phone: string;
  jobTitle: string;
  avatar: string;
}

export interface TenantBranding {
  name: string;
  logoUrl: string;
  agencySignature: string;
  agencyStampUrl: string;
  termsAndConditions: string;
}

interface AuthContextType {
  user: User | null;
  tenant: TenantBranding | null;
  login: (credentials: any) => Promise<User>;
  logout: () => Promise<void>;
  register: (data: any) => Promise<User>;
  googleLogin: (idToken: string) => Promise<User>;
  refreshAccessToken: () => Promise<string | null>;
  updateProfile: (profile: Partial<UserProfile>) => void;
  getProfile: () => UserProfile;
  isAuthenticated: boolean;
  isSuperAdmin: boolean;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<TenantBranding | null>(null);
  const [loading, setLoading] = useState(true);
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null);

  const fetchTenantBranding = useCallback(async () => {
    try {
      const { data } = await api.get('/agency');
      setTenant({
        name: data.name || '',
        logoUrl: data.logoUrl || '',
        agencySignature: data.agencySignature || '',
        agencyStampUrl: data.agencyStampUrl || '',
        termsAndConditions: data.termsAndConditions || '',
      });
    } catch {
      setTenant(null);
    }
  }, []);

  // Initialize from localStorage
  useEffect(() => {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      try {
        const parsed = JSON.parse(storedUser);
        setUser(normalizeUser(parsed));
        fetchTenantBranding();
      } catch {
        clearStorage();
      }
    }
    setLoading(false);

    const handleAuthError = () => {
      clearStorage();
      setUser(null);
      // NEVER redirect to login from public contract signing pages
      const isPublicSigningPage = window.location.hash.startsWith('#/contract-sign');
      if (!isPublicSigningPage && window.location.pathname !== '/login' && !window.location.hash.startsWith('#/login')) {
        window.location.href = '/#/login';
      }
    };

    const handleTenantUpdated = () => {
      fetchTenantBranding();
    };

    window.addEventListener('auth-error', handleAuthError);
    window.addEventListener('tenant-updated', handleTenantUpdated);
    return () => {
      window.removeEventListener('auth-error', handleAuthError);
      window.removeEventListener('tenant-updated', handleTenantUpdated);
    };
  }, [fetchTenantBranding]);

  const clearStorage = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    localStorage.removeItem('tokenExpiry');
    localStorage.removeItem('user_profile');
    setTenant(null);
  };

  const normalizeUser = (data: any): User => ({
    id: data.userId || data.id,
    email: data.email,
    role: data.role,
    tenantId: data.tenantId,
    tenantName: data.tenantName,
    emailVerified: data.emailVerified,
    firstName: data.firstName,
    lastName: data.lastName,
    phoneNumber: data.phoneNumber,
    jobTitle: data.jobTitle,
    avatarUrl: data.avatarUrl,
  });

  const setAuthData = (data: any) => {
    localStorage.setItem('token', data.accessToken);
    if (data.refreshToken) {
      localStorage.setItem('refreshToken', data.refreshToken);
    }
    const user = normalizeUser(data);
    localStorage.setItem('user', JSON.stringify(user));
    if (data.expiresIn) {
      const expiry = Date.now() + data.expiresIn * 1000;
      localStorage.setItem('tokenExpiry', expiry.toString());
    }
    // Also sync profile
    const profile = buildProfile(user);
    localStorage.setItem('user_profile', JSON.stringify(profile));
    setUser(user);
    fetchTenantBranding();
  };

  const buildProfile = (data: any): UserProfile => ({
    fullName: data.firstName && data.lastName
      ? `${data.firstName} ${data.lastName}`
      : data.firstName || data.email?.split('@')[0] || 'User',
    email: data.email || '',
    phone: data.phoneNumber || '',
    jobTitle: data.jobTitle || 'Administrator',
    avatar: data.avatarUrl || 'https://ui-avatars.com/api/?name=' + encodeURIComponent(data.email?.split('@')[0] || 'U') + '&background=4318ff&color=fff',
  });

  const login = async (credentials: any) => {
    const { data } = await api.post('/auth/login', credentials);
    setAuthData(data);
    return data;
  };

  const register = async (registerData: any) => {
    const { data } = await api.post('/auth/register', registerData);
    setAuthData(data);
    return data;
  };

  const googleLogin = async (idToken: string) => {
    const { data } = await api.post('/auth/google', { idToken });
    setAuthData(data);
    return data;
  };

  const refreshAccessToken = useCallback(async (): Promise<string | null> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      clearStorage();
      setUser(null);
      window.dispatchEvent(new Event('auth-error'));
      return null;
    }

    // If a refresh is already in flight, return that promise
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current;
    }

    const promise = api.post('/auth/refresh', { refreshToken })
      .then(({ data }) => {
        localStorage.setItem('token', data.accessToken);
        if (data.refreshToken) {
          localStorage.setItem('refreshToken', data.refreshToken);
        }
        if (data.expiresIn) {
          const expiry = Date.now() + data.expiresIn * 1000;
          localStorage.setItem('tokenExpiry', expiry.toString());
        }
        return data.accessToken as string;
      })
      .catch(() => {
        clearStorage();
        setUser(null);
        window.dispatchEvent(new Event('auth-error'));
        return null;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });

    refreshPromiseRef.current = promise;
    return promise;
  }, []);

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken });
      }
    } catch {
      // Ignore logout errors
    } finally {
      clearStorage();
      setUser(null);
    }
  };

  const updateProfile = useCallback((profile: Partial<UserProfile>) => {
    const existing = getProfileRaw();
    const updated = { ...existing, ...profile };
    localStorage.setItem('user_profile', JSON.stringify(updated));
    // Also update the user object if fields map
    setUser(prev => {
      if (!prev) return prev;
      const names = updated.fullName?.split(' ') || [];
      return {
        ...prev,
        firstName: names[0] || prev.firstName,
        lastName: names.slice(1).join(' ') || prev.lastName,
        phoneNumber: updated.phone || prev.phoneNumber,
        jobTitle: updated.jobTitle || prev.jobTitle,
        avatarUrl: updated.avatar || prev.avatarUrl,
      };
    });
  }, []);

  const getProfile = useCallback((): UserProfile => {
    return getProfileRaw();
  }, []);

  const getProfileRaw = (): UserProfile => {
    const stored = localStorage.getItem('user_profile');
    if (stored) {
      try {
        return JSON.parse(stored);
      } catch { /* fall through */ }
    }
    const u = user;
    const names = (u?.firstName || u?.lastName)
      ? `${u?.firstName || ''} ${u?.lastName || ''}`.trim()
      : u?.email?.split('@')[0] || 'User';
    return {
      fullName: names,
      email: u?.email || '',
      phone: u?.phoneNumber || '',
      jobTitle: u?.jobTitle || 'Administrator',
      avatar: u?.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(names)}&background=4318ff&color=fff`,
    };
  };

  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  return (
    <AuthContext.Provider value={{
      user,
      tenant,
      login,
      logout,
      register,
      googleLogin,
      refreshAccessToken,
      updateProfile,
      getProfile,
      isAuthenticated: !!user,
      isSuperAdmin,
      loading,
    }}>
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
