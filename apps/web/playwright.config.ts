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
    // A screenshot of a failed test only. Recording a trace on every test cost about 50 s (35 %) of this suite in CI,
    // so CI reruns the failed tests with `--trace on` instead (see .github/workflows/web.yml). Retries stay at 0.
    screenshot: 'only-on-failure'
  }
});
