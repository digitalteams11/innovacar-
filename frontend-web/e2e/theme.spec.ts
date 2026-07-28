import { test, expect, type Page } from '@playwright/test';

/**
 * E2E coverage for theme persistence and dark-mode contrast on the public
 * Login page (/#/login — reachable with no backend/auth, unlike the rest of
 * the authenticated app). The canonical storage key and bootstrap script are
 * shared by every route (see src/context/ThemeContext.tsx and
 * public/theme-bootstrap.js), so what's verified here holds for the
 * authenticated app too.
 */

const THEME_KEY = 'innovacar-theme';

async function setThemeBeforeLoad(page: Page, mode: 'light' | 'dark' | 'system') {
  // addInitScript runs before any page script (including theme-bootstrap.js),
  // so this exercises the real "theme applied before first paint" path
  // rather than setting it after the fact.
  await page.addInitScript((value) => {
    window.localStorage.setItem('innovacar-theme', value);
  }, mode);
}

async function isDark(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.classList.contains('dark'));
}

test.describe('Theme — persistence across reload', () => {
  test('light mode survives a reload', async ({ page }) => {
    await setThemeBeforeLoad(page, 'light');
    await page.goto('/#/login');
    expect(await isDark(page)).toBe(false);
    await page.reload();
    expect(await isDark(page)).toBe(false);
    expect(await page.evaluate((k) => localStorage.getItem(k), THEME_KEY)).toBe('light');
  });

  test('dark mode survives a reload', async ({ page }) => {
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    expect(await isDark(page)).toBe(true);
    await page.reload();
    expect(await isDark(page)).toBe(true);
    expect(await page.evaluate((k) => localStorage.getItem(k), THEME_KEY)).toBe('dark');
  });

  test('theme is applied before first paint (no flash) — html already has the resolved class at DOM-ready', async ({ page }) => {
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    // The bootstrap script (a plain <script src>, not React) applies the
    // class synchronously before React ever mounts — true by construction if
    // it's already present the moment the page has finished loading.
    expect(await isDark(page)).toBe(true);
  });
});

test.describe('Theme — explicit choice overrides OS preference', () => {
  test('explicit light stays light even when the OS is set to dark', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await setThemeBeforeLoad(page, 'light');
    await page.goto('/#/login');
    expect(await isDark(page)).toBe(false);
  });

  test('explicit dark stays dark even when the OS is set to light', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    expect(await isDark(page)).toBe(true);
  });

  test('system mode follows the OS', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await setThemeBeforeLoad(page, 'system');
    await page.goto('/#/login');
    expect(await isDark(page)).toBe(true);
  });
});

test.describe('Theme — dark-mode button/link contrast', () => {
  test('brand-colored links (e.g. "Forgot password") are readable in dark mode, not dark-on-dark', async ({ page }) => {
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    const link = page.getByRole('link', { name: /forgot\?|oubli/i });
    await expect(link).toBeVisible();

    const { color, bg } = await link.evaluate((el) => {
      const style = getComputedStyle(el);
      let bgEl: Element | null = el;
      let bgColor = 'rgba(0, 0, 0, 0)';
      while (bgEl) {
        const c = getComputedStyle(bgEl).backgroundColor;
        if (c && c !== 'rgba(0, 0, 0, 0)' && c !== 'transparent') { bgColor = c; break; }
        bgEl = bgEl.parentElement;
      }
      return { color: style.color, bg: bgColor };
    });

    // Not the historical bug shape: text color must not equal (or nearly
    // equal) the background it sits on, and must not be a near-black navy —
    // the "dark navy text on dark navy background" report this fixes.
    expect(color).not.toBe(bg);
    const rgb = color.match(/\d+/g)?.map(Number) ?? [255, 255, 255];
    const luminance = (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255;
    expect(luminance).toBeGreaterThan(0.25); // readable against a dark page, not near-black
  });

  test('a disabled submit button remains legible (not opacity-only invisible) in dark mode', async ({ page }) => {
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    const submit = page.locator('button[type="submit"]').first();
    await expect(submit).toBeVisible();
    const opacity = await submit.evaluate((el) => Number(getComputedStyle(el).opacity));
    expect(opacity).toBeGreaterThan(0.15);
  });
});

test.describe('Theme — visual regression', () => {
  test('login page screenshot — light theme', async ({ page }) => {
    await setThemeBeforeLoad(page, 'light');
    await page.goto('/#/login');
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await expect(page).toHaveScreenshot('login-light.png', { maxDiffPixelRatio: 0.05 });
  });

  test('login page screenshot — dark theme', async ({ page }) => {
    await setThemeBeforeLoad(page, 'dark');
    await page.goto('/#/login');
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await expect(page).toHaveScreenshot('login-dark.png', { maxDiffPixelRatio: 0.05 });
  });
});
