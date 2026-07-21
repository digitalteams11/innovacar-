import './marketing.css';
import { MARKETING_PAGES } from './pages';

/**
 * Client-side mount for the public marketing site. Rendered directly into
 * #root by main.tsx — bypassing HashRouter/AuthProvider entirely — only when
 * the visitor has no auth token and is on a bare, hash-free marketing path.
 * See shouldRenderMarketingSite() in main.tsx for the exact condition.
 */
export default function MarketingApp() {
  const path = window.location.pathname;
  const entry = MARKETING_PAGES[path] ?? MARKETING_PAGES['/'];
  const { Component } = entry;
  return <Component />;
}
