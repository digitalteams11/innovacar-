import { useEffect, useState } from 'react';
import { API_BASE_URL } from '../lib/api';

export interface DesktopReleaseNotes {
  en: string[];
  fr: string[];
  ar: string[];
}

export interface DesktopRelease {
  available: boolean;
  releaseId?: number;
  platform?: string;
  architecture?: string;
  version?: string;
  channel?: string;
  releaseDate?: string;
  fileName?: string;
  downloadUrl?: string;
  fileSizeBytes?: number;
  sha256?: string;
  minimumOs?: string;
  mandatoryUpdate?: boolean;
  releaseNotes?: DesktopReleaseNotes;
}

interface UseDesktopReleaseResult {
  release: DesktopRelease | null;
  loading: boolean;
  error: boolean;
}

/**
 * Single source of truth for "what's the latest published Windows release,
 * if any" — every surface that promotes the desktop app (landing page,
 * /desktop, the authenticated Desktop App page, the dashboard announcement)
 * reads from this same hook/endpoint so metadata is never duplicated or
 * allowed to drift between surfaces. Public, unauthenticated endpoint — safe
 * to call from the marketing site too.
 */
export function useDesktopRelease(platform = 'WINDOWS', arch = 'X64'): UseDesktopReleaseResult {
  const [release, setRelease] = useState<DesktopRelease | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    fetch(`${API_BASE_URL}/public/desktop/releases/latest?platform=${platform}&arch=${arch}`)
      .then((res) => {
        if (!res.ok) throw new Error(`status ${res.status}`);
        return res.json();
      })
      .then((data) => {
        if (!cancelled) setRelease(data);
      })
      .catch(() => {
        if (!cancelled) {
          setError(true);
          setRelease(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [platform, arch]);

  return { release, loading, error };
}
