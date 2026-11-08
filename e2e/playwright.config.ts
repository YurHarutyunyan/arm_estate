import { defineConfig, devices } from '@playwright/test'

/**
 * Runs against the full stack started with `docker compose up` (frontend on :3000).
 * BASE_URL can point at another environment, e.g. a deployed demo.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: true,
  // Each test drives a full browser against Elasticsearch, MongoDB and the API on one machine;
  // more workers than this starve laptops and small CI runners and make timings flaky
  workers: 2,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /mobile\.spec/ },
    { name: 'mobile', use: { ...devices['Pixel 7'], viewport: { width: 375, height: 812 } }, testMatch: /mobile\.spec/ },
  ],
})
