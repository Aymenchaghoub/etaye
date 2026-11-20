import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { Session } from '../src/api/generated/types.gen';
import { allowsAccountPath, checkPausedWork, pauseWork } from '../src/auth/session-work';

const csrf = '/api/v1/csrf';
const sessionPath = '/api/v1/session';
const recovery = '/api/v1/access-recovery';
const grant = '00000000-0000-0000-0000-000000000001';
const contextPath = `/compte/pratiques/${grant}`;
const portalPath = `/compte/patient/${grant}`;
const address = 'camille.exemple@example.test';
const login = 'Mot de passe de la démo';
const placeholderToken = 'T'.repeat(43);

function session(expires = 90_000): Session {
  const timestamp = new Date().toISOString();
  return {
    account: {
      id: '00000000-0000-0000-0000-000000000002',
      version: 0,
      displayName: 'Camille Exemple',
      email: address,
      state: 'ACTIVE',
      locale: 'fr-FR',
      createdAt: timestamp,
      updatedAt: timestamp,
    },
    membershipIds: [grant],
    portalLinkIds: [],
    expiresAt: new Date(Date.now() + expires).toISOString(),
  };
}

async function stubCsrf(page: Page) {
  await page.route(csrf, (route) => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } }));
}

async function filledLogin(page: Page) {
  await page.locator('#l-mail').fill(address);
  await page.locator('#l-pw').fill(login);
}

async function axe(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  const violations = result.violations.map(({ id, nodes }) => ({ id, nodes: nodes.map(({ target }) => target) }));
  await test.info().attach('axe-account-access', {
    body: JSON.stringify({ passedRules: result.passes.map(({ id }) => id), violations }),
    contentType: 'application/json',
  });
  expect(result.passes.length).toBeGreaterThan(0);
  expect(violations).toEqual([]);
}

