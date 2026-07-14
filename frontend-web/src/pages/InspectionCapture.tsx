import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Camera, CheckCircle2, Loader2, RotateCcw, ShieldCheck, Upload } from 'lucide-react';
import { API_ORIGIN } from '../lib/api';
import api from '../api/axios';

const PHOTO_STEPS = [
  { label: 'FRONT_SIDE', title: 'Front Side' },
  { label: 'REAR_SIDE', title: 'Rear Side' },
  { label: 'LEFT_SIDE', title: 'Left Side' },
  { label: 'RIGHT_SIDE', title: 'Right Side' },
  { label: 'INTERIOR', title: 'Interior' },
  { label: 'DASHBOARD', title: 'Dashboard' },
  { label: 'MILEAGE', title: 'Mileage' },
  { label: 'FUEL_LEVEL', title: 'Fuel Level' },
  { label: 'WHEELS', title: 'Wheels' },
  { label: 'TRUNK', title: 'Trunk' },
  { label: 'DOCUMENTS', title: 'Documents' },
  { label: 'ACCESSORIES', title: 'Accessories' },
  { label: 'VIDEO_WALKAROUND', title: 'Video Walkaround Photo' },
];

export default function InspectionCapture() {
  const { token } = useParams<{ token: string }>();
  const [inspection, setInspection] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [uploading, setUploading] = useState('');
  const [notes, setNotes] = useState<Record<string, string>>({});

  const loadInspection = async () => {
    if (!token) return;
    const { data } = await api.get(`/public/inspections/${token}`);
    setInspection(data);
  };

  useEffect(() => {
    setLoading(true);
    loadInspection()
      .catch((err) => setError(normalizeUploadError(err) || 'Inspection link is invalid or expired'))
      .finally(() => setLoading(false));
  }, [token]);

  const uploadedLabels = useMemo(() => new Set((inspection?.media || []).map((m: any) => m.label)), [inspection]);
  const doneCount = PHOTO_STEPS.filter((step) => uploadedLabels.has(step.label)).length;

  const uploadFile = async (label: string, file?: File | null) => {
    if (!file || !inspection || !token) return;
    setError('');
    setUploading(label);
    const form = new FormData();
    form.append('file', file);
    form.append('label', label);
    form.append('notes', notes[label] || '');
    form.append('inspectionType', inspection.type || 'BEFORE_DELIVERY');
    const endpoint = `/public/inspections/${token}/media`;
    console.info('[inspection-upload]', {
      tokenPresent: Boolean(token),
      fileName: file.name,
      fileSize: file.size,
      fileType: file.type,
      endpoint,
    });
    try {
      const res = await api.post(endpoint, form);
      console.info('[inspection-upload-response]', { status: res.status });
      const body = res.data;
      if (body?.success === false) throw new Error(messageForStatus(res.status, body));
      await loadInspection();
    } catch (err: any) {
      setError(normalizeUploadError(err));
    } finally {
      setUploading('');
    }
  };

  if (loading) {
    return <Centered><Loader2 size={30} className="animate-spin text-brand-500" /><p>Opening secure inspection...</p></Centered>;
  }

  if (error && !inspection) {
    return <Centered><ShieldCheck size={34} className="text-danger-500" /><p className="max-w-xs text-center text-danger-600">{error}</p></Centered>;
  }

  return (
    <main className="min-h-screen bg-[#f6f6f1] px-4 py-5 text-slate-900">
      <div className="mx-auto max-w-md space-y-4">
        <header className="rounded-3xl bg-slate-950 p-5 text-white shadow-xl">
          <div className="mb-4 flex items-center justify-between">
            <div className="rounded-2xl bg-white/10 p-3"><Camera size={24} /></div>
            <span className="rounded-full bg-emerald-500/20 px-3 py-1 text-[11px] font-bold uppercase text-emerald-200">
              {inspection.status}
            </span>
          </div>
          <p className="text-xs uppercase tracking-[0.25em] text-white/50">
            {inspection.type === 'BEFORE_DELIVERY' ? 'Before Delivery' : 'After Return'}
          </p>
          <h1 className="mt-1 text-2xl font-black">{inspection.vehicleName || 'Vehicle Inspection'}</h1>
          <p className="mt-1 text-sm text-white/70">{inspection.plateNumber} • {inspection.clientName}</p>
          <p className="mt-3 rounded-xl bg-white/10 px-3 py-2 text-xs text-white/70">
            Reservation {inspection.reservationNumber || '-'} • Media expires {new Date(inspection.mediaExpiresAt).toLocaleDateString()}
          </p>
        </header>

        {error && <div className="rounded-2xl bg-danger-50 p-3 text-sm text-danger-600">{error}</div>}

        <section className="rounded-3xl bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-bold">Inspection checklist</h2>
            <span className="text-xs font-bold text-brand-600">{doneCount}/{PHOTO_STEPS.length} photos</span>
          </div>
          <div className="space-y-3">
            {PHOTO_STEPS.map((step) => (
              <CaptureRow
                key={step.label}
                title={step.title}
                done={uploadedLabels.has(step.label)}
                uploading={uploading === step.label}
                note={notes[step.label] || ''}
                onNote={(value) => setNotes((current) => ({ ...current, [step.label]: value }))}
                onFile={(file) => uploadFile(step.label, file)}
              />
            ))}
          </div>
        </section>

        <section className="rounded-3xl bg-white p-4 shadow-sm">
          <h2 className="mb-3 font-bold">Uploaded media</h2>
          <div className="grid grid-cols-2 gap-2">
            {(inspection.media || []).map((item: any) => (
              <div key={item.id} className="overflow-hidden rounded-2xl border border-slate-100 bg-slate-50">
                {item.type === 'VIDEO'
                  ? <video src={mediaUrl(item.fileUrl)} controls className="h-28 w-full bg-black object-cover" />
                  : <img src={mediaUrl(item.fileUrl)} alt={item.label} className="h-28 w-full object-cover" />}
                <p className="truncate px-2 py-1 text-[11px] font-semibold text-slate-600">{item.label}</p>
              </div>
            ))}
          </div>
        </section>

        <div className="rounded-2xl bg-emerald-50 p-3 text-sm text-emerald-700">
          <CheckCircle2 size={16} className="mr-1 inline" />
          Inspection media is linked to the reservation, contract, client, vehicle, and employee.
        </div>
      </div>
    </main>
  );
}

