import { useTranslation } from 'react-i18next';
import { usePermissions } from '../context/PermissionContext';
import AccessDenied from '../pages/AccessDenied';

// Route-level and widget-level permission gate — spec section 16: never a
// silent redirect, always a professional "access denied" state that names
// the required permission (safely — a code, not an internal error) with a
// way back. Used throughout App.tsx wrapping every permission-gated route.
export default function PermissionGate({ permission, children }: {
  permission: string;
  children: React.ReactNode;
}) {
  const { hasPermission, loading } = usePermissions();
  const { t } = useTranslation();
  if (loading) return <div className="p-8 text-sm text-[var(--text-muted)]">{t('common.loadingAccess', 'Loading access...')}</div>;
  if (hasPermission(permission)) return <>{children}</>;
  return <AccessDenied permission={permission} />;
}
