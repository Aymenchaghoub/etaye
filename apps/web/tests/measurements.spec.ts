import { expect, test, type Page } from '@playwright/test';
import { accessible, base, canal, claire, heading, ids, louis, noOverflow, noRingOnHeading, problem, tilleuls, usesBackground } from './support/patients';
import { claireMeasurements, DIASTOLIC, type Meas, measurementBody, stubMeasurements, SYSTOLIC } from './support/measurements';

const RECORD = `${base}/patients/${claire.recordId}`;
const MEASURES = `${RECORD}/mesures`;
const LOUIS = `${base}/patients/${louis.recordId}/mesures`;
const rows = (page: Page) => page.locator('.etaye-tableau tbody tr');
const banner = (page: Page) => page.locator('header.etaye-banniere');
const panel = (page: Page, name: string) => page.getByRole('region', { name, exact: true });
const tabs = (page: Page) => page.getByRole('navigation', { name: 'Sections' });

const choose = async (page: Page, parts: { j: string; m: string; a: string; h: string; mi: string }) => {
  await page.locator('#as-j').fill(parts.j);
  await page.locator('#as-m').fill(parts.m);
  await page.locator('#as-a').fill(parts.a);
  await page.locator('#as-h').fill(parts.h);
  await page.locator('#as-mi').fill(parts.mi);
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
};

const generated = (count: number, patch: (n: number) => Partial<Meas> & Pick<Meas, 'concept' | 'value' | 'unit'>): Meas[] =>
  Array.from({ length: count }, (_, n) => ({
    id: ids(20000 + n), lineage: ids(21000 + n), revision: 1, author: 'Dr Nora Lefebvre', sourceId: ids(5004), reference: `SRC-G-${n}`,
    at: new Date(Date.UTC(2026, 4, 26 + 7 * n, 7)).toISOString(), recordedAt: new Date(Date.UTC(2026, 4, 26 + 7 * n, 8)).toISOString(), ...patch(n),
  }));

