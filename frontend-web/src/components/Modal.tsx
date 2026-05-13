import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  maxWidth?: string;
}

export default function Modal({ isOpen, onClose, title, children, maxWidth = 'max-w-lg' }: ModalProps) {
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-[100] flex items-start justify-center p-4 pt-10"
      onClick={(e) => {
        if (e.target === overlayRef.current) onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm animate-fade"></div>
      <div className={`relative bg-white rounded-2xl shadow-2xl w-full ${maxWidth} max-h-[85vh] flex flex-col animate-fade overflow-hidden`}>
        <div className="flex items-center justify-between p-5 border-b border-[#e8e6e1] shrink-0">
          <h3 className="text-base font-bold text-[#1e293b]">{title}</h3>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-[#1e293b] hover:bg-[#f5f5f0] rounded-xl transition-all"
          >
            <X size={18} />
          </button>
        </div>
        <div className="p-5 overflow-y-auto" style={{ maxHeight: 'calc(85vh - 70px)' }}>
          {children}
        </div>
      </div>
    </div>
  );
}
