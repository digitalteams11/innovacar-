import { useState } from 'react';
import { motion } from 'framer-motion';
import { cn } from '../lib/utils';
import { type LucideIcon } from 'lucide-react';

interface Tab {
  id: string;
  label: string;
  icon?: LucideIcon;
  content: React.ReactNode;
  badge?: number;
}

interface GlassTabsProps {
  tabs: Tab[];
  defaultTab?: string;
  className?: string;
  tabListClassName?: string;
  contentClassName?: string;
}

export function GlassTabs({
  tabs,
  defaultTab,
  className,
  tabListClassName,
  contentClassName,
}: GlassTabsProps) {
  const [activeTab, setActiveTab] = useState(defaultTab || tabs[0]?.id);

  const activeContent = tabs.find((t) => t.id === activeTab)?.content;

  return (
    <div className={cn('space-y-4', className)}>
      {/* Tab List */}
      <div
        className={cn(
          'flex items-center gap-1 p-1 rounded-2xl overflow-x-auto no-scrollbar',
          tabListClassName
        )}
        style={{
          backgroundColor: 'var(--bg-card)',
          border: '1px solid var(--border-subtle)',
        }}
      >
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                'relative flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-colors whitespace-nowrap',
                isActive ? 'text-white' : 'hover:text-[var(--text-primary)]'
              )}
              style={{
                color: isActive ? 'white' : 'var(--text-muted)',
              }}
            >
              {isActive && (
                <motion.div
                  layoutId="activeTab"
                  className="absolute inset-0 rounded-xl"
                  style={{
                    background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)',
                  }}
                  transition={{ type: 'spring', bounce: 0.2, duration: 0.5 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-2">
                {Icon && <Icon size={16} />}
                {tab.label}
                {tab.badge !== undefined && tab.badge > 0 && (
                  <span
                    className="ml-1 px-1.5 py-0.5 rounded-md text-[10px] font-bold"
                    style={{
                      backgroundColor: isActive ? 'rgba(255,255,255,0.2)' : 'var(--bg-hover)',
                      color: isActive ? 'white' : 'var(--text-secondary)',
                    }}
                  >
                    {tab.badge}
                  </span>
                )}
              </span>
            </button>
          );
        })}
      </div>

      {/* Tab Content */}
      <motion.div
        key={activeTab}
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
        className={contentClassName}
      >
        {activeContent}
      </motion.div>
    </div>
  );
}
