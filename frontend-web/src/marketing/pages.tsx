import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

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
// ─────────────────────────────────────────────────────────────────────────
type EnvBag = Record<string, string | boolean | undefined>;
const ENV: EnvBag = ((import.meta as unknown as { env?: EnvBag })?.env) ?? {};

function envStr(key: string): string {
  const v = ENV[key];
  return typeof v === 'string' ? v.trim() : '';
}
function envBool(key: string): boolean {
  const v = ENV[key];
  return v === true || v === 'true' || v === '1';
}

const INNOVAX_URL = envStr('VITE_INNOVAX_WEBSITE_URL');
const COMPANY_NAME = envStr('VITE_COMPANY_NAME') || 'Innovax Technologies';
const TRIAL_DAYS = envStr('VITE_TRIAL_DURATION_DAYS');
const TRIAL_PROMO_ENABLED = envBool('VITE_TRIAL_PROMO_ENABLED');
const TRIAL_LABEL_OVERRIDE = envStr('VITE_TRIAL_LABEL');
const DESKTOP_AVAILABLE = envBool('VITE_DESKTOP_AVAILABLE');
const DESKTOP_URL = envStr('VITE_DESKTOP_DOWNLOAD_URL');
const DESKTOP_PLATFORM = envStr('VITE_DESKTOP_PLATFORM') || 'Windows';
const CONTACT_EMAIL = envStr('VITE_CONTACT_EMAIL');
const CONTACT_WHATSAPP_DIGITS = envStr('VITE_CONTACT_WHATSAPP').replace(/[^\d]/g, '');

/** True once this module runs in a real browser (never during SSR/prerender). */
function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

/**
 * Minimal duplicate of src/lib/api.ts's base-URL resolution. Can't import
 * that module here (zero-import constraint above) — this file must also
 * execute standalone in the Node prerender script, which has neither
 * import.meta.env substitution nor `window`.
 */
function apiBase(): string {
  const configured = envStr('VITE_API_URL');
  if (configured) {
    const trimmed = configured.replace(/\/+$/, '');
    return /\/api$/.test(trimmed) ? trimmed : `${trimmed}/api`;
  }
  if (!isBrowser()) return 'https://api.innovacar.app/api';
  if (ENV.PROD) return 'https://api.innovacar.app/api';
  return `http://${window.location.hostname}:8082/api`;
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
  navPricing: { fr: 'Tarifs', en: 'Pricing', ar: 'الأسعار' },
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
  desktopCardTitle: { fr: `Application ${DESKTOP_PLATFORM}`, en: `${DESKTOP_PLATFORM} application`, ar: `تطبيق ${DESKTOP_PLATFORM}` },
  desktopCardBody: {
    fr: `Une expérience dédiée pour ${DESKTOP_PLATFORM}, connectée au même compte Innovacar et aux mêmes données que la version web.`,
    en: `A dedicated experience for ${DESKTOP_PLATFORM}, connected to the same Innovacar account and the same data as the web version.`,
    ar: `تجربة مخصصة لنظام ${DESKTOP_PLATFORM}، متصلة بنفس حساب Innovacar ونفس بيانات النسخة الإلكترونية.`,
  },
  desktopDownload: { fr: 'Télécharger', en: 'Download', ar: 'تحميل' },
  desktopSoon: { fr: 'Bientôt disponible', en: 'Coming soon', ar: 'قريباً' },
  desktopWaitlist: { fr: "M'avertir à la disponibilité", en: 'Notify me when available', ar: 'أعلمني عند التوفر' },

  pricingTitle: { fr: 'Des tarifs adaptés à la taille de votre agence', en: 'Pricing that fits the size of your agency', ar: 'أسعار تناسب حجم وكالتك' },
  pricingSub: {
    fr: "Tarifs en dirhams marocains (MAD). Changez de formule à tout moment.",
    en: 'Prices in Moroccan dirhams (MAD). Change plans at any time.',
    ar: 'الأسعار بالدرهم المغربي. يمكنك تغيير باقتك في أي وقت.',
  },
  pricingLoadError: {
    fr: 'Nos formules sont momentanément indisponibles. Contactez-nous pour un devis.',
    en: 'Our plans are temporarily unavailable. Contact us for a quote.',
    ar: 'باقاتنا غير متوفرة مؤقتاً. تواصل معنا للحصول على عرض سعر.',
  },
  pricingRecommended: { fr: 'Recommandé', en: 'Recommended', ar: 'موصى به' },
  perMonth: { fr: '/mois', en: '/mo', ar: '/شهر' },
  vehiclesUpTo: { fr: "Jusqu'à {n} véhicules", en: 'Up to {n} vehicles', ar: 'حتى {n} مركبة' },
  employeesUpTo: { fr: "Jusqu'à {n} employés", en: 'Up to {n} employees', ar: 'حتى {n} موظف' },
  gpsUpTo: { fr: 'Suivi GPS ({n} appareils)', en: 'GPS tracking ({n} devices)', ar: 'تتبع GPS ({n} أجهزة)' },
  trialEligible: { fr: "Essai gratuit inclus", en: 'Free trial included', ar: 'تجربة مجانية متضمنة' },
  choosePlan: { fr: 'Choisir cette formule', en: 'Choose this plan', ar: 'اختر هذه الباقة' },
  contactUs: { fr: 'Contactez-nous', en: 'Contact us', ar: 'تواصل معنا' },

  trialTitle: { fr: 'Testez Innovacar gratuitement', en: 'Try Innovacar for free', ar: 'جرّب Innovacar مجاناً' },
  trialBody: {
    fr: 'Configurez votre agence, ajoutez vos véhicules et découvrez les outils essentiels sans engagement.',
    en: 'Set up your agency, add your vehicles and explore the essential tools, no commitment.',
    ar: 'أعدّ وكالتك، أضف مركباتك واكتشف الأدوات الأساسية دون أي التزام.',
  },
  trialCta: { fr: 'Commencer mon essai gratuit', en: 'Start my free trial', ar: 'ابدأ تجربتي المجانية' },
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
};