test.describe('S-11 measurements (AT-011-A, AT-011-B)', () => {
  test('lists the measurements with value, unit, origin and source, the paired blood pressure and the corrected weight, read-only', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page);
    await page.goto(MEASURES);
    await expect(heading(page, 'Mesures')).toBeFocused();
    await noRingOnHeading(page);
    await expect(rows(page)).toHaveCount(3);
    const first = rows(page).nth(0);
    await expect(first).toContainText('20/10/2026 10:12');
    await expect(first).toContainText('Poids');
    await expect(first).toContainText('68,4');
    await expect(first).toContainText('kg');
    await expect(first).toContainText('Observé au cabinet · Dr Nora Lefebvre');
    await expect(first).toContainText('corrigé · v2');
    await expect(rows(page).nth(1)).toContainText('134/82');
    await expect(rows(page).nth(1)).toContainText('mmHg');
    await expect(rows(page).nth(1)).toContainText('Observée au cabinet · Dr Nora Lefebvre');
    await expect(rows(page).nth(2)).toContainText('14/09/2026 09:35');
    await expect(rows(page).nth(2)).toContainText('128/80');
    // The chart is an image whose text alternative states every plotted value; the table is the reference.
    const chart = page.getByRole('img', { name: /Pression artérielle :/ });
    await expect(chart).toHaveAttribute('aria-label', 'Pression artérielle : 128/80 mmHg le 14/09/2026, 134/82 mmHg le 20/10/2026. Aucune autre mesure.');
    await expect(page.getByText('Deux points reliés par un trait : aucune mesure entre le 14/09/2026 et le 20/10/2026.')).toBeVisible();
    await expect(page.getByText('Le trait n’est pas une mesure. Le tableau ci-dessous est la référence.').first()).toBeVisible();
    await expect(panel(page, 'Pression artérielle')).toContainText('2 mesures · unité unique mmHg');
    // The weight is one point: no curve, the current value, the previous value and the reason as stored.
    const weight = panel(page, 'Poids');
    await expect(weight).toContainText('1 mesure · pas de courbe');
    await expect(weight).toContainText('68,4');
    await expect(weight).toContainText('le 20/10/2026 à 10:12');
    await expect(weight).toContainText('Corrigé · version 2');
    await expect(weight).toContainText('version précédente 86,4 kg conservée · motif : Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');
    await expect(weight.getByRole('img')).toHaveCount(0);
    // Nothing writes, nothing is delivered by another story, and the order is fixed (no sortable header).
    await expect(page.getByRole('button', { name: /Saisir|Corriger la mesure|Enregistrer|Ajouter/ })).toHaveCount(0);
    await expect(page.getByRole('link', { name: /Biologie|Documents/ })).toHaveCount(0);
    await expect(page.locator('th button, th [aria-sort]')).toHaveCount(0);
    await expect(page.getByText('R-A-001')).toHaveCount(0);
    await expect(page.getByText('Date inconnue')).toHaveCount(0);
    await expect(banner(page)).toContainText('MARTIN Claire');
    await expect(tabs(page).getByRole('link', { name: 'Mesures' })).toHaveAttribute('aria-current', 'page');
    expect(measurementCalls.lists[0].searchParams.get('limit')).toBe('25');
    expect(measurementCalls.lists[0].searchParams.has('asOf')).toBe(false);
    expect(measurementCalls.lists[0].searchParams.has('from')).toBe(false);
    await accessible(page);
  });

  test('the history of a corrected weight opens in place, oldest first, by Enter and Space, and keeps focus', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page);
    await page.goto(MEASURES);
    // The button changes its name when it opens, so it is found by what it controls.
    const button = page.locator('button[aria-controls^="lin-"]');
    await expect(button).toHaveAttribute('aria-expanded', 'false');
    await button.focus();
    await page.keyboard.press('Enter');
    await expect(button).toHaveAttribute('aria-expanded', 'true');
    await expect(button).toBeFocused();
    const region = page.getByRole('region', { name: 'Historique des versions : Poids' });
    const entries = region.locator('.etaye-lignee li');
    await expect(entries).toHaveCount(2);
    await expect(entries.nth(0)).toContainText('Version 1 · 86,4 kg · enregistrée le 20/10/2026 10:13 · Dr Nora Lefebvre · Observation du praticien · remplacée par la version 2');
    await expect(entries.nth(1)).toContainText('Version 2 · 68,4 kg · enregistrée le 20/10/2026 10:15 · Dr Nora Lefebvre · Version courante');
    await expect(entries.nth(1)).toContainText('motif : Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');
    await accessible(page);
    await page.keyboard.press('Space');
    await expect(button).toHaveAttribute('aria-expanded', 'false');
    await expect(button).toBeFocused();
    await expect(region).toHaveCount(0);
    expect(measurementCalls.revisions.every((url) => url.pathname.endsWith(`${ids(9205)}/revisions`))).toBe(true);
  });

  test('a failing history says the current value stays and offers a retry', async ({ page }) => {
    let fail = true;
    await stubMeasurements(page, { revisions: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(MEASURES);
    // The failed read is said in the note itself: the previous value is not loaded, and the screen does not pretend otherwise.
    await expect(panel(page, 'Poids')).toContainText('version précédente conservée · valeur non chargée · motif');
    await expect(panel(page, 'Poids')).not.toContainText('version précédente 86,4 kg');
    await page.getByRole('button', { name: /Voir l’historique/ }).click();
    const region = page.getByRole('region', { name: 'Historique des versions : Poids' });
    await expect(region.getByRole('alert')).toContainText('L’historique n’a pas pu être chargé');
    await expect(region).toContainText('La valeur courante reste affichée au-dessus. Rien n’a été supprimé.');
    await accessible(page);
    fail = false;
    await region.getByRole('button', { name: 'Réessayer' }).click();
    await expect(region.locator('.etaye-lignee li')).toHaveCount(2);
    await expect(panel(page, 'Poids')).toContainText('version précédente 86,4 kg conservée');
    await expect(panel(page, 'Poids')).not.toContainText('valeur non chargée');
  });

  test('a lineage longer than the response says it was shortened and still lists the original and the current value', async ({ page }) => {
    const weight = claireMeasurements.filter((row) => row.concept === 'WEIGHT');
    const original = weight[0];
    const chain = Array.from({ length: 99 }, (_, n) => ({
      ...original, id: ids(30000 + n), revision: n + 3, supersedes: n === 0 ? ids(9205) : ids(30000 + n - 1), value: String(70 + n),
      recordedAt: new Date(Date.UTC(2026, 9, 21, 8, n)).toISOString(), reference: `SRC-A-007-v${n + 3}`,
    }));
    const listed = [original, ...chain];
    await stubMeasurements(page, {
      measurements: { [claire.recordId]: [...weight, ...chain], [louis.recordId]: [] },
      revisions: () => ({ json: { items: listed.map(measurementBody), truncated: true } }),
    });
    await page.goto(MEASURES);
    await page.locator('button[aria-controls^="lin-"]').first().click();
    const region = page.getByRole('region', { name: 'Historique des versions : Poids' });
    await expect(region.locator('.etaye-lignee li')).toHaveCount(100);
    await expect(region.locator('.etaye-lignee li').first()).toContainText('Version 1 · 86,4 kg');
    // The next listed revision is version 3 and does not name version 1 as its predecessor: no false "remplacée par".
    await expect(region.locator('.etaye-lignee li').first()).toContainText('remplacée ensuite · versions intermédiaires non affichées');
    await expect(region.locator('.etaye-lignee li').first()).not.toContainText('remplacée par la version 3');
    await expect(region.locator('.etaye-lignee li').nth(1)).toContainText('remplacée par la version 4');
    await expect(region).toContainText('Historique raccourci : la version d’origine et les 99 plus récentes sont listées');
    await accessible(page);
  });

  test('a marker opens its source on the spot, moves focus to it, and "Revenir à l’élément" returns focus to the marker', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(MEASURES);
    const marker = page.getByRole('link', { name: 'Source 1 : SRC-A-007-v2' });
    await marker.focus();
    await page.keyboard.press('Enter');
    const entry = page.locator('.etaye-sources-liste li').first();
    await expect(entry.getByRole('button', { name: /Masquer la source/ })).toBeFocused();
    const region = entry.getByRole('region', { name: 'Source SRC-A-007-v2' });
    await expect(region).toContainText('SRC-A-007-v2 · version 2 · MEAS-A-003:version-2');
    await expect(region).toContainText('Remplace SRC-A-007-v1');
    await expect(region).toContainText('Poids corrigé : 68,4 kg.');
    await accessible(page);
    await region.getByRole('link', { name: 'Revenir à l’élément' }).click();
    await expect(marker).toBeFocused();
    // The disclosure is a button operated by Enter and Space.
    const button = page.locator('.etaye-sources-liste li').nth(1).locator('.etaye-source > button');
    await expect(button).toHaveAttribute('aria-expanded', 'false');
    await button.focus();
    await page.keyboard.press('Space');
    await expect(button).toHaveAttribute('aria-expanded', 'true');
    await expect(button).toBeFocused();
  });

  test('a source that fails says so, keeps the row, and works again on retry', async ({ page }) => {
    let fail = true;
    await stubMeasurements(page, {}, { source: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(MEASURES);
    const entry = page.locator('.etaye-sources-liste li').nth(1);
    await entry.getByRole('button', { name: /Afficher la source/ }).click();
    await expect(entry.getByRole('alert')).toContainText('La source n’a pas pu être chargée');
    await expect(rows(page)).toHaveCount(3);
    fail = false;
    await entry.getByRole('button', { name: 'Réessayer' }).click();
    await expect(entry.getByRole('region', { name: 'Source SRC-A-004' })).toContainText('Pression artérielle mesurée au cabinet : 134/82 mmHg.');
  });

  test('a record without measurement says so and never reads as zero or normal', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(LOUIS);
    await expect(heading(page, 'Mesures')).toBeFocused();
    await expect(page.getByText('Aucune mesure au dossier')).toBeVisible();
    await expect(page.getByText('Aucune mesure n’a été enregistrée pour BERNARD Louis. Aucune valeur n’est supposée : ce n’est ni zéro ni une valeur normale.')).toBeVisible();
    await expect(page.getByRole('search', { name: 'Filtrer les mesures' })).toHaveCount(0);
    await expect(page.locator('main svg.etaye-courbe')).toHaveCount(0);
    await expect(rows(page)).toHaveCount(0);
    await expect(banner(page)).toContainText('Non renseigné');
    await expect(page.getByRole('button', { name: /Saisir|Ajouter/ })).toHaveCount(0);
    await accessible(page);
  });

  test('moving to another record through the browser history never keeps the first record on screen', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    await expect(panel(page, 'Poids')).toBeVisible();
    await page.evaluate((target) => {
      window.history.pushState(null, '', target);
      window.dispatchEvent(new PopStateEvent('popstate'));
    }, LOUIS);
    await expect(page.getByText('Aucune mesure au dossier')).toBeVisible();
    await expect(rows(page)).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText('Pression artérielle');
    await expect(page.locator('main')).not.toContainText('Poids');
    await expect(banner(page)).toContainText('BERNARD Louis');
    await expect(banner(page)).not.toContainText('MARTIN Claire');
  });

  test('while loading it shows a skeleton and never "Aucune mesure" (C3)', async ({ page }) => {
    await stubMeasurements(page, { list: () => ({ json: { items: [], nextCursor: null }, delay: 900 }) });
    await page.goto(MEASURES);
    await expect(page.getByText('Chargement des mesures…')).toBeVisible();
    await expect(page.locator('main')).not.toContainText('Aucune mesure');
    await expect(page.getByRole('search', { name: 'Filtrer les mesures' })).toHaveCount(0);
    await expect(page.locator('[aria-busy="true"]').first()).toBeVisible();
    await accessible(page);
    await expect(page.getByText('Chargement des mesures…')).toHaveCount(0);
  });

  test('failing measurements keep the identity banner and the tabs, say nothing was deleted, and retry', async ({ page }) => {
    let fail = true;
    await stubMeasurements(page, { list: () => (fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined) });
    await page.goto(MEASURES);
    await expect(page.getByRole('alert').filter({ hasText: 'Les mesures n’ont pas pu être chargées' })).toBeVisible();
    await expect(page.getByText('Ceci n’est pas un dossier sans mesure : les mesures n’ont pas pu être lues.')).toBeVisible();
    await expect(page.getByText('Rien n’a été supprimé.')).toBeVisible();
    await expect(page.getByText('Aucune mesure au dossier')).toHaveCount(0);
    await expect(banner(page)).toContainText('Déclarations discordantes');
    await expect(tabs(page).getByRole('link', { name: 'Chronologie' })).toBeVisible();
    await accessible(page);
    fail = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(rows(page)).toHaveCount(3);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('a throttled read says how long to wait and can be retried', async ({ page }) => {
    let throttled = true;
    await stubMeasurements(page, { list: () => (throttled ? { status: 429, json: problem(429, 'RATE_LIMITED'), headers: { 'Retry-After': '7' } } : undefined) });
    await page.goto(MEASURES);
    await expect(page.getByRole('alert').filter({ hasText: 'Trop de requêtes' })).toContainText('Patientez 7 secondes avant de recharger les mesures.');
    await accessible(page);
    throttled = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(rows(page)).toHaveCount(3);
  });

  test('a refusal of the measurements removes every clinical row at once; an absent record is the restricted state', async ({ page }) => {
    await stubMeasurements(page, { list: () => ({ status: 403, json: problem(403, 'FORBIDDEN') }) });
    await page.goto(MEASURES);
    await expect(page.getByText('Accès non autorisé · 403')).toBeVisible();
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
    await expect(rows(page)).toHaveCount(0);
    await stubMeasurements(page, { list: () => ({ status: 404, json: problem(404, 'NOT_FOUND') }) });
    await page.goto(MEASURES);
    await expect(page.getByText('Élément introuvable · 404')).toBeVisible();
    await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
  });
});

test.describe('filters, period and pages', () => {
  test('the measure filter sends the exact codes, keeps a visible reset and tells a filter without result from an empty record', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page);
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    await expect(page.getByLabel('Mesure', { exact: true }).locator('option')).toHaveText(['Toutes', 'Pression artérielle', 'Poids']);
    await expect(page.getByLabel('Période', { exact: true }).locator('option')).toHaveText(['Tout le dossier', '3 derniers mois', '12 derniers mois']);
    await page.getByLabel('Mesure', { exact: true }).selectOption('p');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(rows(page)).toHaveCount(2);
    expect(measurementCalls.lists.at(-1)!.searchParams.getAll('concept')).toEqual([SYSTOLIC, DIASTOLIC]);
    await page.getByLabel('Mesure', { exact: true }).selectOption('c:WEIGHT');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(rows(page)).toHaveCount(1);
    expect(measurementCalls.lists.at(-1)!.searchParams.getAll('concept')).toEqual(['WEIGHT']);
    await page.getByRole('button', { name: 'Réinitialiser les filtres' }).click();
    await expect(rows(page)).toHaveCount(3);
    expect(measurementCalls.lists.at(-1)!.searchParams.has('concept')).toBe(false);
  });

  test('a relative period counts back from now; a filter without result says the record is not empty (never "Aucune mesure au dossier")', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page, { measurements: { [claire.recordId]: claireMeasurements.map((row) => ({ ...row, at: '2020-01-01T08:00:00Z', recordedAt: '2020-01-01T09:00:00Z' })), [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    await page.getByLabel('Période', { exact: true }).selectOption('3m');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    const from = Date.parse(measurementCalls.lists.at(-1)!.searchParams.get('from')!);
    const expected = new Date(); expected.setMonth(expected.getMonth() - 3);
    expect(Math.abs(from - expected.getTime())).toBeLessThan(2 * 86_400_000);
    await expect(page.getByText('Aucune mesure ne correspond à ces filtres')).toBeVisible();
    await expect(page.getByText('Cela ne signifie pas que le dossier est sans mesure')).toBeVisible();
    await expect(page.getByText('0 mesure affichée')).toBeVisible();
    await expect(page.getByText('Aucune mesure au dossier')).toHaveCount(0);
    await accessible(page);
    await page.getByRole('button', { name: 'Réinitialiser les filtres' }).last().click();
    await expect(rows(page)).toHaveCount(3);
  });

  test('pages of 25 follow the cursor; a series longer than the loaded pages says it is not the whole trend', async ({ page }) => {
    const list = generated(30, (n) => ({ concept: 'A', value: String(30 + (n * 7) % 23), unit: 'u' }));
    let failNext = true;
    const { measurementCalls } = await stubMeasurements(page, {
      measurements: { [claire.recordId]: list, [louis.recordId]: [] },
      list: (url) => (url.searchParams.get('cursor') && failNext ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined),
    });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(25);
    await expect(page.getByRole('status').filter({ hasText: '25 mesures affichées · d’autres mesures existent' })).toBeVisible();
    await expect(panel(page, 'A')).toContainText('25 mesures · d’autres mesures existent');
    await expect(page.getByText('Courbe limitée aux mesures chargées')).toBeVisible();
    await expect(page.getByText('25 mesures sont affichées ; d’autres existent. Cette courbe n’est pas la tendance complète de la période.')).toBeVisible();
    await expect(page.getByRole('img', { name: /Courbe limitée aux mesures chargées/ })).toBeVisible();
    expect(measurementCalls.lists[0].searchParams.get('limit')).toBe('25');
    await accessible(page);
    await page.getByRole('button', { name: 'Afficher plus de mesures' }).click();
    await expect(page.getByText('La suite n’a pas pu être chargée')).toBeVisible();
    await expect(rows(page)).toHaveCount(25);
    failNext = false;
    await page.getByRole('button', { name: 'Réessayer' }).last().click();
    await expect(rows(page)).toHaveCount(30);
    await expect(page.getByText('Courbe limitée aux mesures chargées')).toHaveCount(0);
    await expect(page.getByRole('status').filter({ hasText: '30 mesures affichées' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Afficher plus de mesures' })).toHaveCount(0);
  });

  test('when no drawn chart can load more, the table carries the only button that does', async ({ page }) => {
    // Thirty one-point series: every panel is a single point, so none of them has a button of its own.
    const list = generated(30, (n) => ({ concept: `K${n}`, value: String(n), unit: 'u' }));
    await stubMeasurements(page, { measurements: { [claire.recordId]: list, [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(25);
    const more = page.getByRole('button', { name: 'Afficher plus de mesures' });
    await expect(more).toHaveCount(1);
    await more.click();
    await expect(rows(page)).toHaveCount(30);
    await expect(more).toHaveCount(0);
  });

  test('a corrected measurement that no chart draws still shows its previous value and its reason', async ({ page }) => {
    // C is the oldest of three series: it stays in the table, and its correction must not disappear with its chart.
    const base = generated(5, (n) => ({ concept: n === 0 ? 'C' : n < 3 ? 'A' : 'B', value: String(10 + n), unit: 'u' }));
    const [c1] = base;
    const c2 = { ...c1, id: ids(20900), revision: 2, supersedes: c1.id, value: '6', sourceId: ids(5005), reference: 'SRC-G-C2',
      recordedAt: new Date(Date.parse(c1.recordedAt) + 2 * 86_400_000).toISOString(), reason: 'Valeur corrigée après relecture.' };
    await stubMeasurements(page, { measurements: { [claire.recordId]: [...base, c2], [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(5);
    await expect(page.getByText('1 autre série n’est pas tracée')).toBeVisible();
    // Two panels are drawn (each has a wide and a narrow form, only one of which is exposed): A and B, never C.
    await expect(page.getByRole('img', { name: /^[AB] :/ })).toHaveCount(2);
    await expect(page.getByRole('img', { name: /^C :/ })).toHaveCount(0);
    const section = page.getByRole('region', { name: 'Mesures corrigées' });
    await expect(section).toBeVisible();
    await expect(section).toContainText('C');
    await expect(section).toContainText('6 u');
    await expect(section).toContainText('Corrigé · version 2');
    await expect(section).toContainText('version précédente 10 u conservée · motif : Valeur corrigée après relecture.');
    await accessible(page);
  });

  test('a late page of an earlier view never replaces the view chosen after it', async ({ page }) => {
    const list = generated(30, (n) => ({ concept: 'A', value: String(10 + n), unit: 'u' }));
    await stubMeasurements(page, {
      measurements: { [claire.recordId]: list, [louis.recordId]: [] },
      list: (url) => (url.searchParams.get('cursor') ? { json: { items: [], nextCursor: null }, delay: 1200 } : undefined),
    });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(25);
    const isNextPage = (request: { url: () => string }) => request.url().includes('cursor=');
    const stale = Promise.race([page.waitForEvent('requestfinished', isNextPage), page.waitForEvent('requestfailed', isNextPage)]);
    await page.getByRole('button', { name: 'Afficher plus de mesures' }).click();
    await page.locator('button[aria-controls="asof-region"]').click();
    await choose(page, { j: '01', m: '05', a: '2026', h: '10', mi: '00' });
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
    await stale;
    await expect(rows(page)).toHaveCount(0);
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
  });
});

test.describe('the series rule (BR-009)', () => {
  test('two units of one measure are two series with their own axis, never converted or merged', async ({ page }) => {
    const list = [
      ...generated(3, (n) => ({ concept: 'A', value: String(10 + 2 * n), unit: 'unité A', at: new Date(Date.UTC(2026, 8, 14 + 20 * n, 7)).toISOString() })),
      ...generated(2, (n) => ({ id: ids(22000 + n), lineage: ids(23000 + n), concept: 'A', value: String(80 + 15 * n), unit: 'unité B', at: new Date(Date.UTC(2026, 8, 15 + 36 * n, 8)).toISOString() })),
    ];
    await stubMeasurements(page, { measurements: { [claire.recordId]: list, [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(5);
    await expect(panel(page, 'A · unité A')).toContainText('3 mesures');
    await expect(panel(page, 'A · unité B')).toContainText('2 mesures');
    await expect(page.getByRole('img', { name: /^A · unité A :/ })).toBeVisible();
    await expect(page.getByRole('img', { name: /^A · unité B :/ })).toBeVisible();
    await expect(page.getByText('Deux unités pour une même mesure')).toBeVisible();
    await expect(page.getByText('jamais converties, jamais additionnées, jamais reliées entre elles')).toBeVisible();
    await expect(page.getByText(/équivaut|≈/)).toHaveCount(0);
    expect(await rows(page).locator('td:nth-child(4)').allTextContents()).toEqual(expect.arrayContaining(['unité A', 'unité B']));
    await accessible(page);
  });

  test('a reading with one component is incomplete in the table and left off the paired curve', async ({ page }) => {
    const only = { ...claireMeasurements[2], id: ids(9301), lineage: ids(9801), value: '130', at: '2026-11-12T07:40:00Z', recordedAt: '2026-11-12T07:41:00Z', group: ids(9503), sourceId: ids(5004), reference: 'SRC-A-004' };
    await stubMeasurements(page, { measurements: { [claire.recordId]: [...claireMeasurements, only], [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(4);
    const incomplete = rows(page).first();
    await expect(incomplete).toContainText('130 / —');
    await expect(incomplete).toContainText('Incomplète · diastolique manquante');
    await expect(panel(page, 'Pression artérielle')).toContainText('2 mesures · unité unique mmHg · 1 incomplète non tracée');
    await expect(page.getByText('1 mesure incomplète n’est pas tracée sur la courbe : voir le tableau.')).toBeVisible();
    await expect(page.getByRole('img', { name: /Pression artérielle :/ }).first()).toHaveAttribute('aria-label', /^Pression artérielle : 128\/80 mmHg le 14\/09\/2026, 134\/82 mmHg le 20\/10\/2026\./);
    await expect(page.locator('main')).not.toContainText('130/0');
    await accessible(page);
  });

  test('draws at most two series and says the others stay in the table', async ({ page }) => {
    const extra = generated(2, (n) => ({ id: ids(24000 + n), lineage: ids(25000 + n), concept: n === 0 ? 'HEIGHT' : 'PULSE', value: String(170 + n), unit: n === 0 ? 'cm' : '/min', at: '2026-11-0' + (n + 1) + 'T08:00:00Z' }));
    await stubMeasurements(page, { measurements: { [claire.recordId]: [...claireMeasurements, ...extra], [louis.recordId]: [] } });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(5);
    await expect(page.locator('.etaye-panneau h2')).toHaveCount(2);
    await expect(page.getByText('2 autres séries ne sont pas tracées : elles restent dans le tableau. Choisissez une mesure pour les tracer.')).toBeVisible();
  });
});

test.describe('the past-instant view (UD-32, DF-17 for S-11)', () => {
  test('shows the weight before and after its correction, and nothing before the first record', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page);
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '20', m: '10', a: '2026', h: '10', mi: '14' });
    const status = page.locator('.etaye-bandeau--info').filter({ hasText: 'Vue historique — lecture seule' });
    await expect(status).toBeFocused();
    await expect(status).toContainText('Dossier tel qu’il était le 20/10/2026 à 10:14 (heure de Paris).');
    expect(measurementCalls.lists.at(-1)!.searchParams.get('asOf')).toBe('2026-10-20T08:14:00.000Z');
    // At 10:14 the weight is version 1, with no trace that a correction exists.
    const weight = panel(page, 'Poids');
    await expect(weight).toContainText('86,4');
    await expect(weight).toContainText('Version 1 · enregistrée le 20/10/2026 10:13 · Dr Nora Lefebvre');
    await expect(page.locator('main')).not.toContainText('68,4');
    await expect(page.locator('main')).not.toContainText('Corrigé');
    await expect(page.locator('main')).not.toContainText('corrigé');
    await expect(page.getByRole('button', { name: /Voir l’historique/ })).toHaveCount(0);
    await expect(rows(page)).toHaveCount(3);
    await accessible(page);
    // At 10:16 the correction is recorded: 68,4 kg, corrected, with the whole lineage.
    await page.getByRole('button', { name: 'Changer de date' }).click();
    await choose(page, { j: '20', m: '10', a: '2026', h: '10', mi: '16' });
    await expect(panel(page, 'Poids')).toContainText('68,4');
    await expect(panel(page, 'Poids')).toContainText('Corrigé · version 2');
    await expect(panel(page, 'Poids')).toContainText('version précédente 86,4 kg conservée');
    expect(measurementCalls.revisions.at(-1)!.searchParams.get('asOf')).toBe('2026-10-20T08:16:00.000Z');
    // Before any record nothing is known, and nothing reads as normal.
    await page.getByRole('button', { name: 'Changer de date' }).click();
    await choose(page, { j: '01', m: '09', a: '2026', h: '08', mi: '00' });
    await expect(page.getByText('Rien n’était enregistré à cette date')).toBeVisible();
    await expect(page.getByText('Cela ne signifie pas que les valeurs étaient normales')).toBeVisible();
    await expect(page.getByText('Aucune mesure enregistrée au 01/09/2026 à 08:00')).toBeVisible();
    await expect(page.getByText('Aucune mesure au dossier')).toHaveCount(0);
    await expect(page.getByRole('search', { name: 'Filtrer les mesures' })).toHaveCount(0);
    await accessible(page);
    await page.getByRole('button', { name: 'Revenir à la situation actuelle' }).click();
    await expect(rows(page)).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Voir le dossier à une date passée' })).toBeFocused();
  });

  test('a filter without result in a past view is not an empty record', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(MEASURES);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '15', m: '09', a: '2026', h: '12', mi: '00' });
    await expect(rows(page)).toHaveCount(1);
    await page.getByLabel('Mesure', { exact: true }).selectOption('c:WEIGHT');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(page.getByText('Aucune mesure ne correspond à ces filtres')).toBeVisible();
    await expect(page.getByText('Rien n’était enregistré à cette date')).toHaveCount(0);
    await accessible(page);
  });

  test('the relative period counts back from the past instant, not from today', async ({ page }) => {
    const { measurementCalls } = await stubMeasurements(page);
    await page.goto(MEASURES);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '20', m: '10', a: '2026', h: '10', mi: '14' });
    await page.getByLabel('Période', { exact: true }).selectOption('3m');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect.poll(() => measurementCalls.lists.at(-1)!.searchParams.get('from')).toBe('2026-07-20T08:14:00.000Z');
    expect(measurementCalls.lists.at(-1)!.searchParams.get('asOf')).toBe('2026-10-20T08:14:00.000Z');
  });

  test('the choice follows the record to the other tabs and is cleared when the practice changes; nothing clinical is stored', async ({ page }) => {
    await stubMeasurements(page, {}, {}, { contexts: [tilleuls, canal] });
    await page.goto(MEASURES);
    await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
    await choose(page, { j: '15', m: '09', a: '2026', h: '12', mi: '00' });
    await page.getByLabel('Période', { exact: true }).selectOption('12m');
    await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
    await expect(rows(page)).toHaveCount(1);
    await tabs(page).getByRole('link', { name: 'Chronologie' }).click();
    await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
    await tabs(page).getByRole('link', { name: 'Mesures' }).click();
    await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
    await expect(page.getByLabel('Période', { exact: true })).toHaveValue('12m');
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
    for (const secret of ['2026', '128', 'SRC-A', 'Claire', 'WEIGHT', 'mmHg']) expect(stored).not.toContain(secret);
    expect(page.url()).not.toContain('asOf');
    expect(page.url()).not.toContain('2026-09');
    // Another practice and back, through the application only: the as-of choice and the filters must be gone.
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet du Canal/ }).click();
    await page.getByRole('button', { name: /Changer de contexte/ }).click();
    await page.getByRole('button', { name: /Ouvrir · Praticien · Cabinet des Tilleuls/ }).click();
    await page.getByRole('link', { name: 'Patients', exact: true }).first().click();
    await page.getByRole('link', { name: 'MARTIN Claire', exact: true }).click();
    await tabs(page).getByRole('link', { name: 'Mesures' }).click();
    await expect(rows(page)).toHaveCount(3);
    await expect(page.getByText('Vue historique — lecture seule')).toHaveCount(0);
    await expect(page.getByLabel('Période', { exact: true })).toHaveValue('all');
  });
});

test.describe('the practice time zone (D-64)', () => {
  const zoned = (timezone: string | undefined) => [{ ...tilleuls, ...(timezone ? { timezone } : { timezone: undefined }) }];

  for (const [zone, asOf, label] of [
    ['America/Martinique', '2026-10-20T14:14:00.000Z', 'heure du cabinet'],
    ['Pacific/Noumea', '2026-10-19T23:14:00.000Z', 'heure du cabinet'],
    ['Europe/Paris', '2026-10-20T08:14:00.000Z', 'heure de Paris'],
  ] as const) {
    test(`an instant typed by the user is read in ${zone}`, async ({ page }) => {
      const { measurementCalls, evidence } = await stubMeasurements(page, {}, {}, { contexts: zoned(zone) });
      await page.goto(MEASURES);
      await expect(rows(page)).toHaveCount(3);
      await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
      await expect(page.getByText(new RegExp(`^${label.charAt(0).toLocaleUpperCase('fr-FR')}${label.slice(1)}\\. Lecture seule`))).toBeVisible();
      await choose(page, { j: '20', m: '10', a: '2026', h: '10', mi: '14' });
      await expect(page.locator('.etaye-bandeau--info').filter({ hasText: 'Vue historique' })).toContainText(`Dossier tel qu’il était le 20/10/2026 à 10:14 (${label}).`);
      expect(measurementCalls.lists.at(-1)!.searchParams.get('asOf')).toBe(asOf);
      expect(evidence.histories.at(-1)!.searchParams.get('asOf')).toBe(asOf);
    });
  }

  test('every displayed date is shown in the zone of the practice, and a missing zone falls back to Paris', async ({ page }) => {
    await stubMeasurements(page, {}, {}, { contexts: zoned('America/Martinique') });
    await page.goto(MEASURES);
    // 08:12 UTC is 10:12 in Paris and 04:12 in Martinique (UTC-4).
    await expect(rows(page).first()).toContainText('20/10/2026 04:12');
    await expect(page.getByText('le 20/10/2026 à 04:12')).toBeVisible();
    await page.goto(RECORD);
    await expect(page.locator('.etaye-chrono__item').filter({ hasText: 'SRC-A-007-v2' })).toContainText('survenu le 20/10/2026 04:12');
    await stubMeasurements(page, {}, {}, { contexts: zoned(undefined) });
    await page.goto(MEASURES);
    await expect(rows(page).first()).toContainText('20/10/2026 10:12');
  });

  test('a zone the browser cannot read, or none at all, says that Paris time is shown instead of falling back silently', async ({ page }) => {
    await stubMeasurements(page, {}, {}, { contexts: zoned('Not/AZone') });
    await page.goto(MEASURES);
    const notice = page.locator('.etaye-bandeau--attention').filter({ hasText: 'Fuseau du cabinet non disponible' });
    await expect(notice).toBeVisible();
    await expect(notice).toContainText('affichées à l’heure de Paris par défaut');
    await expect(rows(page).first()).toContainText('20/10/2026 10:12');
    await accessible(page);
    await stubMeasurements(page, {}, {}, { contexts: zoned(undefined) });
    await page.goto(MEASURES);
    await expect(page.getByText('Fuseau du cabinet non disponible')).toBeVisible();
    // A readable zone, Paris included, needs no such notice.
    await stubMeasurements(page, {}, {}, { contexts: zoned('Europe/Paris') });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    await expect(page.getByText('Fuseau du cabinet non disponible')).toHaveCount(0);
  });
});

test.describe('long untrusted content and the layout (AT-011-C)', () => {
  test('a 100-character concept, a 40-character unit, a 200-character author and an 18-digit value wrap and clip nothing at 1440, 390 and 320 px', async ({ page }) => {
    const hostile = '<img src=x onerror="window.__m = 1"> ';
    const concept = (hostile + 'Mesure de gabarit dont le libellé de concept est volontairement très long pour vérifier le retour à la ligne').slice(0, 100);
    const unit = 'unité-de-gabarit-très-longue-sans-espace'.slice(0, 40);
    const author = ('Dr Nora Lefebvre-' + 'X'.repeat(60) + ' · Cabinet des Tilleuls · service de gabarit avec un libellé d’auteur très long pour vérifier le retour à la ligne des sources').slice(0, 200);
    const list = generated(2, (n) => ({ concept, value: n === 0 ? '123456789012.123456' : '123456789013.5', unit, author, at: n === 0 ? '2026-09-14T07:35:00Z' : '2026-10-20T08:10:00Z' }));
    await stubMeasurements(page, { measurements: { [claire.recordId]: list, [louis.recordId]: [] } });
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 844 });
      await page.goto(MEASURES);
      await expect(rows(page)).toHaveCount(2);
      await expect(rows(page).last()).toContainText('123456789012,123456');
      await noOverflow(page);
      const clipped = await page.evaluate(() => [...document.querySelectorAll('main td, main th, .etaye-panneau__tete h2, main .etaye-3, main .etaye-etat, .etaye-sources-liste li')]
        .filter((element) => element.scrollWidth > element.clientWidth + 1).map((element) => `${element.tagName}.${element.className} ${element.scrollWidth}>${element.clientWidth}`));
      expect(clipped).toEqual([]);
      expect(await page.evaluate(() => (window as unknown as { __m?: number }).__m)).toBeUndefined();
      await expect(page.locator('main img[src="x"]')).toHaveCount(0);
      // The text of an SVG never wraps: labels that would clip are left out, and the unit is said in the panel title.
      await expect(page.locator('.etaye-panneau h2').first()).toContainText(unit);
      await expect(page.locator('svg.etaye-courbe:visible text.val')).toHaveCount(0);
      if (width !== 1440) await accessible(page);
    }
  });

  test('the tabs wrap at 320 px so the current tab stays visible', async ({ page }) => {
    await stubMeasurements(page);
    await page.setViewportSize({ width: 320, height: 844 });
    await page.goto(MEASURES);
    await expect(rows(page)).toHaveCount(3);
    const current = tabs(page).getByRole('link', { name: 'Mesures' });
    await expect(current).toBeVisible();
    const box = await current.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(320);
    const scrolls = await page.evaluate(() => { const nav = document.querySelector('.etaye-onglets')!; return nav.scrollWidth > nav.clientWidth + 1; });
    expect(scrolls).toBe(false);
    await noOverflow(page);
  });

  test('keeps the shell and background in both themes at four widths without overflow or violation, the chart switching to its narrow form', async ({ page }) => {
    await stubMeasurements(page);
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme });
      for (const [width, height] of [[1440, 1024], [1024, 768], [390, 844], [320, 844]] as const) {
        await page.setViewportSize({ width, height });
        for (const path of [MEASURES, LOUIS]) {
          await page.goto(path);
          await expect(page.locator('.etaye-banniere')).toBeVisible();
          await expect(page.locator('h1')).toBeVisible();
          expect(await usesBackground(page, '.etaye-app', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-top', '--fond')).toBe(true);
          expect(await usesBackground(page, '.etaye-main', '--surface')).toBe(true);
          await noRingOnHeading(page);
          await noOverflow(page);
          if (path === MEASURES) {
            await expect(rows(page)).toHaveCount(3);
            await expect(page.locator(width <= 760 ? 'svg.etaye-courbe--etroite' : 'svg.etaye-courbe--large').first()).toBeVisible();
            await expect(page.locator(width <= 760 ? 'svg.etaye-courbe--large' : 'svg.etaye-courbe--etroite').first()).toBeHidden();
          }
          if (width === 390 || width === 1440) await accessible(page);
        }
      }
    }
  });

  test('keyboard focus shows a 2 px ring on every control of the measurements', async ({ page }) => {
    await stubMeasurements(page);
    await page.setViewportSize({ width: 1440, height: 1024 });
    await page.goto(MEASURES);
    await expect(heading(page, 'Mesures')).toBeFocused();
    await expect(rows(page)).toHaveCount(3);
    await expect(heading(page, 'Mesures')).toBeFocused();
    const stops: string[] = [];
    for (let tab = 0; tab < 15; tab += 1) {
      await page.keyboard.press('Tab');
      const ring = await page.evaluate(() => {
        const element = document.activeElement as HTMLElement;
        const style = getComputedStyle(element);
        return { style: style.outlineStyle, width: style.outlineWidth, element: `${element.tagName}.${element.className}#${element.id}` };
      });
      stops.push(`${ring.element} ${ring.style} ${ring.width}`);
    }
    expect(stops.filter((stop) => !/ solid 2px$/.test(stop)), stops.join('\n')).toEqual([]);
  });

  test('the tab bar keeps the ink of the approved specimens, never the accent of a plain link', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(MEASURES);
    await expect(tabs(page).getByRole('link')).toHaveCount(3);
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme });
      const colors = await page.evaluate(() => {
        const probe = (token: string) => {
          const span = document.createElement('span');
          span.style.color = `var(${token})`;
          document.body.append(span);
          const color = getComputedStyle(span).color;
          span.remove();
          return color;
        };
        const links = [...document.querySelectorAll('.etaye-onglets a')];
        return {
          inactive: links.filter((a) => !a.hasAttribute('aria-current')).map((a) => getComputedStyle(a).color),
          current: links.filter((a) => a.hasAttribute('aria-current')).map((a) => getComputedStyle(a).color),
          secondary: probe('--encre-2'), ink: probe('--encre'), accent: probe('--accent'),
        };
      });
      expect(colors.inactive, scheme).toEqual([colors.secondary, colors.secondary]);
      expect(colors.current, scheme).toEqual([colors.ink]);
      expect(colors.secondary).not.toEqual(colors.accent);
    }
  });

  test('the Mesures tab is reachable from the other two and keeps the heading focus', async ({ page }) => {
    await stubMeasurements(page);
    await page.goto(RECORD);
    await tabs(page).getByRole('link', { name: 'Mesures' }).click();
    await expect(heading(page, 'Mesures')).toBeFocused();
    await expect(page).toHaveURL(MEASURES);
    await tabs(page).getByRole('link', { name: 'Antécédents et allergies' }).click();
    await expect(heading(page, 'Antécédents et allergies')).toBeFocused();
    await tabs(page).getByRole('link', { name: 'Mesures' }).click();
    await expect(rows(page)).toHaveCount(3);
  });
});
