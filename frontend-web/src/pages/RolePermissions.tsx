import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ShieldCheck, Loader2, ChevronDown, Plus, Trash2, Search,
  Sparkles, Save, X as XIcon, Users as UsersIcon,
} from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';

// ── Types (mirror the backend dto/rbac package) ────────────────────────────

interface RoleSummary {
  roleId: string;
  code: string;
  name: string;
  type: 'SYSTEM_ROLE' | 'CUSTOM_ROLE';
  description: string | null;
  color: string | null;
  icon: string | null;
  userCount: number;
  editable: boolean;
  deletable: boolean;
}

interface PermissionState {
  code: string;
  module: string | null;
  resource: string | null;
  action: string | null;
  labelKey: string | null;
  descriptionKey: string | null;
  riskLevel: 'NORMAL' | 'ELEVATED' | 'DANGEROUS' | null;
  dependencies: string[];
  enabled: boolean;
  isNew: boolean;
}

interface RoleDetail {
  roleId: string;
  code: string;
  name: string;
  description: string | null;
  type: 'SYSTEM_ROLE' | 'CUSTOM_ROLE';
  editable: boolean;
  permissions: PermissionState[];
}

// Display-only mapping — never sent back to the API.
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

const RISK_STYLES: Record<string, string> = {
  NORMAL: 'bg-slate-500/10 text-slate-500',
  ELEVATED: 'bg-amber-500/10 text-amber-600',
  DANGEROUS: 'bg-rose-500/10 text-rose-600',
};

function permissionLabel(p: PermissionState, t: (k: string, opts?: any) => string): string {
  if (p.labelKey && t(p.labelKey, { defaultValue: '' })) return t(p.labelKey);
  return p.code;
}

function permissionDescription(p: PermissionState, t: (k: string, opts?: any) => string): string {
  if (p.descriptionKey && t(p.descriptionKey, { defaultValue: '' })) return t(p.descriptionKey);
  return '';
}

function moduleLabel(module: string, t: (k: string, opts?: any) => string): string {
  const key = `permissions.modules.${module}`;
  const value = t(key, { defaultValue: '' });
  return value || module;
}

function roleLabel(role: RoleSummary, t: (k: string) => string): string {
  if (role.type === 'CUSTOM_ROLE') return role.name;
  return t(ROLE_LABEL_KEYS[role.code] || 'roleAccess.roles.customRole');
}

/** Every distinct code that (directly or transitively) depends on `code`, among `all`. */
function dependentsOf(code: string, all: PermissionState[]): string[] {
  const result = new Set<string>();
  let frontier = new Set([code]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const p of all) {
      if (result.has(p.code) || p.code === code) continue;
      if (p.dependencies.some(dep => frontier.has(dep))) {
        result.add(p.code);
        frontier.add(p.code);
        changed = true;
      }
    }
  }
  return Array.from(result);
}

