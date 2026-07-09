import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';

interface PermissionState {
  role: string;
  permissions: Set<string>;
  loading: boolean;
  hasPermission: (code: string) => boolean;
}

const PermissionContext = createContext<PermissionState>({
  role: '',
  permissions: new Set(),
  loading: true,
  hasPermission: () => false,
});

export function PermissionProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isSuperAdmin } = useAuth();
  const [role, setRole] = useState('');
  const [permissions, setPermissions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated || isSuperAdmin) {
      setRole('');
      setPermissions([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    api.get('/permissions/me')
      .then(({ data }) => {
        setRole(data.role || '');
        setPermissions(data.permissions || []);
      })
      .catch(() => { /* silently ignore — user will be redirected to login */ })
      .finally(() => setLoading(false));
  }, [isAuthenticated, isSuperAdmin]);

  const value = useMemo(() => {
    const permissionSet = new Set(permissions);
    return {
      role,
      permissions: permissionSet,
      loading,
      hasPermission: (code: string) => permissionSet.has(code),
    };
  }, [role, permissions, loading]);

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>;
}

export const usePermissions = () => useContext(PermissionContext);
