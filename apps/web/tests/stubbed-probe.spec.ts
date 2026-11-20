import { expect, test } from '@playwright/test';

const probePath = '/api/v1/probes/readiness';
const unavailableMessage = 'Le service est temporairement indisponible.';

test.describe('Étaye smoke with a stubbed probe', () => {
  test('HTTP 200 with status UP shows READY', async ({ page }) => {
    await page.route(probePath, (route) => route.fulfill({ status: 200, json: { status: 'UP' } }));
    await page.goto('/');

    await expect(page.getByRole('status')).toHaveText('READY');
    await expect(page.getByTestId('probe-error')).toHaveCount(0);
  });

  for (const { name, status, body } of [
    { name: 'HTTP 503 with status DOWN', status: 503, body: '{"status":"DOWN"}' },
    { name: 'HTTP 503 with status UP', status: 503, body: '{"status":"UP"}' },
    { name: 'HTTP 200 with status DOWN', status: 200, body: '{"status":"DOWN"}' },
    { name: 'HTTP 200 with a wrong JSON shape', status: 200, body: '{"status":["UP"]}' },
    { name: 'HTTP 200 with malformed JSON', status: 200, body: '{"status":' },
  ]) {
    test(`${name} shows UNAVAILABLE`, async ({ page }) => {
      await page.route(probePath, (route) => route.fulfill({ status, contentType: 'application/json', body }));
      await page.goto('/');

      await expect(page.getByRole('status')).toHaveText('UNAVAILABLE');
      await expect(page.getByTestId('probe-error')).toHaveText(unavailableMessage);
    });
  }

  test('HTTP 503 with an unfinished body shows UNAVAILABLE within 2 seconds', async ({ page }) => {
    await page.addInitScript((path) => {
      const fetch = window.fetch.bind(window);
      window.fetch = (input, init) => {
        if (input !== path) return fetch(input, init);
        return Promise.resolve(new Response(new ReadableStream<Uint8Array>(), { status: 503 }));
      };
    }, probePath);
    await page.goto('/');

    await expect(page.getByRole('status')).toHaveText('UNAVAILABLE', { timeout: 2_000 });
    await expect(page.getByTestId('probe-error')).toHaveText(unavailableMessage);
  });

  test('a refused connection shows UNAVAILABLE', async ({ page }) => {
    await page.route(probePath, (route) => route.abort('connectionrefused'));
    await page.goto('/');

    await expect(page.getByRole('status')).toHaveText('UNAVAILABLE');
    await expect(page.getByTestId('probe-error')).toHaveText(unavailableMessage);
  });

  test('an unanswered probe shows LOADING first and UNAVAILABLE within 8 seconds', async ({ page }) => {
    await page.route(probePath, () => {});

    await test.step('Wait for the unanswered probe to time out', async () => {
      const [request] = await Promise.all([
        page.waitForRequest((candidate) => new URL(candidate.url()).pathname === probePath, { timeout: 8_000 }),
        page.goto('/', { waitUntil: 'domcontentloaded' }),
      ]);
      await expect(page.getByRole('status')).toHaveText('LOADING');
      expect(request.method()).toBe('GET');
      await expect(page.getByRole('status')).toHaveText('UNAVAILABLE', { timeout: 8_000 });
      await expect(page.getByTestId('probe-error')).toHaveText(unavailableMessage);
    }, { timeout: 8_000 });
  });
});
