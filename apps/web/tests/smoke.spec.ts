import { test, expect } from '@playwright/test';

test.describe('Étaye Smoke Journey & Database Readiness Probe', () => {
  test('positive run: browser accesses web page and verifies database-backed readiness is READY and UP', async ({ page }) => {
    await page.goto('/');

    const statusLocator = page.locator('[data-testid="readiness-status"]');
    await expect(statusLocator).toBeVisible();
    await expect(statusLocator).toHaveText('READY');

    const dbLocator = page.locator('[data-testid="db-status"]');
    await expect(dbLocator).toHaveText('UP');
  });

  test('negative run: probe failure or stopped database displays honest UNAVAILABLE and DOWN state', async ({ page }) => {
    // Intercept probe endpoint to simulate PostgreSQL failure / database unavailable
    await page.route('/api/v1/probes/readiness', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'DOWN', database: 'DOWN', error: 'Database connection unavailable' })
      });
    });

    await page.goto('/');

    const statusLocator = page.locator('[data-testid="readiness-status"]');
    await expect(statusLocator).toBeVisible();
    await expect(statusLocator).toHaveText('UNAVAILABLE');

    const dbLocator = page.locator('[data-testid="db-status"]');
    await expect(dbLocator).toHaveText('DOWN');

    const errorLocator = page.locator('[data-testid="probe-error"]');
    await expect(errorLocator).toBeVisible();
  });
});
