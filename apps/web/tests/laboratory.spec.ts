import { expect, test, type Page } from './support/browser-coverage';
import { accessible, base, claire, heading, ids, louis, noOverflow, problem, reply, tilleuls, type Reply } from './support/patients';
import { claireRows, stubRecord } from './support/record';
import type { LabResult, LabResultPage } from '../src/api/generated/types.gen';

const RECORD = `${base}/patients/${claire.recordId}`;
const LABS = `${RECORD}/biologie`;
const JULY = '2026-07-15T00:00:00Z';
const VIEW = '2026-11-26T12:00:00Z';
const ANALYZE = 'a'.repeat(64);
const eventDate = { precision: 'INSTANT' as const, instant: '2026-10-12T06:00:00Z' };
const sourceRef = claireRows[2];
const numeric: LabResult = {
  id: ids(3301), practiceId: tilleuls.practiceId, version: 1, createdAt: VIEW, updatedAt: VIEW,
  analyte: 'Analyse A', valueKind: 'NUMERIC', numericValue: '0.060', comparator: 'LT', unit: 'µmol/L',
  eventDate, specimenDate: eventDate, sourceType: 'DOCUMENT_REPORTED', sourceRevisionId: sourceRef.id, sourceReference: sourceRef.reference,
  recordedAt: sourceRef.recordedAt, authorLabel: sourceRef.author,
};
const text: LabResult = {
  ...numeric, id: ids(3302), analyte: 'Analyse textuelle', valueKind: 'QUALITATIVE',
  numericValue: undefined, comparator: undefined, qualitativeValue: 'Sous le seuil de détection', unit: undefined,
  eventDate: { precision: 'UNKNOWN' }, specimenDate: { precision: 'UNKNOWN' },
};
const newer: LabResult = { ...numeric, id: ids(3303), revision: 2, version: 2, supersedesId: numeric.id,
  comparator: undefined, numericValue: '7.04', correctionReason: 'Chiffres inversés' };

const asPage = (items: LabResult[], nextCursor: string | null = null): LabResultPage => ({ items, nextCursor, viewAt: VIEW });
const resultRows = (page: Page) => page.locator('.etaye-biologie tbody tr');

async function stubLabs(page: Page, options: {
  list?: (url: URL) => Reply | undefined; analytes?: (url: URL) => Reply | undefined;
  revisions?: (url: URL) => Reply | undefined; source?: () => Reply | undefined;
} = {}) {
  const { evidence } = await stubRecord(page, { source: options.source });
  const requests: URL[] = [];
  await page.route(url => /\/records\/[^/]+\/lab-results(?:\/.*)?$/.test(url.pathname), async route => {
    const url = new URL(route.request().url());
    requests.push(url);
    if (url.pathname.endsWith('/analytes')) return reply(route, options.analytes?.(url) ?? {
      json: { items: [{ key: ANALYZE, label: 'Analyse A' }], nextCursor: null },
    });
    if (url.pathname.endsWith('/revisions')) return reply(route, options.revisions?.(url) ?? { json: { revisions: [numeric, newer] } });
    return reply(route, options.list?.(url) ?? {
      json: asPage(url.pathname.includes(louis.recordId) ? [] : [numeric, text]),
    });
  });
  return { requests, evidence };
}

