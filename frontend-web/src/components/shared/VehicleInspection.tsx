import { useState } from 'react';
import { AlertTriangle, Check } from 'lucide-react';

interface DamageZone {
  id: string;
  label: string;
  damaged: boolean;
  notes: string;
}

interface VehicleInspectionProps {
  value: DamageZone[];
  onChange: (zones: DamageZone[]) => void;
  readOnly?: boolean;
}

const DEFAULT_ZONES: DamageZone[] = [
  { id: 'front', label: 'Front', damaged: false, notes: '' },
  { id: 'rear', label: 'Rear', damaged: false, notes: '' },
  { id: 'leftSide', label: 'Left Side', damaged: false, notes: '' },
  { id: 'rightSide', label: 'Right Side', damaged: false, notes: '' },
  { id: 'windshield', label: 'Windshield', damaged: false, notes: '' },
  { id: 'interior', label: 'Interior', damaged: false, notes: '' },
  { id: 'roof', label: 'Roof', damaged: false, notes: '' },
  { id: 'bumperFront', label: 'Front Bumper', damaged: false, notes: '' },
  { id: 'bumperRear', label: 'Rear Bumper', damaged: false, notes: '' },
  { id: 'hood', label: 'Hood', damaged: false, notes: '' },
  { id: 'trunk', label: 'Trunk', damaged: false, notes: '' },
];

