import React, { useRef } from 'react';
import SignatureCanvas from 'react-signature-canvas';
import { RotateCcw, Check } from 'lucide-react';

interface SignaturePadProps {
  onSave: (signatureDataUrl: string) => void;
  onClear?: () => void;
  title?: string;
}

export default function SignaturePad({ onSave, onClear, title = "Sign here" }: SignaturePadProps) {
  const sigCanvas = useRef<SignatureCanvas>(null);

  const clear = () => {
    sigCanvas.current?.clear();
    if (onClear) onClear();
  };

  const save = () => {
    if (sigCanvas.current?.isEmpty()) {
      return;
    }
    const dataUrl = sigCanvas.current?.getTrimmedCanvas().toDataURL('image/png');
    if (dataUrl) {
      onSave(dataUrl);
    }
  };

  return (
    <div className="w-full space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
        <button
          onClick={clear}
          className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 transition-colors"
        >
          <RotateCcw size={14} />
          Clear
        </button>
      </div>
      
      <div className="relative w-full aspect-[2/1] bg-slate-50 border-2 border-dashed border-slate-200 rounded-2xl overflow-hidden group focus-within:border-brand-300 transition-all">
        <SignatureCanvas
          ref={sigCanvas}
          penColor="#0f172a"
          canvasProps={{
            className: "w-full h-full cursor-crosshair",
          }}
        />
        <div className="absolute bottom-3 right-3 pointer-events-none opacity-20 group-hover:opacity-40 transition-opacity">
          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-900">Digital Signature</span>
        </div>
      </div>

      <button
        onClick={save}
        className="w-full flex items-center justify-center gap-2 py-3.5 bg-brand-500 text-white rounded-xl font-bold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-[0.98] transition-all"
      >
        <Check size={18} />
        Confirm Signature
      </button>
    </div>
  );
}
