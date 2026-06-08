import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import {
  User, Lock, Building2, Mail, Phone, MapPin, Camera,
  Save, ShieldCheck, Briefcase, FileText, Globe, Loader2
} from 'lucide-react';

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
}

interface PasswordData {
  current: string;
  newPassword: string;
  confirm: string;
}

export default function Settings() {
  const { t } = useTranslation();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState<'profile' | 'password' | 'agency'>('profile');

  const [profile, setProfile] = useState<ProfileData>(() => {
    const saved = localStorage.getItem('user_profile');
    return saved ? JSON.parse(saved) : {
      fullName: 'Yassine Admin',
      email: 'admin@loccar.com',
      phone: '+212 600-000000',
      jobTitle: 'Administrateur',
      avatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200',
    };
  });

  const [agency, setAgency] = useState<AgencyData>({
    name: '',
    address: '',
    phone: '',
    email: '',
    taxId: '',
    city: '',
    country: '',
  });
  const [agencyLoading, setAgencyLoading] = useState(true);

  const [password, setPassword] = useState<PasswordData>({
    current: '',
    newPassword: '',
    confirm: '',
  });

  const [passwordError, setPasswordError] = useState('');

  useEffect(() => {
    localStorage.setItem('user_profile', JSON.stringify(profile));
  }, [profile]);

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

  const saveProfile = () => {
    showToast(t('settings.profileSaved'));
  };

  const saveAgency = async () => {
    try {
      await api.put('/agency', agency);
      showToast(t('settings.agencySaved'));
    } catch (err) {
      showToast('Failed to save agency');
    }
  };

  const changePassword = () => {
    if (password.newPassword !== password.confirm) {
      setPasswordError(t('settings.passwordMismatch'));
      return;
    }
    if (password.newPassword.length < 6) {
      setPasswordError(t('settings.passwordTooShort'));
      return;
    }
    setPassword({ current: '', newPassword: '', confirm: '' });
    showToast(t('settings.passwordChanged'));
  };

  const tabs = [
    { key: 'profile' as const, label: t('settings.tabs.profile'), icon: User },
    { key: 'password' as const, label: t('settings.tabs.password'), icon: Lock },
    { key: 'agency' as const, label: t('settings.tabs.agency'), icon: Building2 },
  ];

  return (
    <div className="space-y-5 animate-fade max-w-5xl mx-auto">
      <div>
        <h1 className="text-xl font-bold text-[#1e293b]">{t('settings.title')}</h1>
        <p className="text-slate-500 font-normal text-sm mt-0.5">{t('settings.subtitle')}</p>
      </div>

      <div className="card-premium p-2 flex gap-1 w-full overflow-x-auto no-scrollbar max-w-full">
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
            <tab.icon className="w-4 h-4 sm:w-[17px] sm:h-[17px]" />
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'profile' && (
        <div className="card-premium space-y-6">
          <div className="flex items-center gap-4 pb-5 border-b border-[#e8e6e1]/60">
            <div className="relative">
              <img
                src={profile.avatar}
                alt="Avatar"
                className="w-20 h-20 rounded-2xl object-cover border-2 border-[#e8e6e1] shadow-sm"
              />
              <label className="absolute -bottom-2 -right-2 w-8 h-8 bg-brand-500 text-white rounded-lg flex items-center justify-center cursor-pointer hover:bg-brand-600 transition-colors shadow-md">
                <Camera size={14} />
                <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
              </label>
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{profile.fullName}</h3>
              <p className="text-sm text-slate-400 font-normal">{profile.jobTitle}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex flex-col sm:flex-row justify-end">
            <button
              onClick={saveProfile}
              className="flex items-center justify-center gap-2 px-4 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all w-full sm:w-auto"
            >
              <Save size={16} />
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

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.currentPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="password"
                  value={password.current}
                  onChange={(e) => handlePasswordChange('current', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.newPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="password"
                  value={password.newPassword}
                  onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.confirmPassword')}</label>
              <div className="relative">
                <Lock size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="password"
                  value={password.confirm}
                  onChange={(e) => handlePasswordChange('confirm', e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex flex-col sm:flex-row justify-end">
            <button
              onClick={changePassword}
              className="flex items-center justify-center gap-2 px-4 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all w-full sm:w-auto"
            >
              <Lock size={16} />
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

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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

          <div className="pt-4 border-t border-[#e8e6e1]/60 flex flex-col sm:flex-row justify-end">
            <button
              onClick={saveAgency}
              className="flex items-center justify-center gap-2 px-4 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all w-full sm:w-auto"
            >
              <Save size={16} />
              {t('settings.saveChanges')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
