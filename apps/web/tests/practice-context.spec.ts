import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { AccessContext, Session } from '../src/api/generated/types.gen';

const ids = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const tilleuls: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(1), practiceId: ids(10), practiceName: 'Cabinet des Tilleuls', displayRole: 'CLINICAL',
};
const canal: AccessContext = {
  kind: 'MEMBERSHIP', grantId: ids(2), practiceId: ids(20), practiceName: 'Cabinet du Canal', displayRole: 'ADMINISTRATION',
};
const portal: AccessContext = { ...canal, kind: 'PORTAL', grantId: ids(3), displayRole: 'PATIENT' };
const staffPath = `/compte/pratiques/${tilleuls.grantId}`;
const secondPath = `/compte/pratiques/${canal.grantId}`;
const portalPath = `/compte/patient/${portal.grantId}`;
const dirtyFixture = '/tests/fixtures/practice-switch.html';
const longName = 'Cabinet des Tilleuls — Maison de santé pluriprofessionnelle de Saint-Martin-des-Rives';

function snapshot(contexts: AccessContext[]): Session {
  const time = new Date().toISOString();
  return {
    account: {
      id: ids(100), displayName: 'Sami Rahmani', email: 'sami.rahmani@example.test', state: 'ACTIVE', locale: 'fr-FR',
      version: 0, createdAt: time, updatedAt: time,
    },
    membershipIds: contexts.filter(({ kind }) => kind === 'MEMBERSHIP').map(({ grantId }) => grantId),
    portalLinkIds: contexts.filter(({ kind }) => kind === 'PORTAL').map(({ grantId }) => grantId),
    expiresAt: new Date(Date.now() + 90_000).toISOString(),
    contexts,
  };
}

async function axe(page: Page) {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.passes.length).toBeGreaterThan(0);
  await test.info().attach('axe-practice-context', {
    body: JSON.stringify({ passes: result.passes.length, violations: result.violations.map(({ id }) => id) }),
    contentType: 'application/json',
  });
  expect(result.violations.map(({ id, nodes }) => [id, nodes.map(({ target }) => target)])).toEqual([]);
}

test('selects distinct staff and patient grants, rechecking on every switch and re-entry', async ({ page }) => {
  let reads = 0;
  let release: (() => void) | undefined;
  let block = false;
  await page.route('**/api/v1/session', async (route) => {
    expect(route.request().method()).toBe('GET');
    reads++;
    if (block) await new Promise<void>((resolve) => { release = resolve; });
    await route.fulfill({ json: snapshot([tilleuls, canal, portal]) });
  });

  await page.goto('/compte');
  await expect(page.getByRole('button', { name: /Ouvrir · Administration/ })).toBeVisible();
  await axe(page);
  await page.getByRole('button', { name: /Ouvrir · Praticien/ }).click();
  await expect(page).toHaveURL(new RegExp(staffPath + '$'));
  await expect(page.locator('header')).toContainText(tilleuls.practiceName);
  await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeFocused();
  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  await expect(page.getByRole('button', { name: /Ouvrir · Espace patient/ })).toBeVisible();

  block = true;
  await page.getByRole('button', { name: /Ouvrir · Espace patient/ }).click();
  await expect(page).toHaveURL(new RegExp(portalPath + '$'));
  await expect(page.getByRole('status')).toContainText('Vérification de l’accès');
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await expect(page.getByText(tilleuls.practiceName)).toHaveCount(0);
  await expect.poll(() => release !== undefined).toBe(true);
  block = false;
  release?.();

  await expect(page.getByRole('heading', { name: 'Votre espace patient' })).toBeFocused();
  await expect(page.locator('header')).toContainText(canal.practiceName);
  await axe(page);
  await page.evaluate(() => history.back());
  await expect(page.getByRole('heading', { name: 'Choisir un contexte de travail' })).toBeVisible();
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  expect(reads).toBeGreaterThanOrEqual(5);
});

test('a late status response from an abandoned switch cannot enter the current view', async ({ page }) => {
  let blockNext = false;
  let release: (() => void) | undefined;
  let oldFinished = false;
  await page.route('**/api/v1/session', async (route) => {
    if (!blockNext) return route.fulfill({ json: snapshot([tilleuls, canal]) });
    blockNext = false;
    await new Promise<void>((resolve) => { release = resolve; });
    try {
      await route.fulfill({ json: snapshot([tilleuls, { ...canal, practiceName: 'Cabinet périmé' }]) });
    } finally {
      oldFinished = true;
    }
  });
  await page.goto('/compte');
  const openB = page.getByRole('button', { name: /Ouvrir · Administration/ });
  await expect(openB).toBeVisible();
  blockNext = true;
  await openB.click();
  await expect(page.getByRole('status')).toContainText('Vérification de l’accès');
  await expect.poll(() => release !== undefined).toBe(true);
  await page.evaluate(() => history.back());
  await page.getByRole('button', { name: /Ouvrir · Praticien/ }).click();
  await expect(page.locator('header')).toContainText(tilleuls.practiceName);

  release?.();
  await expect.poll(() => oldFinished).toBe(true);
  await expect(page).toHaveURL(new RegExp(staffPath + '$'));
  await expect(page.getByText('Cabinet périmé')).toHaveCount(0);
  await expect(page.locator('header')).toContainText(tilleuls.practiceName);
});

