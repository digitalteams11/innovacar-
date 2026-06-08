import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import Modal from '../components/Modal';
import {
  Satellite, Key, Globe, Link2, Save, RefreshCw,
  CheckCircle2, XCircle, AlertTriangle, Loader2, Shield,
  Server, Radio, Clock, Car, Eye, EyeOff,
  Trash2, Wifi, WifiOff, Activity
} from 'lucide-react';

interface GpsSettingsData {
  id?: number;
  provider: string;
  appId: string;
  apiKey: string;
  baseUrl: string;
  deviceGroupId: string;
  webhookUrl: string;
  connectionStatus: string;
  lastSyncAt: string | null;
  activeDevices: number;
  lastError: string | null;
  enabled: boolean;
  hasCredentials: boolean;
}

interface TestResult {
  success: boolean;
  message: string;
  provider: string;
  devicesFound: number;
  responseTime: string;
  errorCode: string;
}

const PROVIDERS = [
  { value: 'IOPGPS', label: 'IOPGPS', description: 'Professional fleet tracking platform' },
  { value: 'TRACCAR', label: 'Traccar', description: 'Open source GPS tracking system' },
  { value: 'WIALON', label: 'Wialon', description: 'Gurtam unified fleet management' },
  { value: 'GPSWOX', label: 'GPSWOX', description: 'White-label GPS tracking software' },
  { value: 'CUSTOM', label: 'Custom API', description: 'Your own GPS provider endpoint' },
];

const STATUS_CONFIG: Record<string, { color: string; bg: string; icon: any; label: string }> = {
  CONNECTED: { color: 'text-emerald-600', bg: 'bg-emerald-50', icon: Wifi, label: 'Connected' },
  DISCONNECTED: { color: 'text-slate-500', bg: 'bg-slate-50', icon: WifiOff, label: 'Disconnected' },
  ERROR: { color: 'text-rose-600', bg: 'bg-rose-50', icon: XCircle, label: 'Error' },
  PENDING: { color: 'text-amber-600', bg: 'bg-amber-50', icon: Clock, label: 'Pending' },
};

