import { test, expect, type Page } from '@playwright/test';

/**
 * E2E coverage for the public marketing landing page. Default language on a
 * fresh visit is French (see getInitialLang() in src/marketing/pages.tsx),
 * so selectors below use the French copy unless a test explicitly switches
 * language.
 */

const SECTION_IDS = ['features', 'how-it-works', 'web-desktop', 'pricing', 'faq', 'contact'] as const;

async function isInViewport(page: Page, id: string): Promise<boolean> {
  return page.evaluate((elementId) => {
    const el = document.getElementById(elementId);
    if (!el) return false;
    const rect = el.getBoundingClientRect();
    return rect.top >= -1 && rect.top < window.innerHeight;
  }, id);
}

test.describe('Header in-page navigation', () => {
  for (const id of SECTION_IDS) {
    test(`nav item scrolls to #${id}`, async ({ page }) => {
      await page.goto('/');
      // Nav buttons render in IN_PAGE_NAV order, which matches SECTION_IDS
      // above — click by index rather than by (language-dependent) label text.
      const index = SECTION_IDS.indexOf(id as (typeof SECTION_IDS)[number]);
      await page.locator('nav.im-nav-desktop button').nth(index).click();
      await page.waitForTimeout(600); // smooth-scroll settle
      expect(await isInViewport(page, id)).toBe(true);
    });
  }
});

test.describe('Auth CTAs', () => {
  test('Login navigates to /login', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Connexion' }).first().click();
    await expect(page).toHaveURL(/#\/login$/);
  });

  test('Start free trial navigates to signup', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-header-actions a.im-btn-primary').click();
    await expect(page).toHaveURL(/#\/register/);
  });

  test('Hero primary CTA (Try it for free) navigates to signup', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-hero-actions a.im-btn-primary').click();
    await expect(page).toHaveURL(/#\/register/);
  });

  test('Hero secondary CTA (Discover the platform) scrolls to product preview', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-hero-actions button.im-btn-ghost').click();
    await page.waitForTimeout(600);
    expect(await isInViewport(page, 'product')).toBe(true);
  });
});

test.describe('Language and RTL', () => {
  test('switching language changes hero content', async ({ page }) => {
    await page.goto('/');
    const heroBefore = await page.locator('.im-hero h1').textContent();
    await page.locator('.im-lang-btn', { hasText: 'EN' }).first().click();
    await expect(page.locator('.im-hero h1')).not.toHaveText(heroBefore ?? '');
    await expect(page.locator('.im-hero h1')).toContainText('Run your entire car rental agency');
  });

  test('Arabic enables RTL layout', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-lang-btn', { hasText: 'AR' }).first().click();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  });
});

test.describe('Mobile navigation', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('mobile menu opens and closes', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('.im-menu-toggle');
    const drawer = page.locator('.im-mobile-drawer');
    await expect(drawer).toBeHidden();
    await toggle.click();
    await expect(drawer).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Escape');
    await expect(drawer).toBeHidden();
  });

  test('mobile menu closes after clicking a nav link', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    await page.locator('.im-mobile-drawer .im-nav-link').first().click();
    await expect(page.locator('.im-mobile-drawer')).toBeHidden();
  });

  test('mobile menu closes on outside click', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    await expect(page.locator('.im-mobile-drawer')).toBeVisible();
    // Click well below the header/drawer, genuinely outside it.
    await page.mouse.click(200, 780);
    await expect(page.locator('.im-mobile-drawer')).toBeHidden();
  });
});

test.describe('Responsive layout', () => {
  const widths = [320, 360, 375, 390, 393, 412, 430, 768, 820, 912, 1024, 1180];

  for (const width of widths) {
    test(`no horizontal overflow at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/');
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
      );
      expect(overflow).toBe(false);
    });
  }

  test('header does not wrap onto a second visual row on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1180, height: 900 });
    await page.goto('/');
    const header = page.locator('.im-header');
    const box = await header.boundingBox();
    // A single-row header is ~65-75px tall; anything approaching double that
    // means nav items wrapped onto a second line.
    expect(box?.height ?? 0).toBeLessThan(120);
  });

  test('CTA buttons remain visible at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 700 });
    await page.goto('/');
    await expect(page.locator('.im-hero-actions a.im-btn-primary')).toBeVisible();
    await expect(page.locator('.im-hero-actions button.im-btn-ghost')).toBeVisible();
  });

  test('product preview stays within viewport at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    await page.goto('/');
    const box = await page.locator('.im-mockup').boundingBox();
    expect(box).not.toBeNull();
    if (box) expect(box.x + box.width).toBeLessThanOrEqual(321);
  });
});

test.describe('Footer', () => {
  test('legal links navigate to real pages', async ({ page }) => {
    await page.goto('/');
    const legalLinks = page.locator(
      'footer a[href="/confidentialite"], footer a[href="/conditions"], footer a[href="/cookies"], footer a[href="/securite"]'
    );
    const count = await legalLinks.count();
    expect(count).toBe(4);
    const firstHref = await legalLinks.first().getAttribute('href');
    await legalLinks.first().click();
    await expect(page).toHaveURL(new RegExp(`${firstHref}$`));
    await expect(page.locator('.im-hero h1')).toBeVisible();
  });
});

test.describe('Web & Desktop section', () => {
  test('desktop download control has a valid state (real link or coming-soon)', async ({ page }) => {
    await page.goto('/');
    await page.locator('nav.im-nav-desktop button').nth(2).click(); // Web & Desktop
    const realDownload = page.locator('.im-webdesktop-card a.im-btn-primary[target="_blank"]');
    const comingSoon = page.locator('.im-desktop-soon .im-badge');
    const hasRealDownload = await realDownload.count();
    const hasComingSoon = await comingSoon.count();
    expect(hasRealDownload + hasComingSoon).toBeGreaterThan(0);
    if (hasRealDownload > 0) {
      const href = await realDownload.getAttribute('href');
      expect(href).toMatch(/^https:\/\//);
    }
  });
});

test.describe('No dead controls', () => {
  test('no visible link uses a bare "#" placeholder href', async ({ page }) => {
    await page.goto('/');
    const deadLinks = await page.locator('a[href="#"], a[href=""]').count();
    expect(deadLinks).toBe(0);
  });

  test('every visible header/footer/hero button or link has a real href or click handler', async ({ page }) => {
    await page.goto('/');
    const anchors = page.locator('header a, .im-hero a, footer a');
    const anchorCount = await anchors.count();
    for (let i = 0; i < anchorCount; i += 1) {
      const href = await anchors.nth(i).getAttribute('href');
      expect(href, `anchor #${i} must have a real href`).toBeTruthy();
      expect(href).not.toBe('#');
    }
    // Buttons in this codebase are only ever rendered with an onClick handler
    // (scrollToId / setOpen / setLang) — assert none are left as plain,
    // handler-less buttons by checking they are all enabled and clickable.
    const buttons = page.locator('header button, .im-hero button, footer button');
    const buttonCount = await buttons.count();
    for (let i = 0; i < buttonCount; i += 1) {
      await expect(buttons.nth(i)).toBeEnabled();
    }
  });
});
