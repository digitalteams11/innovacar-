import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Search, Trash2, ChevronDown, Shield, UserCheck, User, Users,
  Crown, Briefcase
} from 'lucide-react';
import { useToast } from '../../context/ToastContext';

const roleConfig: Record<string, { color: string; icon: any; label: string }> = {
  SUPER_ADMIN: { color: 'bg-violet-50 text-violet-700 border-violet-200', icon: Crown, label: 'Super Admin' },
  ADMIN: { color: 'bg-blue-50 text-blue-700 border-blue-200', icon: Shield, label: 'Admin' },
  EMPLOYEE: { color: 'bg-emerald-50 text-emerald-700 border-emerald-200', icon: Briefcase, label: 'Employee' },
  AGENT: { color: 'bg-amber-50 text-amber-700 border-amber-200', icon: User, label: 'Agent' },
  CLIENT: { color: 'bg-slate-50 text-slate-600 border-slate-200', icon: Users, label: 'Client' },
};

const allRoles = ['SUPER_ADMIN', 'ADMIN', 'EMPLOYEE', 'AGENT', 'CLIENT'];

export default function SuperAdminUsers() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [changingRoleFor, setChangingRoleFor] = useState<number | null>(null);

  useEffect(() => {
    fetchUsers();
  }, [roleFilter, search]);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await superAdminApi.getAllUsers({
        role: roleFilter || undefined,
        search: search || undefined,
      });
      setUsers(res.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load users. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const updateRole = async (id: number, role: string) => {
    try {
      await superAdminApi.updateUserRole(id, role);
      setChangingRoleFor(null);
      await fetchUsers();
      showToast(`Role changed to ${roleConfig[role]?.label || role} successfully`, 'success');
    } catch (err) {
      console.error(err);
      showToast('Unable to change role. Please try again later.', 'error');
    }
  };

  const deleteUser = async (id: number) => {
    if (!window.confirm(t('superAdmin.users.confirmDelete'))) return;
    try {
      await superAdminApi.deleteUser(id);
      await fetchUsers();
      showToast('User deleted successfully', 'success');
    } catch (err) {
      console.error(err);
      showToast('Unable to delete user. Please try again later.', 'error');
    }
  };

  const stats = {
    SUPER_ADMIN: users.filter(u => u.role === 'SUPER_ADMIN').length,
    ADMIN: users.filter(u => u.role === 'ADMIN').length,
    EMPLOYEE: users.filter(u => u.role === 'EMPLOYEE').length,
    CLIENT: users.filter(u => u.role === 'CLIENT').length,
  };

  return (
    <div className="space-y-6 animate-fade">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">{t('superAdmin.users.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">{t('superAdmin.users.subtitle')}</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex items-center gap-3 bg-white dark:bg-[#1a2332]/70 px-4 py-2.5 rounded-xl shadow-soft border border-[#e8e6e1]/80 dark:border-white/5 flex-1">
          <Search size={16} className="text-slate-400" />
          <input
            type="text"
            placeholder={t('superAdmin.users.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="bg-transparent border-none outline-none text-sm w-full text-[#1e293b] dark:text-white placeholder:text-slate-400"
          />
        </div>
        <div className="relative">
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="appearance-none bg-white dark:bg-[#1a2332]/70 px-4 py-2.5 pr-10 rounded-xl shadow-soft border border-[#e8e6e1]/80 dark:border-white/5 text-sm text-[#1e293b] dark:text-white cursor-pointer outline-none focus:ring-2 ring-brand-100/50"
          >
            <option value="">{t('superAdmin.users.allRoles')}</option>
            {allRoles.map(r => <option key={r} value={r}>{r}</option>)}
          </select>
          <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
        {Object.entries(stats).map(([role, count]) => {
          const cfg = roleConfig[role];
          return (
            <div key={role} className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
              <p className="text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider font-medium">{cfg?.label || role}</p>
              <p className="text-2xl font-bold text-[#1e293b] dark:text-white mt-1">{count}</p>
            </div>
          );
        })}
      </div>

      {/* Users Table */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.users.user')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.users.role')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.users.tenant')}</th>
                <th className="text-right text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.users.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={4} className="text-center py-12 text-slate-400">{t('app.loading')}</td></tr>
              ) : users.length === 0 ? (
                <tr><td colSpan={4} className="text-center py-12 text-slate-400">{t('superAdmin.users.noUsers')}</td></tr>
              ) : (
                users.map((user) => {
                  const cfg = roleConfig[user.role];
                  const Icon = cfg?.icon || User;
                  const isChanging = changingRoleFor === user.id;
                  return (
                    <tr key={user.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-full bg-[#0a0f2c] dark:bg-white/10 flex items-center justify-center text-white dark:text-white text-sm font-bold">
                            {user.email.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <p className="text-sm font-medium text-[#1e293b] dark:text-white">{user.email}</p>
                            <p className="text-xs text-slate-400">ID: {user.id}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border ${cfg?.color || 'bg-slate-50 text-slate-600 border-slate-200'}`}>
                          <Icon size={12} />
                          {user.role}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <p className="text-sm text-[#1e293b] dark:text-white">{user.tenantName}</p>
                        <p className="text-xs text-slate-400">ID: {user.tenantId}</p>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center justify-end gap-2">
                          {!user.isSystemUser && (
                            <>
                              {isChanging ? (
                                <div className="flex flex-wrap items-center gap-1">
                                  {allRoles.filter(r => r !== user.role).map(role => (
                                    <button
                                      key={role}
                                      onClick={() => updateRole(user.id, role)}
                                      className="px-2 py-1 rounded-lg text-[10px] font-medium bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/80 transition-colors"
                                    >
                                      {roleConfig[role]?.label || role}
                                    </button>
                                  ))}
                                  <button
                                    onClick={() => setChangingRoleFor(null)}
                                    className="px-2 py-1 rounded-lg text-[10px] font-medium bg-slate-100 dark:bg-white/5 text-slate-500 hover:bg-slate-200 transition-colors"
                                  >
                                    Cancel
                                  </button>
                                </div>
                              ) : (
                                <button
                                  onClick={() => setChangingRoleFor(user.id)}
                                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white dark:bg-[#1a2332]/50 border border-[#e8e6e1]/80 dark:border-white/5 text-[#1e293b] dark:text-white hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
                                >
                                  <UserCheck size={14} />
                                  Change Role
                                </button>
                              )}
                              <button
                                onClick={() => deleteUser(user.id)}
                                className="p-2 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg text-slate-400 hover:text-rose-600 transition-colors"
                                title="Delete"
                              >
                                <Trash2 size={16} />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
