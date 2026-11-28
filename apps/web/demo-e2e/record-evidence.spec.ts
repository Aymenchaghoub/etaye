import { expect, test } from '@playwright/test';
import { fixtureId, heading, signIn } from './support';

test('inspect the dated sources, measurements and laboratory results of the fictional records', async ({ page }) => {
  await page.goto('/connexion');
  await signIn(page, 'nora.lefebvre@example.test');
  await page.getByRole('link', { name: 'Patients', exact: true }).click();
  await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
  await expect(heading(page, 'Chronologie')).toBeFocused();
  await expect(page.getByRole('main')).toContainText('Poids 68,4 kg');
  const record = `/api/v1/practices/${fixtureId('PRA-A')}/records/${fixtureId('R-A-001')}`;
  const [sources, assertions] = await Promise.all([
    page.request.get(`${record}/sources`), page.request.get(`${record}/assertions`),
  ]);
  expect(sources.status()).toBe(200);
  expect(assertions.status()).toBe(200);
  expect(await sources.json()).toMatchObject({
    items: expect.arrayContaining([expect.objectContaining({ reference: 'SRC-A-001' })]),
  });
  expect(await assertions.json()).toMatchObject({
    items: expect.arrayContaining([expect.objectContaining({ sourceReference: 'SRC-A-001' })]),
  });

  const sections = page.getByRole('navigation', { name: 'Sections' });
  await sections.getByRole('link', { name: 'Antécédents et allergies' }).click();
  await page.getByRole('button', { name: /Examiner les sources/ }).click();
  const review = page.getByRole('region', { name: 'Informations à vérifier' });
  await expect(review).toContainText('Plus ancienne');
  await expect(review).toContainText('Plus récente');
  await review.getByRole('button', { name: /Afficher la source/ }).first().click();
  await expect(review.getByRole('region', { name: 'Source SRC-A-001' }))
    .toContainText('Aucune allergie connue, selon la déclaration recueillie ce jour.');

  await sections.getByRole('link', { name: 'Mesures' }).click();
  await expect(heading(page, 'Mesures')).toBeFocused();
  const rows = page.locator('.etaye-tableau tbody tr');
  await expect(rows).toHaveCount(3);
  await expect(rows.filter({ hasText: '134/82' })).toHaveCount(1);
  await expect(page.getByRole('img', { name: /Pression artérielle : 128\/80 mmHg/ })).toBeVisible();
  await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
  for (const [id, value] of [['as-j', '20'], ['as-m', '10'], ['as-a', '2026'], ['as-h', '10'], ['as-mi', '14']]) {
    await page.locator(`#${id}`).fill(value);
  }
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
  await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
  await expect(rows.filter({ hasText: '86,4' })).toHaveCount(1);
  await expect(rows.filter({ hasText: '68,4' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Revenir à la situation actuelle' }).click();
  await expect(rows.filter({ hasText: '68,4' })).toHaveCount(1);
  await page.locator('button[aria-controls^="lin-"]').click();
  await expect(page.getByRole('region', { name: 'Historique des versions : Poids' })).toContainText('86,4 kg');

  await sections.getByRole('link', { name: 'Biologie' }).click();
  await expect(heading(page, 'Biologie')).toBeFocused();
  await expect(rows).toHaveCount(3);
  const glucose = rows.filter({ hasText: 'Glucose' });
  await expect(glucose).toContainText('4,1–6,1 mmol/L');
  await glucose.getByRole('link').click();
  await expect(page.getByRole('region', { name: 'Source SRC-A-003' })).toContainText('DOC-A-001:page-1');
  await page.getByLabel('Analyse', { exact: true }).selectOption({ label: 'Créatinine' });
  await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
  await expect(rows).toHaveCount(2);
  await expect(glucose).toHaveCount(0);
  await page.getByRole('link', { name: 'Patients', exact: true }).first().click();
  await page.getByRole('link', { name: 'COLLET Marie', exact: true }).click();
  await sections.getByRole('link', { name: 'Biologie' }).click();
  await expect(rows.filter({ hasText: 'Analyse B' })).toContainText('Intervalle non fourni');
  await expect(rows.filter({ hasText: 'Analyse B' })).toContainText('< 0,060');
  await expect(rows.filter({ hasText: 'Analyse C' })).toContainText('Non détecté dans le prélèvement');
  await page.getByRole('button', { name: 'Historique du résultat · Analyse A' }).click();
  const versions = page.locator('.etaye-lab-revisions li');
  await expect(versions).toHaveCount(2);
  await expect(versions.first()).toContainText('7,40 unité A');
  await expect(versions.last()).toContainText('7,04 unité A');
});
