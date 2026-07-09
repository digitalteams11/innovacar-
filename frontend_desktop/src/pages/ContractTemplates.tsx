import { useEffect, useMemo, useState } from 'react';
import { Copy, Eye, FileText, Image, Layers, Plus, Save, Search, Star, Trash2, Upload, X } from 'lucide-react';
import api from '../api/axios';
import { API_ORIGIN } from '../lib/api';
import { useToast } from '../context/ToastContext';

const FIELD_KEYS = [
  'client.fullName', 'client.firstName', 'client.lastName', 'client.address', 'client.birthDate',
  'client.nationality', 'client.cin', 'client.passport', 'client.drivingLicenseNumber',
  'client.drivingLicenseIssuedAt', 'client.phone', 'client.email',
  'additionalDriver.fullName', 'additionalDriver.address', 'additionalDriver.cin', 'additionalDriver.drivingLicenseNumber',
  'vehicle.brand', 'vehicle.model', 'vehicle.type', 'vehicle.registrationNumber', 'vehicle.fuelType',
  'vehicle.startMileage', 'vehicle.endMileage', 'vehicle.fuelLevel',
  'reservation.startDate', 'reservation.startTime', 'reservation.startLocation',
  'reservation.endDate', 'reservation.endTime', 'reservation.endLocation',
  'contract.number', 'contract.createdAt', 'contract.status',
  'payment.dailyPrice', 'payment.days', 'payment.totalAmount', 'payment.advancePaid',
  'payment.remainingAmount', 'payment.deposit', 'payment.extraFees', 'payment.paymentMethod',
  'payment.cash', 'payment.cheque', 'payment.card', 'payment.bankTransfer',
  'documents.registrationCard', 'documents.insurance', 'documents.vignette',
  'documents.technicalInspection', 'documents.authorization',
  'signature.client', 'signature.agency', 'signature.employee', 'contract.verificationQr',
];

const QUICK_FIELDS = [
  { label: 'Client Name', fieldKey: 'client.fullName', xPercent: 12, yPercent: 18, widthPercent: 32 },
  { label: 'CIN', fieldKey: 'client.cin', xPercent: 58, yPercent: 18, widthPercent: 20 },
  { label: 'Vehicle Plate', fieldKey: 'vehicle.registrationNumber', xPercent: 58, yPercent: 34, widthPercent: 24 },
  { label: 'Start Date', fieldKey: 'reservation.startDate', xPercent: 18, yPercent: 43, widthPercent: 20 },
  { label: 'End Date', fieldKey: 'reservation.endDate', xPercent: 58, yPercent: 43, widthPercent: 20 },
  { label: 'Total Amount', fieldKey: 'payment.totalAmount', xPercent: 58, yPercent: 58, widthPercent: 22 },
  { label: 'Client Signature', fieldKey: 'signature.client', xPercent: 12, yPercent: 82, widthPercent: 28 },
  { label: 'Agency Signature', fieldKey: 'signature.agency', xPercent: 58, yPercent: 82, widthPercent: 28 },
];

const SYSTEM_TEMPLATES = [
  {
    key: 'classic-moroccan',
    name: 'Classic Moroccan Rental Contract',
    description: 'A4 rental contract with client info, vehicle info, rental dates, fuel, documents, signatures, and terms.',
    language: 'FR',
    pages: '2 pages',
    hasConditions: true,
    accessPlan: 'STARTER',
    unlocked: true,
    locked: false,
  },
  {
    key: 'modern-a4',
    name: 'Modern A4 Rental Contract',
    description: 'Clean professional contract for daily rentals with payment and signature blocks.',
    language: 'FR',
    pages: '1 page',
    hasConditions: false,
    accessPlan: 'BASIC',
    unlocked: false,
    locked: true,
  },
  {
    key: 'compact-one-page',
    name: 'Compact One Page Contract',
    description: 'Short printable layout for fast counter rentals and small agencies.',
    language: 'FR',
    pages: '1 page',
    hasConditions: false,
    accessPlan: 'STARTER',
    unlocked: true,
    locked: false,
  },
  {
    key: 'detailed-agency',
    name: 'Detailed Agency Contract',
    description: 'Detailed contract with client, vehicle, pricing, deposit, payment, documents, and signatures.',
    language: 'FR',
    pages: '2 pages',
    hasConditions: true,
    accessPlan: 'STANDARD',
    unlocked: false,
    locked: true,
  },
  {
    key: 'vehicle-inspection',
    name: 'Contract with Vehicle Inspection',
    description: 'Contract including vehicle inspection diagram, mileage, fuel level, documents, and condition notes.',
    language: 'FR',
    pages: '2 pages',
    hasConditions: true,
    accessPlan: 'STANDARD',
    unlocked: false,
    locked: true,
  },
  {
    key: 'conditions-page',
    name: 'Contract with Conditions',
    description: 'Professional rental contract with full terms and conditions page.',
    language: 'FR',
    pages: '2 pages',
    hasConditions: true,
    accessPlan: 'BASIC',
    unlocked: false,
    locked: true,
  },
  {
    key: 'premium-luxury',
    name: 'Premium Luxury Contract',
    description: 'Premium layout for luxury vehicle rental agencies with detailed guarantees and inspection.',
    language: 'FR',
    pages: '3 pages',
    hasConditions: true,
    accessPlan: 'PREMIUM',
    unlocked: false,
    locked: true,
  },
  {
    key: 'enterprise-custom',
    name: 'Enterprise Custom Contract',
    description: 'Advanced contract template for agencies with custom clauses, multi-branch support, and audit records.',
    language: 'FR',
    pages: 'Custom',
    hasConditions: true,
    accessPlan: 'ENTERPRISE',
    unlocked: false,
    locked: true,
  },
];

