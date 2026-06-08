import { useEffect, useState } from 'react';
import { Calculator, Tag, Truck, Shield, Clock, Percent, Wallet } from 'lucide-react';

interface LivePriceSidebarProps {
  vehiclePrice: number;
  dailyPrice: number;
  startDate: string;
  endDate: string;
  insuranceFees?: number;
  deliveryFees?: number;
  extraHours?: number;
  discountAmount?: number;
  discountPercent?: number;
}

export default function LivePriceSidebar({
  vehiclePrice: _vehiclePrice,
  dailyPrice,
  startDate,
  endDate,
  insuranceFees = 0,
  deliveryFees = 0,
  extraHours = 0,
  discountAmount = 0,
  discountPercent = 0,
}: LivePriceSidebarProps) {
  const [calculation, setCalculation] = useState({
    days: 0,
    basePrice: 0,
    insurance: 0,
    delivery: 0,
    extraHoursCost: 0,
    subtotal: 0,
    discount: 0,
    tax: 0,
    total: 0,
  });

  useEffect(() => {
    if (!startDate || !endDate || !dailyPrice) {
      setCalculation({ days: 0, basePrice: 0, insurance: 0, delivery: 0, extraHoursCost: 0, subtotal: 0, discount: 0, tax: 0, total: 0 });
      return;
    }

    const start = new Date(startDate);
    const end = new Date(endDate);
    const diffTime = end.getTime() - start.getTime();
    const days = Math.max(1, Math.ceil(diffTime / (1000 * 60 * 60 * 24)));

    const basePrice = dailyPrice * days;
    const insurance = insuranceFees * days;
    const delivery = deliveryFees;
    const extraHoursCost = extraHours > 0 ? (dailyPrice / 4) * extraHours : 0;
    const subtotal = basePrice + insurance + delivery + extraHoursCost;

    let discount = 0;
    if (discountAmount > 0) {
      discount = discountAmount;
    } else if (discountPercent > 0) {
      discount = subtotal * (discountPercent / 100);
    }
    discount = Math.min(discount, subtotal);

    const afterDiscount = subtotal - discount;
    const tax = afterDiscount * 0.20; // 20% VAT
    const total = afterDiscount + tax;

    setCalculation({
      days,
      basePrice: Math.round(basePrice * 100) / 100,
      insurance: Math.round(insurance * 100) / 100,
      delivery: Math.round(delivery * 100) / 100,
      extraHoursCost: Math.round(extraHoursCost * 100) / 100,
      subtotal: Math.round(subtotal * 100) / 100,
      discount: Math.round(discount * 100) / 100,
      tax: Math.round(tax * 100) / 100,
      total: Math.round(total * 100) / 100,
    });
  }, [dailyPrice, startDate, endDate, insuranceFees, deliveryFees, extraHours, discountAmount, discountPercent]);

  if (!startDate || !endDate) {
    return (
      <div className="bg-slate-50 rounded-2xl p-5 border border-slate-100">
        <div className="flex items-center gap-2 text-slate-400">
          <Calculator size={16} />
          <span className="text-sm">Select dates to see pricing</span>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm space-y-4">
      <div className="flex items-center gap-2 text-brand-500">
        <Calculator size={14} />
        <span className="text-xs font-bold uppercase tracking-wider">Live Pricing</span>
      </div>

      <div className="space-y-2.5">
        <PriceRow icon={<Tag size={12} />} label={`Base Price (${calculation.days} days)`} value={calculation.basePrice} />
        <PriceRow icon={<Shield size={12} />} label="Insurance" value={calculation.insurance} />
        <PriceRow icon={<Truck size={12} />} label="Delivery" value={calculation.delivery} />
        {calculation.extraHoursCost > 0 && (
          <PriceRow icon={<Clock size={12} />} label="Extra Hours" value={calculation.extraHoursCost} />
        )}

        <div className="h-px bg-slate-100" />

        <div className="flex justify-between text-sm">
          <span className="text-slate-500 font-medium">Subtotal</span>
          <span className="font-bold text-[#1e293b]">{calculation.subtotal} MAD</span>
        </div>

        {calculation.discount > 0 && (
          <div className="flex justify-between text-sm">
            <span className="text-success-500 font-medium flex items-center gap-1">
              <Percent size={12} /> Discount
            </span>
            <span className="font-bold text-success-500">-{calculation.discount} MAD</span>
          </div>
        )}

        <div className="flex justify-between text-sm">
          <span className="text-slate-500 font-medium">Tax (20%)</span>
          <span className="font-bold text-[#1e293b]">{calculation.tax} MAD</span>
        </div>

        <div className="h-px bg-slate-100" />

        <div className="flex justify-between items-baseline">
          <span className="text-sm font-bold text-[#1e293b]">Total</span>
          <span className="text-2xl font-black text-brand-500">{calculation.total} <span className="text-sm font-medium">MAD</span></span>
        </div>
      </div>

      <div className="flex items-center gap-2 p-3 bg-brand-50 rounded-xl">
        <Wallet size={14} className="text-brand-500" />
        <span className="text-xs text-brand-600 font-medium">
          {calculation.days} days × {dailyPrice} MAD/day
        </span>
      </div>
    </div>
  );
}

function PriceRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: number }) {
  if (value === 0) return null;
  return (
    <div className="flex justify-between text-sm">
      <span className="text-slate-400 flex items-center gap-1.5">{icon} {label}</span>
      <span className="font-medium text-slate-600">{value} MAD</span>
    </div>
  );
}
