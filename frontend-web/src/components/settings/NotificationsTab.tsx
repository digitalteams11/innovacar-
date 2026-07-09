import { Bell, Save } from 'lucide-react';
import SoundSettings from '../SoundSettings';

interface NotificationsTabProps {
  notificationInApp: boolean;
  notificationEmail: boolean;
  notificationPush: boolean;
  onChange: (field: 'notificationInApp' | 'notificationEmail' | 'notificationPush', value: boolean) => void;
  onSave: () => void;
  saving: boolean;
}

export default function NotificationsTab({
  notificationInApp, notificationEmail, notificationPush, onChange, onSave, saving,
}: NotificationsTabProps) {
  return (
    <div className="space-y-6">
      <div className="card-premium space-y-5 p-4 sm:p-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Bell size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Notification Channels</h3>
            <p className="text-sm text-slate-400 font-normal">Choose how you want to be notified about agency activity</p>
          </div>
        </div>

        <div className="space-y-3">
          <NotificationToggle
            label="In-app notifications"
            description="Contract signed, reservation reminders, payment activity, and more"
            checked={notificationInApp}
            onChange={(checked) => onChange('notificationInApp', checked)}
          />
          <NotificationToggle
            label="Email notifications"
            description="Receive a copy of important alerts by email"
            checked={notificationEmail}
            onChange={(checked) => onChange('notificationEmail', checked)}
          />
          <NotificationToggle
            label="Browser push notifications"
            description="Get notified even when RentCar is in the background"
            checked={notificationPush}
            onChange={(checked) => onChange('notificationPush', checked)}
          />
        </div>

        <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
          <button
            onClick={onSave}
            disabled={saving}
            className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all disabled:opacity-70 w-full sm:w-auto"
          >
            {saving ? <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Save size={16} />}
            Save Settings
          </button>
        </div>
      </div>

      <SoundSettings />
    </div>
  );
}

function NotificationToggle({ label, description, checked, onChange }: {
  label: string; description: string; checked: boolean; onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl cursor-pointer">
      <span>
        <span className="block text-sm font-medium text-[#1e293b]">{label}</span>
        <span className="block text-xs text-slate-400 mt-0.5">{description}</span>
      </span>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="w-4 h-4 accent-brand-500 shrink-0" />
    </label>
  );
}
