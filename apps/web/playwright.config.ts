import { defineConfig } from '@playwright/test';

const port = Number(process.env.ETAYE_SMOKE_PORT ?? 5173);
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  retries: 0,
  webServer: {
    command: `python3 ../../tools/generate_web_tokens.py --target-dir .cache/design && vite --host 127.0.0.1 --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: false,
  },
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    // Diagnostics for a failed test only; retries stay at 0 so a flake is never hidden.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  }
});