export default function VehicleInspection({ value, onChange, readOnly = false }: VehicleInspectionProps) {
  const zones = value.length > 0 ? value : DEFAULT_ZONES;
  const [, setSelectedZone] = useState<string | null>(null);

  const toggleZone = (zoneId: string) => {
    if (readOnly) return;
    const updated = zones.map((z) =>
      z.id === zoneId ? { ...z, damaged: !z.damaged } : z
    );
    onChange(updated);
    setSelectedZone(zoneId);
  };

  const updateNotes = (zoneId: string, notes: string) => {
    if (readOnly) return;
    const updated = zones.map((z) =>
      z.id === zoneId ? { ...z, notes } : z
    );
    onChange(updated);
  };

  const getZone = (id: string) => zones.find((z) => z.id === id);
  const isDamaged = (id: string) => getZone(id)?.damaged || false;

  const damageColor = (id: string) =>
    isDamaged(id) ? '#ef4444' : '#e2e8f0';

  const damageFill = (id: string) =>
    isDamaged(id) ? '#fef2f2' : '#f8fafc';

  return (
    <div className="space-y-4">
      {/* SVG Car Diagram */}
      <div className="relative bg-white rounded-2xl border border-slate-200 p-4 overflow-hidden">
        <p className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
          Click zones to mark damage
        </p>
        <svg viewBox="0 0 320 520" className="w-full max-w-[280px] mx-auto">
          {/* Roof */}
          <path
            d="M110 160 L210 160 L200 140 L120 140 Z"
            fill={damageFill('roof')}
            stroke={damageColor('roof')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('roof')}
          />
          {isDamaged('roof') && (
            <text x="160" y="155" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Hood */}
          <path
            d="M100 260 L220 260 L210 200 L110 200 Z"
            fill={damageFill('hood')}
            stroke={damageColor('hood')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('hood')}
          />
          {isDamaged('hood') && (
            <text x="160" y="235" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Front Bumper */}
          <path
            d="M90 320 L230 320 L220 260 L100 260 Z"
            fill={damageFill('bumperFront')}
            stroke={damageColor('bumperFront')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('bumperFront')}
          />
          {isDamaged('bumperFront') && (
            <text x="160" y="295" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Front */}
          <path
            d="M80 360 L240 360 L230 320 L90 320 Z"
            fill={damageFill('front')}
            stroke={damageColor('front')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('front')}
          />
          {isDamaged('front') && (
            <text x="160" y="345" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Windshield */}
          <path
            d="M115 195 L205 195 L200 165 L120 165 Z"
            fill={damageFill('windshield')}
            stroke={damageColor('windshield')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('windshield')}
          />
          {isDamaged('windshield') && (
            <text x="160" y="185" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Left Side */}
          <rect
            x="40" y="140" width="70" height="220" rx="8"
            fill={damageFill('leftSide')}
            stroke={damageColor('leftSide')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('leftSide')}
          />
          {isDamaged('leftSide') && (
            <text x="75" y="255" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Right Side */}
          <rect
            x="210" y="140" width="70" height="220" rx="8"
            fill={damageFill('rightSide')}
            stroke={damageColor('rightSide')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('rightSide')}
          />
          {isDamaged('rightSide') && (
            <text x="245" y="255" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Trunk */}
          <path
            d="M100 120 L220 120 L210 80 L110 80 Z"
            fill={damageFill('trunk')}
            stroke={damageColor('trunk')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('trunk')}
          />
          {isDamaged('trunk') && (
            <text x="160" y="105" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Rear Bumper */}
          <path
            d="M90 80 L230 80 L220 40 L100 40 Z"
            fill={damageFill('bumperRear')}
            stroke={damageColor('bumperRear')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('bumperRear')}
          />
          {isDamaged('bumperRear') && (
            <text x="160" y="65" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Rear */}
          <path
            d="M80 40 L240 40 L230 0 L90 0 Z"
            fill={damageFill('rear')}
            stroke={damageColor('rear')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('rear')}
          />
          {isDamaged('rear') && (
            <text x="160" y="25" textAnchor="middle" fill="#ef4444" fontSize="14">⚠</text>
          )}

          {/* Interior - represented as a panel below */}
          <rect
            x="110" y="380" width="100" height="60" rx="8"
            fill={damageFill('interior')}
            stroke={damageColor('interior')}
            strokeWidth="2"
            className={readOnly ? '' : 'cursor-pointer hover:opacity-80 transition-all'}
            onClick={() => toggleZone('interior')}
          />
          <text x="160" y="415" textAnchor="middle" fill={isDamaged('interior') ? '#ef4444' : '#94a3b8'} fontSize="11" fontWeight="bold">
            INTERIOR
          </text>
          {isDamaged('interior') && (
            <text x="160" y="430" textAnchor="middle" fill="#ef4444" fontSize="12">⚠</text>
          )}

          {/* Labels */}
          <text x="160" y="475" textAnchor="middle" fill="#64748b" fontSize="10" fontWeight="bold">FRONT →</text>
          <text x="160" y="495" textAnchor="middle" fill="#64748b" fontSize="10">(Click zones to toggle damage)</text>
        </svg>
      </div>

      {/* Zone Details */}
      <div className="space-y-2">
        {zones.map((zone) => (
          <div
            key={zone.id}
            className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${
              zone.damaged
                ? 'bg-danger-50 border-danger-200'
                : 'bg-white border-slate-100 hover:border-slate-200'
            }`}
          >
            <button
              onClick={() => toggleZone(zone.id)}
              disabled={readOnly}
              className={`w-6 h-6 rounded-md flex items-center justify-center transition-all ${
                zone.damaged
                  ? 'bg-danger-500 text-white'
                  : 'bg-slate-100 text-slate-300 hover:bg-slate-200'
              }`}
            >
              {zone.damaged ? <AlertTriangle size={14} /> : <Check size={14} />}
            </button>
            <span className={`text-sm font-medium flex-1 ${zone.damaged ? 'text-danger-600' : 'text-slate-600'}`}>
              {zone.label}
            </span>
            {zone.damaged && !readOnly && (
              <input
                type="text"
                placeholder="Damage description..."
                value={zone.notes}
                onChange={(e) => updateNotes(zone.id, e.target.value)}
                className="flex-1 max-w-[200px] px-3 py-1.5 bg-white border border-danger-200 rounded-lg text-xs text-slate-600 focus:outline-none focus:ring-1 ring-danger-300"
              />
            )}
            {zone.damaged && readOnly && zone.notes && (
              <span className="text-xs text-danger-500">{zone.notes}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
