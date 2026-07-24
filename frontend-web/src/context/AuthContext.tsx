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
  login: (credentials: any) => Promise<any>;
  verify2FA: (challengeToken: string, code: string, recoveryCode?: string, trustDevice?: boolean) => Promise<User>;
  verifyEmailOtp2FA: (challengeToken: string, code: string, trustDevice?: boolean) => Promise<User>;
  logout: () => Promise<void>;
  register: (data: any) => Promise<User>;
  googleLogin: (idToken: string) => Promise<any>;
  refreshAccessToken: () => Promise<boolean>;
  updateProfile: (profile: Partial<UserProfile>) => void;
  updateCurrentUser: (updatedUser: Partial<User>) => void;
  getProfile: () => UserProfile;
  isAuthenticated: boolean;
  isSuperAdmin: boolean;
  loading: boolean;
  sessionExpired: boolean;
  signInAgain: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const getStoredAccessToken = () =>
  localStorage.getItem('accessToken')
  || sessionStorage.getItem('accessToken')
  || localStorage.getItem('token')
  || sessionStorage.getItem('token');

const getStoredRefreshToken = () =>
  localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');

const storeAccessToken = (token: string) => {
  localStorage.setItem('token', token);
  localStorage.setItem('accessToken', token);
};

const logAuthDebug = (details: Record<string, unknown>) => {
  if (!import.meta.env.DEV) return;
  const token = getStoredAccessToken();
  console.info('[AUTH_DEBUG]', {
    origin: window.location.origin,
    apiBaseUrl: api.defaults.baseURL,
    tokenExists: Boolean(token),
    tokenLength: token?.length || 0,
    ...details,
  });
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [tenant, setTenant] = useState<TenantBranding | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const refreshPromiseRef = useRef<Promise<boolean> | null>(null);

  // Single controlled entry point for the "session expired" flow — shows one
  // modal no matter how many requests failed with 401 at once, and remembers
  // the route the user was on so "Sign in again" can return them to it.
  const triggerSessionExpired = useCallback(() => {
    const isPublicSigningPage = window.location.hash.startsWith('#/contract-sign') || window.location.hash.startsWith('#/inspection');
    if (isPublicSigningPage || window.location.hash.startsWith('#/login')) return;
    const currentRoute = window.location.hash.replace(/^#/, '') || '/dashboard';
    sessionStorage.setItem('postLoginRedirect', currentRoute);
    setSessionExpired(prev => prev || true);
  }, []);

  const signInAgain = useCallback(() => {
    setSessionExpired(false);
    window.location.href = '/#/login';
  }, []);

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
    const storedAccessToken = getStoredAccessToken();
    const storedRefreshToken = getStoredRefreshToken();
    logAuthDebug({ meStatus: 'BOOTSTRAP_START' });

    // Bootstrap watchdog: the /me call below already resolves via axios's own
    // 20s request timeout in the worst case, but that's far longer than a
    // login page should ever sit on a loader. This forces the app out of
    // "checking session" and into the normal unauthenticated/login-visible
    // state well before that, treating a slow bootstrap exactly like a failed
    // one (session state stays whatever it already resolved to, if anything).
    const watchdog = window.setTimeout(() => {
      if (!cancelled) setLoading(false);
    }, 6000);

    if (storedAccessToken) {
      api.get('/me')
        .then(({ data }) => {
          if (cancelled) return;
          const currentUser = normalizeUser(data);
          logAuthDebug({ meStatus: 200, currentUserRole: currentUser.role, agencyId: currentUser.tenantId || (data as any)?.agencyId });
          localStorage.setItem('user', JSON.stringify(currentUser));
          setUser(currentUser);
          fetchTenantBranding();
        })
        .catch((err: any) => {
          if (cancelled) return;
          // Only drop the session on a confirmed auth rejection (401, even
          // after the interceptor's silent refresh attempt already failed).
          // Network errors / 5xx must never log the user out.
          logAuthDebug({ meStatus: err?.response?.status || 'NETWORK_ERROR', currentUserRole: null, agencyId: null });
          if (err?.response?.status === 401) {
            clearStorage();
            setUser(null);
            triggerSessionExpired();
          }
        })
        .finally(() => {
          window.clearTimeout(watchdog);
          if (!cancelled) setLoading(false);
        });
    } else {
      window.clearTimeout(watchdog);
      if (storedUser || storedRefreshToken) {
        logAuthDebug({ meStatus: 'TOKEN_MISSING_FOR_ORIGIN', currentUserRole: null, agencyId: null });
        clearStorage();
        setUser(null);
        window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'warning', message: 'You are not signed in on this browser address. Please login again.' } }));
      }
      setLoading(false);
    }

    const handleAuthError = () => {
      clearStorage();
      setUser(null);
      // NEVER redirect to login from public contract signing pages
      if (window.location.pathname !== '/login') {
        triggerSessionExpired();
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
      window.clearTimeout(watchdog);
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
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
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
    if (payload?.accessToken) storeAccessToken(payload.accessToken);
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
      deviceFingerprint = crypto.randomUUID ? crypto.randomUUID() : generateId('device');
      localStorage.setItem('deviceFingerprint', deviceFingerprint);
    }
    const deviceName = `${navigator.platform || 'Device'} · ${navigator.userAgent.includes('Mobile') ? 'Mobile' : 'Browser'}`;
    const { data } = await api.post('/auth/login', {
      ...credentials,
      deviceFingerprint,
      deviceName,
    });
    const payload = (data && data.success !== undefined && data.data) ? data.data : data;
    // 2FA challenge — return payload without storing tokens
    if (payload?.twoFactorRequired) return payload;
    return setAuthData(data);
  };

  const verify2FA = async (challengeToken: string, code: string, recoveryCode?: string, trustDevice?: boolean) => {
    let deviceFingerprint = localStorage.getItem('deviceFingerprint');
    if (!deviceFingerprint) {
      deviceFingerprint = crypto.randomUUID ? crypto.randomUUID() : generateId('device');
      localStorage.setItem('deviceFingerprint', deviceFingerprint);
    }
    const deviceName = `${navigator.platform || 'Device'} · ${navigator.userAgent.includes('Mobile') ? 'Mobile' : 'Browser'}`;
    const { data } = await api.post('/auth/2fa/verify', {
      challengeToken,
      code: code || '',
      recoveryCode: recoveryCode || '',
      deviceFingerprint,
      deviceName,
      trustDevice: !!trustDevice,
    });
    return setAuthData(data);
  };

  const verifyEmailOtp2FA = async (challengeToken: string, code: string, trustDevice?: boolean) => {
    let deviceFingerprint = localStorage.getItem('deviceFingerprint');
    if (!deviceFingerprint) {
      deviceFingerprint = crypto.randomUUID ? crypto.randomUUID() : generateId('device');
      localStorage.setItem('deviceFingerprint', deviceFingerprint);
    }
    const deviceName = `${navigator.platform || 'Device'} · ${navigator.userAgent.includes('Mobile') ? 'Mobile' : 'Browser'}`;
    const { data } = await api.post('/auth/2fa/email/verify', {
      challengeToken,
      code,
      deviceFingerprint,
      deviceName,
      trustDevice: !!trustDevice,
    });
    return setAuthData(data);
  };

  const register = async (registerData: any) => {
    const { data } = await api.post('/auth/register', registerData);
    return setAuthData(data);
  };

  const googleLogin = async (idToken: string) => {
    const { data } = await api.post('/auth/google', { idToken });
    const payload = (data && data.success !== undefined && data.data) ? data.data : data;
    // 2FA challenge — return payload without storing tokens (same as email login path)
    if (payload?.twoFactorRequired) return payload;
    return setAuthData(data);
  };

  const refreshAccessToken = useCallback(async (): Promise<boolean> => {
    const refreshToken = getStoredRefreshToken();
    if (!refreshToken) return false;

    // If a refresh is already in flight, return that promise
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current;
    }

    const promise = api.post('/auth/refresh', { refreshToken })
      .then(({ data }) => {
        const payload = (data && data.success !== undefined && data.data) ? data.data : data;
        if (payload?.accessToken) storeAccessToken(payload.accessToken);
        if (payload?.refreshToken) localStorage.setItem('refreshToken', payload.refreshToken);
        return true;
      })
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
      const refreshToken = getStoredRefreshToken();
      await api.post('/auth/logout', refreshToken ? { refreshToken } : {});
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
      verify2FA,
      verifyEmailOtp2FA,
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
      sessionExpired,
      signInAgain,
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

