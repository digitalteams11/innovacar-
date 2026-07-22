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

// Footer-only — kept separate from NAV_LINKS so the header nav stays short;
// legal pages belong in a footer, not primary navigation.
const LEGAL_LINKS: Array<{ href: string; label: string }> = [
  { href: '/confidentialite', label: 'Confidentialité' },
  { href: '/conditions', label: 'Conditions d’utilisation' },
  { href: '/cookies', label: 'Cookies' },
  { href: '/securite', label: 'Sécurité' },
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
      <nav aria-label="Mentions légales">
        {LEGAL_LINKS.map((link) => (
          <a key={link.href} href={link.href}>{link.label}</a>
        ))}
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

const LEGAL_UPDATED = '22 juillet 2026';

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
            "Innovacar propose un essai gratuit de 30 jours, sans engagement, permettant de tester le service avant de souscrire un abonnement payant (Basic, Standard ou Premium). À l'issue de l'essai, l'accès aux fonctionnalités payantes nécessite la souscription d'un abonnement actif. Les tarifs affichés sont indicatifs et peuvent évoluer ; toute modification tarifaire sera communiquée à l'avance aux agences abonnées.",
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
