import { useEffect } from 'react';

/**
 * Every tag this component can write is tagged with data-seo-managed="1" and
 * fully cleared before each render's tags are written. That's what stops
 * route changes (this mounts fresh per page since pages aren't kept alive)
 * from ever leaving a stale or duplicate <meta>/<link> behind — no external
 * head-management library needed for a page count this small.
 */

export interface SeoHeadProps {
  title: string;
  description: string;
  /** Absolute canonical URL, e.g. "https://innovacar.app/contact". Always required — never omit to "inherit" a default. */
  canonical: string;
  /** Defaults to "noindex,follow,noarchive" — pages must opt IN to being indexable, not opt out. */
  robots?: string;
  ogType?: 'website' | 'article';
  ogImage?: string;
  jsonLd?: Record<string, unknown> | Record<string, unknown>[];
}

const MANAGED_SELECTOR = '[data-seo-managed="1"]';
const SITE_NAME = 'Innovacar';
const DEFAULT_OG_IMAGE = 'https://innovacar.app/og/innovacar-og.jpg';

function setMeta(attr: 'name' | 'property', key: string, content: string) {
  const el = document.createElement('meta');
  el.setAttribute(attr, key);
  el.setAttribute('content', content);
  el.setAttribute('data-seo-managed', '1');
  document.head.appendChild(el);
}

function setLink(rel: string, href: string, extra?: Record<string, string>) {
  const el = document.createElement('link');
  el.setAttribute('rel', rel);
  el.setAttribute('href', href);
  if (extra) Object.entries(extra).forEach(([k, v]) => el.setAttribute(k, v));
  el.setAttribute('data-seo-managed', '1');
  document.head.appendChild(el);
}

export default function SeoHead({
  title,
  description,
  canonical,
  robots = 'noindex,follow,noarchive',
  ogType = 'website',
  ogImage = DEFAULT_OG_IMAGE,
  jsonLd,
}: SeoHeadProps) {
  useEffect(() => {
    document.querySelectorAll(MANAGED_SELECTOR).forEach((el) => el.remove());

    const fullTitle = title.includes(SITE_NAME) ? title : `${title} | ${SITE_NAME}`;
    document.title = fullTitle;

    setMeta('name', 'description', description);
    setMeta('name', 'robots', robots);
    setLink('canonical', canonical);

    setMeta('property', 'og:site_name', SITE_NAME);
    setMeta('property', 'og:title', fullTitle);
    setMeta('property', 'og:description', description);
    setMeta('property', 'og:url', canonical);
    setMeta('property', 'og:type', ogType);
    setMeta('property', 'og:image', ogImage);
    setMeta('property', 'og:image:width', '1200');
    setMeta('property', 'og:image:height', '630');

    setMeta('name', 'twitter:card', 'summary_large_image');
    setMeta('name', 'twitter:title', fullTitle);
    setMeta('name', 'twitter:description', description);
    setMeta('name', 'twitter:image', ogImage);

    if (jsonLd) {
      const script = document.createElement('script');
      script.type = 'application/ld+json';
      script.setAttribute('data-seo-managed', '1');
      script.textContent = JSON.stringify(jsonLd);
      document.head.appendChild(script);
    }

    return () => {
      document.querySelectorAll(MANAGED_SELECTOR).forEach((el) => el.remove());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, description, canonical, robots, ogType, ogImage, JSON.stringify(jsonLd)]);

  return null;
}
