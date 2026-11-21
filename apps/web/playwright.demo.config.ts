import { defineConfig } from '@playwright/test';

const baseURL = process.env.ETAYE_E2E_DEMO_URL ?? 'http://localhost:4179';
const url = new URL(baseURL);
if (url.protocol !== 'http:' || !['localhost', '127.0.0.1'].includes(url.hostname) || url.pathname !== '/') {
  throw new Error('The demo journey requires the disposable local Compose stack on loopback.');
}

export default defineConfig({
  testDir: './demo-e2e',
  timeout: 90_000,
  retries: 0,
  workers: 1,
  outputDir: 'test-results/demo',
  use: { baseURL, browserName: 'chromium', video: 'on' },
});
