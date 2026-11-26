import { createHash } from 'node:crypto';
import { expect, test, type Page } from '../tests/support/browser-coverage';

/**
 * Real Spring, PostgreSQL and React stack for story 3.2, on the synthetic demonstration package (Claire Martin, Louis
 * Bernard, Hugo Petit): AT-010-A (dated history, allergies and sources with their types, the past-instant view) and
 * AT-010-B (a thin record reads unknown or not recorded; conflicting reports stay visible), plus the refusals.
 * Start the package (`infra/demo/start.sh`) and run with ETAYE_E2E_BASE_URL pointing at its web port.
 */
const password = 'Etaye-Jeu-Fictif-91!';
const fixtureId = (alias: string) => {
  const hex = createHash('md5').update(`etaye-design-v1/${alias}`).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const grant = (alias: string) => `/compte/pratiques/${fixtureId(alias)}`;
const claire = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-001')}`;
const louis = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-002')}`;
const hugoThroughNora = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-B-001')}`;

async function signIn(page: Page, email: string) {
  await page.goto('/connexion');
  await page.locator('#l-mail').fill(email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).not.toHaveURL(/\/connexion/);
}
const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });
const items = (page: Page) => page.locator('.etaye-chrono__item');

test('Nora reads Claire: dated evidence, conflicting allergy reports, the corrected weight and the sources', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(`${grant('MEM-NORA-A')}/patients`);
  await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
  await expect(heading(page, 'Chronologie')).toBeFocused();
  await expect(page).toHaveURL(claire);
  await expect(items(page)).toHaveCount(8);
  expect(await page.locator('.etaye-provenance .etaye-num:last-of-type').allTextContents())
    .toEqual(['SRC-A-005', 'SRC-A-006', 'SRC-A-007-v2', 'SRC-A-004', 'SRC-A-003', 'SRC-A-008', 'SRC-A-001', 'SRC-A-002']);
  await expect(page.locator('header.etaye-banniere')).toContainText('MARTIN Claire');
  await expect(page.locator('header.etaye-banniere')).toContainText('Déclarations discordantes à préciser · ne pas conclure à l’absence');
  const weight = items(page).nth(2);
  await expect(weight).toContainText('Poids 68,4 kg');
  await expect(weight).toContainText('Corrigé · version 2');
  await expect(weight).toContainText('enregistré le 20/10/2026 10:15');

  // The source is read on opening, with its identifier, version, author and origin.
  await weight.locator('.etaye-source > button').click();
  const region = weight.getByRole('region', { name: 'Source SRC-A-007-v2' });
  await expect(region).toContainText('SRC-A-007-v2 · version 2 · MEAS-A-003:version-2');
  await expect(region).toContainText('Remplace SRC-A-007-v1');
  await expect(region).toContainText('Poids corrigé : 68,4 kg.');

  // The record as known at 20/10/2026 10:14 (Paris): version 1 of the weight and nothing recorded later.
  await page.locator('button[aria-controls="asof-region"]').click();
  await page.locator('#as-j').fill('20');
  await page.locator('#as-m').fill('10');
  await page.locator('#as-a').fill('2026');
  await page.locator('#as-h').fill('10');
  await page.locator('#as-mi').fill('14');
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
  await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
  await expect(items(page)).toHaveCount(6);
  await expect(page.locator('main')).toContainText('Poids 86,4 kg');
  await expect(page.locator('main')).not.toContainText('68,4');
  await expect(page.locator('main')).not.toContainText('SRC-A-005');
  await expect(page.locator('header.etaye-banniere')).toContainText('Absence déclarée le 14/09/2026');
  await items(page).filter({ hasText: 'Poids 86,4 kg' }).locator('.etaye-source > button').click();
  await expect(page.getByRole('region', { name: 'Source SRC-A-007-v1' })).toContainText('Poids saisi : 86,4 kg.');
  await expect(page.locator('main')).not.toContainText('corrigé');
  await page.getByRole('button', { name: 'Revenir à la situation actuelle' }).click();
  await expect(items(page)).toHaveCount(8);

  // History and allergies, then the read-only review of the conflict.
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Antécédents et allergies' }).click();
  await expect(heading(page, 'Antécédents et allergies')).toBeFocused();
  const allergies = page.getByRole('region', { name: 'Allergies' });
  await expect(allergies).toContainText('Déclarations discordantes');
  await expect(allergies.locator('.etaye-controle li')).toHaveCount(2);
  await expect(page.getByRole('region', { name: 'Antécédents' })).toContainText('Non renseigné');
  await expect(page.getByRole('region', { name: 'Traitements déclarés' })).toContainText('Inconnu · à préciser');
  await page.getByRole('button', { name: /Examiner les sources/ }).click();
  const review = page.getByRole('region', { name: 'Informations à vérifier' });
  await expect(review).toContainText('Plus ancienne');
  await expect(review).toContainText('Plus récente');
  await review.getByRole('button', { name: /Afficher la source/ }).first().click();
  await expect(review.getByRole('region', { name: 'Source SRC-A-001' })).toContainText('Aucune allergie connue, selon la déclaration recueillie ce jour.');
});

test('Nora reads Louis: a thin record is unknown or not recorded, never normal', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(louis);
  await expect(heading(page, 'Chronologie')).toBeFocused();
  await expect(page.getByText('Historique peu renseigné')).toBeVisible();
  await expect(page.getByText('Aucun événement au dossier')).toBeVisible();
  await expect(page.locator('header.etaye-banniere')).toContainText('Non renseigné');
  const panel = page.getByRole('region', { name: 'Rubriques du dossier' });
  await expect(panel).toContainText('Allergies : non renseigné');
  await expect(panel).toContainText('Antécédents : inconnu · à préciser');
  await expect(panel).toContainText('Traitements déclarés : non renseigné');
  for (const forbidden of [/aucune allergie\b/i, /aucun traitement\b/i, /\bnormal\b/i]) {
    await expect(page.locator('main')).not.toContainText(forbidden);
  }
});

test('a record of another practice is introuvable, and an administrator without clinical capability is refused', async ({ page, browser }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(hugoThroughNora);
  await expect(page.getByText('Élément introuvable · 404')).toBeVisible();
  await expect(page.getByText('PETIT Hugo')).toHaveCount(0);
  const status = await page.evaluate(async ([practice, record]) => {
    const read = (suffix: string) => fetch(`/api/v1/practices/${practice}/records/${record}${suffix}`, { cache: 'no-store' }).then((response) => response.status);
    return [await read('/timeline'), await read('/sources'), await read('/assertions'), await read('/history')];
  }, [fixtureId('PRA-B'), fixtureId('R-B-001')] as const);
  expect(status).toEqual([404, 404, 404, 404]);

  const other = await browser.newContext();
  const mila = await other.newPage();
  await signIn(mila, 'mila.dubois@example.test');
  // Mila's own grant (administration only): the route names her context, the server refuses the clinical read.
  await mila.goto(`${grant('MEM-MILA-A')}/patients/${fixtureId('R-A-001')}`);
  await expect(mila.getByText('Accès non autorisé · 403')).toBeVisible();
  await expect(mila.getByText('MARTIN Claire')).toHaveCount(0);
  await other.close();
});
