import { BellRing, Moon, Play, Save, Volume2, VolumeX } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { type SoundEvent, useNotificationSound } from '../context/NotificationSoundContext';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';

const eventLabels: { event: SoundEvent; labelKey: string; label: string; descriptionKey: string; description: string }[] = [
  { event: 'newReservation', labelKey: 'soundSettings.eventNames.newReservation', label: 'New Reservation', descriptionKey: 'soundSettings.eventDescriptions.newReservation', description: 'Soft booking chime' },
  { event: 'contractSigned', labelKey: 'soundSettings.eventNames.contractSigned', label: 'Contract Signed', descriptionKey: 'soundSettings.eventDescriptions.contractSigned', description: 'Clean confirmation tone' },
  { event: 'paymentReceived', labelKey: 'soundSettings.eventNames.paymentReceived', label: 'Payment Received', descriptionKey: 'soundSettings.eventDescriptions.paymentReceived', description: 'Premium success tone' },
  { event: 'vehicleReturned', labelKey: 'soundSettings.eventNames.vehicleReturned', label: 'Vehicle Returned', descriptionKey: 'soundSettings.eventDescriptions.vehicleReturned', description: 'Soft completion tone' },
  { event: 'gpsAlert', labelKey: 'soundSettings.eventNames.gpsAlert', label: 'GPS Alert', descriptionKey: 'soundSettings.eventDescriptions.gpsAlert', description: 'Short warning pulse' },
  { event: 'subscriptionExpiring', labelKey: 'soundSettings.eventNames.subscriptionExpiring', label: 'Subscription Expiring', descriptionKey: 'soundSettings.eventDescriptions.subscriptionExpiring', description: 'Subtle warning tone' },
  { event: 'error', labelKey: 'soundSettings.eventNames.error', label: 'Error / Failed Action', descriptionKey: 'soundSettings.eventDescriptions.error', description: 'Low soft error tone' },
  { event: 'supportMessage', labelKey: 'soundSettings.eventNames.supportMessage', label: 'New Support Message', descriptionKey: 'soundSettings.eventDescriptions.supportMessage', description: 'Light message pop' },
];

