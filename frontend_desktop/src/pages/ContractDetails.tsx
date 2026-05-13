import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { 
  FileText, ArrowLeft, QrCode, Share2, Copy, 
  ExternalLink, Download, Clock, CheckCircle2, 
  XCircle, AlertCircle, Calendar, User, Car, 
  Wallet, Printer, Mail, Send, Shield
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
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
  totalAmount: number;
  status: 'Draft' | 'Pending Signature' | 'Signed' | 'Expired';
  signedAt?: string;
}

export default function ContractDetails() {
  const { id } = useParams<{ id: string }>();
  const { showToast } = useToast();
  const [contract, setContract] = useState<ContractData | null>(null);

  // In a real app, this would be the actual public URL
  const publicSignUrl = `${window.location.origin}/#/sign/${id}`;

  useEffect(() => {
    // Check if we have a saved version in localStorage
    const saved = localStorage.getItem(`contract_${id}`);
    if (saved) {
      setContract(JSON.parse(saved));
    } else {
      // Mock data if not found
      setContract({
        id: id || 'CTR-2026-001',
        clientName: 'Youssef El Mansouri',
        clientEmail: 'youssef.mansouri@email.com',
        clientPhone: '+212 612-345678',
        vehicleName: 'Dacia Logan 2024',
        vehiclePlate: '12345-A-1',
        startDate: '2026-05-15',
        endDate: '2026-05-20',
        totalAmount: 1750,
        status: 'Pending Signature',
      });
    }
  }, [id]);

  const copyToClipboard = () => {
    navigator.clipboard.writeText(publicSignUrl);
    showToast('Link copied to clipboard!', 'success');
  };

  const shareContract = () => {
    if (navigator.share) {
      navigator.share({
        title: `Contract ${id}`,
        text: `Please sign the rental agreement for ${contract?.vehicleName}`,
        url: publicSignUrl,
      });
    } else {
      copyToClipboard();
    }
  };

  if (!contract) return <div className="p-8 text-center">Loading contract...</div>;

  return (
    <div className="space-y-6 animate-fade">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <Link to="/contracts" className="p-2 bg-white border border-[#e8e6e1] rounded-xl text-slate-500 hover:bg-slate-50 transition-all">
            <ArrowLeft size={20} />
          </Link>
          <div>
            <h1 className="text-xl font-bold text-[#1e293b]">Contract Details</h1>
            <p className="text-slate-500 font-normal text-sm mt-0.5">Manage and track your digital agreement</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button className="flex items-center gap-2 px-5 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all">
            <Printer size={18} /> Print
          </button>
          <button className="flex items-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
            <Download size={18} /> Export PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Contract Info */}
        <div className="lg:col-span-2 space-y-6">
          {/* Status Banner */}
          <div className={`p-6 rounded-2xl border flex items-center justify-between ${
            contract.status === 'Signed' ? 'bg-emerald-50 border-emerald-100 text-emerald-800' : 
            contract.status === 'Pending Signature' ? 'bg-amber-50 border-amber-100 text-amber-800' :
            'bg-slate-50 border-slate-100 text-slate-800'
          }`}>
            <div className="flex items-center gap-4">
              <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                contract.status === 'Signed' ? 'bg-emerald-500 text-white' : 
                contract.status === 'Pending Signature' ? 'bg-amber-500 text-white' :
                'bg-slate-500 text-white'
              }`}>
                {contract.status === 'Signed' ? <CheckCircle2 size={24} /> : 
                 contract.status === 'Pending Signature' ? <Clock size={24} /> :
                 <FileText size={24} />}
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-widest opacity-70">Current Status</p>
                <h3 className="text-lg font-bold">{contract.status}</h3>
              </div>
            </div>
            {contract.status === 'Signed' && (
              <div className="text-right hidden md:block">
                <p className="text-xs font-medium opacity-70 italic">Signed at</p>
                <p className="font-bold">{new Date(contract.signedAt!).toLocaleString()}</p>
              </div>
            )}
          </div>

          {/* Details Card */}
          <div className="card-premium space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {/* Client Info */}
              <div className="space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b pb-2">Client Details</h4>
                <div className="space-y-3">
                  <div className="flex items-center gap-3">
                    <User size={18} className="text-brand-500" />
                    <div>
                      <p className="text-sm font-bold text-slate-900">{contract.clientName}</p>
                      <p className="text-xs text-slate-500">{contract.clientEmail}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 text-slate-600">
                    <Mail size={18} />
                    <span className="text-sm">{contract.clientEmail}</span>
                  </div>
                </div>
              </div>

              {/* Vehicle Info */}
              <div className="space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b pb-2">Vehicle Details</h4>
                <div className="space-y-3">
                  <div className="flex items-center gap-3">
                    <Car size={18} className="text-brand-500" />
                    <div>
                      <p className="text-sm font-bold text-slate-900">{contract.vehicleName}</p>
                      <p className="text-xs text-slate-500 font-mono">{contract.vehiclePlate}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 text-slate-600">
                    <Calendar size={18} />
                    <span className="text-sm">{new Date(contract.startDate).toLocaleDateString()} - {new Date(contract.endDate).toLocaleDateString()}</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="pt-6 border-t border-slate-100 flex flex-wrap gap-12">
               <div>
                  <p className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-1">Total Amount</p>
                  <p className="text-2xl font-black text-brand-600">{contract.totalAmount} DH</p>
               </div>
               <div>
                  <p className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-1">Payment Method</p>
                  <p className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Wallet size={16} className="text-slate-400" /> Credit Card / In-Person
                  </p>
               </div>
               <div>
                  <p className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-1">Verification</p>
                  <p className="text-sm font-bold text-emerald-600 flex items-center gap-2">
                    <Shield size={16} /> Secure Digital Audit
                  </p>
               </div>
            </div>
          </div>

          {/* Action Log */}
          <div className="card-premium">
             <h4 className="text-sm font-bold text-slate-900 mb-6">Activity Timeline</h4>
             <div className="space-y-6">
                <div className="flex gap-4">
                   <div className="w-1 bg-brand-500 rounded-full"></div>
                   <div>
                      <p className="text-sm font-bold text-slate-900">Contract Created</p>
                      <p className="text-xs text-slate-500">Draft version generated by Admin</p>
                      <p className="text-[10px] text-slate-400 mt-1 uppercase font-bold">{new Date(contract.startDate).toLocaleDateString()} - 09:42 AM</p>
                   </div>
                </div>
                <div className="flex gap-4">
                   <div className="w-1 bg-amber-500 rounded-full"></div>
                   <div>
                      <p className="text-sm font-bold text-slate-900">Sent to Client</p>
                      <p className="text-xs text-slate-500">Public signature link generated</p>
                      <p className="text-[10px] text-slate-400 mt-1 uppercase font-bold">{new Date(contract.startDate).toLocaleDateString()} - 10:15 AM</p>
                   </div>
                </div>
                {contract.status === 'Signed' && (
                  <div className="flex gap-4">
                    <div className="w-1 bg-emerald-500 rounded-full"></div>
                    <div>
                        <p className="text-sm font-bold text-slate-900">Client Signed</p>
                        <p className="text-xs text-slate-500">Digitally signed via mobile device</p>
                        <p className="text-[10px] text-slate-400 mt-1 uppercase font-bold font-mono">{new Date(contract.signedAt!).toLocaleString()}</p>
                    </div>
                  </div>
                )}
             </div>
          </div>
        </div>

        {/* Right Column: QR & Sharing */}
        <div className="space-y-6">
          {/* QR Code Card */}
          <div className="card-premium text-center">
            <h4 className="text-sm font-bold text-slate-900 mb-2">Signature QR Code</h4>
            <p className="text-xs text-slate-500 mb-6 px-4">Client scans this QR code to sign instantly on their device</p>
            
            <div className="bg-white p-6 rounded-2xl border-2 border-dashed border-slate-100 inline-block mb-6 shadow-inner">
               <QRCodeSVG value={publicSignUrl} size={180} level="H" />
            </div>

            <div className="flex flex-col gap-3">
               <button 
                onClick={copyToClipboard}
                className="w-full flex items-center justify-center gap-2 py-3 bg-[#f5f5f0] text-slate-700 rounded-xl font-bold text-sm hover:bg-[#ebebe5] transition-all"
               >
                 <Copy size={16} /> Copy URL
               </button>
               <button 
                onClick={shareContract}
                className="w-full flex items-center justify-center gap-2 py-3 bg-brand-500 text-white rounded-xl font-bold text-sm hover:bg-brand-600 transition-all shadow-lg shadow-brand-500/20"
               >
                 <Share2 size={16} /> Share via WhatsApp
               </button>
            </div>
          </div>

          {/* Quick Actions */}
          <div className="card-premium">
             <h4 className="text-sm font-bold text-slate-900 mb-4">Quick Actions</h4>
             <div className="space-y-2">
                <Link to={`/sign/${id}`} target="_blank" className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-all group">
                   <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-500 flex items-center justify-center"><ExternalLink size={16} /></div>
                      <span className="text-sm font-medium text-slate-700">Preview as Client</span>
                   </div>
                   <ArrowLeft size={16} className="text-slate-300 rotate-180 group-hover:translate-x-1 transition-all" />
                </Link>
                <button className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 transition-all group">
                   <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-500 flex items-center justify-center"><Send size={16} /></div>
                      <span className="text-sm font-medium text-slate-700">Email to Client</span>
                   </div>
                   <ArrowLeft size={16} className="text-slate-300 rotate-180 group-hover:translate-x-1 transition-all" />
                </button>
                <button className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-rose-50 transition-all group">
                   <div className="flex items-center gap-3 text-rose-600">
                      <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-500 flex items-center justify-center"><XCircle size={16} /></div>
                      <span className="text-sm font-medium">Cancel Contract</span>
                   </div>
                </button>
             </div>
          </div>
        </div>
      </div>

      {/* Full Document Preview Section */}
      <div className="space-y-4 pt-6">
        <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
          <FileText size={20} className="text-brand-500" /> Full Document Content
        </h2>
        <div className="card-premium overflow-hidden bg-slate-50 border-2 border-dashed border-slate-200 p-0">
          <div className="max-w-3xl mx-auto my-8 bg-white shadow-xl border border-slate-100 p-8 md:p-12 text-slate-800 pointer-events-none opacity-90 scale-[0.98] origin-top">
             <div className="flex justify-between items-start mb-8 pb-8 border-b">
                <div className="font-black text-xl text-brand-600">AUTOLINK <span className="text-slate-400">RENTAL</span></div>
                <div className="text-right text-xs text-slate-400 font-mono">ID: {contract.id}</div>
             </div>
             
             <div className="space-y-6 text-sm leading-relaxed">
                <div className="grid grid-cols-2 gap-8">
                   <div className="space-y-1">
                      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Lessor</p>
                      <p className="font-bold">AutoLink Rental Solutions</p>
                      <p className="text-slate-500">Casablanca, Morocco</p>
                   </div>
                   <div className="space-y-1">
                      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Lessee</p>
                      <p className="font-bold">{contract.clientName}</p>
                      <p className="text-slate-500">{contract.clientEmail}</p>
                   </div>
                </div>

                <div className="py-4 border-y border-slate-50 space-y-2">
                   <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Vehicle & Period</p>
                   <p className="font-medium text-slate-700">{contract.vehicleName} ({contract.vehiclePlate})</p>
                   <p className="text-slate-600 italic">{new Date(contract.startDate).toLocaleDateString()} to {new Date(contract.endDate).toLocaleDateString()}</p>
                </div>

                <div className="space-y-3">
                   <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">General Terms</p>
                   <p className="text-xs text-slate-500">1. The vehicle must be returned with the same fuel level as received.</p>
                   <p className="text-xs text-slate-500">2. Any damage to the vehicle during the rental period is the responsibility of the client.</p>
                   <p className="text-xs text-slate-500">3. Late return will incur additional charges as per the company policy.</p>
                </div>

                <div className="pt-8 flex justify-between items-end opacity-40">
                   <div className="w-32 border-b border-slate-300 pb-1 text-[10px] uppercase font-bold text-slate-300">Owner Signature</div>
                   <div className="w-32 border-b border-slate-300 pb-1 text-[10px] uppercase font-bold text-slate-300 text-right">Client Signature</div>
                </div>
             </div>
          </div>
          <div className="bg-white/80 backdrop-blur-sm p-4 border-t border-slate-100 text-center relative z-10">
             <p className="text-xs text-slate-500">This is a system-generated preview of the legal document.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
