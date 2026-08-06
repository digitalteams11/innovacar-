import { createContext, useContext, useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';

/**
 * The public marketing site. Deliberately self-contained (no imports from
 * outside this file besides `react`) because scripts/prerender-marketing.mjs
 * transforms and imports this exact file at build time, outside the normal
 * Vite graph, to render it to static HTML for crawlers. Keep it that way —
 * do not import app components (AuthContext, i18n, etc.) here.
 *
 * The homepage ("/") is the full product landing page (hero, product demo,
 * features, how-it-works, web/desktop, pricing, trial, trust, FAQ, contact)
 * — the header's nav items scroll to sections on this one page rather than
 * linking to separate routes, per the redesign brief. "/fonctionnalites" and
 * "/tarifs" remain as focused, separately-indexable deep-dive pages and stay
 * in the sitemap.
 *
 * i18n: this file cannot depend on react-i18next (see constraint above), so
 * it ships a small self-contained FR/EN/AR dictionary + LangProvider context
 * below. FR is the crawler-facing prerendered language (Morocco default);
 * EN/AR are a client-side-only enhancement switched after mount, matching
 * the "brief content replace on load is acceptable for this MVP" precedent
 * already used for meta tags in prerender-marketing.mjs. Do not add hreflang
 * tags — there are no separate alternate-language URLs, only an in-page
 * language switch.
 *
 * This module mixes component and non-component exports (MARKETING_PAGES)
 * by design — splitting it would break the prerender script's single-file
 * assumption above — so fast refresh doesn't apply here; a full reload on
 * edit is an acceptable dev-time cost for this rarely-touched file.
 */
/* eslint-disable react-refresh/only-export-components */

export interface MarketingPageMeta {
  /** Path relative to the canonical origin, e.g. "/fonctionnalites". */
  path: string;
  title: string;
  description: string;
}

// ─────────────────────────────────────────────────────────────────────────
// Config — read from Vite env when this file runs through the normal client
// bundle. During scripts/prerender-marketing.mjs (plain Node, transformed
// with oxc outside Vite) `import.meta.env` doesn't exist, so every read is
// optional-chained with a safe fallback; the live client re-renders over the
// static HTML immediately after mount with the real values (same tradeoff
// already accepted for meta tags in that script).
//
// Each key below is read through its own literal `import.meta.env.VITE_*`
// expression (matching src/lib/api.ts / src/lib/publicUrl.ts) rather than
// grabbing the whole `import.meta.env` object once and indexing into it
// dynamically. Vite only replaces a *specific* `import.meta.env.KEY`
// expression with that key's value — grabbing the bare object instead makes
// Vite inline the full resolved env, including every other VITE_*-prefixed
// variable present at build time. That previously shipped Vercel's
// auto-injected system vars (VITE_VERCEL_URL, VITE_VERCEL_BRANCH_URL,
// VITE_VERCEL_PROJECT_PRODUCTION_URL — which legitimately contain a
// *.vercel.app host) into this bundle even though this file never reads
// those keys.
// ─────────────────────────────────────────────────────────────────────────
type EnvBag = Record<string, string | boolean | undefined>;
const ENV: EnvBag = {
  VITE_INNOVAX_WEBSITE_URL: import.meta.env?.VITE_INNOVAX_WEBSITE_URL,
  VITE_COMPANY_NAME: import.meta.env?.VITE_COMPANY_NAME,
  VITE_TRIAL_DURATION_DAYS: import.meta.env?.VITE_TRIAL_DURATION_DAYS,
  VITE_TRIAL_PROMO_ENABLED: import.meta.env?.VITE_TRIAL_PROMO_ENABLED,
  VITE_TRIAL_LABEL: import.meta.env?.VITE_TRIAL_LABEL,
  VITE_DESKTOP_PLATFORM: import.meta.env?.VITE_DESKTOP_PLATFORM,
  VITE_API_URL: import.meta.env?.VITE_API_URL,
  VITE_CONTACT_EMAIL: import.meta.env?.VITE_CONTACT_EMAIL,
  VITE_CONTACT_WHATSAPP: import.meta.env?.VITE_CONTACT_WHATSAPP,
};

// Blanket guard applied to EVERY env value this file reads, not just the
// ones that are obviously URLs (VITE_CONTACT_EMAIL, VITE_TRIAL_LABEL, etc.
// are free text an operator fills in, and any of them could just as easily
// have a leftover Vercel preview URL pasted into it as VITE_*_URL/DOWNLOAD
// fields do). Only vercel.app is stripped here — NOT localhost/192.168.*,
// so a legitimate LAN address in another *_URL field isn't blanket-rejected.
// A rejected value renders as unconfigured/empty rather than shipping the
// literal string into the production bundle.
const UNSAFE_VALUE_PATTERN = /vercel\.app/i;
function envStr(key: string): string {
  const v = ENV[key];
  if (typeof v !== 'string') return '';
  const trimmed = v.trim();
  return UNSAFE_VALUE_PATTERN.test(trimmed) ? '' : trimmed;
}
function envBool(key: string): boolean {
  const v = ENV[key];
  return v === true || v === 'true' || v === '1';
}

// Public marketing links (the real Innovax website, the desktop installer)
// must additionally be a real https:// URL that isn't a LAN/loopback
// address — there's no legitimate reason for either of these to ever be a
// local/internal host.
const UNSAFE_PUBLIC_HOST_PATTERN = /localhost|127\.0\.0\.1|^192\.168\./i;
function envSafeUrl(key: string): string {
  const value = envStr(key);
  if (!value) return '';
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== 'https:') return '';
    if (UNSAFE_PUBLIC_HOST_PATTERN.test(parsed.hostname)) return '';
    return value;
  } catch {
    return '';
  }
}

const INNOVAX_URL = envSafeUrl('VITE_INNOVAX_WEBSITE_URL');
const COMPANY_NAME = envStr('VITE_COMPANY_NAME') || 'Innovax Technologies';
const TRIAL_DAYS = envStr('VITE_TRIAL_DURATION_DAYS');
const TRIAL_PROMO_ENABLED = envBool('VITE_TRIAL_PROMO_ENABLED');
const TRIAL_LABEL_OVERRIDE = envStr('VITE_TRIAL_LABEL');
const DESKTOP_PLATFORM = envStr('VITE_DESKTOP_PLATFORM') || 'Windows';
// Desktop availability/download URL are no longer env-driven — they come
// live from the backend-managed release (GET /api/public/desktop/releases/latest),
// the single source of truth shared with the authenticated Desktop App page
// and Super Admin's release manager. See useDesktopReleaseLive() below.
const API_ORIGIN = (() => {
  const configured = envStr('VITE_API_URL').replace(/\/api\/?$/, '').replace(/\/+$/, '');
  return configured || 'https://api.innovacar.app';
})();

interface LiveDesktopRelease {
  loading: boolean;
  available: boolean;
  releaseId?: number;
  version?: string;
  downloadUrl?: string;
  fileName?: string;
  fileSizeBytes?: number;
  releaseDate?: string;
  sha256?: string;
  minimumOs?: string;
  releaseNotes?: { en: string[]; fr: string[]; ar: string[] };
}

/** Live release metadata for the homepage's Web & Desktop section, the FAQ, and the /desktop page — never a hardcoded URL. */
function useDesktopReleaseLive(): LiveDesktopRelease {
  const [state, setState] = useState<LiveDesktopRelease>({ loading: true, available: false });
  useEffect(() => {
    if (!isBrowser()) return;
    let cancelled = false;
    fetch(`${API_ORIGIN}/api/public/desktop/releases/latest?platform=WINDOWS&arch=X64`)
      .then((res) => res.json())
      .then((data) => { if (!cancelled) setState({ loading: false, ...data }); })
      .catch(() => { if (!cancelled) setState({ loading: false, available: false }); });
    return () => { cancelled = true; };
  }, []);
  return state;
}

