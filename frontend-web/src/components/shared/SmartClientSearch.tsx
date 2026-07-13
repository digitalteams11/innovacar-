import { useState, useEffect, useRef } from 'react';
import { Search, User, Phone, Mail, Shield, Check, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../../api/axios';

export interface ClientData {
  id: number;
  name: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  secondaryPhone: string;
  address: string;
  city: string;
  country: string;
  postalCode: string;
  nationality: string;
  gender: string;
  birthDate: string;
  cin: string;
  passportNumber: string;
  drivingLicense: string;
  drivingLicenseIssue: string;
  drivingLicenseExpiry: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
  companyName: string;
  notes: string;
}

export interface SmartClientSearchValue {
  clientId?: number;
  clientFirstName?: string;
  clientLastName?: string;
  clientFullName?: string;
  clientEmail?: string;
  clientPhone?: string;
  clientSecondaryPhone?: string;
  clientAddress?: string;
  clientCity?: string;
  clientCountry?: string;
  clientPostalCode?: string;
  clientNationality?: string;
  clientGender?: string;
  clientBirthDate?: string;
  clientCin?: string;
  clientPassportNumber?: string;
  clientDriverLicense?: string;
  clientDriverLicenseIssue?: string;
  clientDriverLicenseExpiry?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  companyName?: string;
  notes?: string;
}

interface SmartClientSearchProps {
  value: Partial<SmartClientSearchValue>;
  onSelect: (client: Partial<SmartClientSearchValue>) => void;
  onCreateNew?: () => void;
  label?: string;
  placeholder?: string;
  required?: boolean;
}

export default function SmartClientSearch({
  value,
  onSelect,
  onCreateNew,
  label,
  placeholder,
  required = false,
}: SmartClientSearchProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [clients, setClients] = useState<ClientData[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (value.clientFullName && !query) {
      setQuery(value.clientFullName);
    }
  }, [value.clientFullName]);

  const searchClients = async (q: string) => {
    if (q.length < 2) {
      setClients([]);
      return;
    }
    // Cancel any still-in-flight search so a slow earlier response can never
    // overwrite the result of a more recent keystroke.
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    try {
      const response = await api.get('/clients', { signal: controller.signal });
      const data = Array.isArray(response.data) ? response.data : response.data?.data;
      const filtered = (Array.isArray(data) ? data : []).filter((c: ClientData) =>
        c.name?.toLowerCase().includes(q.toLowerCase()) ||
        c.phone?.includes(q) ||
        c.email?.toLowerCase().includes(q.toLowerCase()) ||
        c.cin?.includes(q) ||
        c.passportNumber?.includes(q)
      );
      setClients(filtered.slice(0, 8));
    } catch (err) {
      if ((err as { code?: string })?.code === 'ERR_CANCELED') return;
      setClients([]);
    } finally {
      if (abortRef.current === controller) setLoading(false);
    }
  };

  const handleSelect = (client: ClientData) => {
    onSelect({
      clientId: client.id,
      clientFirstName: client.firstName || client.name?.split(' ')[0] || '',
      clientLastName: client.lastName || client.name?.split(' ').slice(1).join(' ') || '',
      clientFullName: client.name,
      clientEmail: client.email,
      clientPhone: client.phone,
      clientSecondaryPhone: client.secondaryPhone,
      clientAddress: client.address,
      clientCity: client.city,
      clientCountry: client.country,
      clientPostalCode: client.postalCode,
      clientNationality: client.nationality,
      clientGender: client.gender,
      clientBirthDate: client.birthDate,
      clientCin: client.cin,
      clientPassportNumber: client.passportNumber,
      clientDriverLicense: client.drivingLicense,
      clientDriverLicenseIssue: client.drivingLicenseIssue,
      clientDriverLicenseExpiry: client.drivingLicenseExpiry,
      emergencyContactName: client.emergencyContactName,
      emergencyContactPhone: client.emergencyContactPhone,
      companyName: client.companyName,
      notes: client.notes,
    });
    setQuery(client.name);
    setShowDropdown(false);
  };

  const handleInputChange = (val: string) => {
    setQuery(val);
    setShowDropdown(true);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      searchClients(val);
    }, 250);
  };

  const showCreateOption = query.length >= 2 && clients.length === 0 && !loading && onCreateNew;
  const labelText = label ?? t('clientSearch.label');
  const placeholderText = placeholder ?? t('clientSearch.placeholder');

  return (
    <div ref={containerRef} className="relative">
      <label className="block text-xs font-medium text-slate-500 mb-1">
        {labelText}{required && <span className="text-danger-500 ml-0.5">*</span>}
      </label>
      <div className="relative">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="search"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          spellCheck="false"
          name="client_search"
          placeholder={placeholderText}
          value={query}
          onChange={(e) => handleInputChange(e.target.value)}
          onFocus={() => {
            if (query.length >= 2) setShowDropdown(true);
          }}
          className="w-full pl-10 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
        />
      </div>

      {showDropdown && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-slate-200 rounded-xl shadow-xl max-h-72 overflow-y-auto">
          {loading ? (
            <div className="p-4 text-center text-sm text-slate-400">{t('clientSearch.searching')}</div>
          ) : clients.length > 0 ? (
            clients.map((client) => (
              <button
                key={client.id}
                onClick={() => handleSelect(client)}
                className="w-full flex items-start gap-3 p-3 hover:bg-brand-50 transition-all text-left border-b border-slate-50 last:border-0"
              >
                <div className="w-9 h-9 bg-brand-100 rounded-lg flex items-center justify-center text-brand-500 shrink-0">
                  <User size={16} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[#1e293b]">{client.name}</p>
                  <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-0.5">
                    {client.phone && <span className="text-[11px] text-slate-400 flex items-center gap-1"><Phone size={10} /> {client.phone}</span>}
                    {client.email && <span className="text-[11px] text-slate-400 flex items-center gap-1"><Mail size={10} /> {client.email}</span>}
                    {client.cin && <span className="text-[11px] text-slate-400 flex items-center gap-1"><Shield size={10} /> {client.cin}</span>}
                  </div>
                </div>
                {value.clientId === client.id && <Check size={16} className="text-success-500 shrink-0 mt-1" />}
              </button>
            ))
          ) : showCreateOption ? (
            <button
              onClick={() => {
                setShowDropdown(false);
                onCreateNew?.();
              }}
              className="w-full flex items-center gap-3 p-3 hover:bg-brand-50 transition-all text-left text-brand-500"
            >
              <div className="w-9 h-9 bg-brand-50 rounded-lg flex items-center justify-center shrink-0">
                <Plus size={16} />
              </div>
              <span className="text-sm font-medium">{t('clientSearch.createNew', { name: query })}</span>
            </button>
          ) : (
            <div className="p-4 text-center text-sm text-slate-400">
              {query.length < 2 ? t('clientSearch.minChars') : t('clientSearch.noResults')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
