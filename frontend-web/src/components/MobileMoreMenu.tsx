import { Link } from 'react-router-dom';
import { LockKeyhole } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '../lib/utils';
import MobileBottomSheet from './MobileBottomSheet';

export interface MoreMenuItem {
  to: string;
  icon: LucideIcon;
  label: string;
}

interface MobileMoreMenuProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  items: MoreMenuItem[];
  isActive: (to: string) => boolean;
  isLocked: (item: MoreMenuItem) => boolean;
}

/** Mobile "More" navigation sheet — every sidebar item that isn't one of the 4 primary bottom-nav routes. */
export default function MobileMoreMenu({ isOpen, onClose, title, items, isActive, isLocked }: MobileMoreMenuProps) {
  return (
    <MobileBottomSheet isOpen={isOpen} onClose={onClose} title={title}>
      <div className="grid grid-cols-3 gap-1 overflow-y-auto p-2" style={{ maxHeight: 'calc(62vh - 48px)' }}>
        {items.map((item) => {
          const active = isActive(item.to);
          const locked = isLocked(item);
          return (
            <Link
              key={item.to}
              to={item.to}
              onClick={onClose}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'relative min-h-[76px] p-2 rounded-lg flex flex-col items-center justify-center gap-2 text-center text-[10px]',
                active ? 'bg-[var(--bg-active)] text-[var(--brand-primary)]' : 'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]',
              )}
            >
              <item.icon size={19} />
              <span>{item.label}</span>
              {locked && <LockKeyhole size={10} className="absolute top-2 end-2" />}
            </Link>
          );
        })}
      </div>
    </MobileBottomSheet>
  );
}
