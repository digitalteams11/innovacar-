import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, ShieldCheck } from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';

interface PermissionDefinition {
  code: string;
  name: string;
  category: string;
}

interface RolePermission {
  permissionCode: string;
  enabled: boolean;
}

interface PermissionMatrix {
  definitions: PermissionDefinition[];
  roles: Record<string, RolePermission[]>;
}

// Display-only mapping — the backend role code (used in the API payload) is
// never translated, only what's rendered on screen.
const ROLE_LABEL_KEYS: Record<string, string> = {
  AGENCY_OWNER: 'roleAccess.roles.agencyOwner',
  ADMIN: 'roleAccess.roles.administrator',
  MANAGER: 'roleAccess.roles.manager',
  EMPLOYEE: 'roleAccess.roles.employee',
  ACCOUNTANT: 'roleAccess.roles.accountant',
  FLEET_MANAGER: 'roleAccess.roles.fleetManager',
  DRIVER: 'roleAccess.roles.driver',
  RECEPTIONIST: 'roleAccess.roles.receptionist',
  VIEWER: 'roleAccess.roles.viewer',
  AGENT: 'roleAccess.roles.agent',
  CUSTOM: 'roleAccess.roles.customRole',
};

function getRoleLabel(roleCode: string, t: (key: string) => string): string {
  return t(ROLE_LABEL_KEYS[roleCode] || 'roleAccess.roles.customRole');
}