export default function GpsSettingsPage() {
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [settings, setSettings] = useState<GpsSettingsData>({
    provider: '',
    appId: '',
    apiKey: '',
    baseUrl: '',
    deviceGroupId: '',
    webhookUrl: '',
    connectionStatus: 'DISCONNECTED',
    lastSyncAt: null,
    activeDevices: 0,
    lastError: null,
    enabled: false,
    hasCredentials: false,
  });

  const [originalSettings, setOriginalSettings] = useState<GpsSettingsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);
  const [showSaveModal, setShowSaveModal] = useState(false);

  useEffect(() => {
    fetchSettings();
  }, []);

  useEffect(() => {
    if (originalSettings) {
      const changed = JSON.stringify(settings) !== JSON.stringify(originalSettings);
      setHasChanges(changed);
    }
  }, [settings, originalSettings]);

  const fetchSettings = async () => {
    try {
      const { data } = await api.get('/gps/settings');
      const mapped: GpsSettingsData = {
        provider: data.provider || '',
        appId: data.appId || '',
        apiKey: '', // Never returned from API for security
        baseUrl: data.baseUrl || '',
        deviceGroupId: data.deviceGroupId || '',
        webhookUrl: data.webhookUrl || '',
        connectionStatus: data.connectionStatus || 'DISCONNECTED',
        lastSyncAt: data.lastSyncAt,
        activeDevices: data.activeDevices || 0,
        lastError: data.lastError,
        enabled: data.enabled || false,
        hasCredentials: data.hasCredentials || false,
      };
      setSettings(mapped);
      setOriginalSettings({ ...mapped, apiKey: '' });
    } catch (err) {
      showToast('Failed to load GPS settings', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (field: keyof GpsSettingsData, value: string | boolean) => {
    setSettings(prev => ({ ...prev, [field]: value }));
    setTestResult(null);
  };

  const handleTestConnection = async () => {
    if (!settings.provider) {
      showToast('Please select a GPS provider first', 'error');
      return;
    }

    setTesting(true);
    setTestResult(null);

    try {
      // If we have stored credentials and no new API key entered, test with stored
      const hasNewKey = settings.apiKey && settings.apiKey.length > 0;
      let result;

      if (hasNewKey) {
        // Test with raw credentials before saving
        const { data } = await api.post('/gps/settings/test-raw', {
          provider: settings.provider,
          appId: settings.appId,
          apiKey: settings.apiKey,
          baseUrl: settings.baseUrl,
          deviceGroupId: settings.deviceGroupId,
        });
        result = data;
      } else if (settings.hasCredentials) {
        const { data } = await api.post('/gps/settings/test');
        result = data;
      } else {
        showToast('Please enter API credentials to test', 'error');
        setTesting(false);
        return;
      }

      setTestResult(result);
      if (result.success) {
        showToast(`Connected successfully! ${result.devicesFound ?? 0} devices found.`, 'success');
      } else {
        showToast(result.message || 'Connection failed', 'error');
      }
    } catch (err: any) {
      const msg = (err as any).userMessage || 'Connection test failed';
      setTestResult({ success: false, message: msg, provider: settings.provider, devicesFound: 0, responseTime: '', errorCode: 'ERROR' });
      showToast(msg, 'error');
    } finally {
      setTesting(false);
    }
  };


  const handleSave = async () => {
    if (!settings.provider) {
      showToast('Provider is required', 'error');
      return;
    }

    setSaving(true);
    try {
      const payload: any = {
        provider: settings.provider,
        appId: settings.appId,
        baseUrl: settings.baseUrl,
        deviceGroupId: settings.deviceGroupId,
        webhookUrl: settings.webhookUrl,
        enabled: settings.enabled,
      };

      // Only send API key if user entered a new one
      if (settings.apiKey && settings.apiKey.trim().length > 0) {
        payload.apiKey = settings.apiKey;
      }

      const { data } = await api.post('/gps/settings', payload);

      const mapped: GpsSettingsData = {
        provider: data.provider || '',
        appId: data.appId || '',
        apiKey: '',
        baseUrl: data.baseUrl || '',
        deviceGroupId: data.deviceGroupId || '',
        webhookUrl: data.webhookUrl || '',
        connectionStatus: data.connectionStatus || 'DISCONNECTED',
        lastSyncAt: data.lastSyncAt,
        activeDevices: data.activeDevices || 0,
        lastError: data.lastError,
        enabled: data.enabled || false,
        hasCredentials: data.hasCredentials || false,
      };

      setSettings(mapped);
      setOriginalSettings({ ...mapped });
      setHasChanges(false);
      setShowSaveModal(false);
      showToast('GPS settings saved successfully', 'success');
      
      // Redirect to GPS Tracking after save
      setTimeout(() => navigate('/gps-tracking'), 1500);
    } catch (err: any) {
      const msg = (err as any).userMessage || 'Failed to save settings';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleSyncDevices = async () => {
    setSyncing(true);
    try {
      const { data } = await api.post('/gps/settings/sync');
      if (data.success) {
        showToast(`Synced ${data.devicesSynced} devices (${data.devicesUpdated} updated)`, 'success');
        fetchSettings();
      } else {
        showToast(data.message || 'Sync failed', 'error');
      }
    } catch (err: any) {
      showToast((err as any).userMessage || 'Device sync failed', 'error');
    } finally {
      setSyncing(false);
    }
  };

  const handleDelete = async () => {
    try {
      await api.delete('/gps/settings');
      setSettings({
        provider: '', appId: '', apiKey: '', baseUrl: '', deviceGroupId: '',
        webhookUrl: '', connectionStatus: 'DISCONNECTED', lastSyncAt: null,
        activeDevices: 0, lastError: null, enabled: false, hasCredentials: false,
      });
      setOriginalSettings(null);
      setHasChanges(false);
      setShowDeleteModal(false);
      showToast('GPS settings removed', 'success');
    } catch {
      showToast('Failed to remove settings', 'error');
    }
  };

  const statusConfig = STATUS_CONFIG[settings.connectionStatus] || STATUS_CONFIG.DISCONNECTED;
  const StatusIcon = statusConfig.icon;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade max-w-6xl mx-auto w-full p-3 sm:p-4 lg:p-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">GPS Integration Settings</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">
            Configure your GPS provider credentials and manage fleet tracking
          </p>
        </div>
        {settings.hasCredentials && (
          <button
            onClick={() => setShowDeleteModal(true)}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 text-rose-600 bg-rose-50 rounded-xl text-sm font-medium hover:bg-rose-100 transition-all"
          >
            <Trash2 size={16} />
            Remove Integration
          </button>
        )}
      </div>

      {/* Status Card */}
      <div className="card-premium p-3 sm:p-5">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <div className={`w-12 h-12 rounded-xl ${statusConfig.bg} flex items-center justify-center`}>
              <StatusIcon size={24} className={statusConfig.color} />
            </div>
            <div className="min-w-0">
              <h3 className="text-base font-bold text-[#1e293b]">Connection Status</h3>
              <p className="text-sm text-slate-400">{statusConfig.label}</p>
            </div>
          </div>
          <div className={`px-4 py-1.5 rounded-full text-xs font-semibold ${statusConfig.bg} ${statusConfig.color}`}>
            {settings.connectionStatus}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Server size={14} />
              <span className="text-xs font-medium">Provider</span>
            </div>
            <p className="text-sm font-bold text-[#1e293b]">{settings.provider || 'Not set'}</p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Radio size={14} />
              <span className="text-xs font-medium">Active Devices</span>
            </div>
            <p className="text-sm font-bold text-[#1e293b]">{settings.activeDevices}</p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Clock size={14} />
              <span className="text-xs font-medium">Last Sync</span>
            </div>
            <p className="text-sm font-bold text-[#1e293b]">
              {settings.lastSyncAt
                ? new Date(settings.lastSyncAt).toLocaleString()
                : 'Never'}
            </p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Shield size={14} />
              <span className="text-xs font-medium">Credentials</span>
            </div>
            <p className="text-sm font-bold text-[#1e293b]">
              {settings.hasCredentials ? 'Stored' : 'Not stored'}
            </p>
          </div>
        </div>

        {settings.lastError && (
          <div className="mt-4 bg-rose-50 border border-rose-100 rounded-xl p-4 flex items-start gap-3">
            <AlertTriangle size={18} className="text-rose-500 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-rose-700">Last Error</p>
              <p className="text-sm text-rose-600 mt-0.5">{settings.lastError}</p>
            </div>
          </div>
        )}
      </div>

      {/* Provider Selector */}
      <div className="card-premium space-y-6 p-3 sm:p-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Satellite size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">GPS Provider</h3>
            <p className="text-sm text-slate-400 font-normal">Select your tracking platform</p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {PROVIDERS.map((p) => {
            const selected = settings.provider === p.value;
            return (
              <button
                key={p.value}
                onClick={() => handleChange('provider', p.value)}
                className={`relative p-4 rounded-xl border-2 text-left transition-all ${
                  selected
                    ? 'border-brand-500 bg-brand-50/50'
                    : 'border-[#e8e6e1] bg-white hover:border-brand-200 hover:bg-[#f5f5f0]/50'
                }`}
              >
                {selected && (
                  <div className="absolute top-3 right-3 w-5 h-5 bg-brand-500 rounded-full flex items-center justify-center">
                    <CheckCircle2 size={12} className="text-white" />
                  </div>
                )}
                <p className={`text-sm font-bold ${selected ? 'text-brand-700' : 'text-[#1e293b]'}`}>
                  {p.label}
                </p>
                <p className="text-xs text-slate-400 mt-1">{p.description}</p>
              </button>
            );
          })}
        </div>
      </div>

      {/* API Credentials Form */}
      <div className="card-premium space-y-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <Key size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">API Credentials</h3>
            <p className="text-sm text-slate-400 font-normal">Your credentials are encrypted at rest</p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">
              APP ID / Account ID
            </label>
            <div className="relative">
              <Shield size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={settings.appId}
                onChange={(e) => handleChange('appId', e.target.value)}
                placeholder="your-app-id"
                className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">
              API Key
              {settings.hasCredentials && !settings.apiKey && (
                <span className="ml-2 text-xs text-emerald-600 font-medium">• Stored securely</span>
              )}
            </label>
            <div className="relative">
              <Key size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type={showApiKey ? 'text' : 'password'}
                value={settings.apiKey}
                onChange={(e) => handleChange('apiKey', e.target.value)}
                placeholder={settings.hasCredentials ? '••••••••••••••••' : 'Enter your API key'}
                className="w-full pl-11 pr-12 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
              <button
                onClick={() => setShowApiKey(!showApiKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-[#1e293b] transition-colors"
              >
                {showApiKey ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <p className="text-xs text-slate-400 mt-1.5">
              {settings.hasCredentials
                ? 'Leave blank to keep existing key. Enter new value to replace.'
                : 'Your API key will be encrypted before storage'}
            </p>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">
              Base URL
            </label>
            <div className="relative">
              <Globe size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={settings.baseUrl}
                onChange={(e) => handleChange('baseUrl', e.target.value)}
                placeholder="https://api.provider.com"
                className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">
              Device Group ID
            </label>
            <div className="relative">
              <Car size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={settings.deviceGroupId}
                onChange={(e) => handleChange('deviceGroupId', e.target.value)}
                placeholder="Optional group filter"
                className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-[#1e293b] mb-2">
              Webhook URL <span className="text-slate-400 font-normal">(optional)</span>
            </label>
            <div className="relative">
              <Link2 size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={settings.webhookUrl}
                onChange={(e) => handleChange('webhookUrl', e.target.value)}
                placeholder="https://your-domain.com/api/gps/webhook"
                className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
          </div>
        </div>

        {/* Enable Toggle */}
        <div className="flex items-center justify-between pt-4 border-t border-[#e8e6e1]/60">
          <div className="flex items-center gap-3">
            <div className={`w-10 h-6 rounded-full relative transition-colors cursor-pointer ${settings.enabled ? 'bg-brand-500' : 'bg-slate-300'}`}
              onClick={() => handleChange('enabled', !settings.enabled)}>
              <div className={`absolute top-0.5 w-5 h-5 bg-white rounded-full shadow-md transition-transform ${settings.enabled ? 'translate-x-4' : 'translate-x-0.5'}`} />
            </div>
            <div>
              <p className="text-sm font-semibold text-[#1e293b]">Enable GPS Tracking</p>
              <p className="text-xs text-slate-400">Activate live tracking for your fleet</p>
            </div>
          </div>
        </div>
      </div>

      {/* Test Result */}
      {testResult && (
        <div className={`rounded-xl p-4 flex items-start gap-3 ${testResult.success ? 'bg-emerald-50 border border-emerald-100' : 'bg-rose-50 border border-rose-100'}`}>
          {testResult.success ? (
            <CheckCircle2 size={20} className="text-emerald-600 shrink-0 mt-0.5" />
          ) : (
            <XCircle size={20} className="text-rose-600 shrink-0 mt-0.5" />
          )}
          <div className="flex-1">
            <p className={`text-sm font-semibold ${testResult.success ? 'text-emerald-700' : 'text-rose-700'}`}>
              {testResult.success ? 'Connection Successful' : 'Connection Failed'}
            </p>
            <p className={`text-sm mt-0.5 ${testResult.success ? 'text-emerald-600' : 'text-rose-600'}`}>
              {testResult.message}
            </p>
            {testResult.success && (
              <div className="flex gap-4 mt-2">
                <span className="text-xs text-emerald-600 font-medium">
                  {testResult.devicesFound ?? 0} devices found
                </span>
                {testResult.responseTime && (
                  <span className="text-xs text-emerald-600 font-medium">
                    {testResult.responseTime}
                  </span>
                )}
              </div>
            )}
            {!testResult.success && testResult.errorCode && (
              <p className="text-xs text-rose-500 mt-1 font-mono">{testResult.errorCode}</p>
            )}
          </div>
        </div>
      )}

      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <button
          onClick={handleTestConnection}
          disabled={testing || !settings.provider}
          className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] text-[#1e293b] rounded-xl font-medium text-sm hover:bg-[#f5f5f0] hover:border-brand-300 transition-all disabled:opacity-50 disabled:cursor-not-allowed w-full sm:w-auto"
        >
          {testing ? <Loader2 size={16} className="animate-spin" /> : <Activity size={16} />}
          Test Connection
        </button>

        {settings.hasCredentials && (
          <button
            onClick={handleSyncDevices}
            disabled={syncing}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] text-[#1e293b] rounded-xl font-medium text-sm hover:bg-[#f5f5f0] hover:border-brand-300 transition-all disabled:opacity-50 disabled:cursor-not-allowed w-full sm:w-auto"
          >
            {syncing ? <Loader2 size={16} className="animate-spin" /> : <RefreshCw size={16} />}
            Sync Devices
          </button>
        )}

        <div className="flex-1" />

        {hasChanges && (
          <button
            onClick={() => setShowSaveModal(true)}
            disabled={saving}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-50 w-full sm:w-auto"
          >
            {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            Save Configuration
          </button>
        )}
      </div>

      {/* Save Confirmation Modal */}
      <Modal isOpen={showSaveModal} onClose={() => setShowSaveModal(false)} title="Save GPS Configuration" maxWidth="max-w-md">
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-3 bg-brand-50 rounded-xl">
            <Shield size={20} className="text-brand-500" />
            <p className="text-sm text-brand-700">
              Your API credentials will be encrypted using AES-256 GCM before storage.
            </p>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Provider</span>
              <span className="font-semibold text-[#1e293b]">{settings.provider}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">APP ID</span>
              <span className="font-semibold text-[#1e293b]">{settings.appId || '—'}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Base URL</span>
              <span className="font-semibold text-[#1e293b]">{settings.baseUrl || '—'}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Status</span>
              <span className="font-semibold text-[#1e293b]">{settings.enabled ? 'Enabled' : 'Disabled'}</span>
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              onClick={() => setShowSaveModal(false)}
              className="flex-1 px-4 py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl text-sm font-medium hover:bg-[#e8e6e1] transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex-1 px-4 py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium hover:bg-brand-600 transition-all disabled:opacity-50"
            >
              {saving ? <Loader2 size={16} className="animate-spin mx-auto" /> : 'Confirm & Save'}
            </button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal isOpen={showDeleteModal} onClose={() => setShowDeleteModal(false)} title="Remove GPS Integration" maxWidth="max-w-md">
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-3 bg-rose-50 rounded-xl">
            <AlertTriangle size={20} className="text-rose-500" />
            <p className="text-sm text-rose-700">
              This will permanently remove all GPS credentials and tracking configuration for your agency.
            </p>
          </div>
          <p className="text-sm text-slate-500">
            Your vehicle data will remain, but live tracking will be disabled. This action cannot be undone.
          </p>
          <div className="flex gap-3 pt-2">
            <button
              onClick={() => setShowDeleteModal(false)}
              className="flex-1 px-4 py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl text-sm font-medium hover:bg-[#e8e6e1] transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleDelete}
              className="flex-1 px-4 py-2.5 bg-rose-500 text-white rounded-xl text-sm font-medium hover:bg-rose-600 transition-all"
            >
              Remove Integration
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
