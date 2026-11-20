import { AxeBuilder } from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const probePath = '/api/v1/probes/readiness';
const unavailableMessage = 'Le service est temporairement indisponible.';

test.describe('Étaye smoke with a stubbed probe', () => {
  test('HTTP 200 with status UP shows READY', async ({ page }) => {
    await page.route(probePath, async (route) => {
      expect(route.request().headers()['x-client-version']).toMatch(/^web\/\d+\.\d+\.\d+$/);
      await route.fulfill({ status: 200, json: { status: 'UP', futureField: 'ignored' } });
    });
    await page.goto('/');

    await expect(page.getByRole('status')).toHaveText('READY');
    await expect(page.getByTestId('probe-error')).toHaveCount(0);
  });

  for (const { name, status, body } of [
    { name: 'HTTP 503 with status DOWN', status: 503, body: '{"status":"DOWN"}' },
    { name: 'HTTP 503 with status UP', status: 503, body: '{"status":"UP"}' },
    { name: 'HTTP 200 with an unknown future status', status: 200, body: '{"status":"FUTURE"}' },
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
        const url = new URL(input instanceof Request ? input.url : input, window.location.href);
        if (url.pathname !== path) return fetch(input, init);
        document.documentElement.dataset.unfinishedProbe = 'hit';
        return Promise.resolve(new Response(new ReadableStream<Uint8Array>(), { status: 503 }));
      };
    }, probePath);
    await page.goto('/');

    await expect(page.locator('html')).toHaveAttribute('data-unfinished-probe', 'hit');
    await expect(page.getByRole('status')).toHaveText('UNAVAILABLE', { timeout: 2_000 });
    await expect(page.getByTestId('probe-error')).toHaveText(unavailableMessage);
  });

  test('transport override is used only when the flag is enabled', async ({ page }) => {
    await page.route(probePath, (route) => route.fulfill({ status: 200, json: { status: 'UP' } }));
    await page.goto('/');
    const result = await page.evaluate(async (modulePath) => {
      const { createBrowserApi } = await import(modulePath) as typeof import('../src/api/browser');
      const requests: { path: string; credentials: string; version: string | null; bearer: string | null }[] = [];
      const transport = {
        fetch: async (input: RequestInfo | URL, init?: RequestInit) => {
          const request = new Request(input, init);
          requests.push({
            path: new URL(request.url).pathname,
            credentials: request.credentials,
            version: request.headers.get('X-Client-Version'),
            bearer: request.headers.get('Authorization'),
          });
          return Response.json({ status: 'UP' });
        },
      };
      const signal = new AbortController().signal;
      const off = await createBrowserApi(location.origin, false, transport).isReady(signal, () => {});
      const offRequests = requests.length;
      const on = await createBrowserApi(location.origin, true, transport).isReady(signal, () => {});
      return { off, offRequests, on, requests };
    }, '/src/api/browser.ts');
    expect(result).toEqual({
      off: true, offRequests: 0, on: true,
      requests: [{ path: probePath, credentials: 'same-origin', version: expect.stringMatching(/^web\//), bearer: null }],
    });
  });

  for (const [code, payload, state] of [[200, 'UP', 'READY'], [503, 'DOWN', 'UNAVAILABLE']] as const) {
    test(`axe checks the ${state} smoke state`, async ({ page }, info) => {
      await page.route(probePath, (route) => route.fulfill({ status: code, json: { status: payload } }));
      await page.goto('/');
      await expect(page.getByRole('status')).toHaveText(state);
      const results = await new AxeBuilder({ page }).analyze();
      await info.attach('axe-results', { body: JSON.stringify(results), contentType: 'application/json' });
      expect(results.passes.length).toBeGreaterThan(0);
      expect(results.violations).toEqual([]);
    });
  }

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