test('professional and patient entries preserve labels and clear an incorrect password', async ({ page }) => {
  await stubCsrf(page);
  await page.route(sessionPath, (route) => {
    expect(route.request().headers()['x-csrf-token']).toBe('test-csrf');
    expect(route.request().headers()['x-client-version']).toMatch(/^web\//);
    return route.fulfill({ status: 401, json: { code: 'INVALID_CREDENTIALS' } });
  });
  await page.goto('/connexion');
  await expect(page.getByRole('heading', { name: 'Se connecter' })).toBeVisible();
  await expect(page.locator('#l-mail')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.locator('#l-pw')).toBeFocused();
  await filledLogin(page);
  await page.getByRole('checkbox', { name: 'Afficher le mot de passe' }).check();
  await expect(page.locator('#l-pw')).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('alert')).toBeFocused();
  await expect(page.locator('#l-pw')).toHaveValue('');
  await expect(page.locator('#l-pw')).toHaveAttribute('type', 'password');
  await page.getByRole('link', { name: 'Adresse e-mail ou mot de passe incorrect' }).press('Enter');
  await expect(page.locator('#l-pw')).toBeFocused();
  await axe(page);
  await page.getByRole('link', { name: 'Espace patient', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Se connecter à votre espace patient' })).toBeVisible();
  await axe(page);
});

test('recovery keeps the address and receipt on an explicit retry after an outage', async ({ page }) => {
  await stubCsrf(page);
  const receipts: string[] = [];
  await page.route(recovery, (route) => {
    const request = route.request();
    receipts.push(request.headers()['idempotency-key']);
    expect(request.postDataJSON()).toEqual({ email: address });
    return route.fulfill(receipts.length === 1
      ? { status: 503, json: { code: 'SERVICE_UNAVAILABLE' } }
      : { status: 202, json: { accepted: true, correlationId: 'fake-correlation' } });
  });
  await page.goto('/recuperation');
  await page.locator('#r-mail').fill(address);
  await page.getByRole('button', { name: 'Recevoir un lien' }).click();
  await expect(page.getByRole('alert')).toContainText('La demande');
  await expect(page.locator('#r-mail')).toHaveValue(address);
  expect(receipts).toHaveLength(1);
  await axe(page);
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await expect(page.getByRole('heading', { name: 'Demande de récupération reçue' })).toBeFocused();
  expect(receipts).toHaveLength(2);
  expect(typeof receipts[0]).toBe('string');
  expect(receipts[0].length).toBeGreaterThan(0);
  expect(receipts[0]).toBe(receipts[1]);
  await axe(page);
});

test('reset validation has the amber alert and keeps the fragment until completion', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.setViewportSize({ width: 320, height: 700 });
  await stubCsrf(page);
  let mutations = 0;
  await page.route(`${recovery}/complete`, async (route) => {
    mutations++;
    expect(route.request().url()).not.toContain('#');
    expect(route.request().postDataJSON().token).toBe(placeholderToken);
    await route.fulfill({ status: 200, json: { accepted: true, correlationId: 'fake-correlation' } });
  });
  await page.goto(`/recuperation/nouveau#${placeholderToken}`);
  await page.locator('#r-pw').fill('court');
  await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
  const summary = page.locator('.etaye-auth__carte').getByRole('alert');
  await expect(summary).toBeFocused();
  expect(await summary.evaluate((element) => getComputedStyle(element).borderColor))
    .toBe(await page.evaluate(() => {
      const probe = document.createElement('div');
      probe.style.color = 'var(--attention)';
      document.body.append(probe);
      const color = getComputedStyle(probe).color;
      probe.remove();
      return color;
    }));
  await summary.getByRole('link').press('Enter');
  expect(await page.evaluate(() => location.hash.length)).toBe(44);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
  expect(mutations).toBe(0);
  await axe(page);

  await page.locator('#r-pw').fill(login);
  await page.locator('#r-pw2').fill(login);
  await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
  await expect(page.getByRole('heading', { name: 'Mot de passe modifié' })).toBeFocused();
  expect(await page.evaluate(() => location.hash)).toBe('');
  expect(mutations).toBe(1);
  await axe(page);
});

test('an idle deadline removes protected nodes before a same-account authorized return', async ({ page }) => {
  await stubCsrf(page);
  let writes = 0;
  let active: Session | undefined;
  await page.route(sessionPath, (route) => {
    if (route.request().method() === 'POST') {
      writes++;
      active = session();
      expect(route.request().postDataJSON().returnTo).toBe(contextPath);
      return route.fulfill({ json: { ...active, returnTo: contextPath } });
    }
    active ??= session(1_200);
    return route.fulfill({ json: active });
  });
  await page.goto(contextPath);
  await expect(page.getByTestId('protected-account')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeFocused();
  await expect(page.getByText('Camille Exemple')).toHaveCount(0);
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  expect(await page.locator('.etaye-session-scene').evaluate((scene) => scene.scrollHeight)).toBe(720);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.locator('.etaye-session-panel__actions').evaluate((actions) =>
    Math.round(actions.getBoundingClientRect().bottom))).toBe(844);
  await axe(page);
  await page.getByRole('button', { name: 'Se reconnecter' }).click();
  await filledLogin(page);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByTestId('protected-account')).toBeVisible();
  await expect(page).toHaveURL(new RegExp(contextPath + '$'));
  expect(writes).toBe(1);
});

test('grant loss or an outage clears protected content on reauthorization', async ({ page }) => {
  let response = { status: 200, json: session() as object };
  await page.route(sessionPath, (route) => route.fulfill(response));
  await page.goto(contextPath);
  await expect(page.getByTestId('protected-account')).toBeVisible();
  response = { status: 503, json: { code: 'SERVICE_UNAVAILABLE' } };
  await page.evaluate(() => window.dispatchEvent(new Event('pageshow')));
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await expect(page.getByRole('alert')).toContainText('Le service est temporairement indisponible');
  response = { status: 200, json: { ...session(), membershipIds: [] } };
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await expect(page.getByRole('heading', { name: 'Accès modifié' })).toBeFocused();
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await axe(page);
});

test('paused work requires the same account and exact grant kind and id', () => {
  const original = session();
  const work = pauseWork(original, contextPath, { note: 'Exemple synthétique' });
  if (!work) throw new Error('Expected authorized synthetic work');
  expect(checkPausedWork(work, original, contextPath)).toEqual({ status: 'restored', value: work.value });
  expect(checkPausedWork(work, { ...original, membershipIds: [] }, contextPath)).toEqual({ status: 'grant-changed' });
  expect(checkPausedWork(work, { ...original, portalLinkIds: [grant] }, portalPath)).toEqual({ status: 'grant-changed' });
  expect(checkPausedWork(work, { ...original, account: { ...original.account, id: grant } }, contextPath))
    .toEqual({ status: 'account-changed' });
  expect(allowsAccountPath(original, 'https://other.example.test/compte')).toBe(false);
});
