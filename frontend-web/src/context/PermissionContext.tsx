import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';

const PERMISSION_ALIASES: Record<string, string[]> = {
  VIEW_VEHICLES: ['VEHICLE_VIEW'], CREATE_VEHICLE: ['VEHICLE_CREATE'], EDIT_VEHICLE: ['VEHICLE_UPDATE'], DELETE_VEHICLE: ['VEHICLE_DELETE'],
  VIEW_CLIENTS: ['CLIENT_VIEW'], CREATE_CLIENT: ['CLIENT_CREATE'], EDIT_CLIENT: ['CLIENT_UPDATE'], DELETE_CLIENT: ['CLIENT_DELETE'],
  VIEW_RESERVATIONS: ['RESERVATION_VIEW'], CREATE_RESERVATION: ['RESERVATION_CREATE'], EDIT_RESERVATION: ['RESERVATION_UPDATE'], CANCEL_RESERVATION: ['RESERVATION_CANCEL'],
  VIEW_CONTRACTS: ['CONTRACT_VIEW'], CREATE_CONTRACT: ['CONTRACT_CREATE'], EDIT_CONTRACT: ['CONTRACT_UPDATE'], DELETE_CONTRACT: ['CONTRACT_DELETE'],
  SIGN_CONTRACT: ['CONTRACT_QR_SIGNATURE'], COMPLETE_CONTRACT: ['CONTRACT_UPDATE'],
  VIEW_PAYMENTS: ['PAYMENT_VIEW'], RECORD_PAYMENT: ['PAYMENT_CREATE'], VIEW_INVOICES: ['INVOICE_VIEW'], MANAGE_INVOICES: ['INVOICE_EXPORT'],
  VIEW_REPORTS: ['REPORT_VIEW'], GPS_ACCESS: ['GPS_VIEW'], MANAGE_GPS: ['GPS_SETTINGS'], VIEW_MAINTENANCE: ['VEHICLE_VIEW'],
  MANAGE_MAINTENANCE: ['VEHICLE_MAINTENANCE_MANAGE'], MANAGE_EMPLOYEES: ['EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_DELETE', 'ROLE_ACCESS_MANAGE'],
  MANAGE_SETTINGS: ['AGENCY_SETTINGS_UPDATE'],
};

Object.entries({ ...PERMISSION_ALIASES }).forEach(([legacy, modernCodes]) => {
  modernCodes.forEach((modern) => {
    PERMISSION_ALIASES[modern] = Array.from(new Set([...(PERMISSION_ALIASES[modern] || []), legacy]));
  });
});

interface PermissionState {
  role: string;
  roleCode: string;
  permissions: Set<string>;
  loading: boolean;
  isAgencyAdmin: boolean;
  isEmployee: boolean;
  hasPermission: (code: string) => boolean;
  hasAnyPermission: (codes: string[]) => boolean;
  hasAllPermissions: (codes: string[]) => boolean;
}

const PermissionContext = createContext<PermissionState>({
  role: '',
  roleCode: '',
  permissions: new Set(),
  loading: true,
  isAgencyAdmin: false,
  isEmployee: false,
  hasPermission: () => false,
  hasAnyPermission: () => false,
  hasAllPermissions: () => false,
});

export function PermissionProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isSuperAdmin } = useAuth();
  const [role, setRole] = useState('');
  const [roleCode, setRoleCode] = useState('');
  const [permissions, setPermissions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated || isSuperAdmin) {
      setRole('');
      setRoleCode('');
      setPermissions([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    api.get('/permissions/me')
      .then(({ data }) => {
        setRole(data.role || '');
        setRoleCode(data.roleCode || data.role || '');
        setPermissions(data.permissions || []);
      })
      .catch(() => { /* silently ignore; auth interceptor handles expired sessions */ })
      .finally(() => setLoading(false));
  }, [isAuthenticated, isSuperAdmin]);

  const value = useMemo(() => {
    const expanded = new Set<string>();
    permissions.forEach((permission) => {
      expanded.add(permission);
      (PERMISSION_ALIASES[permission] || []).forEach((alias) => expanded.add(alias));
    });
    const agencyAdmin = role === 'ADMIN' || role === 'AGENCY_OWNER' || roleCode === 'ADMIN' || roleCode === 'AGENCY_OWNER';
    const hasPermission = (code: string) => agencyAdmin || expanded.has(code) || (PERMISSION_ALIASES[code] || []).some((alias) => expanded.has(alias));
    return {
      role,
      roleCode,
      permissions: expanded,
      loading,
      isAgencyAdmin: agencyAdmin,
      isEmployee: Boolean(role || roleCode) && !agencyAdmin && !isSuperAdmin,
      hasPermission,
      hasAnyPermission: (codes: string[]) => codes.some(hasPermission),
      hasAllPermissions: (codes: string[]) => codes.every(hasPermission),
    };
  }, [role, roleCode, permissions, loading, isSuperAdmin]);

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>;
}

export const usePermissions = () => useContext(PermissionContext);