import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import api from '../api/axios';

import { useAuth } from './AuthContext';

export type SoundEvent =
  | 'newReservation'
  | 'contractSigned'
  | 'paymentReceived'
  | 'vehicleReturned'
  | 'gpsAlert'
  | 'subscriptionExpiring'
  | 'error'
  | 'supportMessage';

export type SoundTheme = 'minimal' | 'premium' | 'automotive' | 'silent';

export interface SoundPreferences {
  enabled: boolean;
  volume: number;
  theme: SoundTheme;
  muteNightMode: boolean;
  muteSystemAlerts: boolean;
  importantOnly: boolean;
  doNotDisturb: boolean;
  mutedUntil?: number;
  events: Record<SoundEvent, boolean>;
}

interface NotificationSoundContextValue {
  preferences: SoundPreferences;
  updatePreferences: (patch: Partial<SoundPreferences>) => void;
  setEventEnabled: (event: SoundEvent, enabled: boolean) => void;
  playSound: (event: SoundEvent, force?: boolean) => void;
  testSound: (event?: SoundEvent) => void;
  temporarilyMute: (minutes: number) => void;
  audioReady: boolean;
}

const SoundContext = createContext<NotificationSoundContextValue | undefined>(undefined);

const STORAGE_KEY = 'rentcar_notification_sound_preferences';
const MIN_SOUND_GAP_MS = 3000;
const importantEvents = new Set<SoundEvent>(['gpsAlert', 'subscriptionExpiring', 'error', 'contractSigned', 'paymentReceived']);

const defaultEvents: Record<SoundEvent, boolean> = {
  newReservation: true,
  contractSigned: true,
  paymentReceived: true,
  vehicleReturned: true,
  gpsAlert: true,
  subscriptionExpiring: true,
  error: true,
  supportMessage: true,
};

const defaultPreferences: SoundPreferences = {
  enabled: true,
  volume: 0.38,
  theme: 'premium',
  muteNightMode: false,
  muteSystemAlerts: false,
  importantOnly: false,
  doNotDisturb: false,
  events: defaultEvents,
};

const toneLibrary: Record<SoundTheme, Record<SoundEvent, number[]>> = {
  minimal: {
    newReservation: [660, 880],
    contractSigned: [740, 980],
    paymentReceived: [784, 1046],
    vehicleReturned: [620, 830],
    gpsAlert: [420, 420],
    subscriptionExpiring: [520, 440],
    error: [260, 196],
    supportMessage: [700],
  },
  premium: {
    newReservation: [587, 784, 988],
    contractSigned: [659, 880, 1174],
    paymentReceived: [698, 932, 1244],
    vehicleReturned: [523, 698, 880],
    gpsAlert: [392, 330, 392],
    subscriptionExpiring: [554, 466],
    error: [220, 174],
    supportMessage: [740, 932],
  },
  automotive: {
    newReservation: [440, 660, 880],
    contractSigned: [494, 740, 988],
    paymentReceived: [523, 784, 1046],
    vehicleReturned: [392, 587, 784],
    gpsAlert: [330, 277, 330],
    subscriptionExpiring: [415, 349],
    error: [196, 147],
    supportMessage: [587, 784],
  },
  silent: {
    newReservation: [],
    contractSigned: [],
    paymentReceived: [],
    vehicleReturned: [],
    gpsAlert: [],
    subscriptionExpiring: [],
    error: [],
    supportMessage: [],
  },
};

export function NotificationSoundProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [preferences, setPreferences] = useState<SoundPreferences>(() => readPreferences());
  const [loadedRemotePreferences, setLoadedRemotePreferences] = useState(false);
  const [audioReady, setAudioReady] = useState(false);
  const audioRef = useRef<AudioContext | null>(null);
  const lastPlayedAt = useRef(0);
  const userInteractedRef = useRef(false);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  }, [preferences]);

  useEffect(() => {
    if (!isAuthenticated) {
      setLoadedRemotePreferences(false);
      return;
    }
    setLoadedRemotePreferences(false);
    let cancelled = false;
    api.get('/sound-settings')
      .then(({ data }) => {
        if (cancelled) return;
        const settings = data?.data && typeof data.data === 'object' ? data.data : data;
        if (settings && Object.keys(settings).length > 0) {
          setPreferences((current) => normalizePreferences({ ...current, ...settings }));
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoadedRemotePreferences(true);
      });
    return () => { cancelled = true; };
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated || !loadedRemotePreferences) return;
    const timer = window.setTimeout(() => {
      api.put('/sound-settings', preferences).catch(() => undefined);
    }, 700);
    return () => window.clearTimeout(timer);
  }, [preferences, loadedRemotePreferences, isAuthenticated]);

  // Browsers warn (and refuse to start) an AudioContext created/resumed
  // outside a real user gesture. Sound events can fire at any time (e.g. an
  // SSE notification arriving on page load), so only actually construct/
  // resume the context once a genuine user gesture has unlocked it.
  const ensureAudio = useCallback(async () => {
    if (!userInteractedRef.current) return null;
    if (!audioRef.current) {
      const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioContextCtor) return null;
      audioRef.current = new AudioContextCtor();
    }
    if (audioRef.current.state === 'suspended') {
      await audioRef.current.resume().catch(() => undefined);
    }
    setAudioReady(audioRef.current.state === 'running');
    return audioRef.current;
  }, []);

  useEffect(() => {
    const unlock = () => {
      userInteractedRef.current = true;
      ensureAudio();
    };
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [ensureAudio]);

  const playSound = useCallback((event: SoundEvent, force = false) => {
    if (!force && !canPlay(preferences, event, lastPlayedAt.current)) return;
    const tones = toneLibrary[preferences.theme][event];
    if (!tones.length) return;
    const now = Date.now();
    lastPlayedAt.current = now;
    ensureAudio().then((ctx) => {
      if (!ctx || ctx.state !== 'running') return;
      playToneSequence(ctx, tones, preferences.volume, event);
    });
  }, [ensureAudio, preferences]);

  const updatePreferences = useCallback((patch: Partial<SoundPreferences>) => {
    setPreferences((current) => ({ ...current, ...patch }));
  }, []);

  const setEventEnabled = useCallback((event: SoundEvent, enabled: boolean) => {
    setPreferences((current) => ({ ...current, events: { ...current.events, [event]: enabled } }));
  }, []);

  const temporarilyMute = useCallback((minutes: number) => {
    setPreferences((current) => ({ ...current, mutedUntil: Date.now() + minutes * 60_000 }));
  }, []);

  const value = useMemo<NotificationSoundContextValue>(() => ({
    preferences,
    updatePreferences,
    setEventEnabled,
    playSound,
    testSound: (event = 'paymentReceived') => playSound(event, true),
    temporarilyMute,
    audioReady,
  }), [audioReady, playSound, preferences, setEventEnabled, temporarilyMute, updatePreferences]);

  return <SoundContext.Provider value={value}>{children}</SoundContext.Provider>;
}

