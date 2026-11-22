import { expect, test, type Page } from '@playwright/test';

const password = 'Etaye-Jeu-Fictif-91!';

async function signIn(page: Page, email?: string) {
  if (email) await page.getByLabel('Adresse e-mail').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test('enter the fictional practice, restore the fixture and notify the previous session', async ({ page, browser, request }) => {
  const first = await request.get('/api/v1/meta');
  expect(first.ok()).toBe(true);
  const baseline = await first.json();
  expect(baseline.demoDatasetVersion).toBe('etaye-design-v1');
  expect(baseline.demoResetGeneration).toBeGreaterThanOrEqual(1);

  await page.goto('/');
  await expect(page).toHaveURL(/\/demonstration$/);
  await expect(page.getByRole('button', { name: /^Choisir · / })).toHaveCount(3);
  await page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' }).click();
  const notice = page.getByRole('main');
  await expect(notice.getByRole('heading', { name: 'Scénario praticienne' })).toBeVisible();
  await expect(notice).toContainText('Cabinet des Tilleuls');
  await expect(notice).toContainText('Ne saisissez aucune donnée réelle');
  await notice.getByRole('link', { name: 'Ouvrir la démonstration' }).click();
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue('nora.lefebvre@example.test');
  await signIn(page);
  await expect(page).toHaveURL(/\/compte\/pratiques\//);
  await expect(page.getByRole('heading', { name: 'Votre espace de travail' })).toBeVisible();
  await expect(page.getByRole('banner')).toContainText('Cabinet des Tilleuls');
  await expect(page.getByRole('main')).toContainText('Nora Lefebvre');

  const operator = await browser.newContext({
    baseURL: new URL(page.url()).origin,
    recordVideo: { dir: test.info().outputPath('operator-video') },
  });
  const panel = await operator.newPage();
  try {
    await panel.goto('/demonstration');
    await panel.getByRole('link', { name: 'Connexion opérateur' }).click();
    await signIn(panel, 'operateur@example.test');
    await expect(panel).toHaveURL(/\/compte$/);
    await panel.getByRole('link', { name: 'Retour à la démonstration' }).click();
    await panel.getByRole('button', { name: 'Réinitialiser la démonstration' }).click();
    await expect(panel.getByRole('main')).toContainText('ferme les sessions ouvertes');
    const accepted = panel.waitForResponse((response) =>
      response.url().endsWith('/api/v1/demo/reset') && response.request().method() === 'POST');
    await panel.getByRole('button', { name: 'Confirmer la réinitialisation' }).click();
    expect((await accepted).status()).toBe(202);
    await expect(panel.getByText('Démonstration réinitialisée')).toBeVisible();
    const restored = test.info().outputPath('restored-fixture.png');
    await panel.screenshot({ path: restored });
    await test.info().attach('S-01 restored fixture', { path: restored, contentType: 'image/png' });
    const after = await (await request.get('/api/v1/meta')).json();
    expect(after.demoDatasetVersion).toBe(baseline.demoDatasetVersion);
    expect(after.demoResetGeneration).toBe(baseline.demoResetGeneration + 1);
  } finally {
    await operator.close();
    const video = await panel.video()?.path();
    if (video) await test.info().attach('Operator reset video', { path: video, contentType: 'video/webm' });
  }

  await page.bringToFront();
  await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Démonstration réinitialisée')).toBeVisible();
  await expect(page.getByText('Cabinet des Tilleuls')).toHaveCount(0);
  await page.getByRole('link', { name: 'Revenir à la démonstration' }).click();
  await expect(page.getByRole('button', { name: 'Choisir · Dr Nora Lefebvre' })).toBeVisible();
});

test('Claire and Mila enter their respective existing spaces', async ({ page }) => {
  for (const scenario of [
    { actor: 'Claire Martin', path: /\/compte\/patient\//, heading: 'Votre espace patient' },
    { actor: 'Mila Dubois', path: /\/compte\/pratiques\//, heading: 'Votre espace de travail' },
  ]) {
    await page.goto('/demonstration');
    await page.getByRole('button', { name: 'Choisir · ' + scenario.actor }).click();
    await expect(page.getByRole('main')).toContainText(scenario.actor);
    await page.getByRole('link', { name: 'Ouvrir la démonstration' }).click();
    await signIn(page);
    await expect(page).toHaveURL(scenario.path);
    await expect(page.getByRole('heading', { name: scenario.heading })).toBeVisible();
    await expect(page.getByRole('main')).toContainText(scenario.actor);
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL(/\/demonstration$/);
  }
});