export default function SoundSettings() {
  const { preferences, updatePreferences, setEventEnabled, testSound, temporarilyMute, audioReady } = useNotificationSound();
  const { showToast } = useToast();
  const { t } = useTranslation();
  const [saving, setSaving] = useState(false);

  const saveSoundSettings = async () => {
    setSaving(true);
    try {
      const { data } = await api.put('/sound-settings', preferences);
      showToast(data?.message || t('soundSettings.savedSuccess', 'Sound settings saved successfully'), 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || t('soundSettings.saveFailed', 'Unable to save sound settings. Please try again.'), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-4 rounded-2xl border border-slate-100 bg-white p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <BellRing size={16} className="text-brand-500" />
            <h4 className="text-sm font-bold text-[#1e293b]">{t('soundSettings.title', 'Sounds')}</h4>
          </div>
          <p className="mt-1 text-xs text-slate-500">{t('soundSettings.subtitle', 'Premium, subtle audio feedback for important agency events.')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => testSound()} className="flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white">
            <Play size={14} /> {t('soundSettings.testSound', 'Test sound')}
          </button>
          <button type="button" onClick={saveSoundSettings} disabled={saving} className="flex items-center justify-center gap-2 rounded-xl bg-brand-500 px-4 py-2 text-xs font-bold text-white disabled:opacity-60">
            {saving ? <span className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" /> : <Save size={14} />}
            {saving ? t('common.saving') : t('soundSettings.saveSoundSettings', 'Save Sound Settings')}
          </button>
        </div>
      </div>

      {!audioReady && (
        <div className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">
          {t('soundSettings.audioUnlockNotice', 'Browser audio unlocks after the first click or key press. Visual notifications always remain active.')}
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <ToggleCard
          title={t('soundSettings.enableSounds', 'Enable notification sounds')}
          description={t('soundSettings.enableSoundsDesc', 'Turn all notification sounds on or off.')}
          checked={preferences.enabled}
          onChange={(checked) => updatePreferences({ enabled: checked })}
          icon={preferences.enabled ? <Volume2 size={18} /> : <VolumeX size={18} />}
        />
        <ToggleCard
          title={t('soundSettings.doNotDisturb', 'Do not disturb')}
          description={t('soundSettings.doNotDisturbDesc', 'Mute all sounds until disabled.')}
          checked={preferences.doNotDisturb}
          onChange={(checked) => updatePreferences({ doNotDisturb: checked })}
          icon={<Moon size={18} />}
        />
      </div>

      <div className="space-y-2">
        <label className="text-xs font-bold uppercase tracking-wider text-slate-400">{t('soundSettings.volume', 'Volume')}</label>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={preferences.volume}
          onChange={(event) => updatePreferences({ volume: Number(event.target.value) })}
          className="w-full accent-brand-500"
        />
        <p className="text-xs text-slate-400">{Math.round(preferences.volume * 100)}%</p>
      </div>

      <div className="space-y-2">
        <label className="text-xs font-bold uppercase tracking-wider text-slate-400">{t('soundSettings.soundTheme', 'Sound theme')}</label>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {(['minimal', 'premium', 'automotive', 'silent'] as const).map((theme) => (
            <button key={theme} type="button" onClick={() => updatePreferences({ theme })}
              className={`rounded-xl border px-3 py-2 text-sm font-bold capitalize transition-all ${
                preferences.theme === theme ? 'border-brand-500 bg-brand-50 text-brand-600' : 'border-slate-200 bg-white text-slate-500'
              }`}>
              {theme === 'silent' ? t('soundSettings.silentMode', 'Silent Mode') : theme}
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <ToggleCard title={t('soundSettings.muteNightMode', 'Mute during night mode')} description={t('soundSettings.muteNightModeDesc', 'Quiet hours: 22:00 to 07:00.')} checked={preferences.muteNightMode}
          onChange={(checked) => updatePreferences({ muteNightMode: checked })} />
        <ToggleCard title={t('soundSettings.muteSystemAlerts', 'Mute system alerts')} description={t('soundSettings.muteSystemAlertsDesc', 'Suppress GPS, subscription, and error sounds.')} checked={preferences.muteSystemAlerts}
          onChange={(checked) => updatePreferences({ muteSystemAlerts: checked })} />
        <ToggleCard title={t('soundSettings.importantOnly', 'Important events only')} description={t('soundSettings.importantOnlyDesc', 'Play sound only for priority events.')} checked={preferences.importantOnly}
          onChange={(checked) => updatePreferences({ importantOnly: checked })} />
      </div>

      <div className="flex flex-wrap gap-2">
        {[15, 60, 240].map((minutes) => (
          <button key={minutes} type="button" onClick={() => temporarilyMute(minutes)}
            className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-bold text-slate-500 hover:bg-slate-50">
            {t('soundSettings.muteFor', 'Mute {{duration}}', { duration: minutes < 60 ? `${minutes} min` : `${minutes / 60}h` })}
          </button>
        ))}
      </div>

      <div className="space-y-2">
        <label className="text-xs font-bold uppercase tracking-wider text-slate-400">{t('soundSettings.events', 'Events')}</label>
        <div className="grid gap-2 md:grid-cols-2">
          {eventLabels.map((item) => (
            <div key={item.event} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
              <div>
                <p className="text-sm font-bold text-slate-700">{t(item.labelKey, item.label)}</p>
                <p className="text-xs text-slate-400">{t(item.descriptionKey, item.description)}</p>
              </div>
              <div className="flex items-center gap-2">
                <button type="button" onClick={() => testSound(item.event)} className="rounded-lg bg-white p-2 text-slate-500 shadow-sm">
                  <Play size={13} />
                </button>
                <input type="checkbox" checked={preferences.events[item.event]} onChange={(event) => setEventEnabled(item.event, event.target.checked)} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function ToggleCard({ title, description, checked, onChange, icon }: {
  title: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  icon?: React.ReactNode;
}) {
  return (
    <label className={`flex cursor-pointer items-center gap-3 rounded-2xl border p-3 transition-all ${
      checked ? 'border-brand-200 bg-brand-50' : 'border-slate-100 bg-slate-50'
    }`}>
      <span className={checked ? 'text-brand-500' : 'text-slate-400'}>{icon}</span>
      <span className="flex-1">
        <span className="block text-sm font-bold text-slate-700">{title}</span>
        <span className="block text-xs text-slate-400">{description}</span>
      </span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
    </label>
  );
}