function t(lang: Lang, key: keyof typeof UI): string {
  return UI[key][lang] ?? UI[key].fr;
}
function fmt(s: string, vars: Record<string, string | number>): string {
  return Object.entries(vars).reduce((acc, [k, v]) => acc.replaceAll(`{${k}}`, String(v)), s);
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
  { key: 'navPricing', id: 'pricing' },
  { key: 'navFaq', id: 'faq' },
  { key: 'navContact', id: 'contact' },
];
// Footer-only legal links.
const LEGAL_LINKS: Array<{ href: string; label: string }> = [
  { href: '/confidentialite', label: 'Confidentialité' },
  { href: '/conditions', label: 'Conditions d’utilisation' },
  { href: '/cookies', label: 'Cookies' },
  { href: '/securite', label: 'Sécurité' },
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

function Header() {
  const { lang } = useLang();
  const [open, setOpen] = useState(false);
  return (
    <header className="im-header">
      <a href="/" className="im-brand">
        <img src="/brand/innovacar-logo.png" alt="Innovacar" width={36} height={36} />
        <span>Innovacar</span>
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
        <a href="/#/login" className="im-btn im-btn-ghost">{t(lang, 'login')}</a>
        <a href={registerHref()} className="im-btn im-btn-primary">{t(lang, 'startTrial')}</a>
        <button
          type="button"
          className="im-menu-toggle"
          aria-label={open ? t(lang, 'closeMenu') : t(lang, 'openMenu')}
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          <Icon d={open ? ICONS.close : ICONS.menu} />
        </button>
      </div>

      {open && (
        <div className="im-mobile-drawer" role="dialog" aria-label="Menu">
          {IN_PAGE_NAV.map((item) => (
            <button
              key={item.id}
              type="button"
              className="im-nav-link"
              onClick={() => { setOpen(false); scrollToId(item.id); }}
            >
              {t(lang, item.key)}
            </button>
          ))}
          <div className="im-mobile-drawer-actions">
            <a href="/#/login" className="im-btn im-btn-ghost">{t(lang, 'login')}</a>
            <a href={registerHref()} className="im-btn im-btn-primary">{t(lang, 'startTrial')}</a>
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
          <button type="button" onClick={() => scrollToId('pricing')}>{t(lang, 'navPricing')}</button>
          <a href="/#/login">{t(lang, 'login')}</a>
          <a href={registerHref()}>{t(lang, 'startTrial')}</a>
          <button type="button" onClick={() => scrollToId('web-desktop')}>{t(lang, 'footerWebApp')}</button>
          <button type="button" onClick={() => scrollToId('web-desktop')}>{t(lang, 'footerDesktopApp')}</button>
        </div>

        <div className="im-footer-col">
          <h4>{t(lang, 'footerLegal')}</h4>
          {LEGAL_LINKS.map((link) => (
            <a key={link.href} href={link.href}>{link.label}</a>
          ))}
        </div>

        <div className="im-footer-col">
          <h4>{t(lang, 'footerCompany')}</h4>
          {INNOVAX_URL ? (
            <a href={INNOVAX_URL} target="_blank" rel="noopener noreferrer">{COMPANY_NAME}</a>
          ) : (
            <span className="im-footer-static">{COMPANY_NAME}</span>
          )}
          <a href="/#/contact">{t(lang, 'navContact')}</a>
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
const FAQ_ITEMS: Array<{ q: Dict; a: Dict }> = [
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
    a: {
      fr: DESKTOP_AVAILABLE
        ? `Oui, une application ${DESKTOP_PLATFORM} est disponible, connectée aux mêmes données que la version web.`
        : `Une application ${DESKTOP_PLATFORM} est en préparation. En attendant, la version web fonctionne sur ordinateur sans installation.`,
      en: DESKTOP_AVAILABLE
        ? `Yes, a ${DESKTOP_PLATFORM} application is available, connected to the same data as the web version.`
        : `A ${DESKTOP_PLATFORM} application is in the works. In the meantime, the web version works on desktop with no install.`,
      ar: DESKTOP_AVAILABLE
        ? `نعم، يتوفر تطبيق ${DESKTOP_PLATFORM} متصل بنفس بيانات النسخة الإلكترونية.`
        : `تطبيق ${DESKTOP_PLATFORM} قيد التحضير. في غضون ذلك، تعمل نسخة الويب على الحاسوب دون تثبيت.`,
    },
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

function Faq() {
  const { lang } = useLang();
  return (
    <div className="im-faq">
      {FAQ_ITEMS.map((item) => (
        <details key={item.q.fr} className="im-faq-item">
          <summary>
            <span>{item.q[lang]}</span>
            <Icon d={ICONS.chevron} size={18} />
          </summary>
          <p>{item.a[lang]}</p>
        </details>
      ))}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────
// Pricing (Section 9) — fetches the real backend plans (GET /api/public/plans,
// unauthenticated). Falls back to a "contact us" message on failure rather
// than ever inventing a price.
// ─────────────────────────────────────────────────────────────────────────
interface PublicPlan {
  code: string;
  name: string;
  description?: string;
  monthlyPrice?: number;
  yearlyPrice?: number;
  currency?: string;
  maxVehicles?: number;
  maxEmployees?: number;
  maxGpsDevices?: number;
  trialDays?: number;
  highlighted?: boolean;
}

function usePublicPlans() {
  const [plans, setPlans] = useState<PublicPlan[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    fetch(`${apiBase()}/public/plans`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(String(res.status)))))
      .then((data) => { if (!cancelled) setPlans(Array.isArray(data) ? data : []); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, []);
  return { plans, failed };
}

function PricingSection() {
  const { lang } = useLang();
  const { plans, failed } = usePublicPlans();

  if (failed || (plans && plans.length === 0)) {
    return <p className="im-pricing-fallback">{t(lang, 'pricingLoadError')}</p>;
  }
  if (!plans) {
    return <p className="im-pricing-fallback im-pricing-loading">…</p>;
  }

  return (
    <div className="im-grid im-grid-pricing">
      {plans.map((plan) => (
        <div key={plan.code} className={`im-card im-pricing-card${plan.highlighted ? ' im-pricing-card-highlight' : ''}`}>
          {plan.highlighted && <span className="im-pricing-badge">{t(lang, 'pricingRecommended')}</span>}
          <h3>{plan.name}</h3>
          {typeof plan.monthlyPrice === 'number' && (
            <p className="im-pricing-price">
              {plan.monthlyPrice.toLocaleString(lang === 'ar' ? 'ar-MA' : lang)} {plan.currency || 'MAD'}
              <span>{t(lang, 'perMonth')}</span>
            </p>
          )}
          {plan.description && <p className="im-pricing-tagline">{plan.description}</p>}
          <ul>
            {typeof plan.maxVehicles === 'number' && <li>{fmt(t(lang, 'vehiclesUpTo'), { n: plan.maxVehicles })}</li>}
            {typeof plan.maxEmployees === 'number' && <li>{fmt(t(lang, 'employeesUpTo'), { n: plan.maxEmployees })}</li>}
            {typeof plan.maxGpsDevices === 'number' && plan.maxGpsDevices > 0 && <li>{fmt(t(lang, 'gpsUpTo'), { n: plan.maxGpsDevices })}</li>}
            {typeof plan.trialDays === 'number' && plan.trialDays > 0 && <li>{t(lang, 'trialEligible')}</li>}
          </ul>
          <a href={registerHref({ plan: plan.code })} className="im-btn im-btn-primary">{t(lang, 'choosePlan')}</a>
        </div>
      ))}
    </div>
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
          <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg">{t(lang, 'heroPrimaryCta')} — {trialLabel(lang)}</a>
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
            <a href={registerHref()} className="im-btn im-btn-primary">{t(lang, 'startTrial')}</a>
          </div>
          <div className="im-card im-webdesktop-card">
            <div className="im-feature-icon"><Icon d={ICONS.monitor} /></div>
            <h3>{t(lang, 'desktopCardTitle')}</h3>
            <p>{t(lang, 'desktopCardBody')}</p>
            {DESKTOP_AVAILABLE && DESKTOP_URL ? (
              <a href={DESKTOP_URL} className="im-btn im-btn-primary" target="_blank" rel="noopener noreferrer">
                {t(lang, 'desktopDownload')}
              </a>
            ) : (
              <div className="im-desktop-soon">
                <span className="im-badge">{t(lang, 'desktopSoon')}</span>
                <button type="button" className="im-btn im-btn-ghost" onClick={() => scrollToId('contact')}>
                  {t(lang, 'desktopWaitlist')}
                </button>
              </div>
            )}
          </div>
        </div>
      </section>

      <section id="pricing" className="im-section">
        <h2>{t(lang, 'pricingTitle')}</h2>
        <p className="im-section-sub">{t(lang, 'pricingSub')}</p>
        <PricingSection />
      </section>

      <section id="trial" className="im-section im-trial">
        <h2>{t(lang, 'trialTitle')}</h2>
        <p className="im-section-sub">{t(lang, 'trialBody')}</p>
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg">{t(lang, 'trialCta')}</a>
        <ul className="im-trial-notes">
          <li>{t(lang, 'trialNoCard')}</li>
          <li>{t(lang, 'trialCancel')}</li>
          <li>{t(lang, 'trialSupport')}</li>
        </ul>
      </section>

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
              <a href={link.href}>{link.label}</a>
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
          <a href="/#/contact" className="im-btn im-btn-primary">{t(lang, 'contactOpenForm')}</a>
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
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg">{t(lang, 'heroPrimaryCta')}</a>
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
        <h1>Fonctionnalités</h1>
        <p className="im-hero-sub">
          Découvrez les outils qu'Innovacar met à la disposition de votre agence de location de voitures.
        </p>
      </section>
      <section className="im-section">
        <FeatureGrid />
      </section>
      <section className="im-section im-cta">
        <h2>Voir Innovacar en action</h2>
        <a href={registerHref()} className="im-btn im-btn-primary im-btn-lg">{t(lang, 'heroPrimaryCta')}</a>
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
        <h1>Tarifs</h1>
        <p className="im-hero-sub">
          {trialLabel(lang)}, puis un tarif adapté à la taille de votre agence.
          Tarifs indicatifs en dirhams marocains (MAD).
        </p>
      </section>
      <section className="im-section">
        <PricingSection />
      </section>
      <section className="im-section im-cta">
        <h2>Une question sur nos tarifs ?</h2>
        <a href="/#/contact" className="im-btn im-btn-ghost im-btn-lg">Contactez-nous</a>
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
            "Les présentes conditions régissent l'utilisation d'Innovacar, plateforme SaaS de gestion pour agences de location de voitures éditée par Innovax Technologies. En créant un compte ou en utilisant le service, votre agence accepte ces conditions.",
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
      title: 'Tarifs | Innovacar',
      description: "Découvrez les formules Basic, Standard et Premium d'Innovacar, avec un essai gratuit, adaptées à la taille de votre agence.",
    },
    Component: PricingPage,
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
};

export const MARKETING_PATHS: readonly string[] = Object.keys(MARKETING_PAGES);
