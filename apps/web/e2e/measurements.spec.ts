import { createHash } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';

/**
 * Real Spring, PostgreSQL and React stack for story 3.3, on the synthetic demonstration package (Claire Martin, Louis
 * Bernard, Hugo Petit): AT-011-A (dated measurements with unit, origin and source, the trend, the correction lineage and the
 * as-of view before and after the correction) and AT-011-B (nothing is joined silently, a record without measurement
 * reads as such, a foreign record and an administration-only account are refused). Start the package
 * (`infra/demo/start.sh`) and run with ETAYE_E2E_BASE_URL pointing at its web port.
 */
const password = 'Etaye-Jeu-Fictif-91!';
const fixtureId = (alias: string) => {
  const hex = createHash('md5').update(`etaye-design-v1/${alias}`).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const grant = (alias: string) => `/compte/pratiques/${fixtureId(alias)}`;
const claire = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-001')}/mesures`;
const louis = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-002')}/mesures`;
const hugoThroughNora = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-B-001')}/mesures`;

async function signIn(page: Page, email: string) {
  await page.goto('/connexion');
  await page.locator('#l-mail').fill(email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).not.toHaveURL(/\/connexion/);
}
const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });
const rows = (page: Page) => page.locator('.etaye-tableau tbody tr');
const panel = (page: Page, name: string) => page.getByRole('region', { name, exact: true });
const choose = async (page: Page, parts: [string, string, string, string, string]) => {
  for (const [id, value] of [['as-j', parts[0]], ['as-m', parts[1]], ['as-a', parts[2]], ['as-h', parts[3]], ['as-mi', parts[4]]]) await page.locator(`#${id}`).fill(value);
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
};

test('Nora reads Claire: the paired blood pressure, the corrected weight with its lineage and the sources', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(`${grant('MEM-NORA-A')}/patients`);
  await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Mesures' }).click();
  await expect(heading(page, 'Mesures')).toBeFocused();
  await expect(page).toHaveURL(claire);
  await expect(rows(page)).toHaveCount(3);
  await expect(rows(page).nth(0)).toContainText('20/10/2026 10:12');
  await expect(rows(page).nth(0)).toContainText('68,4');
  await expect(rows(page).nth(0)).toContainText('kg');
  await expect(rows(page).nth(0)).toContainText('corrigé · v2');
  await expect(rows(page).nth(1)).toContainText('134/82');
  await expect(rows(page).nth(2)).toContainText('128/80');
  await expect(rows(page).nth(2)).toContainText('14/09/2026 09:35');
  await expect(page.getByRole('img', { name: /Pression artérielle :/ })).toHaveAttribute('aria-label',
    'Pression artérielle : 128/80 mmHg le 14/09/2026, 134/82 mmHg le 20/10/2026. Aucune autre mesure.');
  await expect(panel(page, 'Poids')).toContainText('1 mesure · pas de courbe');
  await expect(panel(page, 'Poids')).toContainText('version précédente 86,4 kg conservée · motif : Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');
  await expect(page.getByRole('button', { name: /Saisir|Corriger la mesure/ })).toHaveCount(0);

  // The lineage lists both versions, oldest first, each with its recording time and author.
  await page.locator('button[aria-controls^="lin-"]').click();
  const lineage = page.getByRole('region', { name: 'Historique des versions : Poids' }).locator('.etaye-lignee li');
  await expect(lineage).toHaveCount(2);
  await expect(lineage.nth(0)).toContainText('Version 1 · 86,4 kg · enregistrée le 20/10/2026 10:13 · Dr Nora Lefebvre · Observation du praticien · remplacée par la version 2');
  await expect(lineage.nth(1)).toContainText('Version 2 · 68,4 kg · enregistrée le 20/10/2026 10:15 · Dr Nora Lefebvre · Version courante');

  // The source is read on opening, through the numbered marker.
  await page.getByRole('link', { name: 'Source 1 : SRC-A-007-v2' }).click();
  const source = page.getByRole('region', { name: 'Source SRC-A-007-v2' });
  await expect(source).toContainText('Remplace SRC-A-007-v1');
  await expect(source).toContainText('Poids corrigé : 68,4 kg.');

  // The record as known at 20/10/2026 10:14 (Paris): version 1 of the weight, no trace of the correction.
  await page.locator('button[aria-controls="asof-region"]').click();
  await choose(page, ['20', '10', '2026', '10', '14']);
  await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
  await expect(panel(page, 'Poids')).toContainText('86,4');
  await expect(panel(page, 'Poids')).toContainText('Version 1 · enregistrée le 20/10/2026 10:13');
  await expect(page.locator('main')).not.toContainText('68,4');
  await expect(page.locator('main')).not.toContainText('Corrigé');
  await expect(page.locator('main')).not.toContainText('corrigé');
  await expect(rows(page)).toHaveCount(3);

  // Two minutes later the correction exists: 68,4 kg, corrected, with the original retained.
  await page.getByRole('button', { name: 'Changer de date' }).click();
  await choose(page, ['20', '10', '2026', '10', '16']);
  await expect(panel(page, 'Poids')).toContainText('68,4');
  await expect(panel(page, 'Poids')).toContainText('Corrigé · version 2');
  await expect(panel(page, 'Poids')).toContainText('version précédente 86,4 kg conservée');

  // Before the first record nothing is known, and nothing reads as normal.
  await page.getByRole('button', { name: 'Changer de date' }).click();
  await choose(page, ['01', '09', '2026', '08', '00']);
  await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
  await expect(page.getByText('Aucune mesure enregistrée au 01/09/2026 à 08:00')).toBeVisible();
  await page.getByRole('button', { name: 'Revenir à la situation actuelle' }).click();
  await expect(rows(page)).toHaveCount(3);
});