export default function RolePermissions() {
  const { t } = useTranslation();
  const { showToast } = useToast();

  const [roles, setRoles] = useState<RoleSummary[]>([]);
  const [rolesLoading, setRolesLoading] = useState(true);
  const [selectedRoleId, setSelectedRoleId] = useState<string>('');
  const [detail, setDetail] = useState<RoleDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [enabled, setEnabled] = useState<Set<string>>(new Set());
  const [savedEnabled, setSavedEnabled] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [roleSearch, setRoleSearch] = useState('');

  const dirty = useMemo(() => {
    if (enabled.size !== savedEnabled.size) return true;
    for (const c of enabled) if (!savedEnabled.has(c)) return true;
    return false;
  }, [enabled, savedEnabled]);

  const loadRoles = async (preserveSelection?: string) => {
    setRolesLoading(true);
    try {
      const { data } = await api.get('/roles');
      const list: RoleSummary[] = data || [];
      setRoles(list);
      const next = preserveSelection && list.some(r => r.roleId === preserveSelection)
        ? preserveSelection
        : list[0]?.roleId || '';
      if (next) setSelectedRoleId(next);
    } catch (err: any) {
      showToast(err?.userMessage || t('roleAccess.loadError'), 'error');
    } finally {
      setRolesLoading(false);
    }
  };

  useEffect(() => { void loadRoles(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedRoleId) return;
    setDetailLoading(true);
    api.get(`/roles/${encodeURIComponent(selectedRoleId)}`)
      .then(({ data }) => {
        const roleDetail: RoleDetail = data;
        setDetail(roleDetail);
        const codes = new Set(roleDetail.permissions.filter(p => p.enabled).map(p => p.code));
        setEnabled(new Set(codes));
        setSavedEnabled(new Set(codes));
        setExpanded(new Set(Array.from(new Set(roleDetail.permissions.map(p => p.module || 'GENERAL'))).slice(0, 1)));
      })
      .catch((err: any) => {
        showToast(err?.userMessage || t('roleAccess.loadError'), 'error');
      })
      .finally(() => setDetailLoading(false));
  }, [selectedRoleId]); // eslint-disable-line react-hooks/exhaustive-deps

  const modules = useMemo(() => {
    if (!detail) return [];
    const byModule = new Map<string, PermissionState[]>();
    for (const p of detail.permissions) {
      const key = p.module || 'GENERAL';
      if (!byModule.has(key)) byModule.set(key, []);
      byModule.get(key)!.push(p);
    }
    const q = search.trim().toLowerCase();
    return Array.from(byModule.entries())
      .map(([module, perms]) => ({
        module,
        permissions: q
          ? perms.filter(p =>
              permissionLabel(p, t).toLowerCase().includes(q) ||
              p.code.toLowerCase().includes(q))
          : perms,
      }))
      .filter(m => m.permissions.length > 0);
  }, [detail, search, t]);

  const toggleModule = (module: string) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(module)) next.delete(module); else next.add(module);
      return next;
    });
  };

  const togglePermission = (p: PermissionState) => {
    setEnabled(prev => {
      const next = new Set(prev);
      if (next.has(p.code)) {
        const dependents = detail ? dependentsOf(p.code, detail.permissions).filter(c => next.has(c)) : [];
        next.delete(p.code);
        if (dependents.length > 0) {
          dependents.forEach(c => next.delete(c));
          const names = dependents
            .map(c => detail?.permissions.find(d => d.code === c))
            .filter(Boolean)
            .map(d => permissionLabel(d as PermissionState, t))
            .join(', ');
          showToast(t('roleAccess.cascadeDisabled', { names, defaultValue: `Also disabled: ${names}` }), 'info');
        }
      } else {
        next.add(p.code);
        p.dependencies.forEach(dep => next.add(dep));
      }
      return next;
    });
  };

  const selectAllInModule = (perms: PermissionState[]) => {
    setEnabled(prev => {
      const next = new Set(prev);
      perms.forEach(p => { next.add(p.code); p.dependencies.forEach(dep => next.add(dep)); });
      return next;
    });
  };

  const clearAllInModule = (perms: PermissionState[]) => {
    setEnabled(prev => {
      const next = new Set(prev);
      perms.forEach(p => next.delete(p.code));
      return next;
    });
  };

  const resetModuleToSaved = (perms: PermissionState[]) => {
    setEnabled(prev => {
      const next = new Set(prev);
      perms.forEach(p => {
        if (savedEnabled.has(p.code)) next.add(p.code); else next.delete(p.code);
      });
      return next;
    });
  };

  const save = async () => {
    if (!detail) return;
    setSaving(true);
    try {
      const { data } = await api.put(`/roles/${encodeURIComponent(detail.roleId)}/permissions`, {
        enabledPermissionCodes: Array.from(enabled),
      });
      const roleDetail: RoleDetail = data;
      setDetail(roleDetail);
      const codes = new Set(roleDetail.permissions.filter(p => p.enabled).map(p => p.code));
      setEnabled(new Set(codes));
      setSavedEnabled(new Set(codes));
      showToast(t('roleAccess.updateSuccess'), 'success');
    } catch (err: any) {
      const status = err?.response?.status;
      const message = err?.response?.data?.message || err?.userMessage
        || (status === 409 ? t('roleAccess.lockoutBlocked', { defaultValue: 'This change would lock administrators out and was blocked.' })
        : t('roleAccess.saveFailed', { defaultValue: 'Unable to save role permissions.' }));
      showToast(message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const discard = () => setEnabled(new Set(savedEnabled));

  const deleteRole = async (role: RoleSummary) => {
    try {
      await api.delete(`/roles/${encodeURIComponent(role.roleId)}`);
      showToast(t('roleAccess.roleDeleted', { defaultValue: 'Role deleted.' }), 'success');
      await loadRoles();
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || t('roleAccess.deleteFailed', { defaultValue: 'Unable to delete this role.' }), 'error');
    }
  };

  const filteredRoles = roles.filter(r => roleLabel(r, t).toLowerCase().includes(roleSearch.toLowerCase()));

  return (
    <div className="space-y-5 pb-24">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-500">
          <ShieldCheck size={21} />
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-xl font-bold text-[#1e293b]">{t('roleAccess.title')}</h1>
          <p className="text-sm text-slate-500">{t('roleAccess.subtitle')}</p>
        </div>
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="premium-action flex shrink-0 items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95"
        >
          <Plus size={18} /> {t('roleAccess.newRole', { defaultValue: 'New role' })}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-4">
        {/* Left panel — role list */}
        <div className="card-premium overflow-hidden p-0">
          <div className="border-b border-[#e8e6e1] p-3">
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={roleSearch}
                onChange={e => setRoleSearch(e.target.value)}
                placeholder={t('roleAccess.searchRoles', { defaultValue: 'Search roles…' })}
                className="w-full rounded-lg border border-[#e8e6e1] bg-white py-2 pl-8 pr-3 text-sm"
              />
            </div>
          </div>
          {rolesLoading ? (
            <div className="flex justify-center py-8"><Loader2 className="animate-spin text-brand-500" size={22} /></div>
          ) : (
            <ul className="max-h-[70vh] overflow-y-auto divide-y divide-[#e8e6e1]/60">
              {filteredRoles.map(role => (
                <li key={role.roleId}>
                  <button
                    type="button"
                    onClick={() => {
                      if (dirty) { showToast(t('roleAccess.unsavedChangesWarning', { defaultValue: 'Save or discard your changes before switching roles.' }), 'warning'); return; }
                      setSelectedRoleId(role.roleId);
                    }}
                    className={`w-full text-left px-4 py-3 transition-colors ${selectedRoleId === role.roleId ? 'bg-brand-50' : 'hover:bg-[#f5f5f0]/70'}`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate text-sm font-semibold text-[#1e293b]">{roleLabel(role, t)}</span>
                      {role.type === 'CUSTOM_ROLE' && (
                        <span className="shrink-0 rounded-full bg-violet-500/10 px-2 py-0.5 text-[9px] font-bold uppercase text-violet-600">
                          {t('roleAccess.customBadge', { defaultValue: 'Custom' })}
                        </span>
                      )}
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 text-[11px] text-slate-400">
                      <UsersIcon size={11} /> {role.userCount}
                    </div>
                  </button>
                </li>
              ))}
              {filteredRoles.length === 0 && (
                <li className="px-4 py-6 text-center text-xs text-slate-400">{t('roleAccess.noRolesFound', { defaultValue: 'No roles found.' })}</li>
              )}
            </ul>
          )}
        </div>

        {/* Right panel — module accordion */}
        <div className="space-y-3">
          {detailLoading || !detail ? (
            <div className="flex min-h-[240px] items-center justify-center">
              <Loader2 className="animate-spin text-brand-500" size={26} />
            </div>
          ) : (
            <>
              <div className="card-premium flex flex-wrap items-center justify-between gap-3 p-4">
                <div>
                  <h2 className="text-sm font-bold text-[#1e293b]">{detail.type === 'CUSTOM_ROLE' ? detail.name : t(ROLE_LABEL_KEYS[detail.code] || 'roleAccess.roles.customRole')}</h2>
                  {detail.description && <p className="text-xs text-slate-500">{detail.description}</p>}
                </div>
                <div className="relative">
                  <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    placeholder={t('roleAccess.searchPermissions', { defaultValue: 'Search permissions…' })}
                    className="w-64 max-w-full rounded-lg border border-[#e8e6e1] bg-white py-2 pl-8 pr-3 text-sm"
                  />
                </div>
                {detail.type === 'CUSTOM_ROLE' && (
                  <button
                    type="button"
                    onClick={() => {
                      const role = roles.find(r => r.roleId === detail.roleId);
                      if (role && !role.deletable) {
                        showToast(t('roleAccess.cannotDeleteAssigned', { defaultValue: 'This role is assigned to users — reassign them first.' }), 'warning');
                        return;
                      }
                      if (role) void deleteRole(role);
                    }}
                    className="flex items-center gap-2 rounded-lg border border-rose-200 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                  >
                    <Trash2 size={14} /> {t('roleAccess.deleteRole', { defaultValue: 'Delete role' })}
                  </button>
                )}
              </div>

              {modules.map(({ module, permissions }) => {
                const isOpen = expanded.has(module);
                const enabledCount = permissions.filter(p => enabled.has(p.code)).length;
                return (
                  <div key={module} className="card-premium overflow-hidden p-0">
                    <button
                      type="button"
                      onClick={() => toggleModule(module)}
                      className="flex w-full items-center justify-between gap-3 px-5 py-4"
                    >
                      <div className="flex items-center gap-3">
                        <ChevronDown size={16} className={`text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                        <span className="text-sm font-bold text-[#1e293b]">{moduleLabel(module, t)}</span>
                      </div>
                      <span className="text-xs font-medium text-slate-400">{enabledCount}/{permissions.length}</span>
                    </button>
                    {isOpen && (
                      <div className="border-t border-[#e8e6e1]">
                        <div className="flex items-center gap-2 border-b border-[#e8e6e1]/60 bg-[#f5f5f0]/50 px-5 py-2">
                          <button type="button" onClick={() => selectAllInModule(permissions)} className="text-xs font-semibold text-brand-600 hover:underline">
                            {t('roleAccess.selectAll', { defaultValue: 'Select all' })}
                          </button>
                          <span className="text-slate-300">·</span>
                          <button type="button" onClick={() => clearAllInModule(permissions)} className="text-xs font-semibold text-slate-500 hover:underline">
                            {t('roleAccess.clearAll', { defaultValue: 'Clear all' })}
                          </button>
                          <span className="text-slate-300">·</span>
                          <button type="button" onClick={() => resetModuleToSaved(permissions)} className="text-xs font-semibold text-slate-500 hover:underline">
                            {t('roleAccess.resetToDefault', { defaultValue: 'Reset' })}
                          </button>
                        </div>
                        <ul className="divide-y divide-[#e8e6e1]/60">
                          {permissions.map(p => {
                            const isEnabled = enabled.has(p.code);
                            const description = permissionDescription(p, t);
                            return (
                              <li key={p.code} className="flex items-center justify-between gap-3 px-5 py-3">
                                <div className="min-w-0">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <span className="text-sm font-medium text-[#1e293b]">{permissionLabel(p, t)}</span>
                                    {p.riskLevel && p.riskLevel !== 'NORMAL' && (
                                      <span className={`rounded-full px-2 py-0.5 text-[9px] font-bold uppercase ${RISK_STYLES[p.riskLevel]}`}>
                                        {t(`permissions.risk.${p.riskLevel}`, { defaultValue: p.riskLevel })}
                                      </span>
                                    )}
                                    {p.isNew && (
                                      <span className="flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[9px] font-bold uppercase text-emerald-600">
                                        <Sparkles size={9} /> {t('roleAccess.newBadge', { defaultValue: 'New' })}
                                      </span>
                                    )}
                                  </div>
                                  {description && <p className="mt-0.5 text-[11px] text-slate-400">{description}</p>}
                                </div>
                                <button
                                  type="button"
                                  role="switch"
                                  aria-checked={isEnabled}
                                  aria-label={permissionLabel(p, t)}
                                  disabled={!detail.editable}
                                  onClick={() => togglePermission(p)}
                                  className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${isEnabled ? 'bg-brand-500' : 'bg-slate-200'} disabled:opacity-60`}
                                >
                                  <span className={`absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all ${isEnabled ? 'left-6' : 'left-1'}`} />
                                </button>
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    )}
                  </div>
                );
              })}
              {modules.length === 0 && (
                <div className="card-premium p-8 text-center text-sm text-slate-400">
                  {t('roleAccess.noPermissionsFound', { defaultValue: 'No permissions match your search.' })}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {dirty && (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-[#e8e6e1] bg-white/95 px-4 py-3 backdrop-blur sm:left-[var(--sidebar-width,0px)]">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-3">
            <span className="text-sm font-medium text-[#1e293b]">{t('roleAccess.unsavedChanges', { defaultValue: 'You have unsaved changes' })}</span>
            <div className="flex items-center gap-2">
              <button type="button" onClick={discard} disabled={saving} className="rounded-lg border border-[#e8e6e1] px-4 py-2 text-sm font-semibold text-slate-600 disabled:opacity-60">
                {t('common.discard', { defaultValue: 'Discard' })}
              </button>
              <button type="button" onClick={save} disabled={saving} className="premium-action flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-60">
                {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />} {t('common.save', { defaultValue: 'Save' })}
              </button>
            </div>
          </div>
        </div>
      )}

      <CreateRoleModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        systemRoles={roles.filter(r => r.type === 'SYSTEM_ROLE')}
        onCreated={async (roleId) => { setShowCreate(false); await loadRoles(roleId); }}
      />
    </div>
  );
}

function CreateRoleModal({ isOpen, onClose, systemRoles, onCreated }: {
  isOpen: boolean;
  onClose: () => void;
  systemRoles: RoleSummary[];
  onCreated: (roleId: string) => void;
}) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [step, setStep] = useState<'template' | 'details'>('template');
  const [template, setTemplate] = useState('');
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [permissions, setPermissions] = useState<PermissionState[]>([]);
  const [enabled, setEnabled] = useState<Set<string>>(new Set());
  const [loadingTemplate, setLoadingTemplate] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setStep('template'); setTemplate(''); setName(''); setCode(''); setDescription('');
      setPermissions([]); setEnabled(new Set());
    }
  }, [isOpen]);

  const pickTemplate = async (roleId: string) => {
    setTemplate(roleId);
    setLoadingTemplate(true);
    try {
      const { data } = await api.get(`/roles/${encodeURIComponent(roleId)}`);
      const detail: RoleDetail = data;
      setPermissions(detail.permissions);
      setEnabled(new Set(detail.permissions.filter(p => p.enabled).map(p => p.code)));
      setStep('details');
    } catch (err: any) {
      showToast(err?.userMessage || t('roleAccess.loadError'), 'error');
    } finally {
      setLoadingTemplate(false);
    }
  };

  const grouped = useMemo(() => {
    const byModule = new Map<string, PermissionState[]>();
    for (const p of permissions) {
      const key = p.module || 'GENERAL';
      if (!byModule.has(key)) byModule.set(key, []);
      byModule.get(key)!.push(p);
    }
    return Array.from(byModule.entries());
  }, [permissions]);

  const toggle = (p: PermissionState) => {
    setEnabled(prev => {
      const next = new Set(prev);
      if (next.has(p.code)) next.delete(p.code);
      else { next.add(p.code); p.dependencies.forEach(d => next.add(d)); }
      return next;
    });
  };

  const create = async () => {
    if (!name.trim() || !code.trim()) {
      showToast(t('roleAccess.nameAndCodeRequired', { defaultValue: 'Name and code are required.' }), 'warning');
      return;
    }
    setSaving(true);
    try {
      const { data } = await api.post('/roles', {
        code: code.trim(),
        name: name.trim(),
        description: description.trim() || null,
        baseTemplate: template.startsWith('SYSTEM:') ? template.slice('SYSTEM:'.length) : null,
        enabledPermissionCodes: Array.from(enabled),
      });
      showToast(t('roleAccess.roleCreated', { defaultValue: 'Role created.' }), 'success');
      onCreated(data.roleId);
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || t('roleAccess.createFailed', { defaultValue: 'Unable to create this role.' }), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('roleAccess.newRole', { defaultValue: 'New role' })} maxWidth="max-w-2xl">
      {step === 'template' && (
        <div className="space-y-3">
          <p className="text-sm text-slate-500">{t('roleAccess.pickTemplate', { defaultValue: 'Start from an existing role template, then customize.' })}</p>
          {loadingTemplate ? (
            <div className="flex justify-center py-8"><Loader2 className="animate-spin text-brand-500" size={22} /></div>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              {systemRoles.map(r => (
                <button
                  key={r.roleId}
                  type="button"
                  onClick={() => void pickTemplate(r.roleId)}
                  className="rounded-lg border border-[#e8e6e1] px-4 py-3 text-left text-sm font-semibold text-[#1e293b] hover:border-brand-300 hover:bg-brand-50"
                >
                  {t(ROLE_LABEL_KEYS[r.code] || 'roleAccess.roles.customRole')}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {step === 'details' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-[#1e293b]">{t('roleAccess.roleName', { defaultValue: 'Role name' })} *</label>
              <input value={name} onChange={e => setName(e.target.value)} className="w-full rounded-lg border border-[#e8e6e1] px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-[#1e293b]">{t('roleAccess.roleCode', { defaultValue: 'Role code' })} *</label>
              <input value={code} onChange={e => setCode(e.target.value.toUpperCase().replace(/\s+/g, '_'))} className="w-full rounded-lg border border-[#e8e6e1] px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-[#1e293b]">{t('roleAccess.roleDescription', { defaultValue: 'Description' })}</label>
            <input value={description} onChange={e => setDescription(e.target.value)} className="w-full rounded-lg border border-[#e8e6e1] px-3 py-2 text-sm" />
          </div>

          <div className="max-h-[40vh] space-y-2 overflow-y-auto">
            {grouped.map(([module, perms]) => (
              <div key={module} className="rounded-lg border border-[#e8e6e1]">
                <div className="border-b border-[#e8e6e1] px-3 py-2 text-xs font-bold text-[#1e293b]">{moduleLabel(module, t)}</div>
                <ul className="divide-y divide-[#e8e6e1]/60">
                  {perms.map(p => (
                    <li key={p.code} className="flex items-center justify-between px-3 py-2">
                      <span className="text-xs text-[#1e293b]">{permissionLabel(p, t)}</span>
                      <input type="checkbox" checked={enabled.has(p.code)} onChange={() => toggle(p)} className="h-4 w-4 accent-brand-500" />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="flex items-center justify-between gap-2 border-t border-[#e8e6e1] pt-3">
            <button type="button" onClick={() => setStep('template')} className="text-sm font-semibold text-slate-500">
              {t('common.back', { defaultValue: 'Back' })}
            </button>
            <div className="flex items-center gap-2">
              <button type="button" onClick={onClose} className="flex items-center gap-1 rounded-lg border border-[#e8e6e1] px-4 py-2 text-sm font-semibold text-slate-600">
                <XIcon size={14} /> {t('common.cancel', { defaultValue: 'Cancel' })}
              </button>
              <button type="button" onClick={() => void create()} disabled={saving} className="premium-action flex items-center gap-2 px-4 py-2 text-sm font-semibold disabled:opacity-60">
                {saving && <Loader2 size={14} className="animate-spin" />} {t('common.save', { defaultValue: 'Save' })}
              </button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}

