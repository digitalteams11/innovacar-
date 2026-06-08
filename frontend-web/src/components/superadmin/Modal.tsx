import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
}

const sizeClasses = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
  full: 'max-w-[95vw]',
};

export default function Modal({ isOpen, onClose, title, children, footer, size = 'md' }: ModalProps) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => { document.body.style.overflow = ''; };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className={`relative bg-white dark:bg-[#1a2332] rounded-2xl shadow-elevated w-full ${sizeClasses[size]} max-h-[90vh] flex flex-col`}>
        <div className="sticky top-0 bg-white dark:bg-[#1a2332] px-6 py-4 border-b border-[#e8e6e1]/60 dark:border-white/5 flex items-center justify-between z-10 rounded-t-2xl">
          <h2 className="text-lg font-bold text-[#1e293b] dark:text-white">{title}</h2>
          <button onClick={onClose} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors">
            <X size={18} className="text-slate-400" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6">
          {children}
        </div>
        {footer && (
          <div className="sticky bottom-0 bg-white dark:bg-[#1a2332] px-6 py-4 border-t border-[#e8e6e1]/60 dark:border-white/5 rounded-b-2xl z-10">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
