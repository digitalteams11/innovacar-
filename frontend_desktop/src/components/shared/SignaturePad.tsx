import { useRef, useState, useEffect, useCallback } from 'react';
import SignatureCanvas from 'react-signature-canvas';
import { Check, RotateCcw, Pen, AlertCircle } from 'lucide-react';

interface SignaturePadProps {
  onSave: (signatureDataUrl: string) => void;
  onClear?: () => void;
  label?: string;
  penColor?: string;
  disabled?: boolean;
  showWatermark?: boolean;
  autoSaveKey?: string;
}

export default function SignaturePad({
  onSave,
  onClear,
  label = 'Sign here',
  penColor = '#0f172a',
  disabled = false,
  showWatermark = true,
  autoSaveKey,
}: SignaturePadProps) {
  const sigRef = useRef<SignatureCanvas>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [isEmpty, setIsEmpty] = useState(true);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [penThickness, setPenThickness] = useState(2);

  // Load draft from localStorage
  useEffect(() => {
    if (autoSaveKey && sigRef.current) {
      const draft = localStorage.getItem(`signature_draft_${autoSaveKey}`);
      if (draft) {
        sigRef.current.fromDataURL(draft);
        setIsEmpty(false);
      }
    }
  }, [autoSaveKey]);

  // Auto-save draft
  const autoSave = useCallback(() => {
    if (autoSaveKey && sigRef.current && !sigRef.current.isEmpty()) {
      const data = sigRef.current.toDataURL('image/png');
      localStorage.setItem(`signature_draft_${autoSaveKey}`, data);
    }
  }, [autoSaveKey]);

  const handleEnd = () => {
    setIsEmpty(false);
    setValidationError(null);
    autoSave();
  };

  const clear = () => {
    sigRef.current?.clear();
    setIsEmpty(true);
    setValidationError(null);
    if (autoSaveKey) {
      localStorage.removeItem(`signature_draft_${autoSaveKey}`);
    }
    onClear?.();
  };

  const validateSignature = (): boolean => {
    if (!sigRef.current || sigRef.current.isEmpty()) {
      setValidationError('Please provide a signature');
      return false;
    }
    setValidationError(null);
    return true;
  };

  const save = () => {
    if (disabled) return;
    if (!validateSignature()) return;

    try {
      const dataUrl = sigRef.current!.toDataURL('image/png');
      onSave(dataUrl);
      if (autoSaveKey) {
        localStorage.removeItem(`signature_draft_${autoSaveKey}`);
      }
    } catch (e) {
      console.error('Failed to save signature:', e);
      setValidationError('Failed to capture signature. Please try again.');
    }
  };

  // Prevent scrolling while signing on mobile
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const preventScroll = (e: TouchEvent) => {
      e.preventDefault();
    };

    el.addEventListener('touchmove', preventScroll, { passive: false });
    return () => el.removeEventListener('touchmove', preventScroll);
  }, []);

  return (
    <div className="w-full space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Pen size={14} className="text-brand-500" />
          <span className="text-sm font-semibold text-slate-700">{label}</span>
        </div>
        <div className="flex items-center gap-2">
          {/* Pen thickness selector */}
          <div className="flex items-center gap-1 bg-slate-100 rounded-lg p-0.5">
            {[1, 2, 3].map((t) => (
              <button
                key={t}
                onClick={() => setPenThickness(t)}
                className={`w-6 h-6 rounded-md flex items-center justify-center transition-all ${
                  penThickness === t
                    ? 'bg-white shadow-sm text-brand-500'
                    : 'text-slate-400 hover:text-slate-600'
                }`}
                title={`Pen thickness ${t}`}
              >
                <div
                  className="rounded-full bg-current"
                  style={{ width: t * 2 + 2, height: t * 2 + 2 }}
                />
              </button>
            ))}
          </div>
          <button
            onClick={clear}
            disabled={isEmpty || disabled}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-all disabled:opacity-40"
          >
            <RotateCcw size={13} />
            Clear
          </button>
        </div>
      </div>

      <div
        ref={containerRef}
        className={`relative bg-white border-2 border-dashed rounded-2xl overflow-hidden transition-all ${
          validationError
            ? 'border-danger-300 bg-danger-50/30'
            : disabled
            ? 'border-slate-200 bg-slate-50'
            : 'border-slate-200 hover:border-brand-300 focus-within:border-brand-400'
        }`}
        style={{ height: '200px' }}
      >
        <SignatureCanvas
          ref={sigRef}
          penColor={disabled ? '#94a3b8' : penColor}
          minWidth={penThickness * 0.5}
          maxWidth={penThickness * 1.5}
          canvasProps={{
            className: 'w-full h-full touch-none cursor-crosshair',
            style: { width: '100%', height: '100%' },
          }}
          onEnd={handleEnd}
          backgroundColor="rgba(0,0,0,0)"
        />

        {isEmpty && !disabled && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <span className="text-slate-300 text-sm font-medium tracking-wide select-none">
              {label}
            </span>
          </div>
        )}

        {showWatermark && !isEmpty && (
          <div className="absolute bottom-2 right-2 pointer-events-none opacity-10">
            <span className="text-[9px] font-bold uppercase tracking-widest text-slate-900">
              Digital Signature
            </span>
          </div>
        )}

        {disabled && (
          <div className="absolute inset-0 bg-white/60 backdrop-blur-[1px] flex items-center justify-center">
            <span className="text-xs font-medium text-slate-400">Signature locked</span>
          </div>
        )}
      </div>

      {validationError && (
        <div className="flex items-center gap-1.5 text-danger-500 text-xs">
          <AlertCircle size={13} />
          <span>{validationError}</span>
        </div>
      )}

      <button
        onClick={save}
        disabled={isEmpty || disabled}
        className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl font-semibold text-sm transition-all active:scale-[0.98] ${
          isEmpty || disabled
            ? 'bg-slate-100 text-slate-300 cursor-not-allowed'
            : 'bg-brand-500 text-white hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10'
        }`}
      >
        <Check size={17} />
        Confirm Signature
      </button>
    </div>
  );
}
