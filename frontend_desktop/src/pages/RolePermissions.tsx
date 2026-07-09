import { useEffect, useMemo, useState } from 'react';
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

const roleLabels: Record<string, string> = {
  AGENCY_OWNER: 'Agency Owner',
  ADMIN: 'Administrator',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
  ACCOUNTANT: 'Accountant',
  FLEET_MANAGER: 'Fleet Manager',
  RECEPTIONIST: 'Receptionist',
  VIEWER: 'Viewer',
  AGENT: 'Agent',
  CUSTOM: 'Custom Role',
};

export default function RolePermissions() {
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
      showToast('Role access updated successfully', 'success');
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
    return <div className="p-6 text-sm text-slate-500">{errorMessage || 'Role permissions are unavailable.'}</div>;
  }

  const roles = Object.keys(matrix.roles);

  return (
    <div className="space-y-5 p-3 sm:p-4 lg:p-6">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-500">
          <ShieldCheck size={21} />
        </div>
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">Role Access</h1>
          <p className="text-sm text-slate-500">Control every role from stored tenant permissions.</p>
        </div>
      </div>

      {categories.map(category => {
        const definitions = matrix.definitions.filter(item => item.category === category);
        return (
          <section key={category} className="card-premium overflow-hidden p-0">
            <div className="border-b border-[#e8e6e1] px-5 py-4">
              <h2 className="text-sm font-bold text-[#1e293b]">{category}</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-left">
                <thead>
                  <tr className="bg-[#f5f5f0]/70 text-[10px] font-bold uppercase text-slate-400">
                    <th className="w-56 px-5 py-3">Permission</th>
                    {roles.map(role => (
                      <th key={role} className="px-3 py-3 text-center">{roleLabels[role] || role}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#e8e6e1]/60">
                  {definitions.map(definition => (
                    <tr key={definition.code}>
                      <td className="px-5 py-3">
                        <p className="text-sm font-medium text-[#1e293b]">{definition.name}</p>
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
                              aria-label={`${definition.name} for ${roleLabels[role] || role}`}
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