export function useNotificationSound() {
  const context = useContext(SoundContext);
  if (!context) throw new Error('useNotificationSound must be used within NotificationSoundProvider');
  return context;
}

export function inferSoundEvent(notification: { title?: string; message?: string; type?: string }): SoundEvent {
  const type = String(notification.type || '').toUpperCase();
  const text = `${notification.title || ''} ${notification.message || ''}`.toLowerCase();
  if (type.includes('CONTRACT') && (type.includes('SIGNED') || text.includes('signed'))) return 'contractSigned';
  if (type.includes('SUBSCRIPTION') || text.includes('subscription')) return 'subscriptionExpiring';
  if (type === 'ERROR' || text.includes('failed') || text.includes('unable')) return 'error';
  if (text.includes('reservation') || text.includes('booking')) return 'newReservation';
  if (text.includes('payment') || text.includes('paid')) return 'paymentReceived';
  if (text.includes('vehicle returned') || text.includes('return processed')) return 'vehicleReturned';
  if (text.includes('gps') || text.includes('geofence') || text.includes('device disconnect')) return 'gpsAlert';
  if (text.includes('support') || text.includes('ticket') || text.includes('message')) return 'supportMessage';
  if (type === 'WARNING') return 'subscriptionExpiring';
  if (type === 'SUCCESS') return 'paymentReceived';
  return 'supportMessage';
}

function readPreferences(): SoundPreferences {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return defaultPreferences;
    return normalizePreferences(JSON.parse(stored));
  } catch {
    return defaultPreferences;
  }
}

function normalizePreferences(value: Partial<SoundPreferences>): SoundPreferences {
  const rawVolume = typeof value.volume === 'number' ? value.volume : defaultPreferences.volume;
  const normalizedVolume = rawVolume > 1 ? rawVolume / 100 : rawVolume;
  return {
    ...defaultPreferences,
    ...value,
    theme: value.theme || defaultPreferences.theme,
    volume: Math.min(1, Math.max(0, normalizedVolume)),
    events: { ...defaultEvents, ...(value.events || {}) },
  };
}

function canPlay(preferences: SoundPreferences, event: SoundEvent, lastPlayedAt: number): boolean {
  if (!preferences.enabled || preferences.theme === 'silent') return false;
  if (preferences.doNotDisturb) return false;
  if (preferences.mutedUntil && Date.now() < preferences.mutedUntil) return false;
  if (preferences.importantOnly && !importantEvents.has(event)) return false;
  if (!preferences.events[event]) return false;
  if (preferences.muteSystemAlerts && (event === 'gpsAlert' || event === 'subscriptionExpiring' || event === 'error')) return false;
  if (preferences.muteNightMode && isNightTime()) return false;
  return Date.now() - lastPlayedAt >= MIN_SOUND_GAP_MS;
}

function isNightTime(): boolean {
  const hour = new Date().getHours();
  return hour >= 22 || hour < 7;
}

function playToneSequence(ctx: AudioContext, tones: number[], volume: number, event: SoundEvent) {
  const baseTime = ctx.currentTime + 0.01;
  const duration = event === 'gpsAlert' ? 0.095 : 0.11;
  tones.forEach((freq, index) => {
    const start = baseTime + index * (duration + 0.035);
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = event === 'error' || event === 'gpsAlert' ? 'triangle' : 'sine';
    oscillator.frequency.setValueAtTime(freq, start);
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume * 0.18), start + 0.018);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(gain);
    gain.connect(ctx.destination);
    oscillator.start(start);
    oscillator.stop(start + duration + 0.03);
  });
}
