import { useState } from 'react';
import { Camera, Clock, FileVideo, Image as ImageIcon, ShieldCheck } from 'lucide-react';

interface InspectionMedia {
  id: number;
  type: 'PHOTO' | 'VIDEO' | 'NOTE';
  fileUrl?: string;
  url?: string;
  fullUrl?: string;
  thumbnailUrl?: string;
  label?: string;
  notes?: string;
  uploadedAt?: string;
  size?: number;
}

interface Inspection {
  id: number;
  type: 'BEFORE_DELIVERY' | 'AFTER_RETURN';
  status: string;
  vehicleName?: string;
  plateNumber?: string;
  clientName?: string;
  mediaExpiresAt?: string;
  completedAt?: string;
  media?: InspectionMedia[];
}

const REQUIRED_LABELS = [
  'FRONT_SIDE',
  'REAR_SIDE',
  'LEFT_SIDE',
  'RIGHT_SIDE',
  'INTERIOR',
  'DASHBOARD',
  'MILEAGE',
  'FUEL_LEVEL',
  'WHEELS',
  'TRUNK',
  'DOCUMENTS',
  'ACCESSORIES',
  'VIDEO_WALKAROUND',
];

const API_ORIGIN =
  import.meta.env.VITE_API_ORIGIN ||
  import.meta.env.VITE_API_URL?.replace(/\/api\/?$/, '') ||
  `http://${window.location.hostname}:8082`;

function resolveMediaUrl(url?: string | null) {
  if (!url) return null;
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`;
}

export default function InspectionGallery({ inspections }: { inspections: Inspection[] }) {
  if (!inspections.length) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/60 p-8 text-center">
        <Camera size={28} className="mx-auto mb-3 text-slate-300" />
        <p className="text-sm font-semibold text-slate-500">No vehicle inspection media yet</p>
        <p className="mt-1 text-xs text-slate-400">Start a before-delivery or after-return inspection to create proof.</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {inspections.map((inspection) => {
        const media = inspection.media || [];
        const uploadedLabels = new Set(
          media
            .filter((item) => item.type === 'PHOTO')
            .map((item) => item.label)
            .filter(Boolean)
        );
        const photoCount = uploadedLabels.size;
        const requiredPhotoCount = REQUIRED_LABELS.length;
        const displayStatus = photoCount === 0
          ? 'NOT_STARTED'
          : photoCount >= requiredPhotoCount
            ? 'COMPLETED'
            : 'IN_PROGRESS';

        return (
          <section key={inspection.id} className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
            <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <ShieldCheck size={16} className="text-brand-500" />
                  <h4 className="text-sm font-bold text-slate-800">
                    {inspection.type === 'BEFORE_DELIVERY' ? 'Before Delivery' : 'After Return'} Inspection
                  </h4>
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {inspection.vehicleName} {inspection.plateNumber ? `- ${inspection.plateNumber}` : ''}
                </p>
              </div>
              <div className="flex flex-wrap gap-2 text-[11px] font-bold uppercase">
                <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-emerald-600">{displayStatus}</span>
                <span className="rounded-lg bg-brand-50 px-2.5 py-1 text-brand-600">
                  {photoCount}/{requiredPhotoCount} photos uploaded
                </span>
                {inspection.mediaExpiresAt && (
                  <span className="rounded-lg bg-amber-50 px-2.5 py-1 text-amber-600">
                    Expires {new Date(inspection.mediaExpiresAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            </div>

            <div className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">
              {REQUIRED_LABELS.map((label) => (
                <div
                  key={label}
                  className={`rounded-lg px-2.5 py-2 text-[11px] font-semibold ${
                    uploadedLabels.has(label) ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-50 text-slate-400'
                  }`}
                >
                  {label.replaceAll('_', ' ')}: {uploadedLabels.has(label) ? 'uploaded' : 'waiting for photo'}
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {media.map((item) => {
                const mediaUrl = resolveMediaUrl(item.fullUrl || item.url || item.fileUrl);
                return (
                  <article key={item.id} className="overflow-hidden rounded-xl border border-slate-100 bg-slate-50">
                    {item.type === 'VIDEO' ? (
                      <video src={mediaUrl || undefined} controls className="h-40 w-full bg-black object-cover" />
                    ) : item.type === 'PHOTO' ? (
                      <MediaImage src={mediaUrl} label={item.label || 'Inspection media'} />
                    ) : (
                      <div className="flex h-40 items-center justify-center bg-slate-100 text-slate-400">
                        <ImageIcon size={28} />
                      </div>
                    )}
                    <div className="space-y-1 p-3">
                      <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
                        {item.type === 'VIDEO' ? <FileVideo size={14} /> : <ImageIcon size={14} />}
                        <span>{item.label || item.type}</span>
                      </div>
                      {item.notes && <p className="text-xs text-slate-500">{item.notes}</p>}
                      {item.uploadedAt && (
                        <p className="flex items-center gap-1 text-[10px] text-slate-400">
                          <Clock size={11} /> {new Date(item.uploadedAt).toLocaleString()}
                        </p>
                      )}
                    </div>
                  </article>
                );
              })}
              {!media.length && (
                <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-xs text-slate-400">
                  Waiting for inspection photos
                </div>
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function MediaImage({ src, label }: { src: string | null; label: string }) {
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <div className="flex h-40 flex-col items-center justify-center bg-slate-100 text-slate-400">
        <ImageIcon size={28} />
        <span className="mt-2 text-xs font-semibold">{label}</span>
        <span className="text-[11px]">Image unavailable</span>
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={label}
      className="h-40 w-full object-cover"
      onError={() => {
        console.warn('[inspection-media] image failed to load', { label, url: src });
        setFailed(true);
      }}
    />
  );
}
