import { defineConfig, devices } from '@playwright/test';

/**
 * E2E coverage for the public marketing landing page (src/marketing/pages.tsx).
 * Runs against the Vite dev server so import.meta.env resolves the same way
 * a real visitor's session would (see vite.config.ts port below).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