test('from chronology reads comparator, text and missing intervals, and returns source focus to the invoking row', async ({ page }) => {
  const { requests, evidence } = await stubLabs(page);
  await page.goto(RECORD);
  await page.getByRole('navigation', { name: 'Sections' }).getByRole('link', {name:'Biologie'}).click();
  await expect(heading(page, 'Biologie')).toBeFocused();
  await expect(resultRows(page)).toHaveCount(2);
  await expect(resultRows(page).first()).toContainText('< 0,060');
  await expect(resultRows(page).first()).toContainText('Intervalle non fourni');
  await expect(resultRows(page).last()).toContainText('Sous le seuil de détection');
  await expect(resultRows(page).last()).toContainText('Unité non fournie');
  await expect(page.getByRole('button', { name: /Saisir|Extraire|Corriger un résultat/ })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Documents' })).toHaveCount(0);
  const marker = resultRows(page).last().getByRole('link', { name: /Source 1/ });
  await marker.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: '1 · SRC-A-003' })).toBeFocused();
  await expect(page.getByRole('region', { name: 'Source SRC-A-003' })).toContainText('DOC-A-001:page-1');
  await page.getByRole('link', { name: 'Revenir au résultat' }).focus();
  await page.keyboard.press('Enter');
  await expect(marker).toBeFocused();
  expect(evidence.sources[0].searchParams.get('asOf')).toBe(VIEW);
  expect(requests.find(url => url.pathname.endsWith('/analytes'))?.searchParams.get('asOf')).toBe(VIEW);
  await accessible(page);
});

test('an unconfirmed transcription and a supplied outside notice stay separate from inferences', async ({ page }) => {
  const outside: LabResult = { ...numeric, comparator: undefined, numericValue: '12.00', upperBound: '8',
    intervalUnit: 'µmol/L', outsideInterval: 'ABOVE', unconfirmedTranscription: true };
  await stubLabs(page, { list: () => ({ json: asPage([outside, { ...outside, id: text.id, outsideInterval: undefined, unconfirmedTranscription: undefined }]) }) });
  await page.goto(LABS);
  await expect(resultRows(page).first()).toContainText('Au-dessus de l’intervalle fourni · mention de la source');
  await expect(resultRows(page).first()).toContainText('à vérifier contre la source');
  await expect(resultRows(page).last()).not.toContainText('Au-dessus');
  await accessible(page);
});

test('only compatible dated points are plotted and the table is kept', async ({ page }) => {
  const first = { ...numeric, comparator: undefined, numericValue:'6.80', lowerBound: '2', upperBound: '8',
    intervalSource: 'Laboratoire fictif', intervalUnit: 'µmol/L', intervalContext: 'Méthode A, à jeun, matin', comparisonContext: { method: 'A', fasting: 'À jeun', samplingWindow: 'Matin' } };
  const second = { ...first, id: ids(3304), numericValue: '7.04', eventDate: { ...eventDate, instant: '2026-10-22T06:00:00Z' } };
  await stubLabs(page, { list: () => ({ json: asPage([second, first, text]) }) });
  await page.goto(LABS);
  const chart = page.getByRole('img', { name: /Analyse A · µmol\/L/ });
  await expect(chart.locator('circle')).toHaveCount(2);
  await expect(chart.locator('polyline, path')).toHaveCount(0);
  await expect(resultRows(page)).toHaveCount(3);
  await accessible(page);
});

