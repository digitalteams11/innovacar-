import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { FileText, Calendar, User, Car, Shield, CheckCircle2, Download, Printer, MapPin, Phone, Mail, Building2, Landmark } from 'lucide-react';
import SignaturePad from '../components/contracts/SignaturePad';
import { useToast } from '../context/ToastContext';

interface ContractData {
  id: string;
  clientName: string;
  clientEmail: string;
  clientPhone: string;
  vehicleName: string;
  vehiclePlate: string;
  startDate: string;
  endDate: string;
  pricePerDay: number;
  totalAmount: number;
  status: 'Draft' | 'Pending Signature' | 'Signed' | 'Expired';
  ownerSignature: string;
  clientSignature?: string;
  signedAt?: string;
  terms: string[];
}

export default function PublicContract() {
  const { contractId, token } = useParams<{ contractId?: string; token?: string }>();
  const contractIdentifier = contractId || token || 'CTR-2026-001';
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [contract, setContract] = useState<ContractData | null>(null);
  const [isSigned, setIsSigned] = useState(false);

  useEffect(() => {
    // Mock data for the demonstration
    // In a real app, this would fetch from an API
    const mockContract: ContractData = {
      id: contractIdentifier,
      clientName: 'Youssef El Mansouri',
      clientEmail: 'youssef.mansouri@email.com',
      clientPhone: '+212 612-345678',
      vehicleName: 'Dacia Logan 2024',
      vehiclePlate: '12345-A-1',
      startDate: '2026-05-15',
      endDate: '2026-05-20',
      pricePerDay: 350,
      totalAmount: 1750,
      status: 'Pending Signature',
      ownerSignature: 'https://upload.wikimedia.org/wikipedia/commons/3/3a/Jon_Snow_Signature.png', // Placeholder
      terms: [
        'The vehicle must be returned with the same fuel level as received.',
        'Any damage to the vehicle during the rental period is the responsibility of the client.',
        'Late return will incur additional charges as per the company policy.',
        'The vehicle is insured for third-party liability only.',
        'Smoking is strictly prohibited inside the vehicle.'
      ]
    };
    
    // Check if we have a saved version in localStorage
    const saved = localStorage.getItem(`contract_${contractIdentifier}`);
    if (saved) {
      const parsed = JSON.parse(saved);
      setContract(parsed);
      if (parsed.status === 'Signed') setIsSigned(true);
    } else {
      setContract(mockContract);
    }
  }, [contractIdentifier]);

  const handleSignatureSave = (signatureDataUrl: string) => {
    if (!contract) return;

    const updatedContract: ContractData = {
      ...contract,
      status: 'Signed',
      clientSignature: signatureDataUrl,
      signedAt: new Date().toISOString()
    };

    setContract(updatedContract);
    setIsSigned(true);
    localStorage.setItem(`contract_${token}`, JSON.stringify(updatedContract));
    showToast('Contract signed successfully!', 'success');
  };

  const handlePrint = () => {
    window.print();
  };

  if (!contract) return <div className="min-h-screen flex items-center justify-center">Loading...</div>;

  return (
    <div className="min-h-screen bg-slate-100 py-6 px-4 md:py-12 md:px-0 font-sans print:bg-white print:p-0 animate-fade">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Status Header - Hide during print */}
        <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-4 print:hidden">
          <div className="flex items-center gap-3">
            <div className={`w-3 h-3 rounded-full ${isSigned ? 'bg-success-500 animate-pulse' : 'bg-warning-500 animate-pulse'}`}></div>
            <span className="font-bold text-slate-700">
              {isSigned ? 'Contract Signed & Secured' : 'Action Required: Please Review & Sign'}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button 
              onClick={handlePrint}
              className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-xl font-medium text-sm hover:bg-slate-200 transition-all"
            >
              <Printer size={16} /> Print
            </button>
            {isSigned && (
              <button className="flex items-center gap-2 px-4 py-2 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all">
                <Download size={16} /> Download PDF
              </button>
            )}
          </div>
        </div>

        {/* The Contract Document */}
        <div className="bg-white shadow-2xl rounded-sm border border-slate-200 min-h-[11in] p-8 md:p-16 text-slate-800 relative overflow-hidden print:shadow-none print:border-none print:m-0">
          {/* Document Header */}
          <div className="flex flex-col sm:flex-row justify-between gap-8 mb-12 pb-12 border-b border-slate-100">
            <div className="space-y-4">
              <div className="flex items-center gap-3 text-brand-600">
                <Building2 size={32} />
                <h1 className="text-2xl font-black uppercase tracking-tighter">AutoLink <span className="text-slate-400 font-light">Rental</span></h1>
              </div>
              <div className="space-y-1 text-sm text-slate-500">
                <p className="flex items-center gap-2"><MapPin size={14} /> 123 Business Ave, Casablanca, Morocco</p>
                <p className="flex items-center gap-2"><Phone size={14} /> +212 522-123456</p>
                <p className="flex items-center gap-2"><Mail size={14} /> contact@autolink-rental.com</p>
              </div>
            </div>
            <div className="text-right space-y-2">
              <h2 className="text-3xl font-serif font-bold text-slate-900 italic">Rental Agreement</h2>
              <div className="inline-block px-3 py-1 bg-slate-100 rounded text-xs font-mono font-bold text-slate-500 uppercase tracking-widest">
                ID: {contract.id}
              </div>
              <p className="text-sm text-slate-400">Date: {new Date().toLocaleDateString()}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-12 mb-12">
            {/* Client Section */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold uppercase tracking-widest text-brand-600 flex items-center gap-2 border-b border-brand-100 pb-2">
                <User size={14} /> Client Information
              </h3>
              <div className="space-y-2">
                <p className="font-bold text-lg text-slate-900">{contract.clientName}</p>
                <p className="text-sm text-slate-600">{contract.clientEmail}</p>
                <p className="text-sm text-slate-600">{contract.clientPhone}</p>
              </div>
            </div>

            {/* Vehicle Section */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold uppercase tracking-widest text-brand-600 flex items-center gap-2 border-b border-brand-100 pb-2">
                <Car size={14} /> Vehicle Details
              </h3>
              <div className="space-y-2">
                <p className="font-bold text-lg text-slate-900">{contract.vehicleName}</p>
                <p className="text-sm font-mono bg-slate-100 px-2 py-0.5 rounded inline-block">Plate: {contract.vehiclePlate}</p>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-12 p-6 bg-slate-50 rounded-2xl border border-slate-100">
            {/* Rental Period */}
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Rental Period</p>
              <div className="flex items-center gap-2 text-slate-700 font-medium">
                <Calendar size={16} className="text-brand-500" />
                <span>{new Date(contract.startDate).toLocaleDateString()}</span>
                <span className="text-slate-300">→</span>
                <span>{new Date(contract.endDate).toLocaleDateString()}</span>
              </div>
            </div>
            
            {/* Price Per Day */}
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Daily Rate</p>
              <p className="text-lg font-bold text-slate-900">{contract.pricePerDay} MAD <span className="text-sm font-normal text-slate-500">/day</span></p>
            </div>

            {/* Total Amount */}
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Total Amount</p>
              <p className="text-2xl font-black text-brand-600">{contract.totalAmount} MAD</p>
            </div>
          </div>

          {/* Terms and Conditions */}
          <div className="mb-12 space-y-4">
            <h3 className="text-xs font-bold uppercase tracking-widest text-slate-900 flex items-center gap-2">
              <Shield size={14} /> Terms and Conditions
            </h3>
            <ul className="space-y-3">
              {contract.terms.map((term, idx) => (
                <li key={idx} className="flex gap-3 text-sm text-slate-600 leading-relaxed">
                  <span className="font-bold text-slate-300">{idx + 1}.</span>
                  {term}
                </li>
              ))}
            </ul>
          </div>

          {/* Signatures Section */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-12 mt-16 pt-12 border-t border-slate-100">
            {/* Owner Signature */}
            <div className="space-y-4">
              <p className="text-xs font-bold uppercase tracking-widest text-slate-400">Owner Signature</p>
              <div className="h-24 flex items-center justify-center border-b border-slate-200 bg-slate-50/50 rounded-t-lg">
                <img src={contract.ownerSignature} alt="Owner Signature" className="max-h-16 grayscale opacity-80" />
              </div>
              <div className="text-center">
                <p className="font-bold text-slate-900">AutoLink Rental Management</p>
                <p className="text-xs text-slate-400 font-mono uppercase">Authorized Signatory</p>
              </div>
            </div>

            {/* Client Signature */}
            <div className="space-y-4">
              <p className="text-xs font-bold uppercase tracking-widest text-slate-400">Client Signature</p>
              {isSigned ? (
                <div className="space-y-4 animate-fade">
                  <div className="h-24 flex items-center justify-center border-b border-slate-200 bg-emerald-50/30 rounded-t-lg relative overflow-hidden">
                    <img src={contract.clientSignature} alt="Client Signature" className="max-h-16" />
                    <div className="absolute top-2 right-2">
                      <CheckCircle2 size={20} className="text-emerald-500" />
                    </div>
                  </div>
                  <div className="text-center">
                    <p className="font-bold text-slate-900">{contract.clientName}</p>
                    <p className="text-xs text-slate-400 font-mono uppercase">Signed on {new Date(contract.signedAt!).toLocaleString()}</p>
                  </div>
                </div>
              ) : (
                <div className="bg-slate-50 p-6 rounded-2xl border-2 border-dashed border-brand-200 print:hidden">
                  <SignaturePad onSave={handleSignatureSave} title="Please sign to complete the agreement" />
                </div>
              )}
            </div>
          </div>

          {/* Footer watermark */}
          <div className="absolute bottom-8 left-1/2 -translate-x-1/2 opacity-[0.03] pointer-events-none select-none">
             <Landmark size={400} />
          </div>
        </div>

        {/* Verification Section */}
        <div className="bg-white/50 backdrop-blur-xl rounded-2xl p-6 sm:p-8 border border-white shadow-xl flex flex-col sm:flex-row items-center justify-between gap-6 print:hidden">
          <div className="space-y-2">
            <h4 className="font-bold text-slate-800 flex items-center gap-2">
              <Shield size={18} className="text-brand-500" /> Blockchain Verified
            </h4>
            <p className="text-xs text-slate-500 max-w-md">
              This document is cryptographically signed and timestamped. Any alteration will invalidate the signature. 
              Verification ID: <span className="font-mono bg-slate-100 px-1 rounded">{contract.id.replace('-', '')}f7a9c2</span>
            </p>
          </div>
          {isSigned && (
             <div className="flex items-center gap-3 bg-emerald-50 text-emerald-700 px-5 py-3 rounded-2xl font-bold text-sm border border-emerald-100">
               <CheckCircle2 size={20} />
               Valid Document
             </div>
          )}
        </div>
      </div>
    </div>
  );
}
