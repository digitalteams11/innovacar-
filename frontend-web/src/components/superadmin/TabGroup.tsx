

interface Tab {
  id: string;
  label: string;
  count?: number;
}

interface TabGroupProps {
  tabs: Tab[];
  activeTab: string;
  onChange: (tabId: string) => void;
}

export default function TabGroup({ tabs, activeTab, onChange }: TabGroupProps) {
  return (
    <div className="flex items-center gap-1 bg-white dark:bg-[#1a2332]/70 p-1 rounded-xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft w-fit">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-all ${
            activeTab === tab.id
              ? 'bg-[#0a0f2c] dark:bg-white/10 text-white dark:text-white shadow-sm'
              : 'text-slate-500 dark:text-slate-400 hover:text-[#1e293b] dark:hover:text-white hover:bg-slate-50 dark:hover:bg-white/5'
          }`}
        >
          {tab.label}
          {tab.count !== undefined && tab.count > 0 && (
            <span className={`ml-1.5 px-1.5 py-0.5 rounded-md text-[10px] font-bold ${
              activeTab === tab.id ? 'bg-white/20 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
            }`}>
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
