import { test, expect } from '@playwright/test';

/**
 * Regression coverage for the "desktop nav + hamburger both visible on
 * mobile" production bug. Root cause: an orphaned, unscoped `.im-header nav`
 * CSS rule (higher specificity than the media-queried `.im-nav-desktop {
 * display: none }` override) kept the desktop nav rendered at every width,
 * and the "Start free trial" button in the header row was never hidden on
 * mobile at all. See src/marketing/marketing.css.
 *
 * MOBILE_WIDTH (390px) is the exact width named in the bug report/spec.
 * DESKTOP_WIDTH is comfortably above the 860px cutover in marketing.css.
 */
const MOBILE_WIDTH = 390;
const DESKTOP_WIDTH = 1280;
const NAV_ITEM_COUNT = 6; // features, how-it-works, web-desktop, trial, faq, contact

test.describe('Mobile header — desktop nav must not coexist with the hamburger', () => {
  test.use({ viewport: { width: MOBILE_WIDTH, height: 844 } });

  test('desktop nav links are not visible at 390px', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('nav.im-nav-desktop')).toBeHidden();
  });

  test('the hamburger button is visible at 390px', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.im-menu-toggle')).toBeVisible();
  });

  test('"Start free trial" is not present in the header row at 390px (only inside the closed drawer)', async ({ page }) => {
    await page.goto('/');
    // The header-row copy (im-header-actions > im-btn-primary) must be hidden;
    // the drawer's own copy exists in the DOM but is not rendered until opened.
    const headerRowCta = page.locator('.im-header-actions > a.im-btn-primary');
    await expect(headerRowCta).toBeHidden();
  });

  test('the header stays a single compact row when the drawer is closed (no wrapping/oversized height)', async ({ page }) => {
    await page.goto('/');
    const header = page.locator('.im-header');
    const box = await header.boundingBox();
    // A single-row mobile header (logo + hamburger only) is well under 100px
    // even with safe-area padding; the reported bug pushed this to 150px+
    // once desktop nav wrapped inside it.
    expect(box?.height ?? 0).toBeLessThan(100);
  });

  test('logo and hamburger are the only controls visible in the closed header row', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.im-brand')).toBeVisible();
    await expect(page.locator('.im-menu-toggle')).toBeVisible();
    await expect(page.locator('nav.im-nav-desktop')).toBeHidden();
    await expect(page.locator('.im-header-actions .im-lang-switch')).toBeHidden();
    await expect(page.locator('.im-header-actions > a.im-btn-ghost')).toBeHidden();
  });

  test('the mobile drawer opens and contains every nav link, the language selector, Log in, and Start free trial', async ({ page }) => {
    await page.goto('/');
    const drawer = page.locator('#im-mobile-drawer');
    await expect(drawer).toBeHidden();
    await page.locator('.im-menu-toggle').click();
    await expect(drawer).toBeVisible();

    await expect(drawer.locator('button.im-nav-link')).toHaveCount(NAV_ITEM_COUNT);
    await expect(drawer.locator('.im-lang-switch')).toBeVisible();
    await expect(drawer.locator('a.im-btn-ghost')).toBeVisible(); // Log in
    await expect(drawer.locator('a.im-btn-primary')).toBeVisible(); // Start free trial
  });

  test('the drawer closes after clicking a nav link', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    const drawer = page.locator('#im-mobile-drawer');
    await expect(drawer).toBeVisible();
    await drawer.locator('button.im-nav-link').first().click();
    await expect(drawer).toBeHidden();
  });

  test('no horizontal overflow at 390px with the drawer open', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    await expect(page.locator('#im-mobile-drawer')).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
    );
    expect(overflow).toBe(false);
  });
});

test.describe('Desktop header — hamburger must not coexist with the desktop nav', () => {
  test.use({ viewport: { width: DESKTOP_WIDTH, height: 900 } });

  test('the hamburger is hidden at desktop width', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.im-menu-toggle')).toBeHidden();
  });

  test('the desktop nav, language switcher, Log in, and Start free trial are all visible in the header row', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('nav.im-nav-desktop')).toBeVisible();
    await expect(page.locator('.im-header-actions .im-lang-switch')).toBeVisible();
    await expect(page.locator('.im-header-actions > a.im-btn-ghost')).toBeVisible();
    await expect(page.locator('.im-header-actions > a.im-btn-primary')).toBeVisible();
  });
});

test.describe('Mobile header — Arabic RTL', () => {
  test.use({ viewport: { width: MOBILE_WIDTH, height: 844 } });

  test('the drawer opens correctly and reads RTL once Arabic is selected', async ({ page }) => {
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    const drawer = page.locator('#im-mobile-drawer');
    await expect(drawer).toBeVisible();
    await drawer.locator('.im-lang-btn', { hasText: 'AR' }).first().click();

    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    // Selecting a language inside the drawer must not close/break it.
    await expect(drawer).toBeVisible();
    await expect(drawer.locator('button.im-nav-link')).toHaveCount(NAV_ITEM_COUNT);
  });
});

test.describe('Mobile header — visual regression', () => {
  test('closed header screenshot at 390px', async ({ page }) => {
    await page.setViewportSize({ width: MOBILE_WIDTH, height: 844 });
    await page.goto('/');
    await expect(page.locator('.im-header')).toHaveScreenshot('mobile-header-closed.png', { maxDiffPixelRatio: 0.05 });
  });

  test('open drawer screenshot at 390px', async ({ page }) => {
    await page.setViewportSize({ width: MOBILE_WIDTH, height: 844 });
    await page.goto('/');
    await page.locator('.im-menu-toggle').click();
    await expect(page.locator('#im-mobile-drawer')).toBeVisible();
    await expect(page.locator('.im-header')).toHaveScreenshot('mobile-header-drawer-open.png', { maxDiffPixelRatio: 0.05 });
  });

  test('desktop header screenshot', async ({ page }) => {
    await page.setViewportSize({ width: DESKTOP_WIDTH, height: 900 });
    await page.goto('/');
    await expect(page.locator('.im-header')).toHaveScreenshot('desktop-header.png', { maxDiffPixelRatio: 0.05 });
  });
});
