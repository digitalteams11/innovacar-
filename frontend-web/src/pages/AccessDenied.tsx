import { useNavigate } from 'react-router-dom';
import { ShieldOff, ArrowLeft } from 'lucide-react';
import { useTranslation } from 'react-i18next';

// The professional, non-silent denial screen for a missing permission (spec
// section 16): never a silent redirect to the dashboard, never expose
// internals — just the permission code itself (not a stack trace, not a
// backend error), a description, and a way back.
export default function AccessDenied({ permission }: { permission?: string }) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-6">
      <div className="w-full max-w-md rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] p-8 text-center shadow-soft">
        <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-rose-500/10">
          <ShieldOff size={28} className="text-rose-500" />
        </div>
        <h1 className="text-xl font-bold text-[var(--text-primary)]">{t('roleAccess.accessDeniedTitle')}</h1>
        <p className="mt-2 text-sm text-[var(--text-muted)]">{t('roleAccess.accessDeniedDescription')}</p>
        {permission && (
          <p className="mt-3 rounded-lg bg-[var(--bg-hover)] px-3 py-2 font-mono text-[11px] text-[var(--text-muted)]">
            {t('roleAccess.accessDeniedPermission', { permission })}
          </p>
        )}
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="mt-6 inline-flex items-center gap-2 rounded-xl bg-[var(--brand-primary)] px-4 py-2.5 text-sm font-semibold text-[var(--brand-primary-foreground)] hover:opacity-90"
        >
          <ArrowLeft size={16} /> {t('roleAccess.accessDeniedBack')}
        </button>
      </div>
    </div>
  );
}
