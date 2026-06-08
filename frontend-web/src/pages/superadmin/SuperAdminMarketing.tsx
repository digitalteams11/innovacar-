import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Megaphone, TrendingUp, Users, Target, Save } from 'lucide-react';
import { PageHeader, TabGroup, FormField, TextInput, TextArea, StatCard } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminMarketing() {
  useTranslation();
  const { showToast } = useToast();
  const [conversion, setConversion] = useState<any>(null);
  const [onboarding, setOnboarding] = useState<any>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [conversionRes, onboardingRes] = await Promise.all([
        superAdminApi.getMarketingConversion().catch(() => ({ data: null })),
        superAdminApi.getMarketingOnboarding().catch(() => ({ data: {} })),
      ]);
      setConversion(conversionRes.data);
      setOnboarding(onboardingRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveOnboarding = async () => {
    setSaving(true);
    try {
      await superAdminApi.updateMarketingOnboarding(onboarding);
      showToast('Onboarding settings saved');
    } catch (err) {
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'onboarding', label: 'Onboarding Flow' },
    { id: 'landing', label: 'Landing Page' },
  ];

  const funnelStages = [
    { label: 'Website Visits', value: conversion?.websiteVisits || 0, color: 'bg-[#0a0f2c]' },
    { label: 'Signups Started', value: conversion?.signupsStarted || 0, color: 'bg-brand-500' },
    { label: 'Trials Created', value: conversion?.trialsCreated || 0, color: 'bg-blue-500' },
    { label: 'Trial Completed', value: conversion?.trialsCompleted || 0, color: 'bg-emerald-500' },
    { label: 'Paid Conversion', value: conversion?.paidConversion || 0, color: 'bg-accent-400' },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="Marketing & Onboarding" subtitle="Manage growth, onboarding flows, and conversion optimization" />

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Trial Signups (30d)" value={conversion?.trialsCreated || 0} change={12} changeType="up" icon={Users} loading={loading} />
        <StatCard title="Trial-to-Paid Rate" value={`${conversion?.trialToPaidRate || 0}%`} change={5} changeType="up" icon={TrendingUp} loading={loading} />
        <StatCard title="Avg. Onboarding Time" value={`${conversion?.avgOnboardingMinutes || 0}m`} change={8} changeType="down" icon={Target} loading={loading} />
        <StatCard title="Landing Page CTR" value={`${conversion?.landingCTR || 0}%`} change={3} changeType="up" icon={Megaphone} loading={loading} />
      </div>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Conversion Funnel</h3>
            <div className="space-y-4">
              {funnelStages.map((stage, idx) => {
                const max = funnelStages[0].value || 1;
                const width = max > 0 ? (stage.value / max) * 100 : 0;
                return (
                  <div key={stage.label} className="flex items-center gap-4">
                    <div className="w-20 sm:w-32 text-xs sm:text-sm text-slate-500 text-right">{stage.label}</div>
                    <div className="flex-1 h-8 bg-slate-100 dark:bg-slate-800 rounded-lg overflow-hidden relative">
                      <div className={`h-full ${stage.color} rounded-lg transition-all duration-500 flex items-center justify-end px-3`} style={{ width: `${Math.max(width, 5)}%` }}>
                        <span className="text-xs font-bold text-white">{stage.value.toLocaleString()}</span>
                      </div>
                    </div>
                    {idx > 0 && (
                      <div className="w-16 text-xs text-slate-500">
                        {funnelStages[idx - 1].value > 0 ? Math.round((stage.value / funnelStages[idx - 1].value) * 100) : 0}% conv.
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {activeTab === 'onboarding' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Onboarding Flow Configuration</h3>
              <button onClick={handleSaveOnboarding} disabled={saving} className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60">
                <Save size={16} />
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
            <div className="space-y-4">
              {[
                { key: 'step1Title', label: 'Step 1 Title', placeholder: 'Welcome to Innovax' },
                { key: 'step1Description', label: 'Step 1 Description', placeholder: 'Let\'s get your agency set up...', textarea: true },
                { key: 'step2Title', label: 'Step 2 Title', placeholder: 'Configure Your Fleet' },
                { key: 'step3Title', label: 'Step 3 Title', placeholder: 'Invite Your Team' },
                { key: 'completionMessage', label: 'Completion Message', placeholder: 'You\'re all set!', textarea: true },
              ].map((field) => (
                <FormField key={field.key} label={field.label}>
                  {field.textarea ? (
                    <TextArea value={onboarding[field.key] || ''} onChange={(v) => setOnboarding({ ...onboarding, [field.key]: v })} placeholder={field.placeholder} rows={3} />
                  ) : (
                    <TextInput value={onboarding[field.key] || ''} onChange={(v) => setOnboarding({ ...onboarding, [field.key]: v })} placeholder={field.placeholder} />
                  )}
                </FormField>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'landing' && (
          <div className="max-w-2xl space-y-6">
            <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Landing Page Content</h3>
            <div className="space-y-4">
              <FormField label="Hero Headline">
                <TextInput value={onboarding.heroHeadline || ''} onChange={(v) => setOnboarding({ ...onboarding, heroHeadline: v })} placeholder="The complete car rental management platform" />
              </FormField>
              <FormField label="Hero Subtitle">
                <TextArea value={onboarding.heroSubtitle || ''} onChange={(v) => setOnboarding({ ...onboarding, heroSubtitle: v })} placeholder="Manage your fleet, bookings, and payments in one place..." rows={3} />
              </FormField>
              <FormField label="CTA Button Text">
                <TextInput value={onboarding.ctaText || ''} onChange={(v) => setOnboarding({ ...onboarding, ctaText: v })} placeholder="Start Free Trial" />
              </FormField>
              <FormField label="Features (one per line)">
                <TextArea value={onboarding.features || ''} onChange={(v) => setOnboarding({ ...onboarding, features: v })} placeholder="GPS Tracking\nDigital Contracts\nRevenue Analytics" rows={5} />
              </FormField>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
