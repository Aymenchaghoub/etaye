import { execFileSync } from 'node:child_process';
import { expect, test, type Page } from '../tests/support/browser-coverage';

const probePath = '/api/v1/probes/readiness';

function docker(...args: string[]) {
  return execFileSync('docker', args, { encoding: 'utf8', timeout: 20_000 }).trim();
}

async function expectProbe(page: Page, url: string, state: 'UP' | 'DOWN', timeout: number) {
  const [response] = await Promise.all([
    page.waitForResponse((candidate) => candidate.url() === url && candidate.request().method() === 'GET', { timeout }),
    page.reload({ waitUntil: 'domcontentloaded', timeout }),
  ]);
  expect(response.status()).toBe(state === 'UP' ? 200 : 503);
  expect(await response.json()).toEqual({ status: state });
  await expect(page.getByRole('status')).toHaveText(state === 'UP' ? 'READY' : 'UNAVAILABLE', { timeout });
}

test('real browser and API report a stopped or paused PostgreSQL container and recover', async ({ page, baseURL }) => {
  const container = process.env.ETAYE_E2E_DB_CONTAINER;
  if (!container || !/^[A-Za-z0-9_.-]+$/.test(container)) {
    throw new Error('Set ETAYE_E2E_DB_CONTAINER to a valid PostgreSQL container name.');
  }
  if (!baseURL) {
    throw new Error('Set ETAYE_E2E_BASE_URL to the running web application URL.');
  }
  const probeURL = new URL(probePath, baseURL).href;

  try {
    await page.goto('/', { waitUntil: 'domcontentloaded', timeout: 15_000 });
    await expect(page.getByRole('status')).toHaveText('READY', { timeout: 8_000 });
    await expectProbe(page, probeURL, 'UP', 8_000);

    docker('stop', '--', container);
    await test.step('Stopped PostgreSQL returns 503 and shows UNAVAILABLE', async () => {
      await expectProbe(page, probeURL, 'DOWN', 15_000);
    }, { timeout: 15_000 });

    docker('start', '--', container);
    await test.step('Started PostgreSQL returns 200 and shows READY', async () => {
      await expect(async () => expectProbe(page, probeURL, 'UP', 8_000)).toPass({ timeout: 60_000 });
    }, { timeout: 60_000 });

    docker('pause', '--', container);
    await test.step('Paused PostgreSQL returns 503 and shows UNAVAILABLE', async () => {
      await expectProbe(page, probeURL, 'DOWN', 15_000);
    }, { timeout: 15_000 });

    docker('unpause', '--', container);
    await test.step('Unpaused PostgreSQL returns 200 and shows READY', async () => {
      await expect(async () => expectProbe(page, probeURL, 'UP', 8_000)).toPass({ timeout: 60_000 });
    }, { timeout: 60_000 });
  } finally {
    const [paused, running] = docker(
      'container', 'inspect', '--format', '{{.State.Paused}} {{.State.Running}}', '--', container,
    ).split(' ');
    if (paused === 'true') {
      docker('unpause', '--', container);
    }
    if (running !== 'true') {
      docker('start', '--', container);
    }
  }
});