// Display-only mapping — the backend permission code (used in the API
// payload) is never translated, only the label rendered on screen. Several
// legacy codes share the same English label as a newer code (e.g.
// VEHICLE_VIEW and VIEW_VEHICLES both mean "View Vehicles"), so they map to
// the same translation key.
const PERMISSION_LABEL_KEYS: Record<string, string> = {
  DASHBOARD_VIEW: 'roleAccess.permissions.viewDashboard',
  VEHICLE_VIEW: 'roleAccess.permissions.viewVehicles',
  VIEW_VEHICLES: 'roleAccess.permissions.viewVehicles',
  VEHICLE_CREATE: 'roleAccess.permissions.createVehicle',
  CREATE_VEHICLE: 'roleAccess.permissions.createVehicle',
  VEHICLE_UPDATE: 'roleAccess.permissions.updateVehicle',
  EDIT_VEHICLE: 'roleAccess.permissions.editVehicle',
  VEHICLE_DELETE: 'roleAccess.permissions.deleteVehicle',
  DELETE_VEHICLE: 'roleAccess.permissions.deleteVehicle',
  VEHICLE_ARCHIVE: 'roleAccess.permissions.archiveVehicle',
  VEHICLE_MAINTENANCE_MANAGE: 'roleAccess.permissions.manageVehicleMaintenance',
  CLIENT_VIEW: 'roleAccess.permissions.viewClients',
  VIEW_CLIENTS: 'roleAccess.permissions.viewClients',
  CLIENT_CREATE: 'roleAccess.permissions.createClient',
  CREATE_CLIENT: 'roleAccess.permissions.createClient',
  CLIENT_UPDATE: 'roleAccess.permissions.updateClient',
  EDIT_CLIENT: 'roleAccess.permissions.editClient',
  CLIENT_DELETE: 'roleAccess.permissions.deleteClient',
  DELETE_CLIENT: 'roleAccess.permissions.deleteClient',
  RESERVATION_VIEW: 'roleAccess.permissions.viewReservations',
  VIEW_RESERVATIONS: 'roleAccess.permissions.viewReservations',
  RESERVATION_CREATE: 'roleAccess.permissions.createReservation',
  CREATE_RESERVATION: 'roleAccess.permissions.createReservation',
  RESERVATION_UPDATE: 'roleAccess.permissions.updateReservation',
  EDIT_RESERVATION: 'roleAccess.permissions.editReservation',
  RESERVATION_CANCEL: 'roleAccess.permissions.cancelReservation',
  CANCEL_RESERVATION: 'roleAccess.permissions.cancelReservation',
  RESERVATION_DELETE: 'roleAccess.permissions.deleteReservation',
  CONTRACT_VIEW: 'roleAccess.permissions.viewContracts',
  VIEW_CONTRACTS: 'roleAccess.permissions.viewContracts',
  CONTRACT_CREATE: 'roleAccess.permissions.createContract',
  CREATE_CONTRACT: 'roleAccess.permissions.createContract',
  CONTRACT_UPDATE: 'roleAccess.permissions.updateContract',
  EDIT_CONTRACT: 'roleAccess.permissions.editContract',
  CONTRACT_DELETE: 'roleAccess.permissions.deleteContract',
  DELETE_CONTRACT: 'roleAccess.permissions.deleteContract',
  CONTRACT_RESTORE: 'roleAccess.permissions.restoreContract',
  CONTRACT_PURGE: 'roleAccess.permissions.purgeContract',
  CONTRACT_EXPORT_PDF: 'roleAccess.permissions.exportContractPdf',
  CONTRACT_QR_SIGNATURE: 'roleAccess.permissions.qrSignature',
  CONTRACT_INSPECTION_MEDIA: 'roleAccess.permissions.inspectionMedia',
  SIGN_CONTRACT: 'roleAccess.permissions.signContract',
  COMPLETE_CONTRACT: 'roleAccess.permissions.completeContract',
  PAYMENT_VIEW: 'roleAccess.permissions.viewPayments',
  VIEW_PAYMENTS: 'roleAccess.permissions.viewPayments',
  PAYMENT_CREATE: 'roleAccess.permissions.createPayment',
  PAYMENT_UPDATE: 'roleAccess.permissions.updatePayment',
  PAYMENT_REFUND: 'roleAccess.permissions.refundPayment',
  PAYMENT_STATS_VIEW: 'roleAccess.permissions.viewPaymentStatistics',
  RECORD_PAYMENT: 'roleAccess.permissions.recordPayment',
  VIEW_DEPOSITS: 'roleAccess.permissions.viewDeposits',
  MANAGE_DEPOSITS: 'roleAccess.permissions.manageDeposits',
  INVOICE_VIEW: 'roleAccess.permissions.viewInvoices',
  VIEW_INVOICES: 'roleAccess.permissions.viewInvoices',
  INVOICE_EXPORT: 'roleAccess.permissions.exportInvoices',
  MANAGE_INVOICES: 'roleAccess.permissions.manageInvoices',
  REPORT_VIEW: 'roleAccess.permissions.viewReports',
  VIEW_REPORTS: 'roleAccess.permissions.viewReports',
  REPORT_FINANCIAL: 'roleAccess.permissions.financialReports',
  REPORT_ADVANCED: 'roleAccess.permissions.advancedReports',
  GPS_VIEW: 'roleAccess.permissions.viewGps',
  GPS_ACCESS: 'roleAccess.permissions.gpsAccess',
  GPS_SETTINGS: 'roleAccess.permissions.manageGpsSettings',
  MANAGE_GPS: 'roleAccess.permissions.manageGpsSettings',
  GPS_SETTINGS_VIEW: 'roleAccess.permissions.viewGpsSettings',
  GPS_SETTINGS_UPDATE: 'roleAccess.permissions.updateGpsSettings',
  GPS_CREDENTIALS_DELETE: 'roleAccess.permissions.deleteGpsCredentials',
  GPS_TEST_CONNECTION: 'roleAccess.permissions.testGpsConnection',
  GPS_SYNC_DEVICES: 'roleAccess.permissions.syncGpsDevices',
  GPS_ALERTS_VIEW: 'roleAccess.permissions.viewGpsAlerts',
  GPS_ALERTS_MANAGE: 'roleAccess.permissions.manageGpsAlerts',
  VIEW_MAINTENANCE: 'roleAccess.permissions.viewMaintenance',
  MANAGE_MAINTENANCE: 'roleAccess.permissions.manageMaintenance',
  EMPLOYEE_VIEW: 'roleAccess.permissions.viewEmployees',
  EMPLOYEE_CREATE: 'roleAccess.permissions.createEmployee',
  EMPLOYEE_UPDATE: 'roleAccess.permissions.updateEmployee',
  EMPLOYEE_DELETE: 'roleAccess.permissions.deleteEmployee',
  EMPLOYEE_RESET_PASSWORD: 'roleAccess.permissions.resetEmployeePassword',
  MANAGE_EMPLOYEES: 'roleAccess.permissions.manageEmployees',
  AGENCY_SETTINGS_VIEW: 'roleAccess.permissions.viewAgencySettings',
  AGENCY_SETTINGS_UPDATE: 'roleAccess.permissions.updateAgencySettings',
  ROLE_ACCESS_MANAGE: 'roleAccess.permissions.manageRoleAccess',
  MANAGE_SETTINGS: 'roleAccess.permissions.manageSettings',
  MANAGE_PAYMENTS: 'roleAccess.permissions.managePayments',
  SECURITY_VIEW: 'roleAccess.permissions.viewSecurity',
  SECURITY_MANAGE: 'roleAccess.permissions.manageSecurity',
};

function getPermissionLabel(permissionCode: string, t: (key: string) => string): string {
  return t(PERMISSION_LABEL_KEYS[permissionCode] || 'roleAccess.permissions.unknown');
}

// Display-only mapping for the permission-group section headers
// (backend `category` values), also never sent back to the API.
const GROUP_LABEL_KEYS: Record<string, string> = {
  Dashboard: 'roleAccess.groups.dashboard',
  Fleet: 'roleAccess.groups.fleet',
  Clients: 'roleAccess.groups.clients',
  Reservations: 'roleAccess.groups.reservations',
  Contracts: 'roleAccess.groups.contracts',
  Finance: 'roleAccess.groups.finance',
  Analytics: 'roleAccess.groups.analytics',
  Administration: 'roleAccess.groups.administration',
  Security: 'roleAccess.groups.security',
};

