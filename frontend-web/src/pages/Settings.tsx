import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import {
  User, Lock, Building2, Mail, Phone, MapPin, Camera,
  Save, ShieldCheck, Briefcase, FileText, Globe,
  Sun, Moon, Monitor, Palette, Eye, EyeOff, Signature, Stamp, Image, FileCheck
  , Bell, Server
} from 'lucide-react';
import SignaturePad from '../components/shared/SignaturePad';
import { useTheme } from '../context/ThemeContext';

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

interface PasswordData {
  current: string;
  newPassword: string;
  confirm: string;
}

interface OperationalSettings {
  currency: string;
  language: string;
  timezone: string;
  smtpHost: string;
  smtpPort: number;
  smtpUsername: string;
  smtpPassword: string;
  hasSmtpPassword: boolean;
  smtpTls: boolean;
  notificationInApp: boolean;
  notificationEmail: boolean;
  notificationPush: boolean;
}

export default function Settings() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const { user, updateProfile } = useAuth();

  const [activeTab, setActiveTab] = useState<'profile' | 'password' | 'agency' | 'operations' | 'appearance'>('profile');
  const { theme, setTheme, resolvedTheme } = useTheme();

  const [profileLoading, setProfileLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);

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

  const [, setAgencyLoading] = useState(true);

  const [password, setPassword] = useState<PasswordData>({
    current: '',
    newPassword: '',
    confirm: '',
  });

  const [passwordError, setPasswordError] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [operationsLoading, setOperationsLoading] = useState(false);
  const [operations, setOperations] = useState<OperationalSettings>({
    currency: 'MAD', language: 'fr', timezone: 'Africa/Casablanca',
    smtpHost: '', smtpPort: 587, smtpUsername: '', smtpPassword: '',
    hasSmtpPassword: false, smtpTls: true,
    notificationInApp: true, notificationEmail: true, notificationPush: false,
  });

  // Load profile from auth context / localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('user_profile');
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        setProfile(parsed);
        return;
      } catch { /* fall through */ }
    }
    // Fallback: build from user
    if (user) {
      const names = user.firstName && user.lastName
        ? `${user.firstName} ${user.lastName}`
        : user.firstName || user.email?.split('@')[0] || 'User';
      setProfile({
        fullName: names,
        email: user.email || '',
        phone: user.phoneNumber || '',
        jobTitle: user.jobTitle || 'Administrator',
        avatar: user.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(names)}&background=4318ff&color=fff`,
      });
    }
  }, [user]);

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
      .then(({ data }) => setOperations((current) => ({ ...current, ...data, smtpPassword: '' })))
      .catch((err) => console.error('Failed to fetch tenant settings', err));
  }, []);

  const handleProfileChange = (field: keyof ProfileData, value: string) => {
    setProfile(prev => ({ ...prev, [field]: value }));
  };

  const handleAgencyChange = (field: keyof AgencyData, value: string) => {
    setAgency(prev => ({ ...prev, [field]: value }));
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setProfile(prev => ({ ...prev, avatar: reader.result as string }));
      };
      reader.readAsDataURL(file);
    }
  };

  const handlePasswordChange = (field: keyof PasswordData, value: string) => {
    setPassword(prev => ({ ...prev, [field]: value }));
    setPasswordError('');
  };

  const saveProfile = async () => {
    if (!user) return;
    setProfileLoading(true);
    try {
      const names = profile.fullName.trim().split(' ');
      const firstName = names[0] || '';
      const lastName = names.slice(1).join(' ') || '';

      await api.put(`/users/${user.id}`, {
        firstName,
        lastName,
        phoneNumber: profile.phone,
        jobTitle: profile.jobTitle,
        avatarUrl: profile.avatar,
      });

      // Update local auth context
      updateProfile({
        fullName: profile.fullName,
        phone: profile.phone,
        jobTitle: profile.jobTitle,
        avatar: profile.avatar,
      });

      showToast(t('settings.profileSaved'));
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to save profile', 'error');
    } finally {
      setProfileLoading(false);
    }
  };

  const saveAgency = async () => {
    try {
      await api.put('/agency', agency);
      showToast(t('settings.agencySaved'));
      // Force refresh tenant branding in AuthContext so ContractDetails sees it immediately
      window.dispatchEvent(new Event('tenant-updated'));
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to save agency', 'error');
    }
  };

  const changePassword = async () => {
    if (!user) return;
    if (password.newPassword !== password.confirm) {
      setPasswordError(t('settings.passwordMismatch'));
      return;
    }
    if (password.newPassword.length < 6) {
      setPasswordError(t('settings.passwordTooShort'));
      return;
    }
    setPasswordLoading(true);
    try {
      await api.put(`/users/${user.id}/password`, {
        currentPassword: password.current,
        newPassword: password.newPassword,
      });
      setPassword({ current: '', newPassword: '', confirm: '' });
      showToast(t('settings.passwordChanged'));
    } catch (err: any) {
      setPasswordError(err?.response?.data?.message || 'Failed to change password');
    } finally {
      setPasswordLoading(false);
    }
  };

  const saveOperations = async () => {
    setOperationsLoading(true);
    try {
      const { data } = await api.put('/tenant-settings', operations);
      setOperations((current) => ({ ...current, ...data, smtpPassword: '' }));
      showToast('Operational settings saved');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to save operational settings', 'error');
    } finally {
      setOperationsLoading(false);
    }
  };

  const tabs = [
    { key: 'profile' as const, label: t('settings.tabs.profile'), icon: User },
    { key: 'password' as const, label: t('settings.tabs.password'), icon: Lock },
    { key: 'agency' as const, label: t('settings.tabs.agency'), icon: Building2 },
    { key: 'operations' as const, label: 'Operations', icon: Server },
    { key: 'appearance' as const, label: 'Appearance', icon: Palette },
  ];

  const themeOptions = [
    { value: 'light' as const, icon: Sun, label: 'Light', desc: 'Always light mode' },
    { value: 'dark' as const, icon: Moon, label: 'Dark', desc: 'Always dark mode' },
    { value: 'auto' as const, icon: Monitor, label: 'Auto', desc: '6 AM - 6 PM = Light, else Dark' },
  ];

  return (
    <div className="space-y-5 animate-fade max-w-5xl mx-auto w-full p-3 sm:p-4 lg:p-6">
      <div>
        <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('settings.title')}</h1>
        <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('settings.subtitle')}</p>
      </div>

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
                src={profile.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(profile.fullName || 'U')}&background=4318ff&color=fff`}
                alt="Avatar"
                className="w-20 h-20 rounded-2xl object-cover border-2 border-[#e8e6e1] shadow-sm"
              />
              <label className="absolute -bottom-2 -right-2 w-8 h-8 bg-brand-500 text-white rounded-lg flex items-center justify-center cursor-pointer hover:bg-brand-600 transition-colors shadow-md">
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
                <User size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={profile.fullName}
                  onChange={(e) => handleProfileChange('fullName', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.jobTitle')}</label>
              <div className="relative">
                <Briefcase size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={profile.jobTitle}
                  onChange={(e) => handleProfileChange('jobTitle', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.email')}</label>
              <div className="relative">
                <Mail size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={profile.email}
                  onChange={(e) => handleProfileChange('email', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  disabled
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.phone')}</label>
              <div className="relative">
                <Phone size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="tel"
                  value={profile.phone}
                  onChange={(e) => handleProfileChange('phone', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
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

      {activeTab === 'password' && (
        <div className="card-premium space-y-6">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <ShieldCheck size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{t('settings.changePassword')}</h3>
              <p className="text-sm text-slate-400 font-normal">{t('settings.passwordSubtitle')}</p>
            </div>
          </div>

          {passwordError && (
            <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl text-sm font-medium flex items-center gap-3 border border-danger-100">
              <div className="w-2 h-2 bg-danger-500 rounded-full"></div>
              {passwordError}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.currentPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type={showCurrentPassword ? 'text' : 'password'}
                  value={password.current}
                  onChange={(e) => handlePasswordChange('current', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowCurrentPassword(v => !v)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors"
                  tabIndex={-1}
                >
                  {showCurrentPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.newPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type={showNewPassword ? 'text' : 'password'}
                  value={password.newPassword}
                  onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowNewPassword(v => !v)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors"
                  tabIndex={-1}
                >
                  {showNewPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.confirmPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={password.confirm}
                  onChange={(e) => handlePasswordChange('confirm', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(v => !v)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors"
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button
              onClick={changePassword}
              disabled={passwordLoading}
              className="flex items-center gap-2 px-3 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-70 w-full sm:w-auto"
            >
              {passwordLoading ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <Lock size={16} />
              )}
              {t('settings.updatePassword')}
            </button>
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
                <Building2 size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.name}
                  onChange={(e) => handleAgencyChange('name', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.taxId')}</label>
              <div className="relative">
                <FileText size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.taxId}
                  onChange={(e) => handleAgencyChange('taxId', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.address')}</label>
              <div className="relative">
                <MapPin size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.address}
                  onChange={(e) => handleAgencyChange('address', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.city')}</label>
              <div className="relative">
                <Globe size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.city}
                  onChange={(e) => handleAgencyChange('city', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.country')}</label>
              <div className="relative">
                <Globe size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={agency.country}
                  onChange={(e) => handleAgencyChange('country', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.agencyEmail')}</label>
              <div className="relative">
                <Mail size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={agency.email}
                  onChange={(e) => handleAgencyChange('email', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.agencyPhone')}</label>
              <div className="relative">
                <Phone size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="tel"
                  value={agency.phone}
                  onChange={(e) => handleAgencyChange('phone', e.target.value)}
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          {/* Agency Logo */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Image size={16} className="text-brand-500" /> Agency Logo
            </label>
            <div className="flex items-center gap-4">
              {agency.logoUrl ? (
                <img src={agency.logoUrl} alt="Agency Logo" className="w-20 h-20 rounded-xl object-contain border border-[#e8e6e1] bg-white" />
              ) : (
                <div className="w-20 h-20 rounded-xl bg-[#f5f5f0] border border-[#e8e6e1] flex items-center justify-center text-slate-400 text-xs">No Logo</div>
              )}
              <label className="flex items-center gap-2 px-4 py-2 bg-brand-50 text-brand-500 rounded-xl text-sm font-medium cursor-pointer hover:bg-brand-100 transition-all">
                <Camera size={14} />
                Upload Logo
                <input type="file" accept="image/*" className="hidden" onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    const reader = new FileReader();
                    reader.onloadend = () => setAgency(prev => ({ ...prev, logoUrl: reader.result as string }));
                    reader.readAsDataURL(file);
                  }
                }} />
              </label>
            </div>
          </div>

          {/* Agency Signature */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Signature size={16} className="text-brand-500" /> Agency Digital Signature
            </label>
            <p className="text-xs text-slate-400 mb-3">This signature will be automatically applied to all future contracts.</p>
            {agency.agencySignature ? (
              <div className="space-y-3">
                <img src={agency.agencySignature} alt="Agency Signature" className="h-24 border border-[#e8e6e1] rounded-xl bg-white p-2" />
                <button
                  onClick={() => setAgency(prev => ({ ...prev, agencySignature: '' }))}
                  className="text-xs text-danger-500 hover:text-danger-600 font-medium"
                >
                  Clear & Redraw
                </button>
              </div>
            ) : (
              <div className="bg-white border border-[#e8e6e1] rounded-xl p-2">
                <SignaturePad
                  onSave={(sig) => setAgency(prev => ({ ...prev, agencySignature: sig }))}
                  label="Draw agency signature here"
                  showWatermark={false}
                />
              </div>
            )}
          </div>

          {/* Agency Stamp */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <Stamp size={16} className="text-brand-500" /> Agency Stamp (Optional)
            </label>
            <div className="flex items-center gap-4">
              {agency.agencyStampUrl ? (
                <img src={agency.agencyStampUrl} alt="Agency Stamp" className="w-20 h-20 rounded-xl object-contain border border-[#e8e6e1] bg-white" />
              ) : (
                <div className="w-20 h-20 rounded-xl bg-[#f5f5f0] border border-[#e8e6e1] flex items-center justify-center text-slate-400 text-xs">No Stamp</div>
              )}
              <label className="flex items-center gap-2 px-4 py-2 bg-brand-50 text-brand-500 rounded-xl text-sm font-medium cursor-pointer hover:bg-brand-100 transition-all">
                <Camera size={14} />
                Upload Stamp
                <input type="file" accept="image/*" className="hidden" onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    const reader = new FileReader();
                    reader.onloadend = () => setAgency(prev => ({ ...prev, agencyStampUrl: reader.result as string }));
                    reader.readAsDataURL(file);
                  }
                }} />
              </label>
              {agency.agencyStampUrl && (
                <button onClick={() => setAgency(prev => ({ ...prev, agencyStampUrl: '' }))} className="text-xs text-danger-500 hover:text-danger-600 font-medium">Remove</button>
              )}
            </div>
          </div>

          {/* Terms & Conditions */}
          <div className="pt-6 border-t border-[#e8e6e1]/60">
            <label className="block text-sm font-medium text-[#1e293b] mb-3 flex items-center gap-2">
              <FileCheck size={16} className="text-brand-500" /> Terms & Conditions
            </label>
            <p className="text-xs text-slate-400 mb-3">These terms will be displayed on every contract signing page. Leave blank to use defaults.</p>
            <textarea
              value={agency.termsAndConditions}
              onChange={(e) => handleAgencyChange('termsAndConditions', e.target.value)}
              placeholder="Enter your agency's terms and conditions here..."
              rows={6}
              className="w-full px-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all resize-none"
            />
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button
              onClick={saveAgency}
              className="flex items-center gap-2 px-3 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all w-full sm:w-auto"
            >
              <Save size={16} />
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
              <h3 className="text-base font-bold text-[#1e293b]">Operational Settings</h3>
              <p className="text-sm text-slate-400">Regional, email, and notification configuration</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <SettingSelect label="Currency" value={operations.currency}
              options={['MAD', 'EUR', 'USD']} onChange={(value) => setOperations({ ...operations, currency: value })} />
            <SettingSelect label="Language" value={operations.language}
              options={['fr', 'en', 'ar']} onChange={(value) => setOperations({ ...operations, language: value })} />
            <SettingSelect label="Timezone" value={operations.timezone}
              options={['Africa/Casablanca', 'Europe/Paris', 'UTC']}
              onChange={(value) => setOperations({ ...operations, timezone: value })} />
          </div>

          <div className="pt-5 border-t border-[#e8e6e1]/60 space-y-4">
            <div className="flex items-center gap-2">
              <Mail size={16} className="text-brand-500" />
              <h4 className="text-sm font-bold text-[#1e293b]">Agency SMTP</h4>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <SettingInput label="SMTP Host" value={operations.smtpHost}
                onChange={(value) => setOperations({ ...operations, smtpHost: value })} />
              <SettingInput label="SMTP Port" value={String(operations.smtpPort)} type="number"
                onChange={(value) => setOperations({ ...operations, smtpPort: Number(value) })} />
              <SettingInput label="Username" value={operations.smtpUsername}
                onChange={(value) => setOperations({ ...operations, smtpUsername: value })} />
              <SettingInput label="Password" value={operations.smtpPassword} type="password"
                placeholder={operations.hasSmtpPassword ? 'Stored securely - enter to replace' : 'Enter SMTP password'}
                onChange={(value) => setOperations({ ...operations, smtpPassword: value })} />
            </div>
            <Toggle label="Use TLS" checked={operations.smtpTls}
              onChange={(checked) => setOperations({ ...operations, smtpTls: checked })} />
          </div>

          <div className="pt-5 border-t border-[#e8e6e1]/60 space-y-3">
            <div className="flex items-center gap-2">
              <Bell size={16} className="text-brand-500" />
              <h4 className="text-sm font-bold text-[#1e293b]">Notification Channels</h4>
            </div>
            <Toggle label="In-app notifications" checked={operations.notificationInApp}
              onChange={(checked) => setOperations({ ...operations, notificationInApp: checked })} />
            <Toggle label="Email notifications" checked={operations.notificationEmail}
              onChange={(checked) => setOperations({ ...operations, notificationEmail: checked })} />
            <Toggle label="Browser push notifications" checked={operations.notificationPush}
              onChange={(checked) => setOperations({ ...operations, notificationPush: checked })} />
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
            <button onClick={saveOperations} disabled={operationsLoading}
              className="flex items-center justify-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium disabled:opacity-60">
              {operationsLoading ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Save size={16} />}
              Save Settings
            </button>
          </div>
        </div>
      )}

      {activeTab === 'appearance' && (
        <div className="card-premium space-y-6">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Palette size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">Appearance</h3>
              <p className="text-sm text-slate-400 font-normal">Customize how the app looks</p>
            </div>
          </div>

          {/* Current status */}
          <div className="flex items-center gap-3 p-4 bg-[#f5f5f0] rounded-xl">
            <div className="w-10 h-10 rounded-xl bg-white flex items-center justify-center shadow-sm">
              {resolvedTheme === 'dark' ? <Moon size={18} className="text-brand-500" /> : <Sun size={18} className="text-amber-500" />}
            </div>
            <div>
              <p className="text-sm font-semibold text-[#1e293b]">Currently {resolvedTheme === 'dark' ? 'Dark' : 'Light'} Mode</p>
              <p className="text-xs text-slate-400">
                {theme === 'auto'
                  ? `Auto-switching: ${new Date().getHours() >= 6 && new Date().getHours() < 18 ? 'Light' : 'Dark'} (updates every minute)`
                  : `Manual: ${theme} mode`}
              </p>
            </div>
          </div>

          {/* Theme options */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {themeOptions.map((opt) => {
              const isActive = theme === opt.value;
              return (
                <button
                  key={opt.value}
                  onClick={() => setTheme(opt.value)}
                  className={`flex flex-col items-center gap-3 p-5 rounded-2xl border-2 transition-all duration-200 ${
                    isActive
                      ? 'border-brand-500 bg-brand-50 text-brand-500 shadow-md'
                      : 'border-[#e8e6e1] bg-white text-slate-500 hover:border-slate-300 hover:bg-[#f5f5f0]'
                  }`}
                >
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${isActive ? 'bg-brand-500 text-white' : 'bg-[#f5f5f0] text-slate-400'}`}>
                    <opt.icon size={24} />
                  </div>
                  <div className="text-center">
                    <p className={`text-sm font-semibold ${isActive ? 'text-brand-500' : 'text-[#1e293b]'}`}>{opt.label}</p>
                    <p className="text-[11px] text-slate-400 mt-0.5">{opt.desc}</p>
                  </div>
                  {isActive && (
                    <div className="w-2 h-2 rounded-full bg-brand-500 mt-1"></div>
                  )}
                </button>
              );
            })}
          </div>

          {/* Auto schedule info */}
          <div className="p-4 bg-blue-50 rounded-xl border border-blue-100">
            <div className="flex items-start gap-3">
              <Monitor size={16} className="text-blue-500 mt-0.5 shrink-0" />
              <div>
                <p className="text-sm font-medium text-blue-700">Auto Mode Schedule</p>
                <p className="text-xs text-blue-500 mt-1">
                  <span className="font-semibold">Light:</span> 6:00 AM - 5:59 PM<br />
                  <span className="font-semibold">Dark:</span> 6:00 PM - 5:59 AM<br />
                  The app checks every minute and switches automatically.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
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

function Toggle({ label, checked, onChange }: {
  label: string; checked: boolean; onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
      <span className="text-sm font-medium text-[#1e293b]">{label}</span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)}
        className="w-4 h-4 accent-brand-500" />
    </label>
  );
}