test('Nora reads Louis: a record without measurement says so, never zero or normal', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(louis);
  await expect(heading(page, 'Mesures')).toBeFocused();
  await expect(page.getByText('Aucune mesure au dossier')).toBeVisible();
  await expect(page.getByText('ce n’est ni zéro ni une valeur normale')).toBeVisible();
  await expect(rows(page)).toHaveCount(0);
  await expect(page.locator('main svg.etaye-courbe')).toHaveCount(0);
});

test('a record of another practice is introuvable on the API too, and an administrator without clinical capability is refused', async ({ page, browser }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(hugoThroughNora);
  await expect(page.getByText('Élément introuvable · 404')).toBeVisible();
  await expect(page.getByText('PETIT Hugo')).toHaveCount(0);
  const status = await page.evaluate(async ([practice, record]) => {
    const get = (path: string) => fetch(`/api/v1/practices/${practice}${path}`, { cache: 'no-store' }).then((response) => response.status);
    return [await get(`/records/${record}/measurements`), await get('/measurements/00000000-0000-4000-8000-000000000999/revisions')];
  }, [fixtureId('PRA-A'), fixtureId('R-B-001')] as const);
  expect(status).toEqual([404, 404]);

  const other = await browser.newContext();
  const mila = await other.newPage();
  await signIn(mila, 'mila.dubois@example.test');
  await mila.goto(`${grant('MEM-MILA-A')}/patients/${fixtureId('R-A-001')}/mesures`);
  await expect(mila.getByText('Accès non autorisé · 403')).toBeVisible();
  await expect(mila.getByText('MARTIN Claire')).toHaveCount(0);
  const refused = await mila.evaluate(async ([practice, record]) =>
    fetch(`/api/v1/practices/${practice}/records/${record}/measurements`, { cache: 'no-store' }).then((response) => response.status),
  [fixtureId('PRA-A'), fixtureId('R-A-001')] as const);
  expect(refused).toBe(403);
  await other.close();
});
