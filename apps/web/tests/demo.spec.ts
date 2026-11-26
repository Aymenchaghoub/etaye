import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const version = 'etaye-design-v1';
const meta = {
  apiVersion: 'v1', minimumClientVersion: '0.0.1', latestClientVersion: '0.0.1',
  demoDatasetVersion: version, demoResetGeneration: 1,
};
const items = [
  ['praticienne', 'Praticienne', 'Dr Nora Lefebvre', 'nora.lefebvre@example.test'],
  ['patiente', 'Patiente', 'Claire Martin', 'claire.martin@example.test'],
  ['administration', 'Administration', 'Mila Dubois', 'mila.dubois@example.test'],
].map(([id, roleLabel, actorName, signInIdentifier]) => ({
  id, name: roleLabel, roleLabel, actorName, signInIdentifier,
  practiceName: 'Cabinet des Tilleuls',
  journeySummary: 'Ouvrir son espace fictif puis vérifier les accès actuels au Cabinet des Tilleuls.',
  limitsNotice: 'Données fictives uniquement. Les modifications disparaissent à la réinitialisation.',
  datasetVersion: version, synthetic: true, available: true,
}));

async function stub(page: Page, operator = false) {
  await page.route('**/api/v1/meta', (route) => route.fulfill({ json: meta }));
  await page.route('**/api/v1/demo/scenarios', (route) => route.fulfill({ json: { items, nextCursor: null } }));
  await page.route('**/api/v1/session', (route) => operator
    ? route.fulfill({ json: { account: { email: 'operateur@example.test' } } })
    : route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED' } }));
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 'fictional-fixture-csrf' } }));
}

async function accessible(page: Page) {
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
}

test('the scenario page discloses the fictional identity and keeps normal sign-in', async ({ page }) => {
  await stub(page);
  await page.setViewportSize({ width: 320, height: 844 });
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/demonstration');
  const choose = page.getByRole('button', { name: 'Choisir · Claire Martin' });
  await expect(choose).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
  await expect(page.getByRole('button', { name: 'Réinitialiser la démonstration' })).toHaveCount(0);
  await accessible(page);
  await choose.focus();
  await page.keyboard.press('Enter');
  const panel = page.getByRole('main');
  const heading = panel.getByRole('heading', { name: 'Scénario patiente' });
  await expect(heading).toBeFocused();
  await expect(panel).toContainText('Ne saisissez aucune donnée réelle');
  await expect(panel).toContainText('claire.martin@example.test');
  await expect(panel.getByRole('link', { name: 'README de la démonstration' })).toBeVisible();
  await accessible(page);
  await page.keyboard.press('Escape');
  await expect(heading).toHaveCount(0);
  await expect(choose).toBeFocused();
  await choose.click();
  await panel.getByRole('link', { name: 'Ouvrir la démonstration' }).click();
  await expect(page).toHaveURL(/\/connexion\/patient$/);
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue('claire.martin@example.test');
  await expect(page.locator('input[name="password"]')).toHaveValue('');
});