test('a cold load shows no empty message, panel retry keeps the record, and a real empty outcome is explicit', async ({ page }) => {
  let attempt = 0;
  await stubLabs(page, { list: () => ++attempt === 1 ? { status: 503, json: problem(503, 'UNAVAILABLE'), delay: 600 } : { json: asPage([]) } });
  await page.goto(LABS);
  await expect(page.getByRole('status').filter({ hasText: 'Chargement des résultats de biologie' })).toBeVisible();
  await expect(page.getByText('Aucun résultat de biologie', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Résultats non chargés')).toBeVisible();
  await expect(page.locator('.etaye-banniere')).toContainText('MARTIN Claire');
  await accessible(page);
  await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(page.getByText('Aucun résultat de biologie', { exact: true })).toBeVisible();
  await accessible(page);
});

test('exact analyte and period filter can yield no results and can be reset', async ({ page }) => {
  const { requests } = await stubLabs(page, { list: url => url.searchParams.get('analyteKey') ? { json: asPage([]) } : undefined });
  await page.goto(LABS);
  await page.getByLabel('Analyse', { exact: true }).selectOption(ANALYZE);
  await page.getByLabel('Période', { exact: true }).selectOption('3m');
  await page.getByRole('button', { name: 'Appliquer les filtres' }).click();
  await expect(page.getByText('Aucun résultat pour ces filtres')).toBeVisible();
  expect(requests.filter(url => url.searchParams.has('analyteKey')).at(-1)?.searchParams.get('from')).toBeTruthy();
  await accessible(page);
  await page.getByRole('button', { name: 'Effacer les filtres' }).click();
  await expect(resultRows(page)).toHaveCount(2);
});

test('a failing next page and its retry preserve existing rows and keep the same cursor', async ({ page }) => {
  let overflowAttempts = 0;
  const { requests } = await stubLabs(page, { list: url => {
    if (!url.searchParams.get('cursor')) return { json: asPage([numeric], 'page-2') };
    overflowAttempts += 1;
    return overflowAttempts === 1 ? { status: 503, json: problem(503, 'UNAVAILABLE') } : { json: asPage([text]) };
  } });
  await page.goto(LABS);
  await page.getByRole('button', { name: 'Afficher plus de résultats' }).click();
  await expect(page.getByText('Suite de la liste indisponible')).toBeVisible();
  await expect(resultRows(page)).toHaveCount(1);
  await accessible(page);
  await page.getByRole('button', { name: 'Réessayer d’afficher la suite' }).click();
  await expect(resultRows(page)).toHaveCount(2);
  expect(requests.filter(url => url.searchParams.has('cursor')).map(url => url.searchParams.get('cursor'))).toEqual(['page-2', 'page-2']);
});

test('options can load independent pages while the results stay visible', async ({ page }) => {
  const key = 'b'.repeat(64);
  await stubLabs(page, { analytes: url => url.searchParams.has('cursor')
    ? { json: { items: [{ key, label: 'Analyse B' }], nextCursor: null } }
    : { json: { items: [{ key: ANALYZE, label: 'Analyse A' }], nextCursor: 'option-2' } } });
  await page.goto(LABS);
  await page.getByRole('button', { name: 'Afficher plus d’analyses' }).click();
  await page.getByLabel('Analyse', { exact: true }).selectOption(key);
  await expect(resultRows(page)).toHaveCount(2);
  await accessible(page);
});

test('an authorized source failure preserves the result and offers retry', async ({ page }) => {
  let fail = true;
  await stubLabs(page, { source: () => fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined });
  await page.goto(LABS);
  await resultRows(page).first().getByRole('link').click();
  const source = page.getByRole('region', { name: 'Source SRC-A-003' });
  await expect(source).toContainText('Le résultat enregistré reste affiché.');
  await expect(resultRows(page)).toHaveCount(2);
  await accessible(page);
  fail = false;
  await source.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(source).toContainText('DOC-A-001:page-1');
});

test('correction source and history are pinned to the view, including independent error recovery', async ({ page }) => {
  let fail = true;
  const { requests } = await stubLabs(page, { list: url => {
    const asOf = url.searchParams.get('asOf');
    return { json: asOf ? { ...asPage([numeric]), viewAt: asOf } : asPage([newer]) };
  }, revisions: () => fail ? { status: 503, json: problem(503, 'UNAVAILABLE') } : undefined });
  await page.goto(LABS);
  await page.getByRole('button', { name: 'Historique du résultat · Analyse A' }).click();
  await expect(page.getByText('Historique temporairement indisponible')).toBeVisible();
  await expect(resultRows(page).first()).toContainText('7,04');
  await accessible(page);
  fail = false;
  await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(page.locator('.etaye-lab-revisions li')).toHaveCount(2);
  await expect(page.locator('.etaye-lab-revisions li').first()).toContainText('Version 1');
  await expect(page.locator('.etaye-lab-revisions li').last()).toContainText('Chiffres inversés');
  expect(requests.find(url => url.pathname.endsWith('/revisions'))?.searchParams.get('asOf')).toBe(VIEW);
  await accessible(page);
  await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
  for (const [part, value] of [['j','21'],['m','10'],['a','2026'],['h','10'],['mi','14']]) await page.locator(`#as-${part}`).fill(value);
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
  await expect(page.getByText('Vue historique — lecture seule')).toBeVisible();
  await expect(resultRows(page)).toHaveCount(1);
  await expect(page.locator('.etaye-biologie')).not.toContainText('Chiffres inversés');
  await expect(page.getByRole('button', { name: /Historique du résultat/ })).toHaveCount(0);
});

test('rate limited and authorization failures never render a successful clinical body', async ({ page }) => {
  let restricted = false;
  await stubLabs(page, { list: () => restricted
    ? { status: 403, json: problem(403, 'ACCESS_DENIED') }
    : { status: 429, json: problem(429, 'RATE_LIMITED'), headers: { 'Retry-After': '1' } } });
  await page.goto(LABS);
  await expect(page.getByText('Limite temporaire de requêtes')).toBeVisible();
  await expect(page.getByRole('button', { name: /Réessayer dans/ })).toBeDisabled();
  await accessible(page);
  restricted = true;
  await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(heading(page, 'Cet espace est réservé aux soins')).toBeVisible();
  await expect(page.getByText('MARTIN Claire')).toHaveCount(0);
  await expect(resultRows(page)).toHaveCount(0);
  await accessible(page);
});

test('source and correction history announce loading independently', async ({ page }) => {
  const { requests } = await stubLabs(page, { list: () => ({ json: asPage([newer]) }) });
  await page.route(url => /\/sources\/[^/]+$|\/lab-results\/[^/]+\/revisions$/.test(url.pathname), () => {});
  await page.goto(LABS);
  await resultRows(page).first().getByRole('link').click();
  await page.getByRole('button', { name: 'Historique du résultat · Analyse A' }).click();
  await expect(page.getByText('Chargement de la source…')).toBeVisible();
  await expect(page.getByText('Chargement de l’historique…')).toBeVisible();
  await expect(resultRows(page)).toHaveCount(1);
  await accessible(page);
  expect(requests.some(url => url.pathname.endsWith('/lab-results'))).toBe(true);
});

test('uncertain comparison stays in the table and does not merge incompatible units', async ({ page }) => {
  const other = { ...numeric, id: text.id, unit: 'mmol/L' };
  await stubLabs(page, { list: () => ({ json: asPage([numeric, other]) }) });
  await page.goto(LABS);
  await expect(resultRows(page)).toHaveCount(2);
  await expect(page.getByText(/La comparaison nécessite des dates distinctes/)).toBeVisible();
  await expect(page.locator('.etaye-lab-chart')).toHaveCount(0);
  await accessible(page);
});

test('comparable axis labels and points keep distinct exact large decimals', async ({ page }) => {
  const lower = { ...numeric, comparator: undefined, numericValue: '999999999999.123455',
    lowerBound: '2', upperBound: '8', intervalSource: 'Laboratoire fictif', intervalUnit: numeric.unit,
    comparisonContext: { method: 'A', fasting: 'À jeun', samplingWindow: 'Matin' } };
  const upper = { ...lower, id: ids(3310), numericValue: '999999999999.123456',
    eventDate: { ...eventDate, instant: '2026-10-22T06:00:00Z' } };
  await page.setViewportSize({ width: 390, height: 844 });
  await stubLabs(page, { list: () => ({ json: asPage([upper, lower]) }) });
  await page.goto(LABS);
  const chart = page.getByRole('img', { name: /Analyse A · µmol\/L/ });
  await expect(chart.locator('text')).toHaveText(['999999999999,123455', '999999999999,123456', '12/10/26', '22/10/26']);
  expect(new Set(await chart.locator('circle').evaluateAll(points => points.map(point => point.getAttribute('cy')))).size).toBe(2);
  await noOverflow(page);
  await accessible(page);
});

test('long correction and source prose remains legible and keyboard-accessible', async ({ page }) => {
  const reason = 'R'.repeat(1000);
  const excerpt = 'E'.repeat(500);
  const corrected = { ...newer, correctionReason: reason };
  await page.setViewportSize({ width: 320, height: 844 });
  await page.emulateMedia({ colorScheme: 'dark' });
  await stubLabs(page, { list: () => ({ json: asPage([corrected]) }),
    revisions: () => ({ json: { revisions: [numeric, corrected] } }),
    source: () => ({ json: { id: sourceRef.id, reference: sourceRef.reference, revision: 1, sourceType: 'DOCUMENT_REPORTED',
      eventDate, recordedAt: sourceRef.recordedAt, authorLabel: sourceRef.author, excerpt } }) });
  await page.goto(LABS);
  const marker = resultRows(page).first().getByRole('link');
  await marker.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('region', { name: 'Source SRC-A-003' })).toContainText(excerpt);
  const history = page.getByRole('button', { name: 'Historique du résultat · Analyse A' });
  await history.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('.etaye-lab-revisions li').last()).toContainText(reason);
  await expect(history).toBeFocused();
  await noOverflow(page);
  await accessible(page);
});

