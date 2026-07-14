import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import Modal from '../components/Modal';
import ApiErrorState from '../components/ApiErrorState';
import GpsFieldHelp from '../components/gps/GpsFieldHelp';
import GpsConnectGuideModal from '../components/gps/GpsConnectGuideModal';
import GpsDeviceMappingTable, { type GpsSyncDevice, type GpsVehicleOption } from '../components/gps/GpsDeviceMappingTable';
import {
  Satellite, Key, Globe, Link2, Save, RefreshCw,
  CheckCircle2, XCircle, AlertTriangle, Loader2, Shield,
  Server, Radio, Clock, Car, Eye, EyeOff,
  Trash2, Wifi, WifiOff, Activity, BookOpen, PowerOff,
  MapPin, Bell, Navigation
} from 'lucide-react';

interface GpsSettingsData {
  id?: number;
  provider: string;
  appId: string;
  apiKey: string;
  password: string;
  authHeaderName: string;
  authPrefix: string;
  baseUrl: string;
  deviceGroupId: string;
  webhookUrl: string;
  connectionStatus: string;
  lastSyncAt: string | null;
  lastTestedAt: string | null;
  activeDevices: number;
  lastError: string | null;
  enabled: boolean;
  hasCredentials: boolean;
  hasPassword: boolean;
  // Geofence & alert config
  cityLat: string;
  cityLng: string;
  radiusKm: string;
  movementThresholdM: string;
  inactivityTimeoutMin: string;
  notifyMovement: boolean;
  notifyGeofence: boolean;
  notifyOffline: boolean;
  pollingIntervalSec: string;
}

interface TestResult {
  success: boolean;
  message: string;
  provider: string;
  devicesFound: number;
  responseTime: string;
  errorCode: string;
  action?: string;
}

const IOPGPS_BASE_URL = 'https://open.iopgps.com';

const PROVIDERS = [
  { value: 'IOPGPS', label: 'IOPGPS', description: 'Professional fleet tracking platform' },
  { value: 'TRACCAR', label: 'Traccar', description: 'Open source GPS tracking system' },
  { value: 'WIALON', label: 'Wialon', description: 'Gurtam unified fleet management' },
  { value: 'GPSWOX', label: 'GPSWOX', description: 'White-label GPS tracking software' },
  { value: 'CUSTOM', label: 'Custom API', description: 'Your own GPS provider endpoint' },
];

