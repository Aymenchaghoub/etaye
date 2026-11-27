import { createHash } from 'node:crypto';
import { expect, test, type Page } from '../tests/support/browser-coverage';
import { accessible } from '../tests/support/patients';

const password = 'Etaye-Jeu-Fictif-91!';
const fixtureId = (alias: string) => {
  const hex = createHash('md5').update(`etaye-design-v1/${alias}`).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const grant = (alias: string) => `/compte/pratiques/${fixtureId(alias)}`;
const claire = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-001')}/biologie`;
const marie = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-003')}/biologie`;
const louis = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-A-002')}/biologie`;
const hugoThroughNora = `${grant('MEM-NORA-A')}/patients/${fixtureId('R-B-001')}/biologie`;

async function signIn(page: Page, email: string) {
  await page.goto('/connexion');
  await page.locator('#l-mail').fill(email);
  await page.locator('#l-pw').fill(password);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).not.toHaveURL(/\/connexion/);
}
const heading = (page: Page, name: string) => page.getByRole('heading', { name, exact: true, level: 1 });
const rows = (page: Page) => page.locator('.etaye-tableau tbody tr');
const choose = async (page: Page, parts: [string, string, string, string, string]) => {
  for (const [id, value] of [['as-j', parts[0]], ['as-m', parts[1]], ['as-a', parts[2]], ['as-h', parts[3]], ['as-mi', parts[4]]]) await page.locator(`#${id}`).fill(value);
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
};

test('Nora reads Claire’s non-inferred intervals and exact laboratory source', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(`${grant('MEM-NORA-A')}/patients`);
  await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Biologie' }).click();
  await expect(page).toHaveURL(claire);
  await expect(heading(page, 'Biologie')).toBeFocused();
  await expect(rows(page)).toHaveCount(3);
  const glucose = rows(page).filter({ hasText: 'Glucose' });
  await expect(glucose).toContainText('5,2');
  await expect(glucose).toContainText('4,1–6,1 mmol/L');
  await expect(rows(page).filter({ hasText: 'Créatinine' })).toHaveCount(2);
  const marker = glucose.getByRole('link');
  await marker.click();
  const reference = page.getByRole('region', { name: 'Source SRC-A-003' });
  await expect(reference).toContainText('DOC-A-001:page-1');
  await expect(reference).toContainText('4,1–6,1 mmol/L');
  await reference.getByRole('link', {name: 'Revenir au résultat'}).click();
  await expect(marker).toBeFocused();
  await expect(page.getByRole('button', {name: /Saisir|Corriger|Extraire/})).toHaveCount(0);
  await accessible(page);

  await page.getByLabel('Analyse', {exact: true}).selectOption({label:'Créatinine'});
  await page.getByRole('button', {name:'Appliquer les filtres'}).click();
  await expect(rows(page)).toHaveCount(2);
  await expect(rows(page).filter({ hasText: 'Glucose' })).toHaveCount(0);
  await accessible(page);
  await page.goto(louis);
  await expect(page.getByText('Aucun résultat de biologie', {exact: true})).toBeVisible();
  await accessible(page);
});

test('Marie provides qualitative, comparator and matched point evidence, with both sides of a correction', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(marie);
  await expect(rows(page)).toHaveCount(5);
  await expect(rows(page).filter({hasText:'Analyse B'})).toContainText('< 0,060');
  await expect(rows(page).filter({hasText:'Analyse B'})).toContainText('Intervalle non fourni');
  await expect(rows(page).filter({hasText:'Analyse C'})).toContainText('Non détecté dans le prélèvement');
  await expect(page.getByRole('img', {name:/Analyse D · unité A/}).locator('circle')).toHaveCount(2);
  await page.getByRole('button', {name: 'Historique du résultat · Analyse A'}).click();
  const versions = page.locator('.etaye-lab-revisions li');
  await expect(versions).toHaveCount(2);
  await expect(versions.first()).toContainText('7,40 unité A');
  await expect(versions.last()).toContainText('7,04 unité A');
  await expect(versions.last()).toContainText('chiffres inversés');
  await accessible(page);

  await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
  await choose(page, ['21', '10', '2026', '10', '14']);
  await expect(rows(page)).toHaveCount(1);
  await expect(rows(page).first()).toContainText('7,40');
  await expect(page.getByRole('button', {name: 'Historique du résultat · Analyse A'})).toHaveCount(0);
  await expect(page.locator('.etaye-biologie')).not.toContainText('7,04');
  await accessible(page);

  await page.getByRole('button', { name: 'Changer de date' }).click();
  await choose(page, ['21', '10', '2026', '10', '15']);
  await expect(rows(page)).toHaveCount(1);
  await expect(rows(page).first()).toContainText('7,04');
  await accessible(page);
});

test('foreign and restricted records hide their content', async ({ page }) => {
  await signIn(page, 'nora.lefebvre@example.test');
  await page.goto(hugoThroughNora);
  await expect(heading(page, 'Élément introuvable')).toBeVisible();
  await expect(rows(page)).toHaveCount(0);
  await accessible(page);
  await page.context().clearCookies();
  await signIn(page, 'mila.dubois@example.test');
  await page.goto(`${grant('MEM-MILA-A')}/patients/${fixtureId('R-A-001')}/biologie`);
  await expect(heading(page, 'Cet espace est réservé aux soins')).toBeVisible();
  await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
  await accessible(page);
});
