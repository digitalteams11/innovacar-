import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import type { ReactNode } from 'react';
import api from '../api/axios';
import { generateId } from '../lib/generateId';
import i18n from '../i18n';

export type UserRole =
  | 'SUPER_ADMIN'
  | 'AGENCY_OWNER'
  | 'ADMIN'
  | 'MANAGER'
  | 'EMPLOYEE'
  | 'ACCOUNTANT'
  | 'FLEET_MANAGER'
  | 'CUSTOM'
  | 'RECEPTIONIST'
  | 'VIEWER'
  | 'AGENT'
  | 'CLIENT';

export interface AccountAccess {
  canUsePlatform: boolean;
  canCreateContracts: boolean;
  canCreateReservations: boolean;
  canManageVehicles: boolean;
  canAccessBilling: boolean;
  canAccessSupport: boolean;
  blockedReason: string | null;
}

export interface User {
  id: number;
  email: string;
  role: UserRole;
  tenantId: number;
  tenantName: string;
  emailVerified?: boolean;
  twoFactorEnabled?: boolean;
  language?: string;
  themeMode?: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  jobTitle?: string;
  avatarUrl?: string;
  agencyStatus?: string;
  subscriptionStatus?: string;
  planCode?: string;
  planName?: string;
  accountAccess?: AccountAccess;
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
  profile: UserProfile;
  tenant: TenantBranding | null;
  login: (credentials: any) => Promise<User>;
  logout: () => Promise<void>;
  register: (data: any) => Promise<User>;
  googleLogin: (idToken: string) => Promise<User>;
  refreshAccessToken: () => Promise<boolean>;
  updateProfile: (profile: Partial<UserProfile>) => void;
  updateCurrentUser: (updatedUser: Partial<User>) => void;
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
  const refreshPromiseRef = useRef<Promise<boolean> | null>(null);

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