const STATUS_CONFIG: Record<string, { color: string; bg: string; icon: any; label: string }> = {
  CONNECTED: { color: 'text-emerald-600', bg: 'bg-emerald-50', icon: Wifi, label: 'Connected' },
  CONFIGURED: { color: 'text-blue-600', bg: 'bg-blue-50', icon: Shield, label: 'Configured (not tested)' },
  DISCONNECTED: { color: 'text-slate-500', bg: 'bg-slate-50', icon: WifiOff, label: 'Disconnected' },
  FAILED: { color: 'text-rose-600', bg: 'bg-rose-50', icon: XCircle, label: 'Failed' },
  DISABLED: { color: 'text-amber-600', bg: 'bg-amber-50', icon: PowerOff, label: 'Disabled' },
  // Legacy values from before this status vocabulary existed — kept so old
  // rows don't render as an unrecognized/blank state.
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
    password: '',
    authHeaderName: '',
    authPrefix: '',
    baseUrl: '',
    deviceGroupId: '',
    webhookUrl: '',
    connectionStatus: 'DISCONNECTED',
    lastSyncAt: null,
    lastTestedAt: null,
    activeDevices: 0,
    lastError: null,
    enabled: false,
    hasCredentials: false,
    hasPassword: false,
    cityLat: '',
    cityLng: '',
    radiusKm: '50',
    movementThresholdM: '50',
    inactivityTimeoutMin: '30',
    notifyMovement: false,
    notifyGeofence: false,
    notifyOffline: false,
    pollingIntervalSec: '30',
  });

  const [originalSettings, setOriginalSettings] = useState<GpsSettingsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [testResult, setTestResult] = useState<TestResult | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showDeactivateModal, setShowDeactivateModal] = useState(false);
  const [showDeleteCredentialsModal, setShowDeleteCredentialsModal] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const [deletingCredentials, setDeletingCredentials] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [guideOpen, setGuideOpen] = useState(false);
  const [devices, setDevices] = useState<GpsSyncDevice[]>([]);
  const [vehicles, setVehicles] = useState<GpsVehicleOption[]>([]);

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
      setLoading(true);
      setLoadError('');
      const { data } = await api.get('/gps/settings');
      const mapped: GpsSettingsData = {
        provider: data.provider || '',
        appId: data.appId || '',
        apiKey: '',        // never returned
        password: '',      // never returned
        authHeaderName: data.authHeaderName || '',
        authPrefix: data.authPrefix || '',
        baseUrl: data.baseUrl || '',
        deviceGroupId: data.deviceGroupId || '',
        webhookUrl: data.webhookUrl || '',
        connectionStatus: data.connectionStatus || 'DISCONNECTED',
        lastSyncAt: data.lastSyncAt,
        lastTestedAt: data.lastTestedAt,
        activeDevices: data.activeDevices || 0,
        lastError: data.lastError,
        enabled: data.enabled || false,
        hasCredentials: data.hasCredentials || false,
        hasPassword: data.hasPassword || false,
        cityLat: data.cityLat != null ? String(data.cityLat) : '',
        cityLng: data.cityLng != null ? String(data.cityLng) : '',
        radiusKm: data.radiusKm != null ? String(data.radiusKm) : '50',
        movementThresholdM: data.movementThresholdM != null ? String(data.movementThresholdM) : '50',
        inactivityTimeoutMin: data.inactivityTimeoutMin != null ? String(data.inactivityTimeoutMin) : '30',
        notifyMovement: data.notifyMovement || false,
        notifyGeofence: data.notifyGeofence || false,
        notifyOffline: data.notifyOffline || false,
        pollingIntervalSec: data.pollingIntervalSec != null ? String(data.pollingIntervalSec) : '30',
      };
      setSettings(mapped);
      setOriginalSettings({ ...mapped });
      if (mapped.hasCredentials) fetchVehicles();
    } catch (err: any) {
      if (err?.response?.status === 404) {
        setOriginalSettings(null);
      } else {
        setLoadError('Unable to load GPS information. Please try again later.');
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchVehicles = async () => {
    try {
      const { data } = await api.get('/gps/vehicles');
      setVehicles((data || []).map((v: any) => ({
        id: v.id,
        marque: v.marque,
        plate: v.plate,
        gpsDeviceId: v.gpsDeviceId,
      })));
    } catch {
      // Non-blocking: device mapping table will simply show no vehicle options.
    }
  };

  const handleChange = (field: keyof GpsSettingsData, value: string | boolean) => {
    setSettings(prev => ({ ...prev, [field]: value }));
    setTestResult(null);
  };

  const handleTestConnection = async () => {
    if (!settings.provider) {
      showToast('Please select a GPS provider first', 'warning');
      return;
    }

    setTesting(true);
    setTestResult(null);

    try {
      // If we have stored credentials and no new API key entered, test with stored
      const hasNewKey = settings.apiKey && settings.apiKey.length > 0;
      let result;

      const hasNewPassword = settings.password && settings.password.length > 0;

      if (hasNewKey || hasNewPassword) {
        // Test with raw credentials before saving
        const rawPayload: any = {
          provider: settings.provider,
          appId: settings.appId,
          baseUrl: settings.baseUrl,
          deviceGroupId: settings.deviceGroupId,
        };
        if (hasNewKey) rawPayload.apiKey = settings.apiKey;
        if (hasNewPassword) rawPayload.password = settings.password;
        const { data } = await api.post('/gps/settings/test-raw', rawPayload);
        result = data;
      } else if (settings.hasCredentials || settings.hasPassword) {
        const { data } = await api.post('/gps/settings/test');
        result = data;
      } else {
        showToast('Please enter API credentials to test', 'warning');
        setTesting(false);
        return;
      }

      setTestResult(result);
      if (result.success) {
        showToast(`Connected successfully! ${result.devicesFound ?? 0} devices found.`, 'success');
      } else {
        showToast('Unable to connect to the GPS provider. Please verify the configuration.', 'error');
      }
    } catch (err: any) {
      const msg = 'Unable to connect to the GPS provider. Please try again later.';
      setTestResult({ success: false, message: msg, provider: settings.provider, devicesFound: 0, responseTime: '', errorCode: 'ERROR' });
      showToast(msg, 'error');
    } finally {
      setTesting(false);
    }
  };


  const handleSave = async () => {
    if (!settings.provider) {
      showToast('Provider is required', 'warning');
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
        // Geofence & alert config
        cityLat: settings.cityLat !== '' ? parseFloat(settings.cityLat) : null,
        cityLng: settings.cityLng !== '' ? parseFloat(settings.cityLng) : null,
        radiusKm: settings.radiusKm !== '' ? parseFloat(settings.radiusKm) : null,
        movementThresholdM: settings.movementThresholdM !== '' ? parseInt(settings.movementThresholdM) : null,
        inactivityTimeoutMin: settings.inactivityTimeoutMin !== '' ? parseInt(settings.inactivityTimeoutMin) : null,
        notifyMovement: settings.notifyMovement,
        notifyGeofence: settings.notifyGeofence,
        notifyOffline: settings.notifyOffline,
        pollingIntervalSec: settings.pollingIntervalSec !== '' ? parseInt(settings.pollingIntervalSec) : null,
      };

      // Only send credentials if user entered new values
      if (settings.apiKey && settings.apiKey.trim().length > 0) {
        payload.apiKey = settings.apiKey;
      }
      if (settings.password && settings.password.trim().length > 0) {
        payload.password = settings.password;
      }
      if (settings.authHeaderName) payload.authHeaderName = settings.authHeaderName;
      if (settings.authPrefix) payload.authPrefix = settings.authPrefix;

      const { data } = await api.post('/gps/settings', payload);

      const mapped: GpsSettingsData = {
        provider: data.provider || '',
        appId: data.appId || '',
        apiKey: '',
        password: '',
        authHeaderName: data.authHeaderName || '',
        authPrefix: data.authPrefix || '',
        baseUrl: data.baseUrl || '',
        deviceGroupId: data.deviceGroupId || '',
        webhookUrl: data.webhookUrl || '',
        connectionStatus: data.connectionStatus || 'DISCONNECTED',
        lastSyncAt: data.lastSyncAt,
        lastTestedAt: data.lastTestedAt,
        activeDevices: data.activeDevices || 0,
        lastError: data.lastError,
        enabled: data.enabled || false,
        hasCredentials: data.hasCredentials || false,
        hasPassword: data.hasPassword || false,
        cityLat: data.cityLat != null ? String(data.cityLat) : '',
        cityLng: data.cityLng != null ? String(data.cityLng) : '',
        radiusKm: data.radiusKm != null ? String(data.radiusKm) : '50',
        movementThresholdM: data.movementThresholdM != null ? String(data.movementThresholdM) : '50',
        inactivityTimeoutMin: data.inactivityTimeoutMin != null ? String(data.inactivityTimeoutMin) : '30',
        notifyMovement: data.notifyMovement || false,
        notifyGeofence: data.notifyGeofence || false,
        notifyOffline: data.notifyOffline || false,
        pollingIntervalSec: data.pollingIntervalSec != null ? String(data.pollingIntervalSec) : '30',
      };

      setSettings(mapped);
      setOriginalSettings({ ...mapped });
      setHasChanges(false);
      setShowSaveModal(false);
      showToast('GPS settings saved successfully', 'success');
      fetchVehicles();

      // Redirect to GPS Tracking after save
      setTimeout(() => navigate('/gps-tracking'), 1500);
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to save GPS information. Please try again later.', 'error');
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
        setDevices(data.devices || []);
        fetchVehicles();
        fetchSettings();
      } else {
        showToast(data.message || 'Unable to synchronize GPS devices. Please try again later.', 'error');
      }
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to synchronize GPS devices. Please try again later.', 'error');
    } finally {
      setSyncing(false);
    }
  };

  const handleDelete = async () => {
    try {
      await api.delete('/gps/settings');
      setSettings({
        provider: '', appId: '', apiKey: '', password: '', authHeaderName: '', authPrefix: '',
        baseUrl: '', deviceGroupId: '', webhookUrl: '', connectionStatus: 'DISCONNECTED',
        lastSyncAt: null, lastTestedAt: null, activeDevices: 0, lastError: null,
        enabled: false, hasCredentials: false, hasPassword: false,
        cityLat: '', cityLng: '', radiusKm: '50', movementThresholdM: '50',
        inactivityTimeoutMin: '30', notifyMovement: false, notifyGeofence: false,
        notifyOffline: false, pollingIntervalSec: '30',
      });
      setOriginalSettings(null);
      setHasChanges(false);
      setShowDeleteModal(false);
      setDevices([]);
      setVehicles([]);
      showToast('GPS settings removed', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to remove GPS settings. Please try again later.', 'error');
    }
  };

  const handleDeactivate = async () => {
    setDeactivating(true);
    try {
      const { data } = await api.post('/gps/settings/deactivate');
      const result = data?.data || {};
      setSettings((prev) => ({ ...prev, enabled: false, connectionStatus: result.connectionStatus || 'DISABLED' }));
      setOriginalSettings((prev) => prev ? { ...prev, enabled: false, connectionStatus: result.connectionStatus || 'DISABLED' } : prev);
      setShowDeactivateModal(false);
      showToast(data?.message || 'GPS integration deactivated successfully.', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to deactivate GPS integration. Please try again later.', 'error');
    } finally {
      setDeactivating(false);
    }
  };

  const handleDeleteCredentials = async () => {
    setDeletingCredentials(true);
    try {
      const { data } = await api.delete('/gps/settings/credentials');
      const result = data?.data || {};
      setSettings((prev) => ({
        ...prev,
        appId: '',
        apiKey: '',
        password: '',
        enabled: false,
        hasCredentials: false,
        hasPassword: false,
        connectionStatus: result.connectionStatus || 'DISCONNECTED',
      }));
      setOriginalSettings((prev) => prev ? {
        ...prev, appId: '', apiKey: '', password: '', enabled: false,
        hasCredentials: false, hasPassword: false,
        connectionStatus: result.connectionStatus || 'DISCONNECTED',
      } : prev);
      setShowDeleteCredentialsModal(false);
      setDevices([]);
      showToast(data?.message || 'GPS credentials deleted successfully.', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to delete GPS credentials. Please try again later.', 'error');
    } finally {
      setDeletingCredentials(false);
    }
  };

  const handleDeviceLinked = (vehicleId: number, deviceId: string) => {
    setVehicles((prev) => prev.map((v) => {
      if (v.id === vehicleId) return { ...v, gpsDeviceId: deviceId };
      if (v.gpsDeviceId === deviceId && v.id !== vehicleId) return { ...v, gpsDeviceId: null };
      return v;
    }));
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

  if (loadError) {
    return <div className="p-3 sm:p-4 lg:p-6"><ApiErrorState message={loadError} onRetry={fetchSettings} /></div>;
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
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => setGuideOpen(true)}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-50 text-brand-700 rounded-xl text-sm font-medium hover:bg-brand-100 transition-all"
          >
            <BookOpen size={16} />
            Open Guide
          </button>
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
      </div>

      {/* How to connect guide card */}
      <div className="card-premium p-3 sm:p-5 bg-brand-50/40 border border-brand-100">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-white flex items-center justify-center shrink-0">
            <BookOpen size={18} className="text-brand-500" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-[#1e293b]">How to connect your GPS platform</p>
            <p className="text-xs text-slate-500 mt-1">
              New to GPS integration? Follow our step-by-step guide to link your provider account and start tracking your fleet.
            </p>
          </div>
          <button
            onClick={() => setGuideOpen(true)}
            className="shrink-0 px-3 py-1.5 bg-white border border-brand-200 text-brand-700 rounded-lg text-xs font-semibold hover:bg-brand-50 transition-all"
          >
            View steps
          </button>
        </div>
      </div>

      {/* Status Card */}
      <div className="card-premium p-3 sm:p-5">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <div className={`w-12 h-12 rounded-xl ${statusConfig.bg} flex items-center justify-center`}>
              <StatusIcon size={24} className={statusConfig.color} />
            </div>
            <div className="min-w-0">
              <h3 className="text-base font-bold text-[#1e293b] flex items-center gap-1.5">
                Connection Status
                <GpsFieldHelp fieldKey="testConnection" provider={settings.provider} />
              </h3>
              <p className="text-sm text-slate-400">{statusConfig.label}</p>
            </div>
          </div>
          <div className={`px-4 py-1.5 rounded-full text-xs font-semibold ${statusConfig.bg} ${statusConfig.color}`}>
            {settings.connectionStatus}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-4">
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
              <GpsFieldHelp fieldKey="activeDevices" />
            </div>
            <p className="text-sm font-bold text-[#1e293b]">{settings.activeDevices}</p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Clock size={14} />
              <span className="text-xs font-medium">Last Sync</span>
              <GpsFieldHelp fieldKey="lastSync" />
            </div>
            <p className="text-sm font-bold text-[#1e293b]">
              {settings.lastSyncAt
                ? new Date(settings.lastSyncAt).toLocaleString()
                : 'Never'}
            </p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Activity size={14} />
              <span className="text-xs font-medium">Last Tested</span>
            </div>
            <p className="text-sm font-bold text-[#1e293b]">
              {settings.lastTestedAt
                ? new Date(settings.lastTestedAt).toLocaleString()
                : 'Never'}
            </p>
          </div>
          <div className="bg-[#f5f5f0] rounded-xl p-4">
            <div className="flex items-center gap-2 text-slate-400 mb-1">
              <Shield size={14} />
              <span className="text-xs font-medium">Credentials</span>
              <GpsFieldHelp fieldKey="credentials" />
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
            <h3 className="text-base font-bold text-[#1e293b] flex items-center gap-1.5">
              GPS Provider
              <GpsFieldHelp fieldKey="provider" />
            </h3>
            <p className="text-sm text-slate-400 font-normal">Select your tracking platform</p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {PROVIDERS.map((p) => {
            const selected = settings.provider === p.value;
            return (
              <button
                key={p.value}
                onClick={() => {
                  handleChange('provider', p.value);
                  if (p.value === 'IOPGPS') handleChange('baseUrl', IOPGPS_BASE_URL);
                }}
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
            <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
              {settings.provider === 'IOPGPS' ? 'IOPGPS Account Name' : 'APP ID / Account ID'}
              <GpsFieldHelp fieldKey="appId" provider={settings.provider} />
            </label>
            <div className="relative">
              <Shield size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={settings.appId}
                onChange={(e) => handleChange('appId', e.target.value)}
                placeholder={settings.provider === 'IOPGPS' ? 'exact account name' : 'your-app-id'}
                className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
            {settings.provider === 'IOPGPS' && (
              <p className="text-xs text-slate-400 mt-1.5">
                Use the exact account name linked to the API key generated in IOPGPS &gt; System Configuration &gt; API Key Request.
              </p>
            )}
          </div>

          <div>
            <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
              API Key
              <GpsFieldHelp fieldKey="apiKey" provider={settings.provider} />
              {settings.hasCredentials && !settings.apiKey && (
                <span className="ml-1 text-xs text-emerald-600 font-medium">• Stored securely</span>
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

          {/* Password field — Traccar Basic Auth */}
          {settings.provider === 'TRACCAR' && (
            <div>
              <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
                Password
                {settings.hasPassword && !settings.password && (
                  <span className="ml-1 text-xs text-emerald-600 font-medium">• Stored securely</span>
                )}
              </label>
              <div className="relative">
                <Key size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={settings.password}
                  onChange={(e) => handleChange('password', e.target.value)}
                  placeholder={settings.hasPassword ? '••••••••••••••••' : 'Traccar password'}
                  className="w-full pl-11 pr-12 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
                <button
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-[#1e293b] transition-colors"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              <p className="text-xs text-slate-400 mt-1.5">
                {settings.hasPassword
                  ? 'Leave blank to keep existing password. Enter new value to replace.'
                  : 'Used for Traccar Basic Auth. Encrypted before storage.'}
              </p>
            </div>
          )}

          {/* Auth header fields — Custom API */}
          {settings.provider === 'CUSTOM' && (
            <>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">Auth Header Name</label>
                <div className="relative">
                  <Shield size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    value={settings.authHeaderName}
                    onChange={(e) => handleChange('authHeaderName', e.target.value)}
                    placeholder="Authorization"
                    className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  />
                </div>
                <p className="text-xs text-slate-400 mt-1.5">HTTP header name sent to your API (default: Authorization)</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2">Auth Prefix</label>
                <div className="relative">
                  <Shield size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    value={settings.authPrefix}
                    onChange={(e) => handleChange('authPrefix', e.target.value)}
                    placeholder="Bearer"
                    className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  />
                </div>
                <p className="text-xs text-slate-400 mt-1.5">Prefix before the API key value (default: Bearer)</p>
              </div>
            </>
          )}

          {settings.provider === 'CUSTOM' ? (
            <div>
              <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
                Base URL
                <GpsFieldHelp fieldKey="baseUrl" provider={settings.provider} />
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
          ) : settings.provider === 'IOPGPS' && (
            <div>
              <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
                API Host
              </label>
              <div className="relative">
                <Globe size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={IOPGPS_BASE_URL}
                  disabled
                  className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0]/60 border border-[#e8e6e1] rounded-xl text-sm font-normal text-slate-400 cursor-not-allowed"
                />
              </div>
              <p className="text-xs text-slate-400 mt-1.5">
                RentCar always connects to the official IOPGPS API host. This value cannot be changed.
              </p>
            </div>
          )}

          <div>
            <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
              Device Group ID
              <GpsFieldHelp fieldKey="deviceGroupId" provider={settings.provider} />
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
            <label className="flex items-center gap-1.5 text-sm font-medium text-[#1e293b] mb-2">
              Webhook URL <span className="text-slate-400 font-normal">(optional)</span>
              <GpsFieldHelp fieldKey="webhookUrl" provider={settings.provider} />
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
            {!testResult.success && testResult.action && (
              <p className="text-xs text-rose-500 mt-1">{testResult.action}</p>
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

        {settings.enabled && (
          <button
            onClick={() => setShowDeactivateModal(true)}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-amber-200 text-amber-700 rounded-xl font-medium text-sm hover:bg-amber-50 transition-all w-full sm:w-auto"
          >
            <PowerOff size={16} />
            Deactivate GPS
          </button>
        )}

        {settings.hasCredentials && (
          <button
            onClick={() => setShowDeleteCredentialsModal(true)}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-rose-200 text-rose-600 rounded-xl font-medium text-sm hover:bg-rose-50 transition-all w-full sm:w-auto"
          >
            <Trash2 size={16} />
            Delete Credentials
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

      {/* Geofence & Notifications */}
      {settings.enabled && (
        <div className="card-premium p-3 sm:p-5 space-y-5">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Navigation size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">Geofence &amp; Notifications</h3>
              <p className="text-sm text-slate-400 font-normal">Define your city zone and configure automatic alerts</p>
            </div>
          </div>

          {/* City center coordinates */}
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3 flex items-center gap-1.5">
              <MapPin size={13} /> City center coordinates
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Latitude</label>
                <input
                  type="number"
                  step="0.000001"
                  value={settings.cityLat}
                  onChange={(e) => handleChange('cityLat', e.target.value)}
                  placeholder="e.g. 33.9716"
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Longitude</label>
                <input
                  type="number"
                  step="0.000001"
                  value={settings.cityLng}
                  onChange={(e) => handleChange('cityLng', e.target.value)}
                  placeholder="e.g. -6.8498"
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          {/* Zone & timing thresholds */}
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3 flex items-center gap-1.5">
              <Globe size={13} /> Zone &amp; timing thresholds
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Zone radius (km)</label>
                <input
                  type="number"
                  min="1"
                  value={settings.radiusKm}
                  onChange={(e) => handleChange('radiusKm', e.target.value)}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Movement threshold (m)</label>
                <input
                  type="number"
                  min="10"
                  value={settings.movementThresholdM}
                  onChange={(e) => handleChange('movementThresholdM', e.target.value)}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Offline timeout (min)</label>
                <input
                  type="number"
                  min="5"
                  value={settings.inactivityTimeoutMin}
                  onChange={(e) => handleChange('inactivityTimeoutMin', e.target.value)}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-1.5">Polling interval (sec)</label>
                <input
                  type="number"
                  min="15"
                  value={settings.pollingIntervalSec}
                  onChange={(e) => handleChange('pollingIntervalSec', e.target.value)}
                  className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
              </div>
            </div>
          </div>

          {/* Alert toggles */}
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3 flex items-center gap-1.5">
              <Bell size={13} /> Alert notifications
            </p>
            <div className="space-y-3">
              {([
                { field: 'notifyMovement' as const, label: 'Movement alerts', desc: 'Alert when a vehicle starts or stops moving' },
                { field: 'notifyGeofence' as const, label: 'Geofence alerts', desc: 'Alert when a vehicle leaves or re-enters the city zone' },
                { field: 'notifyOffline' as const, label: 'Offline alerts', desc: 'Alert when a vehicle goes offline beyond the timeout' },
              ]).map(({ field, label, desc }) => (
                <div key={field} className="flex items-center justify-between py-2 border-b border-[#e8e6e1]/40 last:border-0">
                  <div>
                    <p className="text-sm font-medium text-[#1e293b]">{label}</p>
                    <p className="text-xs text-slate-400">{desc}</p>
                  </div>
                  <div
                    className={`w-10 h-6 rounded-full relative transition-colors cursor-pointer ${settings[field] ? 'bg-brand-500' : 'bg-slate-300'}`}
                    onClick={() => handleChange(field, !settings[field])}
                  >
                    <div className={`absolute top-0.5 w-5 h-5 bg-white rounded-full shadow-md transition-transform ${settings[field] ? 'translate-x-4' : 'translate-x-0.5'}`} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Device Mapping */}
      {settings.hasCredentials && (
        <div className="card-premium p-3 sm:p-5">
          <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Car size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b] flex items-center gap-1.5">
                Device Mapping
                <GpsFieldHelp fieldKey="deviceMapping" />
              </h3>
              <p className="text-sm text-slate-400 font-normal">Link GPS devices found on your provider to your vehicles</p>
            </div>
          </div>
          <GpsDeviceMappingTable devices={devices} vehicles={vehicles} onLinked={handleDeviceLinked} />
        </div>
      )}

      <GpsConnectGuideModal isOpen={guideOpen} onClose={() => setGuideOpen(false)} provider={settings.provider} />

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

      {/* Deactivate Confirmation Modal */}
      <Modal isOpen={showDeactivateModal} onClose={() => setShowDeactivateModal(false)} title="Deactivate GPS Integration" maxWidth="max-w-md">
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-3 bg-amber-50 rounded-xl">
            <PowerOff size={20} className="text-amber-500" />
            <p className="text-sm text-amber-700">
              Live tracking and device sync will stop. Your stored credentials are kept, so you can reactivate later without re-entering your API key.
            </p>
          </div>
          <div className="flex gap-3 pt-2">
            <button
              onClick={() => setShowDeactivateModal(false)}
              className="flex-1 px-4 py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl text-sm font-medium hover:bg-[#e8e6e1] transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleDeactivate}
              disabled={deactivating}
              className="flex-1 px-4 py-2.5 bg-amber-500 text-white rounded-xl text-sm font-medium hover:bg-amber-600 transition-all disabled:opacity-50"
            >
              {deactivating ? <Loader2 size={16} className="animate-spin mx-auto" /> : 'Deactivate'}
            </button>
          </div>
        </div>
      </Modal>

      {/* Delete Credentials Confirmation Modal */}
      <Modal isOpen={showDeleteCredentialsModal} onClose={() => setShowDeleteCredentialsModal(false)} title="Delete GPS Credentials" maxWidth="max-w-md">
        <div className="space-y-4">
          <div className="flex items-center gap-3 p-3 bg-rose-50 rounded-xl">
            <AlertTriangle size={20} className="text-rose-500" />
            <p className="text-sm text-rose-700">
              Deleting credentials will disconnect this agency from the GPS provider. Existing vehicle GPS history will be kept. Continue?
            </p>
          </div>
          <p className="text-sm text-slate-500">
            Provider and Base URL are kept so you can reconnect later without looking them up again.
          </p>
          <div className="flex gap-3 pt-2">
            <button
              onClick={() => setShowDeleteCredentialsModal(false)}
              className="flex-1 px-4 py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl text-sm font-medium hover:bg-[#e8e6e1] transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleDeleteCredentials}
              disabled={deletingCredentials}
              className="flex-1 px-4 py-2.5 bg-rose-500 text-white rounded-xl text-sm font-medium hover:bg-rose-600 transition-all disabled:opacity-50"
            >
              {deletingCredentials ? <Loader2 size={16} className="animate-spin mx-auto" /> : 'Delete Credentials'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