test('a removed choice and an unrecognized route omit the former practice and show safe recovery', async ({ page }) => {
  let current = [tilleuls, canal];
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: snapshot(current) }));
  await page.goto('/compte');
  const open = page.getByRole('button', { name: /Ouvrir · Administration/ });
  await expect(open).toBeVisible();
  current = [tilleuls];
  await open.click();
  await expect(page).toHaveURL(new RegExp(secondPath + '$'));
  await expect(page.getByRole('heading', { name: 'Accès modifié' })).toBeFocused();
  await expect(page.getByText(canal.practiceName)).toHaveCount(0);
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await axe(page);
  await page.goto('/compte/pratiques/invalide');
  await expect(page.getByRole('alert')).toContainText('Ce contexte n’est plus disponible');
  await expect(page.getByRole('button', { name: /Ouvrir · Praticien/ })).toBeVisible();
});

test('distinguishes zero context, one context, missing projection and retry', async ({ page }) => {
  let response: Session = snapshot([]);
  await page.route('**/api/v1/session', (route) => route.fulfill({ json: response }));
  await page.goto('/compte');
  await expect(page.getByRole('status')).toContainText('Aucun contexte actif');
  await axe(page);
  response = { ...snapshot([tilleuls]), contexts: undefined };
  await page.getByRole('button', { name: 'Vérifier mes accès' }).click();
  await expect(page.getByRole('alert')).toContainText('Impossible de charger vos accès');
  await expect(page.getByTestId('protected-account')).toHaveCount(0);
  await axe(page);
  response = snapshot([tilleuls]);
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await expect(page).toHaveURL(new RegExp(staffPath + '$'));
  await expect(page.locator('header')).toContainText(tilleuls.practiceName);
  await expect(page.getByRole('button', { name: /Changer de contexte/ })).toHaveCount(0);
});

test('long French choices and the visible switch control stay within 320 px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 844 });
  await page.route('**/api/v1/session', (route) => route.fulfill({
    json: snapshot([{ ...tilleuls, practiceName: longName }, canal, portal, { ...portal, grantId: ids(4) }]),
  }));
  await page.goto('/compte?theme=sombre');
  await expect(page.getByText('Espace patient 1', { exact: true })).toBeVisible();
  await expect(page.getByText('Espace patient 2', { exact: true })).toBeVisible();
  const open = page.getByRole('button', { name: `Ouvrir · Praticien · ${longName}` });
  await expect(open).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
  await axe(page);
  await open.click();
  const switcher = page.getByRole('button', { name: /Changer de contexte/ });
  await expect(switcher).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(320);
  await axe(page);
});

test('I-20 traps focus, preserves draft on Escape and distinguishes explicit discard', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 844 });
  await page.goto(dirtyFixture);
  const trigger = page.getByRole('button', { name: 'Changer de contexte' });
  await trigger.click();
  const heading = page.getByRole('heading', { name: 'Modifications non enregistrées' });
  const close = page.getByRole('button', { name: 'Fermer' });
  await expect(heading).toBeFocused();
  await expect(heading).toHaveCSS('outline-style', 'none');
  await expect(close).toHaveCSS('outline-style', 'none');
  await page.keyboard.press('Tab');
  await expect(close).toBeFocused();
  await expect(close).toHaveCSS('outline-style', 'solid');
  await axe(page);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(page.getByLabel('Saisie', { exact: true })).toHaveValue('Saisie de démonstration en cours.');
  await trigger.click();
  await page.getByRole('button', { name: 'Quitter sans enregistrer' }).click();
  await expect(page.getByRole('heading', { name: 'Cabinet du Canal' })).toBeVisible();
  await expect(page.getByLabel('Saisie', { exact: true })).toHaveCount(0);
  await expect(page.getByTestId('attempts')).toHaveText('0');
});

test('I-20 pending save blocks dismissal and failed save retains the original input', async ({ page }) => {
  await page.goto(dirtyFixture + '?outcome=failed');
  await page.getByRole('button', { name: 'Changer de contexte' }).click();
  await page.getByRole('button', { name: 'Enregistrer et continuer' }).click();
  await expect(page.getByRole('status', { name: '' }).filter({ hasText: 'Enregistrement en cours' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Fermer' })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('Enregistrement impossible');
  await axe(page);
  await page.getByRole('button', { name: 'Rester sur cette page' }).click();
  await expect(page.getByLabel('Saisie', { exact: true })).toHaveValue('Saisie de démonstration en cours.');
  await expect(page.getByTestId('attempts')).toHaveText('1');
});

for (const outcome of ['invalid', 'unconfirmed', 'target-unavailable', 'source-unavailable', 'entered'] as const) {
  test(`I-20 distinguishes ${outcome} after an attempted save`, async ({ page }) => {
    await page.goto(dirtyFixture + '?outcome=' + outcome);
    await page.getByRole('button', { name: 'Changer de contexte' }).click();
    const save = page.getByRole('button', { name: 'Enregistrer et continuer' });
    await save.click();
    if (outcome === 'invalid') {
      await expect(page.getByRole('alert')).toContainText('Saisie à vérifier');
      await expect(save).toBeEnabled();
    } else if (outcome === 'unconfirmed') {
      await expect(page.getByRole('alert')).toContainText('Enregistrement non confirmé');
      await expect(save).toBeDisabled();
    } else if (outcome === 'target-unavailable') {
      await expect(page.getByRole('status').filter({ hasText: 'Modifications enregistrées' })).toBeVisible();
      await expect(page.getByText(canal.practiceName, { exact: true })).toHaveCount(0);
      await expect(save).toHaveCount(0);
    } else {
      await expect(page.getByRole('heading', { name: outcome === 'entered' ? canal.practiceName : 'Accès modifié' })).toBeVisible();
      await expect(page.getByLabel('Saisie', { exact: true })).toHaveCount(0);
    }
    await expect(page.getByTestId('attempts')).toHaveText('1');
  });
}