test('the one-shot preparation trace is delayed and remains static under reduced motion', async ({ page }) => {
  await stub(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  let unblock!: () => void;
  const paused = new Promise<void>((resolve) => { unblock = resolve; });
  await page.route('**/api/v1/demo/scenarios', async (route) => {
    await paused;
    await route.fulfill({ json: { items, nextCursor: null } });
  });
  await page.goto('/demonstration');
  await expect(page.getByRole('status')).toContainText('Préparation du scénario');
  const sign = page.locator('.etaye-demo-sign path').first();
  await expect(sign).toHaveCSS('animation-name', 'none');
  await accessible(page);
  unblock();
  await expect(page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' })).toBeVisible();
  await expect(sign).toHaveCount(0);
});

for (const boundary of [
  { status: 503, code: 'SERVICE_UNAVAILABLE', title: 'Démonstration indisponible' },
  { status: 503, code: 'DEMO_RESET_IN_PROGRESS', title: 'Réinitialisation en cours' },
  { status: 429, code: 'RATE_LIMITED', title: 'Demande temporairement limitée' },
]) {
  test(`the showcase recovers after ${boundary.code}`, async ({ page }) => {
    await stub(page);
    await page.route('**/api/v1/demo/scenarios', (route) => route.fulfill({
      status: boundary.status, headers: { 'Retry-After': '1' }, json: { code: boundary.code },
    }), { times: 1 });
    await page.goto('/demonstration');
    await expect(page.getByRole('alert')).toContainText(boundary.title);
    await expect(page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' })).toHaveCount(0);
    const retry = page.getByRole('button', { name: 'Réessayer' });
    if (boundary.status === 429) await expect(retry).toBeDisabled();
    await accessible(page);
    await retry.click();
    await expect(page.getByRole('main')).toBeFocused();
    await expect(page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' })).toBeVisible();
  });
}

test('the reset confirmation keeps focus, survives a busy response and reports a subsequent refusal', async ({ page }) => {
  await stub(page, true);
  let complete!: () => void;
  const paused = new Promise<void>((resolve) => { complete = resolve; });
  const keys: (string | undefined)[] = [];
  await page.route('**/api/v1/demo/reset', async (route) => {
    keys.push(route.request().headers()['idempotency-key']);
    if (keys.length === 1) {
      await paused;
      await route.fulfill({ status: 409, json: { code: 'DEMO_RESET_IN_PROGRESS' } });
    } else {
      await route.fulfill({ status: 403, json: { code: 'ACCESS_DENIED' } });
    }
  });
  await page.goto('/demonstration');
  const launch = page.getByRole('button', { name: 'Réinitialiser la démonstration' });
  await launch.click();
  const panel = page.getByRole('main');
  const heading = panel.getByRole('heading', { name: 'Réinitialiser la démonstration' });
  await expect(heading).toBeFocused();
  await accessible(page);
  const confirm = panel.getByRole('button', { name: 'Confirmer la réinitialisation' });
  await confirm.click();
  await expect(panel.getByRole('status')).toContainText('Réinitialisation en cours');
  await page.keyboard.press('Escape');
  await expect(heading).toBeVisible();
  await accessible(page);
  complete();
  await expect(panel.getByRole('alert')).toContainText('Réinitialisation en cours');
  await accessible(page);
  await confirm.click();
  await expect(panel.getByRole('alert')).toContainText('Accès réservé');
  await expect(confirm).toBeDisabled();
  expect(keys).toHaveLength(2);
  expect(keys[0]).toMatch(/\S/);
  expect(keys[1]).toBe(keys[0]);
  await accessible(page);
  await expect(page.getByText('Démonstration réinitialisée')).toHaveCount(0);
});

test('a new reset uses live metadata and leaves an unrelated reset unconfirmed', async ({ page }) => {
  await stub(page, true);
  let generation = 1;
  let baselineUnavailable = false;
  let resetRequests = 0;
  await page.route('**/api/v1/meta', (route) => baselineUnavailable
    ? route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } })
    : route.fulfill({ json: { ...meta, demoResetGeneration: generation } }));
  await page.route('**/api/v1/demo/reset', (route) => {
    resetRequests++;
    return route.abort('failed');
  });
  await page.goto('/demonstration');
  await page.getByRole('button', { name: 'Réinitialiser la démonstration' }).click();
  generation = 2;
  baselineUnavailable = true;

  const confirm = page.getByRole('button', { name: 'Confirmer la réinitialisation' });
  const unavailable = page.getByRole('alert').filter({ hasText: 'Démonstration indisponible' });
  await confirm.click();
  await expect(unavailable).toBeVisible();
  expect(resetRequests).toBe(0);

  baselineUnavailable = false;
  await confirm.click();
  await expect(unavailable).toBeVisible();
  await expect(page.getByText('Démonstration réinitialisée')).toHaveCount(0);
  expect(resetRequests).toBe(1);
});

test('a new sign-in after a reset opens the current fixture', async ({ page }) => {
  await stub(page);
  let generation = 1;
  let authenticated = false;
  const membership = '11111111-1111-4111-8111-111111111111';
  const session = {
    account: { id: '22222222-2222-4222-8222-222222222222', displayName: 'Nora Lefebvre', email: items[0].signInIdentifier },
    membershipIds: [membership], portalLinkIds: [], expiresAt: new Date(Date.now() + 600_000).toISOString(),
    contexts: [{
      grantId: membership, kind: 'MEMBERSHIP', practiceId: '33333333-3333-4333-8333-333333333333',
      practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
    }],
  };
  await page.route('**/api/v1/meta', (route) => route.fulfill({ json: { ...meta, demoResetGeneration: generation } }));
  await page.route('**/api/v1/session', (route) => {
    if (route.request().method() === 'POST') authenticated = true;
    return authenticated ? route.fulfill({ json: session }) : route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED' } });
  });
  await page.goto('/demonstration');
  await page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' }).click();
  await page.getByRole('link', { name: 'Ouvrir la démonstration' }).click();
  generation = 2;
  await page.locator('input[name="password"]').fill('Mot de passe fictif');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeVisible();
  await expect(page.getByText('Démonstration réinitialisée')).toHaveCount(0);
});

test('a lost reset response is reconciled across a showcase reload', async ({ page }) => {
  await stub(page, true);
  let generation = 1;
  let readsFail = false;
  let writes = 0;
  await page.route('**/api/v1/meta', (route) => readsFail
    ? route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } })
    : route.fulfill({ json: { ...meta, demoResetGeneration: generation } }));
  await page.route('**/api/v1/session', (route) => generation === 1
    ? route.fulfill({ json: { account: { email: 'operateur@example.test' } } })
    : route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED' } }));
  await page.route('**/api/v1/demo/reset', (route) => {
    writes++;
    generation++;
    readsFail = true;
    return route.abort('failed');
  });
  await page.goto('/demonstration');
  await page.getByRole('button', { name: 'Réinitialiser la démonstration' }).click();
  await page.getByRole('button', { name: 'Confirmer la réinitialisation' }).click();
  await expect(page.getByRole('alert')).toContainText('Démonstration indisponible');
  readsFail = false;
  await page.reload();
  await expect(page.getByRole('status').filter({ hasText: 'Démonstration réinitialisée' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Réinitialiser la démonstration' })).toHaveCount(0);
  expect(writes).toBe(1);
});

test('an unavailable operator session check offers verification without hiding the catalogue', async ({ page }) => {
  await stub(page, true);
  // The StrictMode mount can start and abort a request before the visible check.
  let sessionAvailable = false;
  await page.route('**/api/v1/session', (route) => sessionAvailable ? route.fallback()
    : route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } }));
  await page.goto('/demonstration');
  await expect(page.getByRole('alert')).toContainText('Session opérateur indisponible');
  await expect(page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Connexion opérateur' })).toHaveCount(0);
  await accessible(page);
  sessionAvailable = true;
  await page.getByRole('button', { name: 'Vérifier la session' }).click();
  await expect(page.getByRole('main')).toBeFocused();
  await expect(page.getByRole('button', { name: 'Réinitialiser la démonstration' })).toBeVisible();
});

