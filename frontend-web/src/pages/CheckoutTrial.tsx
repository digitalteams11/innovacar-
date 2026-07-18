import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Calendar, FileText, Users, Car, BarChart3, HeadphonesIcon,
  ArrowRight, Loader2, Lock, ShieldCheck, ChevronLeft, Mail, Building2, Sparkles,
} from 'lucide-react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';
import FeatureChecklist from '../components/checkout/FeatureChecklist';
import TrustBadges from '../components/checkout/TrustBadges';
import PriceSummaryCard from '../components/checkout/PriceSummaryCard';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';
const TRIAL_DAYS = 30;
const FALLBACK_MONTHLY_PRICE = 199;

interface PlanData {
  id: number;
  code: string;
  name: string;
  monthlyPrice: number;
  isFree?: boolean;
  checkoutConfigured?: boolean;
}

const FEATURES = [
  { icon: Calendar, label: 'Reservations management' },
  { icon: FileText, label: 'Contracts' },
  { icon: Users, label: 'Clients' },
  { icon: Car, label: 'Vehicles' },
  { icon: BarChart3, label: 'Reports' },
  { icon: HeadphonesIcon, label: 'Support' },
];

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function formatLongDate(date: Date): string {
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}

/**
 * Premium pre-checkout / trial-start page. Subscriptions are billed through
 * Whop's hosted checkout (see BillingTab.tsx) — there is no embedded card
 * form to redesign, so this page presents the offer, then hands off to
 * Whop's hosted page on CTA click (POST /billing/checkout -> redirect).
 */