function recordDesktopDownload(releaseId: number | undefined, source: 'LANDING' | 'DESKTOP_PAGE') {
  if (!isBrowser()) return;
  fetch(`${API_ORIGIN}/api/public/desktop/downloads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ releaseId, source, status: 'STARTED' }),
  }).catch(() => { /* analytics is best-effort, never blocks the download */ });
}
const CONTACT_EMAIL = envStr('VITE_CONTACT_EMAIL');
const CONTACT_WHATSAPP_DIGITS = envStr('VITE_CONTACT_WHATSAPP').replace(/[^\d]/g, '');

/** True once this module runs in a real browser (never during SSR/prerender). */
function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

function scrollToId(id: string) {
  if (!isBrowser()) return;
  if (window.location.pathname !== '/') {
    window.location.href = `/?section=${id}`;
    return;
  }
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ─────────────────────────────────────────────────────────────────────────
// i18n — small self-contained dictionary (see file header for why).
// ─────────────────────────────────────────────────────────────────────────
type Lang = 'fr' | 'en' | 'ar';
type Dict = Record<Lang, string>;
const LANGS: Array<{ code: Lang; label: string }> = [
  { code: 'fr', label: 'Français' },
  { code: 'en', label: 'English' },
  { code: 'ar', label: 'العربية' },
];

function getInitialLang(): Lang {
  if (!isBrowser()) return 'fr';
  const stored = window.localStorage.getItem('im_lang');
  if (stored === 'en' || stored === 'ar' || stored === 'fr') return stored;
  return 'fr';
}

const LangContext = createContext<{ lang: Lang; setLang: (l: Lang) => void }>({
  lang: 'fr',
  setLang: () => {},
});
function useLang() {
  return useContext(LangContext);
}

function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(getInitialLang);
  useEffect(() => {
    if (!isBrowser()) return;
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }, [lang]);
  const setLang = (l: Lang) => {
    setLangState(l);
    if (isBrowser()) window.localStorage.setItem('im_lang', l);
  };
  return <LangContext.Provider value={{ lang, setLang }}>{children}</LangContext.Provider>;
}

// UI chrome strings (nav, buttons, section headings, generic labels).
const UI: Record<string, Dict> = {
  navFeatures: { fr: 'Fonctionnalités', en: 'Features', ar: 'الميزات' },
  navHow: { fr: 'Comment ça marche', en: 'How it works', ar: 'كيف يعمل' },
  navWebDesktop: { fr: 'Web & Bureau', en: 'Web & Desktop', ar: 'الويب وسطح المكتب' },
  navFreeTrial: { fr: 'Essai gratuit', en: 'Free trial', ar: 'تجربة مجانية' },
  navFaq: { fr: 'FAQ', en: 'FAQ', ar: 'الأسئلة الشائعة' },
  navContact: { fr: 'Contact', en: 'Contact', ar: 'اتصل بنا' },
  login: { fr: 'Connexion', en: 'Log in', ar: 'تسجيل الدخول' },
  startTrial: { fr: 'Essai gratuit', en: 'Start free trial', ar: 'ابدأ التجربة المجانية' },
  openMenu: { fr: 'Ouvrir le menu', en: 'Open menu', ar: 'فتح القائمة' },
  closeMenu: { fr: 'Fermer le menu', en: 'Close menu', ar: 'إغلاق القائمة' },
  poweredBy: { fr: 'Une solution développée par', en: 'A solution developed by', ar: 'حل طورته' },

  heroTitle: {
    fr: 'Gérez toute votre agence de location de voitures depuis une seule plateforme',
    en: 'Run your entire car rental agency from a single platform',
    ar: 'أدر وكالة تأجير السيارات بالكامل من منصة واحدة',
  },
  heroSub: {
    fr: 'Véhicules, clients, réservations, contrats, signatures, paiements et suivi GPS réunis dans un espace simple et sécurisé.',
    en: 'Vehicles, clients, reservations, contracts, signatures, payments and GPS tracking, all in one simple and secure workspace.',
    ar: 'المركبات والعملاء والحجوزات والعقود والتوقيعات والمدفوعات وتتبع GPS، كلها في مساحة عمل واحدة بسيطة وآمنة.',
  },
  heroPrimaryCta: { fr: 'Essayer gratuitement', en: 'Try it for free', ar: 'جرّب مجاناً' },
  heroSecondaryCta: { fr: 'Découvrir la plateforme', en: 'Discover the platform', ar: 'اكتشف المنصة' },
  heroNoCard: { fr: 'Aucune carte bancaire requise', en: 'No credit card required', ar: 'لا حاجة لبطاقة بنكية' },

  productTitle: { fr: 'Une plateforme complète, pensée pour votre activité', en: 'A complete platform, built for your business', ar: 'منصة متكاملة مصممة لنشاطك' },
  productSub: {
    fr: "Aperçu réel de l'interface Innovacar : tableau de bord, flotte, réservations, contrats et suivi GPS.",
    en: "A real look at the Innovacar interface: dashboard, fleet, reservations, contracts and GPS tracking.",
    ar: "لمحة حقيقية عن واجهة Innovacar: لوحة التحكم، الأسطول، الحجوزات، العقود وتتبع GPS.",
  },

  featuresTitle: { fr: "Tout ce qu'il faut pour piloter votre agence", en: 'Everything you need to run your agency', ar: 'كل ما تحتاجه لإدارة وكالتك' },

  howTitle: { fr: 'Comment ça marche', en: 'How it works', ar: 'كيف يعمل' },

  webDesktopTitle: { fr: 'Travaillez sur le web ou depuis votre ordinateur', en: 'Work from the web or from your computer', ar: 'اعمل عبر الويب أو من حاسوبك' },
  webCardTitle: { fr: 'Application web', en: 'Web application', ar: 'تطبيق الويب' },
  webCardBody: {
    fr: "Accessible depuis n'importe quel navigateur moderne, sans installation. Vos données restent synchronisées, sur mobile, tablette ou ordinateur.",
    en: 'Accessible from any modern browser, no installation needed. Your data stays in sync across mobile, tablet and desktop.',
    ar: 'يمكن الوصول إليه من أي متصفح حديث دون تثبيت. تبقى بياناتك متزامنة على الهاتف واللوحي والحاسوب.',
  },
  desktopCardTitle: { fr: 'Application Windows', en: 'Windows application', ar: 'تطبيق Windows' },
  desktopCardBody: {
    fr: 'Installez Innovacar sur votre ordinateur Windows et connectez-vous avec le même compte que sur la version web. Vos véhicules, clients, réservations, contrats et paiements restent synchronisés.',
    en: 'Install Innovacar on your Windows computer and sign in with the same account as the web version. Your vehicles, clients, reservations, contracts and payments stay synchronized.',
    ar: 'ثبّت Innovacar على جهاز Windows الخاص بك وسجّل الدخول بنفس الحساب المستخدم في نسخة الويب. تبقى مركباتك وعملاؤك وحجوزاتك وعقودك ومدفوعاتك متزامنة.',
  },
  desktopDownload: { fr: 'Télécharger pour Windows', en: 'Download for Windows', ar: 'تحميل لنظام Windows' },
  desktopAvailableBadge: { fr: 'Disponible', en: 'Available', ar: 'متوفر' },
  desktopSoon: { fr: 'Bientôt disponible', en: 'Coming soon', ar: 'قريباً' },
  desktopWaitlist: { fr: "M'avertir à la disponibilité", en: 'Notify me when available', ar: 'أعلمني عند التوفر' },
  desktopLearnMore: { fr: 'En savoir plus →', en: 'Learn more →', ar: 'اعرف المزيد ←' },
  desktopViewDetails: { fr: 'Voir les détails', en: 'View details', ar: 'عرض التفاصيل' },
  desktopSameAccountLine: {
    fr: 'Même compte, mêmes données, aucune nouvelle configuration requise.',
    en: 'Same account, same data, no new setup required.',
    ar: 'نفس الحساب، نفس البيانات، دون الحاجة لأي إعداد جديد.',
  },
  desktopWindowsBits: { fr: 'Windows 10/11 · 64 bits', en: 'Windows 10/11 · 64-bit', ar: 'Windows 10/11 · 64 بت' },
  version: { fr: 'Version', en: 'Version', ar: 'الإصدار' },

  contactUs: { fr: 'Contactez-nous', en: 'Contact us', ar: 'تواصل معنا' },

  // Public checkout/plan-selection isn't wired end-to-end yet (see
  // FreeTrialCta below) — every new agency starts on the same free trial,
  // so this is a single trustworthy CTA rather than priced plan cards.
  trialTitle: { fr: 'Commencez votre essai gratuit', en: 'Start your free trial', ar: 'ابدأ تجربتك المجانية' },
  trialBody: {
    fr: 'Découvrez Innovacar et gérez votre agence depuis un espace de travail unique et sécurisé.',
    en: 'Discover Innovacar and manage your agency from one secure workspace.',
    ar: 'اكتشف Innovacar وأدر وكالتك من مساحة عمل واحدة وآمنة.',
  },
  trialNoCard: { fr: 'Aucune carte bancaire requise', en: 'No credit card required', ar: 'لا حاجة لبطاقة بنكية' },
  trialCancel: { fr: 'Annulez à tout moment', en: 'Cancel anytime', ar: 'ألغِ في أي وقت' },
  trialSupport: { fr: 'Support pendant la mise en route', en: 'Support during onboarding', ar: 'دعم أثناء الإعداد' },

  trustTitle: { fr: 'Confiance et sécurité', en: 'Trust and security', ar: 'الثقة والأمان' },
  benefitsTitle: { fr: 'Conçu pour les agences de location marocaines', en: 'Designed with Moroccan rental agencies in mind', ar: 'مصمم لوكالات تأجير السيارات المغربية' },

  faqTitle: { fr: 'Questions fréquentes', en: 'Frequently asked questions', ar: 'الأسئلة الشائعة' },

  contactTitle: { fr: 'Contactez-nous', en: 'Contact us', ar: 'اتصل بنا' },
  contactSub: {
    fr: 'Une question sur Innovacar, nos tarifs ou votre essai gratuit ?',
    en: 'A question about Innovacar, our pricing, or your free trial?',
    ar: 'هل لديك سؤال حول Innovacar أو أسعارنا أو تجربتك المجانية؟',
  },
  contactOpenForm: { fr: 'Ouvrir le formulaire de contact', en: 'Open the contact form', ar: 'افتح نموذج الاتصال' },
  contactEmail: { fr: 'E-mail', en: 'Email', ar: 'البريد الإلكتروني' },
  contactWhatsapp: { fr: 'WhatsApp', en: 'WhatsApp', ar: 'واتساب' },

  finalCtaTitle: { fr: 'Prêt à essayer Innovacar ?', en: 'Ready to try Innovacar?', ar: 'هل أنت مستعد لتجربة Innovacar؟' },
  finalCtaBody: { fr: 'Démarrez un essai gratuit, sans engagement.', en: 'Start a free trial, no commitment.', ar: 'ابدأ تجربة مجانية دون أي التزام.' },

  footerProduct: { fr: 'Innovacar', en: 'Innovacar', ar: 'Innovacar' },
  footerLegal: { fr: 'Légal', en: 'Legal', ar: 'قانوني' },
  footerCompany: { fr: 'Entreprise', en: 'Company', ar: 'الشركة' },
  footerWebApp: { fr: 'Application web', en: 'Web application', ar: 'تطبيق الويب' },
  footerDesktopApp: { fr: 'Application bureau', en: 'Desktop application', ar: 'تطبيق سطح المكتب' },
  footerAbout: { fr: 'À propos', en: 'About', ar: 'حول' },
  footerTagline: { fr: "est un produit d'Innovax Technologies.", en: 'is a product of Innovax Technologies.', ar: 'هو منتج من Innovax Technologies.' },
  footerCopyright: { fr: 'Tous droits réservés.', en: 'All rights reserved.', ar: 'جميع الحقوق محفوظة.' },

  legalPrivacy: { fr: 'Confidentialité', en: 'Privacy', ar: 'الخصوصية' },
  legalTerms: { fr: "Conditions d'utilisation", en: 'Terms of use', ar: 'شروط الاستخدام' },
  legalCookies: { fr: 'Cookies', en: 'Cookies', ar: 'ملفات تعريف الارتباط' },
  legalSecurity: { fr: 'Sécurité', en: 'Security', ar: 'الأمان' },

  featuresPageTitle: { fr: 'Fonctionnalités', en: 'Features', ar: 'الميزات' },
  featuresPageSub: {
    fr: "Découvrez les outils qu'Innovacar met à la disposition de votre agence de location de voitures.",
    en: 'Discover the tools Innovacar puts at your car rental agency’s disposal.',
    ar: 'اكتشف الأدوات التي تضعها Innovacar تحت تصرف وكالة تأجير السيارات الخاصة بك.',
  },
  featuresPageCtaTitle: { fr: 'Voir Innovacar en action', en: 'See Innovacar in action', ar: 'شاهد Innovacar أثناء العمل' },

  pricingPageTitle: { fr: 'Essai gratuit', en: 'Free trial', ar: 'تجربة مجانية' },
  pricingPageSub: {
    fr: "Nous n'affichons pas encore de grille tarifaire publique. Démarrez un essai gratuit ou contactez-nous pour un devis adapté à la taille de votre agence.",
    en: "We don't publish a public price list yet. Start a free trial or contact us for a quote fitted to the size of your agency.",
    ar: 'لا نعرض حالياً قائمة أسعار عامة. ابدأ تجربة مجانية أو تواصل معنا للحصول على عرض سعر يناسب حجم وكالتك.',
  },
};

function t(lang: Lang, key: keyof typeof UI): string {
  return UI[key][lang] ?? UI[key].fr;
}
/** The real, current trial offer — driven by config, never a hardcoded promise. */
function trialLabel(lang: Lang): string {
  if (TRIAL_PROMO_ENABLED && TRIAL_LABEL_OVERRIDE) return TRIAL_LABEL_OVERRIDE;
  if (TRIAL_DAYS) {
    return lang === 'fr'
      ? `Essai gratuit de ${TRIAL_DAYS} jours`
      : lang === 'ar'
        ? `تجربة مجانية لمدة ${TRIAL_DAYS} يوماً`
        : `${TRIAL_DAYS}-day free trial`;
  }
  return lang === 'fr' ? 'Commencer gratuitement' : lang === 'ar' ? 'ابدأ مجاناً' : 'Start for free';
}

function registerHref(extra?: Record<string, string>): string {
  const params = new URLSearchParams(extra ?? {});
  const qs = params.toString();
  return `/#/register${qs ? `?${qs}` : ''}`;
}

/**
 * Every link that hands off from this static, router-less marketing site to
 * the real HashRouter app (login/register/contact — anything under "/#/...")
 * must go through this handler. A plain `<a href="/#/login">` click only
 * changes the URL's fragment, which browsers NEVER reload the page for
 * (true for any same-document fragment navigation, whether triggered by a
 * real anchor click or `location.hash =` — this holds regardless of how the
 * URL is changed). Since MarketingApp (see MarketingApp.tsx) is mounted once
 * by main.tsx with no router of its own, nothing would ever re-render: the
 * URL bar updates but the landing page stays fully visible underneath,
 * exactly the "URL changes but page doesn't" bug this guards against. A full
 * reload forces main.tsx's shouldRenderMarketingSite() to re-run with the
 * new hash present, which correctly mounts the real app instead this time.
 *
 * Modifier-clicks (ctrl/cmd/shift+click, middle-click) are left alone so
 * "open in new tab" still works via the browser's native handling — a brand
 * new tab loads main.tsx fresh with the target hash already in the URL, so
 * it needs no special handling at all.
 */
export function handOffToApp(e: ReactMouseEvent<HTMLAnchorElement>) {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const href = e.currentTarget.getAttribute('href');
  if (!href) return;
  e.preventDefault();
  window.location.href = href;
  window.location.reload();
}

// ─────────────────────────────────────────────────────────────────────────
// Icons — a small hand-rolled, consistent set (stroke-based, 24x24) so the
// marketing bundle doesn't depend on an external icon package (kept out per
// the zero-external-import constraint above).
// ─────────────────────────────────────────────────────────────────────────
function Icon({ d, size = 22 }: { d: string; size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={d} />
    </svg>
  );
}
const ICONS = {
  car: 'M3 12.5l1.6-4.8A2 2 0 0 1 6.5 6.3h11a2 2 0 0 1 1.9 1.4l1.6 4.8M3 12.5h18M3 12.5V16a1 1 0 0 0 1 1h1.2M21 12.5V16a1 1 0 0 1-1 1h-1.2M6.5 17.5a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0Zm8 0a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0Z',
  users: 'M16 19v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1M9 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7 9v-1a4 4 0 0 0-3-3.87M15 4.13a3 3 0 0 1 0 5.74',
  calendar: 'M7 3v3M17 3v3M4 8h16M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Z',
  file: 'M7 3h7l5 5v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm7 0v5h5',
  signature: 'M4 19c2-3 4-3 6 0s4 3 6 0 4-3 6 0M4 15l6-11 4 8 3-5 3 5',
  card: 'M3 6h18a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Zm-1 5h20',
  shield: 'M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3Zm-3 8.5 2 2 4-4',
  pin: 'M12 21s7-6.2 7-11.5A7 7 0 1 0 5 9.5C5 14.8 12 21 12 21Zm0-9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  chart: 'M4 20V10M11 20V4M18 20v-7',
  bell: 'M6 8a6 6 0 1 1 12 0c0 4 1.5 5.5 2 6.5H4c.5-1 2-2.5 2-6.5Zm4.5 10a1.7 1.7 0 0 0 3 0',
  lock: 'M6 11V8a6 6 0 1 1 12 0v3M5 11h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Z',
  building: 'M4 21V6a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v15M14 21h6V11a1 1 0 0 0-1-1h-5M7 8h2M7 12h2M7 16h2',
  globe: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-9-9h18M12 3a13.5 13.5 0 0 1 0 18 13.5 13.5 0 0 1 0-18Z',
  monitor: 'M4 4h16a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm4 17h8M12 17v4',
  headset: 'M4 13v-1a8 8 0 0 1 16 0v1M4 13v5a2 2 0 0 0 2 2h1v-7H5a1 1 0 0 0-1 1Zm16 0v5a2 2 0 0 1-2 2h-1v-7h2a1 1 0 0 1 1 1Z',
  check: 'M20 6 9 17l-5-5',
  chevron: 'm6 9 6 6 6-6',
  mail: 'M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm0 1 8 7 8-7',
  whatsapp: 'M4 20l1.4-4.1A8 8 0 1 1 9 18.5L4 20Zm4.8-5.5c.3 1 1.7 2.2 3 2.3.9.1 1.8-.4 2.1-1.1M8.7 9.3c.2-.4.5-.9.9-.9h.5c.3 0 .5.3.6.6l.4 1.1c.1.3 0 .6-.2.8l-.4.4c.3 1.1 1.2 2 2.3 2.3l.4-.4c.2-.2.5-.3.8-.2l1.1.4c.3.1.6.3.6.6v.5c0 .4-.5.7-.9.9-1.3.5-2.9-.1-4.2-1.4-1.3-1.3-1.9-2.9-1.4-4.2Z',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'M6 6l12 12M18 6 6 18',
};

// ─────────────────────────────────────────────────────────────────────────
// Navigation
// ─────────────────────────────────────────────────────────────────────────
type NavItem = { key: keyof typeof UI; id: string };
const IN_PAGE_NAV: NavItem[] = [
  { key: 'navFeatures', id: 'features' },
  { key: 'navHow', id: 'how-it-works' },
  { key: 'navWebDesktop', id: 'web-desktop' },
  { key: 'navFreeTrial', id: 'trial' },
  { key: 'navFaq', id: 'faq' },
  { key: 'navContact', id: 'contact' },
];
// Footer-only legal links.
const LEGAL_LINKS: Array<{ href: string; key: keyof typeof UI }> = [
  { href: '/confidentialite', key: 'legalPrivacy' },
  { href: '/conditions', key: 'legalTerms' },
  { href: '/cookies', key: 'legalCookies' },
  { href: '/securite', key: 'legalSecurity' },
];

function LangSwitcher({ compact }: { compact?: boolean }) {
  const { lang, setLang } = useLang();
  return (
    <div className={`im-lang-switch${compact ? ' im-lang-switch-compact' : ''}`} role="group" aria-label="Language">
      {LANGS.map((l) => (
        <button
          key={l.code}
          type="button"
          className={`im-lang-btn${lang === l.code ? ' im-lang-btn-active' : ''}`}
          onClick={() => setLang(l.code)}
          aria-pressed={lang === l.code}
        >
          {l.code.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

const MOBILE_DRAWER_ID = 'im-mobile-drawer';
const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';

function Header() {
  const { lang } = useLang();
  const [open, setOpen] = useState(false);
  const headerRef = useRef<HTMLElement | null>(null);
  const drawerRef = useRef<HTMLDivElement | null>(null);
  const toggleRef = useRef<HTMLButtonElement | null>(null);

  const close = () => setOpen(false);

  // Outside click, Escape, body-scroll lock, and a minimal Tab focus trap —
  // all vanilla DOM APIs (this file cannot import anything besides react, see
  // the file header). Focus returns to the toggle button on close so keyboard
  // users don't lose their place.
  useEffect(() => {
    if (!open) return undefined;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      if (headerRef.current && !headerRef.current.contains(event.target as Node)) {
        close();
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close();
        toggleRef.current?.focus();
        return;
      }
      if (event.key !== 'Tab' || !drawerRef.current) return;
      const focusable = Array.from(drawerRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    // Cast to EventListener: the handler's `MouseEvent | TouchEvent` param is
    // wider than the specific event type each overload (mousedown ->
    // MouseEvent, touchstart -> TouchEvent) expects — a real runtime non-
    // issue (one function correctly handles both), just a TS variance gap.
    document.addEventListener('mousedown', handlePointerDown as EventListener);
    document.addEventListener('touchstart', handlePointerDown as EventListener);
    document.addEventListener('keydown', handleKeyDown);
    drawerRef.current?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)?.focus();

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('mousedown', handlePointerDown as EventListener);
      document.removeEventListener('touchstart', handlePointerDown as EventListener);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <header className="im-header" ref={headerRef}>
      <a href="/" className="im-brand" aria-label="Innovacar by Innovax Technologies">
        <img src="/brand/innovacar-logo.png" alt="" width={36} height={36} />
        <span>Innova<span className="im-brand-accent">car</span></span>
      </a>

      <nav className="im-nav-desktop" aria-label="Navigation principale">
        {IN_PAGE_NAV.map((item) => (
          <button key={item.id} type="button" className="im-nav-link" onClick={() => scrollToId(item.id)}>
            {t(lang, item.key)}
          </button>
        ))}
      </nav>

      <div className="im-header-actions">
        <LangSwitcher compact />
        <a href="/#/login" className="im-btn im-btn-ghost" onClick={handOffToApp}>{t(lang, 'login')}</a>
        <a href={registerHref()} className="im-btn im-btn-primary" onClick={handOffToApp}>{t(lang, 'startTrial')}</a>
        <button
          ref={toggleRef}
          type="button"
          className="im-menu-toggle"
          aria-label={open ? t(lang, 'closeMenu') : t(lang, 'openMenu')}
          aria-expanded={open}
          aria-controls={MOBILE_DRAWER_ID}
          onClick={() => setOpen((v) => !v)}
        >
          <Icon d={open ? ICONS.close : ICONS.menu} />
        </button>
      </div>

      {open && (
        <div id={MOBILE_DRAWER_ID} ref={drawerRef} className="im-mobile-drawer" role="dialog" aria-modal="true" aria-label="Menu">
          {IN_PAGE_NAV.map((item) => (
            <button
              key={item.id}
              type="button"
              className="im-nav-link"
              onClick={() => { close(); scrollToId(item.id); }}
            >
              {t(lang, item.key)}
            </button>
          ))}
          <div className="im-mobile-drawer-actions">
            <a href="/#/login" className="im-btn im-btn-ghost" onClick={(e) => { close(); handOffToApp(e); }}>{t(lang, 'login')}</a>
            <a href={registerHref()} className="im-btn im-btn-primary" onClick={(e) => { close(); handOffToApp(e); }}>{t(lang, 'startTrial')}</a>
          </div>
          <LangSwitcher />
        </div>
      )}
    </header>
  );
}

function Footer() {
  const { lang } = useLang();
  return (
    <footer className="im-footer">
      <div className="im-footer-grid">
        <div className="im-footer-brand">
          <strong>Innovacar</strong>
          <p>Innovacar {t(lang, 'footerTagline')}</p>
        </div>

        <div className="im-footer-col">
          <h4>{t(lang, 'footerProduct')}</h4>
          <button type="button" onClick={() => scrollToId('features')}>{t(lang, 'navFeatures')}</button>
          <button type="button" onClick={() => scrollToId('trial')}>{t(lang, 'navFreeTrial')}</button>
          <a href="/#/login" onClick={handOffToApp}>{t(lang, 'login')}</a>
          <a href={registerHref()} onClick={handOffToApp}>{t(lang, 'startTrial')}</a>
          <button type="button" onClick={() => scrollToId('web-desktop')}>{t(lang, 'footerWebApp')}</button>
          <button type="button" onClick={() => scrollToId('web-desktop')}>{t(lang, 'footerDesktopApp')}</button>
        </div>

        <div className="im-footer-col">
          <h4>{t(lang, 'footerLegal')}</h4>
          {LEGAL_LINKS.map((link) => (
            <a key={link.href} href={link.href}>{t(lang, link.key)}</a>
          ))}
        </div>

        <div className="im-footer-col">
          <h4>{t(lang, 'footerCompany')}</h4>
          {INNOVAX_URL ? (
            <a href={INNOVAX_URL} target="_blank" rel="noopener noreferrer">{COMPANY_NAME}</a>
          ) : (
            <span className="im-footer-static">{COMPANY_NAME}</span>
          )}
          <a href="/#/contact" onClick={handOffToApp}>{t(lang, 'navContact')}</a>
        </div>
      </div>

      <div className="im-footer-bottom">
        <LangSwitcher />
        <p className="im-footer-copy">&copy; {new Date().getFullYear()} Innovax Technologies. {t(lang, 'footerCopyright')}</p>
      </div>
    </footer>
  );
}

function Layout({ children }: { children: ReactNode }) {
  return (
    <LangProvider>
      <div className="im-page">
        <Header />
        <main>{children}</main>
        <Footer />
      </div>
    </LangProvider>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Legal pages (unchanged content/structure from the previous redesign pass;
// French-only — exhaustive legal translation is out of scope for this
// redesign and stays a deliberate follow-up).
// ─────────────────────────────────────────────────────────────────────────
interface LegalSection {
  heading: string;
  paragraphs: string[];
  list?: string[];
}

/**
 * Shared renderer for the four policy pages. Content below is a complete,
 * good-faith draft based on what this application actually collects/does
 * (confirmed against the real codebase — auth cookies, GPS tracking,
 * payments, audit logs, etc.) — it is NOT a substitute for review by
 * qualified legal counsel before being relied on as binding. Update the
 * "Dernière mise à jour" date whenever the content changes.
 */
function LegalArticle({ title, updated, sections }: { title: string; updated: string; sections: LegalSection[] }) {
  return (
    <Layout>
      <section className="im-hero im-hero-compact">
        <h1>{title}</h1>
      </section>
      <section className="im-section im-legal">
        <p className="im-legal-updated">Dernière mise à jour : {updated}</p>
        {sections.map((section) => (
          <div key={section.heading}>
            <h2>{section.heading}</h2>
            {section.paragraphs.map((paragraph, i) => (
              <p key={i}>{paragraph}</p>
            ))}
            {section.list && (
              <ul>
                {section.list.map((item, i) => <li key={i}>{item}</li>)}
              </ul>
            )}
          </div>
        ))}
      </section>
    </Layout>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Feature grid (16 items — Section 6 of the brief)
// ─────────────────────────────────────────────────────────────────────────
interface FeatureItem { icon: string; title: Dict; desc: Dict }
const FEATURES: FeatureItem[] = [
  { icon: ICONS.car, title: { fr: 'Gestion de flotte', en: 'Fleet management', ar: 'إدارة الأسطول' }, desc: { fr: 'Disponibilité, kilométrage, entretien et historique de chaque véhicule.', en: 'Availability, mileage, maintenance and history for every vehicle.', ar: 'التوفر والمسافة المقطوعة والصيانة وسجل كل مركبة.' } },
  { icon: ICONS.users, title: { fr: 'Clients', en: 'Clients', ar: 'العملاء' }, desc: { fr: 'Fiches clients centralisées, documents et historique de location.', en: 'Centralized client records, documents and rental history.', ar: 'ملفات عملاء مركزية ووثائق وسجل الإيجار.' } },
  { icon: ICONS.calendar, title: { fr: 'Réservations', en: 'Reservations', ar: 'الحجوزات' }, desc: { fr: 'Calendrier de réservation clair, sans double-booking.', en: 'A clear booking calendar, with no double-booking.', ar: 'تقويم حجز واضح دون ازدواجية.' } },
  { icon: ICONS.file, title: { fr: 'Contrats', en: 'Contracts', ar: 'العقود' }, desc: { fr: 'Générez vos contrats de location en quelques clics.', en: 'Generate your rental contracts in a few clicks.', ar: 'أنشئ عقود الإيجار ببضع نقرات.' } },
  { icon: ICONS.signature, title: { fr: 'Signature électronique', en: 'Electronic signatures', ar: 'التوقيع الإلكتروني' }, desc: { fr: 'Faites signer vos clients à distance, en toute sécurité.', en: 'Get your clients to sign remotely, securely.', ar: 'اجعل عملاءك يوقعون عن بُعد وبأمان.' } },
  { icon: ICONS.card, title: { fr: 'Paiements & factures', en: 'Payments & invoices', ar: 'المدفوعات والفواتير' }, desc: { fr: 'Suivez les paiements et générez vos factures automatiquement.', en: 'Track payments and generate your invoices automatically.', ar: 'تتبع المدفوعات وأنشئ فواتيرك تلقائياً.' } },
  { icon: ICONS.shield, title: { fr: 'Cautions & garanties', en: 'Deposits & guarantees', ar: 'الضمانات والتأمينات' }, desc: { fr: 'Enregistrez et suivez les cautions liées à chaque contrat.', en: 'Record and track the deposits tied to each contract.', ar: 'سجّل وتتبع الضمانات المرتبطة بكل عقد.' } },
  { icon: ICONS.pin, title: { fr: 'Suivi GPS', en: 'GPS tracking', ar: 'تتبع GPS' }, desc: { fr: 'Localisez vos véhicules équipés et recevez des alertes.', en: 'Locate your equipped vehicles and get alerts.', ar: 'حدد موقع مركباتك المجهزة واستقبل التنبيهات.' } },
  { icon: ICONS.chart, title: { fr: 'Rapports & statistiques', en: 'Reports & statistics', ar: 'التقارير والإحصائيات' }, desc: { fr: "Suivez l'activité, les revenus et l'utilisation de la flotte.", en: 'Track activity, revenue and fleet utilization.', ar: 'تابع النشاط والإيرادات واستخدام الأسطول.' } },
  { icon: ICONS.bell, title: { fr: 'Notifications', en: 'Notifications', ar: 'الإشعارات' }, desc: { fr: 'Alertes en temps réel sur les échéances et événements clés.', en: 'Real-time alerts on deadlines and key events.', ar: 'تنبيهات فورية للمواعيد والأحداث المهمة.' } },
  { icon: ICONS.lock, title: { fr: 'Permissions employés', en: 'Employee permissions', ar: 'صلاحيات الموظفين' }, desc: { fr: 'Des rôles distincts pour chaque membre de votre équipe.', en: 'Distinct roles for every member of your team.', ar: 'أدوار مختلفة لكل عضو في فريقك.' } },
  { icon: ICONS.building, title: { fr: 'Multi-agence', en: 'Multi-agency management', ar: 'إدارة متعددة الوكالات' }, desc: { fr: 'Gérez plusieurs agences ou succursales séparément.', en: 'Manage multiple agencies or branches separately.', ar: 'أدر عدة وكالات أو فروع بشكل منفصل.' } },
  { icon: ICONS.globe, title: { fr: 'Multilingue', en: 'Multilingual support', ar: 'دعم متعدد اللغات' }, desc: { fr: 'Interface en français, anglais et arabe.', en: 'Interface in French, English and Arabic.', ar: 'واجهة بالفرنسية والإنجليزية والعربية.' } },
  { icon: ICONS.monitor, title: { fr: 'Web & bureau', en: 'Web & desktop access', ar: 'الويب وسطح المكتب' }, desc: { fr: 'Accédez à Innovacar depuis un navigateur ou une application dédiée.', en: 'Access Innovacar from a browser or a dedicated app.', ar: 'ادخل إلى Innovacar من متصفح أو تطبيق مخصص.' } },
  { icon: ICONS.shield, title: { fr: 'Sécurité des données', en: 'Secure data management', ar: 'إدارة آمنة للبيانات' }, desc: { fr: 'Données cloisonnées par agence, chiffrées et journalisées.', en: "Data isolated per agency, encrypted and logged.", ar: 'بيانات معزولة لكل وكالة، مشفرة ومسجّلة.' } },
  { icon: ICONS.headset, title: { fr: "Centre d'aide", en: 'Support center', ar: 'مركز الدعم' }, desc: { fr: 'Ouvrez un ticket et obtenez de l’aide depuis la plateforme.', en: 'Open a ticket and get help right from the platform.', ar: 'افتح تذكرة واحصل على المساعدة من داخل المنصة.' } },
];

function FeatureGrid() {
  const { lang } = useLang();
  return (
    <div className="im-grid im-grid-features">
      {FEATURES.map((f) => (
        <div key={f.title.fr} className="im-card im-feature-card">
          <div className="im-feature-icon"><Icon d={f.icon} /></div>
          <h3>{f.title[lang]}</h3>
          <p>{f.desc[lang]}</p>
        </div>
      ))}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// How it works (4 steps — Section 7)
// ─────────────────────────────────────────────────────────────────────────
const STEPS: Array<{ title: Dict; body: Dict }> = [
  { title: { fr: 'Créez votre compte agence', en: 'Create your agency account', ar: 'أنشئ حساب وكالتك' }, body: { fr: 'Inscrivez votre agence en quelques minutes.', en: 'Sign up your agency in minutes.', ar: 'سجّل وكالتك في دقائق.' } },
  { title: { fr: 'Ajoutez véhicules et clients', en: 'Add your vehicles and clients', ar: 'أضف مركباتك وعملاءك' }, body: { fr: 'Configurez votre flotte et votre fichier client.', en: 'Set up your fleet and your client records.', ar: 'أعدّ أسطولك وسجل عملائك.' } },
  { title: { fr: 'Gérez réservations et contrats', en: 'Manage reservations and contracts', ar: 'أدر الحجوزات والعقود' }, body: { fr: 'Créez réservations, contrats, paiements et signatures.', en: 'Create reservations, contracts, payments and signatures.', ar: 'أنشئ الحجوزات والعقود والمدفوعات والتوقيعات.' } },
  { title: { fr: 'Suivez votre activité, web ou bureau', en: 'Track your activity, web or desktop', ar: 'تابع نشاطك عبر الويب أو سطح المكتب' }, body: { fr: 'Consultez vos rapports depuis le web ou votre ordinateur.', en: 'Check your reports from the web or your computer.', ar: 'راجع تقاريرك من الويب أو حاسوبك.' } },
];

// ─────────────────────────────────────────────────────────────────────────
// Trust & security items (Section 11)
// ─────────────────────────────────────────────────────────────────────────
const TRUST_ITEMS: Array<{ icon: string; title: Dict; body: Dict }> = [
  { icon: ICONS.lock, title: { fr: 'Authentification sécurisée', en: 'Secure authentication', ar: 'مصادقة آمنة' }, body: { fr: 'Mots de passe hachés, jetons de courte durée, 2FA disponible.', en: 'Hashed passwords, short-lived tokens, 2FA available.', ar: 'كلمات مرور مشفّرة ورموز قصيرة الأمد ومصادقة ثنائية متاحة.' } },
  { icon: ICONS.users, title: { fr: "Contrôle d'accès par rôle", en: 'Role-based access', ar: 'تحكم بالوصول حسب الدور' }, body: { fr: 'Chaque employé ne voit que ce que son rôle autorise.', en: 'Each employee sees only what their role allows.', ar: 'كل موظف يرى فقط ما يسمح به دوره.' } },
  { icon: ICONS.building, title: { fr: 'Données cloisonnées par agence', en: 'Agency data separation', ar: 'فصل بيانات كل وكالة' }, body: { fr: 'Aucune agence ne peut accéder aux données d’une autre.', en: 'No agency can access another agency’s data.', ar: 'لا يمكن لأي وكالة الوصول إلى بيانات وكالة أخرى.' } },
  { icon: ICONS.signature, title: { fr: 'Liens de contrat sécurisés', en: 'Secure contract links', ar: 'روابط عقود آمنة' }, body: { fr: 'Signature et partage de contrats via des liens à durée limitée.', en: 'Contract signing and sharing via time-limited links.', ar: 'توقيع ومشاركة العقود عبر روابط محدودة المدة.' } },
  { icon: ICONS.chart, title: { fr: "Journaux d'audit", en: 'Audit logs', ar: 'سجلات التدقيق' }, body: { fr: 'Les actions sensibles sont journalisées et consultables.', en: 'Sensitive actions are logged and reviewable.', ar: 'يتم تسجيل الإجراءات الحساسة ويمكن مراجعتها.' } },
];

// ─────────────────────────────────────────────────────────────────────────
// FAQ (Section 13)
// ─────────────────────────────────────────────────────────────────────────
const FAQ_ITEMS: Array<{ q: Dict; a: Dict; dynamicDesktop?: boolean }> = [
  {
    q: { fr: "Qu'est-ce qu'Innovacar ?", en: 'What is Innovacar?', ar: 'ما هو Innovacar؟' },
    a: {
      fr: 'Innovacar est une plateforme de gestion pour agences de location de voitures : flotte, clients, réservations, contrats, paiements et suivi GPS.',
      en: 'Innovacar is a management platform for car rental agencies: fleet, clients, reservations, contracts, payments and GPS tracking.',
      ar: 'Innovacar منصة إدارة لوكالات تأجير السيارات: الأسطول والعملاء والحجوزات والعقود والمدفوعات وتتبع GPS.',
    },
  },
  {
    q: { fr: 'Fonctionne-t-il sur mobile ?', en: 'Does it work on mobile?', ar: 'هل يعمل على الهاتف؟' },
    a: {
      fr: "Oui, l'application web est entièrement responsive et s'utilise depuis un téléphone, une tablette ou un ordinateur.",
      en: 'Yes, the web application is fully responsive and works from a phone, tablet or computer.',
      ar: 'نعم، تطبيق الويب متجاوب بالكامل ويعمل من الهاتف أو اللوحي أو الحاسوب.',
    },
  },
  {
    q: { fr: 'Existe-t-il une application de bureau ?', en: 'Is there a desktop application?', ar: 'هل يوجد تطبيق لسطح المكتب؟' },
    // Placeholder — the real, live-availability-aware answer is computed in
    // Faq() via desktopFaqAnswer() below, using useDesktopReleaseLive().
    a: { fr: '', en: '', ar: '' },
    dynamicDesktop: true,
  },
  {
    q: { fr: 'Puis-je gérer plusieurs employés ?', en: 'Can I manage several employees?', ar: 'هل يمكنني إدارة عدة موظفين؟' },
    a: {
      fr: 'Oui, vous pouvez inviter vos employés et leur attribuer des rôles et permissions distincts.',
      en: 'Yes, you can invite your employees and assign them distinct roles and permissions.',
      ar: 'نعم، يمكنك دعوة موظفيك وتحديد أدوار وصلاحيات مختلفة لكل منهم.',
    },
  },
  {
    q: { fr: 'Les clients peuvent-ils signer un contrat à distance ?', en: 'Can clients sign contracts remotely?', ar: 'هل يمكن للعملاء توقيع العقود عن بُعد؟' },
    a: {
      fr: 'Oui, via un lien de signature électronique sécurisé, sans avoir à se déplacer.',
      en: 'Yes, via a secure electronic signature link, with no need to travel.',
      ar: 'نعم، عبر رابط توقيع إلكتروني آمن، دون الحاجة للتنقل.',
    },
  },
  {
    q: { fr: "Le français et l'arabe sont-ils pris en charge ?", en: 'Does it support Arabic and French?', ar: 'هل تدعم المنصة العربية والفرنسية؟' },
    a: {
      fr: 'Oui, Innovacar est disponible en français, en arabe (avec affichage RTL) et en anglais.',
      en: 'Yes, Innovacar is available in French, Arabic (with RTL layout) and English.',
      ar: 'نعم، تتوفر Innovacar بالفرنسية والعربية (بتخطيط من اليمين لليسار) والإنجليزية.',
    },
  },
  {
    q: { fr: 'Le GPS est-il inclus dans toutes les formules ?', en: 'Is GPS included in all plans?', ar: 'هل GPS متضمن في جميع الباقات؟' },
    a: {
      fr: 'Le suivi GPS et le nombre d’appareils inclus dépendent de la formule choisie — voir la section Tarifs.',
      en: 'GPS tracking and the number of included devices depend on the plan you choose — see the Pricing section.',
      ar: 'يعتمد تتبع GPS وعدد الأجهزة المتضمنة على الباقة التي تختارها — راجع قسم الأسعار.',
    },
  },
  {
    q: { fr: "Comment fonctionne l'essai gratuit ?", en: 'How does the free trial work?', ar: 'كيف تعمل التجربة المجانية؟' },
    a: {
      fr: 'Créez votre compte agence et profitez d’un essai gratuit, sans carte bancaire, pour configurer votre activité et tester la plateforme.',
      en: 'Create your agency account and enjoy a free trial, no credit card, to set up your business and test the platform.',
      ar: 'أنشئ حساب وكالتك واستفد من تجربة مجانية دون بطاقة بنكية لإعداد نشاطك واختبار المنصة.',
    },
  },
  {
    q: { fr: 'Puis-je importer mes clients et véhicules existants ?', en: 'Can I import existing clients and vehicles?', ar: 'هل يمكنني استيراد عملائي ومركباتي الحاليين؟' },
    a: {
      fr: 'Vous pouvez ajouter vos clients et véhicules existants manuellement lors de la configuration de votre agence ; l’import en masse n’est pas encore disponible.',
      en: 'You can add your existing clients and vehicles manually while setting up your agency; bulk import isn’t available yet.',
      ar: 'يمكنك إضافة عملائك ومركباتك الحاليين يدوياً عند إعداد وكالتك؛ الاستيراد الجماعي غير متوفر بعد.',
    },
  },
  {
    q: { fr: 'Comment les données des agences sont-elles séparées ?', en: 'How is agency data separated?', ar: 'كيف يتم فصل بيانات الوكالات؟' },
    a: {
      fr: 'Innovacar est multi-agence par conception : les données de chaque agence sont strictement cloisonnées et inaccessibles aux autres.',
      en: 'Innovacar is multi-tenant by design: each agency’s data is strictly isolated and inaccessible to others.',
      ar: 'تم تصميم Innovacar ليكون متعدد الوكالات: بيانات كل وكالة معزولة تماماً وغير قابلة للوصول من الآخرين.',
    },
  },
];

function desktopFaqAnswer(available: boolean, lang: Lang): string {
  const dict: Dict = available
    ? {
        fr: `Oui, une application ${DESKTOP_PLATFORM} est disponible, connectée aux mêmes données que la version web.`,
        en: `Yes, a ${DESKTOP_PLATFORM} application is available, connected to the same data as the web version.`,
        ar: `نعم، يتوفر تطبيق ${DESKTOP_PLATFORM} متصل بنفس بيانات النسخة الإلكترونية.`,
      }
    : {
        fr: `Une application ${DESKTOP_PLATFORM} est en préparation. En attendant, la version web fonctionne sur ordinateur sans installation.`,
        en: `A ${DESKTOP_PLATFORM} application is in the works. In the meantime, the web version works on desktop with no install.`,
        ar: `تطبيق ${DESKTOP_PLATFORM} قيد التحضير. في غضون ذلك، تعمل نسخة الويب على الحاسوب دون تثبيت.`,
      };
  return dict[lang];
}

function Faq() {
  const { lang } = useLang();
  const desktop = useDesktopReleaseLive();
  return (
    <div className="im-faq">
      {FAQ_ITEMS.map((item) => (
        <details key={item.q.fr} className="im-faq-item">
          <summary>
            <span>{item.q[lang]}</span>
            <Icon d={ICONS.chevron} size={18} />
          </summary>
          <p>{item.dynamicDesktop ? desktopFaqAnswer(desktop.available, lang) : item.a[lang]}</p>
        </details>
      ))}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Free trial CTA (replaces the old priced plan cards — see file history).
// Plan selection on the public site never reached checkout: the backend only
// exposes an authenticated, admin-only checkout (POST /api/saas/checkout/*,
// used from the in-app Subscription/Billing settings after signup), and
// every newly registered agency starts on the same free trial regardless of
// which "plan" a visitor clicked. Rather than present cards that imply a
// purchase this site can't complete, this is one honest CTA into the real
// registration flow. In-app plan upgrade/checkout is unaffected — see
// src/pages/Subscription.tsx and src/components/settings/BillingTab.tsx.
// ─────────────────────────────────────────────────────────────────────────
function FreeTrialCta() {
  const { lang } = useLang();
  return (
    <section id="trial" className="im-section im-trial">
      <h2>{t(lang, 'trialTitle')}</h2>
      <p className="im-section-sub">{t(lang, 'trialBody')}</p>
      <div className="im-trial-actions">
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg" onClick={handOffToApp}>{t(lang, 'startTrial')}</a>
        <a href="/#/contact" className="im-btn im-btn-ghost im-btn-lg" onClick={handOffToApp}>{t(lang, 'contactUs')}</a>
      </div>
      <p className="im-hero-note">{t(lang, 'trialNoCard')}</p>
      <ul className="im-trial-notes">
        <li>{t(lang, 'trialCancel')}</li>
        <li>{t(lang, 'trialSupport')}</li>
      </ul>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Product preview — an honest, illustrative mockup of the real Innovacar
// screens (no stock photos, no fake unrelated dashboard). Built from CSS
// only so it stays crisp at every size and needs no image assets.
// ─────────────────────────────────────────────────────────────────────────
function ProductMockup() {
  const { lang } = useLang();
  return (
    <div className="im-mockup" role="img" aria-label={t(lang, 'productSub')}>
      <div className="im-mockup-desktop">
        <div className="im-mockup-topbar">
          <span /><span /><span />
        </div>
        <div className="im-mockup-body">
          <div className="im-mockup-sidebar">
            {[ICONS.chart, ICONS.car, ICONS.calendar, ICONS.users, ICONS.file, ICONS.pin, ICONS.card].map((d, i) => (
              <span key={i} className={i === 0 ? 'im-mockup-sidebar-active' : ''}><Icon d={d} size={16} /></span>
            ))}
          </div>
          <div className="im-mockup-main">
            <div className="im-mockup-stat-row">
              <div className="im-mockup-stat" /><div className="im-mockup-stat" /><div className="im-mockup-stat" />
            </div>
            <div className="im-mockup-chart" />
            <div className="im-mockup-rows">
              <div /><div /><div />
            </div>
          </div>
        </div>
      </div>
      <div className="im-mockup-mobile">
        <div className="im-mockup-mobile-notch" />
        <div className="im-mockup-mobile-body">
          <div className="im-mockup-stat im-mockup-stat-wide" />
          <div className="im-mockup-rows">
            <div /><div />
          </div>
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// HomePage — the full landing page.
// ─────────────────────────────────────────────────────────────────────────
function HomePage() {
  return (
    <Layout>
      <HomePageContent />
    </Layout>
  );
}

/** Rendered as a child of Layout so useLang() here actually sees LangProvider's state. */
function HomePageContent() {
  const { lang } = useLang();
  const desktop = useDesktopReleaseLive();

  useEffect(() => {
    if (!isBrowser()) return;
    const section = new URLSearchParams(window.location.search).get('section');
    if (section) {
      window.requestAnimationFrame(() => document.getElementById(section)?.scrollIntoView({ block: 'start' }));
    }
  }, []);

  return (
    <>
      <section className="im-hero">
        <p className="im-hero-eyebrow">{t(lang, 'poweredBy')} Innovax Technologies</p>
        <h1>{t(lang, 'heroTitle')}</h1>
        <p className="im-hero-sub">{t(lang, 'heroSub')}</p>
        <div className="im-hero-actions">
          <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg" onClick={handOffToApp}>{t(lang, 'heroPrimaryCta')} — {trialLabel(lang)}</a>
          <button type="button" className="im-btn im-btn-ghost im-btn-lg" onClick={() => scrollToId('product')}>{t(lang, 'heroSecondaryCta')}</button>
        </div>
        <p className="im-hero-note">{t(lang, 'heroNoCard')}</p>
      </section>

      <section id="product" className="im-section">
        <h2>{t(lang, 'productTitle')}</h2>
        <p className="im-section-sub">{t(lang, 'productSub')}</p>
        <ProductMockup />
      </section>

      <section id="features" className="im-section">
        <h2>{t(lang, 'featuresTitle')}</h2>
        <FeatureGrid />
      </section>

      <section id="how-it-works" className="im-section im-how">
        <h2>{t(lang, 'howTitle')}</h2>
        <ol className="im-steps">
          {STEPS.map((step, i) => (
            <li key={step.title.fr}>
              <strong>{i + 1}. {step.title[lang]}</strong>
              <p>{step.body[lang]}</p>
            </li>
          ))}
        </ol>
      </section>

      <section id="web-desktop" className="im-section">
        <h2>{t(lang, 'webDesktopTitle')}</h2>
        <div className="im-grid im-grid-2">
          <div className="im-card im-webdesktop-card">
            <div className="im-feature-icon"><Icon d={ICONS.globe} /></div>
            <h3>{t(lang, 'webCardTitle')}</h3>
            <p>{t(lang, 'webCardBody')}</p>
            <a href={registerHref()} className="im-btn im-btn-primary" onClick={handOffToApp}>{t(lang, 'startTrial')}</a>
          </div>
          <div className="im-card im-webdesktop-card">
            <div className="im-feature-icon"><Icon d={ICONS.monitor} /></div>
            <div className="im-desktop-card-head">
              <h3>{t(lang, 'desktopCardTitle')}</h3>
              <span className={`im-badge ${desktop.available ? 'im-badge-available' : ''}`}>
                {t(lang, desktop.available ? 'desktopAvailableBadge' : 'desktopSoon')}
              </span>
            </div>
            <p>{t(lang, 'desktopCardBody')}</p>
            {desktop.available && desktop.downloadUrl ? (
              <>
                <ul className="im-desktop-meta">
                  <li>{t(lang, 'version')} {desktop.version}</li>
                  <li>{t(lang, 'desktopWindowsBits')}</li>
                  {desktop.fileSizeBytes && <li>{(desktop.fileSizeBytes / (1024 * 1024)).toFixed(0)} MB</li>}
                  {desktop.releaseDate && <li>{new Date(desktop.releaseDate).toLocaleDateString(lang)}</li>}
                </ul>
                <div className="im-desktop-actions">
                  <a
                    href={desktop.downloadUrl}
                    className="im-btn im-btn-primary"
                    onClick={() => recordDesktopDownload(desktop.releaseId, 'LANDING')}
                  >
                    {t(lang, 'desktopDownload')}
                  </a>
                  <a href="/desktop" className="im-btn im-btn-ghost">{t(lang, 'desktopViewDetails')}</a>
                </div>
                <p className="im-hero-note">{t(lang, 'desktopSameAccountLine')}</p>
              </>
            ) : (
              <div className="im-desktop-soon">
                <button type="button" className="im-btn im-btn-ghost" onClick={() => scrollToId('contact')}>
                  {t(lang, 'desktopWaitlist')}
                </button>
                <a href="/desktop" className="im-link-more">{t(lang, 'desktopLearnMore')}</a>
              </div>
            )}
          </div>
        </div>
      </section>

      <FreeTrialCta />

      <section id="trust" className="im-section">
        <h2>{t(lang, 'trustTitle')}</h2>
        <div className="im-grid im-grid-trust">
          {TRUST_ITEMS.map((item) => (
            <div key={item.title.fr} className="im-card im-feature-card">
              <div className="im-feature-icon"><Icon d={item.icon} /></div>
              <h3>{item.title[lang]}</h3>
              <p>{item.body[lang]}</p>
            </div>
          ))}
        </div>
        <p className="im-trust-legal-links">
          {LEGAL_LINKS.map((link, i) => (
            <span key={link.href}>
              {i > 0 && ' · '}
              <a href={link.href}>{t(lang, link.key)}</a>
            </span>
          ))}
        </p>
      </section>

      <section className="im-section im-benefits">
        <h2>{t(lang, 'benefitsTitle')}</h2>
      </section>

      <section id="faq" className="im-section">
        <h2>{t(lang, 'faqTitle')}</h2>
        <Faq />
      </section>

      <section id="contact" className="im-section im-contact">
        <h2>{t(lang, 'contactTitle')}</h2>
        <p className="im-section-sub">{t(lang, 'contactSub')}</p>
        <div className="im-contact-actions">
          <a href="/#/contact" className="im-btn im-btn-primary" onClick={handOffToApp}>{t(lang, 'contactOpenForm')}</a>
          {CONTACT_EMAIL && (
            <a href={`mailto:${CONTACT_EMAIL}`} className="im-btn im-btn-ghost">
              <Icon d={ICONS.mail} size={18} /> {CONTACT_EMAIL}
            </a>
          )}
          {CONTACT_WHATSAPP_DIGITS && (
            <a href={`https://wa.me/${CONTACT_WHATSAPP_DIGITS}`} className="im-btn im-btn-ghost" target="_blank" rel="noopener noreferrer">
              <Icon d={ICONS.whatsapp} size={18} /> {t(lang, 'contactWhatsapp')}
            </a>
          )}
        </div>
        <p className="im-contact-company">{COMPANY_NAME} — Maroc</p>
      </section>

      <section className="im-section im-cta">
        <h2>{t(lang, 'finalCtaTitle')}</h2>
        <p>{t(lang, 'finalCtaBody')}</p>
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg" onClick={handOffToApp}>{t(lang, 'heroPrimaryCta')}</a>
      </section>
    </>
  );
}

function FeaturesPage() {
  return (
    <Layout>
      <FeaturesPageContent />
    </Layout>
  );
}

function FeaturesPageContent() {
  const { lang } = useLang();
  return (
    <>
      <section className="im-hero im-hero-compact">
        <h1>{t(lang, 'featuresPageTitle')}</h1>
        <p className="im-hero-sub">{t(lang, 'featuresPageSub')}</p>
      </section>
      <section className="im-section">
        <FeatureGrid />
      </section>
      <section className="im-section im-cta">
        <h2>{t(lang, 'featuresPageCtaTitle')}</h2>
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg" onClick={handOffToApp}>{t(lang, 'heroPrimaryCta')}</a>
      </section>
    </>
  );
}

function PricingPage() {
  return (
    <Layout>
      <PricingPageContent />
    </Layout>
  );
}

function PricingPageContent() {
  const { lang } = useLang();
  return (
    <>
      <section className="im-hero im-hero-compact">
        <h1>{t(lang, 'pricingPageTitle')}</h1>
        <p className="im-hero-sub">{t(lang, 'pricingPageSub')}</p>
      </section>
      <FreeTrialCta />
    </>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Dedicated /desktop page — deeper dive than the homepage's Web & Desktop
// section, using the same live release data (useDesktopReleaseLive) so
// nothing here is ever hand-duplicated from the homepage card or the
// authenticated Desktop App page.
// ─────────────────────────────────────────────────────────────────────────
const DESKTOP_PAGE_UI: Record<string, Dict> = {
  title: { fr: 'Innovacar Bureau', en: 'Innovacar Desktop', ar: 'Innovacar لسطح المكتب' },
  subtitle: {
    fr: `Une application ${DESKTOP_PLATFORM} native pour votre compte Innovacar — même compte, mêmes données.`,
    en: `A native ${DESKTOP_PLATFORM} application for your Innovacar account — same account, same data.`,
    ar: `تطبيق ${DESKTOP_PLATFORM} أصلي لحساب Innovacar الخاص بك — نفس الحساب، نفس البيانات.`,
  },
  benefitsTitle: { fr: 'Pourquoi utiliser Innovacar Bureau', en: 'Why use Innovacar Desktop', ar: 'لماذا تستخدم Innovacar لسطح المكتب' },
  benefit1: { fr: 'Un espace de travail dédié, séparé de vos onglets de navigateur', en: 'A dedicated workspace, separate from your browser tabs', ar: 'مساحة عمل مخصصة، منفصلة عن تبويبات المتصفح' },
  benefit2: { fr: 'Notifications natives Windows', en: 'Native Windows notifications', ar: 'إشعارات Windows الأصلية' },
  benefit3: { fr: 'Enregistrement sécurisé des PDF', en: 'Secure PDF saving', ar: 'حفظ آمن لملفات PDF' },
  benefit4: { fr: 'Impression directe', en: 'Direct printing', ar: 'طباعة مباشرة' },
  benefit5: { fr: 'Accès rapide au tableau de bord', en: 'Fast access to your dashboard', ar: 'وصول سريع إلى لوحة التحكم' },
  benefit6: { fr: 'Mises à jour automatiques (bientôt)', en: 'Automatic updates (coming soon)', ar: 'تحديثات تلقائية (قريباً)' },
  reqTitle: { fr: 'Configuration requise', en: 'System requirements', ar: 'المتطلبات' },
  req1: { fr: 'Windows 10 ou plus récent', en: 'Windows 10 or later', ar: 'Windows 10 أو أحدث' },
  req2: { fr: 'Processeur 64 bits', en: '64-bit processor', ar: 'معالج 64 بت' },
  req3: { fr: 'Connexion Internet', en: 'Internet connection', ar: 'اتصال بالإنترنت' },
  req4: { fr: 'Un compte Innovacar actif', en: 'An active Innovacar account', ar: 'حساب Innovacar نشط' },
  releaseInfo: { fr: 'Informations sur la version', en: 'Release information', ar: 'معلومات الإصدار' },
  version: { fr: 'Version', en: 'Version', ar: 'الإصدار' },
  releaseDate: { fr: 'Date de sortie', en: 'Release date', ar: 'تاريخ الإصدار' },
  fileSize: { fr: 'Taille du fichier', en: 'File size', ar: 'حجم الملف' },
  architecture: { fr: 'Architecture', en: 'Architecture', ar: 'المعمارية' },
  checksum: { fr: 'Somme de contrôle SHA-256', en: 'SHA-256 checksum', ar: 'بصمة SHA-256' },
  releaseNotesTitle: { fr: 'Notes de version', en: 'Release notes', ar: 'ملاحظات الإصدار' },
  securityTitle: { fr: 'Sécurité', en: 'Security', ar: 'الأمان' },
  securityBody: {
    fr: "L'installateur est distribué en HTTPS depuis une source approuvée. La signature de code Windows est en cours de mise en place — nous l'indiquerons ici dès qu'elle sera active.",
    en: 'The installer is distributed over HTTPS from an approved source. Windows code signing is being put in place — we will state clearly here once it is active.',
    ar: 'يتم توزيع المثبت عبر HTTPS من مصدر معتمد. يجري حالياً إعداد التوقيع الرقمي لـ Windows — سنوضح ذلك هنا فور تفعيله.',
  },
  installTitle: { fr: "Étapes d'installation", en: 'Installation steps', ar: 'خطوات التثبيت' },
  installStep1: { fr: 'Ouvrez Innovacar Setup.', en: 'Open Innovacar Setup.', ar: 'افتح Innovacar Setup.' },
  installStep2: { fr: "Suivez les étapes d'installation.", en: 'Follow the installation steps.', ar: 'اتبع خطوات التثبيت.' },
  installStep3: { fr: 'Lancez Innovacar.', en: 'Launch Innovacar.', ar: 'شغّل Innovacar.' },
  installStep4: { fr: 'Connectez-vous avec votre compte Innovacar habituel.', en: 'Sign in using your usual Innovacar account.', ar: 'سجّل الدخول باستخدام حساب Innovacar المعتاد.' },
  loginNote: {
    fr: "Aucun nouveau compte n'est nécessaire. Les données de l'application web et de bureau restent synchronisées automatiquement.",
    en: 'No new account is needed. Web and desktop data stay synchronized automatically.',
    ar: 'لا حاجة لحساب جديد. تبقى بيانات تطبيقي الويب وسطح المكتب متزامنة تلقائياً.',
  },
  supportTitle: { fr: 'Besoin d’aide ?', en: 'Need help?', ar: 'بحاجة للمساعدة؟' },
  supportBody: { fr: 'Contactez le support si l’installation ou la connexion ne se déroule pas comme prévu.', en: "Contact support if the installer or sign-in doesn't work as expected.", ar: 'تواصل مع الدعم إذا لم يعمل التثبيت أو تسجيل الدخول كما هو متوقع.' },
  faqTitle: { fr: 'Questions fréquentes', en: 'Frequently asked questions', ar: 'الأسئلة الشائعة' },
};

function dt(lang: Lang, key: string): string {
  return DESKTOP_PAGE_UI[key]?.[lang] ?? key;
}

function DesktopPage() {
  return (
    <Layout>
      <DesktopPageContent />
    </Layout>
  );
}

function DesktopPageContent() {
  const { lang } = useLang();
  const desktop = useDesktopReleaseLive();
  const notes = desktop.releaseNotes?.[lang] ?? desktop.releaseNotes?.en ?? [];

  return (
    <>
      <section className="im-hero im-hero-compact">
        <h1>{dt(lang, 'title')}</h1>
        <p className="im-hero-sub">{dt(lang, 'subtitle')}</p>
        <span className={`im-badge ${desktop.available ? 'im-badge-available' : ''}`}>
          {t(lang, desktop.available ? 'desktopAvailableBadge' : 'desktopSoon')}
        </span>
        <div className="im-hero-actions">
          {desktop.available && desktop.downloadUrl ? (
            <a
              href={desktop.downloadUrl}
              className="im-btn im-btn-primary im-btn-lg"
              onClick={() => recordDesktopDownload(desktop.releaseId, 'DESKTOP_PAGE')}
            >
              {t(lang, 'desktopDownload')}
            </a>
          ) : (
            <div className="im-desktop-soon">
              <a href="mailto:support@innovacar.app?subject=Notify%20me%20-%20Innovacar%20Desktop" className="im-btn im-btn-ghost im-btn-lg">
                {t(lang, 'desktopWaitlist')}
              </a>
            </div>
          )}
        </div>
      </section>

      <section className="im-section">
        <div className="im-mockup-desktop" role="img" aria-label={dt(lang, 'title')}>
          <div className="im-mockup-topbar"><span /><span /><span /></div>
          <div className="im-mockup-body">
            <div className="im-mockup-sidebar">
              {[ICONS.chart, ICONS.car, ICONS.calendar, ICONS.users].map((d, i) => (
                <span key={i} className={i === 0 ? 'im-mockup-sidebar-active' : ''}><Icon d={d} size={16} /></span>
              ))}
            </div>
            <div className="im-mockup-main">
              <div className="im-mockup-stat-row"><div className="im-mockup-stat" /><div className="im-mockup-stat" /><div className="im-mockup-stat" /></div>
              <div className="im-mockup-chart" />
            </div>
          </div>
        </div>
      </section>

      <section className="im-section">
        <h2>{dt(lang, 'benefitsTitle')}</h2>
        <ul className="im-trial-notes">
          {['benefit1', 'benefit2', 'benefit3', 'benefit4', 'benefit5', 'benefit6'].map((key) => (
            <li key={key}>{dt(lang, key)}</li>
          ))}
        </ul>
      </section>

      <section className="im-section">
        <h2>{dt(lang, 'reqTitle')}</h2>
        <ul className="im-trial-notes">
          <li>{dt(lang, 'req1')}</li>
          <li>{dt(lang, 'req2')}</li>
          <li>{dt(lang, 'req3')}</li>
          <li>{dt(lang, 'req4')}</li>
        </ul>
      </section>

      {desktop.available && (
        <section className="im-section">
          <h2>{dt(lang, 'releaseInfo')}</h2>
          <ul className="im-trial-notes">
            <li>{dt(lang, 'version')}: {desktop.version}</li>
            <li>{dt(lang, 'releaseDate')}: {desktop.releaseDate ? new Date(desktop.releaseDate).toLocaleDateString(lang) : '—'}</li>
            <li>{dt(lang, 'fileSize')}: {desktop.fileSizeBytes ? `${(desktop.fileSizeBytes / (1024 * 1024)).toFixed(0)} MB` : '—'}</li>
            <li>{dt(lang, 'architecture')}: X64</li>
          </ul>
          {desktop.sha256 && (
            <p className="im-hero-note" style={{ wordBreak: 'break-all' }}>{dt(lang, 'checksum')}: {desktop.sha256}</p>
          )}
          {notes.length > 0 && (
            <>
              <h3>{dt(lang, 'releaseNotesTitle')}</h3>
              <ul className="im-trial-notes">
                {notes.map((line, i) => <li key={i}>{line}</li>)}
              </ul>
            </>
          )}
        </section>
      )}

      <section className="im-section">
        <h2>{dt(lang, 'securityTitle')}</h2>
        <p className="im-section-sub">{dt(lang, 'securityBody')}</p>
      </section>

      <section className="im-section">
        <h2>{dt(lang, 'installTitle')}</h2>
        <ol className="im-steps">
          {['installStep1', 'installStep2', 'installStep3', 'installStep4'].map((key, i) => (
            <li key={key}><strong>{i + 1}. {dt(lang, key)}</strong></li>
          ))}
        </ol>
        <p className="im-hero-note">{dt(lang, 'loginNote')}</p>
      </section>

      <section id="faq" className="im-section">
        <h2>{dt(lang, 'faqTitle')}</h2>
        <Faq />
      </section>

      <section className="im-section im-contact">
        <h2>{dt(lang, 'supportTitle')}</h2>
        <p className="im-section-sub">{dt(lang, 'supportBody')} <a href="mailto:support@innovacar.app">support@innovacar.app</a></p>
      </section>
    </>
  );
}

const LEGAL_UPDATED = '24 juillet 2026';

function PrivacyPage() {
  return (
    <LegalArticle
      title="Politique de confidentialité"
      updated={LEGAL_UPDATED}
      sections={[
        {
          heading: '1. Qui sommes-nous',
          paragraphs: [
            "Innovacar est un logiciel de gestion pour agences de location de voitures, édité par Innovax Technologies. Cette politique explique quelles données nous traitons, pourquoi, combien de temps, et quels sont vos droits.",
            "Deux rôles distincts coexistent sur la plateforme : les agences de location, qui souscrivent à Innovacar et sont responsables des données de leurs propres clients (locataires) ; et Innovax Technologies, qui héberge la plateforme et traite ces données pour le compte des agences (sous-traitant), tout en étant responsable de traitement pour les données des comptes d'agence eux-mêmes (identifiants, facturation, utilisation du service).",
          ],
        },
        {
          heading: '2. Données que nous traitons',
          paragraphs: ["Selon votre utilisation d'Innovacar, les données suivantes peuvent être traitées :"],
          list: [
            "Données de compte d'agence : nom de l'agence, coordonnées, employés (nom, e-mail, téléphone, rôle).",
            "Données des clients de l'agence (locataires) : nom, CIN ou passeport, permis de conduire, adresse, téléphone, e-mail, date de naissance — saisies par l'agence pour la gestion de ses contrats de location.",
            "Données véhicules : marque, modèle, immatriculation, kilométrage, statut, entretien.",
            "Données de géolocalisation GPS : uniquement pour les véhicules d'une agence ayant activé le suivi GPS, à des fins de suivi de flotte et de sécurité.",
            "Données de paiement et de facturation : montants, méthodes, statuts des paiements liés aux contrats et aux abonnements — Innovacar ne stocke pas les numéros complets de carte bancaire.",
            "Communications de support : tickets, messages échangés avec notre équipe ou entre une agence et ses clients via la plateforme.",
            "Données techniques : adresse IP, type de navigateur et d'appareil, journaux de connexion et d'audit, horodatages.",
          ],
        },
        {
          heading: '3. Pourquoi nous traitons ces données',
          paragraphs: [],
          list: [
            "Fournir le service : gestion de flotte, contrats, réservations, paiements, suivi GPS, support.",
            "Sécuriser les comptes : authentification, détection des tentatives de connexion suspectes, journaux d'audit.",
            "Assurer la facturation et la gestion des abonnements.",
            "Communiquer avec vous : e-mails transactionnels (confirmation, réinitialisation de mot de passe, factures), réponses au support.",
            "Respecter nos obligations légales et répondre aux demandes des autorités compétentes.",
          ],
        },
        {
          heading: '4. Durée de conservation',
          paragraphs: [
            "Nous conservons les données pendant la durée nécessaire aux finalités décrites ci-dessus, puis selon un calendrier de conservation documenté en interne (données de compte, contrats, paiements, données GPS, tickets de support, consentement aux cookies), en tenant compte des durées de prescription légale applicables en matière commerciale et fiscale au Maroc. Les données peuvent être supprimées ou anonymisées à l'expiration de ces durées, sauf obligation légale de conservation plus longue.",
          ],
        },
        {
          heading: '5. Partage des données',
          paragraphs: [
            "Nous ne vendons aucune donnée personnelle. Certaines données sont partagées avec des prestataires techniques strictement nécessaires au fonctionnement du service, notamment : hébergement de l'application et de la base de données, envoi d'e-mails transactionnels (ZeptoMail), et, si votre agence le configure, votre prestataire de suivi GPS. Ces prestataires n'utilisent les données que pour exécuter les services demandés.",
          ],
        },
        {
          heading: '6. Sécurité',
          paragraphs: [
            "Voir notre page dédiée « Sécurité » pour le détail des mesures techniques et organisationnelles mises en œuvre (chiffrement, authentification, contrôle d'accès, sauvegardes, journalisation).",
          ],
        },
        {
          heading: '7. Vos droits',
          paragraphs: [
            "Conformément à la loi marocaine n° 09-08 relative à la protection des personnes physiques à l'égard du traitement des données à caractère personnel, et sous le contrôle de la CNDP (Commission Nationale de contrôle de la protection des Données à caractère Personnel), vous disposez d'un droit d'accès, de rectification, d'opposition et de suppression de vos données. Si vous êtes client d'une agence utilisant Innovacar, adressez votre demande directement à cette agence, responsable de vos données. Pour toute question relative à votre compte d'agence, contactez-nous.",
          ],
        },
        {
          heading: '8. Contact',
          paragraphs: [
            "Pour toute question relative à cette politique ou à vos données, contactez-nous via notre page de contact.",
          ],
        },
      ]}
    />
  );
}

function TermsPage() {
  return (
    <LegalArticle
      title="Conditions d'utilisation"
      updated={LEGAL_UPDATED}
      sections={[
        {
          heading: '1. Objet',
          paragraphs: [
            "Les présentes conditions régissent l'utilisation d'Innovacar, plateforme de gestion pour agences de location de voitures éditée par Innovax Technologies. En créant un compte ou en utilisant le service, votre agence accepte ces conditions.",
          ],
        },
        {
          heading: '2. Abonnements et essai gratuit',
          paragraphs: [
            "Innovacar propose un essai gratuit d'un mois calendaire, sans engagement, permettant de tester le service avant de souscrire un abonnement payant (Basic, Standard ou Premium). À l'issue de l'essai, l'accès aux fonctionnalités payantes nécessite la souscription d'un abonnement actif. Les tarifs affichés sont indicatifs et peuvent évoluer ; toute modification tarifaire sera communiquée à l'avance aux agences abonnées.",
          ],
        },
        {
          heading: '3. Annulation et remboursement',
          paragraphs: [
            "Une agence peut annuler son abonnement à tout moment depuis les paramètres de son compte ou en contactant le support. L'annulation prend effet à la fin de la période de facturation en cours ; aucun remboursement au prorata n'est effectué pour la période déjà entamée, sauf disposition légale contraire ou accord commercial spécifique.",
          ],
        },
        {
          heading: '4. Responsabilités de l\'agence',
          paragraphs: [],
          list: [
            "Fournir des informations exactes lors de la création du compte et de ses clients.",
            "Obtenir le consentement de ses propres clients pour la collecte et le traitement de leurs données via Innovacar, et respecter la réglementation applicable envers eux (l'agence agit comme responsable de traitement pour les données de ses clients).",
            "Protéger les identifiants de connexion de ses employés et signaler toute utilisation non autorisée du compte.",
            "Utiliser le service conformément à la réglementation marocaine applicable à son activité de location de véhicules.",
          ],
        },
        {
          heading: '5. Responsabilités d\'Innovax Technologies',
          paragraphs: [
            "Nous nous engageons à maintenir une disponibilité raisonnable du service, à appliquer des mesures de sécurité appropriées (voir notre page « Sécurité »), et à fournir un support conforme au niveau de votre abonnement. Le service est fourni « en l'état » ; nous ne garantissons pas une disponibilité ininterrompue et pouvons réaliser des opérations de maintenance planifiée, annoncées lorsque cela est raisonnablement possible.",
          ],
        },
        {
          heading: '6. Utilisation acceptable',
          paragraphs: [
            "Il est interdit d'utiliser Innovacar à des fins illégales, de tenter de contourner les mesures de sécurité, d'accéder à des données d'une autre agence, ou de perturber le fonctionnement du service. Tout manquement grave peut entraîner la suspension ou la résiliation du compte.",
          ],
        },
        {
          heading: '7. Disponibilité et support',
          paragraphs: [
            "Le niveau de support (standard ou prioritaire) dépend de la formule souscrite. Les demandes peuvent être soumises via le centre d'aide intégré ou notre page de contact.",
          ],
        },
        {
          heading: '8. Modifications des conditions',
          paragraphs: [
            "Nous pouvons modifier ces conditions pour refléter une évolution du service ou de la réglementation. Les changements substantiels seront communiqués aux agences abonnées avant leur entrée en vigueur.",
          ],
        },
      ]}
    />
  );
}

function CookiesPage() {
  return (
    <LegalArticle
      title="Politique de cookies"
      updated={LEGAL_UPDATED}
      sections={[
        {
          heading: '1. Ce que nous utilisons réellement',
          paragraphs: [
            "Par souci de transparence : Innovacar n'utilise aujourd'hui aucun cookie publicitaire ou de suivi (« analytics » ou « marketing »). Seuls des cookies strictement nécessaires au fonctionnement du service sont déposés, décrits ci-dessous.",
          ],
        },
        {
          heading: '2. Cookies strictement nécessaires',
          paragraphs: [
            "Ces cookies permettent de vous garder connecté en toute sécurité et ne peuvent pas être désactivés sans empêcher le fonctionnement du service :",
          ],
          list: [
            "rentcar_access — jeton de session de courte durée, prouvant que vous êtes connecté à votre demande.",
            "rentcar_refresh — jeton permettant de renouveler votre session sans ressaisir votre mot de passe, limité au chemin de connexion.",
          ],
        },
        {
          heading: '3. Stockage local (non-cookie)',
          paragraphs: [
            "Certaines préférences (thème clair/sombre, langue choisie) sont enregistrées dans le stockage local de votre navigateur (« localStorage »), un mécanisme distinct des cookies qui n'est jamais transmis à nos serveurs. Vous pouvez l'effacer à tout moment depuis les paramètres de votre navigateur ; cela réinitialisera simplement vos préférences d'affichage.",
          ],
        },
        {
          heading: '4. Durée de conservation',
          paragraphs: [
            "Le cookie de session expire après une courte durée ; le cookie de renouvellement expire après votre période d'inactivité prolongée ou lors de la déconnexion. Aucun cookie non essentiel n'est conservé, puisqu'aucun n'est déposé.",
          ],
        },
        {
          heading: '5. Évolution future',
          paragraphs: [
            "Si Innovacar venait à utiliser des cookies de préférence, d'analyse ou marketing à l'avenir, cette politique sera mise à jour et un bandeau de consentement vous permettra d'accepter, de refuser ou de personnaliser ces catégories avant tout dépôt.",
          ],
        },
        {
          heading: '6. Gestion et suppression',
          paragraphs: [
            "Vous pouvez supprimer les cookies déposés par Innovacar à tout moment depuis les paramètres de votre navigateur. Notez que la suppression du cookie de session vous déconnectera immédiatement.",
          ],
        },
      ]}
    />
  );
}

function SecurityPage() {
  return (
    <LegalArticle
      title="Sécurité"
      updated={LEGAL_UPDATED}
      sections={[
        {
          heading: '1. Chiffrement',
          paragraphs: [
            "Toutes les communications entre votre navigateur et nos serveurs sont chiffrées via HTTPS/TLS. Les mots de passe ne sont jamais stockés en clair : ils sont hachés avec l'algorithme BCrypt avant tout enregistrement.",
          ],
        },
        {
          heading: '2. Authentification',
          paragraphs: [
            "L'accès à votre compte repose sur des jetons JWT de courte durée, accompagnés d'un jeton de renouvellement, transmis via des cookies sécurisés (HttpOnly, avec l'attribut Secure en production). Une authentification à deux facteurs (application d'authentification ou code par e-mail) est disponible pour renforcer la protection des comptes.",
          ],
        },
        {
          heading: '3. Contrôle d\'accès et isolation des données',
          paragraphs: [
            "Innovacar est une plateforme multi-agence : les données de chaque agence sont strictement cloisonnées et inaccessibles aux autres agences. Au sein d'une agence, des rôles et permissions déterminent ce que chaque employé peut consulter ou modifier.",
          ],
        },
        {
          heading: '4. Protection contre les abus',
          paragraphs: [
            "Les tentatives de connexion sont limitées en fréquence (limitation de débit) et un verrouillage temporaire est appliqué après plusieurs échecs consécutifs, afin de limiter les attaques par force brute.",
          ],
        },
        {
          heading: '5. Journalisation et audit',
          paragraphs: [
            "Les actions sensibles (connexions, modifications de données critiques, actions d'administration) sont enregistrées dans des journaux d'audit, consultables par les administrateurs autorisés de chaque agence pour assurer la traçabilité.",
          ],
        },
        {
          heading: '6. Sauvegardes',
          paragraphs: [
            "La base de données est sauvegardée régulièrement afin de permettre une restauration en cas d'incident.",
          ],
        },
        {
          heading: '7. Infrastructure et disponibilité',
          paragraphs: [
            "Le service est hébergé chez des fournisseurs d'infrastructure cloud reconnus, avec surveillance de la disponibilité. Des opérations de maintenance planifiée peuvent occasionnellement interrompre temporairement le service ; elles sont annoncées lorsque cela est raisonnablement possible.",
          ],
        },
        {
          heading: '8. Signaler un problème de sécurité',
          paragraphs: [
            "Si vous identifiez une vulnérabilité de sécurité, merci de nous la signaler de manière responsable via notre page de contact plutôt que de la divulguer publiquement. Nous nous engageons à examiner tout signalement rapidement.",
          ],
        },
      ]}
    />
  );
}

export const MARKETING_PAGES: Record<string, { meta: MarketingPageMeta; Component: () => ReturnType<typeof HomePage> }> = {
  '/': {
    meta: {
      path: '/',
      title: 'Innovacar | Logiciel de gestion pour agences de location de voitures',
      description: `Innovacar centralise flotte, contrats, paiements et suivi GPS pour les agences de location de voitures au Maroc. ${TRIAL_DAYS ? `Essai gratuit de ${TRIAL_DAYS} jours.` : 'Essai gratuit disponible.'}`,
    },
    Component: HomePage,
  },
  '/fonctionnalites': {
    meta: {
      path: '/fonctionnalites',
      title: 'Fonctionnalités | Innovacar',
      description: 'Gestion de flotte, contrats et signature électronique, suivi GPS, paiements, rapports et support — les fonctionnalités d’Innovacar pour votre agence.',
    },
    Component: FeaturesPage,
  },
  '/tarifs': {
    meta: {
      path: '/tarifs',
      title: 'Essai gratuit | Innovacar',
      description: "Démarrez un essai gratuit d'Innovacar, sans carte bancaire, ou contactez-nous pour un devis adapté à votre agence.",
    },
    Component: PricingPage,
  },
  '/desktop': {
    meta: {
      path: '/desktop',
      title: 'Innovacar Desktop | Application Windows',
      description: "Téléchargez Innovacar pour Windows : notifications natives, PDF sécurisés, impression et accès rapide, connecté aux mêmes données que la version web.",
    },
    Component: DesktopPage,
  },
  '/confidentialite': {
    meta: {
      path: '/confidentialite',
      title: 'Politique de confidentialité | Innovacar',
      description: "Comment Innovacar et Innovax Technologies collectent, utilisent et protègent les données de votre agence et de vos clients.",
    },
    Component: PrivacyPage,
  },
  '/conditions': {
    meta: {
      path: '/conditions',
      title: "Conditions d'utilisation | Innovacar",
      description: "Conditions d'utilisation d'Innovacar : abonnements, essai gratuit, annulation, responsabilités de l'agence et d'Innovax Technologies.",
    },
    Component: TermsPage,
  },
  '/cookies': {
    meta: {
      path: '/cookies',
      title: 'Politique de cookies | Innovacar',
      description: "Quels cookies Innovacar utilise réellement — uniquement des cookies de session strictement nécessaires, aucun cookie publicitaire ou de suivi.",
    },
    Component: CookiesPage,
  },
  '/securite': {
    meta: {
      path: '/securite',
      title: 'Sécurité | Innovacar',
      description: "Chiffrement, authentification à deux facteurs, isolation des données par agence, sauvegardes et journaux d'audit — comment Innovacar protège vos données.",
    },
    Component: SecurityPage,
  },
  // English-named aliases of the pages above — same components/content, reachable
  // under the English path visitors and external links are likely to guess/use.
  // The French paths above stay canonical for the sitemap/meta; these are pure
  // aliases so no visitor hits a dead/fallback-to-home page for these names.
  '/features': {
    meta: {
      path: '/features',
      title: 'Features | Innovacar',
      description: 'Fleet management, contracts and electronic signature, GPS tracking, payments, reports and support — Innovacar’s features for your agency.',
    },
    Component: FeaturesPage,
  },
  '/pricing': {
    meta: {
      path: '/pricing',
      title: 'Free trial | Innovacar',
      description: 'Start a free trial of Innovacar, no credit card required, or contact us for a quote fitted to your agency.',
    },
    Component: PricingPage,
  },
  '/privacy': {
    meta: {
      path: '/privacy',
      title: 'Privacy Policy | Innovacar',
      description: 'How Innovacar and Innovax Technologies collect, use and protect your agency’s and your clients’ data.',
    },
    Component: PrivacyPage,
  },
  '/terms': {
    meta: {
      path: '/terms',
      title: 'Terms of Use | Innovacar',
      description: 'Innovacar terms of use: subscriptions, free trial, cancellation, agency and Innovax Technologies responsibilities.',
    },
    Component: TermsPage,
  },
  '/security': {
    meta: {
      path: '/security',
      title: 'Security | Innovacar',
      description: 'Encryption, two-factor authentication, per-agency data isolation, backups and audit logs — how Innovacar protects your data.',
    },
    Component: SecurityPage,
  },
};

export const MARKETING_PATHS: readonly string[] = Object.keys(MARKETING_PAGES);
