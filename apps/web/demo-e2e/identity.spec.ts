import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { rmSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import type { Session } from '../src/api/generated/types.gen';
import { fixturePassword, heading, signIn } from './support';

test.use({ actionTimeout: 10_000 });

test('recover a fictional account through the local inbox and return after session expiry', async ({ page, browser, baseURL }) => {
  const email = 'nora.lefebvre@example.test';
  const replacement = randomBytes(24).toString('base64url');
  await page.goto('/connexion');
  await signIn(page, email);
  await expect(heading(page, 'Votre espace de travail')).toBeVisible();

  const recovery = await browser.newContext({ baseURL });
  const inbox = await recovery.newPage();
  try {
    await inbox.goto('/connexion');
    await inbox.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
    await inbox.getByLabel('Adresse e-mail').fill(email);
    await inbox.getByRole('button', { name: 'Recevoir un lien' }).click();
    await expect(heading(inbox, 'Demande de récupération reçue')).toBeVisible();

    const exporter = fileURLToPath(new URL('../../../infra/demo/recovery-inbox.py', import.meta.url));
    const snapshot = execFileSync('python3', [exporter, '--origin', new URL(page.url()).origin], {
      encoding: 'utf8', timeout: 15_000,
    }).trim();
    try {
      await inbox.goto(snapshot);
      await inbox.getByRole('link', { name: email, exact: true }).click();
      await expect(heading(inbox, 'Choisir un nouveau mot de passe')).toBeVisible();
      await inbox.locator('#r-pw').fill(replacement);
      await inbox.locator('#r-pw2').fill(replacement);
      await inbox.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
      await expect(heading(inbox, 'Mot de passe modifié')).toBeVisible();
      expect(await inbox.evaluate(() => location.hash.length)).toBe(0);
    } finally {
      rmSync(fileURLToPath(snapshot));
    }

    await page.bringToFront();
    await page.evaluate(() => window.dispatchEvent(new Event('pageshow')));
    await expect(heading(page, 'Votre session a expiré')).toBeVisible();
    await expect(page.getByTestId('protected-account')).toHaveCount(0);
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await signIn(page, email, replacement);
    await expect(heading(page, 'Votre espace de travail')).toBeVisible();
  } finally {
    await recovery.close();
  }
});

test('activate the two intended recipients and inspect their distinct grants', async ({ page }) => {
  await page.goto('/invitation#etaye-fictional-invitation-INV-TEAM-01');
  await expect(page.getByRole('heading', { name: /Rejoindre.*Cabinet des Tilleuls/ })).toBeVisible();
  await page.locator('#a-nom').fill('Alex Rivière');
  await page.locator('#a-pw').fill(fixturePassword);
  await page.locator('#a-pw-confirm').fill(fixturePassword);
  const teamAccept = page.waitForResponse((response) => response.url().endsWith('/invitations/accept'));
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  const team = await teamAccept;
  expect(team.status()).toBe(200);
  expect(await team.json()).toMatchObject({ membershipIds: [expect.any(String)], portalLinkIds: [] });
  await expect(page.getByTestId('protected-account')).toContainText('Alex Rivière');
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page).toHaveURL(/\/demonstration$/);

  await page.goto('/invitation#etaye-fictional-invitation-INV-PORTAL-03');
  await expect(heading(page, 'Activer mon espace patient')).toBeVisible();
  await page.getByLabel('Votre date de naissance').fill('12/03/1990');
  await page.getByLabel('Créer un mot de passe').fill(fixturePassword);
  const portalAccept = page.waitForResponse((response) => response.url().endsWith('/invitations/accept'));
  await page.getByRole('button', { name: 'Activer mon espace' }).click();
  const portal = await portalAccept;
  expect(portal.status()).toBe(200);
  const state = await portal.json() as Session;
  expect(state.membershipIds).toEqual([]);
  expect(state.activatedPortalLinkId).toBeTruthy();
  expect(state.portalLinkIds).toEqual([state.activatedPortalLinkId]);
  await expect(page.getByTestId('protected-account')).toContainText('Marie Collet');
  await expect(page).toHaveURL(new RegExp(`/compte/patient/${state.activatedPortalLinkId}$`));
  const session = await page.request.get('/api/v1/session');
  expect(session.status()).toBe(200);
  expect(await session.json()).toMatchObject({
    contexts: [{ kind: 'PORTAL', grantId: state.activatedPortalLinkId, displayRole: 'PATIENT' }],
  });
});

test('the operator restores the fixture after the identity walkthrough in under thirty seconds', async ({ page, request }) => {
  const before = await request.get('/api/v1/meta');
  expect(before.status()).toBe(200);
  const baseline = await before.json() as { demoResetGeneration: number };
  await page.goto('/demonstration');
  await page.getByRole('link', { name: 'Connexion opérateur' }).click();
  await signIn(page, 'operateur@example.test');
  await page.getByRole('link', { name: 'Retour à la démonstration' }).click();
  await page.getByRole('button', { name: 'Réinitialiser la démonstration' }).click();

  const started = performance.now();
  const resetCall = page.waitForResponse((response) => response.url().endsWith('/api/v1/demo/reset')
    && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirmer la réinitialisation' }).click();
  expect((await resetCall).status()).toBe(202);
  await expect(page.getByText('Démonstration réinitialisée', { exact: true })).toBeVisible({ timeout: 30_000 });
  const elapsedMs = Math.ceil(performance.now() - started);
  expect(elapsedMs).toBeLessThan(30_000);
  await test.info().attach('operator-reset-timing.json', {
    body: JSON.stringify({ elapsedMs }), contentType: 'application/json',
  });
  const after = await request.get('/api/v1/meta');
  expect(after.status()).toBe(200);
  expect(await after.json()).toMatchObject({ demoResetGeneration: baseline.demoResetGeneration + 1 });

  await page.goto('/connexion');
  await signIn(page, 'nora.lefebvre@example.test');
  await expect(heading(page, 'Votre espace de travail')).toBeVisible();
});