for (const outcome of ['reset', 'expiry', 'unavailable', 'invalid marker'] as const) {
  test(`the session recheck at the deadline handles ${outcome}`, async ({ page }) => {
    const start = new Date();
    await page.clock.install({ time: start });
    await page.clock.pauseAt(new Date(start.getTime() + 500));
    await stub(page);
    await page.addInitScript((fixture) => sessionStorage.setItem('etaye-demo-entry', JSON.stringify(fixture)),
      { version, generation: 1 });

    const membership = '11111111-1111-4111-8111-111111111111';
    const needsRetry = outcome === 'unavailable' || outcome === 'invalid marker';
    let expired = false;
    let expiredMetaReads = 0;
    let releaseMeta!: () => void;
    const heldMeta = new Promise<void>((resolve) => { releaseMeta = resolve; });
    await page.route('**/api/v1/meta', async (route) => {
      if (expired) await heldMeta;
      const read = expired ? ++expiredMetaReads : 0;
      if (read === 2 && outcome === 'unavailable') {
        return route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } });
      }
      if (read === 2 && outcome === 'invalid marker') {
        return route.fulfill({ json: { ...meta, demoResetGeneration: 0 } });
      }
      const reset = outcome === 'reset' || (needsRetry && read > 2);
      await route.fulfill({ json: { ...meta, demoResetGeneration: expired && reset ? 2 : 1 } });
    });
    await page.route('**/api/v1/session', (route) => expired
      ? route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED' } })
      : route.fulfill({ json: {
        account: { id: '22222222-2222-4222-8222-222222222222', displayName: 'Nora Lefebvre' },
        membershipIds: [membership], portalLinkIds: [], expiresAt: new Date(start.getTime() + 10_000).toISOString(),
        contexts: [{
          grantId: membership, kind: 'MEMBERSHIP', practiceId: '33333333-3333-4333-8333-333333333333',
          practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
        }],
      } }));

    try {
      await page.goto(`/compte/pratiques/${membership}`);
      const workspace = page.getByRole('heading', { name: 'Votre espace de travail' });
      await expect(workspace).toBeVisible();
      expired = true;
      await page.clock.fastForward(10_000);
      await expect(workspace).toHaveCount(0);
    } finally {
      releaseMeta();
    }

    const expiryHeading = page.getByRole('heading', { name: 'Votre session a expiré' });
    if (needsRetry) {
      const retry = page.getByRole('button', { name: 'Réessayer' });
      await expect(retry).toBeVisible();
      await expect(expiryHeading).toHaveCount(0);
      await retry.click();
    }
    await expect(expiryHeading).toBeFocused();
    if (outcome !== 'expiry') {
      await expect(page.getByText('Démonstration réinitialisée')).toBeVisible();
    } else {
      await expect(page.getByRole('button', { name: 'Se reconnecter' })).toBeVisible();
      await expect(page.getByText('Démonstration réinitialisée')).toHaveCount(0);
    }
  });
}
