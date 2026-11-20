import { randomBytes } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';

test.use({ trace: 'off', screenshot: 'off', video: 'off' });

const enabled = process.env.ETAYE_E2E_AUTH === 'true';
const inboxOrigin = 'http://127.0.0.1:8025';

test.skip(!enabled, 'Enable with ETAYE_E2E_AUTH=true on an isolated demo stack.');

async function signIn(page: Page, address: string, password: string) {
  await page.locator('#l-mail').fill(address);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByTestId('protected-account')).toBeVisible();
}

test('real browser signs in, consumes the committed recovery link and revokes another session', async ({ browser, page }) => {
  const address = process.env.ETAYE_E2E_AUTH_EMAIL;
  const password = process.env.ETAYE_E2E_AUTH_PASSWORD;
  if (!address?.endsWith('@example.test') || !password) {
    throw new Error('Configure a disposable @example.test account and password for the isolated stack.');
  }
  const replacement = randomBytes(24).toString('base64url');
  await page.goto('/connexion');
  await signIn(page, address, password);

  const recoveryContext = await browser.newContext();
  let passwordChanged = false;
  try {
    const recovering = await recoveryContext.newPage();
    await recovering.goto('http://localhost:5173/connexion/patient');
    await expect(recovering.getByRole('heading', { name: 'Se connecter à votre espace patient' })).toBeVisible();
    await recovering.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
    await recovering.locator('#r-mail').fill(address);
    await recovering.getByRole('button', { name: 'Recevoir un lien' }).click();
    await expect(recovering.getByRole('heading', { name: 'Demande de récupération reçue' })).toBeVisible();

    const inbox = await recoveryContext.newPage();
    await inbox.goto(inboxOrigin + '/');
    await inbox.getByRole('link', { name: address, exact: true }).click();
    await expect(inbox.getByRole('heading', { name: 'Choisir un nouveau mot de passe' })).toBeVisible();
    expect(await inbox.evaluate(() => location.hash.length)).toBe(44);
    await inbox.locator('#r-pw').fill(replacement);
    await inbox.locator('#r-pw2').fill(replacement);
    await inbox.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
    await expect(inbox.getByRole('heading', { name: 'Mot de passe modifié' })).toBeVisible();
    expect(await inbox.evaluate(() => location.hash.length)).toBe(0);
    passwordChanged = true;

    await page.evaluate(() => window.dispatchEvent(new Event('pageshow')));
    await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeVisible();
    await expect(page.getByTestId('protected-account')).toHaveCount(0);
    await page.getByRole('button', { name: 'Se reconnecter' }).click();
    await signIn(page, address, replacement);
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page.getByRole('heading', { name: 'Se connecter', exact: true })).toBeVisible();

    // Restore the original test password so subsequent test runs succeed on the same stack
    await recovering.goto('http://localhost:5173/connexion/patient');
    await recovering.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
    await recovering.locator('#r-mail').fill(address);
    await recovering.getByRole('button', { name: 'Recevoir un lien' }).click();
    await expect(recovering.getByRole('heading', { name: 'Demande de récupération reçue' })).toBeVisible();

    await inbox.goto(inboxOrigin + '/');
    await inbox.getByRole('link', { name: address, exact: true }).click();
    await expect(inbox.getByRole('heading', { name: 'Choisir un nouveau mot de passe' })).toBeVisible();
    await inbox.locator('#r-pw').fill(password);
    await inbox.locator('#r-pw2').fill(password);
    await inbox.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
    await expect(inbox.getByRole('heading', { name: 'Mot de passe modifié' })).toBeVisible();
    passwordChanged = false;
  } finally {
    if (passwordChanged) {
      try {
        const recovering = await recoveryContext.newPage();
        await recovering.goto('http://localhost:5173/connexion/patient');
        await recovering.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
        await recovering.locator('#r-mail').fill(address);
        await recovering.getByRole('button', { name: 'Recevoir un lien' }).click();
        const inbox = await recoveryContext.newPage();
        await inbox.goto(inboxOrigin + '/');
        await inbox.getByRole('link', { name: address, exact: true }).click();
        await inbox.locator('#r-pw').fill(password);
        await inbox.locator('#r-pw2').fill(password);
        await inbox.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
      } catch {
        // Best effort restore
      }
    }
    await recoveryContext.close();
  }
});