const SAMPLE_VALUES: Record<string, string> = {
  'client.fullName': 'Mohamed Yacoubi',
  'client.cin': 'AB123456',
  'client.phone': '+212 600 000 000',
  'vehicle.brand': 'Hyundai',
  'vehicle.model': 'i20',
  'vehicle.registrationNumber': '12345-A-6',
  'reservation.startDate': '19/06/2026',
  'reservation.endDate': '22/06/2026',
  'contract.number': 'CTR-2026-00004',
  'payment.dailyPrice': '400 MAD',
  'payment.totalAmount': '1200 MAD',
  'signature.client': 'Signature client',
  'signature.agency': 'Signature agence',
  'contract.verificationQr': 'QR',
};

interface TemplateField {
  id?: number;
  fieldKey: string;
  label: string;
  pageNumber: number;
  xPercent: number;
  yPercent: number;
  widthPercent: number;
  heightPercent: number;
  fontSize: number;
  fontFamily: string;
  fontWeight: string;
  textAlign: string;
  color: string;
  multiline: boolean;
  enabled: boolean;
}

interface Template {
  id: number;
  name: string;
  description?: string;
  templateCode?: string;
  templateType: string;
  source?: string;
  language?: string;
  pages?: string;
  pagesCount?: number;
  hasConditions?: boolean;
  accessPlan?: string;
  requiredPlan?: string;
  locked?: boolean;
  unlocked?: boolean;
  featureCode?: string;
  frontFileUrl?: string;
  backFileUrl?: string;
  pageSize?: string;
  default: boolean;
  active: boolean;
  fields?: TemplateField[];
}

type SystemTemplate = (typeof SYSTEM_TEMPLATES)[number] & {
  requiredPlan?: string;
  featureCode?: string;
};

interface TemplateForm {
  name: string;
  templateType: 'SYSTEM_DEFAULT' | 'AGENCY_SCAN_TEMPLATE';
  language: 'FR' | 'AR' | 'EN';
  default: boolean;
  active: boolean;
  frontFile?: File | null;
  backFile?: File | null;
}

const emptyTemplateForm: TemplateForm = {
  name: '',
  templateType: 'AGENCY_SCAN_TEMPLATE',
  language: 'FR',
  default: false,
  active: true,
  frontFile: null,
  backFile: null,
};

function unwrapData<T>(payload: any, fallback: T): T {
  return payload?.data ?? payload ?? fallback;
}

function unwrapList<T>(payload: any): T[] {
  const data = unwrapData<any>(payload, []);
  return Array.isArray(data) ? data : [];
}

const emptyField = (): TemplateField => ({
  fieldKey: 'client.fullName',
  label: 'Client full name',
  pageNumber: 1,
  xPercent: 10,
  yPercent: 10,
  widthPercent: 30,
  heightPercent: 4,
  fontSize: 10,
  fontFamily: 'Helvetica',
  fontWeight: 'normal',
  textAlign: 'left',
  color: '#000000',
  multiline: false,
  enabled: true,
});