function getGroupLabel(category: string, t: (key: string) => string): string {
  const key = GROUP_LABEL_KEYS[category];
  return key ? t(key) : category;
}

export default function RolePermissions() {
  const { t } = useTranslation();
  const [matrix, setMatrix] = useState<PermissionMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const { showToast } = useToast();

  useEffect(() => {
    api.get('/permissions/matrix')
      .then(({ data }) => {
        const payload = data?.data && typeof data.data === 'object' ? data.data : data;
        setMatrix(payload);
        setErrorMessage('');
      })
      .catch((err: any) => {
        const status = err?.response?.status;
        const message = status === 401
          ? 'Session expired. Please sign in again.'
          : status === 403
          ? 'You do not have permission to manage role permissions.'
          : err?.userMessage || 'Unable to load role permissions. Please try again later.';
        setErrorMessage(message);
        showToast(message, 'error');
      })
      .finally(() => setLoading(false));
  }, [showToast]);

  const categories = useMemo(() => {
    if (!matrix) return [];
    return Array.from(new Set(matrix.definitions.map(item => item.category)));
  }, [matrix]);

  const isEnabled = (role: string, code: string) =>
    matrix?.roles[role]?.find(item => item.permissionCode === code)?.enabled ?? false;

  const applyEnabled = (role: string, code: string, enabled: boolean) => {
    setMatrix(current => {
      if (!current) return current;
      return {
        ...current,
        roles: {
          ...current.roles,
          [role]: current.roles[role].map(item =>
            item.permissionCode === code ? { ...item, enabled } : item
          ),
        },
      };
    });
  };

  const toggle = async (role: string, code: string, enabled: boolean) => {
    const key = `${role}:${code}`;
    const previousValue = !enabled;
    setSavingKey(key);
    // Optimistic update — reverted below if the backend rejects the change.
    applyEnabled(role, code, enabled);
    try {
      await api.put(`/permissions/${role}/${code}`, { enabled });
      showToast(t('roleAccess.updateSuccess'), 'success');
    } catch (err: any) {
      applyEnabled(role, code, previousValue);
      const status = err?.response?.status;
      const message = err?.response?.data?.message
        || err?.userMessage
        || (status === 403
          ? 'You do not have permission to manage role permissions.'
          : status === 409
          ? 'This change would lock administrators out and was blocked.'
          : 'Unable to update role access. Please try again later.');
      showToast(message, 'error');
    } finally {
      setSavingKey('');
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[320px] items-center justify-center">
        <Loader2 className="animate-spin text-brand-500" size={30} />
      </div>
    );
  }

  if (!matrix) {
    return <div className="p-6 text-sm text-slate-500">{errorMessage || t('roleAccess.loadError')}</div>;
  }

  const roles = Object.keys(matrix.roles);

  return (
    <div className="space-y-5 p-3 sm:p-4 lg:p-6">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-500">
          <ShieldCheck size={21} />
        </div>
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('roleAccess.title')}</h1>
          <p className="text-sm text-slate-500">{t('roleAccess.subtitle')}</p>
        </div>
      </div>

      {categories.map(category => {
        const definitions = matrix.definitions.filter(item => item.category === category);
        return (
          <section key={category} className="card-premium overflow-hidden p-0">
            <div className="border-b border-[#e8e6e1] px-5 py-4">
              <h2 className="text-sm font-bold text-[#1e293b]">{getGroupLabel(category, t)}</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-left">
                <thead>
                  <tr className="bg-[#f5f5f0]/70 text-[10px] font-bold uppercase text-slate-400">
                    <th className="w-56 px-5 py-3">{t('roleAccess.permissionColumn')}</th>
                    {roles.map(role => (
                      <th key={role} className="px-3 py-3 text-center">{getRoleLabel(role, t)}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#e8e6e1]/60">
                  {definitions.map(definition => (
                    <tr key={definition.code}>
                      <td className="px-5 py-3">
                        <p className="text-sm font-medium text-[#1e293b]">{getPermissionLabel(definition.code, t)}</p>
                        <p className="text-[10px] text-slate-400">{definition.code}</p>
                      </td>
                      {roles.map(role => {
                        const key = `${role}:${definition.code}`;
                        const enabled = isEnabled(role, definition.code);
                        return (
                          <td key={role} className="px-3 py-3 text-center">
                            <button
                              type="button"
                              role="switch"
                              aria-checked={enabled}
                              aria-label={`${getPermissionLabel(definition.code, t)} for ${getRoleLabel(role, t)}`}
                              disabled={savingKey === key}
                              onClick={() => toggle(role, definition.code, !enabled)}
                              className={`relative h-6 w-11 rounded-full transition-colors ${
                                enabled ? 'bg-brand-500' : 'bg-slate-200'
                              } disabled:opacity-60`}
                            >
                              <span className={`absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all ${
                                enabled ? 'left-6' : 'left-1'
                              }`} />
                            </button>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}
    </div>
  );
}
