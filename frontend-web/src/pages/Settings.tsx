import { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import {
  User, Building2, Mail, Phone, MapPin, Camera,
  Save, ShieldCheck, Briefcase, FileText, Globe,
  Palette, Signature, Stamp, Image, FileCheck,
  Bell, Server, Settings as SettingsIcon, Lock, CreditCard, Database,
  HelpCircle, ArrowRight,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import SignaturePad from '../components/shared/SignaturePad';
import AppearanceCustomizer from '../components/AppearanceCustomizer';
import SecurityTab from '../components/settings/SecurityTab';
import NotificationsTab from '../components/settings/NotificationsTab';
import BillingTab from '../components/settings/BillingTab';
import PrivacyTab from '../components/settings/PrivacyTab';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { resolveMediaUrl } from '../lib/utils';

interface ProfileData {
  fullName: string;
  email: string;
  phone: string;
  jobTitle: string;
  avatar: string;
}

interface AgencyData {
  name: string;
  address: string;
  phone: string;
  email: string;
  taxId: string;
  city: string;
  country: string;
  logoUrl: string;
  agencySignature: string;
  agencyStampUrl: string;
  termsAndConditions: string;
}

interface OperationalSettings {
  currency: string;
  language: string;
  timezone: string;
  notificationInApp: boolean;
  notificationEmail: boolean;
  notificationPush: boolean;
  inspectionRetentionDays: number;
}

type SettingsTab = 'profile' | 'security' | 'agency' | 'operations' | 'appearance' | 'notifications' | 'billing' | 'privacy';

const VALID_TABS: SettingsTab[] = ['profile', 'security', 'agency', 'operations', 'appearance', 'notifications', 'billing', 'privacy'];

export default function Settings() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const { user, profile: authProfile, updateCurrentUser } = useAuth();
  const [searchParams] = useSearchParams();

  const tabFromQuery = searchParams.get('tab') as SettingsTab | null;
  const [activeTab, setActiveTab] = useState<SettingsTab>(
    tabFromQuery && VALID_TABS.includes(tabFromQuery) ? tabFromQuery : 'profile',
  );
  const [profileLoading, setProfileLoading] = useState(false);

  const [profile, setProfile] = useState<ProfileData>({
    fullName: '',
    email: '',
    phone: '',
    jobTitle: '',
    avatar: '',
  });

  const [agency, setAgency] = useState<AgencyData>({
    name: '',
    address: '',
    phone: '',
    email: '',
    taxId: '',
    city: '',
    country: '',
    logoUrl: '',
    agencySignature: '',
    agencyStampUrl: '',
    termsAndConditions: '',
  });

  const [agencyLoading, setAgencyLoading] = useState(true);

  const [operationsLoading, setOperationsLoading] = useState(false);
  const [operations, setOperations] = useState<OperationalSettings>({
    currency: 'MAD', language: 'fr', timezone: 'Africa/Casablanca',
    notificationInApp: true, notificationEmail: true, notificationPush: false,
    inspectionRetentionDays: 7,
  });

  // Seed the editable form from the AuthContext profile, which is always
  // derived live from the current user (kept fresh by login, refresh,
  // profile save, and avatar upload).
  useEffect(() => {
    setProfile(authProfile);
  }, [authProfile]);

  useEffect(() => {
    const fetchAgency = async () => {
      try {
        const { data } = await api.get('/agency');
        const agencyData = {
          name: data.name || '',
          address: data.address || '',
          phone: data.phone || '',
          email: data.email || '',
          taxId: data.taxId || '',
          city: data.city || '',
          country: data.country || '',
          logoUrl: data.logoUrl || '',
          agencySignature: data.agencySignature || '',
          agencyStampUrl: data.agencyStampUrl || '',
          termsAndConditions: data.termsAndConditions || '',
        };
        setAgency(agencyData);
      } catch (err) {
        console.error('Failed to fetch agency', err);
      } finally {
        setAgencyLoading(false);
      }
    };
    fetchAgency();
  }, []);

  useEffect(() => {
    api.get('/tenant-settings')
      .then(({ data }) => {
        const raw: any = data?.data && typeof data.data === 'object' ? data.data : data;
        setOperations((current) => ({
          ...current,
          currency:                raw.currency               ?? current.currency,
          language:                raw.language               ?? current.language,
          timezone:                raw.timezone               ?? current.timezone,
          notificationInApp:       raw.notificationInApp      ?? current.notificationInApp,
          notificationEmail:       raw.notificationEmail      ?? current.notificationEmail,
          notificationPush:        raw.notificationPush       ?? current.notificationPush,
          inspectionRetentionDays: raw.inspectionRetentionDays ?? current.inspectionRetentionDays,
        }));
      })
      .catch((err) => console.error('Failed to fetch tenant settings', err));
  }, []);

  const handleProfileChange = (field: keyof ProfileData, value: string) => {
    setProfile(prev => ({ ...prev, [field]: value }));
  };

  const handleAgencyChange = (field: keyof AgencyData, value: string) => {
    setAgency(prev => ({ ...prev, [field]: value }));
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !user) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      const { data } = await api.post(`/users/${user.id}/avatar`, formData);
      const updated = data?.data;
      const avatarUrl = updated?.avatarUrl as string;
      setProfile(prev => ({ ...prev, avatar: avatarUrl }));
      updateCurrentUser({
        firstName: updated?.firstName,
        lastName: updated?.lastName,
        phoneNumber: updated?.phoneNumber,
        jobTitle: updated?.jobTitle,
        avatarUrl,
      });
      showToast(t('settings.avatarSaved'));
    } catch (err: any) {
      showToast((err as any).userMessage || t('settings.avatarUploadFailed'), 'error');
    }
  };

  const saveProfile = async () => {
    if (!user) return;
    const trimmedName = profile.fullName.trim();
    if (!trimmedName) {
      showToast(t('settings.fullNameRequired'), 'error');
      return;
    }

    setProfileLoading(true);
    try {
      const names = trimmedName.split(' ');
      const firstName = names[0] || '';
      const lastName = names.slice(1).join(' ') || '';

      // avatarUrl is intentionally omitted — it is only ever changed via the
      // dedicated multipart avatar upload, never echoed back from this form.
      const { data: updated } = await api.put(`/users/${user.id}`, {
        firstName,
        lastName,
        phoneNumber: profile.phone.trim() || null,
        jobTitle: profile.jobTitle.trim() || null,
      });

      // Sync the global current-user state from the backend's confirmed
      // response so Settings, the topbar, and any other consumer agree.
      updateCurrentUser({
        firstName: updated?.firstName,
        lastName: updated?.lastName,
        phoneNumber: updated?.phoneNumber,
        jobTitle: updated?.jobTitle,
        avatarUrl: updated?.avatarUrl,
      });

      showToast(t('settings.profileSaved'));
    } catch (err: any) {
      showToast((err as any).userMessage || t('settings.profileSaveFailed'), 'error');
    } finally {
      setProfileLoading(false);
    }
  };

  const saveAgency = async () => {
    if (agencyLoading) return;
    setAgencyLoading(true);
    try {
      // Exclude logoUrl and agencyStampUrl — those are managed by dedicated
      // upload endpoints and must never be overwritten with stale state here.
      const { logoUrl: _logo, agencyStampUrl: _stamp, ...agencyPayload } = agency;
      const { data } = await api.put('/agency', agencyPayload);
      setAgency((current) => ({ ...current, ...data }));
      showToast(t('settings.agencySaved'));
      window.dispatchEvent(new Event('tenant-updated'));
    } catch (err: any) {
      showToast((err as any).userMessage || 'Unable to save agency information. Please try again later.', 'error');
    } finally {
      setAgencyLoading(false);
    }
  };

  const saveOperations = async () => {
    setOperationsLoading(true);
    try {
      const { data } = await api.put('/tenant-settings', operations);
      const raw: any = data?.data && typeof data.data === 'object' ? data.data : data;
      setOperations((current) => ({
        ...current,
        currency:               raw.currency               ?? current.currency,
        language:               raw.language               ?? current.language,
        timezone:               raw.timezone               ?? current.timezone,
        notificationInApp:      raw.notificationInApp      ?? current.notificationInApp,
        notificationEmail:      raw.notificationEmail      ?? current.notificationEmail,
        notificationPush:       raw.notificationPush       ?? current.notificationPush,
        inspectionRetentionDays: raw.inspectionRetentionDays ?? current.inspectionRetentionDays,
      }));
      showToast(data?.message || 'Settings saved successfully', 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || 'Unable to save operational settings. Please try again later.', 'error');
    } finally {
      setOperationsLoading(false);
    }
  };

  const handleNotificationToggle = (field: 'notificationInApp' | 'notificationEmail' | 'notificationPush', value: boolean) => {
    setOperations((prev) => ({ ...prev, [field]: value }));
  };

  const tabs: { key: SettingsTab; label: string; icon: typeof User }[] = [
    { key: 'profile', label: t('settings.tabs.profile'), icon: User },
    { key: 'security', label: t('settings.tabs.security'), icon: Lock },
    { key: 'agency', label: t('settings.tabs.agency'), icon: Building2 },
    { key: 'operations', label: t('settings.tabs.operations'), icon: Server },
    { key: 'appearance', label: t('settings.tabs.appearance'), icon: Palette },
    { key: 'notifications', label: t('settings.tabs.notifications'), icon: Bell },
    { key: 'billing', label: t('settings.tabs.billing'), icon: CreditCard },
    { key: 'privacy', label: t('settings.tabs.privacy'), icon: Database },
  ];

  return (
    <div className="space-y-5 animate-fade max-w-6xl mx-auto w-full">
      <GlassPageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} icon={SettingsIcon} />

      <div className="card-premium p-2 sm:p-3 flex gap-1 overflow-x-auto pb-1 no-scrollbar w-full sm:w-fit min-w-0">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl text-xs sm:text-sm font-medium transition-all whitespace-nowrap flex-shrink-0 ${
              activeTab === tab.key
                ? 'bg-brand-500 text-white shadow-md'
                : 'text-slate-500 hover:text-[#1e293b] hover:bg-[#f5f5f0]'
            }`}
          >
            <tab.icon size={14} className="sm:w-[17px] sm:h-[17px]" />
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'profile' && (
        <div className="card-premium space-y-6 p-3 sm:p-5">
          <div className="flex items-center gap-4 pb-5 border-b border-[#e8e6e1]/60">
            <div className="relative">
              <img
                src={resolveMediaUrl(profile.avatar) || `https://ui-avatars.com/api/?name=${encodeURIComponent(profile.fullName || 'U')}&background=4318ff&color=fff`}
                alt="Avatar"
                className="w-20 h-20 rounded-2xl object-cover border-2 border-[#e8e6e1] shadow-sm"
              />
              <label className="absolute -bottom-2 -end-2 w-8 h-8 bg-brand-500 text-white rounded-lg flex items-center justify-center cursor-pointer hover:bg-brand-600 transition-colors shadow-md">
                <Camera size={14} />
                <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
              </label>
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{profile.fullName || 'User'}</h3>
              <p className="text-sm text-slate-400 font-normal">{profile.jobTitle || 'Administrator'}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.fullName')}</label>
              <div className="relative">
                <User size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={profile.fullName}
                  onChange={(e) => handleProfileChange('fullName', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.jobTitle')}</label>
              <div className="relative">
                <Briefcase size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={profile.jobTitle}
                  onChange={(e) => handleProfileChange('jobTitle', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.email')}</label>
              <div className="relative">
                <Mail size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={profile.email}
                  onChange={(e) => handleProfileChange('email', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  disabled
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.phone')}</label>
              <div className="relative">
                <Phone size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="tel"
                  value={profile.phone}
                  onChange={(e) => handleProfileChange('phone', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button
              onClick={saveProfile}
              disabled={profileLoading}
              className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-70 w-full sm:w-auto"
            >
              {profileLoading ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <Save size={16} />
              )}
              {t('settings.saveChanges')}
            </button>
          </div>
        </div>
      )}

      {activeTab === 'security' && (
        <div className="space-y-4">
          <SecurityTab />
          <div className="card-premium p-3 sm:p-5 space-y-2">
            <h4 className="text-sm font-bold px-1" style={{ color: 'var(--text-primary)' }}>{t('settings.teamAccess')}</h4>
            <QuickLinkCard
              to="/role-permissions"
              icon={ShieldCheck}
              label={t('nav.roleAccess')}
              description={t('settings.teamAccessDesc')}
            />
          </div>
        </div>
      )}

      {activeTab === 'agency' && (
        <div className="card-premium space-y-6">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
              <Building2 size={20} className="text-accent-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{t('settings.agencyInfo')}</h3>
              <p className="text-sm text-slate-400 font-normal">{t('settings.agencySubtitle')}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.agencyName')}</label>
              <div className="relative">
                <Building2 size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.name}
                  onChange={(e) => handleAgencyChange('name', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.taxId')}</label>
              <div className="relative">
                <FileText size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.taxId}
                  onChange={(e) => handleAgencyChange('taxId', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.address')}</label>
              <div className="relative">
                <MapPin size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.address}
                  onChange={(e) => handleAgencyChange('address', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.city')}</label>
              <div className="relative">
                <Globe size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.city}
                  onChange={(e) => handleAgencyChange('city', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.country')}</label>
              <div className="relative">
                <Globe size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.country}
                  onChange={(e) => handleAgencyChange('country', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.agencyEmail')}</label>
              <div className="relative">
                <Mail size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={agency.email}
                  onChange={(e) => handleAgencyChange('email', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.agencyPhone')}</label>
              <div className="relative">
                <Phone size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="tel"
                  value={agency.phone}
                  onChange={(e) => handleAgencyChange('phone', e.target.value)}
                  className="w-full ps-11 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          {/* Agency Logo */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Image size={16} className="text-brand-500" /> {t('settings.agencyLogo')}
            </label>
            <div className="flex items-center gap-4">
              {agency.logoUrl ? (
                <img src={resolveMediaUrl(agency.logoUrl) || undefined} alt={t('settings.agencyLogo')} className="w-20 h-20 rounded-xl object-contain border border-[#e8e6e1] bg-white" />
              ) : (
                <div className="w-20 h-20 rounded-xl bg-[#f5f5f0] border border-[#e8e6e1] flex items-center justify-center text-slate-400 text-xs">{t('settings.noLogo')}</div>
              )}
              <label className="flex items-center gap-2 px-4 py-2 bg-brand-50 text-brand-500 rounded-xl text-sm font-medium cursor-pointer hover:bg-brand-100 transition-all">
                <Camera size={14} />
                {t('settings.uploadLogo')}
                <input type="file" accept="image/*" className="hidden" onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  const formData = new FormData();
                  formData.append('file', file);
                  try {
                    const { data } = await api.post('/agency/settings/logo', formData);
                    setAgency(prev => ({ ...prev, logoUrl: data.url || data.logoUrl || '' }));
                    window.dispatchEvent(new Event('tenant-updated'));
                    showToast(t('settings.logoUploadSuccess'), 'success');
                  } catch (err: any) {
                    showToast((err as any).userMessage || t('settings.logoUploadFailed'), 'error');
                  }
                  e.target.value = '';
                }} />
              </label>
              {agency.logoUrl && (
                <button onClick={async () => {
                  try {
                    await api.delete('/agency/settings/logo');
                    setAgency(prev => ({ ...prev, logoUrl: '' }));
                    window.dispatchEvent(new Event('tenant-updated'));
                    showToast(t('settings.logoRemoved'), 'success');
                  } catch {
                    showToast(t('settings.logoRemoveFailed'), 'error');
                  }
                }} className="text-xs text-danger-500 hover:text-danger-600 font-medium">
                  {t('common.remove', 'Remove')}
                </button>
              )}
            </div>
          </div>

          {/* Agency Signature */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Signature size={16} className="text-brand-500" /> {t('settings.agencyDigitalSignature')}
            </label>
            <p className="text-xs text-slate-400 mb-3">{t('settings.agencySignatureDesc')}</p>
            {agency.agencySignature ? (
              <div className="space-y-3">
                <img src={agency.agencySignature} alt={t('settings.agencyDigitalSignature')} className="h-24 border border-[#e8e6e1] rounded-xl bg-white p-2" />
                <button
                  onClick={() => setAgency(prev => ({ ...prev, agencySignature: '' }))}
                  className="text-xs text-danger-500 hover:text-danger-600 font-medium"
                >
                  {t('settings.clearRedraw')}
                </button>
              </div>
            ) : (
              <div className="bg-white border border-[#e8e6e1] rounded-xl p-2">
                <SignaturePad
                  onSave={(sig) => setAgency(prev => ({ ...prev, agencySignature: sig }))}
                  label={t('settings.drawAgencySignature')}
                  showWatermark={false}
                />
              </div>
            )}
          </div>

          {/* Agency Stamp */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Stamp size={16} className="text-brand-500" /> {t('settings.agencyStampOptional')}
            </label>
            <div className="flex items-center gap-4">
              {agency.agencyStampUrl ? (
                <img src={resolveMediaUrl(agency.agencyStampUrl) || undefined} alt={t('settings.agencyStamp')} className="w-20 h-20 rounded-xl object-contain border border-[#e8e6e1] bg-white" />
              ) : (
                <div className="w-20 h-20 rounded-xl bg-[#f5f5f0] border border-[#e8e6e1] flex items-center justify-center text-slate-400 text-xs">{t('settings.noStamp')}</div>
              )}
              <label className="flex items-center gap-2 px-4 py-2 bg-brand-50 text-brand-500 rounded-xl text-sm font-medium cursor-pointer hover:bg-brand-100 transition-all">
                <Camera size={14} />
                {t('settings.uploadStamp')}
                <input type="file" accept="image/*" className="hidden" onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  const formData = new FormData();
                  formData.append('file', file);
                  try {
                    const { data } = await api.post('/agency/settings/stamp', formData);
                    setAgency(prev => ({ ...prev, agencyStampUrl: data.url || data.stampUrl || '' }));
                    window.dispatchEvent(new Event('tenant-updated'));
                    showToast(t('settings.stampUploadSuccess'), 'success');
                  } catch (err: any) {
                    showToast((err as any).userMessage || t('settings.stampUploadFailed'), 'error');
                  }
                  e.target.value = '';
                }} />
              </label>
              {agency.agencyStampUrl && (
                <button onClick={async () => {
                  try {
                    await api.delete('/agency/settings/stamp');
                    setAgency(prev => ({ ...prev, agencyStampUrl: '' }));
                    window.dispatchEvent(new Event('tenant-updated'));
                    showToast(t('settings.stampRemoved'), 'success');
                  } catch {
                    showToast(t('settings.stampRemoveFailed'), 'error');
                  }
                }} className="text-xs text-danger-500 hover:text-danger-600 font-medium">{t('common.remove', 'Remove')}</button>
              )}
            </div>
          </div>

          {/* Terms & Conditions */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <FileCheck size={16} className="text-brand-500" /> {t('settings.termsConditions')}
            </label>
            <p className="text-xs text-slate-400 mb-3">{t('settings.termsConditionsDesc')}</p>
            <textarea
              value={agency.termsAndConditions}
              onChange={(e) => handleAgencyChange('termsAndConditions', e.target.value)}
              placeholder={t('settings.termsConditionsPlaceholder')}
              rows={6}
              className="w-full px-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all resize-none"
            />
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button
              onClick={saveAgency}
              disabled={agencyLoading}
              className="flex items-center gap-2 px-3 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-70 w-full sm:w-auto"
            >
              {agencyLoading ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <Save size={16} />
              )}
              {t('settings.saveChanges')}
            </button>
          </div>
        </div>
      )}

      {activeTab === 'operations' && (
        <div className="card-premium space-y-6 p-4 sm:p-6">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Server size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{t('settings.operationsTab.title')}</h3>
              <p className="text-sm text-slate-400">{t('settings.operationsTab.subtitle')}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <SettingSelect label={t('settings.operationsTab.currency')} value={operations.currency}
              options={['MAD', 'EUR', 'USD']} onChange={(value) => setOperations({ ...operations, currency: value })} />
            <SettingSelect label={t('settings.operationsTab.language')} value={operations.language}
              options={['fr', 'en', 'ar']} onChange={(value) => setOperations({ ...operations, language: value })} />
            <SettingSelect label={t('settings.operationsTab.timezone')} value={operations.timezone}
              options={['Africa/Casablanca', 'Europe/Paris', 'UTC']}
              onChange={(value) => setOperations({ ...operations, timezone: value })} />
          </div>

          <div className="pt-5 border-t border-[#e8e6e1]/60 space-y-3">
            <div className="flex items-center gap-2">
              <ShieldCheck size={16} className="text-brand-500" />
              <h4 className="text-sm font-bold text-[#1e293b]">{t('settings.operationsTab.vehicleInspectionMedia')}</h4>
            </div>
            <p className="text-xs text-slate-500">
              {t('settings.operationsTab.vehicleInspectionMediaDesc')}
            </p>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[7, 15, 30].map((days) => (
                <button key={days} type="button" onClick={() => setOperations({ ...operations, inspectionRetentionDays: days })}
                  className={`rounded-xl border px-3 py-2 text-sm font-bold transition-all ${
                    operations.inspectionRetentionDays === days
                      ? 'border-brand-500 bg-brand-50 text-brand-600'
                      : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50'
                  }`}>
                  {t('settings.operationsTab.days', { count: days })}
                </button>
              ))}
              <SettingInput label={t('settings.operationsTab.customDays')} value={String(operations.inspectionRetentionDays)} type="number"
                onChange={(value) => setOperations({ ...operations, inspectionRetentionDays: Number(value) })} />
            </div>
          </div>

          <div className="pt-5 border-t border-[#e8e6e1]/60 space-y-2">
            <h4 className="text-sm font-bold text-[#1e293b]">{t('settings.operationsTab.moreOperationsTools')}</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <QuickLinkCard
                to="/operations-center"
                icon={HelpCircle}
                label={t('nav.operationsCenter')}
                description={t('settings.operationsTab.operationsCenterDesc')}
              />
            </div>
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button onClick={saveOperations} disabled={operationsLoading}
              className="flex items-center justify-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium disabled:opacity-60">
              {operationsLoading ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Save size={16} />}
              {t('settings.operationsTab.saveSettings')}
            </button>
          </div>
        </div>
      )}

      {activeTab === 'appearance' && (
        <div className="space-y-4">
          <div className="card-premium p-4 sm:p-6">
            <AppearanceCustomizer />
          </div>
          <div className="card-premium p-3 sm:p-5 space-y-2">
            <h4 className="text-sm font-bold text-[#1e293b] px-1">More branding tools</h4>
            <QuickLinkCard
              to="/white-label"
              icon={Palette}
              label={t('nav.whiteLabel')}
              description="Custom domain, logo, and white-label branding."
            />
          </div>
        </div>
      )}

      {activeTab === 'notifications' && (
        <NotificationsTab
          notificationInApp={operations.notificationInApp}
          notificationEmail={operations.notificationEmail}
          notificationPush={operations.notificationPush}
          onChange={handleNotificationToggle}
          onSave={saveOperations}
          saving={operationsLoading}
        />
      )}

      {activeTab === 'billing' && <BillingTab />}

      {activeTab === 'privacy' && <PrivacyTab inspectionRetentionDays={operations.inspectionRetentionDays} />}
    </div>
  );
}

function SettingInput({ label, value, onChange, type = 'text', placeholder }: {
  label: string; value: string; onChange: (value: string) => void; type?: string; placeholder?: string;
}) {
  return (
    <label className="space-y-2">
      <span className="block text-sm font-medium text-[#1e293b]">{label}</span>
      <input type={type} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)}
        className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm outline-none focus:border-brand-300" />
    </label>
  );
}

function SettingSelect({ label, value, options, onChange }: {
  label: string; value: string; options: string[]; onChange: (value: string) => void;
}) {
  return (
    <label className="space-y-2">
      <span className="block text-sm font-medium text-[#1e293b]">{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}
        className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm outline-none focus:border-brand-300">
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

function QuickLinkCard({ to, icon: Icon, label, description }: {
  to: string; icon: LucideIcon; label: string; description: string;
}) {
  return (
    <Link
      to={to}
      className="flex items-center gap-3 p-3 rounded-xl border border-[#e8e6e1] hover:border-brand-300 hover:bg-brand-50/40 transition-all group"
    >
      <div className="w-9 h-9 rounded-lg bg-[#f5f5f0] flex items-center justify-center shrink-0 group-hover:bg-white">
        <Icon size={16} className="text-brand-500" />
      </div>
      <div className="min-w-0 flex-1">
        <strong className="block text-sm text-[#1e293b]">{label}</strong>
        <span className="block text-xs text-slate-400">{description}</span>
      </div>
      <ArrowRight size={14} className="text-slate-300 group-hover:text-brand-500 shrink-0" />
    </Link>
  );
}