export default function ContractTemplates() {
  const { showToast } = useToast();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [fields, setFields] = useState<TemplateField[]>([]);
  const [terms, setTerms] = useState<any[]>([]);
  const [termsText, setTermsText] = useState('');
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [templateForm, setTemplateForm] = useState<TemplateForm>(emptyTemplateForm);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [dragId, setDragId] = useState<number | string | null>(null);
  const [activeTab, setActiveTab] = useState<'gallery' | 'templates' | 'mapping' | 'preview' | 'conditions'>('gallery');
  const [search, setSearch] = useState('');
  const [selectedFieldIndex, setSelectedFieldIndex] = useState<number | null>(null);
  const [systemTemplates, setSystemTemplates] = useState<SystemTemplate[]>(SYSTEM_TEMPLATES);
  const [previewSystemTemplate, setPreviewSystemTemplate] = useState<SystemTemplate | null>(null);

  const selected = templates.find((item) => item.id === selectedId) || null;
  const selectedIsSystem = selected?.templateType === 'SYSTEM_DEFAULT';
  const frontUrl = selected?.frontFileUrl ? `${API_ORIGIN}${selected.frontFileUrl}` : '';
  const backUrl = selected?.backFileUrl ? `${API_ORIGIN}${selected.backFileUrl}` : '';
  const agencyTemplates = templates.filter((template) => template.templateType !== 'SYSTEM_DEFAULT');
  const savedSystemTemplates = templates.filter((template) => template.templateType === 'SYSTEM_DEFAULT');
  const filteredTemplates = templates.filter((template) =>
    template.name.toLowerCase().includes(search.trim().toLowerCase())
  );

  useEffect(() => { load(); }, []);
  useEffect(() => {
    if (!selectedId) return;
    api.get(`/contract-templates/${selectedId}/fields`)
      .then(({ data }) => setFields(unwrapList<TemplateField>(data)))
      .catch(() => setFields([]));
  }, [selectedId]);

  const load = async () => {
    const [templateRes, termsRes] = await Promise.all([
      api.get('/contract-templates').catch(() => ({ data: [] })),
      api.get('/contract-terms').catch(() => ({ data: [] })),
    ]);
    const loadedTemplates = unwrapList<Template>(templateRes.data);
    const loadedTerms = unwrapList<any>(termsRes.data);
    setTemplates(loadedTemplates);
    setTerms(loadedTerms);
    setTermsText(loadedTerms[0]?.content || defaultTerms);
    if (!selectedId && loadedTemplates[0]) setSelectedId(loadedTemplates[0].id);
    try {
      const { data } = await api.get('/contract-templates/system');
      const gallery = unwrapList<SystemTemplate>(data);
      if (gallery.length) setSystemTemplates(gallery);
    } catch {
      setSystemTemplates(SYSTEM_TEMPLATES);
    }
  };

  const createTemplate = async () => {
    if (!templateForm.name.trim()) {
      showToast('Template name is required.', 'warning');
      return;
    }
    setSavingTemplate(true);
    try {
      const { data } = await api.post('/contract-templates', {
        name: templateForm.name.trim(),
        templateType: templateForm.templateType,
        language: templateForm.language,
        pageSize: 'A4',
        default: templateForm.default,
        active: templateForm.active,
      });
      let saved = unwrapData<Template>(data, data);
      if (templateForm.frontFile) {
        const form = new FormData();
        form.append('file', templateForm.frontFile);
        try {
          const uploadResponse = await api.post(`/contract-templates/${saved.id}/upload-front`, form);
          saved = unwrapData<Template>(uploadResponse.data, saved);
        } catch (uploadError: any) {
          await load();
          setSelectedId(saved.id);
          showToast(`Template created, but front page upload failed: ${uploadError?.response?.data?.message || uploadError?.userMessage || 'Unable to upload template file.'}`, 'error');
          return;
        }
      }
      if (templateForm.backFile) {
        const form = new FormData();
        form.append('file', templateForm.backFile);
        try {
          const uploadResponse = await api.post(`/contract-templates/${saved.id}/upload-back`, form);
          saved = unwrapData<Template>(uploadResponse.data, saved);
        } catch (uploadError: any) {
          await load();
          setSelectedId(saved.id);
          showToast(`Template created, but conditions page upload failed: ${uploadError?.response?.data?.message || uploadError?.userMessage || 'Unable to upload template file.'}`, 'error');
          return;
        }
      }
      await load();
      setSelectedId(saved.id);
      setCreateOpen(false);
      setTemplateForm(emptyTemplateForm);
      setActiveTab('mapping');
      showToast(data?.message || 'Template created successfully', 'success');
    } catch (error: any) {
      showToast(error?.response?.data?.message || error?.userMessage || 'Unable to save template.', 'error');
    } finally {
      setSavingTemplate(false);
    }
  };

  const upload = async (kind: 'front' | 'back', file?: File | null) => {
    if (!selected || !file) return;
    const form = new FormData();
    form.append('file', file);
    try {
      const { data } = await api.post(`/contract-templates/${selected.id}/upload-${kind}`, form);
      const updated = unwrapData<Template>(data, data);
      setTemplates((current) => current.map((item) => item.id === updated.id ? updated : item));
      showToast(data?.message || `${kind === 'front' ? 'Front template' : 'Conditions page'} uploaded`, 'success');
    } catch (error: any) {
      showToast(error?.response?.data?.message || error?.userMessage || 'Unable to upload template file.', 'error');
    }
  };

  const useSystemTemplate = async (template: SystemTemplate) => {
    if (template.locked || template.unlocked === false) {
      showToast(`This template requires ${template.requiredPlan || template.accessPlan || 'a higher'} plan.`, 'warning');
      return;
    }
    const existing = templates.find((item) => item.name === template.name && item.templateType === 'SYSTEM_DEFAULT');
    if (existing) {
      setSelectedId(existing.id);
      setActiveTab('preview');
      showToast('System template already saved for this agency.', 'info');
      return;
    }
    setSavingTemplate(true);
    try {
      const { data } = await api.post(`/contract-templates/system/${template.key}/use`);
      const saved = unwrapData<Template>(data, data);
      await load();
      setSelectedId(saved.id);
      setActiveTab('preview');
      showToast(data?.message || 'Template selected successfully.', 'success');
    } catch (error: any) {
      const status = error?.response?.status;
      const data = error?.response?.data;
      if (status === 403 && data?.errorCode === 'TEMPLATE_PLAN_REQUIRED') {
        showToast(data.message || `This template requires ${data?.data?.requiredPlan || template.accessPlan} plan.`, 'warning');
      } else {
        showToast(data?.message || error?.userMessage || 'Unable to use system template.', 'error');
      }
    } finally {
      setSavingTemplate(false);
    }
  };

  const saveField = async (field: TemplateField) => {
    if (!selected) return;
    const request = field.id
      ? api.put(`/contract-templates/${selected.id}/fields/${field.id}`, field)
      : api.post(`/contract-templates/${selected.id}/fields`, field);
    const { data } = await request;
    const saved = unwrapData<TemplateField>(data, data);
    setFields((current) => field.id
      ? current.map((item) => item.id === field.id ? saved : item)
      : current.map((item) => item === field ? saved : item));
    showToast(data?.message || 'Field mapping saved', 'success');
  };

  const saveMapping = async () => {
    if (!selected) return;
    try {
      const { data } = await api.put(`/contract-templates/${selected.id}/fields`, { fields });
      setFields(unwrapList<TemplateField>(data));
      showToast(data?.message || 'Field mapping saved', 'success');
    } catch (error: any) {
      showToast(error?.response?.data?.message || error?.userMessage || 'Unable to save field mapping.', 'error');
    }
  };

  const previewPdf = async () => {
    if (!selected) return;
    if (selected.templateType === 'SYSTEM_DEFAULT') {
      const system = systemTemplates.find((item) => item.key === selected.templateCode || item.name === selected.name);
      if (system) setPreviewSystemTemplate(system);
      showToast('PDF preview generated.', 'success');
      return;
    }
    if (!selected.frontFileUrl) {
      showToast('Upload a front template page before previewing.', 'warning');
      return;
    }
    if (!fields.length) {
      showToast('Template has no fields mapped yet. Previewing background only.', 'info');
    }
    try {
      const response = await api.get(`/contract-templates/${selected.id}/preview-pdf`, { responseType: 'blob' });
      if (!response.data || response.data.size === 0) {
        showToast('Preview PDF is empty. Please try again.', 'error');
        return;
      }
      const blob = new Blob([response.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (error: any) {
      let message = error?.userMessage || 'Unable to generate preview PDF.';
      const blobData = error?.response?.data;
      if (blobData instanceof Blob) {
        try { message = JSON.parse(await blobData.text())?.message || message; } catch { /* ignore */ }
      }
      showToast(message, 'error');
    }
  };

  const setDefault = async () => {
    if (!selected) return;
    const { data } = await api.post(`/contract-templates/${selected.id}/set-default`);
    const updated = unwrapData<Template>(data, data);
    setTemplates((current) => current.map((item) => ({ ...item, default: item.id === updated.id })));
    showToast(data?.message || 'Default agency contract template saved', 'success');
  };

  const deleteTemplate = async () => {
    if (!selected || !window.confirm(`Delete ${selected.name}?`)) return;
    await api.delete(`/contract-templates/${selected.id}`);
    setTemplates((current) => current.filter((item) => item.id !== selected.id));
    setSelectedId(null);
    setFields([]);
    showToast('Template deleted successfully', 'success');
  };

  const saveTerms = async () => {
    const existing = terms[0];
    const payload = { title: 'Conditions generales', content: termsText, language: 'fr', default: true };
    const { data } = existing?.id
      ? await api.put(`/contract-terms/${existing.id}`, payload)
      : await api.post('/contract-terms', payload);
    setTerms([unwrapData<any>(data, data)]);
    showToast(data?.message || 'Contract terms saved', 'success');
  };

  const updateField = (index: number, patch: Partial<TemplateField>) => {
    setFields((current) => current.map((item, i) => i === index ? { ...item, ...patch } : item));
  };

  const addField = (patch?: Partial<TemplateField>) => {
    const next = { ...emptyField(), ...patch };
    setFields((current) => [...current, next]);
    setSelectedFieldIndex(fields.length);
    setActiveTab('mapping');
  };

  const duplicateField = (index: number) => {
    const current = fields[index];
    if (!current) return;
    const copy = {
      ...current,
      id: undefined,
      xPercent: Math.min(98, Number(current.xPercent) + 2),
      yPercent: Math.min(98, Number(current.yPercent) + 2),
    };
    setFields((items) => [...items, copy]);
    setSelectedFieldIndex(fields.length);
  };

  const deleteFieldAt = (index: number) => {
    setFields((items) => items.filter((_, i) => i !== index));
    setSelectedFieldIndex(null);
  };

  const onCanvasMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (dragId === null) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const xPercent = Math.max(0, Math.min(98, ((event.clientX - rect.left) / rect.width) * 100));
    const yPercent = Math.max(0, Math.min(98, ((event.clientY - rect.top) / rect.height) * 100));
    setFields((current) => current.map((field, index) =>
      (field.id || `new-${index}`) === dragId ? { ...field, xPercent, yPercent } : field
    ));
  };

  const previewFields = useMemo(() => fields.filter((field) => field.enabled && field.pageNumber === 1), [fields]);
  const selectedField = selectedFieldIndex !== null ? fields[selectedFieldIndex] : null;

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-black text-[#1e293b]">Agency Contract Templates</h1>
          <p className="text-sm text-slate-500">Choose a ready contract, upload your scanned paper, map fields, and set the PDF default.</p>
        </div>
        <button onClick={() => setCreateOpen(true)} className="flex items-center gap-2 rounded-xl bg-brand-500 px-4 py-2 text-sm font-bold text-white">
          <Upload size={16} /> Upload My Contract Paper
        </button>
      </div>

      <div className="flex gap-2 overflow-x-auto rounded-2xl bg-slate-100 p-1">
        {[
          ['gallery', 'Gallery', Layers],
          ['templates', 'My Templates', FileText],
          ['mapping', 'Mapping', Plus],
          ['preview', 'Preview', Eye],
          ['conditions', 'Conditions Page', Image],
        ].map(([key, label, Icon]: any) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex shrink-0 items-center gap-2 rounded-xl px-4 py-2 text-xs font-bold ${activeTab === key ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
          >
            <Icon size={15} /> {label}
          </button>
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-[300px_1fr]">
        <aside className="card-premium p-4 space-y-4">
          <div>
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400">My Templates</h2>
            <div className="mt-3 flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2">
              <Search size={14} className="text-slate-300" />
              <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search template" className="w-full bg-transparent text-xs outline-none" />
            </div>
          </div>

          <div className="space-y-2">
            {filteredTemplates.map((template) => (
              <button key={template.id} onClick={() => { setSelectedId(template.id); setActiveTab(template.templateType === 'SYSTEM_DEFAULT' ? 'preview' : 'mapping'); }}
                className={`w-full rounded-xl border p-3 text-left text-sm ${selectedId === template.id ? 'border-brand-300 bg-brand-50' : 'border-slate-100 bg-white'}`}>
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold text-slate-800">{template.name}</p>
                  {template.default && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-700">Default</span>}
                </div>
                <p className="mt-1 text-xs text-slate-400">{template.templateType === 'SYSTEM_DEFAULT' ? 'System template' : 'Agency scan'} - {template.active ? 'Active' : 'Draft'}</p>
              </button>
            ))}
            {!filteredTemplates.length && (
              <div className="rounded-xl border border-dashed border-slate-200 p-4 text-center">
                <p className="text-sm text-slate-400">No templates found.</p>
                <button onClick={() => setCreateOpen(true)} className="mt-3 rounded-xl bg-brand-500 px-4 py-2 text-xs font-bold text-white">Create your first template</button>
              </div>
            )}
          </div>

          <div className="rounded-xl bg-slate-50 p-3 text-xs text-slate-500">
            <p className="font-bold text-slate-700">Template Usage Info</p>
            <p className="mt-1">Agency scan templates use your uploaded paper and mapped fields. System templates use the built-in PDF layout safely.</p>
            <p className="mt-2">Saved system templates: {savedSystemTemplates.length}</p>
            <p>Agency scans: {agencyTemplates.length}</p>
          </div>
        </aside>

        <main className="space-y-5">
          {activeTab === 'gallery' && (
            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {systemTemplates.map((template) => {
                const locked = template.locked || template.unlocked === false;
                const accessLabel = template.accessPlan === 'STARTER' ? 'Free / Starter+' : `${template.accessPlan}+`;
                return (
                <article key={template.key} className="card-premium overflow-hidden">
                  <div className="relative flex aspect-[4/3] items-center justify-center overflow-hidden bg-gradient-to-br from-slate-50 to-brand-50 p-4">
                    <DocumentThumbnail template={template} />
                    {locked && (
                      <div className="absolute inset-0 flex items-center justify-center bg-slate-950/45 backdrop-blur-[1px]">
                        <div className="rounded-2xl bg-white/95 px-4 py-3 text-center shadow-lg">
                          <p className="text-xs font-black uppercase tracking-wider text-slate-800">Locked</p>
                          <p className="mt-1 text-[11px] font-bold text-brand-600">Requires {template.requiredPlan || template.accessPlan}</p>
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="space-y-3 p-4">
                    <div>
                      <h3 className="font-bold text-slate-800">{template.name}</h3>
                      <p className="mt-1 text-xs text-slate-500">{template.description}</p>
                    </div>
                    <div className="flex flex-wrap gap-2 text-[11px] font-bold text-slate-500">
                      <span className="rounded-lg bg-slate-100 px-2 py-1">{template.language}</span>
                      <span className="rounded-lg bg-slate-100 px-2 py-1">{template.pages}</span>
                      <span className="rounded-lg bg-slate-100 px-2 py-1">Conditions: {template.hasConditions ? 'Yes' : 'No'}</span>
                      <span className={`rounded-lg px-2 py-1 ${locked ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>{accessLabel}</span>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <button onClick={() => setPreviewSystemTemplate(template)} className="rounded-xl bg-slate-100 px-3 py-2 text-xs font-bold text-slate-700">Preview</button>
                      {locked ? (
                        <button onClick={() => { showToast(`This template requires ${template.requiredPlan || template.accessPlan} plan.`, 'warning'); window.location.hash = '#/subscription'; }} className="rounded-xl bg-amber-500 px-3 py-2 text-xs font-bold text-white">Upgrade</button>
                      ) : (
                        <button onClick={() => useSystemTemplate(template)} disabled={savingTemplate} className="rounded-xl bg-brand-500 px-3 py-2 text-xs font-bold text-white disabled:opacity-50">Use Template</button>
                      )}
                    </div>
                  </div>
                </article>
              );})}
            </section>
          )}

          {activeTab === 'templates' && (
            <section className="card-premium p-4">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-800">My Templates</h2>
                  <p className="text-xs text-slate-500">Your agency templates and saved system choices. These persist after refresh.</p>
                </div>
                <button onClick={() => setCreateOpen(true)} className="rounded-xl bg-brand-500 px-4 py-2 text-xs font-bold text-white"><Plus size={14} className="mr-1 inline" /> New Template</button>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                {templates.map((template) => (
                  <button key={template.id} onClick={() => setSelectedId(template.id)} className={`rounded-xl border p-4 text-left ${selectedId === template.id ? 'border-brand-300 bg-brand-50' : 'border-slate-100 bg-white'}`}>
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-bold text-slate-800">{template.name}</h3>
                      {template.default && <Star size={16} className="fill-amber-400 text-amber-400" />}
                    </div>
                    <p className="mt-1 text-xs text-slate-500">{template.templateType === 'SYSTEM_DEFAULT' ? 'Ready system template' : 'Uploaded agency paper'}</p>
                    <p className="mt-2 text-xs text-slate-400">{template.active ? 'Active' : 'Draft'} - {template.frontFileUrl ? 'Front page uploaded' : 'Uses system/default PDF layout'}</p>
                  </button>
                ))}
              </div>
            </section>
          )}

          {(activeTab === 'mapping' || activeTab === 'preview') && selected && (
            <>
              <section className="card-premium p-4 space-y-4">
                <div className="flex flex-wrap gap-2">
                  {!selectedIsSystem && (
                    <>
                      <label className="rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white cursor-pointer">
                        <Upload size={14} className="mr-1 inline" /> Upload Front Template
                        <input type="file" accept="application/pdf,image/jpeg,image/jpg,image/png,image/webp,image/jfif" className="hidden" onChange={(e) => upload('front', e.target.files?.[0])} />
                      </label>
                      <label className="rounded-xl bg-slate-100 px-4 py-2 text-xs font-bold text-slate-700 cursor-pointer">
                        <Upload size={14} className="mr-1 inline" /> Upload Conditions Page
                        <input type="file" accept="application/pdf,image/jpeg,image/jpg,image/png,image/webp,image/jfif" className="hidden" onChange={(e) => upload('back', e.target.files?.[0])} />
                      </label>
                      <button onClick={() => addField()} className="rounded-xl bg-emerald-500 px-4 py-2 text-xs font-bold text-white">Add Field</button>
                      <button onClick={saveMapping} disabled={!fields.length} title={!fields.length ? 'Add at least one field before saving mapping.' : 'Save all mapped fields'} className="rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white disabled:opacity-50">Save Mapping</button>
                    </>
                  )}
                  <button onClick={previewPdf} className="rounded-xl bg-slate-100 px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-200">Preview PDF</button>
                  <button onClick={setDefault} className="rounded-xl bg-brand-500 px-4 py-2 text-xs font-bold text-white">Set As Default</button>
                  <button onClick={deleteTemplate} className="rounded-xl bg-red-50 px-4 py-2 text-xs font-bold text-red-600"><Trash2 size={13} className="mr-1 inline" /> Delete Template</button>
                </div>
                <div className="flex flex-wrap gap-2 text-xs text-slate-500">
                  <span className="rounded-lg bg-slate-50 px-2 py-1">Name: {selected.name}</span>
                  <span className="rounded-lg bg-slate-50 px-2 py-1">Source: {selected.templateType === 'SYSTEM_DEFAULT' ? 'SYSTEM' : 'AGENCY_SCAN'}</span>
                  <span className="rounded-lg bg-slate-50 px-2 py-1">Status: {selected.active ? 'Active' : 'Draft'}</span>
                  {selected.default && <span className="rounded-lg bg-emerald-50 px-2 py-1 text-emerald-700">Default</span>}
                </div>
                <p className="text-xs text-slate-500">Recommended scan: A4 portrait, 300 DPI, clean white background. System templates do not require uploaded paper.</p>
              </section>

              <section className="grid gap-5 xl:grid-cols-[1fr_420px]">
                <div className="card-premium p-4">
                  <p className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-400">Template Preview</p>
                  <div
                    className="relative mx-auto aspect-[210/297] max-h-[820px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
                    onMouseMove={onCanvasMove}
                    onMouseUp={() => setDragId(null)}
                    onMouseLeave={() => setDragId(null)}
                  >
                    {frontUrl ? (
                      selected.frontFileUrl?.toLowerCase().endsWith('.pdf') ? (
                        <iframe src={frontUrl} className="h-full w-full" title="Contract template preview" />
                      ) : (
                        <img src={frontUrl} alt="Contract template" className="h-full w-full object-fill" />
                      )
                    ) : (
                      <div className="flex h-full flex-col items-center justify-center p-8 text-center text-slate-300">
                        <FileText size={46} />
                        <p className="mt-3 text-sm font-bold">{selected.templateType === 'SYSTEM_DEFAULT' ? 'System PDF layout will be used' : 'Upload a scanned front page'}</p>
                        <p className="mt-1 text-xs text-slate-400">{selected.templateType === 'SYSTEM_DEFAULT' ? 'This ready template uses the backend system PDF generator.' : 'No fields mapped yet? Click Add Field to place contract data on your paper.'}</p>
                      </div>
                    )}
                    {previewFields.map((field, index) => (
                      <button
                        key={field.id || `new-${index}`}
                        onClick={() => setSelectedFieldIndex(fields.indexOf(field))}
                        onMouseDown={() => setDragId(field.id || `new-${fields.indexOf(field)}`)}
                        className={`absolute rounded border px-1 text-left text-[10px] font-bold ${selectedFieldIndex === fields.indexOf(field) ? 'border-emerald-500 bg-emerald-500/20 text-emerald-700' : 'border-brand-400 bg-brand-500/15 text-brand-700'}`}
                        style={{ left: `${field.xPercent}%`, top: `${field.yPercent}%`, width: `${field.widthPercent}%`, height: `${field.heightPercent}%`, fontSize: `${Math.max(8, field.fontSize)}px` }}
                      >
                        {SAMPLE_VALUES[field.fieldKey] || field.label || field.fieldKey}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="card-premium max-h-[860px] overflow-y-auto p-4 space-y-4">
                  <div>
                    <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400">Field Mapping Tools</h2>
                    {selectedIsSystem ? (
                      <p className="mt-2 rounded-xl border border-brand-100 bg-brand-50 p-3 text-xs text-brand-700">This system template already includes built-in field mapping. You can preview it, set it as default, or select it from Contract Details for PDF generation.</p>
                    ) : !fields.length && (
                      <p className="mt-2 rounded-xl border border-dashed border-slate-200 p-3 text-xs text-slate-400">No fields mapped yet. Click Add Field or use a quick button below.</p>
                    )}
                  </div>
                  {!selectedIsSystem && (
                    <div className="grid grid-cols-2 gap-2">
                      {QUICK_FIELDS.map((quick) => (
                        <button key={quick.fieldKey} onClick={() => addField({ ...quick, label: quick.label, heightPercent: quick.fieldKey.includes('signature') ? 7 : 4 })}
                          className="rounded-xl bg-slate-100 px-3 py-2 text-left text-xs font-bold text-slate-600 hover:bg-slate-200">
                          {quick.label}
                        </button>
                      ))}
                    </div>
                  )}

                  {!selectedIsSystem && selectedField ? (
                    <div className="rounded-xl border border-brand-100 bg-brand-50 p-3 space-y-2">
                      <p className="text-xs font-bold text-brand-700">Selected field properties</p>
                      <select value={selectedField.fieldKey} onChange={(e) => updateField(selectedFieldIndex!, { fieldKey: e.target.value, label: e.target.value })} className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs">
                        {FIELD_KEYS.map((key) => <option key={key} value={key}>{key}</option>)}
                      </select>
                      <div className="grid grid-cols-4 gap-2">
                        <Input label="X%" value={selectedField.xPercent} onChange={(value) => updateField(selectedFieldIndex!, { xPercent: value })} />
                        <Input label="Y%" value={selectedField.yPercent} onChange={(value) => updateField(selectedFieldIndex!, { yPercent: value })} />
                        <Input label="W%" value={selectedField.widthPercent} onChange={(value) => updateField(selectedFieldIndex!, { widthPercent: value })} />
                        <Input label="H%" value={selectedField.heightPercent} onChange={(value) => updateField(selectedFieldIndex!, { heightPercent: value })} />
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        <Input label="Font" value={selectedField.fontSize} onChange={(value) => updateField(selectedFieldIndex!, { fontSize: value })} />
                        <select value={selectedField.textAlign} onChange={(e) => updateField(selectedFieldIndex!, { textAlign: e.target.value })} className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs">
                          <option value="left">Left</option><option value="center">Center</option><option value="right">Right</option>
                        </select>
                        <button onClick={() => saveField(selectedField)} className="rounded-lg bg-brand-500 px-2 py-2 text-xs font-bold text-white"><Save size={13} className="inline" /> Save</button>
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <button onClick={() => duplicateField(selectedFieldIndex!)} className="rounded-lg bg-white px-2 py-2 text-xs font-bold text-slate-600"><Copy size={13} className="inline" /> Duplicate</button>
                        <button onClick={() => deleteFieldAt(selectedFieldIndex!)} className="rounded-lg bg-red-50 px-2 py-2 text-xs font-bold text-red-600">Delete</button>
                      </div>
                    </div>
                  ) : !selectedIsSystem ? (
                    <p className="rounded-xl bg-slate-50 p-3 text-xs text-slate-500">Select a field on the template or click Add Field to start mapping.</p>
                  ) : (
                    <div className="rounded-xl bg-slate-50 p-3 text-xs text-slate-500">
                      <p className="font-bold text-slate-700">Built-in sample data</p>
                      <p className="mt-1">CTR-2026-00004 - Mohamed Yacoubi - Hyundai i20 - 19/06/2026 to 22/06/2026 - 1728 MAD</p>
                    </div>
                  )}

                  {!selectedIsSystem && <div className="space-y-2">
                    {fields.map((field, index) => (
                      <button key={field.id || `field-${index}`} onClick={() => setSelectedFieldIndex(index)}
                        className={`w-full rounded-xl border p-3 text-left text-xs ${selectedFieldIndex === index ? 'border-brand-300 bg-brand-50' : 'border-slate-100 bg-slate-50'}`}>
                        <p className="font-bold text-slate-700">{field.label || field.fieldKey}</p>
                        <p className="text-slate-400">Page {field.pageNumber} - X {field.xPercent}% / Y {field.yPercent}%</p>
                      </button>
                    ))}
                  </div>}
                </div>
              </section>
            </>
          )}

          {activeTab === 'preview' && !selected && (
            <button onClick={() => setCreateOpen(true)} className="card-premium block w-full p-10 text-center text-slate-400">Create or choose a template to preview it.</button>
          )}

          {activeTab === 'conditions' && (
            <section className="grid gap-5 xl:grid-cols-[1fr_360px]">
              <div className="card-premium p-4 space-y-3">
                <div>
                  <h2 className="text-sm font-bold text-slate-800">Conditions generales</h2>
                  <p className="text-xs text-amber-600">Please review your legal terms before using them officially.</p>
                </div>
                <textarea value={termsText} onChange={(e) => setTermsText(e.target.value)} className="min-h-[280px] w-full rounded-xl border border-slate-200 bg-white p-3 text-sm outline-none focus:border-brand-300" />
                <button disabled={loading} onClick={async () => { setLoading(true); await saveTerms().finally(() => setLoading(false)); }} className="rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white">Save Conditions</button>
              </div>
              <div className="card-premium p-4 space-y-3">
                <h3 className="text-sm font-bold text-slate-800">Conditions Page Options</h3>
                <p className="text-xs text-slate-500">Use uploaded scanned conditions page, agency terms, or system default conditions in generated PDFs.</p>
                {selected && (
                  <label className="block rounded-xl border border-dashed border-slate-200 p-3 text-sm text-slate-500">
                    Upload Conditions Page
                    <input type="file" accept="application/pdf,image/jpeg,image/jpg,image/png,image/webp,image/jfif" className="mt-2 block w-full text-xs" onChange={(e) => upload('back', e.target.files?.[0])} />
                  </label>
                )}
                {backUrl ? (
                  selected?.backFileUrl?.toLowerCase().endsWith('.pdf') ? <iframe src={backUrl} className="h-56 w-full rounded-lg bg-white" title="Conditions template preview" /> : <img src={backUrl} alt="Conditions template" className="max-h-56 w-full rounded-lg object-contain" />
                ) : (
                  <p className="rounded-lg border border-dashed border-slate-200 p-4 text-center text-xs text-slate-400">No scanned conditions page uploaded. Agency/system text can still be used.</p>
                )}
              </div>
            </section>
          )}
        </main>
      </div>

      {previewSystemTemplate && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm">
          <div className="w-full max-w-xl rounded-3xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">{previewSystemTemplate.name}</h2>
              <button onClick={() => setPreviewSystemTemplate(null)} className="rounded-lg p-2 text-slate-400 hover:bg-slate-50"><X size={18} /></button>
            </div>
            <div className="mt-4 rounded-2xl bg-slate-50 p-5">
              <DocumentThumbnail template={previewSystemTemplate} large />
            </div>
            <p className="mt-4 text-sm text-slate-500">{previewSystemTemplate.description}</p>
            {previewSystemTemplate.locked || previewSystemTemplate.unlocked === false ? (
              <button onClick={() => { showToast(`This template requires ${previewSystemTemplate.requiredPlan || previewSystemTemplate.accessPlan} plan.`, 'warning'); window.location.hash = '#/subscription'; }} className="mt-4 w-full rounded-xl bg-amber-500 px-4 py-3 text-sm font-bold text-white">Upgrade to {previewSystemTemplate.requiredPlan || previewSystemTemplate.accessPlan}</button>
            ) : (
              <button onClick={() => { useSystemTemplate(previewSystemTemplate); setPreviewSystemTemplate(null); }} className="mt-4 w-full rounded-xl bg-brand-500 px-4 py-3 text-sm font-bold text-white">Use Template</button>
            )}
          </div>
        </div>
      )}

      {createOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl rounded-3xl bg-white p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">Create Contract Template</h2>
              <button onClick={() => setCreateOpen(false)} className="rounded-lg p-2 text-slate-400 hover:bg-slate-50"><X size={18} /></button>
            </div>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <input value={templateForm.name} onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })} placeholder="Template name" className="rounded-xl border border-slate-200 px-3 py-3 text-sm outline-none focus:border-brand-300 sm:col-span-2" />
              <select value={templateForm.templateType} onChange={(e) => setTemplateForm({ ...templateForm, templateType: e.target.value as TemplateForm['templateType'] })} className="rounded-xl border border-slate-200 px-3 py-3 text-sm outline-none focus:border-brand-300">
                <option value="AGENCY_SCAN_TEMPLATE">Agency scan template</option>
                <option value="SYSTEM_DEFAULT">System default</option>
              </select>
              <select value={templateForm.language} onChange={(e) => setTemplateForm({ ...templateForm, language: e.target.value as TemplateForm['language'] })} className="rounded-xl border border-slate-200 px-3 py-3 text-sm outline-none focus:border-brand-300">
                <option value="FR">FR</option>
                <option value="AR">AR</option>
                <option value="EN">EN</option>
              </select>
              <label className="rounded-xl border border-dashed border-slate-200 p-3 text-sm text-slate-500">
                Front page upload
                <input type="file" accept="application/pdf,image/jpeg,image/jpg,image/png,image/webp,image/jfif" className="mt-2 block w-full text-xs" onChange={(e) => setTemplateForm({ ...templateForm, frontFile: e.target.files?.[0] || null })} />
              </label>
              <label className="rounded-xl border border-dashed border-slate-200 p-3 text-sm text-slate-500">
                Back page / conditions upload optional
                <input type="file" accept="application/pdf,image/jpeg,image/jpg,image/png,image/webp,image/jfif" className="mt-2 block w-full text-xs" onChange={(e) => setTemplateForm({ ...templateForm, backFile: e.target.files?.[0] || null })} />
              </label>
              <label className="flex items-center gap-3 text-sm text-slate-600">
                <input type="checkbox" checked={templateForm.default} onChange={(e) => setTemplateForm({ ...templateForm, default: e.target.checked })} />
                Set as default
              </label>
              <label className="flex items-center gap-3 text-sm text-slate-600">
                <input type="checkbox" checked={templateForm.active} onChange={(e) => setTemplateForm({ ...templateForm, active: e.target.checked })} />
                Active
              </label>
            </div>
            <div className="mt-5 flex gap-2">
              <button onClick={() => setCreateOpen(false)} disabled={savingTemplate} className="flex-1 rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 disabled:opacity-50">Cancel</button>
              <button onClick={createTemplate} disabled={savingTemplate} className="flex-1 rounded-xl bg-brand-500 px-4 py-3 text-sm font-bold text-white disabled:opacity-50">
                {savingTemplate ? 'Saving...' : 'Save Template'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function DocumentThumbnail({ template, large = false }: { template: SystemTemplate; large?: boolean }) {
  const tone = template.accessPlan === 'PREMIUM' ? 'from-amber-50 to-white' : template.accessPlan === 'ENTERPRISE' ? 'from-slate-100 to-white' : 'from-white to-slate-50';
  const showInspection = template.key.includes('inspection') || template.key.includes('luxury');
  const showConditions = Boolean(template.hasConditions);
  return (
    <div className={`${large ? 'mx-auto max-h-[560px] max-w-sm p-5' : 'w-44 p-3'} aspect-[210/297] rounded-xl border border-slate-200 bg-gradient-to-br ${tone} text-[6px] text-slate-700 shadow-lg`}>
      <div className="flex items-start justify-between gap-2 border-b border-slate-200 pb-2">
        <div>
          <div className={`${large ? 'h-3 w-28' : 'h-2 w-20'} rounded bg-slate-900`} />
          <div className="mt-1 h-1.5 w-16 rounded bg-slate-300" />
        </div>
        <div className={`${large ? 'h-9 w-9' : 'h-7 w-7'} rounded border border-slate-300 bg-white text-center leading-7 text-[5px] font-bold text-slate-400`}>LOGO</div>
      </div>
      <div className="mt-2 rounded bg-slate-900 px-2 py-1 text-center font-black uppercase tracking-wider text-white">
        Contrat de location
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2">
        <DocSection title="Client" rows={4} />
        <DocSection title="Vehicule" rows={4} />
      </div>
      <div className="mt-2 grid grid-cols-3 gap-1.5">
        <MiniBox label="Depart" />
        <MiniBox label="Retour" />
        <MiniBox label="Total" strong />
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2">
        <DocSection title="Paiement" rows={3} />
        <div className="rounded border border-slate-200 bg-white p-1">
          <p className="mb-1 font-bold uppercase text-slate-500">Documents</p>
          {['Carte grise', 'Assurance', 'Vignette', 'Permis'].map((item) => (
            <div key={item} className="mb-0.5 flex items-center gap-1"><span className="h-1.5 w-1.5 rounded-sm border border-slate-400" /><span className="h-1 w-12 rounded bg-slate-200" /></div>
          ))}
        </div>
      </div>
      {showInspection && (
        <div className="mt-2 rounded border border-slate-200 bg-white p-1">
          <p className="font-bold uppercase text-slate-500">Inspection vehicule</p>
          <div className="mt-1 flex items-center justify-center rounded bg-slate-50 py-1">
            <div className="h-5 w-14 rounded-full border border-slate-300">
              <div className="mx-auto mt-1 h-2 w-8 rounded border border-slate-300" />
            </div>
          </div>
        </div>
      )}
      <div className="mt-2 grid grid-cols-2 gap-2">
        <SignatureBox label="Signature client" />
        <SignatureBox label="Signature agence" />
      </div>
      {showConditions && (
        <div className="mt-2 rounded border border-dashed border-slate-300 bg-white/80 p-1">
          <p className="font-bold uppercase text-slate-500">Conditions generales</p>
          <div className="mt-1 space-y-0.5">{Array.from({ length: 4 }).map((_, index) => <div key={index} className="h-1 rounded bg-slate-200" />)}</div>
        </div>
      )}
    </div>
  );
}

function DocSection({ title, rows }: { title: string; rows: number }) {
  return (
    <div className="rounded border border-slate-200 bg-white p-1">
      <p className="mb-1 font-bold uppercase text-slate-500">{title}</p>
      {Array.from({ length: rows }).map((_, index) => <div key={index} className="mb-0.5 h-1 rounded bg-slate-200" />)}
    </div>
  );
}

function MiniBox({ label, strong = false }: { label: string; strong?: boolean }) {
  return (
    <div className={`rounded border p-1 ${strong ? 'border-brand-200 bg-brand-50' : 'border-slate-200 bg-white'}`}>
      <p className="font-bold uppercase text-slate-400">{label}</p>
      <div className="mt-1 h-1 rounded bg-slate-300" />
    </div>
  );
}

function SignatureBox({ label }: { label: string }) {
  return (
    <div className="rounded border border-slate-200 bg-white p-1">
      <p className="font-bold uppercase text-slate-400">{label}</p>
      <div className="mt-2 h-6 rounded border border-dashed border-slate-300" />
    </div>
  );
}

function Input({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label className="text-[10px] font-bold uppercase text-slate-400">
      {label}
      <input type="number" value={value} onChange={(e) => onChange(Number(e.target.value))}
        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700" />
    </label>
  );
}

const defaultTerms = `ART 1: UTILISATION DU VEHICULE
ART 2: ETAT DU VEHICULE
ART 3: CARBURANTS ET LUBRIFIANTS
ART 4: ENTRETIEN ET REPARATIONS
ART 5: ASSURANCES
ART 6: REGLEMENT - PROLONGATION - RETOUR
ART 7: DOCUMENTS DE LA VOITURE
ART 8: RESPONSABILITE
ART 9: JURIDICTION`;