  // Bootstrap the UI by verifying the current origin's bearer token via /me.
  // localhost and 192.168.x.x have separate localStorage, so opening the LAN
  // URL for the first time correctly stays unauthenticated without calling
  // protected APIs until the user logs in on that origin.
  useEffect(() => {
    let cancelled = false;
    const storedUser = localStorage.getItem('user');
    const storedAccessToken = localStorage.getItem('token');
    const storedRefreshToken = localStorage.getItem('refreshToken');

    if (storedAccessToken) {
      api.get('/me')
        .then(({ data }) => {
          if (cancelled) return;
          const currentUser = normalizeUser(data);
          localStorage.setItem('user', JSON.stringify(currentUser));
          setUser(currentUser);
          fetchTenantBranding();
        })
        .catch((err: any) => {
          if (cancelled) return;
          // Only drop the session on a confirmed auth rejection (401, even
          // after the interceptor's silent refresh attempt already failed).
          // Network errors / 5xx must never log the user out.
          if (err?.response?.status === 401) {
            clearStorage();
            setUser(null);
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    } else {
      if (storedUser || storedRefreshToken) {
        clearStorage();
        setUser(null);
      }
      setLoading(false);
    }

    const handleAuthError = () => {
      clearStorage();
      setUser(null);
      // NEVER redirect to login from public contract signing pages
      const isPublicSigningPage = window.location.hash.startsWith('#/contract-sign') || window.location.hash.startsWith('#/inspection');
      if (!isPublicSigningPage && window.location.pathname !== '/login' && !window.location.hash.startsWith('#/login')) {
        window.location.href = '/#/login';
      }
    };

    const handleTenantUpdated = () => {
      fetchTenantBranding();
    };

    // Any API call can surface a freshly-suspended/blocked agency or an
    // expired/suspended subscription — update accountAccess immediately
    // so the route guard redirects to the lock screen on this same request
    // cycle, without waiting for the next /me poll or a logout/login.
    const handleAccountBlocked = (event: Event) => {
      const detail = (event as CustomEvent).detail as { errorCode: string; data?: any } | undefined;
      if (!detail) return;
      setUser((current) => {
        if (!current) return current;
        return {
          ...current,
          agencyStatus: detail.data?.agencyStatus || current.agencyStatus,
          subscriptionStatus: detail.data?.subscriptionStatus || current.subscriptionStatus,
          accountAccess: {
            canUsePlatform: false,
            canCreateContracts: false,
            canCreateReservations: false,
            canManageVehicles: false,
            canAccessBilling: true,
            canAccessSupport: true,
            blockedReason: detail.errorCode,
          },
        };
      });
    };

    window.addEventListener('auth-error', handleAuthError);
    window.addEventListener('tenant-updated', handleTenantUpdated);
    window.addEventListener('account-blocked', handleAccountBlocked);
    return () => {
      cancelled = true;
      window.removeEventListener('auth-error', handleAuthError);
      window.removeEventListener('tenant-updated', handleTenantUpdated);
      window.removeEventListener('account-blocked', handleAccountBlocked);
    };
  }, [fetchTenantBranding]);

  // The user's saved UI language is a personal account preference — apply it
  // as soon as it's known (bootstrap /me, login, or a later preference save)
  // so the whole app (including RTL direction) follows the account, not just
  // this browser's localStorage.
  useEffect(() => {
    if (user?.language && user.language !== i18n.language) {
      i18n.changeLanguage(user.language);
    }
  }, [user?.language]);

  const clearStorage = () => {
    // Remove legacy token storage left by pre-cookie releases.
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
    twoFactorEnabled: data.twoFactorEnabled,
    language: data.language,
    themeMode: data.themeMode,
    firstName: data.firstName,
    lastName: data.lastName,
    phoneNumber: data.phoneNumber,
    jobTitle: data.jobTitle,
    avatarUrl: data.avatarUrl,
    agencyStatus: data.agencyStatus,
    subscriptionStatus: data.subscriptionStatus,
    planCode: data.planCode,
    planName: data.planName,
    accountAccess: data.accountAccess,
  });

  const setAuthData = (data: any): User => {
    const payload = (data && data.success !== undefined && data.data) ? data.data : data;
    const userPayload = payload?.user || payload;
    if (payload?.accessToken) localStorage.setItem('token', payload.accessToken);
    else localStorage.removeItem('token');
    if (payload?.refreshToken) localStorage.setItem('refreshToken', payload.refreshToken);
    else localStorage.removeItem('refreshToken');
    if (payload?.expiresIn) {
      localStorage.setItem('tokenExpiry', String(Date.now() + payload.expiresIn * 1000));
    } else {
      localStorage.removeItem('tokenExpiry');
    }
    const user = normalizeUser(userPayload);
    localStorage.setItem('user', JSON.stringify(user));
    // Also sync profile
    const profile = buildProfile(user);
    localStorage.setItem('user_profile', JSON.stringify(profile));
    setUser(user);
    fetchTenantBranding();
    // The login/register response doesn't carry agencyStatus/accountAccess —
    // only /me computes those fresh from the DB. Enrich right away so a
    // just-suspended agency is locked out from the very first page load,
    // not only after the next unrelated /me call happens to fire.
    api.get('/me')
      .then(({ data }) => {
        setUser((current) => current && current.id === user.id ? { ...current, ...normalizeUser(data) } : current);
      })
      .catch(() => undefined);
    return user;
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
    let deviceFingerprint = localStorage.getItem('deviceFingerprint');
    if (!deviceFingerprint) {
      deviceFingerprint = generateId('device');
      localStorage.setItem('deviceFingerprint', deviceFingerprint);
    }
    const deviceName = `${navigator.platform || 'Device'} · ${navigator.userAgent.includes('Mobile') ? 'Mobile' : 'Browser'}`;
    const { data } = await api.post('/auth/login', {
      ...credentials,
      deviceFingerprint,
      deviceName,
    });
    return setAuthData(data);
  };

  const register = async (registerData: any) => {
    const { data } = await api.post('/auth/register', registerData);
    return setAuthData(data);
  };

  const googleLogin = async (idToken: string) => {
    const { data } = await api.post('/auth/google', { idToken });
    return setAuthData(data);
  };

  const refreshAccessToken = useCallback(async (): Promise<boolean> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return false;

    // If a refresh is already in flight, return that promise
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current;
    }

    const promise = api.post('/auth/refresh', { refreshToken })
      .then(() => true)
      .catch(() => {
        clearStorage();
        setUser(null);
        window.dispatchEvent(new Event('auth-error'));
        return false;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });

    refreshPromiseRef.current = promise;
    return promise;
  }, []);

  const logout = async () => {
    try {
      await api.post('/auth/logout', {});
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
      const next = {
        ...prev,
        firstName: names[0] || prev.firstName,
        lastName: names.slice(1).join(' ') || prev.lastName,
        phoneNumber: updated.phone || prev.phoneNumber,
        jobTitle: updated.jobTitle || prev.jobTitle,
        avatarUrl: updated.avatar || prev.avatarUrl,
      };
      localStorage.setItem('user', JSON.stringify(next));
      return next;
    });
  }, []);

  // Merges fields straight from a backend user DTO (e.g. the response of a
  // profile/avatar save) into the global user state — the single source of
  // truth every topbar/sidebar/settings component reads from.
  const updateCurrentUser = useCallback((updatedUser: Partial<User>) => {
    setUser(prev => {
      if (!prev) return prev;
      const next = { ...prev, ...updatedUser };
      localStorage.setItem('user', JSON.stringify(next));
      localStorage.setItem('user_profile', JSON.stringify(buildProfile(next)));
      return next;
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

  // Always derived from `user` so any update to it (login, refresh, profile
  // save, avatar upload) is reflected synchronously everywhere — no stale
  // localStorage-only state, no same-tab event wiring needed.
  const profile = useMemo<UserProfile>(() => buildProfile(user || {}), [user]);

  return (
    <AuthContext.Provider value={{
      user,
      profile,
      tenant,
      login,
      logout,
      register,
      googleLogin,
      refreshAccessToken,
      updateProfile,
      updateCurrentUser,
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
