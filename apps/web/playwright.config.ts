import { defineConfig } from '@playwright/test';

const baseURL = 'http://127.0.0.1:5173';

export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  retries: 0,
  webServer: {
    command: 'vite --host 127.0.0.1 --strictPort',
    url: baseURL,
    reuseExistingServer: false,
  },
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true
  }
});
