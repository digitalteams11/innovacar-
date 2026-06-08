import { LockKeyhole } from 'lucide-react';
import { usePermissions } from '../context/PermissionContext';

export default function PermissionGate({ permission, children }: {
  permission: string;
  children: React.ReactNode;
}) {
  const { hasPermission, loading } = usePermissions();
  if (loading) return <div className="p-8 text-sm text-slate-400">Loading access...</div>;
  if (hasPermission(permission)) return <>{children}</>;
  return (
    <div className="m-6 p-8 border border-[#e8e6e1] bg-white rounded-lg text-center">
      <LockKeyhole size={24} className="mx-auto text-slate-400" />
      <h2 className="mt-3 text-base font-bold text-[#1e293b]">Access restricted</h2>
      <p className="mt-1 text-sm text-slate-500">Your role does not include {permission.replaceAll('_', ' ').toLowerCase()}.</p>
    </div>
  );
}
