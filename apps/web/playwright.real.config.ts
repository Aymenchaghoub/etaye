import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 240_000,
  retries: 0,
  workers: 1,
  use: {
    baseURL: process.env.ETAYE_E2E_BASE_URL ?? 'http://localhost:5173',
    browserName: 'chromium',
    headless: true,
  },
});
