import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, ShieldCheck, Plus, X, ChevronDown } from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';

interface RoleSummary {
  roleId: string;
  code: string;
  name: string;
  type: 'SYSTEM_ROLE' | 'CUSTOM_ROLE';
}

interface EffectiveAccess {
  roleCode: string | null;
  effectivePermissions: string[];
  additionalGrants: string[];
  restrictedDenials: string[];
}

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

/**
 * Per-user "Access & Role" panel (spec: change role, add/remove specific
 * access, preview effective access) — embedded in the employee edit modal
 * rather than a separate route, since this codebase has no employee detail
 * page to attach a tab to.
 */
export default function EmployeeAccessTab({ userId }: { userId: number }) {
  const { t } = useTranslation();
  const { showToast } = useToast();

  const [roles, setRoles] = useState<RoleSummary[]>([]);
  const [access, setAccess] = useState<EffectiveAccess | null>(null);
  const [loading, setLoading] = useState(true);
  const [changingRole, setChangingRole] = useState(false);
  const [addingCode, setAddingCode] = useState('');
  const [addingType, setAddingType] = useState<'GRANT' | 'DENY'>('GRANT');
  const [savingOverride, setSavingOverride] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [rolesRes, accessRes] = await Promise.all([
        api.get('/roles'),
        api.get(`/users/${userId}/effective-permissions`),
      ]);
      setRoles(rolesRes.data || []);
      setAccess(accessRes.data);
    } catch (err: any) {
      showToast(err?.userMessage || t('roleAccess.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [userId]); // eslint-disable-line react-hooks/exhaustive-deps

  const changeRole = async (roleId: string) => {
    setChangingRole(true);
    try {
      if (roleId.startsWith('SYSTEM:')) {
        await api.put(`/users/${userId}/role`, { roleCode: roleId.slice('SYSTEM:'.length) });
      } else {
        await api.put(`/users/${userId}/role`, { customRoleId: Number(roleId.slice('CUSTOM:'.length)) });
      }
      showToast(t('roleAccess.roleChanged', { defaultValue: 'Role updated.' }), 'success');
      await load();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || t('roleAccess.saveFailed', { defaultValue: 'Unable to update this role.' }), 'error');
    } finally {
      setChangingRole(false);
    }
  };

  const addOverride = async () => {
    if (!addingCode.trim()) return;
    setSavingOverride(true);
    try {
      await api.put(`/users/${userId}/permission-overrides`, {
        permissionCode: addingCode.trim().toUpperCase(),
        overrideType: addingType,
      });
      setAddingCode('');
      showToast(t('roleAccess.overrideAdded', { defaultValue: 'Access override saved.' }), 'success');
      await load();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || t('roleAccess.saveFailed', { defaultValue: 'Unable to save this override.' }), 'error');
    } finally {
      setSavingOverride(false);
    }
  };

  const removeOverride = async (code: string) => {
    try {
      await api.delete(`/users/${userId}/permission-overrides/${encodeURIComponent(code)}`);
      showToast(t('roleAccess.overrideRemoved', { defaultValue: 'Override removed.' }), 'success');
      await load();
    } catch (err: any) {
      showToast(err?.userMessage || t('roleAccess.saveFailed', { defaultValue: 'Unable to remove this override.' }), 'error');
    }
  };

  if (loading) {
    return <div className="flex justify-center py-8"><Loader2 className="animate-spin text-brand-500" size={22} /></div>;
  }
  if (!access) return null;

  const currentRoleId = roles.find(r => r.code === access.roleCode)?.roleId || '';

  return (
    <div className="space-y-4 rounded-xl border border-[var(--border-subtle)] p-4">
      <div className="flex items-center gap-2">
        <ShieldCheck size={16} className="text-brand-500" />
        <h4 className="text-sm font-bold text-[var(--text-primary)]">{t('roleAccess.accessAndRole', { defaultValue: 'Access & Role' })}</h4>
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-[var(--text-muted)]">{t('roleAccess.currentRole', { defaultValue: 'Current role' })}</label>
        <div className="relative">
          <select
            value={currentRoleId}
            disabled={changingRole}
            onChange={e => void changeRole(e.target.value)}
            className="w-full appearance-none rounded-lg border border-[var(--border-subtle)] bg-transparent px-3 py-2 text-sm"
          >
            {roles.map(r => (
              <option key={r.roleId} value={r.roleId}>
                {r.type === 'CUSTOM_ROLE' ? r.name : t(ROLE_LABEL_KEYS[r.code] || 'roleAccess.roles.customRole')}
              </option>
            ))}
          </select>
          <ChevronDown size={14} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" />
        </div>
      </div>

      <div className="text-xs text-[var(--text-muted)]">
        {t('roleAccess.effectivePermissionCount', {
          count: access.effectivePermissions.length,
          defaultValue: `${access.effectivePermissions.length} effective permissions`,
        })}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <p className="mb-1 text-xs font-semibold text-emerald-600">{t('roleAccess.additionalGrants', { defaultValue: 'Additional access' })}</p>
          <ul className="space-y-1">
            {access.additionalGrants.length === 0 && <li className="text-[11px] text-slate-400">{t('common.none', { defaultValue: 'None' })}</li>}
            {access.additionalGrants.map(code => (
              <li key={code} className="flex items-center justify-between gap-2 rounded-lg bg-emerald-500/10 px-2 py-1 text-[11px] font-medium text-emerald-700">
                {code}
                <button type="button" onClick={() => void removeOverride(code)} aria-label={t('common.remove', { defaultValue: 'Remove' })}><X size={12} /></button>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <p className="mb-1 text-xs font-semibold text-rose-600">{t('roleAccess.restrictedDenials', { defaultValue: 'Restricted access' })}</p>
          <ul className="space-y-1">
            {access.restrictedDenials.length === 0 && <li className="text-[11px] text-slate-400">{t('common.none', { defaultValue: 'None' })}</li>}
            {access.restrictedDenials.map(code => (
              <li key={code} className="flex items-center justify-between gap-2 rounded-lg bg-rose-500/10 px-2 py-1 text-[11px] font-medium text-rose-700">
                {code}
                <button type="button" onClick={() => void removeOverride(code)} aria-label={t('common.remove', { defaultValue: 'Remove' })}><X size={12} /></button>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-[var(--border-subtle)] pt-3">
        <input
          value={addingCode}
          onChange={e => setAddingCode(e.target.value)}
          placeholder={t('roleAccess.permissionCode', { defaultValue: 'Permission code, e.g. VEHICLE_DELETE' })}
          className="min-w-0 flex-1 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs"
        />
        <select value={addingType} onChange={e => setAddingType(e.target.value as 'GRANT' | 'DENY')} className="rounded-lg border border-[var(--border-subtle)] px-2 py-2 text-xs">
          <option value="GRANT">{t('roleAccess.grant', { defaultValue: 'Grant' })}</option>
          <option value="DENY">{t('roleAccess.deny', { defaultValue: 'Deny' })}</option>
        </select>
        <button
          type="button"
          onClick={() => void addOverride()}
          disabled={savingOverride || !addingCode.trim()}
          className="flex items-center gap-1 rounded-lg bg-brand-500 px-3 py-2 text-xs font-semibold text-white disabled:opacity-60"
        >
          {savingOverride ? <Loader2 size={13} className="animate-spin" /> : <Plus size={13} />}
          {t('common.add', { defaultValue: 'Add' })}
        </button>
      </div>
    </div>
  );
}
