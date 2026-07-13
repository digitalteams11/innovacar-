import { useRef, useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { X, Copy, Check, MessageCircle, Mail, Smartphone, Download, CheckCircle2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import { resolveMediaUrl } from '../../lib/utils';
import api from '../../api/axios';

interface QRCodeModalProps {
  isOpen: boolean;
  onClose: () => void;
  qrToken: string;
  signingUrl: string;
  contractNumber: string;
  clientName: string;
  contractId?: string | number;
  clientSigned?: boolean;
  signedAt?: string;
}

export default function QRCodeModal({
  isOpen,
  onClose,
  qrToken,
  signingUrl: _signingUrl,
  contractNumber,
  clientName,
  contractId,
  clientSigned = false,
  signedAt,
}: QRCodeModalProps) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const { tenant } = useAuth();
  const [copied, setCopied] = useState(false);
  const [internalSigned, setInternalSigned] = useState(false);
  const [qrLogoSrc, setQrLogoSrc] = useState('');
  const qrRef = useRef<HTMLDivElement>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isSigned = clientSigned || internalSigned;
  const brandingLogoUrl = resolveMediaUrl(tenant?.logoUrl) || '';

  // Polling fallback: every 3 s while modal open and not yet signed
  useEffect(() => {
    if (!isOpen || !contractId || isSigned) {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
      return;
    }
    pollRef.current = setInterval(async () => {
      try {
        const { data } = await api.get(`/contracts/${contractId}`);
        const payload = data?.data ?? data;
        if (payload?.clientSigned || payload?.signatureStatus === 'FULLY_SIGNED') {
          setInternalSigned(true);
          showToast(t('contracts.contractSignedByClient') || 'Contract signed by client!', 'success');
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        }
      } catch {
        // silently fail — page-level SSE/polling is the primary signal
      }
    }, 3000);
    return () => { if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; } };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, contractId, isSigned]);

  // Reset internal state when modal closes
  useEffect(() => {
    if (!isOpen) setInternalSigned(false);
  }, [isOpen]);

  useEffect(() => {
    let cancelled = false;

    if (!isOpen) return;
    if (!brandingLogoUrl) {
      setQrLogoSrc('');
      return;
    }
    if (brandingLogoUrl.startsWith('data:')) {
      setQrLogoSrc(brandingLogoUrl);
      return;
    }

    fetch(brandingLogoUrl)
      .then((response) => {
        if (!response.ok) throw new Error('Logo fetch failed');
        return response.blob();
      })
      .then((blob) => new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(String(reader.result));
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      }))
      .then((dataUrl) => {
        if (!cancelled) setQrLogoSrc(dataUrl);
      })
      .catch(() => {
        if (!cancelled) setQrLogoSrc('');
      });

    return () => { cancelled = true; };
  }, [brandingLogoUrl, isOpen]);

  if (!isOpen) return null;

  // Build the signing URL. Prefer the backend-provided signingUrl so we use
  // the correct origin (e.g. network IP instead of localhost). Fallback to
  // current window origin for backwards compatibility.
  const resolveUrl = (): string => {
    let baseUrl: string;

    if (_signingUrl && _signingUrl.includes('/contract-sign/')) {
      // Extract base URL from backend-stored signingUrl
      // signingUrl looks like: http://192.168.1.10:5174/#/contract-sign/TOKEN
      const idx = _signingUrl.indexOf('/contract-sign/');
      baseUrl = _signingUrl.substring(0, idx);
    } else {
      baseUrl = `${window.location.origin}/#`;
    }

    if (contractId) {
      return `${baseUrl}/contract-sign/${contractId}/${qrToken}`;
    }
    return `${baseUrl}/contract-sign/${qrToken}`;
  };

  const fullUrl = resolveUrl();

  const copyToClipboard = async (text: string): Promise<boolean> => {
    // Modern API (requires secure context — HTTPS or localhost)
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch {
        // fall through to fallback
      }
    }
    // Fallback for non-secure contexts (IP addresses over HTTP)
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px';
    textArea.style.top = '0';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
      const success = document.execCommand('copy');
      document.body.removeChild(textArea);
      return success;
    } catch {
      document.body.removeChild(textArea);
      return false;
    }
  };

  const handleCopy = async () => {
    const success = await copyToClipboard(fullUrl);
    if (success) {
      setCopied(true);
      showToast(t('contracts.linkCopied') || 'Link copied to clipboard', 'success');
      setTimeout(() => setCopied(false), 2000);
    } else {
      showToast(t('contracts.copyFailed') || 'Unable to copy link. Please try again later.', 'error');
    }
  };

  const handleShareWhatsApp = () => {
    const text = encodeURIComponent(
      `Please sign your rental contract (${contractNumber}) for ${clientName}: ${fullUrl}`
    );
    window.open(`https://wa.me/?text=${text}`, '_blank');
  };

  const handleShareSms = () => {
    const text = encodeURIComponent(
      `Please sign your rental contract: ${fullUrl}`
    );
    window.open(`sms:?body=${text}`, '_blank');
  };

  const handleShareEmail = () => {
    const subject = encodeURIComponent(`Rental Contract ${contractNumber} - Signature Required`);
    const body = encodeURIComponent(
      `Dear ${clientName},\n\nPlease sign your rental contract by clicking the link below:\n\n${fullUrl}\n\nThank you.`
    );
    window.open(`mailto:?subject=${subject}&body=${body}`, '_blank');
  };

  const handleDownloadQR = () => {
    const svg = qrRef.current?.querySelector('svg');
    if (!svg) return;

    const serializer = new XMLSerializer();
    const svgString = serializer.serializeToString(svg);
    const blob = new Blob([svgString], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);

    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 512;
      canvas.height = 512;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, 512, 512);
      ctx.drawImage(img, 32, 32, 448, 448);
      URL.revokeObjectURL(url);

      const png = canvas.toDataURL('image/png');
      const a = document.createElement('a');
      a.href = png;
      a.download = `contract-qr-${contractNumber}.png`;
      a.click();
      showToast(t('contracts.qrDownloaded') || 'QR code downloaded', 'success');
    };
    img.src = url;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative bg-white rounded-3xl shadow-2xl w-full max-w-md overflow-hidden animate-scale-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-lg font-bold text-[#1e293b]">
              {t('contracts.signingLink') || 'Contract Signing Link'}
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              {contractNumber} • {clientName}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all"
          >
            <X size={18} />
          </button>
        </div>

        {/* Signed success banner */}
        {isSigned && (
          <div className="mx-6 mt-4 flex items-center gap-3 rounded-2xl bg-success-50 border border-success-200 px-4 py-3">
            <CheckCircle2 size={22} className="shrink-0 text-success-500" />
            <div>
              <p className="text-sm font-semibold text-success-700">
                {t('contracts.contractSigned') || 'Contract Signed'}
              </p>
              {signedAt && (
                <p className="text-xs text-success-600 mt-0.5">
                  {new Date(signedAt).toLocaleString()}
                </p>
              )}
            </div>
          </div>
        )}

        <div className="p-6 space-y-6">
          {/* QR Code */}
          <div className="flex flex-col items-center gap-4">
            <div
              ref={qrRef}
              className="p-4 bg-white rounded-2xl border-2 border-slate-100 shadow-sm"
            >
              <QRCodeSVG
                value={fullUrl}
                size={200}
                level="H"
                includeMargin={false}
                imageSettings={qrLogoSrc ? {
                  src: qrLogoSrc,
                  height: 30,
                  width: 30,
                  excavate: true,
                } : undefined}
              />
            </div>
            <button
              onClick={handleDownloadQR}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-brand-500 hover:text-brand-600 hover:bg-brand-50 rounded-xl transition-all"
            >
              <Download size={15} />
              {t('contracts.downloadQR') || 'Download QR Code'}
            </button>
          </div>

          {/* URL Copy */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              {t('contracts.signingUrl') || 'Signing URL'}
            </label>
            <div className="flex items-center gap-2">
              <div className="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600 truncate">
                {fullUrl}
              </div>
              <button
                onClick={handleCopy}
                className={`p-3 rounded-xl transition-all ${
                  copied
                    ? 'bg-success-50 text-success-500'
                    : 'bg-brand-50 text-brand-500 hover:bg-brand-100'
                }`}
              >
                {copied ? <Check size={18} /> : <Copy size={18} />}
              </button>
            </div>
          </div>

          {/* Share Buttons */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              {t('contracts.shareVia') || 'Share via'}
            </label>
            <div className="grid grid-cols-3 gap-3">
              <button
                onClick={handleShareWhatsApp}
                className="flex flex-col items-center gap-2 p-3 bg-emerald-50 text-emerald-600 rounded-xl hover:bg-emerald-100 transition-all active:scale-95"
              >
                <MessageCircle size={22} />
                <span className="text-xs font-medium">WhatsApp</span>
              </button>
              <button
                onClick={handleShareSms}
                className="flex flex-col items-center gap-2 p-3 bg-blue-50 text-blue-600 rounded-xl hover:bg-blue-100 transition-all active:scale-95"
              >
                <Smartphone size={22} />
                <span className="text-xs font-medium">SMS</span>
              </button>
              <button
                onClick={handleShareEmail}
                className="flex flex-col items-center gap-2 p-3 bg-amber-50 text-amber-600 rounded-xl hover:bg-amber-100 transition-all active:scale-95"
              >
                <Mail size={22} />
                <span className="text-xs font-medium">Email</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
