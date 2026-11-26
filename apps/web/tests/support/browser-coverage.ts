import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { test as base, type Page } from '@playwright/test';

export { expect, type Locator, type Page, type Route } from '@playwright/test';

const coverageDir = process.env.ETAYE_BROWSER_COVERAGE_DIR;
let sequence = 0;

function keepSourceUrl(url: string): boolean {
  if (!url) return false;
  try {
    const pathname = new URL(url).pathname;
    return pathname.startsWith('/src/')
      && /\.(?:ts|tsx)$/.test(pathname)
      && !pathname.startsWith('/src/api/generated/')
      && !pathname.includes('.test.');
  } catch {
    return false;
  }
}

async function flushPageCoverage(page: Page, targetDir: string, workerIndex: number): Promise<void> {
  if (page.isClosed()) return;
  try {
    const entries = await page.coverage.stopJSCoverage();
    const filtered = entries
      .filter((entry) => keepSourceUrl(entry.url) && typeof entry.source === 'string')
      .map((entry) => ({ url: entry.url, source: entry.source, functions: entry.functions }));
    if (filtered.length === 0) return;
    mkdirSync(targetDir, { recursive: true });
    sequence += 1;
    const file = path.join(targetDir, `cov-${process.pid}-${workerIndex}-${sequence}.json`);
    writeFileSync(file, JSON.stringify(filtered), 'utf8');
  } catch {
    // Ignore pages closed unexpectedly during failure teardown.
  }
}

export const test = base.extend({
  page: async ({ page, context }, use, testInfo) => {
    if (!coverageDir) {
      await use(page);
      return;
    }
    const tracked = new Set<Page>();
    const arm = async (candidate: Page) => {
      if (tracked.has(candidate) || candidate.isClosed()) return;
      tracked.add(candidate);
      try {
        await candidate.coverage.startJSCoverage({ reportAnonymousScripts: false, resetOnNavigation: false });
      } catch {
        tracked.delete(candidate);
      }
    };
    const onNewPage = (extra: Page) => {
      void arm(extra);
    };
    context.on('page', onNewPage);
    await arm(page);
    try {
      await use(page);
    } finally {
      context.off('page', onNewPage);
      for (const candidate of tracked) {
        await flushPageCoverage(candidate, coverageDir, testInfo.workerIndex);
      }
    }
  },
});
