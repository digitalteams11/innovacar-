import { useRef, useState } from 'react';
import SignatureCanvas from 'react-signature-canvas';
import { Eraser, Check, RotateCcw } from 'lucide-react';

interface SignaturePadProps {
  onSave: (signatureDataUrl: string) => void;
  label?: string;
}

export default function SignaturePad({ onSave, label = 'Sign here' }: SignaturePadProps) {
  const sigRef = useRef<SignatureCanvas>(null);
  const [isEmpty, setIsEmpty] = useState(true);

  const clear = () => {
    sigRef.current?.clear();
    setIsEmpty(true);
  };

  const handleEnd = () => {
    setIsEmpty(false);
  };

  const save = () => {
    if (sigRef.current && !sigRef.current.isEmpty()) {
      const dataUrl = sigRef.current.getTrimmedCanvas().toDataURL('image/png');
      onSave(dataUrl);
    }
  };

  return (
    <div className="space-y-3">
      <p className="text-sm font-medium text-[#1e293b]">{label}</p>
      <div className="relative bg-white border-2 border-dashed border-[#e8e6e1] rounded-2xl overflow-hidden">
        <SignatureCanvas
          ref={sigRef}
          penColor="#1e3a5f"
          canvasProps={{
            className: 'w-full h-40 md:h-56 touch-none cursor-crosshair',
            style: { width: '100%', height: '100%' },
          }}
          onEnd={handleEnd}
          backgroundColor="rgba(0,0,0,0)"
        />
        {isEmpty && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <span className="text-slate-300 text-sm font-medium tracking-wide">{label}</span>
          </div>
        )}
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={clear}
          className="flex items-center gap-2 px-4 py-2 bg-[#f5f5f0] text-slate-500 rounded-xl text-sm font-medium hover:bg-[#ebe9e4] active:scale-95 transition-all"
        >
          <RotateCcw size={15} />
          Clear
        </button>
        <button
          onClick={save}
          disabled={isEmpty}
          className={`flex items-center gap-2 px-5 py-2 rounded-xl text-sm font-medium transition-all active:scale-95 flex-1 justify-center ${
            isEmpty
              ? 'bg-slate-100 text-slate-300 cursor-not-allowed'
              : 'bg-brand-500 text-white hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10'
          }`}
        >
          <Check size={15} />
          Confirm Signature
        </button>
      </div>
    </div>
  );
}
