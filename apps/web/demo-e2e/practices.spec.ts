import { expect, test } from '@playwright/test';
import { fixtureId, heading, practicePath, signIn } from './support';

test('Sami switches between two isolated practice directories and can return', async ({ page }) => {
  await page.goto('/connexion');
  await signIn(page, 'sami.rahmani@example.test');
  await expect(heading(page, 'Choisir un contexte de travail')).toBeVisible();

  const openPractice = (practice: string) => page.getByRole('button', { name: new RegExp(`Ouvrir · Praticien · ${practice}`) }).click();
  const recordIn = (practice: string, record: string) =>
    page.request.get(`/api/v1/practices/${fixtureId(practice)}/records/${fixtureId(record)}`);
  const tilleuls = 'Cabinet des Tilleuls';
  const canal = 'Cabinet du Canal';
  await openPractice(tilleuls);
  await expect(page).toHaveURL(practicePath('MEM-SAMI-A'));
  await page.getByRole('link', { name: 'Patients', exact: true }).click();
  await expect(page.getByRole('link', { name: 'MARTIN Claire', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'PETIT Hugo', exact: true })).toHaveCount(0);
  expect((await recordIn('PRA-A', 'R-B-001')).status()).toBe(404);

  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  await openPractice(canal);
  await expect(page).toHaveURL(practicePath('MEM-SAMI-B'));
  await expect(page.getByRole('banner')).toContainText(canal);
  await page.getByRole('link', { name: 'Patients', exact: true }).click();
  await expect(page.getByRole('link', { name: 'PETIT Hugo', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'MARTIN Claire', exact: true })).toHaveCount(0);
  expect((await recordIn('PRA-B', 'R-A-001')).status()).toBe(404);
  expect((await recordIn('PRA-B', 'R-B-001')).status()).toBe(200);

  await page.getByRole('button', { name: /Changer de contexte/ }).click();
  await openPractice(tilleuls);
  await expect(page).toHaveURL(practicePath('MEM-SAMI-A'));
  await expect(page.getByRole('banner')).toContainText(tilleuls);
});
