import { expect, test } from '@playwright/test';
import { demoCompose, fixtureId, signIn } from './support';

test('the local gateway warms up, reports its API version and uses both generated themes', async ({ page, request }) => {
  const probe = page.waitForResponse((response) => response.url().endsWith('/api/v1/probes/readiness') && response.ok());
  await page.goto('/');
  expect((await probe).status()).toBe(200);
  await expect(page).toHaveURL(/\/demonstration$/);
  const readiness = await request.get('/api/v1/probes/readiness');
  expect(await readiness.json()).toEqual({ status: 'UP' });

  const metaCall = page.waitForResponse((response) => response.url().endsWith('/api/v1/meta'));
  await page.goto('/demonstration?theme=clair');
  const meta = await metaCall;
  const version = /^\d+\.\d+\.\d+/;
  expect(meta.status()).toBe(200);
  expect(await meta.json()).toMatchObject({
    apiVersion: 'v1', minimumClientVersion: expect.stringMatching(version),
    latestClientVersion: expect.stringMatching(version), demoDatasetVersion: 'etaye-design-v1',
  });
  expect((await meta.request().allHeaders())['x-client-version']).toMatch(/^web\/\d+\.\d+\.\d+/);
  expect((await request.get('/api/v1/session')).status()).toBe(401);
  await page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' }).click();
  const opened = page.waitForEvent('popup');
  await page.getByRole('link', { name: 'README de la démonstration' }).click();
  const guide = await opened;
  await expect(guide).toHaveURL(/\/demo-guide\.txt$/);
  for (const route of ['Bienvenue et identité', 'Deux cabinets', 'Accueil d’un patient', 'Dossier et preuves', 'Plateforme et isolation']) {
    await expect(guide.locator('body')).toContainText(route);
  }
  expect(await guide.evaluate(() => document.characterSet)).toBe('UTF-8');
  await guide.close();

  await expect(page.locator('html')).toHaveAttribute('data-theme', 'clair');
  const light = await page.locator('html').evaluate((element) => getComputedStyle(element).getPropertyValue('--fond'));
  await page.goto('/demonstration?theme=sombre');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'sombre');
  const dark = await page.locator('html').evaluate((element) => getComputedStyle(element).getPropertyValue('--fond'));
  expect(light.trim()).not.toBe('');
  expect(dark.trim()).not.toBe('');
  expect(dark).not.toBe(light);
});

test('a protected write needs CSRF and a scoped refusal leaves only a safe audit receipt', async ({ page }) => {
  await page.goto('/connexion');
  await signIn(page, 'mila.dubois@example.test');
  const rejected = await page.request.delete('/api/v1/session');
  expect(rejected.status()).toBe(403);
  expect(await rejected.json()).toMatchObject({ code: 'CSRF_REJECTED' });
  expect((await page.request.get('/api/v1/session')).status()).toBe(200);

  const practice = fixtureId('PRA-A');
  const denied = await page.request.get(`/api/v1/practices/${practice}/records/${fixtureId('R-A-001')}`);
  expect(denied.status()).toBe(403);
  const problem = await denied.json() as { correlationId: string };
  expect(problem.correlationId).toEqual(expect.any(String));
  const receipt = demoCompose(['exec', '-T', 'postgres', 'psql', '-XAt', '-U', 'postgres', '-d', 'etaye_demo',
    '-v', 'ON_ERROR_STOP=1', '-v', `correlation=${problem.correlationId}`], `
      SELECT event_type, resource_type, outcome, resource_id IS NULL, safe_metadata->>'operation'
      FROM audit_event
      WHERE correlation_id = :'correlation';
    `);
  expect(receipt).toBe('ACCESS_DENIED|API_OPERATION|DENIED|t|GET /api/v1/practices/{practiceId}/records/{recordId}');
});
