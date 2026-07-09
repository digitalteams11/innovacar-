import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ArrowLeft, ArrowRight, BookOpen, Check, CircleHelp, ExternalLink,
  Lightbulb, PlayCircle, X
} from 'lucide-react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { useOnboarding } from '../context/OnboardingContext';
import { usePermissions } from '../context/PermissionContext';
import { getModuleHelpContent } from '../data/helpCenterContent';
import VideoGuideModal from './help/VideoGuideModal';
import DocumentationModal from './help/DocumentationModal';
import FaqModal from './help/FaqModal';

const moduleKeys: Record<string, string> = {
  '/': 'dashboard',
  '/dashboard': 'dashboard',
  '/vehicles': 'vehicles',
  '/clients': 'clients',
  '/reservations': 'reservations',
  '/contracts': 'contracts',
  '/payments': 'payments',
  '/gps-tracking': 'gps',
  '/gps-settings': 'gps',
  '/settings': 'settings',
  '/contract-templates': 'contractTemplates',
  '/operations-center': 'operationsCenter',
};

const tourSteps = [
  { path: '/', target: '/', key: 'dashboard' },
  { path: '/vehicles', target: '/vehicles', key: 'vehicles' },
  { path: '/reservations', target: '/reservations', key: 'reservations' },
  { path: '/gps-tracking', target: '/gps-tracking', key: 'gps' },
  { path: '/subscription', target: '/subscription', key: 'subscription' },
];

