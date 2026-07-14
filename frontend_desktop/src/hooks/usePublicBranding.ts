import { useEffect, useState } from 'react';
import api from '../api/axios';

export interface PublicBranding {
  found: boolean;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  tenantName?: string;
}

/**
 * Resolves agency branding for unauthenticated pages (login, public booking, client portal)
 * from the current hostname. Backed by GET /api/public/branding, which matches either a
 * verified custom domain or an Innovacar subdomain — see PublicBrandingController.
 */
export function usePublicBranding(): PublicBranding | null {
  const [branding, setBranding] = useState<PublicBranding | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get('/public/branding', { params: { host: window.location.hostname } })
      .then(({ data }) => { if (!cancelled) setBranding(data); })
      .catch(() => { if (!cancelled) setBranding({ found: false }); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!branding?.found) return;
    const root = document.documentElement;
    if (branding.primaryColor) root.style.setProperty('--brand-primary', branding.primaryColor);
    if (branding.accentColor) root.style.setProperty('--brand-accent', branding.accentColor);
  }, [branding]);

  return branding;
}
