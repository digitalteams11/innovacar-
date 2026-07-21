import type { ReactNode } from 'react';

/**
 * The public marketing site. Deliberately self-contained (no imports from
 * outside this file besides `react`) because scripts/prerender-marketing.mjs
 * transforms and imports this exact file at build time, outside the normal
 * Vite graph, to render it to static HTML for crawlers. Keep it that way —
 * do not import app components (AuthContext, i18n, etc.) here.
 *
 * French-only for now. English/Arabic versions are a deliberate follow-up
 * (see docs/seo-route-inventory.md) — do not add hreflang tags until those
 * alternate-language pages actually exist.
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

const NAV_LINKS: Array<{ href: string; label: string }> = [
  { href: '/fonctionnalites', label: 'Fonctionnalités' },
  { href: '/tarifs', label: 'Tarifs' },
  { href: '/#/contact', label: 'Contact' },
];

function Header() {
  return (
    <header className="im-header">
      <a href="/" className="im-brand">
        <img src="/brand/innovacar-logo.png" alt="Innovacar" width={36} height={36} />
        <span>Innovacar</span>
      </a>
      <nav aria-label="Navigation principale">
        {NAV_LINKS.map((link) => (
          <a key={link.href} href={link.href}>{link.label}</a>
        ))}
      </nav>
      <div className="im-header-actions">
        <a href="/#/login" className="im-btn im-btn-ghost">Connexion</a>
        <a href="/#/register" className="im-btn im-btn-primary">Essai gratuit</a>
      </div>
    </header>
  );
}

function Footer() {
  return (
    <footer className="im-footer">
      <div className="im-footer-brand">
        <strong>Innovacar</strong>
        <p>Un produit d'Innovax Technologies.</p>
      </div>
      <nav aria-label="Liens du pied de page">
        {NAV_LINKS.map((link) => (
          <a key={link.href} href={link.href}>{link.label}</a>
        ))}
        <a href="/#/login">Connexion</a>
      </nav>
      <p className="im-footer-copy">&copy; {new Date().getFullYear()} Innovax Technologies. Tous droits réservés.</p>
    </footer>
  );
}

function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="im-page">
      <Header />
      <main>{children}</main>
      <Footer />
    </div>
  );
}

const CORE_FEATURES: Array<{ title: string; description: string }> = [
  { title: 'Gestion de flotte', description: "Suivez chaque véhicule de votre parc : disponibilité, kilométrage, entretien et historique des réservations en un seul endroit." },
  { title: 'Contrats & signature électronique', description: 'Générez des contrats de location, faites-les signer électroniquement par le client et gardez chaque PDF archivé automatiquement.' },
  { title: 'Suivi GPS', description: 'Surveillez la position de vos véhicules équipés d’un traceur GPS et recevez une alerte si un appareil passe hors ligne.' },
  { title: 'Paiements & factures', description: 'Enregistrez les paiements de vos clients et générez des factures liées à chaque contrat et réservation.' },
  { title: 'Rapports & statistiques', description: "Consultez des rapports sur l'activité de votre agence : réservations, revenus et utilisation de la flotte." },
  { title: 'Support & centre d’aide', description: 'Vos équipes et vos clients peuvent ouvrir des tickets de support, suivis depuis un centre d’aide intégré.' },
  { title: 'Multi-agence & permissions', description: 'Gérez plusieurs agences ou succursales, avec des rôles et permissions distincts pour chaque employé.' },
  { title: 'Multi-langue', description: "Interface disponible en français, anglais et arabe, pour votre équipe comme pour vos clients." },
];

function FeatureGrid({ items }: { items: typeof CORE_FEATURES }) {
  return (
    <div className="im-grid">
      {items.map((f) => (
        <div key={f.title} className="im-card">
          <h3>{f.title}</h3>
          <p>{f.description}</p>
        </div>
      ))}
    </div>
  );
}

function HomePage() {
  return (
    <Layout>
      <section className="im-hero">
        <h1>Le logiciel de gestion pour agences de location de voitures</h1>
        <p className="im-hero-sub">
          Innovacar centralise la flotte, les contrats, les paiements et le suivi GPS de votre
          agence de location de voitures au Maroc, dans une seule plateforme.
        </p>
        <div className="im-hero-actions">
          <a href="/#/register" className="im-btn im-btn-primary im-btn-lg">Commencer l'essai gratuit</a>
          <a href="/fonctionnalites" className="im-btn im-btn-ghost im-btn-lg">Voir les fonctionnalités</a>
        </div>
      </section>

      <section className="im-section">
        <h2>Tout ce qu'il faut pour piloter votre agence</h2>
        <FeatureGrid items={CORE_FEATURES} />
      </section>

      <section className="im-section im-how">
        <h2>Comment ça marche</h2>
        <ol className="im-steps">
          <li>
            <strong>1. Créez votre compte</strong>
            <p>Inscrivez votre agence et configurez votre flotte, vos employés et vos tarifs.</p>
          </li>
          <li>
            <strong>2. Gérez vos locations</strong>
            <p>Créez réservations, contrats et factures, et suivez vos véhicules en GPS.</p>
          </li>
          <li>
            <strong>3. Suivez votre activité</strong>
            <p>Consultez vos rapports et pilotez votre agence depuis un tableau de bord unique.</p>
          </li>
        </ol>
      </section>

      <section className="im-section im-cta">
        <h2>Prêt à essayer Innovacar ?</h2>
        <p>Démarrez un essai gratuit, sans engagement.</p>
        <a href="/#/register" className="im-btn im-btn-primary im-btn-lg">Commencer l'essai gratuit</a>
      </section>
    </Layout>
  );
}

function FeaturesPage() {
  return (
    <Layout>
      <section className="im-hero im-hero-compact">
        <h1>Fonctionnalités</h1>
        <p className="im-hero-sub">
          Découvrez les outils qu'Innovacar met à la disposition de votre agence de location de voitures.
        </p>
      </section>
      <section className="im-section">
        <FeatureGrid items={CORE_FEATURES} />
      </section>
      <section className="im-section im-cta">
        <h2>Voir Innovacar en action</h2>
        <a href="/#/register" className="im-btn im-btn-primary im-btn-lg">Commencer l'essai gratuit</a>
      </section>
    </Layout>
  );
}

interface PricingTier {
  name: string;
  tagline: string;
  highlights: string[];
}

const PRICING_TIERS: PricingTier[] = [
  {
    name: 'Basic',
    tagline: "À partir de 199 MAD/mois — pour les petites flottes",
    highlights: ['Jusqu\'à 10 véhicules', 'Jusqu\'à 2 employés', 'Contrats et paiements', 'Support standard'],
  },
  {
    name: 'Standard',
    tagline: 'Pour les agences en croissance',
    highlights: ['Jusqu\'à 30 véhicules', 'Jusqu\'à 5 employés', 'Suivi GPS (jusqu\'à 5 appareils)', 'Rapports avancés'],
  },
  {
    name: 'Premium',
    tagline: 'Pour les opérations à grande échelle',
    highlights: ['Flotte et équipe étendues', 'Suivi GPS à grande échelle', 'Marque blanche (white-label)', 'Support prioritaire'],
  },
];

function PricingPage() {
  return (
    <Layout>
      <section className="im-hero im-hero-compact">
        <h1>Tarifs</h1>
        <p className="im-hero-sub">
          Un essai gratuit de 30 jours, puis un tarif adapté à la taille de votre agence.
          Tarifs indicatifs en dirhams marocains (MAD) — contactez-nous pour un devis précis.
        </p>
      </section>
      <section className="im-section">
        <div className="im-grid im-grid-pricing">
          {PRICING_TIERS.map((tier) => (
            <div key={tier.name} className="im-card im-pricing-card">
              <h3>{tier.name}</h3>
              <p className="im-pricing-tagline">{tier.tagline}</p>
              <ul>
                {tier.highlights.map((h) => <li key={h}>{h}</li>)}
              </ul>
              <a href="/#/register" className="im-btn im-btn-primary">Commencer l'essai gratuit</a>
            </div>
          ))}
        </div>
      </section>
      <section className="im-section im-cta">
        <h2>Une question sur nos tarifs ?</h2>
        <a href="/#/contact" className="im-btn im-btn-ghost im-btn-lg">Contactez-nous</a>
      </section>
    </Layout>
  );
}

export const MARKETING_PAGES: Record<string, { meta: MarketingPageMeta; Component: () => ReturnType<typeof HomePage> }> = {
  '/': {
    meta: {
      path: '/',
      title: 'Innovacar | Logiciel de gestion pour agences de location de voitures',
      description: "Innovacar centralise flotte, contrats, paiements et suivi GPS pour les agences de location de voitures au Maroc. Essai gratuit de 30 jours.",
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
      description: "Découvrez les formules Basic, Standard et Premium d'Innovacar, à partir de 199 MAD/mois, avec un essai gratuit de 30 jours.",
    },
    Component: PricingPage,
  },
};

export const MARKETING_PATHS: readonly string[] = Object.keys(MARKETING_PAGES);