function CaptureRow({ title, done, uploading, note, onNote, onFile }: {
  title: string;
  done: boolean;
  uploading: boolean;
  note: string;
  onNote: (value: string) => void;
  onFile: (file?: File | null) => void;
}) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-3">
      <div className="flex items-center gap-3">
        <div className={`flex h-9 w-9 items-center justify-center rounded-xl ${done ? 'bg-emerald-500 text-white' : 'bg-white text-slate-400'}`}>
          {done ? <CheckCircle2 size={18} /> : <Camera size={18} />}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-bold text-slate-800">{title}</p>
          <p className="text-[11px] text-slate-400">JPEG, PNG, or WebP</p>
        </div>
        <label className="relative flex cursor-pointer items-center gap-1 rounded-xl bg-brand-500 px-3 py-2 text-xs font-bold text-white">
          {uploading ? <Loader2 size={14} className="animate-spin" /> : done ? <RotateCcw size={14} /> : <Upload size={14} />}
          {done ? 'Retake' : 'Capture'}
          <input
            type="file"
            accept="image/*"
            capture="environment"
            className="absolute inset-0 opacity-0"
            disabled={uploading}
            onChange={(e) => onFile(e.target.files?.[0])}
          />
        </label>
      </div>
      <input
        value={note}
        onChange={(e) => onNote(e.target.value)}
        placeholder="Notes or damage marker..."
        className="mt-3 w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-brand-400"
      />
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <main className="flex min-h-screen flex-col items-center justify-center gap-3 bg-[#f6f6f1] text-slate-600">{children}</main>;
}

function mediaUrl(url?: string) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`;
}

function messageForStatus(status: number, body: any) {
  const code = body?.errorCode || body?.error;
  const message = body?.message;
  if (status === 404) return 'Invalid inspection token.';
  if (status === 410 || code === 'INSPECTION_TOKEN_EXPIRED') return 'Inspection link expired.';
  if (status === 413) return 'Upload failed. File is too large.';
  if (status === 0) return 'Unable to reach backend from phone. Check Wi-Fi IP.';
  if (message && typeof message === 'string') return message;
  return 'Upload failed. Please try again.';
}

function normalizeUploadError(error: any) {
  const status = error?.response?.status;
  const body = error?.response?.data;
  if (status) return messageForStatus(status, body);
  const message = error?.message || '';
  if (/Failed to fetch|NetworkError|Load failed|Network Error/i.test(message)) {
    return 'Unable to reach backend from phone. Check Wi-Fi IP.';
  }
  if (/permission/i.test(message)) return 'Camera permission denied.';
  return message || 'Upload failed. Please try again.';
}