export default function CheckoutTrial() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [plans, setPlans] = useState<PlanData[]>([]);
  const [loading, setLoading] = useState(true);
  const [checkoutLoading, setCheckoutLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.get('/billing/plans')
      .then(({ data }) => {
        if (cancelled) return;
        const raw = Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
        setPlans(raw);
      })
      .catch(() => { if (!cancelled) setPlans([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const plan = useMemo(() => {
    return (
      plans.find((p) => p.monthlyPrice === FALLBACK_MONTHLY_PRICE) ||
      plans.find((p) => !p.isFree) ||
      plans[0] ||
      null
    );
  }, [plans]);

  const monthlyPrice = plan?.monthlyPrice ?? FALLBACK_MONTHLY_PRICE;
  const billingStartDate = useMemo(() => formatLongDate(addDays(new Date(), TRIAL_DAYS)), []);

  const handleStartTrial = async () => {
    if (!plan) {
      showToast('No subscription plan is available right now. Please contact support.', 'error');
      return;
    }
    if (plan.checkoutConfigured === false) {
      showToast('Checkout is not configured for this plan yet. Please contact support.', 'error');
      return;
    }
    setCheckoutLoading(true);
    try {
      const { data } = await api.post('/billing/checkout', {
        planId: plan.id,
        billingCycle: 'MONTHLY',
      });
      if (data.success && data.checkoutUrl) {
        window.location.href = data.checkoutUrl;
      } else {
        showToast(data.message || 'Checkout link is missing for this plan. Please contact support.', 'error');
      }
    } catch (err: any) {
      showToast(err?.userMessage || err?.response?.data?.message || 'Failed to start checkout. Please try again.', 'error');
    } finally {
      setCheckoutLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen overflow-hidden bg-gradient-to-br from-[#f4f6fb] via-white to-[#eef2fb] px-4 py-8 sm:px-6 sm:py-12 lg:py-16">
      {/* Decorative ambient glow — purely visual, doesn't affect layout flow */}
      <div className="pointer-events-none absolute -left-32 -top-32 h-80 w-80 rounded-full bg-brand-200/30 blur-3xl" />
      <div className="pointer-events-none absolute -right-24 top-1/3 h-72 w-72 rounded-full bg-success-200/25 blur-3xl" />

      <SeoHead
        title="Start your free trial"
        description="Start your 30-day free trial of Innovacar — the all-in-one car rental management platform."
        canonical="https://innovacar.app/#/checkout"
        robots={ROBOTS_PRIVATE}
      />

      <div className="relative mx-auto w-full max-w-5xl">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="mb-6 flex items-center gap-1.5 text-sm font-semibold text-slate-500 transition-colors hover:text-brand-500"
        >
          <ChevronLeft size={16} /> Back
        </button>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-8">
          {/* ── Left column: offer / value ─────────────────────────────── */}
          <div className="order-1 animate-slide-up-fade rounded-[22px] border border-white/60 bg-white/80 p-6 shadow-[0_20px_60px_-24px_rgba(15,23,42,0.18)] backdrop-blur-sm sm:p-8 lg:rounded-[24px]">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-white shadow-md ring-1 ring-black/5">
                <img src={INNOVACAR_LOGO_URL} alt="Innovacar" className="h-full w-full object-contain p-1.5" />
              </div>
              <span className="text-base font-extrabold tracking-tight text-[#1e293b]">Innovacar</span>
            </div>

            <p className="mt-5 max-w-md text-sm leading-relaxed text-slate-500">
              The all-in-one platform to run your car rental agency — reservations, contracts, fleet and clients, in one place.
            </p>

            <div className="mt-7">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-success-50 px-3 py-1 text-xs font-bold uppercase tracking-wide text-success-600">
                <Sparkles size={12} strokeWidth={2.5} /> Free trial
              </span>
              <h1 className="mt-3 text-[2.5rem] font-black leading-none tracking-tight text-[#1e293b] sm:text-5xl">
                30 days free
              </h1>
              <p className="mt-2.5 text-sm font-semibold text-slate-500">
                Then MAD {monthlyPrice.toFixed(0)}/month starting on <span className="text-[#1e293b]">{billingStartDate}</span>
              </p>
            </div>

            <div className="mt-6">
              <PriceSummaryCard monthlyPrice={monthlyPrice} billingStartDate={billingStartDate} />
            </div>

            <div className="mt-7 border-t border-slate-100 pt-6">
              <p className="mb-4 text-xs font-bold uppercase tracking-wider text-slate-400">Everything you need, included</p>
              <FeatureChecklist features={FEATURES} />
            </div>

            <div className="mt-7 border-t border-slate-100 pt-5">
              <TrustBadges />
            </div>
          </div>

          {/* ── Right column: activation / CTA ─────────────────────────── */}
          <div className="order-2 animate-scale-in lg:sticky lg:top-10 lg:self-start">
            <div className="rounded-[22px] border border-white/60 bg-white p-6 shadow-[0_24px_70px_-24px_rgba(15,23,42,0.22)] sm:p-8 lg:rounded-[24px]">
              <h2 className="text-lg font-extrabold text-[#1e293b]">Activate your account</h2>
              <p className="mt-1 text-sm text-slate-500">Review your details, then start your free trial.</p>

              <div className="mt-6 space-y-2.5">
                <div className="flex items-center gap-3 rounded-2xl border border-slate-100 bg-[#f8f9fc] px-4 py-3.5">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-brand-500 shadow-sm ring-1 ring-black/5">
                    <Mail size={15} strokeWidth={2.25} />
                  </span>
                  <div className="min-w-0">
                    <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">Account</p>
                    <p className="truncate text-sm font-semibold text-[#1e293b]">{user?.email || '—'}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 rounded-2xl border border-slate-100 bg-[#f8f9fc] px-4 py-3.5">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-brand-500 shadow-sm ring-1 ring-black/5">
                    <Building2 size={15} strokeWidth={2.25} />
                  </span>
                  <div className="min-w-0">
                    <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">Agency</p>
                    <p className="truncate text-sm font-semibold text-[#1e293b]">{user?.tenantName || '—'}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between gap-3 rounded-2xl border border-slate-100 bg-[#f8f9fc] px-4 py-3.5">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white text-brand-500 shadow-sm ring-1 ring-black/5">
                      <Sparkles size={15} strokeWidth={2.25} />
                    </span>
                    <div className="min-w-0">
                      <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">Plan</p>
                      <p className="truncate text-sm font-semibold text-[#1e293b]">{plan?.name || 'Standard'}</p>
                    </div>
                  </div>
                  <span className="shrink-0 rounded-lg bg-brand-50 px-2.5 py-1 text-xs font-bold text-brand-600">
                    MAD {monthlyPrice.toFixed(0)}/mo
                  </span>
                </div>
              </div>

              <div className="my-6 flex items-center gap-3">
                <div className="h-px flex-1 bg-slate-100" />
                <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Secure checkout</span>
                <div className="h-px flex-1 bg-slate-100" />
              </div>

              <p className="mb-6 flex items-start gap-2 text-xs leading-relaxed text-slate-500">
                <Lock size={14} className="mt-0.5 shrink-0 text-slate-400" />
                You'll enter your payment details on the next, secure step. Your card is only saved — not charged — until your 30-day trial ends.
              </p>

              <button
                type="button"
                onClick={handleStartTrial}
                disabled={loading || checkoutLoading || !plan}
                className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-500 to-brand-600 py-4 text-base font-bold text-white shadow-[0_16px_36px_-12px_rgba(30,58,95,0.55)] transition-all hover:-translate-y-0.5 hover:shadow-glow-blue active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
              >
                {loading ? (
                  <Loader2 size={18} className="animate-spin" />
                ) : checkoutLoading ? (
                  <><Loader2 size={18} className="animate-spin" /> Redirecting…</>
                ) : (
                  <>Start Free Trial <ArrowRight size={18} strokeWidth={2.5} /></>
                )}
              </button>

              <div className="mt-4 flex flex-col items-center gap-1.5 text-center">
                <p className="text-xs font-semibold text-slate-500">You will not be charged today · Cancel anytime</p>
                <p className="flex items-center gap-1.5 text-[11px] font-medium text-slate-400">
                  <ShieldCheck size={13} className="text-success-500" /> Secure checkout — payments processed by Whop
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Sticky mobile CTA — keeps the primary action reachable without
          scrolling back up once the offer/feature list has been read. */}
      <div
        className="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/95 p-3 backdrop-blur-sm lg:hidden"
        style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}
      >
        <button
          type="button"
          onClick={handleStartTrial}
          disabled={loading || checkoutLoading || !plan}
          className="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-500 to-brand-600 py-3.5 text-sm font-bold text-white shadow-lg disabled:opacity-60"
        >
          {checkoutLoading ? <Loader2 size={16} className="animate-spin" /> : <>Start Free Trial <ArrowRight size={16} /></>}
        </button>
      </div>
      {/* Spacer so the sticky bar never overlaps page content on mobile */}
      <div className="h-20 lg:hidden" />
    </div>
  );
}
