import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const API_ORIGIN =
  (import.meta as any).env?.VITE_API_ORIGIN ||
  (import.meta as any).env?.VITE_API_URL?.replace(/\/api\/?$/, '') ||
  `http://${window.location.hostname}:8082`;

/** Resolves a backend-relative media path (e.g. "/uploads/...") into an absolute URL. */
export function resolveMediaUrl(url?: string | null) {
  if (!url) return url ?? null;
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url;
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`;
}
