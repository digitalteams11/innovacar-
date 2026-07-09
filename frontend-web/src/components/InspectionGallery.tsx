import { useState } from 'react';
import { Camera, Clock, FileVideo, Image as ImageIcon, RefreshCw, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { resolveMediaUrl, BACKEND_ORIGIN, API_BASE_URL } from '../utils/mediaUrl';

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

export default function InspectionGallery({
  inspections,
  onRefresh,
}: {
  inspections: Inspection[];
  onRefresh?: () => void;
}) {
  const { t } = useTranslation();
  if (!inspections.length) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/60 p-8 text-center">
        <Camera size={28} className="mx-auto mb-3 text-slate-300" />
        <p className="text-sm font-semibold text-slate-500">{t('inspectionGallery.noMediaYet', 'No vehicle inspection media yet')}</p>
        <p className="mt-1 text-xs text-slate-400">{t('inspectionGallery.startInspectionHint', 'Start a before-delivery or after-return inspection to create proof.')}</p>
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
                    {inspection.type === 'BEFORE_DELIVERY' ? t('inspectionGallery.beforeDeliveryInspection', 'Before Delivery Inspection') : t('inspectionGallery.afterReturnInspection', 'After Return Inspection')}
                  </h4>
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {inspection.vehicleName} {inspection.plateNumber ? `- ${inspection.plateNumber}` : ''}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2 text-[11px] font-bold uppercase">
                {onRefresh && (
                  <button
                    type="button"
                    onClick={onRefresh}
                    className="flex items-center gap-1 rounded-lg bg-slate-50 px-2.5 py-1 text-slate-500 hover:bg-slate-100 transition-colors normal-case font-semibold"
                  >
                    <RefreshCw size={11} />
                    {t('inspectionGallery.refresh', 'Refresh')}
                  </button>
                )}
                <span className="rounded-lg bg-emerald-50 px-2.5 py-1 text-emerald-600">{displayStatus}</span>
                <span className="rounded-lg bg-brand-50 px-2.5 py-1 text-brand-600">
                  {t('inspectionGallery.photosUploaded', '{{count}}/{{total}} photos uploaded', { count: photoCount, total: requiredPhotoCount })}
                </span>
                {inspection.mediaExpiresAt && (
                  <span className="rounded-lg bg-amber-50 px-2.5 py-1 text-amber-600">
                    {t('inspectionGallery.expiresOn', 'Expires {{date}}', { date: new Date(inspection.mediaExpiresAt).toLocaleDateString() })}
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
                  {label.replaceAll('_', ' ')}: {uploadedLabels.has(label) ? t('inspectionGallery.uploaded', 'uploaded') : t('inspectionGallery.waitingForPhoto', 'waiting for photo')}
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {media.map((item) => {
                const originalPath = item.fullUrl || item.url || item.fileUrl;
                const resolvedUrl = resolveMediaUrl(originalPath);

                if (import.meta.env.DEV) {
                  console.info('[INSPECTION_MEDIA_RENDER_DEBUG]', {
                    mediaId: item.id,
                    category: item.label,
                    rawUrl: originalPath,
                    finalImageUrl: resolvedUrl,
                    apiBaseUrl: API_BASE_URL,
                    backendOrigin: BACKEND_ORIGIN,
                    type: item.type,
                  });
                }

                return (
                  <article key={item.id} className="overflow-hidden rounded-xl border border-slate-100 bg-slate-50">
                    {item.type === 'VIDEO' ? (
                      <video src={resolvedUrl || undefined} controls className="h-40 w-full bg-black object-cover" />
                    ) : item.type === 'PHOTO' ? (
                      <MediaImage
                        src={resolvedUrl}
                        label={item.label || t('inspectionGallery.inspectionMediaFallback', 'Inspection media')}
                        mediaId={item.id}
                        originalPath={originalPath}
                      />
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
                  {t('inspectionGallery.waitingForInspectionPhotos', 'Waiting for inspection photos')}
                </div>
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function MediaImage({
  src,
  label,
  mediaId,
  originalPath,
}: {
  src: string | null;
  label: string;
  mediaId?: number;
  originalPath?: string;
}) {
  const { t } = useTranslation();
  const [failed, setFailed] = useState(false);

  if (!src || failed) {
    return (
      <div className="flex h-40 flex-col items-center justify-center gap-1 bg-slate-100 text-slate-400 px-2">
        <ImageIcon size={24} />
        <span className="text-xs font-semibold">{label}</span>
        <span className="text-[10px]">{t('inspectionGallery.imageUnavailable', 'Image unavailable')}</span>
        {src && (
          <a
            href={src}
            target="_blank"
            rel="noreferrer"
            className="text-[10px] text-brand-500 underline truncate max-w-full"
            title={src}
          >
            {t('inspectionGallery.openFile', 'Open file')}
          </a>
        )}
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={label}
      className="h-40 w-full object-cover"
      onError={() => {
        console.warn('[inspection-media] image failed to load', {
          mediaId,
          category: label,
          rawUrl: originalPath,
          finalImageUrl: src,
          backendOrigin: BACKEND_ORIGIN,
          apiBaseUrl: API_BASE_URL,
        });
        setFailed(true);
      }}
    />
  );
}