test('a rejected future date keeps the current data, and a date before the first result is explicitly empty', async ({ page }) => {
  const { requests } = await stubLabs(page, { list: url => {
    const asOf = url.searchParams.get('asOf');
    return asOf ? { json: { ...asPage([]), viewAt: asOf } } : undefined;
  } });
  await page.goto(LABS);
  await expect(resultRows(page)).toHaveCount(2);
  await page.getByRole('button', { name: 'Voir le dossier à une date passée' }).click();
  for (const [part, value] of [['j', '1'], ['m', '9'], ['a', '2090'], ['h', '9'], ['mi', '00']]) await page.locator(`#as-${part}`).fill(value);
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'une date future ne peut pas être affichée' })).toBeVisible();
  await expect(resultRows(page)).toHaveCount(2);
  expect(requests.filter(url => url.pathname.endsWith('/lab-results')).some(url => url.searchParams.has('asOf'))).toBe(false);
  await accessible(page);
  await page.locator('#as-a').fill('2026');
  await page.getByRole('button', { name: 'Afficher cette date' }).click();
  await expect(page.getByText('Aucun résultat enregistré à cette date.')).toBeVisible();
  await expect(resultRows(page)).toHaveCount(0);
  await accessible(page);
});

for (const width of [320, 390, 768, 1440]) {
  test(`long values preserve source, unit, focus and record tabs at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 880 });
    await stubLabs(page, { list: () => ({ json: asPage([{ ...numeric, comparator: 'LE', numericValue: '999999999999.123456',
      analyte: 'Analyse'.repeat(14), unit: 'u'.repeat(40), referenceIntervalText: 'Intervalle'.repeat(50),
      resultDate: { precision: 'DATE', date: '2026-10-13' }, createdAt: JULY }]) }) });
    await page.goto(LABS);
    await expect(resultRows(page).first()).toContainText('≤ 999999999999,123456');
    await expect(resultRows(page).first()).toContainText('Résultat du 13/10/2026');
    const marker = resultRows(page).first().getByRole('link');
    await marker.focus();
    await expect(marker).toBeFocused();
    if (width === 768) {
      const headerFits = await page.locator('.etaye-top').evaluate(header => {
        const context = header.querySelector('.etaye-contexte')?.getBoundingClientRect();
        const top = header.getBoundingClientRect();
        return Boolean(context && context.top >= top.top && context.bottom <= top.bottom);
      });
      expect(headerFits).toBe(true);
      await expect(page.locator('.etaye-biologie thead')).toBeHidden();
    }
    await noOverflow(page);
    await accessible(page);
  });
}