export default function GuidanceSystem() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { role } = usePermissions();
  const { status, loading, refresh, update } = useOnboarding();
  const [showWelcome, setShowWelcome] = useState(false);
  const [showWizard, setShowWizard] = useState(false);
  const [wizardStep, setWizardStep] = useState(0);
  const [helpOpen, setHelpOpen] = useState(false);
  const [videoGuideOpen, setVideoGuideOpen] = useState(false);
  const [documentationOpen, setDocumentationOpen] = useState(false);
  const [faqOpen, setFaqOpen] = useState(false);
  const [tourIndex, setTourIndex] = useState<number | null>(null);
  const [guideOpen, setGuideOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [welcomeSaving, setWelcomeSaving] = useState(false);
  const [agency, setAgency] = useState({
    name: '', logoUrl: '', phone: '', address: '', city: '', country: 'Morocco',
    currency: 'MAD', timezone: 'Africa/Casablanca', language: 'fr', taxRate: '20',
    rentalPolicies: '',
  });

  const isOwner = ['AGENCY_OWNER', 'ADMIN'].includes(role || user?.role || '');
  const moduleKey = moduleKeys[location.pathname];
  const helpContent = useMemo(() => getModuleHelpContent(moduleKey), [moduleKey]);

  useEffect(() => {
    if (loading || !status || !isOwner) return;
    setShowWelcome(!status.welcomeDismissed && !status.completed);
  }, [loading, status, isOwner]);

  useEffect(() => {
    if (!moduleKey || !user) return;
    const key = `rentcar-guide:${user.tenantId}:${moduleKey}`;
    if (!localStorage.getItem(key)) setGuideOpen(true);
  }, [moduleKey, user]);

  useEffect(() => {
    if (user) refresh();
  }, [location.pathname, user, refresh]);

  useEffect(() => {
    const openHelp = () => setHelpOpen(true);
    window.addEventListener('open-help-center', openHelp);
    return () => window.removeEventListener('open-help-center', openHelp);
  }, []);

  useEffect(() => {
    if (!showWizard) return;
    Promise.all([api.get('/agency'), api.get('/tenant-settings')]).then(([agencyResponse, settingsResponse]) => {
      setAgency(current => ({
        ...current,
        ...agencyResponse.data,
        currency: settingsResponse.data.currency || current.currency,
        timezone: settingsResponse.data.timezone || current.timezone,
        language: settingsResponse.data.language || current.language,
        rentalPolicies: agencyResponse.data.termsAndConditions || '',
      }));
    }).catch(() => undefined);
  }, [showWizard]);

  useEffect(() => {
    if (tourIndex === null) return;
    navigate(tourSteps[tourIndex].path);
  }, [tourIndex, navigate]);

  const wizardSteps = useMemo(() => [
    { key: 'agency', action: null },
    { key: 'business', action: null },
    { key: 'vehicle', action: '/vehicles' },
    { key: 'client', action: '/clients' },
    { key: 'reservation', action: '/reservations' },
    { key: 'contract', action: '/contracts' },
    { key: 'gps', action: '/gps-settings' },
    { key: 'complete', action: null },
  ], []);

  const closeGuide = () => {
    if (moduleKey && user) localStorage.setItem(`rentcar-guide:${user.tenantId}:${moduleKey}`, '1');
    setGuideOpen(false);
  };

  const startWizard = async () => {
    if (welcomeSaving) return;
    setWelcomeSaving(true);
    setShowWelcome(false);
    setShowWizard(true);
    try {
      await update({ welcomeDismissed: true });
    } finally {
      setWelcomeSaving(false);
    }
  };

  const skipOnboarding = async () => {
    if (welcomeSaving) return;
    setWelcomeSaving(true);
    setShowWelcome(false);
    try {
      await update({ welcomeDismissed: true, wizardSkipped: true });
    } finally {
      setWelcomeSaving(false);
    }
  };

  const saveConfigurationStep = async () => {
    setSaving(true);
    try {
      if (wizardStep === 0) {
        await api.put('/agency', {
          name: agency.name, logoUrl: agency.logoUrl, phone: agency.phone,
          address: agency.address, city: agency.city, country: agency.country,
        });
        window.dispatchEvent(new Event('tenant-updated'));
      }
      if (wizardStep === 1) {
        await Promise.all([
          api.put('/tenant-settings', {
            currency: agency.currency, timezone: agency.timezone, language: agency.language,
          }),
          api.put('/agency', { termsAndConditions: agency.rentalPolicies }),
        ]);
      }
      await refresh();
      setWizardStep(step => Math.min(step + 1, 7));
    } finally {
      setSaving(false);
    }
  };

  const resumeSetup = () => {
    setHelpOpen(false);
    const firstIncompleteIndex = wizardSteps.findIndex(
      step => step.key !== 'complete' && !status?.steps[step.key],
    );
    setWizardStep(firstIncompleteIndex === -1 ? 0 : firstIncompleteIndex);
    setShowWizard(true);
  };

  const openModule = (path: string) => {
    setShowWizard(false);
    navigate(path, { state: { onboardingCreate: true } });
  };

  const finish = async () => {
    await update({ completed: true, welcomeDismissed: true, wizardSkipped: false });
    setShowWizard(false);
    if (!status?.tourCompleted) setTourIndex(0);
  };

  const finishTour = async () => {
    setTourIndex(null);
    await update({ tourCompleted: true });
  };

  const recommendation = !status ? null
    : status.counts.vehicles === 0 ? { key: 'vehicle', path: '/vehicles' }
    : status.counts.clients === 0 ? { key: 'client', path: '/clients' }
    : status.counts.reservations === 0 ? { key: 'reservation', path: '/reservations' }
    : status.counts.contracts === 0 ? { key: 'contract', path: '/contracts' }
    : null;

  return (
    <>
      <button
        onClick={() => setHelpOpen(true)}
        className="fixed bottom-20 end-4 sm:end-6 z-40 flex h-11 w-11 items-center justify-center rounded-full bg-[#0b1437] text-white shadow-lg transition-transform hover:scale-105"
        aria-label={t('guidance.help')}
        title={t('guidance.help')}
      >
        <CircleHelp size={21} />
      </button>

      {recommendation && location.pathname === '/' && (
        <div className="fixed bottom-20 end-16 z-30 hidden w-72 border border-amber-200 bg-white p-4 shadow-xl lg:block">
          <button onClick={() => navigate(recommendation.path)} className="flex w-full items-start gap-3 text-start">
            <Lightbulb className="mt-0.5 shrink-0 text-amber-500" size={19} />
            <span>
              <strong className="block text-sm text-[#1e293b]">{t('guidance.recommendation.title')}</strong>
              <span className="mt-1 block text-xs text-slate-500">{t(`guidance.recommendation.${recommendation.key}`)}</span>
            </span>
          </button>
        </div>
      )}

      {guideOpen && moduleKey && (
        <div className="fixed end-5 top-24 z-50 w-[min(360px,calc(100vw-2rem))] border border-sky-200 bg-white p-5 shadow-2xl" role="dialog">
          <div className="flex items-start gap-3">
            <Lightbulb className="shrink-0 text-sky-600" size={21} />
            <div className="flex-1">
              <h2 className="text-sm font-bold text-[#1e293b]">{t(`guidance.pages.${moduleKey}.title`)}</h2>
              <p className="mt-2 text-xs leading-5 text-slate-500">{t(`guidance.pages.${moduleKey}.description`)}</p>
              <button onClick={closeGuide} className="mt-4 rounded-lg bg-brand-500 px-4 py-2 text-xs font-semibold text-white">
                {t('guidance.gotIt')}
              </button>
            </div>
            <button onClick={closeGuide} aria-label={t('guidance.close')}><X size={16} /></button>
          </div>
        </div>
      )}

      {helpOpen && (
        <div className="fixed inset-0 z-[120]">
          <button className="absolute inset-0 bg-black/35" onClick={() => setHelpOpen(false)} aria-label={t('guidance.close')} />
          <aside className="absolute inset-y-0 end-0 w-full max-w-md overflow-y-auto bg-white p-6 shadow-2xl" role="dialog">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-[#1e293b]">{t('guidance.helpCenter')}</h2>
              <button onClick={() => setHelpOpen(false)} className="p-2"><X size={18} /></button>
            </div>
            <p className="mt-2 text-sm text-slate-500">{t(`guidance.pages.${moduleKey || 'dashboard'}.description`)}</p>
            <div className="mt-6 space-y-3">
              <HelpItem
                icon={PlayCircle}
                title={t('guidance.video')}
                text={t('guidance.videoDescription')}
                onClick={() => { setHelpOpen(false); setVideoGuideOpen(true); }}
              />
              <HelpItem
                icon={BookOpen}
                title={t('guidance.documentation')}
                text={t('guidance.documentationDescription')}
                onClick={() => { setHelpOpen(false); setDocumentationOpen(true); }}
              />
              <HelpItem
                icon={CircleHelp}
                title={t('guidance.faq')}
                text={t('guidance.faqDescription')}
                onClick={() => { setHelpOpen(false); setFaqOpen(true); }}
              />
            </div>
            {isOwner && (
              <button onClick={resumeSetup} className="mt-6 flex w-full items-center justify-center gap-2 rounded-lg bg-brand-500 py-3 text-sm font-semibold text-white">
                {t('guidance.resumeSetup')} <ExternalLink size={15} />
              </button>
            )}
          </aside>
        </div>
      )}

      {showWelcome && (
        <Dialog onClose={skipOnboarding}>
          <div className="text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-lg bg-accent-400 text-2xl">RC</div>
            <h2 className="mt-5 text-2xl font-bold text-[#1e293b]">{t('guidance.welcome.title')}</h2>
            <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-500">{t('guidance.welcome.description')}</p>
            <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
              <button
                type="button"
                onClick={startWizard}
                disabled={welcomeSaving}
                className="rounded-lg bg-brand-500 px-6 py-3 text-sm font-semibold text-white disabled:cursor-wait disabled:opacity-60"
              >
                {welcomeSaving ? t('common.loading', 'Loading...') : t('guidance.welcome.start')}
              </button>
              <button
                type="button"
                onClick={skipOnboarding}
                disabled={welcomeSaving}
                className="rounded-lg border border-slate-200 px-6 py-3 text-sm font-semibold text-slate-600 disabled:cursor-wait disabled:opacity-60"
              >
                {t('guidance.welcome.skip')}
              </button>
            </div>
          </div>
        </Dialog>
      )}

      {showWizard && (
        <Dialog onClose={() => setShowWizard(false)} wide>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-bold uppercase text-brand-500">{t('guidance.setup')} {wizardStep + 1}/8</p>
              <h2 className="mt-1 text-xl font-bold text-[#1e293b]">{t(`guidance.wizard.${wizardSteps[wizardStep].key}.title`)}</h2>
            </div>
            <button onClick={() => setShowWizard(false)} className="p-2"><X size={18} /></button>
          </div>
          <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full bg-brand-500 transition-all" style={{ width: `${((wizardStep + 1) / 8) * 100}%` }} />
          </div>
          <div className="mt-6 min-h-[290px]">
            {wizardStep === 0 && <AgencyFields value={agency} onChange={setAgency} />}
            {wizardStep === 1 && <BusinessFields value={agency} onChange={setAgency} />}
            {wizardStep >= 2 && wizardStep <= 6 && (
              <ActionStep
                title={t(`guidance.wizard.${wizardSteps[wizardStep].key}.description`)}
                completed={Boolean(status?.steps[wizardSteps[wizardStep].key])}
                actionLabel={t(`guidance.wizard.${wizardSteps[wizardStep].key}.action`)}
                onAction={() => openModule(wizardSteps[wizardStep].action!)}
              />
            )}
            {wizardStep === 7 && (
              <ActionStep title={t('guidance.wizard.complete.description')} completed actionLabel={t('guidance.wizard.complete.action')} onAction={finish} />
            )}
          </div>
          {wizardStep < 7 && (
            <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4">
              <button disabled={wizardStep === 0} onClick={() => setWizardStep(step => step - 1)} className="flex items-center gap-2 px-3 py-2 text-sm text-slate-500 disabled:opacity-30">
                <ArrowLeft size={16} /> {t('guidance.previous')}
              </button>
              <button
                disabled={saving}
                onClick={wizardStep < 2 ? saveConfigurationStep : () => setWizardStep(step => step + 1)}
                className="flex items-center gap-2 rounded-lg bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
              >
                {t('guidance.next')} <ArrowRight size={16} />
              </button>
            </div>
          )}
        </Dialog>
      )}

      {tourIndex !== null && (
        <TourStep
          index={tourIndex}
          total={tourSteps.length}
          target={tourSteps[tourIndex].target}
          title={t(`guidance.tour.${tourSteps[tourIndex].key}`)}
          onPrevious={() => setTourIndex(index => Math.max(0, (index || 0) - 1))}
          onNext={() => tourIndex === tourSteps.length - 1 ? finishTour() : setTourIndex(tourIndex + 1)}
          onSkip={finishTour}
        />
      )}

      <VideoGuideModal isOpen={videoGuideOpen} onClose={() => setVideoGuideOpen(false)} content={helpContent} />
      <DocumentationModal isOpen={documentationOpen} onClose={() => setDocumentationOpen(false)} content={helpContent} />
      <FaqModal isOpen={faqOpen} onClose={() => setFaqOpen(false)} content={helpContent} />
    </>
  );
}

function Dialog({ children, onClose, wide = false }: { children: React.ReactNode; onClose: () => void; wide?: boolean }) {
  useEffect(() => {
    const handler = (event: KeyboardEvent) => event.key === 'Escape' && onClose();
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <button className="absolute inset-0 bg-[#0b1437]/65 backdrop-blur-sm" onClick={onClose} aria-label="Close" />
      <div className={`relative max-h-[90vh] w-full overflow-y-auto bg-white p-6 shadow-2xl sm:p-8 ${wide ? 'max-w-3xl' : 'max-w-xl'}`}>
        {children}
      </div>
    </div>
  );
}

function AgencyFields({ value, onChange }: { value: any; onChange: (value: any) => void }) {
  const { t } = useTranslation();
  return <div className="grid gap-4 sm:grid-cols-2">
    {['name', 'logoUrl', 'phone', 'address', 'city', 'country'].map(field => (
      <label key={field} className={field === 'address' ? 'sm:col-span-2' : ''}>
        <span className="mb-1.5 block text-xs font-semibold text-slate-600">{t(`guidance.fields.${field}`)}</span>
        <input value={value[field]} onChange={event => onChange({ ...value, [field]: event.target.value })} className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm" />
      </label>
    ))}
  </div>;
}

function BusinessFields({ value, onChange }: { value: any; onChange: (value: any) => void }) {
  const { t } = useTranslation();
  return <div className="grid gap-4 sm:grid-cols-2">
    {['currency', 'timezone', 'language', 'taxRate'].map(field => (
      <label key={field}>
        <span className="mb-1.5 block text-xs font-semibold text-slate-600">{t(`guidance.fields.${field}`)}</span>
        <input value={value[field]} onChange={event => onChange({ ...value, [field]: event.target.value })} className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm" />
      </label>
    ))}
    <label className="sm:col-span-2">
      <span className="mb-1.5 block text-xs font-semibold text-slate-600">{t('guidance.fields.rentalPolicies')}</span>
      <textarea rows={5} value={value.rentalPolicies} onChange={event => onChange({ ...value, rentalPolicies: event.target.value })} className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm" />
    </label>
  </div>;
}

function ActionStep({ title, completed, actionLabel, onAction }: { title: string; completed: boolean; actionLabel: string; onAction: () => void }) {
  const { t } = useTranslation();
  return <div className="flex min-h-[280px] flex-col items-center justify-center text-center">
    <div className={`flex h-14 w-14 items-center justify-center rounded-full ${completed ? 'bg-emerald-50 text-emerald-600' : 'bg-brand-50 text-brand-500'}`}>
      {completed ? <Check size={25} /> : <ArrowRight size={25} />}
    </div>
    <p className="mt-5 max-w-md text-sm leading-6 text-slate-500">{title}</p>
    {completed && <span className="mt-3 text-xs font-semibold text-emerald-600">{t('guidance.completed')}</span>}
    <button onClick={onAction} className="mt-6 rounded-lg bg-brand-500 px-6 py-3 text-sm font-semibold text-white">{actionLabel}</button>
  </div>;
}

function HelpItem({ icon: Icon, title, text, onClick }: { icon: any; title: string; text: string; onClick: () => void }) {
  return <button type="button" onClick={onClick} className="flex w-full cursor-pointer items-start gap-3 border border-slate-200 p-4 text-start transition-colors hover:border-brand-300 hover:bg-brand-50/40">
    <Icon className="mt-0.5 text-brand-500" size={19} />
    <span><strong className="block text-sm text-[#1e293b]">{title}</strong><span className="mt-1 block text-xs text-slate-500">{text}</span></span>
  </button>;
}

function TourStep({ index, total, target, title, onPrevious, onNext, onSkip }: any) {
  const { t } = useTranslation();
  const [rect, setRect] = useState<DOMRect | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setRect(document.querySelector(`[data-tour="${target}"]`)?.getBoundingClientRect() || null);
    }, 120);
    return () => window.clearTimeout(timer);
  }, [target]);
  return <div className="fixed inset-0 z-[140]">
    <div className="absolute inset-0 bg-black/60" />
    {rect && <div className="absolute z-10 border-2 border-accent-400 bg-transparent shadow-[0_0_0_9999px_rgba(0,0,0,.05)]" style={{ left: rect.left - 5, top: rect.top - 5, width: rect.width + 10, height: rect.height + 10 }} />}
    <div className="absolute bottom-8 start-1/2 z-20 w-[min(430px,calc(100vw-2rem))] -translate-x-1/2 bg-white p-5 shadow-2xl">
      <p className="text-xs font-bold text-brand-500">{index + 1}/{total}</p>
      <h2 className="mt-2 text-lg font-bold text-[#1e293b]">{title}</h2>
      <div className="mt-5 flex items-center justify-between">
        <button onClick={onSkip} className="text-sm text-slate-500">{t('guidance.skipTour')}</button>
        <div className="flex gap-2">
          <button disabled={index === 0} onClick={onPrevious} className="rounded-lg border border-slate-200 px-3 py-2 text-sm disabled:opacity-30">{t('guidance.previous')}</button>
          <button onClick={onNext} className="rounded-lg bg-brand-500 px-4 py-2 text-sm font-semibold text-white">{index === total - 1 ? t('guidance.finish') : t('guidance.next')}</button>
        </div>
      </div>
    </div>
  </div>;
}
