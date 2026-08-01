import { test, expect, type Page } from '@playwright/test';

/**
 * Guards the shared authenticated workspace width — the actual bug reported
 * ("sidebar/header use full width, but page content stays centered in a
 * narrow column with large side margins"). Renders real authenticated pages
 * against a fully mocked backend (no real login needed) and asserts the
 * measurable rules from the layout fix: at 1366px the page content must use
 * nearly all the space beside the sidebar, and at 1920px it must not still
 * be clamped to a ~1200px centered column. Data fetches are mocked to return
 * empty/error-free payloads — this test is about the layout shell (Sidebar +
 * AppHeader + <main class="page-canvas">), not real business data.
 */

const FAKE_USER = {
  id: 1,
  email: 'layout-test@innovacar.app',
  role: 'ADMIN',
  tenantId: 1,
  tenantName: 'Layout Test Agency',
  emailVerified: true,
  twoFactorEnabled: false,
  language: 'en',
  themeMode: 'light',
  planCode: 'BASIC',
  planName: 'Basic',
  subscriptionStatus: 'ACTIVE',
  accountAccess: { canUsePlatform: true },
};

async function mockAuthenticatedBackend(page: Page) {
  await page.addInitScript((user) => {
    window.localStorage.setItem('token', 'e2e-fake-token');
    window.localStorage.setItem('accessToken', 'e2e-fake-token');
    window.localStorage.setItem('user', JSON.stringify(user));
  }, FAKE_USER);

  // Match only the real backend origin (localhost:8082 in dev — see
  // src/lib/api.ts's dev fallback), never a bare "**/api/**" glob: that also
  // matches Vite's own dev-server module requests for source files that
  // happen to live under src/api/ (e.g. src/api/axios.ts), breaking the
  // module graph by returning JSON where a JS module was expected.
  await page.route((url) => url.port === '8082', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/me') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(FAKE_USER) });
    }
    if (path === '/api/health') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }
    // Everything else the dashboard/vehicles pages fetch (stats, lists,
    // notifications, feature access, permissions, tenant-settings, ...).
    // A bare empty array satisfies both call sites that read `res.data`
    // directly as a list (e.g. NotificationContext) and ones that read
    // `res.data?.data || res.data || []` — this is about rendering the
    // layout shell, not real business data, so an empty response is fine
    // everywhere.
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '[]',
    });
  });
}

function sidebarWidthFor(viewportWidth: number): number {
  return viewportWidth < 1024 ? 0 : 248; // sidebar is hidden below the lg breakpoint (mobile drawer instead)
}

const VIEWPORTS = [
  { width: 390, height: 844, label: 'mobile' },
  { width: 1366, height: 900, label: 'laptop' },
  { width: 1920, height: 1080, label: 'ultrawide' },
];

for (const route of ['/#/vehicles', '/#/']) {
  test.describe(`authenticated layout — ${route}`, () => {
    for (const viewport of VIEWPORTS) {
      test(`uses the available workspace at ${viewport.label} (${viewport.width}px)`, async ({ page }) => {
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        await mockAuthenticatedBackend(page);
        await page.goto(route);
        await page.waitForSelector('main.page-canvas', { timeout: 15000 });

        // No horizontal overflow at any width.
        const { scrollWidth, clientWidth } = await page.evaluate(() => ({
          scrollWidth: document.documentElement.scrollWidth,
          clientWidth: document.documentElement.clientWidth,
        }));
        expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1); // 1px rounding tolerance

        const mainBox = await page.locator('main.page-canvas').boundingBox();
        expect(mainBox).not.toBeNull();
        if (!mainBox) return;

        const sidebar = sidebarWidthFor(viewport.width);
        const available = viewport.width - sidebar;

        if (viewport.width >= 1024) {
          // Measurable acceptance rule: content should use at least 88% of
          // the workspace remaining beside the sidebar (never a narrow
          // centered marketing-style column).
          expect(mainBox.width / available).toBeGreaterThanOrEqual(0.88);
        }

        if (viewport.width >= 1920) {
          // Must not still be clamped to the old ~1100-1200px centered column.
          expect(mainBox.width).toBeGreaterThan(1400);
        }
      });
    }
  });
}
